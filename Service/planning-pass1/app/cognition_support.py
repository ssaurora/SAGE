from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import time
from pathlib import Path
from typing import Any

import httpx


DEBUG_COGNITION = os.getenv("SAGE_COGNITION_DEBUG", "false").strip().lower() in ("1", "true", "yes", "on")
DEBUG_MAX_CHARS = int(os.getenv("SAGE_COGNITION_DEBUG_MAX_CHARS", "2000"))
GLM_CACHE_ENABLED = os.getenv("SAGE_GLM_CACHE_ENABLED", "true").strip().lower() in ("1", "true", "yes", "on")
GLM_CACHE_DIR = Path(os.getenv("SAGE_GLM_CACHE_DIR", "/workspace/cognition-cache"))
GLM_CACHE_TTL_SECONDS = int(os.getenv("SAGE_GLM_CACHE_TTL_SECONDS", "604800"))
GLM_CACHE_PREFER = os.getenv("SAGE_GLM_CACHE_PREFER", "true").strip().lower() in ("1", "true", "yes", "on")
VOLATILE_CACHE_KEYS = {
    "task_id",
    "state_version",
    "job_id",
    "run_id",
    "job_run_id",
    "result_id",
    "workspace_id",
    "artifact_id",
    "manifest_id",
    "resume_request_id",
    "attempt_no",
    "node_id",
    "assertion_id",
    "response_id",
    "confidence",
    "decision_summary",
    "cognition_metadata",
    "failure_code",
    "failure_message",
    "workspace_dir",
    "absolute_path",
    "relative_path",
    "created_at",
    "updated_at",
}


def debug_log(message: str) -> None:
    if DEBUG_COGNITION:
        print(f"[cognition-debug] {message}", flush=True)


def truncate_text(text: str) -> str:
    if len(text) <= DEBUG_MAX_CHARS:
        return text
    return f"{text[:DEBUG_MAX_CHARS]}...<truncated>"


