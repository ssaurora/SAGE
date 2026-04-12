package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.RepairRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.task.dto.CatalogGovernanceView;
import com.sage.backend.task.dto.CorruptionStateView;
import com.sage.backend.task.dto.ContractGovernanceView;
import com.sage.backend.task.dto.ResumeTransactionView;
import com.sage.backend.task.dto.TaskContractResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TaskQuerySupport {

    private TaskQuerySupport() {
    }

    static JsonNode readJsonNode(String sourceJson, ObjectMapper objectMapper) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(sourceJson);
        } catch (Exception exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> readJsonMap(String sourceJson, ObjectMapper objectMapper) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(sourceJson, Map.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception exception) {
            return null;
        }
    }

    static CorruptionStateView buildCorruptionState(TaskState taskState) {
        CorruptionStateView view = new CorruptionStateView();
        view.setCorrupted(TaskStatus.STATE_CORRUPTED.name().equals(taskState.getCurrentState()));
        view.setReason(taskState.getCorruptionReason());
        view.setCorruptedSince(taskState.getCorruptedSince() == null ? null : taskState.getCorruptedSince().toString());
        return view;
    }

    static String derivePromotionStatus(String taskState, String corruptionReason) {
        if (TaskStatus.ARTIFACT_PROMOTING.name().equals(taskState)) {
            return "PROMOTING";
        }
        if (TaskStatus.SUCCEEDED.name().equals(taskState)) {
            return "PROMOTED";
        }
        if (TaskStatus.STATE_CORRUPTED.name().equals(taskState)
                && corruptionReason != null
                && corruptionReason.startsWith("ARTIFACT_PROMOTION_FAILED")) {
            return "FAILED";
        }
        return "NOT_PROMOTED";
    }

    static ResumeTransactionView buildResumeTransaction(TaskState taskState, ObjectMapper objectMapper) {
        return TaskProjectionBuilder.buildResumeTransaction(
                readJsonNode(taskState == null ? null : taskState.getResumeTxnJson(), objectMapper)
        );
    }

    static void applyLifecycleProjection(TaskDetailResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        response.setResumeTransaction(buildResumeTransaction(taskState, objectMapper));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(
                taskState.getCurrentState(),
                taskState.getCorruptionReason()
        ));
    }

    static void applyLifecycleProjection(TaskManifestResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        response.setResumeTransaction(buildResumeTransaction(taskState, objectMapper));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(
                taskState.getCurrentState(),
                taskState.getCorruptionReason()
        ));
    }

    static void applyLifecycleProjection(TaskResultResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        response.setResumeTransaction(buildResumeTransaction(taskState, objectMapper));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(
                taskState.getCurrentState(),
                taskState.getCorruptionReason()
        ));
        response.setCognitionVerdict(taskState.getCognitionVerdict());
    }

    static String extractCaseId(AnalysisManifest manifest, ObjectMapper objectMapper) {
        if (manifest == null) {
            return null;
        }
        JsonNode argsDraft = readJsonNode(manifest.getArgsDraftJson(), objectMapper);
        if (argsDraft == null || argsDraft.isNull() || argsDraft.isMissingNode()) {
            return null;
        }
        String caseId = argsDraft.path("case_id").asText(null);
        return caseId == null || caseId.isBlank() ? null : caseId;
    }

    static int currentInventoryVersion(TaskState taskState) {
        if (taskState == null || taskState.getInventoryVersion() == null) {
            return 0;
        }
        return taskState.getInventoryVersion();
    }

    static Map<String, Object> resolveCurrentCatalogSummary(
            String taskId,
            List<TaskAttachment> attachments,
            TaskState taskState,
            TaskCatalogSnapshotService taskCatalogSnapshotService
    ) {
        return taskCatalogSnapshotService.resolveCatalogSummary(
                taskId,
                attachments,
                currentInventoryVersion(taskState)
        );
    }

    static Map<String, Object> resolveManifestCatalogSummary(
            AnalysisManifest manifest,
            String taskId,
            List<TaskAttachment> attachments,
            TaskState taskState,
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            ObjectMapper objectMapper
    ) {
        Map<String, Object> frozenSummary = readJsonMap(manifest == null ? null : manifest.getCatalogSummaryJson(), objectMapper);
        return taskCatalogSnapshotService.resolveManifestCatalogSummary(
                frozenSummary,
                taskId,
                attachments,
                currentInventoryVersion(taskState)
        );
    }

    static RouteProjection buildRouteProjection(
            String goalParseJson,
            String skillRouteJson,
            JsonNode pass1Projection,
            GoalRouteService goalRouteService,
            ObjectMapper objectMapper
    ) {
        return new RouteProjection(
                goalRouteService.enrichGoalParse(readJsonNode(goalParseJson, objectMapper), pass1Projection),
                goalRouteService.enrichSkillRoute(readJsonNode(skillRouteJson, objectMapper), pass1Projection)
        );
    }

    static StageRoots readStageRoots(TaskState taskState, ObjectMapper objectMapper) {
        if (taskState == null) {
            return new StageRoots(null, null, null);
        }
        JsonNode goalParseRoot = readJsonNode(taskState.getGoalParseJson(), objectMapper);
        JsonNode skillRouteRoot = readJsonNode(taskState.getSkillRouteJson(), objectMapper);
        JsonNode passBRoot = readJsonNode(taskState.getPassbResultJson(), objectMapper);
        return new StageRoots(goalParseRoot, skillRouteRoot, passBRoot);
    }

    static StageRoots readStageRoots(RouteProjection routeProjection, TaskState taskState, ObjectMapper objectMapper) {
        JsonNode passBRoot = readJsonNode(taskState == null ? null : taskState.getPassbResultJson(), objectMapper);
        return new StageRoots(
                routeProjection == null ? null : routeProjection.goalParse(),
                routeProjection == null ? null : routeProjection.skillRoute(),
                passBRoot
        );
    }

    static void applyStageProjection(
            TaskDetailResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
    }

    static void applyStageProjection(
            TaskDetailResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applyStageProjection(response, null, null, null, objectMapper);
            return;
        }
        applyStageProjection(response, stageRoots.goalParse(), stageRoots.skillRoute(), stageRoots.passB(), objectMapper);
    }

    static void applyStageProjection(
            TaskManifestResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
    }

    static void applyStageProjection(
            TaskManifestResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applyStageProjection(response, null, null, null, objectMapper);
            return;
        }
        applyStageProjection(response, stageRoots.goalParse(), stageRoots.skillRoute(), stageRoots.passB(), objectMapper);
    }

    static void applyStageProjection(
            TaskResultResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
    }

    static void applyStageProjection(
            TaskResultResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applyStageProjection(response, null, null, null, objectMapper);
            return;
        }
        applyStageProjection(response, stageRoots.goalParse(), stageRoots.skillRoute(), stageRoots.passB(), objectMapper);
    }

    static Map<String, Object> resolvePlanningSummary(
            String primaryJson,
            String fallbackJson,
            ObjectMapper objectMapper,
            boolean emptyObjectWhenMissing
    ) {
        Map<String, Object> summary = TaskProjectionBuilder.buildJsonObjectView(
                readJsonNode(primaryJson, objectMapper),
                objectMapper
        );
        if (summary == null && fallbackJson != null) {
            summary = TaskProjectionBuilder.buildJsonObjectView(
                    readJsonNode(fallbackJson, objectMapper),
                    objectMapper
            );
        }
        if (summary == null && emptyObjectWhenMissing) {
            return Collections.emptyMap();
        }
        return summary;
    }

    static void applyRepairProjection(TaskDetailResponse response, RepairRecord latestRepair, ObjectMapper objectMapper) {
        if (response == null || latestRepair == null) {
            return;
        }
        JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson(), objectMapper);
        response.setRepairProposal(TaskProjectionBuilder.buildRepairProposal(repairProposalNode));
        response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
        response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
    }

    static void applyRepairProjection(TaskManifestResponse response, RepairRecord latestRepair, ObjectMapper objectMapper) {
        if (response == null || latestRepair == null) {
            return;
        }
        JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson(), objectMapper);
        response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
        response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
    }

    static void applyRepairProjection(TaskResultResponse response, RepairRecord latestRepair, ObjectMapper objectMapper) {
        if (response == null || latestRepair == null) {
            return;
        }
        JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson(), objectMapper);
        response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
        response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
    }

    static void applyJobProjection(
            TaskDetailResponse response,
            JobRecord jobRecord,
            AnalysisManifest activeManifest,
            ObjectMapper objectMapper
    ) {
        if (response == null || jobRecord == null) {
            return;
        }
        TaskDetailResponse.JobSummary jobSummary = new TaskDetailResponse.JobSummary();
        jobSummary.setJobId(jobRecord.getJobId());
        jobSummary.setJobState(jobRecord.getJobState());
        jobSummary.setLastHeartbeatAt(jobRecord.getLastHeartbeatAt() == null ? null : jobRecord.getLastHeartbeatAt().toString());
        jobSummary.setProviderKey(jobRecord.getProviderKey());
        jobSummary.setCapabilityKey(jobRecord.getCapabilityKey());
        jobSummary.setRuntimeProfile(jobRecord.getRuntimeProfile());
        jobSummary.setCaseId(extractCaseId(activeManifest, objectMapper));
        response.setJob(jobSummary);
        applyFinalExplanationProjection(response, jobRecord, objectMapper);
    }

    static void applyFinalExplanationProjection(TaskDetailResponse response, JobRecord jobRecord, ObjectMapper objectMapper) {
        if (response == null || jobRecord == null) {
            return;
        }
        JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson(), objectMapper);
        response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
        response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
    }

    static void applyFinalExplanationProjection(TaskManifestResponse response, JobRecord jobRecord, ObjectMapper objectMapper) {
        if (response == null || jobRecord == null) {
            return;
        }
        JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson(), objectMapper);
        response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
        response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
    }

    static void applyFinalExplanationProjection(TaskResultResponse response, JobRecord jobRecord, ObjectMapper objectMapper) {
        if (response == null || jobRecord == null) {
            return;
        }
        JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson(), objectMapper);
        response.setFinalExplanation(TaskProjectionBuilder.buildTaskFinalExplanation(finalExplanationNode));
        response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
        response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
    }

    static void applyDetailOutcomeProjection(TaskDetailResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson(), objectMapper);
        response.setPass2Summary(TaskProjectionBuilder.buildPass2Summary(pass2Root));
        response.setResultObjectSummary(TaskProjectionBuilder.buildResultObjectSummary(
                readJsonNode(taskState.getResultObjectSummaryJson(), objectMapper)
        ));
        response.setResultBundleSummary(TaskProjectionBuilder.buildResultBundleSummaryView(
                readJsonNode(taskState.getResultBundleSummaryJson(), objectMapper)
        ));
        response.setFinalExplanationSummary(TaskProjectionBuilder.buildFinalExplanationSummary(
                readJsonNode(taskState.getFinalExplanationSummaryJson(), objectMapper)
        ));
        response.setLastFailureSummary(TaskProjectionBuilder.buildFailureSummary(
                readJsonNode(taskState.getLastFailureSummaryJson(), objectMapper)
        ));
        response.setGraphDigest(pass2Root == null ? null : pass2Root.path("graph_digest").asText(null));
        response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("planning_summary"),
                objectMapper
        ));
    }

    static void applyManifestPayload(
            TaskManifestResponse response,
            AnalysisManifest manifest,
            JsonNode pass1Projection,
            GoalRouteService goalRouteService,
            ObjectMapper objectMapper
    ) {
        if (response == null || manifest == null) {
            return;
        }
        RouteProjection routeProjection = buildRouteProjection(
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
                readJsonNode(manifest.getLogicalInputRolesJson(), objectMapper)
        ));
        response.setSlotSchemaView(TaskProjectionBuilder.buildManifestSlotSchemaView(
                readJsonNode(manifest.getSlotSchemaViewJson(), objectMapper)
        ));
        response.setSlotBindings(TaskProjectionBuilder.buildManifestSlotBindings(
                readJsonNode(manifest.getSlotBindingsJson(), objectMapper)
        ));
        response.setArgsDraft(TaskProjectionBuilder.buildJsonObjectView(
                readJsonNode(manifest.getArgsDraftJson(), objectMapper),
                objectMapper
        ));
        response.setValidationSummary(TaskProjectionBuilder.buildManifestValidationSummary(
                readJsonNode(manifest.getValidationSummaryJson(), objectMapper)
        ));
        response.setExecutionGraph(TaskProjectionBuilder.buildManifestExecutionGraph(
                readJsonNode(manifest.getExecutionGraphJson(), objectMapper)
        ));
        response.setRuntimeAssertions(TaskProjectionBuilder.buildManifestRuntimeAssertions(
                readJsonNode(manifest.getRuntimeAssertionsJson(), objectMapper)
        ));
        response.setCreatedAt(manifest.getCreatedAt() == null ? null : manifest.getCreatedAt().toString());
    }

    static void applyManifestPass2Projection(TaskManifestResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson(), objectMapper);
        response.setCanonicalizationSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("canonicalization_summary"),
                objectMapper
        ));
        response.setRewriteSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("rewrite_summary"),
                objectMapper
        ));
    }

    static void applyResultPass2Projection(TaskResultResponse response, TaskState taskState, ObjectMapper objectMapper) {
        if (response == null || taskState == null) {
            return;
        }
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson(), objectMapper);
        response.setCanonicalizationSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("canonicalization_summary"),
                objectMapper
        ));
        response.setRewriteSummary(TaskProjectionBuilder.buildJsonObjectView(
                pass2Root == null ? null : pass2Root.path("rewrite_summary"),
                objectMapper
        ));
        response.setFailureSummary(TaskProjectionBuilder.buildTaskResultFailureSummary(
                readJsonNode(taskState.getLastFailureSummaryJson(), objectMapper)
        ));
    }

    static void applyResultJobProjection(
            TaskResultResponse response,
            JobRecord jobRecord,
            String activeCaseId,
            ObjectMapper objectMapper
    ) {
        if (response == null || jobRecord == null) {
            return;
        }
        response.setJobId(jobRecord.getJobId());
        response.setJobState(jobRecord.getJobState());
        response.setProviderKey(jobRecord.getProviderKey());
        response.setRuntimeProfile(jobRecord.getRuntimeProfile());
        response.setCaseId(activeCaseId);
        response.setResultBundle(TaskProjectionBuilder.buildTaskResultBundle(
                readJsonNode(jobRecord.getResultBundleJson(), objectMapper)
        ));
        applyFinalExplanationProjection(response, jobRecord, objectMapper);
        TaskResultResponse.FailureSummary jobFailureSummary = TaskProjectionBuilder.buildTaskResultFailureSummary(
                readJsonNode(jobRecord.getFailureSummaryJson(), objectMapper)
        );
        if (jobFailureSummary != null) {
            response.setFailureSummary(jobFailureSummary);
        }
        TaskResultResponse.DockerRuntimeEvidence dockerRuntimeEvidence = TaskProjectionBuilder.buildDockerRuntimeEvidence(
                readJsonNode(jobRecord.getDockerRuntimeEvidenceJson(), objectMapper)
        );
        response.setDockerRuntimeEvidence(dockerRuntimeEvidence);
        if (response.getCaseId() == null && dockerRuntimeEvidence != null) {
            response.setCaseId(dockerRuntimeEvidence.getCaseId());
        }
        response.setWorkspaceSummary(TaskProjectionBuilder.buildWorkspaceSummary(
                readJsonNode(jobRecord.getWorkspaceSummaryJson(), objectMapper)
        ));
        response.setArtifactCatalog(TaskProjectionBuilder.buildArtifactCatalog(
                readJsonNode(jobRecord.getArtifactCatalogJson(), objectMapper)
        ));
        response.setPlanningSummary(resolvePlanningSummary(
                jobRecord.getPlanningPass2SummaryJson(),
                null,
                objectMapper,
                false
        ));
    }

    static void applyResultJobPayload(
            TaskResultResponse response,
            JobRecord jobRecord,
            String activeCaseId,
            Map<String, Object> frozenCatalogSummary,
            Map<String, Object> currentCatalogSummary,
            Map<String, Object> catalogSummary,
            ObjectMapper objectMapper
    ) {
        if (response == null || jobRecord == null) {
            return;
        }
        applyResultJobProjection(response, jobRecord, activeCaseId, objectMapper);
        CatalogProjection catalogProjection = buildFrozenCatalogProjection(
                "result_catalog",
                "result_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                extractResultInputRoleNames(response),
                "result_input_bindings"
        );
        applyCatalogProjection(response, catalogProjection);
        response.setCatalogSummary(catalogSummary);
    }

    static void applyDetailSummaryProjection(
            TaskDetailResponse response,
            JsonNode pass1Projection,
            RouteProjection routeProjection,
            TaskState taskState,
            ObjectMapper objectMapper
    ) {
        if (response == null || taskState == null) {
            return;
        }
        response.setGoalParseSummary(TaskProjectionBuilder.buildGoalParseSummary(
                routeProjection == null ? null : routeProjection.goalParse()
        ));
        response.setSkillRouteSummary(TaskProjectionBuilder.buildSkillRouteSummary(
                routeProjection == null ? null : routeProjection.skillRoute()
        ));
        response.setPass1Summary(TaskProjectionBuilder.buildPass1Summary(pass1Projection));
        response.setSlotBindingsSummary(TaskProjectionBuilder.buildSlotBindingsSummary(
                readJsonNode(taskState.getSlotBindingsSummaryJson(), objectMapper)
        ));
        response.setArgsDraftSummary(TaskProjectionBuilder.buildArgsDraftSummary(
                readJsonNode(taskState.getArgsDraftSummaryJson(), objectMapper)
        ));
        response.setValidationSummary(TaskProjectionBuilder.buildValidationSummary(
                readJsonNode(taskState.getValidationSummaryJson(), objectMapper)
        ));
        response.setInputChainStatus(taskState.getInputChainStatus());
    }

    static void applySkillBindingProjection(
            TaskDetailResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));
        response.setSkillId(skillRouteRoot == null ? null : skillRouteRoot.path("skill_id").asText(null));
        response.setSkillVersion(skillRouteRoot == null ? null : skillRouteRoot.path("skill_version").asText(null));
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("overruled_fields")
        ));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("blocked_mutations")
        ));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean()
                        ? passBRoot.path("assembly_blocked").asBoolean()
                        : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
    }

    static void applySkillBindingProjection(
            TaskDetailResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applySkillBindingProjection(response, null, null, null, objectMapper);
            return;
        }
        applySkillBindingProjection(
                response,
                stageRoots.goalParse(),
                stageRoots.skillRoute(),
                stageRoots.passB(),
                objectMapper
        );
    }

    static void applySkillBindingProjection(
            TaskManifestResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));
        response.setSkillId(skillRouteRoot == null ? null : skillRouteRoot.path("skill_id").asText(
                passBRoot == null ? null : passBRoot.path("skill_id").asText(null)
        ));
        response.setSkillVersion(skillRouteRoot == null ? null : skillRouteRoot.path("skill_version").asText(
                passBRoot == null ? null : passBRoot.path("skill_version").asText(null)
        ));
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("overruled_fields")
        ));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("blocked_mutations")
        ));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean()
                        ? passBRoot.path("assembly_blocked").asBoolean()
                        : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
    }

    static void applySkillBindingProjection(
            TaskManifestResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applySkillBindingProjection(response, null, null, null, objectMapper);
            return;
        }
        applySkillBindingProjection(
                response,
                stageRoots.goalParse(),
                stageRoots.skillRoute(),
                stageRoots.passB(),
                objectMapper
        );
    }

    static void applySkillBindingProjection(
            TaskResultResponse response,
            JsonNode goalParseRoot,
            JsonNode skillRouteRoot,
            JsonNode passBRoot,
            ObjectMapper objectMapper
    ) {
        if (response == null) {
            return;
        }
        response.setPlanningIntentStatus(goalParseRoot == null ? null : goalParseRoot.path("planning_intent_status").asText(null));
        response.setSkillId(skillRouteRoot == null ? null : skillRouteRoot.path("skill_id").asText(
                passBRoot == null ? null : passBRoot.path("skill_id").asText(null)
        ));
        response.setSkillVersion(skillRouteRoot == null ? null : skillRouteRoot.path("skill_version").asText(
                passBRoot == null ? null : passBRoot.path("skill_version").asText(null)
        ));
        response.setBindingStatus(passBRoot == null ? null : passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("overruled_fields")
        ));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(
                passBRoot == null ? null : passBRoot.path("blocked_mutations")
        ));
        response.setAssemblyBlocked(
                passBRoot != null && passBRoot.path("assembly_blocked").isBoolean()
                        ? passBRoot.path("assembly_blocked").asBoolean()
                        : null
        );
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
    }

    static void applySkillBindingProjection(
            TaskResultResponse response,
            StageRoots stageRoots,
            ObjectMapper objectMapper
    ) {
        if (stageRoots == null) {
            applySkillBindingProjection(response, null, null, null, objectMapper);
            return;
        }
        applySkillBindingProjection(
                response,
                stageRoots.goalParse(),
                stageRoots.skillRoute(),
                stageRoots.passB(),
                objectMapper
        );
    }

    static CatalogProjection buildDetailCatalogProjection(
            TaskDetailResponse.WaitingContext waitingContext,
            Map<String, Object> currentCatalogSummary
    ) {
        Map<String, Object> consistency = CatalogConsistencyProjector.buildDetailCatalogConsistency(
                waitingContext,
                currentCatalogSummary
        );
        CatalogGovernanceView governance = CatalogGovernanceAssembler.build(
                "task_catalog_governance",
                waitingContext == null ? null : waitingContext.getCatalogSummary(),
                currentCatalogSummary,
                consistency
        );
        return new CatalogProjection(currentCatalogSummary, consistency, governance);
    }

    static void applyCatalogProjection(TaskDetailResponse response, CatalogProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setCatalogSummary(projection.summary());
        response.setCatalogConsistency(projection.consistency());
        response.setCatalogGovernance(projection.governance());
    }

    static void applyCatalogProjection(TaskManifestResponse response, CatalogProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setCatalogSummary(projection.summary());
        response.setCatalogConsistency(projection.consistency());
        response.setCatalogGovernance(projection.governance());
    }

    static void applyCatalogProjection(TaskResultResponse response, CatalogProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setCatalogSummary(projection.summary());
        response.setCatalogConsistency(projection.consistency());
        response.setCatalogGovernance(projection.governance());
    }

    static CatalogProjection buildFrozenCatalogProjection(
            String consistencyScope,
            String governanceScope,
            Map<String, Object> frozenCatalogSummary,
            Map<String, Object> currentCatalogSummary,
            List<String> expectedRoleNames,
            String coverageSource
    ) {
        Map<String, Object> consistency = CatalogConsistencyProjector.mergeCoverageConsistency(
                CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                        consistencyScope,
                        frozenCatalogSummary,
                        currentCatalogSummary
                ),
                expectedRoleNames,
                currentCatalogSummary,
                coverageSource
        );
        CatalogGovernanceView governance = CatalogGovernanceAssembler.build(
                governanceScope,
                frozenCatalogSummary,
                currentCatalogSummary,
                consistency
        );
        return new CatalogProjection(
                frozenCatalogSummary != null && !frozenCatalogSummary.isEmpty() ? frozenCatalogSummary : currentCatalogSummary,
                consistency,
                governance
        );
    }

    static ContractProjection buildDetailContractProjection(
            JsonNode pass1Projection,
            Map<String, Object> manifestContractSummary,
            ResumeTransactionView resumeTransaction,
            String governanceScope
    ) {
        Map<String, Object> frozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                manifestContractSummary,
                pass1Projection
        );
        Map<String, Object> currentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> consistency = ContractConsistencyProjector.buildDetailContractConsistency(
                frozenSummary,
                currentSummary,
                resumeTransaction
        );
        ContractGovernanceView governance = ContractGovernanceAssembler.build(
                governanceScope,
                frozenSummary,
                currentSummary,
                consistency,
                resumeTransaction
        );
        return new ContractProjection(frozenSummary, currentSummary, consistency, governance);
    }

    static void applyContractProjection(TaskDetailResponse response, ContractProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setContractConsistency(projection.consistency());
        response.setContractGovernance(projection.governance());
    }

    static void applyContractProjection(TaskManifestResponse response, ContractProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setContractConsistency(projection.consistency());
        response.setContractGovernance(projection.governance());
    }

    static void applyContractProjection(TaskResultResponse response, ContractProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setContractConsistency(projection.consistency());
        response.setContractGovernance(projection.governance());
    }

    static void applyContractProjection(TaskContractResponse response, ContractProjection projection) {
        if (response == null || projection == null) {
            return;
        }
        response.setFrozenContractSummary(projection.frozenSummary());
        response.setCurrentContractSummary(projection.currentSummary());
        response.setContractGovernance(projection.governance());
    }

    static ContractProjection buildFrozenContractProjection(
            JsonNode pass1Projection,
            Map<String, Object> manifestContractSummary,
            ResumeTransactionView resumeTransaction,
            String consistencyScope,
            String governanceScope
    ) {
        Map<String, Object> frozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                manifestContractSummary,
                pass1Projection
        );
        Map<String, Object> currentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> consistency = ContractConsistencyProjector.buildFrozenContractConsistency(
                consistencyScope,
                frozenSummary,
                currentSummary
        );
        ContractGovernanceView governance = ContractGovernanceAssembler.build(
                governanceScope,
                frozenSummary,
                currentSummary,
                consistency,
                resumeTransaction
        );
        return new ContractProjection(frozenSummary, currentSummary, consistency, governance);
    }

    static List<String> extractManifestRoleNames(List<TaskManifestResponse.SlotBinding> slotBindings) {
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

    static List<String> extractResultInputRoleNames(TaskResultResponse response) {
        if (response == null || response.getDockerRuntimeEvidence() == null || response.getDockerRuntimeEvidence().getInputBindings() == null) {
            return List.of();
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (TaskResultResponse.InputBinding binding : response.getDockerRuntimeEvidence().getInputBindings()) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        return new ArrayList<>(roleNames);
    }

    record RouteProjection(
            JsonNode goalParse,
            JsonNode skillRoute
    ) {
    }

    record StageRoots(
            JsonNode goalParse,
            JsonNode skillRoute,
            JsonNode passB
    ) {
    }

    record CatalogProjection(
            Map<String, Object> summary,
            Map<String, Object> consistency,
            CatalogGovernanceView governance
    ) {
    }

    record ContractProjection(
            Map<String, Object> frozenSummary,
            Map<String, Object> currentSummary,
            Map<String, Object> consistency,
            ContractGovernanceView governance
    ) {
    }
}
