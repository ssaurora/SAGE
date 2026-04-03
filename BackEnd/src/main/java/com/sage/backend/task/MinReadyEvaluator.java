package com.sage.backend.task;

import com.sage.backend.model.TaskAttachment;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.task.dto.ResumeTaskRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MinReadyEvaluator {

    private MinReadyEvaluator() {
    }

    static boolean isReady(
            RepairProposalRequest.WaitingContext waitingContext,
            List<TaskAttachment> attachments,
            ResumeTaskRequest request
    ) {
        if (waitingContext == null) {
            return false;
        }
        if (Boolean.TRUE.equals(waitingContext.getCanResume())
                && isEmpty(waitingContext.getMissingSlots())
                && isEmpty(waitingContext.getRequiredUserActions())) {
            return true;
        }
        if (request == null) {
            return false;
        }

        Set<String> readySlots = resolveReadySlots(attachments);
        Map<String, Object> slotOverrides = request.getSlotOverrides() == null ? Map.of() : request.getSlotOverrides();
        Map<String, Object> argOverrides = request.getArgsOverrides() == null ? Map.of() : request.getArgsOverrides();

        if (waitingContext.getMissingSlots() != null) {
            for (RepairProposalRequest.MissingSlot missingSlot : waitingContext.getMissingSlots()) {
                if (missingSlot == null || Boolean.FALSE.equals(missingSlot.getRequired())) {
                    continue;
                }
                if (!isSlotSatisfied(missingSlot.getSlotName(), readySlots, slotOverrides)) {
                    return false;
                }
            }
        }

        if (waitingContext.getRequiredUserActions() != null) {
            for (RepairProposalRequest.RequiredUserAction action : waitingContext.getRequiredUserActions()) {
                if (action == null || Boolean.FALSE.equals(action.getRequired())) {
                    continue;
                }
                String actionType = safeString(action.getActionType());
                if ("upload".equalsIgnoreCase(actionType)) {
                    if (!isSlotSatisfied(stripPrefix(action.getKey(), "upload_"), readySlots, slotOverrides)) {
                        return false;
                    }
                    continue;
                }
                if ("override".equalsIgnoreCase(actionType)) {
                    if (!isOverrideSatisfied(stripPrefix(action.getKey(), "override_"), slotOverrides, argOverrides)) {
                        return false;
                    }
                    continue;
                }
                if ("clarify".equalsIgnoreCase(actionType)) {
                    String actionKey = safeString(action.getKey());
                    if ("clarify_case_selection".equalsIgnoreCase(actionKey)) {
                        if (!hasAcceptedOverride(argOverrides, "case_id")) {
                            return false;
                        }
                    } else if (request.getUserNote() == null || request.getUserNote().isBlank()) {
                        return false;
                    }
                    continue;
                }
                return false;
            }
        }

        return true;
    }

    private static Set<String> resolveReadySlots(List<TaskAttachment> attachments) {
        Set<String> readySlots = new HashSet<>();
        for (Map<String, Object> fact : AttachmentCatalogProjector.project(attachments)) {
            if (!isUsableCatalogFact(fact)) {
                continue;
            }
            Object roleCandidates = fact.get("logical_role_candidates");
            if (!(roleCandidates instanceof List<?> candidateList)) {
                continue;
            }
            for (Object candidate : candidateList) {
                String normalized = safeString(candidate == null ? null : candidate.toString());
                if (!normalized.isBlank()) {
                    readySlots.add(normalized);
                }
            }
        }
        return readySlots;
    }

    private static boolean isUsableCatalogFact(Map<String, Object> fact) {
        if (fact == null || Boolean.TRUE.equals(fact.get("blacklist_flag"))) {
            return false;
        }
        return "READY".equalsIgnoreCase(safeString(stringValue(fact.get("availability_status"))));
    }

    private static boolean isSlotSatisfied(String slotName, Set<String> readySlots, Map<String, Object> slotOverrides) {
        String normalizedSlot = safeString(slotName);
        return !normalizedSlot.isBlank()
                && (readySlots.contains(normalizedSlot) || hasAcceptedOverride(slotOverrides, normalizedSlot));
    }

    private static boolean isOverrideSatisfied(
            String overrideKey,
            Map<String, Object> slotOverrides,
            Map<String, Object> argOverrides
    ) {
        String normalizedKey = safeString(overrideKey);
        return !normalizedKey.isBlank()
                && (hasAcceptedOverride(argOverrides, normalizedKey) || hasAcceptedOverride(slotOverrides, normalizedKey));
    }

    private static boolean hasAcceptedOverride(Map<String, Object> overrides, String key) {
        if (overrides == null || !overrides.containsKey(key)) {
            return false;
        }
        Object value = overrides.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private static String stripPrefix(String value, String prefix) {
        String normalized = safeString(value);
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private static String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
