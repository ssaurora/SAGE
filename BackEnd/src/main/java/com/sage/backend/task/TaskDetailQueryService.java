package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.RepairRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.task.dto.TaskDetailResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskDetailQueryService {

    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final GoalRouteService goalRouteService;
    private final ObjectMapper objectMapper;

    public TaskDetailQueryService(
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            GoalRouteService goalRouteService,
            ObjectMapper objectMapper
    ) {
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.goalRouteService = goalRouteService;
        this.objectMapper = objectMapper;
    }

    public TaskDetailResponse buildTaskDetailResponse(
            TaskState taskState,
            List<TaskAttachment> attachments,
            AnalysisManifest activeManifest,
            RepairRecord latestRepair,
            JobRecord jobRecord
    ) {
        String taskId = taskState.getTaskId();
        JsonNode pass1Projection = TaskQuerySupport.readJsonNode(taskState.getPass1ResultJson(), objectMapper);
        TaskQuerySupport.RouteProjection routeProjection = TaskQuerySupport.buildRouteProjection(
                taskState.getGoalParseJson(),
                taskState.getSkillRouteJson(),
                pass1Projection,
                goalRouteService,
                objectMapper
        );
        Map<String, Object> catalogSummary = taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                TaskQuerySupport.currentInventoryVersion(taskState)
        );

        TaskDetailResponse response = new TaskDetailResponse();
        response.setTaskId(taskState.getTaskId());
        response.setState(taskState.getCurrentState());
        response.setStateVersion(taskState.getStateVersion());
        response.setPlanningRevision(taskState.getPlanningRevision());
        response.setCheckpointVersion(taskState.getCheckpointVersion());
        TaskQuerySupport.applyLifecycleProjection(response, taskState, objectMapper);
        response.setGoalParseSummary(TaskProjectionBuilder.buildGoalParseSummary(routeProjection.goalParse()));
        response.setSkillRouteSummary(TaskProjectionBuilder.buildSkillRouteSummary(routeProjection.skillRoute()));
        response.setPass1Summary(TaskProjectionBuilder.buildPass1Summary(pass1Projection));
        response.setSlotBindingsSummary(TaskProjectionBuilder.buildSlotBindingsSummary(
                TaskQuerySupport.readJsonNode(taskState.getSlotBindingsSummaryJson(), objectMapper)
        ));
        response.setArgsDraftSummary(TaskProjectionBuilder.buildArgsDraftSummary(
                TaskQuerySupport.readJsonNode(taskState.getArgsDraftSummaryJson(), objectMapper)
        ));
        response.setValidationSummary(TaskProjectionBuilder.buildValidationSummary(
                TaskQuerySupport.readJsonNode(taskState.getValidationSummaryJson(), objectMapper)
        ));
        response.setInputChainStatus(taskState.getInputChainStatus());
        TaskQuerySupport.applyDetailOutcomeProjection(response, taskState, objectMapper);
        response.setWaitingContext(TaskProjectionBuilder.buildWaitingContext(
                TaskQuerySupport.readJsonNode(taskState.getWaitingContextJson(), objectMapper)
        ));
        TaskQuerySupport.CatalogProjection catalogProjection = TaskQuerySupport.buildDetailCatalogProjection(
                response.getWaitingContext(),
                catalogSummary
        );
        TaskQuerySupport.applyCatalogProjection(response, catalogProjection);
        TaskQuerySupport.ContractProjection contractProjection = TaskQuerySupport.buildDetailContractProjection(
                pass1Projection,
                TaskQuerySupport.readJsonMap(activeManifest == null ? null : activeManifest.getContractSummaryJson(), objectMapper),
                response.getResumeTransaction(),
                "task_contract_governance"
        );
        TaskQuerySupport.applyContractProjection(response, contractProjection);
        response.setLatestResultBundleId(taskState.getLatestResultBundleId());
        response.setLatestWorkspaceId(taskState.getLatestWorkspaceId());
        JsonNode goalParseRoot = TaskQuerySupport.readJsonNode(taskState.getGoalParseJson(), objectMapper);
        JsonNode passBRoot = TaskQuerySupport.readJsonNode(taskState.getPassbResultJson(), objectMapper);
        TaskQuerySupport.applySkillBindingProjection(
                response,
                goalParseRoot,
                routeProjection.skillRoute(),
                passBRoot,
                objectMapper
        );
        TaskQuerySupport.applyStageProjection(
                response,
                routeProjection.goalParse(),
                routeProjection.skillRoute(),
                passBRoot,
                objectMapper
        );

        TaskQuerySupport.applyRepairProjection(response, latestRepair, objectMapper);

        TaskQuerySupport.applyJobProjection(response, jobRecord, activeManifest, objectMapper);
        return response;
    }
}
