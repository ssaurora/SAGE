package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.List;

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
        TaskCatalogResponse response = new TaskCatalogResponse();
        response.setTaskId(taskId);
        TaskQuerySupport.applyCatalogQueryPayload(
                response,
                taskState,
                attachments,
                taskCatalogSnapshotService.resolveCatalogSummary(
                        taskId,
                        attachments,
                        TaskQuerySupport.currentInventoryVersion(taskState)
                ),
                taskCatalogSnapshotService.findLatestCatalogSnapshot(taskId),
                auditRecords,
                objectMapper
        );
        return response;
    }
}
