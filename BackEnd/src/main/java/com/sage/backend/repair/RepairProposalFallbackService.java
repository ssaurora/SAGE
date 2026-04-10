package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepairProposalFallbackService {

    public RepairProposalResponse generate(RepairProposalRequest request, String note) {
        RepairProposalRequest.ValidationSummary validationSummary = request == null ? null : request.getValidationSummary();
        RepairProposalRequest.FailureSummary failureSummary = request == null ? null : request.getFailureSummary();

        RepairProposalResponse proposal = new RepairProposalResponse();
        proposal.setAvailable(false);
        proposal.setUserFacingReason(buildUserFacingReason(request));
        proposal.setResumeHint(buildResumeHint(request));
        proposal.setActionExplanations(buildActionExplanations(request));

        List<String> notes = new ArrayList<>();
        notes.add(note);
        notes.add("Dispatcher rules remain the source of truth.");
        String failureCode = "COGNITION_UNAVAILABLE";
        String failureMessage = "Repair proposal cognition unavailable.";
        String errorCode = validationSummary == null ? "" : validationSummary.getErrorCode();
        if (errorCode != null && !errorCode.isBlank()) {
            notes.add("Latest structured failure code: " + errorCode);
        } else {
            String latestFailureCode = failureSummary == null ? "" : failureSummary.getFailureCode();
            if (latestFailureCode != null && !latestFailureCode.isBlank()) {
                notes.add("Latest structured failure code: " + latestFailureCode);
            }
        }
        proposal.setNotes(notes);
        proposal.setFailureCode(failureCode);
        proposal.setFailureMessage(failureMessage);
        proposal.setCognitionMetadata(buildCognitionMetadata(failureCode, failureMessage));
        return proposal;
    }

    private Map<String, Object> buildCognitionMetadata(String failureCode, String failureMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "backend_fallback_unavailable");
        metadata.put("provider", "glm");
        metadata.put("model", null);
        metadata.put("prompt_version", "repair_proposal_v1");
        metadata.put("fallback_used", true);
        metadata.put("schema_valid", false);
        metadata.put("response_id", null);
        metadata.put("status", "COGNITION_UNAVAILABLE");
        metadata.put("failure_code", failureCode);
        metadata.put("failure_message", failureMessage);
        return metadata;
    }

    private String buildUserFacingReason(RepairProposalRequest request) {
        RepairProposalRequest.WaitingContext waitingContext = request == null ? null : request.getWaitingContext();
        if (waitingContext == null) {
            return "Task requires additional user actions before it can continue.";
        }
        List<RepairProposalRequest.MissingSlot> missingSlots = waitingContext.getMissingSlots();
        if (missingSlots != null && !missingSlots.isEmpty()) {
            List<String> labels = missingSlots.stream()
                    .map(this::missingSlotLabel)
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            if (!labels.isEmpty()) {
                return "Required inputs are still missing: " + String.join(", ", labels) + ".";
            }
        }
        List<String> invalidBindings = waitingContext.getInvalidBindings();
        if (invalidBindings != null && !invalidBindings.isEmpty()) {
            return "Some provided bindings are invalid and must be corrected: "
                    + String.join(", ", invalidBindings)
                    + ".";
        }
        return "Task requires additional user actions before it can continue.";
    }

    private String missingSlotLabel(RepairProposalRequest.MissingSlot missingSlot) {
        if (missingSlot == null) {
            return "";
        }
        String slotName = missingSlot.getSlotName();
        String expectedType = missingSlot.getExpectedType();
        if (slotName == null || slotName.isBlank()) {
            return "";
        }
        if (expectedType == null || expectedType.isBlank()) {
            return slotName;
        }
        return slotName + " (" + expectedType + ")";
    }

    private String buildResumeHint(RepairProposalRequest request) {
        RepairProposalRequest.WaitingContext waitingContext = request == null ? null : request.getWaitingContext();
        if (waitingContext != null
                && waitingContext.getResumeHint() != null
                && !waitingContext.getResumeHint().isBlank()) {
            return waitingContext.getResumeHint();
        }
        return "Complete required actions and try resume.";
    }

    private List<RepairProposalResponse.ActionExplanation> buildActionExplanations(RepairProposalRequest request) {
        RepairProposalRequest.WaitingContext waitingContext = request == null ? null : request.getWaitingContext();
        if (waitingContext == null
                || waitingContext.getRequiredUserActions() == null
                || waitingContext.getRequiredUserActions().isEmpty()) {
            return List.of();
        }
        return waitingContext.getRequiredUserActions().stream()
                .map(this::buildActionExplanation)
                .toList();
    }

    private RepairProposalResponse.ActionExplanation buildActionExplanation(RepairProposalRequest.RequiredUserAction action) {
        RepairProposalResponse.ActionExplanation explanation = new RepairProposalResponse.ActionExplanation();
        if (action == null) {
            explanation.setKey("");
            explanation.setMessage("Complete required action.");
            return explanation;
        }
        explanation.setKey(action.getKey());
        String label = action.getLabel();
        if (label != null && !label.isBlank()) {
            explanation.setMessage(ensurePeriod(label));
            return explanation;
        }
        String key = action.getKey();
        if (key != null && !key.isBlank()) {
            explanation.setMessage("Complete action: " + key);
            return explanation;
        }
        explanation.setMessage("Complete required action.");
        return explanation;
    }

    private String ensurePeriod(String value) {
        if (value.endsWith(".")) {
            return value;
        }
        return value + ".";
    }
}
