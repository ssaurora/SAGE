package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskControlPayloadBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildTaskCreateAuditPayloadOmitsBlankOptionalFields() {
        Map<String, Object> payload = TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                TaskStatus.WAITING_USER.name(),
                false,
                "INCOMPLETE",
                false,
                " ",
                null
        );

        assertEquals(TaskStatus.WAITING_USER.name(), payload.get("state"));
        assertEquals(false, payload.get("validation_is_valid"));
        assertEquals("INCOMPLETE", payload.get("input_chain_status"));
        assertFalse(payload.containsKey("job_id"));
        assertFalse(payload.containsKey("failure_code"));
    }

    @Test
    void buildFatalValidationFailureSummaryPayloadPreservesValidationFacts() {
        PrimitiveValidationResponse response = new PrimitiveValidationResponse();
        response.setErrorCode("MISSING_ROLE");
        response.setMissingRoles(List.of("precipitation"));
        response.setMissingParams(List.of("eto_value"));
        response.setInvalidBindings(List.of("bad_slot"));

        Map<String, Object> payload = TaskControlPayloadBuilder.buildFatalValidationFailureSummaryPayload(
                response,
                "2026-03-27T10:00:00Z"
        );

        assertEquals("FATAL_VALIDATION", payload.get("failure_code"));
        assertEquals("MISSING_ROLE", payload.get("error_code"));
        assertEquals(List.of("precipitation"), payload.get("missing_roles"));
        assertEquals(List.of("eto_value"), payload.get("missing_params"));
        assertEquals(List.of("bad_slot"), payload.get("invalid_bindings"));
        assertEquals("2026-03-27T10:00:00Z", payload.get("created_at"));
    }

    @Test
    void buildPassBCompletionPayloadCountsBindingsAndResumeFlag() throws Exception {
        Map<String, Object> payload = TaskControlPayloadBuilder.buildPassBCompletionPayload(
                objectMapper.readTree("""
                        {
                          "slot_bindings": [
                            {"role_name": "precipitation"},
                            {"role_name": "eto"}
                          ]
                        }
                        """),
                true
        );

        assertEquals(2, payload.get("binding_count"));
        assertEquals(true, payload.get("resume"));
    }

    @Test
    void buildAttachmentUploadedPayloadPreservesSlotFacts() {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setLogicalSlot("precipitation");
        attachment.setAssignmentStatus("ASSIGNED");

        Map<String, Object> payload = TaskControlPayloadBuilder.buildAttachmentUploadedPayload("att_1", attachment);

        assertEquals("att_1", payload.get("attachment_id"));
        assertEquals("precipitation", payload.get("logical_slot"));
        assertEquals("ASSIGNED", payload.get("assignment_status"));
    }

    @Test
    void buildPipelineFailureAuditPayloadPreservesErrorContext() {
        Map<String, Object> payload = TaskControlPayloadBuilder.buildPipelineFailureAuditPayload(
                new IllegalStateException("boom"),
                TaskStatus.PLANNING,
                7,
                "2026-03-27T11:00:00Z"
        );

        assertEquals("IllegalStateException", payload.get("error"));
        assertEquals("boom", payload.get("message"));
        assertEquals(TaskStatus.PLANNING.name(), payload.get("from_state"));
        assertEquals(7, payload.get("expected_state_version"));
        assertEquals("2026-03-27T11:00:00Z", payload.get("at"));
    }
}
