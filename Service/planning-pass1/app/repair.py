from __future__ import annotations

from app.schemas import RepairActionExplanation, RepairProposalRequest, RepairProposalResponse
import base64
import hashlib
import hmac
import json
import os
import time
from typing import Any

import httpx


def build_repair_proposal_response(payload: RepairProposalRequest) -> RepairProposalResponse:
    provider = os.getenv("SAGE_REPAIR_PROVIDER", "deterministic").strip().lower()
    if provider == "glm":
        try:
            return build_glm_repair_proposal(payload)
        except Exception:
            return build_deterministic_repair_proposal(payload)
    return build_deterministic_repair_proposal(payload)


def build_deterministic_repair_proposal(payload: RepairProposalRequest) -> RepairProposalResponse:
    waiting_context = payload.waiting_context or {}
    validation_summary = payload.validation_summary or {}
    failure_summary = payload.failure_summary or {}

    missing_slots = waiting_context.get("missing_slots") or []
    required_actions = waiting_context.get("required_user_actions") or []
    invalid_bindings = waiting_context.get("invalid_bindings") or []
    failure_code = failure_summary.get("failure_code") or validation_summary.get("error_code") or ""
    user_note = (payload.user_note or "").strip()

    if missing_slots:
        user_facing_reason = "Additional required inputs are missing before the task can continue."
    elif invalid_bindings:
        user_facing_reason = "The current task has binding issues that must be repaired before execution can continue."
    elif failure_code:
        user_facing_reason = f"The task requires review because the latest validation reported {failure_code}."
    else:
        user_facing_reason = "Task requires additional user actions before it can continue."

    resume_hint = waiting_context.get("resume_hint") or "Complete required actions and try resume."

    action_explanations: list[RepairActionExplanation] = []
    for action in required_actions:
        key = str(action.get("key") or "")
        label = str(action.get("label") or key or "required action")
        action_type = str(action.get("action_type") or "action")
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

    user_payload = {
        "waiting_context": payload.waiting_context,
        "validation_summary": payload.validation_summary,
        "failure_summary": payload.failure_summary,
        "user_note": payload.user_note,
    }

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
        data = response.json()

    content = extract_llm_content(data)
    parsed = parse_llm_json(content)

    action_explanations: list[RepairActionExplanation] = []
    for item in parsed.get("action_explanations", []) or []:
        key = str(item.get("key") or "")
        message = str(item.get("message") or "")
        if key or message:
            action_explanations.append(RepairActionExplanation(key=key, message=message))

    return RepairProposalResponse(
        user_facing_reason=str(parsed.get("user_facing_reason") or "Task requires additional user actions."),
        resume_hint=str(parsed.get("resume_hint") or "Complete required actions and try resume."),
        action_explanations=action_explanations,
        notes=[str(note) for note in (parsed.get("notes") or [])],
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
    if trimmed.startswith("```"):
        trimmed = trimmed.strip("`")
        if trimmed.lower().startswith("json"):
            trimmed = trimmed[4:].strip()

    try:
        return json.loads(trimmed)
    except json.JSONDecodeError:
        raise ValueError("LLM response was not valid JSON")
