package com.sage.backend.session.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class WaitingForUserProjectionDto {
    @JsonProperty("waiting_reason_type")
    private String waitingReasonType;

    @JsonProperty("missing_slots")
    private List<MissingSlotDto> missingSlots;

    @JsonProperty("invalid_bindings")
    private List<String> invalidBindings;

    @JsonProperty("required_user_actions")
    private List<RequiredUserActionDto> requiredUserActions;

    @JsonProperty("resume_hint")
    private String resumeHint;

    @JsonProperty("can_resume")
    private Boolean canResume;

    @JsonProperty("catalog_summary")
    private Map<String, Object> catalogSummary;

    @JsonProperty("why_blocked")
    private String whyBlocked;

    @JsonProperty("user_facing_phrasing")
    private String userFacingPhrasing;

    public String getWaitingReasonType() {
        return waitingReasonType;
    }

    public void setWaitingReasonType(String waitingReasonType) {
        this.waitingReasonType = waitingReasonType;
    }

    public List<MissingSlotDto> getMissingSlots() {
        return missingSlots;
    }

    public void setMissingSlots(List<MissingSlotDto> missingSlots) {
        this.missingSlots = missingSlots;
    }

    public List<String> getInvalidBindings() {
        return invalidBindings;
    }

    public void setInvalidBindings(List<String> invalidBindings) {
        this.invalidBindings = invalidBindings;
    }

    public List<RequiredUserActionDto> getRequiredUserActions() {
        return requiredUserActions;
    }

    public void setRequiredUserActions(List<RequiredUserActionDto> requiredUserActions) {
        this.requiredUserActions = requiredUserActions;
    }

    public String getResumeHint() {
        return resumeHint;
    }

    public void setResumeHint(String resumeHint) {
        this.resumeHint = resumeHint;
    }

    public Boolean getCanResume() {
        return canResume;
    }

    public void setCanResume(Boolean canResume) {
        this.canResume = canResume;
    }

    public Map<String, Object> getCatalogSummary() {
        return catalogSummary;
    }

    public void setCatalogSummary(Map<String, Object> catalogSummary) {
        this.catalogSummary = catalogSummary;
    }

    public String getWhyBlocked() {
        return whyBlocked;
    }

    public void setWhyBlocked(String whyBlocked) {
        this.whyBlocked = whyBlocked;
    }

    public String getUserFacingPhrasing() {
        return userFacingPhrasing;
    }

    public void setUserFacingPhrasing(String userFacingPhrasing) {
        this.userFacingPhrasing = userFacingPhrasing;
    }

    public static class MissingSlotDto {
        @JsonProperty("slot_name")
        private String slotName;

        @JsonProperty("expected_type")
        private String expectedType;

        private Boolean required;

        public String getSlotName() {
            return slotName;
        }

        public void setSlotName(String slotName) {
            this.slotName = slotName;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public void setExpectedType(String expectedType) {
            this.expectedType = expectedType;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }

    public static class RequiredUserActionDto {
        @JsonProperty("action_type")
        private String actionType;

        private String key;

        private String label;

        private Boolean required;

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }
    }
}
