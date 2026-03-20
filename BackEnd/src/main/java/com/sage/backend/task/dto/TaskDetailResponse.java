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

    @JsonProperty("goal_parse_summary")
    private Object goalParseSummary;

    @JsonProperty("skill_route_summary")
    private Object skillRouteSummary;

    @JsonProperty("slot_bindings_summary")
    private Object slotBindingsSummary;

    @JsonProperty("args_draft_summary")
    private Object argsDraftSummary;

    @JsonProperty("validation_summary")
    private Object validationSummary;

    @JsonProperty("input_chain_status")
    private String inputChainStatus;

    private JobSummary job;

    @JsonProperty("pass2_summary")
    private Object pass2Summary;

    @JsonProperty("result_object_summary")
    private Object resultObjectSummary;

    @JsonProperty("result_bundle_summary")
    private Object resultBundleSummary;

    @JsonProperty("final_explanation_summary")
    private Object finalExplanationSummary;

    @JsonProperty("last_failure_summary")
    private Object lastFailureSummary;

    @JsonProperty("waiting_context")
    private Object waitingContext;

    @JsonProperty("repair_proposal")
    private Object repairProposal;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    @JsonProperty("latest_workspace_id")
    private String latestWorkspaceId;

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

    public Object getGoalParseSummary() {
        return goalParseSummary;
    }

    public void setGoalParseSummary(Object goalParseSummary) {
        this.goalParseSummary = goalParseSummary;
    }

    public Object getSkillRouteSummary() {
        return skillRouteSummary;
    }

    public void setSkillRouteSummary(Object skillRouteSummary) {
        this.skillRouteSummary = skillRouteSummary;
    }

    public Object getSlotBindingsSummary() {
        return slotBindingsSummary;
    }

    public void setSlotBindingsSummary(Object slotBindingsSummary) {
        this.slotBindingsSummary = slotBindingsSummary;
    }

    public Object getArgsDraftSummary() {
        return argsDraftSummary;
    }

    public void setArgsDraftSummary(Object argsDraftSummary) {
        this.argsDraftSummary = argsDraftSummary;
    }

    public Object getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(Object validationSummary) {
        this.validationSummary = validationSummary;
    }

    public String getInputChainStatus() {
        return inputChainStatus;
    }

    public void setInputChainStatus(String inputChainStatus) {
        this.inputChainStatus = inputChainStatus;
    }

    public JobSummary getJob() {
        return job;
    }

    public void setJob(JobSummary job) {
        this.job = job;
    }

    public Object getPass2Summary() {
        return pass2Summary;
    }

    public void setPass2Summary(Object pass2Summary) {
        this.pass2Summary = pass2Summary;
    }

    public Object getResultObjectSummary() {
        return resultObjectSummary;
    }

    public void setResultObjectSummary(Object resultObjectSummary) {
        this.resultObjectSummary = resultObjectSummary;
    }

    public Object getResultBundleSummary() {
        return resultBundleSummary;
    }

    public void setResultBundleSummary(Object resultBundleSummary) {
        this.resultBundleSummary = resultBundleSummary;
    }

    public Object getFinalExplanationSummary() {
        return finalExplanationSummary;
    }

    public void setFinalExplanationSummary(Object finalExplanationSummary) {
        this.finalExplanationSummary = finalExplanationSummary;
    }

    public Object getLastFailureSummary() {
        return lastFailureSummary;
    }

    public void setLastFailureSummary(Object lastFailureSummary) {
        this.lastFailureSummary = lastFailureSummary;
    }

    public Object getWaitingContext() {
        return waitingContext;
    }

    public void setWaitingContext(Object waitingContext) {
        this.waitingContext = waitingContext;
    }

    public Object getRepairProposal() {
        return repairProposal;
    }

    public void setRepairProposal(Object repairProposal) {
        this.repairProposal = repairProposal;
    }

    public String getLatestResultBundleId() {
        return latestResultBundleId;
    }

    public void setLatestResultBundleId(String latestResultBundleId) {
        this.latestResultBundleId = latestResultBundleId;
    }

    public String getLatestWorkspaceId() {
        return latestWorkspaceId;
    }

    public void setLatestWorkspaceId(String latestWorkspaceId) {
        this.latestWorkspaceId = latestWorkspaceId;
    }

    public static class JobSummary {
        @JsonProperty("job_id")
        private String jobId;

        @JsonProperty("job_state")
        private String jobState;

        @JsonProperty("last_heartbeat_at")
        private String lastHeartbeatAt;

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getJobState() {
            return jobState;
        }

        public void setJobState(String jobState) {
            this.jobState = jobState;
        }

        public String getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }

        public void setLastHeartbeatAt(String lastHeartbeatAt) {
            this.lastHeartbeatAt = lastHeartbeatAt;
        }
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
