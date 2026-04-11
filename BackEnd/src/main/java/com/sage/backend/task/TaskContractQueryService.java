package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.ResumeTransactionView;
import com.sage.backend.task.dto.TaskContractResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        JsonNode pass1Projection = TaskQuerySupport.readJsonNode(taskState == null ? null : taskState.getPass1ResultJson(), objectMapper);
        Map<String, Object> manifestContractSummary = TaskQuerySupport.readJsonMap(
                activeManifest == null ? null : activeManifest.getContractSummaryJson(),
                objectMapper
        );
        Map<String, Object> frozenContractSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                manifestContractSummary,
                pass1Projection
        );
        Map<String, Object> currentContractSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        ResumeTransactionView resumeTransaction = TaskProjectionBuilder.buildResumeTransaction(
                TaskQuerySupport.readJsonNode(taskState == null ? null : taskState.getResumeTxnJson(), objectMapper)
        );
        Map<String, Object> contractConsistency = ContractConsistencyProjector.buildDetailContractConsistency(
                frozenContractSummary,
                currentContractSummary,
                resumeTransaction
        );

        TaskContractResponse response = new TaskContractResponse();
        response.setTaskId(taskId);
        response.setFrozenContractSummary(frozenContractSummary);
        response.setCurrentContractSummary(currentContractSummary);
        response.setActiveManifest(buildManifestContractView(activeManifest, manifestContractSummary));
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "task_contract_query_governance",
                frozenContractSummary,
                currentContractSummary,
                contractConsistency,
                resumeTransaction
        ));

        if (auditRecords != null) {
            for (AuditRecord auditRecord : auditRecords) {
                Map<String, Object> detail = TaskQuerySupport.readJsonMap(auditRecord.getDetailJson(), objectMapper);
                if (!ContractGovernanceAssembler.hasAuditContractEvidence(detail)) {
                    continue;
                }
                TaskContractResponse.AuditContractItem item = new TaskContractResponse.AuditContractItem();
                item.setId(auditRecord.getId());
                item.setActionType(auditRecord.getActionType());
                item.setActionResult(auditRecord.getActionResult());
                item.setTraceId(auditRecord.getTraceId());
                item.setCreatedAt(auditRecord.getCreatedAt() == null ? null : auditRecord.getCreatedAt().toString());
                item.setContractGovernance(ContractGovernanceAssembler.buildAudit(detail));
                response.getAuditItems().add(item);
            }
        }
        return response;
    }

    private TaskContractResponse.ManifestContractView buildManifestContractView(
            AnalysisManifest manifest,
            Map<String, Object> contractSummary
    ) {
        if (manifest == null) {
            return null;
        }
        TaskContractResponse.ManifestContractView view = new TaskContractResponse.ManifestContractView();
        view.setManifestId(manifest.getManifestId());
        view.setManifestVersion(manifest.getManifestVersion());
        view.setFreezeStatus(manifest.getFreezeStatus());
        view.setContractSummary(contractSummary);
        return view;
    }
}
