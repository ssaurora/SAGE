package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskAuditResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskAuditQueryService {

    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final ObjectMapper objectMapper;

    public TaskAuditQueryService(TaskCatalogSnapshotService taskCatalogSnapshotService, ObjectMapper objectMapper) {
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.objectMapper = objectMapper;
    }

    public TaskAuditResponse buildTaskAuditResponse(
            String taskId,
            TaskState taskState,
            List<TaskAttachment> attachments,
            List<AuditRecord> auditRecords
    ) {
        Map<String, Object> currentCatalogSummary = taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                TaskQuerySupport.currentInventoryVersion(taskState)
        );
        TaskAuditResponse response = new TaskAuditResponse();
        response.setTaskId(taskId);
        if (auditRecords == null) {
            return response;
        }
        for (AuditRecord auditRecord : auditRecords) {
            TaskAuditResponse.AuditItem item = new TaskAuditResponse.AuditItem();
            item.setId(auditRecord.getId());
            item.setActionType(auditRecord.getActionType());
            item.setActionResult(auditRecord.getActionResult());
            item.setTraceId(auditRecord.getTraceId());
            item.setCreatedAt(auditRecord.getCreatedAt() == null ? null : auditRecord.getCreatedAt().toString());
            Map<String, Object> detail = TaskQuerySupport.readJsonMap(auditRecord.getDetailJson(), objectMapper);
            item.setDetail(detail == null ? Map.of() : detail);
            item.setContractGovernance(ContractGovernanceAssembler.buildAudit(detail));
            item.setCatalogGovernance(CatalogGovernanceAssembler.buildAudit(detail, currentCatalogSummary));
            response.getItems().add(item);
        }
        return response;
    }
}
