package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneActionRecommendationDTO {
    @JsonProperty("recommended_entry")
    private String recommendedEntry;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("recommendation_reason")
    private String recommendationReason;

    public String getRecommendedEntry() {
        return recommendedEntry;
    }

    public void setRecommendedEntry(String recommendedEntry) {
        this.recommendedEntry = recommendedEntry;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getRecommendationReason() {
        return recommendationReason;
    }

    public void setRecommendationReason(String recommendationReason) {
        this.recommendationReason = recommendationReason;
    }
}
