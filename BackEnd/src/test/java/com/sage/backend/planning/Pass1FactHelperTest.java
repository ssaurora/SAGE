package com.sage.backend.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class Pass1FactHelperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveAnalysisTemplatePrefersStableDefaults() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "selected_template": "water_yield_v1",
                  "stable_defaults": {
                    "analysis_template": "water_yield_v2"
                  }
                }
                """);

        assertEquals("water_yield_v2", Pass1FactHelper.resolveAnalysisTemplate(pass1Result));
    }

    @Test
    void resolveAnalysisTemplateFallsBackToSelectedTemplate() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "selected_template": "water_yield_v1"
                }
                """);

        assertEquals("water_yield_v1", Pass1FactHelper.resolveAnalysisTemplate(pass1Result));
    }

    @Test
    void resolveStableDefaultDoubleUsesPass1StableDefaults() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "stable_defaults": {
                    "root_depth_factor": 0.8,
                    "pawc_factor": "0.85"
                  }
                }
                """);

        assertEquals(0.8, Pass1FactHelper.resolveStableDefaultDouble(pass1Result, "root_depth_factor", 1.0));
        assertEquals(0.85, Pass1FactHelper.resolveStableDefaultDouble(pass1Result, "pawc_factor", 1.0));
        assertEquals(1.0, Pass1FactHelper.resolveStableDefaultDouble(pass1Result, "missing_key", 1.0));
    }

    @Test
    void resolveTemplateVersionUsesPass1FactWhenPresent() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "template_version": "1.0.0"
                }
                """);

        assertEquals("1.0.0", Pass1FactHelper.resolveTemplateVersion(pass1Result));
        assertEquals("", Pass1FactHelper.resolveTemplateVersion(objectMapper.readTree("{}")));
    }

    @Test
    void resolveRuntimeProfileHintUsesCapabilityFactsWhenPresent() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "runtime_profile_hint": "docker-local"
                  }
                }
                """);

        assertEquals("docker-local", Pass1FactHelper.resolveRuntimeProfileHint(pass1Result));
        assertEquals("", Pass1FactHelper.resolveRuntimeProfileHint(objectMapper.readTree("{}")));
    }

    @Test
    void resolveCapabilityKeyPrefersExplicitPass1Fact() throws Exception {
        JsonNode goalParse = objectMapper.readTree("""
                {"goal_type": "generic_analysis_request", "analysis_kind": "generic_analysis"}
                """);
        JsonNode skillRoute = objectMapper.readTree("""
                {"primary_skill": "generic_analysis", "capability_key": "generic_analysis"}
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {"capability_key": "water_yield"}
                """);

        assertEquals("water_yield", Pass1FactHelper.resolveCapabilityKey(goalParse, skillRoute, pass1Result));
    }

    @Test
    void resolveRoleArgMappingReturnsStructuredMapping() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "role_arg_mappings": [
                    {
                      "role_name": "precipitation",
                      "slot_arg_key": "precipitation_slot",
                      "value_arg_key": "precipitation_index",
                      "default_value": 1200.0
                    }
                  ]
                }
                """);

        Pass1FactHelper.RoleArgMapping mapping = Pass1FactHelper.resolveRoleArgMapping(pass1Result, "precipitation");

        assertNotNull(mapping);
        assertEquals("precipitation_slot", mapping.slotArgKey());
        assertEquals("precipitation_index", mapping.valueArgKey());
        assertEquals(1200.0, mapping.defaultValue().asDouble());
        assertNull(Pass1FactHelper.resolveRoleArgMapping(pass1Result, "eto"));
    }

    @Test
    void resolveRepairActionUsesCapabilityHintBeforeFallback() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "repair_hints": [
                      {
                        "role_name": "precipitation",
                        "action_type": "upload",
                        "action_key": "upload_precipitation",
                        "action_label": "Upload precipitation"
                      }
                    ]
                  }
                }
                """);

        Pass1FactHelper.RepairAction action = Pass1FactHelper.resolveRepairAction(pass1Result, "precipitation", "override");

        assertEquals("upload", action.actionType());
        assertEquals("upload_precipitation", action.actionKey());
        assertEquals("Upload precipitation", action.actionLabel());
    }
}
