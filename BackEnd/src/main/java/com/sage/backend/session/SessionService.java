package com.sage.backend.session;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.sage.backend.session.dto.SessionMessageDto;
import com.sage.backend.session.dto.SessionMessagesResponse;
import com.sage.backend.session.dto.SessionStreamResponse;
import com.sage.backend.session.dto.UploadSessionAttachmentResponse;
import com.sage.backend.task.TaskService;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SessionService {

    private static final String SESSION_BUSY_MESSAGE =
            "SESSION_BUSY: Current session is being advanced by the system; new progress input is temporarily rejected.";

    private final AnalysisSessionMapper analysisSessionMapper;
    private final SessionMessageMapper sessionMessageMapper;
    private final TaskStateMapper taskStateMapper;
    private final TaskService taskService;
    private final SessionLifecycleService sessionLifecycleService;
    private final ObjectMapper objectMapper;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public SessionService(
            AnalysisSessionMapper analysisSessionMapper,
            SessionMessageMapper sessionMessageMapper,
            TaskStateMapper taskStateMapper,
            TaskService taskService,
            SessionLifecycleService sessionLifecycleService,
            ObjectMapper objectMapper
    ) {
        this.analysisSessionMapper = analysisSessionMapper;
        this.sessionMessageMapper = sessionMessageMapper;
        this.taskStateMapper = taskStateMapper;
        this.taskService = taskService;
        this.sessionLifecycleService = sessionLifecycleService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateSessionResponse createSession(Long userId, CreateSessionRequest request) {
        AnalysisSession session = sessionLifecycleService.createSessionShell(
                userId,
                request.getUserGoal(),
                request.getTitle(),
                request.getSceneId()
        );
        CreateTaskRequest createTaskRequest = new CreateTaskRequest();
        createTaskRequest.setUserQuery(request.getUserGoal());
        taskService.createTaskInSession(userId, createTaskRequest, session.getSessionId(), true);
        return toCreateSessionResponse(getSession(userId, session.getSessionId()));
    }

    public SessionListResponse listSessions(Long userId, String status, String sceneId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        List<AnalysisSession> sessions = new ArrayList<>(analysisSessionMapper.findByUserId(userId, status, sceneId, safeLimit));
        sessions.sort(priorityComparator());

        SessionListResponse response = new SessionListResponse();
        for (AnalysisSession session : sessions) {
            response.getItems().add(toListItem(session));
        }

        SessionListResponse.SessionListSummaryProjection summary = new SessionListResponse.SessionListSummaryProjection();
        summary.setTotalSessions(sessions.size());
        summary.setNeedsActionCount((int) sessions.stream().filter(session -> "WAITING_USER".equals(session.getStatus())).count());
        summary.setRunningCount((int) sessions.stream().filter(session -> "RUNNING".equals(session.getStatus())).count());
        summary.setReadyResultsCount((int) sessions.stream().filter(session -> "READY_RESULT".equals(session.getStatus())).count());
        sessions.stream().sorted(priorityComparator()).limit(5).map(this::toListItem).forEach(summary.getPrioritySessions()::add);
        response.setSummary(summary);
        return response;
    }

    public AnalysisSessionResponse getSession(Long userId, String sessionId) {
        AnalysisSession session = getOwnedSession(sessionId, userId);
        TaskState currentTask = resolveCurrentTask(session);

        AnalysisSessionResponse response = new AnalysisSessionResponse();
        response.setSessionId(session.getSessionId());
        response.setTitle(session.getTitle());
        response.setUserGoal(session.getUserGoal());
        response.setStatus(session.getStatus());
        response.setSceneId(session.getSceneId());
        response.setCurrentTaskId(session.getCurrentTaskId());
        response.setLatestResultBundleId(session.getLatestResultBundleId());
        response.setCreatedAt(toIsoString(session.getCreatedAt()));
        response.setUpdatedAt(toIsoString(session.getUpdatedAt()));
        response.setCurrentRequiredUserAction(readMap(session.getCurrentRequiredUserActionJson()));
        response.setSessionContextSummary(readMap(session.getSessionSummaryJson()));

        if (currentTask != null) {
            TaskDetailResponse taskDetail = taskService.getTask(currentTask.getTaskId(), userId);
            response.setCurrentTaskSummary(buildCurrentTaskSummary(taskDetail));
            response.setProgressProjection(buildProgressProjection(taskDetail));
            response.setWaitingProjection(buildWaitingProjection(taskDetail));
            if (taskDetail.getLatestResultBundleId() != null || "READY_RESULT".equals(session.getStatus())) {
                TaskResultResponse taskResult = taskService.getTaskResult(currentTask.getTaskId(), userId);
                response.setLatestResultSummary(buildLatestResultSummary(taskResult));
            }
        }

        return response;
    }

    public SessionMessagesResponse getMessages(Long userId, String sessionId) {
        AnalysisSession session = getOwnedSession(sessionId, userId);
        SessionMessagesResponse response = new SessionMessagesResponse();
        response.setSessionId(session.getSessionId());
        for (SessionMessage message : sessionMessageMapper.findBySessionId(sessionId)) {
            response.getItems().add(toMessageDto(message));
        }
        return response;
    }

    @Transactional
    public AnalysisSessionResponse postMessage(Long userId, String sessionId, PostSessionMessageRequest request) {
        AnalysisSession session = getOwnedSession(sessionId, userId);
        TaskState currentTask = resolveCurrentTask(session);

        if (currentTask != null && isBusyTaskState(currentTask.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, SESSION_BUSY_MESSAGE);
        }

        if (currentTask == null) {
            sessionLifecycleService.recordUserReply(sessionId, null, request.getContent(), request.getClientRequestId(), request.getAttachmentIds());
            CreateTaskRequest createTaskRequest = new CreateTaskRequest();
            createTaskRequest.setUserQuery(request.getContent());
            taskService.createTaskInSession(userId, createTaskRequest, sessionId, false);
            return getSession(userId, sessionId);
        }

        if (TaskStatus.WAITING_USER.name().equals(currentTask.getCurrentState())) {
            if (isClarificationTask(currentTask)) {
                sessionLifecycleService.recordUserClarification(sessionId, currentTask.getTaskId(), request.getContent(), request.getClientRequestId(), request.getAttachmentIds());
            } else {
                sessionLifecycleService.recordUserReply(sessionId, currentTask.getTaskId(), request.getContent(), request.getClientRequestId(), request.getAttachmentIds());
            }
            ResumeTaskRequest resumeTaskRequest = new ResumeTaskRequest();
            resumeTaskRequest.setResumeRequestId(request.getClientRequestId());
            resumeTaskRequest.setAttachmentIds(request.getAttachmentIds());
            resumeTaskRequest.setSlotOverrides(request.getSlotOverrides());
            resumeTaskRequest.setArgsOverrides(request.getArgsOverrides());
            resumeTaskRequest.setUserNote(request.getContent());
            taskService.resumeTask(currentTask.getTaskId(), userId, resumeTaskRequest);
            return getSession(userId, sessionId);
        }

        if (isTerminalTaskState(currentTask.getCurrentState())) {
            sessionLifecycleService.recordUserReply(sessionId, currentTask.getTaskId(), request.getContent(), request.getClientRequestId(), request.getAttachmentIds());
            CreateTaskRequest createTaskRequest = new CreateTaskRequest();
            createTaskRequest.setUserQuery(request.getContent());
            taskService.createTaskInSession(userId, createTaskRequest, sessionId, false);
            return getSession(userId, sessionId);
        }

        throw new ResponseStatusException(CONFLICT, SESSION_BUSY_MESSAGE);
    }

    @Transactional
    public UploadSessionAttachmentResponse uploadAttachment(Long userId, String sessionId, MultipartFile file, String logicalSlot) {
        AnalysisSession session = getOwnedSession(sessionId, userId);
        if (session.getCurrentTaskId() == null || session.getCurrentTaskId().isBlank()) {
            throw new ResponseStatusException(CONFLICT, "NO_ACTIVE_TASK: The session has no active task to receive attachments.");
        }
        UploadAttachmentResponse upload = taskService.uploadAttachment(session.getCurrentTaskId(), userId, file, logicalSlot);
        UploadSessionAttachmentResponse response = new UploadSessionAttachmentResponse();
        response.setSessionId(sessionId);
        response.setTaskId(upload.getTaskId());
        response.setAttachmentId(upload.getAttachmentId());
        response.setLogicalSlot(upload.getLogicalSlot());
        response.setStoredPath(upload.getStoredPath());
        response.setSizeBytes(upload.getSizeBytes());
        response.setCreatedAt(upload.getCreatedAt());
        response.setAssignmentStatus(upload.getAssignmentStatus());
        return response;
    }

    public SseEmitter streamSession(Long userId, String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);

        Runnable emitSnapshot = () -> {
            try {
                SessionStreamResponse payload = new SessionStreamResponse();
                AnalysisSessionResponse session = getSession(userId, sessionId);
                payload.setSession(session);
                payload.setMessages(getMessages(userId, sessionId));
                payload.setProgressProjection(session.getProgressProjection());
                payload.setWaitingProjection(session.getWaitingProjection());
                payload.setLatestResultSummary(session.getLatestResultSummary());
                emitter.send(SseEmitter.event().name("session_update").data(payload));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        };

        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> {
            running.set(false);
            emitter.complete();
        });
        emitter.onError(error -> running.set(false));

        streamExecutor.execute(() -> {
            try {
                emitSnapshot.run();
                while (running.get()) {
                    Thread.sleep(3000);
                    emitSnapshot.run();
                }
            } catch (Exception exception) {
                if (running.get()) {
                    emitter.completeWithError(exception);
                }
            }
        });
        return emitter;
    }

    private AnalysisSession getOwnedSession(String sessionId, Long userId) {
        AnalysisSession session = analysisSessionMapper.findBySessionId(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ResponseStatusException(NOT_FOUND, "Session not found");
        }
        return session;
    }

    private TaskState resolveCurrentTask(AnalysisSession session) {
        if (session.getCurrentTaskId() == null || session.getCurrentTaskId().isBlank()) {
            return null;
        }
        return taskStateMapper.findByTaskId(session.getCurrentTaskId());
    }

    private SessionListResponse.SessionListItem toListItem(AnalysisSession session) {
        SessionListResponse.SessionListItem item = new SessionListResponse.SessionListItem();
        item.setSessionId(session.getSessionId());
        item.setTitle(session.getTitle());
        item.setUserGoal(session.getUserGoal());
        item.setStatus(session.getStatus());
        item.setSceneId(session.getSceneId());
        item.setCurrentTaskId(session.getCurrentTaskId());
        item.setLatestResultBundleId(session.getLatestResultBundleId());
        item.setSessionSummary(readMap(session.getSessionSummaryJson()));
        item.setCreatedAt(toIsoString(session.getCreatedAt()));
        item.setUpdatedAt(toIsoString(session.getUpdatedAt()));
        return item;
    }

    private SessionMessageDto toMessageDto(SessionMessage message) {
        SessionMessageDto dto = new SessionMessageDto();
        dto.setMessageId(message.getMessageId());
        dto.setSessionId(message.getSessionId());
        dto.setTaskId(message.getTaskId());
        dto.setResultBundleId(message.getResultBundleId());
        dto.setRole(message.getRole());
        dto.setMessageType(message.getMessageType());
        dto.setContent(readMap(message.getContentJson()));
        dto.setAttachmentRefs(readMap(message.getAttachmentRefsJson()));
        dto.setActionSchema(readMap(message.getActionSchemaJson()));
        dto.setRelatedObjectRefs(readMap(message.getRelatedObjectRefsJson()));
        dto.setCreatedAt(toIsoString(message.getCreatedAt()));
        return dto;
    }

    private CreateSessionResponse toCreateSessionResponse(AnalysisSessionResponse response) {
        CreateSessionResponse created = new CreateSessionResponse();
        created.setSessionId(response.getSessionId());
        created.setTitle(response.getTitle());
        created.setUserGoal(response.getUserGoal());
        created.setStatus(response.getStatus());
        created.setSceneId(response.getSceneId());
        created.setCurrentTaskId(response.getCurrentTaskId());
        created.setLatestResultBundleId(response.getLatestResultBundleId());
        created.setCreatedAt(response.getCreatedAt());
        created.setUpdatedAt(response.getUpdatedAt());
        created.setCurrentRequiredUserAction(response.getCurrentRequiredUserAction());
        created.setSessionContextSummary(response.getSessionContextSummary());
        created.setCurrentTaskSummary(response.getCurrentTaskSummary());
        created.setLatestResultSummary(response.getLatestResultSummary());
        created.setProgressProjection(response.getProgressProjection());
        created.setWaitingProjection(response.getWaitingProjection());
        return created;
    }

    private Map<String, Object> buildCurrentTaskSummary(TaskDetailResponse taskDetail) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("task_id", taskDetail.getTaskId());
        summary.put("task_state", taskDetail.getState());
        summary.put("state_version", taskDetail.getStateVersion());
        summary.put("planning_revision", taskDetail.getPlanningRevision());
        summary.put("checkpoint_version", taskDetail.getCheckpointVersion());
        summary.put("promotion_status", taskDetail.getPromotionStatus());
        summary.put("cognition_verdict", taskDetail.getCognitionVerdict());
        summary.put("latest_result_bundle_id", taskDetail.getLatestResultBundleId());
        return summary;
    }

    private Map<String, Object> buildProgressProjection(TaskDetailResponse taskDetail) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("current_phase_label", taskDetail.getState());
        projection.put("current_system_action", describeTaskState(taskDetail.getState()));
        projection.put("latest_progress_note", taskDetail.getState() + " is the current governed phase.");
        projection.put("estimated_next_milestone", estimateNextMilestone(taskDetail.getState()));
        projection.put("related_task_id", taskDetail.getTaskId());
        projection.put("related_result_bundle_id", taskDetail.getLatestResultBundleId());
        return projection;
    }

    private Map<String, Object> buildWaitingProjection(TaskDetailResponse taskDetail) {
        if (taskDetail.getWaitingContext() == null) {
            return null;
        }
        Map<String, Object> root = objectMapper.convertValue(taskDetail.getWaitingContext(), new TypeReference<>() {});
        root.put("why_blocked", taskDetail.getWaitingContext().getWaitingReasonType());
        root.put("user_facing_phrasing", "The system is blocked on additional input before governed execution can continue.");
        return root;
    }

    private Map<String, Object> buildLatestResultSummary(TaskResultResponse taskResult) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("task_id", taskResult.getTaskId());
        summary.put("task_state", taskResult.getTaskState());
        summary.put("result_bundle", objectMapper.convertValue(taskResult.getResultBundle(), new TypeReference<Map<String, Object>>() {}));
        summary.put("final_explanation", objectMapper.convertValue(taskResult.getFinalExplanation(), new TypeReference<Map<String, Object>>() {}));
        summary.put("latest_result_bundle_id", taskResult.getResultBundle() == null ? null : taskResult.getResultBundle().getResultId());
        return summary;
    }

    private Comparator<AnalysisSession> priorityComparator() {
        return Comparator
                .comparingInt((AnalysisSession session) -> priorityRank(session.getStatus()))
                .thenComparing(AnalysisSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AnalysisSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int priorityRank(String status) {
        return switch (status) {
            case "WAITING_USER" -> 0;
            case "READY_RESULT" -> 1;
            case "RUNNING" -> 2;
            case "FAILED" -> 3;
            case "CANCELLED" -> 4;
            default -> 5;
        };
    }

    private boolean isBusyTaskState(String taskState) {
        return TaskStatus.CREATED.name().equals(taskState)
                || TaskStatus.COGNIZING.name().equals(taskState)
                || TaskStatus.PLANNING.name().equals(taskState)
                || TaskStatus.VALIDATING.name().equals(taskState)
                || TaskStatus.QUEUED.name().equals(taskState)
                || TaskStatus.RUNNING.name().equals(taskState)
                || TaskStatus.ARTIFACT_PROMOTING.name().equals(taskState)
                || TaskStatus.RESUMING.name().equals(taskState)
                || TaskStatus.RESULT_PROCESSING.name().equals(taskState);
    }

    private boolean isTerminalTaskState(String taskState) {
        return TaskStatus.SUCCEEDED.name().equals(taskState)
                || TaskStatus.FAILED.name().equals(taskState)
                || TaskStatus.CANCELLED.name().equals(taskState)
                || TaskStatus.STATE_CORRUPTED.name().equals(taskState);
    }

    private boolean isClarificationTask(TaskState taskState) {
        String waitingReasonType = taskState.getWaitingReasonType();
        return waitingReasonType != null && waitingReasonType.startsWith("CLARIFY");
    }

    private String describeTaskState(String state) {
        return switch (state) {
            case "CREATED", "COGNIZING" -> "Interpreting the request and establishing the governed route";
            case "PLANNING" -> "Building governed inputs and execution bindings";
            case "VALIDATING" -> "Checking readiness and governance constraints";
            case "WAITING_USER" -> "Waiting for clarification or missing input";
            case "RESUMING" -> "Re-entering governed validation after your input";
            case "QUEUED" -> "Queued for runtime execution";
            case "RUNNING" -> "Runtime execution is in progress";
            case "ARTIFACT_PROMOTING" -> "Persisting and promoting governed outputs";
            case "SUCCEEDED" -> "Result is ready";
            case "FAILED", "STATE_CORRUPTED" -> "Execution failed and needs inspection";
            case "CANCELLED" -> "Execution was cancelled";
            default -> "The session is progressing through the governed task pipeline";
        };
    }

    private String estimateNextMilestone(String state) {
        return switch (state) {
            case "CREATED", "COGNIZING", "PLANNING" -> "Validation";
            case "VALIDATING", "RESUMING" -> "Queue or waiting";
            case "QUEUED" -> "Runtime start";
            case "RUNNING" -> "Result packaging";
            case "ARTIFACT_PROMOTING" -> "Session result summary";
            case "WAITING_USER" -> "User clarification or upload";
            case "SUCCEEDED" -> "Follow-up or review";
            case "FAILED", "STATE_CORRUPTED" -> "Failure review";
            case "CANCELLED" -> "No further milestones";
            default -> "Further governed progression";
        };
    }

    private Map<String, Object> readMap(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sourceJson, new TypeReference<>() {});
        } catch (Exception exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", sourceJson);
            return fallback;
        }
    }

    private String toIsoString(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
