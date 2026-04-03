from __future__ import annotations

import sys
from pathlib import Path

import pytest


APP_ROOT = Path(__file__).resolve().parents[1]
if str(APP_ROOT) not in sys.path:
    sys.path.insert(0, str(APP_ROOT))


@pytest.fixture(autouse=True)
def _stable_test_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("SAGE_COGNITION_PROVIDER", "deterministic")
    monkeypatch.setenv("SAGE_COGNITION_GOAL_ROUTE_PROVIDER", "deterministic")
    monkeypatch.setenv("SAGE_COGNITION_PASSB_PROVIDER", "deterministic")
    monkeypatch.setenv("SAGE_REPAIR_PROVIDER", "deterministic")
    monkeypatch.setenv("SAGE_FINAL_EXPLANATION_PROVIDER", "deterministic")
    monkeypatch.setenv("SAGE_REAL_CASE_LLM_REQUIRED", "false")
