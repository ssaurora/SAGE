from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import BaseModel, Field


class PlanningPass1Request(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    state_version: int = Field(ge=0)


class LogicalInputRole(BaseModel):
    role_name: str
    required: bool


class SlotSchemaItem(BaseModel):
    slot_name: str
    type: str


class SlotSchemaView(BaseModel):
    slots: list[SlotSchemaItem]


class GraphSkeleton(BaseModel):
    nodes: list[str]
    edges: list[list[str]]


class PlanningPass1Response(BaseModel):
    selected_template: str
    template_version: str
    logical_input_roles: list[LogicalInputRole]
    slot_schema_view: SlotSchemaView
    graph_skeleton: GraphSkeleton


class CognitionPassBRequest(BaseModel):
    task_id: str = Field(min_length=1)
    user_query: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    pass1_result: PlanningPass1Response


class SlotBinding(BaseModel):
    role_name: str
    slot_name: str
    source: str


class DecisionSummary(BaseModel):
    strategy: str
    assumptions: list[str]


class CognitionPassBResponse(BaseModel):
    slot_bindings: list[SlotBinding]
    args_draft: dict[str, str | int | float | bool]
    decision_summary: DecisionSummary


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


class PlanningPass2Request(BaseModel):
    task_id: str = Field(min_length=1)
    state_version: int = Field(ge=0)
    pass1_result: PlanningPass1Response
    passb_result: CognitionPassBResponse
    validation_summary: PrimitiveValidationResponse


class MaterializedExecutionNode(BaseModel):
    node_id: str
    kind: str


class MaterializedExecutionGraph(BaseModel):
    nodes: list[MaterializedExecutionNode]
    edges: list[list[str]]


class RuntimeAssertion(BaseModel):
    name: str
    required: bool
    message: str


class PlanningPass2Response(BaseModel):
    materialized_execution_graph: MaterializedExecutionGraph
    runtime_assertions: list[RuntimeAssertion]
    planning_summary: dict[str, str | int | bool]


class RepairProposalRequest(BaseModel):
    waiting_context: dict[str, object] = Field(default_factory=dict)
    validation_summary: dict[str, object] = Field(default_factory=dict)
    failure_summary: dict[str, object] = Field(default_factory=dict)
    user_note: str = ""


class RepairActionExplanation(BaseModel):
    key: str
    message: str


class RepairProposalResponse(BaseModel):
    user_facing_reason: str
    resume_hint: str
    action_explanations: list[RepairActionExplanation] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)


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
    materialized_execution_graph: MaterializedExecutionGraph
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


class ResultBundle(BaseModel):
    result_id: str
    task_id: str
    job_id: str
    summary: str
    metrics: dict[str, str | int | float | bool]
    main_outputs: list[str]
    artifacts: list[str]
    primary_outputs: list[str] = Field(default_factory=list)
    intermediate_outputs: list[str] = Field(default_factory=list)
    audit_artifacts: list[str] = Field(default_factory=list)
    derived_outputs: list[str] = Field(default_factory=list)
    created_at: datetime


class FinalExplanation(BaseModel):
    title: str
    highlights: list[str]
    narrative: str
    generated_at: datetime


class FailureSummary(BaseModel):
    failure_code: str
    failure_message: str
    created_at: datetime


class DockerRuntimeEvidence(BaseModel):
    container_name: str
    image: str
    workspace_output_path: str
    result_file_exists: bool


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
