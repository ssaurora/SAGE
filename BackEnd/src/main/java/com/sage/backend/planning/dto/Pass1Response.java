package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Pass1Response {
    @JsonProperty("skill_id")
    private String skillId;

    @JsonProperty("skill_version")
    private String skillVersion;

    @JsonProperty("capability_key")
    private String capabilityKey;

    @JsonProperty("capability_facts")
    private CapabilityFacts capabilityFacts;

    @JsonProperty("selected_template")
    private String selectedTemplate;

    @JsonProperty("template_version")
    private String templateVersion;

    @JsonProperty("logical_input_roles")
    private List<LogicalInputRole> logicalInputRoles;

    @JsonProperty("role_arg_mappings")
    private List<RoleArgMapping> roleArgMappings;

    @JsonProperty("stable_defaults")
    private Map<String, Object> stableDefaults;

    @JsonProperty("slot_schema_view")
    private SlotSchemaView slotSchemaView;

    @JsonProperty("graph_skeleton")
    private GraphSkeleton graphSkeleton;

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion;
    }

    public String getCapabilityKey() {
        return capabilityKey;
    }

    public void setCapabilityKey(String capabilityKey) {
        this.capabilityKey = capabilityKey;
    }

    public CapabilityFacts getCapabilityFacts() {
        return capabilityFacts;
    }

    public void setCapabilityFacts(CapabilityFacts capabilityFacts) {
        this.capabilityFacts = capabilityFacts;
    }

    public String getSelectedTemplate() {
        return selectedTemplate;
    }

    public void setSelectedTemplate(String selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(String templateVersion) {
        this.templateVersion = templateVersion;
    }

    public List<LogicalInputRole> getLogicalInputRoles() {
        return logicalInputRoles;
    }

    public void setLogicalInputRoles(List<LogicalInputRole> logicalInputRoles) {
        this.logicalInputRoles = logicalInputRoles;
    }

    public List<RoleArgMapping> getRoleArgMappings() {
        return roleArgMappings;
    }

    public void setRoleArgMappings(List<RoleArgMapping> roleArgMappings) {
        this.roleArgMappings = roleArgMappings;
    }

    public Map<String, Object> getStableDefaults() {
        return stableDefaults;
    }

    public void setStableDefaults(Map<String, Object> stableDefaults) {
        this.stableDefaults = stableDefaults;
    }

    public SlotSchemaView getSlotSchemaView() {
        return slotSchemaView;
    }

    public void setSlotSchemaView(SlotSchemaView slotSchemaView) {
        this.slotSchemaView = slotSchemaView;
    }

    public GraphSkeleton getGraphSkeleton() {
        return graphSkeleton;
    }

    public void setGraphSkeleton(GraphSkeleton graphSkeleton) {
        this.graphSkeleton = graphSkeleton;
    }

    public static class LogicalInputRole {
        @JsonProperty("role_name")
        private String roleName;
        private boolean required;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }
    }

    public static class SlotSchemaView {
        private List<SlotSchemaItem> slots;

        public List<SlotSchemaItem> getSlots() {
            return slots;
        }

        public void setSlots(List<SlotSchemaItem> slots) {
            this.slots = slots;
        }
    }

    public static class RoleArgMapping {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("slot_arg_key")
        private String slotArgKey;

        @JsonProperty("value_arg_key")
        private String valueArgKey;

        @JsonProperty("default_value")
        private Object defaultValue;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getSlotArgKey() {
            return slotArgKey;
        }

        public void setSlotArgKey(String slotArgKey) {
            this.slotArgKey = slotArgKey;
        }

        public String getValueArgKey() {
            return valueArgKey;
        }

        public void setValueArgKey(String valueArgKey) {
            this.valueArgKey = valueArgKey;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    public static class SlotSchemaItem {
        @JsonProperty("slot_name")
        private String slotName;
        private String type;

        @JsonProperty("bound_role")
        private String boundRole;

        public String getSlotName() {
            return slotName;
        }

        public void setSlotName(String slotName) {
            this.slotName = slotName;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBoundRole() {
            return boundRole;
        }

        public void setBoundRole(String boundRole) {
            this.boundRole = boundRole;
        }
    }

    public static class GraphSkeleton {
        private List<String> nodes;
        private List<List<String>> edges;

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public List<List<String>> getEdges() {
            return edges;
        }

        public void setEdges(List<List<String>> edges) {
            this.edges = edges;
        }
    }

    public static class CapabilityFacts {
        @JsonProperty("capability_key")
        private String capabilityKey;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("validation_hints")
        private List<ValidationHint> validationHints;

        @JsonProperty("repair_hints")
        private List<RepairHint> repairHints;

        @JsonProperty("output_contract")
        private Object outputContract;

        @JsonProperty("runtime_profile_hint")
        private String runtimeProfileHint;

        public String getCapabilityKey() {
            return capabilityKey;
        }

        public void setCapabilityKey(String capabilityKey) {
            this.capabilityKey = capabilityKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<ValidationHint> getValidationHints() {
            return validationHints;
        }

        public void setValidationHints(List<ValidationHint> validationHints) {
            this.validationHints = validationHints;
        }

        public List<RepairHint> getRepairHints() {
            return repairHints;
        }

        public void setRepairHints(List<RepairHint> repairHints) {
            this.repairHints = repairHints;
        }

        public Object getOutputContract() {
            return outputContract;
        }

        public void setOutputContract(Object outputContract) {
            this.outputContract = outputContract;
        }

        public String getRuntimeProfileHint() {
            return runtimeProfileHint;
        }

        public void setRuntimeProfileHint(String runtimeProfileHint) {
            this.runtimeProfileHint = runtimeProfileHint;
        }
    }

    public static class ValidationHint {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("expected_slot_type")
        private String expectedSlotType;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getExpectedSlotType() {
            return expectedSlotType;
        }

        public void setExpectedSlotType(String expectedSlotType) {
            this.expectedSlotType = expectedSlotType;
        }
    }

    public static class RepairHint {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("action_key")
        private String actionKey;

        @JsonProperty("action_label")
        private String actionLabel;

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
        }

        public String getActionKey() {
            return actionKey;
        }

        public void setActionKey(String actionKey) {
            this.actionKey = actionKey;
        }

        public String getActionLabel() {
            return actionLabel;
        }

        public void setActionLabel(String actionLabel) {
            this.actionLabel = actionLabel;
        }
    }
}
