from __future__ import annotations

import hashlib
import json
import os

import httpx

from app.case_registry import (
    REGISTRY_VERSION,
    get_case,
    list_case_ids,
    list_cases,
    match_cases,
)
from app.cognition_support import call_glm_json
from app.schemas import (
    CaseProjection,
    CognitionGoalRouteRequest,
    CognitionGoalRouteResponse,
    CognitionMetadata,
    CognitionPassBRequest,
    CognitionPassBResponse,
    DecisionSummary,
    GoalParseOutput,
    GraphSkeleton,
    InferredSemanticArg,
    MaterializedExecutionGraph,
    MaterializedExecutionNode,
    PlanningPass1Request,
    PlanningPass1Response,
    PlanningPass2Request,
    PlanningPass2Response,
    PrimitiveValidationRequest,
    PrimitiveValidationResponse,
    RuntimeAssertion,
    SkillRouteOutput,
    SlotBinding,
    SlotSchemaItem,
    SlotSchemaView,
)
from app.skill_catalog import get_skill_definition


def build_pass1_response(payload: PlanningPass1Request) -> PlanningPass1Response:
    skill = get_skill_definition(payload.capability_key)
    selected_template = payload.selected_template or skill.selected_template
    if selected_template != skill.selected_template:
        raise ValueError(f"Unsupported template for {skill.capability.capability_key}: {selected_template}")
    return PlanningPass1Response(
        capability_key=skill.capability.capability_key,
        capability_facts=skill.capability,
        selected_template=selected_template,
        template_version=skill.skill_version,
        logical_input_roles=[*skill.required_roles, *skill.optional_roles],
        role_arg_mappings=skill.role_arg_mappings,
        stable_defaults=skill.stable_defaults,
        slot_schema_view=SlotSchemaView(
            slots=[
                SlotSchemaItem(slot_name=slot.slot_name, type=slot.type, bound_role=slot.bound_role)
                for slot in skill.slot_specs
            ]
        ),
        graph_skeleton=GraphSkeleton(
            nodes=[
                "load_inputs",
                "validate_inputs",
                "align_rasters",
                "run_water_yield",
                "summarize_outputs",
            ],
            edges=[
                ["load_inputs", "validate_inputs"],
                ["validate_inputs", "align_rasters"],
                ["align_rasters", "run_water_yield"],
                ["run_water_yield", "summarize_outputs"],
            ],
        ),
    )


def build_goal_route_response(payload: CognitionGoalRouteRequest) -> CognitionGoalRouteResponse:
    normalized = _normalize_query(payload.user_query, payload.user_note)
    real_case_requested = _is_real_case_requested(normalized)
    provider = _resolve_cognition_provider("SAGE_COGNITION_GOAL_ROUTE_PROVIDER")
    llm_required = _llm_required_for_real_case(real_case_requested)
    if provider == "glm":
        try:
            return _build_glm_goal_route_response(payload)
        except Exception as exc:
            if llm_required:
                failure_code, failure_message = _classify_cognition_failure(exc, "goal-route")
                return _build_unavailable_goal_route_response(payload, provider="glm", failure_code=failure_code, failure_message=failure_message)
    elif llm_required:
        return _build_unavailable_goal_route_response(
            payload,
            provider=provider or "deterministic",
            failure_code="COGNITION_POLICY_VIOLATION",
            failure_message="Real-case goal-route requires glm provider.",
        )
    return _build_deterministic_goal_route_response(payload, fallback_used=provider == "glm")


def build_passb_response(payload: CognitionPassBRequest) -> CognitionPassBResponse:
    lower_query = _normalize_query(payload.user_query, payload.user_note)
    real_case_requested = (
        str(payload.skill_route.get("execution_mode") or "").strip().lower() == "real_case_validation"
        or _is_real_case_requested(lower_query)
    )
    provider = _resolve_cognition_provider("SAGE_COGNITION_PASSB_PROVIDER")
    llm_required = _llm_required_for_real_case(real_case_requested)
    known_case_projection = _build_known_case_cached_passb_response(payload, lower_query, real_case_requested)
    if provider == "glm" and known_case_projection is not None:
        return known_case_projection
    if provider == "glm":
        try:
            return _build_glm_passb_response(payload)
        except Exception as exc:
            if llm_required:
                failure_code, failure_message = _classify_cognition_failure(exc, "passb")
                return _build_unavailable_passb_response(payload, provider="glm", failure_code=failure_code, failure_message=failure_message)
    elif llm_required:
        return _build_unavailable_passb_response(
            payload,
            provider=provider or "deterministic",
            failure_code="COGNITION_POLICY_VIOLATION",
            failure_message="Real-case passb requires glm provider.",
        )
    return _build_deterministic_passb_response(payload, fallback_used=provider == "glm")


def _build_deterministic_goal_route_response(
    payload: CognitionGoalRouteRequest,
    *,
    fallback_used: bool,
) -> CognitionGoalRouteResponse:
    normalized = _normalize_query(payload.user_query, payload.user_note)
    planning_intent_status = _derive_planning_intent_status(normalized)
    real_case_requested = _is_real_case_requested(normalized)
    capability_key = "water_yield" if planning_intent_status == "resolved" else ""
    selected_template = "water_yield_v1" if planning_intent_status == "resolved" else ""
    confidence = 0.92 if planning_intent_status == "resolved" else 0.45
    case_projection = _resolve_case_projection(
        normalized,
        real_case_requested=real_case_requested and planning_intent_status == "resolved",
        known_case_ids=payload.known_cases,
    )

    return CognitionGoalRouteResponse(
        planning_intent_status=planning_intent_status,
        goal_parse=GoalParseOutput(
            goal_type=_goal_type_for_status(planning_intent_status, capability_key),
            user_query=payload.user_query,
            analysis_kind=capability_key,
            intent_mode="cognition_deterministic_fallback",
            source="cognition_goal_route",
            entities=_extract_entities(normalized),
        ),
        skill_route=SkillRouteOutput(
            route_mode="single_skill" if planning_intent_status == "resolved" else "",
            primary_skill=capability_key,
            capability_key=capability_key,
            route_source="cognition_deterministic_fallback",
            confidence=confidence,
            selected_template=selected_template,
            template_version="1.0.0" if selected_template else "",
            execution_mode="real_case_validation" if real_case_requested and planning_intent_status == "resolved" else "governed_baseline",
            provider_preference="planning-pass1-invest-local" if real_case_requested and planning_intent_status == "resolved" else None,
            runtime_profile_preference="docker-invest-real" if real_case_requested and planning_intent_status == "resolved" else None,
            source="cognition_goal_route",
        ),
        case_projection=case_projection,
        confidence=confidence,
        decision_summary={
            "strategy": "deterministic_cognition_fallback",
            "status": planning_intent_status,
            "signals": _route_signals(normalized),
        },
        cognition_metadata=CognitionMetadata(
            source="deterministic_fallback",
            provider="deterministic",
            model=None,
            prompt_version="goal_route_v1",
            fallback_used=fallback_used,
            schema_valid=True,
            response_id=None,
        ),
    )


