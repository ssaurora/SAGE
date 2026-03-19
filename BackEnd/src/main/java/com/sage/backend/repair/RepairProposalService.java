package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RepairProposalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairProposalService.class);

    private final ObjectMapper objectMapper;
    private final boolean llmEnabled;
    private final String llmApiKey;
    private final String llmModel;
    private final Duration timeout;

    public RepairProposalService(
            ObjectMapper objectMapper,
            @Value("${sage.repair-llm.enabled:true}") boolean llmEnabled,
            @Value("${sage.repair-llm.api-key:}") String llmApiKey,
            @Value("${sage.repair-llm.model:gpt-4o-mini}") String llmModel,
            @Value("${sage.repair-llm.timeout-ms:3000}") long timeoutMs
    ) {
        this.objectMapper = objectMapper;
        this.llmEnabled = llmEnabled;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.timeout = Duration.ofMillis(Math.max(500, timeoutMs));
    }

    public ObjectNode generate(JsonNode waitingContext, JsonNode validationSummary, JsonNode failureSummary, String userNote) {
        if (!llmEnabled || llmApiKey == null || llmApiKey.isBlank()) {
            return fallback(waitingContext, "LLM disabled or key missing");
        }

        try {
            ObjectNode promptPayload = objectMapper.createObjectNode();
            promptPayload.put("instruction", "Generate a concise repair proposal in JSON.");
            promptPayload.set("waiting_context", waitingContext == null ? objectMapper.createObjectNode() : waitingContext);
            promptPayload.set("validation_summary", validationSummary == null ? objectMapper.createObjectNode() : validationSummary);
            promptPayload.set("failure_summary", failureSummary == null ? objectMapper.createObjectNode() : failureSummary);
            promptPayload.put("user_note", userNote == null ? "" : userNote);

            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", llmModel);
            request.put("temperature", 0.2);
            ArrayNode input = request.putArray("input");
            ObjectNode message = input.addObject();
            message.put("role", "user");
            message.put("content", promptPayload.toString());

            RestClient client = RestClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader("Authorization", "Bearer " + llmApiKey)
                    .requestInterceptor((request1, body, execution) -> {
                        request1.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request1.getHeaders().setAccept(List.of(MediaType.APPLICATION_JSON));
                        return execution.execute(request1, body);
                    })
                    .build();

            Map<?, ?> response = client.post()
                    .uri("/responses")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            ObjectNode parsed = parseResponsesOutput(response);
            if (isValidSchema(parsed)) {
                return parsed;
            }
            return fallback(waitingContext, "LLM output schema invalid");
        } catch (Exception exception) {
            LOGGER.warn("Repair proposal LLM fallback: {}", exception.getMessage());
            return fallback(waitingContext, "LLM request failed");
        }
    }

    private ObjectNode parseResponsesOutput(Map<?, ?> response) {
        if (response == null) {
            return objectMapper.createObjectNode();
        }
        Object outputText = response.get("output_text");
        if (outputText instanceof String text && !text.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(text);
                if (node.isObject()) {
                    return (ObjectNode) node;
                }
            } catch (Exception ignored) {
            }
        }

        ObjectNode proposal = objectMapper.createObjectNode();
        proposal.put("user_facing_reason", "Repair details are available in structured context.");
        proposal.put("resume_hint", "Complete required actions then resume.");
        proposal.set("action_explanations", objectMapper.createArrayNode());
        proposal.set("notes", objectMapper.createArrayNode());
        return proposal;
    }

    private boolean isValidSchema(ObjectNode proposal) {
        return proposal.has("user_facing_reason")
                && proposal.has("resume_hint")
                && proposal.has("action_explanations")
                && proposal.path("action_explanations").isArray()
                && proposal.has("notes")
                && proposal.path("notes").isArray();
    }

    private ObjectNode fallback(JsonNode waitingContext, String note) {
        ObjectNode proposal = objectMapper.createObjectNode();
        proposal.put("user_facing_reason", "Task requires additional user actions before it can continue.");
        proposal.put("resume_hint", waitingContext == null ? "Complete required actions and try resume." : waitingContext.path("resume_hint").asText("Complete required actions and try resume."));

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

