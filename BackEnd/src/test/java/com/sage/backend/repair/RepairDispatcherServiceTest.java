package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.TaskAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairDispatcherServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RepairDispatcherService service;

    @BeforeEach
    void setUp() {
        service = new RepairDispatcherService(objectMapper);
    }

    @Test
    void decideBuildsWaitingContextFromCombinedFacts() throws Exception {
        JsonNode validationSummary = objectMapper.readTree("""
                {
                  "missing_roles": ["precipitation"],
                  "missing_params": [],
                  "invalid_bindings": [],
                  "error_code": "MISSING_ROLE"
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "validation_hints": [
                      {"role_name": "precipitation", "expected_slot_type": "raster"}
                    ],
                    "repair_hints": [
                      {
                        "role_name": "precipitation",
                        "action_type": "upload",
                        "action_key": "upload_precipitation",
                        "action_label": "Upload precipitation"
                      }
                    ]
                  },
                  "slot_schema_view": {
                    "slots": [
                      {"slot_name": "precipitation", "type": "raster", "bound_role": "precipitation"}
                    ]
                  }
                }
                """);

        RepairDecision decision = service.decide(validationSummary, pass1Result, List.of());

        assertEquals("WAITING_USER", decision.routing());
        assertEquals("RECOVERABLE", decision.severity());
        assertFalse(decision.waitingContext().path("can_resume").asBoolean());
        assertEquals("raster", decision.waitingContext().path("missing_slots").get(0).path("expected_type").asText());
        assertEquals("upload_precipitation", decision.waitingContext().path("required_user_actions").get(0).path("key").asText());
        assertEquals("Upload precipitation", decision.waitingContext().path("required_user_actions").get(0).path("label").asText());
    }

    @Test
    void decideUsesAttachmentsAsAuthorityFactsDuringRecompute() throws Exception {
        JsonNode validationSummary = objectMapper.readTree("""
                {
                  "missing_roles": ["precipitation"],
                  "missing_params": [],
                  "invalid_bindings": [],
                  "error_code": "MISSING_ROLE"
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "validation_hints": [
                      {"role_name": "precipitation", "expected_slot_type": "raster"}
                    ],
                    "repair_hints": [
                      {
                        "role_name": "precipitation",
                        "action_type": "upload",
                        "action_key": "upload_precipitation",
                        "action_label": "Upload precipitation"
                      }
                    ]
                  },
                  "slot_schema_view": {
                    "slots": [
                      {"slot_name": "precipitation", "type": "raster", "bound_role": "precipitation"}
                    ]
                  }
                }
                """);

        TaskAttachment attachment = new TaskAttachment();
        attachment.setLogicalSlot("precipitation");

        RepairDecision decision = service.decide(validationSummary, pass1Result, List.of(attachment));

        assertTrue(decision.waitingContext().path("can_resume").asBoolean());
        assertEquals(0, decision.waitingContext().path("required_user_actions").size());
        assertEquals(0, decision.waitingContext().path("missing_slots").size());
    }

    @Test
    void decideKeepsInvalidBindingFatal() throws Exception {
        JsonNode validationSummary = objectMapper.readTree("""
                {
                  "missing_roles": [],
                  "missing_params": [],
                  "invalid_bindings": ["eto"],
                  "error_code": "INVALID_BINDING"
                }
                """);
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_facts": {
                    "validation_hints": [],
                    "repair_hints": []
                  },
                  "slot_schema_view": {
                    "slots": [
                      {"slot_name": "eto", "type": "raster", "bound_role": "eto"}
                    ]
                  }
                }
                """);

        RepairDecision decision = service.decide(validationSummary, pass1Result, List.of());

        assertEquals("FAILED", decision.routing());
        assertEquals("FATAL", decision.severity());
        assertFalse(decision.waitingContext().path("can_resume").asBoolean());
    }
}
