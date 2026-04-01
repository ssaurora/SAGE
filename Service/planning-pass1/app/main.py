from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from app.planner import (
    build_goal_route_response,
    build_pass1_response,
    build_pass2_response,
    build_passb_response,
    build_primitive_validation_response,
)
from app.explanation import build_final_explanation_response
from app.repair import build_repair_proposal_response
from app.runtime import JobRuntimeManager
from app.schemas import (
    CancelJobRequest,
    CancelJobResponse,
    CognitionFinalExplanationRequest,
    FinalExplanation,
    CognitionGoalRouteRequest,
    CognitionGoalRouteResponse,
    CognitionPassBRequest,
    CognitionPassBResponse,
    CreateJobRequest,
    CreateJobResponse,
    JobStatusResponse,
    PlanningPass1Request,
    PlanningPass1Response,
    PlanningPass2Request,
    PlanningPass2Response,
    PrimitiveValidationRequest,
    PrimitiveValidationResponse,
    RepairProposalRequest,
    RepairProposalResponse,
)
from app.workspace import ensure_workspace_directories


runtime_manager = JobRuntimeManager()


@asynccontextmanager
async def lifespan(_: FastAPI):
    ensure_workspace_directories()
    yield


app = FastAPI(title="SAGE Planning & Execution Service", version="0.3.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/cognition/goal-route", response_model=CognitionGoalRouteResponse)
def cognition_goal_route(payload: CognitionGoalRouteRequest) -> CognitionGoalRouteResponse:
    return build_goal_route_response(payload)


@app.post("/planning/pass1", response_model=PlanningPass1Response)
def planning_pass1(payload: PlanningPass1Request) -> PlanningPass1Response:
    return build_pass1_response(payload)


@app.post("/cognition/passb", response_model=CognitionPassBResponse)
def cognition_passb(payload: CognitionPassBRequest) -> CognitionPassBResponse:
    return build_passb_response(payload)


@app.post("/validate/primitive", response_model=PrimitiveValidationResponse)
def validate_primitive(payload: PrimitiveValidationRequest) -> PrimitiveValidationResponse:
    return build_primitive_validation_response(payload)


@app.post("/planning/pass2", response_model=PlanningPass2Response)
def planning_pass2(payload: PlanningPass2Request) -> PlanningPass2Response:
    return build_pass2_response(payload)


@app.post("/repair/proposal", response_model=RepairProposalResponse)
def repair_proposal(payload: RepairProposalRequest) -> RepairProposalResponse:
    return build_repair_proposal_response(payload)


@app.post("/cognition/final-explanation", response_model=FinalExplanation)
def cognition_final_explanation(payload: CognitionFinalExplanationRequest) -> FinalExplanation:
    return build_final_explanation_response(payload)


@app.post("/jobs", response_model=CreateJobResponse)
def create_job(payload: CreateJobRequest) -> CreateJobResponse:
    return runtime_manager.create_job(payload)


@app.get("/jobs/{job_id}", response_model=JobStatusResponse)
def get_job(job_id: str) -> JobStatusResponse:
    try:
        return runtime_manager.get_job(job_id)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="job not found") from exc


@app.post("/jobs/{job_id}/cancel", response_model=CancelJobResponse)
def cancel_job(job_id: str, payload: CancelJobRequest) -> CancelJobResponse:
    try:
        response = runtime_manager.cancel_job(job_id, payload.reason)
    except KeyError as exc:
        raise HTTPException(status_code=404, detail="job not found") from exc

    if not response.accepted:
        raise HTTPException(status_code=409, detail="job already terminal")
    return response
