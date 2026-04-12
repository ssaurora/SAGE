package com.sage.backend.model;

import java.time.OffsetDateTime;

public class AnalysisSession {
    private String sessionId;
    private Long userId;
    private String title;
    private String userGoal;
    private String status;
    private String sceneId;
    private String currentTaskId;
    private String latestResultBundleId;
    private String currentRequiredUserActionJson;
    private String sessionSummaryJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getCurrentRequiredUserActionJson() {
        return currentRequiredUserActionJson;
    }

    public void setCurrentRequiredUserActionJson(String currentRequiredUserActionJson) {
        this.currentRequiredUserActionJson = currentRequiredUserActionJson;
    }

    public String getSessionSummaryJson() {
        return sessionSummaryJson;
    }

    public void setSessionSummaryJson(String sessionSummaryJson) {
        this.sessionSummaryJson = sessionSummaryJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
