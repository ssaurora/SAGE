from __future__ import annotations

import hashlib
import json

from app.schemas import (
    CognitionPassBRequest,
    CognitionPassBResponse,
    DecisionSummary,
    GraphSkeleton,
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
from app.schemas import SkillDefinition
from app.skill_catalog import get_skill_definition

DEFAULT_WATER_YIELD_CASE_ID = "annual_water_yield_gura"
DEFAULT_WATER_YIELD_SAMPLE_ROOT = "/sample-data/Annual_Water_Yield"
WATER_YIELD_CASE_CONFIGS = {
    "annual_water_yield_gura": {
        "sample_root": DEFAULT_WATER_YIELD_SAMPLE_ROOT,
        "results_suffix": "gura",
        "seasonality_constant": 5.0,
        "inputs": {
            "watersheds": "watershed_gura.shp",
            "sub_watersheds": "subwatersheds_gura.shp",
            "lulc": "land_use_gura.tif",
            "biophysical_table": "biophysical_table_gura.csv",
            "precipitation": "precipitation_gura.tif",
            "eto": "reference_ET_gura.tif",
            "depth_to_root_restricting_layer": "depth_to_root_restricting_layer_gura.tif",
            "plant_available_water_content": "plant_available_water_fraction_gura.tif",
            "invest_datastack": "annual_water_yield_gura.invs.json",
        },
    }
}


def build_pass1_response(payload: PlanningPass1Request) -> PlanningPass1Response:
    skill = get_skill_definition(payload.capability_key)
    return PlanningPass1Response(
        capability_key=skill.capability.capability_key,
        capability_facts=skill.capability,
        selected_template=skill.selected_template,
        template_version=skill.skill_version,
        logical_input_roles=[*skill.required_roles, *skill.optional_roles],
        role_arg_mappings=skill.role_arg_mappings,
        stable_defaults=skill.stable_defaults,
        slot_schema_view=SlotSchemaView(
            slots=[
                SlotSchemaItem(slot_name=slot.slot_name, type=slot.type, bound_role=slot.bound_role)
                for slot in skill.slot_specs
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
    skill = get_skill_definition(payload.pass1_result.capability_key)
    slot_names = {slot.slot_name for slot in payload.pass1_result.slot_schema_view.slots}
    lower_query = payload.user_query.lower()
    case_id = _derive_case_id(lower_query)
    case_config = WATER_YIELD_CASE_CONFIGS[case_id]
    real_case_requested = _is_real_case_requested(lower_query)

    fallback_slot = "watersheds" if "watersheds" in slot_names else next(iter(slot_names), "workspace")
    bindings: list[SlotBinding] = []
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
        "results_suffix": str(case_config["results_suffix"]) if real_case_requested else "week3",
        "n_workers": 1,
        "analysis_template": payload.pass1_result.selected_template,
        "case_id": case_id,
        "case_profile_version": "water_yield_case_contract_v1",
        "contract_mode": "invest_real_case_v1" if real_case_requested else "real_case_prep_v1",
        "runtime_mode": "invest_real_runner" if real_case_requested else "deterministic_stub",
        "sample_data_root": str(case_config["sample_root"]),
    }
    args_draft.update(_build_case_args(case_id))
    args_draft.update(_build_domain_args(skill, bindings, case_id))
    if "assertionfailure" in lower_query:
        args_draft.pop("precipitation_slot", None)
    if "promotionfailure" in lower_query:
        args_draft["simulate_promotion_failure"] = True

    return CognitionPassBResponse(
        slot_bindings=bindings,
        args_draft=args_draft,
        decision_summary=DecisionSummary(
            strategy="template_default_binding",
            assumptions=[
                "PassB only generates candidate structures",
                "Completeness is determined by Validation",
                f"water_yield case_id is fixed to {case_id} unless the query selects another canonical case",
            ],
        ),
    )


def _build_domain_args(
    skill: SkillDefinition,
    bindings: list[SlotBinding],
    case_id: str,
) -> dict[str, str | int | float | bool]:
    mapping_by_role = {mapping.role_name: mapping for mapping in skill.role_arg_mappings}
    args: dict[str, str | int | float | bool] = dict(skill.stable_defaults)
    for binding in bindings:
        mapping = mapping_by_role.get(binding.role_name)
        if mapping is None:
            continue
        args[mapping.slot_arg_key] = binding.slot_name
        path_arg_key = _path_arg_key(mapping.slot_arg_key)
        if path_arg_key:
            args[path_arg_key] = _build_case_input_path(case_id, binding.role_name)
        if mapping.value_arg_key and mapping.default_value is not None:
            args[mapping.value_arg_key] = mapping.default_value

    if "root_depth_factor" not in args:
        args["root_depth_factor"] = 0.8
    if "pawc_factor" not in args:
        args["pawc_factor"] = 0.85
    return args


def _build_case_args(case_id: str) -> dict[str, str]:
    case_config = WATER_YIELD_CASE_CONFIGS[case_id]
    args: dict[str, str | float] = {"seasonality_constant": float(case_config["seasonality_constant"])}
    sample_root = str(case_config["sample_root"])
    for input_name, relative_path in case_config["inputs"].items():
        args[f"{input_name}_path"] = f"{sample_root}/{relative_path}"
    return args


def _derive_case_id(lower_query: str) -> str:
    if any(token in lower_query for token in ("gura", "case_b", "annual_water_yield_gura")):
        return "annual_water_yield_gura"
    return DEFAULT_WATER_YIELD_CASE_ID


def _is_real_case_requested(lower_query: str) -> bool:
    return any(
        token in lower_query
        for token in ("real case", "real_case", "真实", "invest", "gura", "annual_water_yield_gura")
    )


def _path_arg_key(slot_arg_key: str) -> str | None:
    if not slot_arg_key.endswith("_slot"):
        return None
    return f"{slot_arg_key[:-5]}_path"


def _build_case_input_path(case_id: str, role_name: str) -> str:
    case_config = WATER_YIELD_CASE_CONFIGS[case_id]
    relative_path = case_config["inputs"].get(role_name)
    sample_root = str(case_config["sample_root"])
    if relative_path is None:
        return f"{sample_root}/{role_name}"
    return f"{sample_root}/{relative_path}"


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

    rewritten_nodes, rewritten_edges, rewrite_summary = _rewrite_graph(graph_nodes, graph_edges)
    canonical_nodes, canonical_edges, canonicalization_summary = _canonicalize_graph(rewritten_nodes, rewritten_edges)
    graph_digest = _build_graph_digest(canonical_nodes, canonical_edges)
    assertions = _build_runtime_assertions(payload)

    planning_summary = {
        "planner": "pass2_minimal",
        "node_count": len(canonical_nodes),
        "edge_count": len(canonical_edges),
        "validation_is_valid": payload.validation_summary.is_valid,
        "validation_error_code": payload.validation_summary.error_code,
        "capability_key": payload.pass1_result.capability_key,
        "template": payload.pass1_result.selected_template,
        "runtime_assertion_count": len(assertions),
        "graph_digest": graph_digest,
    }

    return PlanningPass2Response(
        materialized_execution_graph=MaterializedExecutionGraph(nodes=canonical_nodes, edges=canonical_edges),
        runtime_assertions=assertions,
        graph_digest=graph_digest,
        planning_summary=planning_summary,
        canonicalization_summary=canonicalization_summary,
        rewrite_summary=rewrite_summary,
    )


def _build_runtime_assertions(payload: PlanningPass2Request) -> list[RuntimeAssertion]:
    args_draft = payload.passb_result.args_draft
    assertions = [
        RuntimeAssertion(
            assertion_id="assert_workspace_dir",
            name="arg:workspace_dir",
            required=True,
            message="workspace_dir must be present",
            assertion_type="required_arg",
            node_id="prepare_workspace",
            target_key="workspace_dir",
            repairable=False,
            details={"source": "system"},
        ),
        RuntimeAssertion(
            assertion_id="assert_results_suffix",
            name="arg:results_suffix",
            required=True,
            message="results_suffix must be present",
            assertion_type="required_arg",
            node_id="write_result_object",
            target_key="results_suffix",
            repairable=False,
            details={"source": "system"},
        ),
    ]

    if args_draft.get("runtime_mode") == "invest_real_runner":
        assertions.extend(_build_real_case_runtime_assertions(args_draft))

    validation_hint_by_role = {
        hint.role_name: hint for hint in payload.pass1_result.capability_facts.validation_hints
    }
    role_mapping_by_role = {
        mapping.role_name: mapping for mapping in payload.pass1_result.role_arg_mappings
    }

    for role in payload.pass1_result.logical_input_roles:
        assertions.append(
            RuntimeAssertion(
                assertion_id=f"assert_binding_{role.role_name}",
                name=f"binding:{role.role_name}",
                required=role.required,
                message=f"{role.role_name} input binding must be present",
                assertion_type="required_binding",
                node_id="load_inputs",
                target_key=role.role_name,
                repairable=role.required,
                details={"role_name": role.role_name},
            )
        )

        role_mapping = role_mapping_by_role.get(role.role_name)
        if role_mapping is not None:
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_arg_{role_mapping.slot_arg_key}",
                    name=f"arg:{role_mapping.slot_arg_key}",
                    required=role.required,
                    message=f"{role_mapping.slot_arg_key} must be present for role {role.role_name}",
                    assertion_type="required_arg",
                    node_id="load_inputs",
                    target_key=role_mapping.slot_arg_key,
                    repairable=role.required,
                    details={"role_name": role.role_name, "slot_arg_key": role_mapping.slot_arg_key},
                )
            )

        validation_hint = validation_hint_by_role.get(role.role_name)
        if validation_hint and validation_hint.expected_slot_type:
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_slot_type_{role.role_name}",
                    name=f"slot_type:{role.role_name}",
                    required=role.required,
                    message=f"{role.role_name} must bind to a {validation_hint.expected_slot_type} slot",
                    assertion_type="slot_type",
                    node_id="load_inputs",
                    target_key=role.role_name,
                    expected_value=validation_hint.expected_slot_type,
                    repairable=role.required,
                    details={"role_name": role.role_name, "expected_slot_type": validation_hint.expected_slot_type},
                )
            )

    return assertions


