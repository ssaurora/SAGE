package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResumeTransactionView {
    @JsonProperty("resume_request_id")
    private String resumeRequestId;

    private String status;

    @JsonProperty("base_checkpoint_version")
    private Integer baseCheckpointVersion;

    @JsonProperty("candidate_checkpoint_version")
    private Integer candidateCheckpointVersion;

    @JsonProperty("candidate_inventory_version")
    private Integer candidateInventoryVersion;

    @JsonProperty("candidate_manifest_id")
    private String candidateManifestId;

    @JsonProperty("candidate_attempt_no")
    private Integer candidateAttemptNo;

    @JsonProperty("candidate_job_id")
    private String candidateJobId;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("updated_at")
    private String updatedAt;

    public String getResumeRequestId() {
        return resumeRequestId;
    }

    public void setResumeRequestId(String resumeRequestId) {
        this.resumeRequestId = resumeRequestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBaseCheckpointVersion() {
        return baseCheckpointVersion;
    }

    public void setBaseCheckpointVersion(Integer baseCheckpointVersion) {
        this.baseCheckpointVersion = baseCheckpointVersion;
    }

    public Integer getCandidateCheckpointVersion() {
        return candidateCheckpointVersion;
    }

    public void setCandidateCheckpointVersion(Integer candidateCheckpointVersion) {
        this.candidateCheckpointVersion = candidateCheckpointVersion;
    }

    public Integer getCandidateInventoryVersion() {
        return candidateInventoryVersion;
    }

    public void setCandidateInventoryVersion(Integer candidateInventoryVersion) {
        this.candidateInventoryVersion = candidateInventoryVersion;
    }

    public String getCandidateManifestId() {
        return candidateManifestId;
    }

    public void setCandidateManifestId(String candidateManifestId) {
        this.candidateManifestId = candidateManifestId;
    }

    public Integer getCandidateAttemptNo() {
        return candidateAttemptNo;
    }

    public void setCandidateAttemptNo(Integer candidateAttemptNo) {
        this.candidateAttemptNo = candidateAttemptNo;
    }

    public String getCandidateJobId() {
        return candidateJobId;
    }

    public void setCandidateJobId(String candidateJobId) {
        this.candidateJobId = candidateJobId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
