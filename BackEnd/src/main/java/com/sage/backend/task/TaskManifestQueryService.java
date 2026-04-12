package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.RepairRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskManifestResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskManifestQueryService {

    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final GoalRouteService goalRouteService;
    private final ObjectMapper objectMapper;

    public TaskManifestQueryService(
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            GoalRouteService goalRouteService,
            ObjectMapper objectMapper
    ) {
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.goalRouteService = goalRouteService;
        this.objectMapper = objectMapper;
    }

    public TaskManifestResponse buildTaskManifestResponse(
            TaskState taskState,
            AnalysisManifest manifest,
            List<TaskAttachment> attachments,
            RepairRecord latestRepair,
            JobRecord jobRecord
    ) {
        String taskId = taskState.getTaskId();
        TaskManifestResponse response = new TaskManifestResponse();
        response.setManifestId(manifest.getManifestId());
        response.setTaskId(manifest.getTaskId());
        response.setAttemptNo(manifest.getAttemptNo());
        response.setManifestVersion(manifest.getManifestVersion());
        response.setFreezeStatus(manifest.getFreezeStatus());
        response.setPlanningRevision(manifest.getPlanningRevision());
        response.setCheckpointVersion(manifest.getCheckpointVersion());
        response.setGraphDigest(manifest.getGraphDigest());
        response.setPlanningSummary(TaskQuerySupport.resolvePlanningSummary(
                manifest.getPlanningSummaryJson(),
                null,
                objectMapper,
                true
        ));

        Map<String, Object> frozenCatalogSummary = TaskQuerySupport.readJsonMap(manifest.getCatalogSummaryJson(), objectMapper);
        Map<String, Object> currentCatalogSummary = TaskQuerySupport.resolveCurrentCatalogSummary(
                taskId,
                attachments,
                taskState,
                taskCatalogSnapshotService
        );
        Map<String, Object> catalogSummary = TaskQuerySupport.resolveManifestCatalogSummary(
                manifest,
                taskId,
                attachments,
                taskState,
                taskCatalogSnapshotService,
                objectMapper
        );
        TaskQuerySupport.applyLifecycleProjection(response, taskState, objectMapper);

        JsonNode goalParseRoot = TaskQuerySupport.readJsonNode(taskState.getGoalParseJson(), objectMapper);
        JsonNode skillRouteRoot = TaskQuerySupport.readJsonNode(taskState.getSkillRouteJson(), objectMapper);
        JsonNode passBRoot = TaskQuerySupport.readJsonNode(taskState.getPassbResultJson(), objectMapper);
        TaskQuerySupport.applySkillBindingProjection(
                response,
                goalParseRoot,
                skillRouteRoot,
                passBRoot,
                objectMapper
        );
        TaskQuerySupport.applyStageProjection(response, goalParseRoot, skillRouteRoot, passBRoot, objectMapper);
        TaskQuerySupport.applyManifestPass2Projection(response, taskState, objectMapper);

        JsonNode pass1Projection = TaskQuerySupport.readJsonNode(taskState.getPass1ResultJson(), objectMapper);
        TaskQuerySupport.ContractProjection contractProjection = TaskQuerySupport.buildFrozenContractProjection(
                pass1Projection,
                TaskQuerySupport.readJsonMap(manifest.getContractSummaryJson(), objectMapper),
                response.getResumeTransaction(),
                "manifest_contract",
                "manifest_contract_governance"
        );
        TaskQuerySupport.applyContractProjection(response, contractProjection);

        TaskQuerySupport.RouteProjection routeProjection = TaskQuerySupport.buildRouteProjection(
                manifest.getGoalParseJson(),
                manifest.getSkillRouteJson(),
                pass1Projection,
                goalRouteService,
                objectMapper
        );
        response.setGoalParse(TaskProjectionBuilder.buildManifestGoalParse(routeProjection.goalParse()));
        response.setSkillRoute(TaskProjectionBuilder.buildManifestSkillRoute(routeProjection.skillRoute()));
        TaskProjectionBuilder.applyPass1Projection(response, pass1Projection, objectMapper);
        response.setLogicalInputRoles(TaskProjectionBuilder.buildManifestLogicalInputRoles(
                TaskQuerySupport.readJsonNode(manifest.getLogicalInputRolesJson(), objectMapper)
        ));
        response.setSlotSchemaView(TaskProjectionBuilder.buildManifestSlotSchemaView(
                TaskQuerySupport.readJsonNode(manifest.getSlotSchemaViewJson(), objectMapper)
        ));
        response.setSlotBindings(TaskProjectionBuilder.buildManifestSlotBindings(
                TaskQuerySupport.readJsonNode(manifest.getSlotBindingsJson(), objectMapper)
        ));

        TaskQuerySupport.CatalogProjection catalogProjection = TaskQuerySupport.buildFrozenCatalogProjection(
                "manifest_catalog",
                "manifest_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                TaskQuerySupport.extractManifestRoleNames(response.getSlotBindings()),
                "manifest_slot_bindings"
        );
        TaskQuerySupport.applyCatalogProjection(response, catalogProjection);
        response.setCatalogSummary(catalogSummary);

        response.setArgsDraft(TaskProjectionBuilder.buildJsonObjectView(
                TaskQuerySupport.readJsonNode(manifest.getArgsDraftJson(), objectMapper),
                objectMapper
        ));
        response.setValidationSummary(TaskProjectionBuilder.buildManifestValidationSummary(
                TaskQuerySupport.readJsonNode(manifest.getValidationSummaryJson(), objectMapper)
        ));
        response.setExecutionGraph(TaskProjectionBuilder.buildManifestExecutionGraph(
                TaskQuerySupport.readJsonNode(manifest.getExecutionGraphJson(), objectMapper)
        ));
        response.setRuntimeAssertions(TaskProjectionBuilder.buildManifestRuntimeAssertions(
                TaskQuerySupport.readJsonNode(manifest.getRuntimeAssertionsJson(), objectMapper)
        ));
        response.setCreatedAt(manifest.getCreatedAt() == null ? null : manifest.getCreatedAt().toString());

        TaskQuerySupport.applyRepairProjection(response, latestRepair, objectMapper);
        TaskQuerySupport.applyFinalExplanationProjection(response, jobRecord, objectMapper);
        return response;
    }
}
