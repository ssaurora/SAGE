from __future__ import annotations

from app.schemas import (
    CognitionMetadata,
    RepairActionExplanation,
    RepairProposalRequest,
    RepairProposalResponse,
    RequiredUserAction,
)
import os
from typing import Any

import httpx

from app.cognition_support import call_glm_json

DEBUG_REPAIR = os.getenv("SAGE_REPAIR_DEBUG", "false").strip().lower() in ("1", "true", "yes", "on")
DEBUG_MAX_CHARS = int(os.getenv("SAGE_REPAIR_DEBUG_MAX_CHARS", "2000"))


def debug_log(message: str) -> None:
    if DEBUG_REPAIR:
        print(f"[repair-debug] {message}", flush=True)


def truncate_text(text: str) -> str:
    if len(text) <= DEBUG_MAX_CHARS:
        return text
    return f"{text[:DEBUG_MAX_CHARS]}...<truncated>"


def build_repair_proposal_response(payload: RepairProposalRequest) -> RepairProposalResponse:
    provider = os.getenv("SAGE_REPAIR_PROVIDER", "deterministic").strip().lower()
    if provider == "glm":
        try:
            return build_glm_repair_proposal(payload)
        except Exception as exc:
            debug_log(f"GLM provider failed: {type(exc).__name__}: {exc}")
            return build_unavailable_repair_proposal(payload, provider="glm", exc=exc)
    return build_deterministic_repair_proposal(payload)


def build_deterministic_repair_proposal(
    payload: RepairProposalRequest, *, debug_note: str | None = None
) -> RepairProposalResponse:
    waiting_context = payload.waiting_context
    validation_summary = payload.validation_summary
    failure_summary = payload.failure_summary

    missing_slots = waiting_context.missing_slots
    required_actions = waiting_context.required_user_actions
    invalid_bindings = waiting_context.invalid_bindings
    failure_code = failure_summary.failure_code or validation_summary.error_code or ""
    user_note = (payload.user_note or "").strip()

    if missing_slots:
        user_facing_reason = "Additional required inputs are missing before the task can continue."
    elif invalid_bindings:
        user_facing_reason = "The current task has binding issues that must be repaired before execution can continue."
    elif failure_code:
        user_facing_reason = f"The task requires review because the latest validation reported {failure_code}."
    else:
        user_facing_reason = "Task requires additional user actions before it can continue."

    resume_hint = waiting_context.resume_hint or "Complete required actions and try resume."

    action_explanations: list[RepairActionExplanation] = []
    for action in required_actions:
        key = action.key
        label = action.label or key or "required action"
        action_type = action.action_type or "action"
        if action_type == "upload":
            message = f"Upload the required input for {label}."
        elif action_type == "override":
            message = f"Provide or correct the required value for {label}."
        else:
            message = f"Complete the required step for {label}."
        action_explanations.append(RepairActionExplanation(key=key, message=message))

    notes = [
        "Dispatcher rules remain the source of truth.",
        "This repair proposal is advisory and does not decide workflow state.",
    ]
    if user_note:
        notes.append(f"User note acknowledged: {user_note}")
    if failure_code:
        notes.append(f"Latest structured failure code: {failure_code}")
    if DEBUG_REPAIR and debug_note:
        notes.append(f"debug_note: {debug_note}")

    return RepairProposalResponse(
        available=True,
        user_facing_reason=user_facing_reason,
        resume_hint=resume_hint,
        action_explanations=action_explanations,
        notes=notes,
        failure_code=None,
        failure_message=None,
        cognition_metadata=CognitionMetadata(
            source="deterministic_baseline",
            provider="deterministic",
            model=None,
            prompt_version="repair_proposal_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=None,
            status="DETERMINISTIC_BASELINE",
        ),
    )


