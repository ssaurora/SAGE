package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepairProposalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairProposalService.class);

    private final ObjectMapper objectMapper;
    private final RepairProposalClient repairProposalClient;
    private final RepairProposalFallbackService fallbackService;

    public RepairProposalService(
            ObjectMapper objectMapper,
            RepairProposalClient repairProposalClient,
            RepairProposalFallbackService fallbackService
    ) {
        this.objectMapper = objectMapper;
        this.repairProposalClient = repairProposalClient;
        this.fallbackService = fallbackService;
    }

    public ObjectNode generate(JsonNode waitingContext, JsonNode validationSummary, JsonNode failureSummary, String userNote) {
        try {
            RepairProposalRequest request = new RepairProposalRequest();
            request.setWaitingContext(waitingContext == null ? objectMapper.createObjectNode() : waitingContext);
            request.setValidationSummary(validationSummary == null ? objectMapper.createObjectNode() : validationSummary);
            request.setFailureSummary(failureSummary == null ? objectMapper.createObjectNode() : failureSummary);
            request.setUserNote(userNote == null ? "" : userNote);

            RepairProposalResponse response = repairProposalClient.generate(request);
            if (isValidSchema(response)) {
                return objectMapper.valueToTree(response);
            }
            return fallbackService.generate(waitingContext, "Cognition output schema invalid");
        } catch (Exception exception) {
            LOGGER.warn("Repair proposal fallback: {}", exception.getMessage());
            return fallbackService.generate(waitingContext, "Cognition request failed");
        }
    }

    private boolean isValidSchema(RepairProposalResponse proposal) {
        return proposal != null
                && proposal.getUserFacingReason() != null && !proposal.getUserFacingReason().isBlank()
                && proposal.getResumeHint() != null && !proposal.getResumeHint().isBlank()
                && proposal.getActionExplanations() != null
                && proposal.getNotes() != null;
    }
}
