package com.sage.backend.task;

import com.sage.backend.model.TaskAttachment;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.task.dto.ResumeTaskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinReadyEvaluatorTest {

    @Test
    void rejectsRequiredUploadWhenAttachmentMetadataIsIncomplete() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(missingSlot("precipitation")),
                List.of(requiredAction("upload", "upload_precipitation"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();

        TaskAttachment attachment = readyAttachment("precipitation");
        attachment.setStoredPath(null);

        assertFalse(MinReadyEvaluator.isReady(waitingContext, List.of(attachment), request));
    }

    @Test
    void acceptsRequiredUploadWhenAttachmentMetadataIsVisible() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(missingSlot("precipitation")),
                List.of(requiredAction("upload", "upload_precipitation"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();

        assertTrue(MinReadyEvaluator.isReady(waitingContext, List.of(readyAttachment("precipitation")), request));
    }

    @Test
    void rejectsBlacklistedCatalogFactForRequiredUpload() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(missingSlot("precipitation")),
                List.of(requiredAction("upload", "upload_precipitation"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();

        TaskAttachment attachment = readyAttachment("precipitation");
        attachment.setAssignmentStatus("BLACKLISTED");

        assertFalse(MinReadyEvaluator.isReady(waitingContext, List.of(attachment), request));
    }

    @Test
    void acceptsSlotOverrideAsSatisfiedMissingInput() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(missingSlot("precipitation")),
                List.of(requiredAction("upload", "upload_precipitation"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setSlotOverrides(Map.of("precipitation", "uploaded_raster_slot"));

        assertTrue(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void acceptsArgOverrideForMissingParam() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("override", "override_eto_value"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setArgsOverrides(Map.of("eto_value", 1.25));

        assertTrue(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void handlesMixedAttachmentAndOverrideInputs() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(missingSlot("precipitation")),
                List.of(
                        requiredAction("upload", "upload_precipitation"),
                        requiredAction("override", "override_results_suffix")
                )
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setArgsOverrides(Map.of("results_suffix", "repair"));

        assertTrue(MinReadyEvaluator.isReady(
                waitingContext,
                List.of(readyAttachment("precipitation")),
                request
        ));
    }

    @Test
    void acceptsClarifyActionWhenUserNoteIsPresent() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("clarify", "clarify_intent"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setUserNote("Run the real annual water yield case for gura.");

        assertTrue(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void rejectsClarifyActionWhenUserNoteIsMissing() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("clarify", "clarify_intent"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();

        assertFalse(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void acceptsClarifyCaseSelectionWhenCaseIdOverrideIsPresent() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("clarify", "clarify_case_selection"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setArgsOverrides(Map.of("case_id", "annual_water_yield_gura"));

        assertTrue(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void rejectsClarifyCaseSelectionWhenCaseIdOverrideIsMissing() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("clarify", "clarify_case_selection"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setUserNote("choose gura");

        assertFalse(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    @Test
    void rejectsUnknownRequiredActionTypes() {
        RepairProposalRequest.WaitingContext waitingContext = waitingContext(
                List.of(),
                List.of(requiredAction("manual_review", "review_precipitation"))
        );
        ResumeTaskRequest request = new ResumeTaskRequest();

        assertFalse(MinReadyEvaluator.isReady(waitingContext, List.of(), request));
    }

    private RepairProposalRequest.WaitingContext waitingContext(
            List<RepairProposalRequest.MissingSlot> missingSlots,
            List<RepairProposalRequest.RequiredUserAction> actions
    ) {
        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setMissingSlots(missingSlots);
        waitingContext.setRequiredUserActions(actions);
        waitingContext.setCanResume(false);
        return waitingContext;
    }

    private RepairProposalRequest.MissingSlot missingSlot(String slotName) {
        RepairProposalRequest.MissingSlot slot = new RepairProposalRequest.MissingSlot();
        slot.setSlotName(slotName);
        slot.setRequired(true);
        return slot;
    }

    private RepairProposalRequest.RequiredUserAction requiredAction(String actionType, String key) {
        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType(actionType);
        action.setKey(key);
        action.setRequired(true);
        return action;
    }

    private TaskAttachment readyAttachment(String logicalSlot) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setId("att_ready");
        attachment.setLogicalSlot(logicalSlot);
        attachment.setFileName(logicalSlot + ".tif");
        attachment.setSizeBytes(1024L);
        attachment.setStoredPath("E:/tmp/" + logicalSlot + ".tif");
        attachment.setChecksum("abc123");
        return attachment;
    }
}
