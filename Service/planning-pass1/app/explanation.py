from __future__ import annotations

import os
from datetime import UTC, datetime

import httpx

from app.case_registry import get_case
from app.cognition_support import call_glm_json
from app.skill_assets import maybe_load_skill_bundle
from app.schemas import CognitionFinalExplanationRequest, CognitionMetadata, FinalExplanation


def build_final_explanation_response(payload: CognitionFinalExplanationRequest) -> FinalExplanation:
    provider = os.getenv("SAGE_FINAL_EXPLANATION_PROVIDER", os.getenv("SAGE_COGNITION_PROVIDER", "deterministic")).strip().lower()
    real_case = _is_real_case_request(payload)
    llm_required = real_case and os.getenv("SAGE_REAL_CASE_LLM_REQUIRED", "true").strip().lower() in ("1", "true", "yes", "on")

    if provider == "glm":
        try:
            return _build_glm_final_explanation_response(payload)
        except Exception as exc:
            if llm_required:
                code, message = _classify_failure(exc)
                return _build_unavailable_explanation(provider="glm", failure_code=code, failure_message=message)
            return _build_deterministic_final_explanation_response(payload, fallback_used=True)

    if llm_required:
        return _build_unavailable_explanation(
            provider=provider or "deterministic",
            failure_code="COGNITION_POLICY_VIOLATION",
            failure_message="Real-case final explanation requires glm provider.",
        )
    return _build_deterministic_final_explanation_response(payload, fallback_used=False)


def _build_glm_final_explanation_response(payload: CognitionFinalExplanationRequest) -> FinalExplanation:
    system_prompt = (
        "You are a governed result explanation generator for a single-skill annual water yield workflow. "
        "Return ONLY strict JSON with keys: title, highlights, narrative. "
        "Use only the supplied result facts. Do not invent file paths, runtime controls, or policy decisions."
    )
    compact_payload = _build_compact_explanation_payload(payload)
    try:
        parsed, metadata = call_glm_json(
            system_prompt,
            compact_payload,
            temperature=0.1,
            max_tokens=900,
            timeout_ms=60000,
        )
        title, highlights, narrative = _normalize_explanation_fields(parsed)
    except Exception:
        parsed, metadata = _build_glm_final_explanation_retry(payload)
        title, highlights, narrative = _normalize_explanation_fields(parsed)
    return FinalExplanation(
        available=True,
        title=title,
        highlights=highlights,
        narrative=narrative,
        generated_at=datetime.now(UTC),
        cognition_metadata=CognitionMetadata(
            source="glm_primary",
            provider="glm",
            model=str(metadata.get("model") or ""),
            prompt_version="final_explanation_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=str(metadata.get("response_id") or "") or None,
            status="LLM_PRIMARY",
        ),
    )


def _build_glm_final_explanation_retry(
    payload: CognitionFinalExplanationRequest,
) -> tuple[dict[str, object], dict[str, object]]:
    retry_prompt = (
        "Return ONLY raw JSON. No markdown. No reasoning. "
        "Required keys: title, highlights, narrative. "
        "Keep title under 8 words, highlights to at most 3 items, and narrative to at most 2 sentences. "
        "Use only supplied facts."
    )
    retry_payload = _build_minimal_retry_payload(payload)
    return call_glm_json(
        retry_prompt,
        retry_payload,
        temperature=0.0,
        max_tokens=260,
        timeout_ms=45000,
    )


