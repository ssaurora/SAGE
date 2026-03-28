package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.audit.AuditService;
import com.sage.backend.cognition.CognitionPassBClient;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.JobRuntimeClient;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.mapper.AnalysisManifestMapper;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.RepairRecordMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.mapper.TaskAttemptMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.JobState;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.Pass2Client;
import com.sage.backend.planning.dto.Pass2Response;
import com.sage.backend.repair.RepairDecision;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.ResumeTaskResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.validationgate.ValidationClient;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceGovernanceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resumeTaskSuccessWritesPreparingAckedAndCommittedTransactions() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_success");

        AtomicReference<String> preparingTxn = new AtomicReference<>();
        AtomicReference<String> ackedTxn = new AtomicReference<>();
        AtomicReference<String> committedTxn = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_success")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    preparingTxn.set(invocation.getArgument(4));
                    return 1;
                });
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenReturn(pass2Response());
        when(harness.analysisManifestMapper.insert(any())).thenReturn(1);
        when(harness.analysisManifestMapper.updateFreezeStatus(anyString(), anyString(), anyString())).thenReturn(1);
        when(harness.jobRuntimeClient.createJob(any())).thenReturn(createJobResponse("job_resume_acked"));
        when(harness.taskStateMapper.updateResumeTransaction(eq("task_resume"), anyString()))
                .thenAnswer(invocation -> {
                    ackedTxn.set(invocation.getArgument(1));
                    return 1;
                });
        when(harness.taskStateMapper.commitQueuedWithGovernance(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()
        )).thenAnswer(invocation -> {
            committedTxn.set(invocation.getArgument(10));
            return 1;
        });

        ResumeTaskResponse response = harness.service.resumeTask("task_resume", 42L, request);

        assertEquals(TaskStatus.QUEUED.name(), response.getState());

        JsonNode preparing = objectMapper.readTree(preparingTxn.get());
        JsonNode acked = objectMapper.readTree(ackedTxn.get());
        JsonNode committed = objectMapper.readTree(committedTxn.get());

        assertEquals("PREPARING", preparing.path("status").asText());
        assertEquals(4, preparing.path("candidate_attempt_no").asInt());
        assertEquals("ACKED", acked.path("status").asText());
        assertEquals("COMMITTED", committed.path("status").asText());
        assertEquals(4, acked.path("candidate_checkpoint_version").asInt());
        assertEquals(8, acked.path("candidate_inventory_version").asInt());
        assertEquals(acked.path("candidate_manifest_id").asText(), committed.path("candidate_manifest_id").asText());
        assertEquals(acked.path("candidate_job_id").asText(), committed.path("candidate_job_id").asText());

        verify(harness.taskStateMapper).acceptResume(
                eq("task_resume"),
                eq(4),
                eq(TaskStatus.RESUMING.name()),
                anyString(),
                anyString(),
                eq(3),
                eq(4)
        );

        inOrder(harness.taskStateMapper, harness.analysisManifestMapper).verify(harness.taskStateMapper)
                .acceptResume(eq("task_resume"), eq(4), eq(TaskStatus.RESUMING.name()), anyString(), anyString(), eq(3), eq(4));
        inOrder(harness.taskStateMapper, harness.analysisManifestMapper).verify(harness.taskStateMapper)
                .updateResumeTransaction(eq("task_resume"), anyString());
        inOrder(harness.taskStateMapper, harness.analysisManifestMapper).verify(harness.analysisManifestMapper)
                .updateFreezeStatus(anyString(), eq("CANDIDATE"), eq("FROZEN"));
        inOrder(harness.taskStateMapper, harness.analysisManifestMapper).verify(harness.taskStateMapper)
                .commitQueuedWithGovernance(eq("task_resume"), eq(5), eq(TaskStatus.QUEUED.name()), anyString(), eq("job_resume_acked"), anyString(), eq(1), eq(4), eq(4), eq(8), anyString());
    }

    @Test
    void resumeTaskRecoverableValidationWritesRolledBackTransaction() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_recoverable");

        AtomicReference<String> preparingTxn = new AtomicReference<>();
        AtomicReference<String> rolledBackTxn = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_recoverable")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any()))
                .thenReturn(readyDecision(), recoverableDecision(), recoverableDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    preparingTxn.set(invocation.getArgument(4));
                    return 1;
                });
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(recoverableValidation());
        when(harness.taskStateMapper.rollbackResumeToWaiting(anyString(), anyInt(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    rolledBackTxn.set(invocation.getArgument(6));
                    return 1;
                });

        ResumeTaskResponse response = harness.service.resumeTask("task_resume", 42L, request);

        assertEquals(TaskStatus.WAITING_USER.name(), response.getState());
        assertEquals("PREPARING", objectMapper.readTree(preparingTxn.get()).path("status").asText());

        JsonNode rolledBack = objectMapper.readTree(rolledBackTxn.get());
        assertEquals("ROLLED_BACK", rolledBack.path("status").asText());
        assertEquals("RECOVERABLE_VALIDATION", rolledBack.path("failure_reason").asText());
        assertEquals(4, rolledBack.path("candidate_checkpoint_version").asInt());
        assertEquals(8, rolledBack.path("candidate_inventory_version").asInt());
    }

    @Test
    void resumeTaskCommitConflictMarksCorruptedWithCandidatePointers() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_corrupted");

        AtomicReference<String> corruptedTxn = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_corrupted")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenReturn(pass2Response());
        when(harness.analysisManifestMapper.insert(any())).thenReturn(1);
        when(harness.analysisManifestMapper.updateFreezeStatus(anyString(), anyString(), anyString())).thenReturn(1);
        when(harness.jobRuntimeClient.createJob(any())).thenReturn(createJobResponse("job_resume_corrupted"));
        when(harness.taskStateMapper.updateResumeTransaction(eq("task_resume"), anyString())).thenReturn(1);
        when(harness.taskStateMapper.commitQueuedWithGovernance(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()
        )).thenReturn(0);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    corruptedTxn.set(invocation.getArgument(5));
                    return 1;
                });

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.resumeTask("task_resume", 42L, request)
        );

        assertEquals(502, exception.getStatusCode().value());
        assertEquals("Resume pipeline failed", exception.getReason());
        assertNotNull(exception.getCause());
        assertEquals("State version conflict", exception.getCause().getMessage());
        assertNotNull(corruptedTxn.get());
        JsonNode corrupted = objectMapper.readTree(corruptedTxn.get());
        assertEquals("CORRUPTED", corrupted.path("status").asText());
        assertEquals("State version conflict", corrupted.path("failure_reason").asText());
        assertNotNull(corrupted.path("candidate_manifest_id").asText(null));
        assertEquals("job_resume_corrupted", corrupted.path("candidate_job_id").asText());
    }

    @Test
    void getTaskManifestSupportsLegacyManifestWithNullGovernanceFields() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);

        TaskManifestResponse response = harness.service.getTaskManifest("task_legacy", 42L);

        assertEquals("manifest_legacy", response.getManifestId());
        assertEquals("FROZEN", response.getFreezeStatus());
        assertNull(response.getGraphDigest());
        assertEquals(Map.of(), response.getPlanningSummary());
        assertNull(response.getCanonicalizationSummary());
        assertNull(response.getRewriteSummary());
    }

    @Test
    void getTaskResultSupportsLegacyManifestWithNullPlanningFields() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);

        TaskResultResponse response = harness.service.getTaskResult("task_legacy", 42L);

        assertEquals(TaskStatus.SUCCEEDED.name(), response.getTaskState());
        assertEquals("PROMOTED", response.getPromotionStatus());
        assertEquals("FROZEN", response.getFreezeStatus());
        assertNull(response.getGraphDigest());
        assertEquals(Map.of(), response.getPlanningSummary());
        assertNull(response.getCanonicalizationSummary());
        assertNull(response.getRewriteSummary());
    }

    private TaskState waitingTaskState() {
        TaskState taskState = new TaskState();
        taskState.setTaskId("task_resume");
        taskState.setUserId(42L);
        taskState.setCurrentState(TaskStatus.WAITING_USER.name());
        taskState.setStateVersion(4);
        taskState.setUserQuery("resume task");
        taskState.setPass1ResultJson(samplePass1Json());
        taskState.setGoalParseJson("{\"goal_type\":\"water_yield_analysis\"}");
        taskState.setSkillRouteJson("{\"primary_skill\":\"water_yield\",\"capability_key\":\"water_yield\"}");
        taskState.setValidationSummaryJson("{}");
        taskState.setResumeAttemptCount(2);
        taskState.setActiveAttemptNo(3);
        taskState.setPlanningRevision(3);
        taskState.setCheckpointVersion(3);
        taskState.setInventoryVersion(7);
        taskState.setJobId("job_previous");
        return taskState;
    }

    private TaskState resumingTaskState() {
        TaskState taskState = waitingTaskState();
        taskState.setCurrentState(TaskStatus.RESUMING.name());
        taskState.setStateVersion(5);
        taskState.setResumeAttemptCount(3);
        taskState.setActiveAttemptNo(4);
        return taskState;
    }

    private TaskState succeededTaskState() {
        TaskState taskState = new TaskState();
        taskState.setTaskId("task_legacy");
        taskState.setUserId(42L);
        taskState.setCurrentState(TaskStatus.SUCCEEDED.name());
        taskState.setStateVersion(8);
        taskState.setActiveAttemptNo(1);
        taskState.setActiveManifestId("manifest_legacy");
        taskState.setPlanningRevision(1);
        taskState.setCheckpointVersion(1);
        taskState.setPass1ResultJson("{}");
        return taskState;
    }

    private AnalysisManifest legacyManifest() {
        AnalysisManifest manifest = new AnalysisManifest();
        manifest.setManifestId("manifest_legacy");
        manifest.setTaskId("task_legacy");
        manifest.setAttemptNo(1);
        manifest.setManifestVersion(1);
        manifest.setFreezeStatus("FROZEN");
        manifest.setPlanningRevision(1);
        manifest.setCheckpointVersion(1);
        manifest.setGraphDigest(null);
        manifest.setPlanningSummaryJson(null);
        manifest.setGoalParseJson("{}");
        manifest.setSkillRouteJson("{}");
        manifest.setLogicalInputRolesJson("[]");
        manifest.setSlotSchemaViewJson("{\"slots\":[]}");
        manifest.setSlotBindingsJson("[]");
        manifest.setArgsDraftJson("{}");
        manifest.setValidationSummaryJson("{}");
        manifest.setExecutionGraphJson("{\"nodes\":[],\"edges\":[]}");
        manifest.setRuntimeAssertionsJson("[]");
        return manifest;
    }

    private ResumeTaskRequest resumeRequest(String resumeRequestId) {
        ResumeTaskRequest request = new ResumeTaskRequest();
        request.setResumeRequestId(resumeRequestId);
        request.setUserNote("resume");
        return request;
    }

    private RepairDecision readyDecision() {
        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setCanResume(true);
        waitingContext.setResumeHint("Ready");
        waitingContext.setWaitingReasonType("READY");
        return new RepairDecision("RECOVERABLE", "WAITING_USER", waitingContext);
    }

    private RepairDecision recoverableDecision() {
        RepairProposalRequest.MissingSlot missingSlot = new RepairProposalRequest.MissingSlot();
        missingSlot.setSlotName("precipitation");
        missingSlot.setExpectedType("raster");
        missingSlot.setRequired(true);

        RepairProposalRequest.RequiredUserAction action = new RepairProposalRequest.RequiredUserAction();
        action.setActionType("upload");
        action.setKey("upload_precipitation");
        action.setLabel("Upload precipitation");
        action.setRequired(true);

        RepairProposalRequest.WaitingContext waitingContext = new RepairProposalRequest.WaitingContext();
        waitingContext.setCanResume(false);
        waitingContext.setWaitingReasonType("MISSING_INPUT");
        waitingContext.setMissingSlots(List.of(missingSlot));
        waitingContext.setRequiredUserActions(List.of(action));
        waitingContext.setResumeHint("Complete required actions before resuming.");
        return new RepairDecision("RECOVERABLE", "WAITING_USER", waitingContext);
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

    private PrimitiveValidationResponse recoverableValidation() {
        PrimitiveValidationResponse response = new PrimitiveValidationResponse();
        response.setIsValid(false);
        response.setMissingRoles(List.of("precipitation"));
        response.setMissingParams(List.of());
        response.setInvalidBindings(List.of());
        response.setErrorCode("MISSING_ROLE");
        return response;
    }

    private CognitionPassBResponse passBResponse() {
        CognitionPassBResponse response = new CognitionPassBResponse();
        response.setSlotBindings(List.of());
        response.setArgsDraft(Map.of(
                "workspace_dir", "/tmp/workspace",
                "results_suffix", "attempt_4",
                "analysis_template", "water_yield_v1"
        ));
        response.setDecisionSummary(Map.of("strategy", "resume"));
        return response;
    }

    private Pass2Response pass2Response() throws Exception {
        Pass2Response response = new Pass2Response();
        response.setGraphDigest("digest_resume");
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

    private CreateJobResponse createJobResponse(String jobId) {
        CreateJobResponse response = new CreateJobResponse();
        response.setJobId(jobId);
        response.setJobState(JobState.ACCEPTED.name());
        response.setAcceptedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return response;
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
                  }
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
        private final Pass1Client pass1Client = mock(Pass1Client.class);
        private final CognitionPassBClient cognitionPassBClient = mock(CognitionPassBClient.class);
        private final ValidationClient validationClient = mock(ValidationClient.class);
        private final Pass2Client pass2Client = mock(Pass2Client.class);
        private final JobRuntimeClient jobRuntimeClient = mock(JobRuntimeClient.class);
        private final RepairDispatcherService repairDispatcherService = mock(RepairDispatcherService.class);
        private final RepairProposalService repairProposalService = mock(RepairProposalService.class);
        private final RegistryService registryService = mock(RegistryService.class);
        private final WorkspaceTraceService workspaceTraceService = mock(WorkspaceTraceService.class);
        private final TaskService service;

        private Harness(ObjectMapper objectMapper) {
            when(taskAttachmentMapper.findByTaskId(anyString())).thenReturn(List.of());
            when(taskAttemptMapper.updateSnapshotAndJob(anyString(), anyInt(), any(), anyString(), any())).thenReturn(1);
            when(taskAttemptMapper.insert(any())).thenReturn(1);
            when(jobRecordMapper.insert(any())).thenReturn(1);
            when(repairProposalService.generate(any(), any(), any(), any())).thenReturn(new RepairProposalResponse());
            when(taskStateMapper.updateResumeTransaction(anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);
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
                    pass1Client,
                    cognitionPassBClient,
                    validationClient,
                    pass2Client,
                    jobRuntimeClient,
                    repairDispatcherService,
                    repairProposalService,
                    new AssertionFailureMapper(),
                    new GoalRouteService(objectMapper),
                    registryService,
                    workspaceTraceService,
                    objectMapper,
                    "BackEnd/runtime/test-uploads"
            );
        }
    }
}