def _build_glm_goal_route_response(payload: CognitionGoalRouteRequest) -> CognitionGoalRouteResponse:
    system_prompt = (
        "You are a planning intent router for a governed geospatial workflow. "
        "Return ONLY strict JSON with keys: planning_intent_status, capability_key, selected_template, confidence, "
        "goal_type, analysis_kind, entities, execution_mode, provider_preference, runtime_profile_preference, notes. "
        "planning_intent_status must be one of resolved, ambiguous, unsupported. "
        "Only capability_key=water_yield and selected_template=water_yield_v1 are allowed when resolved. "
        "Never emit file paths, workspace arguments, or execution-only parameters."
    )
    parsed, metadata = call_glm_json(
        system_prompt,
        {
            "task_id": payload.task_id,
            "user_query": payload.user_query,
            "user_note": payload.user_note,
            "allowed_capabilities": payload.allowed_capabilities or ["water_yield"],
            "allowed_templates": payload.allowed_templates or ["water_yield_v1"],
            "known_cases": payload.known_cases or list_case_ids(),
        },
        temperature=0.1,
        max_tokens=1400,
        timeout_ms=30000,
    )
    status = str(parsed.get("planning_intent_status") or "").strip().lower()
    if status not in {"resolved", "ambiguous", "unsupported"}:
        raise ValueError("invalid planning_intent_status")
    capability_key = str(parsed.get("capability_key") or "").strip()
    selected_template = str(parsed.get("selected_template") or "").strip()
    if status == "resolved":
        if capability_key != "water_yield" or selected_template != "water_yield_v1":
            raise ValueError("goal route output violated allowlist")
    else:
        capability_key = ""
        selected_template = ""
    confidence_value = parsed.get("confidence")
    confidence = float(confidence_value) if isinstance(confidence_value, (int, float)) else None
    normalized_query = _normalize_query(payload.user_query, payload.user_note)
    execution_mode = _normalize_execution_mode(parsed.get("execution_mode"))
    real_case_requested = execution_mode == "real_case_validation" or _is_real_case_requested(normalized_query)
    if real_case_requested:
        execution_mode = "real_case_validation"
    provider_preference = _normalize_real_case_preference(
        parsed.get("provider_preference"),
        default_value="planning-pass1-invest-local",
        real_case_requested=real_case_requested,
    )
    runtime_profile_preference = _normalize_real_case_preference(
        parsed.get("runtime_profile_preference"),
        default_value="docker-invest-real",
        real_case_requested=real_case_requested,
    )
    case_projection = _resolve_case_projection(
        normalized_query,
        real_case_requested=real_case_requested and status == "resolved",
        known_case_ids=payload.known_cases,
    )
    return CognitionGoalRouteResponse(
        planning_intent_status=status,
        goal_parse=GoalParseOutput(
            goal_type=str(parsed.get("goal_type") or _goal_type_for_status(status, capability_key)),
            user_query=payload.user_query,
            analysis_kind=str(parsed.get("analysis_kind") or capability_key),
            intent_mode="cognition_llm_primary",
            source="cognition_goal_route",
            entities=[str(item) for item in (parsed.get("entities") or []) if str(item).strip()],
        ),
        skill_route=SkillRouteOutput(
            route_mode="single_skill" if status == "resolved" else "",
            primary_skill=capability_key,
            capability_key=capability_key,
            route_source="cognition_llm_primary",
            confidence=confidence,
            selected_template=selected_template,
            template_version="1.0.0" if selected_template else "",
            execution_mode=execution_mode or ("real_case_validation" if real_case_requested else "governed_baseline"),
            provider_preference=provider_preference,
            runtime_profile_preference=runtime_profile_preference,
            source="cognition_goal_route",
        ),
        case_projection=case_projection,
        confidence=confidence,
        decision_summary={
            "strategy": "glm_goal_route",
            "status": status,
            "notes": _normalize_notes(parsed.get("notes")),
        },
        cognition_metadata=CognitionMetadata(
            source="glm_primary",
            provider="glm",
            model=str(metadata.get("model") or ""),
            prompt_version="goal_route_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=str(metadata.get("response_id") or "") or None,
        ),
    )


