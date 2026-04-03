package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.planning.Pass1FactHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class ExecutionContractAssembler {
    private final ObjectMapper objectMapper;

    ExecutionContractAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AssemblyResult assemble(String taskId, JsonNode skillRouteNode, JsonNode pass1Node, ObjectNode passBNode) {
        ArrayNode slotBindings = passBNode.withArray("slot_bindings");
        ObjectNode userSemanticArgs = ensureObjectNode(passBNode, "user_semantic_args");
        ObjectNode inferredSemanticArgs = ensureObjectNode(passBNode, "inferred_semantic_args");
        ObjectNode semanticDefaults = SemanticDefaultResolver.buildSemanticDefaults(passBNode, pass1Node);
        passBNode.set("semantic_defaults", semanticDefaults);

        List<String> blockedMutations = new ArrayList<>();
        List<String> overruledFields = new ArrayList<>();
        boolean assemblyBlocked = false;
        if (!"resolved".equalsIgnoreCase(safeText(passBNode.path("binding_status")))) {
            blockedMutations.add("binding_status");
            assemblyBlocked = true;
        }
        if (slotBindings.isEmpty()) {
            blockedMutations.add("slot_bindings");
            assemblyBlocked = true;
        }

        String caseId = SemanticDefaultResolver.resolveCaseId(passBNode);
        if (caseId.isBlank()) {
            blockedMutations.add("case_id");
            assemblyBlocked = true;
        }

        String executionMode = safeText(skillRouteNode.path("execution_mode"));
        boolean realCase = "real_case_validation".equalsIgnoreCase(executionMode);
        ObjectNode argsDraft = ensureObjectNode(passBNode, "args_draft");

        if (!argsDraft.has("analysis_template")) {
            argsDraft.put("analysis_template", Pass1FactHelper.resolveAnalysisTemplate(pass1Node));
        }
        if (!caseId.isBlank()) {
            argsDraft.put("case_id", caseId);
        }
        if (!argsDraft.has("contract_mode")) {
            argsDraft.put("contract_mode", realCase ? "invest_real_case_v1" : "governed_baseline_v1");
        }
        if (!argsDraft.has("runtime_mode")) {
            argsDraft.put("runtime_mode", realCase ? "invest_real_runner" : "deterministic_stub");
        }
        if (!argsDraft.has("workspace_dir")) {
            argsDraft.put("workspace_dir", "/workspace/output/" + taskId.replace("/", "_"));
        }
        if (!argsDraft.has("results_suffix")) {
            argsDraft.put("results_suffix", caseId.isBlank() ? "baseline" : caseId);
        }
        if (!argsDraft.has("n_workers")) {
            argsDraft.put("n_workers", 1);
        }

        copyScalarIfPresent(userSemanticArgs, argsDraft, "simulate_promotion_failure");
        copyScalarIfPresent(userSemanticArgs, argsDraft, "simulate_assertion_failure");
        copyScalarIfPresent(userSemanticArgs, argsDraft, "seasonality_constant");
        copyInferredScalarIfPresent(inferredSemanticArgs, argsDraft, "seasonality_constant");

        if (!argsDraft.has("seasonality_constant") && semanticDefaults.has("seasonality_constant")) {
            argsDraft.set("seasonality_constant", semanticDefaults.get("seasonality_constant"));
        }

        boolean simulateAssertionFailure = userSemanticArgs.path("simulate_assertion_failure").asBoolean(false);
        for (JsonNode binding : slotBindings) {
            String roleName = safeText(binding.path("role_name"));
            String slotName = safeText(binding.path("slot_name"));
            if (roleName.isBlank() || slotName.isBlank()) {
                continue;
            }
            Pass1FactHelper.RoleArgMapping mapping = Pass1FactHelper.resolveRoleArgMapping(pass1Node, roleName);
            if (mapping == null) {
                continue;
            }
            if (simulateAssertionFailure && "precipitation".equals(roleName)) {
                continue;
            }
            if (!mapping.slotArgKey().isBlank()) {
                argsDraft.put(mapping.slotArgKey(), slotName);
            }
            if (!mapping.valueArgKey().isBlank() && mapping.defaultValue() != null && !argsDraft.has(mapping.valueArgKey())) {
                argsDraft.set(mapping.valueArgKey(), mapping.defaultValue().deepCopy());
            }
        }

        copyStableDefaultIfMissing(pass1Node, argsDraft, "root_depth_factor");
        copyStableDefaultIfMissing(pass1Node, argsDraft, "pawc_factor");

        passBNode.set("args_draft", argsDraft);
        return new AssemblyResult(caseId, blockedMutations, overruledFields, assemblyBlocked);
    }

    private void copyScalarIfPresent(ObjectNode source, ObjectNode target, String key) {
        if (source.has(key) && !source.get(key).isNull()) {
            target.set(key, source.get(key));
        }
    }

    private ObjectNode ensureObjectNode(ObjectNode parent, String fieldName) {
        JsonNode existing = parent.get(fieldName);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode objectNode = objectMapper.createObjectNode();
        parent.set(fieldName, objectNode);
        return objectNode;
    }

    private void copyInferredScalarIfPresent(ObjectNode source, ObjectNode target, String key) {
        JsonNode value = source.path(key).path("value");
        if (!value.isMissingNode() && !value.isNull()) {
            target.set(key, value.deepCopy());
        }
    }

    private void copyStableDefaultIfMissing(JsonNode pass1Node, ObjectNode argsDraft, String key) {
        if (argsDraft.has(key)) {
            return;
        }
        JsonNode stableDefault = Pass1FactHelper.resolveStableDefault(pass1Node, key);
        if (stableDefault != null) {
            argsDraft.set(key, stableDefault.deepCopy());
        }
    }

    private String safeText(JsonNode node) {
        return node == null ? "" : node.asText("");
    }

    record AssemblyResult(
            String caseId,
            List<String> blockedMutations,
            List<String> overruledFields,
            boolean assemblyBlocked
    ) {
    }
}
