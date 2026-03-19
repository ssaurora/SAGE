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

    public RepairDecision decide(JsonNode validationSummary, List<TaskAttachment> attachments) {
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
                requiredActions.add(objectMapper.createObjectNode()
                        .put("action_type", "upload")
                        .put("key", "upload_" + roleName)
                        .put("label", "Upload " + roleName)
                        .put("required", true));
            }
        }

        for (JsonNode paramNode : missingParams) {
            String paramName = paramNode.asText("");
            if (paramName.isBlank()) {
                continue;
            }
            requiredActions.add(objectMapper.createObjectNode()
                    .put("action_type", "override")
                    .put("key", "override_" + paramName)
                    .put("label", "Provide " + paramName)
                    .put("required", true));
        }

        for (JsonNode bindingNode : invalidBindings) {
            String roleName = bindingNode.asText("");
            if (roleName.isBlank()) {
                continue;
            }
            requiredActions.add(objectMapper.createObjectNode()
                    .put("action_type", "override")
                    .put("key", "repair_" + roleName)
                    .put("label", "Repair binding for " + roleName)
                    .put("required", true));
        }

        boolean canResume = requiredActions.isEmpty();
        ObjectNode waitingContext = objectMapper.createObjectNode();
        waitingContext.put("waiting_reason_type", resolveWaitingReason(unresolvedMissingRoles, missingParams, invalidBindings));
        waitingContext.set("missing_slots", buildMissingSlots(unresolvedMissingRoles));
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

    private ArrayNode buildMissingSlots(ArrayNode missingRoles) {
        ArrayNode missingSlots = objectMapper.createArrayNode();
        for (JsonNode roleNode : missingRoles) {
            String roleName = roleNode.asText("");
            if (roleName.isBlank()) {
                continue;
            }
            ObjectNode slot = objectMapper.createObjectNode();
            slot.put("slot_name", roleName);
            slot.put("expected_type", "unknown");
            slot.put("required", true);
            missingSlots.add(slot);
        }
        return missingSlots;
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
