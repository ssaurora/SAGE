from __future__ import annotations

import json
import os
import sys
import time
import uuid
from pathlib import Path


REQUIRED_PATH_KEYS = (
    "lulc_path",
    "depth_to_root_rest_layer_path",
    "precipitation_path",
    "pawc_path",
    "eto_path",
    "watersheds_path",
    "biophysical_table_path",
)


def main() -> int:
    if len(sys.argv) != 2:
        raise RuntimeError("usage: python -m app.invest_real_runner <runtime_request.json>")

    request_path = Path(sys.argv[1])
    payload = json.loads(request_path.read_text(encoding="utf-8"))
    args = _build_invest_args(payload)
    _validate_required_inputs(args)
    _apply_prestart_delay()
    execute = _load_execute()
    output_registry = execute(args)
    _write_runtime_result(payload, args, output_registry)
    return 0


def _build_invest_args(payload: dict) -> dict[str, object]:
    args_draft = payload["args_draft"]
    args: dict[str, object] = {
        "workspace_dir": args_draft["workspace_dir"],
        "lulc_path": args_draft["lulc_path"],
        "depth_to_root_rest_layer_path": args_draft["depth_to_root_restricting_layer_path"],
        "precipitation_path": args_draft["precipitation_path"],
        "pawc_path": args_draft["plant_available_water_content_path"],
        "eto_path": args_draft["eto_path"],
        "watersheds_path": args_draft["watersheds_path"],
        "biophysical_table_path": args_draft["biophysical_table_path"],
        "seasonality_constant": float(args_draft["seasonality_constant"]),
        "results_suffix": args_draft.get("results_suffix", ""),
    }
    if "n_workers" in args_draft:
        args["n_workers"] = int(args_draft["n_workers"])
    sub_watersheds_path = args_draft.get("sub_watersheds_path")
    if isinstance(sub_watersheds_path, str) and sub_watersheds_path.strip():
        args["sub_watersheds_path"] = sub_watersheds_path
    demand_table_path = args_draft.get("demand_table_path")
    if isinstance(demand_table_path, str) and demand_table_path.strip():
        args["demand_table_path"] = demand_table_path
    valuation_table_path = args_draft.get("valuation_table_path")
    if isinstance(valuation_table_path, str) and valuation_table_path.strip():
        args["valuation_table_path"] = valuation_table_path
    return args


def _validate_required_inputs(args: dict[str, object]) -> None:
    for key in REQUIRED_PATH_KEYS:
        value = args.get(key)
        if not isinstance(value, str) or not value.strip():
            raise RuntimeError(f"real InVEST provider requires non-empty {key}")
        if not Path(value).exists():
            raise FileNotFoundError(f"real InVEST provider input does not exist: {key}={value}")


def _apply_prestart_delay() -> None:
    raw_value = str(os.getenv("SAGE_INVEST_REAL_PRESTART_DELAY_SECONDS", "0")).strip()
    if not raw_value:
        return
    try:
        delay_seconds = float(raw_value)
    except ValueError:
        return
    if delay_seconds <= 0:
        return
    time.sleep(delay_seconds)


def _load_execute():
    try:
        from natcap.invest import annual_water_yield
    except ImportError as exc:  # pragma: no cover - depends on optional image contents
        raise RuntimeError(
            "natcap.invest is not installed. Build the service image with "
            "SAGE_INVEST_PIP_SPEC=natcap.invest and mount SAGE_SAMPLE_DATA_ROOT."
        ) from exc
    return annual_water_yield.execute


def _write_runtime_result(payload: dict, args: dict[str, object], output_registry: object) -> None:
    output_dir = Path(str(payload["output_dir"]))
    output_dir.mkdir(parents=True, exist_ok=True)
    output_files = sorted(path for path in output_dir.rglob("*") if path.is_file())
    normalized_output_registry = _normalize_output_registry(output_registry)
    geotiff_count = sum(1 for path in output_files if path.suffix.lower() in {".tif", ".tiff"})
    vector_count = sum(1 for path in output_files if path.suffix.lower() in {".shp", ".gpkg", ".geojson"})
    table_count = sum(1 for path in output_files if path.suffix.lower() in {".csv", ".dbf"})
    summary = f"Real InVEST annual water yield run completed for task {payload['task_id']}"
    result = {
        "result_id": f"result_{uuid.uuid4().hex[:12]}",
        "summary": summary,
        "metrics": {
            "task_id": payload["task_id"],
            "job_id": payload["job_id"],
            "workspace_id": payload["workspace_id"],
            "attempt_no": int(payload["attempt_no"]),
            "node_count": int(payload["node_count"]),
            "edge_count": int(payload["edge_count"]),
            "capability_key": str(payload["capability_key"]),
            "provider_key": str(payload["provider_key"]),
            "runtime_profile": str(payload["runtime_profile"]),
            "case_id": str(payload.get("case_id") or ""),
            "case_descriptor_version": str(payload["args_draft"].get("case_descriptor_version", "")),
            "analysis_template": str(payload["args_draft"].get("analysis_template", "water_yield_v1")),
            "contract_mode": str(payload["args_draft"].get("contract_mode", "invest_real_case_v1")),
            "runtime_mode": str(payload["args_draft"].get("runtime_mode", "invest_real_runner")),
            "used_real_invest": True,
            "seasonality_constant": float(args["seasonality_constant"]),
            "output_file_count": len(output_files),
            "file_registry_count": len(normalized_output_registry),
            "geotiff_count": geotiff_count,
            "vector_count": vector_count,
            "table_count": table_count,
            "status": "SUCCEEDED",
        },
        "output_registry": normalized_output_registry,
    }
    (output_dir / "runtime_result.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _normalize_output_registry(output_registry: object) -> dict[str, str]:
    if not isinstance(output_registry, dict):
        return {}
    normalized: dict[str, str] = {}
    for key, value in output_registry.items():
        if key is None:
            continue
        if isinstance(value, str):
            normalized[str(key)] = value
        elif isinstance(value, Path):
            normalized[str(key)] = str(value)
    return normalized


if __name__ == "__main__":
    raise SystemExit(main())
