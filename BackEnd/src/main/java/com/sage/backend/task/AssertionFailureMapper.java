package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.planning.Pass1FactHelper;
import com.sage.backend.repair.RepairDecision;
import com.sage.backend.repair.dto.RepairProposalRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AssertionFailureMapper {

    public RepairDecision map(JsonNode failureSummary, JsonNode pass1Result, List<TaskAttachment> attachments) {
        if (failureSummary == null || failureSummary.isNull() || failureSummary.isMissingNode()) {
            return null;
        }
        String failureCode = failureSummary.path("failure_code").asText("");
        if (!"ASSERTION_FAILED".equalsIgnoreCase(failureCode)) {
            return null;
        }

        boolean repairable = failureSummary.path("repairable").asBoolean(false);
        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setCanResume(Boolean.FALSE);

        if (!repairable) {
            waitingContext.setWaitingReasonType("ASSERTION_FAILED");
            waitingContext.setResumeHint("Runtime assertion failed and cannot be resumed automatically.");
            return new RepairDecision("FATAL", "FAILED", waitingContext);
        }

        String roleName = resolveRoleName(failureSummary, pass1Result);
        String targetKey = resolveTargetKey(failureSummary);
        String assertionId = failureSummary.path("assertion_id").asText("");
        Set<String> assignedSlots = resolveAssignedSlots(attachments);

        List<RepairProposalRequest.RequiredUserAction> requiredActions = new ArrayList<>();
        List<RepairProposalRequest.MissingSlot> missingSlots = new ArrayList<>();
        List<String> invalidBindings = new ArrayList<>();

        if (!roleName.isBlank()) {
            String actionType = resolveActionType(assertionId, roleName, pass1Result);
            boolean alreadyAssigned = assignedSlots.contains(roleName);
            if ("override".equalsIgnoreCase(actionType)) {
                requiredActions.add(buildFallbackAction("override", "override_" + roleName, "Repair binding for " + roleName));
                invalidBindings.add(roleName);
            } else if (!alreadyAssigned) {
                requiredActions.add(buildAction(Pass1FactHelper.resolveRepairAction(pass1Result, roleName, "upload")));
                missingSlots.add(buildMissingSlot(roleName, pass1Result));
            }
        } else if (!targetKey.isBlank()) {
            requiredActions.add(buildFallbackAction("override", "override_" + targetKey, "Provide " + targetKey));
        } else {
            requiredActions.add(buildFallbackAction("override", "repair_assertion", "Repair failed runtime assertion"));
        }

        boolean canResume = requiredActions.isEmpty();
        waitingContext.setMissingSlots(missingSlots);
        waitingContext.setInvalidBindings(invalidBindings);
        waitingContext.setRequiredUserActions(requiredActions);
        waitingContext.setCanResume(canResume);
        waitingContext.setWaitingReasonType(resolveWaitingReason(missingSlots, invalidBindings, requiredActions, canResume));
        waitingContext.setResumeHint(
                canResume
                        ? "Runtime assertion is repairable. You can resume now."
                        : "Complete required actions before resuming."
        );
        return new RepairDecision("RECOVERABLE", "WAITING_USER", waitingContext);
    }

    private Set<String> resolveAssignedSlots(List<TaskAttachment> attachments) {
        Set<String> assignedSlots = new HashSet<>();
        if (attachments == null) {
            return assignedSlots;
        }
        for (TaskAttachment attachment : attachments) {
            if (attachment == null || attachment.getLogicalSlot() == null || attachment.getLogicalSlot().isBlank()) {
                continue;
            }
            assignedSlots.add(attachment.getLogicalSlot());
        }
        return assignedSlots;
    }

    private String resolveRoleName(JsonNode failureSummary, JsonNode pass1Result) {
        JsonNode details = failureSummary.path("details");
        String roleName = details.path("role_name").asText("");
        if (!roleName.isBlank()) {
            return roleName;
        }
        String targetKey = resolveTargetKey(failureSummary);
        if (!targetKey.isBlank() && Pass1FactHelper.resolveExpectedSlotType(pass1Result, targetKey) != null
                && !"unknown".equalsIgnoreCase(Pass1FactHelper.resolveExpectedSlotType(pass1Result, targetKey))) {
            return targetKey;
        }
        String assertionId = failureSummary.path("assertion_id").asText("");
        if (assertionId.startsWith("assert_binding_")) {
            return assertionId.substring("assert_binding_".length());
        }
        if (assertionId.startsWith("assert_slot_type_")) {
            return assertionId.substring("assert_slot_type_".length());
        }
        return "";
    }

    private String resolveTargetKey(JsonNode failureSummary) {
        JsonNode details = failureSummary.path("details");
        String targetKey = details.path("target_key").asText("");
        if (!targetKey.isBlank()) {
            return targetKey;
        }
        String assertionId = failureSummary.path("assertion_id").asText("");
        if (assertionId.startsWith("assert_arg_")) {
            return assertionId.substring("assert_arg_".length());
        }
        return "";
    }

    private String resolveActionType(String assertionId, String roleName, JsonNode pass1Result) {
        if (assertionId.startsWith("assert_slot_type_")) {
            return "override";
        }
        Pass1FactHelper.RepairAction repairAction = Pass1FactHelper.resolveRepairAction(pass1Result, roleName, "upload");
        if (repairAction == null || repairAction.actionType().isBlank()) {
            return "upload";
        }
        return repairAction.actionType();
    }

    private RepairProposalRequest.MissingSlot buildMissingSlot(String roleName, JsonNode pass1Result) {
        RepairProposalRequest.MissingSlot slot = new RepairProposalRequest.MissingSlot();
        slot.setSlotName(roleName);
        slot.setExpectedType(Pass1FactHelper.resolveExpectedSlotType(pass1Result, roleName));
        slot.setRequired(Boolean.TRUE);
        return slot;
    }

    private RepairProposalRequest.RequiredUserAction buildAction(Pass1FactHelper.RepairAction repairAction) {
        return buildFallbackAction(repairAction.actionType(), repairAction.actionKey(), repairAction.actionLabel());
    }

    private RepairProposalRequest.RequiredUserAction buildFallbackAction(String actionType, String key, String label) {
        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType(actionType);
        action.setKey(key);
        action.setLabel(label);
        action.setRequired(Boolean.TRUE);
        return action;
    }

    private String resolveWaitingReason(
            List<RepairProposalRequest.MissingSlot> missingSlots,
            List<String> invalidBindings,
            List<RepairProposalRequest.RequiredUserAction> requiredActions,
            boolean canResume
    ) {
        if (!missingSlots.isEmpty()) {
            return "MISSING_INPUT";
        }
        if (!invalidBindings.isEmpty()) {
            return "INVALID_BINDING";
        }
        if (!requiredActions.isEmpty()) {
            return "ASSERTION_REPAIR_REQUIRED";
        }
        return canResume ? "ASSERTION_REPAIR_READY" : "ASSERTION_FAILED";
    }
}
