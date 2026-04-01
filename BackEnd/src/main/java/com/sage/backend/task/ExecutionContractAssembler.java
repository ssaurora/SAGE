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
    private static final String DEFAULT_CASE_ID = "annual_water_yield_gura";
    private static final String DEFAULT_SAMPLE_ROOT = "/sample-data/Annual_Water_Yield";

    private final ObjectMapper objectMapper;

    ExecutionContractAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AssemblyResult assemble(String taskId, JsonNode skillRouteNode, JsonNode pass1Node, ObjectNode passBNode) {
        ArrayNode slotBindings = passBNode.withArray("slot_bindings");
        ObjectNode userSemanticArgs = ensureObjectNode(passBNode, "user_semantic_args");
        ObjectNode inferredSemanticArgs = ensureObjectNode(passBNode, "inferred_semantic_args");
        ObjectNode semanticDefaults = SemanticDefaultResolver.buildSemanticDefaults(passBNode);
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
        if (!DEFAULT_CASE_ID.equals(caseId)) {
            blockedMutations.add("case_id");
            overruledFields.add("case_id");
            caseId = DEFAULT_CASE_ID;
            assemblyBlocked = true;
        }

        String executionMode = safeText(skillRouteNode.path("execution_mode"));
        boolean realCase = "real_case_validation".equalsIgnoreCase(executionMode);

        ObjectNode argsDraft = objectMapper.createObjectNode();
        argsDraft.put("analysis_template", Pass1FactHelper.resolveAnalysisTemplate(pass1Node));
        argsDraft.put("case_id", caseId);
        argsDraft.put("case_profile_version", "water_yield_case_contract_v1");
        argsDraft.put("contract_mode", realCase ? "invest_real_case_v1" : "real_case_prep_v1");
        argsDraft.put("runtime_mode", realCase ? "invest_real_runner" : "deterministic_stub");
        argsDraft.put("workspace_dir", "/workspace/output/" + taskId.replace("/", "_"));
        argsDraft.put("results_suffix", realCase ? "gura" : "week3");
        argsDraft.put("n_workers", 1);
        argsDraft.put("sample_data_root", DEFAULT_SAMPLE_ROOT);
        argsDraft.put("watersheds_path", DEFAULT_SAMPLE_ROOT + "/watershed_gura.shp");
        argsDraft.put("sub_watersheds_path", DEFAULT_SAMPLE_ROOT + "/subwatersheds_gura.shp");
        argsDraft.put("lulc_path", DEFAULT_SAMPLE_ROOT + "/land_use_gura.tif");
        argsDraft.put("biophysical_table_path", DEFAULT_SAMPLE_ROOT + "/biophysical_table_gura.csv");
        argsDraft.put("precipitation_path", DEFAULT_SAMPLE_ROOT + "/precipitation_gura.tif");
        argsDraft.put("eto_path", DEFAULT_SAMPLE_ROOT + "/reference_ET_gura.tif");
        argsDraft.put("depth_to_root_restricting_layer_path", DEFAULT_SAMPLE_ROOT + "/depth_to_root_restricting_layer_gura.tif");
        argsDraft.put("plant_available_water_content_path", DEFAULT_SAMPLE_ROOT + "/plant_available_water_fraction_gura.tif");
        argsDraft.put("invest_datastack_path", DEFAULT_SAMPLE_ROOT + "/annual_water_yield_gura.invs.json");

        argsDraft.put("case_id", caseId);
        copyScalarIfPresent(userSemanticArgs, argsDraft, "simulate_promotion_failure");
        copyScalarIfPresent(userSemanticArgs, argsDraft, "simulate_assertion_failure");
        copyScalarIfPresent(userSemanticArgs, argsDraft, "seasonality_constant");
        copyInferredScalarIfPresent(inferredSemanticArgs, argsDraft, "seasonality_constant");

        if (!argsDraft.has("seasonality_constant") && semanticDefaults.has("seasonality_constant")) {
            argsDraft.set("seasonality_constant", semanticDefaults.get("seasonality_constant"));
        } else if (!argsDraft.has("seasonality_constant")) {
            argsDraft.put("seasonality_constant", 5.0);
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
            if (!mapping.valueArgKey().isBlank() && mapping.defaultValue() != null) {
                argsDraft.set(mapping.valueArgKey(), mapping.defaultValue().deepCopy());
            }
        }

        if (!argsDraft.has("root_depth_factor")) {
            argsDraft.put("root_depth_factor", Pass1FactHelper.resolveStableDefaultDouble(pass1Node, "root_depth_factor", 0.8));
        }
        if (!argsDraft.has("pawc_factor")) {
            argsDraft.put("pawc_factor", Pass1FactHelper.resolveStableDefaultDouble(pass1Node, "pawc_factor", 0.85));
        }

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
