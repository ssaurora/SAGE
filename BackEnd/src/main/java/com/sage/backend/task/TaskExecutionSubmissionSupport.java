package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.audit.AuditService;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.mapper.AnalysisManifestMapper;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.EventType;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.JobState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.dto.Pass2Response;

import java.time.OffsetDateTime;

final class TaskExecutionSubmissionSupport {
    private TaskExecutionSubmissionSupport() {
    }

    static JobRecord persistAcceptedJobAttempt(
            String taskId,
            int attemptNo,
            CreateJobResponse createJobResponse,
            Pass2Response pass2Response,
            String workspaceId,
            RegistryService.ProviderResolution providerResolution,
            JobRecordMapper jobRecordMapper,
            WorkspaceTraceService workspaceTraceService,
            ObjectMapper objectMapper
    ) {
        JobRecord jobRecord = new JobRecord();
        jobRecord.setJobId(createJobResponse.getJobId());
        jobRecord.setTaskId(taskId);
        jobRecord.setAttemptNo(attemptNo);
        jobRecord.setJobState(createJobResponse.getJobState());
        jobRecord.setExecutionGraphJson(writeJsonIfPresent(TaskProjectionBuilder.buildExecutionGraphPayload(
                pass2Response.getMaterializedExecutionGraph(),
                objectMapper
        ), objectMapper));
        jobRecord.setRuntimeAssertionsJson(writeJsonIfPresent(TaskProjectionBuilder.buildRuntimeAssertionsPayload(
                pass2Response.getRuntimeAssertions(),
                objectMapper
        ), objectMapper));
        jobRecord.setPlanningPass2SummaryJson(writeJsonIfPresent(TaskProjectionBuilder.buildPlanningPass2SummaryPayload(
                pass2Response.getPlanningSummary(),
                objectMapper
        ), objectMapper));
        jobRecord.setWorkspaceId(workspaceId);
        jobRecord.setProviderKey(providerResolution.providerKey());
        jobRecord.setCapabilityKey(providerResolution.capabilityKey());
        jobRecord.setRuntimeProfile(providerResolution.runtimeProfile());
        jobRecord.setAcceptedAt(createJobResponse.getAcceptedAt());
        jobRecord.setLastHeartbeatAt(createJobResponse.getAcceptedAt());
        ensureInserted(jobRecordMapper.insert(jobRecord));
        workspaceTraceService.createWorkspaceRecord(
                workspaceId,
                taskId,
                createJobResponse.getJobId(),
                attemptNo,
                providerResolution.runtimeProfile()
        );
        return jobRecord;
    }

    static void appendAcceptedJobEvents(
            String taskId,
            int stateVersion,
            String jobId,
            EventService eventService,
            ObjectMapper objectMapper
    ) {
        String payloadJson = writeJson(TaskControlPayloadBuilder.buildJobReferencePayload(jobId), objectMapper);
        eventService.appendEvent(taskId, EventType.JOB_SUBMITTED.name(), null, null, stateVersion, payloadJson);
        eventService.appendEvent(taskId, EventType.JOB_STATE_CHANGED.name(), null, JobState.ACCEPTED.name(), stateVersion, payloadJson);
    }

    static void freezeManifestOnCommit(
            AnalysisManifest manifest,
            int stateVersion,
            AnalysisManifestMapper analysisManifestMapper,
            EventService eventService,
            ObjectMapper objectMapper
    ) {
        ensureUpdated(analysisManifestMapper.updateFreezeStatus(
                manifest.getManifestId(),
                "CANDIDATE",
                "FROZEN"
        ));
        eventService.appendEvent(
                manifest.getTaskId(),
                EventType.ANALYSIS_MANIFEST_FROZEN.name(),
                null,
                null,
                stateVersion,
                writeJson(TaskControlPayloadBuilder.buildManifestFrozenPayload(
                        manifest.getManifestId(),
                        manifest.getAttemptNo(),
                        manifest.getManifestVersion()
                ), objectMapper)
        );
    }

    static void handlePipelineFailure(
            String taskId,
            String traceId,
            Exception exception,
            int expectedVersion,
            TaskStatus fromState,
            TaskStateMapper taskStateMapper,
            EventService eventService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        try {
            int failedVersion = expectedVersion;
            if (taskStateMapper.updateState(taskId, expectedVersion, TaskStatus.FAILED.name()) > 0) {
                failedVersion = expectedVersion + 1;
                eventService.appendEvent(taskId, EventType.STATE_CHANGED.name(), fromState.name(), TaskStatus.FAILED.name(), failedVersion, null);
            }
            eventService.appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, failedVersion, null);
            auditService.appendAudit(
                    taskId,
                    "TASK_CREATE",
                    "FAILED",
                    traceId,
                    writeJson(TaskControlPayloadBuilder.buildPipelineFailureAuditPayload(
                            exception,
                            fromState,
                            expectedVersion,
                            OffsetDateTime.now().toString()
                    ), objectMapper)
            );
        } catch (Exception ignored) {
        }
    }

    private static String writeJson(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private static String writeJsonIfPresent(JsonNode value, ObjectMapper objectMapper) {
        if (value == null || value.isNull()) {
            return null;
        }
        return writeJson(value, objectMapper);
    }

    private static void ensureUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("State version conflict");
        }
    }

    private static void ensureInserted(int insertedRows) {
        if (insertedRows != 1) {
            throw new IllegalStateException("Insert failed");
        }
    }
}
