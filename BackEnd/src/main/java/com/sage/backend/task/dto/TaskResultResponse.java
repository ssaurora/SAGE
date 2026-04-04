package com.sage.backend.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class TaskResultResponse {
    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("task_state")
    private String taskState;

    @JsonProperty("job_state")
    private String jobState;

    @JsonProperty("provider_key")
    private String providerKey;

    @JsonProperty("runtime_profile")
    private String runtimeProfile;

    @JsonProperty("case_id")
    private String caseId;

    @JsonProperty("skill_id")
    private String skillId;

    @JsonProperty("skill_version")
    private String skillVersion;

    @JsonProperty("resume_transaction")
    private ResumeTransactionView resumeTransaction;

    @JsonProperty("corruption_state")
    private CorruptionStateView corruptionState;

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

    @JsonProperty("catalog_summary")
    private Map<String, Object> catalogSummary;

    @JsonProperty("catalog_consistency")
    private Map<String, Object> catalogConsistency;

    @JsonProperty("canonicalization_summary")
    private Map<String, Object> canonicalizationSummary;

    @JsonProperty("rewrite_summary")
    private Map<String, Object> rewriteSummary;

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

    @JsonProperty("result_bundle")
    private ResultBundle resultBundle;

    @JsonProperty("final_explanation")
    private FinalExplanation finalExplanation;

    @JsonProperty("failure_summary")
    private FailureSummary failureSummary;

    @JsonProperty("docker_runtime_evidence")
    private DockerRuntimeEvidence dockerRuntimeEvidence;

    @JsonProperty("workspace_summary")
    private WorkspaceSummary workspaceSummary;

    @JsonProperty("artifact_catalog")
    private ArtifactCatalog artifactCatalog;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTaskState() {
        return taskState;
    }

    public void setTaskState(String taskState) {
        this.taskState = taskState;
    }

    public String getJobState() {
        return jobState;
    }

    public void setJobState(String jobState) {
        this.jobState = jobState;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void setProviderKey(String providerKey) {
        this.providerKey = providerKey;
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

    public String getFreezeStatus() {
        return freezeStatus;
    }

    public void setFreezeStatus(String freezeStatus) {
        this.freezeStatus = freezeStatus;
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

    public Map<String, Object> getCatalogSummary() {
        return catalogSummary;
    }

    public void setCatalogSummary(Map<String, Object> catalogSummary) {
        this.catalogSummary = catalogSummary;
    }

    public Map<String, Object> getCatalogConsistency() {
        return catalogConsistency;
    }

    public void setCatalogConsistency(Map<String, Object> catalogConsistency) {
        this.catalogConsistency = catalogConsistency;
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

    public String getPromotionStatus() {
        return promotionStatus;
    }

    public void setPromotionStatus(String promotionStatus) {
        this.promotionStatus = promotionStatus;
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

    public List<String> getOverruledFields() {
        return overruledFields;
    }

    public void setOverruledFields(List<String> overruledFields) {
        this.overruledFields = overruledFields;
    }

    public List<String> getBlockedMutations() {
        return blockedMutations;
    }

    public void setBlockedMutations(List<String> blockedMutations) {
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

    public ResultBundle getResultBundle() {
        return resultBundle;
    }

    public void setResultBundle(ResultBundle resultBundle) {
        this.resultBundle = resultBundle;
    }

    public FinalExplanation getFinalExplanation() {
        return finalExplanation;
    }

    public void setFinalExplanation(FinalExplanation finalExplanation) {
        this.finalExplanation = finalExplanation;
    }

    public FailureSummary getFailureSummary() {
        return failureSummary;
    }

    public void setFailureSummary(FailureSummary failureSummary) {
        this.failureSummary = failureSummary;
    }

    public DockerRuntimeEvidence getDockerRuntimeEvidence() {
        return dockerRuntimeEvidence;
    }

    public void setDockerRuntimeEvidence(DockerRuntimeEvidence dockerRuntimeEvidence) {
        this.dockerRuntimeEvidence = dockerRuntimeEvidence;
    }

    public WorkspaceSummary getWorkspaceSummary() {
        return workspaceSummary;
    }

    public void setWorkspaceSummary(WorkspaceSummary workspaceSummary) {
        this.workspaceSummary = workspaceSummary;
    }

    public ArtifactCatalog getArtifactCatalog() {
        return artifactCatalog;
    }

    public void setArtifactCatalog(ArtifactCatalog artifactCatalog) {
        this.artifactCatalog = artifactCatalog;
    }

    public static class ResultBundle {
        @JsonProperty("result_id")
        private String resultId;

        @JsonProperty("task_id")
        private String taskId;

        @JsonProperty("job_id")
        private String jobId;

        private String summary;

        private Map<String, Object> metrics;

        @JsonProperty("output_registry")
        private Map<String, Object> outputRegistry;

        @JsonProperty("primary_output_refs")
        private List<OutputReference> primaryOutputRefs;

        @JsonProperty("main_outputs")
        private List<String> mainOutputs;

        private List<String> artifacts;

        @JsonProperty("primary_outputs")
        private List<String> primaryOutputs;

        @JsonProperty("intermediate_outputs")
        private List<String> intermediateOutputs;

        @JsonProperty("audit_artifacts")
        private List<String> auditArtifacts;

        @JsonProperty("derived_outputs")
        private List<String> derivedOutputs;

        @JsonProperty("created_at")
        private String createdAt;

        public String getResultId() {
            return resultId;
        }

        public void setResultId(String resultId) {
            this.resultId = resultId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }

        public void setMetrics(Map<String, Object> metrics) {
            this.metrics = metrics;
        }

        public Map<String, Object> getOutputRegistry() {
            return outputRegistry;
        }

        public void setOutputRegistry(Map<String, Object> outputRegistry) {
            this.outputRegistry = outputRegistry;
        }

        public List<OutputReference> getPrimaryOutputRefs() {
            return primaryOutputRefs;
        }

        public void setPrimaryOutputRefs(List<OutputReference> primaryOutputRefs) {
            this.primaryOutputRefs = primaryOutputRefs;
        }

        public List<String> getMainOutputs() {
            return mainOutputs;
        }

        public void setMainOutputs(List<String> mainOutputs) {
            this.mainOutputs = mainOutputs;
        }

        public List<String> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(List<String> artifacts) {
            this.artifacts = artifacts;
        }

        public List<String> getPrimaryOutputs() {
            return primaryOutputs;
        }

        public void setPrimaryOutputs(List<String> primaryOutputs) {
            this.primaryOutputs = primaryOutputs;
        }

        public List<String> getIntermediateOutputs() {
            return intermediateOutputs;
        }

        public void setIntermediateOutputs(List<String> intermediateOutputs) {
            this.intermediateOutputs = intermediateOutputs;
        }

        public List<String> getAuditArtifacts() {
            return auditArtifacts;
        }

        public void setAuditArtifacts(List<String> auditArtifacts) {
            this.auditArtifacts = auditArtifacts;
        }

        public List<String> getDerivedOutputs() {
            return derivedOutputs;
        }

        public void setDerivedOutputs(List<String> derivedOutputs) {
            this.derivedOutputs = derivedOutputs;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class OutputReference {
        @JsonProperty("output_id")
        private String outputId;

        private String path;

        public String getOutputId() {
            return outputId;
        }

        public void setOutputId(String outputId) {
            this.outputId = outputId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class FinalExplanation {
        private Boolean available;

        private String title;

        private List<String> highlights;

        private String narrative;

        @JsonProperty("generated_at")
        private String generatedAt;

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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<String> getHighlights() {
            return highlights;
        }

        public void setHighlights(List<String> highlights) {
            this.highlights = highlights;
        }

        public String getNarrative() {
            return narrative;
        }

        public void setNarrative(String narrative) {
            this.narrative = narrative;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(String generatedAt) {
            this.generatedAt = generatedAt;
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

    public static class DockerRuntimeEvidence {
        @JsonProperty("container_name")
        private String containerName;

        private String image;

        @JsonProperty("workspace_output_path")
        private String workspaceOutputPath;

        @JsonProperty("result_file_exists")
        private Boolean resultFileExists;

        @JsonProperty("provider_key")
        private String providerKey;

        @JsonProperty("runtime_profile")
        private String runtimeProfile;

        @JsonProperty("case_id")
        private String caseId;

        @JsonProperty("case_descriptor_version")
        private String caseDescriptorVersion;

        @JsonProperty("contract_mode")
        private String contractMode;

        @JsonProperty("runtime_mode")
        private String runtimeMode;

        @JsonProperty("input_bindings")
        private List<InputBinding> inputBindings;

        @JsonProperty("promotion_status")
        private String promotionStatus;

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getWorkspaceOutputPath() {
            return workspaceOutputPath;
        }

        public void setWorkspaceOutputPath(String workspaceOutputPath) {
            this.workspaceOutputPath = workspaceOutputPath;
        }

        public Boolean getResultFileExists() {
            return resultFileExists;
        }

        public void setResultFileExists(Boolean resultFileExists) {
            this.resultFileExists = resultFileExists;
        }

        public String getProviderKey() {
            return providerKey;
        }

        public void setProviderKey(String providerKey) {
            this.providerKey = providerKey;
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

        public String getCaseDescriptorVersion() {
            return caseDescriptorVersion;
        }

        public void setCaseDescriptorVersion(String caseDescriptorVersion) {
            this.caseDescriptorVersion = caseDescriptorVersion;
        }

        public String getContractMode() {
            return contractMode;
        }

        public void setContractMode(String contractMode) {
            this.contractMode = contractMode;
        }

        public String getRuntimeMode() {
            return runtimeMode;
        }

        public void setRuntimeMode(String runtimeMode) {
            this.runtimeMode = runtimeMode;
        }

        public List<InputBinding> getInputBindings() {
            return inputBindings;
        }

        public void setInputBindings(List<InputBinding> inputBindings) {
            this.inputBindings = inputBindings;
        }

        public String getPromotionStatus() {
            return promotionStatus;
        }

        public void setPromotionStatus(String promotionStatus) {
            this.promotionStatus = promotionStatus;
        }
    }

    public static class InputBinding {
        @JsonProperty("role_name")
        private String roleName;

        @JsonProperty("slot_name")
        private String slotName;

        private String source;

        @JsonProperty("arg_key")
        private String argKey;

        @JsonProperty("provider_input_path")
        private String providerInputPath;

        @JsonProperty("source_ref")
        private String sourceRef;

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

        public String getArgKey() {
            return argKey;
        }

        public void setArgKey(String argKey) {
            this.argKey = argKey;
        }

        public String getProviderInputPath() {
            return providerInputPath;
        }

        public void setProviderInputPath(String providerInputPath) {
            this.providerInputPath = providerInputPath;
        }

        public String getSourceRef() {
            return sourceRef;
        }

        public void setSourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
        }
    }

    public static class WorkspaceSummary {
        @JsonProperty("workspace_id")
        private String workspaceId;

        @JsonProperty("workspace_output_path")
        private String workspaceOutputPath;

        @JsonProperty("archive_path")
        private String archivePath;

        @JsonProperty("cleanup_completed")
        private Boolean cleanupCompleted;

        @JsonProperty("archive_completed")
        private Boolean archiveCompleted;

        public String getWorkspaceId() {
            return workspaceId;
        }

        public void setWorkspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
        }

        public String getWorkspaceOutputPath() {
            return workspaceOutputPath;
        }

        public void setWorkspaceOutputPath(String workspaceOutputPath) {
            this.workspaceOutputPath = workspaceOutputPath;
        }

        public String getArchivePath() {
            return archivePath;
        }

        public void setArchivePath(String archivePath) {
            this.archivePath = archivePath;
        }

        public Boolean getCleanupCompleted() {
            return cleanupCompleted;
        }

        public void setCleanupCompleted(Boolean cleanupCompleted) {
            this.cleanupCompleted = cleanupCompleted;
        }

        public Boolean getArchiveCompleted() {
            return archiveCompleted;
        }

        public void setArchiveCompleted(Boolean archiveCompleted) {
            this.archiveCompleted = archiveCompleted;
        }
    }

    public static class ArtifactCatalog {
        @JsonProperty("primary_outputs")
        private List<ArtifactMeta> primaryOutputs;

        @JsonProperty("intermediate_outputs")
        private List<ArtifactMeta> intermediateOutputs;

        @JsonProperty("audit_artifacts")
        private List<ArtifactMeta> auditArtifacts;

        @JsonProperty("derived_outputs")
        private List<ArtifactMeta> derivedOutputs;

        private List<ArtifactMeta> logs;

        public List<ArtifactMeta> getPrimaryOutputs() {
            return primaryOutputs;
        }

        public void setPrimaryOutputs(List<ArtifactMeta> primaryOutputs) {
            this.primaryOutputs = primaryOutputs;
        }

        public List<ArtifactMeta> getIntermediateOutputs() {
            return intermediateOutputs;
        }

        public void setIntermediateOutputs(List<ArtifactMeta> intermediateOutputs) {
            this.intermediateOutputs = intermediateOutputs;
        }

        public List<ArtifactMeta> getAuditArtifacts() {
            return auditArtifacts;
        }

        public void setAuditArtifacts(List<ArtifactMeta> auditArtifacts) {
            this.auditArtifacts = auditArtifacts;
        }

        public List<ArtifactMeta> getDerivedOutputs() {
            return derivedOutputs;
        }

        public void setDerivedOutputs(List<ArtifactMeta> derivedOutputs) {
            this.derivedOutputs = derivedOutputs;
        }

        public List<ArtifactMeta> getLogs() {
            return logs;
        }

        public void setLogs(List<ArtifactMeta> logs) {
            this.logs = logs;
        }
    }

    public static class ArtifactMeta {
        @JsonProperty("artifact_id")
        private String artifactId;

        @JsonProperty("artifact_role")
        private String artifactRole;

        @JsonProperty("logical_name")
        private String logicalName;

        @JsonProperty("relative_path")
        private String relativePath;

        @JsonProperty("absolute_path")
        private String absolutePath;

        @JsonProperty("content_type")
        private String contentType;

        @JsonProperty("size_bytes")
        private Long sizeBytes;

        private String sha256;

        @JsonProperty("created_at")
        private String createdAt;

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getArtifactRole() {
            return artifactRole;
        }

        public void setArtifactRole(String artifactRole) {
            this.artifactRole = artifactRole;
        }

        public String getLogicalName() {
            return logicalName;
        }

        public void setLogicalName(String logicalName) {
            this.logicalName = logicalName;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public void setRelativePath(String relativePath) {
            this.relativePath = relativePath;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public void setAbsolutePath(String absolutePath) {
            this.absolutePath = absolutePath;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Long getSizeBytes() {
            return sizeBytes;
        }

        public void setSizeBytes(Long sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }
}
