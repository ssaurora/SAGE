package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.model.TaskState;

import java.util.Map;

final class TaskResumeGovernanceSupport {

    private TaskResumeGovernanceSupport() {
    }

    static ResumeCatalogScope buildResumeCatalogScope(
            TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts,
            int candidateInventoryVersion
    ) {
        CatalogConsistencyProjector.CatalogIdentity identity = currentCatalogFacts == null
                ? CatalogConsistencyProjector.catalogIdentity(null)
                : currentCatalogFacts.identity();
        return new ResumeCatalogScope(
                identity.inventoryVersion(),
                identity.revision(),
                identity.fingerprint(),
                candidateInventoryVersion,
                candidateInventoryVersion,
                candidateInventoryVersion,
                identity.fingerprint()
        );
    }

    static ResumeCatalogScope buildForceRevertCatalogScope(
            TaskState taskState,
            TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts
    ) {
        CatalogConsistencyProjector.CatalogIdentity identity = currentCatalogFacts == null
                ? CatalogConsistencyProjector.catalogIdentity(null)
                : currentCatalogFacts.identity();
        int currentInventoryVersion = taskState == null || taskState.getInventoryVersion() == null
                ? 0
                : taskState.getInventoryVersion();
        return new ResumeCatalogScope(
                identity.inventoryVersion(),
                identity.revision(),
                identity.fingerprint(),
                currentInventoryVersion,
                currentInventoryVersion,
                identity.revision(),
                identity.fingerprint()
        );
    }

    static ResumeContractDrift buildResumeContractDrift(JsonNode frozenPass1Node, JsonNode currentPass1Node) {
        Map<String, Object> drift = ContractConsistencyProjector.buildResumeContractDriftEvaluation(
                frozenPass1Node,
                currentPass1Node
        );
        return new ResumeContractDrift(
                drift,
                stringValue(drift.get("mismatch_code")),
                stringValue(drift.get("failure_reason")),
                stringValue(drift.get("frozen_contract_version")),
                stringValue(drift.get("frozen_contract_fingerprint")),
                stringValue(drift.get("current_contract_version")),
                stringValue(drift.get("current_contract_fingerprint"))
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record ResumeCatalogScope(
            Integer baseCatalogInventoryVersion,
            Integer baseCatalogRevision,
            String baseCatalogFingerprint,
            Integer candidateInventoryVersion,
            Integer candidateCatalogInventoryVersion,
            Integer candidateCatalogRevision,
            String candidateCatalogFingerprint
    ) {
    }

    record ResumeContractDrift(
            Map<String, Object> raw,
            String mismatchCode,
            String failureReason,
            String baseContractVersion,
            String baseContractFingerprint,
            String candidateContractVersion,
            String candidateContractFingerprint
    ) {
        boolean isPresent() {
            return raw != null && !raw.isEmpty();
        }

        Map<String, Object> toAuditDetail() {
            return ContractConsistencyProjector.buildResumeContractAuditDetail(raw);
        }

        String conflictReason() {
            return ContractConsistencyProjector.contractConflictReason(mismatchCode);
        }
    }
}