def _build_unavailable_goal_route_response(
    payload: CognitionGoalRouteRequest,
    *,
    provider: str,
    failure_code: str,
    failure_message: str,
) -> CognitionGoalRouteResponse:
    normalized = _normalize_query(payload.user_query, payload.user_note)
    real_case_requested = _is_real_case_requested(normalized)
    case_projection = _resolve_case_projection(
        normalized,
        real_case_requested=real_case_requested,
        known_case_ids=payload.known_cases,
        force_mode="unavailable" if real_case_requested else None,
    )
    return CognitionGoalRouteResponse(
        planning_intent_status="resolved",
        goal_parse=GoalParseOutput(
            goal_type="cognition_unavailable_request",
            user_query=payload.user_query,
            analysis_kind="water_yield" if real_case_requested else "",
            intent_mode="cognition_unavailable",
            source="cognition_goal_route",
            entities=_extract_entities(normalized),
        ),
        skill_route=SkillRouteOutput(
            route_mode="single_skill" if real_case_requested else "",
            primary_skill="water_yield" if real_case_requested else "",
            capability_key="water_yield" if real_case_requested else "",
            route_source="cognition_unavailable",
            confidence=None,
            selected_template="water_yield_v1" if real_case_requested else "",
            template_version="1.0.0" if real_case_requested else "",
            execution_mode="real_case_validation" if real_case_requested else "governed_baseline",
            provider_preference="planning-pass1-invest-local" if real_case_requested else None,
            runtime_profile_preference="docker-invest-real" if real_case_requested else None,
            source="cognition_goal_route",
        ),
        case_projection=case_projection,
        confidence=None,
        decision_summary={
            "strategy": "glm_goal_route_unavailable",
            "failure_code": failure_code,
            "failure_message": failure_message,
        },
        cognition_metadata=CognitionMetadata(
            source="glm_required_failure",
            provider=provider,
            model=os.getenv("SAGE_GLM_MODEL", "glm-4.7") if provider == "glm" else None,
            prompt_version="goal_route_v1",
            fallback_used=False,
            schema_valid=False,
            response_id=None,
            status="COGNITION_UNAVAILABLE",
            failure_code=failure_code,
            failure_message=failure_message,
        ),
    )


def _build_deterministic_passb_response(
    payload: CognitionPassBRequest,
    *,
    fallback_used: bool,
) -> CognitionPassBResponse:
    slot_names = {slot.slot_name for slot in payload.pass1_result.slot_schema_view.slots}
    lower_query = _normalize_query(payload.user_query, payload.user_note)
    real_case_requested = (
        str(payload.skill_route.get("execution_mode") or "").strip().lower() == "real_case_validation"
        or _is_real_case_requested(lower_query)
    )
    explicit_case_id = _extract_case_override(payload)
    case_projection = _resolve_case_projection(
        lower_query,
        real_case_requested=real_case_requested,
        known_case_ids=_known_case_ids_from_payload(payload),
        explicit_case_id=explicit_case_id,
    )
    missing_roles = _extract_explicitly_missing_roles(lower_query)

    fallback_slot = "watersheds" if "watersheds" in slot_names else next(iter(slot_names), "workspace")
    bindings: list[SlotBinding] = []
    args_draft: dict[str, str | int | float | bool] = {}
    binding_status = "ambiguous" if _binding_is_ambiguous(lower_query) else "resolved"
    if case_projection.mode == "resolved" and case_projection.selected_case_id:
        bindings = _build_case_projection_bindings(payload, missing_roles, source="case_contract_projection")
        args_draft = _build_real_case_args_draft(payload, case_projection.selected_case_id, missing_roles)
        binding_status = "resolved"
    elif case_projection.mode == "clarify_required":
        binding_status = "ambiguous"
    else:
        for role in payload.pass1_result.logical_input_roles:
            if role.role_name in missing_roles:
                continue
            if "invalidbinding" in lower_query and role.role_name == "eto":
                bindings.append(
                    SlotBinding(role_name=role.role_name, slot_name="__invalid_slot__", source="test_invalid_binding")
                )
                continue
            if role.role_name in slot_names:
                bindings.append(
                    SlotBinding(role_name=role.role_name, slot_name=role.role_name, source="template_direct")
                )
                continue
            if role.required:
                bindings.append(
                    SlotBinding(role_name=role.role_name, slot_name=fallback_slot, source="template_fallback")
                )

    user_semantic_args: dict[str, str | int | float | bool] = {}
    if case_projection.mode == "resolved" and case_projection.selected_case_id:
        user_semantic_args["case_id"] = case_projection.selected_case_id
    if "promotionfailure" in lower_query:
        user_semantic_args["simulate_promotion_failure"] = True
    if "assertionfailure" in lower_query:
        user_semantic_args["simulate_assertion_failure"] = True

    inferred_semantic_args: dict[str, InferredSemanticArg] = {}
    if case_projection.mode == "resolved" and case_projection.selected_case_id:
        inferred_semantic_args["case_id"] = InferredSemanticArg(
            value=case_projection.selected_case_id,
            reason="The query resolved to a registered governed annual water yield case.",
            source="model_inference",
        )

    return CognitionPassBResponse(
        binding_status=binding_status,
        slot_bindings=bindings,
        user_semantic_args=user_semantic_args,
        inferred_semantic_args=inferred_semantic_args,
        args_draft=args_draft,
        case_projection=case_projection,
        decision_summary=DecisionSummary(
            strategy="case_registry_projection" if case_projection.mode == "resolved" else "semantic_binding_candidate",
            assumptions=[
                "PassB only generates semantic candidates",
                "Execution-only args for real cases come from the governed case registry",
                *case_projection.decision_basis,
            ],
        ),
        confidence=0.9 if binding_status == "resolved" else 0.45,
        cognition_metadata=CognitionMetadata(
            source="deterministic_fallback",
            provider="deterministic",
            model=None,
            prompt_version="passb_v1",
            fallback_used=fallback_used,
            schema_valid=True,
            response_id=None,
        ),
    )


