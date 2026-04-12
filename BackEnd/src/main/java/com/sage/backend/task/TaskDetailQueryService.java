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
        TaskQuerySupport.applyDetailSummaryProjection(response, pass1Projection, routeProjection, taskState, objectMapper);
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
        TaskQuerySupport.StageRoots stageRoots = TaskQuerySupport.readStageRoots(routeProjection, taskState, objectMapper);
        TaskQuerySupport.applySkillBindingProjection(response, stageRoots, objectMapper);
        TaskQuerySupport.applyStageProjection(response, stageRoots, objectMapper);

        TaskQuerySupport.applyRepairProjection(response, latestRepair, objectMapper);

        TaskQuerySupport.applyJobProjection(response, jobRecord, activeManifest, objectMapper);
        return response;
    }
}
