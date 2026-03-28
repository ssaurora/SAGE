package com.sage.backend.repair.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RepairProposalRequest {
    @JsonProperty("waiting_context")
    private WaitingContext waitingContext = new WaitingContext();

    @JsonProperty("validation_summary")
    private ValidationSummary validationSummary = new ValidationSummary();

    @JsonProperty("failure_summary")
    private FailureSummary failureSummary = new FailureSummary();

    @JsonProperty("user_note")
    private String userNote = "";

    public WaitingContext getWaitingContext() {
        return waitingContext;
    }

    public void setWaitingContext(WaitingContext waitingContext) {
        this.waitingContext = waitingContext == null ? new WaitingContext() : waitingContext;
    }

    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(ValidationSummary validationSummary) {
        this.validationSummary = validationSummary == null ? new ValidationSummary() : validationSummary;
    }

    public FailureSummary getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(FailureSummary failureSummary) {
        this.failureSummary = failureSummary == null ? new FailureSummary() : failureSummary;
    }

    public String getUserNote() {
        return userNote;
    }

    public void setUserNote(String userNote) {
        this.userNote = userNote == null ? "" : userNote;
    }

    public static class WaitingContext {
        @JsonProperty("waiting_reason_type")
        private String waitingReasonType = "";

        @JsonProperty("missing_slots")
        private List<MissingSlot> missingSlots = new ArrayList<>();

        @JsonProperty("invalid_bindings")
        private List<String> invalidBindings = new ArrayList<>();

        @JsonProperty("required_user_actions")
        private List<RequiredUserAction> requiredUserActions = new ArrayList<>();

        @JsonProperty("resume_hint")
        private String resumeHint = "";

        @JsonProperty("can_resume")
        private Boolean canResume = Boolean.FALSE;

        public String getWaitingReasonType() {
            return waitingReasonType;
        }

        public void setWaitingReasonType(String waitingReasonType) {
            this.waitingReasonType = waitingReasonType == null ? "" : waitingReasonType;
        }

        public List<MissingSlot> getMissingSlots() {
            return missingSlots;
        }

        public void setMissingSlots(List<MissingSlot> missingSlots) {
            this.missingSlots = missingSlots == null ? new ArrayList<>() : missingSlots;
        }

        public List<String> getInvalidBindings() {
            return invalidBindings;
        }

        public void setInvalidBindings(List<String> invalidBindings) {
            this.invalidBindings = invalidBindings == null ? new ArrayList<>() : invalidBindings;
        }

        public List<RequiredUserAction> getRequiredUserActions() {
            return requiredUserActions;
        }

        public void setRequiredUserActions(List<RequiredUserAction> requiredUserActions) {
            this.requiredUserActions = requiredUserActions == null ? new ArrayList<>() : requiredUserActions;
        }

        public String getResumeHint() {
            return resumeHint;
        }

        public void setResumeHint(String resumeHint) {
            this.resumeHint = resumeHint == null ? "" : resumeHint;
        }

        public Boolean getCanResume() {
            return canResume;
        }

        public void setCanResume(Boolean canResume) {
            this.canResume = canResume;
        }
    }

    public static class MissingSlot {
        @JsonProperty("slot_name")
        private String slotName = "";

        @JsonProperty("expected_type")
        private String expectedType = "";

        @JsonProperty("required")
        private Boolean required = Boolean.TRUE;

        public String getSlotName() {
            return slotName;
        }

        public void setSlotName(String slotName) {
            this.slotName = slotName == null ? "" : slotName;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public void setExpectedType(String expectedType) {
            this.expectedType = expectedType == null ? "" : expectedType;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }

    public static class RequiredUserAction {
        @JsonProperty("action_type")
        private String actionType = "";

        @JsonProperty("key")
        private String key = "";

        @JsonProperty("label")
        private String label = "";

        @JsonProperty("required")
        private Boolean required = Boolean.TRUE;

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType == null ? "" : actionType;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key == null ? "" : key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label == null ? "" : label;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }

    public static class ValidationSummary {
        @JsonProperty("is_valid")
        private Boolean isValid;

        @JsonProperty("missing_roles")
        private List<String> missingRoles = new ArrayList<>();

        @JsonProperty("missing_params")
        private List<String> missingParams = new ArrayList<>();

        @JsonProperty("error_code")
        private String errorCode = "";

        @JsonProperty("invalid_bindings")
        private List<String> invalidBindings = new ArrayList<>();

        public Boolean getIsValid() {
            return isValid;
        }

        public void setIsValid(Boolean isValid) {
            this.isValid = isValid;
        }

        public List<String> getMissingRoles() {
            return missingRoles;
        }

        public void setMissingRoles(List<String> missingRoles) {
            this.missingRoles = missingRoles == null ? new ArrayList<>() : missingRoles;
        }

        public List<String> getMissingParams() {
            return missingParams;
        }

        public void setMissingParams(List<String> missingParams) {
            this.missingParams = missingParams == null ? new ArrayList<>() : missingParams;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode == null ? "" : errorCode;
        }

        public List<String> getInvalidBindings() {
            return invalidBindings;
        }

        public void setInvalidBindings(List<String> invalidBindings) {
            this.invalidBindings = invalidBindings == null ? new ArrayList<>() : invalidBindings;
        }
    }

    public static class FailureSummary {
        @JsonProperty("failure_code")
        private String failureCode = "";

        @JsonProperty("failure_message")
        private String failureMessage = "";

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("assertion_id")
        private String assertionId = "";

        @JsonProperty("node_id")
        private String nodeId = "";

        private Boolean repairable;

        private Map<String, Object> details = new LinkedHashMap<>();

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode == null ? "" : failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage == null ? "" : failureMessage;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getAssertionId() {
            return assertionId;
        }

        public void setAssertionId(String assertionId) {
            this.assertionId = assertionId == null ? "" : assertionId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId == null ? "" : nodeId;
        }

        public Boolean getRepairable() {
            return repairable;
        }

        public void setRepairable(Boolean repairable) {
            this.repairable = repairable;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details == null ? new LinkedHashMap<>() : details;
        }
    }
}
