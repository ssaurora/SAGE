package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;

public final class CapabilityContractGuard {

    private CapabilityContractGuard() {
    }

    public static void requireResumeAckContract(JsonNode pass1Node) {
        requireContract(pass1Node, "checkpoint_resume_ack", "control_only", "workflow_checkpoint");
    }

    public static void requireValidationContracts(JsonNode pass1Node) {
        requireContract(pass1Node, "validate_bindings", "control_or_planning", "read_only");
        requireContract(pass1Node, "validate_args", "control_or_planning", "read_only");
    }

    public static void requireSubmitJobContract(JsonNode pass1Node) {
        requireContract(pass1Node, "submit_job", "control_only", "runtime_submission");
    }

    public static void requireQueryJobStatusContract(JsonNode pass1Node) {
        requireContract(pass1Node, "query_job_status", "control_or_presentation", "read_only");
    }

    public static void requireCollectResultBundleContract(JsonNode pass1Node) {
        requireContract(pass1Node, "collect_result_bundle", "control_only", "artifact_collection");
    }

    private static void requireContract(
            JsonNode pass1Node,
            String contractName,
            String expectedCallerScope,
            String expectedSideEffectLevel
    ) {
        JsonNode contractsNode = pass1Node == null
                ? null
                : pass1Node.path("capability_facts").path("contracts");
        JsonNode contractNode = contractsNode == null ? null : contractsNode.path(contractName);
        if (contractNode == null || contractNode.isMissingNode() || contractNode.isNull()) {
            throw new IllegalStateException("CAPABILITY_CONTRACT_UNAVAILABLE: " + contractName);
        }
        String callerScope = safeString(contractNode.path("caller_scope").asText(null));
        if (!expectedCallerScope.equals(callerScope)) {
            throw new IllegalStateException("CAPABILITY_CONTRACT_MISMATCH: " + contractName + " caller_scope");
        }
        String sideEffectLevel = safeString(contractNode.path("side_effect_level").asText(null));
        if (!expectedSideEffectLevel.equals(sideEffectLevel)) {
            throw new IllegalStateException("CAPABILITY_CONTRACT_MISMATCH: " + contractName + " side_effect_level");
        }
        if (safeString(contractNode.path("input_schema").asText(null)).isBlank()) {
            throw new IllegalStateException("CAPABILITY_CONTRACT_MISMATCH: " + contractName + " input_schema");
        }
        if (safeString(contractNode.path("output_schema").asText(null)).isBlank()) {
            throw new IllegalStateException("CAPABILITY_CONTRACT_MISMATCH: " + contractName + " output_schema");
        }
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }
}
