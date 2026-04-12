package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.execution.dto.JobStatusResponse;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobRecord;

final class TaskSuccessProjectionSupport {
    private TaskSuccessProjectionSupport() {
    }

    static SuccessProjection buildSuccessProjection(
            JobStatusResponse status,
            JsonNode finalExplanation,
            ObjectMapper objectMapper
    ) throws Exception {
        return new SuccessProjection(
                writePayload(TaskProjectionBuilder.buildResultBundleSummary(status.getResultBundle()), objectMapper),
                writePayload(TaskProjectionBuilder.buildFinalExplanationSummaryPayload(finalExplanation), objectMapper),
                writePayload(TaskProjectionBuilder.buildResultObjectSummaryPayload(status.getResultObject(), status.getResultBundle()), objectMapper)
        );
    }

    static boolean isRealCaseRuntime(JobRecord jobRecord, JobStatusResponse status) {
        if (jobRecord != null && "docker-invest-real".equalsIgnoreCase(jobRecord.getRuntimeProfile())) {
            return true;
        }
        JsonNode runtimeEvidence = status == null ? null : status.getDockerRuntimeEvidence();
        return runtimeEvidence != null
                && ("docker-invest-real".equalsIgnoreCase(runtimeEvidence.path("runtime_profile").asText(""))
                || !runtimeEvidence.path("case_id").asText("").isBlank());
    }

    static String resolveCaseId(AnalysisManifest manifest, JobStatusResponse status, ObjectMapper objectMapper) {
        String caseId = extractCaseId(manifest, objectMapper);
        if (caseId != null && !caseId.isBlank()) {
            return caseId;
        }
        JsonNode runtimeEvidence = status == null ? null : status.getDockerRuntimeEvidence();
        if (runtimeEvidence == null || runtimeEvidence.isNull() || runtimeEvidence.isMissingNode()) {
            return null;
        }
        String runtimeCaseId = runtimeEvidence.path("case_id").asText(null);
        return runtimeCaseId == null || runtimeCaseId.isBlank() ? null : runtimeCaseId;
    }

    static JsonNode buildUnavailableFinalExplanationNode(
            String failureCode,
            String failureMessage,
            ObjectMapper objectMapper
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("available", false);
        node.putNull("title");
        node.putArray("highlights");
        node.putNull("narrative");
        node.putNull("generated_at");
        node.put("failure_code", "EXPLANATION_UNAVAILABLE");
        node.put("failure_message", normalizeFailureMessage(failureMessage, describeExplanationFailure(failureCode)));
        ObjectNode metadata = node.putObject("cognition_metadata");
        metadata.put("source", "glm_required_failure");
        metadata.put("provider", "glm");
        metadata.putNull("model");
        metadata.put("prompt_version", "final_explanation_v1");
        metadata.put("fallback_used", false);
        metadata.put("schema_valid", false);
        metadata.putNull("response_id");
        metadata.put("status", "EXPLANATION_UNAVAILABLE");
        metadata.put("failure_code", failureCode);
        metadata.put("failure_message", normalizeFailureMessage(failureMessage, describeExplanationFailure(failureCode)));
        return node;
    }

    static String classifyCognitionException(Exception exception) {
        if (exception instanceof org.springframework.web.client.ResourceAccessException) {
            return "COGNITION_TIMEOUT";
        }
        return "COGNITION_UNAVAILABLE";
    }

    static String describeExplanationFailure(String failureCode) {
        return switch (failureCode) {
            case "COGNITION_TIMEOUT" -> "Final explanation cognition timed out.";
            case "COGNITION_SCHEMA_INVALID" -> "Final explanation cognition returned invalid schema.";
            case "COGNITION_POLICY_VIOLATION" -> "Final explanation cognition did not satisfy the real-case LLM policy.";
            default -> "Final explanation cognition unavailable.";
        };
    }

    static String normalizeFailureMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String extractCaseId(AnalysisManifest manifest, ObjectMapper objectMapper) {
        if (manifest == null || manifest.getArgsDraftJson() == null || manifest.getArgsDraftJson().isBlank()) {
            return null;
        }
        try {
            JsonNode argsDraft = objectMapper.readTree(manifest.getArgsDraftJson());
            if (argsDraft == null || argsDraft.isNull() || argsDraft.isMissingNode()) {
                return null;
            }
            String caseId = argsDraft.path("case_id").asText(null);
            return caseId == null || caseId.isBlank() ? null : caseId;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String writePayload(Object value, ObjectMapper objectMapper) throws Exception {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
    }

    record SuccessProjection(
            String resultBundleSummary,
            String finalExplanationSummary,
            String resultObjectSummary
    ) {
    }
}
