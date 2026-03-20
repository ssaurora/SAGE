from __future__ import annotations

import hashlib
import json
import os
import signal
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from app.redis_runtime import RedisRuntimeCoordinator
from app.schemas import (
    ArtifactCatalog,
    ArtifactMeta,
    CancelJobResponse,
    CreateJobRequest,
    CreateJobResponse,
    DockerRuntimeEvidence,
    ErrorObject,
    FailureSummary,
    FinalExplanation,
    JobState,
    JobStatusResponse,
    ResultBundle,
    ResultObject,
    WorkspaceSummary,
)
from app.workspace import (
    WorkspaceContext,
    archive_workspace,
    cleanup_workspace,
    create_workspace,
)


@dataclass
class RuntimeJob:
    job_id: str
    task_id: str
    workspace_id: str
    attempt_no: int
    capability_key: str
    provider_key: str
    runtime_profile: str
    job_state: JobState
    accepted_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    last_heartbeat_at: datetime | None = None
    cancel_requested_at: datetime | None = None
    cancelled_at: datetime | None = None
    cancel_reason: str | None = None
    result_object: ResultObject | None = None
    result_bundle: ResultBundle | None = None
    final_explanation: FinalExplanation | None = None
    failure_summary: FailureSummary | None = None
    docker_runtime_evidence: DockerRuntimeEvidence | None = None
    workspace_summary: WorkspaceSummary | None = None
    artifact_catalog: ArtifactCatalog | None = None
    error_object: ErrorObject | None = None
    runtime_pid_group: int | None = None
    runtime_process: subprocess.Popen[str] | None = None
    log_handle: object | None = None
    workspace_context: WorkspaceContext | None = None


