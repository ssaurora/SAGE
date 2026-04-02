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
    CapabilityOutputItem,
    CancelJobResponse,
    CognitionMetadata,
    CreateJobRequest,
    CreateJobResponse,
    DockerRuntimeEvidence,
    ErrorObject,
    FailureSummary,
    FinalExplanation,
    JobState,
    JobStatusResponse,
    OutputReference,
    ProviderInputBinding,
    ResultBundle,
    ResultObject,
    WorkspaceSummary,
)
from app.skill_catalog import get_skill_definition
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


@dataclass
class RuntimeAssertionViolation(RuntimeError):
    assertion_id: str
    node_id: str | None
    message: str
    repairable: bool
    details: dict[str, str | int | float | bool | None]

    def __str__(self) -> str:
        return self.message


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
            self._evaluate_runtime_assertions(request, context)
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
            error_code = "JOB_RUNTIME_ERROR"
            failure = FailureSummary(failure_code="JOB_RUNTIME_ERROR", failure_message=str(exc), created_at=datetime.now(UTC))
            if isinstance(exc, RuntimeAssertionViolation):
                error_code = "ASSERTION_FAILED"
                failure = FailureSummary(
                    failure_code="ASSERTION_FAILED",
                    failure_message=exc.message,
                    created_at=datetime.now(UTC),
                    assertion_id=exc.assertion_id,
                    node_id=exc.node_id,
                    repairable=exc.repairable,
                    details=exc.details,
                )
            error = ErrorObject(error_code=error_code, message=str(exc), created_at=datetime.now(UTC))
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

        skill = get_skill_definition(request.capability_key)
        payload = json.loads(runtime_output_file.read_text(encoding="utf-8"))
        result_id = str(payload["result_id"])
        summary = str(payload["summary"])
        metrics = payload["metrics"]
        output_registry = payload.get("output_registry") if isinstance(payload.get("output_registry"), dict) else {}
        case_id = self._resolve_case_id(request)
        contract_mode = str(request.args_draft.get("contract_mode", "unknown"))
        runtime_mode = str(request.args_draft.get("runtime_mode", "unknown"))
        provider_input_bindings = self._build_provider_input_bindings(request)
        primary_output_refs = self._extract_primary_output_refs(output_registry)

        watershed_summary_payload = {
            "capability_key": request.capability_key,
            "provider_key": request.provider_key,
            "runtime_profile": request.runtime_profile,
            "case_id": case_id,
            "contract_mode": contract_mode,
            "runtime_mode": runtime_mode,
            "input_binding_count": len(provider_input_bindings),
        }
        highlights = [
            f"provider = {request.provider_key}",
            f"case_id = {case_id or '-'}",
        ]
        if "water_yield_index" in metrics and "climate_balance" in metrics:
            watershed_summary_payload.update(
                {
                    "water_yield_index": metrics["water_yield_index"],
                    "climate_balance": metrics["climate_balance"],
                }
            )
            highlights = [
                f"water_yield_index = {metrics['water_yield_index']}",
                f"climate_balance = {metrics['climate_balance']}",
                *highlights,
            ]
            narrative = (
                f"Task {request.task_id} completed a {runtime_mode} water_yield run. "
                f"The current contract mode is {contract_mode} and case_id is {case_id or '-'}. "
                f"The run used precipitation_index={metrics.get('precipitation_index', '-')} "
                f"and eto_index={metrics.get('eto_index', '-')}, producing result object {result_id}."
            )
        else:
            watershed_summary_payload.update(
                {
                    "used_real_invest": bool(metrics.get("used_real_invest", False)),
                    "output_file_count": int(metrics.get("output_file_count", len(output_registry))),
                    "file_registry_count": int(metrics.get("file_registry_count", len(output_registry))),
                    "geotiff_count": int(metrics.get("geotiff_count", 0)),
                    "vector_count": int(metrics.get("vector_count", 0)),
                    "table_count": int(metrics.get("table_count", 0)),
                    "primary_output_ref_count": len(primary_output_refs),
                }
            )
            highlights = [
                f"used_real_invest = {bool(metrics.get('used_real_invest', False))}",
                f"output_file_count = {int(metrics.get('output_file_count', len(output_registry)))}",
                f"file_registry_count = {int(metrics.get('file_registry_count', len(output_registry)))}",
                *highlights,
            ]
            narrative = (
                f"Task {request.task_id} completed a {runtime_mode} water_yield run. "
                f"The current contract mode is {contract_mode} and case_id is {case_id or '-'}. "
                f"The run produced {int(metrics.get('output_file_count', len(output_registry)))} output files, "
                f"{int(metrics.get('file_registry_count', len(output_registry)))} registered outputs, "
                f"and {len(primary_output_refs)} primary output references."
            )

        derived_summary = {"watershed_summary": watershed_summary_payload}
        derived_file = context.work_dir / "watershed_summary.json"
        derived_file.write_text(json.dumps(derived_summary, ensure_ascii=False, indent=2), encoding="utf-8")

        contract_primary_outputs = self._write_contract_primary_outputs(
            context=context,
            output_items=skill.capability.output_contract.outputs,
            result_id=result_id,
            summary=summary,
            metrics=metrics,
            output_registry=output_registry,
            task_id=request.task_id,
            job_id=job_id,
        )
        self._write_contract_audit_artifacts(
            context=context,
            output_items=skill.capability.output_contract.outputs,
            request=request,
            job_id=job_id,
            metrics=metrics,
            provider_input_bindings=provider_input_bindings,
            output_registry=output_registry,
        )

        audit_artifact_names = list(dict.fromkeys([
            *[
                f"{item.logical_name}.json"
                for item in skill.capability.output_contract.outputs
                if item.artifact_role == "AUDIT_ARTIFACT"
            ],
            "runtime_request.json",
        ]))

        result_bundle = ResultBundle(
            result_id=result_id,
            task_id=request.task_id,
            job_id=job_id,
            summary=summary,
            metrics=metrics,
            output_registry=output_registry,
            primary_output_refs=[OutputReference(output_id=ref["output_id"], path=ref["path"]) for ref in primary_output_refs],
            main_outputs=contract_primary_outputs + ["watershed_summary", *sorted(output_registry.keys())],
            artifacts=list(dict.fromkeys([
                "result_bundle.json",
                "runtime_result.json",
                "metrics.json",
                "watershed_summary.json",
                *[f"{logical_name}.json" for logical_name in contract_primary_outputs],
                *audit_artifact_names,
            ])),
            primary_outputs=["result_bundle.json", "runtime_result.json", *[f"{logical_name}.json" for logical_name in contract_primary_outputs]],
            intermediate_outputs=["metrics.json"],
            audit_artifacts=audit_artifact_names,
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
            available=True,
            title="Water Yield Result Summary",
            highlights=highlights,
            narrative=narrative,
            generated_at=datetime.now(UTC),
            cognition_metadata=CognitionMetadata(
                source="runtime_template",
                provider="deterministic",
                model=None,
                prompt_version="runtime_explanation_v1",
                fallback_used=False,
                schema_valid=True,
                response_id=None,
                status="DETERMINISTIC_BASELINE",
            ),
        )

        result_file = context.work_dir / "result_bundle.json"
        result_file.write_text(result_bundle.model_dump_json(indent=2), encoding="utf-8")
        (context.work_dir / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

        archive_summary = archive_workspace(context)
        cleanup_summary = cleanup_workspace(context)
        artifact_catalog = self._build_artifact_catalog(context, skill.capability.output_contract.outputs)
        if request.args_draft.get("simulate_promotion_failure"):
            artifact_catalog = self._duplicate_artifact_id_for_promotion_failure(artifact_catalog)
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
            provider_key=request.provider_key,
            runtime_profile=request.runtime_profile,
            case_id=case_id,
            case_descriptor_version=str(request.args_draft.get("case_descriptor_version") or "") or None,
            contract_mode=contract_mode,
            runtime_mode=runtime_mode,
            input_bindings=provider_input_bindings,
            promotion_status="READY_FOR_PROMOTION",
        )
        return result_bundle, result_object, final_explanation, docker_evidence, workspace_summary, artifact_catalog

    def _write_audit_input(self, context: WorkspaceContext, request: CreateJobRequest, job_id: str) -> None:
        provider_input_bindings = self._build_provider_input_bindings(request)
        runtime_input = {
            "task_id": request.task_id,
            "job_id": job_id,
            "workspace_id": request.workspace_id,
            "attempt_no": request.attempt_no,
            "capability_key": request.capability_key,
            "provider_key": request.provider_key,
            "runtime_profile": request.runtime_profile,
            "case_id": self._resolve_case_id(request),
            "case_descriptor_version": request.args_draft.get("case_descriptor_version"),
            "node_count": len(request.materialized_execution_graph.nodes),
            "edge_count": len(request.materialized_execution_graph.edges),
            "slot_bindings": [binding.model_dump(mode="json") for binding in request.slot_bindings],
            "provider_input_bindings": [binding.model_dump(mode="json") for binding in provider_input_bindings],
            "args_draft": {
                **request.args_draft,
                "workspace_dir": str(context.work_dir),
                "results_suffix": f"attempt_{request.attempt_no}",
            },
            "runtime_assertions": [assertion.model_dump(mode="json") for assertion in request.runtime_assertions],
            "output_dir": str(context.work_dir),
        }
        input_file = context.audit_dir / "runtime_request.json"
        input_file.write_text(json.dumps(runtime_input, ensure_ascii=False, indent=2), encoding="utf-8")

    def _evaluate_runtime_assertions(self, request: CreateJobRequest, context: WorkspaceContext) -> None:
        enriched_args = {
            **request.args_draft,
            "workspace_dir": str(context.work_dir),
            "results_suffix": f"attempt_{request.attempt_no}",
        }
        for assertion in request.runtime_assertions:
            if not assertion.required:
                continue
            if assertion.assertion_type == "required_arg":
                target_key = assertion.target_key or ""
                value = enriched_args.get(target_key)
                if value is None or (isinstance(value, str) and not value.strip()):
                    raise RuntimeAssertionViolation(
                        assertion_id=assertion.assertion_id,
                        node_id=assertion.node_id,
                        message=assertion.message,
                        repairable=assertion.repairable,
                        details={
                            **assertion.details,
                            "target_key": target_key,
                            "workspace_id": request.workspace_id,
                        },
                    )
            if assertion.assertion_type == "file_exists":
                target_key = assertion.target_key or ""
                value = enriched_args.get(target_key)
                candidate_path = None if value is None else Path(str(value))
                if candidate_path is None or not candidate_path.exists():
                    raise RuntimeAssertionViolation(
                        assertion_id=assertion.assertion_id,
                        node_id=assertion.node_id,
                        message=assertion.message,
                        repairable=assertion.repairable,
                        details={
                            **assertion.details,
                            "target_key": target_key,
                            "target_path": None if candidate_path is None else str(candidate_path),
                            "workspace_id": request.workspace_id,
                        },
                    )

    def _launch_runtime_process(self, job_id: str, request: CreateJobRequest, context: WorkspaceContext) -> None:
        input_file = context.audit_dir / "runtime_request.json"
        log_file = context.logs_dir / "runtime.log"
        log_handle = open(log_file, "w", encoding="utf-8")
        if self._use_real_invest_runner(request):
            cmd = [sys.executable, "-m", "app.invest_real_runner", str(input_file)]
        else:
            cmd = [sys.executable, "-c", self._build_deterministic_inline_code(), str(input_file)]
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

    def _use_real_invest_runner(self, request: CreateJobRequest) -> bool:
        runtime_mode = str(request.args_draft.get("runtime_mode", "")).strip().lower()
        return request.provider_key == "planning-pass1-invest-local" or runtime_mode == "invest_real_runner"

    def _build_deterministic_inline_code(self) -> str:
        return """
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
            'case_id': str(payload.get('case_id') or ''),
            'case_descriptor_version': str(args_draft.get('case_descriptor_version', '')),
            'contract_mode': str(args_draft.get('contract_mode', 'unknown')),
            'runtime_mode': str(args_draft.get('runtime_mode', 'deterministic_stub')),
            'input_binding_count': len(payload.get('provider_input_bindings', [])),
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

    def _write_contract_primary_outputs(
        self,
        context: WorkspaceContext,
        output_items: list[CapabilityOutputItem],
        result_id: str,
        summary: str,
        metrics: dict[str, str | int | float | bool],
        output_registry: dict[str, str],
        task_id: str,
        job_id: str,
    ) -> list[str]:
        primary_logical_names: list[str] = []
        for item in output_items:
            if item.artifact_role != "PRIMARY_OUTPUT":
                continue
            output_file = context.work_dir / f"{item.logical_name}.json"
            output_payload = {
                "logical_name": item.logical_name,
                "result_id": result_id,
                "task_id": task_id,
                "job_id": job_id,
                "summary": summary,
                "metrics": metrics,
                "output_registry": output_registry,
                "primary_output_refs": self._extract_primary_output_refs(output_registry),
            }
            output_file.write_text(json.dumps(output_payload, ensure_ascii=False, indent=2), encoding="utf-8")
            primary_logical_names.append(item.logical_name)
        return primary_logical_names

    def _write_contract_audit_artifacts(
        self,
        context: WorkspaceContext,
        output_items: list[CapabilityOutputItem],
        request: CreateJobRequest,
        job_id: str,
        metrics: dict[str, str | int | float | bool],
        provider_input_bindings: list[ProviderInputBinding],
        output_registry: dict[str, str],
    ) -> None:
        for item in output_items:
            if item.artifact_role != "AUDIT_ARTIFACT":
                continue
            output_file = context.audit_dir / f"{item.logical_name}.json"
            if item.logical_name == "runtime_request" and output_file.exists():
                continue
            output_payload = {
                "logical_name": item.logical_name,
                "task_id": request.task_id,
                "job_id": job_id,
                "workspace_id": request.workspace_id,
                "attempt_no": request.attempt_no,
                "capability_key": request.capability_key,
                "provider_key": request.provider_key,
                "runtime_profile": request.runtime_profile,
                "case_id": self._resolve_case_id(request),
                "case_descriptor_version": request.args_draft.get("case_descriptor_version"),
                "contract_mode": request.args_draft.get("contract_mode"),
                "runtime_mode": request.args_draft.get("runtime_mode"),
                "slot_bindings": [binding.model_dump(mode="json") for binding in request.slot_bindings],
                "provider_input_bindings": [binding.model_dump(mode="json") for binding in provider_input_bindings],
                "output_registry": output_registry,
                "metrics_snapshot": metrics,
            }
            output_file.write_text(json.dumps(output_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    def _build_provider_input_bindings(self, request: CreateJobRequest) -> list[ProviderInputBinding]:
        skill = get_skill_definition(request.capability_key)
        mapping_by_role = {mapping.role_name: mapping for mapping in skill.role_arg_mappings}
        case_id = self._resolve_case_id(request)
        bindings: list[ProviderInputBinding] = []
        seen_roles: set[str] = set()
        for binding in request.slot_bindings:
            mapping = mapping_by_role.get(binding.role_name)
            slot_arg_key = None if mapping is None else mapping.slot_arg_key
            provider_input_path = self._resolve_provider_input_path(binding.role_name, slot_arg_key, request)
            bindings.append(
                ProviderInputBinding(
                    role_name=binding.role_name,
                    slot_name=binding.slot_name,
                    source=binding.source,
                    arg_key=slot_arg_key,
                    provider_input_path=provider_input_path,
                    source_ref=self._build_source_ref(case_id, binding.role_name, binding.slot_name, provider_input_path),
                )
            )
            seen_roles.add(binding.role_name)
        for contract_input in ("watersheds", "lulc", "biophysical_table"):
            if contract_input in seen_roles:
                continue
            provider_input_path = self._resolve_provider_input_path(contract_input, None, request)
            bindings.append(
                ProviderInputBinding(
                    role_name=contract_input,
                    slot_name=contract_input,
                    source="case_contract",
                    arg_key=f"{contract_input}_path",
                    provider_input_path=provider_input_path,
                    source_ref=self._build_source_ref(case_id, contract_input, contract_input, provider_input_path),
                )
            )
        return bindings

    def _resolve_provider_input_path(
        self,
        role_name: str,
        slot_arg_key: str | None,
        request: CreateJobRequest,
    ) -> str | None:
        if slot_arg_key:
            path_arg_key = slot_arg_key[:-5] + "_path" if slot_arg_key.endswith("_slot") else None
            if path_arg_key:
                value = request.args_draft.get(path_arg_key)
                if isinstance(value, str) and value.strip():
                    return value
        value = request.args_draft.get(f"{role_name}_path")
        if isinstance(value, str) and value.strip():
            return value
        return None

    def _resolve_case_id(self, request: CreateJobRequest) -> str | None:
        if request.case_id and request.case_id.strip():
            return request.case_id.strip()
        value = request.args_draft.get("case_id")
        if isinstance(value, str) and value.strip():
            return value
        return None

    def _build_source_ref(
        self,
        case_id: str | None,
        role_name: str,
        slot_name: str | None,
        provider_input_path: str | None,
    ) -> str:
        if case_id and provider_input_path and "/sample-data/" in provider_input_path:
            return f"sample-data:{case_id}:{role_name}"
        return f"slot-binding:{slot_name or role_name}"

    def _extract_primary_output_refs(self, output_registry: dict[str, str]) -> list[dict[str, str]]:
        refs: list[dict[str, str]] = []
        preferred_tokens = ("watershed", "wyield", "water_yield")
        for output_id, path in sorted(output_registry.items()):
            normalized_id = output_id.lower()
            normalized_path = path.lower()
            if not any(token in normalized_id or token in normalized_path for token in preferred_tokens):
                continue
            refs.append({"output_id": output_id, "path": path})
        return refs

    def _build_artifact_catalog(self, context: WorkspaceContext, output_items: list[CapabilityOutputItem]) -> ArtifactCatalog:
        contract_names = self._build_contract_name_map(output_items)
        reserved_archive_names = {
            "result_bundle.json",
            "runtime_result.json",
            "metrics.json",
            *contract_names["PRIMARY_OUTPUT"].keys(),
        }
        return ArtifactCatalog(
            primary_outputs=self._collect_artifacts(
                context,
                context.archive_dir,
                "PRIMARY_OUTPUT",
                {"result_bundle.json", "runtime_result.json", *contract_names["PRIMARY_OUTPUT"].keys()},
                contract_names["PRIMARY_OUTPUT"],
            ),
            intermediate_outputs=self._collect_artifacts(context, context.archive_dir, "INTERMEDIATE_OUTPUT", {"metrics.json"}),
            audit_artifacts=self._collect_artifacts(
                context,
                context.audit_dir,
                "AUDIT_ARTIFACT",
                set(contract_names["AUDIT_ARTIFACT"].keys()) | {"runtime_request.json"},
                contract_names["AUDIT_ARTIFACT"],
            ),
            derived_outputs=self._collect_derived_outputs(context, reserved_archive_names),
            logs=self._collect_artifacts(context, context.logs_dir, "LOG", set()),
        )

    def _duplicate_artifact_id_for_promotion_failure(self, artifact_catalog: ArtifactCatalog) -> ArtifactCatalog:
        duplicate_source = None
        duplicate_target = None
        for collection in (
            artifact_catalog.primary_outputs,
            artifact_catalog.audit_artifacts,
            artifact_catalog.derived_outputs,
            artifact_catalog.logs,
        ):
            if collection and len(collection) >= 2:
                duplicate_source = collection[0]
                duplicate_target = collection[1]
                break
        if duplicate_source is not None and duplicate_target is not None:
            duplicate_target.artifact_id = duplicate_source.artifact_id
        return artifact_catalog

    def _build_contract_name_map(self, output_items: list[CapabilityOutputItem]) -> dict[str, dict[str, str]]:
        by_role: dict[str, dict[str, str]] = {
            "PRIMARY_OUTPUT": {},
            "AUDIT_ARTIFACT": {},
            "INTERMEDIATE_OUTPUT": {},
            "DERIVED_OUTPUT": {},
        }
        for item in output_items:
            by_role.setdefault(item.artifact_role, {})
            by_role[item.artifact_role][f"{item.logical_name}.json"] = item.logical_name
        return by_role

    def _collect_artifacts(
        self,
        context: WorkspaceContext,
        base_dir: Path,
        artifact_role: str,
        names: set[str],
        logical_name_overrides: dict[str, str] | None = None,
    ) -> list[ArtifactMeta]:
        items: list[ArtifactMeta] = []
        if not base_dir.exists():
            return items
        logical_name_overrides = logical_name_overrides or {}
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
                    logical_name=logical_name_overrides.get(path.name, path.name),
                    relative_path=str(path.relative_to(context.root_dir)).replace("\\", "/"),
                    content_type=_guess_content_type(path),
                    size_bytes=path.stat().st_size,
                    sha256=_sha256(path),
                )
            )
        return items

    def _collect_derived_outputs(self, context: WorkspaceContext, reserved_archive_names: set[str]) -> list[ArtifactMeta]:
        items: list[ArtifactMeta] = []
        if not context.archive_dir.exists():
            return items
        allowed_suffixes = {".json", ".tif", ".tiff", ".csv", ".gpkg", ".shp", ".geojson", ".dbf"}
        for path in sorted(context.archive_dir.rglob("*")):
            if not path.is_file():
                continue
            if path.name in reserved_archive_names:
                continue
            if path.suffix.lower() not in allowed_suffixes:
                continue
            artifact_id = f"artifact_{uuid.uuid5(uuid.NAMESPACE_URL, str(path)).hex[:16]}"
            logical_name = path.stem if path.name != "watershed_summary.json" else "watershed_summary"
            items.append(
                ArtifactMeta(
                    artifact_id=artifact_id,
                    artifact_role="DERIVED_OUTPUT",
                    logical_name=logical_name,
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
            capability_key = runtime_job.capability_key
        if context is None:
            return None, None
        archive_summary = archive_workspace(context)
        cleanup_summary = cleanup_workspace(context)
        skill = get_skill_definition(capability_key)
        artifact_catalog = self._build_artifact_catalog(context, skill.capability.output_contract.outputs)
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


