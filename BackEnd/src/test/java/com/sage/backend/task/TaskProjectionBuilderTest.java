package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskProjectionBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildPass1SummaryIncludesCapabilityAndRoleMappingCounts() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "capability_facts": {
                    "contracts": {
                      "validate_args": {
                        "input_schema": "args_draft_validation_v1"
                      },
                      "submit_job": {
                        "input_schema": "runtime_submission_v1"
                      }
                    }
                  },
                  "logical_input_roles": [
                    {"role_name": "precipitation", "required": true},
                    {"role_name": "eto", "required": true},
                    {"role_name": "depth_to_root_restricting_layer", "required": false}
                  ],
                  "role_arg_mappings": [
                    {"role_name": "precipitation", "slot_arg_key": "precipitation_slot"},
                    {"role_name": "eto", "slot_arg_key": "eto_slot"}
                  ],
                  "stable_defaults": {
                    "analysis_template": "water_yield_v1",
                    "root_depth_factor": 0.8,
                    "pawc_factor": 0.85
                  }
                }
                """);

        TaskDetailResponse.Pass1Summary summary = TaskProjectionBuilder.buildPass1Summary(pass1Result);

        assertEquals("water_yield", summary.getCapabilityKey());
        assertEquals("water_yield_v1", summary.getSelectedTemplate());
        assertEquals(3, summary.getLogicalInputRolesCount());
        assertEquals(2, summary.getRequiredRolesCount());
        assertEquals(1, summary.getOptionalRolesCount());
        assertEquals(2, summary.getRoleArgMappingCount());
        assertEquals("v1", summary.getSlotSchemaViewVersion());
        assertEquals(2, summary.getContractCount());
        assertEquals(List.of("submit_job", "validate_args"), summary.getContractNames());
        assertEquals("water_yield_v1", summary.getStableDefaults().getAnalysisTemplate());
        assertEquals(0.8, summary.getStableDefaults().getRootDepthFactor());
        assertEquals(0.85, summary.getStableDefaults().getPawcFactor());
    }

    @Test
    void buildGoalParseSummaryPreservesDeterministicRouteFacts() throws Exception {
        JsonNode goalParse = objectMapper.readTree("""
                {
                  "goal_type": "water_yield_analysis",
                  "user_query": "run water yield",
                  "analysis_kind": "water_yield",
                  "intent_mode": "deterministic_phase0",
                  "entities": ["precipitation", "eto"]
                }
                """);

        TaskDetailResponse.GoalParseSummary summary = TaskProjectionBuilder.buildGoalParseSummary(goalParse);

        assertEquals("water_yield_analysis", summary.getGoalType());
        assertEquals("run water yield", summary.getUserQuery());
        assertEquals("water_yield", summary.getAnalysisKind());
        assertEquals("deterministic_phase0", summary.getIntentMode());
        assertEquals(List.of("precipitation", "eto"), summary.getEntities());
    }

    @Test
    void buildSlotBindingsSummaryPayloadPreservesBoundRoles() {
        CognitionPassBResponse passBResponse = new CognitionPassBResponse();
        CognitionPassBResponse.SlotBinding precipitation = new CognitionPassBResponse.SlotBinding();
        precipitation.setRoleName("precipitation");
        CognitionPassBResponse.SlotBinding eto = new CognitionPassBResponse.SlotBinding();
        eto.setRoleName("eto");
        passBResponse.setSlotBindings(List.of(precipitation, eto));

        Map<String, Object> payload = TaskProjectionBuilder.buildSlotBindingsSummaryPayload(passBResponse);

        assertEquals(2, payload.get("bound_slots_count"));
        assertEquals(List.of("precipitation", "eto"), payload.get("bound_role_names"));
    }

    @Test
    void buildArgsDraftSummaryPayloadPreservesSortedKeys() {
        CognitionPassBResponse passBResponse = new CognitionPassBResponse();
        passBResponse.setArgsDraft(Map.of(
                "eto_value", 800.0,
                "precipitation_value", 1200.0
        ));

        Map<String, Object> payload = TaskProjectionBuilder.buildArgsDraftSummaryPayload(passBResponse);

        assertEquals(2, payload.get("param_count"));
        assertEquals(List.of("eto_value", "precipitation_value"), payload.get("param_keys"));
    }

    @Test
    void buildFinalExplanationSummaryPayloadPreservesHighlights() throws Exception {
        JsonNode finalExplanation = objectMapper.readTree("""
                {
                  "title": "Water yield completed",
                  "highlights": ["a", "b", "c"],
                  "generated_at": "2026-03-26T10:00:00Z"
                }
                """);

        Map<String, Object> payload = TaskProjectionBuilder.buildFinalExplanationSummaryPayload(finalExplanation);

        assertEquals("Water yield completed", payload.get("title"));
        assertEquals(3, payload.get("highlight_count"));
        assertEquals("2026-03-26T10:00:00Z", payload.get("generated_at"));
    }

    @Test
    void buildResultObjectSummaryPayloadFallsBackToResultBundle() throws Exception {
        JsonNode resultBundle = objectMapper.readTree("""
                {
                  "result_id": "rb_001",
                  "summary": "bundle summary",
                  "created_at": "2026-03-26T10:00:00Z",
                  "artifacts": ["a", "b"]
                }
                """);

        Map<String, Object> payload = TaskProjectionBuilder.buildResultObjectSummaryPayload(null, resultBundle);

        assertEquals("rb_001", payload.get("result_id"));
        assertEquals("bundle summary", payload.get("summary"));
        assertEquals(2, payload.get("artifact_count"));
        assertEquals("2026-03-26T10:00:00Z", payload.get("created_at"));
    }

    @Test
    void buildSkillRouteSummaryHandlesCanonicalAndFallbackFields() throws Exception {
        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "route_mode": "single_skill",
                  "primary_skill": "water_yield",
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "capability_key": "water_yield",
                  "route_source": "deterministic_phase0",
                  "confidence": 0.9,
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "execution_mode": "real_case_validation",
                  "provider_preference": "planning-pass1-invest-local",
                  "runtime_profile_preference": "docker-invest-real"
                }
                """);

        TaskDetailResponse.SkillRouteSummary summary = TaskProjectionBuilder.buildSkillRouteSummary(skillRoute);

        assertEquals("single_skill", summary.getRouteMode());
        assertEquals("water_yield", summary.getPrimarySkill());
        assertEquals("water_yield", summary.getSkillId());
        assertEquals("1.0.0", summary.getSkillVersion());
        assertEquals("water_yield", summary.getCapabilityKey());
        assertEquals("deterministic_phase0", summary.getRouteSource());
        assertEquals(0.9, summary.getConfidence());
        assertEquals("water_yield_v1", summary.getSelectedTemplate());
        assertEquals("1.0.0", summary.getTemplateVersion());
        assertEquals("real_case_validation", summary.getExecutionMode());
        assertEquals("planning-pass1-invest-local", summary.getProviderPreference());
        assertEquals("docker-invest-real", summary.getRuntimeProfilePreference());
    }

    @Test
    void buildSlotBindingsSummaryPreservesBoundRoleFacts() throws Exception {
        JsonNode slotBindingsSummary = objectMapper.readTree("""
                {
                  "bound_slots_count": 2,
                  "bound_role_names": ["precipitation", "eto"]
                }
                """);

        TaskDetailResponse.SlotBindingsSummary summary = TaskProjectionBuilder.buildSlotBindingsSummary(slotBindingsSummary);

        assertEquals(2, summary.getBoundSlotsCount());
        assertEquals(List.of("precipitation", "eto"), summary.getBoundRoleNames());
    }

    @Test
    void buildArgsDraftSummaryPreservesSortedParamKeys() throws Exception {
        JsonNode argsDraftSummary = objectMapper.readTree("""
                {
                  "param_count": 3,
                  "param_keys": ["eto_slot", "precipitation_slot", "workspace_dir"]
                }
                """);

        TaskDetailResponse.ArgsDraftSummary summary = TaskProjectionBuilder.buildArgsDraftSummary(argsDraftSummary);

        assertEquals(3, summary.getParamCount());
        assertEquals(List.of("eto_slot", "precipitation_slot", "workspace_dir"), summary.getParamKeys());
    }

    @Test
    void buildValidationSummaryPreservesValidationFacts() throws Exception {
        JsonNode validationSummary = objectMapper.readTree("""
                {
                  "is_valid": false,
                  "missing_roles": ["precipitation"],
                  "missing_params": ["workspace_dir"],
                  "error_code": "MISSING_ROLE",
                  "invalid_bindings": ["eto"]
                }
                """);

        TaskDetailResponse.ValidationSummary summary = TaskProjectionBuilder.buildValidationSummary(validationSummary);

        assertEquals(false, summary.getIsValid());
        assertEquals(List.of("precipitation"), summary.getMissingRoles());
        assertEquals(List.of("workspace_dir"), summary.getMissingParams());
        assertEquals("MISSING_ROLE", summary.getErrorCode());
        assertEquals(List.of("eto"), summary.getInvalidBindings());
    }

    @Test
    void buildPass2SummaryPreservesPlannerFacts() throws Exception {
        JsonNode pass2Summary = objectMapper.readTree("""
                {
                  "graph_digest": "digest_123",
                  "planning_summary": {
                    "planner": "pass2_minimal",
                    "node_count": 4,
                    "edge_count": 3,
                    "validation_is_valid": true,
                    "validation_error_code": "OK",
                    "capability_key": "water_yield",
                    "template": "water_yield_v1",
                    "runtime_assertion_count": 6
                  },
                  "canonicalization_summary": {
                    "canonicalizer": "deterministic_sort_v1",
                    "node_order_normalized": true
                  },
                  "rewrite_summary": {
                    "rewriter": "whitelist_minimal",
                    "duplicate_nodes_removed": 1
                  }
                }
                """);

        TaskDetailResponse.Pass2Summary summary = TaskProjectionBuilder.buildPass2Summary(pass2Summary);

        assertEquals("pass2_minimal", summary.getPlanner());
        assertEquals(4, summary.getNodeCount());
        assertEquals(3, summary.getEdgeCount());
        assertEquals(true, summary.getValidationIsValid());
        assertEquals("OK", summary.getValidationErrorCode());
        assertEquals("water_yield", summary.getCapabilityKey());
        assertEquals("water_yield_v1", summary.getTemplate());
        assertEquals(6, summary.getRuntimeAssertionCount());
        assertEquals("digest_123", summary.getGraphDigest());
        assertEquals("deterministic_sort_v1", summary.getCanonicalizationSummary().get("canonicalizer"));
        assertEquals("whitelist_minimal", summary.getRewriteSummary().get("rewriter"));
    }

    @Test
    void buildResultObjectSummaryPreservesArtifactFacts() throws Exception {
        JsonNode resultObjectSummary = objectMapper.readTree("""
                {
                  "result_id": "result_001",
                  "summary": "done",
                  "artifact_count": 2,
                  "created_at": "2026-03-26T10:00:00Z"
                }
                """);

        TaskDetailResponse.ResultObjectSummary summary = TaskProjectionBuilder.buildResultObjectSummary(resultObjectSummary);

        assertEquals("result_001", summary.getResultId());
        assertEquals("done", summary.getSummary());
        assertEquals(2, summary.getArtifactCount());
        assertEquals("2026-03-26T10:00:00Z", summary.getCreatedAt());
    }

    @Test
    void buildResultBundleSummaryPreservesCapabilityDrivenOutputNames() throws Exception {
        JsonNode resultBundle = objectMapper.readTree("""
                {
                  "result_id": "result_001",
                  "summary": "done",
                  "main_outputs": ["water_yield_result", "watershed_summary"],
                  "primary_outputs": ["result_bundle.json", "water_yield_result.json"],
                  "audit_artifacts": ["run_manifest.json", "runtime_request.json"],
                  "created_at": "2026-03-26T10:00:00Z"
                }
                """);

        Map<String, Object> summary = TaskProjectionBuilder.buildResultBundleSummary(resultBundle);

        assertEquals("result_001", summary.get("result_id"));
        assertEquals(2, summary.get("main_output_count"));
        assertEquals(List.of("water_yield_result", "watershed_summary"), summary.get("main_outputs"));
        assertEquals(List.of("result_bundle.json", "water_yield_result.json"), summary.get("primary_outputs"));
        assertEquals(List.of("run_manifest.json", "runtime_request.json"), summary.get("audit_artifacts"));
    }

    @Test
    void buildResultBundleSummaryViewPreservesCapabilityDrivenOutputNames() throws Exception {
        JsonNode resultBundleSummary = objectMapper.readTree("""
                {
                  "result_id": "result_001",
                  "summary": "done",
                  "main_output_count": 2,
                  "main_outputs": ["water_yield_result", "watershed_summary"],
                  "primary_outputs": ["result_bundle.json", "water_yield_result.json"],
                  "audit_artifacts": ["run_manifest.json", "runtime_request.json"],
                  "created_at": "2026-03-26T10:00:00Z"
                }
                """);

        TaskDetailResponse.ResultBundleSummary summary = TaskProjectionBuilder.buildResultBundleSummaryView(resultBundleSummary);

        assertEquals("result_001", summary.getResultId());
        assertEquals("done", summary.getSummary());
        assertEquals(2, summary.getMainOutputCount());
        assertEquals(List.of("water_yield_result", "watershed_summary"), summary.getMainOutputs());
        assertEquals(List.of("result_bundle.json", "water_yield_result.json"), summary.getPrimaryOutputs());
        assertEquals(List.of("run_manifest.json", "runtime_request.json"), summary.getAuditArtifacts());
        assertEquals("2026-03-26T10:00:00Z", summary.getCreatedAt());
    }

    @Test
    void buildFinalExplanationSummaryPreservesSummaryFacts() throws Exception {
        JsonNode finalExplanationSummary = objectMapper.readTree("""
                {
                  "title": "Water yield completed",
                  "highlight_count": 2,
                  "generated_at": "2026-03-26T10:00:00Z"
                }
                """);

        TaskDetailResponse.FinalExplanationSummary summary = TaskProjectionBuilder.buildFinalExplanationSummary(finalExplanationSummary);

        assertEquals("Water yield completed", summary.getTitle());
        assertEquals(2, summary.getHighlightCount());
        assertEquals("2026-03-26T10:00:00Z", summary.getGeneratedAt());
    }

    @Test
    void buildFailureSummaryPreservesFailureFacts() throws Exception {
        JsonNode failureSummary = objectMapper.readTree("""
                {
                  "failure_code": "JOB_RUNTIME_ERROR",
                  "failure_message": "Job runtime error",
                  "created_at": "2026-03-26T10:00:00Z"
                }
                """);

        TaskDetailResponse.FailureSummary summary = TaskProjectionBuilder.buildFailureSummary(failureSummary);

        assertEquals("JOB_RUNTIME_ERROR", summary.getFailureCode());
        assertEquals("Job runtime error", summary.getFailureMessage());
        assertEquals("2026-03-26T10:00:00Z", summary.getCreatedAt());
    }

    @Test
    void buildFailureSummaryPayloadFallsBackToErrorObjectFacts() throws Exception {
        JsonNode errorObject = objectMapper.readTree("""
                {
                  "error_code": "CONTAINER_EXITED",
                  "message": "Container exited unexpectedly"
                }
                """);

        Map<String, Object> payload = TaskProjectionBuilder.buildFailureSummaryPayload(
                null,
                errorObject,
                "2026-03-26T11:00:00Z"
        );

        assertEquals("CONTAINER_EXITED", payload.get("failure_code"));
        assertEquals("Container exited unexpectedly", payload.get("failure_message"));
        assertEquals("2026-03-26T11:00:00Z", payload.get("created_at"));
    }

    @Test
    void buildFailureSummaryPayloadFallsBackToUnknownFailure() {
        Map<String, Object> payload = TaskProjectionBuilder.buildFailureSummaryPayload(
                null,
                null,
                "2026-03-26T12:00:00Z"
        );

        assertEquals("UNKNOWN_FAILURE", payload.get("failure_code"));
        assertEquals("Job failed without detail", payload.get("failure_message"));
        assertEquals("2026-03-26T12:00:00Z", payload.get("created_at"));
    }

    @Test
    void buildWaitingContextPreservesAuthorityFacts() throws Exception {
        JsonNode waitingContext = objectMapper.readTree("""
                {
                  "waiting_reason_type": "MISSING_INPUT",
                  "missing_slots": [
                    {"slot_name": "precipitation", "expected_type": "file", "required": true}
                  ],
                  "invalid_bindings": ["eto"],
                  "required_user_actions": [
                    {"action_type": "upload", "key": "upload_precipitation", "label": "Upload precipitation", "required": true}
                  ],
                  "resume_hint": "Upload required files.",
                  "can_resume": false
                }
                """);

        TaskDetailResponse.WaitingContext summary = TaskProjectionBuilder.buildWaitingContext(waitingContext);

        assertEquals("MISSING_INPUT", summary.getWaitingReasonType());
        assertEquals(1, summary.getMissingSlots().size());
        assertEquals("precipitation", summary.getMissingSlots().get(0).getSlotName());
        assertEquals("file", summary.getMissingSlots().get(0).getExpectedType());
        assertEquals(true, summary.getMissingSlots().get(0).getRequired());
        assertEquals(List.of("eto"), summary.getInvalidBindings());
        assertEquals(1, summary.getRequiredUserActions().size());
        assertEquals("upload", summary.getRequiredUserActions().get(0).getActionType());
        assertEquals("upload_precipitation", summary.getRequiredUserActions().get(0).getKey());
        assertEquals("Upload precipitation", summary.getRequiredUserActions().get(0).getLabel());
        assertEquals(true, summary.getRequiredUserActions().get(0).getRequired());
        assertEquals("Upload required files.", summary.getResumeHint());
        assertEquals(false, summary.getCanResume());
    }

    @Test
    void buildRepairProposalPreservesAdvisoryFacts() throws Exception {
        JsonNode repairProposal = objectMapper.readTree("""
                {
                  "user_facing_reason": "Precipitation input is missing.",
                  "resume_hint": "Upload precipitation and retry.",
                  "action_explanations": [
                    {"key": "upload_precipitation", "message": "Upload the precipitation file."}
                  ],
                  "notes": ["Dispatcher remains authority."]
                }
                """);

        TaskDetailResponse.RepairProposal summary = TaskProjectionBuilder.buildRepairProposal(repairProposal);

        assertEquals("Precipitation input is missing.", summary.getUserFacingReason());
        assertEquals("Upload precipitation and retry.", summary.getResumeHint());
        assertEquals(1, summary.getActionExplanations().size());
        assertEquals("upload_precipitation", summary.getActionExplanations().get(0).getKey());
        assertEquals("Upload the precipitation file.", summary.getActionExplanations().get(0).getMessage());
        assertEquals(List.of("Dispatcher remains authority."), summary.getNotes());
    }

    @Test
    void applyPass1ProjectionAddsCapabilityFactsToManifestView() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "capability_key": "water_yield",
                  "capability_facts": {
                    "capability_key": "water_yield",
                    "display_name": "Water Yield",
                    "contracts": {
                      "validate_args": {
                        "input_schema": "args_draft_validation_v1",
                        "output_schema": "validation_result_v1"
                      }
                    }
                  },
                  "role_arg_mappings": [
                    {
                      "role_name": "precipitation",
                      "slot_arg_key": "precipitation_slot",
                      "value_arg_key": "precipitation_index",
                      "default_value": 1200.0
                    }
                  ],
                  "stable_defaults": {
                    "analysis_template": "water_yield_v1",
                    "root_depth_factor": 0.8,
                    "pawc_factor": 0.85
                  }
                }
                """);
        TaskManifestResponse response = new TaskManifestResponse();

        TaskProjectionBuilder.applyPass1Projection(response, pass1Result, objectMapper);

        assertEquals("water_yield", response.getCapabilityKey());
        assertEquals("Water Yield", response.getCapabilityFacts().getDisplayName());
        Map<?, ?> contracts = (Map<?, ?>) response.getCapabilityFacts().getContracts();
        Map<?, ?> validateArgsContract = (Map<?, ?>) contracts.get("validate_args");
        assertEquals("args_draft_validation_v1", validateArgsContract.get("input_schema"));
        assertEquals("precipitation_slot", response.getRoleArgMappings().get(0).getSlotArgKey());
        assertEquals("precipitation_index", response.getRoleArgMappings().get(0).getValueArgKey());
        assertEquals(1200.0, response.getRoleArgMappings().get(0).getDefaultValue());
        assertEquals("water_yield_v1", response.getStableDefaults().getAnalysisTemplate());
        assertEquals(0.8, response.getStableDefaults().getRootDepthFactor());
        assertEquals(0.85, response.getStableDefaults().getPawcFactor());
    }

    @Test
    void buildTaskResultBundlePreservesRuntimeResultFacts() throws Exception {
        JsonNode resultBundle = objectMapper.readTree("""
                {
                  "result_id": "result_001",
                  "task_id": "task_001",
                  "job_id": "job_001",
                  "summary": "done",
                  "metrics": {"water_yield": 12.5},
                  "output_registry": {
                    "watershed_results_wyield_vector_path": "/workspace/output/watershed_results_wyield.shp"
                  },
                  "primary_output_refs": [
                    {
                      "output_id": "watershed_results_wyield_vector_path",
                      "path": "/workspace/output/watershed_results_wyield.shp"
                    }
                  ],
                  "main_outputs": ["water_yield_result"],
                  "artifacts": ["result_bundle.json"],
                  "primary_outputs": ["result_bundle.json"],
                  "intermediate_outputs": [],
                  "audit_artifacts": ["run_manifest.json"],
                  "derived_outputs": [],
                  "created_at": "2026-03-26T10:00:00Z"
                }
                """);

        TaskResultResponse.ResultBundle summary = TaskProjectionBuilder.buildTaskResultBundle(resultBundle);

        assertEquals("result_001", summary.getResultId());
        assertEquals("task_001", summary.getTaskId());
        assertEquals("job_001", summary.getJobId());
        assertEquals("done", summary.getSummary());
        assertEquals(12.5, summary.getMetrics().get("water_yield"));
        assertEquals("/workspace/output/watershed_results_wyield.shp", summary.getOutputRegistry().get("watershed_results_wyield_vector_path"));
        assertEquals(1, summary.getPrimaryOutputRefs().size());
        assertEquals("watershed_results_wyield_vector_path", summary.getPrimaryOutputRefs().get(0).getOutputId());
        assertEquals(List.of("water_yield_result"), summary.getMainOutputs());
        assertEquals(List.of("result_bundle.json"), summary.getArtifacts());
        assertEquals(List.of("run_manifest.json"), summary.getAuditArtifacts());
    }

    @Test
    void buildDockerRuntimeEvidencePreservesRuntimeFields() throws Exception {
        JsonNode evidence = objectMapper.readTree("""
                {
                  "container_name": "runtime-1",
                  "image": "sage-pass1:latest",
                  "workspace_output_path": "/tmp/ws",
                  "result_file_exists": true,
                  "provider_key": "invest-local",
                  "runtime_profile": "docker-local",
                  "case_id": "wy_case_a",
                  "contract_mode": "real_case_prep_v1",
                  "runtime_mode": "deterministic_stub",
                  "input_bindings": [
                    {
                      "role_name": "precipitation",
                      "slot_name": "precipitation",
                      "source": "template_direct",
                      "arg_key": "precipitation_slot",
                      "provider_input_path": "/sample-data/water_yield/wy_case_a/inputs/precipitation.tif",
                      "source_ref": "sample-data:wy_case_a:precipitation"
                    }
                  ]
                }
                """);

        TaskResultResponse.DockerRuntimeEvidence summary = TaskProjectionBuilder.buildDockerRuntimeEvidence(evidence);

        assertEquals("runtime-1", summary.getContainerName());
        assertEquals("sage-pass1:latest", summary.getImage());
        assertEquals("/tmp/ws", summary.getWorkspaceOutputPath());
        assertEquals(true, summary.getResultFileExists());
        assertEquals("invest-local", summary.getProviderKey());
        assertEquals("docker-local", summary.getRuntimeProfile());
        assertEquals("wy_case_a", summary.getCaseId());
        assertEquals("real_case_prep_v1", summary.getContractMode());
        assertEquals("deterministic_stub", summary.getRuntimeMode());
        assertEquals(1, summary.getInputBindings().size());
        assertEquals("precipitation", summary.getInputBindings().get(0).getRoleName());
    }

    @Test
    void buildArtifactCatalogPreservesArtifactMetaLists() throws Exception {
        JsonNode catalog = objectMapper.readTree("""
                {
                  "primary_outputs": [
                    {
                      "artifact_id": "art_1",
                      "artifact_role": "PRIMARY_OUTPUT",
                      "logical_name": "water_yield_result",
                      "relative_path": "result_bundle.json",
                      "absolute_path": "/tmp/result_bundle.json",
                      "content_type": "application/json",
                      "size_bytes": 128,
                      "sha256": "abc"
                    }
                  ],
                  "audit_artifacts": [],
                  "intermediate_outputs": [],
                  "derived_outputs": [],
                  "logs": []
                }
                """);

        TaskResultResponse.ArtifactCatalog summary = TaskProjectionBuilder.buildArtifactCatalog(catalog);

        assertEquals(1, summary.getPrimaryOutputs().size());
        assertEquals("art_1", summary.getPrimaryOutputs().get(0).getArtifactId());
        assertEquals("water_yield_result", summary.getPrimaryOutputs().get(0).getLogicalName());
        assertEquals("/tmp/result_bundle.json", summary.getPrimaryOutputs().get(0).getAbsolutePath());
    }

    @Test
    void buildManifestGoalParsePreservesRouteFacts() throws Exception {
        JsonNode goalParse = objectMapper.readTree("""
                {
                  "goal_type": "water_yield_analysis",
                  "user_query": "run water yield",
                  "analysis_kind": "water_yield",
                  "intent_mode": "deterministic_phase0",
                  "entities": ["precipitation", "eto"],
                  "source": "goal_router"
                }
                """);

        TaskManifestResponse.GoalParse summary = TaskProjectionBuilder.buildManifestGoalParse(goalParse);

        assertEquals("water_yield_analysis", summary.getGoalType());
        assertEquals("run water yield", summary.getUserQuery());
        assertEquals("water_yield", summary.getAnalysisKind());
        assertEquals("deterministic_phase0", summary.getIntentMode());
        assertEquals(List.of("precipitation", "eto"), summary.getEntities());
        assertEquals("goal_router", summary.getSource());
    }

    @Test
    void buildManifestSkillRoutePreservesCanonicalRouteFacts() throws Exception {
        JsonNode skillRoute = objectMapper.readTree("""
                {
                  "route_mode": "single_skill",
                  "primary_skill": "water_yield",
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "capability_key": "water_yield",
                  "route_source": "deterministic_phase0",
                  "confidence": 0.9,
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "execution_mode": "real_case_validation",
                  "provider_preference": "planning-pass1-invest-local",
                  "runtime_profile_preference": "docker-invest-real",
                  "source": "goal_router"
                }
                """);

        TaskManifestResponse.SkillRoute summary = TaskProjectionBuilder.buildManifestSkillRoute(skillRoute);

        assertEquals("single_skill", summary.getRouteMode());
        assertEquals("water_yield", summary.getPrimarySkill());
        assertEquals("water_yield", summary.getSkillId());
        assertEquals("1.0.0", summary.getSkillVersion());
        assertEquals("water_yield", summary.getCapabilityKey());
        assertEquals("deterministic_phase0", summary.getRouteSource());
        assertEquals(0.9, summary.getConfidence());
        assertEquals("water_yield_v1", summary.getSelectedTemplate());
        assertEquals("1.0.0", summary.getTemplateVersion());
        assertEquals("real_case_validation", summary.getExecutionMode());
        assertEquals("planning-pass1-invest-local", summary.getProviderPreference());
        assertEquals("docker-invest-real", summary.getRuntimeProfilePreference());
        assertEquals("goal_router", summary.getSource());
    }

    @Test
    void buildManifestValidationSummaryPreservesValidationFacts() throws Exception {
        JsonNode validationSummary = objectMapper.readTree("""
                {
                  "is_valid": false,
                  "missing_roles": ["precipitation"],
                  "missing_params": [],
                  "error_code": "MISSING_ROLE",
                  "invalid_bindings": []
                }
                """);

        TaskManifestResponse.ValidationSummary summary = TaskProjectionBuilder.buildManifestValidationSummary(validationSummary);

        assertEquals(false, summary.getIsValid());
        assertEquals(List.of("precipitation"), summary.getMissingRoles());
        assertEquals("MISSING_ROLE", summary.getErrorCode());
    }

    @Test
    void buildManifestLogicalInputRolesPayloadNormalizesRoleFacts() throws Exception {
        JsonNode pass1Result = objectMapper.readTree("""
                {
                  "logical_input_roles": [
                    {"role_name": "precipitation", "required": true},
                    {"role_name": "eto", "required": false}
                  ]
                }
                """);

        JsonNode payload = TaskProjectionBuilder.buildManifestLogicalInputRolesPayload(pass1Result, objectMapper);

        assertEquals(true, payload.isArray());
        assertEquals("precipitation", payload.get(0).path("role_name").asText());
        assertEquals(true, payload.get(0).path("required").asBoolean());
        assertEquals("eto", payload.get(1).path("role_name").asText());
        assertEquals(false, payload.get(1).path("required").asBoolean());
    }

    @Test
    void buildManifestRuntimeAssertionsPayloadNormalizesAssertionFacts() throws Exception {
        JsonNode runtimeAssertions = objectMapper.readTree("""
                [
                  {"name": "arg:workspace_dir", "required": true, "message": "workspace_dir must be present"},
                  {"name": "binding:precipitation", "required": false, "message": "precipitation binding is optional"}
                ]
                """);

        JsonNode payload = TaskProjectionBuilder.buildManifestRuntimeAssertionsPayload(runtimeAssertions, objectMapper);

        assertEquals(true, payload.isArray());
        assertEquals("arg:workspace_dir", payload.get(0).path("name").asText());
        assertEquals(true, payload.get(0).path("required").asBoolean());
        assertEquals("binding:precipitation", payload.get(1).path("name").asText());
        assertEquals(false, payload.get(1).path("required").asBoolean());
    }

    @Test
    void buildExecutionGraphPayloadNormalizesExecutionFacts() throws Exception {
        JsonNode graph = objectMapper.readTree("""
                {
                  "nodes": [
                    {"node_id": "prepare_workspace", "kind": "system"},
                    {"node_id": "run_minimal_analyzer", "kind": "analysis"}
                  ],
                  "edges": [
                    ["prepare_workspace", "run_minimal_analyzer"]
                  ]
                }
                """);

        JsonNode payload = TaskProjectionBuilder.buildExecutionGraphPayload(graph, objectMapper);

        assertEquals(true, payload.isObject());
        assertEquals("prepare_workspace", payload.path("nodes").get(0).path("node_id").asText());
        assertEquals("analysis", payload.path("nodes").get(1).path("kind").asText());
        assertEquals("prepare_workspace", payload.path("edges").get(0).get(0).asText());
    }

    @Test
    void buildPlanningPass2SummaryPayloadPreservesPlannerFacts() throws Exception {
        JsonNode planningSummary = objectMapper.readTree("""
                {
                  "planner": "pass2_minimal",
                  "node_count": 4,
                  "edge_count": 3,
                  "validation_is_valid": true,
                  "validation_error_code": "NONE",
                  "capability_key": "water_yield",
                  "template": "water_yield_v1",
                  "runtime_assertion_count": 8
                }
                """);

        JsonNode payload = TaskProjectionBuilder.buildPlanningPass2SummaryPayload(planningSummary, objectMapper);

        assertEquals(true, payload.isObject());
        assertEquals("pass2_minimal", payload.path("planner").asText());
        assertEquals(4, payload.path("node_count").asInt());
        assertEquals("water_yield", payload.path("capability_key").asText());
        assertEquals(8, payload.path("runtime_assertion_count").asInt());
    }

    @Test
    void buildManifestExecutionGraphPreservesNodeFacts() throws Exception {
        JsonNode graph = objectMapper.readTree("""
                {
                  "nodes": [
                    {"node_id": "prepare_workspace", "kind": "system"},
                    {"node_id": "run_minimal_analyzer", "kind": "analysis"}
                  ],
                  "edges": [
                    ["prepare_workspace", "run_minimal_analyzer"]
                  ]
                }
                """);

        TaskManifestResponse.ExecutionGraph summary = TaskProjectionBuilder.buildManifestExecutionGraph(graph);

        assertEquals(2, summary.getNodes().size());
        assertEquals("prepare_workspace", summary.getNodes().get(0).getNodeId());
        assertEquals("analysis", summary.getNodes().get(1).getKind());
        assertEquals(List.of("prepare_workspace", "run_minimal_analyzer"), summary.getEdges().get(0));
    }
}
