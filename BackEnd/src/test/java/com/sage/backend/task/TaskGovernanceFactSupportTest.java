package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskGovernanceFactSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveCurrentCatalogFactsBuildsIdentityFromResolvedSummary() {
        TaskAttachmentMapper attachmentMapper = mock(TaskAttachmentMapper.class);
        TaskCatalogSnapshotService snapshotService = mock(TaskCatalogSnapshotService.class);
        when(attachmentMapper.findByTaskId("task-1")).thenReturn(List.of());
        when(snapshotService.resolveCatalogSummary("task-1", List.of(), 3)).thenReturn(Map.of(
                "catalog_source", "task_catalog_snapshot",
                "catalog_inventory_version", 3,
                "catalog_revision", 7,
                "catalog_fingerprint", "catalog-fingerprint"
        ));

        TaskGovernanceFactSupport.CatalogFacts facts = TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                "task-1",
                taskState(3),
                attachmentMapper,
                snapshotService
        );

        assertEquals(3, facts.identity().inventoryVersion());
        assertEquals(7, facts.identity().revision());
        assertEquals("catalog-fingerprint", facts.identity().fingerprint());
        assertEquals("task_catalog_snapshot", facts.summary().get("catalog_source"));
    }

    @Test
    void resolveManifestCatalogSummaryFallsBackToSnapshotServiceWhenFrozenSummaryMissing() {
        TaskCatalogSnapshotService snapshotService = mock(TaskCatalogSnapshotService.class);
        when(snapshotService.resolveManifestCatalogSummary(null, "task-2", List.of(), 0)).thenReturn(Map.of(
                "catalog_source", "task_catalog_snapshot",
                "catalog_inventory_version", 0,
                "catalog_revision", 1,
                "catalog_fingerprint", "fallback-fingerprint"
        ));

        Map<String, Object> summary = TaskGovernanceFactSupport.resolveManifestCatalogSummary(
                new AnalysisManifest(),
                "task-2",
                List.of(),
                taskState(null),
                snapshotService,
                objectMapper
        );

        assertEquals("fallback-fingerprint", summary.get("catalog_fingerprint"));
    }

    @Test
    void resolveManifestContractSummaryAndAuditEnrichmentReuseSameContractIdentity() throws Exception {
        JsonNode pass1Node = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "contract_version": "contracts_v2",
                    "contract_fingerprint": "fingerprint_v2",
                    "contracts": {
                      "submit_job": {
                        "input_schema": "runtime_submission_v1"
                      }
                    }
                  }
                }
                """);
        AnalysisManifest manifest = new AnalysisManifest();
        manifest.setContractSummaryJson("""
                {
                  "contract_version": "contracts_v1",
                  "contract_fingerprint": "fingerprint_v1",
                  "contract_count": 1,
                  "contract_names": ["submit_job"],
                  "contract_present": true
                }
                """);

        Map<String, Object> manifestSummary = TaskGovernanceFactSupport.resolveManifestContractSummary(manifest, objectMapper);
        Map<String, Object> enrichedDetail = TaskGovernanceFactSupport.readJsonMap(
                TaskGovernanceFactSupport.enrichAuditDetailWithContract(
                        pass1Node,
                        "{\"failure_code\":\"X\"}",
                        objectMapper
                ),
                objectMapper
        );

        assertEquals("contracts_v1", manifestSummary.get("contract_version"));
        assertEquals("contracts_v2", ((Map<?, ?>) enrichedDetail.get("contract_identity")).get("contract_version"));
        assertEquals("X", enrichedDetail.get("failure_code"));
    }

    @Test
    void readJsonMapReturnsNullForInvalidJson() {
        assertNull(TaskGovernanceFactSupport.readJsonMap("{not-json", objectMapper));
    }

    private TaskState taskState(Integer inventoryVersion) {
        TaskState taskState = new TaskState();
        taskState.setInventoryVersion(inventoryVersion);
        return taskState;
    }
}
