package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionListItemDto {
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

    @JsonProperty("session_summary")
    private SessionContextSummaryDto sessionSummary;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

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

    public SessionContextSummaryDto getSessionSummary() {
        return sessionSummary;
    }

    public void setSessionSummary(SessionContextSummaryDto sessionSummary) {
        this.sessionSummary = sessionSummary;
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
}
