package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TaskControlPayloadBuilder {
    private TaskControlPayloadBuilder() {
    }

    static Map<String, Object> buildJobReferencePayload(String jobId) {
        return Map.of("job_id", jobId);
    }

    static Map<String, Object> buildDispatcherOutputPayload(String severity, String routing) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("severity", severity);
        payload.put("routing", routing);
        return payload;
    }

    static Map<String, Object> buildValidationEventPayload(PrimitiveValidationResponse validationResponse) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_code", safeString(validationResponse.getErrorCode()));
        payload.put("missing_roles", safeList(validationResponse.getMissingRoles()));
        payload.put("missing_params", safeList(validationResponse.getMissingParams()));
        return payload;
    }

    static Map<String, Object> buildFatalValidationFailureSummaryPayload(
            PrimitiveValidationResponse validationResponse,
            String createdAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failure_code", "FATAL_VALIDATION");
        payload.put("failure_message", "Task validation is fatal and not recoverable in Week5.");
        payload.put("error_code", safeString(validationResponse.getErrorCode()));
        payload.put("invalid_bindings", safeList(validationResponse.getInvalidBindings()));
        payload.put("missing_roles", safeList(validationResponse.getMissingRoles()));
        payload.put("missing_params", safeList(validationResponse.getMissingParams()));
        payload.put("created_at", createdAt);
        return payload;
    }

    static Map<String, Object> buildQueuedAttemptSnapshotPayload(String jobId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", TaskStatus.QUEUED.name());
        payload.put("job_id", jobId);
        return payload;
    }

    static Map<String, Object> buildPass1CompletedPayload(String selectedTemplate) {
        return Map.of("selected_template", safeString(selectedTemplate));
    }

    static Map<String, Object> buildTaskCreateAuditPayload(
            String state,
            boolean validationIsValid,
            String inputChainStatus,
            boolean jobCreated,
            String jobId,
            String failureCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", state);
        payload.put("validation_is_valid", validationIsValid);
        payload.put("input_chain_status", inputChainStatus);
        payload.put("job_created", jobCreated);
        if (jobId != null && !jobId.isBlank()) {
            payload.put("job_id", jobId);
        }
        if (failureCode != null && !failureCode.isBlank()) {
            payload.put("failure_code", failureCode);
        }
        return payload;
    }

    static Map<String, Object> buildAttachmentUploadedPayload(String attachmentId, TaskAttachment attachment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachment_id", attachmentId);
        payload.put("logical_slot", safeString(attachment.getLogicalSlot()));
        payload.put("assignment_status", attachment.getAssignmentStatus());
        return payload;
    }

    static Map<String, Object> buildResumeRequestEventPayload(String resumeRequestId) {
        return Map.of("resume_request_id", resumeRequestId);
    }

    static Map<String, Object> buildResumeRejectedEventPayload(String resumeRequestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resume_request_id", resumeRequestId);
        payload.put("reason", "required actions not satisfied");
        return payload;
    }

    static Map<String, Object> buildResumeAcceptedEventPayload(String resumeRequestId, int resumeAttempt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resume_request_id", resumeRequestId);
        payload.put("resume_attempt", resumeAttempt);
        return payload;
    }

    static Map<String, Object> buildResumeDeactivatedAttemptSnapshotPayload(String currentTaskState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", currentTaskState);
        payload.put("deactivated_by", "resume");
        return payload;
    }

    static Map<String, Object> buildValidatingAttemptSnapshotPayload(String resumeRequestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", TaskStatus.VALIDATING.name());
        payload.put("resume_request_id", resumeRequestId);
        return payload;
    }

    static Map<String, Object> buildCancelledJobEventPayload(String jobId, String cancelReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_id", jobId);
        payload.put("cancel_reason", safeString(cancelReason));
        return payload;
    }

    static Map<String, Object> buildAttemptRuntimeSnapshotPayload(String jobState, String taskState) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("job_state", jobState);
        payload.put("task_state", safeString(taskState));
        return payload;
    }

    static Map<String, Object> buildPassBCompletionPayload(JsonNode passBNode, boolean resume) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("binding_count", passBNode.path("slot_bindings").isArray() ? passBNode.path("slot_bindings").size() : 0);
        payload.put("resume", resume);
        return payload;
    }

    static Map<String, Object> buildPass2CompletedPayload(JsonNode executionGraphNode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "node_count",
                executionGraphNode != null && executionGraphNode.path("nodes").isArray()
                        ? executionGraphNode.path("nodes").size()
                        : 0
        );
        return payload;
    }

    static Map<String, Object> buildManifestFrozenPayload(String manifestId, int attemptNo, Integer manifestVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("manifest_id", manifestId);
        payload.put("attempt_no", attemptNo);
        payload.put("manifest_version", manifestVersion);
        return payload;
    }

    static Map<String, Object> buildPipelineFailureAuditPayload(
            Exception exception,
            TaskStatus fromState,
            int expectedVersion,
            String at
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", exception.getClass().getSimpleName());
        payload.put("message", safeString(exception.getMessage()));
        payload.put("at", at);
        payload.put("from_state", fromState.name());
        payload.put("expected_state_version", expectedVersion);
        return payload;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
