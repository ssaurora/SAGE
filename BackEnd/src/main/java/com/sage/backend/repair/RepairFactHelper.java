package com.sage.backend.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;

public final class RepairFactHelper {
    private RepairFactHelper() {
    }

    public static PrimitiveValidationResponse readPrimitiveValidation(ObjectMapper objectMapper, JsonNode node) {
        PrimitiveValidationResponse summary = readTyped(objectMapper, node, PrimitiveValidationResponse.class);
        return summary == null ? new PrimitiveValidationResponse() : summary;
    }

    public static RepairProposalRequest.ValidationSummary readValidationSummary(ObjectMapper objectMapper, JsonNode node) {
        RepairProposalRequest.ValidationSummary summary = readTyped(objectMapper, node, RepairProposalRequest.ValidationSummary.class);
        return summary == null ? new RepairProposalRequest.ValidationSummary() : summary;
    }

    public static RepairProposalRequest.ValidationSummary toValidationSummary(PrimitiveValidationResponse validationResponse) {
        RepairProposalRequest.ValidationSummary summary = new RepairProposalRequest.ValidationSummary();
        if (validationResponse == null) {
            return summary;
        }
        summary.setIsValid(validationResponse.getIsValid());
        summary.setMissingRoles(validationResponse.getMissingRoles());
        summary.setMissingParams(validationResponse.getMissingParams());
        summary.setErrorCode(validationResponse.getErrorCode());
        summary.setInvalidBindings(validationResponse.getInvalidBindings());
        return summary;
    }

    public static RepairProposalRequest.FailureSummary readFailureSummary(ObjectMapper objectMapper, JsonNode node) {
        RepairProposalRequest.FailureSummary summary = readTyped(objectMapper, node, RepairProposalRequest.FailureSummary.class);
        return summary == null ? new RepairProposalRequest.FailureSummary() : summary;
    }

    public static RepairProposalRequest buildRequest(
            ObjectMapper objectMapper,
            JsonNode waitingContext,
            JsonNode validationSummary,
            JsonNode failureSummary,
            String userNote
    ) {
        return buildRequest(
                readTyped(objectMapper, waitingContext, RepairProposalRequest.WaitingContext.class),
                readValidationSummary(objectMapper, validationSummary),
                readFailureSummary(objectMapper, failureSummary),
                userNote
        );
    }

    public static RepairProposalRequest buildRequest(
            RepairProposalRequest.WaitingContext waitingContext,
            RepairProposalRequest.ValidationSummary validationSummary,
            RepairProposalRequest.FailureSummary failureSummary,
            String userNote
    ) {
        RepairProposalRequest request = new RepairProposalRequest();
        request.setWaitingContext(waitingContext);
        request.setValidationSummary(validationSummary);
        request.setFailureSummary(failureSummary);
        request.setUserNote(userNote == null ? "" : userNote);
        return request;
    }

    public static JsonNode toJsonNode(ObjectMapper objectMapper, Object value) {
        return objectMapper.valueToTree(value);
    }

    private static <T> T readTyped(ObjectMapper objectMapper, JsonNode node, Class<T> type) {
        try {
            JsonNode source = node == null ? objectMapper.createObjectNode() : node;
            return objectMapper.treeToValue(source, type);
        } catch (Exception exception) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception reflectionException) {
                throw new IllegalStateException("Failed to instantiate typed repair fact view", reflectionException);
            }
        }
    }
}
