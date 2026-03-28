package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.planning.Pass1FactHelper;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RepairDispatcherService {

    public RepairDecision decide(PrimitiveValidationResponse validationSummary, JsonNode pass1Result, List<TaskAttachment> attachments) {
        Set<String> assignedSlots = new HashSet<>();
        for (TaskAttachment attachment : attachments) {
            if (attachment.getLogicalSlot() != null && !attachment.getLogicalSlot().isBlank()) {
                assignedSlots.add(attachment.getLogicalSlot());
            }
        }

        List<String> missingRoles = safeList(validationSummary == null ? null : validationSummary.getMissingRoles());
        List<String> missingParams = safeList(validationSummary == null ? null : validationSummary.getMissingParams());
        List<String> invalidBindings = safeList(validationSummary == null ? null : validationSummary.getInvalidBindings());
        String errorCode = validationSummary == null || validationSummary.getErrorCode() == null ? "" : validationSummary.getErrorCode();

        List<RepairProposalRequest.RequiredUserAction> requiredActions = new ArrayList<>();
        List<RepairProposalRequest.MissingSlot> unresolvedMissingSlots = new ArrayList<>();
        for (String roleName : missingRoles) {
            if (roleName.isBlank()) {
                continue;
            }
            if (!assignedSlots.contains(roleName)) {
                unresolvedMissingSlots.add(buildMissingSlot(roleName, pass1Result));
                requiredActions.add(buildRequiredAction(roleName, "upload", pass1Result));
            }
        }

        for (String paramName : missingParams) {
            if (paramName.isBlank()) {
                continue;
            }
            requiredActions.add(buildFallbackAction("override", "override_" + paramName, "Provide " + paramName));
        }

        for (String roleName : invalidBindings) {
            if (roleName.isBlank()) {
                continue;
            }
            requiredActions.add(buildRequiredAction(roleName, "override", pass1Result));
        }

        boolean canResume = requiredActions.isEmpty();
        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setWaitingReasonType(resolveWaitingReason(unresolvedMissingSlots, missingParams, invalidBindings));
        waitingContext.setMissingSlots(unresolvedMissingSlots);
        waitingContext.setInvalidBindings(invalidBindings);
        waitingContext.setRequiredUserActions(requiredActions);
        waitingContext.setResumeHint(canResume ? "All required actions are complete. You can resume now." : "Complete required actions before resuming.");
        waitingContext.setCanResume(canResume);

        String routing = "WAITING_USER";
        String severity = "RECOVERABLE";
        if ("INVALID_BINDING".equals(errorCode)) {
            routing = "FAILED";
            severity = "FATAL";
            waitingContext.setResumeHint("Unrecoverable binding error. Task cannot be resumed.");
            waitingContext.setCanResume(false);
        }
        return new RepairDecision(severity, routing, waitingContext);
    }

    private String resolveWaitingReason(List<RepairProposalRequest.MissingSlot> missingSlots, List<String> missingParams, List<String> invalidBindings) {
        if (!missingSlots.isEmpty()) {
            return "MISSING_INPUT";
        }
        if (!invalidBindings.isEmpty()) {
            return "INVALID_BINDING";
        }
        if (!missingParams.isEmpty()) {
            return "MISSING_PARAM";
        }
        return "REPAIR_REQUIRED";
    }

    private RepairProposalRequest.MissingSlot buildMissingSlot(String roleName, JsonNode pass1Result) {
        RepairProposalRequest.MissingSlot slot = new RepairProposalRequest.MissingSlot();
        slot.setSlotName(roleName);
        slot.setExpectedType(resolveExpectedSlotType(roleName, pass1Result));
        slot.setRequired(true);
        return slot;
    }

    private RepairProposalRequest.RequiredUserAction buildRequiredAction(String roleName, String fallbackActionType, JsonNode pass1Result) {
        Pass1FactHelper.RepairAction repairAction = Pass1FactHelper.resolveRepairAction(pass1Result, roleName, fallbackActionType);
        return buildFallbackAction(repairAction.actionType(), repairAction.actionKey(), repairAction.actionLabel());
    }

    private RepairProposalRequest.RequiredUserAction buildFallbackAction(String actionType, String actionKey, String actionLabel) {
        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType(actionType);
        action.setKey(actionKey);
        action.setLabel(actionLabel);
        action.setRequired(true);
        return action;
    }

    private String resolveExpectedSlotType(String roleName, JsonNode pass1Result) {
        return Pass1FactHelper.resolveExpectedSlotType(pass1Result, roleName);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
