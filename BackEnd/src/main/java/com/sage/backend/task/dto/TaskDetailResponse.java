package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskDetailResponse {
    @JsonProperty("task_id")
    private String taskId;

    private String state;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("pass1_summary")
    private Pass1Summary pass1Summary;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getStateVersion() {
        return stateVersion;
    }

    public void setStateVersion(Integer stateVersion) {
        this.stateVersion = stateVersion;
    }

    public Pass1Summary getPass1Summary() {
        return pass1Summary;
    }

    public void setPass1Summary(Pass1Summary pass1Summary) {
        this.pass1Summary = pass1Summary;
    }

    public static class Pass1Summary {
        @JsonProperty("selected_template")
        private String selectedTemplate;

        @JsonProperty("logical_input_roles_count")
        private Integer logicalInputRolesCount;

        @JsonProperty("slot_schema_view_version")
        private String slotSchemaViewVersion;

        public String getSelectedTemplate() {
            return selectedTemplate;
        }

        public void setSelectedTemplate(String selectedTemplate) {
            this.selectedTemplate = selectedTemplate;
        }

        public Integer getLogicalInputRolesCount() {
            return logicalInputRolesCount;
        }

        public void setLogicalInputRolesCount(Integer logicalInputRolesCount) {
            this.logicalInputRolesCount = logicalInputRolesCount;
        }

        public String getSlotSchemaViewVersion() {
            return slotSchemaViewVersion;
        }

        public void setSlotSchemaViewVersion(String slotSchemaViewVersion) {
            this.slotSchemaViewVersion = slotSchemaViewVersion;
        }
    }
}

