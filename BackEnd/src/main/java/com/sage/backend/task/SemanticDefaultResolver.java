package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticDefaultResolver {
    private SemanticDefaultResolver() {
    }

    static ObjectNode buildSemanticDefaults(ObjectNode passBNode) {
        ObjectNode defaults = passBNode.objectNode();
        String caseId = resolveCaseId(passBNode);
        if (!caseId.isBlank() && !hasSemanticValue(passBNode, "seasonality_constant")) {
            defaults.put("seasonality_constant", 5.0);
        }
        return defaults;
    }

    static String resolveCaseId(JsonNode passBNode) {
        String fromUser = passBNode.path("user_semantic_args").path("case_id").asText("");
        if (!fromUser.isBlank()) {
            return fromUser;
        }
        String fromInference = passBNode.path("inferred_semantic_args").path("case_id").path("value").asText("");
        if (!fromInference.isBlank()) {
            return fromInference;
        }
        String fromArgsDraft = passBNode.path("args_draft").path("case_id").asText("");
        if (!fromArgsDraft.isBlank()) {
            return fromArgsDraft;
        }
        return "";
    }

    private static boolean hasSemanticValue(JsonNode passBNode, String key) {
        JsonNode userArgs = passBNode.path("user_semantic_args");
        if (userArgs.hasNonNull(key) && !userArgs.path(key).asText("").isBlank()) {
            return true;
        }
        JsonNode inferredArgs = passBNode.path("inferred_semantic_args");
        return inferredArgs.has(key) && !inferredArgs.path(key).path("value").asText("").isBlank();
    }
}