class JobRuntimeManager:
    def __init__(self) -> None:
        self._jobs: dict[str, RuntimeJob] = {}
        self._lock = threading.Lock()
        self._redis = RedisRuntimeCoordinator()

    def create_job(self, request: CreateJobRequest) -> CreateJobResponse:
        job_id = f"job_{datetime.now(UTC).strftime('%Y%m%d%H%M%S%f')}_{uuid.uuid4().hex[:8]}"
        now = datetime.now(UTC)
        runtime_job = RuntimeJob(
            job_id=job_id,
            task_id=request.task_id,
            workspace_id=request.workspace_id,
            attempt_no=request.attempt_no,
            capability_key=request.capability_key,
            provider_key=request.provider_key,
            runtime_profile=request.runtime_profile,
            job_state=JobState.ACCEPTED,
            accepted_at=now,
            last_heartbeat_at=now,
        )

        with self._lock:
            self._jobs[job_id] = runtime_job

        self._redis.seed_lease(job_id)
        worker = threading.Thread(target=self._run_job, args=(job_id, request), daemon=True)
        worker.start()
        return CreateJobResponse(job_id=job_id, job_state=JobState.ACCEPTED, accepted_at=now)

    def cancel_job(self, job_id: str, reason: str | None) -> CancelJobResponse:
        cancel_reason = reason.strip() if reason and reason.strip() else "USER_REQUESTED"

        with self._lock:
            runtime_job = self._jobs.get(job_id)
            if runtime_job is None:
                raise KeyError(job_id)

            if runtime_job.job_state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED):
                return CancelJobResponse(
                    job_id=runtime_job.job_id,
                    job_state=runtime_job.job_state,
                    accepted=False,
                    cancelled_at=runtime_job.cancelled_at,
                    cancel_reason=runtime_job.cancel_reason,
                )

            now = datetime.now(UTC)
            runtime_job.cancel_requested_at = now
            runtime_job.cancel_reason = cancel_reason
            runtime_process = runtime_job.runtime_process
            runtime_pid_group = runtime_job.runtime_pid_group
            current_state = runtime_job.job_state

        self._redis.request_cancel(job_id, cancel_reason)

        if current_state == JobState.ACCEPTED:
            self._finish_cancelled(job_id, cancel_reason)
        elif current_state == JobState.RUNNING:
            self._terminate_runtime(runtime_process, runtime_pid_group)

        with self._lock:
            latest = self._jobs[job_id]
            return CancelJobResponse(
                job_id=latest.job_id,
                job_state=latest.job_state,
                accepted=True,
                cancelled_at=latest.cancelled_at,
                cancel_reason=latest.cancel_reason,
            )

    def get_job(self, job_id: str) -> JobStatusResponse:
        with self._lock:
            runtime_job = self._jobs.get(job_id)
            if runtime_job is None:
                raise KeyError(job_id)
            return JobStatusResponse(
                job_id=runtime_job.job_id,
                job_state=runtime_job.job_state,
                last_heartbeat_at=runtime_job.last_heartbeat_at,
                started_at=runtime_job.started_at,
                finished_at=runtime_job.finished_at,
                cancel_requested_at=runtime_job.cancel_requested_at,
                cancelled_at=runtime_job.cancelled_at,
                cancel_reason=runtime_job.cancel_reason,
                result_object=runtime_job.result_object,
                result_bundle=runtime_job.result_bundle,
                final_explanation=runtime_job.final_explanation,
                failure_summary=runtime_job.failure_summary,
                docker_runtime_evidence=runtime_job.docker_runtime_evidence,
                workspace_summary=runtime_job.workspace_summary,
                artifact_catalog=runtime_job.artifact_catalog,
                error_object=runtime_job.error_object,
            )

    def _run_job(self, job_id: str, request: CreateJobRequest) -> None:
        try:
            for _ in range(3):
                time.sleep(0.2)
                if self._is_cancel_requested(job_id):
                    self._finish_cancelled(job_id, self._cancel_reason(job_id))
                    return
                self._heartbeat(job_id)

            self._transition(job_id, JobState.RUNNING)
            context = create_workspace(
                workspace_id=request.workspace_id,
                task_id=request.task_id,
                attempt_no=request.attempt_no,
                job_id=job_id,
                runtime_profile=request.runtime_profile,
            )
            self._attach_workspace(job_id, context)
            self._write_audit_input(context, request, job_id)
            self._launch_runtime_process(job_id, request, context)

            while True:
                if self._is_cancel_requested(job_id):
                    runtime_process, runtime_pid_group = self._runtime_process(job_id)
                    self._terminate_runtime(runtime_process, runtime_pid_group)
                    self._finish_cancelled(job_id, self._cancel_reason(job_id))
                    return

                runtime_process, _ = self._runtime_process(job_id)
                if runtime_process is None:
                    raise RuntimeError("runtime process is missing")

                return_code = runtime_process.poll()
                self._heartbeat(job_id)
                if return_code is not None:
                    if return_code != 0:
                        raise RuntimeError(f"runtime process exited with code {return_code}")
                    break
                time.sleep(0.2)

            result_bundle, result_object, final_explanation, docker_evidence, workspace_summary, artifact_catalog = self._collect_runtime_outputs(
                job_id, request, context
            )
            self._finish_success(job_id, result_bundle, result_object, final_explanation, docker_evidence, workspace_summary, artifact_catalog)
        except Exception as exc:
            if self._is_terminal(job_id):
                return
            if self._is_cancel_requested(job_id):
                self._finish_cancelled(job_id, self._cancel_reason(job_id))
                return
            error = ErrorObject(error_code="JOB_RUNTIME_ERROR", message=str(exc), created_at=datetime.now(UTC))
            failure = FailureSummary(failure_code="JOB_RUNTIME_ERROR", failure_message=str(exc), created_at=datetime.now(UTC))
            self._finish_failed(job_id, error, failure)

    def _collect_runtime_outputs(
        self,
        job_id: str,
        request: CreateJobRequest,
        context: WorkspaceContext,
    ) -> tuple[ResultBundle, ResultObject, FinalExplanation, DockerRuntimeEvidence, WorkspaceSummary, ArtifactCatalog]:
        runtime_output_file = context.work_dir / "runtime_result.json"
        if not runtime_output_file.exists():
            raise RuntimeError("runtime_result.json was not produced")

        payload = json.loads(runtime_output_file.read_text(encoding="utf-8"))
        result_id = str(payload["result_id"])
        summary = str(payload["summary"])
        metrics = payload["metrics"]

        derived_summary = {
            "watershed_summary": {
                "capability_key": request.capability_key,
                "provider_key": request.provider_key,
                "runtime_profile": request.runtime_profile,
                "water_yield_index": metrics["water_yield_index"],
                "climate_balance": metrics["climate_balance"],
            }
        }
        derived_file = context.work_dir / "watershed_summary.json"
        derived_file.write_text(json.dumps(derived_summary, ensure_ascii=False, indent=2), encoding="utf-8")

        result_bundle = ResultBundle(
            result_id=result_id,
            task_id=request.task_id,
            job_id=job_id,
            summary=summary,
            metrics=metrics,
            main_outputs=["water_yield_index", "climate_balance", "watershed_summary"],
            artifacts=["result_bundle.json", "metrics.json", "runtime_result.json", "watershed_summary.json"],
            primary_outputs=["result_bundle.json", "runtime_result.json"],
            intermediate_outputs=["metrics.json"],
            audit_artifacts=["runtime_request.json"],
            derived_outputs=["watershed_summary.json"],
            created_at=datetime.now(UTC),
        )

        result_object = ResultObject(
            result_id=result_id,
            task_id=request.task_id,
            job_id=job_id,
            summary=summary,
            artifacts=result_bundle.artifacts,
            created_at=result_bundle.created_at,
        )

        final_explanation = FinalExplanation(
            title="Water Yield Result Summary",
            highlights=[
                f"water_yield_index = {metrics['water_yield_index']}",
                f"climate_balance = {metrics['climate_balance']}",
                f"provider = {request.provider_key}",
            ],
            narrative=(
                f"Task {request.task_id} completed a minimal deterministic water_yield run. "
                f"The run used precipitation_index={metrics['precipitation_index']} and eto_index={metrics['eto_index']}, "
                f"producing result object {result_id}."
            ),
            generated_at=datetime.now(UTC),
        )

        result_file = context.work_dir / "result_bundle.json"
        result_file.write_text(result_bundle.model_dump_json(indent=2), encoding="utf-8")
        (context.work_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

        archive_summary = archive_workspace(context)
        cleanup_summary = cleanup_workspace(context)
        artifact_catalog = self._build_artifact_catalog(context)
        workspace_summary = WorkspaceSummary(
            workspace_id=context.workspace_id,
            workspace_output_path=str(context.work_dir),
            archive_path=archive_summary.archive_path,
            cleanup_completed=cleanup_summary.cleaned,
            archive_completed=archive_summary.archived,
        )

        docker_evidence = DockerRuntimeEvidence(
            container_name=os.getenv("HOSTNAME", "local-process"),
            image=os.getenv("RUNTIME_IMAGE", "sage-pass1:week6"),
            workspace_output_path=str(context.archive_dir),
            result_file_exists=(context.archive_dir / "result_bundle.json").exists(),
        )
        return result_bundle, result_object, final_explanation, docker_evidence, workspace_summary, artifact_catalog

    def _write_audit_input(self, context: WorkspaceContext, request: CreateJobRequest, job_id: str) -> None:
        runtime_input = {
            "task_id": request.task_id,
            "job_id": job_id,
            "workspace_id": request.workspace_id,
            "attempt_no": request.attempt_no,
            "capability_key": request.capability_key,
            "provider_key": request.provider_key,
            "runtime_profile": request.runtime_profile,
            "node_count": len(request.materialized_execution_graph.nodes),
            "edge_count": len(request.materialized_execution_graph.edges),
            "args_draft": request.args_draft,
            "output_dir": str(context.work_dir),
        }
        input_file = context.audit_dir / "runtime_request.json"
        input_file.write_text(json.dumps(runtime_input, ensure_ascii=False, indent=2), encoding="utf-8")

    def _launch_runtime_process(self, job_id: str, request: CreateJobRequest, context: WorkspaceContext) -> None:
        input_file = context.audit_dir / "runtime_request.json"
        log_file = context.logs_dir / "runtime.log"
        inline_code = """
import json, sys, time, uuid
from pathlib import Path

input_path = Path(sys.argv[1])
payload = json.loads(input_path.read_text(encoding='utf-8'))
args_draft = payload['args_draft']
output_dir = Path(payload['output_dir'])
output_dir.mkdir(parents=True, exist_ok=True)

for _ in range(4):
    time.sleep(0.2)

precipitation_index = float(args_draft.get('precipitation_index', 0.0))
eto_index = float(args_draft.get('eto_index', 0.0))
root_depth_factor = float(args_draft.get('root_depth_factor', 0.8))
pawc_factor = float(args_draft.get('pawc_factor', 0.85))
climate_balance = round(precipitation_index - eto_index, 3)
water_yield_index = round(max(0.0, climate_balance) * root_depth_factor * pawc_factor / 1000.0, 6)
summary = f"Minimal water_yield run completed for task {payload['task_id']}"

result = {
    'result_id': f"result_{uuid.uuid4().hex[:12]}",
    'summary': summary,
    'metrics': {
        'task_id': payload['task_id'],
        'job_id': payload['job_id'],
        'workspace_id': payload['workspace_id'],
        'attempt_no': int(payload['attempt_no']),
        'node_count': int(payload['node_count']),
        'edge_count': int(payload['edge_count']),
        'capability_key': str(payload['capability_key']),
        'provider_key': str(payload['provider_key']),
        'runtime_profile': str(payload['runtime_profile']),
        'analysis_template': str(args_draft.get('analysis_template', 'water_yield_v1')),
        'precipitation_index': precipitation_index,
        'eto_index': eto_index,
        'root_depth_factor': root_depth_factor,
        'pawc_factor': pawc_factor,
        'climate_balance': climate_balance,
        'water_yield_index': water_yield_index,
        'status': 'SUCCEEDED'
    }
}
(output_dir / 'runtime_result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
"""
        log_handle = open(log_file, "w", encoding="utf-8")
        cmd = [sys.executable, "-c", inline_code, str(input_file)]
        if os.name == "nt":
            process = subprocess.Popen(cmd, creationflags=subprocess.CREATE_NEW_PROCESS_GROUP, stdout=log_handle, stderr=subprocess.STDOUT, text=True)
            process_group = process.pid
        else:
            process = subprocess.Popen(cmd, start_new_session=True, stdout=log_handle, stderr=subprocess.STDOUT, text=True)
            process_group = process.pid

        with self._lock:
            runtime_job = self._jobs[job_id]
            runtime_job.runtime_process = process
            runtime_job.runtime_pid_group = process_group
            runtime_job.log_handle = log_handle

    def _build_artifact_catalog(self, context: WorkspaceContext) -> ArtifactCatalog:
        return ArtifactCatalog(
            primary_outputs=self._collect_artifacts(context, context.archive_dir, "PRIMARY_OUTPUT", {"result_bundle.json", "runtime_result.json"}),
            intermediate_outputs=self._collect_artifacts(context, context.archive_dir, "INTERMEDIATE_OUTPUT", {"metrics.json"}),
            audit_artifacts=self._collect_artifacts(context, context.audit_dir, "AUDIT_ARTIFACT", set()),
            derived_outputs=self._collect_artifacts(context, context.archive_dir, "DERIVED_OUTPUT", {"watershed_summary.json"}),
            logs=self._collect_artifacts(context, context.logs_dir, "LOG", set()),
        )

    def _collect_artifacts(self, context: WorkspaceContext, base_dir: Path, artifact_role: str, names: set[str]) -> list[ArtifactMeta]:
        items: list[ArtifactMeta] = []
        if not base_dir.exists():
            return items
        for path in sorted(base_dir.rglob("*")):
            if not path.is_file():
                continue
            if names and path.name not in names:
                continue
            artifact_id = f"artifact_{uuid.uuid5(uuid.NAMESPACE_URL, str(path)).hex[:16]}"
            items.append(
                ArtifactMeta(
                    artifact_id=artifact_id,
                    artifact_role=artifact_role,
                    logical_name=path.name,
                    relative_path=str(path.relative_to(context.root_dir)).replace("\\", "/"),
                    content_type=_guess_content_type(path),
                    size_bytes=path.stat().st_size,
                    sha256=_sha256(path),
                )
            )
        return items

    def _attach_workspace(self, job_id: str, context: WorkspaceContext) -> None:
        with self._lock:
            runtime_job = self._jobs[job_id]
            runtime_job.workspace_context = context

    def _runtime_process(self, job_id: str) -> tuple[subprocess.Popen[str] | None, int | None]:
        with self._lock:
            runtime_job = self._jobs[job_id]
            return runtime_job.runtime_process, runtime_job.runtime_pid_group

    def _close_log_handle(self, job_id: str) -> None:
        with self._lock:
            runtime_job = self._jobs[job_id]
            log_handle = runtime_job.log_handle
            runtime_job.log_handle = None
        if log_handle is not None:
            try:
                log_handle.close()
            except Exception:
                pass

    def _terminate_runtime(self, runtime_process: subprocess.Popen[str] | None, runtime_pid_group: int | None) -> None:
        if runtime_process is None:
            return
        if runtime_process.poll() is not None:
            return

        try:
            if os.name == "nt":
                runtime_process.terminate()
            elif runtime_pid_group is not None:
                os.killpg(runtime_pid_group, signal.SIGTERM)
            else:
                runtime_process.terminate()

            try:
                runtime_process.wait(timeout=1.2)
            except subprocess.TimeoutExpired:
                runtime_process.kill()
        except Exception:
            try:
                runtime_process.kill()
            except Exception:
                pass

    def _transition(self, job_id: str, state: JobState) -> None:
        now = datetime.now(UTC)
        with self._lock:
            runtime_job = self._jobs[job_id]
            runtime_job.job_state = state
            runtime_job.last_heartbeat_at = now
            if state == JobState.RUNNING:
                runtime_job.started_at = now
        self._redis.heartbeat(job_id)

    def _heartbeat(self, job_id: str) -> None:
        with self._lock:
            runtime_job = self._jobs[job_id]
            if runtime_job.job_state in (JobState.ACCEPTED, JobState.RUNNING):
                runtime_job.last_heartbeat_at = datetime.now(UTC)
        self._redis.heartbeat(job_id)

    def _finish_success(
        self,
        job_id: str,
        result_bundle: ResultBundle,
        result_object: ResultObject,
        final_explanation: FinalExplanation,
        docker_evidence: DockerRuntimeEvidence,
        workspace_summary: WorkspaceSummary,
        artifact_catalog: ArtifactCatalog,
    ) -> None:
        now = datetime.now(UTC)
        self._close_log_handle(job_id)
        with self._lock:
            runtime_job = self._jobs[job_id]
            if runtime_job.job_state == JobState.CANCELLED:
                return
            runtime_job.job_state = JobState.SUCCEEDED
            runtime_job.last_heartbeat_at = now
            runtime_job.finished_at = now
            runtime_job.result_bundle = result_bundle
            runtime_job.result_object = result_object
            runtime_job.final_explanation = final_explanation
            runtime_job.docker_runtime_evidence = docker_evidence
            runtime_job.workspace_summary = workspace_summary
            runtime_job.artifact_catalog = artifact_catalog

    def _finish_failed(self, job_id: str, error_object: ErrorObject, failure_summary: FailureSummary) -> None:
        now = datetime.now(UTC)
        self._close_log_handle(job_id)
        workspace_summary, artifact_catalog = self._finalize_workspace(job_id)
        with self._lock:
            runtime_job = self._jobs[job_id]
            if runtime_job.job_state == JobState.CANCELLED:
                return
            runtime_job.job_state = JobState.FAILED
            runtime_job.last_heartbeat_at = now
            runtime_job.finished_at = now
            runtime_job.error_object = error_object
            runtime_job.failure_summary = failure_summary
            runtime_job.workspace_summary = workspace_summary
            runtime_job.artifact_catalog = artifact_catalog

    def _finish_cancelled(self, job_id: str, reason: str) -> None:
        now = datetime.now(UTC)
        self._close_log_handle(job_id)
        context = self._ensure_workspace_context(job_id)
        self._write_cancel_artifacts(job_id, context, reason, now)
        workspace_summary, artifact_catalog = self._finalize_workspace(job_id)
        with self._lock:
            runtime_job = self._jobs[job_id]
            if runtime_job.job_state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED):
                return
            runtime_job.job_state = JobState.CANCELLED
            runtime_job.last_heartbeat_at = now
            runtime_job.finished_at = now
            runtime_job.cancel_requested_at = runtime_job.cancel_requested_at or now
            runtime_job.cancelled_at = now
            runtime_job.cancel_reason = reason
            runtime_job.failure_summary = FailureSummary(
                failure_code="JOB_CANCELLED",
                failure_message="Job was cancelled by request",
                created_at=now,
            )
            runtime_job.workspace_summary = workspace_summary
            runtime_job.artifact_catalog = artifact_catalog
            runtime_job.runtime_process = None
            runtime_job.runtime_pid_group = None

    def _ensure_workspace_context(self, job_id: str) -> WorkspaceContext:
        with self._lock:
            runtime_job = self._jobs[job_id]
            context = runtime_job.workspace_context
            if context is not None:
                return context
            context = create_workspace(
                workspace_id=runtime_job.workspace_id,
                task_id=runtime_job.task_id,
                attempt_no=runtime_job.attempt_no,
                job_id=runtime_job.job_id,
                runtime_profile=runtime_job.runtime_profile,
            )
            runtime_job.workspace_context = context
            return context

    def _write_cancel_artifacts(self, job_id: str, context: WorkspaceContext, reason: str, now: datetime) -> None:
        log_file = context.logs_dir / "runtime.log"
        with log_file.open("a", encoding="utf-8") as handle:
            handle.write(f"{now.isoformat()} CANCELLED {job_id} reason={reason}\n")

        audit_payload = {
            "task_id": context.task_id,
            "job_id": job_id,
            "workspace_id": context.workspace_id,
            "attempt_no": context.attempt_no,
            "runtime_profile": context.runtime_profile,
            "event": "JOB_CANCELLED",
            "reason": reason,
            "cancelled_at": now.isoformat(),
        }
        (context.audit_dir / "cancel.json").write_text(json.dumps(audit_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _finalize_workspace(self, job_id: str) -> tuple[WorkspaceSummary | None, ArtifactCatalog | None]:
        with self._lock:
            runtime_job = self._jobs[job_id]
            context = runtime_job.workspace_context
        if context is None:
            return None, None
        archive_summary = archive_workspace(context)
        cleanup_summary = cleanup_workspace(context)
        artifact_catalog = self._build_artifact_catalog(context)
        workspace_summary = WorkspaceSummary(
            workspace_id=context.workspace_id,
            workspace_output_path=str(context.work_dir),
            archive_path=archive_summary.archive_path,
            cleanup_completed=cleanup_summary.cleaned,
            archive_completed=archive_summary.archived,
        )
        return workspace_summary, artifact_catalog

    def _is_cancel_requested(self, job_id: str) -> bool:
        with self._lock:
            runtime_job = self._jobs[job_id]
            local_requested = runtime_job.cancel_requested_at is not None
        return local_requested or self._redis.is_cancel_requested(job_id)

    def _cancel_reason(self, job_id: str) -> str:
        with self._lock:
            runtime_job = self._jobs[job_id]
            local_reason = runtime_job.cancel_reason or "USER_REQUESTED"
        return self._redis.cancel_reason(job_id, local_reason)

    def _is_terminal(self, job_id: str) -> bool:
        with self._lock:
            runtime_job = self._jobs[job_id]
            return runtime_job.job_state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _guess_content_type(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".json":
        return "application/json"
    if suffix == ".log":
        return "text/plain"
    if suffix == ".txt":
        return "text/plain"
    return "application/octet-stream"


