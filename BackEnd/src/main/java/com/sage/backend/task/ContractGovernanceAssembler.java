package com.sage.backend.task;

import com.sage.backend.task.dto.ContractGovernanceView;
import com.sage.backend.task.dto.ResumeTransactionView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ContractGovernanceAssembler {

    private ContractGovernanceAssembler() {
    }

    static ContractGovernanceView build(
            String scope,
            Map<String, Object> frozenContractSummary,
            Map<String, Object> currentContractSummary,
            Map<String, Object> contractConsistency,
            ResumeTransactionView resumeTransaction
    ) {
        ContractGovernanceView view = new ContractGovernanceView();
        view.setScope(scope);
        view.setFrozenContractSummary(toContractIdentityView(frozenContractSummary));
        view.setCurrentContractSummary(toContractIdentityView(currentContractSummary));
        view.setConsistency(toContractConsistencyView(contractConsistency));
        view.setResumeContractEvaluation(toResumeContractEvaluation(resumeTransaction, contractConsistency));
        return view;
    }

    static ContractGovernanceView buildAudit(Map<String, Object> detail) {
        ContractGovernanceView view = new ContractGovernanceView();
        view.setScope("audit_contract_governance");
        if (detail == null || detail.isEmpty()) {
            view.setFrozenContractSummary(toContractIdentityView(Map.of()));
            view.setCurrentContractSummary(toContractIdentityView(Map.of()));
            view.setConsistency(new ContractGovernanceView.ContractConsistencyView());
            view.setResumeContractEvaluation(null);
            return view;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> currentContractIdentity = detail.get("contract_identity") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        String frozenVersion = objectStringValue(detail.get("frozen_contract_version"));
        String frozenFingerprint = objectStringValue(detail.get("frozen_contract_fingerprint"));

        Map<String, Object> frozenContractIdentity = new LinkedHashMap<>();
        frozenContractIdentity.put("contract_version", frozenVersion);
        frozenContractIdentity.put("contract_fingerprint", frozenFingerprint);
        frozenContractIdentity.put("contract_count", 0);
        frozenContractIdentity.put("contract_names", List.of());
        frozenContractIdentity.put("contract_present", !safeString(frozenVersion).isBlank() || !safeString(frozenFingerprint).isBlank());

        view.setFrozenContractSummary(toContractIdentityView(frozenContractIdentity));
        view.setCurrentContractSummary(toContractIdentityView(currentContractIdentity));

        String currentVersion = objectStringValue(currentContractIdentity.get("contract_version"));
        String currentFingerprint = objectStringValue(currentContractIdentity.get("contract_fingerprint"));
        String mismatchCode = objectStringValue(detail.get("mismatch_code"));
        if (safeString(mismatchCode).isBlank()) {
            String failureCode = objectStringValue(detail.get("failure_code"));
            mismatchCode = safeString(failureCode).startsWith("CONTRACT_") ? failureCode : null;
        }

        Map<String, Object> consistency = new LinkedHashMap<>();
        consistency.put("scope", "audit_contract");
        consistency.put("frozen_contract_present", Boolean.TRUE.equals(frozenContractIdentity.get("contract_present")));
        consistency.put("current_contract_present", Boolean.TRUE.equals(currentContractIdentity.get("contract_present")));
        consistency.put("frozen_contract_version", frozenVersion);
        consistency.put("frozen_contract_fingerprint", frozenFingerprint);
        consistency.put("current_contract_version", currentVersion);
        consistency.put("current_contract_fingerprint", currentFingerprint);
        consistency.put("mismatch_code", mismatchCode);
        consistency.put("consistency_code", safeString(mismatchCode).isBlank() ? null : mismatchCode);
        consistency.put("compatibility_code", objectStringValue(detail.get("compatibility_code")));
        consistency.put("migration_hint", objectStringValue(detail.get("migration_hint")));
        consistency.put("drift_detected", !safeString(mismatchCode).isBlank());

        view.setConsistency(toContractConsistencyView(consistency));
        view.setResumeContractEvaluation(toAuditResumeContractEvaluation(detail, frozenVersion, frozenFingerprint, currentVersion, currentFingerprint, mismatchCode));
        return view;
    }

    private static ContractGovernanceView.ResumeContractEvaluationView toResumeContractEvaluation(
            ResumeTransactionView resumeTransaction,
            Map<String, Object> contractConsistency
    ) {
        if (resumeTransaction == null) {
            return null;
        }
        ContractGovernanceView.ResumeContractEvaluationView resumeView = new ContractGovernanceView.ResumeContractEvaluationView();
        resumeView.setFailureCode(resumeTransaction.getFailureCode());
        resumeView.setBaseContractVersion(resumeTransaction.getBaseContractVersion());
        resumeView.setBaseContractFingerprint(resumeTransaction.getBaseContractFingerprint());
        resumeView.setCandidateContractVersion(resumeTransaction.getCandidateContractVersion());
        resumeView.setCandidateContractFingerprint(resumeTransaction.getCandidateContractFingerprint());
        if (contractConsistency != null) {
            resumeView.setCompatibilityCode(objectStringValue(contractConsistency.get("resume_compatibility_code")));
            resumeView.setMigrationHint(objectStringValue(contractConsistency.get("resume_migration_hint")));
            resumeView.setMismatchCode(objectStringValue(contractConsistency.get("resume_mismatch_code")));
            resumeView.setDriftDetected((Boolean) contractConsistency.get("resume_detected_contract_drift"));
        }
        return resumeView;
    }

    private static ContractGovernanceView.ResumeContractEvaluationView toAuditResumeContractEvaluation(
            Map<String, Object> detail,
            String frozenVersion,
            String frozenFingerprint,
            String currentVersion,
            String currentFingerprint,
            String mismatchCode
    ) {
        String failureCode = objectStringValue(detail.get("failure_code"));
        if (!safeString(failureCode).startsWith("CONTRACT_")) {
            return null;
        }
        ContractGovernanceView.ResumeContractEvaluationView resumeView = new ContractGovernanceView.ResumeContractEvaluationView();
        resumeView.setFailureCode(failureCode);
        resumeView.setBaseContractVersion(frozenVersion);
        resumeView.setBaseContractFingerprint(frozenFingerprint);
        resumeView.setCandidateContractVersion(currentVersion);
        resumeView.setCandidateContractFingerprint(currentFingerprint);
        resumeView.setMismatchCode(mismatchCode);
        resumeView.setCompatibilityCode(objectStringValue(detail.get("compatibility_code")));
        resumeView.setMigrationHint(objectStringValue(detail.get("migration_hint")));
        resumeView.setDriftDetected(!safeString(mismatchCode).isBlank());
        return resumeView;
    }

    private static ContractGovernanceView.ContractIdentityView toContractIdentityView(Map<String, Object> summary) {
        ContractGovernanceView.ContractIdentityView view = new ContractGovernanceView.ContractIdentityView();
        if (summary == null || summary.isEmpty()) {
            view.setContractCount(0);
            view.setContractNames(List.of());
            view.setContractPresent(false);
            return view;
        }
        view.setContractVersion(objectStringValue(summary.get("contract_version")));
        view.setContractFingerprint(objectStringValue(summary.get("contract_fingerprint")));
        Object contractCount = summary.get("contract_count");
        view.setContractCount(contractCount instanceof Number number ? number.intValue() : 0);
        @SuppressWarnings("unchecked")
        List<String> contractNames = summary.get("contract_names") instanceof List<?> list
                ? (List<String>) list
                : List.of();
        view.setContractNames(contractNames);
        view.setContractPresent(Boolean.TRUE.equals(summary.get("contract_present")));
        return view;
    }

    private static ContractGovernanceView.ContractConsistencyView toContractConsistencyView(Map<String, Object> consistency) {
        ContractGovernanceView.ContractConsistencyView view = new ContractGovernanceView.ContractConsistencyView();
        if (consistency == null || consistency.isEmpty()) {
            return view;
        }
        view.setScope(objectStringValue(consistency.get("scope")));
        view.setFrozenContractPresent((Boolean) consistency.get("frozen_contract_present"));
        view.setCurrentContractPresent((Boolean) consistency.get("current_contract_present"));
        view.setFrozenContractVersion(objectStringValue(consistency.get("frozen_contract_version")));
        view.setFrozenContractFingerprint(objectStringValue(consistency.get("frozen_contract_fingerprint")));
        view.setCurrentContractVersion(objectStringValue(consistency.get("current_contract_version")));
        view.setCurrentContractFingerprint(objectStringValue(consistency.get("current_contract_fingerprint")));
        view.setMatchesCurrentContract((Boolean) consistency.get("matches_current_contract"));
        view.setDriftDetected((Boolean) consistency.get("drift_detected"));
        view.setMismatchCode(objectStringValue(consistency.get("mismatch_code")));
        view.setConsistencyCode(objectStringValue(consistency.get("consistency_code")));
        view.setCompatibilityCode(objectStringValue(consistency.get("compatibility_code")));
        view.setMigrationHint(objectStringValue(consistency.get("migration_hint")));
        view.setResumeFailureCode(objectStringValue(consistency.get("resume_failure_code")));
        view.setResumeBaseContractVersion(objectStringValue(consistency.get("resume_base_contract_version")));
        view.setResumeBaseContractFingerprint(objectStringValue(consistency.get("resume_base_contract_fingerprint")));
        view.setResumeCandidateContractVersion(objectStringValue(consistency.get("resume_candidate_contract_version")));
        view.setResumeCandidateContractFingerprint(objectStringValue(consistency.get("resume_candidate_contract_fingerprint")));
        view.setResumeMismatchCode(objectStringValue(consistency.get("resume_mismatch_code")));
        view.setResumeDetectedContractDrift((Boolean) consistency.get("resume_detected_contract_drift"));
        view.setResumeCompatibilityCode(objectStringValue(consistency.get("resume_compatibility_code")));
        view.setResumeMigrationHint(objectStringValue(consistency.get("resume_migration_hint")));
        return view;
    }

    private static String objectStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
