from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class PlanningPass1Request(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    capability_key: str | None = None
    selected_template: str | None = None


class CognitionMetadata(BaseModel):
    source: str = ""
    provider: str = ""
    model: str | None = None
    prompt_version: str = ""
    fallback_used: bool = False
    schema_valid: bool = True
    response_id: str | None = None
    status: str = ""
    failure_code: str | None = None
    failure_message: str | None = None


class GoalParseOutput(BaseModel):
    goal_type: str = ""
    user_query: str = ""
    analysis_kind: str = ""
    intent_mode: str = ""
    source: str = ""
    entities: list[str] = Field(default_factory=list)


class SkillRouteOutput(BaseModel):
    route_mode: str = ""
    primary_skill: str = ""
    skill_id: str = ""
    skill_version: str = ""
    analysis_type: str = ""
    capability_key: str = ""
    route_source: str = ""
    confidence: float | None = None
    selected_template: str = ""
    template_version: str = ""
    execution_mode: str = ""
    provider_preference: str | None = None
    runtime_profile_preference: str | None = None
    source: str = ""


class CaseProjection(BaseModel):
    mode: str = "unavailable"
    selected_case_id: str | None = None
    candidate_case_ids: list[str] = Field(default_factory=list)
    clarify_prompt: str | None = None
    decision_basis: list[str] = Field(default_factory=list)
    registry_version: str = ""


class CognitionGoalRouteRequest(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    user_note: str = ""
    allowed_capabilities: list[str] = Field(default_factory=list)
    allowed_templates: list[str] = Field(default_factory=list)
    known_cases: list[str] = Field(default_factory=list)


class CognitionGoalRouteResponse(BaseModel):
    planning_intent_status: str
    goal_parse: GoalParseOutput = Field(default_factory=GoalParseOutput)
    skill_route: SkillRouteOutput = Field(default_factory=SkillRouteOutput)
    case_projection: CaseProjection = Field(default_factory=CaseProjection)
    confidence: float | None = None
    decision_summary: dict[str, str | int | float | bool | list[str]] = Field(default_factory=dict)
    cognition_metadata: CognitionMetadata = Field(default_factory=CognitionMetadata)


class LogicalInputRole(BaseModel):
    role_name: str
    required: bool


class CapabilityValidationHint(BaseModel):
    role_name: str
    expected_slot_type: str | None = None


class CapabilityRepairHint(BaseModel):
    role_name: str
    action_type: str
    action_key: str
    action_label: str


class CapabilityContractSpec(BaseModel):
    capability_key: str = Field(min_length=1)
    input_schema: str = Field(min_length=1)
    output_schema: str = Field(min_length=1)
    side_effect_level: str = Field(min_length=1)
    caller_scope: str = Field(min_length=1)
    idempotency: str = Field(min_length=1)
    cancel_semantics: str = Field(min_length=1)
    audit_requirement: str = Field(min_length=1)


class CapabilityOutputItem(BaseModel):
    artifact_role: str
    logical_name: str


class CapabilityOutputContract(BaseModel):
    outputs: list[CapabilityOutputItem] = Field(default_factory=list)


class CapabilityDefinitionLite(BaseModel):
    capability_key: str = Field(min_length=1)
    display_name: str = Field(min_length=1)
    validation_hints: list[CapabilityValidationHint] = Field(default_factory=list)
    repair_hints: list[CapabilityRepairHint] = Field(default_factory=list)
    contracts: dict[str, CapabilityContractSpec] = Field(default_factory=dict)
    output_contract: CapabilityOutputContract = Field(default_factory=CapabilityOutputContract)
    runtime_profile_hint: str = Field(min_length=1)


class SkillSlotSpec(BaseModel):
    slot_name: str
    type: str
    bound_role: str | None = None


class SkillRoleArgMapping(BaseModel):
    role_name: str
    slot_arg_key: str
    value_arg_key: str | None = None
    default_value: str | int | float | bool | None = None


class SkillDefinition(BaseModel):
    skill_id: str = Field(min_length=1)
    skill_version: str = Field(min_length=1)
    selected_template: str = Field(min_length=1)
    capability: CapabilityDefinitionLite
    required_roles: list[LogicalInputRole] = Field(default_factory=list)
    optional_roles: list[LogicalInputRole] = Field(default_factory=list)
    slot_specs: list[SkillSlotSpec] = Field(default_factory=list)
    role_arg_mappings: list[SkillRoleArgMapping] = Field(default_factory=list)
    stable_defaults: dict[str, str | int | float | bool] = Field(default_factory=dict)


class SlotSchemaItem(BaseModel):
    slot_name: str
    type: str
    bound_role: str | None = None


class SlotSchemaView(BaseModel):
    slots: list[SlotSchemaItem]


class GraphSkeleton(BaseModel):
    nodes: list[str]
    edges: list[list[str]]


class PlanningPass1Response(BaseModel):
    skill_id: str = Field(min_length=1)
    skill_version: str = Field(min_length=1)
    capability_key: str = Field(min_length=1)
    capability_facts: CapabilityDefinitionLite
    selected_template: str
    template_version: str
    logical_input_roles: list[LogicalInputRole]
    role_arg_mappings: list[SkillRoleArgMapping] = Field(default_factory=list)
    stable_defaults: dict[str, str | int | float | bool] = Field(default_factory=dict)
    slot_schema_view: SlotSchemaView
    graph_skeleton: GraphSkeleton


class CognitionPassBRequest(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    pass1_result: PlanningPass1Response
    goal_parse: dict[str, object] = Field(default_factory=dict)
    skill_route: dict[str, object] = Field(default_factory=dict)
    user_note: str = ""
    attachment_facts: list[dict[str, object]] = Field(default_factory=list)
    accepted_overrides: dict[str, object] = Field(default_factory=dict)
    resume_context: dict[str, object] = Field(default_factory=dict)


class SlotBinding(BaseModel):
    role_name: str
    slot_name: str
    source: str


class DecisionSummary(BaseModel):
    strategy: str
    assumptions: list[str]


class InferredSemanticArg(BaseModel):
    value: str | int | float | bool | None = None
    reason: str
    source: str


class CognitionPassBResponse(BaseModel):
    skill_id: str = ""
    skill_version: str = ""
    binding_status: str = "resolved"
    slot_bindings: list[SlotBinding]
    user_semantic_args: dict[str, str | int | float | bool] = Field(default_factory=dict)
    inferred_semantic_args: dict[str, InferredSemanticArg] = Field(default_factory=dict)
    args_draft: dict[str, str | int | float | bool] = Field(default_factory=dict)
    case_projection: CaseProjection = Field(default_factory=CaseProjection)
    decision_summary: DecisionSummary
    confidence: float | None = None
    cognition_metadata: CognitionMetadata = Field(default_factory=CognitionMetadata)


class PrimitiveValidationRequest(BaseModel):
    task_id: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    pass1_result: PlanningPass1Response
    passb_result: CognitionPassBResponse


class PrimitiveValidationResponse(BaseModel):
    is_valid: bool
    missing_roles: list[str]
    missing_params: list[str]
    error_code: str
    invalid_bindings: list[str]


class MetadataCatalogFact(BaseModel):
    asset_id: str = Field(min_length=1)
    logical_role_candidates: list[str] = Field(default_factory=list)
    file_type: str | None = None
    crs: str | None = None
    extent: str | None = None
    resolution: str | None = None
    nodata_info: str | None = None
    source: str = ""
    checksum_version: str | None = None
    availability_status: str = "UNKNOWN"
    blacklist_flag: bool = False


class PlanningPass2Request(BaseModel):
    task_id: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    pass1_result: PlanningPass1Response
    passb_result: CognitionPassBResponse
    validation_summary: PrimitiveValidationResponse
    metadata_catalog_facts: list[MetadataCatalogFact] = Field(default_factory=list)


class MaterializedExecutionNode(BaseModel):
    node_id: str
    kind: str


class MaterializedExecutionGraph(BaseModel):
    nodes: list[MaterializedExecutionNode]
    edges: list[list[str]]


class RuntimeAssertion(BaseModel):
    assertion_id: str
    name: str
    required: bool
    message: str
    assertion_type: str
    node_id: str | None = None
    target_key: str | None = None
    expected_value: str | None = None
    repairable: bool = False
    details: dict[str, str | int | float | bool | None] = Field(default_factory=dict)


class PlanningPass2Response(BaseModel):
    materialized_execution_graph: MaterializedExecutionGraph
    runtime_assertions: list[RuntimeAssertion]
    graph_digest: str
    planning_summary: dict[str, str | int | bool]
    canonicalization_summary: dict[str, str | int | bool]
    rewrite_summary: dict[str, str | int | bool]


class MissingSlotView(BaseModel):
    slot_name: str
    expected_type: str | None = None
    required: bool = True


class RequiredUserAction(BaseModel):
    action_type: str
    key: str
    label: str
    required: bool = True


class WaitingContextView(BaseModel):
    waiting_reason_type: str = ""
    missing_slots: list[MissingSlotView] = Field(default_factory=list)
    invalid_bindings: list[str] = Field(default_factory=list)
    required_user_actions: list[RequiredUserAction] = Field(default_factory=list)
    resume_hint: str = ""
    can_resume: bool = False


class ValidationSummaryView(BaseModel):
    is_valid: bool | None = None
    missing_roles: list[str] = Field(default_factory=list)
    missing_params: list[str] = Field(default_factory=list)
    error_code: str = ""
    invalid_bindings: list[str] = Field(default_factory=list)


class FailureSummaryView(BaseModel):
    failure_code: str = ""
    failure_message: str = ""
    created_at: str | None = None


class RepairProposalRequest(BaseModel):
    waiting_context: WaitingContextView = Field(default_factory=WaitingContextView)
    validation_summary: ValidationSummaryView = Field(default_factory=ValidationSummaryView)
    failure_summary: FailureSummaryView = Field(default_factory=FailureSummaryView)
    user_note: str = ""


class RepairActionExplanation(BaseModel):
    key: str
    message: str


class RepairProposalResponse(BaseModel):
    available: bool = True
    user_facing_reason: str | None = None
    resume_hint: str | None = None
    action_explanations: list[RepairActionExplanation] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)
    failure_code: str | None = None
    failure_message: str | None = None
    cognition_metadata: CognitionMetadata = Field(default_factory=CognitionMetadata)


class JobState(str, Enum):
    ACCEPTED = "ACCEPTED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class CreateJobRequest(BaseModel):
    task_id: str = Field(min_length=1)
    workspace_id: str = Field(min_length=1)
    attempt_no: int = Field(ge=1)
    capability_key: str = Field(min_length=1)
    provider_key: str = Field(min_length=1)
    runtime_profile: str = Field(min_length=1)
    case_id: str | None = None
    materialized_execution_graph: MaterializedExecutionGraph
    runtime_assertions: list[RuntimeAssertion] = Field(default_factory=list)
    slot_bindings: list[SlotBinding] = Field(default_factory=list)
    args_draft: dict[str, str | int | float | bool]


class ArtifactMeta(BaseModel):
    artifact_id: str
    artifact_role: str
    logical_name: str
    relative_path: str
    content_type: str | None = None
    size_bytes: int | None = None
    sha256: str | None = None


class ArtifactCatalog(BaseModel):
    primary_outputs: list[ArtifactMeta] = Field(default_factory=list)
    intermediate_outputs: list[ArtifactMeta] = Field(default_factory=list)
    audit_artifacts: list[ArtifactMeta] = Field(default_factory=list)
    derived_outputs: list[ArtifactMeta] = Field(default_factory=list)
    logs: list[ArtifactMeta] = Field(default_factory=list)


class WorkspaceSummary(BaseModel):
    workspace_id: str
    workspace_output_path: str
    archive_path: str | None = None
    cleanup_completed: bool
    archive_completed: bool


class OutputReference(BaseModel):
    output_id: str
    path: str


class ResultBundle(BaseModel):
    result_id: str
    task_id: str
    job_id: str
    summary: str
    metrics: dict[str, str | int | float | bool]
    output_registry: dict[str, str] = Field(default_factory=dict)
    primary_output_refs: list[OutputReference] = Field(default_factory=list)
    main_outputs: list[str]
    artifacts: list[str]
    primary_outputs: list[str] = Field(default_factory=list)
    intermediate_outputs: list[str] = Field(default_factory=list)
    audit_artifacts: list[str] = Field(default_factory=list)
    derived_outputs: list[str] = Field(default_factory=list)
    created_at: datetime


class FinalExplanation(BaseModel):
    available: bool = True
    title: str | None = None
    highlights: list[str] = Field(default_factory=list)
    narrative: str | None = None
    generated_at: datetime | None = None
    failure_code: str | None = None
    failure_message: str | None = None
    cognition_metadata: CognitionMetadata = Field(default_factory=CognitionMetadata)


class CognitionFinalExplanationRequest(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    case_id: str | None = None
    provider_key: str | None = None
    runtime_profile: str | None = None
    result_bundle: dict[str, object] = Field(default_factory=dict)
    artifact_catalog: dict[str, object] = Field(default_factory=dict)
    docker_runtime_evidence: dict[str, object] = Field(default_factory=dict)
    workspace_summary: dict[str, object] = Field(default_factory=dict)


class FailureSummary(BaseModel):
    failure_code: str
    failure_message: str
    created_at: datetime
    assertion_id: str | None = None
    node_id: str | None = None
    repairable: bool | None = None
    details: dict[str, str | int | float | bool | None] = Field(default_factory=dict)


class ProviderInputBinding(BaseModel):
    role_name: str
    slot_name: str | None = None
    source: str | None = None
    arg_key: str | None = None
    provider_input_path: str | None = None
    source_ref: str | None = None


class DockerRuntimeEvidence(BaseModel):
    container_name: str
    image: str
    workspace_output_path: str
    result_file_exists: bool
    provider_key: str | None = None
    runtime_profile: str | None = None
    case_id: str | None = None
    case_descriptor_version: str | None = None
    contract_mode: str | None = None
    runtime_mode: str | None = None
    input_bindings: list[ProviderInputBinding] = Field(default_factory=list)
    promotion_status: str | None = None


class ResultObject(BaseModel):
    result_id: str
    task_id: str
    job_id: str
    summary: str
    artifacts: list[str]
    created_at: datetime


class ErrorObject(BaseModel):
    error_code: str
    message: str
    created_at: datetime


class CreateJobResponse(BaseModel):
    job_id: str
    job_state: JobState
    accepted_at: datetime


class CancelJobRequest(BaseModel):
    reason: str | None = None


class CancelJobResponse(BaseModel):
    job_id: str
    job_state: JobState
    accepted: bool
    cancelled_at: datetime | None
    cancel_reason: str | None


class JobStatusResponse(BaseModel):
    job_id: str
    job_state: JobState
    last_heartbeat_at: datetime | None
    started_at: datetime | None
    finished_at: datetime | None
    cancel_requested_at: datetime | None
    cancelled_at: datetime | None
    cancel_reason: str | None
    result_object: ResultObject | None
    result_bundle: ResultBundle | None
    final_explanation: FinalExplanation | None
    failure_summary: FailureSummary | None
    docker_runtime_evidence: DockerRuntimeEvidence | None
    workspace_summary: WorkspaceSummary | None = None
    artifact_catalog: ArtifactCatalog | None = None
    error_object: ErrorObject | None
