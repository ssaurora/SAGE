package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.TaskCatalogSnapshotMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskCatalogSnapshot;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskAuditResponse;
import com.sage.backend.task.dto.TaskCatalogResponse;
import com.sage.backend.task.dto.TaskContractResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskQueryServiceProjectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildTaskAuditResponseBuildsGovernanceAndKeepsInvalidDetailAsEmptyObject() throws Exception {
        TaskCatalogSnapshotMapper snapshotMapper = mock(TaskCatalogSnapshotMapper.class);
        when(snapshotMapper.findByTaskIdAndInventoryVersion("task-1", 3)).thenReturn(snapshot(
                11L,
                3,
                7,
                "current-fingerprint",
                Map.of(
                        "catalog_source", "task_catalog_snapshot",
                        "catalog_inventory_version", 3,
                        "catalog_revision", 7,
                        "catalog_fingerprint", "current-fingerprint",
                        "catalog_asset_count", 1,
                        "catalog_ready_asset_count", 1,
                        "catalog_blacklisted_asset_count", 0,
                        "catalog_role_coverage_count", 1,
                        "catalog_ready_role_names", List.of("precipitation")
                )
        ));
        TaskAuditQueryService service = new TaskAuditQueryService(
                new TaskCatalogSnapshotService(snapshotMapper, objectMapper),
                objectMapper
        );

        TaskAuditResponse response = service.buildTaskAuditResponse(
                "task-1",
                taskState(3, null),
                List.of(),
                List.of(
                        auditRecord(1L, objectMapper.writeValueAsString(Map.of(
                                "contract_identity", Map.of(
                                        "contract_version", "contracts_v2",
                                        "contract_fingerprint", "fingerprint_v2",
                                        "contract_count", 1,
                                        "contract_names", List.of("submit_job"),
                                        "contract_present", true
                                ),
                                "frozen_contract_version", "contracts_v1",
                                "frozen_contract_fingerprint", "fingerprint_v1",
                                "mismatch_code", "CONTRACT_VERSION_MISMATCH",
                                "catalog_summary", Map.of(
                                        "catalog_source", "audit_catalog_summary",
                                        "catalog_inventory_version", 2,
                                        "catalog_revision", 6,
                                        "catalog_fingerprint", "audit-fingerprint",
                                        "catalog_asset_count", 1,
                                        "catalog_ready_asset_count", 1,
                                        "catalog_blacklisted_asset_count", 0,
                                        "catalog_role_coverage_count", 1,
                                        "catalog_ready_role_names", List.of("precipitation")
                                )
                        ))),
                        auditRecord(2L, "{not-json")
                )
        );

        assertEquals(2, response.getItems().size());
        assertEquals("audit_contract_governance", response.getItems().get(0).getContractGovernance().getScope());
        assertEquals("CONTRACT_VERSION_MISMATCH", response.getItems().get(0).getContractGovernance().getConsistency().getMismatchCode());
        assertEquals("audit_catalog_governance", response.getItems().get(0).getCatalogGovernance().getScope());
        assertEquals(6, response.getItems().get(0).getCatalogGovernance().getBaselineCatalogSummary().getCatalogRevision());
        assertEquals(Map.of(), response.getItems().get(1).getDetail());
        assertEquals("audit_contract_governance", response.getItems().get(1).getContractGovernance().getScope());
        assertEquals(false, response.getItems().get(1).getCatalogGovernance().getBaselineCatalogSummary().getCatalogPresent());
    }

    @Test
    void buildTaskCatalogResponseKeepsOnlyAuditItemsWithCatalogEvidence() throws Exception {
        TaskCatalogSnapshotMapper snapshotMapper = mock(TaskCatalogSnapshotMapper.class);
        when(snapshotMapper.findByTaskIdAndInventoryVersion("task-2", 2)).thenReturn(snapshot(
                21L,
                2,
                9,
                "current-catalog-fingerprint",
                Map.of(
                        "catalog_source", "task_catalog_snapshot",
                        "catalog_inventory_version", 2,
                        "catalog_revision", 9,
                        "catalog_fingerprint", "current-catalog-fingerprint",
                        "catalog_asset_count", 1,
                        "catalog_ready_asset_count", 1,
                        "catalog_blacklisted_asset_count", 0,
                        "catalog_role_coverage_count", 1,
                        "catalog_ready_role_names", List.of("precipitation")
                )
        ));
        when(snapshotMapper.findLatestByTaskId("task-2")).thenReturn(snapshot(
                22L,
                1,
                8,
                "frozen-catalog-fingerprint",
                Map.of(
                        "catalog_source", "task_catalog_snapshot",
                        "catalog_inventory_version", 1,
                        "catalog_revision", 8,
                        "catalog_fingerprint", "frozen-catalog-fingerprint",
                        "catalog_asset_count", 1,
                        "catalog_ready_asset_count", 1,
                        "catalog_blacklisted_asset_count", 0,
                        "catalog_role_coverage_count", 1,
                        "catalog_ready_role_names", List.of("precipitation")
                )
        ));
        TaskCatalogQueryService service = new TaskCatalogQueryService(
                new TaskCatalogSnapshotService(snapshotMapper, objectMapper),
                objectMapper
        );

        TaskCatalogResponse response = service.buildTaskCatalogResponse(
                "task-2",
                taskState(2, null),
                List.of(),
                List.of(
                        auditRecord(3L, objectMapper.writeValueAsString(Map.of(
                                "catalog_identity", Map.of(
                                        "catalog_source", "audit_candidate_catalog_identity",
                                        "catalog_inventory_version", 2,
                                        "catalog_revision", 8,
                                        "catalog_fingerprint", "frozen-catalog-fingerprint",
                                        "catalog_asset_count", 0,
                                        "catalog_ready_asset_count", 0,
                                        "catalog_blacklisted_asset_count", 0,
                                        "catalog_role_coverage_count", 0,
                                        "catalog_ready_role_names", List.of()
                                )
                        ))),
                        auditRecord(4L, objectMapper.writeValueAsString(Map.of(
                                "contract_identity", Map.of(
                                        "contract_version", "contracts_v2",
                                        "contract_fingerprint", "fingerprint_v2",
                                        "contract_count", 1,
                                        "contract_names", List.of("submit_job"),
                                        "contract_present", true
                                )
                        )))
                )
        );

        assertEquals(2, response.getInventoryVersion());
        assertEquals("task_catalog_query_governance", response.getCatalogGovernance().getScope());
        assertNotNull(response.getLatestSnapshot());
        assertEquals(8, response.getLatestSnapshot().getCatalogRevision());
        assertEquals(1, response.getAuditItems().size());
        assertEquals(3L, response.getAuditItems().get(0).getId());
        assertEquals("audit_catalog_governance", response.getAuditItems().get(0).getCatalogGovernance().getScope());
    }

    @Test
    void buildTaskContractResponseKeepsOnlyAuditItemsWithContractEvidence() throws Exception {
        TaskContractQueryService service = new TaskContractQueryService(objectMapper);
        AnalysisManifest manifest = new AnalysisManifest();
        manifest.setManifestId("manifest-1");
        manifest.setManifestVersion(5);
        manifest.setFreezeStatus("FROZEN");
        manifest.setContractSummaryJson(objectMapper.writeValueAsString(Map.of(
                "contract_version", "contracts_v1",
                "contract_fingerprint", "fingerprint_v1",
                "contract_count", 1,
                "contract_names", List.of("submit_job"),
                "contract_present", true
        )));

        TaskContractResponse response = service.buildTaskContractResponse(
                "task-3",
                taskState(1, """
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
                        """),
                manifest,
                List.of(
                        auditRecord(5L, objectMapper.writeValueAsString(Map.of(
                                "current_contract_version", "contracts_v2",
                                "current_contract_fingerprint", "fingerprint_v2",
                                "frozen_contract_version", "contracts_v1",
                                "frozen_contract_fingerprint", "fingerprint_v1",
                                "failure_code", "CONTRACT_VERSION_MISMATCH",
                                "mismatch_code", "CONTRACT_VERSION_MISMATCH"
                        ))),
                        auditRecord(6L, objectMapper.writeValueAsString(Map.of(
                                "catalog_summary", Map.of(
                                        "catalog_source", "audit_catalog_summary",
                                        "catalog_inventory_version", 1,
                                        "catalog_revision", 4,
                                        "catalog_fingerprint", "catalog-fingerprint"
                                )
                        )))
                )
        );

        assertEquals("task_contract_query_governance", response.getContractGovernance().getScope());
        assertEquals("contracts_v1", response.getFrozenContractSummary().get("contract_version"));
        assertEquals("contracts_v2", response.getCurrentContractSummary().get("contract_version"));
        assertNotNull(response.getActiveManifest());
        assertEquals("manifest-1", response.getActiveManifest().getManifestId());
        assertEquals(1, response.getAuditItems().size());
        assertEquals(5L, response.getAuditItems().get(0).getId());
        assertEquals("audit_contract_governance", response.getAuditItems().get(0).getContractGovernance().getScope());
    }

    @Test
    void queryServicesHandleMissingContextGracefully() throws Exception {
        TaskCatalogSnapshotMapper snapshotMapper = mock(TaskCatalogSnapshotMapper.class);
        when(snapshotMapper.findByTaskIdAndInventoryVersion("task-4", 0)).thenReturn(snapshot(
                41L,
                0,
                1,
                "catalog-fingerprint",
                Map.of(
                        "catalog_source", "task_catalog_snapshot",
                        "catalog_inventory_version", 0,
                        "catalog_revision", 1,
                        "catalog_fingerprint", "catalog-fingerprint",
                        "catalog_asset_count", 0,
                        "catalog_ready_asset_count", 0,
                        "catalog_blacklisted_asset_count", 0,
                        "catalog_role_coverage_count", 0,
                        "catalog_ready_role_names", List.of()
                )
        ));
        when(snapshotMapper.findLatestByTaskId("task-4")).thenReturn(null);

        TaskCatalogSnapshotService snapshotService = new TaskCatalogSnapshotService(snapshotMapper, objectMapper);
        TaskAuditQueryService auditService = new TaskAuditQueryService(snapshotService, objectMapper);
        TaskCatalogQueryService catalogService = new TaskCatalogQueryService(snapshotService, objectMapper);
        TaskContractQueryService contractService = new TaskContractQueryService(objectMapper);

        TaskState taskState = taskState(null, null);

        TaskAuditResponse auditResponse = auditService.buildTaskAuditResponse("task-4", taskState, List.of(), null);
        TaskCatalogResponse catalogResponse = catalogService.buildTaskCatalogResponse("task-4", taskState, List.of(), null);
        TaskContractResponse contractResponse = contractService.buildTaskContractResponse("task-4", taskState, null, null);

        assertEquals(0, auditResponse.getItems().size());
        assertEquals(0, catalogResponse.getInventoryVersion());
        assertNull(catalogResponse.getLatestSnapshot());
        assertEquals("task_contract_query_governance", contractResponse.getContractGovernance().getScope());
        assertNull(contractResponse.getActiveManifest());
        assertEquals(0, contractResponse.getAuditItems().size());
    }

    private TaskState taskState(Integer inventoryVersion, String pass1ResultJson) {
        TaskState taskState = new TaskState();
        taskState.setTaskId("task");
        taskState.setInventoryVersion(inventoryVersion);
        taskState.setPass1ResultJson(pass1ResultJson);
        return taskState;
    }

    private AuditRecord auditRecord(Long id, String detailJson) {
        AuditRecord record = new AuditRecord();
        record.setId(id);
        record.setTaskId("task");
        record.setActionType("TEST_ACTION");
        record.setActionResult("OK");
        record.setTraceId("trace-" + id);
        record.setDetailJson(detailJson);
        record.setCreatedAt(OffsetDateTime.of(2026, 4, 12, 12, 0, 0, 0, ZoneOffset.UTC));
        return record;
    }

    private TaskCatalogSnapshot snapshot(
            Long id,
            Integer inventoryVersion,
            Integer catalogRevision,
            String catalogFingerprint,
            Map<String, Object> catalogSummary
    ) throws Exception {
        TaskCatalogSnapshot snapshot = new TaskCatalogSnapshot();
        snapshot.setId(id);
        snapshot.setTaskId("task");
        snapshot.setInventoryVersion(inventoryVersion);
        snapshot.setCatalogRevision(catalogRevision);
        snapshot.setCatalogFingerprint(catalogFingerprint);
        snapshot.setCatalogSource("task_catalog_snapshot");
        snapshot.setCatalogSummaryJson(objectMapper.writeValueAsString(catalogSummary));
        snapshot.setCreatedAt(OffsetDateTime.of(2026, 4, 12, 12, 0, 0, 0, ZoneOffset.UTC));
        return snapshot;
    }
}