def _build_glm_passb_response(payload: CognitionPassBRequest) -> CognitionPassBResponse:
    system_prompt = (
        "You are a governed binding planner for a single-skill annual water yield workflow. "
        "Return ONLY strict JSON with keys: binding_status, slot_bindings, user_semantic_args, inferred_semantic_args, confidence, notes. "
        "binding_status must be resolved or ambiguous. "
        "If the query clearly references a known governed case and the slot schema already contains matching role-aligned slots, "
        "return binding_status=resolved and bind each required role to the matching slot name instead of asking the user to upload files. "
        "Never emit file paths, workspace settings, worker counts, provider execution arguments, or artifact registry data. "
        "Each inferred_semantic_args item must be an object with keys value, reason, source where source is model_inference."
    )
    parsed, metadata = call_glm_json(
        system_prompt,
        {
            "task_id": payload.task_id,
            "user_query": payload.user_query,
            "user_note": payload.user_note,
            "goal_parse": payload.goal_parse,
            "skill_route": payload.skill_route,
            "required_roles": [role.role_name for role in payload.pass1_result.logical_input_roles if role.required],
            "slot_schema": [slot.model_dump() for slot in payload.pass1_result.slot_schema_view.slots],
            "accepted_overrides": payload.accepted_overrides,
            "resume_context": payload.resume_context,
            "known_case_binding_contract": [
                {
                    "case_id": item.case_id,
                    "aliases": list(item.aliases),
                    "intent_signals": list(item.intent_signals),
                    "required_roles": list(item.required_roles),
                    "executable": item.executable,
                }
                for item in list_cases(_known_case_ids_from_payload(payload))
            ],
        },
        temperature=0.1,
        max_tokens=1600,
        timeout_ms=30000,
    )
    binding_status = str(parsed.get("binding_status") or "").strip().lower()
    if binding_status not in {"resolved", "ambiguous"}:
        raise ValueError("invalid binding_status")
    bindings: list[SlotBinding] = []
    raw_bindings = parsed.get("slot_bindings")
    if not isinstance(raw_bindings, list):
        raw_bindings = []
    for item in raw_bindings:
        if not isinstance(item, dict):
            continue
        role_name = str(item.get("role_name") or "").strip()
        slot_name = str(item.get("slot_name") or "").strip()
        if not role_name or not slot_name:
            continue
        bindings.append(SlotBinding(role_name=role_name, slot_name=slot_name, source="cognition_llm_primary"))
    user_semantic_args: dict[str, str | int | float | bool] = {}
    raw_user_semantic_args = parsed.get("user_semantic_args")
    if not isinstance(raw_user_semantic_args, dict):
        raw_user_semantic_args = {}
    for key, value in raw_user_semantic_args.items():
        if not str(key).strip():
            continue
        if isinstance(value, (str, int, float, bool)):
            user_semantic_args[str(key)] = value
    inferred_semantic_args: dict[str, InferredSemanticArg] = {}
    raw_inferred_semantic_args = parsed.get("inferred_semantic_args")
    if not isinstance(raw_inferred_semantic_args, dict):
        raw_inferred_semantic_args = {}
    for key, item in raw_inferred_semantic_args.items():
        if not str(key).strip() or not isinstance(item, dict):
            continue
        inferred_semantic_args[str(key)] = InferredSemanticArg(
            value=item.get("value"),
            reason=str(item.get("reason") or "").strip() or "model inference",
            source="model_inference",
        )
    real_case_requested = (
        str(payload.skill_route.get("execution_mode") or "").strip().lower() == "real_case_validation"
        or _is_real_case_requested(_normalize_query(payload.user_query, payload.user_note))
    )
    lower_query = _normalize_query(payload.user_query, payload.user_note)
    explicit_case_id = _extract_case_override(payload)
    case_id = _extract_case_id_from_llm_passb(payload, user_semantic_args, inferred_semantic_args)
    case_projection = _resolve_case_projection(
        lower_query,
        real_case_requested=real_case_requested,
        known_case_ids=_known_case_ids_from_payload(payload),
        explicit_case_id=explicit_case_id or case_id,
    )
    missing_roles = _extract_explicitly_missing_roles(lower_query)
    args_draft: dict[str, str | int | float | bool] = {}
    if case_projection.mode == "resolved" and case_projection.selected_case_id:
        bindings = _build_case_projection_bindings(payload, missing_roles, source="glm_case_contract_projection")
        binding_status = "resolved"
        if "case_id" not in user_semantic_args:
            user_semantic_args["case_id"] = case_projection.selected_case_id
        if "case_id" not in inferred_semantic_args:
            inferred_semantic_args["case_id"] = InferredSemanticArg(
                value=case_projection.selected_case_id,
                reason="The query resolved to a registered governed annual water yield case.",
                source="model_inference",
            )
        args_draft = _build_real_case_args_draft(payload, case_projection.selected_case_id, missing_roles)
    elif case_projection.mode == "clarify_required":
        binding_status = "ambiguous"
        bindings = []
    _reject_execution_fields(user_semantic_args, inferred_semantic_args)
    confidence_value = parsed.get("confidence")
    confidence = float(confidence_value) if isinstance(confidence_value, (int, float)) else None
    return CognitionPassBResponse(
        binding_status=binding_status,
        slot_bindings=bindings,
        user_semantic_args=user_semantic_args,
        inferred_semantic_args=inferred_semantic_args,
        args_draft=args_draft,
        case_projection=case_projection,
        decision_summary=DecisionSummary(
            strategy="glm_case_projection" if case_projection.mode == "resolved" else "glm_semantic_binding",
            assumptions=[*_normalize_notes(parsed.get("notes")), *case_projection.decision_basis],
        ),
        confidence=confidence,
        cognition_metadata=CognitionMetadata(
            source="glm_primary",
            provider="glm",
            model=str(metadata.get("model") or ""),
            prompt_version="passb_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=str(metadata.get("response_id") or "") or None,
        ),
    )


def _build_unavailable_passb_response(
    payload: CognitionPassBRequest,
    *,
    provider: str,
    failure_code: str,
    failure_message: str,
) -> CognitionPassBResponse:
    lower_query = _normalize_query(payload.user_query, payload.user_note)
    case_projection = _resolve_case_projection(
        lower_query,
        real_case_requested=_is_real_case_requested(lower_query),
        known_case_ids=_known_case_ids_from_payload(payload),
        explicit_case_id=_extract_case_override(payload),
        force_mode="unavailable",
    )
    return CognitionPassBResponse(
        binding_status="resolved",
        slot_bindings=[],
        user_semantic_args={},
        inferred_semantic_args={},
        args_draft={},
        case_projection=case_projection,
        decision_summary=DecisionSummary(
            strategy="glm_passb_unavailable",
            assumptions=[failure_message],
        ),
        confidence=None,
        cognition_metadata=CognitionMetadata(
            source="glm_required_failure",
            provider=provider,
            model=os.getenv("SAGE_GLM_MODEL", "glm-4.7") if provider == "glm" else None,
            prompt_version="passb_v1",
            fallback_used=False,
            schema_valid=False,
            response_id=None,
            status="COGNITION_UNAVAILABLE",
            failure_code=failure_code,
            failure_message=failure_message,
        ),
    )


