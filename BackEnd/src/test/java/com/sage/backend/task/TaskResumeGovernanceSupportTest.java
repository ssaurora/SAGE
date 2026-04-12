package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.TaskState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskResumeGovernanceSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildResumeCatalogScopeProjectsBaseAndCandidateFacts() {
        TaskGovernanceFactSupport.CatalogFacts currentFacts = TaskGovernanceFactSupport.catalogFacts(Map.of(
                "catalog_inventory_version", 3,
                "catalog_revision", 7,
                "catalog_fingerprint", "catalog-fingerprint"
        ));

        TaskResumeGovernanceSupport.ResumeCatalogScope scope =
                TaskResumeGovernanceSupport.buildResumeCatalogScope(currentFacts, 4);

        assertEquals(3, scope.baseCatalogInventoryVersion());
        assertEquals(7, scope.baseCatalogRevision());
        assertEquals("catalog-fingerprint", scope.baseCatalogFingerprint());
        assertEquals(4, scope.candidateInventoryVersion());
        assertEquals(4, scope.candidateCatalogInventoryVersion());
        assertEquals(4, scope.candidateCatalogRevision());
        assertEquals("catalog-fingerprint", scope.candidateCatalogFingerprint());
    }

    @Test
    void buildForceRevertCatalogScopePreservesCurrentCatalogIdentity() {
        TaskGovernanceFactSupport.CatalogFacts currentFacts = TaskGovernanceFactSupport.catalogFacts(Map.of(
                "catalog_inventory_version", 5,
                "catalog_revision", 9,
                "catalog_fingerprint", "current-fingerprint"
        ));
        TaskState taskState = new TaskState();
        taskState.setInventoryVersion(5);

        TaskResumeGovernanceSupport.ResumeCatalogScope scope =
                TaskResumeGovernanceSupport.buildForceRevertCatalogScope(taskState, currentFacts);

        assertEquals(5, scope.baseCatalogInventoryVersion());
        assertEquals(9, scope.baseCatalogRevision());
        assertEquals(5, scope.candidateInventoryVersion());
        assertEquals(9, scope.candidateCatalogRevision());
        assertEquals("current-fingerprint", scope.candidateCatalogFingerprint());
    }

    @Test
    void buildResumeContractDriftWrapsProjectorOutput() throws Exception {
        JsonNode frozenPass1 = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contract_version": "contracts_v1",
                    "contract_fingerprint": "fingerprint_v1"
                  }
                }
                """);
        JsonNode currentPass1 = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contract_version": "contracts_v2",
                    "contract_fingerprint": "fingerprint_v2"
                  }
                }
                """);

        TaskResumeGovernanceSupport.ResumeContractDrift drift =
                TaskResumeGovernanceSupport.buildResumeContractDrift(frozenPass1, currentPass1);

        assertTrue(drift.isPresent());
        assertEquals("CONTRACT_VERSION_MISMATCH", drift.mismatchCode());
        assertEquals("contracts_v1", drift.baseContractVersion());
        assertEquals("contracts_v2", drift.candidateContractVersion());
        assertEquals("CONTRACT_VERSION_MISMATCH", drift.toAuditDetail().get("mismatch_code"));
        assertEquals("Contract version mismatch", drift.conflictReason());
    }

    @Test
    void buildResumeContractDriftReturnsEmptyWhenNoMismatch() throws Exception {
        JsonNode pass1 = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contract_version": "contracts_v1",
                    "contract_fingerprint": "fingerprint_v1"
                  }
                }
                """);

        TaskResumeGovernanceSupport.ResumeContractDrift drift =
                TaskResumeGovernanceSupport.buildResumeContractDrift(pass1, pass1);

        assertFalse(drift.isPresent());
    }
}
