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
import com.sage.backend.execution.dto.CancelJobResponse;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.execution.dto.JobStatusResponse;
import com.sage.backend.mapper.AnalysisManifestMapper;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.RepairRecordMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.mapper.TaskAttemptMapper;
import com.sage.backend.mapper.TaskCatalogSnapshotMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.AuditRecord;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.JobState;
import com.sage.backend.model.TaskCatalogSnapshot;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.Pass2Client;
import com.sage.backend.planning.dto.Pass2Request;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.planning.dto.Pass2Response;
import com.sage.backend.repair.RepairDecision;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import com.sage.backend.repair.dto.RepairProposalRequest;
import com.sage.backend.repair.dto.RepairProposalResponse;
import com.sage.backend.task.dto.CancelTaskResponse;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.ResumeTaskResponse;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskCatalogResponse;
import com.sage.backend.task.dto.TaskContractResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.validationgate.ValidationClient;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
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
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()
        )).thenAnswer(invocation -> {
            committedTxn.set(invocation.getArgument(11));
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
        assertEquals(7, acked.path("base_catalog_revision").asInt());
        assertEquals(8, acked.path("candidate_catalog_revision").asInt());
        assertEquals(64, acked.path("base_catalog_fingerprint").asText().length());
        assertEquals(acked.path("base_catalog_fingerprint").asText(), acked.path("candidate_catalog_fingerprint").asText());
        assertEquals(acked.path("candidate_manifest_id").asText(), committed.path("candidate_manifest_id").asText());
        assertEquals(acked.path("candidate_job_id").asText(), committed.path("candidate_job_id").asText());
        assertEquals(acked.path("base_catalog_revision").asInt(), committed.path("base_catalog_revision").asInt());
        assertEquals(acked.path("candidate_catalog_revision").asInt(), committed.path("candidate_catalog_revision").asInt());

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
                .commitQueuedWithGovernance(eq("task_resume"), eq(6), eq(TaskStatus.QUEUED.name()), anyString(), eq("job_resume_acked"), anyString(), eq(1), eq(4), eq(4), eq(8), anyString(), anyString());
    }

    @Test
    void uploadAttachmentPersistsCatalogSnapshotForIncrementedInventoryVersion() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState refreshedTask = waitingTaskState();
        refreshedTask.setInventoryVersion(8);
        when(harness.taskStateMapper.findByTaskId("task_resume"))
                .thenReturn(waitingTask, refreshedTask, refreshedTask, refreshedTask);
        when(harness.taskAttachmentMapper.insert(any())).thenReturn(1);
        when(harness.taskStateMapper.incrementInventoryVersion("task_resume")).thenReturn(1);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(recoverableDecision());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "precipitation.tif",
                "image/tiff",
                "fake-raster".getBytes()
        );

        UploadAttachmentResponse response = harness.service.uploadAttachment("task_resume", 42L, file, "precipitation");

        assertEquals("ASSIGNED", response.getAssignmentStatus());
        verify(harness.taskCatalogSnapshotMapper).insert(argThat(snapshot ->
                snapshot != null
                        && "task_resume".equals(snapshot.getTaskId())
                        && Integer.valueOf(8).equals(snapshot.getInventoryVersion())
                        && snapshot.getCatalogSummaryJson() != null
                        && snapshot.getCatalogFactsJson() != null
        ));
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
        when(harness.repairDispatcherService.decide(any(), any(), any(), any()))
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
        assertEquals(7, rolledBack.path("base_catalog_revision").asInt());
        assertEquals(8, rolledBack.path("candidate_catalog_revision").asInt());
        assertEquals(64, rolledBack.path("base_catalog_fingerprint").asText().length());
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
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
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
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()
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
        assertEquals(7, corrupted.path("base_catalog_revision").asInt());
        assertEquals(8, corrupted.path("candidate_catalog_revision").asInt());
        assertNotNull(corrupted.path("candidate_manifest_id").asText(null));
        assertEquals("job_resume_corrupted", corrupted.path("candidate_job_id").asText());
    }

    @Test
    void clarifyResumeReusesFrozenSkillVersionWithoutRerunningGoalRouteOrPass1() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = clarifyWaitingTaskState();
        TaskState resumedTask = clarifyResumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_clarify_version");
        request.setArgsOverrides(Map.of("case_id", "annual_water_yield_gura"));

        AtomicReference<com.sage.backend.cognition.dto.CognitionPassBRequest> capturedPassBRequest = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_clarify_version")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.cognitionPassBClient.runPassB(any())).thenAnswer(invocation -> {
            capturedPassBRequest.set(invocation.getArgument(0));
            return passBResponse();
        });
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenReturn(pass2Response());
        when(harness.analysisManifestMapper.insert(any())).thenReturn(1);
        when(harness.analysisManifestMapper.updateFreezeStatus(anyString(), anyString(), anyString())).thenReturn(1);
        when(harness.jobRuntimeClient.createJob(any())).thenReturn(createJobResponse("job_resume_clarify"));
        when(harness.taskStateMapper.updateResumeTransaction(eq("task_resume"), anyString())).thenReturn(1);
        when(harness.taskStateMapper.commitQueuedWithGovernance(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()
        )).thenReturn(1);

        ResumeTaskResponse response = harness.service.resumeTask("task_resume", 42L, request);

        assertEquals(TaskStatus.QUEUED.name(), response.getState());
        assertNotNull(capturedPassBRequest.get());
        assertEquals("water_yield", capturedPassBRequest.get().getSkillRoute().path("skill_id").asText());
        assertEquals("1.0.0", capturedPassBRequest.get().getSkillRoute().path("skill_version").asText());
        verify(harness.cognitionGoalRouteClient, never()).route(any());
        verify(harness.pass1Client, never()).runPass1(any());
    }

    @Test
    void resumeTaskMissingCheckpointResumeAckContractFailsBeforeValidationAndExecution() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        waitingTask.setPass1ResultJson(samplePass1JsonWithoutContract("checkpoint_resume_ack"));
        resumedTask.setPass1ResultJson(samplePass1JsonWithoutContract("checkpoint_resume_ack"));
        waitingTask.setPassbResultJson("{\"binding_status\":\"resolved\",\"slot_bindings\":[],\"args_draft\":{}}");
        resumedTask.setPassbResultJson("{\"binding_status\":\"resolved\",\"slot_bindings\":[],\"args_draft\":{}}");
        ResumeTaskRequest request = resumeRequest("resume_missing_checkpoint_contract");

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_missing_checkpoint_contract")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.resumeTask("task_resume", 42L, request)
        );

        assertEquals(502, exception.getStatusCode().value());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.pass2Client, never()).runPass2(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
        verify(harness.taskStateMapper).markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void resumeTaskContractVersionMismatchMarksCorruptedBeforeValidation() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_contract_mismatch");

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_contract_mismatch")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);
        when(harness.pass1Client.runPass1(any())).thenReturn(pass1ResponseWithContractIdentity(
                "water_yield_contracts_v2",
                "9999999999999999999999999999999999999999999999999999999999999999"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.resumeTask("task_resume", 42L, request)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Contract version mismatch", exception.getReason());
        verify(harness.validationClient, never()).validatePrimitive(any());
        verify(harness.jobRuntimeClient, never()).createJob(any());
        ArgumentCaptor<String> corruptedTxnCaptor = ArgumentCaptor.forClass(String.class);
        verify(harness.taskStateMapper).markCorrupted(
                eq("task_resume"),
                eq(5),
                eq(TaskStatus.STATE_CORRUPTED.name()),
                argThat(reason -> reason != null && reason.startsWith("CONTRACT_VERSION_MISMATCH: frozen=water_yield_contracts_v1/")),
                any(),
                corruptedTxnCaptor.capture()
        );
        JsonNode resumeTxn = objectMapper.readTree(corruptedTxnCaptor.getValue());
        assertEquals("CONTRACT_VERSION_MISMATCH", resumeTxn.path("failure_code").asText());
        assertEquals("water_yield_contracts_v1", resumeTxn.path("base_contract_version").asText());
        assertEquals("water_yield_contracts_v2", resumeTxn.path("candidate_contract_version").asText());
        ArgumentCaptor<String> auditDetailCaptor = ArgumentCaptor.forClass(String.class);
        verify(harness.auditService).appendAudit(
                eq("task_resume"),
                eq("TASK_RESUME"),
                eq("REJECTED"),
                eq("resume_contract_mismatch"),
                auditDetailCaptor.capture()
        );
        JsonNode auditDetail = objectMapper.readTree(auditDetailCaptor.getValue());
        assertEquals("CONTRACT_VERSION_MISMATCH", auditDetail.path("failure_code").asText());
        assertEquals("CONTRACT_VERSION_MISMATCH", auditDetail.path("mismatch_code").asText());
        assertEquals("INCOMPATIBLE", auditDetail.path("compatibility_code").asText());
        assertEquals("REGENERATE_PASS1_AND_REFREEZE", auditDetail.path("migration_hint").asText());
        assertEquals("water_yield_contracts_v1", auditDetail.path("frozen_contract_version").asText());
        assertEquals("water_yield_contracts_v2", auditDetail.path("current_contract_version").asText());
    }

    @Test
    void resumeTaskContractFingerprintMismatchUsesStructuredFingerprintCode() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_contract_fingerprint_mismatch");

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_contract_fingerprint_mismatch")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);
        when(harness.pass1Client.runPass1(any())).thenReturn(pass1ResponseWithContractIdentity(
                "water_yield_contracts_v1",
                "9999999999999999999999999999999999999999999999999999999999999999"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.resumeTask("task_resume", 42L, request)
        );

        assertEquals(409, exception.getStatusCode().value());
        assertEquals("Contract fingerprint mismatch", exception.getReason());
        ArgumentCaptor<String> corruptedTxnCaptor = ArgumentCaptor.forClass(String.class);
        verify(harness.taskStateMapper).markCorrupted(
                eq("task_resume"),
                eq(4),
                eq(TaskStatus.STATE_CORRUPTED.name()),
                argThat(reason -> reason != null && reason.startsWith("CONTRACT_FINGERPRINT_MISMATCH: frozen=water_yield_contracts_v1/")),
                any(),
                corruptedTxnCaptor.capture()
        );
        JsonNode resumeTxn = objectMapper.readTree(corruptedTxnCaptor.getValue());
        assertEquals("CONTRACT_FINGERPRINT_MISMATCH", resumeTxn.path("failure_code").asText());
        ArgumentCaptor<String> auditDetailCaptor = ArgumentCaptor.forClass(String.class);
        verify(harness.auditService).appendAudit(
                eq("task_resume"),
                eq("TASK_RESUME"),
                eq("REJECTED"),
                eq("resume_contract_fingerprint_mismatch"),
                auditDetailCaptor.capture()
        );
        JsonNode auditDetail = objectMapper.readTree(auditDetailCaptor.getValue());
        assertEquals("CONTRACT_FINGERPRINT_MISMATCH", auditDetail.path("failure_code").asText());
        assertEquals("INCOMPATIBLE", auditDetail.path("compatibility_code").asText());
        assertEquals("REFREEZE_REQUIRED", auditDetail.path("migration_hint").asText());
    }

    @Test
    void getTaskProjectsStructuredContractMismatchCodeFromResumeTransaction() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setResumeTxnJson("""
                {
                  "resume_request_id": "resume_contract_drift",
                  "status": "CORRUPTED",
                  "failure_code": "CONTRACT_VERSION_MISMATCH",
                  "base_contract_version": "water_yield_contracts_v1",
                  "base_contract_fingerprint": "1111111111111111111111111111111111111111111111111111111111111111",
                  "candidate_contract_version": "water_yield_contracts_v2",
                  "candidate_contract_fingerprint": "2222222222222222222222222222222222222222222222222222222222222222"
                }
                """);
        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskDetailResponse response = harness.service.getTask("task_legacy", 42L);

        assertEquals("CONTRACT_VERSION_MISMATCH", response.getContractConsistency().get("resume_mismatch_code"));
        assertEquals(true, response.getContractConsistency().get("resume_detected_contract_drift"));
        assertEquals("INCOMPATIBLE", response.getContractConsistency().get("resume_compatibility_code"));
        assertEquals("REGENERATE_PASS1_AND_REFREEZE", response.getContractConsistency().get("resume_migration_hint"));
        assertEquals("task_contract_governance", response.getContractGovernance().getScope());
        assertEquals("CONTRACT_VERSION_MISMATCH", response.getContractGovernance().getResumeContractEvaluation().getMismatchCode());
    }

    @Test
    void getTaskAuditProjectsContractGovernanceFromAuditDetail() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AuditRecord record = new AuditRecord();
        record.setId(11L);
        record.setTaskId("task_legacy");
        record.setActionType("TASK_RESUME");
        record.setActionResult("REJECTED");
        record.setTraceId("resume_contract_audit");
        record.setCreatedAt(OffsetDateTime.of(2026, 4, 4, 20, 30, 0, 0, ZoneOffset.UTC));
        record.setDetailJson(objectMapper.writeValueAsString(Map.of(
                "frozen_contract_version", "water_yield_contracts_v1",
                "frozen_contract_fingerprint", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "current_contract_version", "water_yield_contracts_v2",
                "current_contract_fingerprint", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "failure_code", "CONTRACT_VERSION_MISMATCH",
                "mismatch_code", "CONTRACT_VERSION_MISMATCH",
                "compatibility_code", "INCOMPATIBLE",
                "migration_hint", "REGENERATE_PASS1_AND_REFREEZE"
        )));

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.auditService.findByTaskId("task_legacy")).thenReturn(List.of(record));

        var response = harness.service.getTaskAudit("task_legacy", 42L);

        assertEquals("task_legacy", response.getTaskId());
        assertEquals(1, response.getItems().size());
        var item = response.getItems().get(0);
        assertEquals("audit_contract_governance", item.getContractGovernance().getScope());
        assertEquals("water_yield_contracts_v1", item.getContractGovernance().getFrozenContractSummary().getContractVersion());
        assertEquals("water_yield_contracts_v2", item.getContractGovernance().getCurrentContractSummary().getContractVersion());
        assertEquals("CONTRACT_VERSION_MISMATCH", item.getContractGovernance().getConsistency().getMismatchCode());
        assertEquals("INCOMPATIBLE", item.getContractGovernance().getConsistency().getCompatibilityCode());
        assertEquals("REGENERATE_PASS1_AND_REFREEZE", item.getContractGovernance().getResumeContractEvaluation().getMigrationHint());
    }

    @Test
    void getTaskAuditProjectsCatalogGovernanceFromResumeCatalogDetail() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setInventoryVersion(8);
        AuditRecord record = new AuditRecord();
        record.setId(12L);
        record.setTaskId("task_legacy");
        record.setActionType("TASK_RESUME");
        record.setActionResult("ACKED");
        record.setTraceId("resume_catalog_audit");
        record.setCreatedAt(OffsetDateTime.of(2026, 4, 4, 21, 30, 0, 0, ZoneOffset.UTC));
        record.setDetailJson(objectMapper.writeValueAsString(Map.of(
                "resume_request_id", "resume_catalog_audit",
                "base_catalog_inventory_version", 6,
                "base_catalog_revision", 6,
                "base_catalog_fingerprint", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "candidate_catalog_inventory_version", 7,
                "candidate_catalog_revision", 7,
                "candidate_catalog_fingerprint", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )));

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));
        when(harness.auditService.findByTaskId("task_legacy")).thenReturn(List.of(record));

        var response = harness.service.getTaskAudit("task_legacy", 42L);

        assertEquals(1, response.getItems().size());
        var item = response.getItems().get(0);
        assertEquals("audit_catalog_governance", item.getCatalogGovernance().getScope());
        assertEquals("audit_candidate_catalog_identity", item.getCatalogGovernance().getBaselineCatalogSummary().getCatalogSource());
        assertEquals(7, item.getCatalogGovernance().getBaselineCatalogSummary().getCatalogRevision());
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", item.getCatalogGovernance().getBaselineCatalogSummary().getCatalogFingerprint());
        assertEquals(8, item.getCatalogGovernance().getCurrentCatalogSummary().getCatalogRevision());
        assertEquals("CATALOG_REVISION_MISMATCH", item.getCatalogGovernance().getConsistency().getMismatchCode());
        assertEquals(true, item.getCatalogGovernance().getConsistency().getDriftDetected());
    }

    @Test
    void getTaskCatalogProjectsQueryGovernanceSnapshotAndCatalogAuditItems() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setInventoryVersion(8);
        TaskCatalogSnapshot snapshot = new TaskCatalogSnapshot();
        snapshot.setId(88L);
        snapshot.setTaskId("task_legacy");
        snapshot.setInventoryVersion(8);
        snapshot.setCatalogRevision(8);
        snapshot.setCatalogFingerprint("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        snapshot.setCatalogSource("task_attachment_projection");
        snapshot.setCreatedAt(OffsetDateTime.of(2026, 4, 4, 22, 30, 0, 0, ZoneOffset.UTC));
        snapshot.setCatalogSummaryJson(objectMapper.writeValueAsString(Map.of(
                "catalog_source", "task_catalog_snapshot",
                "catalog_inventory_version", 8,
                "catalog_revision", 8,
                "catalog_fingerprint", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "catalog_asset_count", 1,
                "catalog_ready_asset_count", 1,
                "catalog_blacklisted_asset_count", 0,
                "catalog_role_coverage_count", 1,
                "catalog_ready_role_names", List.of("precipitation")
        )));
        AuditRecord catalogAudit = new AuditRecord();
        catalogAudit.setId(13L);
        catalogAudit.setTaskId("task_legacy");
        catalogAudit.setActionType("TASK_RESUME");
        catalogAudit.setActionResult("ACKED");
        catalogAudit.setTraceId("resume_catalog_query_audit");
        catalogAudit.setCreatedAt(OffsetDateTime.of(2026, 4, 4, 23, 30, 0, 0, ZoneOffset.UTC));
        catalogAudit.setDetailJson(objectMapper.writeValueAsString(Map.of(
                "candidate_catalog_inventory_version", 7,
                "candidate_catalog_revision", 7,
                "candidate_catalog_fingerprint", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        )));
        AuditRecord nonCatalogAudit = new AuditRecord();
        nonCatalogAudit.setId(14L);
        nonCatalogAudit.setTaskId("task_legacy");
        nonCatalogAudit.setActionType("TASK_CANCEL");
        nonCatalogAudit.setActionResult("FAILED");
        nonCatalogAudit.setTraceId("no_catalog");
        nonCatalogAudit.setDetailJson(objectMapper.writeValueAsString(Map.of("failure_code", "CANCEL_FAILED")));

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));
        when(harness.taskCatalogSnapshotMapper.findByTaskIdAndInventoryVersion("task_legacy", 8)).thenReturn(snapshot);
        when(harness.taskCatalogSnapshotMapper.findLatestByTaskId("task_legacy")).thenReturn(snapshot);
        when(harness.auditService.findByTaskId("task_legacy")).thenReturn(List.of(catalogAudit, nonCatalogAudit));

        TaskCatalogResponse response = harness.service.getTaskCatalog("task_legacy", 42L);

        assertEquals("task_legacy", response.getTaskId());
        assertEquals(8, response.getInventoryVersion());
        assertEquals("task_catalog_snapshot", response.getCatalogSummary().get("catalog_source"));
        assertEquals(1, response.getCatalogFacts().size());
        assertEquals("READY", response.getCatalogFacts().get(0).get("availability_status"));
        assertEquals(8, response.getLatestSnapshot().getCatalogRevision());
        assertEquals("task_catalog_query_governance", response.getCatalogGovernance().getScope());
        assertEquals(false, response.getCatalogGovernance().getConsistency().getDriftDetected());
        assertEquals(1, response.getAuditItems().size());
        assertEquals("resume_catalog_query_audit", response.getAuditItems().get(0).getTraceId());
        assertEquals("CATALOG_REVISION_MISMATCH", response.getAuditItems().get(0).getCatalogGovernance().getConsistency().getMismatchCode());
        assertEquals(true, response.getAuditItems().get(0).getCatalogGovernance().getConsistency().getDriftDetected());
    }

    @Test
    void getTaskContractProjectsQueryGovernanceManifestAndContractAuditItems() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setPass1ResultJson(objectMapper.writeValueAsString(objectMapper.readTree(samplePass1Json())));
        taskState.setResumeTxnJson("""
                {
                  "resume_request_id": "resume_contract_query",
                  "status": "CORRUPTED",
                  "failure_code": "CONTRACT_VERSION_MISMATCH",
                  "base_contract_version": "water_yield_contracts_v1",
                  "base_contract_fingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "candidate_contract_version": "water_yield_contracts_v2",
                  "candidate_contract_fingerprint": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                }
                """);
        AnalysisManifest manifest = legacyManifest();
        manifest.setContractSummaryJson(objectMapper.writeValueAsString(Map.of(
                "contract_version", "water_yield_contracts_v1",
                "contract_fingerprint", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "contract_count", 1,
                "contract_names", List.of("submit_job"),
                "contract_present", true
        )));
        AuditRecord contractAudit = new AuditRecord();
        contractAudit.setId(15L);
        contractAudit.setTaskId("task_legacy");
        contractAudit.setActionType("TASK_RESUME");
        contractAudit.setActionResult("REJECTED");
        contractAudit.setTraceId("resume_contract_query_audit");
        contractAudit.setCreatedAt(OffsetDateTime.of(2026, 4, 5, 0, 30, 0, 0, ZoneOffset.UTC));
        contractAudit.setDetailJson(objectMapper.writeValueAsString(Map.of(
                "frozen_contract_version", "water_yield_contracts_v1",
                "frozen_contract_fingerprint", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "current_contract_version", "water_yield_contracts_v2",
                "current_contract_fingerprint", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "failure_code", "CONTRACT_VERSION_MISMATCH",
                "mismatch_code", "CONTRACT_VERSION_MISMATCH",
                "compatibility_code", "INCOMPATIBLE",
                "migration_hint", "REGENERATE_PASS1_AND_REFREEZE"
        )));
        AuditRecord nonContractAudit = new AuditRecord();
        nonContractAudit.setId(16L);
        nonContractAudit.setTaskId("task_legacy");
        nonContractAudit.setActionType("TASK_CANCEL");
        nonContractAudit.setActionResult("FAILED");
        nonContractAudit.setTraceId("no_contract");
        nonContractAudit.setDetailJson(objectMapper.writeValueAsString(Map.of("failure_code", "CANCEL_FAILED")));

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.auditService.findByTaskId("task_legacy")).thenReturn(List.of(contractAudit, nonContractAudit));

        TaskContractResponse response = harness.service.getTaskContract("task_legacy", 42L);

        assertEquals("task_legacy", response.getTaskId());
        assertEquals("water_yield_contracts_v1", response.getFrozenContractSummary().get("contract_version"));
        assertEquals("water_yield_contracts_v1", response.getActiveManifest().getContractSummary().get("contract_version"));
        assertEquals("manifest_legacy", response.getActiveManifest().getManifestId());
        assertEquals("task_contract_query_governance", response.getContractGovernance().getScope());
        assertEquals("CONTRACT_FINGERPRINT_MISMATCH", response.getContractGovernance().getConsistency().getMismatchCode());
        assertEquals("CONTRACT_VERSION_MISMATCH", response.getContractGovernance().getResumeContractEvaluation().getMismatchCode());
        assertEquals("INCOMPATIBLE", response.getContractGovernance().getResumeContractEvaluation().getCompatibilityCode());
        assertEquals(1, response.getAuditItems().size());
        assertEquals("resume_contract_query_audit", response.getAuditItems().get(0).getTraceId());
        assertEquals("CONTRACT_VERSION_MISMATCH", response.getAuditItems().get(0).getContractGovernance().getConsistency().getMismatchCode());
        assertEquals("REGENERATE_PASS1_AND_REFREEZE", response.getAuditItems().get(0).getContractGovernance().getResumeContractEvaluation().getMigrationHint());
    }

    @Test
    void runPass2ProjectsAttachmentCatalogFactsIntoRequest() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_catalog_projection");
        AtomicReference<Pass2Request> capturedPass2Request = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_catalog_projection")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenAnswer(invocation -> {
            capturedPass2Request.set(invocation.getArgument(0));
            return pass2Response();
        });
        when(harness.analysisManifestMapper.insert(any())).thenReturn(1);
        when(harness.analysisManifestMapper.updateFreezeStatus(anyString(), anyString(), anyString())).thenReturn(1);
        when(harness.jobRuntimeClient.createJob(any())).thenReturn(createJobResponse("job_resume_catalog"));
        when(harness.taskStateMapper.updateResumeTransaction(eq("task_resume"), anyString())).thenReturn(1);
        when(harness.taskStateMapper.commitQueuedWithGovernance(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()
        )).thenReturn(1);
        when(harness.taskAttachmentMapper.findByTaskId("task_resume")).thenReturn(List.of(readyAttachment("precipitation")));

        ResumeTaskResponse response = harness.service.resumeTask("task_resume", 42L, request);

        assertEquals(TaskStatus.QUEUED.name(), response.getState());
        assertNotNull(capturedPass2Request.get());
        JsonNode metadataCatalogFacts = capturedPass2Request.get().getMetadataCatalogFacts();
        assertNotNull(metadataCatalogFacts);
        assertEquals(1, metadataCatalogFacts.size());
        assertEquals("task_attachment", metadataCatalogFacts.get(0).path("source").asText());
        assertEquals("READY", metadataCatalogFacts.get(0).path("availability_status").asText());
        assertEquals("precipitation", metadataCatalogFacts.get(0).path("logical_role_candidates").get(0).asText());
    }

    @Test
    void resumeTaskPersistsFrozenCatalogSummaryOnManifestCandidate() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState waitingTask = waitingTaskState();
        TaskState resumedTask = resumingTaskState();
        ResumeTaskRequest request = resumeRequest("resume_manifest_catalog");
        AtomicReference<AnalysisManifest> insertedManifest = new AtomicReference<>();

        when(harness.taskStateMapper.findByTaskId("task_resume")).thenReturn(waitingTask, resumedTask, resumedTask);
        when(harness.repairRecordMapper.findByTaskIdAndResumeRequestId("task_resume", "resume_manifest_catalog")).thenReturn(null);
        when(harness.repairDispatcherService.decide(any(), any(), any(), any())).thenReturn(readyDecision());
        when(harness.taskStateMapper.acceptResume(anyString(), anyInt(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(1);
        when(harness.cognitionPassBClient.runPassB(any())).thenReturn(passBResponse());
        when(harness.validationClient.validatePrimitive(any())).thenReturn(validValidation());
        when(harness.pass2Client.runPass2(any())).thenReturn(pass2Response());
        when(harness.analysisManifestMapper.insert(any())).thenAnswer(invocation -> {
            insertedManifest.set(invocation.getArgument(0));
            return 1;
        });
        when(harness.analysisManifestMapper.updateFreezeStatus(anyString(), anyString(), anyString())).thenReturn(1);
        when(harness.jobRuntimeClient.createJob(any())).thenReturn(createJobResponse("job_resume_manifest_catalog"));
        when(harness.taskStateMapper.updateResumeTransaction(eq("task_resume"), anyString())).thenReturn(1);
        when(harness.taskStateMapper.commitQueuedWithGovernance(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyString()
        )).thenReturn(1);
        when(harness.taskAttachmentMapper.findByTaskId("task_resume")).thenReturn(List.of(readyAttachment("precipitation")));

        ResumeTaskResponse response = harness.service.resumeTask("task_resume", 42L, request);

        assertEquals(TaskStatus.QUEUED.name(), response.getState());
        assertNotNull(insertedManifest.get());
        JsonNode frozenCatalog = objectMapper.readTree(insertedManifest.get().getCatalogSummaryJson());
        assertEquals(1, frozenCatalog.path("catalog_asset_count").asInt());
        assertEquals(1, frozenCatalog.path("catalog_ready_asset_count").asInt());
        assertEquals(7, frozenCatalog.path("catalog_revision").asInt());
        assertEquals(64, frozenCatalog.path("catalog_fingerprint").asText().length());
        assertEquals("precipitation", frozenCatalog.path("catalog_ready_role_names").get(0).asText());
    }

    @Test
    void getTaskManifestSupportsLegacyManifestWithNullGovernanceFields() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskManifestResponse response = harness.service.getTaskManifest("task_legacy", 42L);

        assertEquals("manifest_legacy", response.getManifestId());
        assertEquals("FROZEN", response.getFreezeStatus());
        assertNull(response.getGraphDigest());
        assertEquals(Map.of(), response.getPlanningSummary());
        assertEquals(1, response.getCatalogSummary().get("catalog_asset_count"));
        assertEquals(List.of("precipitation"), response.getCatalogSummary().get("catalog_ready_role_names"));
        assertNull(response.getCanonicalizationSummary());
        assertNull(response.getRewriteSummary());
    }

    @Test
    void getTaskManifestPrefersFrozenCatalogSummaryFromManifest() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();
        manifest.setCatalogSummaryJson("""
                {
                  "catalog_asset_count": 2,
                  "catalog_ready_asset_count": 1,
                  "catalog_blacklisted_asset_count": 0,
                  "catalog_role_coverage_count": 1,
                  "catalog_ready_role_names": ["eto"],
                  "catalog_revision": 5,
                  "catalog_fingerprint": "frozen_manifest_catalog_fp",
                  "catalog_source": "frozen_manifest"
                }
                """);

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskManifestResponse response = harness.service.getTaskManifest("task_legacy", 42L);

        assertEquals(2, response.getCatalogSummary().get("catalog_asset_count"));
        assertEquals(List.of("eto"), response.getCatalogSummary().get("catalog_ready_role_names"));
        assertEquals(5, response.getCatalogSummary().get("catalog_revision"));
        assertEquals("frozen_manifest_catalog_fp", response.getCatalogSummary().get("catalog_fingerprint"));
        assertEquals("frozen_manifest", response.getCatalogSummary().get("catalog_source"));
    }

    @Test
    void getTaskManifestProjectsCatalogConsistencyAgainstSlotBindings() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setPass1ResultJson(samplePass1Json());
        AnalysisManifest manifest = legacyManifest();
        manifest.setSlotBindingsJson("""
                [
                  {"role_name": "precipitation", "slot_name": "precipitation", "source": "task_attachment"}
                ]
                """);
        manifest.setContractSummaryJson("""
                {
                  "contract_version": "water_yield_contracts_v1",
                  "contract_fingerprint": "2222222222222222222222222222222222222222222222222222222222222222",
                  "contract_count": 9,
                  "contract_names": ["cancel_job", "checkpoint_resume_ack"],
                  "contract_present": true
                }
                """);

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskManifestResponse response = harness.service.getTaskManifest("task_legacy", 42L);

        assertEquals("manifest_slot_bindings", response.getCatalogConsistency().get("scope"));
        assertEquals(true, response.getCatalogConsistency().get("covered"));
        assertEquals(List.of("precipitation"), response.getCatalogConsistency().get("expected_role_names"));
        assertEquals(List.of(), response.getCatalogConsistency().get("missing_catalog_roles"));
        assertEquals("manifest_contract", response.getContractConsistency().get("scope"));
        assertEquals(true, response.getContractConsistency().get("frozen_contract_present"));
        assertEquals(true, response.getContractConsistency().get("current_contract_present"));
        assertEquals(true, response.getContractConsistency().get("matches_current_contract"));
        assertEquals("CONTRACT_MATCHED", response.getContractConsistency().get("consistency_code"));
        assertEquals(null, response.getContractConsistency().get("mismatch_code"));
        assertEquals("COMPATIBLE", response.getContractConsistency().get("compatibility_code"));
        assertEquals("NO_ACTION", response.getContractConsistency().get("migration_hint"));
        assertEquals("manifest_contract_governance", response.getContractGovernance().getScope());
        assertEquals("COMPATIBLE", response.getContractGovernance().getConsistency().getCompatibilityCode());
    }

    @Test
    void getTaskResultSupportsLegacyManifestWithNullPlanningFields() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskResultResponse response = harness.service.getTaskResult("task_legacy", 42L);

        assertEquals(TaskStatus.SUCCEEDED.name(), response.getTaskState());
        assertEquals("PROMOTED", response.getPromotionStatus());
        assertEquals("FROZEN", response.getFreezeStatus());
        assertNull(response.getGraphDigest());
        assertEquals(Map.of(), response.getPlanningSummary());
        assertEquals(1, response.getCatalogSummary().get("catalog_asset_count"));
        assertEquals(List.of("precipitation"), response.getCatalogSummary().get("catalog_ready_role_names"));
        assertNull(response.getCanonicalizationSummary());
        assertNull(response.getRewriteSummary());
    }

    @Test
    void getTaskResultProjectsCatalogConsistencyAgainstRuntimeInputBindings() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        taskState.setPass1ResultJson(samplePass1Json());
        taskState.setJobId("job_legacy");
        AnalysisManifest manifest = legacyManifest();
        manifest.setContractSummaryJson("""
                {
                  "contract_version": "water_yield_contracts_v1",
                  "contract_fingerprint": "2222222222222222222222222222222222222222222222222222222222222222",
                  "contract_count": 9,
                  "contract_names": ["cancel_job", "checkpoint_resume_ack"],
                  "contract_present": true
                }
                """);
        JobRecord jobRecord = new JobRecord();
        jobRecord.setJobId("job_legacy");
        jobRecord.setJobState(JobState.SUCCEEDED.name());
        jobRecord.setDockerRuntimeEvidenceJson("""
                {
                  "input_bindings": [
                    {
                      "role_name": "precipitation",
                      "slot_name": "precipitation",
                      "source": "task_attachment",
                      "arg_key": "precipitation",
                      "provider_input_path": "/workspace/input/precipitation.tif",
                      "source_ref": "att_precipitation"
                    }
                  ]
                }
                """);

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));
        when(harness.jobRecordMapper.findByJobId("job_legacy")).thenReturn(jobRecord);

        TaskResultResponse response = harness.service.getTaskResult("task_legacy", 42L);

        assertEquals("result_input_bindings", response.getCatalogConsistency().get("scope"));
        assertEquals(true, response.getCatalogConsistency().get("covered"));
        assertEquals(List.of("precipitation"), response.getCatalogConsistency().get("expected_role_names"));
        assertEquals(List.of(), response.getCatalogConsistency().get("missing_catalog_roles"));
        assertEquals("result_manifest_contract", response.getContractConsistency().get("scope"));
        assertEquals(true, response.getContractConsistency().get("frozen_contract_present"));
        assertEquals(true, response.getContractConsistency().get("matches_current_contract"));
        assertEquals("CONTRACT_MATCHED", response.getContractConsistency().get("consistency_code"));
        assertEquals(null, response.getContractConsistency().get("mismatch_code"));
        assertEquals("COMPATIBLE", response.getContractConsistency().get("compatibility_code"));
        assertEquals("NO_ACTION", response.getContractConsistency().get("migration_hint"));
        assertEquals("result_contract_governance", response.getContractGovernance().getScope());
        assertEquals("COMPATIBLE", response.getContractGovernance().getConsistency().getCompatibilityCode());
    }

    @Test
    void getTaskResultPrefersFrozenCatalogSummaryFromManifest() {
        Harness harness = new Harness(objectMapper);
        TaskState taskState = succeededTaskState();
        AnalysisManifest manifest = legacyManifest();
        manifest.setCatalogSummaryJson("""
                {
                  "catalog_asset_count": 2,
                  "catalog_ready_asset_count": 1,
                  "catalog_blacklisted_asset_count": 0,
                  "catalog_role_coverage_count": 1,
                  "catalog_ready_role_names": ["eto"],
                  "catalog_revision": 5,
                  "catalog_fingerprint": "frozen_manifest_catalog_fp",
                  "catalog_source": "frozen_manifest"
                }
                """);

        when(harness.taskStateMapper.findByTaskId("task_legacy")).thenReturn(taskState);
        when(harness.analysisManifestMapper.findByManifestId("manifest_legacy")).thenReturn(manifest);
        when(harness.taskAttachmentMapper.findByTaskId("task_legacy")).thenReturn(List.of(readyAttachment("precipitation")));

        TaskResultResponse response = harness.service.getTaskResult("task_legacy", 42L);

        assertEquals(2, response.getCatalogSummary().get("catalog_asset_count"));
        assertEquals(List.of("eto"), response.getCatalogSummary().get("catalog_ready_role_names"));
        assertEquals(5, response.getCatalogSummary().get("catalog_revision"));
        assertEquals("frozen_manifest_catalog_fp", response.getCatalogSummary().get("catalog_fingerprint"));
        assertEquals("frozen_manifest", response.getCatalogSummary().get("catalog_source"));
    }

    @Test
    void syncActiveJobsMissingQueryJobStatusContractMarksCorruptedWithoutPollingRuntime() throws Exception {
        Harness harness = new Harness(objectMapper);
        JobRecord activeJob = activeJobRecord("job_active_query_contract");
        TaskState runningTask = runningTaskState();
        runningTask.setPass1ResultJson(samplePass1JsonWithoutContract("query_job_status"));

        when(harness.jobRecordMapper.findActiveJobs()).thenReturn(List.of(activeJob));
        when(harness.taskStateMapper.findByTaskId("task_running")).thenReturn(runningTask);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        harness.service.syncActiveJobs();

        verify(harness.jobRuntimeClient, never()).getJob(anyString());
        verify(harness.taskStateMapper).markCorrupted(
                eq("task_running"),
                eq(5),
                eq(TaskStatus.STATE_CORRUPTED.name()),
                eq("CAPABILITY_CONTRACT_UNAVAILABLE: query_job_status"),
                any(),
                anyString()
        );
    }

    @Test
    void syncActiveJobsMissingCollectResultBundleContractMarksCorruptedBeforePromotion() throws Exception {
        Harness harness = new Harness(objectMapper);
        JobRecord activeJob = activeJobRecord("job_active_collect_contract");
        TaskState runningTask = runningTaskState();
        runningTask.setJobId("job_active_collect_contract");
        runningTask.setPass1ResultJson(samplePass1JsonWithoutContract("collect_result_bundle"));
        JobStatusResponse succeededStatus = succeededJobStatus("job_active_collect_contract");

        when(harness.jobRecordMapper.findActiveJobs()).thenReturn(List.of(activeJob));
        when(harness.taskStateMapper.findByTaskId("task_running")).thenReturn(runningTask, runningTask);
        when(harness.jobRuntimeClient.getJob("job_active_collect_contract")).thenReturn(succeededStatus);
        when(harness.jobRecordMapper.findByJobId("job_active_collect_contract")).thenReturn(activeJob);
        when(harness.taskStateMapper.updateState("task_running", 5, TaskStatus.ARTIFACT_PROMOTING.name())).thenReturn(1);
        when(harness.taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);

        harness.service.syncActiveJobs();

        verify(harness.workspaceTraceService, never()).persistSuccess(any(), any(), any(), any(), any());
        verify(harness.taskStateMapper).markCorrupted(
                eq("task_running"),
                eq(6),
                eq(TaskStatus.STATE_CORRUPTED.name()),
                eq("ARTIFACT_PROMOTION_FAILED: CAPABILITY_CONTRACT_UNAVAILABLE: collect_result_bundle"),
                any(),
                anyString()
        );
    }

    @Test
    void cancelTaskMissingCancelJobContractFailsBeforeRuntimeCancel() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState cancelTask = cancelableTaskState();
        cancelTask.setPass1ResultJson(samplePass1JsonWithoutContract("cancel_job"));
        when(harness.taskStateMapper.findByTaskId("task_running")).thenReturn(cancelTask);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.cancelTask("task_running", 42L)
        );

        assertEquals(502, exception.getStatusCode().value());
        verify(harness.jobRuntimeClient, never()).cancelJob(anyString(), anyString());
        verify(harness.auditService, never()).appendAudit(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelTaskSuccessWritesAuditWithContractIdentity() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState cancelTask = cancelableTaskState();
        CancelJobResponse cancelJobResponse = new CancelJobResponse();
        cancelJobResponse.setAccepted(true);
        when(harness.taskStateMapper.findByTaskId("task_running")).thenReturn(cancelTask, cancelTask);
        when(harness.jobRuntimeClient.cancelJob("job_cancel_target", "USER_REQUESTED")).thenReturn(cancelJobResponse);
        when(harness.jobRecordMapper.findByJobId("job_cancel_target")).thenReturn(null);

        CancelTaskResponse response = harness.service.cancelTask("task_running", 42L);

        assertEquals(true, response.getAccepted());
        ArgumentCaptor<String> auditDetailCaptor = ArgumentCaptor.forClass(String.class);
        verify(harness.auditService).appendAudit(
                eq("task_running"),
                eq("TASK_CANCEL"),
                eq("REQUESTED"),
                eq("task_running"),
                auditDetailCaptor.capture()
        );
        JsonNode auditDetail = objectMapper.readTree(auditDetailCaptor.getValue());
        assertEquals("water_yield_contracts_v1", auditDetail.path("contract_identity").path("contract_version").asText());
        assertEquals(9, auditDetail.path("contract_identity").path("contract_count").asInt());
        assertEquals(true, auditDetail.path("contract_identity").path("contract_present").asBoolean());
    }

    @Test
    void cancelTaskMissingRecordAuditContractFailsBeforeRuntimeCancel() throws Exception {
        Harness harness = new Harness(objectMapper);
        TaskState cancelTask = cancelableTaskState();
        cancelTask.setPass1ResultJson(samplePass1JsonWithoutContract("record_audit"));
        when(harness.taskStateMapper.findByTaskId("task_running")).thenReturn(cancelTask);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.cancelTask("task_running", 42L)
        );

        assertEquals(502, exception.getStatusCode().value());
        verify(harness.jobRuntimeClient, never()).cancelJob(anyString(), anyString());
        verify(harness.auditService, never()).appendAudit(anyString(), anyString(), anyString(), anyString(), anyString());
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
        taskState.setSkillRouteJson("""
                {
                  "primary_skill": "water_yield",
                  "skill_id": "water_yield",
                  "skill_version": "1.0.0",
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1"
                }
                """);
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

    private TaskState clarifyWaitingTaskState() {
        TaskState taskState = waitingTaskState();
        taskState.setWaitingContextJson("""
                {
                  "waiting_reason_type": "CLARIFY_CASE_SELECTION",
                  "can_resume": false
                }
                """);
        taskState.setGoalParseJson("""
                {
                  "goal_type": "water_yield_analysis",
                  "analysis_kind": "water_yield",
                  "case_projection": {
                    "mode": "clarify_required",
                    "candidate_case_ids": ["annual_water_yield_gura", "annual_water_yield_gtm_national"]
                  }
                }
                """);
        taskState.setPassbResultJson(null);
        return taskState;
    }

    private TaskState clarifyResumingTaskState() {
        TaskState taskState = clarifyWaitingTaskState();
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

    private TaskState runningTaskState() {
        TaskState taskState = new TaskState();
        taskState.setTaskId("task_running");
        taskState.setUserId(42L);
        taskState.setCurrentState(TaskStatus.RUNNING.name());
        taskState.setStateVersion(5);
        taskState.setActiveAttemptNo(1);
        taskState.setPlanningRevision(1);
        taskState.setCheckpointVersion(1);
        taskState.setJobId("job_active_query_contract");
        taskState.setPass1ResultJson(samplePass1Json());
        taskState.setResumeTxnJson("{\"status\":\"COMMITTED\"}");
        return taskState;
    }

    private TaskState cancelableTaskState() {
        TaskState taskState = runningTaskState();
        taskState.setCurrentState(TaskStatus.RUNNING.name());
        taskState.setJobId("job_cancel_target");
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

    private JobRecord activeJobRecord(String jobId) {
        JobRecord jobRecord = new JobRecord();
        jobRecord.setJobId(jobId);
        jobRecord.setTaskId("task_running");
        jobRecord.setJobState(JobState.RUNNING.name());
        return jobRecord;
    }

    private JobStatusResponse succeededJobStatus(String jobId) {
        JobStatusResponse response = new JobStatusResponse();
        response.setJobId(jobId);
        response.setJobState(JobState.SUCCEEDED.name());
        response.setResultObject(objectMapper.createObjectNode().put("result_id", "result_001"));
        response.setResultBundle(objectMapper.createObjectNode().put("result_id", "bundle_001"));
        response.setArtifactCatalog(objectMapper.createArrayNode());
        response.setWorkspaceSummary(objectMapper.createObjectNode().put("workspace_id", "ws_001"));
        response.setDockerRuntimeEvidence(objectMapper.createObjectNode().put("runtime_profile", "docker-invest-real"));
        return response;
    }

    private CancelJobResponse acceptedCancelResponse() {
        CancelJobResponse response = new CancelJobResponse();
        response.setAccepted(true);
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
        response.setBindingStatus("resolved");
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

    private static TaskAttachment readyAttachment(String logicalSlot) {
        TaskAttachment attachment = new TaskAttachment();
        attachment.setId("att_" + logicalSlot);
        attachment.setLogicalSlot(logicalSlot);
        attachment.setFileName(logicalSlot + ".tif");
        attachment.setSizeBytes(1024L);
        attachment.setStoredPath("E:/tmp/" + logicalSlot + ".tif");
        attachment.setChecksum("abc123");
        attachment.setAssignmentStatus("BOUND");
        return attachment;
    }

    private String samplePass1Json() {
        return """
                {
                  "capability_key": "water_yield",
                  "selected_template": "water_yield_v1",
                  "template_version": "1.0.0",
                  "capability_facts": {
                    "contract_version": "water_yield_contracts_v1",
                    "contract_fingerprint": "2222222222222222222222222222222222222222222222222222222222222222",
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
                  }
                }
                """;
    }

    private String samplePass1JsonWithoutContract(String contractName) throws Exception {
        JsonNode root = objectMapper.readTree(samplePass1Json());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("capability_facts").path("contracts")).remove(contractName);
        return objectMapper.writeValueAsString(root);
    }

    private Pass1Response pass1ResponseWithContractIdentity(String contractVersion, String contractFingerprint) throws Exception {
        JsonNode root = objectMapper.readTree(samplePass1Json());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("capability_facts")).put("contract_version", contractVersion);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("capability_facts")).put("contract_fingerprint", contractFingerprint);
        return objectMapper.readValue(objectMapper.writeValueAsString(root), Pass1Response.class);
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
        private final Pass2Client pass2Client = mock(Pass2Client.class);
        private final JobRuntimeClient jobRuntimeClient = mock(JobRuntimeClient.class);
        private final RepairDispatcherService repairDispatcherService = mock(RepairDispatcherService.class);
        private final RepairProposalService repairProposalService = mock(RepairProposalService.class);
        private final RegistryService registryService = mock(RegistryService.class);
        private final WorkspaceTraceService workspaceTraceService = mock(WorkspaceTraceService.class);
        private final TaskDetailQueryService taskDetailQueryService;
        private final TaskResultQueryService taskResultQueryService;
        private final TaskAuditQueryService taskAuditQueryService;
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
            this.taskCatalogQueryService = new TaskCatalogQueryService(taskCatalogSnapshotService, objectMapper);
            this.taskContractQueryService = new TaskContractQueryService(objectMapper);
            when(taskAttachmentMapper.findByTaskId(anyString())).thenReturn(List.of());
            when(taskCatalogSnapshotMapper.findByTaskIdAndInventoryVersion(anyString(), anyInt())).thenReturn(null);
            when(taskCatalogSnapshotMapper.insert(any())).thenReturn(1);
            when(taskAttemptMapper.updateSnapshotAndJob(anyString(), anyInt(), any(), anyString(), any())).thenReturn(1);
            when(taskAttemptMapper.insert(any())).thenReturn(1);
            when(jobRecordMapper.insert(any())).thenReturn(1);
            when(taskStateMapper.updateStateAndPass1(anyString(), anyInt(), anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateGoalAndRoute(anyString(), anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateCognitionVerdict(anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateInputChainSnapshot(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.updateWaitingContext(anyString(), anyString(), anyString())).thenReturn(1);
            when(repairProposalService.generate(any(), any(), any(), any())).thenReturn(new RepairProposalResponse());
            when(taskStateMapper.updateResumeTransaction(anyString(), anyString())).thenReturn(1);
            when(taskStateMapper.markCorrupted(anyString(), anyInt(), anyString(), anyString(), any(), anyString())).thenReturn(1);
            when(registryService.resolve(any(), any(), any()))
                    .thenReturn(new RegistryService.ProviderResolution("water_yield", "planning-pass1-local", "docker-local"));
            when(cognitionGoalRouteClient.route(any())).thenReturn(defaultGoalRouteResponse(objectMapper));
            when(pass1Client.runPass1(any())).thenReturn(defaultPass1Response(objectMapper));

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
                    taskCatalogQueryService,
                    taskContractQueryService,
                    objectMapper,
                    "BackEnd/runtime/test-uploads"
            );
        }

        private CognitionGoalRouteResponse defaultGoalRouteResponse(ObjectMapper objectMapper) {
            try {
                CognitionGoalRouteResponse response = new CognitionGoalRouteResponse();
                response.setPlanningIntentStatus("resolved");
                response.setGoalParse(objectMapper.readTree("""
                        {
                          "goal_type": "water_yield_analysis",
                          "user_query": "resume task",
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
                          "execution_mode": "governed_baseline"
                        }
                        """));
                response.setConfidence(0.9);
                response.setDecisionSummary(Map.of("strategy", "test"));
                response.setCognitionMetadata(Map.of("fallback_used", false, "schema_valid", true, "source", "test"));
                return response;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        private Pass1Response defaultPass1Response(ObjectMapper objectMapper) {
            try {
                return objectMapper.readValue(samplePass1Json(), Pass1Response.class);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
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
                        "contract_fingerprint": "2222222222222222222222222222222222222222222222222222222222222222",
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
    }
}
