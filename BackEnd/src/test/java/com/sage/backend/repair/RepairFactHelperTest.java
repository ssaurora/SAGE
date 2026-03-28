package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairFactHelperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildRequestNormalizesJsonFactsIntoTypedView() throws Exception {
        JsonNode waitingContext = objectMapper.readTree("""
                {
                  "waiting_reason_type": "MISSING_INPUT",
                  "missing_slots": [{"slot_name": "precipitation", "expected_type": "raster", "required": true}],
                  "required_user_actions": [{"action_type": "upload", "key": "upload_precipitation", "label": "Upload precipitation", "required": true}],
                  "resume_hint": "Complete required actions before resuming.",
                  "can_resume": false
                }
                """);
        JsonNode validationSummary = objectMapper.readTree("""
                {"is_valid": false, "missing_roles": ["precipitation"], "error_code": "MISSING_ROLE"}
                """);
        JsonNode failureSummary = objectMapper.readTree("""
                {"failure_code": "RECOVERABLE_WAITING", "failure_message": "missing input"}
                """);

        RepairProposalRequest request = RepairFactHelper.buildRequest(
                objectMapper,
                waitingContext,
                validationSummary,
                failureSummary,
                "user note"
        );

        assertEquals("MISSING_INPUT", request.getWaitingContext().getWaitingReasonType());
        assertEquals("precipitation", request.getWaitingContext().getMissingSlots().get(0).getSlotName());
        assertFalse(Boolean.TRUE.equals(request.getWaitingContext().getCanResume()));
        assertEquals("MISSING_ROLE", request.getValidationSummary().getErrorCode());
        assertEquals("RECOVERABLE_WAITING", request.getFailureSummary().getFailureCode());
        assertEquals("user note", request.getUserNote());
    }

    @Test
    void toValidationSummaryPreservesStructuredValidationFacts() {
        PrimitiveValidationResponse validationResponse = new PrimitiveValidationResponse();
        validationResponse.setIsValid(false);
        validationResponse.setMissingRoles(java.util.List.of("precipitation"));
        validationResponse.setMissingParams(java.util.List.of("root_depth_factor"));
        validationResponse.setInvalidBindings(java.util.List.of("soil"));
        validationResponse.setErrorCode("INVALID_BINDING");

        RepairProposalRequest.ValidationSummary summary = RepairFactHelper.toValidationSummary(validationResponse);

        assertFalse(Boolean.TRUE.equals(summary.getIsValid()));
        assertEquals(java.util.List.of("precipitation"), summary.getMissingRoles());
        assertEquals(java.util.List.of("root_depth_factor"), summary.getMissingParams());
        assertEquals(java.util.List.of("soil"), summary.getInvalidBindings());
        assertEquals("INVALID_BINDING", summary.getErrorCode());
    }

    @Test
    void readPrimitiveValidationFallsBackToEmptyViewOnBadJson() throws Exception {
        JsonNode invalidShape = objectMapper.readTree("""
                {"is_valid": "not-a-boolean", "missing_roles": "precipitation"}
                """);

        PrimitiveValidationResponse summary = RepairFactHelper.readPrimitiveValidation(objectMapper, invalidShape);

        assertTrue(summary.getMissingRoles() == null || summary.getMissingRoles().isEmpty());
        assertEquals(null, summary.getIsValid());
    }
}
