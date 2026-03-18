from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient


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

    from app.main import ensure_workspace_directories

    ensure_workspace_directories()

    assert (workspace_root / "input").exists()
    assert (workspace_root / "output").exists()
    assert (workspace_root / "logs").exists()

