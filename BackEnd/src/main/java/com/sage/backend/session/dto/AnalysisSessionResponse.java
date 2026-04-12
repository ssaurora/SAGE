package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class AnalysisSessionResponse {
    @JsonProperty("session_id")
    private String sessionId;

    private String title;

    @JsonProperty("user_goal")
    private String userGoal;

    private String status;

    @JsonProperty("scene_id")
    private String sceneId;

    @JsonProperty("current_task_id")
    private String currentTaskId;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("current_required_user_action")
    private Map<String, Object> currentRequiredUserAction;

    @JsonProperty("session_context_summary")
    private Map<String, Object> sessionContextSummary;

    @JsonProperty("current_task_summary")
    private Map<String, Object> currentTaskSummary;

    @JsonProperty("latest_result_summary")
    private Map<String, Object> latestResultSummary;

    @JsonProperty("progress_projection")
    private Map<String, Object> progressProjection;

    @JsonProperty("waiting_projection")
    private Map<String, Object> waitingProjection;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUserGoal() {
        return userGoal;
    }

    public void setUserGoal(String userGoal) {
        this.userGoal = userGoal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getCurrentRequiredUserAction() {
        return currentRequiredUserAction;
    }

    public void setCurrentRequiredUserAction(Map<String, Object> currentRequiredUserAction) {
        this.currentRequiredUserAction = currentRequiredUserAction;
    }

    public Map<String, Object> getSessionContextSummary() {
        return sessionContextSummary;
    }

    public void setSessionContextSummary(Map<String, Object> sessionContextSummary) {
        this.sessionContextSummary = sessionContextSummary;
    }

    public Map<String, Object> getCurrentTaskSummary() {
        return currentTaskSummary;
    }

    public void setCurrentTaskSummary(Map<String, Object> currentTaskSummary) {
        this.currentTaskSummary = currentTaskSummary;
    }

    public Map<String, Object> getLatestResultSummary() {
        return latestResultSummary;
    }

    public void setLatestResultSummary(Map<String, Object> latestResultSummary) {
        this.latestResultSummary = latestResultSummary;
    }

    public Map<String, Object> getProgressProjection() {
        return progressProjection;
    }

    public void setProgressProjection(Map<String, Object> progressProjection) {
        this.progressProjection = progressProjection;
    }

    public Map<String, Object> getWaitingProjection() {
        return waitingProjection;
    }

    public void setWaitingProjection(Map<String, Object> waitingProjection) {
        this.waitingProjection = waitingProjection;
    }
}
