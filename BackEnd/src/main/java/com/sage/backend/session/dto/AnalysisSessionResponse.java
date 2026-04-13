package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    private CurrentRequiredUserActionDto currentRequiredUserAction;

    @JsonProperty("session_context_summary")
    private SessionContextSummaryDto sessionContextSummary;

    @JsonProperty("current_task_summary")
    private CurrentTaskSummaryDto currentTaskSummary;

    @JsonProperty("latest_result_summary")
    private ResultConversationProjectionDto latestResultSummary;

    @JsonProperty("progress_projection")
    private SessionProgressProjectionDto progressProjection;

    @JsonProperty("waiting_projection")
    private WaitingForUserProjectionDto waitingProjection;

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

    public CurrentRequiredUserActionDto getCurrentRequiredUserAction() {
        return currentRequiredUserAction;
    }

    public void setCurrentRequiredUserAction(CurrentRequiredUserActionDto currentRequiredUserAction) {
        this.currentRequiredUserAction = currentRequiredUserAction;
    }

    public SessionContextSummaryDto getSessionContextSummary() {
        return sessionContextSummary;
    }

    public void setSessionContextSummary(SessionContextSummaryDto sessionContextSummary) {
        this.sessionContextSummary = sessionContextSummary;
    }

    public CurrentTaskSummaryDto getCurrentTaskSummary() {
        return currentTaskSummary;
    }

    public void setCurrentTaskSummary(CurrentTaskSummaryDto currentTaskSummary) {
        this.currentTaskSummary = currentTaskSummary;
    }

    public ResultConversationProjectionDto getLatestResultSummary() {
        return latestResultSummary;
    }

    public void setLatestResultSummary(ResultConversationProjectionDto latestResultSummary) {
        this.latestResultSummary = latestResultSummary;
    }

    public SessionProgressProjectionDto getProgressProjection() {
        return progressProjection;
    }

    public void setProgressProjection(SessionProgressProjectionDto progressProjection) {
        this.progressProjection = progressProjection;
    }

    public WaitingForUserProjectionDto getWaitingProjection() {
        return waitingProjection;
    }

    public void setWaitingProjection(WaitingForUserProjectionDto waitingProjection) {
        this.waitingProjection = waitingProjection;
    }
}
