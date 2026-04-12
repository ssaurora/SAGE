package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskAuditResponse;
import org.springframework.stereotype.Service;

import java.util.List;

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
        TaskAuditResponse response = new TaskAuditResponse();
        response.setTaskId(taskId);
        TaskQuerySupport.applyAuditQueryPayload(
                response,
                auditRecords,
                taskCatalogSnapshotService.resolveCatalogSummary(
                        taskId,
                        attachments,
                        TaskQuerySupport.currentInventoryVersion(taskState)
                ),
                objectMapper
        );
        return response;
    }
}
