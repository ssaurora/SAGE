package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepairProposalServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateBuildsTypedRequestAndFallsBackOnInvalidSchema() throws Exception {
        RepairProposalClient client = mock(RepairProposalClient.class);
        RepairProposalFallbackService fallbackService = mock(RepairProposalFallbackService.class);
        RepairProposalService service = new RepairProposalService(client, fallbackService);

        RepairProposalResponse invalid = new RepairProposalResponse();
        invalid.setUserFacingReason("");
        invalid.setResumeHint("resume");
        when(client.generate(any())).thenReturn(invalid);

        RepairProposalResponse fallback = new RepairProposalResponse();
        fallback.setUserFacingReason("fallback reason");
        fallback.setResumeHint("fallback hint");
        when(fallbackService.generate(any(), eq("Cognition output schema invalid"))).thenReturn(fallback);

        JsonNode waitingContext = objectMapper.readTree("""
                {
                  "missing_slots": [{"slot_name": "precipitation", "expected_type": "raster", "required": true}],
                  "required_user_actions": [{"action_type": "upload", "key": "upload_precipitation", "label": "Upload precipitation", "required": true}],
                  "resume_hint": "Complete required actions before resuming.",
                  "can_resume": false
                }
                """);
        JsonNode validationSummary = objectMapper.readTree("""
                {"error_code": "MISSING_ROLE"}
                """);

        RepairProposalResponse response = service.generate(
                RepairFactHelper.buildRequest(objectMapper, waitingContext, validationSummary, null, "user note")
        );

        ArgumentCaptor<RepairProposalRequest> requestCaptor = ArgumentCaptor.forClass(RepairProposalRequest.class);
        verify(client).generate(requestCaptor.capture());
        RepairProposalRequest request = requestCaptor.getValue();
        assertEquals("precipitation", request.getWaitingContext().getMissingSlots().get(0).getSlotName());
        assertEquals("upload_precipitation", request.getWaitingContext().getRequiredUserActions().get(0).getKey());
        assertEquals("MISSING_ROLE", request.getValidationSummary().getErrorCode());
        assertEquals("user note", request.getUserNote());

        verify(fallbackService).generate(any(), eq("Cognition output schema invalid"));
        assertEquals("fallback reason", response.getUserFacingReason());
        assertEquals("fallback hint", response.getResumeHint());
    }

    @Test
    void generateBuildsDefaultTypedViewsWhenClientThrows() {
        RepairProposalClient client = mock(RepairProposalClient.class);
        RepairProposalFallbackService fallbackService = mock(RepairProposalFallbackService.class);
        RepairProposalService service = new RepairProposalService(client, fallbackService);

        when(client.generate(any())).thenThrow(new IllegalStateException("boom"));

        RepairProposalResponse fallback = new RepairProposalResponse();
        fallback.setUserFacingReason("fallback");
        fallback.setResumeHint("retry");
        when(fallbackService.generate(any(), eq("Cognition request failed"))).thenReturn(fallback);

        RepairProposalResponse response = service.generate(new RepairProposalRequest());

        ArgumentCaptor<RepairProposalRequest> requestCaptor = ArgumentCaptor.forClass(RepairProposalRequest.class);
        verify(fallbackService).generate(requestCaptor.capture(), eq("Cognition request failed"));
        RepairProposalRequest request = requestCaptor.getValue();
        assertTrue(request.getWaitingContext().getMissingSlots().isEmpty());
        assertTrue(request.getValidationSummary().getMissingRoles().isEmpty());
        assertEquals("", request.getFailureSummary().getFailureCode());
        assertFalse(Boolean.TRUE.equals(request.getWaitingContext().getCanResume()));
        assertEquals("", request.getUserNote());
        assertEquals("fallback", response.getUserFacingReason());
    }

    @Test
    void generateAcceptsTypedViewsWithoutJsonNormalization() {
        RepairProposalClient client = mock(RepairProposalClient.class);
        RepairProposalFallbackService fallbackService = mock(RepairProposalFallbackService.class);
        RepairProposalService service = new RepairProposalService(client, fallbackService);

        RepairProposalResponse success = new RepairProposalResponse();
        success.setAvailable(true);
        success.setUserFacingReason("typed reason");
        success.setResumeHint("typed hint");
        success.setCognitionMetadata(Map.of("source", "mock"));
        when(client.generate(any())).thenReturn(success);

        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setResumeHint("Complete required actions before resuming.");
        waitingContext.setCanResume(false);

        RepairProposalRequest.ValidationSummary validationSummary = new RepairProposalRequest.ValidationSummary();
        validationSummary.setErrorCode("MISSING_ROLE");

        RepairProposalRequest.FailureSummary failureSummary = new RepairProposalRequest.FailureSummary();
        failureSummary.setFailureCode("RECOVERABLE_WAITING");

        RepairProposalResponse response = service.generate(waitingContext, validationSummary, failureSummary, "typed note");

        ArgumentCaptor<RepairProposalRequest> requestCaptor = ArgumentCaptor.forClass(RepairProposalRequest.class);
        verify(client).generate(requestCaptor.capture());
        RepairProposalRequest request = requestCaptor.getValue();
        assertEquals("MISSING_ROLE", request.getValidationSummary().getErrorCode());
        assertEquals("RECOVERABLE_WAITING", request.getFailureSummary().getFailureCode());
        assertEquals("typed note", request.getUserNote());
        assertEquals("typed reason", response.getUserFacingReason());
    }
}
