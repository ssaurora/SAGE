package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.sage.backend.repair.dto.RepairProposalRequest;

public record RepairDecision(
        String severity,
        String routing,
        RepairProposalRequest.WaitingContext waitingContext
) {
}
