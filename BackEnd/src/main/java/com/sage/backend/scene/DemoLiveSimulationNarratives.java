package com.sage.backend.scene;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.model.AnalysisSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DemoLiveSimulationNarratives {

    static final String DEMO_NARRATIVE_TYPE_URBAN_COOLING = "urban_cooling";
    static final String DEMO_NARRATIVE_TYPE_WATER_YIELD = "water_yield";

    private static final DemoLiveSimulationNarrative URBAN_COOLING = new DemoLiveSimulationNarrative(
            DEMO_NARRATIVE_TYPE_URBAN_COOLING,
            "scene-urban-cooling-for-heat-mitigation",
            "sess_live_urban_cooling",
            "task_live_urban_cooling",
            "task_demo_urban_cooling",
            "rb_demo_urban_cooling_2024",
            "Urban Cooling for Heat Mitigation",
            "Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.",
            "urban_cooling_first_turn_live_success",
            "urban_cooling_priority",
            "Urban Cooling",
            "urban cooling prioritization",
            "heat mitigation",
            "urban_cooling_v1",
            "I'll identify where added tree canopy and park expansion are most likely to reduce extreme heat exposure across the city, with priority given to places where heat burden and lack of green access overlap.",
            "The analysis has been prepared, validated, and submitted under governed execution. I'll update you when the result is ready.",
            "Preparing governed urban cooling analysis",
            "Analysis prepared, validated, and submitted for governed execution.",
            "Cooling priority zones ranked",
            "A governed result is ready. The strongest cooling opportunities cluster in low-canopy neighborhoods with high afternoon heat burden and limited park access.",
            "The result is ready. Cooling priority zones have been identified and ranked.",
            "Priority zones concentrate in low-canopy neighborhoods with high heat burden and limited park access.",
            "If you want, I can continue by comparing tree canopy expansion versus park expansion, narrowing this to one district or corridor, or turning these priority zones into a phased action shortlist.",
            List.of(
                    "Urban heat exposure surface",
                    "Existing tree canopy coverage",
                    "Park access and green-space availability"
            ),
            "Relevant heat burden, canopy gap, and green-access context were compiled into a walkthrough plan summary for the urban cooling run.",
            "Prepared urban cooling inputs passed the governed validation gate for this demo success path.",
            "Manifest freezing is represented as a control-plane walkthrough summary for the governed urban cooling run.",
            "Job and workspace handoff are summarized for the governed urban cooling execution path.",
            "The demo execution path completed and returned urban cooling outputs for priority zones and canopy opportunity blocks.",
            "This is a demo-derived execution progress summary, not native runtime heartbeat telemetry.",
            "Structured result facts were summarized into cooling-priority and canopy-opportunity interpretation cues.",
            "The demo result bundle and primary urban cooling interpretation artifacts were promoted for Session delivery.",
            List.of(
                    "Southwest residential heat islands rank highest for canopy expansion.",
                    "The central transit corridor shows the largest cooling benefit per hectare of added shade.",
                    "Pocket park expansion is recommended around dense apartment clusters with limited green relief."
            )
    );

    private static final DemoLiveSimulationNarrative WATER_YIELD = new DemoLiveSimulationNarrative(
            DEMO_NARRATIVE_TYPE_WATER_YIELD,
            "scene-water-yield-for-gura-subwatersheds",
            "sess_live_water_yield",
            "task_live_water_yield",
            "task_demo_water_yield",
            "rb_demo_water_yield_gura_2024",
            "Water Yield for Gura Subwatersheds",
            "Identify which subwatersheds contribute the most annual water yield and summarize what the pattern implies for watershed management.",
            "water_yield_first_turn_live_success",
            "water_yield",
            "Water Yield",
            "annual water yield analysis",
            "water supply contribution",
            "water_yield_v1",
            "I'll analyze annual water yield across the Gura watershed and subwatersheds to identify where water supply contribution is strongest and where the pattern has the clearest management implications.",
            "The annual water yield analysis has been prepared, validated, and submitted under governed execution. I'll update you when the result is ready.",
            "Preparing governed annual water yield analysis",
            "Annual water yield analysis prepared, validated, and submitted for governed execution.",
            "Subwatershed contribution patterns summarized",
            "A governed result is ready. The strongest annual water yield contribution is concentrated in a subset of Gura subwatersheds, especially the upper catchments where precipitation input is stronger and land-cover conditions support higher runoff and supply generation. These subwatersheds matter most because they contribute disproportionately to downstream water availability relative to the rest of the study area. From a management perspective, this suggests prioritizing protection and monitoring in the high-yield catchments, while using the lower-yield subwatersheds to flag areas where land-use pressure or vegetation change could reduce future supply stability.",
            "The result is ready. Annual water yield outputs are available for the watershed and subwatersheds.",
            "Highest water-yield contribution is concentrated in a subset of Gura subwatersheds with stronger downstream supply significance.",
            "If you want, I can continue by comparing subwatershed water yield, focusing on one catchment, or summarizing the management implications.",
            List.of(
                    "Watershed and subwatershed boundaries",
                    "Land use and land cover",
                    "Biophysical coefficients",
                    "Annual precipitation and reference evapotranspiration"
            ),
            "The water yield run plan was compiled from watershed boundaries, land use, biophysical coefficients, precipitation, and reference evapotranspiration inputs.",
            "The annual water yield request passed governed validation for the configured watershed and climate inputs.",
            "A governed annual water yield execution package was frozen for the demo walkthrough before submission.",
            "Job and workspace handoff are summarized for the annual water yield demo execution path.",
            "The demo execution path completed and returned annual water yield outputs for the watershed and subwatersheds.",
            "This is a demo-derived execution progress summary, not native runtime heartbeat telemetry.",
            "Result outputs were summarized into watershed-level and subwatershed-level annual water yield interpretation cues.",
            "The demo result bundle and primary water-yield interpretation artifacts were promoted for Session delivery.",
            List.of(
                    "Upper Gura catchments contribute the strongest annual water yield signal.",
                    "Subwatershed contribution is uneven, with a subset carrying disproportionate downstream supply importance.",
                    "Management attention should prioritize protection in high-yield catchments and monitoring in lower-yield areas under land-use pressure."
            )
    );

    private static final Map<String, DemoLiveSimulationNarrative> BY_TYPE = byType();
    private static final Map<String, DemoLiveSimulationNarrative> BY_SCENE_ID = bySceneId();
    private static final Map<String, DemoLiveSimulationNarrative> BY_SESSION_ID = bySessionId();

    private DemoLiveSimulationNarratives() {
    }

    static DemoLiveSimulationNarrative findByType(String narrativeType) {
        return narrativeType == null ? null : BY_TYPE.get(narrativeType);
    }

    static DemoLiveSimulationNarrative findBySceneAndSession(String sceneId, AnalysisSession session) {
        if (sceneId == null || session == null || session.getSessionId() == null) {
            return null;
        }
        DemoLiveSimulationNarrative byScene = BY_SCENE_ID.get(sceneId);
        if (byScene != null && byScene.liveSessionId().equals(session.getSessionId())) {
            return byScene;
        }
        return null;
    }

    static DemoLiveSimulationNarrative findBySessionId(String sessionId) {
        return sessionId == null ? null : BY_SESSION_ID.get(sessionId);
    }

    private static Map<String, DemoLiveSimulationNarrative> byType() {
        Map<String, DemoLiveSimulationNarrative> values = new LinkedHashMap<>();
        values.put(URBAN_COOLING.demoNarrativeType(), URBAN_COOLING);
        values.put(WATER_YIELD.demoNarrativeType(), WATER_YIELD);
        return values;
    }

    private static Map<String, DemoLiveSimulationNarrative> bySceneId() {
        Map<String, DemoLiveSimulationNarrative> values = new LinkedHashMap<>();
        values.put(URBAN_COOLING.sceneId(), URBAN_COOLING);
        values.put(WATER_YIELD.sceneId(), WATER_YIELD);
        return values;
    }

    private static Map<String, DemoLiveSimulationNarrative> bySessionId() {
        Map<String, DemoLiveSimulationNarrative> values = new LinkedHashMap<>();
        values.put(URBAN_COOLING.liveSessionId(), URBAN_COOLING);
        values.put(WATER_YIELD.liveSessionId(), WATER_YIELD);
        return values;
    }

    record DemoLiveSimulationNarrative(
            String demoNarrativeType,
            String sceneId,
            String liveSessionId,
            String liveTaskId,
            String replayTaskId,
            String replayResultBundleId,
            String defaultTitle,
            String defaultUserGoal,
            String demoRunScope,
            String capabilityKey,
            String skillDisplayName,
            String analysisKind,
            String goalType,
            String selectedTemplate,
            String understandingText,
            String preparedSubmittedText,
            String currentSystemAction,
            String validationNote,
            String estimatedNextMilestone,
            String primaryExplanationText,
            String resultReadyText,
            String shortResultSummary,
            String followUpInvitationText,
            List<String> planInputsSummary,
            String planningSummary,
            String validationSummary,
            String manifestSummary,
            String jobContractSummary,
            String executionSummary,
            String executionProgressSummary,
            String resultExtractionSummary,
            String artifactPromotionSummary,
            List<String> resultHighlights
    ) {

        ObjectNode buildAssistantUnderstandingPayload(ObjectMapper objectMapper, String userGoal) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", understandingText);
            root.put("analysis_kind", analysisKind);
            root.put("goal_type", goalType);
            root.put("route_mode", "skill_grounded");
            root.put("capability_key", capabilityKey);
            root.put("skill_display_name", skillDisplayName);
            root.put("selected_template", selectedTemplate);
            root.put("source_user_goal", userGoal);
            root.put("goal_parse_summary", "The request was framed as a " + analysisKind + " focused on " + goalType + ".");
            root.put("problem_framing_summary", understandingText);
            return root;
        }

        ObjectNode buildProgressUpdatePayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", preparedSubmittedText);
            root.put("current_phase_label", "RUNNING");
            root.put("current_system_action", currentSystemAction);
            root.put("latest_progress_note", validationNote);
            root.put("estimated_next_milestone", estimatedNextMilestone);
            root.put("planning_summary", planningSummary);
            root.put("validation_summary", validationSummary);
            root.put("manifest_summary", manifestSummary);
            root.put("job_contract_summary", jobContractSummary);
            root.put("execution_progress_summary", executionProgressSummary);
            ArrayNode inputs = root.putArray("plan_inputs_summary");
            planInputsSummary.forEach(inputs::add);
            return root;
        }

        ObjectNode buildResultSummaryPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", "A governed result is ready. " + primaryExplanationText);
            root.put("summary", shortResultSummary);
            root.put("result_ready_text", resultReadyText);
            root.put("result_extraction_summary", resultExtractionSummary);
            root.put("artifact_promotion_summary", artifactPromotionSummary);
            ArrayNode highlights = root.putArray("highlights");
            resultHighlights.forEach(highlights::add);
            return root;
        }

        ObjectNode buildFollowUpPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", followUpInvitationText);
            return root;
        }
    }
}
