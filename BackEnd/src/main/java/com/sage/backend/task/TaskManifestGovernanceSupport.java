package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.model.TaskState;
import com.sage.backend.planning.Pass1FactHelper;

final class TaskManifestGovernanceSupport {
    private TaskManifestGovernanceSupport() {
    }

    static ManifestGovernanceProjection build(
            String taskId,
            String userQuery,
            TaskState latestTaskState,
            JsonNode pass1Node,
            TaskAttachmentMapper taskAttachmentMapper,
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            GoalRouteService goalRouteService,
            ObjectMapper objectMapper
    ) {
        TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts = TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                taskId,
                latestTaskState,
                taskAttachmentMapper,
                taskCatalogSnapshotService
        );

        String goalParseJson = latestTaskState == null ? null : latestTaskState.getGoalParseJson();
        String skillRouteJson = latestTaskState == null ? null : latestTaskState.getSkillRouteJson();
        if ((goalParseJson == null || goalParseJson.isBlank()) || (skillRouteJson == null || skillRouteJson.isBlank())) {
            GoalRouteService.GoalRouteDecision fallbackDecision = goalRouteService.deriveFallback(userQuery, pass1Node);
            if (goalParseJson == null || goalParseJson.isBlank()) {
                goalParseJson = writeJson(fallbackDecision.goalParse(), objectMapper);
            }
            if (skillRouteJson == null || skillRouteJson.isBlank()) {
                skillRouteJson = writeJson(fallbackDecision.skillRoute(), objectMapper);
            }
        }
        if ((goalParseJson != null && !goalParseJson.isBlank()) || (skillRouteJson != null && !skillRouteJson.isBlank())) {
            JsonNode enrichedGoalParse = goalRouteService.enrichGoalParse(readJson(goalParseJson, objectMapper), pass1Node);
            JsonNode enrichedSkillRoute = goalRouteService.enrichSkillRoute(readJson(skillRouteJson, objectMapper), pass1Node);
            goalParseJson = writeJson(enrichedGoalParse, objectMapper);
            skillRouteJson = writeJson(enrichedSkillRoute, objectMapper);
        }

        return new ManifestGovernanceProjection(
                writeJson(currentCatalogFacts.summary(), objectMapper),
                TaskGovernanceFactSupport.writeContractSummary(pass1Node, objectMapper),
                pass1Node == null || pass1Node.isMissingNode()
                        ? null
                        : Pass1FactHelper.normalizeCapabilityKey(pass1Node.path("capability_key").asText(null)),
                pass1Node == null || pass1Node.isMissingNode() ? null : pass1Node.path("selected_template").asText(null),
                pass1Node == null || pass1Node.isMissingNode() ? null : pass1Node.path("template_version").asText(null),
                goalParseJson,
                skillRouteJson
        );
    }

    private static JsonNode readJson(String source, ObjectMapper objectMapper) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(source);
        } catch (Exception exception) {
            return null;
        }
    }

    private static String writeJson(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    record ManifestGovernanceProjection(
            String catalogSummaryJson,
            String contractSummaryJson,
            String capabilityKey,
            String selectedTemplate,
            String templateVersion,
            String goalParseJson,
            String skillRouteJson
    ) {
    }
}
