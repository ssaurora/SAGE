package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CurrentTaskSummaryDto {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("task_state")
    private String taskState;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("planning_revision")
    private Integer planningRevision;

    @JsonProperty("checkpoint_version")
    private Integer checkpointVersion;

    @JsonProperty("promotion_status")
    private String promotionStatus;

    @JsonProperty("cognition_verdict")
    private String cognitionVerdict;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public Integer getPlanningRevision() {
        return planningRevision;
    }

    public void setPlanningRevision(Integer planningRevision) {
        this.planningRevision = planningRevision;
    }

    public Integer getCheckpointVersion() {
        return checkpointVersion;
    }

    public void setCheckpointVersion(Integer checkpointVersion) {
        this.checkpointVersion = checkpointVersion;
    }

    public String getPromotionStatus() {
        return promotionStatus;
    }

    public void setPromotionStatus(String promotionStatus) {
        this.promotionStatus = promotionStatus;
    }

    public String getCognitionVerdict() {
        return cognitionVerdict;
    }

    public void setCognitionVerdict(String cognitionVerdict) {
        this.cognitionVerdict = cognitionVerdict;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }
}
