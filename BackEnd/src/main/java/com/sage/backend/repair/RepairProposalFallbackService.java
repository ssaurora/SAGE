package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RepairProposalFallbackService {

    public RepairProposalResponse generate(RepairProposalRequest request, String note) {
        RepairProposalRequest.WaitingContext waitingContext = request == null ? null : request.getWaitingContext();
        RepairProposalRequest.ValidationSummary validationSummary = request == null ? null : request.getValidationSummary();
        RepairProposalRequest.FailureSummary failureSummary = request == null ? null : request.getFailureSummary();

        RepairProposalResponse proposal = new RepairProposalResponse();
        proposal.setUserFacingReason(buildUserFacingReason(waitingContext));
        proposal.setResumeHint(
                waitingContext == null || waitingContext.getResumeHint() == null || waitingContext.getResumeHint().isBlank()
                        ? "Complete required actions and try resume."
                        : waitingContext.getResumeHint()
        );

        List<RepairProposalResponse.ActionExplanation> actions = new ArrayList<>();
        List<RepairProposalRequest.RequiredUserAction> requiredActions =
                waitingContext == null ? List.of() : waitingContext.getRequiredUserActions();
        for (RepairProposalRequest.RequiredUserAction action : requiredActions) {
            RepairProposalResponse.ActionExplanation item = new RepairProposalResponse.ActionExplanation();
            item.setKey(action.getKey());
            item.setMessage(buildActionMessage(action));
            actions.add(item);
        }
        proposal.setActionExplanations(actions);

        List<String> notes = new ArrayList<>();
        notes.add(note);
        notes.add("Dispatcher rules remain the source of truth.");
        String errorCode = validationSummary == null ? "" : validationSummary.getErrorCode();
        if (errorCode != null && !errorCode.isBlank()) {
            notes.add("Latest structured failure code: " + errorCode);
        } else {
            String failureCode = failureSummary == null ? "" : failureSummary.getFailureCode();
            if (failureCode != null && !failureCode.isBlank()) {
                notes.add("Latest structured failure code: " + failureCode);
            }
        }
        proposal.setNotes(notes);
        return proposal;
    }

    private String buildUserFacingReason(RepairProposalRequest.WaitingContext waitingContext) {
        if (waitingContext == null) {
            return "Task requires additional user actions before it can continue.";
        }

        List<RepairProposalRequest.MissingSlot> missingSlots = waitingContext.getMissingSlots();
        if (missingSlots != null && !missingSlots.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (RepairProposalRequest.MissingSlot slot : missingSlots) {
                String slotName = slot.getSlotName();
                if (slotName.isBlank()) {
                    continue;
                }
                String expectedType = slot.getExpectedType();
                if (!expectedType.isBlank()) {
                    parts.add(slotName + " (" + expectedType + ")");
                } else {
                    parts.add(slotName);
                }
            }
            if (!parts.isEmpty()) {
                return "Required inputs are still missing: " + String.join(", ", parts) + ".";
            }
        }

        List<String> invalidBindings = waitingContext.getInvalidBindings();
        if (invalidBindings != null && !invalidBindings.isEmpty()) {
            List<String> parts = new ArrayList<>();
            invalidBindings.forEach(value -> {
                if (!value.isBlank()) {
                    parts.add(value);
                }
            });
            if (!parts.isEmpty()) {
                return "Some provided bindings are invalid and must be corrected: " + String.join(", ", parts) + ".";
            }
            return "Some provided bindings are invalid and must be corrected.";
        }

        List<RepairProposalRequest.RequiredUserAction> requiredActions = waitingContext.getRequiredUserActions();
        if (requiredActions != null && !requiredActions.isEmpty()) {
            return "Additional required actions must be completed before the task can continue.";
        }

        return "Task requires additional user actions before it can continue.";
    }

    private String buildActionMessage(RepairProposalRequest.RequiredUserAction action) {
        String label = action.getLabel();
        if (!label.isBlank()) {
            return label + ".";
        }
        String key = action.getKey();
        if (!key.isBlank()) {
            return "Complete action: " + key;
        }
        return "Complete the required action.";
    }
}
