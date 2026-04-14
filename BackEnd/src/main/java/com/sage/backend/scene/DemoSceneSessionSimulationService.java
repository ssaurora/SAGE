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
import com.sage.backend.scene.dto.DemoLiveSimulationSupportDTO;
import com.sage.backend.scene.dto.DemoTraceStageDTO;
import com.sage.backend.scene.dto.DeveloperTraceSupportDataDTO;
import com.sage.backend.scene.dto.PostSceneSessionMessageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class DemoSceneSessionSimulationService {

    private static final String DEMO_URBAN_SCENE_ID = "scene-urban-cooling-for-heat-mitigation";
    private static final String DEMO_LIVE_SIMULATION_SESSION_ID = "sess_live_urban_cooling";
    private static final String DEMO_LIVE_SIMULATION_TASK_ID = "task_live_urban_cooling";
    private static final String DEMO_REPLAY_TASK_ID = "task_demo_urban_cooling";
    private static final String DEMO_REPLAY_RESULT_BUNDLE_ID = "rb_demo_urban_cooling_2024";
    private static final String DEMO_DEFAULT_TITLE = "Urban Cooling for Heat Mitigation";
    private static final String DEMO_RUN_SCOPE = "urban_cooling_first_turn_live_success";

    private static final String DEMO_STEP_IDLE = "demo_idle";
    private static final String DEMO_STEP_USER_GOAL_ACCEPTED = "demo_user_goal_accepted";
    private static final String DEMO_STEP_ASSISTANT_UNDERSTANDING_EMITTED = "demo_assistant_understanding_emitted";
    private static final String DEMO_STEP_PREPARED_VALIDATED_SUBMITTED_EMITTED = "demo_prepared_validated_submitted_emitted";
    private static final String DEMO_STEP_RESULT_READY_EMITTED = "demo_result_ready_emitted";
    private static final String DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED = "demo_follow_up_invitation_emitted";
    private static final String DEMO_STEP_COMPLETED = "demo_completed";
    private static final String DEMO_STEP_RUN_STATE_UNAVAILABLE = "demo_run_state_unavailable";

    private static final String DEMO_TRACE_AUTHORITY_BACKED = "authority_backed";
    private static final String DEMO_TRACE_DEMO_ORCHESTRATED = "demo_orchestrated";
    private static final String DEMO_TRACE_DERIVED_SUMMARY = "derived_summary";
    private static final String DEMO_TRACE_UNSUPPORTED = "unsupported";

    private static final long DEMO_ASSISTANT_UNDERSTANDING_DELAY_MS = 1100L;
    private static final long DEMO_PREPARED_VALIDATED_SUBMITTED_DELAY_MS = 1800L;
    private static final long DEMO_RESULT_READY_DELAY_MS = 3300L;
    private static final long DEMO_FOLLOW_UP_INVITATION_DELAY_MS = 1400L;

    private static final String DEMO_RUN_ACTIVE_MESSAGE =
            "DEMO_LIVE_SIMULATION_ACTIVE: SAGE is already working on this demo analysis. Wait for the current run to finish before replaying.";
    private static final String DEMO_RUN_COMPLETE_MESSAGE =
            "DEMO_LIVE_SIMULATION_COMPLETE: This demo run is complete. Reset the demo session to replay it.";
    private static final String DEMO_RUN_RESET_REQUIRED_MESSAGE =
            "DEMO_LIVE_SIMULATION_RESET_REQUIRED: This demo session must be reset before it can be replayed.";

    private final AnalysisSessionMapper analysisSessionMapper;
    private final SessionMessageMapper sessionMessageMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService demoLiveSimulationScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<String, DemoLiveSimulationRunState> demoLiveSimulationRuns = new ConcurrentHashMap<>();

    public DemoSceneSessionSimulationService(
            AnalysisSessionMapper analysisSessionMapper,
            SessionMessageMapper sessionMessageMapper,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.analysisSessionMapper = analysisSessionMapper;
        this.sessionMessageMapper = sessionMessageMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean isDemoLiveSimulationSession(String sceneId, AnalysisSession currentSession) {
        return DEMO_URBAN_SCENE_ID.equals(sceneId)
                && currentSession != null
                && DEMO_LIVE_SIMULATION_SESSION_ID.equals(currentSession.getSessionId());
    }

    public void handleDemoLiveSimulationPost(AnalysisSession currentSession, PostSceneSessionMessageRequest request) {
        if (!DEMO_LIVE_SIMULATION_SESSION_ID.equals(currentSession.getSessionId())) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }

        AnalysisSession latestSession = requireDemoLiveSimulationSession(currentSession.getSessionId());
        DemoLiveSimulationRunState activeDemoRun = demoLiveSimulationRuns.get(currentSession.getSessionId());
        if (activeDemoRun != null && activeDemoRun.demoRunActive() && isExternallyResetDemoSession(latestSession)) {
            demoLiveSimulationRuns.remove(currentSession.getSessionId(), activeDemoRun);
            activeDemoRun = null;
        }
        if (activeDemoRun != null && activeDemoRun.demoRunActive()) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_ACTIVE_MESSAGE);
        }
        if (SessionStatus.READY_RESULT.name().equals(latestSession.getStatus())) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_COMPLETE_MESSAGE);
        }
        if (!sessionMessageMapper.findBySessionId(currentSession.getSessionId()).isEmpty()) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }

        String demoRunId = "demo_run_" + UUID.randomUUID();
        String userGoal = normalizeUserGoal(request.getContent());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DemoLiveSimulationRunState demoRunState = DemoLiveSimulationRunState.start(demoRunId, now);
        demoLiveSimulationRuns.put(currentSession.getSessionId(), demoRunState);

        transactionTemplate.executeWithoutResult(status -> {
            analysisSessionMapper.updateTitleAndUserGoal(currentSession.getSessionId(), DEMO_DEFAULT_TITLE, userGoal);
            analysisSessionMapper.updateStateAndPointers(
                    currentSession.getSessionId(),
                    DEMO_LIVE_SIMULATION_TASK_ID,
                    null,
                    SessionStatus.RUNNING.name(),
                    null,
                    writeJson(buildSessionSummaryPayload(DEMO_LIVE_SIMULATION_TASK_ID, SessionStatus.RUNNING.name(), userGoal, null))
            );
            appendMessage(
                    currentSession.getSessionId(),
                    DEMO_LIVE_SIMULATION_TASK_ID,
                    null,
                    "user",
                    "user_goal",
                    buildUserGoalPayload(userGoal, request.getClientRequestId()),
                    buildTaskRef(DEMO_LIVE_SIMULATION_TASK_ID),
                    now
            );
        });

        demoRunState.markDemoStepCompleted(DEMO_STEP_USER_GOAL_ACCEPTED, now);
        scheduleDemoAssistantUnderstanding(currentSession.getSessionId(), demoRunId, userGoal);
    }

    public DeveloperTraceSupportDataDTO buildDeveloperTraceSupportData(AnalysisSession currentSession) {
        if (currentSession == null || !DEMO_LIVE_SIMULATION_SESSION_ID.equals(currentSession.getSessionId())) {
            return null;
        }

        DeveloperTraceSupportDataDTO supportData = new DeveloperTraceSupportDataDTO();
        supportData.setDemoLiveSimulation(buildDemoLiveSimulationSupport(currentSession));
        return supportData;
    }

    private DemoLiveSimulationSupportDTO buildDemoLiveSimulationSupport(AnalysisSession currentSession) {
        DemoLiveSimulationSupportDTO support = new DemoLiveSimulationSupportDTO();
        support.setDemoRunScope(DEMO_RUN_SCOPE);
        List<SessionMessage> demoMessages = sessionMessageMapper.findBySessionId(currentSession.getSessionId());

        DemoLiveSimulationRunState demoRunState = demoLiveSimulationRuns.get(currentSession.getSessionId());
        if (demoRunState != null) {
            demoRunState.copyInto(support);
            support.setDemoResetRequired(SessionStatus.READY_RESULT.name().equals(currentSession.getStatus()));
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper));
            return support;
        }

        support.setDemoRunActive(false);
        support.setDemoRunId(null);
        support.setDemoResetRequired(SessionStatus.READY_RESULT.name().equals(currentSession.getStatus()));
        support.setDemoStartedAt(null);
        support.setDemoLastStepEmittedAt(null);
        support.setDemoNextScheduledStepAt(null);

        if (SessionStatus.READY_RESULT.name().equals(currentSession.getStatus())) {
            support.setDemoCurrentStep(DEMO_STEP_COMPLETED);
            support.getDemoCompletedSteps().addAll(allDemoCompletedSteps());
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper));
            return support;
        }

        if (demoMessages.isEmpty()) {
            support.setDemoCurrentStep(DEMO_STEP_IDLE);
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper));
            return support;
        }

        support.setDemoCurrentStep(DEMO_STEP_RUN_STATE_UNAVAILABLE);
        support.getDemoCompletedSteps().add(DEMO_STEP_USER_GOAL_ACCEPTED);
        support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper));
        return support;
    }

    private void scheduleDemoAssistantUnderstanding(String sessionId, String demoRunId, String userGoal) {
        scheduleDemoStep(
                sessionId,
                demoRunId,
                DEMO_STEP_ASSISTANT_UNDERSTANDING_EMITTED,
                DEMO_ASSISTANT_UNDERSTANDING_DELAY_MS,
                () -> {
                    appendMessage(
                            sessionId,
                            DEMO_LIVE_SIMULATION_TASK_ID,
                            null,
                            "assistant",
                            "assistant_understanding",
                            buildAssistantUnderstandingPayload(userGoal),
                            buildTaskRef(DEMO_LIVE_SIMULATION_TASK_ID),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoPreparedValidatedSubmitted(sessionId, demoRunId);
                }
        );
    }

    private void scheduleDemoPreparedValidatedSubmitted(String sessionId, String demoRunId) {
        scheduleDemoStep(
                sessionId,
                demoRunId,
                DEMO_STEP_PREPARED_VALIDATED_SUBMITTED_EMITTED,
                DEMO_PREPARED_VALIDATED_SUBMITTED_DELAY_MS,
                () -> {
                    appendMessage(
                            sessionId,
                            DEMO_LIVE_SIMULATION_TASK_ID,
                            null,
                            "assistant",
                            "progress_update",
                            buildProgressUpdatePayload(),
                            buildTaskRef(DEMO_LIVE_SIMULATION_TASK_ID),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoResultReady(sessionId, demoRunId);
                }
        );
    }

    private void scheduleDemoResultReady(String sessionId, String demoRunId) {
        scheduleDemoStep(
                sessionId,
                demoRunId,
                DEMO_STEP_RESULT_READY_EMITTED,
                DEMO_RESULT_READY_DELAY_MS,
                () -> {
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    appendMessage(
                            sessionId,
                            DEMO_REPLAY_TASK_ID,
                            DEMO_REPLAY_RESULT_BUNDLE_ID,
                            "assistant",
                            "result_summary",
                            buildResultSummaryPayload(),
                            buildResultRef(DEMO_REPLAY_TASK_ID, DEMO_REPLAY_RESULT_BUNDLE_ID),
                            now
                    );

                    AnalysisSession refreshedSession = requireDemoLiveSimulationSession(sessionId);
                    analysisSessionMapper.updateStateAndPointers(
                            sessionId,
                            DEMO_REPLAY_TASK_ID,
                            DEMO_REPLAY_RESULT_BUNDLE_ID,
                            SessionStatus.READY_RESULT.name(),
                            null,
                            writeJson(buildSessionSummaryPayload(
                                    DEMO_REPLAY_TASK_ID,
                                    SessionStatus.READY_RESULT.name(),
                                    nonBlank(refreshedSession.getUserGoal(), normalizeUserGoal(null)),
                                    DEMO_REPLAY_RESULT_BUNDLE_ID
                            ))
                    );
                    scheduleDemoFollowUpInvitation(sessionId, demoRunId);
                }
        );
    }

    private void scheduleDemoFollowUpInvitation(String sessionId, String demoRunId) {
        scheduleDemoStep(
                sessionId,
                demoRunId,
                DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED,
                DEMO_FOLLOW_UP_INVITATION_DELAY_MS,
                () -> appendMessage(
                        sessionId,
                        DEMO_REPLAY_TASK_ID,
                        DEMO_REPLAY_RESULT_BUNDLE_ID,
                        "assistant",
                        "next_step_guidance",
                        buildFollowUpPayload(),
                        buildResultRef(DEMO_REPLAY_TASK_ID, DEMO_REPLAY_RESULT_BUNDLE_ID),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
        );
    }

    private void scheduleDemoStep(
            String sessionId,
            String demoRunId,
            String stepId,
            long delayMs,
            Runnable stageWriter
    ) {
        DemoLiveSimulationRunState demoRunState = demoLiveSimulationRuns.get(sessionId);
        if (demoRunState != null) {
            demoRunState.setDemoNextScheduledStepAt(OffsetDateTime.now(ZoneOffset.UTC).plusNanos(TimeUnit.MILLISECONDS.toNanos(delayMs)));
        }

        demoLiveSimulationScheduler.schedule(() -> transactionTemplate.executeWithoutResult(status -> {
            DemoLiveSimulationRunState latestRunState = demoLiveSimulationRuns.get(sessionId);
            if (latestRunState == null || !latestRunState.matchesDemoRun(demoRunId) || !latestRunState.demoRunActive()) {
                return;
            }
            AnalysisSession currentSession = requireDemoLiveSimulationSession(sessionId);
            if (isExternallyResetDemoSession(currentSession)) {
                demoLiveSimulationRuns.remove(sessionId, latestRunState);
                return;
            }

            stageWriter.run();
            OffsetDateTime emittedAt = OffsetDateTime.now(ZoneOffset.UTC);
            latestRunState.markDemoStepCompleted(stepId, emittedAt);
            if (DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED.equals(stepId)) {
                latestRunState.finishDemoRun(emittedAt);
            }
        }), delayMs, TimeUnit.MILLISECONDS);
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

    private AnalysisSession requireDemoLiveSimulationSession(String sessionId) {
        AnalysisSession current = analysisSessionMapper.findBySessionId(sessionId);
        if (current == null) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }
        return current;
    }

    private boolean isExternallyResetDemoSession(AnalysisSession session) {
        return SessionStatus.RUNNING.name().equals(session.getStatus())
                && (session.getLatestResultBundleId() == null || session.getLatestResultBundleId().isBlank())
                && sessionMessageMapper.findBySessionId(session.getSessionId()).isEmpty();
    }

    private List<String> allDemoCompletedSteps() {
        List<String> steps = new ArrayList<>();
        steps.add(DEMO_STEP_USER_GOAL_ACCEPTED);
        steps.add(DEMO_STEP_ASSISTANT_UNDERSTANDING_EMITTED);
        steps.add(DEMO_STEP_PREPARED_VALIDATED_SUBMITTED_EMITTED);
        steps.add(DEMO_STEP_RESULT_READY_EMITTED);
        steps.add(DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED);
        steps.add(DEMO_STEP_COMPLETED);
        return steps;
    }

    private String normalizeUserGoal(String content) {
        if (content == null || content.isBlank()) {
            return "Identify urban cooling priority zones where tree canopy and park expansion would most reduce extreme heat exposure.";
        }
        return content.trim();
    }

    private String nonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize demo live simulation payload", exception);
        }
    }

    private static final class DemoLiveSimulationRunState {
        private final String demoRunId;
        private final OffsetDateTime demoStartedAt;
        private final Set<String> demoCompletedSteps = new LinkedHashSet<>();
        private volatile boolean demoRunActive;
        private volatile String demoCurrentStep;
        private volatile OffsetDateTime demoLastStepEmittedAt;
        private volatile OffsetDateTime demoNextScheduledStepAt;

        private DemoLiveSimulationRunState(String demoRunId, OffsetDateTime demoStartedAt) {
            this.demoRunId = demoRunId;
            this.demoStartedAt = demoStartedAt;
            this.demoRunActive = true;
            this.demoCurrentStep = DEMO_STEP_IDLE;
        }

        private static DemoLiveSimulationRunState start(String demoRunId, OffsetDateTime demoStartedAt) {
            return new DemoLiveSimulationRunState(demoRunId, demoStartedAt);
        }

        private boolean matchesDemoRun(String candidateDemoRunId) {
            return demoRunId.equals(candidateDemoRunId);
        }

        private boolean demoRunActive() {
            return demoRunActive;
        }

        private synchronized void setDemoNextScheduledStepAt(OffsetDateTime nextScheduledStepAt) {
            this.demoNextScheduledStepAt = nextScheduledStepAt;
        }

        private synchronized void markDemoStepCompleted(String stepId, OffsetDateTime emittedAt) {
            this.demoCurrentStep = stepId;
            this.demoCompletedSteps.add(stepId);
            this.demoLastStepEmittedAt = emittedAt;
            this.demoNextScheduledStepAt = null;
        }

        private synchronized void finishDemoRun(OffsetDateTime emittedAt) {
            this.demoRunActive = false;
            this.demoCurrentStep = DEMO_STEP_COMPLETED;
            this.demoCompletedSteps.add(DEMO_STEP_COMPLETED);
            this.demoLastStepEmittedAt = emittedAt;
            this.demoNextScheduledStepAt = null;
        }

        private synchronized void copyInto(DemoLiveSimulationSupportDTO target) {
            target.setDemoRunActive(demoRunActive);
            target.setDemoRunId(demoRunId);
            target.setDemoCurrentStep(demoCurrentStep);
            target.getDemoCompletedSteps().addAll(demoCompletedSteps);
            target.setDemoNextScheduledStepAt(demoNextScheduledStepAt == null ? null : demoNextScheduledStepAt.toString());
            target.setDemoStartedAt(demoStartedAt.toString());
            target.setDemoLastStepEmittedAt(demoLastStepEmittedAt == null ? null : demoLastStepEmittedAt.toString());
        }
    }
}
