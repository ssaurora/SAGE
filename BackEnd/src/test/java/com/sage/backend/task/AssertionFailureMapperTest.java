package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.repair.RepairDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionFailureMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AssertionFailureMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AssertionFailureMapper();
    }

    @Test
    void mapBuildsUploadRepairForMissingRepairableBinding() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_binding_precipitation",
                  "repairable": true,
                  "details": {
                    "role_name": "precipitation"
                  }
                }
                """);

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of());

        assertEquals("WAITING_USER", decision.routing());
        assertEquals("RECOVERABLE", decision.severity());
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals("MISSING_INPUT", decision.waitingContext().getWaitingReasonType());
        assertEquals("precipitation", decision.waitingContext().getMissingSlots().get(0).getSlotName());
        assertEquals("upload_precipitation", decision.waitingContext().getRequiredUserActions().get(0).getKey());
    }

    @Test
    void mapAllowsImmediateResumeWhenRepairableBindingAlreadyHasAttachment() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_binding_precipitation",
                  "repairable": true,
                  "details": {
                    "role_name": "precipitation"
                  }
                }
                """);
        TaskAttachment attachment = new TaskAttachment();
        attachment.setLogicalSlot("precipitation");

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of(attachment));

        assertEquals("WAITING_USER", decision.routing());
        assertTrue(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals(0, decision.waitingContext().getRequiredUserActions().size());
        assertEquals(0, decision.waitingContext().getMissingSlots().size());
    }

    @Test
    void mapKeepsSlotTypeAssertionInOverrideFlow() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_slot_type_precipitation",
                  "repairable": true,
                  "details": {
                    "role_name": "precipitation",
                    "expected_slot_type": "raster"
                  }
                }
                """);
        TaskAttachment attachment = new TaskAttachment();
        attachment.setLogicalSlot("precipitation");

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of(attachment));

        assertEquals("WAITING_USER", decision.routing());
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals("INVALID_BINDING", decision.waitingContext().getWaitingReasonType());
        assertEquals(List.of("precipitation"), decision.waitingContext().getInvalidBindings());
        assertEquals("override", decision.waitingContext().getRequiredUserActions().get(0).getActionType());
    }

    @Test
    void mapKeepsNonRepairableAssertionFatal() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_workspace_dir",
                  "repairable": false,
                  "details": {
                    "target_key": "workspace_dir"
                  }
                }
                """);

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of());

        assertEquals("FAILED", decision.routing());
        assertEquals("FATAL", decision.severity());
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
    }

    @Test
    void mapBuildsOverrideRepairForMissingRuntimeArg() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_arg_precipitation_slot",
                  "repairable": true,
                  "details": {
                    "target_key": "precipitation_slot"
                  }
                }
                """);

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of());

        assertEquals("WAITING_USER", decision.routing());
        assertEquals("RECOVERABLE", decision.severity());
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals("ASSERTION_REPAIR_REQUIRED", decision.waitingContext().getWaitingReasonType());
        assertEquals("override", decision.waitingContext().getRequiredUserActions().get(0).getActionType());
        assertEquals("override_precipitation_slot", decision.waitingContext().getRequiredUserActions().get(0).getKey());
    }

    @Test
    void mapPrefersRoleRepairHintWhenRuntimeArgFailureIncludesRoleName() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "ASSERTION_FAILED",
                  "assertion_id": "assert_arg_precipitation_slot",
                  "repairable": true,
                  "details": {
                    "role_name": "precipitation",
                    "slot_arg_key": "precipitation_slot",
                    "target_key": "precipitation_slot"
                  }
                }
                """);

        RepairDecision decision = mapper.map(failureSummary, samplePass1Result(), List.of());

        assertEquals("WAITING_USER", decision.routing());
        assertEquals("RECOVERABLE", decision.severity());
        assertFalse(Boolean.TRUE.equals(decision.waitingContext().getCanResume()));
        assertEquals("MISSING_INPUT", decision.waitingContext().getWaitingReasonType());
        assertEquals("precipitation", decision.waitingContext().getMissingSlots().get(0).getSlotName());
        assertEquals("upload", decision.waitingContext().getRequiredUserActions().get(0).getActionType());
        assertEquals("upload_precipitation", decision.waitingContext().getRequiredUserActions().get(0).getKey());
    }

    private JsonNode samplePass1Result() throws Exception {
        return objectMapper.readTree("""
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
                  "role_arg_mappings": [
                    {
                      "role_name": "precipitation",
                      "slot_arg_key": "precipitation_slot"
                    }
                  ],
                  "slot_schema_view": {
                    "slots": [
                      {"slot_name": "precipitation", "type": "raster", "bound_role": "precipitation"}
                    ]
                  }
                }
                """);
    }
}
