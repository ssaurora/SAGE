package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class TaskManifestResponse {
    @JsonProperty("manifest_id")
    private String manifestId;

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("attempt_no")
    private Integer attemptNo;

    @JsonProperty("manifest_version")
    private Integer manifestVersion;

    @JsonProperty("freeze_status")
    private String freezeStatus;

    @JsonProperty("planning_revision")
    private Integer planningRevision;

    @JsonProperty("checkpoint_version")
    private Integer checkpointVersion;

    @JsonProperty("graph_digest")
    private String graphDigest;

    @JsonProperty("planning_summary")
    private Map<String, Object> planningSummary;

    @JsonProperty("canonicalization_summary")
    private Map<String, Object> canonicalizationSummary;

    @JsonProperty("rewrite_summary")
    private Map<String, Object> rewriteSummary;

    @JsonProperty("resume_transaction")
    private ResumeTransactionView resumeTransaction;

    @JsonProperty("corruption_state")
    private CorruptionStateView corruptionState;

    @JsonProperty("promotion_status")
    private String promotionStatus;

    @JsonProperty("planning_intent_status")
    private String planningIntentStatus;

    @JsonProperty("binding_status")
    private String bindingStatus;

    @JsonProperty("overruled_fields")
    private List<String> overruledFields;

    @JsonProperty("blocked_mutations")
    private List<String> blockedMutations;

    @JsonProperty("assembly_blocked")
    private Boolean assemblyBlocked;

    @JsonProperty("cognition_verdict")
    private String cognitionVerdict;

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

    @JsonProperty("goal_parse")
    private GoalParse goalParse;

    @JsonProperty("skill_route")
    private SkillRoute skillRoute;

    @JsonProperty("capability_key")
    private String capabilityKey;

    @JsonProperty("capability_facts")
    private CapabilityFacts capabilityFacts;

    @JsonProperty("logical_input_roles")
    private List<LogicalInputRole> logicalInputRoles;

    @JsonProperty("role_arg_mappings")
    private List<RoleArgMapping> roleArgMappings;

    @JsonProperty("stable_defaults")
    private StableDefaults stableDefaults;

    @JsonProperty("slot_schema_view")
    private SlotSchemaView slotSchemaView;

    @JsonProperty("slot_bindings")
    private List<SlotBinding> slotBindings;

    @JsonProperty("args_draft")
    private Map<String, Object> argsDraft;

    @JsonProperty("validation_summary")
    private ValidationSummary validationSummary;

    @JsonProperty("execution_graph")
    private ExecutionGraph executionGraph;

    @JsonProperty("runtime_assertions")
    private List<RuntimeAssertion> runtimeAssertions;

    @JsonProperty("created_at")
    private String createdAt;

    public String getManifestId() { return manifestId; }
    public void setManifestId(String manifestId) { this.manifestId = manifestId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public Integer getManifestVersion() { return manifestVersion; }
    public void setManifestVersion(Integer manifestVersion) { this.manifestVersion = manifestVersion; }
    public String getFreezeStatus() { return freezeStatus; }
    public void setFreezeStatus(String freezeStatus) { this.freezeStatus = freezeStatus; }
    public Integer getPlanningRevision() { return planningRevision; }
    public void setPlanningRevision(Integer planningRevision) { this.planningRevision = planningRevision; }
    public Integer getCheckpointVersion() { return checkpointVersion; }
    public void setCheckpointVersion(Integer checkpointVersion) { this.checkpointVersion = checkpointVersion; }
    public String getGraphDigest() { return graphDigest; }
    public void setGraphDigest(String graphDigest) { this.graphDigest = graphDigest; }
    public Map<String, Object> getPlanningSummary() { return planningSummary; }
    public void setPlanningSummary(Map<String, Object> planningSummary) { this.planningSummary = planningSummary; }
    public Map<String, Object> getCanonicalizationSummary() { return canonicalizationSummary; }
    public void setCanonicalizationSummary(Map<String, Object> canonicalizationSummary) { this.canonicalizationSummary = canonicalizationSummary; }
    public Map<String, Object> getRewriteSummary() { return rewriteSummary; }
    public void setRewriteSummary(Map<String, Object> rewriteSummary) { this.rewriteSummary = rewriteSummary; }
    public ResumeTransactionView getResumeTransaction() { return resumeTransaction; }
    public void setResumeTransaction(ResumeTransactionView resumeTransaction) { this.resumeTransaction = resumeTransaction; }
    public CorruptionStateView getCorruptionState() { return corruptionState; }
    public void setCorruptionState(CorruptionStateView corruptionState) { this.corruptionState = corruptionState; }
    public String getPromotionStatus() { return promotionStatus; }
    public void setPromotionStatus(String promotionStatus) { this.promotionStatus = promotionStatus; }
    public String getPlanningIntentStatus() { return planningIntentStatus; }
    public void setPlanningIntentStatus(String planningIntentStatus) { this.planningIntentStatus = planningIntentStatus; }
    public String getBindingStatus() { return bindingStatus; }
    public void setBindingStatus(String bindingStatus) { this.bindingStatus = bindingStatus; }
    public List<String> getOverruledFields() { return overruledFields; }
    public void setOverruledFields(List<String> overruledFields) { this.overruledFields = overruledFields; }
    public List<String> getBlockedMutations() { return blockedMutations; }
    public void setBlockedMutations(List<String> blockedMutations) { this.blockedMutations = blockedMutations; }
    public Boolean getAssemblyBlocked() { return assemblyBlocked; }
    public void setAssemblyBlocked(Boolean assemblyBlocked) { this.assemblyBlocked = assemblyBlocked; }
    public String getCognitionVerdict() { return cognitionVerdict; }
    public void setCognitionVerdict(String cognitionVerdict) { this.cognitionVerdict = cognitionVerdict; }
    public Map<String, Object> getGoalRouteCognition() { return goalRouteCognition; }
    public void setGoalRouteCognition(Map<String, Object> goalRouteCognition) { this.goalRouteCognition = goalRouteCognition; }
    public Map<String, Object> getGoalRouteOutput() { return goalRouteOutput; }
    public void setGoalRouteOutput(Map<String, Object> goalRouteOutput) { this.goalRouteOutput = goalRouteOutput; }
    public Map<String, Object> getPassbCognition() { return passbCognition; }
    public void setPassbCognition(Map<String, Object> passbCognition) { this.passbCognition = passbCognition; }
    public Map<String, Object> getPassbOutput() { return passbOutput; }
    public void setPassbOutput(Map<String, Object> passbOutput) { this.passbOutput = passbOutput; }
    public Map<String, Object> getRepairProposalCognition() { return repairProposalCognition; }
    public void setRepairProposalCognition(Map<String, Object> repairProposalCognition) { this.repairProposalCognition = repairProposalCognition; }
    public Map<String, Object> getRepairProposalOutput() { return repairProposalOutput; }
    public void setRepairProposalOutput(Map<String, Object> repairProposalOutput) { this.repairProposalOutput = repairProposalOutput; }
    public Map<String, Object> getFinalExplanationCognition() { return finalExplanationCognition; }
    public void setFinalExplanationCognition(Map<String, Object> finalExplanationCognition) { this.finalExplanationCognition = finalExplanationCognition; }
    public Map<String, Object> getFinalExplanationOutput() { return finalExplanationOutput; }
    public void setFinalExplanationOutput(Map<String, Object> finalExplanationOutput) { this.finalExplanationOutput = finalExplanationOutput; }
    public GoalParse getGoalParse() { return goalParse; }
    public void setGoalParse(GoalParse goalParse) { this.goalParse = goalParse; }
    public SkillRoute getSkillRoute() { return skillRoute; }
    public void setSkillRoute(SkillRoute skillRoute) { this.skillRoute = skillRoute; }
    public String getCapabilityKey() { return capabilityKey; }
    public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
    public CapabilityFacts getCapabilityFacts() { return capabilityFacts; }
    public void setCapabilityFacts(CapabilityFacts capabilityFacts) { this.capabilityFacts = capabilityFacts; }
    public List<LogicalInputRole> getLogicalInputRoles() { return logicalInputRoles; }
    public void setLogicalInputRoles(List<LogicalInputRole> logicalInputRoles) { this.logicalInputRoles = logicalInputRoles; }
    public List<RoleArgMapping> getRoleArgMappings() { return roleArgMappings; }
    public void setRoleArgMappings(List<RoleArgMapping> roleArgMappings) { this.roleArgMappings = roleArgMappings; }
    public StableDefaults getStableDefaults() { return stableDefaults; }
    public void setStableDefaults(StableDefaults stableDefaults) { this.stableDefaults = stableDefaults; }
    public SlotSchemaView getSlotSchemaView() { return slotSchemaView; }
    public void setSlotSchemaView(SlotSchemaView slotSchemaView) { this.slotSchemaView = slotSchemaView; }
    public List<SlotBinding> getSlotBindings() { return slotBindings; }
    public void setSlotBindings(List<SlotBinding> slotBindings) { this.slotBindings = slotBindings; }
    public Map<String, Object> getArgsDraft() { return argsDraft; }
    public void setArgsDraft(Map<String, Object> argsDraft) { this.argsDraft = argsDraft; }
    public ValidationSummary getValidationSummary() { return validationSummary; }
    public void setValidationSummary(ValidationSummary validationSummary) { this.validationSummary = validationSummary; }
    public ExecutionGraph getExecutionGraph() { return executionGraph; }
    public void setExecutionGraph(ExecutionGraph executionGraph) { this.executionGraph = executionGraph; }
    public List<RuntimeAssertion> getRuntimeAssertions() { return runtimeAssertions; }
    public void setRuntimeAssertions(List<RuntimeAssertion> runtimeAssertions) { this.runtimeAssertions = runtimeAssertions; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public static class GoalParse {
        @JsonProperty("goal_type")
        private String goalType;

        @JsonProperty("user_query")
        private String userQuery;

        @JsonProperty("analysis_kind")
        private String analysisKind;

        @JsonProperty("intent_mode")
        private String intentMode;

        private List<String> entities;

        private String source;

        public String getGoalType() { return goalType; }
        public void setGoalType(String goalType) { this.goalType = goalType; }
        public String getUserQuery() { return userQuery; }
        public void setUserQuery(String userQuery) { this.userQuery = userQuery; }
        public String getAnalysisKind() { return analysisKind; }
        public void setAnalysisKind(String analysisKind) { this.analysisKind = analysisKind; }
        public String getIntentMode() { return intentMode; }
        public void setIntentMode(String intentMode) { this.intentMode = intentMode; }
        public List<String> getEntities() { return entities; }
        public void setEntities(List<String> entities) { this.entities = entities; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class SkillRoute {
        @JsonProperty("route_mode")
        private String routeMode;

        @JsonProperty("primary_skill")
        private String primarySkill;

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

        public String getRouteMode() { return routeMode; }
        public void setRouteMode(String routeMode) { this.routeMode = routeMode; }
        public String getPrimarySkill() { return primarySkill; }
        public void setPrimarySkill(String primarySkill) { this.primarySkill = primarySkill; }
        public String getCapabilityKey() { return capabilityKey; }
        public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
        public String getRouteSource() { return routeSource; }
        public void setRouteSource(String routeSource) { this.routeSource = routeSource; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getSelectedTemplate() { return selectedTemplate; }
        public void setSelectedTemplate(String selectedTemplate) { this.selectedTemplate = selectedTemplate; }
        public String getTemplateVersion() { return templateVersion; }
        public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
        public String getExecutionMode() { return executionMode; }
        public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }
        public String getProviderPreference() { return providerPreference; }
        public void setProviderPreference(String providerPreference) { this.providerPreference = providerPreference; }
        public String getRuntimeProfilePreference() { return runtimeProfilePreference; }
        public void setRuntimeProfilePreference(String runtimeProfilePreference) { this.runtimeProfilePreference = runtimeProfilePreference; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class CapabilityFacts {
        @JsonProperty("capability_key")
        private String capabilityKey;

        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("validation_hints")
        private List<CapabilityValidationHint> validationHints;

        @JsonProperty("repair_hints")
        private List<CapabilityRepairHint> repairHints;

        @JsonProperty("output_contract")
        private CapabilityOutputContract outputContract;

        @JsonProperty("runtime_profile_hint")
        private String runtimeProfileHint;

        public String getCapabilityKey() { return capabilityKey; }
        public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<CapabilityValidationHint> getValidationHints() { return validationHints; }
        public void setValidationHints(List<CapabilityValidationHint> validationHints) { this.validationHints = validationHints; }
        public List<CapabilityRepairHint> getRepairHints() { return repairHints; }
        public void setRepairHints(List<CapabilityRepairHint> repairHints) { this.repairHints = repairHints; }
        public CapabilityOutputContract getOutputContract() { return outputContract; }
        public void setOutputContract(CapabilityOutputContract outputContract) { this.outputContract = outputContract; }
        public String getRuntimeProfileHint() { return runtimeProfileHint; }
        public void setRuntimeProfileHint(String runtimeProfileHint) { this.runtimeProfileHint = runtimeProfileHint; }
    }

    public static class CapabilityValidationHint {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("expected_slot_type")
        private String expectedSlotType;

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getExpectedSlotType() { return expectedSlotType; }
        public void setExpectedSlotType(String expectedSlotType) { this.expectedSlotType = expectedSlotType; }
    }

    public static class CapabilityRepairHint {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("action_type")
        private String actionType;

        @JsonProperty("action_key")
        private String actionKey;

        @JsonProperty("action_label")
        private String actionLabel;

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getActionKey() { return actionKey; }
        public void setActionKey(String actionKey) { this.actionKey = actionKey; }
        public String getActionLabel() { return actionLabel; }
        public void setActionLabel(String actionLabel) { this.actionLabel = actionLabel; }
    }

    public static class CapabilityOutputContract {
        private List<CapabilityOutputItem> outputs;

        public List<CapabilityOutputItem> getOutputs() { return outputs; }
        public void setOutputs(List<CapabilityOutputItem> outputs) { this.outputs = outputs; }
    }

    public static class CapabilityOutputItem {
        @JsonProperty("artifact_role")
        private String artifactRole;

        @JsonProperty("logical_name")
        private String logicalName;

        public String getArtifactRole() { return artifactRole; }
        public void setArtifactRole(String artifactRole) { this.artifactRole = artifactRole; }
        public String getLogicalName() { return logicalName; }
        public void setLogicalName(String logicalName) { this.logicalName = logicalName; }
    }

    public static class LogicalInputRole {
        @JsonProperty("role_name")
        private String roleName;

        private Boolean required;

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
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

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getSlotArgKey() { return slotArgKey; }
        public void setSlotArgKey(String slotArgKey) { this.slotArgKey = slotArgKey; }
        public String getValueArgKey() { return valueArgKey; }
        public void setValueArgKey(String valueArgKey) { this.valueArgKey = valueArgKey; }
        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }
    }

    public static class StableDefaults {
        @JsonProperty("analysis_template")
        private String analysisTemplate;

        @JsonProperty("root_depth_factor")
        private Double rootDepthFactor;

        @JsonProperty("pawc_factor")
        private Double pawcFactor;

        public String getAnalysisTemplate() { return analysisTemplate; }
        public void setAnalysisTemplate(String analysisTemplate) { this.analysisTemplate = analysisTemplate; }
        public Double getRootDepthFactor() { return rootDepthFactor; }
        public void setRootDepthFactor(Double rootDepthFactor) { this.rootDepthFactor = rootDepthFactor; }
        public Double getPawcFactor() { return pawcFactor; }
        public void setPawcFactor(Double pawcFactor) { this.pawcFactor = pawcFactor; }
    }

    public static class SlotSchemaView {
        private List<SlotSchemaItem> slots;

        public List<SlotSchemaItem> getSlots() { return slots; }
        public void setSlots(List<SlotSchemaItem> slots) { this.slots = slots; }
    }

    public static class SlotSchemaItem {
        @JsonProperty("slot_name")
        private String slotName;

        private String type;

        @JsonProperty("bound_role")
        private String boundRole;

        public String getSlotName() { return slotName; }
        public void setSlotName(String slotName) { this.slotName = slotName; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBoundRole() { return boundRole; }
        public void setBoundRole(String boundRole) { this.boundRole = boundRole; }
    }

    public static class SlotBinding {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("slot_name")
        private String slotName;

        private String source;

        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getSlotName() { return slotName; }
        public void setSlotName(String slotName) { this.slotName = slotName; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class ValidationSummary {
        @JsonProperty("is_valid")
        private Boolean isValid;

        @JsonProperty("missing_roles")
        private List<String> missingRoles;

        @JsonProperty("missing_params")
        private List<String> missingParams;

        @JsonProperty("error_code")
        private String errorCode;

        @JsonProperty("invalid_bindings")
        private List<String> invalidBindings;

        public Boolean getIsValid() { return isValid; }
        public void setIsValid(Boolean isValid) { this.isValid = isValid; }
        public List<String> getMissingRoles() { return missingRoles; }
        public void setMissingRoles(List<String> missingRoles) { this.missingRoles = missingRoles; }
        public List<String> getMissingParams() { return missingParams; }
        public void setMissingParams(List<String> missingParams) { this.missingParams = missingParams; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public List<String> getInvalidBindings() { return invalidBindings; }
        public void setInvalidBindings(List<String> invalidBindings) { this.invalidBindings = invalidBindings; }
    }

    public static class ExecutionGraph {
        private List<ExecutionNode> nodes;
        private List<List<String>> edges;

        public List<ExecutionNode> getNodes() { return nodes; }
        public void setNodes(List<ExecutionNode> nodes) { this.nodes = nodes; }
        public List<List<String>> getEdges() { return edges; }
        public void setEdges(List<List<String>> edges) { this.edges = edges; }
    }

    public static class ExecutionNode {
        @JsonProperty("node_id")
        private String nodeId;

        private String kind;

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
    }

    public static class RuntimeAssertion {
        @JsonProperty("assertion_id")
        private String assertionId;
        private String name;
        private Boolean required;
        private String message;
        @JsonProperty("assertion_type")
        private String assertionType;
        @JsonProperty("node_id")
        private String nodeId;
        @JsonProperty("target_key")
        private String targetKey;
        @JsonProperty("expected_value")
        private String expectedValue;
        private Boolean repairable;
        private Map<String, Object> details;

        public String getAssertionId() { return assertionId; }
        public void setAssertionId(String assertionId) { this.assertionId = assertionId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getRequired() { return required; }
        public void setRequired(Boolean required) { this.required = required; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getAssertionType() { return assertionType; }
        public void setAssertionType(String assertionType) { this.assertionType = assertionType; }
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getTargetKey() { return targetKey; }
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
        public String getExpectedValue() { return expectedValue; }
        public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
        public Boolean getRepairable() { return repairable; }
        public void setRepairable(Boolean repairable) { this.repairable = repairable; }
        public Map<String, Object> getDetails() { return details; }
        public void setDetails(Map<String, Object> details) { this.details = details; }
    }
}
