package com.sage.backend.repair;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record RepairDecision(
        String severity,
        String routing,
        ObjectNode waitingContext
) {
}

