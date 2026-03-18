from __future__ import annotations

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

