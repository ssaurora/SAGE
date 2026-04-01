package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;

final class CognitionVerdictResolver {
    private CognitionVerdictResolver() {
    }

    static String resolve(JsonNode goalParseNode, JsonNode passBNode, ExecutionContractAssembler.AssemblyResult assemblyResult) {
        String planningIntentStatus = goalParseNode.path("planning_intent_status").asText("");
        JsonNode goalMetadata = goalParseNode.path("cognition_metadata");
        JsonNode passbMetadata = passBNode.path("cognition_metadata");
        String goalMetadataVerdict = resolveMetadataVerdict(goalMetadata);
        if (goalMetadataVerdict != null) {
            return goalMetadataVerdict;
        }
        String passbMetadataVerdict = resolveMetadataVerdict(passbMetadata);
        if (passbMetadataVerdict != null) {
            return passbMetadataVerdict;
        }
        if ("ambiguous".equalsIgnoreCase(planningIntentStatus)) {
            return "LLM_AMBIGUOUS";
        }
        if (assemblyResult.assemblyBlocked() || !assemblyResult.overruledFields().isEmpty()) {
            return "LLM_PRIMARY_OVERRULED";
        }
        if ("ambiguous".equalsIgnoreCase(passBNode.path("binding_status").asText(""))) {
            return "LLM_AMBIGUOUS";
        }
        return "LLM_PRIMARY";
    }

    private static String resolveMetadataVerdict(JsonNode metadata) {
        if (metadata == null || metadata.isNull() || metadata.isMissingNode()) {
            return null;
        }
        String provider = metadata.path("provider").asText("");
        String status = metadata.path("status").asText("");
        String failureCode = metadata.path("failure_code").asText("");
        boolean fallbackUsed = metadata.path("fallback_used").asBoolean(false);
        boolean schemaValid = !metadata.path("schema_valid").isBoolean() || metadata.path("schema_valid").asBoolean(true);

        if ("deterministic".equalsIgnoreCase(provider)) {
            return "DETERMINISTIC_BASELINE";
        }
        if (fallbackUsed || "COGNITION_POLICY_VIOLATION".equalsIgnoreCase(status)) {
            return "LLM_POLICY_VIOLATION";
        }
        if (!schemaValid || "COGNITION_SCHEMA_INVALID".equalsIgnoreCase(status) || "COGNITION_SCHEMA_INVALID".equalsIgnoreCase(failureCode)) {
            return "LLM_UNAVAILABLE";
        }
        if ("COGNITION_UNAVAILABLE".equalsIgnoreCase(status)
                || "COGNITION_TIMEOUT".equalsIgnoreCase(status)
                || "COGNITION_UNAVAILABLE".equalsIgnoreCase(failureCode)
                || "COGNITION_TIMEOUT".equalsIgnoreCase(failureCode)) {
            return "LLM_UNAVAILABLE";
        }
        return null;
    }
}
