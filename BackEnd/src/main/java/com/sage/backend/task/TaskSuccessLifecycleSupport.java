package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.dto.JobStatusResponse;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.EventType;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class TaskSuccessLifecycleSupport {
    private TaskSuccessLifecycleSupport() {
    }

    static PromotionState enterArtifactPromoting(
            TaskState taskState,
            TaskStateMapper taskStateMapper,
            EventService eventService
    ) {
        int version = taskState.getStateVersion();
        String currentState = taskState.getCurrentState();
        if (!TaskStatus.ARTIFACT_PROMOTING.name().equals(currentState)) {
            ensureUpdated(taskStateMapper.updateState(taskState.getTaskId(), version, TaskStatus.ARTIFACT_PROMOTING.name()));
            eventService.appendEvent(
                    taskState.getTaskId(),
                    EventType.STATE_CHANGED.name(),
                    currentState,
                    TaskStatus.ARTIFACT_PROMOTING.name(),
                    version + 1,
                    null
            );
            version += 1;
            currentState = TaskStatus.ARTIFACT_PROMOTING.name();
        }
        return new PromotionState(version, currentState);
    }

    static void persistSuccessProjection(
            TaskState taskState,
            JobRecord jobRecord,
            JobStatusResponse status,
            JsonNode finalExplanationNode,
            TaskSuccessProjectionSupport.SuccessProjection successProjection,
            int stateVersion,
            TaskStateMapper taskStateMapper,
            JobRecordMapper jobRecordMapper,
            WorkspaceTraceService workspaceTraceService,
            EventService eventService,
            ObjectMapper objectMapper
    ) throws Exception {
        workspaceTraceService.persistSuccess(
                taskState,
                jobRecord,
                status.getResultBundle(),
                finalExplanationNode,
                status.getArtifactCatalog()
        );
        jobRecordMapper.updateFinalExplanation(
                jobRecord.getJobId(),
                writeJsonIfPresent(finalExplanationNode, objectMapper),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        taskStateMapper.updateOutputSummaries(
                taskState.getTaskId(),
                successProjection.resultBundleSummary(),
                successProjection.finalExplanationSummary(),
                null,
                successProjection.resultObjectSummary()
        );
        appendSuccessOutputEvents(
                taskState.getTaskId(),
                stateVersion,
                new SuccessOutputSummaries(
                        successProjection.resultBundleSummary(),
                        successProjection.finalExplanationSummary(),
                        successProjection.resultObjectSummary()
                ),
                eventService
        );
    }

    static void markArtifactPromotionCorrupted(
            TaskState taskState,
            PromotionState promotionState,
            Exception exception,
            TaskStateMapper taskStateMapper,
            EventService eventService
    ) {
        ensureUpdated(taskStateMapper.markCorrupted(
                taskState.getTaskId(),
                promotionState.stateVersion(),
                TaskStatus.STATE_CORRUPTED.name(),
                "ARTIFACT_PROMOTION_FAILED: " + safeString(exception.getMessage()),
                OffsetDateTime.now(ZoneOffset.UTC),
                taskState.getResumeTxnJson()
        ));
        eventService.appendEvent(
                taskState.getTaskId(),
                EventType.STATE_CHANGED.name(),
                promotionState.currentState(),
                TaskStatus.STATE_CORRUPTED.name(),
                promotionState.stateVersion() + 1,
                null
        );
    }

    static void completeSuccessPromotion(
            String taskId,
            PromotionState promotionState,
            TaskStateMapper taskStateMapper,
            EventService eventService
    ) {
        ensureUpdated(taskStateMapper.updateState(taskId, promotionState.stateVersion(), TaskStatus.SUCCEEDED.name()));
        eventService.appendEvent(
                taskId,
                EventType.STATE_CHANGED.name(),
                promotionState.currentState(),
                TaskStatus.SUCCEEDED.name(),
                promotionState.stateVersion() + 1,
                null
        );
    }

    private static void appendSuccessOutputEvents(
            String taskId,
            int stateVersion,
            SuccessOutputSummaries outputSummaries,
            EventService eventService
    ) {
        eventService.appendEvent(
                taskId,
                EventType.RESULT_BUNDLE_READY.name(),
                null,
                null,
                stateVersion,
                outputSummaries.resultBundleSummary()
        );
        eventService.appendEvent(
                taskId,
                EventType.FINAL_EXPLANATION_STARTED.name(),
                null,
                null,
                stateVersion,
                null
        );
        eventService.appendEvent(
                taskId,
                EventType.FINAL_EXPLANATION_COMPLETED.name(),
                null,
                null,
                stateVersion,
                outputSummaries.finalExplanationSummary()
        );
        if (outputSummaries.resultObjectSummary() != null) {
            eventService.appendEvent(
                    taskId,
                    EventType.RESULT_OBJECT_READY.name(),
                    null,
                    null,
                    stateVersion,
                    outputSummaries.resultObjectSummary()
            );
        }
    }

    private static String writeJsonIfPresent(JsonNode value, ObjectMapper objectMapper) throws Exception {
        if (value == null || value.isNull()) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    private static void ensureUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("State version conflict");
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    record PromotionState(
            int stateVersion,
            String currentState
    ) {
    }

    private record SuccessOutputSummaries(
            String resultBundleSummary,
            String finalExplanationSummary,
            String resultObjectSummary
    ) {
    }
}