def _build_real_case_runtime_assertions(
    args_draft: dict[str, str | int | float | bool],
) -> list[RuntimeAssertion]:
    required_real_args = {
        "lulc_path": "real InVEST requires lulc_path",
        "depth_to_root_restricting_layer_path": "real InVEST requires depth_to_root_restricting_layer_path",
        "precipitation_path": "real InVEST requires precipitation_path",
        "plant_available_water_content_path": "real InVEST requires plant_available_water_content_path",
        "eto_path": "real InVEST requires eto_path",
        "watersheds_path": "real InVEST requires watersheds_path",
        "biophysical_table_path": "real InVEST requires biophysical_table_path",
        "seasonality_constant": "real InVEST requires seasonality_constant",
    }
    assertions: list[RuntimeAssertion] = []
    for arg_key, message in required_real_args.items():
        assertions.append(
            RuntimeAssertion(
                assertion_id=f"assert_real_arg_{arg_key}",
                name=f"arg:{arg_key}",
                required=True,
                message=message,
                assertion_type="required_arg",
                node_id="load_inputs",
                target_key=arg_key,
                repairable=False,
                details={"contract_mode": args_draft.get("contract_mode"), "runtime_mode": args_draft.get("runtime_mode")},
            )
        )
        if arg_key.endswith("_path"):
            assertions.append(
                RuntimeAssertion(
                    assertion_id=f"assert_real_file_{arg_key}",
                    name=f"file:{arg_key}",
                    required=True,
                    message=f"{arg_key} must point to an existing file",
                    assertion_type="file_exists",
                    node_id="load_inputs",
                    target_key=arg_key,
                    repairable=False,
                    details={"contract_mode": args_draft.get("contract_mode"), "runtime_mode": args_draft.get("runtime_mode")},
                )
            )
    return assertions