def call_glm_json(
    system_prompt: str,
    user_payload: dict[str, Any],
    *,
    temperature: float = 0.1,
    max_tokens: int = 900,
    timeout_ms: int | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    api_base = os.getenv("SAGE_GLM_API_BASE", "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
    chat_path = os.getenv("SAGE_GLM_CHAT_PATH", "/chat/completions")
    model = os.getenv("SAGE_GLM_MODEL", "glm-4.7")
    api_key = os.getenv("SAGE_GLM_API_KEY", "").strip()
    auth_mode = os.getenv("SAGE_GLM_AUTH_MODE", "jwt").strip().lower()
    effective_timeout_ms = timeout_ms if timeout_ms is not None else int(os.getenv("SAGE_GLM_TIMEOUT_MS", "8000"))
    exp_seconds = int(os.getenv("SAGE_GLM_JWT_EXP_SECONDS", "3600"))
    retry_count = int(os.getenv("SAGE_GLM_RETRY_COUNT", "1"))

    if not api_key:
        raise ValueError("SAGE_GLM_API_KEY is required for glm provider")

    auth_token = build_glm_auth_token(api_key, auth_mode, exp_seconds)
    headers = {
        "Authorization": f"Bearer {auth_token}",
        "Content-Type": "application/json",
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
    cache_key = build_glm_cache_key(
        system_prompt,
        user_payload,
        model=model,
        temperature=temperature,
        max_tokens=max_tokens,
    )
    cached_response = load_cached_glm_response(cache_key)
    if cached_response is not None and GLM_CACHE_PREFER:
        debug_log("Using cached GLM response before live request.")
        data = cached_response
    else:
        last_timeout: httpx.TimeoutException | None = None
        with httpx.Client(timeout=effective_timeout_ms / 1000) as client:
            for attempt in range(retry_count + 1):
                try:
                    response = client.post(url, headers=headers, json=body)
                    response.raise_for_status()
                    data = response.json()
                    cache_glm_response(cache_key, data)
                    break
                except httpx.TimeoutException as exc:
                    last_timeout = exc
                    if attempt >= retry_count:
                        cached = load_cached_glm_response(cache_key)
                        if cached is not None:
                            debug_log("Using cached GLM response after timeout.")
                            data = cached
                            break
                        raise
                    debug_log(f"GLM request timed out on attempt {attempt + 1}; retrying.")
                except httpx.HTTPStatusError as exc:
                    cached = load_cached_glm_response(cache_key)
                    if cached is not None:
                        debug_log(f"Using cached GLM response after HTTP {exc.response.status_code}.")
                        data = cached
                        break
                    raise
                except httpx.HTTPError:
                    cached = load_cached_glm_response(cache_key)
                    if cached is not None:
                        debug_log("Using cached GLM response after transport failure.")
                        data = cached
                        break
                    raise
            else:
                raise last_timeout if last_timeout is not None else RuntimeError("GLM request failed without response")

    debug_log(f"GLM response JSON: {truncate_text(json.dumps(data, ensure_ascii=False))}")
    content = extract_llm_content(data)
    debug_log(f"GLM message content: {truncate_text(content)}")
    parsed = parse_llm_json(content)
    metadata = {
        "provider": "glm",
        "model": model,
        "response_id": data.get("id"),
    }
    return parsed, metadata


def build_glm_cache_key(
    system_prompt: str,
    user_payload: dict[str, Any],
    *,
    model: str,
    temperature: float,
    max_tokens: int,
) -> str:
    normalized_payload = normalize_cache_payload(user_payload)
    serialized = json.dumps(
        {
            "system_prompt": system_prompt,
            "user_payload": normalized_payload,
            "model": model,
            "temperature": temperature,
            "max_tokens": max_tokens,
        },
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()


def normalize_cache_payload(value: Any) -> Any:
    if isinstance(value, dict):
        normalized: dict[str, Any] = {}
        for key in sorted(value.keys()):
            if key in VOLATILE_CACHE_KEYS:
                continue
            normalized[key] = normalize_cache_payload(value[key])
        return normalized
    if isinstance(value, list):
        return [normalize_cache_payload(item) for item in value]
    return value


def cache_glm_response(cache_key: str, data: dict[str, Any]) -> None:
    if not GLM_CACHE_ENABLED:
        return
    GLM_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    cache_file = GLM_CACHE_DIR / f"{cache_key}.json"
    cache_file.write_text(
        json.dumps(
            {
                "cached_at": int(time.time()),
                "response": data,
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def load_cached_glm_response(cache_key: str) -> dict[str, Any] | None:
    if not GLM_CACHE_ENABLED:
        return None
    cache_file = GLM_CACHE_DIR / f"{cache_key}.json"
    if not cache_file.exists():
        return None
    try:
        payload = json.loads(cache_file.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    cached_at = int(payload.get("cached_at") or 0)
    if cached_at <= 0 or (time.time() - cached_at) > GLM_CACHE_TTL_SECONDS:
        return None
    response = payload.get("response")
    if not isinstance(response, dict):
        return None
    return response


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
                content = str(message.get("content") or "")
                if content.strip():
                    return content
                reasoning_content = str(message.get("reasoning_content") or "")
                fallback = extract_json_candidate(reasoning_content)
                if fallback:
                    debug_log("Using JSON extracted from reasoning_content because content was empty.")
                    return fallback
        output_text = response.get("output_text")
        if isinstance(output_text, str):
            return output_text
    return ""


def extract_json_candidate(text: str) -> str:
    if not text:
        return ""
    fenced_start = text.rfind("```json")
    if fenced_start != -1:
        fenced_end = text.find("```", fenced_start + 7)
        if fenced_end != -1:
            candidate = text[fenced_start + 7:fenced_end].strip()
            if candidate:
                return candidate
    generic_fence_start = text.rfind("```")
    if generic_fence_start != -1:
        generic_fence_end = text.find("```", generic_fence_start + 3)
        if generic_fence_end != -1:
            candidate = text[generic_fence_start + 3:generic_fence_end].strip()
            if candidate.startswith("{") and candidate.endswith("}"):
                return candidate
    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end > start:
        return text[start:end + 1].strip()
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
    except json.JSONDecodeError as exc:
        debug_log(f"LLM response was not valid JSON: {truncate_text(trimmed)}")
        raise ValueError("LLM response was not valid JSON") from exc
