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
        response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(
                TaskQuerySupport.readJsonNode(manifest.getPlanningSummaryJson(), objectMapper),
                objectMapper
        ));
        if (response.getPlanningSummary() == null) {
            response.setPlanningSummary(Map.of());
        }

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
        response.setCognitionVerdict(taskState.getCognitionVerdict());

        JsonNode goalParseRoot = TaskQuerySupport.readJsonNode(taskState.getGoalParseJson(), objectMapper);
        JsonNode skillRouteRoot = TaskQuerySupport.readJsonNode(taskState.getSkillRouteJson(), objectMapper);
        response.setSkillId(skillRouteRoot == null ? null : skillRouteRoot.path("skill_id").asText(null));
        response.setSkillVersion(skillRouteRoot == null ? null : skillRouteRoot.path("skill_version").asText(null));
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));

        JsonNode passBRoot = TaskQuerySupport.readJsonNode(taskState.getPassbResultJson(), objectMapper);
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot == null ? null : passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
        response.setResumeTransaction(TaskProjectionBuilder.buildResumeTransaction(
                TaskQuerySupport.readJsonNode(taskState.getResumeTxnJson(), objectMapper)
        ));
        response.setCorruptionState(TaskQuerySupport.buildCorruptionState(taskState));
        response.setPromotionStatus(TaskQuerySupport.derivePromotionStatus(
                taskState.getCurrentState(),
                taskState.getCorruptionReason()
        ));

        JsonNode pass2Root = TaskQuerySupport.readJsonNode(taskState.getPass2ResultJson(), objectMapper);
        response.setCanonicalizationSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("canonicalization_summary"),
                objectMapper
        ));
        response.setRewriteSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("rewrite_summary"),
                objectMapper
        ));

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

        if (latestRepair != null) {
            JsonNode repairProposalNode = TaskQuerySupport.readJsonNode(latestRepair.getRepairProposalJson(), objectMapper);
            response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
            response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
        }
        if (jobRecord != null) {
            JsonNode finalExplanationNode = TaskQuerySupport.readJsonNode(jobRecord.getFinalExplanationJson(), objectMapper);
            response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
            response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
        }
        return response;
    }
}
