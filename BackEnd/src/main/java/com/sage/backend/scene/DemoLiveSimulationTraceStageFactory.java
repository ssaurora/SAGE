package com.sage.backend.scene;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
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
        SessionMessage progress = findMessage(messages, "progress_update");
        SessionMessage result = findMessage(messages, "result_summary");

        boolean taskCreated = userGoal != null;
        boolean goalUnderstood = understanding != null;
        boolean skillRouted = understanding != null;
        boolean planCompiled = progress != null;
        boolean validationPassed = progress != null;
        boolean manifestSubmitted = progress != null;
        boolean executionCompleted = result != null;
        boolean resultDelivered = result != null;

        String runningStage = resolveRunningStage(taskCreated, goalUnderstood, planCompiled, executionCompleted, resultDelivered, support);

        String goalParseSummary = nonBlank(field(understanding, "goal_parse_summary", objectMapper),
                "The request was framed as a " + narrative.analysisKind() + " focused on " + narrative.goalType() + ".");
        String problemFramingSummary = nonBlank(field(understanding, "problem_framing_summary", objectMapper), narrative.understandingText());
        String skillDisplayName = nonBlank(field(understanding, "skill_display_name", objectMapper), narrative.skillDisplayName());
        String capabilityKey = nonBlank(field(understanding, "capability_key", objectMapper), narrative.capabilityKey());
        String routeMode = nonBlank(field(understanding, "route_mode", objectMapper), "skill_grounded");
        String planningSummary = nonBlank(field(progress, "planning_summary", objectMapper), narrative.planningSummary());
        String validationSummary = nonBlank(field(progress, "validation_summary", objectMapper), narrative.validationSummary());
        String manifestSummary = nonBlank(field(progress, "manifest_summary", objectMapper), narrative.manifestSummary());
        String jobContractSummary = nonBlank(field(progress, "job_contract_summary", objectMapper), narrative.jobContractSummary());
        String executionSummary = nonBlank(field(progress, "execution_progress_summary", objectMapper), narrative.executionProgressSummary());
        String resultExtractionSummary = nonBlank(field(result, "result_extraction_summary", objectMapper), narrative.resultExtractionSummary());
        String artifactPromotionSummary = nonBlank(field(result, "artifact_promotion_summary", objectMapper), narrative.artifactPromotionSummary());
        List<String> planInputs = stringArray(progress, "plan_inputs_summary", objectMapper, narrative.planInputsSummary());

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
                progress == null ? null : progress.getCreatedAt().toString(),
                planningSummary,
                List.of(
                        "Template and role declaration summarized for demo walkthrough.",
                        "Context, bindings, args, and execution assumptions summarized rather than traced natively."
                ),
                payload("selected_template", field(understanding, "selected_template", objectMapper), "derived_from", List.of("assistant_understanding", "progress_update")),
                List.of(
                        child("template_role_declared", "Template / Role Declared", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "A high-level template and role declaration is inferred for the demo walkthrough.", List.of(nonBlank(field(understanding, "selected_template", objectMapper), narrative.selectedTemplate())), payload("selected_template", field(understanding, "selected_template", objectMapper))),
                        child("context_enrichment_summary", "Context Enrichment Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, planningSummary, planInputs, payload("analysis_kind", field(understanding, "analysis_kind", objectMapper))),
                        child("slot_binding_summary", "Slot Binding Summary", "planning", planCompiled ? "completed" : "pending", DERIVED_SUMMARY, "Binding details are summarized as part of the demo planning surface.", List.of("No raw slot-binding object is exposed in this demo path."), payload("support_mode", "demo_walkthrough_summary")),
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
                progress == null ? null : progress.getCreatedAt().toString(),
                progress == null ? null : progress.getCreatedAt().toString(),
                "The demo success path emits a single prepared, validated, and submitted beat once governed validation is considered satisfied.",
                List.of("Validation summary is surfaced as a single success-path checkpoint."),
                payload("latest_progress_note", field(progress, "latest_progress_note", objectMapper)),
                List.of(
                        child("validation_summary", "Validation Summary", "capability", validationPassed ? "completed" : "pending", DEMO_ORCHESTRATED, validationSummary, List.of(nonBlank(field(progress, "latest_progress_note", objectMapper), "Validation gate passed.")), payload("current_phase_label", field(progress, "current_phase_label", objectMapper))),
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
                progress == null ? null : progress.getCreatedAt().toString(),
                progress == null ? null : progress.getCreatedAt().toString(),
                "The control plane walkthrough treats the prepared-and-submitted beat as the point where the execution package is frozen and submitted.",
                List.of(
                        "Manifest freeze summarized for demo walkthrough.",
                        "Job/workspace handoff summarized rather than traced as a native event."
                ),
                payload("current_system_action", field(progress, "current_system_action", objectMapper), "estimated_next_milestone", field(progress, "estimated_next_milestone", objectMapper)),
                List.of(
                        child("manifest_freeze_summary", "Manifest Freeze Summary", "control", manifestSubmitted ? "completed" : "pending", DERIVED_SUMMARY, manifestSummary, List.of("Projected from the prepared-and-submitted demo beat."), payload("support_mode", "demo_control_plane_walkthrough")),
                        child("job_workspace_contract_summary", "Job / Workspace Contract Summary", "capability", manifestSubmitted ? "completed" : "pending", DERIVED_SUMMARY, jobContractSummary, List.of("Current direct-success demo does not expose the raw contract object."), payload("estimated_next_milestone", field(progress, "estimated_next_milestone", objectMapper))),
                        child("submit_marker", "Submit Marker", "control", manifestSubmitted ? "completed" : "pending", DEMO_ORCHESTRATED, "The demo orchestrator marks the governed run as submitted at this step.", List.of(nonBlank(field(progress, "latest_progress_note", objectMapper), "Prepared, validated, and submitted.")), payload("demo_current_step", support.getDemoCurrentStep()))
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
                progress == null ? null : progress.getCreatedAt().toString(),
                result == null ? null : result.getCreatedAt().toString(),
                narrative.executionSummary(),
                List.of(
                        "Execution progress summarized without native heartbeat events.",
                        "Result-ready step marks execution completion for this demo."
                ),
                payload("result_bundle_id", session.getLatestResultBundleId(), "demo_support_mode", "execution_progress_summary"),
                List.of(
                        child("execution_progress_summary", "Execution Progress Summary", "execution", executionCompleted ? "completed" : childStatus("execution_completed", runningStage), DERIVED_SUMMARY, executionSummary, List.of("Demo-derived execution progress summary."), payload("source", "demo-derived")),
                        child("execution_completion_marker", "Execution Completion Marker", "execution", executionCompleted ? "completed" : childStatus("execution_completed", runningStage), DEMO_ORCHESTRATED, "The result-ready emission acts as the completion marker for the demo execution lane.", List.of("Completion marker emitted with result step."), payload("result_message_id", result == null ? null : result.getMessageId()))
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
                result == null ? null : result.getCreatedAt().toString(),
                result == null ? null : result.getCreatedAt().toString(),
                "Result delivery bundles extraction summary and explanation arrival without exposing raw execution internals in the main conversation.",
                List.of(
                        "Result bundle pointer attached to the live demo session.",
                        "Primary explanation becomes available in the main conversation."
                ),
                payload("latest_result_bundle_id", session.getLatestResultBundleId(), "session_status", session.getStatus()),
                List.of(
                        child("result_extraction_summary", "Result Extraction Summary", "capability", resultDelivered ? "completed" : "pending", DERIVED_SUMMARY, resultExtractionSummary, List.of(nonBlank(field(result, "summary", objectMapper), "Result summary derived.")), payload("summary", field(result, "summary", objectMapper))),
                        child("artifact_promotion_summary", "Artifact Promotion Summary", "capability", resultDelivered ? "completed" : "pending", DERIVED_SUMMARY, artifactPromotionSummary, List.of("Current demo path does not expose a native artifact-promotion trace event."), payload("result_bundle_id", session.getLatestResultBundleId())),
                        child("primary_explanation_arrived", "Primary Explanation Arrived", "cognition", resultDelivered ? "completed" : "pending", DEMO_ORCHESTRATED, "The demo orchestrator releases the primary explanation into the main conversation with the result-ready step.", List.of(nonBlank(field(result, "text", objectMapper), "Primary explanation emitted.")), payload("explanation_source", "demo_result_summary_mapping"))
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

    private static String resolveRunningStage(boolean taskCreated, boolean goalUnderstood, boolean planCompiled, boolean executionCompleted, boolean resultDelivered, DemoLiveSimulationSupportDTO support) {
        if (support.getDemoRunActive() == null || !support.getDemoRunActive()) {
            return null;
        }
        if (!taskCreated) return "task_created";
        if (!goalUnderstood) return "goal_understood";
        if (!planCompiled) return "plan_compiled";
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
}
