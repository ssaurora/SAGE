package com.sage.backend.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.SessionMessageMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.session.dto.AnalysisSessionResponse;
import com.sage.backend.session.dto.CreateSessionRequest;
import com.sage.backend.session.dto.CreateSessionResponse;
import com.sage.backend.session.dto.PostSessionMessageRequest;
import com.sage.backend.session.dto.SessionListResponse;
import com.sage.backend.session.dto.SessionMessagesResponse;
import com.sage.backend.session.dto.UploadSessionAttachmentResponse;
import com.sage.backend.task.TaskService;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getSessionBuildsTypedProjectionsForWaitingTask() throws Exception {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_waiting", "WAITING_USER", "task_waiting", null);
        TaskState taskState = taskState("task_waiting", "sess_waiting", TaskStatus.WAITING_USER.name(), "MISSING_INPUT");
        TaskDetailResponse detail = waitingTaskDetail("task_waiting");

        session.setCurrentRequiredUserActionJson("""
                {
                  "waiting_reason_type": "MISSING_INPUT",
                  "resume_hint": "Upload precipitation before resuming.",
                  "can_resume": false,
                  "required_user_actions": [
                    {"action_type": "upload", "key": "upload_precipitation", "label": "Upload precipitation", "required": true}
                  ]
                }
                """);
        session.setSessionSummaryJson("""
                {
                  "task_id": "task_waiting",
                  "status": "WAITING_USER",
                  "user_goal": "Run water yield",
                  "latest_result_bundle_id": null
                }
                """);

        when(harness.analysisSessionMapper.findBySessionId("sess_waiting")).thenReturn(session);
        when(harness.taskStateMapper.findByTaskId("task_waiting")).thenReturn(taskState);
        when(harness.taskService.getTask("task_waiting", 42L)).thenReturn(detail);

        AnalysisSessionResponse response = harness.service.getSession(42L, "sess_waiting");

        assertEquals("WAITING_USER", response.getStatus());
        assertEquals("task_waiting", response.getSessionContextSummary().getTaskId());
        assertEquals("WAITING_USER", response.getCurrentTaskSummary().getTaskState());
        assertEquals("MISSING_INPUT", response.getCurrentRequiredUserAction().getWaitingReasonType());
        assertEquals("MISSING_INPUT", response.getWaitingProjection().getWhyBlocked());
        assertEquals("User clarification or upload", response.getProgressProjection().getEstimatedNextMilestone());
        assertNull(response.getLatestResultSummary());
    }

    @Test
    void getSessionBuildsTypedResultProjectionForReadyResult() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_ready", "READY_RESULT", "task_ready", "result_1");
        TaskState taskState = taskState("task_ready", "sess_ready", TaskStatus.SUCCEEDED.name(), null);
        TaskDetailResponse detail = readyTaskDetail("task_ready", "result_1");
        TaskResultResponse result = readyTaskResult("task_ready", "result_1");

        session.setSessionSummaryJson("""
                {
                  "task_id": "task_ready",
                  "status": "READY_RESULT",
                  "user_goal": "Run water yield",
                  "latest_result_bundle_id": "result_1"
                }
                """);

        when(harness.analysisSessionMapper.findBySessionId("sess_ready")).thenReturn(session);
        when(harness.taskStateMapper.findByTaskId("task_ready")).thenReturn(taskState);
        when(harness.taskService.getTask("task_ready", 42L)).thenReturn(detail);
        when(harness.taskService.getTaskResult("task_ready", 42L)).thenReturn(result);

        AnalysisSessionResponse response = harness.service.getSession(42L, "sess_ready");

        assertEquals("result_1", response.getLatestResultSummary().getLatestResultBundleId());
        assertEquals("Governed result is ready", response.getLatestResultSummary().getFinalExplanation().getTitle());
        assertEquals(List.of("water_yield.tif"), response.getLatestResultSummary().getResultBundle().getMainOutputs());
    }

    @Test
    void listSessionsSortsPrioritySessionsAndBuildsTypedSummary() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession running = session("sess_running", "RUNNING", "task_running", null);
        AnalysisSession waiting = session("sess_waiting", "WAITING_USER", "task_waiting", null);
        AnalysisSession ready = session("sess_ready", "READY_RESULT", "task_ready", "result_1");
        running.setUpdatedAt(OffsetDateTime.parse("2026-04-13T10:00:00Z"));
        waiting.setUpdatedAt(OffsetDateTime.parse("2026-04-13T08:00:00Z"));
        ready.setUpdatedAt(OffsetDateTime.parse("2026-04-13T09:00:00Z"));
        running.setSessionSummaryJson("{\"task_id\":\"task_running\",\"status\":\"RUNNING\",\"user_goal\":\"Run\",\"latest_result_bundle_id\":null}");
        waiting.setSessionSummaryJson("{\"task_id\":\"task_waiting\",\"status\":\"WAITING_USER\",\"user_goal\":\"Wait\",\"latest_result_bundle_id\":null}");
        ready.setSessionSummaryJson("{\"task_id\":\"task_ready\",\"status\":\"READY_RESULT\",\"user_goal\":\"Ready\",\"latest_result_bundle_id\":\"result_1\"}");

        when(harness.analysisSessionMapper.findByUserId(42L, null, null, 50))
                .thenReturn(List.of(running, ready, waiting));

        SessionListResponse response = harness.service.listSessions(42L, null, null, 50);

        assertEquals(List.of("sess_waiting", "sess_ready", "sess_running"),
                response.getItems().stream().map(item -> item.getSessionId()).toList());
        assertEquals(3, response.getSummary().getTotalSessions());
        assertEquals(1, response.getSummary().getNeedsActionCount());
        assertEquals(1, response.getSummary().getReadyResultsCount());
        assertEquals("sess_waiting", response.getSummary().getPrioritySessions().get(0).getSessionId());
        assertEquals("WAITING_USER", response.getSummary().getPrioritySessions().get(0).getSessionSummary().getStatus());
    }

    @Test
    void getMessagesMapsPersistedSessionHistory() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_messages", "RUNNING", "task_messages", null);
        SessionMessage message = new SessionMessage();
        message.setMessageId("msg_1");
        message.setSessionId("sess_messages");
        message.setTaskId("task_messages");
        message.setRole("assistant");
        message.setMessageType("result_summary");
        message.setContentJson("{\"text\":\"Result ready\"}");
        message.setAttachmentRefsJson("{\"attachment_id\":\"att_1\"}");
        message.setActionSchemaJson("{\"action\":\"follow_up\"}");
        message.setRelatedObjectRefsJson("{\"task_id\":\"task_messages\"}");
        message.setCreatedAt(OffsetDateTime.parse("2026-04-13T10:00:00Z"));

        when(harness.analysisSessionMapper.findBySessionId("sess_messages")).thenReturn(session);
        when(harness.sessionMessageMapper.findBySessionId("sess_messages")).thenReturn(List.of(message));

        SessionMessagesResponse response = harness.service.getMessages(42L, "sess_messages");

        assertEquals(1, response.getItems().size());
        assertEquals("msg_1", response.getItems().get(0).getMessageId());
        assertEquals("Result ready", response.getItems().get(0).getContent().get("text"));
        assertEquals("att_1", response.getItems().get(0).getAttachmentRefs().get("attachment_id"));
    }

    @Test
    void createSessionCreatesInitialTaskAndReturnsTypedSnapshot() {
        Harness harness = new Harness(objectMapper);
        CreateSessionRequest request = new CreateSessionRequest();
        request.setUserGoal("Run water yield");
        request.setTitle("Water Yield");
        request.setSceneId("scene_1");

        AnalysisSession createdShell = session("sess_created", "RUNNING", null, null);
        AnalysisSession persisted = session("sess_created", "RUNNING", "task_created", null);
        TaskState taskState = taskState("task_created", "sess_created", TaskStatus.QUEUED.name(), null);
        TaskDetailResponse detail = queuedTaskDetail("task_created");
        persisted.setSessionSummaryJson("{\"task_id\":\"task_created\",\"status\":\"RUNNING\",\"user_goal\":\"Run water yield\",\"latest_result_bundle_id\":null}");

        when(harness.sessionLifecycleService.createSessionShell(42L, "Run water yield", "Water Yield", "scene_1"))
                .thenReturn(createdShell);
        when(harness.analysisSessionMapper.findBySessionId("sess_created")).thenReturn(persisted);
        when(harness.taskStateMapper.findByTaskId("task_created")).thenReturn(taskState);
        when(harness.taskService.getTask("task_created", 42L)).thenReturn(detail);

        CreateSessionResponse response = harness.service.createSession(42L, request);

        ArgumentCaptor<CreateTaskRequest> taskRequestCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(harness.taskService).createTaskInSession(eq(42L), taskRequestCaptor.capture(), eq("sess_created"), eq(true));
        assertEquals("Run water yield", taskRequestCaptor.getValue().getUserQuery());
        assertEquals("sess_created", response.getSessionId());
        assertEquals("task_created", response.getCurrentTaskId());
        assertEquals("QUEUED", response.getCurrentTaskSummary().getTaskState());
    }

    @Test
    void postMessageRejectsBusyTaskWithoutRecordingMessage() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_busy", "RUNNING", "task_busy", null);
        TaskState taskState = taskState("task_busy", "sess_busy", TaskStatus.RUNNING.name(), null);

        when(harness.analysisSessionMapper.findBySessionId("sess_busy")).thenReturn(session);
        when(harness.taskStateMapper.findByTaskId("task_busy")).thenReturn(taskState);

        PostSessionMessageRequest request = new PostSessionMessageRequest();
        request.setContent("continue");
        request.setClientRequestId("req_busy");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.postMessage(42L, "sess_busy", request)
        );

        assertEquals(409, exception.getStatusCode().value());
        verifyNoInteractions(harness.sessionLifecycleService);
        verify(harness.taskService, never()).resumeTask(anyString(), anyLong(), any());
        verify(harness.taskService, never()).createTaskInSession(anyLong(), any(), anyString(), eq(false));
    }

    @Test
    void postMessageResumesWaitingClarificationTask() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_waiting", "WAITING_USER", "task_waiting", null);
        TaskState taskState = taskState("task_waiting", "sess_waiting", TaskStatus.WAITING_USER.name(), "CLARIFY_CASE");
        TaskDetailResponse detail = waitingTaskDetail("task_waiting");

        when(harness.analysisSessionMapper.findBySessionId("sess_waiting")).thenReturn(session);
        when(harness.taskStateMapper.findByTaskId("task_waiting")).thenReturn(taskState, taskState);
        when(harness.taskService.getTask("task_waiting", 42L)).thenReturn(detail);

        PostSessionMessageRequest request = new PostSessionMessageRequest();
        request.setContent("Use annual_water_yield_gura");
        request.setClientRequestId("req_resume");
        request.setAttachmentIds(List.of("att_1"));
        request.setArgsOverrides(Map.of("case_id", "annual_water_yield_gura"));

        harness.service.postMessage(42L, "sess_waiting", request);

        verify(harness.sessionLifecycleService).recordUserClarification(
                "sess_waiting",
                "task_waiting",
                "Use annual_water_yield_gura",
                "req_resume",
                List.of("att_1")
        );
        ArgumentCaptor<ResumeTaskRequest> resumeCaptor = ArgumentCaptor.forClass(ResumeTaskRequest.class);
        verify(harness.taskService).resumeTask(eq("task_waiting"), eq(42L), resumeCaptor.capture());
        assertEquals("req_resume", resumeCaptor.getValue().getResumeRequestId());
        assertEquals("Use annual_water_yield_gura", resumeCaptor.getValue().getUserNote());
        assertEquals("annual_water_yield_gura", resumeCaptor.getValue().getArgsOverrides().get("case_id"));
    }

    @Test
    void postMessageCreatesFollowUpTaskForTerminalState() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_terminal", "READY_RESULT", "task_terminal", "result_1");
        TaskState taskState = taskState("task_terminal", "sess_terminal", TaskStatus.SUCCEEDED.name(), null);
        TaskDetailResponse detail = queuedTaskDetail("task_terminal");
        TaskResultResponse result = readyTaskResult("task_terminal", "result_1");

        when(harness.analysisSessionMapper.findBySessionId("sess_terminal")).thenReturn(session);
        when(harness.taskStateMapper.findByTaskId("task_terminal")).thenReturn(taskState, taskState);
        when(harness.taskService.getTask("task_terminal", 42L)).thenReturn(detail);
        when(harness.taskService.getTaskResult("task_terminal", 42L)).thenReturn(result);

        PostSessionMessageRequest request = new PostSessionMessageRequest();
        request.setContent("Run another basin");
        request.setClientRequestId("req_follow_up");

        harness.service.postMessage(42L, "sess_terminal", request);

        verify(harness.sessionLifecycleService).recordUserReply(
                "sess_terminal",
                "task_terminal",
                "Run another basin",
                "req_follow_up",
                null
        );
        ArgumentCaptor<CreateTaskRequest> taskRequestCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(harness.taskService).createTaskInSession(eq(42L), taskRequestCaptor.capture(), eq("sess_terminal"), eq(false));
        assertEquals("Run another basin", taskRequestCaptor.getValue().getUserQuery());
    }

    @Test
    void uploadAttachmentRejectsWhenNoActiveTask() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_no_task", "RUNNING", "", null);
        when(harness.analysisSessionMapper.findBySessionId("sess_no_task")).thenReturn(session);

        MockMultipartFile file = new MockMultipartFile("file", "input.txt", "text/plain", "payload".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> harness.service.uploadAttachment(42L, "sess_no_task", file, "supporting_input")
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(harness.taskService, never()).uploadAttachment(anyString(), anyLong(), any(), anyString());
    }

    @Test
    void uploadAttachmentReturnsTaskScopedUploadResponse() {
        Harness harness = new Harness(objectMapper);
        AnalysisSession session = session("sess_upload", "RUNNING", "task_upload", null);
        UploadAttachmentResponse upload = new UploadAttachmentResponse();
        upload.setTaskId("task_upload");
        upload.setAttachmentId("att_upload");
        upload.setLogicalSlot("precipitation");
        upload.setStoredPath("runtime/uploads/precipitation.tif");
        upload.setSizeBytes(1024L);
        upload.setCreatedAt("2026-04-13T10:00:00Z");
        upload.setAssignmentStatus("ASSIGNED");

        when(harness.analysisSessionMapper.findBySessionId("sess_upload")).thenReturn(session);
        when(harness.taskService.uploadAttachment(eq("task_upload"), eq(42L), any(), eq("precipitation"))).thenReturn(upload);

        MockMultipartFile file = new MockMultipartFile("file", "precipitation.tif", "image/tiff", "data".getBytes());

        UploadSessionAttachmentResponse response = harness.service.uploadAttachment(42L, "sess_upload", file, "precipitation");

        assertEquals("sess_upload", response.getSessionId());
        assertEquals("att_upload", response.getAttachmentId());
        assertEquals("ASSIGNED", response.getAssignmentStatus());
    }

    private AnalysisSession session(String sessionId, String status, String currentTaskId, String latestResultBundleId) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(sessionId);
        session.setUserId(42L);
        session.setTitle("Session " + sessionId);
        session.setUserGoal("Run water yield");
        session.setStatus(status);
        session.setSceneId("scene_1");
        session.setCurrentTaskId(currentTaskId);
        session.setLatestResultBundleId(latestResultBundleId);
        session.setCreatedAt(OffsetDateTime.of(2026, 4, 13, 8, 0, 0, 0, ZoneOffset.UTC));
        session.setUpdatedAt(OffsetDateTime.of(2026, 4, 13, 9, 0, 0, 0, ZoneOffset.UTC));
        return session;
    }

    private TaskState taskState(String taskId, String sessionId, String state, String waitingReasonType) {
        TaskState taskState = new TaskState();
        taskState.setTaskId(taskId);
        taskState.setSessionId(sessionId);
        taskState.setUserId(42L);
        taskState.setCurrentState(state);
        taskState.setWaitingReasonType(waitingReasonType);
        return taskState;
    }

    private TaskDetailResponse waitingTaskDetail(String taskId) {
        TaskDetailResponse response = baseTaskDetail(taskId, TaskStatus.WAITING_USER.name());
        TaskDetailResponse.WaitingContext waitingContext = new TaskDetailResponse.WaitingContext();
        waitingContext.setWaitingReasonType("MISSING_INPUT");
        TaskDetailResponse.MissingSlot missingSlot = new TaskDetailResponse.MissingSlot();
        missingSlot.setSlotName("precipitation");
        missingSlot.setExpectedType("raster");
        missingSlot.setRequired(true);
        waitingContext.setMissingSlots(List.of(missingSlot));
        TaskDetailResponse.RequiredUserAction userAction = new TaskDetailResponse.RequiredUserAction();
        userAction.setActionType("upload");
        userAction.setKey("upload_precipitation");
        userAction.setLabel("Upload precipitation");
        userAction.setRequired(true);
        waitingContext.setRequiredUserActions(List.of(userAction));
        waitingContext.setResumeHint("Upload precipitation before resuming.");
        waitingContext.setCanResume(false);
        response.setWaitingContext(waitingContext);
        return response;
    }

    private TaskDetailResponse queuedTaskDetail(String taskId) {
        return baseTaskDetail(taskId, TaskStatus.QUEUED.name());
    }

    private TaskDetailResponse readyTaskDetail(String taskId, String resultBundleId) {
        TaskDetailResponse response = baseTaskDetail(taskId, TaskStatus.SUCCEEDED.name());
        response.setLatestResultBundleId(resultBundleId);
        return response;
    }

    private TaskDetailResponse baseTaskDetail(String taskId, String state) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setTaskId(taskId);
        response.setState(state);
        response.setStateVersion(3);
        response.setPlanningRevision(2);
        response.setCheckpointVersion(1);
        response.setPromotionStatus("PROMOTED");
        response.setCognitionVerdict("PRIMARY_OK");
        return response;
    }

    private TaskResultResponse readyTaskResult(String taskId, String resultBundleId) {
        TaskResultResponse response = new TaskResultResponse();
        response.setTaskId(taskId);
        response.setTaskState(TaskStatus.SUCCEEDED.name());
        TaskResultResponse.ResultBundle resultBundle = new TaskResultResponse.ResultBundle();
        resultBundle.setResultId(resultBundleId);
        resultBundle.setTaskId(taskId);
        resultBundle.setSummary("Result bundle summary");
        resultBundle.setMainOutputs(List.of("water_yield.tif"));
        response.setResultBundle(resultBundle);
        TaskResultResponse.FinalExplanation finalExplanation = new TaskResultResponse.FinalExplanation();
        finalExplanation.setAvailable(true);
        finalExplanation.setTitle("Governed result is ready");
        finalExplanation.setHighlights(List.of("Output promoted"));
        response.setFinalExplanation(finalExplanation);
        return response;
    }

    private static final class Harness {
        private final AnalysisSessionMapper analysisSessionMapper = mock(AnalysisSessionMapper.class);
        private final SessionMessageMapper sessionMessageMapper = mock(SessionMessageMapper.class);
        private final TaskStateMapper taskStateMapper = mock(TaskStateMapper.class);
        private final TaskService taskService = mock(TaskService.class);
        private final SessionLifecycleService sessionLifecycleService = mock(SessionLifecycleService.class);
        private final SessionService service;

        private Harness(ObjectMapper objectMapper) {
            this.service = new SessionService(
                    analysisSessionMapper,
                    sessionMessageMapper,
                    taskStateMapper,
                    taskService,
                    sessionLifecycleService,
                    objectMapper
            );
        }
    }
}
