package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.task.dto.ResumeTransactionView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ContractConsistencyProjector {

    private ContractConsistencyProjector() {
    }

    static Map<String, Object> resolveManifestContractSummary(Map<String, Object> frozenSummary, JsonNode pass1Node) {
        if (frozenSummary != null && !frozenSummary.isEmpty()) {
            return frozenSummary;
        }
        return buildContractSummary(pass1Node);
    }

    static Map<String, Object> buildContractSummary(JsonNode pass1Node) {
        Map<String, Object> summary = new LinkedHashMap<>();
        JsonNode capabilityFacts = pass1Node == null || pass1Node.isMissingNode()
                ? null
                : pass1Node.path("capability_facts");
        String contractVersion = safeString(capabilityFacts == null ? null : capabilityFacts.path("contract_version").asText(null));
        String contractFingerprint = safeString(capabilityFacts == null ? null : capabilityFacts.path("contract_fingerprint").asText(null));
        JsonNode contractsNode = capabilityFacts == null ? null : capabilityFacts.path("contracts");
        List<String> contractNames = new ArrayList<>();
        if (contractsNode != null && contractsNode.isObject()) {
            contractsNode.fieldNames().forEachRemaining(contractNames::add);
            contractNames.sort(String::compareTo);
        }
        summary.put("contract_version", contractVersion);
        summary.put("contract_fingerprint", contractFingerprint);
        summary.put("contract_count", contractNames.size());
        summary.put("contract_names", contractNames);
        summary.put("contract_present", !contractVersion.isBlank() || !contractFingerprint.isBlank() || !contractNames.isEmpty());
        return summary;
    }

    static Map<String, Object> buildFrozenContractConsistency(
            String scope,
            Map<String, Object> frozenContractSummary,
            Map<String, Object> currentContractSummary
    ) {
        Map<String, Object> consistency = new LinkedHashMap<>();
        boolean frozenPresent = frozenContractSummary != null && !frozenContractSummary.isEmpty()
                && Boolean.TRUE.equals(frozenContractSummary.getOrDefault("contract_present", Boolean.FALSE));
        boolean currentPresent = currentContractSummary != null && !currentContractSummary.isEmpty()
                && Boolean.TRUE.equals(currentContractSummary.getOrDefault("contract_present", Boolean.FALSE));
        String frozenVersion = safeString(objectStringValue(frozenContractSummary == null ? null : frozenContractSummary.get("contract_version")));
        String frozenFingerprint = safeString(objectStringValue(frozenContractSummary == null ? null : frozenContractSummary.get("contract_fingerprint")));
        String currentVersion = safeString(objectStringValue(currentContractSummary == null ? null : currentContractSummary.get("contract_version")));
        String currentFingerprint = safeString(objectStringValue(currentContractSummary == null ? null : currentContractSummary.get("contract_fingerprint")));
        boolean matches = frozenPresent && currentPresent
                && Objects.equals(frozenVersion, currentVersion)
                && Objects.equals(frozenFingerprint, currentFingerprint);
        String mismatchCode = determineContractMismatchCode(
                frozenPresent,
                currentPresent,
                frozenVersion,
                frozenFingerprint,
                currentVersion,
                currentFingerprint
        );
        consistency.put("scope", scope);
        consistency.put("frozen_contract_present", frozenPresent);
        consistency.put("current_contract_present", currentPresent);
        consistency.put("frozen_contract_version", frozenVersion);
        consistency.put("frozen_contract_fingerprint", frozenFingerprint);
        consistency.put("current_contract_version", currentVersion);
        consistency.put("current_contract_fingerprint", currentFingerprint);
        consistency.put("matches_current_contract", matches);
        consistency.put("drift_detected", frozenPresent && currentPresent && !matches);
        consistency.put("mismatch_code", mismatchCode);
        consistency.put("consistency_code", matches ? "CONTRACT_MATCHED" : mismatchCode);
        consistency.putAll(buildContractCompatibilityView(matches ? "CONTRACT_MATCHED" : mismatchCode));
        return consistency;
    }

    static Map<String, Object> buildDetailContractConsistency(
            Map<String, Object> frozenContractSummary,
            Map<String, Object> currentContractSummary,
            ResumeTransactionView resumeTransaction
    ) {
        Map<String, Object> consistency = buildFrozenContractConsistency(
                "task_contract",
                frozenContractSummary,
                currentContractSummary
        );
        if (resumeTransaction != null) {
            String resumeFailureCode = safeString(resumeTransaction.getFailureCode());
            consistency.put("resume_failure_code", resumeTransaction.getFailureCode());
            consistency.put("resume_base_contract_version", resumeTransaction.getBaseContractVersion());
            consistency.put("resume_base_contract_fingerprint", resumeTransaction.getBaseContractFingerprint());
            consistency.put("resume_candidate_contract_version", resumeTransaction.getCandidateContractVersion());
            consistency.put("resume_candidate_contract_fingerprint", resumeTransaction.getCandidateContractFingerprint());
            consistency.put(
                    "resume_mismatch_code",
                    resumeFailureCode.startsWith("CONTRACT_") ? resumeTransaction.getFailureCode() : null
            );
            consistency.put(
                    "resume_detected_contract_drift",
                    "CONTRACT_VERSION_MISMATCH".equals(resumeTransaction.getFailureCode())
                            || "CONTRACT_FINGERPRINT_MISMATCH".equals(resumeTransaction.getFailureCode())
                            || (!safeString(resumeTransaction.getBaseContractVersion()).isBlank()
                            && !safeString(resumeTransaction.getCandidateContractVersion()).isBlank()
                            && (!Objects.equals(resumeTransaction.getBaseContractVersion(), resumeTransaction.getCandidateContractVersion())
                            || !Objects.equals(
                                    safeString(resumeTransaction.getBaseContractFingerprint()),
                                    safeString(resumeTransaction.getCandidateContractFingerprint())
                            )))
            );
            Map<String, Object> resumeCompatibility = buildContractCompatibilityView(
                    resumeFailureCode.startsWith("CONTRACT_") ? resumeFailureCode : null
            );
            consistency.put("resume_compatibility_code", resumeCompatibility.get("compatibility_code"));
            consistency.put("resume_migration_hint", resumeCompatibility.get("migration_hint"));
        } else {
            consistency.put("resume_mismatch_code", null);
            consistency.put("resume_detected_contract_drift", false);
            consistency.put("resume_compatibility_code", null);
            consistency.put("resume_migration_hint", null);
        }
        return consistency;
    }

    static Map<String, Object> buildResumeContractDriftEvaluation(JsonNode frozenPass1Node, JsonNode currentPass1Node) {
        Map<String, Object> frozenSummary = buildContractSummary(frozenPass1Node);
        Map<String, Object> currentSummary = buildContractSummary(currentPass1Node);
        String frozenVersion = safeString(objectStringValue(frozenSummary.get("contract_version")));
        String frozenFingerprint = safeString(objectStringValue(frozenSummary.get("contract_fingerprint")));
        if (frozenVersion.isBlank() && frozenFingerprint.isBlank()) {
            return Map.of();
        }
        String currentVersion = safeString(objectStringValue(currentSummary.get("contract_version")));
        String currentFingerprint = safeString(objectStringValue(currentSummary.get("contract_fingerprint")));
        String mismatchCode = determineContractMismatchCode(
                true,
                !currentVersion.isBlank() || !currentFingerprint.isBlank(),
                frozenVersion,
                frozenFingerprint,
                currentVersion,
                currentFingerprint
        );
        if (mismatchCode == null) {
            return Map.of();
        }
        Map<String, Object> evaluation = new LinkedHashMap<>();
        String failureReason = mismatchCode + ": frozen="
                + frozenVersion
                + "/"
                + frozenFingerprint
                + " current="
                + currentVersion
                + "/"
                + currentFingerprint;
        evaluation.put("failure_code", mismatchCode);
        evaluation.put("mismatch_code", mismatchCode);
        evaluation.put("failure_reason", failureReason);
        evaluation.put("frozen_contract_version", frozenVersion);
        evaluation.put("frozen_contract_fingerprint", frozenFingerprint);
        evaluation.put("current_contract_version", currentVersion);
        evaluation.put("current_contract_fingerprint", currentFingerprint);
        evaluation.putAll(buildContractCompatibilityView(mismatchCode));
        return evaluation;
    }

    static Map<String, Object> buildResumeContractAuditDetail(Map<String, Object> drift) {
        if (drift == null || drift.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("failure_code", drift.get("failure_code"));
        detail.put("mismatch_code", drift.get("mismatch_code"));
        detail.put("compatibility_code", drift.get("compatibility_code"));
        detail.put("migration_hint", drift.get("migration_hint"));
        detail.put("failure_reason", drift.get("failure_reason"));
        detail.put("frozen_contract_version", drift.get("frozen_contract_version"));
        detail.put("frozen_contract_fingerprint", drift.get("frozen_contract_fingerprint"));
        detail.put("current_contract_version", drift.get("current_contract_version"));
        detail.put("current_contract_fingerprint", drift.get("current_contract_fingerprint"));
        return detail;
    }

    static Map<String, Object> enrichAuditDetailWithContract(JsonNode pass1Node, Map<String, Object> currentDetail) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (currentDetail != null && !currentDetail.isEmpty()) {
            enriched.putAll(currentDetail);
        }
        enriched.put("contract_identity", buildContractSummary(pass1Node));
        return enriched;
    }

    static String contractConflictReason(String mismatchCode) {
        return switch (safeString(mismatchCode)) {
            case "CONTRACT_FINGERPRINT_MISMATCH" -> "Contract fingerprint mismatch";
            case "CURRENT_CONTRACT_MISSING" -> "Current contract identity missing";
            case "FROZEN_CONTRACT_MISSING" -> "Frozen contract identity missing";
            case "CONTRACT_IDENTITY_UNAVAILABLE" -> "Contract identity unavailable";
            default -> "Contract version mismatch";
        };
    }

    static Map<String, Object> buildContractCompatibilityView(String consistencyCode) {
        Map<String, Object> view = new LinkedHashMap<>();
        String normalized = safeString(consistencyCode);
        if (normalized.isBlank() || "CONTRACT_MATCHED".equals(normalized)) {
            view.put("compatibility_code", "COMPATIBLE");
            view.put("migration_hint", "NO_ACTION");
            return view;
        }
        switch (normalized) {
            case "CONTRACT_VERSION_MISMATCH" -> {
                view.put("compatibility_code", "INCOMPATIBLE");
                view.put("migration_hint", "REGENERATE_PASS1_AND_REFREEZE");
            }
            case "CONTRACT_FINGERPRINT_MISMATCH" -> {
                view.put("compatibility_code", "INCOMPATIBLE");
                view.put("migration_hint", "REFREEZE_REQUIRED");
            }
            case "FROZEN_CONTRACT_MISSING" -> {
                view.put("compatibility_code", "IDENTITY_INCOMPLETE");
                view.put("migration_hint", "REBUILD_FROZEN_CONTRACT_IDENTITY");
            }
            case "CURRENT_CONTRACT_MISSING" -> {
                view.put("compatibility_code", "IDENTITY_INCOMPLETE");
                view.put("migration_hint", "RELOAD_CURRENT_CONTRACT_IDENTITY");
            }
            case "CONTRACT_IDENTITY_UNAVAILABLE" -> {
                view.put("compatibility_code", "IDENTITY_UNKNOWN");
                view.put("migration_hint", "RECONSTRUCT_CONTRACT_IDENTITY");
            }
            default -> {
                view.put("compatibility_code", "IDENTITY_UNKNOWN");
                view.put("migration_hint", "MANUAL_REVIEW");
            }
        }
        return view;
    }

    static String determineContractMismatchCode(
            boolean frozenPresent,
            boolean currentPresent,
            String frozenVersion,
            String frozenFingerprint,
            String currentVersion,
            String currentFingerprint
    ) {
        if (!frozenPresent && !currentPresent) {
            return "CONTRACT_IDENTITY_UNAVAILABLE";
        }
        if (!frozenPresent) {
            return "FROZEN_CONTRACT_MISSING";
        }
        if (!currentPresent) {
            return "CURRENT_CONTRACT_MISSING";
        }
        if (!Objects.equals(frozenVersion, currentVersion)) {
            return "CONTRACT_VERSION_MISMATCH";
        }
        if (!Objects.equals(frozenFingerprint, currentFingerprint)) {
            return "CONTRACT_FINGERPRINT_MISMATCH";
        }
        return null;
    }

    private static String objectStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }
}
