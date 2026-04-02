package com.sage.backend.cognition.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CognitionPassBResponse {
    @JsonProperty("binding_status")
    private String bindingStatus;

    @JsonProperty("slot_bindings")
    private List<SlotBinding> slotBindings;

    @JsonProperty("user_semantic_args")
    private Map<String, Object> userSemanticArgs;

    @JsonProperty("inferred_semantic_args")
    private Map<String, InferredSemanticArg> inferredSemanticArgs;

    @JsonProperty("args_draft")
    private Map<String, Object> argsDraft;

    @JsonProperty("case_projection")
    private Map<String, Object> caseProjection;

    @JsonProperty("decision_summary")
    private Map<String, Object> decisionSummary;

    private Double confidence;

    @JsonProperty("cognition_metadata")
    private Map<String, Object> cognitionMetadata;

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public List<SlotBinding> getSlotBindings() {
        return slotBindings;
    }

    public void setSlotBindings(List<SlotBinding> slotBindings) {
        this.slotBindings = slotBindings;
    }

    public Map<String, Object> getUserSemanticArgs() {
        return userSemanticArgs;
    }

    public void setUserSemanticArgs(Map<String, Object> userSemanticArgs) {
        this.userSemanticArgs = userSemanticArgs;
    }

    public Map<String, InferredSemanticArg> getInferredSemanticArgs() {
        return inferredSemanticArgs;
    }

    public void setInferredSemanticArgs(Map<String, InferredSemanticArg> inferredSemanticArgs) {
        this.inferredSemanticArgs = inferredSemanticArgs;
    }

    public Map<String, Object> getArgsDraft() {
        return argsDraft;
    }

    public void setArgsDraft(Map<String, Object> argsDraft) {
        this.argsDraft = argsDraft;
    }

    public Map<String, Object> getCaseProjection() {
        return caseProjection;
    }

    public void setCaseProjection(Map<String, Object> caseProjection) {
        this.caseProjection = caseProjection;
    }

    public Map<String, Object> getDecisionSummary() {
        return decisionSummary;
    }

    public void setDecisionSummary(Map<String, Object> decisionSummary) {
        this.decisionSummary = decisionSummary;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getCognitionMetadata() {
        return cognitionMetadata;
    }

    public void setCognitionMetadata(Map<String, Object> cognitionMetadata) {
        this.cognitionMetadata = cognitionMetadata;
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

    public static class InferredSemanticArg {
        private JsonNode value;
        private String reason;
        private String source;

        public JsonNode getValue() {
            return value;
        }

        public void setValue(JsonNode value) {
            this.value = value;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
