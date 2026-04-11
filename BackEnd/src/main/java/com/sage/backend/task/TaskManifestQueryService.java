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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        Map<String, Object> currentCatalogSummary = taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                TaskQuerySupport.currentInventoryVersion(taskState)
        );
        Map<String, Object> catalogSummary = taskCatalogSnapshotService.resolveManifestCatalogSummary(
                frozenCatalogSummary,
                taskId,
                attachments,
                TaskQuerySupport.currentInventoryVersion(taskState)
        );
        response.setCatalogSummary(catalogSummary);
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
        Map<String, Object> manifestFrozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                TaskQuerySupport.readJsonMap(manifest.getContractSummaryJson(), objectMapper),
                pass1Projection
        );
        Map<String, Object> manifestCurrentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> manifestContractConsistency = ContractConsistencyProjector.buildFrozenContractConsistency(
                "manifest_contract",
                manifestFrozenSummary,
                manifestCurrentSummary
        );
        response.setContractConsistency(manifestContractConsistency);
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "manifest_contract_governance",
                manifestFrozenSummary,
                manifestCurrentSummary,
                manifestContractConsistency,
                response.getResumeTransaction()
        ));

        RouteProjection routeProjection = buildRouteProjection(
                manifest.getGoalParseJson(),
                manifest.getSkillRouteJson(),
                pass1Projection
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

        Map<String, Object> manifestCatalogConsistency = CatalogConsistencyProjector.mergeCoverageConsistency(
                CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                        "manifest_catalog",
                        frozenCatalogSummary,
                        currentCatalogSummary
                ),
                extractManifestRoleNames(response.getSlotBindings()),
                currentCatalogSummary,
                "manifest_slot_bindings"
        );
        response.setCatalogConsistency(manifestCatalogConsistency);
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "manifest_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                manifestCatalogConsistency
        ));

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

    private RouteProjection buildRouteProjection(String goalParseJson, String skillRouteJson, JsonNode pass1Projection) {
        return new RouteProjection(
                goalRouteService.enrichGoalParse(TaskQuerySupport.readJsonNode(goalParseJson, objectMapper), pass1Projection),
                goalRouteService.enrichSkillRoute(TaskQuerySupport.readJsonNode(skillRouteJson, objectMapper), pass1Projection)
        );
    }

    private List<String> extractManifestRoleNames(List<TaskManifestResponse.SlotBinding> slotBindings) {
        if (slotBindings == null || slotBindings.isEmpty()) {
            return List.of();
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (TaskManifestResponse.SlotBinding binding : slotBindings) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        return new ArrayList<>(roleNames);
    }

    private record RouteProjection(
            JsonNode goalParse,
            JsonNode skillRoute
    ) {
    }
}
