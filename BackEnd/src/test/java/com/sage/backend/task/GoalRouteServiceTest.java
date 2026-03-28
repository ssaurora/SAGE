package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoalRouteServiceTest {
    private GoalRouteService service;

    @BeforeEach
    void setUp() {
        service = new GoalRouteService(new ObjectMapper());
    }

    @Test
    void deriveIncludesCanonicalCapabilityAndSource() {
        GoalRouteService.GoalRouteDecision decision = service.derive("run water yield with precipitation and eto");

        assertEquals("water_yield_analysis", decision.goalParse().path("goal_type").asText());
        assertEquals("water_yield", decision.goalParse().path("analysis_kind").asText());
        assertEquals("goal_router", decision.goalParse().path("source").asText());
        assertEquals("governed_baseline", decision.goalParse().path("execution_mode").asText());
        assertEquals("water_yield", decision.skillRoute().path("capability_key").asText());
        assertEquals("goal_router", decision.skillRoute().path("source").asText());
    }

    @Test
    void deriveMarksRealCaseExecutionPreferenceForInvestQueries() {
        GoalRouteService.GoalRouteDecision decision = service.derive("run a real case invest water yield analysis");

        assertEquals("real_case_validation", decision.goalParse().path("execution_mode").asText());
        assertEquals("planning-pass1-invest-local", decision.skillRoute().path("provider_preference").asText());
        assertEquals("docker-invest-real", decision.skillRoute().path("runtime_profile_preference").asText());
    }

    @Test
    void deriveFallbackUsesPass1FactsInsteadOfSelectedTemplateAsGoalType() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "stable_defaults": {
                    "analysis_template": "water_yield_v2"
                  }
                }
                """);

        GoalRouteService.GoalRouteDecision decision = service.deriveFallback("resume with precipitation upload", pass1Result);

        assertEquals("repairable_analysis_request", decision.goalParse().path("goal_type").asText());
        assertEquals("water_yield", decision.goalParse().path("analysis_kind").asText());
        assertEquals("derived_fallback", decision.goalParse().path("source").asText());
        assertEquals("water_yield", decision.skillRoute().path("capability_key").asText());
        assertEquals("water_yield_v2", decision.skillRoute().path("selected_template").asText());
        assertEquals("1.0.0", decision.skillRoute().path("template_version").asText());
        assertEquals("derived_fallback", decision.skillRoute().path("route_source").asText());
    }

    @Test
    void enrichSkillRouteAddsTemplateFactsFromPass1() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "route_mode": "single_skill",
                  "primary_skill": "water_yield",
                  "capability_key": "water_yield",
                  "route_source": "deterministic_phase0",
                  "source": "goal_router",
                  "confidence": 0.9
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "stable_defaults": {
                    "analysis_template": "water_yield_v2"
                  }
                }
                """);

        JsonNode enriched = service.enrichSkillRoute(skillRoute, pass1Result);

        assertEquals("water_yield", enriched.path("capability_key").asText());
        assertEquals("water_yield", enriched.path("primary_skill").asText());
        assertEquals("water_yield_v2", enriched.path("selected_template").asText());
        assertEquals("1.0.0", enriched.path("template_version").asText());
        assertEquals("goal_router", enriched.path("source").asText());
    }

    @Test
    void enrichGoalParseCanonicalizesAnalysisKindFromPass1() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode goalParse = objectMapper.readTree("""
                {
                  "goal_type": "generic_analysis_request",
                  "user_query": "run water yield with precipitation",
                  "analysis_kind": "generic_analysis",
                  "intent_mode": "deterministic_phase0",
                  "source": "goal_router",
                  "entities": ["precipitation"]
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0"
                }
                """);

        JsonNode enriched = service.enrichGoalParse(goalParse, pass1Result);

        assertEquals("water_yield_analysis", enriched.path("goal_type").asText());
        assertEquals("water_yield", enriched.path("analysis_kind").asText());
        assertEquals("deterministic_phase0", enriched.path("intent_mode").asText());
        assertEquals("goal_router", enriched.path("source").asText());
        assertEquals("run water yield with precipitation", enriched.path("user_query").asText());
    }
}
