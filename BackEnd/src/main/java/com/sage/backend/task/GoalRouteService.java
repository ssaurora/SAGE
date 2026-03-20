package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        String goalType = inferGoalType(normalized, primarySkill);

        ObjectNode goalParse = objectMapper.createObjectNode();
        goalParse.put("goal_type", goalType);
        goalParse.put("user_query", userQuery == null ? "" : userQuery);
        goalParse.put("analysis_kind", primarySkill);
        goalParse.put("intent_mode", "deterministic_phase0");
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
        skillRoute.put("capability_key", "water_yield");
        skillRoute.put("route_source", "deterministic_phase0");
        skillRoute.put("confidence", primarySkill.equals("water_yield") ? 0.9 : 0.6);

        return new GoalRouteDecision(goalParse, skillRoute);
    }

    private String inferPrimarySkill(String normalized) {
        if (normalized.contains("water_yield") || normalized.contains("precipitation") || normalized.contains("eto") || normalized.contains("yield")) {
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

    public record GoalRouteDecision(JsonNode goalParse, JsonNode skillRoute) {
    }
}
