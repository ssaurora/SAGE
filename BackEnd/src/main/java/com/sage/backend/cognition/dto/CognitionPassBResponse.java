package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class CognitionPassBResponse {
    @JsonProperty("slot_bindings")
    private List<SlotBinding> slotBindings;

    @JsonProperty("args_draft")
    private Map<String, Object> argsDraft;

    @JsonProperty("decision_summary")
    private Map<String, Object> decisionSummary;

    public List<SlotBinding> getSlotBindings() {
        return slotBindings;
    }

    public void setSlotBindings(List<SlotBinding> slotBindings) {
        this.slotBindings = slotBindings;
    }

    public Map<String, Object> getArgsDraft() {
        return argsDraft;
    }

    public void setArgsDraft(Map<String, Object> argsDraft) {
        this.argsDraft = argsDraft;
    }

    public Map<String, Object> getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(Map<String, Object> decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public static class SlotBinding {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("slot_name")
        private String slotName;

        private String source;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getSlotName() {
            return slotName;
        }

        public void setSlotName(String slotName) {
            this.slotName = slotName;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}

