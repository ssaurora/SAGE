from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel, Field, ValidationError

from app.schemas import (
    CapabilityContractSpec,
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


class SkillAssetError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


class BindingHints(BaseModel):
    provider_preference: str | None = None
    runtime_profile_preference: str | None = None


class SkillProfileAsset(BaseModel):
    skill_id: str
    skill_version: str
    analysis_type: str
    capability_key: str
    selected_template: str
    display_name: str
    supported_case_ids: list[str] = Field(default_factory=list)
    required_roles: list[str] = Field(default_factory=list)
    optional_roles: list[str] = Field(default_factory=list)
    binding_hints: BindingHints = Field(default_factory=BindingHints)
    runtime_profile_hint: str = "docker-local"


class RoleArgMappingAsset(BaseModel):
    role_name: str
    slot_arg_key: str
    value_arg_key: str | None = None
    default_value: str | int | float | bool | None = None


class SlotSpecAsset(BaseModel):
    slot_name: str
    type: str
    bound_role: str | None = None


class ParameterSchemaAsset(BaseModel):
    role_arg_mappings: list[RoleArgMappingAsset] = Field(default_factory=list)
    stable_defaults: dict[str, str | int | float | bool] = Field(default_factory=dict)
    slot_specs: list[SlotSpecAsset] = Field(default_factory=list)
    required_runtime_args: list[str] = Field(default_factory=list)
    forbidden_semantic_keys: list[str] = Field(default_factory=list)
    required_case_args: list[str] = Field(default_factory=list)


class ValidationHintAsset(BaseModel):
    role_name: str
    expected_slot_type: str | None = None


class ValidationPolicyAsset(BaseModel):
    validation_hints: list[ValidationHintAsset] = Field(default_factory=list)
    required_runtime_args: list[str] = Field(default_factory=list)
    forbidden_semantic_keys: list[str] = Field(default_factory=list)


class RepairPolicyAsset(BaseModel):
    reason_messages: dict[str, str] = Field(default_factory=dict)
    action_message_templates: dict[str, str] = Field(default_factory=dict)
    notes: list[str] = Field(default_factory=list)


class InterpretationGuideAsset(BaseModel):
    title: str
    analysis_type_label: str
    required_highlight_templates: list[str] = Field(default_factory=list)
    narrative_template: str
    limitation_note: str | None = None


class ContractSpecAsset(BaseModel):
    capability_key: str
    input_schema: str
    output_schema: str
    side_effect_level: str
    caller_scope: str
    idempotency: str
    cancel_semantics: str
    audit_requirement: str


class McpToolsMapAsset(BaseModel):
    contract_version: str
    contracts: dict[str, ContractSpecAsset] = Field(default_factory=dict)


class SkillAssetBundle(BaseModel):
    profile: SkillProfileAsset
    parameter_schema: ParameterSchemaAsset
    validation_policy: ValidationPolicyAsset
    repair_policy: RepairPolicyAsset
    interpretation_guide: InterpretationGuideAsset
    mcp_tools_map: McpToolsMapAsset


def get_skill_asset_root() -> Path:
    env_override = os.getenv("SAGE_SKILL_ASSET_ROOT")
    if env_override and env_override.strip():
        return Path(env_override).expanduser().resolve()
    return Path(__file__).resolve().parents[1] / "skills"


def load_skill_bundle(skill_id: str) -> SkillAssetBundle:
    skill_root = get_skill_asset_root() / skill_id
    if not skill_root.exists():
        raise SkillAssetError("SKILL_ASSET_UNAVAILABLE", f"Skill asset directory not found for {skill_id}.")
    return SkillAssetBundle(
        profile=_load_yaml_model(skill_root / "skill_profile.yaml", SkillProfileAsset, "SKILL_PROFILE_INVALID"),
        parameter_schema=_load_yaml_model(
            skill_root / "parameter_schema.yaml",
            ParameterSchemaAsset,
            "PARAMETER_SCHEMA_INVALID",
        ),
        validation_policy=_load_yaml_model(
            skill_root / "validation_policy.yaml",
            ValidationPolicyAsset,
            "VALIDATION_POLICY_INVALID",
        ),
        repair_policy=_load_yaml_model(skill_root / "repair_policy.yaml", RepairPolicyAsset, "REPAIR_POLICY_INVALID"),
        interpretation_guide=_load_yaml_model(
            skill_root / "interpretation_guide.yaml",
            InterpretationGuideAsset,
            "INTERPRETATION_GUIDE_INVALID",
        ),
        mcp_tools_map=_load_yaml_model(skill_root / "mcp_tools_map.yaml", McpToolsMapAsset, "MCP_TOOLS_MAP_INVALID"),
    )


def maybe_load_skill_bundle(skill_id: str) -> SkillAssetBundle | None:
    try:
        return load_skill_bundle(skill_id)
    except SkillAssetError:
        return None


def build_skill_definition_from_bundle(bundle: SkillAssetBundle) -> SkillDefinition:
    profile = bundle.profile
    parameter_schema = bundle.parameter_schema
    validation_policy = bundle.validation_policy
    repair_policy = bundle.repair_policy
    contract_fingerprint = _compute_contract_fingerprint(bundle.mcp_tools_map)
    capability = CapabilityDefinitionLite(
        capability_key=profile.capability_key,
        display_name=profile.display_name,
        contract_version=bundle.mcp_tools_map.contract_version,
        contract_fingerprint=contract_fingerprint,
        validation_hints=[
            CapabilityValidationHint(role_name=item.role_name, expected_slot_type=item.expected_slot_type)
            for item in validation_policy.validation_hints
        ],
        repair_hints=[
            _build_repair_hint(role_name)
            for role_name in [*profile.required_roles, *profile.optional_roles]
        ],
        contracts={
            contract_name: CapabilityContractSpec(
                capability_key=contract.capability_key,
                input_schema=contract.input_schema,
                output_schema=contract.output_schema,
                side_effect_level=contract.side_effect_level,
                caller_scope=contract.caller_scope,
                idempotency=contract.idempotency,
                cancel_semantics=contract.cancel_semantics,
                audit_requirement=contract.audit_requirement,
            )
            for contract_name, contract in bundle.mcp_tools_map.contracts.items()
        },
        output_contract=CapabilityOutputContract(
            outputs=[
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="watershed_results"),
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="water_yield_raster"),
                CapabilityOutputItem(artifact_role="PRIMARY_OUTPUT", logical_name="aet_raster"),
                CapabilityOutputItem(artifact_role="AUDIT_ARTIFACT", logical_name="run_manifest"),
                CapabilityOutputItem(artifact_role="AUDIT_ARTIFACT", logical_name="runtime_request"),
            ]
        ),
        runtime_profile_hint=profile.runtime_profile_hint,
    )
    return SkillDefinition(
        skill_id=profile.skill_id,
        skill_version=profile.skill_version,
        selected_template=profile.selected_template,
        capability=capability,
        required_roles=[LogicalInputRole(role_name=role_name, required=True) for role_name in profile.required_roles],
        optional_roles=[LogicalInputRole(role_name=role_name, required=False) for role_name in profile.optional_roles],
        slot_specs=[
            SkillSlotSpec(slot_name=item.slot_name, type=item.type, bound_role=item.bound_role)
            for item in parameter_schema.slot_specs
        ],
        role_arg_mappings=[
            SkillRoleArgMapping(
                role_name=item.role_name,
                slot_arg_key=item.slot_arg_key,
                value_arg_key=item.value_arg_key,
                default_value=item.default_value,
            )
            for item in parameter_schema.role_arg_mappings
        ],
        stable_defaults=parameter_schema.stable_defaults,
    )