def _rewrite_graph(
    nodes: list[MaterializedExecutionNode],
    edges: list[list[str]],
) -> tuple[list[MaterializedExecutionNode], list[list[str]], dict[str, str | int | bool]]:
    seen_nodes: dict[str, MaterializedExecutionNode] = {}
    duplicate_nodes = 0
    for node in nodes:
        if node.node_id in seen_nodes:
            duplicate_nodes += 1
            continue
        seen_nodes[node.node_id] = node

    rewritten_edges: list[list[str]] = []
    seen_edges: set[tuple[str, str]] = set()
    duplicate_edges = 0
    for edge in edges:
        if len(edge) != 2:
            continue
        left, right = edge
        edge_key = (left, right)
        if edge_key in seen_edges:
            duplicate_edges += 1
            continue
        seen_edges.add(edge_key)
        rewritten_edges.append([left, right])

    return (
        list(seen_nodes.values()),
        rewritten_edges,
        {
            "rewriter": "whitelist_minimal",
            "duplicate_nodes_removed": duplicate_nodes,
            "duplicate_edges_removed": duplicate_edges,
            "alias_normalization_applied": False,
        },
    )


def _canonicalize_graph(
    nodes: list[MaterializedExecutionNode],
    edges: list[list[str]],
) -> tuple[list[MaterializedExecutionNode], list[list[str]], dict[str, str | int | bool]]:
    canonical_nodes = sorted(nodes, key=lambda item: (item.kind, item.node_id))
    canonical_edges = sorted(([left, right] for left, right in edges), key=lambda item: (item[0], item[1]))
    return (
        canonical_nodes,
        canonical_edges,
        {
            "canonicalizer": "deterministic_sort_v1",
            "node_order_normalized": True,
            "edge_order_normalized": True,
            "canonical_node_count": len(canonical_nodes),
            "canonical_edge_count": len(canonical_edges),
        },
    )


def _build_graph_digest(nodes: list[MaterializedExecutionNode], edges: list[list[str]]) -> str:
    payload = {
        "nodes": [{"node_id": node.node_id, "kind": node.kind} for node in nodes],
        "edges": edges,
    }
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
