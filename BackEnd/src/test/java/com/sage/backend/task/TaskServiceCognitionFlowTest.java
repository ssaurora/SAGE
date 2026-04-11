package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.audit.AuditService;
import com.sage.backend.cognition.CognitionFinalExplanationClient;
import com.sage.backend.cognition.CognitionGoalRouteClient;
import com.sage.backend.cognition.CognitionPassBClient;
import com.sage.backend.cognition.dto.CognitionGoalRouteResponse;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.JobRuntimeClient;
import com.sage.backend.mapper.AnalysisManifestMapper;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.RepairRecordMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.mapper.TaskAttemptMapper;
import com.sage.backend.mapper.TaskCatalogSnapshotMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.planning.dto.Pass2Response;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import com.sage.backend.repair.dto.RepairProposalResponse;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.validationgate.ValidationClient;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceCognitionFlowTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createTaskUnsupportedDoesNotRerouteDeterministically() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponse("unsupported"));

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run gura carbon case");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.FAILED.name(), response.getState());
        verify(harness.pass1Client, never()).runPass1(any());
        verify(harness.cognitionPassBClient, never()).runPassB(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void createTaskAmbiguousTransitionsToWaitingUserWithClarify() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponse("ambiguous"));
        when(harness.taskStateMapper.findByTaskId(anyString())).thenReturn(taskStateForProjection());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run invest real case for gura");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), response.getState());
        verify(harness.pass1Client, never()).runPass1(any());
        verify(harness.cognitionPassBClient, never()).runPassB(any());
        verify(harness.taskStateMapper).updateStateWithWaitingContext(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTaskBindingAmbiguousStopsBeforeExecution() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponse("resolved"));
        when(harness.pass1Client.runPass1(any())).thenReturn(defaultPass1Response());
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(bindingAmbiguousPassBResponse());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run water yield");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), response.getState());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void createTaskPassBAssetFailureFailsWithoutValidationOrExecution() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponseWithCaseProjection(
                "resolved",
                "real_case_validation",
                Map.of(
                        "mode", "resolved",
                        "selected_case_id", "annual_water_yield_gura",
                        "candidate_case_ids", List.of("annual_water_yield_gura")
                )
        ));
        when(harness.pass1Client.runPass1(any())).thenReturn(defaultPass1Response());
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBAssetFailureResponse());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run a real case invest annual water yield analysis for gura");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.FAILED.name(), response.getState());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void createTaskMissingSubmitJobContractFailsBeforeExecution() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponse("resolved"));
        when(harness.pass1Client.runPass1(any())).thenReturn(pass1ResponseWithoutContract("submit_job"));
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(bindingResolvedPassBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenReturn(defaultPass2Response());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run water yield");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.createTask(42L, request)
        );

        assertEquals(502, exception.getStatusCode().value());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void createTaskRealCaseGoalRouteClarifyRequiredTransitionsToWaitingUser() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponseWithCaseProjection(
                "resolved",
                "real_case_validation",
                Map.of(
                        "mode", "clarify_required",
                        "candidate_case_ids", List.of("annual_water_yield_gura", "annual_water_yield_blue_nile_fixture"),
                        "clarify_prompt", "Choose a governed annual water yield case."
                )
        ));
        when(harness.taskStateMapper.findByTaskId(anyString())).thenReturn(taskStateForProjection());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run a real case invest annual water yield analysis");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), response.getState());
        verify(harness.pass1Client, never()).runPass1(any());
        verify(harness.cognitionPassBClient, never()).runPassB(any());
        verify(harness.taskStateMapper).updateStateWithWaitingContext(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTaskRealCaseGoalRouteFallbackClarifyRequiredStillTransitionsToWaitingUser() throws Exception {
        Harness harness = new Harness(objectMapper);
        CognitionGoalRouteResponse response = goalRouteResponseWithCaseProjection(
                "resolved",
                "real_case_validation",
                Map.of(
                        "mode", "clarify_required",
                        "candidate_case_ids", List.of("annual_water_yield_gura", "annual_water_yield_gtm_national"),
                        "clarify_prompt", "Choose a governed annual water yield case."
                )
        );
        response.setCognitionMetadata(Map.of(
                "fallback_used", true,
                "schema_valid", true,
                "source", "glm_fallback_projection",
                "provider", "glm",
                "status", "LLM_FALLBACK",
                "failure_code", "COGNITION_UNAVAILABLE"
        ));
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(response);
        when(harness.taskStateMapper.findByTaskId(anyString())).thenReturn(taskStateForProjection());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run a real case invest annual water yield analysis");

        CreateTaskResponse createResponse = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), createResponse.getState());
        verify(harness.pass1Client, never()).runPass1(any());
        verify(harness.cognitionPassBClient, never()).runPassB(any());
        verify(harness.taskStateMapper).updateStateWithWaitingContext(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void createTaskRealCasePassBClarifyRequiredTransitionsToWaitingUser() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponseWithCaseProjection(
                "resolved",
                "real_case_validation",
                Map.of(
                        "mode", "resolved",
                        "selected_case_id", "annual_water_yield_gura",
                        "candidate_case_ids", List.of("annual_water_yield_gura")
                )
        ));
        when(harness.pass1Client.runPass1(any())).thenReturn(defaultPass1Response());
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(bindingClarifyRequiredPassBResponse());

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run a real case invest annual water yield analysis");

        CreateTaskResponse response = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), response.getState());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void createTaskRealCasePassBFallbackClarifyRequiredStillTransitionsToWaitingUser() throws Exception {
        Harness harness = new Harness(objectMapper);
        when(harness.cognitionGoalRouteClient.route(any())).thenReturn(goalRouteResponseWithCaseProjection(
                "resolved",
                "real_case_validation",
                Map.of(
                        "mode", "resolved",
                        "selected_case_id", "annual_water_yield_gura",
                        "candidate_case_ids", List.of("annual_water_yield_gura")
                )
        ));
        when(harness.pass1Client.runPass1(any())).thenReturn(defaultPass1Response());
        CognitionPassBResponse response = bindingClarifyRequiredPassBResponse();
        response.setCognitionMetadata(Map.of(
                "fallback_used", true,
                "schema_valid", true,
                "source", "glm_fallback_projection",
                "provider", "glm",
                "status", "LLM_FALLBACK",
                "failure_code", "COGNITION_UNAVAILABLE"
        ));
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(response);

        CreateTaskRequest request = new CreateTaskRequest();
        request.setUserQuery("run a real case invest annual water yield analysis");

        CreateTaskResponse createResponse = harness.service.createTask(42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), createResponse.getState());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
    }

    @Test
    void getTaskProjectsCognitionAuthorityFacts() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = taskStateForProjection();
        when(harness.taskStateMapper.findByTaskId("task_projection")).thenReturn(taskState);
        when(harness.taskAttachmentMapper.findByTaskId("task_projection")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskDetailResponse response = harness.service.getTask("task_projection", 42L);

        assertEquals("resolved", response.getPlanningIntentStatus());
        assertEquals("resolved", response.getBindingStatus());
        assertEquals("water_yield", response.getSkillId());
        assertEquals("1.0.0", response.getSkillVersion());
        assertEquals(List.of("case_id"), response.getOverruledFields());
        assertEquals(List.of("case_id"), response.getBlockedMutations());
        assertTrue(Boolean.TRUE.equals(response.getAssemblyBlocked()));
        assertEquals("PRIMARY_OVERRULED", response.getCognitionVerdict());
        assertEquals("clarify_required", response.getCaseProjection().get("mode"));
        assertEquals("water_yield", response.getSkillRouteSummary().getSkillId());
        assertEquals("1.0.0", response.getSkillRouteSummary().getSkillVersion());
        assertEquals(1, response.getCatalogSummary().get("catalog_asset_count"));
        assertEquals(1, response.getCatalogSummary().get("catalog_ready_asset_count"));
        assertEquals(List.of("precipitation"), response.getCatalogSummary().get("catalog_ready_role_names"));
        assertEquals("task_catalog_snapshot", response.getCatalogSummary().get("catalog_source"));
        assertEquals("waiting_context_catalog", response.getCatalogConsistency().get("scope"));
        assertEquals(false, response.getCatalogConsistency().get("baseline_catalog_present"));
        assertEquals(List.of("precipitation"), response.getCatalogConsistency().get("stale_missing_slots"));
        assertEquals("task_contract", response.getContractConsistency().get("scope"));
        assertEquals(null, response.getContractConsistency().get("mismatch_code"));
        assertEquals("CONTRACT_MATCHED", response.getContractConsistency().get("consistency_code"));
        assertEquals("COMPATIBLE", response.getContractConsistency().get("compatibility_code"));
        assertEquals("NO_ACTION", response.getContractConsistency().get("migration_hint"));
        assertEquals("task_contract_governance", response.getContractGovernance().getScope());
        assertEquals("COMPATIBLE", response.getContractGovernance().getConsistency().getCompatibilityCode());
        assertEquals(true, response.getContractConsistency().get("current_contract_present"));
        assertEquals(true, response.getContractConsistency().get("frozen_contract_present"));
        assertEquals("water_yield_contracts_v1", response.getContractConsistency().get("current_contract_version"));
    }

    private CognitionGoalRouteResponse goalRouteResponse(String status) throws Exception {
        return goalRouteResponseWithCaseProjection(status, "governed_baseline", Map.of());
    }

    private CognitionGoalRouteResponse goalRouteResponseWithCaseProjection(
            String status,
            String executionMode,
            Map<String, Object> caseProjection
    ) throws Exception {
        CognitionGoalRouteResponse response = new CognitionGoalRouteResponse();
        response.setPlanningIntentStatus(status);
        response.setGoalParse(objectMapper.readTree("""
                {
                  "goal_type": "water_yield_analysis",
                  "user_query": "query",
                  "analysis_kind": "water_yield",
                  "intent_mode": "cognition_test",
                  "source": "cognition_goal_route",
                  "entities": []
                }
                """));
        response.setSkillRoute(objectMapper.readTree("""
                {
                  "route_mode": "single_skill",
                  "primary_skill": "water_yield",
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "analysis_type": "Annual Water Yield Analysis",
                  "capability_key": "water_yield",
                  "route_source": "cognition_test",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "execution_mode": "%s"
                }
                """.formatted(executionMode)));
        response.setConfidence(0.9);
        response.setDecisionSummary(Map.of("strategy", "test"));
        response.setCognitionMetadata(Map.of("fallback_used", false, "schema_valid", true, "source", "test", "provider", "glm"));
        response.setCaseProjection(caseProjection);
        return response;
    }

    private Pass1Response defaultPass1Response() throws Exception {
        return objectMapper.readValue(samplePass1Json(), Pass1Response.class);
    }

    private CognitionPassBResponse bindingAmbiguousPassBResponse() {
        CognitionPassBResponse response = new CognitionPassBResponse();
        response.setSkillId("water_yield");
        response.setSkillVersion("1.0.0");
        response.setBindingStatus("ambiguous");
        response.setSlotBindings(List.of());
        response.setUserSemanticArgs(Map.of());
        response.setInferredSemanticArgs(Map.of());
        response.setArgsDraft(Map.of());
        response.setDecisionSummary(Map.of("strategy", "ambiguous"));
        response.setCognitionMetadata(Map.of("fallback_used", false, "schema_valid", true, "source", "test", "provider", "glm"));
        return response;
    }

    private CognitionPassBResponse bindingClarifyRequiredPassBResponse() {
        CognitionPassBResponse response = new CognitionPassBResponse();
        response.setSkillId("water_yield");
        response.setSkillVersion("1.0.0");
        response.setBindingStatus("ambiguous");
        response.setSlotBindings(List.of());
        response.setUserSemanticArgs(Map.of());
        response.setInferredSemanticArgs(Map.of());
        response.setArgsDraft(Map.of());
        response.setCaseProjection(Map.of(
                "mode", "clarify_required",
                "candidate_case_ids", List.of("annual_water_yield_gura", "annual_water_yield_blue_nile_fixture"),
                "clarify_prompt", "Choose a governed annual water yield case."
        ));
        response.setDecisionSummary(Map.of("strategy", "clarify"));
        response.setCognitionMetadata(Map.of("fallback_used", false, "schema_valid", true, "source", "test", "provider", "glm"));
        return response;
    }

    private CognitionPassBResponse passBAssetFailureResponse() {
        CognitionPassBResponse response = new CognitionPassBResponse();
        response.setSkillId("water_yield");
        response.setSkillVersion("1.0.0");
        response.setBindingStatus("ambiguous");
        response.setSlotBindings(List.of());
        response.setUserSemanticArgs(Map.of());
        response.setInferredSemanticArgs(Map.of());
        response.setArgsDraft(Map.of());
        response.setDecisionSummary(Map.of("strategy", "asset_failure"));
        response.setCognitionMetadata(Map.of(
                "fallback_used", false,
                "schema_valid", false,
                "source", "skill_asset",
                "provider", "skill_asset",
                "status", "PARAMETER_SCHEMA_UNAVAILABLE",
                "failure_code", "PARAMETER_SCHEMA_UNAVAILABLE",
                "failure_message", "Required skill asset file missing: parameter_schema.yaml"
        ));
        return response;
    }

    private CognitionPassBResponse bindingResolvedPassBResponse() {
        CognitionPassBResponse response = new CognitionPassBResponse();
        response.setSkillId("water_yield");
        response.setSkillVersion("1.0.0");
        response.setBindingStatus("resolved");
        response.setSlotBindings(List.of());
        response.setUserSemanticArgs(Map.of());
        response.setInferredSemanticArgs(Map.of());
        response.setArgsDraft(Map.of(
                "workspace_dir", "/tmp/workspace",
                "results_suffix", "attempt_1",
                "analysis_template", "water_yield_v1"
        ));
        response.setDecisionSummary(Map.of("strategy", "resolved"));
        response.setCognitionMetadata(Map.of("fallback_used", false, "schema_valid", true, "source", "test", "provider", "glm"));
        return response;
    }

    private TaskState taskStateForProjection() {
        TaskState taskState = new TaskState();
        taskState.setTaskId("task_projection");
        taskState.setUserId(42L);
        taskState.setCurrentState(TaskStatus.WAITING_USER.name());
        taskState.setStateVersion(3);
        taskState.setPlanningRevision(1);
        taskState.setCheckpointVersion(1);
        taskState.setCognitionVerdict("PRIMARY_OVERRULED");
        taskState.setGoalParseJson("""
                {
                  "planning_intent_status": "resolved",
                  "goal_type": "water_yield_analysis",
                  "analysis_kind": "water_yield",
                  "case_projection": {
                    "mode": "clarify_required",
                    "candidate_case_ids": ["annual_water_yield_gura", "annual_water_yield_blue_nile_fixture"],
                    "clarify_prompt": "Choose a governed annual water yield case."
                  }
                }
                """);
        taskState.setSkillRouteJson("""
                {
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1"
                }
                """);
        taskState.setPassbResultJson("""
                {
                  "binding_status": "resolved",
                  "overruled_fields": ["case_id"],
                  "blocked_mutations": ["case_id"],
                  "assembly_blocked": true,
                  "case_projection": {
                    "mode": "clarify_required",
                    "candidate_case_ids": ["annual_water_yield_gura", "annual_water_yield_blue_nile_fixture"],
                    "clarify_prompt": "Choose a governed annual water yield case."
                  },
                  "slot_bindings": [],
                  "args_draft": {}
                }
                """);
        taskState.setPass1ResultJson(samplePass1Json());
        taskState.setValidationSummaryJson("{}");
        taskState.setWaitingContextJson("""
                {
                  "waiting_reason_type": "MISSING_INPUT",
                  "missing_slots": [
                    {"slot_name": "precipitation", "expected_type": "raster", "required": true}
                  ],
                  "required_user_actions": [
                    {"action_type": "upload", "key": "upload_precipitation", "label": "Upload precipitation", "required": true}
                  ],
                  "resume_hint": "Upload precipitation before resuming.",
                  "can_resume": false
                }
                """);
        return taskState;
    }

    private String samplePass1Json() {
        return """
                {
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "capability_facts": {
                    "contract_version": "water_yield_contracts_v1",
                    "contract_fingerprint": "3333333333333333333333333333333333333333333333333333333333333333",
                    "runtime_profile_hint": "docker-local",
                    "validation_hints": [],
                    "repair_hints": [],
                    "contracts": {
                      "validate_bindings": {
                        "input_schema": "slot_bindings_validation_v1",
                        "output_schema": "binding_validation_summary_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      },
                      "validate_args": {
                        "input_schema": "args_draft_validation_v1",
                        "output_schema": "arg_validation_summary_v1",
                        "caller_scope": "control_or_planning",
                        "side_effect_level": "read_only"
                      },
                      "checkpoint_resume_ack": {
                        "input_schema": "checkpoint_resume_request_v1",
                        "output_schema": "checkpoint_resume_ack_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "workflow_checkpoint"
                      },
                      "submit_job": {
                        "input_schema": "create_job_request_v1",
                        "output_schema": "create_job_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "runtime_submission"
                      },
                      "cancel_job": {
                        "input_schema": "cancel_job_request_v1",
                        "output_schema": "cancel_job_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "runtime_cancellation"
                      },
                      "query_job_status": {
                        "input_schema": "job_status_request_v1",
                        "output_schema": "job_status_response_v1",
                        "caller_scope": "control_or_presentation",
                        "side_effect_level": "read_only"
                      },
                      "collect_result_bundle": {
                        "input_schema": "result_bundle_collection_request_v1",
                        "output_schema": "result_bundle_collection_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "artifact_collection"
                      },
                      "index_artifacts": {
                        "input_schema": "artifact_index_request_v1",
                        "output_schema": "artifact_index_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "artifact_indexing"
                      },
                      "record_audit": {
                        "input_schema": "audit_record_request_v1",
                        "output_schema": "audit_record_response_v1",
                        "caller_scope": "control_only",
                        "side_effect_level": "audit_write"
                      }
                    }
                  },
                  "logical_input_roles": [],
                  "role_arg_mappings": [],
                  "slot_schema_view": {
                    "slots": []
                  },
                  "graph_skeleton": {
                    "nodes": [],
                    "edges": []
                  },
                  "stable_defaults": {}
                }
                """;
    }

    private Pass1Response pass1ResponseWithoutContract(String contractName) throws Exception {
        JsonNode root = objectMapper.readTree(samplePass1Json());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("capability_facts").path("contracts")).remove(contractName);
        return objectMapper.treeToValue(root, Pass1Response.class);
    }

    private PrimitiveValidationResponse validValidation() {
        PrimitiveValidationResponse response = new PrimitiveValidationResponse();
        response.setIsValid(true);
        response.setMissingRoles(List.of());
        response.setMissingParams(List.of());
        response.setInvalidBindings(List.of());
        response.setErrorCode("NONE");
        return response;
    }

    private Pass2Response defaultPass2Response() throws Exception {
        Pass2Response response = new Pass2Response();
        response.setGraphDigest("digest_test");
        response.setMaterializedExecutionGraph(objectMapper.readTree("""
                {
                  "nodes": [{"node_id": "load_inputs", "kind": "io"}],
                  "edges": []
                }
                """));
        response.setRuntimeAssertions(objectMapper.readTree("[]"));
        response.setPlanningSummary(objectMapper.readTree("""
                {
                  "planner": "pass2_minimal",
                  "node_count": 1,
                  "edge_count": 0,
                  "validation_is_valid": true,
                  "validation_error_code": "NONE",
                  "capability_key": "water_yield",
                  "template": "water_yield_v1",
                  "runtime_assertion_count": 0
                }
                """));
        response.setCanonicalizationSummary(objectMapper.readTree("""
                {"canonicalizer": "deterministic_sort_v1"}
                """));
        response.setRewriteSummary(objectMapper.readTree("""
                {"rewriter": "whitelist_minimal"}
                """));
        return response;
    }

    private TaskAttachment readyAttachment(String logicalSlot) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setId("att_" + logicalSlot);
        attachment.setLogicalSlot(logicalSlot);
        attachment.setFileName(logicalSlot + ".tif");
        attachment.setContentType("image/tiff");
        attachment.setSizeBytes(1024L);
        attachment.setStoredPath("/workspace/input/" + logicalSlot + ".tif");
        attachment.setChecksum("sha256:" + logicalSlot);
        attachment.setAssignmentStatus("ASSIGNED");
        return attachment;
    }

    private static final class Harness {
        private final TaskStateMapper taskStateMapper = mock(TaskStateMapper.class);
        private final AnalysisManifestMapper analysisManifestMapper = mock(AnalysisManifestMapper.class);
        private final JobRecordMapper jobRecordMapper = mock(JobRecordMapper.class);
        private final TaskAttachmentMapper taskAttachmentMapper = mock(TaskAttachmentMapper.class);
        private final RepairRecordMapper repairRecordMapper = mock(RepairRecordMapper.class);
        private final TaskAttemptMapper taskAttemptMapper = mock(TaskAttemptMapper.class);
        private final TaskCatalogSnapshotMapper taskCatalogSnapshotMapper = mock(TaskCatalogSnapshotMapper.class);
        private final TaskCatalogSnapshotService taskCatalogSnapshotService;
        private final EventService eventService = mock(EventService.class);
        private final AuditService auditService = mock(AuditService.class);
        private final CognitionFinalExplanationClient cognitionFinalExplanationClient = mock(CognitionFinalExplanationClient.class);
        private final CognitionGoalRouteClient cognitionGoalRouteClient = mock(CognitionGoalRouteClient.class);
        private final Pass1Client pass1Client = mock(Pass1Client.class);
        private final CognitionPassBClient cognitionPassBClient = mock(CognitionPassBClient.class);
        private final ValidationClient validationClient = mock(ValidationClient.class);
        private final com.sage.backend.planning.Pass2Client pass2Client = mock(com.sage.backend.planning.Pass2Client.class);
        private final JobRuntimeClient jobRuntimeClient = mock(JobRuntimeClient.class);
        private final RepairDispatcherService repairDispatcherService = mock(RepairDispatcherService.class);
        private final RepairProposalService repairProposalService = mock(RepairProposalService.class);
        private final RegistryService registryService = mock(RegistryService.class);
        private final WorkspaceTraceService workspaceTraceService = mock(WorkspaceTraceService.class);
        private final TaskDetailQueryService taskDetailQueryService;
        private final TaskResultQueryService taskResultQueryService;
        private final TaskAuditQueryService taskAuditQueryService;
        private final TaskManifestQueryService taskManifestQueryService;
        private final TaskCatalogQueryService taskCatalogQueryService;
        private final TaskContractQueryService taskContractQueryService;
        private final TaskService service;

        private Harness(ObjectMapper objectMapper) {
            this.taskCatalogSnapshotService = new TaskCatalogSnapshotService(taskCatalogSnapshotMapper, objectMapper);
            GoalRouteService goalRouteService = new GoalRouteService(objectMapper);
            ExecutionContractAssembler executionContractAssembler = new ExecutionContractAssembler(objectMapper);
            this.taskDetailQueryService = new TaskDetailQueryService(taskCatalogSnapshotService, goalRouteService, objectMapper);
            this.taskResultQueryService = new TaskResultQueryService(taskCatalogSnapshotService, objectMapper);
            this.taskAuditQueryService = new TaskAuditQueryService(taskCatalogSnapshotService, objectMapper);
            this.taskManifestQueryService = new TaskManifestQueryService(taskCatalogSnapshotService, goalRouteService, objectMapper);
            this.taskCatalogQueryService = new TaskCatalogQueryService(taskCatalogSnapshotService, objectMapper);
            this.taskContractQueryService = new TaskContractQueryService(objectMapper);
            when(taskStateMapper.insert(any())).thenReturn(1);
            when(taskStateMapper.updateState(anyString(), anyInt(), anyString())).thenReturn(1);
            when(taskStateMapper.updateGoalAndRoute(anyString(), anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateStateWithWaitingContext(anyString(), anyInt(), anyString(), anyString(), anyString(), any())).thenReturn(1);
            when(taskStateMapper.updateOutputSummaries(anyString(), any(), any(), any(), any())).thenReturn(1);
            when(taskStateMapper.updateCognitionVerdict(anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateStateAndPass1(anyString(), anyInt(), anyString(), anyString())).thenReturn(1);
            when(taskAttemptMapper.insert(any())).thenReturn(1);
            when(repairProposalService.generate(any(), any(), any(), any())).thenReturn(new RepairProposalResponse());
            when(taskAttachmentMapper.findByTaskId(anyString())).thenReturn(List.of());
            when(taskCatalogSnapshotMapper.findByTaskIdAndInventoryVersion(anyString(), anyInt())).thenReturn(null);
            when(taskCatalogSnapshotMapper.insert(any())).thenReturn(1);
            when(registryService.resolve(any(), any(), any()))
                    .thenReturn(new RegistryService.ProviderResolution("water_yield", "planning-pass1-local", "docker-local"));

            service = new TaskService(
                    taskStateMapper,
                    analysisManifestMapper,
                    jobRecordMapper,
                    taskAttachmentMapper,
                    repairRecordMapper,
                    taskAttemptMapper,
                    taskCatalogSnapshotService,
                    eventService,
                    auditService,
                    cognitionFinalExplanationClient,
                    cognitionGoalRouteClient,
                    pass1Client,
                    cognitionPassBClient,
                    validationClient,
                    pass2Client,
                    jobRuntimeClient,
                    repairDispatcherService,
                    repairProposalService,
                    new AssertionFailureMapper(),
                    goalRouteService,
                    executionContractAssembler,
                    registryService,
                    workspaceTraceService,
                    taskDetailQueryService,
                    taskResultQueryService,
                    taskAuditQueryService,
                    taskManifestQueryService,
                    taskCatalogQueryService,
                    taskContractQueryService,
                    objectMapper,
                    "BackEnd/runtime/test-uploads"
            );
        }
    }
}
