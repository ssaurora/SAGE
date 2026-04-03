package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TaskDetailResponse {
    @JsonProperty("task_id")
    private String taskId;

    private String state;

    @JsonProperty("state_version")
    private Integer stateVersion;

    @JsonProperty("planning_revision")
    private Integer planningRevision;

    @JsonProperty("checkpoint_version")
    private Integer checkpointVersion;

    @JsonProperty("resume_transaction")
    private ResumeTransactionView resumeTransaction;

    @JsonProperty("corruption_state")
    private CorruptionStateView corruptionState;

    @JsonProperty("promotion_status")
    private String promotionStatus;

    @JsonProperty("pass1_summary")
    private Pass1Summary pass1Summary;

    @JsonProperty("goal_parse_summary")
    private GoalParseSummary goalParseSummary;

    @JsonProperty("skill_route_summary")
    private SkillRouteSummary skillRouteSummary;

    @JsonProperty("skill_id")
    private String skillId;

    @JsonProperty("skill_version")
    private String skillVersion;

    @JsonProperty("slot_bindings_summary")
    private SlotBindingsSummary slotBindingsSummary;

    @JsonProperty("args_draft_summary")
    private ArgsDraftSummary argsDraftSummary;

    @JsonProperty("validation_summary")
    private ValidationSummary validationSummary;

    @JsonProperty("input_chain_status")
    private String inputChainStatus;

    private JobSummary job;

    @JsonProperty("pass2_summary")
    private Pass2Summary pass2Summary;

    @JsonProperty("result_object_summary")
    private ResultObjectSummary resultObjectSummary;

    @JsonProperty("result_bundle_summary")
    private ResultBundleSummary resultBundleSummary;

    @JsonProperty("final_explanation_summary")
    private FinalExplanationSummary finalExplanationSummary;

    @JsonProperty("last_failure_summary")
    private FailureSummary lastFailureSummary;

    @JsonProperty("waiting_context")
    private WaitingContext waitingContext;

    @JsonProperty("repair_proposal")
    private RepairProposal repairProposal;

    @JsonProperty("latest_result_bundle_id")
    private String latestResultBundleId;

    @JsonProperty("latest_workspace_id")
    private String latestWorkspaceId;

    @JsonProperty("graph_digest")
    private String graphDigest;

    @JsonProperty("planning_summary")
    private Map<String, Object> planningSummary;

    @JsonProperty("planning_intent_status")
    private String planningIntentStatus;

    @JsonProperty("binding_status")
    private String bindingStatus;

    @JsonProperty("overruled_fields")
    private java.util.List<String> overruledFields;

    @JsonProperty("blocked_mutations")
    private java.util.List<String> blockedMutations;

    @JsonProperty("assembly_blocked")
    private Boolean assemblyBlocked;

    @JsonProperty("cognition_verdict")
    private String cognitionVerdict;

    @JsonProperty("case_projection")
    private Map<String, Object> caseProjection;

    @JsonProperty("goal_route_cognition")
    private Map<String, Object> goalRouteCognition;

    @JsonProperty("goal_route_output")
    private Map<String, Object> goalRouteOutput;

    @JsonProperty("passb_cognition")
    private Map<String, Object> passbCognition;

    @JsonProperty("passb_output")
    private Map<String, Object> passbOutput;

    @JsonProperty("repair_proposal_cognition")
    private Map<String, Object> repairProposalCognition;

    @JsonProperty("repair_proposal_output")
    private Map<String, Object> repairProposalOutput;

    @JsonProperty("final_explanation_cognition")
    private Map<String, Object> finalExplanationCognition;

    @JsonProperty("final_explanation_output")
    private Map<String, Object> finalExplanationOutput;

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

    public Integer getPlanningRevision() {
        return planningRevision;
    }

    public void setPlanningRevision(Integer planningRevision) {
        this.planningRevision = planningRevision;
    }

    public Integer getCheckpointVersion() {
        return checkpointVersion;
    }

    public void setCheckpointVersion(Integer checkpointVersion) {
        this.checkpointVersion = checkpointVersion;
    }

    public ResumeTransactionView getResumeTransaction() {
        return resumeTransaction;
    }

    public void setResumeTransaction(ResumeTransactionView resumeTransaction) {
        this.resumeTransaction = resumeTransaction;
    }

    public CorruptionStateView getCorruptionState() {
        return corruptionState;
    }

    public void setCorruptionState(CorruptionStateView corruptionState) {
        this.corruptionState = corruptionState;
    }

    public String getPromotionStatus() {
        return promotionStatus;
    }

    public void setPromotionStatus(String promotionStatus) {
        this.promotionStatus = promotionStatus;
    }

    public Pass1Summary getPass1Summary() {
        return pass1Summary;
    }

    public void setPass1Summary(Pass1Summary pass1Summary) {
        this.pass1Summary = pass1Summary;
    }

    public GoalParseSummary getGoalParseSummary() {
        return goalParseSummary;
    }

    public void setGoalParseSummary(GoalParseSummary goalParseSummary) {
        this.goalParseSummary = goalParseSummary;
    }

    public SkillRouteSummary getSkillRouteSummary() {
        return skillRouteSummary;
    }

    public void setSkillRouteSummary(SkillRouteSummary skillRouteSummary) {
        this.skillRouteSummary = skillRouteSummary;
    }

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

    public SlotBindingsSummary getSlotBindingsSummary() {
        return slotBindingsSummary;
    }

    public void setSlotBindingsSummary(SlotBindingsSummary slotBindingsSummary) {
        this.slotBindingsSummary = slotBindingsSummary;
    }

    public ArgsDraftSummary getArgsDraftSummary() {
        return argsDraftSummary;
    }

    public void setArgsDraftSummary(ArgsDraftSummary argsDraftSummary) {
        this.argsDraftSummary = argsDraftSummary;
    }

    public ValidationSummary getValidationSummary() {
        return validationSummary;
    }

    public void setValidationSummary(ValidationSummary validationSummary) {
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

    public Pass2Summary getPass2Summary() {
        return pass2Summary;
    }

    public void setPass2Summary(Pass2Summary pass2Summary) {
        this.pass2Summary = pass2Summary;
    }

    public ResultObjectSummary getResultObjectSummary() {
        return resultObjectSummary;
    }

    public void setResultObjectSummary(ResultObjectSummary resultObjectSummary) {
        this.resultObjectSummary = resultObjectSummary;
    }

    public ResultBundleSummary getResultBundleSummary() {
        return resultBundleSummary;
    }

    public void setResultBundleSummary(ResultBundleSummary resultBundleSummary) {
        this.resultBundleSummary = resultBundleSummary;
    }

    public FinalExplanationSummary getFinalExplanationSummary() {
        return finalExplanationSummary;
    }

    public void setFinalExplanationSummary(FinalExplanationSummary finalExplanationSummary) {
        this.finalExplanationSummary = finalExplanationSummary;
    }

    public FailureSummary getLastFailureSummary() {
        return lastFailureSummary;
    }

    public void setLastFailureSummary(FailureSummary lastFailureSummary) {
        this.lastFailureSummary = lastFailureSummary;
    }

    public WaitingContext getWaitingContext() {
        return waitingContext;
    }

    public void setWaitingContext(WaitingContext waitingContext) {
        this.waitingContext = waitingContext;
    }

    public RepairProposal getRepairProposal() {
        return repairProposal;
    }

    public void setRepairProposal(RepairProposal repairProposal) {
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

    public String getGraphDigest() {
        return graphDigest;
    }

    public void setGraphDigest(String graphDigest) {
        this.graphDigest = graphDigest;
    }

    public Map<String, Object> getPlanningSummary() {
        return planningSummary;
    }

    public void setPlanningSummary(Map<String, Object> planningSummary) {
        this.planningSummary = planningSummary;
    }

    public String getPlanningIntentStatus() {
        return planningIntentStatus;
    }

    public void setPlanningIntentStatus(String planningIntentStatus) {
        this.planningIntentStatus = planningIntentStatus;
    }

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public java.util.List<String> getOverruledFields() {
        return overruledFields;
    }

    public void setOverruledFields(java.util.List<String> overruledFields) {
        this.overruledFields = overruledFields;
    }

    public java.util.List<String> getBlockedMutations() {
        return blockedMutations;
    }

    public void setBlockedMutations(java.util.List<String> blockedMutations) {
        this.blockedMutations = blockedMutations;
    }

    public Boolean getAssemblyBlocked() {
        return assemblyBlocked;
    }

    public void setAssemblyBlocked(Boolean assemblyBlocked) {
        this.assemblyBlocked = assemblyBlocked;
    }

    public String getCognitionVerdict() {
        return cognitionVerdict;
    }

    public void setCognitionVerdict(String cognitionVerdict) {
        this.cognitionVerdict = cognitionVerdict;
    }

    public Map<String, Object> getCaseProjection() {
        return caseProjection;
    }

    public void setCaseProjection(Map<String, Object> caseProjection) {
        this.caseProjection = caseProjection;
    }

    public Map<String, Object> getGoalRouteCognition() {
        return goalRouteCognition;
    }

    public void setGoalRouteCognition(Map<String, Object> goalRouteCognition) {
        this.goalRouteCognition = goalRouteCognition;
    }

    public Map<String, Object> getGoalRouteOutput() {
        return goalRouteOutput;
    }

    public void setGoalRouteOutput(Map<String, Object> goalRouteOutput) {
        this.goalRouteOutput = goalRouteOutput;
    }

    public Map<String, Object> getPassbCognition() {
        return passbCognition;
    }

    public void setPassbCognition(Map<String, Object> passbCognition) {
        this.passbCognition = passbCognition;
    }

    public Map<String, Object> getPassbOutput() {
        return passbOutput;
    }

    public void setPassbOutput(Map<String, Object> passbOutput) {
        this.passbOutput = passbOutput;
    }

    public Map<String, Object> getRepairProposalCognition() {
        return repairProposalCognition;
    }

    public void setRepairProposalCognition(Map<String, Object> repairProposalCognition) {
        this.repairProposalCognition = repairProposalCognition;
    }

    public Map<String, Object> getRepairProposalOutput() {
        return repairProposalOutput;
    }

    public void setRepairProposalOutput(Map<String, Object> repairProposalOutput) {
        this.repairProposalOutput = repairProposalOutput;
    }

    public Map<String, Object> getFinalExplanationCognition() {
        return finalExplanationCognition;
    }

    public void setFinalExplanationCognition(Map<String, Object> finalExplanationCognition) {
        this.finalExplanationCognition = finalExplanationCognition;
    }

    public Map<String, Object> getFinalExplanationOutput() {
        return finalExplanationOutput;
    }

    public void setFinalExplanationOutput(Map<String, Object> finalExplanationOutput) {
        this.finalExplanationOutput = finalExplanationOutput;
    }

    public static class JobSummary {
        @JsonProperty("job_id")
        private String jobId;

        @JsonProperty("job_state")
        private String jobState;

        @JsonProperty("last_heartbeat_at")
        private String lastHeartbeatAt;

        @JsonProperty("provider_key")
        private String providerKey;

        @JsonProperty("capability_key")
        private String capabilityKey;

        @JsonProperty("runtime_profile")
        private String runtimeProfile;

        @JsonProperty("case_id")
        private String caseId;

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

        public String getProviderKey() {
            return providerKey;
        }

        public void setProviderKey(String providerKey) {
            this.providerKey = providerKey;
        }

        public String getCapabilityKey() {
            return capabilityKey;
        }

        public void setCapabilityKey(String capabilityKey) {
            this.capabilityKey = capabilityKey;
        }

        public String getRuntimeProfile() {
            return runtimeProfile;
        }

        public void setRuntimeProfile(String runtimeProfile) {
            this.runtimeProfile = runtimeProfile;
        }

        public String getCaseId() {
            return caseId;
        }

        public void setCaseId(String caseId) {
            this.caseId = caseId;
        }
    }

    public static class Pass1Summary {
        @JsonProperty("capability_key")
        private String capabilityKey;

        @JsonProperty("selected_template")
        private String selectedTemplate;

        @JsonProperty("logical_input_roles_count")
        private Integer logicalInputRolesCount;

        @JsonProperty("required_roles_count")
        private Integer requiredRolesCount;

        @JsonProperty("optional_roles_count")
        private Integer optionalRolesCount;

        @JsonProperty("role_arg_mapping_count")
        private Integer roleArgMappingCount;

        @JsonProperty("slot_schema_view_version")
        private String slotSchemaViewVersion;

        @JsonProperty("stable_defaults")
        private StableDefaults stableDefaults;

        public String getCapabilityKey() {
            return capabilityKey;
        }

        public void setCapabilityKey(String capabilityKey) {
            this.capabilityKey = capabilityKey;
        }

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

        public Integer getRequiredRolesCount() {
            return requiredRolesCount;
        }

        public void setRequiredRolesCount(Integer requiredRolesCount) {
            this.requiredRolesCount = requiredRolesCount;
        }

        public Integer getOptionalRolesCount() {
            return optionalRolesCount;
        }

        public void setOptionalRolesCount(Integer optionalRolesCount) {
            this.optionalRolesCount = optionalRolesCount;
        }

        public Integer getRoleArgMappingCount() {
            return roleArgMappingCount;
        }

        public void setRoleArgMappingCount(Integer roleArgMappingCount) {
            this.roleArgMappingCount = roleArgMappingCount;
        }

        public String getSlotSchemaViewVersion() {
            return slotSchemaViewVersion;
        }

        public void setSlotSchemaViewVersion(String slotSchemaViewVersion) {
            this.slotSchemaViewVersion = slotSchemaViewVersion;
        }

        public StableDefaults getStableDefaults() {
            return stableDefaults;
        }

        public void setStableDefaults(StableDefaults stableDefaults) {
            this.stableDefaults = stableDefaults;
        }
    }

    public static class StableDefaults {
        @JsonProperty("analysis_template")
        private String analysisTemplate;

        @JsonProperty("root_depth_factor")
        private Double rootDepthFactor;

        @JsonProperty("pawc_factor")
        private Double pawcFactor;

        public String getAnalysisTemplate() {
            return analysisTemplate;
        }

        public void setAnalysisTemplate(String analysisTemplate) {
            this.analysisTemplate = analysisTemplate;
        }

        public Double getRootDepthFactor() {
            return rootDepthFactor;
        }

        public void setRootDepthFactor(Double rootDepthFactor) {
            this.rootDepthFactor = rootDepthFactor;
        }

        public Double getPawcFactor() {
            return pawcFactor;
        }

        public void setPawcFactor(Double pawcFactor) {
            this.pawcFactor = pawcFactor;
        }
    }

    public static class GoalParseSummary {
        @JsonProperty("goal_type")
        private String goalType;

        @JsonProperty("user_query")
        private String userQuery;

        @JsonProperty("analysis_kind")
        private String analysisKind;

        @JsonProperty("intent_mode")
        private String intentMode;

        private String source;

        private java.util.List<String> entities;

        public String getGoalType() {
            return goalType;
        }

        public void setGoalType(String goalType) {
            this.goalType = goalType;
        }

        public String getUserQuery() {
            return userQuery;
        }

        public void setUserQuery(String userQuery) {
            this.userQuery = userQuery;
        }

        public String getAnalysisKind() {
            return analysisKind;
        }

        public void setAnalysisKind(String analysisKind) {
            this.analysisKind = analysisKind;
        }

        public String getIntentMode() {
            return intentMode;
        }

        public void setIntentMode(String intentMode) {
            this.intentMode = intentMode;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public java.util.List<String> getEntities() {
            return entities;
        }

        public void setEntities(java.util.List<String> entities) {
            this.entities = entities;
        }
    }

    public static class SkillRouteSummary {
        @JsonProperty("route_mode")
        private String routeMode;

        @JsonProperty("primary_skill")
        private String primarySkill;

        @JsonProperty("skill_id")
        private String skillId;

        @JsonProperty("skill_version")
        private String skillVersion;

        @JsonProperty("capability_key")
        private String capabilityKey;

        @JsonProperty("route_source")
        private String routeSource;

        private Double confidence;

        @JsonProperty("selected_template")
        private String selectedTemplate;

        @JsonProperty("template_version")
        private String templateVersion;

        @JsonProperty("execution_mode")
        private String executionMode;

        @JsonProperty("provider_preference")
        private String providerPreference;

        @JsonProperty("runtime_profile_preference")
        private String runtimeProfilePreference;

        private String source;

        public String getRouteMode() {
            return routeMode;
        }

        public void setRouteMode(String routeMode) {
            this.routeMode = routeMode;
        }

        public String getPrimarySkill() {
            return primarySkill;
        }

        public void setPrimarySkill(String primarySkill) {
            this.primarySkill = primarySkill;
        }

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

        public String getRouteSource() {
            return routeSource;
        }

        public void setRouteSource(String routeSource) {
            this.routeSource = routeSource;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
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

        public String getExecutionMode() {
            return executionMode;
        }

        public void setExecutionMode(String executionMode) {
            this.executionMode = executionMode;
        }

        public String getProviderPreference() {
            return providerPreference;
        }

        public void setProviderPreference(String providerPreference) {
            this.providerPreference = providerPreference;
        }

        public String getRuntimeProfilePreference() {
            return runtimeProfilePreference;
        }

        public void setRuntimeProfilePreference(String runtimeProfilePreference) {
            this.runtimeProfilePreference = runtimeProfilePreference;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }

    public static class SlotBindingsSummary {
        @JsonProperty("bound_slots_count")
        private Integer boundSlotsCount;

        @JsonProperty("bound_role_names")
        private java.util.List<String> boundRoleNames;

        public Integer getBoundSlotsCount() {
            return boundSlotsCount;
        }

        public void setBoundSlotsCount(Integer boundSlotsCount) {
            this.boundSlotsCount = boundSlotsCount;
        }

        public java.util.List<String> getBoundRoleNames() {
            return boundRoleNames;
        }

        public void setBoundRoleNames(java.util.List<String> boundRoleNames) {
            this.boundRoleNames = boundRoleNames;
        }
    }

    public static class ArgsDraftSummary {
        @JsonProperty("param_count")
        private Integer paramCount;

        @JsonProperty("param_keys")
        private java.util.List<String> paramKeys;

        public Integer getParamCount() {
            return paramCount;
        }

        public void setParamCount(Integer paramCount) {
            this.paramCount = paramCount;
        }

        public java.util.List<String> getParamKeys() {
            return paramKeys;
        }

        public void setParamKeys(java.util.List<String> paramKeys) {
            this.paramKeys = paramKeys;
        }
    }

    public static class ValidationSummary {
        @JsonProperty("is_valid")
        private Boolean isValid;

        @JsonProperty("missing_roles")
        private java.util.List<String> missingRoles;

        @JsonProperty("missing_params")
        private java.util.List<String> missingParams;

        @JsonProperty("error_code")
        private String errorCode;

        @JsonProperty("invalid_bindings")
        private java.util.List<String> invalidBindings;

        public Boolean getIsValid() {
            return isValid;
        }

        public void setIsValid(Boolean isValid) {
            this.isValid = isValid;
        }

        public java.util.List<String> getMissingRoles() {
            return missingRoles;
        }

        public void setMissingRoles(java.util.List<String> missingRoles) {
            this.missingRoles = missingRoles;
        }

        public java.util.List<String> getMissingParams() {
            return missingParams;
        }

        public void setMissingParams(java.util.List<String> missingParams) {
            this.missingParams = missingParams;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public java.util.List<String> getInvalidBindings() {
            return invalidBindings;
        }

        public void setInvalidBindings(java.util.List<String> invalidBindings) {
            this.invalidBindings = invalidBindings;
        }
    }

    public static class Pass2Summary {
        private String planner;

        @JsonProperty("node_count")
        private Integer nodeCount;

        @JsonProperty("edge_count")
        private Integer edgeCount;

        @JsonProperty("validation_is_valid")
        private Boolean validationIsValid;

        @JsonProperty("validation_error_code")
        private String validationErrorCode;

        @JsonProperty("capability_key")
        private String capabilityKey;

        private String template;

        @JsonProperty("runtime_assertion_count")
        private Integer runtimeAssertionCount;

        @JsonProperty("graph_digest")
        private String graphDigest;

        @JsonProperty("canonicalization_summary")
        private Map<String, Object> canonicalizationSummary;

        @JsonProperty("rewrite_summary")
        private Map<String, Object> rewriteSummary;

        public String getPlanner() {
            return planner;
        }

        public void setPlanner(String planner) {
            this.planner = planner;
        }

        public Integer getNodeCount() {
            return nodeCount;
        }

        public void setNodeCount(Integer nodeCount) {
            this.nodeCount = nodeCount;
        }

        public Integer getEdgeCount() {
            return edgeCount;
        }

        public void setEdgeCount(Integer edgeCount) {
            this.edgeCount = edgeCount;
        }

        public Boolean getValidationIsValid() {
            return validationIsValid;
        }

        public void setValidationIsValid(Boolean validationIsValid) {
            this.validationIsValid = validationIsValid;
        }

        public String getValidationErrorCode() {
            return validationErrorCode;
        }

        public void setValidationErrorCode(String validationErrorCode) {
            this.validationErrorCode = validationErrorCode;
        }

        public String getCapabilityKey() {
            return capabilityKey;
        }

        public void setCapabilityKey(String capabilityKey) {
            this.capabilityKey = capabilityKey;
        }

        public String getTemplate() {
            return template;
        }

        public void setTemplate(String template) {
            this.template = template;
        }

        public Integer getRuntimeAssertionCount() {
            return runtimeAssertionCount;
        }

        public void setRuntimeAssertionCount(Integer runtimeAssertionCount) {
            this.runtimeAssertionCount = runtimeAssertionCount;
        }

        public String getGraphDigest() {
            return graphDigest;
        }

        public void setGraphDigest(String graphDigest) {
            this.graphDigest = graphDigest;
        }

        public Map<String, Object> getCanonicalizationSummary() {
            return canonicalizationSummary;
        }

        public void setCanonicalizationSummary(Map<String, Object> canonicalizationSummary) {
            this.canonicalizationSummary = canonicalizationSummary;
        }

        public Map<String, Object> getRewriteSummary() {
            return rewriteSummary;
        }

        public void setRewriteSummary(Map<String, Object> rewriteSummary) {
            this.rewriteSummary = rewriteSummary;
        }
    }

    public static class ResultObjectSummary {
        @JsonProperty("result_id")
        private String resultId;

        private String summary;

        @JsonProperty("artifact_count")
        private Integer artifactCount;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("assertion_id")
        private String assertionId;

        @JsonProperty("node_id")
        private String nodeId;

        private Boolean repairable;

        private Map<String, Object> details;

        public String getResultId() {
            return resultId;
        }

        public void setResultId(String resultId) {
            this.resultId = resultId;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Integer getArtifactCount() {
            return artifactCount;
        }

        public void setArtifactCount(Integer artifactCount) {
            this.artifactCount = artifactCount;
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
            this.assertionId = assertionId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
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
            this.details = details;
        }
    }

    public static class ResultBundleSummary {
        @JsonProperty("result_id")
        private String resultId;

        private String summary;

        @JsonProperty("main_output_count")
        private Integer mainOutputCount;

        @JsonProperty("main_outputs")
        private java.util.List<String> mainOutputs;

        @JsonProperty("primary_outputs")
        private java.util.List<String> primaryOutputs;

        @JsonProperty("audit_artifacts")
        private java.util.List<String> auditArtifacts;

        @JsonProperty("created_at")
        private String createdAt;

        public String getResultId() {
            return resultId;
        }

        public void setResultId(String resultId) {
            this.resultId = resultId;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Integer getMainOutputCount() {
            return mainOutputCount;
        }

        public void setMainOutputCount(Integer mainOutputCount) {
            this.mainOutputCount = mainOutputCount;
        }

        public java.util.List<String> getMainOutputs() {
            return mainOutputs;
        }

        public void setMainOutputs(java.util.List<String> mainOutputs) {
            this.mainOutputs = mainOutputs;
        }

        public java.util.List<String> getPrimaryOutputs() {
            return primaryOutputs;
        }

        public void setPrimaryOutputs(java.util.List<String> primaryOutputs) {
            this.primaryOutputs = primaryOutputs;
        }

        public java.util.List<String> getAuditArtifacts() {
            return auditArtifacts;
        }

        public void setAuditArtifacts(java.util.List<String> auditArtifacts) {
            this.auditArtifacts = auditArtifacts;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class FinalExplanationSummary {
        private String title;

        @JsonProperty("highlight_count")
        private Integer highlightCount;

        @JsonProperty("generated_at")
        private String generatedAt;

        private Boolean available;

        @JsonProperty("failure_code")
        private String failureCode;

        @JsonProperty("failure_message")
        private String failureMessage;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getHighlightCount() {
            return highlightCount;
        }

        public void setHighlightCount(Integer highlightCount) {
            this.highlightCount = highlightCount;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(String generatedAt) {
            this.generatedAt = generatedAt;
        }

        public Boolean getAvailable() {
            return available;
        }

        public void setAvailable(Boolean available) {
            this.available = available;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }

    public static class FailureSummary {
        @JsonProperty("failure_code")
        private String failureCode;

        @JsonProperty("failure_message")
        private String failureMessage;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("assertion_id")
        private String assertionId;

        @JsonProperty("node_id")
        private String nodeId;

        private Boolean repairable;

        private Map<String, Object> details;

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
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
            this.assertionId = assertionId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
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
            this.details = details;
        }
    }

    public static class WaitingContext {
        @JsonProperty("waiting_reason_type")
        private String waitingReasonType;

        @JsonProperty("missing_slots")
        private java.util.List<MissingSlot> missingSlots;

        @JsonProperty("invalid_bindings")
        private java.util.List<String> invalidBindings;

        @JsonProperty("required_user_actions")
        private java.util.List<RequiredUserAction> requiredUserActions;

        @JsonProperty("resume_hint")
        private String resumeHint;

        @JsonProperty("can_resume")
        private Boolean canResume;

        public String getWaitingReasonType() {
            return waitingReasonType;
        }

        public void setWaitingReasonType(String waitingReasonType) {
            this.waitingReasonType = waitingReasonType;
        }

        public java.util.List<MissingSlot> getMissingSlots() {
            return missingSlots;
        }

        public void setMissingSlots(java.util.List<MissingSlot> missingSlots) {
            this.missingSlots = missingSlots;
        }

        public java.util.List<String> getInvalidBindings() {
            return invalidBindings;
        }

        public void setInvalidBindings(java.util.List<String> invalidBindings) {
            this.invalidBindings = invalidBindings;
        }

        public java.util.List<RequiredUserAction> getRequiredUserActions() {
            return requiredUserActions;
        }

        public void setRequiredUserActions(java.util.List<RequiredUserAction> requiredUserActions) {
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
    }

    public static class MissingSlot {
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

    public static class RequiredUserAction {
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

    public static class RepairProposal {
        private Boolean available;

        @JsonProperty("user_facing_reason")
        private String userFacingReason;

        @JsonProperty("resume_hint")
        private String resumeHint;

        @JsonProperty("action_explanations")
        private java.util.List<RepairActionExplanation> actionExplanations;

        private java.util.List<String> notes;

        @JsonProperty("failure_code")
        private String failureCode;

        @JsonProperty("failure_message")
        private String failureMessage;

        public Boolean getAvailable() {
            return available;
        }

        public void setAvailable(Boolean available) {
            this.available = available;
        }

        public String getUserFacingReason() {
            return userFacingReason;
        }

        public void setUserFacingReason(String userFacingReason) {
            this.userFacingReason = userFacingReason;
        }

        public String getResumeHint() {
            return resumeHint;
        }

        public void setResumeHint(String resumeHint) {
            this.resumeHint = resumeHint;
        }

        public java.util.List<RepairActionExplanation> getActionExplanations() {
            return actionExplanations;
        }

        public void setActionExplanations(java.util.List<RepairActionExplanation> actionExplanations) {
            this.actionExplanations = actionExplanations;
        }

        public java.util.List<String> getNotes() {
            return notes;
        }

        public void setNotes(java.util.List<String> notes) {
            this.notes = notes;
        }

        public String getFailureCode() {
            return failureCode;
        }

        public void setFailureCode(String failureCode) {
            this.failureCode = failureCode;
        }

        public String getFailureMessage() {
            return failureMessage;
        }

        public void setFailureMessage(String failureMessage) {
            this.failureMessage = failureMessage;
        }
    }

    public static class RepairActionExplanation {
        private String key;

        private String message;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
