from __future__ import annotations

from app.schemas import (
    GraphSkeleton,
    LogicalInputRole,
    PlanningPass1Request,
    PlanningPass1Response,
    SlotSchemaItem,
    SlotSchemaView,
)


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

