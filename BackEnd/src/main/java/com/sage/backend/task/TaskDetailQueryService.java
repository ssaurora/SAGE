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
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        response.setResumeTransaction(TaskProjectionBuilder.buildResumeTransaction(
                TaskQuerySupport.readJsonNode(taskState.getResumeTxnJson(), objectMapper)
        ));
        response.setCorruptionState(TaskQuerySupport.buildCorruptionState(taskState));
        response.setPromotionStatus(TaskQuerySupport.derivePromotionStatus(
                taskState.getCurrentState(),
                taskState.getCorruptionReason()
        ));
        response.setSkillId(routeProjection.skillRoute().path("skill_id").asText(null));
        response.setSkillVersion(routeProjection.skillRoute().path("skill_version").asText(null));
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
        response.setPass2Summary(TaskProjectionBuilder.buildPass2Summary(
                TaskQuerySupport.readJsonNode(taskState.getPass2ResultJson(), objectMapper)
        ));
        response.setResultObjectSummary(TaskProjectionBuilder.buildResultObjectSummary(
                TaskQuerySupport.readJsonNode(taskState.getResultObjectSummaryJson(), objectMapper)
        ));
        response.setResultBundleSummary(TaskProjectionBuilder.buildResultBundleSummaryView(
                TaskQuerySupport.readJsonNode(taskState.getResultBundleSummaryJson(), objectMapper)
        ));
        response.setFinalExplanationSummary(TaskProjectionBuilder.buildFinalExplanationSummary(
                TaskQuerySupport.readJsonNode(taskState.getFinalExplanationSummaryJson(), objectMapper)
        ));
        response.setLastFailureSummary(TaskProjectionBuilder.buildFailureSummary(
                TaskQuerySupport.readJsonNode(taskState.getLastFailureSummaryJson(), objectMapper)
        ));
        response.setCatalogSummary(catalogSummary);
        response.setWaitingContext(TaskProjectionBuilder.buildWaitingContext(
                TaskQuerySupport.readJsonNode(taskState.getWaitingContextJson(), objectMapper)
        ));
        Map<String, Object> detailCatalogConsistency = CatalogConsistencyProjector.buildDetailCatalogConsistency(
                response.getWaitingContext(),
                catalogSummary
        );
        response.setCatalogConsistency(detailCatalogConsistency);
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "task_catalog_governance",
                response.getWaitingContext() == null ? null : response.getWaitingContext().getCatalogSummary(),
                catalogSummary,
                detailCatalogConsistency
        ));
        Map<String, Object> detailFrozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                TaskQuerySupport.readJsonMap(activeManifest == null ? null : activeManifest.getContractSummaryJson(), objectMapper),
                pass1Projection
        );
        Map<String, Object> detailCurrentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> detailContractConsistency = ContractConsistencyProjector.buildDetailContractConsistency(
                detailFrozenSummary,
                detailCurrentSummary,
                response.getResumeTransaction()
        );
        response.setContractConsistency(detailContractConsistency);
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "task_contract_governance",
                detailFrozenSummary,
                detailCurrentSummary,
                detailContractConsistency,
                response.getResumeTransaction()
        ));
        response.setLatestResultBundleId(taskState.getLatestResultBundleId());
        response.setLatestWorkspaceId(taskState.getLatestWorkspaceId());
        JsonNode pass2Root = TaskQuerySupport.readJsonNode(taskState.getPass2ResultJson(), objectMapper);
        response.setGraphDigest(pass2Root == null ? null : pass2Root.path("graph_digest").asText(null));
        response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("planning_summary"),
                objectMapper
        ));
        JsonNode goalParseRoot = TaskQuerySupport.readJsonNode(taskState.getGoalParseJson(), objectMapper);
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));
        JsonNode passBRoot = TaskQuerySupport.readJsonNode(taskState.getPassbResultJson(), objectMapper);
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(routeProjection.goalParse(), objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(
                routeProjection.goalParse(),
                routeProjection.skillRoute(),
                objectMapper
        ));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));

        if (latestRepair != null) {
            JsonNode repairProposalNode = TaskQuerySupport.readJsonNode(latestRepair.getRepairProposalJson(), objectMapper);
            response.setRepairProposal(TaskProjectionBuilder.buildRepairProposal(repairProposalNode));
            response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
            response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
        }

        if (jobRecord != null) {
            TaskDetailResponse.JobSummary jobSummary = new TaskDetailResponse.JobSummary();
            jobSummary.setJobId(jobRecord.getJobId());
            jobSummary.setJobState(jobRecord.getJobState());
            jobSummary.setLastHeartbeatAt(jobRecord.getLastHeartbeatAt() == null ? null : jobRecord.getLastHeartbeatAt().toString());
            jobSummary.setProviderKey(jobRecord.getProviderKey());
            jobSummary.setCapabilityKey(jobRecord.getCapabilityKey());
            jobSummary.setRuntimeProfile(jobRecord.getRuntimeProfile());
            jobSummary.setCaseId(TaskQuerySupport.extractCaseId(activeManifest, objectMapper));
            response.setJob(jobSummary);
            JsonNode finalExplanationNode = TaskQuerySupport.readJsonNode(jobRecord.getFinalExplanationJson(), objectMapper);
            response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
            response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
        }
        return response;
    }
}
