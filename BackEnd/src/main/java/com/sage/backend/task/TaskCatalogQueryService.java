package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskCatalogSnapshot;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskCatalogQueryService {

    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final ObjectMapper objectMapper;

    public TaskCatalogQueryService(TaskCatalogSnapshotService taskCatalogSnapshotService, ObjectMapper objectMapper) {
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.objectMapper = objectMapper;
    }

    public TaskCatalogResponse buildTaskCatalogResponse(
            String taskId,
            TaskState taskState,
            List<TaskAttachment> attachments,
            List<AuditRecord> auditRecords
    ) {
        Map<String, Object> currentCatalogSummary = taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                currentInventoryVersion(taskState)
        );
        TaskCatalogResponse response = new TaskCatalogResponse();
        response.setTaskId(taskId);
        response.setInventoryVersion(currentInventoryVersion(taskState));
        response.setCatalogSummary(currentCatalogSummary);
        response.setCatalogFacts(AttachmentCatalogProjector.project(attachments));

        TaskCatalogSnapshot latestSnapshot = taskCatalogSnapshotService.findLatestCatalogSnapshot(taskId);
        Map<String, Object> latestSnapshotSummary = readJsonMap(latestSnapshot == null ? null : latestSnapshot.getCatalogSummaryJson());
        response.setLatestSnapshot(buildCatalogSnapshotView(latestSnapshot, latestSnapshotSummary));
        Map<String, Object> queryCatalogConsistency = CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                "task_catalog_query",
                latestSnapshotSummary,
                currentCatalogSummary
        );
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "task_catalog_query_governance",
                latestSnapshotSummary,
                currentCatalogSummary,
                queryCatalogConsistency
        ));

        if (auditRecords != null) {
            for (AuditRecord auditRecord : auditRecords) {
                Map<String, Object> detail = readJsonMap(auditRecord.getDetailJson());
                if (!CatalogGovernanceAssembler.hasAuditCatalogEvidence(detail)) {
                    continue;
                }
                TaskCatalogResponse.AuditCatalogItem item = new TaskCatalogResponse.AuditCatalogItem();
                item.setId(auditRecord.getId());
                item.setActionType(auditRecord.getActionType());
                item.setActionResult(auditRecord.getActionResult());
                item.setTraceId(auditRecord.getTraceId());
                item.setCreatedAt(auditRecord.getCreatedAt() == null ? null : auditRecord.getCreatedAt().toString());
                item.setCatalogGovernance(CatalogGovernanceAssembler.buildAudit(detail, currentCatalogSummary));
                response.getAuditItems().add(item);
            }
        }
        return response;
    }

    private TaskCatalogResponse.SnapshotView buildCatalogSnapshotView(
            TaskCatalogSnapshot snapshot,
            Map<String, Object> catalogSummary
    ) {
        if (snapshot == null) {
            return null;
        }
        TaskCatalogResponse.SnapshotView view = new TaskCatalogResponse.SnapshotView();
        view.setId(snapshot.getId());
        view.setInventoryVersion(snapshot.getInventoryVersion());
        view.setCatalogRevision(snapshot.getCatalogRevision());
        view.setCatalogFingerprint(snapshot.getCatalogFingerprint());
        view.setCatalogSource(snapshot.getCatalogSource());
        view.setCreatedAt(snapshot.getCreatedAt() == null ? null : snapshot.getCreatedAt().toString());
        view.setCatalogSummary(catalogSummary);
        return view;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(sourceJson, Map.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private int currentInventoryVersion(TaskState taskState) {
        if (taskState == null || taskState.getInventoryVersion() == null) {
            return 0;
        }
        return taskState.getInventoryVersion();
    }
}
