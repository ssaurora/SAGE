from __future__ import annotations

import os
from pathlib import Path
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.planner import build_pass1_response
from app.schemas import PlanningPass1Request, PlanningPass1Response


DEFAULT_WORKSPACE_ROOT = Path("/workspace")
WORKSPACE_SUBDIRS = ("input", "output", "logs")


def resolve_workspace_root() -> Path:
    workspace_root = os.getenv("WORKSPACE_ROOT")
    if workspace_root:
        return Path(workspace_root)
    return DEFAULT_WORKSPACE_ROOT


def ensure_workspace_directories() -> None:
    root = resolve_workspace_root()
    root.mkdir(parents=True, exist_ok=True)
    for subdir in WORKSPACE_SUBDIRS:
        (root / subdir).mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(_: FastAPI):
    ensure_workspace_directories()
    yield


app = FastAPI(title="SAGE Planning Pass1 Service", version="0.1.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/planning/pass1", response_model=PlanningPass1Response)
def planning_pass1(payload: PlanningPass1Request) -> PlanningPass1Response:
    return build_pass1_response(payload)
