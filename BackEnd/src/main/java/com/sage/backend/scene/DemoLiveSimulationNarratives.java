package com.sage.backend.scene;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.scene.DemoCaseProfileFactory.DemoCaseProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DemoLiveSimulationNarratives {

    static final String DEMO_NARRATIVE_TYPE_URBAN_COOLING = "urban_cooling";
    static final String DEMO_NARRATIVE_TYPE_WATER_YIELD = "water_yield";

    private static final DemoCaseProfile WATER_YIELD_GURA_PROFILE = DemoCaseProfileFactory.waterYieldGuraProfile();

    private static final DemoLiveSimulationNarrative URBAN_COOLING = new DemoLiveSimulationNarrative(
            DEMO_NARRATIVE_TYPE_URBAN_COOLING,
            null,
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
            "Before I run it, here's the execution brief for this analysis.",
            List.of(
                    "Cooling priority zone summary",
                    "Canopy opportunity blocks",
                    "Interpretation-ready cooling priority summary"
            ),
            "Reviewing the strongest cooling priority patterns and the tradeoff between canopy expansion and park expansion.",
            "The result is ready. Cooling priority zones have been identified and ranked.",
            "The strongest cooling opportunities cluster in low-canopy neighborhoods with high afternoon heat burden and limited park access. These places matter most because heat burden, missing shade, and limited green relief overlap more strongly there than in the rest of the city. From a management perspective, this suggests prioritizing canopy expansion in the highest-burden residential blocks first, then using park expansion selectively where dense apartment clusters still lack accessible green relief.",
            "If you want, I can continue by comparing tree canopy expansion versus park expansion, narrowing this to one district or corridor, or turning these priority zones into a phased action shortlist.",
            List.of(
                    "Urban heat exposure surface",
                    "Existing tree canopy coverage",
                    "Park access and green-space availability"
            ),
            "Citywide heat-mitigation study area",
            "Cooling priority zones + canopy opportunity blocks",
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
            ),
            "Validating configured urban cooling inputs and finalizing the governed run package.",
            "The governed urban cooling run has been submitted.",
            "Running the governed urban cooling analysis.",
            "Urban cooling outputs are ready for review and interpretation."
    );

    private static final DemoLiveSimulationNarrative WATER_YIELD = new DemoLiveSimulationNarrative(
            DEMO_NARRATIVE_TYPE_WATER_YIELD,
            WATER_YIELD_GURA_PROFILE,
            "scene-water-yield-for-gura-subwatersheds",
            "sess_live_water_yield",
            "task_live_water_yield",
            "task_demo_water_yield",
            "rb_demo_water_yield_gura_2024",
            WATER_YIELD_GURA_PROFILE.caseDisplayName(),
            WATER_YIELD_GURA_PROFILE.canonicalUserGoal(),
            "water_yield_first_turn_live_success",
            WATER_YIELD_GURA_PROFILE.capabilityKey(),
            "Water Yield",
            WATER_YIELD_GURA_PROFILE.analysisKind(),
            "water supply contribution",
            WATER_YIELD_GURA_PROFILE.selectedTemplate(),
            "I'll analyze annual water yield across the " + WATER_YIELD_GURA_PROFILE.studyAreaName() + " and its subwatersheds to identify where water supply contribution is strongest and where the pattern has the clearest management implications.",
            "Before I run it, here's the execution brief for this analysis.",
            List.of(
                    "Watershed summary",
                    "Subwatershed contribution outputs",
                    "Interpretation-ready management summary"
            ),
            "I'm reviewing the strongest subwatershed contribution patterns and management implications.",
            "The result is ready. Annual water yield outputs are available for the watershed and subwatersheds.",
            "The strongest annual water yield contribution is concentrated in a subset of Gura subwatersheds, especially the upper catchments where precipitation input is stronger and land-cover conditions support higher runoff and supply generation. These subwatersheds matter most because they contribute disproportionately to downstream water availability relative to the rest of the study area. From a management perspective, " + WATER_YIELD_GURA_PROFILE.managementInterpretationSeed(),
            "If you want, I can continue by comparing subwatershed water yield, focusing on one catchment, or summarizing the management implications.",
            WATER_YIELD_GURA_PROFILE.planInputRolesSummary(),
            WATER_YIELD_GURA_PROFILE.studyAreaName(),
            "Watershed + subwatersheds",
            "The water yield run plan was compiled for the " + WATER_YIELD_GURA_PROFILE.studyAreaName() + " using " + WATER_YIELD_GURA_PROFILE.spatialUnitsSummary().toLowerCase() + " and the required water-yield input roles.",
            "The annual water yield request passed governed validation for the configured watershed and climate inputs.",
            "A governed annual water yield execution package was frozen for the demo walkthrough before submission.",
            "Job and workspace handoff are summarized for the annual water yield demo execution path.",
            "The demo execution path completed and returned annual water yield outputs for the watershed and subwatersheds.",
            "This is a demo-derived execution progress summary, not native runtime heartbeat telemetry.",
            "Annual water yield outputs were summarized into watershed-level and subwatershed-level contribution facts for the Gura case.",
            "The demo result bundle and primary water-yield interpretation artifacts were promoted for Session delivery.",
            WATER_YIELD_GURA_PROFILE.resultHighlightSeeds(),
            "Validating configured water-yield inputs and finalizing the governed run package.",
            "The governed annual water yield run has been submitted.",
            "Running the governed annual water yield analysis.",
            "Annual water yield outputs are ready for review and interpretation."
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
            DemoCaseProfile demoCaseProfile,
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
            String executionBriefIntro,
            List<String> expectedOutputsSummary,
            String reviewingText,
            String resultReadyText,
            String primaryExplanationText,
            String followUpInvitationText,
            List<String> planInputsSummary,
            String studyAreaSummary,
            String spatialUnitsSummary,
            String planningSummary,
            String validationSummary,
            String manifestSummary,
            String jobContractSummary,
            String executionSummary,
            String executionProgressSummary,
            String resultExtractionSummary,
            String artifactPromotionSummary,
            List<String> resultHighlights,
            String runPreparingDetail,
            String runSubmittedDetail,
            String runRunningDetail,
            String runCompletedDetail
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
            if (demoCaseProfile != null) {
                root.put("demo_case_id", demoCaseProfile.demoCaseId());
                root.put("case_display_name", demoCaseProfile.caseDisplayName());
                root.put("study_area_name", demoCaseProfile.studyAreaName());
            }
            return root;
        }

        ObjectNode buildExecutionBriefPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", executionBriefIntro);
            root.put("analysis_type", analysisKind);
            root.put("study_area", studyAreaSummary);
            root.put("spatial_units", spatialUnitsSummary);

            ArrayNode keyInputs = root.putArray("key_inputs");
            planInputsSummary.forEach(keyInputs::add);

            ArrayNode expectedOutputs = root.putArray("expected_outputs");
            expectedOutputsSummary.forEach(expectedOutputs::add);

            ObjectNode brief = root.putObject("brief");
            brief.put("analysis_type", analysisKind);
            brief.put("study_area", studyAreaSummary);
            brief.put("spatial_units", spatialUnitsSummary);
            ArrayNode briefInputs = brief.putArray("key_inputs");
            planInputsSummary.forEach(briefInputs::add);
            ArrayNode briefOutputs = brief.putArray("expected_outputs");
            expectedOutputsSummary.forEach(briefOutputs::add);

            root.put("planning_summary", planningSummary);
            root.put("selected_template", selectedTemplate);
            root.put("capability_key", capabilityKey);
            root.put("result_object_summary", shortResultSummary());
            if (demoCaseProfile != null) {
                root.put("demo_case_id", demoCaseProfile.demoCaseId());
                root.put("case_display_name", demoCaseProfile.caseDisplayName());
            }
            return root;
        }

        ObjectNode buildResultReadyPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", resultReadyText);
            root.put("summary", shortResultSummary());
            root.put("result_ready_text", resultReadyText);
            root.put("result_object_summary", shortResultSummary());
            ArrayNode highlights = root.putArray("highlights");
            resultHighlights.forEach(highlights::add);
            if (demoCaseProfile != null) {
                root.put("case_display_name", demoCaseProfile.caseDisplayName());
                root.put("study_area_name", demoCaseProfile.studyAreaName());
            }
            return root;
        }

        ObjectNode buildAssistantReviewingPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", reviewingText);
            return root;
        }

        ObjectNode buildFinalExplanationPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", primaryExplanationText);
            root.put("summary", shortResultSummary());
            root.put("result_extraction_summary", resultExtractionSummary);
            root.put("artifact_promotion_summary", artifactPromotionSummary);
            root.put("result_object_summary", shortResultSummary());
            ArrayNode highlights = root.putArray("highlights");
            resultHighlights.forEach(highlights::add);
            if (demoCaseProfile != null) {
                root.put("case_display_name", demoCaseProfile.caseDisplayName());
                root.put("study_area_name", demoCaseProfile.studyAreaName());
            }
            return root;
        }

        ObjectNode buildFollowUpPayload(ObjectMapper objectMapper) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("text", followUpInvitationText);
            return root;
        }

        private String shortResultSummary() {
            return demoCaseProfile == null ? resultReadyText : demoCaseProfile.shortResultSummarySeed();
        }
    }
}
