package com.sage.backend.scene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.common.SessionMessageIdGenerator;
import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.SessionMessageMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
import com.sage.backend.model.SessionStatus;
import com.sage.backend.scene.dto.PostSceneSessionMessageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class DemoSceneSessionSimulationService {

    private static final String URBAN_SCENE_ID = "scene-urban-cooling-for-heat-mitigation";
    private static final String LIVE_SESSION_ID = "sess_live_urban_cooling";
    private static final String REPLAY_TASK_ID = "task_demo_urban_cooling";
    private static final String REPLAY_RESULT_BUNDLE_ID = "rb_demo_urban_cooling_2024";
    private static final String DEFAULT_TITLE = "Urban Cooling for Heat Mitigation";

    private final AnalysisSessionMapper analysisSessionMapper;
    private final SessionMessageMapper sessionMessageMapper;
    private final ObjectMapper objectMapper;

    public DemoSceneSessionSimulationService(
            AnalysisSessionMapper analysisSessionMapper,
            SessionMessageMapper sessionMessageMapper,
            ObjectMapper objectMapper
    ) {
        this.analysisSessionMapper = analysisSessionMapper;
        this.sessionMessageMapper = sessionMessageMapper;
        this.objectMapper = objectMapper;
    }

    public boolean shouldSimulate(String sceneId, AnalysisSession currentSession) {
        return URBAN_SCENE_ID.equals(sceneId)
                && currentSession != null
                && LIVE_SESSION_ID.equals(currentSession.getSessionId())
                && sessionMessageMapper.findBySessionId(currentSession.getSessionId()).isEmpty();
    }

    @Transactional
    public void simulateFirstTurn(AnalysisSession currentSession, PostSceneSessionMessageRequest request) {
        String userGoal = normalizeUserGoal(request.getContent());
        analysisSessionMapper.updateTitleAndUserGoal(currentSession.getSessionId(), DEFAULT_TITLE, userGoal);

        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC);

        appendMessage(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                null,
                "user",
                "user_goal",
                buildUserGoalPayload(userGoal, request.getClientRequestId()),
                buildTaskRef(REPLAY_TASK_ID),
                base
        );

        appendMessage(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                null,
                "assistant",
                "assistant_understanding",
                buildAssistantUnderstandingPayload(userGoal),
                buildTaskRef(REPLAY_TASK_ID),
                base.plusSeconds(1)
        );

        appendMessage(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                null,
                "assistant",
                "progress_update",
                buildProgressUpdatePayload(),
                buildTaskRef(REPLAY_TASK_ID),
                base.plusSeconds(2)
        );

        appendMessage(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                REPLAY_RESULT_BUNDLE_ID,
                "assistant",
                "result_summary",
                buildResultSummaryPayload(),
                buildResultRef(REPLAY_TASK_ID, REPLAY_RESULT_BUNDLE_ID),
                base.plusSeconds(3)
        );

        appendMessage(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                REPLAY_RESULT_BUNDLE_ID,
                "assistant",
                "next_step_guidance",
                buildFollowUpPayload(),
                buildResultRef(REPLAY_TASK_ID, REPLAY_RESULT_BUNDLE_ID),
                base.plusSeconds(4)
        );

        analysisSessionMapper.updateStateAndPointers(
                currentSession.getSessionId(),
                REPLAY_TASK_ID,
                REPLAY_RESULT_BUNDLE_ID,
                SessionStatus.READY_RESULT.name(),
                null,
                writeJson(buildSessionSummaryPayload(REPLAY_TASK_ID, SessionStatus.READY_RESULT.name(), userGoal, REPLAY_RESULT_BUNDLE_ID))
        );
    }

    private void appendMessage(
            String sessionId,
            String taskId,
            String resultBundleId,
            String role,
            String messageType,
            JsonNode content,
            JsonNode relatedObjectRefs,
            OffsetDateTime createdAt
    ) {
        SessionMessage message = new SessionMessage();
        message.setMessageId(SessionMessageIdGenerator.generate());
        message.setSessionId(sessionId);
        message.setTaskId(taskId);
        message.setResultBundleId(resultBundleId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContentJson(writeJson(content));
        message.setAttachmentRefsJson(null);
        message.setActionSchemaJson(null);
        message.setRelatedObjectRefsJson(writeJson(relatedObjectRefs));
        message.setCreatedAt(createdAt);
        sessionMessageMapper.insert(message);
    }

    private ObjectNode buildUserGoalPayload(String userGoal, String clientRequestId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("text", userGoal);
        if (clientRequestId != null && !clientRequestId.isBlank()) {
            root.put("client_request_id", clientRequestId);
        }
        return root;
    }

    private ObjectNode buildAssistantUnderstandingPayload(String userGoal) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(
                "text",
                "I'll identify where added tree canopy and park expansion are most likely to reduce extreme heat exposure across the city, with priority given to places where heat burden and lack of green access overlap."
        );
        root.put("analysis_kind", "urban cooling prioritization");
        root.put("goal_type", "heat mitigation");
        root.put("route_mode", "skill_grounded");
        root.put("capability_key", "urban_cooling_priority");
        root.put("selected_template", "urban_cooling_v1");
        root.put("source_user_goal", userGoal);
        return root;
    }

    private ObjectNode buildProgressUpdatePayload() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("current_phase_label", "RUNNING");
        root.put("current_system_action", "Preparing governed urban cooling analysis");
        root.put("latest_progress_note", "Analysis prepared, validated, and submitted for governed execution.");
        root.put("estimated_next_milestone", "Cooling priority zones ranked");
        return root;
    }

    private ObjectNode buildResultSummaryPayload() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(
                "text",
                "A governed result is ready. The strongest cooling opportunities cluster in low-canopy neighborhoods with high afternoon heat burden and limited park access."
        );
        root.put(
                "summary",
                "Priority zones concentrate in low-canopy neighborhoods with high heat burden and limited park access."
        );
        ArrayNode highlights = root.putArray("highlights");
        highlights.add("Southwest residential heat islands rank highest for canopy expansion.");
        highlights.add("The central transit corridor shows the largest cooling benefit per hectare of added shade.");
        highlights.add("Pocket park expansion is recommended around dense apartment clusters with limited green relief.");
        return root;
    }

    private ObjectNode buildFollowUpPayload() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(
                "text",
                "If you want, I can continue by comparing tree canopy expansion versus park expansion, narrowing this to one district or corridor, or turning these priority zones into a phased action shortlist."
        );
        return root;
    }

    private ObjectNode buildTaskRef(String taskId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task_id", taskId);
        return root;
    }

    private ObjectNode buildResultRef(String taskId, String resultBundleId) {
        ObjectNode root = buildTaskRef(taskId);
        root.put("result_bundle_id", resultBundleId);
        return root;
    }

    private ObjectNode buildSessionSummaryPayload(String taskId, String status, String userGoal, String resultBundleId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("task_id", taskId);
        root.put("status", status);
        root.put("user_goal", userGoal);
        if (resultBundleId == null) {
            root.putNull("latest_result_bundle_id");
        } else {
            root.put("latest_result_bundle_id", resultBundleId);
        }
        return root;
    }

    private String normalizeUserGoal(String content) {
        if (content == null || content.isBlank()) {
            return "Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.";
        }
        return content.trim();
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize demo session simulation payload", exception);
        }
    }
}