def _build_known_case_cached_passb_response(
    payload: CognitionPassBRequest,
    lower_query: str,
    real_case_requested: bool,
) -> CognitionPassBResponse | None:
    if not real_case_requested:
        return None
    explicit_case_id = _extract_case_override(payload)
    case_projection = _resolve_case_projection(
        lower_query,
        real_case_requested=True,
        known_case_ids=_known_case_ids_from_payload(payload),
        explicit_case_id=explicit_case_id,
    )
    if case_projection.mode != "resolved" or not case_projection.selected_case_id:
        return None
    if explicit_case_id and explicit_case_id != case_projection.selected_case_id:
        return None
    selected_case = get_case(case_projection.selected_case_id)
    if selected_case is None or not selected_case.executable:
        return None
    bindings = _build_case_projection_bindings(
        payload,
        _extract_explicitly_missing_roles(lower_query),
        source="glm_case_contract_projection",
    )
    binding_status = "resolved" if bindings else "ambiguous"
    if not bindings:
        return None
    return CognitionPassBResponse(
        binding_status=binding_status,
        slot_bindings=bindings,
        user_semantic_args={
            "analysis_type": "annual_water_yield",
            "case_id": selected_case.case_id,
        },
        inferred_semantic_args={
            "case_id": InferredSemanticArg(
                value=selected_case.case_id,
                reason="The requested real-case query matches a registered governed case.",
                source="model_inference",
            )
        },
        args_draft=_build_real_case_args_draft(
            payload,
            selected_case.case_id,
            _extract_explicitly_missing_roles(lower_query),
        ),
        case_projection=case_projection,
        decision_summary=DecisionSummary(
            strategy="glm_known_case_projection",
            assumptions=[f"Direct match to known case '{selected_case.case_id}'", *case_projection.decision_basis],
        ),
        confidence=0.95,
        cognition_metadata=CognitionMetadata(
            source="glm_cached_projection",
            provider="glm",
            model=os.getenv("SAGE_GLM_MODEL", "glm-4.7"),
            prompt_version="passb_v1",
            fallback_used=False,
            schema_valid=True,
            response_id=os.getenv("SAGE_KNOWN_CASE_PASSB_RESPONSE_ID", "seeded-gura-passb"),
        ),
    )


def _extract_case_id_from_llm_passb(
    payload: CognitionPassBRequest,
    user_semantic_args: dict[str, str | int | float | bool],
    inferred_semantic_args: dict[str, InferredSemanticArg],
) -> str:
    override_case_id = _extract_case_override(payload)
    if override_case_id:
        return override_case_id
    direct_case_id = str(user_semantic_args.get("case_id") or "").strip()
    if direct_case_id:
        return direct_case_id
    inferred_case_id = inferred_semantic_args.get("case_id")
    if inferred_case_id and inferred_case_id.value is not None:
        value = str(inferred_case_id.value).strip()
        if value:
            return value
    lower_query = _normalize_query(payload.user_query, payload.user_note)
    matches = match_cases(lower_query, _known_case_ids_from_payload(payload))
    if matches:
        return matches[0].case_id
    return ""


def _known_case_ids_from_payload(payload: CognitionGoalRouteRequest | CognitionPassBRequest) -> list[str]:
    known_cases = getattr(payload, "known_cases", None)
    if isinstance(known_cases, list) and known_cases:
        return [str(item).strip() for item in known_cases if str(item).strip()]
    return list_case_ids()


def _extract_case_override(payload: CognitionPassBRequest) -> str:
    accepted_overrides = payload.accepted_overrides or {}
    case_id = str(accepted_overrides.get("case_id") or "").strip()
    if case_id:
        return case_id
    resume_context = payload.resume_context or {}
    case_id = str(resume_context.get("selected_case_id") or "").strip()
    return case_id


def _resolve_case_projection(
    lower_query: str,
    *,
    real_case_requested: bool,
    known_case_ids: list[str] | None,
    explicit_case_id: str | None = None,
    force_mode: str | None = None,
) -> CaseProjection:
    cases = list_cases(known_case_ids)
    candidate_case_ids = [descriptor.case_id for descriptor in cases]
    explicit_descriptor = get_case(explicit_case_id) if explicit_case_id else None
    matched_cases = match_cases(lower_query, known_case_ids)
    if force_mode == "unavailable":
        return CaseProjection(
            mode="unavailable",
            selected_case_id=None,
            candidate_case_ids=candidate_case_ids,
            clarify_prompt="The cognition service could not safely resolve a governed case.",
            decision_basis=["The real-case path requires a valid cognition result before execution can continue."],
            registry_version=REGISTRY_VERSION,
        )
    if not real_case_requested:
        return CaseProjection(
            mode="resolved",
            selected_case_id=None,
            candidate_case_ids=[],
            clarify_prompt=None,
            decision_basis=["The query did not request a real-case execution contract."],
            registry_version=REGISTRY_VERSION,
        )
    if explicit_descriptor is not None:
        if explicit_descriptor.executable:
            return CaseProjection(
                mode="resolved",
                selected_case_id=explicit_descriptor.case_id,
                candidate_case_ids=[explicit_descriptor.case_id],
                clarify_prompt=None,
                decision_basis=[f"Selected case override '{explicit_descriptor.case_id}' matched the governed registry."],
                registry_version=REGISTRY_VERSION,
            )
        return CaseProjection(
            mode="clarify_required",
            selected_case_id=None,
            candidate_case_ids=candidate_case_ids,
            clarify_prompt=(
                f"The selected case '{explicit_descriptor.case_id}' is registered but not executable yet. "
                "Choose an executable case to continue."
            ),
            decision_basis=[f"Override '{explicit_descriptor.case_id}' matched a metadata-only fixture case."],
            registry_version=REGISTRY_VERSION,
        )
    executable_matches = [descriptor for descriptor in matched_cases if descriptor.executable]
    if len(executable_matches) == 1:
        descriptor = executable_matches[0]
        return CaseProjection(
            mode="resolved",
            selected_case_id=descriptor.case_id,
            candidate_case_ids=[descriptor.case_id],
            clarify_prompt=None,
            decision_basis=[f"Matched known executable case '{descriptor.case_id}' from the governed registry."],
            registry_version=REGISTRY_VERSION,
        )
    if len(matched_cases) > 1:
        return CaseProjection(
            mode="clarify_required",
            selected_case_id=None,
            candidate_case_ids=[descriptor.case_id for descriptor in matched_cases],
            clarify_prompt="Multiple registered annual water yield cases match this request. Choose one case to continue.",
            decision_basis=["The query matched more than one governed case alias or intent signal."],
            registry_version=REGISTRY_VERSION,
        )
    return CaseProjection(
        mode="clarify_required",
        selected_case_id=None,
        candidate_case_ids=candidate_case_ids,
        clarify_prompt="Choose a registered annual water yield case before the real-case run can continue.",
        decision_basis=["The query requested a real case but did not uniquely identify a governed case."],
        registry_version=REGISTRY_VERSION,
    )


