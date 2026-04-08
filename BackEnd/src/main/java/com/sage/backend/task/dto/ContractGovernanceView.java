package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ContractGovernanceView {
    private String scope;

    @JsonProperty("frozen_contract_summary")
    private ContractIdentityView frozenContractSummary;

    @JsonProperty("current_contract_summary")
    private ContractIdentityView currentContractSummary;

    private ContractConsistencyView consistency;

    @JsonProperty("resume_contract_evaluation")
    private ResumeContractEvaluationView resumeContractEvaluation;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public ContractIdentityView getFrozenContractSummary() {
        return frozenContractSummary;
    }

    public void setFrozenContractSummary(ContractIdentityView frozenContractSummary) {
        this.frozenContractSummary = frozenContractSummary;
    }

    public ContractIdentityView getCurrentContractSummary() {
        return currentContractSummary;
    }

    public void setCurrentContractSummary(ContractIdentityView currentContractSummary) {
        this.currentContractSummary = currentContractSummary;
    }

    public ContractConsistencyView getConsistency() {
        return consistency;
    }

    public void setConsistency(ContractConsistencyView consistency) {
        this.consistency = consistency;
    }

    public ResumeContractEvaluationView getResumeContractEvaluation() {
        return resumeContractEvaluation;
    }

    public void setResumeContractEvaluation(ResumeContractEvaluationView resumeContractEvaluation) {
        this.resumeContractEvaluation = resumeContractEvaluation;
    }

    public static class ContractIdentityView {
        @JsonProperty("contract_version")
        private String contractVersion;

        @JsonProperty("contract_fingerprint")
        private String contractFingerprint;

        @JsonProperty("contract_count")
        private Integer contractCount;

        @JsonProperty("contract_names")
        private List<String> contractNames;

        @JsonProperty("contract_present")
        private Boolean contractPresent;

        public String getContractVersion() {
            return contractVersion;
        }

        public void setContractVersion(String contractVersion) {
            this.contractVersion = contractVersion;
        }

        public String getContractFingerprint() {
            return contractFingerprint;
        }

        public void setContractFingerprint(String contractFingerprint) {
            this.contractFingerprint = contractFingerprint;
        }

        public Integer getContractCount() {
            return contractCount;
        }

        public void setContractCount(Integer contractCount) {
            this.contractCount = contractCount;
        }

        public List<String> getContractNames() {
            return contractNames;
        }

        public void setContractNames(List<String> contractNames) {
            this.contractNames = contractNames;
        }

        public Boolean getContractPresent() {
            return contractPresent;
        }

        public void setContractPresent(Boolean contractPresent) {
            this.contractPresent = contractPresent;
        }
    }

    public static class ContractConsistencyView {
        private String scope;

        @JsonProperty("frozen_contract_present")
        private Boolean frozenContractPresent;

        @JsonProperty("current_contract_present")
        private Boolean currentContractPresent;

        @JsonProperty("frozen_contract_version")
        private String frozenContractVersion;

        @JsonProperty("frozen_contract_fingerprint")
        private String frozenContractFingerprint;

        @JsonProperty("current_contract_version")
        private String currentContractVersion;

        @JsonProperty("current_contract_fingerprint")
        private String currentContractFingerprint;

        @JsonProperty("matches_current_contract")
        private Boolean matchesCurrentContract;

        @JsonProperty("drift_detected")
        private Boolean driftDetected;

        @JsonProperty("mismatch_code")
        private String mismatchCode;

        @JsonProperty("consistency_code")
        private String consistencyCode;

        @JsonProperty("compatibility_code")
        private String compatibilityCode;

        @JsonProperty("migration_hint")
        private String migrationHint;

        @JsonProperty("resume_failure_code")
        private String resumeFailureCode;

        @JsonProperty("resume_base_contract_version")
        private String resumeBaseContractVersion;

        @JsonProperty("resume_base_contract_fingerprint")
        private String resumeBaseContractFingerprint;

        @JsonProperty("resume_candidate_contract_version")
        private String resumeCandidateContractVersion;

        @JsonProperty("resume_candidate_contract_fingerprint")
        private String resumeCandidateContractFingerprint;

        @JsonProperty("resume_mismatch_code")
        private String resumeMismatchCode;

        @JsonProperty("resume_detected_contract_drift")
        private Boolean resumeDetectedContractDrift;

        @JsonProperty("resume_compatibility_code")
        private String resumeCompatibilityCode;

        @JsonProperty("resume_migration_hint")
        private String resumeMigrationHint;

        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public Boolean getFrozenContractPresent() { return frozenContractPresent; }
        public void setFrozenContractPresent(Boolean frozenContractPresent) { this.frozenContractPresent = frozenContractPresent; }
        public Boolean getCurrentContractPresent() { return currentContractPresent; }
        public void setCurrentContractPresent(Boolean currentContractPresent) { this.currentContractPresent = currentContractPresent; }
        public String getFrozenContractVersion() { return frozenContractVersion; }
        public void setFrozenContractVersion(String frozenContractVersion) { this.frozenContractVersion = frozenContractVersion; }
        public String getFrozenContractFingerprint() { return frozenContractFingerprint; }
        public void setFrozenContractFingerprint(String frozenContractFingerprint) { this.frozenContractFingerprint = frozenContractFingerprint; }
        public String getCurrentContractVersion() { return currentContractVersion; }
        public void setCurrentContractVersion(String currentContractVersion) { this.currentContractVersion = currentContractVersion; }
        public String getCurrentContractFingerprint() { return currentContractFingerprint; }
        public void setCurrentContractFingerprint(String currentContractFingerprint) { this.currentContractFingerprint = currentContractFingerprint; }
        public Boolean getMatchesCurrentContract() { return matchesCurrentContract; }
        public void setMatchesCurrentContract(Boolean matchesCurrentContract) { this.matchesCurrentContract = matchesCurrentContract; }
        public Boolean getDriftDetected() { return driftDetected; }
        public void setDriftDetected(Boolean driftDetected) { this.driftDetected = driftDetected; }
        public String getMismatchCode() { return mismatchCode; }
        public void setMismatchCode(String mismatchCode) { this.mismatchCode = mismatchCode; }
        public String getConsistencyCode() { return consistencyCode; }
        public void setConsistencyCode(String consistencyCode) { this.consistencyCode = consistencyCode; }
        public String getCompatibilityCode() { return compatibilityCode; }
        public void setCompatibilityCode(String compatibilityCode) { this.compatibilityCode = compatibilityCode; }
        public String getMigrationHint() { return migrationHint; }
        public void setMigrationHint(String migrationHint) { this.migrationHint = migrationHint; }
        public String getResumeFailureCode() { return resumeFailureCode; }
        public void setResumeFailureCode(String resumeFailureCode) { this.resumeFailureCode = resumeFailureCode; }
        public String getResumeBaseContractVersion() { return resumeBaseContractVersion; }
        public void setResumeBaseContractVersion(String resumeBaseContractVersion) { this.resumeBaseContractVersion = resumeBaseContractVersion; }
        public String getResumeBaseContractFingerprint() { return resumeBaseContractFingerprint; }
        public void setResumeBaseContractFingerprint(String resumeBaseContractFingerprint) { this.resumeBaseContractFingerprint = resumeBaseContractFingerprint; }
        public String getResumeCandidateContractVersion() { return resumeCandidateContractVersion; }
        public void setResumeCandidateContractVersion(String resumeCandidateContractVersion) { this.resumeCandidateContractVersion = resumeCandidateContractVersion; }
        public String getResumeCandidateContractFingerprint() { return resumeCandidateContractFingerprint; }
        public void setResumeCandidateContractFingerprint(String resumeCandidateContractFingerprint) { this.resumeCandidateContractFingerprint = resumeCandidateContractFingerprint; }
        public String getResumeMismatchCode() { return resumeMismatchCode; }
        public void setResumeMismatchCode(String resumeMismatchCode) { this.resumeMismatchCode = resumeMismatchCode; }
        public Boolean getResumeDetectedContractDrift() { return resumeDetectedContractDrift; }
        public void setResumeDetectedContractDrift(Boolean resumeDetectedContractDrift) { this.resumeDetectedContractDrift = resumeDetectedContractDrift; }
        public String getResumeCompatibilityCode() { return resumeCompatibilityCode; }
        public void setResumeCompatibilityCode(String resumeCompatibilityCode) { this.resumeCompatibilityCode = resumeCompatibilityCode; }
        public String getResumeMigrationHint() { return resumeMigrationHint; }
        public void setResumeMigrationHint(String resumeMigrationHint) { this.resumeMigrationHint = resumeMigrationHint; }
    }

    public static class ResumeContractEvaluationView {
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

        @JsonProperty("mismatch_code")
        private String mismatchCode;

        @JsonProperty("compatibility_code")
        private String compatibilityCode;

        @JsonProperty("migration_hint")
        private String migrationHint;

        @JsonProperty("drift_detected")
        private Boolean driftDetected;

        public String getFailureCode() { return failureCode; }
        public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
        public String getBaseContractVersion() { return baseContractVersion; }
        public void setBaseContractVersion(String baseContractVersion) { this.baseContractVersion = baseContractVersion; }
        public String getBaseContractFingerprint() { return baseContractFingerprint; }
        public void setBaseContractFingerprint(String baseContractFingerprint) { this.baseContractFingerprint = baseContractFingerprint; }
        public String getCandidateContractVersion() { return candidateContractVersion; }
        public void setCandidateContractVersion(String candidateContractVersion) { this.candidateContractVersion = candidateContractVersion; }
        public String getCandidateContractFingerprint() { return candidateContractFingerprint; }
        public void setCandidateContractFingerprint(String candidateContractFingerprint) { this.candidateContractFingerprint = candidateContractFingerprint; }
        public String getMismatchCode() { return mismatchCode; }
        public void setMismatchCode(String mismatchCode) { this.mismatchCode = mismatchCode; }
        public String getCompatibilityCode() { return compatibilityCode; }
        public void setCompatibilityCode(String compatibilityCode) { this.compatibilityCode = compatibilityCode; }
        public String getMigrationHint() { return migrationHint; }
        public void setMigrationHint(String migrationHint) { this.migrationHint = migrationHint; }
        public Boolean getDriftDetected() { return driftDetected; }
        public void setDriftDetected(Boolean driftDetected) { this.driftDetected = driftDetected; }
    }
}
