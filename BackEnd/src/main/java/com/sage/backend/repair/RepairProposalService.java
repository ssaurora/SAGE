package com.sage.backend.repair;

import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RepairProposalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepairProposalService.class);

    private final RepairProposalClient repairProposalClient;
    private final RepairProposalFallbackService fallbackService;

    public RepairProposalService(
            RepairProposalClient repairProposalClient,
            RepairProposalFallbackService fallbackService
    ) {
        this.repairProposalClient = repairProposalClient;
        this.fallbackService = fallbackService;
    }

    public RepairProposalResponse generate(RepairProposalRequest request) {
        try {
            RepairProposalResponse response = repairProposalClient.generate(request);
            if (isValidSchema(response)) {
                return response;
            }
            return fallbackService.generate(request, "Cognition output schema invalid");
        } catch (Exception exception) {
            LOGGER.warn("Repair proposal unavailable: {}", exception.getMessage());
            return fallbackService.generate(request, "Cognition request failed");
        }
    }

    public RepairProposalResponse generate(
            RepairProposalRequest.WaitingContext waitingContext,
            RepairProposalRequest.ValidationSummary validationSummary,
            RepairProposalRequest.FailureSummary failureSummary,
            String userNote
    ) {
        return generate(RepairFactHelper.buildRequest(waitingContext, validationSummary, failureSummary, userNote));
    }

    private boolean isValidSchema(RepairProposalResponse proposal) {
        if (proposal == null || proposal.getNotes() == null) {
            return false;
        }
        if (Boolean.FALSE.equals(proposal.getAvailable())) {
            return proposal.getFailureCode() != null && !proposal.getFailureCode().isBlank()
                    && proposal.getFailureMessage() != null && !proposal.getFailureMessage().isBlank()
                    && proposal.getCognitionMetadata() != null
                    && !proposal.getCognitionMetadata().isEmpty();
        }
        return proposal.getUserFacingReason() != null && !proposal.getUserFacingReason().isBlank()
                && proposal.getResumeHint() != null && !proposal.getResumeHint().isBlank()
                && proposal.getActionExplanations() != null
                && proposal.getCognitionMetadata() != null
                && !proposal.getCognitionMetadata().isEmpty();
    }
}
