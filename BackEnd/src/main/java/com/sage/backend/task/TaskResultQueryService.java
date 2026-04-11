package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.RepairRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskResultResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskResultQueryService {

    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final ObjectMapper objectMapper;

    public TaskResultQueryService(TaskCatalogSnapshotService taskCatalogSnapshotService, ObjectMapper objectMapper) {
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.objectMapper = objectMapper;
    }

    public TaskResultResponse buildTaskResultResponse(
            TaskState taskState,
            List<TaskAttachment> attachments,
            AnalysisManifest activeManifest,
            JobRecord jobRecord,
            RepairRecord latestRepair
    ) {
        String taskId = taskState.getTaskId();
        String activeCaseId = TaskQuerySupport.extractCaseId(activeManifest, objectMapper);

        TaskResultResponse response = new TaskResultResponse();
        response.setTaskId(taskId);
        response.setTaskState(taskState.getCurrentState());
        TaskQuerySupport.applyLifecycleProjection(response, taskState, objectMapper);
        response.setPlanningRevision(taskState.getPlanningRevision());
        response.setCheckpointVersion(taskState.getCheckpointVersion());
        response.setCaseId(activeCaseId);
        JsonNode goalParseRoot = TaskQuerySupport.readJsonNode(taskState.getGoalParseJson(), objectMapper);
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));
        JsonNode passBRoot = TaskQuerySupport.readJsonNode(taskState.getPassbResultJson(), objectMapper);
        JsonNode skillRouteRoot = TaskQuerySupport.readJsonNode(taskState.getSkillRouteJson(), objectMapper);
        response.setSkillId(skillRouteRoot == null ? null : skillRouteRoot.path("skill_id").asText(
                passBRoot == null ? null : passBRoot.path("skill_id").asText(null)
        ));
        response.setSkillVersion(skillRouteRoot == null ? null : skillRouteRoot.path("skill_version").asText(
                passBRoot == null ? null : passBRoot.path("skill_version").asText(null)
        ));
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
        Map<String, Object> frozenCatalogSummary = TaskQuerySupport.readJsonMap(
                activeManifest == null ? null : activeManifest.getCatalogSummaryJson(),
                objectMapper
        );
        Map<String, Object> currentCatalogSummary = TaskQuerySupport.resolveCurrentCatalogSummary(
                taskId,
                attachments,
                taskState,
                taskCatalogSnapshotService
        );
        Map<String, Object> catalogSummary = TaskQuerySupport.resolveManifestCatalogSummary(
                activeManifest,
                taskId,
                attachments,
                taskState,
                taskCatalogSnapshotService,
                objectMapper
        );
        TaskQuerySupport.CatalogProjection catalogProjection = TaskQuerySupport.buildFrozenCatalogProjection(
                "result_catalog",
                "result_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                List.of(),
                "result_input_bindings"
        );
        TaskQuerySupport.applyCatalogProjection(response, catalogProjection);
        response.setCatalogSummary(catalogSummary);
        JsonNode pass1Projection = TaskQuerySupport.readJsonNode(taskState.getPass1ResultJson(), objectMapper);
        TaskQuerySupport.ContractProjection contractProjection = TaskQuerySupport.buildFrozenContractProjection(
                pass1Projection,
                TaskQuerySupport.readJsonMap(activeManifest == null ? null : activeManifest.getContractSummaryJson(), objectMapper),
                response.getResumeTransaction(),
                "result_manifest_contract",
                "result_contract_governance"
        );
        TaskQuerySupport.applyContractProjection(response, contractProjection);
        TaskQuerySupport.applyStageProjection(response, goalParseRoot, skillRouteRoot, passBRoot, objectMapper);
        TaskQuerySupport.applyResultPass2Projection(response, taskState, objectMapper);
        if (jobRecord != null) {
            TaskQuerySupport.applyResultJobProjection(response, jobRecord, activeCaseId, objectMapper);
            catalogProjection = TaskQuerySupport.buildFrozenCatalogProjection(
                    "result_catalog",
                    "result_catalog_governance",
                    frozenCatalogSummary,
                    currentCatalogSummary,
                    TaskQuerySupport.extractResultInputRoleNames(response),
                    "result_input_bindings"
            );
            TaskQuerySupport.applyCatalogProjection(response, catalogProjection);
            response.setCatalogSummary(catalogSummary);
        }
        TaskQuerySupport.applyRepairProjection(response, latestRepair, objectMapper);
        if (activeManifest != null) {
            response.setFreezeStatus(activeManifest.getFreezeStatus());
            response.setGraphDigest(activeManifest.getGraphDigest());
            if (response.getPlanningSummary() == null) {
                response.setPlanningSummary(TaskQuerySupport.resolvePlanningSummary(
                        null,
                        activeManifest.getPlanningSummaryJson(),
                        objectMapper,
                        true
                ));
            }
        }
        return response;
    }
}
