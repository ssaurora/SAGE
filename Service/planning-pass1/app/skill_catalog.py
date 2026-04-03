from __future__ import annotations

from app.skill_assets import build_skill_definition_from_bundle, maybe_load_skill_bundle
from app.schemas import (
    CapabilityDefinitionLite,
    CapabilityOutputContract,
    CapabilityOutputItem,
    CapabilityRepairHint,
    CapabilityValidationHint,
    LogicalInputRole,
    SkillDefinition,
    SkillRoleArgMapping,
    SkillSlotSpec,
)


CANONICAL_CAPABILITY_KEY = "water_yield"

_ALIASES = {
    "water_yield": CANONICAL_CAPABILITY_KEY,
    "water_yield_v1": CANONICAL_CAPABILITY_KEY,
    "water_yield_analysis": CANONICAL_CAPABILITY_KEY,
    "generic_analysis": CANONICAL_CAPABILITY_KEY,
    "generic_analysis_request": CANONICAL_CAPABILITY_KEY,
    "repairable_analysis_request": CANONICAL_CAPABILITY_KEY,
}


def normalize_capability_key(raw_capability_key: str | None) -> str:
    if raw_capability_key is None:
        return CANONICAL_CAPABILITY_KEY
    normalized = raw_capability_key.strip().lower()
    if not normalized:
        return CANONICAL_CAPABILITY_KEY
    return _ALIASES.get(normalized, normalized)


def get_skill_definition(capability_key: str | None) -> SkillDefinition:
    normalized_key = normalize_capability_key(capability_key)
    if normalized_key != CANONICAL_CAPABILITY_KEY:
        raise ValueError(f"Unsupported capability key: {capability_key}")
    bundle = maybe_load_skill_bundle(normalized_key)
    if bundle is not None:
        return build_skill_definition_from_bundle(bundle)
    return _build_legacy_skill_definition()


def _build_legacy_skill_definition() -> SkillDefinition:
    capability = CapabilityDefinitionLite(
        capability_key=CANONICAL_CAPABILITY_KEY,
        display_name="Water Yield",
        validation_hints=[
            CapabilityValidationHint(role_name="watersheds", expected_slot_type="vector"),
            CapabilityValidationHint(role_name="lulc", expected_slot_type="raster"),
            CapabilityValidationHint(role_name="biophysical_table", expected_slot_type="table"),
            CapabilityValidationHint(role_name="precipitation", expected_slot_type="raster"),
            CapabilityValidationHint(role_name="eto", expected_slot_type="raster"),
            CapabilityValidationHint(role_name="depth_to_root_restricting_layer", expected_slot_type="raster"),
            CapabilityValidationHint(role_name="plant_available_water_content", expected_slot_type="raster"),
        ],
        repair_hints=[
            CapabilityRepairHint(
                role_name="watersheds",
                action_type="upload",
                action_key="upload_watersheds",
                action_label="Upload watersheds",
            ),
            CapabilityRepairHint(
                role_name="lulc",
                action_type="upload",
                action_key="upload_lulc",
                action_label="Upload lulc raster",
            ),
            CapabilityRepairHint(
                role_name="biophysical_table",
                action_type="upload",
                action_key="upload_biophysical_table",
                action_label="Upload biophysical table",
            ),
            CapabilityRepairHint(
                role_name="precipitation",
                action_type="upload",
                action_key="upload_precipitation",
                action_label="Upload precipitation",
            ),
            CapabilityRepairHint(
                role_name="eto",
                action_type="upload",
                action_key="upload_eto",
                action_label="Upload eto",
            ),
            CapabilityRepairHint(
                role_name="depth_to_root_restricting_layer",
                action_type="upload",
                action_key="upload_depth_to_root_restricting_layer",
                action_label="Upload depth to root restricting layer",
            ),
            CapabilityRepairHint(
                role_name="plant_available_water_content",
                action_type="upload",
                action_key="upload_plant_available_water_content",
                action_label="Upload plant available water content",
            ),
        ],
        output_contract=CapabilityOutputContract(
            outputs=[
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="watershed_results"),
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="water_yield_raster"),
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="aet_raster"),
                CapabilityOutputItem(artifact_role="AUDIT_ARTIFACT", logical_name="run_manifest"),
                CapabilityOutputItem(artifact_role="AUDIT_ARTIFACT", logical_name="runtime_request"),
            ]
        ),
        runtime_profile_hint="docker-local",
    )

    return SkillDefinition(
        skill_id=CANONICAL_CAPABILITY_KEY,
        skill_version="1.0.0",
        selected_template="water_yield_v1",
        capability=capability,
        required_roles=[
            LogicalInputRole(role_name="watersheds", required=True),
            LogicalInputRole(role_name="lulc", required=True),
            LogicalInputRole(role_name="biophysical_table", required=True),
            LogicalInputRole(role_name="precipitation", required=True),
            LogicalInputRole(role_name="eto", required=True),
        ],
        optional_roles=[
            LogicalInputRole(role_name="depth_to_root_restricting_layer", required=False),
            LogicalInputRole(role_name="plant_available_water_content", required=False),
        ],
        slot_specs=[
            SkillSlotSpec(slot_name="watersheds", type="vector", bound_role="watersheds"),
            SkillSlotSpec(slot_name="lulc", type="raster", bound_role="lulc"),
            SkillSlotSpec(slot_name="biophysical_table", type="table", bound_role="biophysical_table"),
            SkillSlotSpec(slot_name="precipitation", type="raster", bound_role="precipitation"),
            SkillSlotSpec(slot_name="eto", type="raster", bound_role="eto"),
            SkillSlotSpec(
                slot_name="depth_to_root_restricting_layer",
                type="raster",
                bound_role="depth_to_root_restricting_layer",
            ),
            SkillSlotSpec(
                slot_name="plant_available_water_content",
                type="raster",
                bound_role="plant_available_water_content",
            ),
        ],
        role_arg_mappings=[
            SkillRoleArgMapping(
                role_name="watersheds",
                slot_arg_key="watersheds_slot",
            ),
            SkillRoleArgMapping(
                role_name="lulc",
                slot_arg_key="lulc_slot",
            ),
            SkillRoleArgMapping(
                role_name="biophysical_table",
                slot_arg_key="biophysical_table_slot",
            ),
            SkillRoleArgMapping(
                role_name="precipitation",
                slot_arg_key="precipitation_slot",
                value_arg_key="precipitation_index",
                default_value=1200.0,
            ),
            SkillRoleArgMapping(
                role_name="eto",
                slot_arg_key="eto_slot",
                value_arg_key="eto_index",
                default_value=800.0,
            ),
            SkillRoleArgMapping(
                role_name="depth_to_root_restricting_layer",
                slot_arg_key="root_depth_slot",
                value_arg_key="root_depth_factor",
                default_value=0.95,
            ),
            SkillRoleArgMapping(
                role_name="plant_available_water_content",
                slot_arg_key="pawc_slot",
                value_arg_key="pawc_factor",
                default_value=0.9,
            ),
        ],
        stable_defaults={
            "analysis_template": "water_yield_v1",
            "seasonality_constant": 5.0,
            "root_depth_factor": 0.8,
            "pawc_factor": 0.85,
        },
    )
