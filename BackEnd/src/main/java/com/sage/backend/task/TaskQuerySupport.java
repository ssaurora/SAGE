package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
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
