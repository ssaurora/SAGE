package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.task.dto.ResumeTransactionView;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TaskProjectionBuilder {
    private TaskProjectionBuilder() {
    }

    static void applyPass1Projection(TaskManifestResponse response, JsonNode pass1Projection, ObjectMapper objectMapper) {
        if (response == null || pass1Projection == null || pass1Projection.isNull() || pass1Projection.isMissingNode()) {
            return;
        }
        response.setCapabilityKey(pass1Projection.path("capability_key").asText(null));
        response.setCapabilityFacts(buildManifestCapabilityFacts(pass1Projection.path("capability_facts")));
        response.setRoleArgMappings(buildManifestRoleArgMappings(pass1Projection.path("role_arg_mappings")));
        response.setStableDefaults(buildStableDefaults(pass1Projection.path("stable_defaults")));
    }

    static JsonNode buildManifestLogicalInputRolesPayload(JsonNode pass1Projection, ObjectMapper objectMapper) {
        if (pass1Projection == null || pass1Projection.isNull() || pass1Projection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestLogicalInputRoles(pass1Projection.path("logical_input_roles")), objectMapper);
    }

    static JsonNode buildManifestSlotSchemaViewPayload(JsonNode pass1Projection, ObjectMapper objectMapper) {
        if (pass1Projection == null || pass1Projection.isNull() || pass1Projection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestSlotSchemaView(pass1Projection.path("slot_schema_view")), objectMapper);
    }

    static JsonNode buildManifestSlotBindingsPayload(JsonNode passBProjection, ObjectMapper objectMapper) {
        if (passBProjection == null || passBProjection.isNull() || passBProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestSlotBindings(passBProjection.path("slot_bindings")), objectMapper);
    }

    static JsonNode buildManifestArgsDraftPayload(JsonNode passBProjection, ObjectMapper objectMapper) {
        if (passBProjection == null || passBProjection.isNull() || passBProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildJsonObjectView(passBProjection.path("args_draft"), objectMapper), objectMapper);
    }

    static JsonNode buildManifestValidationSummaryPayload(JsonNode validationProjection, ObjectMapper objectMapper) {
        if (validationProjection == null || validationProjection.isNull() || validationProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestValidationSummary(validationProjection), objectMapper);
    }

    static JsonNode buildManifestExecutionGraphPayload(JsonNode executionGraphProjection, ObjectMapper objectMapper) {
        return buildExecutionGraphPayload(executionGraphProjection, objectMapper);
    }

    static JsonNode buildManifestRuntimeAssertionsPayload(JsonNode runtimeAssertionsProjection, ObjectMapper objectMapper) {
        return buildRuntimeAssertionsPayload(runtimeAssertionsProjection, objectMapper);
    }

    static JsonNode buildExecutionGraphPayload(JsonNode executionGraphProjection, ObjectMapper objectMapper) {
        if (executionGraphProjection == null || executionGraphProjection.isNull() || executionGraphProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestExecutionGraph(executionGraphProjection), objectMapper);
    }

    static JsonNode buildRuntimeAssertionsPayload(JsonNode runtimeAssertionsProjection, ObjectMapper objectMapper) {
        if (runtimeAssertionsProjection == null || runtimeAssertionsProjection.isNull() || runtimeAssertionsProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildManifestRuntimeAssertions(runtimeAssertionsProjection), objectMapper);
    }

    static JsonNode buildPlanningPass2SummaryPayload(JsonNode planningSummaryProjection, ObjectMapper objectMapper) {
        if (planningSummaryProjection == null || planningSummaryProjection.isNull() || planningSummaryProjection.isMissingNode()) {
            return null;
        }
        return toJsonNode(buildJsonObjectView(planningSummaryProjection, objectMapper), objectMapper);
    }

    static TaskDetailResponse.Pass1Summary buildPass1Summary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.Pass1Summary summary = new TaskDetailResponse.Pass1Summary();
        summary.setCapabilityKey(root.path("capability_key").asText(null));
        summary.setSelectedTemplate(root.path("selected_template").asText(null));

        JsonNode rolesNode = root.path("logical_input_roles");
        summary.setLogicalInputRolesCount(rolesNode.isArray() ? rolesNode.size() : 0);

        int requiredRolesCount = 0;
        int optionalRolesCount = 0;
        if (rolesNode.isArray()) {
            for (JsonNode role : rolesNode) {
                if (role.path("required").asBoolean(false)) {
                    requiredRolesCount += 1;
                } else {
                    optionalRolesCount += 1;
                }
            }
        }
        summary.setRequiredRolesCount(requiredRolesCount);
        summary.setOptionalRolesCount(optionalRolesCount);

        JsonNode roleArgMappings = root.path("role_arg_mappings");
        summary.setRoleArgMappingCount(roleArgMappings.isArray() ? roleArgMappings.size() : 0);
        summary.setSlotSchemaViewVersion("v1");
        summary.setStableDefaults(buildPass1StableDefaults(root.path("stable_defaults")));
        return summary;
    }

    static TaskDetailResponse.GoalParseSummary buildGoalParseSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.GoalParseSummary summary = new TaskDetailResponse.GoalParseSummary();
        summary.setGoalType(root.path("goal_type").asText(null));
        summary.setUserQuery(root.path("user_query").asText(null));
        summary.setAnalysisKind(root.path("analysis_kind").asText(null));
        summary.setIntentMode(root.path("intent_mode").asText(null));
        summary.setSource(root.path("source").asText(null));
        summary.setEntities(jsonArrayToStrings(root.path("entities")));
        return summary;
    }

    static Map<String, Object> buildCognitionView(JsonNode root, ObjectMapper objectMapper) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode metadata = root.path("cognition_metadata");
        if (metadata == null || metadata.isNull() || metadata.isMissingNode() || !metadata.isObject()) {
            return null;
        }
        return buildJsonObjectView(metadata, objectMapper);
    }

    static Map<String, Object> buildGoalRouteOutput(JsonNode goalParseRoot, JsonNode skillRouteRoot, ObjectMapper objectMapper) {
        if ((goalParseRoot == null || goalParseRoot.isNull() || goalParseRoot.isMissingNode())
                && (skillRouteRoot == null || skillRouteRoot.isNull() || skillRouteRoot.isMissingNode())) {
            return null;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        if (goalParseRoot != null && !goalParseRoot.isNull() && !goalParseRoot.isMissingNode()) {
            String planningIntentStatus = goalParseRoot.path("planning_intent_status").asText(null);
            if (planningIntentStatus != null && !planningIntentStatus.isBlank()) {
                output.put("planning_intent_status", planningIntentStatus);
            }
            if (goalParseRoot.path("confidence").isNumber()) {
                output.put("confidence", goalParseRoot.path("confidence").asDouble());
            }
            Map<String, Object> decisionSummary = buildJsonObjectView(goalParseRoot.path("decision_summary"), objectMapper);
            if (decisionSummary != null && !decisionSummary.isEmpty()) {
                output.put("decision_summary", decisionSummary);
            }
            output.put("goal_parse", buildJsonObjectView(goalParseRoot, objectMapper));
        }
        if (skillRouteRoot != null && !skillRouteRoot.isNull() && !skillRouteRoot.isMissingNode()) {
            if (!output.containsKey("planning_intent_status")) {
                String planningIntentStatus = skillRouteRoot.path("planning_intent_status").asText(null);
                if (planningIntentStatus != null && !planningIntentStatus.isBlank()) {
                    output.put("planning_intent_status", planningIntentStatus);
                }
            }
            if (!output.containsKey("confidence") && skillRouteRoot.path("confidence").isNumber()) {
                output.put("confidence", skillRouteRoot.path("confidence").asDouble());
            }
            if (!output.containsKey("decision_summary")) {
                Map<String, Object> decisionSummary = buildJsonObjectView(skillRouteRoot.path("decision_summary"), objectMapper);
                if (decisionSummary != null && !decisionSummary.isEmpty()) {
                    output.put("decision_summary", decisionSummary);
                }
            }
            output.put("skill_route", buildJsonObjectView(skillRouteRoot, objectMapper));
        }
        return output.isEmpty() ? null : output;
    }

    static Map<String, Object> buildStageOutput(JsonNode root, ObjectMapper objectMapper) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        return buildJsonObjectView(root, objectMapper);
    }

    static TaskDetailResponse.SkillRouteSummary buildSkillRouteSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.SkillRouteSummary summary = new TaskDetailResponse.SkillRouteSummary();
        summary.setRouteMode(root.path("route_mode").asText(null));
        summary.setPrimarySkill(root.path("primary_skill").asText(null));
        summary.setCapabilityKey(root.path("capability_key").asText(null));
        summary.setRouteSource(root.path("route_source").asText(null));
        summary.setConfidence(root.path("confidence").isNumber() ? root.path("confidence").asDouble() : null);
        summary.setSelectedTemplate(root.path("selected_template").asText(null));
        summary.setTemplateVersion(root.path("template_version").asText(null));
        summary.setExecutionMode(root.path("execution_mode").asText(null));
        summary.setProviderPreference(root.path("provider_preference").asText(null));
        summary.setRuntimeProfilePreference(root.path("runtime_profile_preference").asText(null));
        summary.setSource(root.path("source").asText(null));
        return summary;
    }

    static TaskDetailResponse.SlotBindingsSummary buildSlotBindingsSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.SlotBindingsSummary summary = new TaskDetailResponse.SlotBindingsSummary();
        summary.setBoundSlotsCount(root.path("bound_slots_count").isNumber() ? root.path("bound_slots_count").asInt() : null);
        summary.setBoundRoleNames(jsonArrayToStrings(root.path("bound_role_names")));
        return summary;
    }

    static TaskDetailResponse.ArgsDraftSummary buildArgsDraftSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.ArgsDraftSummary summary = new TaskDetailResponse.ArgsDraftSummary();
        summary.setParamCount(root.path("param_count").isNumber() ? root.path("param_count").asInt() : null);
        summary.setParamKeys(jsonArrayToStrings(root.path("param_keys")));
        return summary;
    }

    static Map<String, Object> buildSlotBindingsSummaryPayload(CognitionPassBResponse passBResponse) {
        if (passBResponse == null) {
            return null;
        }
        List<CognitionPassBResponse.SlotBinding> bindings = passBResponse.getSlotBindings() == null
                ? Collections.emptyList()
                : passBResponse.getSlotBindings();
        List<String> roleNames = new ArrayList<>();
        for (CognitionPassBResponse.SlotBinding binding : bindings) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("bound_slots_count", bindings.size());
        summary.put("bound_role_names", roleNames);
        return summary;
    }

    static Map<String, Object> buildArgsDraftSummaryPayload(CognitionPassBResponse passBResponse) {
        if (passBResponse == null) {
            return null;
        }
        Map<String, Object> argsDraft = passBResponse.getArgsDraft() == null ? Collections.emptyMap() : passBResponse.getArgsDraft();
        List<String> keys = new ArrayList<>(argsDraft.keySet());
        Collections.sort(keys);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("param_count", argsDraft.size());
        summary.put("param_keys", keys);
        return summary;
    }

    static TaskDetailResponse.ValidationSummary buildValidationSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.ValidationSummary summary = new TaskDetailResponse.ValidationSummary();
        summary.setIsValid(root.path("is_valid").isBoolean() ? root.path("is_valid").asBoolean() : null);
        summary.setMissingRoles(jsonArrayToStrings(root.path("missing_roles")));
        summary.setMissingParams(jsonArrayToStrings(root.path("missing_params")));
        summary.setErrorCode(root.path("error_code").asText(null));
        summary.setInvalidBindings(jsonArrayToStrings(root.path("invalid_bindings")));
        return summary;
    }

    static TaskDetailResponse.Pass2Summary buildPass2Summary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        JsonNode planningSummary = root.path("planning_summary");
        TaskDetailResponse.Pass2Summary summary = new TaskDetailResponse.Pass2Summary();
        summary.setPlanner(planningSummary.path("planner").asText(null));
        summary.setNodeCount(planningSummary.path("node_count").isNumber() ? planningSummary.path("node_count").asInt() : null);
        summary.setEdgeCount(planningSummary.path("edge_count").isNumber() ? planningSummary.path("edge_count").asInt() : null);
        summary.setValidationIsValid(planningSummary.path("validation_is_valid").isBoolean() ? planningSummary.path("validation_is_valid").asBoolean() : null);
        summary.setValidationErrorCode(planningSummary.path("validation_error_code").asText(null));
        summary.setCapabilityKey(planningSummary.path("capability_key").asText(null));
        summary.setTemplate(planningSummary.path("template").asText(null));
        summary.setRuntimeAssertionCount(planningSummary.path("runtime_assertion_count").isNumber() ? planningSummary.path("runtime_assertion_count").asInt() : null);
        summary.setGraphDigest(root.path("graph_digest").asText(null));
        summary.setCanonicalizationSummary(buildJsonObjectView(root.path("canonicalization_summary"), null));
        summary.setRewriteSummary(buildJsonObjectView(root.path("rewrite_summary"), null));
        return summary;
    }

    static ResumeTransactionView buildResumeTransaction(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        ResumeTransactionView view = new ResumeTransactionView();
        view.setResumeRequestId(root.path("resume_request_id").asText(null));
        view.setStatus(root.path("status").asText(null));
        view.setBaseCheckpointVersion(root.path("base_checkpoint_version").isNumber() ? root.path("base_checkpoint_version").asInt() : null);
        view.setCandidateCheckpointVersion(root.path("candidate_checkpoint_version").isNumber() ? root.path("candidate_checkpoint_version").asInt() : null);
        view.setCandidateInventoryVersion(root.path("candidate_inventory_version").isNumber() ? root.path("candidate_inventory_version").asInt() : null);
        view.setCandidateManifestId(root.path("candidate_manifest_id").asText(null));
        view.setCandidateAttemptNo(root.path("candidate_attempt_no").isNumber() ? root.path("candidate_attempt_no").asInt() : null);
        view.setCandidateJobId(root.path("candidate_job_id").asText(null));
        view.setFailureReason(root.path("failure_reason").asText(null));
        view.setUpdatedAt(root.path("updated_at").asText(null));
        return view;
    }

    static TaskDetailResponse.ResultObjectSummary buildResultObjectSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.ResultObjectSummary summary = new TaskDetailResponse.ResultObjectSummary();
        summary.setResultId(root.path("result_id").asText(null));
        summary.setSummary(root.path("summary").asText(null));
        summary.setArtifactCount(root.path("artifact_count").isNumber() ? root.path("artifact_count").asInt() : null);
        summary.setCreatedAt(root.path("created_at").asText(null));
        return summary;
    }

    static TaskDetailResponse.ResultBundleSummary buildResultBundleSummaryView(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.ResultBundleSummary summary = new TaskDetailResponse.ResultBundleSummary();
        summary.setResultId(root.path("result_id").asText(null));
        summary.setSummary(root.path("summary").asText(null));
        summary.setMainOutputCount(root.path("main_output_count").isNumber() ? root.path("main_output_count").asInt() : null);
        summary.setMainOutputs(jsonArrayToStrings(root.path("main_outputs")));
        summary.setPrimaryOutputs(jsonArrayToStrings(root.path("primary_outputs")));
        summary.setAuditArtifacts(jsonArrayToStrings(root.path("audit_artifacts")));
        summary.setCreatedAt(root.path("created_at").asText(null));
        return summary;
    }

    static TaskDetailResponse.FinalExplanationSummary buildFinalExplanationSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.FinalExplanationSummary summary = new TaskDetailResponse.FinalExplanationSummary();
        summary.setTitle(root.path("title").asText(null));
        summary.setHighlightCount(root.path("highlight_count").isNumber() ? root.path("highlight_count").asInt() : null);
        summary.setGeneratedAt(root.path("generated_at").asText(null));
        summary.setAvailable(root.path("available").isBoolean() ? root.path("available").asBoolean() : null);
        summary.setFailureCode(root.path("failure_code").asText(null));
        summary.setFailureMessage(root.path("failure_message").asText(null));
        return summary;
    }

    static TaskDetailResponse.FailureSummary buildFailureSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.FailureSummary summary = new TaskDetailResponse.FailureSummary();
        summary.setFailureCode(root.path("failure_code").asText(null));
        summary.setFailureMessage(root.path("failure_message").asText(null));
        summary.setCreatedAt(root.path("created_at").asText(null));
        summary.setAssertionId(root.path("assertion_id").asText(null));
        summary.setNodeId(root.path("node_id").asText(null));
        summary.setRepairable(root.path("repairable").isBoolean() ? root.path("repairable").asBoolean() : null);
        summary.setDetails(jsonObjectToMap(root.path("details"), null));
        return summary;
    }

    static TaskDetailResponse.WaitingContext buildWaitingContext(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.WaitingContext summary = new TaskDetailResponse.WaitingContext();
        summary.setWaitingReasonType(root.path("waiting_reason_type").asText(null));
        summary.setMissingSlots(buildMissingSlots(root.path("missing_slots")));
        summary.setInvalidBindings(jsonArrayToStrings(root.path("invalid_bindings")));
        summary.setRequiredUserActions(buildRequiredUserActions(root.path("required_user_actions")));
        summary.setResumeHint(root.path("resume_hint").asText(null));
        summary.setCanResume(root.path("can_resume").isBoolean() ? root.path("can_resume").asBoolean() : null);
        return summary;
    }

    static TaskDetailResponse.RepairProposal buildRepairProposal(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskDetailResponse.RepairProposal summary = new TaskDetailResponse.RepairProposal();
        summary.setAvailable(root.path("available").isBoolean() ? root.path("available").asBoolean() : null);
        summary.setUserFacingReason(root.path("user_facing_reason").asText(null));
        summary.setResumeHint(root.path("resume_hint").asText(null));
        summary.setActionExplanations(buildRepairActionExplanations(root.path("action_explanations")));
        summary.setNotes(jsonArrayToStrings(root.path("notes")));
        summary.setFailureCode(root.path("failure_code").asText(null));
        summary.setFailureMessage(root.path("failure_message").asText(null));
        return summary;
    }

    static TaskResultResponse.ResultBundle buildTaskResultBundle(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.ResultBundle bundle = new TaskResultResponse.ResultBundle();
        bundle.setResultId(root.path("result_id").asText(null));
        bundle.setTaskId(root.path("task_id").asText(null));
        bundle.setJobId(root.path("job_id").asText(null));
        bundle.setSummary(root.path("summary").asText(null));
        bundle.setMetrics(jsonObjectToMap(root.path("metrics"), null));
        bundle.setOutputRegistry(jsonObjectToMap(root.path("output_registry"), null));
        bundle.setPrimaryOutputRefs(buildOutputReferenceList(root.path("primary_output_refs")));
        bundle.setMainOutputs(jsonArrayToStrings(root.path("main_outputs")));
        bundle.setArtifacts(jsonArrayToStrings(root.path("artifacts")));
        bundle.setPrimaryOutputs(jsonArrayToStrings(root.path("primary_outputs")));
        bundle.setIntermediateOutputs(jsonArrayToStrings(root.path("intermediate_outputs")));
        bundle.setAuditArtifacts(jsonArrayToStrings(root.path("audit_artifacts")));
        bundle.setDerivedOutputs(jsonArrayToStrings(root.path("derived_outputs")));
        bundle.setCreatedAt(root.path("created_at").asText(null));
        return bundle;
    }

    static TaskResultResponse.FinalExplanation buildTaskFinalExplanation(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.FinalExplanation explanation = new TaskResultResponse.FinalExplanation();
        explanation.setAvailable(root.path("available").isBoolean() ? root.path("available").asBoolean() : null);
        explanation.setTitle(root.path("title").asText(null));
        explanation.setHighlights(jsonArrayToStrings(root.path("highlights")));
        explanation.setNarrative(root.path("narrative").asText(null));
        explanation.setGeneratedAt(root.path("generated_at").asText(null));
        explanation.setFailureCode(root.path("failure_code").asText(null));
        explanation.setFailureMessage(root.path("failure_message").asText(null));
        return explanation;
    }

    static TaskResultResponse.FailureSummary buildTaskResultFailureSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.FailureSummary summary = new TaskResultResponse.FailureSummary();
        summary.setFailureCode(root.path("failure_code").asText(null));
        summary.setFailureMessage(root.path("failure_message").asText(null));
        summary.setCreatedAt(root.path("created_at").asText(null));
        summary.setAssertionId(root.path("assertion_id").asText(null));
        summary.setNodeId(root.path("node_id").asText(null));
        summary.setRepairable(root.path("repairable").isBoolean() ? root.path("repairable").asBoolean() : null);
        summary.setDetails(jsonObjectToMap(root.path("details"), null));
        return summary;
    }

    static TaskResultResponse.DockerRuntimeEvidence buildDockerRuntimeEvidence(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.DockerRuntimeEvidence evidence = new TaskResultResponse.DockerRuntimeEvidence();
        evidence.setContainerName(root.path("container_name").asText(null));
        evidence.setImage(root.path("image").asText(null));
        evidence.setWorkspaceOutputPath(root.path("workspace_output_path").asText(null));
        evidence.setResultFileExists(root.path("result_file_exists").isBoolean() ? root.path("result_file_exists").asBoolean() : null);
        evidence.setProviderKey(root.path("provider_key").asText(null));
        evidence.setRuntimeProfile(root.path("runtime_profile").asText(null));
        evidence.setCaseId(root.path("case_id").asText(null));
        evidence.setContractMode(root.path("contract_mode").asText(null));
        evidence.setRuntimeMode(root.path("runtime_mode").asText(null));
        evidence.setInputBindings(buildInputBindingList(root.path("input_bindings")));
        evidence.setPromotionStatus(root.path("promotion_status").asText(null));
        return evidence;
    }

    static TaskResultResponse.WorkspaceSummary buildWorkspaceSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.WorkspaceSummary summary = new TaskResultResponse.WorkspaceSummary();
        summary.setWorkspaceId(root.path("workspace_id").asText(null));
        summary.setWorkspaceOutputPath(root.path("workspace_output_path").asText(null));
        summary.setArchivePath(root.path("archive_path").asText(null));
        summary.setCleanupCompleted(root.path("cleanup_completed").isBoolean() ? root.path("cleanup_completed").asBoolean() : null);
        summary.setArchiveCompleted(root.path("archive_completed").isBoolean() ? root.path("archive_completed").asBoolean() : null);
        return summary;
    }

    static TaskResultResponse.ArtifactCatalog buildArtifactCatalog(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskResultResponse.ArtifactCatalog catalog = new TaskResultResponse.ArtifactCatalog();
        catalog.setPrimaryOutputs(buildArtifactMetaList(root.path("primary_outputs")));
        catalog.setIntermediateOutputs(buildArtifactMetaList(root.path("intermediate_outputs")));
        catalog.setAuditArtifacts(buildArtifactMetaList(root.path("audit_artifacts")));
        catalog.setDerivedOutputs(buildArtifactMetaList(root.path("derived_outputs")));
        catalog.setLogs(buildArtifactMetaList(root.path("logs")));
        return catalog;
    }

    static TaskManifestResponse.GoalParse buildManifestGoalParse(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.GoalParse summary = new TaskManifestResponse.GoalParse();
        summary.setGoalType(root.path("goal_type").asText(null));
        summary.setUserQuery(root.path("user_query").asText(null));
        summary.setAnalysisKind(root.path("analysis_kind").asText(null));
        summary.setIntentMode(root.path("intent_mode").asText(null));
        summary.setEntities(jsonArrayToStrings(root.path("entities")));
        summary.setSource(root.path("source").asText(null));
        return summary;
    }

    static TaskManifestResponse.SkillRoute buildManifestSkillRoute(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.SkillRoute summary = new TaskManifestResponse.SkillRoute();
        summary.setRouteMode(root.path("route_mode").asText(null));
        summary.setPrimarySkill(root.path("primary_skill").asText(null));
        summary.setCapabilityKey(root.path("capability_key").asText(null));
        summary.setRouteSource(root.path("route_source").asText(null));
        summary.setConfidence(root.path("confidence").isNumber() ? root.path("confidence").asDouble() : null);
        summary.setSelectedTemplate(root.path("selected_template").asText(null));
        summary.setTemplateVersion(root.path("template_version").asText(null));
        summary.setExecutionMode(root.path("execution_mode").asText(null));
        summary.setProviderPreference(root.path("provider_preference").asText(null));
        summary.setRuntimeProfilePreference(root.path("runtime_profile_preference").asText(null));
        summary.setSource(root.path("source").asText(null));
        return summary;
    }

    static Map<String, Object> buildJsonObjectView(JsonNode root, ObjectMapper objectMapper) {
        return jsonObjectToMap(root, objectMapper);
    }

    static TaskManifestResponse.CapabilityFacts buildManifestCapabilityFacts(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.CapabilityFacts facts = new TaskManifestResponse.CapabilityFacts();
        facts.setCapabilityKey(root.path("capability_key").asText(null));
        facts.setDisplayName(root.path("display_name").asText(null));
        facts.setValidationHints(buildManifestValidationHints(root.path("validation_hints")));
        facts.setRepairHints(buildManifestRepairHints(root.path("repair_hints")));
        facts.setOutputContract(buildManifestOutputContract(root.path("output_contract")));
        facts.setRuntimeProfileHint(root.path("runtime_profile_hint").asText(null));
        return facts;
    }

    static List<TaskManifestResponse.RoleArgMapping> buildManifestRoleArgMappings(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.RoleArgMapping> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.RoleArgMapping mapping = new TaskManifestResponse.RoleArgMapping();
            mapping.setRoleName(item.path("role_name").asText(null));
            mapping.setSlotArgKey(item.path("slot_arg_key").asText(null));
            mapping.setValueArgKey(item.path("value_arg_key").asText(null));
            mapping.setDefaultValue(jsonNodeToObject(item.path("default_value"), null));
            items.add(mapping);
        }
        return items;
    }

    static TaskManifestResponse.StableDefaults buildStableDefaults(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode() || !root.isObject()) {
            return null;
        }
        TaskManifestResponse.StableDefaults defaults = new TaskManifestResponse.StableDefaults();
        defaults.setAnalysisTemplate(root.path("analysis_template").asText(null));
        defaults.setRootDepthFactor(root.path("root_depth_factor").isNumber() ? root.path("root_depth_factor").asDouble() : null);
        defaults.setPawcFactor(root.path("pawc_factor").isNumber() ? root.path("pawc_factor").asDouble() : null);
        return defaults;
    }

    static TaskDetailResponse.StableDefaults buildPass1StableDefaults(JsonNode root) {
        TaskManifestResponse.StableDefaults manifestDefaults = buildStableDefaults(root);
        if (manifestDefaults == null) {
            return null;
        }
        TaskDetailResponse.StableDefaults defaults = new TaskDetailResponse.StableDefaults();
        defaults.setAnalysisTemplate(manifestDefaults.getAnalysisTemplate());
        defaults.setRootDepthFactor(manifestDefaults.getRootDepthFactor());
        defaults.setPawcFactor(manifestDefaults.getPawcFactor());
        return defaults;
    }

    static List<TaskManifestResponse.LogicalInputRole> buildManifestLogicalInputRoles(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.LogicalInputRole> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.LogicalInputRole role = new TaskManifestResponse.LogicalInputRole();
            role.setRoleName(item.path("role_name").asText(null));
            role.setRequired(item.path("required").isBoolean() ? item.path("required").asBoolean() : null);
            items.add(role);
        }
        return items;
    }

    static TaskManifestResponse.SlotSchemaView buildManifestSlotSchemaView(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.SlotSchemaView view = new TaskManifestResponse.SlotSchemaView();
        view.setSlots(buildManifestSlotSchemaItems(root.path("slots")));
        return view;
    }

    static List<TaskManifestResponse.SlotBinding> buildManifestSlotBindings(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.SlotBinding> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.SlotBinding binding = new TaskManifestResponse.SlotBinding();
            binding.setRoleName(item.path("role_name").asText(null));
            binding.setSlotName(item.path("slot_name").asText(null));
            binding.setSource(item.path("source").asText(null));
            items.add(binding);
        }
        return items;
    }

    static TaskManifestResponse.ValidationSummary buildManifestValidationSummary(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.ValidationSummary summary = new TaskManifestResponse.ValidationSummary();
        summary.setIsValid(root.path("is_valid").isBoolean() ? root.path("is_valid").asBoolean() : null);
        summary.setMissingRoles(jsonArrayToStrings(root.path("missing_roles")));
        summary.setMissingParams(jsonArrayToStrings(root.path("missing_params")));
        summary.setErrorCode(root.path("error_code").asText(null));
        summary.setInvalidBindings(jsonArrayToStrings(root.path("invalid_bindings")));
        return summary;
    }

    static TaskManifestResponse.ExecutionGraph buildManifestExecutionGraph(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.ExecutionGraph graph = new TaskManifestResponse.ExecutionGraph();
        graph.setNodes(buildManifestExecutionNodes(root.path("nodes")));
        graph.setEdges(buildEdgeList(root.path("edges")));
        return graph;
    }

    static List<TaskManifestResponse.RuntimeAssertion> buildManifestRuntimeAssertions(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.RuntimeAssertion> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.RuntimeAssertion assertion = new TaskManifestResponse.RuntimeAssertion();
            assertion.setAssertionId(item.path("assertion_id").asText(null));
            assertion.setName(item.path("name").asText(null));
            assertion.setRequired(item.path("required").isBoolean() ? item.path("required").asBoolean() : null);
            assertion.setMessage(item.path("message").asText(null));
            assertion.setAssertionType(item.path("assertion_type").asText(null));
            assertion.setNodeId(item.path("node_id").asText(null));
            assertion.setTargetKey(item.path("target_key").asText(null));
            assertion.setExpectedValue(item.path("expected_value").asText(null));
            assertion.setRepairable(item.path("repairable").isBoolean() ? item.path("repairable").asBoolean() : null);
            assertion.setDetails(jsonObjectToMap(item.path("details"), null));
            items.add(assertion);
        }
        return items;
    }

    static Map<String, Object> buildResultBundleSummary(JsonNode resultBundle) {
        if (resultBundle == null || resultBundle.isNull() || resultBundle.isMissingNode()) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        JsonNode outputs = resultBundle.path("main_outputs");
        JsonNode primaryOutputs = resultBundle.path("primary_outputs");
        JsonNode auditArtifacts = resultBundle.path("audit_artifacts");
        summary.put("result_id", resultBundle.path("result_id").asText(""));
        summary.put("summary", resultBundle.path("summary").asText(""));
        summary.put("main_output_count", outputs.isArray() ? outputs.size() : 0);
        summary.put("main_outputs", jsonArrayToStrings(outputs));
        summary.put("primary_outputs", jsonArrayToStrings(primaryOutputs));
        summary.put("audit_artifacts", jsonArrayToStrings(auditArtifacts));
        summary.put("created_at", resultBundle.path("created_at").asText(""));
        return summary;
    }

    static Map<String, Object> buildFinalExplanationSummaryPayload(JsonNode finalExplanation) {
        if (finalExplanation == null || finalExplanation.isNull() || finalExplanation.isMissingNode()) {
            return null;
        }
        JsonNode highlights = finalExplanation.path("highlights");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", finalExplanation.path("title").asText(""));
        summary.put("highlight_count", highlights.isArray() ? highlights.size() : 0);
        summary.put("generated_at", finalExplanation.path("generated_at").asText(""));
        summary.put("available", finalExplanation.path("available").isBoolean() ? finalExplanation.path("available").asBoolean() : null);
        summary.put("failure_code", finalExplanation.path("failure_code").asText(null));
        summary.put("failure_message", finalExplanation.path("failure_message").asText(null));
        return summary;
    }

    static Map<String, Object> buildResultObjectSummaryPayload(JsonNode resultObject, JsonNode resultBundle) {
        JsonNode source = resultObject;
        if (source == null || source.isNull() || source.isMissingNode()) {
            source = resultBundle;
        }
        if (source == null || source.isNull() || source.isMissingNode()) {
            return null;
        }
        JsonNode artifacts = source.path("artifacts");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("result_id", source.path("result_id").asText(""));
        summary.put("summary", source.path("summary").asText(""));
        summary.put("artifact_count", artifacts.isArray() ? artifacts.size() : 0);
        summary.put("created_at", source.path("created_at").asText(""));
        return summary;
    }

    static Map<String, Object> buildFailureSummaryPayload(JsonNode failureSummary, JsonNode errorObject, String fallbackCreatedAt) {
        if (failureSummary != null && !failureSummary.isNull() && !failureSummary.isMissingNode()) {
            return jsonNodeToMap(failureSummary);
        }
        if (errorObject != null && !errorObject.isNull() && !errorObject.isMissingNode()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("failure_code", errorObject.path("error_code").asText("JOB_RUNTIME_ERROR"));
            summary.put("failure_message", errorObject.path("message").asText("Job runtime error"));
            summary.put("created_at", errorObject.path("created_at").asText(fallbackCreatedAt));
            return summary;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("failure_code", "UNKNOWN_FAILURE");
        summary.put("failure_message", "Job failed without detail");
        summary.put("created_at", fallbackCreatedAt);
        return summary;
    }

    static Object jsonNodeToObject(JsonNode node, ObjectMapper objectMapper) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
            return mapper.readValue(mapper.writeValueAsString(node), Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    static JsonNode toJsonNode(Object value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        return mapper.valueToTree(value);
    }

    static Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), jsonNodeToScalarOrObject(entry.getValue())));
        return values;
    }

    private static Object jsonNodeToScalarOrObject(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isFloatingPointNumber()) {
            return value.asDouble();
        }
        if (value.isArray()) {
            return jsonArrayToStrings(value);
        }
        if (value.isObject()) {
            return jsonNodeToMap(value);
        }
        return value.asText();
    }

    static List<String> jsonArrayToStrings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isValueNode()) {
                values.add(item.asText(""));
            }
        }
        return values;
    }

    private static Map<String, Object> jsonObjectToMap(JsonNode node, ObjectMapper objectMapper) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isObject()) {
            return null;
        }
        try {
            ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
            return mapper.readValue(mapper.writeValueAsString(node), Map.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private static List<TaskDetailResponse.MissingSlot> buildMissingSlots(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskDetailResponse.MissingSlot> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskDetailResponse.MissingSlot missingSlot = new TaskDetailResponse.MissingSlot();
            missingSlot.setSlotName(item.path("slot_name").asText(null));
            missingSlot.setExpectedType(item.path("expected_type").asText(null));
            missingSlot.setRequired(item.path("required").isBoolean() ? item.path("required").asBoolean() : null);
            values.add(missingSlot);
        }
        return values;
    }

    private static List<TaskDetailResponse.RequiredUserAction> buildRequiredUserActions(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskDetailResponse.RequiredUserAction> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskDetailResponse.RequiredUserAction action = new TaskDetailResponse.RequiredUserAction();
            action.setActionType(item.path("action_type").asText(null));
            action.setKey(item.path("key").asText(null));
            action.setLabel(item.path("label").asText(null));
            action.setRequired(item.path("required").isBoolean() ? item.path("required").asBoolean() : null);
            values.add(action);
        }
        return values;
    }

    private static List<TaskDetailResponse.RepairActionExplanation> buildRepairActionExplanations(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskDetailResponse.RepairActionExplanation> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskDetailResponse.RepairActionExplanation explanation = new TaskDetailResponse.RepairActionExplanation();
            explanation.setKey(item.path("key").asText(null));
            explanation.setMessage(item.path("message").asText(null));
            values.add(explanation);
        }
        return values;
    }

    private static List<TaskResultResponse.ArtifactMeta> buildArtifactMetaList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskResultResponse.ArtifactMeta> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskResultResponse.ArtifactMeta artifact = new TaskResultResponse.ArtifactMeta();
            artifact.setArtifactId(item.path("artifact_id").asText(null));
            artifact.setArtifactRole(item.path("artifact_role").asText(null));
            artifact.setLogicalName(item.path("logical_name").asText(null));
            artifact.setRelativePath(item.path("relative_path").asText(null));
            artifact.setAbsolutePath(item.path("absolute_path").asText(null));
            artifact.setContentType(item.path("content_type").asText(null));
            artifact.setSizeBytes(item.path("size_bytes").isNumber() ? item.path("size_bytes").asLong() : null);
            artifact.setSha256(item.path("sha256").asText(null));
            artifact.setCreatedAt(item.path("created_at").asText(null));
            values.add(artifact);
        }
        return values;
    }

    private static List<TaskResultResponse.OutputReference> buildOutputReferenceList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskResultResponse.OutputReference> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskResultResponse.OutputReference ref = new TaskResultResponse.OutputReference();
            ref.setOutputId(item.path("output_id").asText(null));
            ref.setPath(item.path("path").asText(null));
            values.add(ref);
        }
        return values;
    }

    private static List<TaskResultResponse.InputBinding> buildInputBindingList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Collections.emptyList();
        }
        List<TaskResultResponse.InputBinding> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskResultResponse.InputBinding binding = new TaskResultResponse.InputBinding();
            binding.setRoleName(item.path("role_name").asText(null));
            binding.setSlotName(item.path("slot_name").asText(null));
            binding.setSource(item.path("source").asText(null));
            binding.setArgKey(item.path("arg_key").asText(null));
            binding.setProviderInputPath(item.path("provider_input_path").asText(null));
            binding.setSourceRef(item.path("source_ref").asText(null));
            values.add(binding);
        }
        return values;
    }

    private static List<TaskManifestResponse.CapabilityValidationHint> buildManifestValidationHints(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.CapabilityValidationHint> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.CapabilityValidationHint hint = new TaskManifestResponse.CapabilityValidationHint();
            hint.setRoleName(item.path("role_name").asText(null));
            hint.setExpectedSlotType(item.path("expected_slot_type").asText(null));
            items.add(hint);
        }
        return items;
    }

    private static List<TaskManifestResponse.CapabilityRepairHint> buildManifestRepairHints(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.CapabilityRepairHint> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.CapabilityRepairHint hint = new TaskManifestResponse.CapabilityRepairHint();
            hint.setRoleName(item.path("role_name").asText(null));
            hint.setActionType(item.path("action_type").asText(null));
            hint.setActionKey(item.path("action_key").asText(null));
            hint.setActionLabel(item.path("action_label").asText(null));
            items.add(hint);
        }
        return items;
    }

    private static TaskManifestResponse.CapabilityOutputContract buildManifestOutputContract(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }
        TaskManifestResponse.CapabilityOutputContract contract = new TaskManifestResponse.CapabilityOutputContract();
        contract.setOutputs(buildManifestOutputItems(root.path("outputs")));
        return contract;
    }

    private static List<TaskManifestResponse.CapabilityOutputItem> buildManifestOutputItems(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.CapabilityOutputItem> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.CapabilityOutputItem output = new TaskManifestResponse.CapabilityOutputItem();
            output.setArtifactRole(item.path("artifact_role").asText(null));
            output.setLogicalName(item.path("logical_name").asText(null));
            items.add(output);
        }
        return items;
    }

    private static List<TaskManifestResponse.SlotSchemaItem> buildManifestSlotSchemaItems(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.SlotSchemaItem> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.SlotSchemaItem slot = new TaskManifestResponse.SlotSchemaItem();
            slot.setSlotName(item.path("slot_name").asText(null));
            slot.setType(item.path("type").asText(null));
            slot.setBoundRole(item.path("bound_role").asText(null));
            items.add(slot);
        }
        return items;
    }

    private static List<TaskManifestResponse.ExecutionNode> buildManifestExecutionNodes(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<TaskManifestResponse.ExecutionNode> items = new ArrayList<>();
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) {
                continue;
            }
            TaskManifestResponse.ExecutionNode node = new TaskManifestResponse.ExecutionNode();
            node.setNodeId(item.path("node_id").asText(null));
            node.setKind(item.path("kind").asText(null));
            items.add(node);
        }
        return items;
    }

    private static List<List<String>> buildEdgeList(JsonNode root) {
        if (root == null || !root.isArray()) {
            return Collections.emptyList();
        }
        List<List<String>> edges = new ArrayList<>();
        for (JsonNode edgeNode : root) {
            if (edgeNode == null || !edgeNode.isArray()) {
                continue;
            }
            edges.add(jsonArrayToStrings(edgeNode));
        }
        return edges;
    }
}
