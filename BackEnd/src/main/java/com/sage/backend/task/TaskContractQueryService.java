package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskContractResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskContractQueryService {

    private final ObjectMapper objectMapper;

    public TaskContractQueryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaskContractResponse buildTaskContractResponse(
            String taskId,
            TaskState taskState,
            AnalysisManifest activeManifest,
            List<AuditRecord> auditRecords
    ) {
        TaskContractResponse response = new TaskContractResponse();
        response.setTaskId(taskId);
        TaskQuerySupport.applyContractQueryPayload(response, taskState, activeManifest, auditRecords, objectMapper);
        return response;
    }
}