def build_glm_repair_proposal(payload: RepairProposalRequest) -> RepairProposalResponse:
    system_prompt = (
        "You are a repair assistant. "
        "Return ONLY strict JSON with keys: "
        "user_facing_reason (string), resume_hint (string), "
        "action_explanations (list of {key,message}), notes (list of strings). "
        "Do not include extra keys."
    )

    user_payload = normalize_repair_payload(payload)
    parsed, metadata = call_glm_json(
        system_prompt,
        user_payload,
        temperature=0.2,
        max_tokens=1400,
        timeout_ms=30000,
    )

    action_explanations: list[RepairActionExplanation] = []
    for item in parsed.get("action_explanations", []) or []:
        key = str(item.get("key") or "")
        message = str(item.get("message") or "")
        if key or message:
            action_explanations.append(RepairActionExplanation(key=key, message=message))

    notes = [str(note) for note in (parsed.get("notes") or [])]
    if DEBUG_REPAIR:
        notes.append(f"debug_llm_model: {metadata.get('model')}")

    return RepairProposalResponse(
        available=True,
        user_facing_reason=str(parsed.get("user_facing_reason") or "Task requires additional user actions."),
        resume_hint=str(parsed.get("resume_hint") or "Complete required actions and try resume."),
        action_explanations=action_explanations,
        notes=notes,
        failure_code=None,
        failure_message=None,
        cognition_metadata=CognitionMetadata(
            source="glm_primary",
            provider="glm",
            model=str(metadata.get("model") or ""),
            prompt_version="repair_proposal_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=str(metadata.get("response_id") or "") or None,
            status="LLM_PRIMARY",
        ),
    )


def build_unavailable_repair_proposal(
    payload: RepairProposalRequest,
    *,
    provider: str,
    exc: Exception,
) -> RepairProposalResponse:
    failure_code, failure_message = classify_failure(exc)
    notes = [
        "Repair proposal cognition unavailable.",
        "Dispatcher rules remain the source of truth.",
    ]
    if DEBUG_REPAIR:
        notes.append(f"debug_note: {type(exc).__name__}: {exc}")
    return RepairProposalResponse(
        available=False,
        user_facing_reason=None,
        resume_hint=None,
        action_explanations=[],
        notes=notes,
        failure_code=failure_code,
        failure_message=failure_message,
        cognition_metadata=CognitionMetadata(
            source="glm_required_failure",
            provider=provider,
            model=os.getenv("SAGE_GLM_MODEL", "glm-4.7") if provider == "glm" else None,
            prompt_version="repair_proposal_v1",
            fallback_used=False,
            schema_valid=False,
            response_id=None,
            status="COGNITION_UNAVAILABLE",
            failure_code=failure_code,
            failure_message=failure_message,
        ),
    )


def normalize_repair_payload(payload: RepairProposalRequest) -> dict[str, Any]:
    waiting_context = payload.waiting_context
    validation_summary = payload.validation_summary
    failure_summary = payload.failure_summary
    return {
        "waiting_context": {
            "waiting_reason_type": waiting_context.waiting_reason_type,
            "missing_slots": [
                {
                    "slot_name": slot.slot_name,
                    "expected_type": slot.expected_type,
                    "required": slot.required,
                }
                for slot in waiting_context.missing_slots
            ],
            "invalid_bindings": list(waiting_context.invalid_bindings),
            "required_user_actions": [
                serialize_required_action(action) for action in waiting_context.required_user_actions
            ],
            "resume_hint": waiting_context.resume_hint,
            "can_resume": waiting_context.can_resume,
        },
        "validation_summary": {
            "is_valid": validation_summary.is_valid,
            "missing_roles": list(validation_summary.missing_roles),
            "missing_params": list(validation_summary.missing_params),
            "error_code": validation_summary.error_code,
            "invalid_bindings": list(validation_summary.invalid_bindings),
        },
        "failure_summary": {
            "failure_code": failure_summary.failure_code,
            "failure_message": failure_summary.failure_message,
            "created_at": failure_summary.created_at,
        },
        "user_note": payload.user_note,
    }


def serialize_required_action(action: RequiredUserAction) -> dict[str, Any]:
    return {
        "action_type": action.action_type,
        "key": action.key,
        "label": action.label,
        "required": action.required,
    }


def classify_failure(exc: Exception) -> tuple[str, str]:
    if isinstance(exc, httpx.TimeoutException):
        return "COGNITION_TIMEOUT", "Repair proposal request timed out."
    if isinstance(exc, httpx.HTTPError):
        return "COGNITION_UNAVAILABLE", "Repair proposal provider request failed."
    message = str(exc)
    if "valid json" in message.lower():
        return "COGNITION_SCHEMA_INVALID", "Repair proposal provider returned invalid JSON."
    return "COGNITION_UNAVAILABLE", message or "Repair proposal provider failed."
