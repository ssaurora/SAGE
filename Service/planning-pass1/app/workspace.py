from __future__ import annotations

import os
import shutil
from dataclasses import dataclass
from pathlib import Path


DEFAULT_WORKSPACE_ROOT = Path(__file__).resolve().parents[1] / "runtime" / "workspaces"


@dataclass(frozen=True)
class WorkspaceContext:
    workspace_id: str
    task_id: str
    attempt_no: int
    job_id: str
    runtime_profile: str
    root_dir: Path
    work_dir: Path
    archive_dir: Path
    logs_dir: Path
    audit_dir: Path


@dataclass(frozen=True)
class ArchiveSummary:
    archive_path: str
    archived: bool


@dataclass(frozen=True)
class CleanupSummary:
    cleaned: bool


@dataclass(frozen=True)
class DemolishSummary:
    demolished: bool


def resolve_workspace_root() -> Path:
    workspace_root = os.getenv("WORKSPACE_ROOT")
    if workspace_root:
        return Path(workspace_root)
    return DEFAULT_WORKSPACE_ROOT


def ensure_workspace_directories() -> None:
    resolve_workspace_root().mkdir(parents=True, exist_ok=True)


def create_workspace(
    workspace_id: str,
    task_id: str,
    attempt_no: int,
    job_id: str,
    runtime_profile: str,
) -> WorkspaceContext:
    root_dir = resolve_workspace_root() / task_id / str(attempt_no) / job_id
    work_dir = root_dir / "work"
    archive_dir = root_dir / "archive"
    logs_dir = root_dir / "logs"
    audit_dir = root_dir / "audit"

    for path in (work_dir, archive_dir, logs_dir, audit_dir):
        path.mkdir(parents=True, exist_ok=True)

    return WorkspaceContext(
        workspace_id=workspace_id,
        task_id=task_id,
        attempt_no=attempt_no,
        job_id=job_id,
        runtime_profile=runtime_profile,
        root_dir=root_dir,
        work_dir=work_dir,
        archive_dir=archive_dir,
        logs_dir=logs_dir,
        audit_dir=audit_dir,
    )


def archive_workspace(context: WorkspaceContext) -> ArchiveSummary:
    archived = False
    if context.work_dir.exists():
        for child in context.work_dir.rglob("*"):
            if child.is_dir():
                continue
            target = context.archive_dir / child.relative_to(context.work_dir)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(child, target)
            archived = True
    return ArchiveSummary(archive_path=str(context.archive_dir), archived=archived)


def cleanup_workspace(context: WorkspaceContext) -> CleanupSummary:
    if context.work_dir.exists():
        shutil.rmtree(context.work_dir, ignore_errors=True)
    return CleanupSummary(cleaned=not context.work_dir.exists())


def demolish_workspace(context: WorkspaceContext) -> DemolishSummary:
    if context.root_dir.exists():
        shutil.rmtree(context.root_dir, ignore_errors=True)
    return DemolishSummary(demolished=not context.root_dir.exists())