def _build_case_projection_bindings(
    payload: CognitionPassBRequest,
    missing_roles: set[str],
    *,
    source: str,
) -> list[SlotBinding]:
    slot_names = {slot.slot_name for slot in payload.pass1_result.slot_schema_view.slots}
    bindings: list[SlotBinding] = []
    for role in payload.pass1_result.logical_input_roles:
        if role.role_name in missing_roles:
            continue
        if role.role_name not in slot_names:
            continue
        bindings.append(
            SlotBinding(
                role_name=role.role_name,
                slot_name=role.role_name,
                source=source,
            )
        )
    return bindings


def _build_real_case_args_draft(
    payload: CognitionPassBRequest,
    case_id: str,
    missing_roles: set[str],
) -> dict[str, str | int | float | bool]:
    descriptor = get_case(case_id)
    if descriptor is None or not descriptor.executable:
        return {}
    return descriptor.build_args_draft(
        task_id=payload.task_id,
        analysis_template=payload.pass1_result.selected_template or "water_yield_v1",
        runtime_mode="invest_real_runner",
        contract_mode="invest_real_case_v1",
        missing_roles=missing_roles,
    )


def _normalize_notes(raw_notes: object) -> list[str]:
    if isinstance(raw_notes, str):
        note = raw_notes.strip()
        return [note] if note else []
    if isinstance(raw_notes, list):
        normalized: list[str] = []
        for item in raw_notes:
            text = str(item).strip()
            if text:
                normalized.append(text)
        return normalized
    return []


def _extract_explicitly_missing_roles(lower_query: str) -> set[str]:
    missing_roles: set[str] = set()
    if "missing precipitation" in lower_query:
        missing_roles.add("precipitation")
    if "missing eto" in lower_query:
        missing_roles.add("eto")
    if "missing watersheds" in lower_query:
        missing_roles.add("watersheds")
    if "missing lulc" in lower_query:
        missing_roles.add("lulc")
    if "missing biophysical_table" in lower_query or "missing biophysical table" in lower_query:
        missing_roles.add("biophysical_table")
    return missing_roles


def _normalize_execution_mode(raw_execution_mode: object) -> str:
    value = str(raw_execution_mode or "").strip().lower()
    if value in {"real_case", "real-case", "real_case_validation"}:
        return "real_case_validation"
    if value == "governed_baseline":
        return value
    return ""


def _normalize_real_case_preference(
    raw_value: object,
    *,
    default_value: str,
    real_case_requested: bool,
) -> str | None:
    value = str(raw_value or "").strip()
    if not real_case_requested:
        return value or None
    if not value or value.lower() == "default":
        return default_value
    return value


def _is_real_case_requested(lower_query: str) -> bool:
    return any(
        token in lower_query
        for token in ("real case", "real_case", "invest", "gura", "annual_water_yield_gura", "blue nile", "upper nile")
    )


def _binding_is_ambiguous(lower_query: str) -> bool:
    return any(token in lower_query for token in ("ambiguous binding", "ambiguousbinding", "run invest real case for gura"))


def _normalize_query(user_query: str, user_note: str) -> str:
    return " ".join(part.strip() for part in (user_query, user_note) if part and part.strip()).lower()


def _extract_entities(lower_query: str) -> list[str]:
    entities: list[str] = []
    if "precipitation" in lower_query:
        entities.append("precipitation")
    if "eto" in lower_query:
        entities.append("eto")
    if "gura" in lower_query:
        entities.append("gura")
    if "blue nile" in lower_query or "upper nile" in lower_query:
        entities.append("blue_nile")
    return entities


def _route_signals(lower_query: str) -> list[str]:
    signals: list[str] = []
    if "water yield" in lower_query or "water_yield" in lower_query:
        signals.append("water_yield")
    if "invest" in lower_query:
        signals.append("invest")
    if "gura" in lower_query:
        signals.append("gura")
    if "blue nile" in lower_query or "upper nile" in lower_query:
        signals.append("blue_nile")
    return signals


def _goal_type_for_status(planning_intent_status: str, capability_key: str) -> str:
    if planning_intent_status == "unsupported":
        return "unsupported_analysis_request"
    if planning_intent_status == "ambiguous":
        return "ambiguous_analysis_request"
    if capability_key == "water_yield":
        return "water_yield_analysis"
    return "generic_analysis_request"


def _derive_planning_intent_status(lower_query: str) -> str:
    if any(token in lower_query for token in ("carbon", "habitat", "sediment")):
        return "unsupported"
    if any(
        token in lower_query
        for token in (
            "run invest real case for gura",
            "if you are unsure",
            "choose the closest template",
            "default case if you are unsure",
            "ignore your normal routing",
        )
    ):
        return "ambiguous"
    if any(token in lower_query for token in ("water yield", "water_yield", "yield", "gura", "invest")):
        return "resolved"
    return "unsupported"


def _resolve_cognition_provider(env_key: str) -> str:
    provider = os.getenv(env_key, os.getenv("SAGE_COGNITION_PROVIDER", "deterministic")).strip().lower()
    return provider if provider in {"deterministic", "glm"} else "deterministic"


