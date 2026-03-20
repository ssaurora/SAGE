from __future__ import annotations

from app.schemas import (
    CognitionPassBRequest,
    CognitionPassBResponse,
    DecisionSummary,
    GraphSkeleton,
    LogicalInputRole,
    MaterializedExecutionGraph,
    MaterializedExecutionNode,
    PlanningPass1Request,
    PlanningPass1Response,
    PlanningPass2Request,
    PlanningPass2Response,
    PrimitiveValidationRequest,
    PrimitiveValidationResponse,
    RuntimeAssertion,
    SlotBinding,
    SlotSchemaItem,
    SlotSchemaView,
)


ROLE_DEFAULTS: dict[str, dict[str, str | float]] = {
    "precipitation": {"slot_key": "precipitation_slot", "value_key": "precipitation_index", "value": 1200.0},
    "eto": {"slot_key": "eto_slot", "value_key": "eto_index", "value": 800.0},
    "depth_to_root_restricting_layer": {"slot_key": "root_depth_slot", "value_key": "root_depth_factor", "value": 0.95},
    "plant_available_water_content": {"slot_key": "pawc_slot", "value_key": "pawc_factor", "value": 0.9},
}


def build_pass1_response(_: PlanningPass1Request) -> PlanningPass1Response:
    return PlanningPass1Response(
        selected_template="water_yield_v1",
        template_version="1.0.0",
        logical_input_roles=[
            LogicalInputRole(role_name="precipitation", required=True),
            LogicalInputRole(role_name="eto", required=True),
            LogicalInputRole(role_name="depth_to_root_restricting_layer", required=False),
            LogicalInputRole(role_name="plant_available_water_content", required=False),
        ],
        slot_schema_view=SlotSchemaView(
            slots=[
                SlotSchemaItem(slot_name="watersheds", type="vector"),
                SlotSchemaItem(slot_name="lulc", type="raster"),
                SlotSchemaItem(slot_name="biophysical_table", type="table"),
                SlotSchemaItem(slot_name="precipitation", type="raster"),
                SlotSchemaItem(slot_name="eto", type="raster"),
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


def build_passb_response(payload: CognitionPassBRequest) -> CognitionPassBResponse:
    slot_names = {slot.slot_name for slot in payload.pass1_result.slot_schema_view.slots}

    fallback_slot = "watersheds" if "watersheds" in slot_names else next(iter(slot_names), "workspace")
    bindings: list[SlotBinding] = []
    lower_query = payload.user_query.lower()
    for role in payload.pass1_result.logical_input_roles:
        if "missing" in lower_query and role.role_name == "precipitation":
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

    safe_task_id = payload.task_id.replace("/", "_")
    args_draft = {
        "workspace_dir": f"/workspace/output/{safe_task_id}",
        "results_suffix": "week3",
        "n_workers": 1,
        "analysis_template": payload.pass1_result.selected_template,
    }
    args_draft.update(_build_domain_args(bindings))

    return CognitionPassBResponse(
        slot_bindings=bindings,
        args_draft=args_draft,
        decision_summary=DecisionSummary(
            strategy="template_default_binding",
            assumptions=[
                "PassB only generates candidate structures",
                "Completeness is determined by Validation",
            ],
        ),
    )


def _build_domain_args(bindings: list[SlotBinding]) -> dict[str, str | int | float | bool]:
    args: dict[str, str | int | float | bool] = {}
    for binding in bindings:
        config = ROLE_DEFAULTS.get(binding.role_name)
        if config is None:
            continue
        args[str(config["slot_key"])] = binding.slot_name
        args[str(config["value_key"])] = float(config["value"])

    if "root_depth_factor" not in args:
        args["root_depth_factor"] = 0.8
    if "pawc_factor" not in args:
        args["pawc_factor"] = 0.85
    return args


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

    assertions = [
        RuntimeAssertion(name="workspace_dir", required=True, message="workspace_dir must be present"),
        RuntimeAssertion(name="results_suffix", required=True, message="results_suffix must be present"),
    ]

    planning_summary = {
        "planner": "pass2_minimal",
        "node_count": len(graph_nodes),
        "edge_count": len(graph_edges),
        "validation_is_valid": payload.validation_summary.is_valid,
        "template": payload.pass1_result.selected_template,
    }

    return PlanningPass2Response(
        materialized_execution_graph=MaterializedExecutionGraph(nodes=graph_nodes, edges=graph_edges),
        runtime_assertions=assertions,
        planning_summary=planning_summary,
    )
