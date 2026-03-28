from __future__ import annotations

import time
from pathlib import Path

from fastapi.testclient import TestClient


def _build_chain_payload(
    client: TestClient,
    task_id: str = "task_w4_001",
    user_query: str = "water yield",
) -> tuple[dict, dict, dict, dict]:
    pass1_result = client.post(
        "/planning/pass1",
        json={"task_id": task_id, "user_query": user_query, "state_version": 1},
    ).json()

    passb_result = client.post(
        "/cognition/passb",
        json={
            "task_id": task_id,
            "user_query": user_query,
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
    assert body["capability_key"] == "water_yield"
    assert body["capability_facts"]["capability_key"] == "water_yield"
    assert body["selected_template"] == "water_yield_v1"
    assert body["template_version"] == "1.0.0"
    assert body["stable_defaults"]["analysis_template"] == "water_yield_v1"
    assert body["stable_defaults"]["root_depth_factor"] == 0.8
    assert body["stable_defaults"]["pawc_factor"] == 0.85
    precipitation_mapping = next(
        mapping for mapping in body["role_arg_mappings"] if mapping["role_name"] == "precipitation"
    )
    assert precipitation_mapping["default_value"] == 1200.0
    pawc_mapping = next(
        mapping for mapping in body["role_arg_mappings"] if mapping["role_name"] == "plant_available_water_content"
    )
    assert pawc_mapping["default_value"] == 0.9
    assert body["logical_input_roles"]
    assert body["slot_schema_view"]["slots"]
    precipitation_slot = next(slot for slot in body["slot_schema_view"]["slots"] if slot["slot_name"] == "precipitation")
    assert precipitation_slot["bound_role"] == "precipitation"
    assert body["graph_skeleton"]["nodes"]
    assert body["graph_skeleton"]["edges"]


def test_planning_pass1_normalizes_requested_capability_key(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    response = client.post(
        "/planning/pass1",
        json={
            "task_id": "task_capability_alias",
            "user_query": "water yield",
            "state_version": 1,
            "capability_key": "water_yield_v1",
        },
    )
    assert response.status_code == 200

    body = response.json()
    assert body["capability_key"] == "water_yield"
    assert body["capability_facts"]["capability_key"] == "water_yield"
    assert body["selected_template"] == "water_yield_v1"


def test_workspace_directories_are_created(monkeypatch, tmp_path: Path) -> None:
    workspace_root = tmp_path / "workspace"
    monkeypatch.setenv("WORKSPACE_ROOT", str(workspace_root))

    from app.workspace import create_workspace, ensure_workspace_directories

    ensure_workspace_directories()
    assert workspace_root.exists()

    context = create_workspace(
        workspace_id="ws_test_001",
        task_id="task_workspace_test",
        attempt_no=1,
        job_id="job_workspace_test",
        runtime_profile="docker-local",
    )
    assert context.work_dir.exists()
    assert context.archive_dir.exists()
    assert context.logs_dir.exists()
    assert context.audit_dir.exists()


def _build_create_job_payload(task_id: str, pass1_result: dict, passb_result: dict, pass2_result: dict) -> dict:
    return {
        "task_id": task_id,
        "workspace_id": f"ws_{task_id}",
        "attempt_no": 1,
        "capability_key": pass1_result["capability_key"],
        "provider_key": "test-provider",
        "runtime_profile": pass1_result["capability_facts"]["runtime_profile_hint"],
        "case_id": passb_result["args_draft"].get("case_id"),
        "materialized_execution_graph": pass2_result["materialized_execution_graph"],
        "runtime_assertions": pass2_result["runtime_assertions"],
        "slot_bindings": passb_result["slot_bindings"],
        "args_draft": passb_result["args_draft"],
    }


def test_job_success_returns_result_bundle_and_explanation(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, passb_result, _, pass2_result = _build_chain_payload(client, "task_w4_success")

    create_job_response = client.post(
        "/jobs",
        json=_build_create_job_payload("task_w4_success", pass1_result, passb_result, pass2_result),
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
    assert "water_yield_result" in final_status["result_bundle"]["main_outputs"]
    assert final_status["docker_runtime_evidence"]["provider_key"] == "test-provider"
    assert final_status["docker_runtime_evidence"]["case_id"] == passb_result["args_draft"]["case_id"]
    assert final_status["docker_runtime_evidence"]["runtime_mode"] == "deterministic_stub"
    assert len(final_status["docker_runtime_evidence"]["input_bindings"]) >= 2
    primary_logical_names = {item["logical_name"] for item in final_status["artifact_catalog"]["primary_outputs"]}
    audit_logical_names = {item["logical_name"] for item in final_status["artifact_catalog"]["audit_artifacts"]}
    assert "water_yield_result" in primary_logical_names
    assert "run_manifest" in audit_logical_names


def test_running_job_can_be_cancelled(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, passb_result, _, pass2_result = _build_chain_payload(client, "task_w4_cancel")

    create_job_response = client.post(
        "/jobs",
        json=_build_create_job_payload("task_w4_cancel", pass1_result, passb_result, pass2_result),
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


def test_planning_pass2_derives_runtime_assertions_from_pass1_facts(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, _, validation_summary, pass2_result = _build_chain_payload(client, "task_w4_assertions")

    assertion_by_name = {item["name"]: item for item in pass2_result["runtime_assertions"]}

    assert pass2_result["planning_summary"]["capability_key"] == "water_yield"
    assert pass2_result["planning_summary"]["validation_error_code"] == validation_summary["error_code"]
    assert pass2_result["planning_summary"]["runtime_assertion_count"] == len(pass2_result["runtime_assertions"])

    assert assertion_by_name["arg:workspace_dir"]["required"] is True
    assert assertion_by_name["arg:results_suffix"]["required"] is True
    assert assertion_by_name["binding:precipitation"]["required"] is True
    assert assertion_by_name["binding:eto"]["required"] is True
    assert assertion_by_name["binding:depth_to_root_restricting_layer"]["required"] is False
    assert assertion_by_name["arg:precipitation_slot"]["required"] is True
    assert assertion_by_name["arg:eto_slot"]["required"] is True
    assert assertion_by_name["arg:root_depth_slot"]["required"] is False
    assert assertion_by_name["slot_type:precipitation"]["message"] == "precipitation must bind to a raster slot"
    assert assertion_by_name["slot_type:eto"]["message"] == "eto must bind to a raster slot"


def test_passb_domain_args_follow_skill_role_default_values(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, passb_result, _, _ = _build_chain_payload(client, "task_w4_role_defaults")

    assert pass1_result["role_arg_mappings"]
    assert passb_result["args_draft"]["case_id"] == "annual_water_yield_gura"
    assert passb_result["args_draft"]["contract_mode"] == "real_case_prep_v1"
    assert passb_result["args_draft"]["runtime_mode"] == "deterministic_stub"
    assert passb_result["args_draft"]["precipitation_index"] == 1200.0
    assert passb_result["args_draft"]["eto_index"] == 800.0
    assert passb_result["args_draft"]["root_depth_factor"] == 0.8
    assert passb_result["args_draft"]["pawc_factor"] == 0.85
    assert passb_result["args_draft"]["precipitation_path"].endswith("/Annual_Water_Yield/precipitation_gura.tif")
    assert passb_result["args_draft"]["eto_path"].endswith("/Annual_Water_Yield/reference_ET_gura.tif")


def test_passb_can_simulate_runtime_assertion_failure(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    _, passb_result, validation_summary, _ = _build_chain_payload(
        client,
        "task_w6_assertion_failure",
        "week6 assertionfailure demo",
    )

    assert validation_summary["is_valid"] is True
    assert "precipitation_slot" not in passb_result["args_draft"]


def test_passb_marks_real_invest_runtime_mode_for_real_case_queries(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, passb_result, validation_summary, pass2_result = _build_chain_payload(
        client,
        "task_phase3_real_case",
        "run a real case invest annual water yield analysis for gura",
    )

    assert passb_result["args_draft"]["case_id"] == "annual_water_yield_gura"
    assert passb_result["args_draft"]["runtime_mode"] == "invest_real_runner"
    assert passb_result["args_draft"]["contract_mode"] == "invest_real_case_v1"
    assert passb_result["args_draft"]["seasonality_constant"] == 5.0
    assert passb_result["args_draft"]["sub_watersheds_path"].endswith("/Annual_Water_Yield/subwatersheds_gura.shp")
    assert validation_summary["is_valid"] is True
    assertion_by_name = {item["name"]: item for item in pass2_result["runtime_assertions"]}
    assert assertion_by_name["arg:lulc_path"]["required"] is True
    assert assertion_by_name["file:lulc_path"]["assertion_type"] == "file_exists"
    assert assertion_by_name["arg:watersheds_path"]["required"] is True


def test_job_can_simulate_promotion_failure_artifact_catalog(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.main import app

    client = TestClient(app)
    pass1_result, passb_result, _, pass2_result = _build_chain_payload(
        client,
        "task_w6_promotion_failure",
        "week6 promotionfailure demo",
    )

    create_job_response = client.post(
        "/jobs",
        json=_build_create_job_payload("task_w6_promotion_failure", pass1_result, passb_result, pass2_result),
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
    primary_outputs = final_status["artifact_catalog"]["primary_outputs"]
    assert len(primary_outputs) >= 2
    assert primary_outputs[0]["artifact_id"] == primary_outputs[1]["artifact_id"]


def test_repair_proposal_uses_typed_waiting_context(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    monkeypatch.delenv("SAGE_REPAIR_PROVIDER", raising=False)
    from app.main import app

    client = TestClient(app)
    response = client.post(
        "/repair/proposal",
        json={
            "waiting_context": {
                "waiting_reason_type": "MISSING_INPUT",
                "missing_slots": [
                    {"slot_name": "precipitation", "expected_type": "raster", "required": True}
                ],
                "invalid_bindings": [],
                "required_user_actions": [
                    {
                        "action_type": "upload",
                        "key": "upload_precipitation",
                        "label": "Upload precipitation",
                        "required": True,
                    }
                ],
                "resume_hint": "Complete required actions before resuming.",
                "can_resume": False,
            },
            "validation_summary": {
                "is_valid": False,
                "missing_roles": ["precipitation"],
                "missing_params": [],
                "error_code": "MISSING_ROLE",
                "invalid_bindings": [],
            },
            "failure_summary": {},
            "user_note": "",
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["user_facing_reason"] == "Additional required inputs are missing before the task can continue."
    assert body["action_explanations"][0]["key"] == "upload_precipitation"
    assert "Latest structured failure code: MISSING_ROLE" in body["notes"]


def test_parse_llm_json_supports_fenced_json(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv("WORKSPACE_ROOT", str(tmp_path / "workspace"))
    from app.repair import parse_llm_json

    parsed = parse_llm_json(
        """```json
        {
          "user_facing_reason": "Missing precipitation.",
          "resume_hint": "Upload precipitation.",
          "action_explanations": [{"key": "upload_precipitation", "message": "Upload it."}],
          "notes": ["note"]
        }
        ```"""
    )

    assert parsed["user_facing_reason"] == "Missing precipitation."
