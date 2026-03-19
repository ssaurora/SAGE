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


class JobState(str, Enum):
    ACCEPTED = "ACCEPTED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class CreateJobRequest(BaseModel):
    task_id: str = Field(min_length=1)
    materialized_execution_graph: MaterializedExecutionGraph
    args_draft: dict[str, str | int | float | bool]


class ResultBundle(BaseModel):
    result_id: str
    task_id: str
    job_id: str
    summary: str
    metrics: dict[str, str | int | float | bool]
    main_outputs: list[str]
    artifacts: list[str]
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
    error_object: ErrorObject | None
