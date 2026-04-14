package com.sage.backend.scene;

import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.scene.dto.PostSceneSessionMessageRequest;
import com.sage.backend.scene.dto.PostSceneSessionMessageResponse;
import com.sage.backend.scene.dto.SceneActionRecommendationDTO;
import com.sage.backend.scene.dto.SceneAuditSummaryDTO;
import com.sage.backend.scene.dto.SceneBlockingSummaryDTO;
import com.sage.backend.scene.dto.SceneDetailDTO;
import com.sage.backend.scene.dto.SceneListResponse;
import com.sage.backend.scene.dto.SceneListSummaryDTO;
import com.sage.backend.scene.dto.SceneResultSummaryDTO;
import com.sage.backend.scene.dto.SceneSummaryDTO;
import com.sage.backend.scene.dto.SessionMessageDTO;
import com.sage.backend.scene.dto.SessionMessagesResponseDTO;
import com.sage.backend.scene.dto.SessionProjectionDTO;
import com.sage.backend.session.SessionService;
import com.sage.backend.session.dto.AnalysisSessionResponse;
import com.sage.backend.session.dto.PostSessionMessageRequest;
import com.sage.backend.session.dto.SessionMessageDto;
import com.sage.backend.session.dto.SessionMessagesResponse;
import com.sage.backend.session.dto.UploadSessionAttachmentResponse;
import com.sage.backend.task.TaskService;
import com.sage.backend.task.dto.TaskAuditResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SceneProjectionService {

    private final SceneProjectionQueryService sceneProjectionQueryService;
    private final SessionService sessionService;
    private final TaskService taskService;
    private final DemoSceneSessionSimulationService demoSceneSessionSimulationService;

    public SceneProjectionService(
            SceneProjectionQueryService sceneProjectionQueryService,
            SessionService sessionService,
            TaskService taskService,
            DemoSceneSessionSimulationService demoSceneSessionSimulationService
    ) {
        this.sceneProjectionQueryService = sceneProjectionQueryService;
        this.sessionService = sessionService;
        this.taskService = taskService;
        this.demoSceneSessionSimulationService = demoSceneSessionSimulationService;
    }

    public SceneListResponse getScenes(Long userId, String status, String query, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        List<SceneSummaryDTO> summaries = new ArrayList<>();
        for (SceneProjectionContext context : sceneProjectionQueryService.loadAllSceneContexts(userId)) {
            summaries.add(buildSceneSummary(userId, context));
        }

        List<SceneSummaryDTO> filtered = summaries.stream()
                .filter(item -> matchesStatus(item, status))
                .filter(item -> matchesQuery(item, query))
                .sorted(sceneSummaryComparator())
                .toList();

        SceneListResponse response = new SceneListResponse();
        filtered.stream().limit(safeLimit).forEach(response.getItems()::add);

        SceneListSummaryDTO summary = new SceneListSummaryDTO();
        summary.setTotalScenes(filtered.size());
        summary.setNeedsActionCount((int) filtered.stream().filter(item -> Boolean.TRUE.equals(item.getNeedsAttention())).count());
        summary.setRunningCount((int) filtered.stream().filter(item -> "RUNNING".equals(item.getSceneStatus())).count());
        summary.setReadyResultsCount((int) filtered.stream().filter(item -> item.getResultSummary() != null && Boolean.TRUE.equals(item.getResultSummary().getHasReadyResult())).count());
        response.setSummary(summary);
        return response;
    }

    public SceneDetailDTO getScene(Long userId, String sceneId) {
        SceneProjectionContext context = sceneProjectionQueryService.loadSceneContext(userId, sceneId);
        TaskDetailResponse currentTaskDetail = loadTaskDetail(userId, context.getCurrentTask());
        TaskAuditResponse currentTaskAudit = loadTaskAudit(userId, context.getCurrentTask());
        TaskEventsResponse currentTaskEvents = loadTaskEvents(userId, context.getCurrentTask());
        List<TaskDetailResponse> readyResultTaskDetails = loadReadyResultTaskDetails(userId, context, currentTaskDetail);
        SceneResultSummaryDTO resultSummary = buildResultSummary(readyResultTaskDetails);

        SceneDetailDTO detail = new SceneDetailDTO();
        detail.setSceneId(context.getSceneId());
        detail.setSceneName(null);
        detail.setSceneStatus(resolveSceneStatus(context, resultSummary));
        detail.setCreatedAt(resolveCreatedAt(context));
        detail.setUpdatedAt(resolveUpdatedAt(context, resultSummary, currentTaskAudit, currentTaskEvents));
        detail.setCurrentSessionId(context.getCurrentSession() == null ? null : context.getCurrentSession().getSessionId());
        detail.setCurrentTaskId(context.getCurrentTask() == null ? null : context.getCurrentTask().getTaskId());
        detail.setUserGoalSummary(resolveUserGoalSummary(context));
        detail.setTaskState(currentTaskDetail == null ? null : currentTaskDetail.getState());
        detail.setBlockingSummary(buildBlockingSummary(currentTaskDetail));
        detail.setResultSummary(resultSummary);
        detail.setAuditSummary(buildAuditSummary(currentTaskAudit));
        detail.setActionRecommendation(buildActionRecommendation(detail.getSceneStatus(), detail.getBlockingSummary(), resultSummary, currentTaskDetail));
        detail.setSessionProjection(loadSessionProjection(userId, context.getCurrentSession()));
        return detail;
    }

    public SessionProjectionDTO getSceneSession(Long userId, String sceneId) {
        SceneProjectionContext context = sceneProjectionQueryService.loadSceneContext(userId, sceneId);
        return loadSessionProjection(userId, context.getCurrentSession());
    }

    public SessionMessagesResponseDTO getSceneSessionMessages(Long userId, String sceneId) {
        SceneProjectionContext context = sceneProjectionQueryService.loadSceneContext(userId, sceneId);
        AnalysisSession currentSession = requireCurrentSession(context);
        return toSceneMessagesResponse(sessionService.getMessages(userId, currentSession.getSessionId()));
    }

    public PostSceneSessionMessageResponse postSceneSessionMessage(Long userId, String sceneId, PostSceneSessionMessageRequest request) {
        SceneProjectionContext context = sceneProjectionQueryService.loadSceneContext(userId, sceneId);
        AnalysisSession currentSession = requireCurrentSession(context);

        PostSessionMessageRequest delegate = new PostSessionMessageRequest();
        delegate.setContent(request.getContent());
        delegate.setClientRequestId(request.getClientRequestId());
        delegate.setAttachmentIds(request.getAttachmentIds());
        delegate.setSlotOverrides(request.getSlotOverrides());
        delegate.setArgsOverrides(request.getArgsOverrides());

        AnalysisSessionResponse updatedSession;
        if (demoSceneSessionSimulationService.shouldSimulate(sceneId, currentSession)) {
            demoSceneSessionSimulationService.simulateFirstTurn(currentSession, request);
            updatedSession = sessionService.getSession(userId, currentSession.getSessionId());
        } else {
            updatedSession = sessionService.postMessage(userId, currentSession.getSessionId(), delegate);
        }
        SessionMessagesResponse updatedMessages = sessionService.getMessages(userId, currentSession.getSessionId());

        PostSceneSessionMessageResponse response = new PostSceneSessionMessageResponse();
        response.setAcceptedMessage(findAcceptedMessage(updatedMessages, request.getClientRequestId()));
        response.setSession(toSessionProjection(updatedSession));
        return response;
    }

    public UploadSessionAttachmentResponse uploadSceneSessionAttachment(Long userId, String sceneId, MultipartFile file, String logicalSlot) {
        SceneProjectionContext context = sceneProjectionQueryService.loadSceneContext(userId, sceneId);
        AnalysisSession currentSession = requireCurrentSession(context);
        return sessionService.uploadAttachment(userId, currentSession.getSessionId(), file, logicalSlot);
    }

    private SceneSummaryDTO buildSceneSummary(Long userId, SceneProjectionContext context) {
        TaskDetailResponse currentTaskDetail = loadTaskDetail(userId, context.getCurrentTask());
        List<TaskDetailResponse> readyResultTaskDetails = loadReadyResultTaskDetails(userId, context, currentTaskDetail);
        SceneResultSummaryDTO resultSummary = buildResultSummary(readyResultTaskDetails);
        String sceneStatus = resolveSceneStatus(context, resultSummary);
        SceneBlockingSummaryDTO blockingSummary = buildBlockingSummary(currentTaskDetail);

        SceneSummaryDTO summary = new SceneSummaryDTO();
        summary.setSceneId(context.getSceneId());
        summary.setSceneName(null);
        summary.setSceneStatus(sceneStatus);
        summary.setCurrentSessionId(context.getCurrentSession() == null ? null : context.getCurrentSession().getSessionId());
        summary.setCurrentTaskId(context.getCurrentTask() == null ? null : context.getCurrentTask().getTaskId());
        summary.setUserGoalSummary(resolveUserGoalSummary(context));
        summary.setTaskState(currentTaskDetail == null ? null : currentTaskDetail.getState());
        summary.setBlockingSummary(blockingSummary);
        summary.setResultSummary(resultSummary);
        summary.setNeedsAttention(resolveNeedsAttention(sceneStatus, blockingSummary));
        summary.setUpdatedAt(resolveUpdatedAt(context, resultSummary, null, null));
        summary.setActionRecommendation(buildActionRecommendation(sceneStatus, blockingSummary, resultSummary, currentTaskDetail));
        return summary;
    }

    private SessionProjectionDTO loadSessionProjection(Long userId, AnalysisSession session) {
        if (session == null) {
            return null;
        }
        AnalysisSessionResponse response = sessionService.getSession(userId, session.getSessionId());
        return toSessionProjection(response);
    }

    private SessionProjectionDTO toSessionProjection(AnalysisSessionResponse response) {
        SessionProjectionDTO projection = new SessionProjectionDTO();
        projection.setSessionId(response.getSessionId());
        projection.setTitle(response.getTitle());
        projection.setUserGoal(response.getUserGoal());
        projection.setStatus(response.getStatus());
        projection.setSceneId(response.getSceneId());
        projection.setCurrentTaskId(response.getCurrentTaskId());
        projection.setLatestResultBundleId(response.getLatestResultBundleId());
        projection.setCreatedAt(response.getCreatedAt());
        projection.setUpdatedAt(response.getUpdatedAt());
        projection.setCurrentRequiredUserAction(response.getCurrentRequiredUserAction());
        projection.setSessionContextSummary(response.getSessionContextSummary());
        projection.setCurrentTaskSummary(response.getCurrentTaskSummary());
        projection.setLatestResultSummary(response.getLatestResultSummary());
        projection.setProgressProjection(response.getProgressProjection());
        projection.setWaitingProjection(response.getWaitingProjection());
        return projection;
    }

    private SessionMessagesResponseDTO toSceneMessagesResponse(SessionMessagesResponse response) {
        SessionMessagesResponseDTO dto = new SessionMessagesResponseDTO();
        dto.setSessionId(response.getSessionId());
        dto.setNextCursor(null);
        for (SessionMessageDto item : response.getItems()) {
            dto.getItems().addAll(toSceneMessages(item));
        }
        return dto;
    }

    private List<SessionMessageDTO> toSceneMessages(SessionMessageDto source) {
        List<SessionMessageDTO> mapped = new ArrayList<>();
        String rawType = safe(source.getMessageType());
        switch (rawType) {
            case "user_goal" ->
                    mapped.add(buildSceneMessage(source, normalizeSceneMessageRole(source.getRole()), "conversation", "main_conversation", "goal_parse", normalizeConversationContent(source), false, null));
            case "user_reply", "user_clarification_answer" ->
                    mapped.add(buildSceneMessage(source, "user", "conversation", "main_conversation", "follow_up", normalizeConversationContent(source), false, null));
            case "assistant_understanding" -> {
                mapped.add(buildSceneMessage(source, "assistant", "understanding", "main_conversation", "goal_parse_skill_route", buildUnderstandingContent(source), false, null));
                mapped.add(buildTraceMessage(source, "goal_parse_skill_route"));
            }
            case "progress_update" -> {
                mapped.add(buildSceneMessage(source, "assistant", "governance_note", "main_conversation", "validate_submit", buildPreparedAndSubmittedContent(source), false, null));
                mapped.add(buildTraceMessage(source, "execution"));
            }
            case "result_summary" -> {
                mapped.add(buildSceneMessage(source, "system", "result_notice", "main_conversation", "result_ready", buildResultReadyContent(source), false, null));
                mapped.add(buildSceneMessage(source, "assistant", "result_notice", "main_conversation", "skill_grounded_explanation", buildExplanationContent(source), true, null));
                mapped.add(buildTraceMessage(source, "result_extract_summarize"));
            }
            case "next_step_guidance" ->
                    mapped.add(buildSceneMessage(source, "assistant", "conversation", "main_conversation", "final_response", buildFollowUpInvitationContent(source), false, null));
            case "waiting_notice", "clarification_request", "missing_input_request" -> {
                mapped.add(buildSceneMessage(source, "assistant", "waiting_notice", "main_conversation", "waiting_user", normalizeWaitingContent(source), false, null));
                mapped.add(buildTraceMessage(source, "waiting_user"));
            }
            case "resume_notice", "upload_ack" -> {
                mapped.add(buildSceneMessage(source, "assistant", "governance_note", "main_conversation", "validate_submit", normalizeGovernanceContent(source), false, null));
                mapped.add(buildTraceMessage(source, "validate_submit"));
            }
            case "failure_explanation" -> {
                mapped.add(buildSceneMessage(source, "assistant", "governance_note", "main_conversation", "skill_grounded_explanation", normalizeFailureContent(source), false, null));
                mapped.add(buildTraceMessage(source, "skill_grounded_explanation"));
            }
            case "system_note" -> mapped.add(buildTraceMessage(source, "execution"));
            default ->
                    mapped.add(buildSceneMessage(source, normalizeSceneMessageRole(source.getRole()), "conversation", "main_conversation", null, normalizeConversationContent(source), false, null));
        }
        return mapped;
    }

    private SessionMessageDTO buildSceneMessage(
            SessionMessageDto source,
            String role,
            String messageType,
            String surface,
            String stage,
            Map<String, Object> content,
            boolean primaryExplanation,
            Map<String, Object> developerTrace
    ) {
        SessionMessageDTO dto = new SessionMessageDTO();
        dto.setMessageId(source.getMessageId() + ("developer_trace".equals(surface) ? "__trace" : "__" + nonBlank(stage, messageType)));
        dto.setSessionId(source.getSessionId());
        dto.setRole(role);
        dto.setMessageType(messageType);
        dto.setSurface(surface);
        dto.setStage(stage);
        dto.setContent(content);
        dto.setCreatedAt(source.getCreatedAt());
        dto.setRelatedTaskId(source.getTaskId());
        dto.setRelatedResultBundleId(source.getResultBundleId());
        dto.setRelatedWaitingReasonType(extractWaitingReasonType(source));
        dto.setPrimaryExplanation(primaryExplanation);
        dto.setDeveloperTrace(developerTrace);
        dto.setAttachmentRefs(source.getAttachmentRefs());
        dto.setActionSchema(source.getActionSchema());
        dto.setRelatedObjectRefs(source.getRelatedObjectRefs());
        return dto;
    }

    private SessionMessageDTO buildTraceMessage(SessionMessageDto source, String stage) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("text", "Developer trace entry recorded for " + nonBlank(stage, "internal orchestration") + ".");
        return buildSceneMessage(
                source,
                "system",
                "governance_note",
                "developer_trace",
                stage,
                content,
                false,
                buildDeveloperTracePayload(source, stage)
        );
    }

    private Map<String, Object> normalizeConversationContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        if (!hasText(asString(content.get("text")))) {
            content.put("text", "A session message was recorded.");
        }
        return content;
    }

    private Map<String, Object> buildUnderstandingContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        String text = asString(content.get("text"));
        if (hasText(text) && text.startsWith("I understood this as ")) {
            String remainder = trimTrailingPeriod(text.substring("I understood this as ".length()));
            remainder = remainder.replace("request focused on", "with focus on");
            remainder = remainder.replace("request for", "to address");
            remainder = remainder.replaceFirst("^(an|a)\\s+", "");
            content.put("text", "I'll organize the analysis around " + remainder + ".");
        } else if (!hasText(text)) {
            content.put("text", "I've organized the analysis around the requested ecological outcome and where intervention is most likely to matter.");
        }
        return content;
    }

    private Map<String, Object> buildPreparedAndSubmittedContent(SessionMessageDto source) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("text", "The analysis has been prepared, validated, and submitted under governed execution. I'll update you when the result is ready.");
        if (source.getContent() != null && hasText(asString(source.getContent().get("estimated_next_milestone")))) {
            content.put("next_milestone", asString(source.getContent().get("estimated_next_milestone")));
        }
        return content;
    }

    private Map<String, Object> buildResultReadyContent(SessionMessageDto source) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("text", deriveResultReadyText(source));
        return content;
    }

    private Map<String, Object> buildExplanationContent(SessionMessageDto source) {
        Map<String, Object> content = normalizeResultContent(source);
        String text = asString(content.get("text"));
        if (hasText(text) && text.startsWith("A governed result is ready. ")) {
            content.put("text", text.substring("A governed result is ready. ".length()).trim());
        }
        return content;
    }

    private Map<String, Object> buildFollowUpInvitationContent(SessionMessageDto source) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("text", "If you want, I can continue by comparing intervention types, narrowing this to one district or corridor, or turning the priority zones into an action shortlist.");
        if (source.getContent() != null && hasText(asString(source.getContent().get("text")))) {
            content.put("source_text", source.getContent().get("text"));
        }
        return content;
    }

    private Map<String, Object> normalizeWaitingContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        if (!hasText(asString(content.get("text")))) {
            content.put(
                    "text",
                    nonBlank(
                            asString(content.get("user_facing_phrasing")),
                            "The session is waiting for additional user input before governed execution can continue."
                    )
            );
        }
        return content;
    }

    private Map<String, Object> normalizeGovernanceContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        if (!hasText(asString(content.get("text")))) {
            content.put("text", "SAGE recorded a governance update for this session.");
        }
        return content;
    }

    private Map<String, Object> normalizeFailureContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        if (!hasText(asString(content.get("text")))) {
            content.put("text", nonBlank(asString(content.get("failure_message")), "The governed analysis failed."));
        }
        return content;
    }

    private Map<String, Object> normalizeResultContent(SessionMessageDto source) {
        Map<String, Object> content = copyContent(source);
        if (!hasText(asString(content.get("text")))) {
            content.put(
                    "text",
                    nonBlank(
                            asString(content.get("summary")),
                            "A governed result is ready."
                    )
            );
        }
        return content;
    }

    private Map<String, Object> copyContent(SessionMessageDto source) {
        return source.getContent() == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(source.getContent());
    }

    private String deriveResultReadyText(SessionMessageDto source) {
        Map<String, Object> content = source.getContent();
        String combined = nonBlank(
                content == null ? null : asString(content.get("summary")),
                content == null ? null : asString(content.get("text"))
        );
        if (hasText(combined) && combined.toLowerCase(Locale.ROOT).contains("cooling priority")) {
            return "The result is ready. Cooling priority zones have been identified and ranked.";
        }
        return "The result is ready. The governed analysis output is available.";
    }

    private Map<String, Object> buildDeveloperTracePayload(SessionMessageDto source, String stage) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("raw_message_type", source.getMessageType());
        payload.put("raw_role", source.getRole());
        payload.put("task_id", source.getTaskId());
        payload.put("result_bundle_id", source.getResultBundleId());
        payload.put("content", source.getContent());
        payload.put("attachment_refs", source.getAttachmentRefs());
        payload.put("action_schema", source.getActionSchema());
        payload.put("related_object_refs", source.getRelatedObjectRefs());
        return payload;
    }

    private String normalizeSceneMessageRole(String role) {
        return switch (safe(role)) {
            case "assistant" -> "assistant";
            case "system" -> "system";
            default -> "user";
        };
    }

    private SessionMessageDTO findAcceptedMessage(SessionMessagesResponse response, String clientRequestId) {
        List<SessionMessageDto> items = response.getItems();
        for (int index = items.size() - 1; index >= 0; index -= 1) {
            SessionMessageDto candidate = items.get(index);
            if (!"user".equals(candidate.getRole()) || candidate.getContent() == null) {
                continue;
            }
            if (Objects.equals(clientRequestId, asString(candidate.getContent().get("client_request_id")))) {
                String stage = "user_goal".equals(safe(candidate.getMessageType())) ? "goal_parse" : "follow_up";
                return buildSceneMessage(candidate, "user", "conversation", "main_conversation", stage, normalizeConversationContent(candidate), false, null);
            }
        }
        return null;
    }

    private String extractWaitingReasonType(SessionMessageDto source) {
        if (source.getContent() == null) {
            return null;
        }
        return nonBlank(
                asString(source.getContent().get("waiting_reason_type")),
                asString(source.getContent().get("why_blocked"))
        );
    }

    private String trimTrailingPeriod(String value) {
        if (!hasText(value)) {
            return value;
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    private AnalysisSession requireCurrentSession(SceneProjectionContext context) {
        if (context.getCurrentSession() == null) {
            throw new ResponseStatusException(NOT_FOUND, "Scene has no current session");
        }
        return context.getCurrentSession();
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private TaskDetailResponse loadTaskDetail(Long userId, TaskState task) {
        return task == null ? null : taskService.getTask(task.getTaskId(), userId);
    }

    private TaskAuditResponse loadTaskAudit(Long userId, TaskState task) {
        return task == null ? null : taskService.getTaskAudit(task.getTaskId(), userId);
    }

    private TaskEventsResponse loadTaskEvents(Long userId, TaskState task) {
        return task == null ? null : taskService.getEvents(task.getTaskId(), userId);
    }

    private List<TaskDetailResponse> loadReadyResultTaskDetails(Long userId, SceneProjectionContext context, TaskDetailResponse currentTaskDetail) {
        List<TaskDetailResponse> readyCandidates = new ArrayList<>();
        for (TaskState task : context.getTasks()) {
            if (!hasText(task.getLatestResultBundleId()) || !TaskStatus.SUCCEEDED.name().equals(task.getCurrentState())) {
                continue;
            }
            TaskDetailResponse detail;
            if (currentTaskDetail != null && Objects.equals(currentTaskDetail.getTaskId(), task.getTaskId())) {
                detail = currentTaskDetail;
            } else {
                detail = taskService.getTask(task.getTaskId(), userId);
            }
            if (isReadyResultDetail(detail)) {
                readyCandidates.add(detail);
            }
        }
        return readyCandidates;
    }

    private SceneBlockingSummaryDTO buildBlockingSummary(TaskDetailResponse taskDetail) {
        SceneBlockingSummaryDTO summary = new SceneBlockingSummaryDTO();
        summary.setBlockingType("NONE");
        summary.setRequiredUserActionCount(0);
        summary.setMissingSlotCount(0);
        summary.setInvalidBindingCount(0);

        if (taskDetail == null) {
            return summary;
        }

        if (TaskStatus.WAITING_USER.name().equals(taskDetail.getState()) && taskDetail.getWaitingContext() != null) {
            summary.setBlockingType("WAITING_USER");
            summary.setWaitingReasonType(taskDetail.getWaitingContext().getWaitingReasonType());
            summary.setUserFacingReason(resolveWaitingReason(taskDetail));
            summary.setCanResume(taskDetail.getWaitingContext().getCanResume());
            summary.setRequiredUserActionCount(sizeOf(taskDetail.getWaitingContext().getRequiredUserActions()));
            summary.setMissingSlotCount(sizeOf(taskDetail.getWaitingContext().getMissingSlots()));
            summary.setInvalidBindingCount(sizeOf(taskDetail.getWaitingContext().getInvalidBindings()));
            return summary;
        }

        if (TaskStatus.STATE_CORRUPTED.name().equals(taskDetail.getState())) {
            summary.setBlockingType("STATE_CORRUPTED");
            summary.setUserFacingReason(taskDetail.getCorruptionState() == null ? "The task state is corrupted and requires governance review." : taskDetail.getCorruptionState().getReason());
            return summary;
        }

        if (TaskStatus.FAILED.name().equals(taskDetail.getState())) {
            boolean repairable = (taskDetail.getRepairProposal() != null && Boolean.TRUE.equals(taskDetail.getRepairProposal().getAvailable()))
                    || (taskDetail.getLastFailureSummary() != null && Boolean.TRUE.equals(taskDetail.getLastFailureSummary().getRepairable()));
            summary.setBlockingType(repairable ? "FAILED_RECOVERABLE" : "FAILED_FATAL");
            if (taskDetail.getRepairProposal() != null && hasText(taskDetail.getRepairProposal().getUserFacingReason())) {
                summary.setUserFacingReason(taskDetail.getRepairProposal().getUserFacingReason());
            } else if (taskDetail.getLastFailureSummary() != null) {
                summary.setUserFacingReason(taskDetail.getLastFailureSummary().getFailureMessage());
            } else {
                summary.setUserFacingReason("The task failed during governed execution.");
            }
        }

        return summary;
    }

    private SceneResultSummaryDTO buildResultSummary(List<TaskDetailResponse> readyResultTaskDetails) {
        SceneResultSummaryDTO summary = new SceneResultSummaryDTO();
        LinkedHashSet<String> readyResultIds = new LinkedHashSet<>();
        TaskDetailResponse latestReadyDetail = null;

        // Scene-facing ready result is stricter than "latest_result_bundle_id exists".
        // A task contributes to ready scene results only when it is SUCCEEDED,
        // has a non-blank latest_result_bundle_id, and exposes either a result bundle
        // summary or an available final explanation summary.
        for (TaskDetailResponse detail : readyResultTaskDetails) {
            if (hasText(detail.getLatestResultBundleId())) {
                readyResultIds.add(detail.getLatestResultBundleId());
                if (latestReadyDetail == null || newerReadyDetail(detail, latestReadyDetail)) {
                    latestReadyDetail = detail;
                }
            }
        }

        summary.setReadyResultCount(readyResultIds.size());
        summary.setHasReadyResult(!readyResultIds.isEmpty());
        summary.setLatestResultBundleId(latestReadyDetail == null ? null : latestReadyDetail.getLatestResultBundleId());
        summary.setFinalExplanationAvailable(false);

        if (latestReadyDetail != null) {
            if (latestReadyDetail.getResultBundleSummary() != null) {
                summary.setLatestResultCreatedAt(latestReadyDetail.getResultBundleSummary().getCreatedAt());
                summary.setResultSummaryText(latestReadyDetail.getResultBundleSummary().getSummary());
            }
            if (!hasText(summary.getLatestResultCreatedAt()) && latestReadyDetail.getFinalExplanationSummary() != null) {
                summary.setLatestResultCreatedAt(latestReadyDetail.getFinalExplanationSummary().getGeneratedAt());
            }
            summary.setFinalExplanationAvailable(
                    latestReadyDetail.getFinalExplanationSummary() != null
                            && Boolean.TRUE.equals(latestReadyDetail.getFinalExplanationSummary().getAvailable())
            );
            if (!hasText(summary.getResultSummaryText()) && latestReadyDetail.getFinalExplanationSummary() != null) {
                summary.setResultSummaryText(latestReadyDetail.getFinalExplanationSummary().getTitle());
            }
            if (!hasText(summary.getLatestResultBundleId()) && hasText(latestReadyDetail.getLatestResultBundleId())) {
                summary.setLatestResultBundleId(latestReadyDetail.getLatestResultBundleId());
            }
        }

        return summary;
    }

    private SceneAuditSummaryDTO buildAuditSummary(TaskAuditResponse taskAudit) {
        SceneAuditSummaryDTO summary = new SceneAuditSummaryDTO();
        if (taskAudit == null || taskAudit.getItems().isEmpty()) {
            return summary;
        }
        TaskAuditResponse.AuditItem latest = taskAudit.getItems().stream()
                .min(Comparator.comparing(TaskAuditResponse.AuditItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .orElse(null);
        if (latest == null) {
            return summary;
        }
        summary.setLatestAuditAt(latest.getCreatedAt());
        summary.setLatestAuditActionType(latest.getActionType());
        summary.setLatestAuditActionResult(latest.getActionResult());
        return summary;
    }

    private SceneActionRecommendationDTO buildActionRecommendation(
            String sceneStatus,
            SceneBlockingSummaryDTO blockingSummary,
            SceneResultSummaryDTO resultSummary,
            TaskDetailResponse currentTaskDetail
    ) {
        SceneActionRecommendationDTO recommendation = new SceneActionRecommendationDTO();
        switch (safe(sceneStatus)) {
            case "WAITING_USER" -> {
                recommendation.setRecommendedEntry("clarify");
                recommendation.setRecommendedAction(resolveWaitingActionLabel(currentTaskDetail, blockingSummary));
                recommendation.setRecommendationReason(nonBlank(blockingSummary.getUserFacingReason(), "Current governed task is waiting for user action."));
            }
            case "RUNNING" -> {
                recommendation.setRecommendedEntry("governance");
                recommendation.setRecommendedAction("Track governed execution");
                recommendation.setRecommendationReason("A governed task is currently progressing for this scene.");
            }
            case "READY_RESULT" -> {
                recommendation.setRecommendedEntry("results");
                recommendation.setRecommendedAction("Review latest result");
                recommendation.setRecommendationReason(nonBlank(resultSummary.getResultSummaryText(), "A governed result bundle is available for review."));
            }
            case "FAILED" -> {
                recommendation.setRecommendedEntry("governance");
                recommendation.setRecommendedAction("Inspect failure and repair path");
                recommendation.setRecommendationReason(nonBlank(blockingSummary.getUserFacingReason(), "The governed task requires failure review."));
            }
            case "ARCHIVED" -> {
                recommendation.setRecommendedEntry("overview");
                recommendation.setRecommendedAction("Review scene history");
                recommendation.setRecommendationReason("The latest scene-linked session is cancelled.");
            }
            case "DRAFT" -> {
                recommendation.setRecommendedEntry("session");
                recommendation.setRecommendedAction("Continue scene session");
                recommendation.setRecommendationReason("The scene does not yet have an active governed task.");
            }
            default -> {
                recommendation.setRecommendedEntry("overview");
                recommendation.setRecommendedAction("Open scene overview");
                recommendation.setRecommendationReason("Scene context is available for review.");
            }
        }
        return recommendation;
    }

    private String resolveSceneStatus(SceneProjectionContext context, SceneResultSummaryDTO resultSummary) {
        TaskState currentTask = context.getCurrentTask();
        AnalysisSession currentSession = context.getCurrentSession();

        if (currentTask == null) {
            if (currentSession == null) {
                return "DRAFT";
            }
            if ("CANCELLED".equals(currentSession.getStatus())) {
                return "ARCHIVED";
            }
            if (context.getTasks().isEmpty()) {
                return "DRAFT";
            }
            return switch (safe(currentSession.getStatus())) {
                case "WAITING_USER" -> "WAITING_USER";
                case "RUNNING" -> "RUNNING";
                case "READY_RESULT" -> "READY_RESULT";
                case "FAILED" -> "FAILED";
                default -> "ACTIVE";
            };
        }

        // The selected current task is the authority-bearing task for scene-facing status in v1.
        // Multi-task scenes may contain older succeeded or failed tasks, but current scene entry
        // state follows the selected current task rather than attempting to merge all task states.
        // CANCELLED intentionally maps to ARCHIVED because the scene-facing contract does not expose
        // CANCELLED as a top-level scene status in v1.x.
        return switch (safe(currentTask.getCurrentState())) {
            case "WAITING_USER" -> "WAITING_USER";
            case "RESUMING", "RUNNING", "RESULT_PROCESSING", "ARTIFACT_PROMOTING", "QUEUED", "VALIDATING", "PLANNING", "COGNIZING", "CREATED" -> "RUNNING";
            case "FAILED", "STATE_CORRUPTED" -> "FAILED";
            case "SUCCEEDED" -> Boolean.TRUE.equals(resultSummary.getHasReadyResult()) ? "READY_RESULT" : "ACTIVE";
            case "CANCELLED" -> "ARCHIVED";
            default -> "ACTIVE";
        };
    }

    private boolean resolveNeedsAttention(String sceneStatus, SceneBlockingSummaryDTO blockingSummary) {
        if (blockingSummary != null && !"NONE".equals(blockingSummary.getBlockingType())) {
            return true;
        }
        return "FAILED".equals(sceneStatus);
    }

    private String resolveUserGoalSummary(SceneProjectionContext context) {
        if (context.getCurrentSession() != null && hasText(context.getCurrentSession().getUserGoal())) {
            return context.getCurrentSession().getUserGoal();
        }
        return context.getSessions().stream()
                .map(AnalysisSession::getUserGoal)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String resolveCreatedAt(SceneProjectionContext context) {
        return context.getSessions().stream()
                .map(AnalysisSession::getCreatedAt)
                .filter(Objects::nonNull)
                .min(OffsetDateTime::compareTo)
                .map(OffsetDateTime::toString)
                .orElse(null);
    }

    private String resolveUpdatedAt(
            SceneProjectionContext context,
            SceneResultSummaryDTO resultSummary,
            TaskAuditResponse taskAudit,
            TaskEventsResponse taskEvents
    ) {
        OffsetDateTime latest = null;
        latest = max(latest, context.getCurrentSession() == null ? null : context.getCurrentSession().getUpdatedAt());
        latest = max(latest, context.getCurrentTask() == null ? null : context.getCurrentTask().getUpdatedAt());
        latest = max(latest, parseTime(resultSummary == null ? null : resultSummary.getLatestResultCreatedAt()));
        if (taskAudit != null) {
            for (TaskAuditResponse.AuditItem item : taskAudit.getItems()) {
                latest = max(latest, parseTime(item.getCreatedAt()));
            }
        }
        if (taskEvents != null) {
            for (TaskEventsResponse.EventItem item : taskEvents.getItems()) {
                latest = max(latest, parseTime(item.getCreatedAt()));
            }
        }
        return latest == null ? null : latest.toString();
    }

    private Comparator<SceneSummaryDTO> sceneSummaryComparator() {
        return Comparator.comparingInt((SceneSummaryDTO item) -> scenePriorityRank(item.getSceneStatus()))
                .thenComparing(item -> parseTime(item.getUpdatedAt()), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SceneSummaryDTO::getSceneId, Comparator.nullsLast(String::compareTo));
    }

    private int scenePriorityRank(String sceneStatus) {
        return switch (safe(sceneStatus)) {
            case "WAITING_USER" -> 0;
            case "RUNNING" -> 1;
            case "READY_RESULT" -> 2;
            case "FAILED" -> 3;
            case "ARCHIVED" -> 4;
            case "ACTIVE" -> 5;
            case "DRAFT" -> 6;
            default -> 7;
        };
    }

    private boolean matchesStatus(SceneSummaryDTO item, String status) {
        if (!hasText(status)) {
            return true;
        }
        return safe(item.getSceneStatus()).equalsIgnoreCase(status.trim());
    }

    private boolean matchesQuery(SceneSummaryDTO item, String query) {
        if (!hasText(query)) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return contains(item.getSceneId(), needle)
                || contains(item.getSceneName(), needle)
                || contains(item.getUserGoalSummary(), needle)
                || contains(item.getTaskState(), needle)
                || contains(item.getUpdatedAt(), needle)
                || contains(item.getActionRecommendation() == null ? null : item.getActionRecommendation().getRecommendedAction(), needle);
    }

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String resolveWaitingReason(TaskDetailResponse taskDetail) {
        if (taskDetail.getRepairProposal() != null && hasText(taskDetail.getRepairProposal().getUserFacingReason())) {
            return taskDetail.getRepairProposal().getUserFacingReason();
        }
        if (taskDetail.getWaitingContext() == null) {
            return "The system is blocked on additional input before governed execution can continue.";
        }
        if (sizeOf(taskDetail.getWaitingContext().getMissingSlots()) > 0) {
            return "The system is waiting for required upload or binding confirmation.";
        }
        if (sizeOf(taskDetail.getWaitingContext().getInvalidBindings()) > 0) {
            return "The system is waiting for invalid bindings to be corrected.";
        }
        return "The system is blocked on additional input before governed execution can continue.";
    }

    private String resolveWaitingActionLabel(TaskDetailResponse taskDetail, SceneBlockingSummaryDTO blockingSummary) {
        if (taskDetail != null && taskDetail.getWaitingContext() != null && taskDetail.getWaitingContext().getRequiredUserActions() != null) {
            TaskDetailResponse.RequiredUserAction firstRequired = taskDetail.getWaitingContext().getRequiredUserActions().stream()
                    .filter(action -> Boolean.TRUE.equals(action.getRequired()))
                    .findFirst()
                    .orElse(null);
            if (firstRequired != null && hasText(firstRequired.getLabel())) {
                return firstRequired.getLabel();
            }
        }
        if (blockingSummary != null && "WAITING_USER".equals(blockingSummary.getBlockingType())) {
            return "Complete required user action";
        }
        return "Open scene";
    }

    private boolean isReadyResultDetail(TaskDetailResponse detail) {
        if (detail == null) {
            return false;
        }
        if (!TaskStatus.SUCCEEDED.name().equals(detail.getState()) || !hasText(detail.getLatestResultBundleId())) {
            return false;
        }
        return detail.getResultBundleSummary() != null
                || (detail.getFinalExplanationSummary() != null && Boolean.TRUE.equals(detail.getFinalExplanationSummary().getAvailable()));
    }

    private boolean newerReadyDetail(TaskDetailResponse left, TaskDetailResponse right) {
        OffsetDateTime leftTime = resolveReadyResultTime(left);
        OffsetDateTime rightTime = resolveReadyResultTime(right);
        if (leftTime != null && rightTime != null && !leftTime.equals(rightTime)) {
            return leftTime.isAfter(rightTime);
        }
        if (leftTime != null && rightTime == null) {
            return true;
        }
        if (leftTime == null && rightTime != null) {
            return false;
        }
        return safe(left.getLatestResultBundleId()).compareTo(safe(right.getLatestResultBundleId())) < 0;
    }

    private OffsetDateTime resolveReadyResultTime(TaskDetailResponse detail) {
        if (detail == null) {
            return null;
        }
        OffsetDateTime resultBundleTime = detail.getResultBundleSummary() == null ? null : parseTime(detail.getResultBundleSummary().getCreatedAt());
        OffsetDateTime finalExplanationTime = detail.getFinalExplanationSummary() == null ? null : parseTime(detail.getFinalExplanationSummary().getGeneratedAt());
        return max(resultBundleTime, finalExplanationTime);
    }

    private OffsetDateTime max(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private OffsetDateTime parseTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nonBlank(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

