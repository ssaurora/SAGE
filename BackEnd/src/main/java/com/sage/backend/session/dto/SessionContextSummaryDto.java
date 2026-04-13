package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SessionContextSummaryDto {
    @JsonProperty("task_id")
    private String taskId;

    private String status;

    @JsonProperty("user_goal")
    private String userGoal;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserGoal() {
        return userGoal;
    }

    public void setUserGoal(String userGoal) {
        this.userGoal = userGoal;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }
}