def _build_deterministic_final_explanation_response(
    payload: CognitionFinalExplanationRequest,
    *,
    fallback_used: bool,
) -> FinalExplanation:
    bundle = maybe_load_skill_bundle("water_yield")
    guide = bundle.interpretation_guide if bundle is not None else None
    result_bundle = payload.result_bundle or {}
    metrics = result_bundle.get("metrics") if isinstance(result_bundle.get("metrics"), dict) else {}
    main_outputs = result_bundle.get("main_outputs") if isinstance(result_bundle.get("main_outputs"), list) else []
    artifact_catalog = payload.artifact_catalog or {}
    primary_outputs = artifact_catalog.get("primary_outputs") if isinstance(artifact_catalog.get("primary_outputs"), list) else []
    context = {
        "used_real_invest": bool(metrics.get("used_real_invest", False)),
        "main_output_count": len(main_outputs),
        "primary_artifact_count": len(primary_outputs),
        "case_id": payload.case_id or "-",
        "analysis_type_label": guide.analysis_type_label if guide is not None else "Annual Water Yield Analysis",
    }
    title = guide.title if guide is not None else "Water Yield Result Summary"
    if guide is not None and guide.required_highlight_templates:
        highlights = [template.format(**context) for template in guide.required_highlight_templates]
        if guide.limitation_note:
            highlights.append(guide.limitation_note)
    else:
        highlights = [
            f"used_real_invest = {context['used_real_invest']}",
            f"main_output_count = {context['main_output_count']}",
            f"primary_artifact_count = {context['primary_artifact_count']}",
        ]
    narrative = (
        guide.narrative_template.format(**context)
        if guide is not None
        else f"Task {payload.task_id} completed water_yield processing for case {payload.case_id or '-'}. "
             f"The result bundle recorded {len(main_outputs)} main outputs and {len(primary_outputs)} primary artifacts."
    )
    return FinalExplanation(
        available=True,
        title=title,
        highlights=highlights,
        narrative=narrative,
        generated_at=datetime.now(UTC),
        cognition_metadata=CognitionMetadata(
            source="deterministic_fallback" if fallback_used else "deterministic_baseline",
            provider="deterministic",
            model=None,
            prompt_version="final_explanation_v1",
            fallback_used=fallback_used,
            schema_valid=True,
            response_id=None,
            status="DETERMINISTIC_BASELINE" if not fallback_used else "LLM_FALLBACK",
        ),
    )


def _build_unavailable_explanation(*, provider: str, failure_code: str, failure_message: str) -> FinalExplanation:
    return FinalExplanation(
        available=False,
        title=None,
        highlights=[],
        narrative=None,
        generated_at=datetime.now(UTC),
        failure_code=failure_code,
        failure_message=failure_message,
        cognition_metadata=CognitionMetadata(
            source="glm_required_failure",
            provider=provider,
            model=os.getenv("SAGE_GLM_MODEL", "glm-4.7") if provider == "glm" else None,
            prompt_version="final_explanation_v1",
            fallback_used=False,
            schema_valid=False,
            response_id=None,
            status="EXPLANATION_UNAVAILABLE",
            failure_code=failure_code,
            failure_message=failure_message,
        ),
    )


def _is_real_case_request(payload: CognitionFinalExplanationRequest) -> bool:
    runtime_profile = (payload.runtime_profile or "").strip().lower()
    provider_key = (payload.provider_key or "").strip().lower()
    case_id = (payload.case_id or "").strip().lower()
    return (
        runtime_profile == "docker-invest-real"
        or provider_key == "planning-pass1-invest-local"
        or get_case(case_id) is not None
    )


def _classify_failure(exc: Exception) -> tuple[str, str]:
    if isinstance(exc, httpx.TimeoutException):
        return "COGNITION_TIMEOUT", "Final explanation request timed out."
    if isinstance(exc, httpx.HTTPError):
        return "COGNITION_UNAVAILABLE", "Final explanation provider request failed."
    message = str(exc)
    if "valid json" in message.lower():
        return "COGNITION_SCHEMA_INVALID", "Final explanation provider returned invalid JSON."
    if "allowlist" in message.lower() or "required fields" in message.lower():
        return "COGNITION_POLICY_VIOLATION", "Final explanation provider response violated policy."
    return "COGNITION_UNAVAILABLE", message or "Final explanation provider failed."


