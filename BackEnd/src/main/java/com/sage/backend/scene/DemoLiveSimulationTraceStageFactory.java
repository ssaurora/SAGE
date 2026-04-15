package com.sage.backend.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
import com.sage.backend.scene.DemoCaseProfileFactory.DemoCaseProfile;
import com.sage.backend.scene.DemoLiveSimulationNarratives.DemoLiveSimulationNarrative;
import com.sage.backend.scene.dto.DemoLiveSimulationSupportDTO;
import com.sage.backend.scene.dto.DemoTraceStageDTO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DemoLiveSimulationTraceStageFactory {

    private static final String AUTHORITY_BACKED = "authority_backed";
    private static final String DEMO_ORCHESTRATED = "demo_orchestrated";
    private static final String DERIVED_SUMMARY = "derived_summary";
    private static final String UNSUPPORTED = "unsupported";

    private DemoLiveSimulationTraceStageFactory() {
    }

    static List<DemoTraceStageDTO> build(
            AnalysisSession session,
            List<SessionMessage> messages,
            DemoLiveSimulationSupportDTO support,
            ObjectMapper objectMapper,
            DemoLiveSimulationNarrative narrative
    ) {
        SessionMessage userGoal = findMessage(messages, "user_goal");
        SessionMessage understanding = findMessage(messages, "assistant_understanding");
        SessionMessage executionBrief = findMessage(messages, "assistant_execution_brief");
        SessionMessage resultReady = findMessage(messages, "result_ready");
        SessionMessage finalExplanation = findMessage(messages, "assistant_final_explanation");

        boolean taskCreated = userGoal != null;
        boolean goalUnderstood = understanding != null;
        boolean skillRouted = understanding != null;
        boolean planCompiled = executionBrief != null;
        boolean validationPassed = hasReachedRunSubmitted(support);
        boolean manifestSubmitted = hasReachedRunSubmitted(support);
        boolean executionCompleted = resultReady != null;
        boolean resultDelivered = finalExplanation != null;

        String runningStage = resolveRunningStage(
                taskCreated,
                goalUnderstood,
                planCompiled,
                validationPassed,
                manifestSubmitted,
                executionCompleted,
                resultDelivered,
                support
        );

        String goalParseSummary = nonBlank(field(understanding, "goal_parse_summary", objectMapper),
                "The request was framed as a " + narrative.analysisKind() + " focused on " + narrative.goalType() + ".");
        String problemFramingSummary = nonBlank(field(understanding, "problem_framing_summary", objectMapper), narrative.understandingText());
        String skillDisplayName = nonBlank(field(understanding, "skill_display_name", objectMapper), narrative.skillDisplayName());
        String capabilityKey = nonBlank(field(understanding, "capability_key", objectMapper), narrative.capabilityKey());
        String routeMode = nonBlank(field(understanding, "route_mode", objectMapper), "skill_grounded");
        String planningSummary = nonBlank(field(executionBrief, "planning_summary", objectMapper), narrative.planningSummary());
        String validationSummary = narrative.validationSummary();
        String manifestSummary = narrative.manifestSummary();
        String jobContractSummary = narrative.jobContractSummary();
        String executionSummary = narrative.executionProgressSummary();
        String resultExtractionSummary = nonBlank(field(finalExplanation, "result_extraction_summary", objectMapper), narrative.resultExtractionSummary());
        String artifactPromotionSummary = nonBlank(field(finalExplanation, "artifact_promotion_summary", objectMapper), narrative.artifactPromotionSummary());
        List<String> planInputs = stringArray(executionBrief, "key_inputs", objectMapper, narrative.planInputsSummary());
        DemoCaseProfile demoCaseProfile = narrative.demoCaseProfile();
        String studyAreaName = nonBlank(field(executionBrief, "study_area", objectMapper), demoCaseProfile == null ? narrative.studyAreaSummary() : demoCaseProfile.studyAreaName());
        String spatialUnitsSummary = nonBlank(field(executionBrief, "spatial_units", objectMapper), demoCaseProfile == null ? narrative.spatialUnitsSummary() : demoCaseProfile.spatialUnitsSummary());
        String selectedTemplate = nonBlank(field(executionBrief, "selected_template", objectMapper), demoCaseProfile == null ? narrative.selectedTemplate() : demoCaseProfile.selectedTemplate());
        String shortResultSummarySeed = nonBlank(field(resultReady, "summary", objectMapper), demoCaseProfile == null ? null : demoCaseProfile.shortResultSummarySeed());
        String planCompiledSummary = buildPlanCompiledSummary(planningSummary, studyAreaName, spatialUnitsSummary, planInputs, selectedTemplate);
        String resultDeliveredSummary = buildResultDeliveredSummary(studyAreaName, shortResultSummarySeed);
        List<String> planCompiledOutputs = buildPlanCompiledOutputs(studyAreaName, spatialUnitsSummary, planInputs, selectedTemplate);
        List<String> resultDeliveredOutputs = buildResultDeliveredOutputs(shortResultSummarySeed);

        List<DemoTraceStageDTO> stages = new ArrayList<>();
        stages.add(stage(
                "task_created",
                "Task Created",
                "control",
                status("task_created", taskCreated, runningStage),
                AUTHORITY_BACKED,
                true,
                "Projected from real session and task facts rather than a native trace event.",
                session.getCreatedAt() == null ? null : session.getCreatedAt().toString(),
                userGoal == null ? null : userGoal.getCreatedAt().toString(),
                "A governed task context has been projected from the accepted session request.",
                List.of(
                        "Session accepted for the " + narrative.skillDisplayName().toLowerCase() + " live simulation.",
                        "Current task pointer established for demo execution."
                ),
                payload("session_id", session.getSessionId(), "current_task_id", session.getCurrentTaskId(), "session_status", session.getStatus()),
                List.of(
                        child("session_accepted", "Session Accepted", "control", taskCreated ? "completed" : "pending", AUTHORITY_BACKED, "The live demo session accepted the first-turn request.", List.of("Scene-first session remains the authority-bearing context."), payload("session_id", session.getSessionId())),
                        child("task_context_attached", "Task Context Attached", "control", taskCreated ? "completed" : "pending", AUTHORITY_BACKED, "A task pointer is attached to the live demo session and used as the initial governed context.", List.of("Projected task surface uses real session/task linkage."), payload("task_id", session.getCurrentTaskId()))
                )
        ));

        stages.add(stage(
                "goal_understood",
                "Goal Understood",
                "cognition",
                status("goal_understood", goalUnderstood, runningStage),
                DEMO_ORCHESTRATED,
                false,
                null,
                userGoal == null ? null : userGoal.getCreatedAt().toString(),
                understanding == null ? null : understanding.getCreatedAt().toString(),
                "The demo orchestrator emitted a structured understanding of the " + narrative.skillDisplayName().toLowerCase() + " request.",
                List.of(
                        "Goal parse summary emitted for the direct-success path.",
                        "Problem framing selected for " + narrative.goalType() + "."
                ),
                payload("analysis_kind", field(understanding, "analysis_kind", objectMapper), "goal_type", field(understanding, "goal_type", objectMapper)),
                List.of(
                        child("goal_parse_summary", "Goal Parse Summary", "cognition", goalUnderstood ? "completed" : "pending", DEMO_ORCHESTRATED, goalParseSummary, List.of(nonBlank(field(understanding, "goal_type", objectMapper), narrative.goalType())), payload("source_user_goal", field(understanding, "source_user_goal", objectMapper))),
                        child("problem_framing_selected", "Problem Framing Selected", "cognition", goalUnderstood ? "completed" : "pending", DEMO_ORCHESTRATED, problemFramingSummary, List.of(nonBlank(field(understanding, "text", objectMapper), "Assistant understanding emitted.")), payload("analysis_kind", field(understanding, "analysis_kind", objectMapper)))
                )
        ));

        stages.add(stage(
                "skill_routed",
                "Skill Routed",
                "cognition",
                status("skill_routed", skillRouted, runningStage),
                DEMO_ORCHESTRATED,
                false,
                null,
                understanding == null ? null : understanding.getCreatedAt().toString(),
                understanding == null ? null : understanding.getCreatedAt().toString(),
                "The demo orchestrator routes the request into the " + skillDisplayName.toLowerCase() + " capability lane used for this walkthrough.",
                List.of(
                        "Skill selected for the " + narrative.skillDisplayName().toLowerCase() + " demo path.",
                        "Route mode resolved for the canonical success flow."
                ),
                payload("route_mode", routeMode, "capability_key", capabilityKey),
                List.of(
                        child("skill_selected", "Skill Selected", "cognition", skillRouted ? "completed" : "pending", DEMO_ORCHESTRATED, "The request is routed to the " + skillDisplayName + " capability for the demo walkthrough.", List.of(capabilityKey), payload("capability_key", capabilityKey)),
                        child("route_mode_resolved", "Route Mode Resolved", "cognition", skillRouted ? "completed" : "pending", DEMO_ORCHESTRATED, "The demo route mode remains skill-grounded for this canonical success path.", List.of(routeMode), payload("route_mode", routeMode))
                )
        ));

        stages.add(stage(
                "plan_compiled",
                "Plan Compiled",
                "planning",
                status("plan_compiled", planCompiled, runningStage),
                DERIVED_SUMMARY,
                true,
                "High-level demo summary stage. The current backend does not expose a native planning runtime trace for this path.",
                understanding == null ? null : understanding.getCreatedAt().toString(),
                executionBrief == null ? null : executionBrief.getCreatedAt().toString(),
                planCompiledSummary,
                planCompiledOutputs,
                payload(
                        "selected_template", field(understanding, "selected_template", objectMapper),
                        "study_area_name", studyAreaName,
                        "spatial_units_summary", spatialUnitsSummary,
                        "plan_input_roles_summary", planInputs,
                        "derived_from", List.of("assistant_understanding", "assistant_execution_brief")
                ),
                List.of(
                        child("template_role_declared", "Template / Role Declared", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "A high-level template and role declaration is inferred for the demo walkthrough.", List.of(nonBlank(field(understanding, "selected_template", objectMapper), selectedTemplate)), payload("selected_template", field(understanding, "selected_template", objectMapper), "case_display_name", demoCaseProfile == null ? null : demoCaseProfile.caseDisplayName())),
                        child("context_enrichment_summary", "Context Enrichment Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, planningSummary, buildContextOutputs(studyAreaName, spatialUnitsSummary), payload("analysis_kind", field(understanding, "analysis_kind", objectMapper), "study_area_name", studyAreaName, "spatial_units_summary", spatialUnitsSummary)),
                        child("slot_binding_summary", "Slot Binding Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "Key input roles were mapped into the demo planning surface without exposing raw slot-binding objects.", planInputs, payload("support_mode", "demo_walkthrough_summary", "plan_input_roles_summary", planInputs)),
                        child("args_draft_summary", "Args Draft Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "Execution arguments are represented only as a derived planning summary.", List.of("Current demo path does not expose native args drafting trace."), payload("support_mode", "derived_summary")),
                        child("execution_graph_summary", "Execution Graph Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "A runnable execution graph is implied for the walkthrough but not surfaced as a native runtime graph trace.", List.of("Demo walkthrough only."), payload("support_mode", "derived_summary")),
                        child("runtime_assertions_summary", "Runtime Assertions Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "Runtime assertions are summarized conceptually rather than traced as first-class runtime objects.", List.of("Current backend does not emit native assertion trace for this demo path."), payload("support_mode", "derived_summary"))
                )
        ));

        stages.add(stage(
                "validation_passed",
                "Validation Passed",
                "capability",
                status("validation_passed", validationPassed, runningStage),
                DEMO_ORCHESTRATED,
                false,
                null,
                executionBrief == null ? null : executionBrief.getCreatedAt().toString(),
                hasReachedRunSubmitted(support) ? executionBrief == null ? null : executionBrief.getCreatedAt().toString() : null,
                "The demo success path emits a single prepared, validated, and submitted beat once governed validation is considered satisfied.",
                List.of("Validation summary is surfaced as a single success-path checkpoint."),
                payload("run_surface_phase", support.getRunSurfaceProjection() == null ? null : support.getRunSurfaceProjection().getPhase()),
                List.of(
                        child("validation_summary", "Validation Summary", "capability", validationPassed ? "completed" : childStatus("validation_passed", runningStage), DEMO_ORCHESTRATED, validationSummary, List.of("Validation gate passed for the direct-success demo path."), payload("run_surface_phase", support.getRunSurfaceProjection() == null ? null : support.getRunSurfaceProjection().getPhase())),
                        child("repair_branch_summary", "Repair Branch Summary", "capability", "skipped", UNSUPPORTED, "Not used in this demo success path.", List.of("Repair and recovery routing is intentionally not exercised here."), payload("demo_path", "direct_success_only"))
                )
        ));

        stages.add(stage(
                "manifest_frozen_job_submitted",
                "Manifest Frozen & Job Submitted",
                "control",
                status("manifest_frozen_job_submitted", manifestSubmitted, runningStage),
                DEMO_ORCHESTRATED,
                true,
                "Control-plane walkthrough stage. The current backend does not expose a native manifest freeze trace event for this demo path.",
                executionBrief == null ? null : executionBrief.getCreatedAt().toString(),
                hasReachedRunSubmitted(support) ? executionBrief == null ? null : executionBrief.getCreatedAt().toString() : null,
                "The control plane walkthrough treats the prepared-and-submitted beat as the point where the execution package is frozen and submitted.",
                List.of(
                        "Manifest freeze summarized for demo walkthrough.",
                        "Job/workspace handoff summarized rather than traced as a native event."
                ),
                payload("run_surface_phase", support.getRunSurfaceProjection() == null ? null : support.getRunSurfaceProjection().getPhase(), "run_surface_detail", support.getRunSurfaceProjection() == null ? null : support.getRunSurfaceProjection().getDetail()),
                List.of(
                        child("manifest_freeze_summary", "Manifest Freeze Summary", "control", manifestSubmitted ? "completed" : childStatus("manifest_frozen_job_submitted", runningStage), DERIVED_SUMMARY, manifestSummary, List.of("Projected from the run-surface preparation/submission path."), payload("support_mode", "demo_control_plane_walkthrough")),
                        child("job_workspace_contract_summary", "Job / Workspace Contract Summary", "capability", manifestSubmitted ? "completed" : childStatus("manifest_frozen_job_submitted", runningStage), DERIVED_SUMMARY, jobContractSummary, List.of("Current direct-success demo does not expose the raw contract object."), payload("run_surface_detail", support.getRunSurfaceProjection() == null ? null : support.getRunSurfaceProjection().getDetail())),
                        child("submit_marker", "Submit Marker", "control", manifestSubmitted ? "completed" : childStatus("manifest_frozen_job_submitted", runningStage), DEMO_ORCHESTRATED, "The demo orchestrator marks the governed run as submitted before the running phase begins.", List.of(nonBlank(support.getDemoCurrentStep(), "demo_run_submitted")), payload("demo_current_step", support.getDemoCurrentStep()))
                )
        ));

        stages.add(stage(
                "execution_completed",
                "Execution Completed",
                "execution",
                status("execution_completed", executionCompleted, runningStage),
                DERIVED_SUMMARY,
                true,
                "Projected execution walkthrough stage. The current demo path does not expose a native heartbeat stream.",
                executionBrief == null ? null : executionBrief.getCreatedAt().toString(),
                resultReady == null ? null : resultReady.getCreatedAt().toString(),
                narrative.executionSummary(),
                List.of(
                        "Execution progress summarized without native heartbeat events.",
                        "Result-ready step marks execution completion for this demo."
                ),
                payload("result_bundle_id", session.getLatestResultBundleId(), "demo_support_mode", "execution_progress_summary"),
                List.of(
                        child("execution_progress_summary", "Execution Progress Summary", "execution", executionCompleted ? "completed" : childStatus("execution_completed", runningStage), DERIVED_SUMMARY, executionSummary, List.of("Demo-derived execution progress summary."), payload("source", "demo-derived")),
                        child("execution_completion_marker", "Execution Completion Marker", "execution", executionCompleted ? "completed" : childStatus("execution_completed", runningStage), DEMO_ORCHESTRATED, "The result-ready emission acts as the completion marker for the demo execution lane.", List.of("Completion marker emitted with the result-ready beat."), payload("result_message_id", resultReady == null ? null : resultReady.getMessageId()))
                )
        ));

        stages.add(stage(
                "result_delivered",
                "Result Delivered",
                "control",
                status("result_delivered", resultDelivered, runningStage),
                DERIVED_SUMMARY,
                true,
                "Projected result delivery stage. Mixed-authority children distinguish derived extraction summary from demo-orchestrated explanation arrival.",
                resultReady == null ? null : resultReady.getCreatedAt().toString(),
                finalExplanation == null ? null : finalExplanation.getCreatedAt().toString(),
                resultDeliveredSummary,
                resultDeliveredOutputs,
                payload("latest_result_bundle_id", session.getLatestResultBundleId(), "session_status", session.getStatus(), "study_area_name", studyAreaName),
                List.of(
                        child("result_extraction_summary", "Result Extraction Summary", "capability", executionCompleted ? "completed" : childStatus("result_delivered", runningStage), DERIVED_SUMMARY, resultExtractionSummary, List.of(nonBlank(field(resultReady, "result_object_summary", objectMapper), nonBlank(field(resultReady, "summary", objectMapper), "Result summary derived."))), payload("summary", field(resultReady, "summary", objectMapper), "result_object_summary", field(resultReady, "result_object_summary", objectMapper))),
                        child("artifact_promotion_summary", "Artifact Promotion Summary", "capability", executionCompleted ? "completed" : childStatus("result_delivered", runningStage), DERIVED_SUMMARY, artifactPromotionSummary, List.of("Current demo path does not expose a native artifact-promotion trace event."), payload("result_bundle_id", session.getLatestResultBundleId())),
                        child("primary_explanation_arrived", "Primary Explanation Arrived", "cognition", resultDelivered ? "completed" : childStatus("result_delivered", runningStage), DEMO_ORCHESTRATED, "The demo orchestrator releases the primary explanation into the main conversation after the result-ready beat.", List.of(nonBlank(field(finalExplanation, "text", objectMapper), "Primary explanation emitted.")), payload("explanation_source", "demo_final_explanation_emit"))
                )
        ));

        return stages;
    }

    private static DemoTraceStageDTO stage(String id, String label, String plane, String status, String authority, boolean projected, String note, String startedAt, String completedAt, String summary, List<String> outputs, Map<String, Object> payload, List<DemoTraceStageDTO> children) {
        DemoTraceStageDTO stage = new DemoTraceStageDTO();
        stage.setStageId(id);
        stage.setStageLabel(label);
        stage.setPlane(plane);
        stage.setStatus(status);
        stage.setTraceAuthorityLevel(authority);
        stage.setProjectedStageSurface(projected);
        stage.setStageSurfaceNote(note);
        stage.setStartedAt(startedAt);
        stage.setCompletedAt(completedAt);
        stage.setSummary(summary);
        stage.getKeyOutputs().addAll(outputs);
        stage.setPayload(payload);
        stage.getChildren().addAll(children);
        return stage;
    }

    private static DemoTraceStageDTO child(String id, String label, String plane, String status, String authority, String summary, List<String> outputs, Map<String, Object> payload) {
        return stage(id, label, plane, status, authority, false, null, null, null, summary, outputs, payload, List.of());
    }

    private static String resolveRunningStage(
            boolean taskCreated,
            boolean goalUnderstood,
            boolean planCompiled,
            boolean validationPassed,
            boolean manifestSubmitted,
            boolean executionCompleted,
            boolean resultDelivered,
            DemoLiveSimulationSupportDTO support
    ) {
        if (support.getDemoRunActive() == null || !support.getDemoRunActive()) {
            return null;
        }
        if (!taskCreated) return "task_created";
        if (!goalUnderstood) return "goal_understood";
        if (!planCompiled) return "plan_compiled";
        if (!validationPassed) return "validation_passed";
        if (!manifestSubmitted) return "manifest_frozen_job_submitted";
        if (!executionCompleted) return "execution_completed";
        if (!resultDelivered) return "result_delivered";
        return null;
    }

    private static String status(String stageId, boolean completed, String runningStage) {
        if (completed) return "completed";
        return stageId.equals(runningStage) ? "running" : "pending";
    }

    private static String childStatus(String owningStageId, String runningStage) {
        return owningStageId.equals(runningStage) ? "running" : "pending";
    }

    private static boolean hasReachedRunSubmitted(DemoLiveSimulationSupportDTO support) {
        String currentStep = support.getDemoCurrentStep();
        if (currentStep == null) {
            return false;
        }
        return switch (currentStep) {
            case "demo_run_submitted",
                    "demo_run_running",
                    "demo_result_ready_emitted",
                    "demo_assistant_reviewing_emitted",
                    "demo_assistant_final_explanation_emitted",
                    "demo_follow_up_invitation_emitted",
                    "demo_completed" -> true;
            default -> false;
        };
    }

    private static SessionMessage findMessage(List<SessionMessage> messages, String type) {
        for (SessionMessage message : messages) {
            if (type.equals(message.getMessageType())) return message;
        }
        return null;
    }

    private static String field(SessionMessage message, String field, ObjectMapper objectMapper) {
        JsonNode node = json(message, objectMapper);
        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static List<String> stringArray(SessionMessage message, String field, ObjectMapper objectMapper, List<String> fallback) {
        JsonNode node = json(message, objectMapper);
        if (node == null || node.get(field) == null || !node.get(field).isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node.get(field)) {
            if (item != null && !item.isNull()) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private static JsonNode json(SessionMessage message, ObjectMapper objectMapper) {
        if (message == null || message.getContentJson() == null || message.getContentJson().isBlank()) return null;
        try {
            return objectMapper.readTree(message.getContentJson());
        } catch (Exception exception) {
            return null;
        }
    }

    private static Map<String, Object> payload(Object... entries) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (entries[i] instanceof String key && entries[i + 1] != null) {
                payload.put(key, entries[i + 1]);
            }
        }
        return payload.isEmpty() ? null : payload;
    }

    private static String nonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static String buildPlanCompiledSummary(String planningSummary, String studyAreaName, String spatialUnitsSummary, List<String> planInputs, String selectedTemplate) {
        if (studyAreaName == null && spatialUnitsSummary == null) {
            return planningSummary;
        }
        return "Study area: " + nonBlank(studyAreaName, "Not specified")
                + ". Spatial units: " + nonBlank(spatialUnitsSummary, "Not specified")
                + ". Key inputs: " + String.join(", ", planInputs)
                + ". Template: " + nonBlank(selectedTemplate, "Not specified") + ".";
    }

    private static List<String> buildPlanCompiledOutputs(String studyAreaName, String spatialUnitsSummary, List<String> planInputs, String selectedTemplate) {
        List<String> outputs = new ArrayList<>();
        if (studyAreaName != null) outputs.add("Study area: " + studyAreaName);
        if (spatialUnitsSummary != null) outputs.add("Spatial units: " + spatialUnitsSummary);
        if (!planInputs.isEmpty()) outputs.add("Key inputs: " + String.join(", ", planInputs));
        if (selectedTemplate != null) outputs.add("Template: " + selectedTemplate);
        if (outputs.isEmpty()) {
            outputs.add("Template and walkthrough plan summary available.");
        }
        return outputs;
    }

    private static List<String> buildContextOutputs(String studyAreaName, String spatialUnitsSummary) {
        List<String> outputs = new ArrayList<>();
        if (studyAreaName != null) outputs.add("Study area: " + studyAreaName);
        if (spatialUnitsSummary != null) outputs.add("Spatial units: " + spatialUnitsSummary);
        if (outputs.isEmpty()) outputs.add("Walkthrough context summary available.");
        return outputs;
    }

    private static String buildResultDeliveredSummary(String studyAreaName, String shortResultSummarySeed) {
        String prefix = studyAreaName == null
                ? "Result objects and the primary explanation are ready for Session delivery."
                : "Result objects and the primary explanation are ready for " + studyAreaName + " Session delivery.";
        if (shortResultSummarySeed == null) {
            return prefix;
        }
        return prefix + " " + shortResultSummarySeed;
    }

    private static List<String> buildResultDeliveredOutputs(String shortResultSummarySeed) {
        List<String> outputs = new ArrayList<>();
        if (shortResultSummarySeed != null) {
            outputs.add(shortResultSummarySeed);
        } else {
            outputs.add("Result bundle pointer attached to the live demo session.");
        }
        outputs.add("Primary explanation becomes available in the main conversation.");
        return outputs;
    }
}
