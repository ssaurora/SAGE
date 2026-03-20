package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class RepairProposalFallbackService {

    private final ObjectMapper objectMapper;

    public RepairProposalFallbackService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(JsonNode waitingContext, String note) {
        ObjectNode proposal = objectMapper.createObjectNode();
        proposal.put("user_facing_reason", "Task requires additional user actions before it can continue.");
        proposal.put(
                "resume_hint",
                waitingContext == null
                        ? "Complete required actions and try resume."
                        : waitingContext.path("resume_hint").asText("Complete required actions and try resume.")
        );

        ArrayNode actions = objectMapper.createArrayNode();
        JsonNode requiredActions = waitingContext == null ? null : waitingContext.path("required_user_actions");
        if (requiredActions != null && requiredActions.isArray()) {
            for (JsonNode action : requiredActions) {
                ObjectNode item = actions.addObject();
                String key = action.path("key").asText("");
                item.put("key", key);
                item.put("message", "Complete action: " + key);
            }
        }
        proposal.set("action_explanations", actions);

        ArrayNode notes = proposal.putArray("notes");
        notes.add(note);
        notes.add("Dispatcher rules remain the source of truth.");
        return proposal;
    }
}