def _compute_contract_fingerprint(mcp_tools_map: McpToolsMapAsset) -> str:
    canonical_payload = {
        "contract_version": mcp_tools_map.contract_version,
        "contracts": {
            name: contract.model_dump(mode="json")
            for name, contract in sorted(mcp_tools_map.contracts.items())
        },
    }
    canonical_json = json.dumps(canonical_payload, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()


def _build_repair_hint(role_name: str) -> CapabilityRepairHint:
    return CapabilityRepairHint(
        role_name=role_name,
        action_type="upload",
        action_key=f"upload_{role_name}",
        action_label=f"Upload {role_name.replace('_', ' ')}",
    )


def _load_yaml_model(path: Path, model_type: type[BaseModel], error_code: str) -> BaseModel:
    if not path.exists():
        raise SkillAssetError(error_code.replace("_INVALID", "_UNAVAILABLE"), f"Required skill asset file missing: {path.name}")
    try:
        with path.open("r", encoding="utf-8") as handle:
            payload: Any = yaml.safe_load(handle) or {}
    except yaml.YAMLError as exc:
        raise SkillAssetError(error_code, f"Invalid YAML in {path.name}: {exc}") from exc
    except OSError as exc:
        raise SkillAssetError(error_code.replace("_INVALID", "_UNAVAILABLE"), f"Unable to read {path.name}: {exc}") from exc
    try:
        return model_type.model_validate(payload)
    except ValidationError as exc:
        raise SkillAssetError(error_code, f"Invalid skill asset schema in {path.name}: {exc}") from exc
