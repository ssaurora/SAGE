from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import UTC, datetime

from redis import Redis
from redis.exceptions import RedisError


@dataclass(frozen=True)
class RedisRuntimeConfig:
    enabled: bool
    url: str


def load_redis_runtime_config() -> RedisRuntimeConfig:
    enabled = os.getenv("REDIS_ENABLED", "false").strip().lower() in {"1", "true", "yes", "on"}
    url = os.getenv("REDIS_URL", "redis://localhost:6379/0").strip()
    return RedisRuntimeConfig(enabled=enabled, url=url)


class RedisRuntimeCoordinator:
    def __init__(self) -> None:
        self._config = load_redis_runtime_config()
        self._client = Redis.from_url(self._config.url, decode_responses=True) if self._config.enabled else None

    @property
    def enabled(self) -> bool:
        return self._client is not None

    def _set_value(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        if self._client is None:
            return
        try:
            if ttl_seconds is None:
                self._client.set(key, value)
            else:
                self._client.set(key, value, ex=ttl_seconds)
        except RedisError:
            return

    def seed_lease(self, job_id: str) -> None:
        self._set_value(f"sage:job:{job_id}:lease", datetime.now(UTC).isoformat(), ttl_seconds=300)

    def heartbeat(self, job_id: str) -> None:
        self._set_value(f"sage:job:{job_id}:heartbeat", datetime.now(UTC).isoformat(), ttl_seconds=120)
        self.seed_lease(job_id)

    def request_cancel(self, job_id: str, reason: str) -> None:
        self._set_value(f"sage:job:{job_id}:cancel", reason)

    def is_cancel_requested(self, job_id: str) -> bool:
        if self._client is None:
            return False
        try:
            return self._client.exists(f"sage:job:{job_id}:cancel") == 1
        except RedisError:
            return False

    def cancel_reason(self, job_id: str, default: str = "USER_REQUESTED") -> str:
        if self._client is None:
            return default
        try:
            value = self._client.get(f"sage:job:{job_id}:cancel")
            return value or default
        except RedisError:
            return default