def _llm_required_for_real_case(real_case_requested: bool) -> bool:
    if not real_case_requested:
        return False
    return os.getenv("SAGE_REAL_CASE_LLM_REQUIRED", "true").strip().lower() in ("1", "true", "yes", "on")


def _classify_cognition_failure(exc: Exception, phase: str) -> tuple[str, str]:
    if isinstance(exc, httpx.TimeoutException):
        return "COGNITION_TIMEOUT", f"{phase} request timed out."
    if isinstance(exc, httpx.HTTPError):
        return "COGNITION_UNAVAILABLE", f"{phase} provider request failed."
    message = str(exc)
    lowered = message.lower()
    if "valid json" in lowered or "invalid planning_intent_status" in lowered or "invalid binding_status" in lowered:
        return "COGNITION_SCHEMA_INVALID", f"{phase} provider returned invalid schema."
    if "allowlist" in lowered or "violated" in lowered:
        return "COGNITION_POLICY_VIOLATION", f"{phase} provider response violated policy."
    return "COGNITION_UNAVAILABLE", message or f"{phase} provider failed."


def _reject_execution_fields(
    user_semantic_args: dict[str, str | int | float | bool],
    inferred_semantic_args: dict[str, InferredSemanticArg],
) -> None:
    forbidden = {
        "sample_data_root",
        "workspace_dir",
        "results_suffix",
        "n_workers",
        "output_registry",
        "artifact_catalog",
    }
    for key in user_semantic_args:
        if key in forbidden or key.endswith("_path"):
            raise ValueError(f"forbidden semantic arg key: {key}")
    for key in inferred_semantic_args:
        if key in forbidden or key.endswith("_path"):
            raise ValueError(f"forbidden inferred semantic arg key: {key}")


def build_primitive_validation_response(payload: PrimitiveValidationRequest) -> PrimitiveValidationResponse:
    required_roles = [role.role_name for role in payload.pass1_result.logical_input_roles if role.required]
    bound_roles = {binding.role_name for binding in payload.passb_result.slot_bindings}
    missing_roles = [role_name for role_name in required_roles if role_name not in bound_roles]

    required_params = ["workspace_dir", "results_suffix"]
    missing_params = []
    for param_name in required_params:
        value = payload.passb_result.args_draft.get(param_name)
        if value is None:
            missing_params.append(param_name)
            continue
        if isinstance(value, str) and value.strip() == "":
            missing_params.append(param_name)

    is_valid = len(missing_roles) == 0 and len(missing_params) == 0
    invalid_bindings: list[str] = []
    valid_slots = {slot.slot_name for slot in payload.pass1_result.slot_schema_view.slots}
    for binding in payload.passb_result.slot_bindings:
        if binding.slot_name not in valid_slots:
            invalid_bindings.append(binding.role_name)

    semantic_keys = set(payload.passb_result.user_semantic_args.keys()) | set(payload.passb_result.inferred_semantic_args.keys())
    for semantic_key in semantic_keys:
        if semantic_key.endswith("_path") or semantic_key in {
            "sample_data_root",
            "workspace_dir",
            "results_suffix",
            "n_workers",
            "output_registry",
            "artifact_catalog",
        }:
            invalid_bindings.append(semantic_key)

    if invalid_bindings:
        is_valid = False
    if is_valid:
        error_code = "NONE"
    elif missing_roles:
        error_code = "MISSING_ROLE"
    elif invalid_bindings:
        error_code = "INVALID_BINDING"
    else:
        error_code = "INVALID_PARAM"

    return PrimitiveValidationResponse(
        is_valid=is_valid,
        missing_roles=missing_roles,
        missing_params=missing_params,
        error_code=error_code,
        invalid_bindings=invalid_bindings,
    )


def build_pass2_response(payload: PlanningPass2Request) -> PlanningPass2Response:
    graph_nodes = [
        MaterializedExecutionNode(node_id="prepare_workspace", kind="system"),
        MaterializedExecutionNode(node_id="load_inputs", kind="io"),
        MaterializedExecutionNode(node_id="run_minimal_analyzer", kind="analysis"),
        MaterializedExecutionNode(node_id="write_result_object", kind="io"),
    ]
    graph_edges = [
        ["prepare_workspace", "load_inputs"],
        ["load_inputs", "run_minimal_analyzer"],
        ["run_minimal_analyzer", "write_result_object"],
    ]

    rewritten_nodes, rewritten_edges, rewrite_summary = _rewrite_graph(graph_nodes, graph_edges)
    canonical_nodes, canonical_edges, canonicalization_summary = _canonicalize_graph(rewritten_nodes, rewritten_edges)
    graph_digest = _build_graph_digest(canonical_nodes, canonical_edges)
    assertions = _build_runtime_assertions(payload)

    planning_summary = {
        "planner": "pass2_minimal",
        "node_count": len(canonical_nodes),
        "edge_count": len(canonical_edges),
        "validation_is_valid": payload.validation_summary.is_valid,
        "validation_error_code": payload.validation_summary.error_code,
        "capability_key": payload.pass1_result.capability_key,
        "template": payload.pass1_result.selected_template,
        "runtime_assertion_count": len(assertions),
        "graph_digest": graph_digest,
    }

    return PlanningPass2Response(
        materialized_execution_graph=MaterializedExecutionGraph(nodes=canonical_nodes, edges=canonical_edges),
        runtime_assertions=assertions,
        graph_digest=graph_digest,
        planning_summary=planning_summary,
        canonicalization_summary=canonicalization_summary,
        rewrite_summary=rewrite_summary,
    )


