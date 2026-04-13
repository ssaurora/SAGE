package com.sage.backend.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SceneBlockingSummaryDTO {
    @JsonProperty("blocking_type")
    private String blockingType;

    @JsonProperty("waiting_reason_type")
    private String waitingReasonType;

    @JsonProperty("user_facing_reason")
    private String userFacingReason;

    @JsonProperty("can_resume")
    private Boolean canResume;

    @JsonProperty("required_user_action_count")
    private Integer requiredUserActionCount;

    @JsonProperty("missing_slot_count")
    private Integer missingSlotCount;

    @JsonProperty("invalid_binding_count")
    private Integer invalidBindingCount;

    public String getBlockingType() {
        return blockingType;
    }

    public void setBlockingType(String blockingType) {
        this.blockingType = blockingType;
    }

    public String getWaitingReasonType() {
        return waitingReasonType;
    }

    public void setWaitingReasonType(String waitingReasonType) {
        this.waitingReasonType = waitingReasonType;
    }

    public String getUserFacingReason() {
        return userFacingReason;
    }

    public void setUserFacingReason(String userFacingReason) {
        this.userFacingReason = userFacingReason;
    }

    public Boolean getCanResume() {
        return canResume;
    }

    public void setCanResume(Boolean canResume) {
        this.canResume = canResume;
    }

    public Integer getRequiredUserActionCount() {
        return requiredUserActionCount;
    }

    public void setRequiredUserActionCount(Integer requiredUserActionCount) {
        this.requiredUserActionCount = requiredUserActionCount;
    }

    public Integer getMissingSlotCount() {
        return missingSlotCount;
    }

    public void setMissingSlotCount(Integer missingSlotCount) {
        this.missingSlotCount = missingSlotCount;
    }

    public Integer getInvalidBindingCount() {
        return invalidBindingCount;
    }

    public void setInvalidBindingCount(Integer invalidBindingCount) {
        this.invalidBindingCount = invalidBindingCount;
    }
}
