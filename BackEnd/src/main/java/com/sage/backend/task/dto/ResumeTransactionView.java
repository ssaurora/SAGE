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

    @JsonProperty("base_catalog_inventory_version")
    private Integer baseCatalogInventoryVersion;

    @JsonProperty("base_catalog_revision")
    private Integer baseCatalogRevision;

    @JsonProperty("base_catalog_fingerprint")
    private String baseCatalogFingerprint;

    @JsonProperty("candidate_catalog_revision")
    private Integer candidateCatalogRevision;

    @JsonProperty("candidate_catalog_fingerprint")
    private String candidateCatalogFingerprint;

    @JsonProperty("candidate_catalog_inventory_version")
    private Integer candidateCatalogInventoryVersion;

    @JsonProperty("candidate_manifest_id")
    private String candidateManifestId;

    @JsonProperty("candidate_attempt_no")
    private Integer candidateAttemptNo;

    @JsonProperty("candidate_job_id")
    private String candidateJobId;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("failure_code")
    private String failureCode;

    @JsonProperty("base_contract_version")
    private String baseContractVersion;

    @JsonProperty("base_contract_fingerprint")
    private String baseContractFingerprint;

    @JsonProperty("candidate_contract_version")
    private String candidateContractVersion;

    @JsonProperty("candidate_contract_fingerprint")
    private String candidateContractFingerprint;

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

    public Integer getBaseCatalogRevision() {
        return baseCatalogRevision;
    }

    public void setBaseCatalogRevision(Integer baseCatalogRevision) {
        this.baseCatalogRevision = baseCatalogRevision;
    }

    public Integer getBaseCatalogInventoryVersion() {
        return baseCatalogInventoryVersion;
    }

    public void setBaseCatalogInventoryVersion(Integer baseCatalogInventoryVersion) {
        this.baseCatalogInventoryVersion = baseCatalogInventoryVersion;
    }

    public String getBaseCatalogFingerprint() {
        return baseCatalogFingerprint;
    }

    public void setBaseCatalogFingerprint(String baseCatalogFingerprint) {
        this.baseCatalogFingerprint = baseCatalogFingerprint;
    }

    public Integer getCandidateCatalogRevision() {
        return candidateCatalogRevision;
    }

    public void setCandidateCatalogRevision(Integer candidateCatalogRevision) {
        this.candidateCatalogRevision = candidateCatalogRevision;
    }

    public String getCandidateCatalogFingerprint() {
        return candidateCatalogFingerprint;
    }

    public void setCandidateCatalogFingerprint(String candidateCatalogFingerprint) {
        this.candidateCatalogFingerprint = candidateCatalogFingerprint;
    }

    public Integer getCandidateCatalogInventoryVersion() {
        return candidateCatalogInventoryVersion;
    }

    public void setCandidateCatalogInventoryVersion(Integer candidateCatalogInventoryVersion) {
        this.candidateCatalogInventoryVersion = candidateCatalogInventoryVersion;
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

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getBaseContractVersion() {
        return baseContractVersion;
    }

    public void setBaseContractVersion(String baseContractVersion) {
        this.baseContractVersion = baseContractVersion;
    }

    public String getBaseContractFingerprint() {
        return baseContractFingerprint;
    }

    public void setBaseContractFingerprint(String baseContractFingerprint) {
        this.baseContractFingerprint = baseContractFingerprint;
    }

    public String getCandidateContractVersion() {
        return candidateContractVersion;
    }

    public void setCandidateContractVersion(String candidateContractVersion) {
        this.candidateContractVersion = candidateContractVersion;
    }

    public String getCandidateContractFingerprint() {
        return candidateContractFingerprint;
    }

    public void setCandidateContractFingerprint(String candidateContractFingerprint) {
        this.candidateContractFingerprint = candidateContractFingerprint;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