def _build_compact_explanation_payload(payload: CognitionFinalExplanationRequest) -> dict[str, object]:
    result_bundle = payload.result_bundle if isinstance(payload.result_bundle, dict) else {}
    metrics = result_bundle.get("metrics") if isinstance(result_bundle.get("metrics"), dict) else {}
    main_outputs = result_bundle.get("main_outputs") if isinstance(result_bundle.get("main_outputs"), list) else []
    artifact_catalog = payload.artifact_catalog if isinstance(payload.artifact_catalog, dict) else {}
    workspace_summary = payload.workspace_summary if isinstance(payload.workspace_summary, dict) else {}
    runtime_evidence = payload.docker_runtime_evidence if isinstance(payload.docker_runtime_evidence, dict) else {}
    highlights = result_bundle.get("artifacts") if isinstance(result_bundle.get("artifacts"), list) else []
    return {
        "task_id": payload.task_id,
        "user_query": payload.user_query,
        "case_id": payload.case_id,
        "provider_key": payload.provider_key,
        "runtime_profile": payload.runtime_profile,
        "result_metrics": {
            "used_real_invest": bool(metrics.get("used_real_invest", False)),
            "status": metrics.get("status"),
            "node_count": metrics.get("node_count"),
            "edge_count": metrics.get("edge_count"),
            "output_file_count": metrics.get("output_file_count"),
            "geotiff_count": metrics.get("geotiff_count"),
            "vector_count": metrics.get("vector_count"),
            "table_count": metrics.get("table_count"),
            "seasonality_constant": metrics.get("seasonality_constant"),
        },
        "main_outputs": [str(item) for item in main_outputs[:12]],
        "result_artifacts": [str(item) for item in highlights[:10]],
        "artifact_counts": {
            "primary_outputs": _list_count(artifact_catalog.get("primary_outputs")),
            "audit_artifacts": _list_count(artifact_catalog.get("audit_artifacts")),
            "derived_outputs": _list_count(artifact_catalog.get("derived_outputs")),
            "logs": _list_count(artifact_catalog.get("logs")),
        },
        "workspace_summary": {
            "workspace_state": workspace_summary.get("workspace_state"),
            "runtime_profile": workspace_summary.get("runtime_profile"),
        },
        "runtime_evidence": {
            "runtime_mode": runtime_evidence.get("runtime_mode"),
            "contract_mode": runtime_evidence.get("contract_mode"),
            "input_binding_count": _list_count(runtime_evidence.get("input_bindings")),
        },
    }


def _build_minimal_retry_payload(payload: CognitionFinalExplanationRequest) -> dict[str, object]:
    result_bundle = payload.result_bundle if isinstance(payload.result_bundle, dict) else {}
    metrics = result_bundle.get("metrics") if isinstance(result_bundle.get("metrics"), dict) else {}
    main_outputs = result_bundle.get("main_outputs") if isinstance(result_bundle.get("main_outputs"), list) else []
    return {
        "task_id": payload.task_id,
        "analysis_type": "Annual Water Yield",
        "user_query": payload.user_query,
        "case_id": payload.case_id,
        "provider_key": payload.provider_key,
        "runtime_profile": payload.runtime_profile,
        "status": metrics.get("status"),
        "used_real_invest": bool(metrics.get("used_real_invest", False)),
        "output_file_count": metrics.get("output_file_count"),
        "geotiff_count": metrics.get("geotiff_count"),
        "vector_count": metrics.get("vector_count"),
        "table_count": metrics.get("table_count"),
        "seasonality_constant": metrics.get("seasonality_constant"),
        "main_outputs": [str(item) for item in main_outputs[:5]],
    }


def _normalize_explanation_fields(parsed: dict[str, object]) -> tuple[str, list[str], str]:
    title = str(parsed.get("title") or parsed.get("headline") or parsed.get("summary_title") or "").strip()
    narrative = str(
        parsed.get("narrative")
        or parsed.get("summary")
        or parsed.get("explanation")
        or parsed.get("description")
        or ""
    ).strip()
    raw_highlights = parsed.get("highlights")
    if isinstance(raw_highlights, str):
        highlights = [raw_highlights.strip()] if raw_highlights.strip() else []
    else:
        highlights = [str(item).strip() for item in (raw_highlights or []) if str(item).strip()]
    if not title or not narrative:
        raise ValueError("final explanation response missing required fields")
    return title, highlights, narrative


def _list_count(value: object) -> int:
    return len(value) if isinstance(value, list) else 0
