package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.model.TaskAttachment;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RepairDispatcherService {

    private final ObjectMapper objectMapper;

    public RepairDispatcherService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RepairDecision decide(JsonNode validationSummary, JsonNode pass1Result, List<TaskAttachment> attachments) {
        Set<String> assignedSlots = new HashSet<>();
        for (TaskAttachment attachment : attachments) {
            if (attachment.getLogicalSlot() != null && !attachment.getLogicalSlot().isBlank()) {
                assignedSlots.add(attachment.getLogicalSlot());
            }
        }

        ArrayNode missingRoles = readArray(validationSummary, "missing_roles");
        ArrayNode missingParams = readArray(validationSummary, "missing_params");
        ArrayNode invalidBindings = readArray(validationSummary, "invalid_bindings");
        String errorCode = validationSummary == null ? "" : validationSummary.path("error_code").asText("");

        ArrayNode requiredActions = objectMapper.createArrayNode();
        ArrayNode unresolvedMissingRoles = objectMapper.createArrayNode();
        for (JsonNode roleNode : missingRoles) {
            String roleName = roleNode.asText("");
            if (roleName.isBlank()) {
                continue;
            }
            if (!assignedSlots.contains(roleName)) {
                unresolvedMissingRoles.add(roleName);
                requiredActions.add(buildRequiredAction(roleName, "upload", pass1Result));
            }
        }

        for (JsonNode paramNode : missingParams) {
            String paramName = paramNode.asText("");
            if (paramName.isBlank()) {
                continue;
            }
            requiredActions.add(buildFallbackAction("override", "override_" + paramName, "Provide " + paramName));
        }

        for (JsonNode bindingNode : invalidBindings) {
            String roleName = bindingNode.asText("");
            if (roleName.isBlank()) {
                continue;
            }
            requiredActions.add(buildRequiredAction(roleName, "override", pass1Result));
        }

        boolean canResume = requiredActions.isEmpty();
        ObjectNode waitingContext = objectMapper.createObjectNode();
        waitingContext.put("waiting_reason_type", resolveWaitingReason(unresolvedMissingRoles, missingParams, invalidBindings));
        waitingContext.set("missing_slots", buildMissingSlots(unresolvedMissingRoles, pass1Result));
        waitingContext.set("invalid_bindings", invalidBindings);
        waitingContext.set("required_user_actions", requiredActions);
        waitingContext.put("resume_hint", canResume ? "All required actions are complete. You can resume now." : "Complete required actions before resuming.");
        waitingContext.put("can_resume", canResume);

        String routing = "WAITING_USER";
        String severity = "RECOVERABLE";
        if ("INVALID_BINDING".equals(errorCode)) {
            routing = "FAILED";
            severity = "FATAL";
            waitingContext.put("resume_hint", "Unrecoverable binding error. Task cannot be resumed.");
            waitingContext.put("can_resume", false);
        }
        return new RepairDecision(severity, routing, waitingContext);
    }

    private String resolveWaitingReason(ArrayNode missingRoles, ArrayNode missingParams, ArrayNode invalidBindings) {
        if (!missingRoles.isEmpty()) {
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

    private ArrayNode buildMissingSlots(ArrayNode missingRoles, JsonNode pass1Result) {
        ArrayNode missingSlots = objectMapper.createArrayNode();
        for (JsonNode roleNode : missingRoles) {
            String roleName = roleNode.asText("");
            if (roleName.isBlank()) {
                continue;
            }
            ObjectNode slot = objectMapper.createObjectNode();
            slot.put("slot_name", roleName);
            slot.put("expected_type", resolveExpectedSlotType(roleName, pass1Result));
            slot.put("required", true);
            missingSlots.add(slot);
        }
        return missingSlots;
    }

    private ObjectNode buildRequiredAction(String roleName, String fallbackActionType, JsonNode pass1Result) {
        JsonNode repairHints = pass1Result == null ? null : pass1Result.path("capability_facts").path("repair_hints");
        if (repairHints != null && repairHints.isArray()) {
            for (JsonNode hint : repairHints) {
                if (!roleName.equals(hint.path("role_name").asText(""))) {
                    continue;
                }
                String actionType = hint.path("action_type").asText(fallbackActionType);
                String actionKey = hint.path("action_key").asText(defaultActionKey(actionType, roleName));
                String actionLabel = hint.path("action_label").asText(defaultActionLabel(actionType, roleName));
                return buildFallbackAction(actionType, actionKey, actionLabel);
            }
        }

        return buildFallbackAction(
                fallbackActionType,
                defaultActionKey(fallbackActionType, roleName),
                defaultActionLabel(fallbackActionType, roleName)
        );
    }

    private ObjectNode buildFallbackAction(String actionType, String actionKey, String actionLabel) {
        return objectMapper.createObjectNode()
                .put("action_type", actionType)
                .put("key", actionKey)
                .put("label", actionLabel)
                .put("required", true);
    }

    private String defaultActionKey(String actionType, String roleOrParam) {
        if ("override".equals(actionType)) {
            return "repair_" + roleOrParam;
        }
        return "upload_" + roleOrParam;
    }

    private String defaultActionLabel(String actionType, String roleOrParam) {
        if ("override".equals(actionType)) {
            return "Repair binding for " + roleOrParam;
        }
        return "Upload " + roleOrParam;
    }

    private String resolveExpectedSlotType(String roleName, JsonNode pass1Result) {
        if (pass1Result != null) {
            JsonNode validationHints = pass1Result.path("capability_facts").path("validation_hints");
            if (validationHints.isArray()) {
                for (JsonNode hint : validationHints) {
                    if (roleName.equals(hint.path("role_name").asText(""))) {
                        String expectedSlotType = hint.path("expected_slot_type").asText("");
                        if (!expectedSlotType.isBlank()) {
                            return expectedSlotType;
                        }
                    }
                }
            }

            JsonNode slots = pass1Result.path("slot_schema_view").path("slots");
            if (slots.isArray()) {
                for (JsonNode slot : slots) {
                    String boundRole = slot.path("bound_role").asText("");
                    String slotName = slot.path("slot_name").asText("");
                    if (roleName.equals(boundRole) || roleName.equals(slotName)) {
                        String type = slot.path("type").asText("");
                        if (!type.isBlank()) {
                            return type;
                        }
                    }
                }
            }
        }
        return "unknown";
    }

    private ArrayNode readArray(JsonNode source, String field) {
        ArrayNode empty = objectMapper.createArrayNode();
        if (source == null || source.isMissingNode() || source.isNull()) {
            return empty;
        }
        JsonNode node = source.path(field);
        if (!node.isArray()) {
            return empty;
        }
        ArrayNode copy = objectMapper.createArrayNode();
        node.forEach(copy::add);
        return copy;
    }
}
