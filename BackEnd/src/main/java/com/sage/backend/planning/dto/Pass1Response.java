package com.sage.backend.planning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Pass1Response {
    @JsonProperty("selected_template")
    private String selectedTemplate;

    @JsonProperty("template_version")
    private String templateVersion;

    @JsonProperty("logical_input_roles")
    private List<LogicalInputRole> logicalInputRoles;

    @JsonProperty("slot_schema_view")
    private SlotSchemaView slotSchemaView;

    @JsonProperty("graph_skeleton")
    private GraphSkeleton graphSkeleton;

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

    public static class SlotSchemaItem {
        @JsonProperty("slot_name")
        private String slotName;
        private String type;

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
}

