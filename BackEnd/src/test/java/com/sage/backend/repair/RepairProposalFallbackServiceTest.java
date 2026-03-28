package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepairProposalFallbackServiceTest {
    private RepairProposalFallbackService service;

    @BeforeEach
    void setUp() {
        service = new RepairProposalFallbackService();
    }

    @Test
    void generateUsesMissingSlotsForUserFacingReasonAndActionLabels() {
        RepairProposalRequest request = new RepairProposalRequest();

        RepairProposalRequest.MissingSlot missingSlot = new RepairProposalRequest.MissingSlot();
        missingSlot.setSlotName("precipitation");
        missingSlot.setExpectedType("raster");
        missingSlot.setRequired(true);

        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType("upload");
        action.setKey("upload_precipitation");
        action.setLabel("Upload precipitation");
        action.setRequired(true);

        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setMissingSlots(List.of(missingSlot));
        waitingContext.setInvalidBindings(List.of());
        waitingContext.setRequiredUserActions(List.of(action));
        waitingContext.setResumeHint("Complete required actions before resuming.");
        waitingContext.setCanResume(false);
        request.setWaitingContext(waitingContext);

        RepairProposalRequest.ValidationSummary validationSummary = new RepairProposalRequest.ValidationSummary();
        validationSummary.setErrorCode("MISSING_ROLE");
        request.setValidationSummary(validationSummary);

        RepairProposalResponse proposal = service.generate(request, "fallback_note");

        assertEquals(
                "Required inputs are still missing: precipitation (raster).",
                proposal.getUserFacingReason()
        );
        assertEquals("Upload precipitation.", proposal.getActionExplanations().get(0).getMessage());
        assertEquals("fallback_note", proposal.getNotes().get(0));
        assertEquals("Latest structured failure code: MISSING_ROLE", proposal.getNotes().get(2));
    }

    @Test
    void generateUsesInvalidBindingsWhenNoMissingSlotsRemain() {
        RepairProposalRequest request = new RepairProposalRequest();

        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType("override");
        action.setKey("repair_eto");
        action.setRequired(true);

        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setMissingSlots(List.of());
        waitingContext.setInvalidBindings(List.of("eto"));
        waitingContext.setRequiredUserActions(List.of(action));
        waitingContext.setResumeHint("Fix invalid bindings before resuming.");
        waitingContext.setCanResume(false);
        request.setWaitingContext(waitingContext);

        RepairProposalRequest.ValidationSummary validationSummary = new RepairProposalRequest.ValidationSummary();
        validationSummary.setErrorCode("INVALID_BINDING");
        request.setValidationSummary(validationSummary);

        RepairProposalResponse proposal = service.generate(request, "fallback_note");

        assertEquals(
                "Some provided bindings are invalid and must be corrected: eto.",
                proposal.getUserFacingReason()
        );
        assertEquals("Complete action: repair_eto", proposal.getActionExplanations().get(0).getMessage());
    }

    @Test
    void generateFallsBackToGenericReasonWhenWaitingContextIsEmpty() {
        RepairProposalResponse proposal = service.generate(new RepairProposalRequest(), "fallback_note");

        assertEquals(
                "Task requires additional user actions before it can continue.",
                proposal.getUserFacingReason()
        );
        assertEquals("Complete required actions and try resume.", proposal.getResumeHint());
    }
}
