package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class CognitionGoalRouteResponse {
    @JsonProperty("planning_intent_status")
    private String planningIntentStatus;

    @JsonProperty("goal_parse")
    private JsonNode goalParse;

    @JsonProperty("skill_route")
    private JsonNode skillRoute;

    private Double confidence;

    @JsonProperty("decision_summary")
    private Map<String, Object> decisionSummary;

    @JsonProperty("cognition_metadata")
    private Map<String, Object> cognitionMetadata;

    public String getPlanningIntentStatus() {
        return planningIntentStatus;
    }

    public void setPlanningIntentStatus(String planningIntentStatus) {
        this.planningIntentStatus = planningIntentStatus;
    }

    public JsonNode getGoalParse() {
        return goalParse;
    }

    public void setGoalParse(JsonNode goalParse) {
        this.goalParse = goalParse;
    }

    public JsonNode getSkillRoute() {
        return skillRoute;
    }

    public void setSkillRoute(JsonNode skillRoute) {
        this.skillRoute = skillRoute;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(Map<String, Object> decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public Map<String, Object> getCognitionMetadata() {
        return cognitionMetadata;
    }

    public void setCognitionMetadata(Map<String, Object> cognitionMetadata) {
        this.cognitionMetadata = cognitionMetadata;
    }
}
