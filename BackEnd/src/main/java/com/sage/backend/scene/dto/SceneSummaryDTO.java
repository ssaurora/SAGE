package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneSummaryDTO {
    @JsonProperty("scene_id")
    private String sceneId;

    @JsonProperty("scene_name")
    private String sceneName;

    @JsonProperty("scene_status")
    private String sceneStatus;

    @JsonProperty("current_session_id")
    private String currentSessionId;

    @JsonProperty("current_task_id")
    private String currentTaskId;

    @JsonProperty("user_goal_summary")
    private String userGoalSummary;

    @JsonProperty("task_state")
    private String taskState;

    @JsonProperty("blocking_summary")
    private SceneBlockingSummaryDTO blockingSummary;

    @JsonProperty("result_summary")
    private SceneResultSummaryDTO resultSummary;

    @JsonProperty("needs_attention")
    private Boolean needsAttention;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("action_recommendation")
    private SceneActionRecommendationDTO actionRecommendation;

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public String getSceneStatus() {
        return sceneStatus;
    }

    public void setSceneStatus(String sceneStatus) {
        this.sceneStatus = sceneStatus;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getUserGoalSummary() {
        return userGoalSummary;
    }

    public void setUserGoalSummary(String userGoalSummary) {
        this.userGoalSummary = userGoalSummary;
    }

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public SceneBlockingSummaryDTO getBlockingSummary() {
        return blockingSummary;
    }

    public void setBlockingSummary(SceneBlockingSummaryDTO blockingSummary) {
        this.blockingSummary = blockingSummary;
    }

    public SceneResultSummaryDTO getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(SceneResultSummaryDTO resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Boolean getNeedsAttention() {
        return needsAttention;
    }

    public void setNeedsAttention(Boolean needsAttention) {
        this.needsAttention = needsAttention;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SceneActionRecommendationDTO getActionRecommendation() {
        return actionRecommendation;
    }

    public void setActionRecommendation(SceneActionRecommendationDTO actionRecommendation) {
        this.actionRecommendation = actionRecommendation;
    }
}
