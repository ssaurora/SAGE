from __future__ import annotations

import time
from pathlib import Path

from fastapi.testclient import TestClient


def _build_chain_payload(client: TestClient, task_id: str = "task_w4_001") -> tuple[dict, dict, dict, dict]:
    pass1_result = client.post(
        "/planning/pass1",
        json={"task_id": task_id, "user_query": "water yield", "state_version": 1},
    ).json()

    passb_result = client.post(
        "/cognition/passb",
        json={
            "task_id": task_id,
            "user_query": "water yield",
            "state_version": 2,
            "pass1_result": pass1_result,
        },
    ).json()

    validation_summary = client.post(
        "/validate/primitive",
        json={
            "task_id": task_id,
            "state_version": 3,
            "pass1_result": pass1_result,
            "passb_result": passb_result,
        },
    ).json()

    pass2_result = client.post(
        "/planning/pass2",
        json={
            "task_id": task_id,
            "state_version": 4,
            "pass1_result": pass1_result,
            "passb_result": passb_result,
            "validation_summary": validation_summary,
        },
    ).json()

    return pass1_result, passb_result, validation_summary, pass2_result


def test_planning_pass1_response_schema(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    response = client.post(
        "/planning/pass1",
        json={
            "task_id": "task_20260318_0001",
            "user_query": "请帮我做一个 water yield 分析",
            "state_version": 1,
        },
    )
    assert response.status_code == 200

    body = response.json()
    assert body["selected_template"] == "water_yield_v1"
    assert body["template_version"] == "1.0.0"
    assert body["logical_input_roles"]
    assert body["slot_schema_view"]["slots"]
    assert body["graph_skeleton"]["nodes"]
    assert body["graph_skeleton"]["edges"]


def test_workspace_directories_are_created(monkeypatch, tmp_path: Path) -> None:
    workspace_root = tmp_path / "workspace"
    monkeypatch.setenv("WORKSPACE_ROOT", str(workspace_root))

    from app.workspace import ensure_workspace_directories

    ensure_workspace_directories()
    assert (workspace_root / "input").exists()
    assert (workspace_root / "output").exists()
    assert (workspace_root / "logs").exists()


def test_job_success_returns_result_bundle_and_explanation(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    _, passb_result, _, pass2_result = _build_chain_payload(client, "task_w4_success")

    create_job_response = client.post(
        "/jobs",
        json={
            "task_id": "task_w4_success",
            "materialized_execution_graph": pass2_result["materialized_execution_graph"],
            "args_draft": passb_result["args_draft"],
        },
    )
    assert create_job_response.status_code == 200
    job_id = create_job_response.json()["job_id"]

    final_status = None
    for _ in range(40):
        status_response = client.get(f"/jobs/{job_id}")
        assert status_response.status_code == 200
        final_status = status_response.json()
        if final_status["job_state"] in ("SUCCEEDED", "FAILED", "CANCELLED"):
            break
        time.sleep(0.2)

    assert final_status is not None
    assert final_status["job_state"] == "SUCCEEDED"
    assert final_status["result_bundle"] is not None
    assert final_status["final_explanation"] is not None
    assert final_status["failure_summary"] is None
    assert final_status["docker_runtime_evidence"] is not None
    assert final_status["result_bundle"]["result_id"].startswith("result_")


def test_running_job_can_be_cancelled(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    _, passb_result, _, pass2_result = _build_chain_payload(client, "task_w4_cancel")

    create_job_response = client.post(
        "/jobs",
        json={
            "task_id": "task_w4_cancel",
            "materialized_execution_graph": pass2_result["materialized_execution_graph"],
            "args_draft": passb_result["args_draft"],
        },
    )
    assert create_job_response.status_code == 200
    job_id = create_job_response.json()["job_id"]

    cancel_response = client.post(f"/jobs/{job_id}/cancel", json={"reason": "test_cancel"})
    assert cancel_response.status_code == 200
    cancel_body = cancel_response.json()
    assert cancel_body["accepted"] is True

    final_status = None
    for _ in range(40):
        status_response = client.get(f"/jobs/{job_id}")
        assert status_response.status_code == 200
        final_status = status_response.json()
        if final_status["job_state"] == "CANCELLED":
            break
        time.sleep(0.2)

    assert final_status is not None
    assert final_status["job_state"] == "CANCELLED"
    assert final_status["cancel_reason"] in ("test_cancel", "USER_REQUESTED")
    assert final_status["cancelled_at"] is not None
    assert final_status["failure_summary"] is not None


def test_validation_includes_invalid_bindings_and_missing_role_trigger(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result = client.post(
        "/planning/pass1",
        json={"task_id": "task_w5_missing", "user_query": "missing precipitation", "state_version": 1},
    ).json()

    passb_result = client.post(
        "/cognition/passb",
        json={
            "task_id": "task_w5_missing",
            "user_query": "missing precipitation",
            "state_version": 2,
            "pass1_result": pass1_result,
        },
    ).json()

    validation = client.post(
        "/validate/primitive",
        json={
            "task_id": "task_w5_missing",
            "state_version": 3,
            "pass1_result": pass1_result,
            "passb_result": passb_result,
        },
    ).json()

    assert "invalid_bindings" in validation
    assert validation["is_valid"] is False
    assert "precipitation" in validation["missing_roles"]
