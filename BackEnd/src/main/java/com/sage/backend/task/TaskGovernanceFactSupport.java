package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;

import java.util.List;
import java.util.Map;

final class TaskGovernanceFactSupport {

    private TaskGovernanceFactSupport() {
    }

    static CatalogFacts resolveCurrentCatalogFacts(
            String taskId,
            TaskState taskState,
            TaskAttachmentMapper taskAttachmentMapper,
            TaskCatalogSnapshotService taskCatalogSnapshotService
    ) {
        return resolveCurrentCatalogFacts(
                taskId,
                taskAttachmentMapper.findByTaskId(taskId),
                taskState,
                taskCatalogSnapshotService
        );
    }

    static CatalogFacts resolveCurrentCatalogFacts(
            String taskId,
            List<TaskAttachment> attachments,
            TaskState taskState,
            TaskCatalogSnapshotService taskCatalogSnapshotService
    ) {
        Map<String, Object> summary = taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                currentInventoryVersion(taskState)
        );
        return catalogFacts(summary);
    }

    static CatalogFacts catalogFacts(Map<String, Object> catalogSummary) {
        return new CatalogFacts(catalogSummary, CatalogConsistencyProjector.catalogIdentity(catalogSummary));
    }

    static Map<String, Object> resolveManifestCatalogSummary(
            AnalysisManifest manifest,
            String taskId,
            List<TaskAttachment> attachments,
            TaskState taskState,
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            ObjectMapper objectMapper
    ) {
        Map<String, Object> frozenSummary = readJsonMap(
                manifest == null ? null : manifest.getCatalogSummaryJson(),
                objectMapper
        );
        return taskCatalogSnapshotService.resolveManifestCatalogSummary(
                frozenSummary,
                taskId,
                attachments,
                currentInventoryVersion(taskState)
        );
    }

    static Map<String, Object> resolveManifestContractSummary(AnalysisManifest manifest, ObjectMapper objectMapper) {
        return readJsonMap(manifest == null ? null : manifest.getContractSummaryJson(), objectMapper);
    }

    static String writeContractSummary(JsonNode pass1Node, ObjectMapper objectMapper) {
        return writeJson(ContractConsistencyProjector.buildContractSummary(pass1Node), objectMapper);
    }

    static String enrichAuditDetailWithContract(JsonNode pass1Node, String detailJson, ObjectMapper objectMapper) {
        return writeJson(
                ContractConsistencyProjector.enrichAuditDetailWithContract(
                        pass1Node,
                        readJsonMap(detailJson, objectMapper)
                ),
                objectMapper
        );
    }

    static Map<String, Object> readJsonMap(String sourceJson, ObjectMapper objectMapper) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            return TaskProjectionBuilder.buildJsonObjectView(objectMapper.readTree(sourceJson), objectMapper);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String writeJson(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize governance facts", exception);
        }
    }

    private static int currentInventoryVersion(TaskState taskState) {
        Integer inventoryVersion = taskState == null ? null : taskState.getInventoryVersion();
        return inventoryVersion == null ? 0 : inventoryVersion;
    }

    record CatalogFacts(
            Map<String, Object> summary,
            CatalogConsistencyProjector.CatalogIdentity identity
    ) {
    }
}
