package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.task.dto.CorruptionStateView;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;

import java.util.ArrayList;
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
}
