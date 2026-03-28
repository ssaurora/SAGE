package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.planning.Pass1FactHelper;
import org.springframework.stereotype.Service;

@Service
public class GoalRouteService {

    private final ObjectMapper objectMapper;

    public GoalRouteService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GoalRouteDecision derive(String userQuery) {
        String normalized = userQuery == null ? "" : userQuery.trim().toLowerCase();
        String primarySkill = inferPrimarySkill(normalized);
        String capabilityKey = Pass1FactHelper.normalizeCapabilityKey(primarySkill);
        String goalType = inferGoalType(normalized, primarySkill);
        boolean realCaseRequested = isRealCaseRequested(normalized);

        ObjectNode goalParse = objectMapper.createObjectNode();
        goalParse.put("goal_type", goalType);
        goalParse.put("user_query", userQuery == null ? "" : userQuery);
        goalParse.put("analysis_kind", primarySkill);
        goalParse.put("intent_mode", "deterministic_phase0");
        goalParse.put("source", "goal_router");
        applyExecutionPreferences(goalParse, realCaseRequested);
        ArrayNode entities = goalParse.putArray("entities");
        if (normalized.contains("precipitation")) {
            entities.add("precipitation");
        }
        if (normalized.contains("eto")) {
            entities.add("eto");
        }

        ObjectNode skillRoute = objectMapper.createObjectNode();
        skillRoute.put("route_mode", "single_skill");
        skillRoute.put("primary_skill", primarySkill);
        skillRoute.put("capability_key", capabilityKey);
        skillRoute.put("route_source", "deterministic_phase0");
        skillRoute.put("source", "goal_router");
        skillRoute.put("confidence", primarySkill.equals("water_yield") ? 0.9 : 0.6);
        applyExecutionPreferences(skillRoute, realCaseRequested);

        return new GoalRouteDecision(goalParse, skillRoute);
    }

    public GoalRouteDecision deriveFallback(String userQuery, JsonNode pass1Result) {
        String normalized = userQuery == null ? "" : userQuery.trim().toLowerCase();
        String capabilityKey = Pass1FactHelper.resolveCapabilityKey(null, null, pass1Result);
        String goalType = inferGoalType(normalized, capabilityKey);
        boolean realCaseRequested = isRealCaseRequested(normalized);

        ObjectNode goalParse = objectMapper.createObjectNode();
        goalParse.put("goal_type", goalType);
        goalParse.put("user_query", userQuery == null ? "" : userQuery);
        goalParse.put("analysis_kind", capabilityKey);
        goalParse.put("intent_mode", "derived_from_pass1");
        goalParse.put("source", "derived_fallback");
        applyExecutionPreferences(goalParse, realCaseRequested);
        ArrayNode entities = goalParse.putArray("entities");
        if (normalized.contains("precipitation")) {
            entities.add("precipitation");
        }
        if (normalized.contains("eto")) {
            entities.add("eto");
        }

        ObjectNode skillRoute = objectMapper.createObjectNode();
        skillRoute.put("route_mode", "single_skill");
        skillRoute.put("primary_skill", capabilityKey);
        skillRoute.put("capability_key", capabilityKey);
        skillRoute.put("route_source", "derived_fallback");
        skillRoute.put("source", "derived_fallback");
        skillRoute.put("confidence", 1.0);
        skillRoute.put("selected_template", Pass1FactHelper.resolveAnalysisTemplate(pass1Result));
        skillRoute.put("template_version", Pass1FactHelper.resolveTemplateVersion(pass1Result));
        applyExecutionPreferences(skillRoute, realCaseRequested);

        return new GoalRouteDecision(goalParse, skillRoute);
    }

    public JsonNode enrichGoalParse(JsonNode goalParse, JsonNode pass1Result) {
        ObjectNode enriched;
        if (goalParse instanceof ObjectNode goalObject) {
            enriched = goalObject.deepCopy();
        } else {
            enriched = objectMapper.createObjectNode();
        }

        String userQuery = enriched.path("user_query").asText("");
        String normalized = userQuery.trim().toLowerCase();
        String capabilityKey = Pass1FactHelper.resolveCapabilityKey(enriched, null, pass1Result);

        if (!userQuery.isBlank()) {
            enriched.put("user_query", userQuery);
        }
        if (!capabilityKey.isBlank()) {
            enriched.put("analysis_kind", capabilityKey);
            enriched.put("goal_type", inferGoalType(normalized, capabilityKey));
        }
        if (!enriched.path("intent_mode").isTextual() || enriched.path("intent_mode").asText("").isBlank()) {
            enriched.put("intent_mode", "derived_from_pass1");
        }
        if (!enriched.path("source").isTextual() || enriched.path("source").asText("").isBlank()) {
            enriched.put("source", "derived_fallback");
        }
        if (!enriched.path("execution_mode").isTextual() || enriched.path("execution_mode").asText("").isBlank()) {
            applyExecutionPreferences(enriched, isRealCaseRequested(normalized));
        }

        return enriched;
    }

    public JsonNode enrichSkillRoute(JsonNode skillRoute, JsonNode pass1Result) {
        ObjectNode enriched;
        if (skillRoute instanceof ObjectNode routeObject) {
            enriched = routeObject.deepCopy();
        } else {
            enriched = objectMapper.createObjectNode();
        }

        String capabilityKey = Pass1FactHelper.resolveCapabilityKey(null, enriched, pass1Result);
        if (!capabilityKey.isBlank()) {
            enriched.put("capability_key", capabilityKey);
        }
        if (!enriched.path("primary_skill").isTextual() || enriched.path("primary_skill").asText("").isBlank()) {
            enriched.put("primary_skill", capabilityKey);
        }

        String selectedTemplate = Pass1FactHelper.resolveAnalysisTemplate(pass1Result);
        if (!selectedTemplate.isBlank()) {
            enriched.put("selected_template", selectedTemplate);
        }

        String templateVersion = Pass1FactHelper.resolveTemplateVersion(pass1Result);
        if (!templateVersion.isBlank()) {
            enriched.put("template_version", templateVersion);
        }
        if (!enriched.path("execution_mode").isTextual() || enriched.path("execution_mode").asText("").isBlank()) {
            applyExecutionPreferences(enriched, false);
        }

        return enriched;
    }

    private String inferPrimarySkill(String normalized) {
        if (normalized.contains("water_yield")
                || normalized.contains("precipitation")
                || normalized.contains("eto")
                || normalized.contains("yield")
                || normalized.contains("gura")) {
            return "water_yield";
        }
        return "generic_analysis";
    }

    private String inferGoalType(String normalized, String primarySkill) {
        if (normalized.contains("missing") || normalized.contains("upload") || normalized.contains("resume")) {
            return "repairable_analysis_request";
        }
        if ("water_yield".equals(primarySkill)) {
            return "water_yield_analysis";
        }
        return "generic_analysis_request";
    }

    private void applyExecutionPreferences(ObjectNode node, boolean realCaseRequested) {
        node.put("execution_mode", realCaseRequested ? "real_case_validation" : "governed_baseline");
        if (realCaseRequested) {
            node.put("provider_preference", "planning-pass1-invest-local");
            node.put("runtime_profile_preference", "docker-invest-real");
        }
    }

    private boolean isRealCaseRequested(String normalized) {
        return normalized.contains("real case")
                || normalized.contains("real_case")
                || normalized.contains("invest")
                || normalized.contains("gura")
                || normalized.contains("annual_water_yield_gura")
                || normalized.contains("真实");
    }

    public record GoalRouteDecision(JsonNode goalParse, JsonNode skillRoute) {
    }
}
