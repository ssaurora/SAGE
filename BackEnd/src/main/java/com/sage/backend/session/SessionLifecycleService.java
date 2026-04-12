package com.sage.backend.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.common.SessionIdGenerator;
import com.sage.backend.common.SessionMessageIdGenerator;
import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.SessionMessageMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
import com.sage.backend.model.SessionMessageType;
import com.sage.backend.model.SessionStatus;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SessionLifecycleService {

    private final AnalysisSessionMapper analysisSessionMapper;
    private final SessionMessageMapper sessionMessageMapper;
    private final ObjectMapper objectMapper;

    public SessionLifecycleService(
            AnalysisSessionMapper analysisSessionMapper,
            SessionMessageMapper sessionMessageMapper,
            ObjectMapper objectMapper
    ) {
        this.analysisSessionMapper = analysisSessionMapper;
        this.sessionMessageMapper = sessionMessageMapper;
        this.objectMapper = objectMapper;
    }

    public AnalysisSession createSessionShell(Long userId, String userGoal, String title, String sceneId) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(SessionIdGenerator.generate());
        session.setUserId(userId);
        session.setTitle(resolveTitle(title, userGoal));
        session.setUserGoal(userGoal);
        session.setStatus(SessionStatus.RUNNING.name());
        session.setSceneId(sceneId);
        session.setCurrentTaskId(null);
        session.setLatestResultBundleId(null);
        session.setCurrentRequiredUserActionJson(null);
        session.setSessionSummaryJson(writeJson(buildSessionSummaryPayload(null, SessionStatus.RUNNING.name(), userGoal, null)));
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        analysisSessionMapper.insert(session);
        return session;
    }

    public void bindTaskToSession(String sessionId, String taskId, String userGoal) {
        analysisSessionMapper.updateStateAndPointers(
                sessionId,
                taskId,
                null,
                SessionStatus.RUNNING.name(),
                null,
                writeJson(buildSessionSummaryPayload(taskId, SessionStatus.RUNNING.name(), userGoal, null))
        );
    }

    public void syncFromTask(TaskState taskState) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        String sessionStatus = mapSessionStatus(taskState.getCurrentState());
        analysisSessionMapper.updateStateAndPointers(
                taskState.getSessionId(),
                taskState.getTaskId(),
                taskState.getLatestResultBundleId(),
                sessionStatus,
                SessionStatus.WAITING_USER.name().equals(sessionStatus) ? writeJson(buildCurrentRequiredUserAction(taskState)) : null,
                writeJson(buildSessionSummaryPayload(
                        taskState.getTaskId(),
                        sessionStatus,
                        taskState.getUserQuery(),
                        taskState.getLatestResultBundleId()
                ))
        );
    }

    public void recordUserGoal(String sessionId, String taskId, String userGoal) {
        appendMessage(sessionId, taskId, null, "user", SessionMessageType.user_goal.name(), buildTextPayload(userGoal, null), null, null, buildTaskRef(taskId));
    }

    public void recordAssistantUnderstanding(TaskState taskState) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        ObjectNode content = objectMapper.createObjectNode();
        content.put("text", buildAssistantUnderstandingText(taskState));
        JsonNode goalParse = readJson(taskState.getGoalParseJson());
        JsonNode skillRoute = readJson(taskState.getSkillRouteJson());
        if (goalParse != null) {
            content.put("analysis_kind", goalParse.path("analysis_kind").asText(null));
            content.put("goal_type", goalParse.path("goal_type").asText(null));
        }
        if (skillRoute != null) {
            content.put("route_mode", skillRoute.path("route_mode").asText(null));
            content.put("capability_key", skillRoute.path("capability_key").asText(null));
            content.put("selected_template", skillRoute.path("selected_template").asText(null));
        }
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), null, "assistant", SessionMessageType.assistant_understanding.name(), content, null, null, buildTaskRef(taskState.getTaskId()));
    }

    public void recordProgressUpdate(TaskState taskState, String phaseLabel, String systemAction, String latestProgressNote, String estimatedNextMilestone) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        ObjectNode content = objectMapper.createObjectNode();
        content.put("current_phase_label", phaseLabel);
        content.put("current_system_action", systemAction);
        content.put("latest_progress_note", latestProgressNote);
        content.put("estimated_next_milestone", estimatedNextMilestone);
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.progress_update.name(), content, null, null, buildTaskRef(taskState.getTaskId()));
    }

    public void recordWaiting(TaskState taskState) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        String waitingReasonType = safeString(taskState.getWaitingReasonType());
        String messageType = waitingReasonType.startsWith("CLARIFY")
                ? SessionMessageType.clarification_request.name()
                : SessionMessageType.waiting_notice.name();
        JsonNode content = readJson(taskState.getWaitingContextJson());
        appendMessage(
                taskState.getSessionId(),
                taskState.getTaskId(),
                taskState.getLatestResultBundleId(),
                "assistant",
                messageType,
                content == null ? buildTextPayload("Waiting for user input.", null) : content,
                null,
                null,
                buildTaskRef(taskState.getTaskId())
        );
        syncFromTask(taskState);
    }

    public void recordResumeNotice(TaskState taskState, String resumeRequestId, Integer attemptNo) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        ObjectNode content = objectMapper.createObjectNode();
        content.put("text", "The session accepted your input and resumed governed validation.");
        content.put("resume_request_id", resumeRequestId);
        if (attemptNo != null) {
            content.put("attempt_no", attemptNo);
        }
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.resume_notice.name(), content, null, null, buildTaskRef(taskState.getTaskId()));
    }

    public void recordUploadAck(TaskState taskState, TaskAttachment attachment) {
        if (taskState == null || attachment == null || taskState.getSessionId() == null) {
            return;
        }
        ObjectNode content = objectMapper.createObjectNode();
        content.put("text", "Attachment received and added to the current task inventory.");
        content.put("attachment_id", attachment.getId());
        content.put("file_name", attachment.getFileName());
        content.put("logical_slot", attachment.getLogicalSlot());
        content.put("assignment_status", attachment.getAssignmentStatus());
        content.put("size_bytes", attachment.getSizeBytes());
        ObjectNode refs = objectMapper.createObjectNode();
        refs.put("attachment_id", attachment.getId());
        refs.put("task_id", attachment.getTaskId());
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.upload_ack.name(), content, refs, null, buildTaskRef(taskState.getTaskId()));
    }

    public void recordResultSummary(TaskState taskState) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        JsonNode content = readJson(taskState.getFinalExplanationSummaryJson());
        if (content == null) {
            content = readJson(taskState.getResultBundleSummaryJson());
        }
        if (content == null) {
            content = buildTextPayload("The task completed and a governed result is ready.", null);
        }
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.result_summary.name(), content, null, null, buildResultRef(taskState.getTaskId(), taskState.getLatestResultBundleId()));
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.next_step_guidance.name(), buildTextPayload("Review the result, inspect governance details, or continue the session with a follow-up request.", null), null, null, buildResultRef(taskState.getTaskId(), taskState.getLatestResultBundleId()));
        syncFromTask(taskState);
    }

    public void recordFailureExplanation(TaskState taskState) {
        if (taskState == null || taskState.getSessionId() == null) {
            return;
        }
        JsonNode content = readJson(taskState.getLastFailureSummaryJson());
        if (content == null) {
            content = buildTextPayload("The task failed during governed execution.", null);
        }
        appendMessage(taskState.getSessionId(), taskState.getTaskId(), taskState.getLatestResultBundleId(), "assistant", SessionMessageType.failure_explanation.name(), content, null, null, buildTaskRef(taskState.getTaskId()));
        syncFromTask(taskState);
    }

    public void recordSystemNote(String sessionId, String taskId, String text) {
        appendMessage(sessionId, taskId, null, "system", SessionMessageType.system_note.name(), buildTextPayload(text, null), null, null, buildTaskRef(taskId));
    }

    public void recordUserReply(String sessionId, String taskId, String content, String clientRequestId, List<String> attachmentIds) {
        appendMessage(sessionId, taskId, null, "user", SessionMessageType.user_reply.name(), buildUserPayload(content, clientRequestId, attachmentIds), null, null, buildTaskRef(taskId));
    }

    public void recordUserClarification(String sessionId, String taskId, String content, String clientRequestId, List<String> attachmentIds) {
        appendMessage(sessionId, taskId, null, "user", SessionMessageType.user_clarification_answer.name(), buildUserPayload(content, clientRequestId, attachmentIds), null, null, buildTaskRef(taskId));
    }

    private ObjectNode buildUserPayload(String content, String clientRequestId, List<String> attachmentIds) {
        ObjectNode payload = buildTextPayload(content, clientRequestId);
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            ArrayNode ids = payload.putArray("attachment_ids");
            for (String attachmentId : attachmentIds) {
                ids.add(attachmentId);
            }
        }
        return payload;
    }

    private void appendMessage(
            String sessionId,
            String taskId,
            String resultBundleId,
            String role,
            String messageType,
            JsonNode content,
            JsonNode attachmentRefs,
            JsonNode actionSchema,
            JsonNode relatedObjectRefs
    ) {
        SessionMessage message = new SessionMessage();
        message.setMessageId(SessionMessageIdGenerator.generate());
        message.setSessionId(sessionId);
        message.setTaskId(taskId);
        message.setResultBundleId(resultBundleId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContentJson(writeJson(content));
        message.setAttachmentRefsJson(writeJson(attachmentRefs));
        message.setActionSchemaJson(writeJson(actionSchema));
        message.setRelatedObjectRefsJson(writeJson(relatedObjectRefs));
        message.setCreatedAt(OffsetDateTime.now());
        sessionMessageMapper.insert(message);
    }

    private JsonNode buildCurrentRequiredUserAction(TaskState taskState) {
        JsonNode waitingRoot = readJson(taskState.getWaitingContextJson());
        if (waitingRoot != null) {
            return waitingRoot;
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("waiting_reason_type", taskState.getWaitingReasonType());
        root.put("resume_hint", "Provide the required clarification or upload, then resume the task.");
        return root;
    }

    private JsonNode buildSessionSummaryPayload(String taskId, String status, String userGoal, String latestResultBundleId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task_id", taskId);
        root.put("status", status);
        root.put("user_goal", userGoal);
        root.put("latest_result_bundle_id", latestResultBundleId);
        return root;
    }

    private String buildAssistantUnderstandingText(TaskState taskState) {
        JsonNode goalParse = readJson(taskState.getGoalParseJson());
        JsonNode skillRoute = readJson(taskState.getSkillRouteJson());
        String analysisKind = goalParse == null ? null : goalParse.path("analysis_kind").asText(null);
        String capabilityKey = skillRoute == null ? null : skillRoute.path("capability_key").asText(null);
        String selectedTemplate = skillRoute == null ? null : skillRoute.path("selected_template").asText(null);
        if (analysisKind != null || capabilityKey != null || selectedTemplate != null) {
            StringBuilder builder = new StringBuilder("I understood this as");
            if (analysisKind != null && !analysisKind.isBlank()) {
                builder.append(" ").append(analysisKind);
            } else {
                builder.append(" a governed analysis request");
            }
            if (capabilityKey != null && !capabilityKey.isBlank()) {
                builder.append(" using capability ").append(capabilityKey);
            }
            if (selectedTemplate != null && !selectedTemplate.isBlank()) {
                builder.append(" with template ").append(selectedTemplate);
            }
            builder.append(".");
            return builder.toString();
        }
        return "I understood the request and started building a governed analysis task from your goal.";
    }

    private JsonNode buildTaskRef(String taskId) {
        if (taskId == null) {
            return null;
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task_id", taskId);
        return root;
    }

    private JsonNode buildResultRef(String taskId, String resultBundleId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task_id", taskId);
        root.put("result_bundle_id", resultBundleId);
        return root;
    }

    private ObjectNode buildTextPayload(String text, String clientRequestId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", text);
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            root.put("client_request_id", clientRequestId);
        }
        return root;
    }

    private JsonNode readJson(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(sourceJson);
        } catch (Exception exception) {
            return null;
        }
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize session JSON", exception);
        }
    }

    private String resolveTitle(String title, String userGoal) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (userGoal == null || userGoal.isBlank()) {
            return "Analysis session";
        }
        return userGoal.length() <= 120 ? userGoal : userGoal.substring(0, 120);
    }

    private String mapSessionStatus(String taskState) {
        if (TaskStatus.WAITING_USER.name().equals(taskState)) {
            return SessionStatus.WAITING_USER.name();
        }
        if (TaskStatus.SUCCEEDED.name().equals(taskState)) {
            return SessionStatus.READY_RESULT.name();
        }
        if (TaskStatus.CANCELLED.name().equals(taskState)) {
            return SessionStatus.CANCELLED.name();
        }
        if (TaskStatus.FAILED.name().equals(taskState) || TaskStatus.STATE_CORRUPTED.name().equals(taskState)) {
            return SessionStatus.FAILED.name();
        }
        return SessionStatus.RUNNING.name();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
