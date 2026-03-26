from __future__ import annotations

from app.schemas import (
    RepairActionExplanation,
    RepairProposalRequest,
    RepairProposalResponse,
    RequiredUserAction,
)
import base64
import hashlib
import hmac
import json
import os
import time
from typing import Any

import httpx

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
            debug_note = f"LLM fallback: {type(exc).__name__}: {exc}"
            return build_deterministic_repair_proposal(payload, debug_note=debug_note)
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
        user_facing_reason=user_facing_reason,
        resume_hint=resume_hint,
        action_explanations=action_explanations,
        notes=notes,
    )


def build_glm_repair_proposal(payload: RepairProposalRequest) -> RepairProposalResponse:
    api_base = os.getenv("SAGE_GLM_API_BASE", "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
    chat_path = os.getenv("SAGE_GLM_CHAT_PATH", "/chat/completions")
    model = os.getenv("SAGE_GLM_MODEL", "glm-4.7")
    api_key = os.getenv("SAGE_GLM_API_KEY", "").strip()
    auth_mode = os.getenv("SAGE_GLM_AUTH_MODE", "jwt").strip().lower()
    timeout_ms = int(os.getenv("SAGE_GLM_TIMEOUT_MS", "8000"))
    temperature = float(os.getenv("SAGE_GLM_TEMPERATURE", "0.2"))
    max_tokens = int(os.getenv("SAGE_GLM_MAX_TOKENS", "700"))
    exp_seconds = int(os.getenv("SAGE_GLM_JWT_EXP_SECONDS", "3600"))

    if not api_key:
        raise ValueError("SAGE_GLM_API_KEY is required for glm provider")

    auth_token = build_glm_auth_token(api_key, auth_mode, exp_seconds)
    headers = {
        "Authorization": f"Bearer {auth_token}",
        "Content-Type": "application/json",
    }

    system_prompt = (
        "You are a repair assistant. "
        "Return ONLY strict JSON with keys: "
        "user_facing_reason (string), resume_hint (string), "
        "action_explanations (list of {key,message}), notes (list of strings). "
        "Do not include extra keys."
    )

    user_payload = normalize_repair_payload(payload)

    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False)},
        ],
        "temperature": temperature,
        "max_tokens": max_tokens,
    }

    url = f"{api_base}{chat_path}"
    with httpx.Client(timeout=timeout_ms / 1000) as client:
        response = client.post(url, headers=headers, json=body)
        response.raise_for_status()
        try:
            data = response.json()
        except Exception as exc:
            debug_log(f"GLM response was not JSON: {type(exc).__name__}: {exc}")
            debug_log(f"GLM raw response text: {truncate_text(response.text)}")
            raise

    debug_log(f"GLM response JSON: {truncate_text(json.dumps(data, ensure_ascii=False))}")
    content = extract_llm_content(data)
    debug_log(f"GLM message content: {truncate_text(content)}")
    parsed = parse_llm_json(content)

    action_explanations: list[RepairActionExplanation] = []
    for item in parsed.get("action_explanations", []) or []:
        key = str(item.get("key") or "")
        message = str(item.get("message") or "")
        if key or message:
            action_explanations.append(RepairActionExplanation(key=key, message=message))

    notes = [str(note) for note in (parsed.get("notes") or [])]
    if DEBUG_REPAIR:
        notes.append(f"debug_llm_raw: {truncate_text(content)}")
        notes.append(f"debug_llm_model: {model}")

    return RepairProposalResponse(
        user_facing_reason=str(parsed.get("user_facing_reason") or "Task requires additional user actions."),
        resume_hint=str(parsed.get("resume_hint") or "Complete required actions and try resume."),
        action_explanations=action_explanations,
        notes=notes,
    )


def build_glm_auth_token(api_key: str, auth_mode: str, exp_seconds: int) -> str:
    if auth_mode == "bearer":
        return api_key

    if "." not in api_key:
        raise ValueError("GLM API key must be in '<id>.<secret>' form for jwt mode")

    key_id, secret = api_key.split(".", 1)
    now = int(time.time())
    header = {"alg": "HS256", "sign_type": "SIGN", "typ": "JWT"}
    payload = {"api_key": key_id, "exp": now + exp_seconds, "timestamp": now}

    signing_input = f"{b64url_json(header)}.{b64url_json(payload)}"
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("utf-8"), hashlib.sha256).digest()
    return f"{signing_input}.{b64url_bytes(signature)}"


def b64url_json(data: dict[str, Any]) -> str:
    return b64url_bytes(json.dumps(data, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))


def b64url_bytes(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")


def extract_llm_content(response: dict[str, Any]) -> str:
    if isinstance(response, dict):
        choices = response.get("choices")
        if isinstance(choices, list) and choices:
            message = choices[0].get("message") if isinstance(choices[0], dict) else None
            if isinstance(message, dict):
                return str(message.get("content") or "")
        output_text = response.get("output_text")
        if isinstance(output_text, str):
            return output_text
    return ""


def parse_llm_json(content: str) -> dict[str, Any]:
    if not content:
        raise ValueError("LLM response content missing")

    trimmed = content.strip()
    if trimmed.startswith("```") and trimmed.endswith("```"):
        trimmed = trimmed[3:-3].strip()
        if trimmed.lower().startswith("json"):
            trimmed = trimmed[4:].strip()

    try:
        return json.loads(trimmed)
    except json.JSONDecodeError:
        debug_log(f"LLM response was not valid JSON: {truncate_text(trimmed)}")
        raise ValueError("LLM response was not valid JSON")


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
