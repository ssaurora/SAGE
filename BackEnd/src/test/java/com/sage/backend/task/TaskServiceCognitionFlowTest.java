package com.sage.backend.task;

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
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import com.sage.backend.repair.dto.RepairProposalResponse;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.validationgate.ValidationClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void getTaskProjectsCognitionAuthorityFacts() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = taskStateForProjection();
        when(harness.taskStateMapper.findByTaskId("task_projection")).thenReturn(taskState);

        TaskDetailResponse response = harness.service.getTask("task_projection", 42L);

        assertEquals("resolved", response.getPlanningIntentStatus());
        assertEquals("resolved", response.getBindingStatus());
        assertEquals(List.of("case_id"), response.getOverruledFields());
        assertEquals(List.of("case_id"), response.getBlockedMutations());
        assertTrue(Boolean.TRUE.equals(response.getAssemblyBlocked()));
        assertEquals("PRIMARY_OVERRULED", response.getCognitionVerdict());
        assertEquals("clarify_required", response.getCaseProjection().get("mode"));
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
        taskState.setWaitingContextJson("{}");
        return taskState;
    }

    private String samplePass1Json() {
        return """
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "capability_facts": {
                    "runtime_profile_hint": "docker-local",
                    "validation_hints": [],
                    "repair_hints": []
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

    private static final class Harness {
        private final TaskStateMapper taskStateMapper = mock(TaskStateMapper.class);
        private final AnalysisManifestMapper analysisManifestMapper = mock(AnalysisManifestMapper.class);
        private final JobRecordMapper jobRecordMapper = mock(JobRecordMapper.class);
        private final TaskAttachmentMapper taskAttachmentMapper = mock(TaskAttachmentMapper.class);
        private final RepairRecordMapper repairRecordMapper = mock(RepairRecordMapper.class);
        private final TaskAttemptMapper taskAttemptMapper = mock(TaskAttemptMapper.class);
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
        private final TaskService service;

        private Harness(ObjectMapper objectMapper) {
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
            when(registryService.resolve(any(), any(), any()))
                    .thenReturn(new RegistryService.ProviderResolution("water_yield", "planning-pass1-local", "docker-local"));

            service = new TaskService(
                    taskStateMapper,
                    analysisManifestMapper,
                    jobRecordMapper,
                    taskAttachmentMapper,
                    repairRecordMapper,
                    taskAttemptMapper,
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
                    new GoalRouteService(objectMapper),
                    new ExecutionContractAssembler(objectMapper),
                    registryService,
                    workspaceTraceService,
                    objectMapper,
                    "BackEnd/runtime/test-uploads"
            );
        }
    }
}