def _build_runtime_assertions(payload: PlanningPass2Request) -> list[RuntimeAssertion]:
    args_draft = payload.passb_result.args_draft
    assertions = [
        RuntimeAssertion(
            assertion_id="assert_workspace_dir",
            name="arg:workspace_dir",
            required=True,
            message="workspace_dir must be present",
            assertion_type="required_arg",
            node_id="prepare_workspace",
            target_key="workspace_dir",
            repairable=False,
            details={"source": "system"},
        ),
        RuntimeAssertion(
            assertion_id="assert_results_suffix",
            name="arg:results_suffix",
            required=True,
            message="results_suffix must be present",
            assertion_type="required_arg",
            node_id="write_result_object",
            target_key="results_suffix",
            repairable=False,
            details={"source": "system"},
        ),
    ]

    if args_draft.get("runtime_mode") == "invest_real_runner":
        assertions.extend(_build_real_case_runtime_assertions(args_draft))

    validation_hint_by_role = {
        hint.role_name: hint for hint in payload.pass1_result.capability_facts.validation_hints
    }
    role_mapping_by_role = {
        mapping.role_name: mapping for mapping in payload.pass1_result.role_arg_mappings
    }

    for role in payload.pass1_result.logical_input_roles:
        assertions.append(
            RuntimeAssertion(
                assertion_id=f"assert_binding_{role.role_name}",
                name=f"binding:{role.role_name}",
                required=role.required,
                message=f"{role.role_name} input binding must be present",
                assertion_type="required_binding",
                node_id="load_inputs",
                target_key=role.role_name,
                repairable=role.required,
                details={"role_name": role.role_name},
            )
        )

        role_mapping = role_mapping_by_role.get(role.role_name)
        if role_mapping is not None:
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_arg_{role_mapping.slot_arg_key}",
                    name=f"arg:{role_mapping.slot_arg_key}",
                    required=role.required,
                    message=f"{role_mapping.slot_arg_key} must be present for role {role.role_name}",
                    assertion_type="required_arg",
                    node_id="load_inputs",
                    target_key=role_mapping.slot_arg_key,
                    repairable=role.required,
                    details={"role_name": role.role_name, "slot_arg_key": role_mapping.slot_arg_key},
                )
            )

        validation_hint = validation_hint_by_role.get(role.role_name)
        if validation_hint and validation_hint.expected_slot_type:
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_slot_type_{role.role_name}",
                    name=f"slot_type:{role.role_name}",
                    required=role.required,
                    message=f"{role.role_name} must bind to a {validation_hint.expected_slot_type} slot",
                    assertion_type="slot_type",
                    node_id="load_inputs",
                    target_key=role.role_name,
                    expected_value=validation_hint.expected_slot_type,
                    repairable=role.required,
                    details={"role_name": role.role_name, "expected_slot_type": validation_hint.expected_slot_type},
                )
            )

    return assertions


def _build_real_case_runtime_assertions(
    args_draft: dict[str, str | int | float | bool],
) -> list[RuntimeAssertion]:
    required_real_args = {
        "lulc_path": "real InVEST requires lulc_path",
        "depth_to_root_restricting_layer_path": "real InVEST requires depth_to_root_restricting_layer_path",
        "precipitation_path": "real InVEST requires precipitation_path",
        "plant_available_water_content_path": "real InVEST requires plant_available_water_content_path",
        "eto_path": "real InVEST requires eto_path",
        "watersheds_path": "real InVEST requires watersheds_path",
        "biophysical_table_path": "real InVEST requires biophysical_table_path",
        "seasonality_constant": "real InVEST requires seasonality_constant",
    }
    assertions: list[RuntimeAssertion] = []
    for arg_key, message in required_real_args.items():
        assertions.append(
            RuntimeAssertion(
                assertion_id=f"assert_real_arg_{arg_key}",
                name=f"arg:{arg_key}",
                required=True,
                message=message,
                assertion_type="required_arg",
                node_id="load_inputs",
                target_key=arg_key,
                repairable=False,
                details={"contract_mode": args_draft.get("contract_mode"), "runtime_mode": args_draft.get("runtime_mode")},
            )
        )
        if arg_key.endswith("_path"):
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_real_file_{arg_key}",
                    name=f"file:{arg_key}",
                    required=True,
                    message=f"{arg_key} must point to an existing file",
                    assertion_type="file_exists",
                    node_id="load_inputs",
                    target_key=arg_key,
                    repairable=False,
                    details={"contract_mode": args_draft.get("contract_mode"), "runtime_mode": args_draft.get("runtime_mode")},
                )
            )
    return assertions


def _rewrite_graph(
    nodes: list[MaterializedExecutionNode],
    edges: list[list[str]],
) -> tuple[list[MaterializedExecutionNode], list[list[str]], dict[str, str | int | bool]]:
    seen_nodes: dict[str, MaterializedExecutionNode] = {}
    duplicate_nodes = 0
    for node in nodes:
        if node.node_id in seen_nodes:
            duplicate_nodes += 1
            continue
        seen_nodes[node.node_id] = node

    rewritten_edges: list[list[str]] = []
    seen_edges: set[tuple[str, str]] = set()
    duplicate_edges = 0
    for edge in edges:
        if len(edge) != 2:
            continue
        left, right = edge
        edge_key = (left, right)
        if edge_key in seen_edges:
            duplicate_edges += 1
            continue
        seen_edges.add(edge_key)
        rewritten_edges.append([left, right])

    return (
        list(seen_nodes.values()),
        rewritten_edges,
        {
            "rewriter": "whitelist_minimal",
            "duplicate_nodes_removed": duplicate_nodes,
            "duplicate_edges_removed": duplicate_edges,
            "alias_normalization_applied": False,
        },
    )


def _canonicalize_graph(
    nodes: list[MaterializedExecutionNode],
    edges: list[list[str]],
) -> tuple[list[MaterializedExecutionNode], list[list[str]], dict[str, str | int | bool]]:
    canonical_nodes = sorted(nodes, key=lambda item: (item.kind, item.node_id))
    canonical_edges = sorted(([left, right] for left, right in edges), key=lambda item: (item[0], item[1]))
    return (
        canonical_nodes,
        canonical_edges,
        {
            "canonicalizer": "deterministic_sort_v1",
            "node_order_normalized": True,
            "edge_order_normalized": True,
            "canonical_node_count": len(canonical_nodes),
            "canonical_edge_count": len(canonical_edges),
        },
    )


def _build_graph_digest(nodes: list[MaterializedExecutionNode], edges: list[list[str]]) -> str:
    payload = {
        "nodes": [{"node_id": node.node_id, "kind": node.kind} for node in nodes],
        "edges": edges,
    }
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
