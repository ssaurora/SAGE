package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
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
        service = new RepairDispatcherService();
    }

    @Test
    void decideBuildsWaitingContextFromCombinedFacts() throws Exception {
        PrimitiveValidationResponse validationSummary = new PrimitiveValidationResponse();
        validationSummary.setMissingRoles(List.of("precipitation"));
        validationSummary.setMissingParams(List.of());
        validationSummary.setInvalidBindings(List.of());
        validationSummary.setErrorCode("MISSING_ROLE");
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
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals("raster", decision.waitingContext().getMissingSlots().get(0).getExpectedType());
        assertEquals("upload_precipitation", decision.waitingContext().getRequiredUserActions().get(0).getKey());
        assertEquals("Upload precipitation", decision.waitingContext().getRequiredUserActions().get(0).getLabel());
    }

    @Test
    void decideUsesAttachmentsAsAuthorityFactsDuringRecompute() throws Exception {
        PrimitiveValidationResponse validationSummary = new PrimitiveValidationResponse();
        validationSummary.setMissingRoles(List.of("precipitation"));
        validationSummary.setMissingParams(List.of());
        validationSummary.setInvalidBindings(List.of());
        validationSummary.setErrorCode("MISSING_ROLE");
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

        assertTrue(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals(0, decision.waitingContext().getRequiredUserActions().size());
        assertEquals(0, decision.waitingContext().getMissingSlots().size());
    }

    @Test
    void decideKeepsInvalidBindingFatal() throws Exception {
        PrimitiveValidationResponse validationSummary = new PrimitiveValidationResponse();
        validationSummary.setMissingRoles(List.of());
        validationSummary.setMissingParams(List.of());
        validationSummary.setInvalidBindings(List.of("eto"));
        validationSummary.setErrorCode("INVALID_BINDING");
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
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
    }
}
