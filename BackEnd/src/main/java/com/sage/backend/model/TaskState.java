package com.sage.backend.model;

import java.time.OffsetDateTime;

public class TaskState {
    private String taskId;
    private Long userId;
    private String currentState;
    private Integer stateVersion;
    private String userQuery;
    private String pass1ResultJson;
    private String goalParseJson;
    private String skillRouteJson;
    private String passbResultJson;
    private String slotBindingsSummaryJson;
    private String argsDraftSummaryJson;
    private String validationSummaryJson;
    private String inputChainStatus;
    private String jobId;
    private String pass2ResultJson;
    private String resultObjectSummaryJson;
    private String resultBundleSummaryJson;
    private String finalExplanationSummaryJson;
    private String lastFailureSummaryJson;
    private String waitingContextJson;
    private String waitingReasonType;
    private String resumePayloadJson;
    private String resumeTxnJson;
    private Integer resumeAttemptCount;
    private Integer activeAttemptNo;
    private String activeManifestId;
    private Integer activeManifestVersion;
    private Integer planningRevision;
    private Integer checkpointVersion;
    private Integer inventoryVersion;
    private String cognitionVerdict;
    private String corruptionReason;
    private String latestResultBundleId;
    private String latestWorkspaceId;
    private OffsetDateTime corruptedSince;
    private OffsetDateTime waitingSince;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.currentState = currentState;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery;
    }

    public String getPass1ResultJson() {
        return pass1ResultJson;
    }

    public void setPass1ResultJson(String pass1ResultJson) {
        this.pass1ResultJson = pass1ResultJson;
    }

    public String getGoalParseJson() {
        return goalParseJson;
    }

    public void setGoalParseJson(String goalParseJson) {
        this.goalParseJson = goalParseJson;
    }

    public String getSkillRouteJson() {
        return skillRouteJson;
    }

    public void setSkillRouteJson(String skillRouteJson) {
        this.skillRouteJson = skillRouteJson;
    }

    public String getPassbResultJson() {
        return passbResultJson;
    }

    public void setPassbResultJson(String passbResultJson) {
        this.passbResultJson = passbResultJson;
    }

    public String getSlotBindingsSummaryJson() {
        return slotBindingsSummaryJson;
    }

    public void setSlotBindingsSummaryJson(String slotBindingsSummaryJson) {
        this.slotBindingsSummaryJson = slotBindingsSummaryJson;
    }

    public String getArgsDraftSummaryJson() {
        return argsDraftSummaryJson;
    }

    public void setArgsDraftSummaryJson(String argsDraftSummaryJson) {
        this.argsDraftSummaryJson = argsDraftSummaryJson;
    }

    public String getValidationSummaryJson() {
        return validationSummaryJson;
    }

    public void setValidationSummaryJson(String validationSummaryJson) {
        this.validationSummaryJson = validationSummaryJson;
    }

    public String getInputChainStatus() {
        return inputChainStatus;
    }

    public void setInputChainStatus(String inputChainStatus) {
        this.inputChainStatus = inputChainStatus;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getPass2ResultJson() {
        return pass2ResultJson;
    }

    public void setPass2ResultJson(String pass2ResultJson) {
        this.pass2ResultJson = pass2ResultJson;
    }

    public String getResultObjectSummaryJson() {
        return resultObjectSummaryJson;
    }

    public void setResultObjectSummaryJson(String resultObjectSummaryJson) {
        this.resultObjectSummaryJson = resultObjectSummaryJson;
    }

    public String getResultBundleSummaryJson() {
        return resultBundleSummaryJson;
    }

    public void setResultBundleSummaryJson(String resultBundleSummaryJson) {
        this.resultBundleSummaryJson = resultBundleSummaryJson;
    }

    public String getFinalExplanationSummaryJson() {
        return finalExplanationSummaryJson;
    }

    public void setFinalExplanationSummaryJson(String finalExplanationSummaryJson) {
        this.finalExplanationSummaryJson = finalExplanationSummaryJson;
    }

    public String getLastFailureSummaryJson() {
        return lastFailureSummaryJson;
    }

    public void setLastFailureSummaryJson(String lastFailureSummaryJson) {
        this.lastFailureSummaryJson = lastFailureSummaryJson;
    }

    public String getWaitingContextJson() {
        return waitingContextJson;
    }

    public void setWaitingContextJson(String waitingContextJson) {
        this.waitingContextJson = waitingContextJson;
    }

    public String getWaitingReasonType() {
        return waitingReasonType;
    }

    public void setWaitingReasonType(String waitingReasonType) {
        this.waitingReasonType = waitingReasonType;
    }

    public String getResumePayloadJson() {
        return resumePayloadJson;
    }

    public void setResumePayloadJson(String resumePayloadJson) {
        this.resumePayloadJson = resumePayloadJson;
    }

    public String getResumeTxnJson() {
        return resumeTxnJson;
    }

    public void setResumeTxnJson(String resumeTxnJson) {
        this.resumeTxnJson = resumeTxnJson;
    }

    public Integer getResumeAttemptCount() {
        return resumeAttemptCount;
    }

    public void setResumeAttemptCount(Integer resumeAttemptCount) {
        this.resumeAttemptCount = resumeAttemptCount;
    }

    public Integer getActiveAttemptNo() {
        return activeAttemptNo;
    }

    public void setActiveAttemptNo(Integer activeAttemptNo) {
        this.activeAttemptNo = activeAttemptNo;
    }

    public String getActiveManifestId() {
        return activeManifestId;
    }

    public void setActiveManifestId(String activeManifestId) {
        this.activeManifestId = activeManifestId;
    }

    public Integer getActiveManifestVersion() {
        return activeManifestVersion;
    }

    public void setActiveManifestVersion(Integer activeManifestVersion) {
        this.activeManifestVersion = activeManifestVersion;
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

    public Integer getInventoryVersion() {
        return inventoryVersion;
    }

    public void setInventoryVersion(Integer inventoryVersion) {
        this.inventoryVersion = inventoryVersion;
    }

    public String getCognitionVerdict() {
        return cognitionVerdict;
    }

    public void setCognitionVerdict(String cognitionVerdict) {
        this.cognitionVerdict = cognitionVerdict;
    }

    public String getCorruptionReason() {
        return corruptionReason;
    }

    public void setCorruptionReason(String corruptionReason) {
        this.corruptionReason = corruptionReason;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }

    public String getLatestWorkspaceId() {
        return latestWorkspaceId;
    }

    public void setLatestWorkspaceId(String latestWorkspaceId) {
        this.latestWorkspaceId = latestWorkspaceId;
    }

    public OffsetDateTime getCorruptedSince() {
        return corruptedSince;
    }

    public void setCorruptedSince(OffsetDateTime corruptedSince) {
        this.corruptedSince = corruptedSince;
    }

    public OffsetDateTime getWaitingSince() {
        return waitingSince;
    }

    public void setWaitingSince(OffsetDateTime waitingSince) {
        this.waitingSince = waitingSince;
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
