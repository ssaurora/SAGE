package com.sage.backend.scene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.common.SessionMessageIdGenerator;
import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.SessionMessageMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.SessionMessage;
import com.sage.backend.model.SessionStatus;
import com.sage.backend.scene.DemoLiveSimulationNarratives.DemoLiveSimulationNarrative;
import com.sage.backend.scene.dto.DemoLiveSimulationSupportDTO;
import com.sage.backend.scene.dto.DeveloperTraceSupportDataDTO;
import com.sage.backend.scene.dto.PostSceneSessionMessageRequest;
import com.sage.backend.scene.dto.RunSurfaceProjectionDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String DEMO_STEP_IDLE = "demo_idle";
    private static final String DEMO_STEP_USER_GOAL_ACCEPTED = "demo_user_goal_accepted";
    private static final String DEMO_STEP_ASSISTANT_UNDERSTANDING_EMITTED = "demo_assistant_understanding_emitted";
    private static final String DEMO_STEP_ASSISTANT_EXECUTION_BRIEF_EMITTED = "demo_assistant_execution_brief_emitted";
    private static final String DEMO_STEP_RUN_PREPARING = "demo_run_preparing";
    private static final String DEMO_STEP_RUN_SUBMITTED = "demo_run_submitted";
    private static final String DEMO_STEP_RUN_RUNNING = "demo_run_running";
    private static final String DEMO_STEP_RESULT_READY_EMITTED = "demo_result_ready_emitted";
    private static final String DEMO_STEP_ASSISTANT_REVIEWING_EMITTED = "demo_assistant_reviewing_emitted";
    private static final String DEMO_STEP_ASSISTANT_FINAL_EXPLANATION_EMITTED = "demo_assistant_final_explanation_emitted";
    private static final String DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED = "demo_follow_up_invitation_emitted";
    private static final String DEMO_STEP_COMPLETED = "demo_completed";
    private static final String DEMO_STEP_RUN_STATE_UNAVAILABLE = "demo_run_state_unavailable";

    private static final long DEMO_ASSISTANT_UNDERSTANDING_DELAY_MS = 1100L;
    private static final long DEMO_ASSISTANT_EXECUTION_BRIEF_DELAY_MS = 1000L;
    private static final long DEMO_RUN_PREPARING_DELAY_MS = 800L;
    private static final long DEMO_RUN_SUBMITTED_DELAY_MS = 900L;
    private static final long DEMO_RUN_RUNNING_DELAY_MS = 1200L;
    private static final long DEMO_RESULT_READY_DELAY_MS = 2600L;
    private static final long DEMO_ASSISTANT_REVIEWING_DELAY_MS = 900L;
    private static final long DEMO_ASSISTANT_FINAL_EXPLANATION_DELAY_MS = 1000L;
    private static final long DEMO_FOLLOW_UP_INVITATION_DELAY_MS = 1100L;

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
        return DemoLiveSimulationNarratives.findBySceneAndSession(sceneId, currentSession) != null;
    }

    public void handleDemoLiveSimulationPost(String sceneId, AnalysisSession currentSession, PostSceneSessionMessageRequest request) {
        DemoLiveSimulationNarrative narrative = requireDemoNarrative(sceneId, currentSession);

        AnalysisSession latestSession = requireDemoLiveSimulationSession(narrative.liveSessionId());
        DemoLiveSimulationRunState activeDemoRun = demoLiveSimulationRuns.get(narrative.liveSessionId());
        if (activeDemoRun != null && activeDemoRun.demoRunActive() && isExternallyResetDemoSession(latestSession)) {
            demoLiveSimulationRuns.remove(narrative.liveSessionId(), activeDemoRun);
            activeDemoRun = null;
        }
        if (activeDemoRun != null && activeDemoRun.demoRunActive()) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_ACTIVE_MESSAGE);
        }
        if (SessionStatus.READY_RESULT.name().equals(latestSession.getStatus())) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_COMPLETE_MESSAGE);
        }
        if (!sessionMessageMapper.findBySessionId(narrative.liveSessionId()).isEmpty()) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }

        String demoRunId = "demo_run_" + UUID.randomUUID();
        String userGoal = normalizeUserGoal(request.getContent(), narrative);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DemoLiveSimulationRunState demoRunState = DemoLiveSimulationRunState.start(demoRunId, now);
        demoLiveSimulationRuns.put(narrative.liveSessionId(), demoRunState);

        transactionTemplate.executeWithoutResult(status -> {
            analysisSessionMapper.updateTitleAndUserGoal(narrative.liveSessionId(), narrative.defaultTitle(), userGoal);
            analysisSessionMapper.updateStateAndPointers(
                    narrative.liveSessionId(),
                    narrative.liveTaskId(),
                    null,
                    SessionStatus.RUNNING.name(),
                    null,
                    writeJson(buildSessionSummaryPayload(narrative.liveTaskId(), SessionStatus.RUNNING.name(), userGoal, null))
            );
            appendMessage(
                    narrative.liveSessionId(),
                    narrative.liveTaskId(),
                    null,
                    "user",
                    "user_goal",
                    buildUserGoalPayload(userGoal, request.getClientRequestId()),
                    buildTaskRef(narrative.liveTaskId()),
                    now
            );
        });

        demoRunState.markDemoStepCompleted(DEMO_STEP_USER_GOAL_ACCEPTED, now);
        scheduleDemoAssistantUnderstanding(narrative, demoRunId, userGoal);
    }

    public void resetDemoLiveSimulationSession(AnalysisSession currentSession) {
        DemoLiveSimulationNarrative narrative = DemoLiveSimulationNarratives.findBySessionId(
                currentSession == null ? null : currentSession.getSessionId()
        );
        if (narrative == null) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }

        demoLiveSimulationRuns.remove(narrative.liveSessionId());
        String defaultUserGoal = narrative.defaultUserGoal();

        transactionTemplate.executeWithoutResult(status -> {
            sessionMessageMapper.deleteBySessionId(narrative.liveSessionId());
            analysisSessionMapper.updateTitleAndUserGoal(
                    narrative.liveSessionId(),
                    narrative.defaultTitle(),
                    defaultUserGoal
            );
            analysisSessionMapper.updateStateAndPointers(
                    narrative.liveSessionId(),
                    narrative.liveTaskId(),
                    null,
                    SessionStatus.RUNNING.name(),
                    null,
                    writeJson(buildSessionSummaryPayload(
                            narrative.liveTaskId(),
                            SessionStatus.RUNNING.name(),
                            defaultUserGoal,
                            null
                    ))
            );
        });
    }

    public DeveloperTraceSupportDataDTO buildDeveloperTraceSupportData(AnalysisSession currentSession) {
        DemoLiveSimulationNarrative narrative = DemoLiveSimulationNarratives.findBySessionId(
                currentSession == null ? null : currentSession.getSessionId()
        );
        if (narrative == null) {
            return null;
        }

        DeveloperTraceSupportDataDTO supportData = new DeveloperTraceSupportDataDTO();
        supportData.setDemoLiveSimulation(buildDemoLiveSimulationSupport(currentSession, narrative));
        return supportData;
    }

    private DemoLiveSimulationSupportDTO buildDemoLiveSimulationSupport(
            AnalysisSession currentSession,
            DemoLiveSimulationNarrative narrative
    ) {
        DemoLiveSimulationSupportDTO support = new DemoLiveSimulationSupportDTO();
        support.setDemoNarrativeType(narrative.demoNarrativeType());
        support.setDemoRunScope(narrative.demoRunScope());
        List<SessionMessage> demoMessages = sessionMessageMapper.findBySessionId(currentSession.getSessionId());

        DemoLiveSimulationRunState demoRunState = demoLiveSimulationRuns.get(currentSession.getSessionId());
        if (demoRunState != null) {
            demoRunState.copyInto(support);
            support.setDemoNarrativeType(narrative.demoNarrativeType());
            support.setDemoResetRequired(!demoRunState.demoRunActive() && SessionStatus.READY_RESULT.name().equals(currentSession.getStatus()));
            support.setRunSurfaceProjection(buildRunSurfaceProjection(currentSession, narrative, support));
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper, narrative));
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
            support.setRunSurfaceProjection(buildRunSurfaceProjection(currentSession, narrative, support));
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper, narrative));
            return support;
        }

        if (demoMessages.isEmpty()) {
            support.setDemoCurrentStep(DEMO_STEP_IDLE);
            support.setRunSurfaceProjection(buildRunSurfaceProjection(currentSession, narrative, support));
            support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper, narrative));
            return support;
        }

        support.setDemoCurrentStep(DEMO_STEP_RUN_STATE_UNAVAILABLE);
        support.getDemoCompletedSteps().add(DEMO_STEP_USER_GOAL_ACCEPTED);
        support.setDemoResetRequired(true);
        support.setRunSurfaceProjection(buildRunSurfaceProjection(currentSession, narrative, support));
        support.getStages().addAll(DemoLiveSimulationTraceStageFactory.build(currentSession, demoMessages, support, objectMapper, narrative));
        return support;
    }

    private void scheduleDemoAssistantUnderstanding(DemoLiveSimulationNarrative narrative, String demoRunId, String userGoal) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_ASSISTANT_UNDERSTANDING_EMITTED,
                DEMO_ASSISTANT_UNDERSTANDING_DELAY_MS,
                () -> {
                    appendMessage(
                            narrative.liveSessionId(),
                            narrative.liveTaskId(),
                            null,
                            "assistant",
                            "assistant_understanding",
                            narrative.buildAssistantUnderstandingPayload(objectMapper, userGoal),
                            buildTaskRef(narrative.liveTaskId()),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoAssistantExecutionBrief(narrative, demoRunId);
                }
        );
    }

    private void scheduleDemoAssistantExecutionBrief(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_ASSISTANT_EXECUTION_BRIEF_EMITTED,
                DEMO_ASSISTANT_EXECUTION_BRIEF_DELAY_MS,
                () -> {
                    appendMessage(
                            narrative.liveSessionId(),
                            narrative.liveTaskId(),
                            null,
                            "assistant",
                            "assistant_execution_brief",
                            narrative.buildExecutionBriefPayload(objectMapper),
                            buildTaskRef(narrative.liveTaskId()),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoRunPreparing(narrative, demoRunId);
                }
        );
    }

    private void scheduleDemoRunPreparing(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_RUN_PREPARING,
                DEMO_RUN_PREPARING_DELAY_MS,
                () -> scheduleDemoRunSubmitted(narrative, demoRunId)
        );
    }

    private void scheduleDemoRunSubmitted(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_RUN_SUBMITTED,
                DEMO_RUN_SUBMITTED_DELAY_MS,
                () -> scheduleDemoRunRunning(narrative, demoRunId)
        );
    }

    private void scheduleDemoRunRunning(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_RUN_RUNNING,
                DEMO_RUN_RUNNING_DELAY_MS,
                () -> scheduleDemoResultReady(narrative, demoRunId)
        );
    }

    private void scheduleDemoResultReady(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_RESULT_READY_EMITTED,
                DEMO_RESULT_READY_DELAY_MS,
                () -> {
                    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                    appendMessage(
                            narrative.liveSessionId(),
                            narrative.replayTaskId(),
                            narrative.replayResultBundleId(),
                            "system",
                            "result_ready",
                            narrative.buildResultReadyPayload(objectMapper),
                            buildResultRef(narrative.replayTaskId(), narrative.replayResultBundleId()),
                            now
                    );

                    AnalysisSession refreshedSession = requireDemoLiveSimulationSession(narrative.liveSessionId());
                    analysisSessionMapper.updateStateAndPointers(
                            narrative.liveSessionId(),
                            narrative.replayTaskId(),
                            narrative.replayResultBundleId(),
                            SessionStatus.READY_RESULT.name(),
                            null,
                            writeJson(buildSessionSummaryPayload(
                                    narrative.replayTaskId(),
                                    SessionStatus.READY_RESULT.name(),
                                    nonBlank(refreshedSession.getUserGoal(), narrative.defaultUserGoal()),
                                    narrative.replayResultBundleId()
                            ))
                    );
                    scheduleDemoAssistantReviewing(narrative, demoRunId);
                }
        );
    }

    private void scheduleDemoAssistantReviewing(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_ASSISTANT_REVIEWING_EMITTED,
                DEMO_ASSISTANT_REVIEWING_DELAY_MS,
                () -> {
                    appendMessage(
                            narrative.liveSessionId(),
                            narrative.replayTaskId(),
                            narrative.replayResultBundleId(),
                            "assistant",
                            "assistant_reviewing",
                            narrative.buildAssistantReviewingPayload(objectMapper),
                            buildResultRef(narrative.replayTaskId(), narrative.replayResultBundleId()),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoAssistantFinalExplanation(narrative, demoRunId);
                }
        );
    }

    private void scheduleDemoAssistantFinalExplanation(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_ASSISTANT_FINAL_EXPLANATION_EMITTED,
                DEMO_ASSISTANT_FINAL_EXPLANATION_DELAY_MS,
                () -> {
                    appendMessage(
                            narrative.liveSessionId(),
                            narrative.replayTaskId(),
                            narrative.replayResultBundleId(),
                            "assistant",
                            "assistant_final_explanation",
                            narrative.buildFinalExplanationPayload(objectMapper),
                            buildResultRef(narrative.replayTaskId(), narrative.replayResultBundleId()),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    );
                    scheduleDemoFollowUpInvitation(narrative, demoRunId);
                }
        );
    }

    private void scheduleDemoFollowUpInvitation(DemoLiveSimulationNarrative narrative, String demoRunId) {
        scheduleDemoStep(
                narrative.liveSessionId(),
                demoRunId,
                DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED,
                DEMO_FOLLOW_UP_INVITATION_DELAY_MS,
                () -> appendMessage(
                        narrative.liveSessionId(),
                        narrative.replayTaskId(),
                        narrative.replayResultBundleId(),
                        "assistant",
                        "next_step_guidance",
                        narrative.buildFollowUpPayload(objectMapper),
                        buildResultRef(narrative.replayTaskId(), narrative.replayResultBundleId()),
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

    private DemoLiveSimulationNarrative requireDemoNarrative(String sceneId, AnalysisSession currentSession) {
        DemoLiveSimulationNarrative narrative = DemoLiveSimulationNarratives.findBySceneAndSession(sceneId, currentSession);
        if (narrative == null) {
            throw new ResponseStatusException(CONFLICT, DEMO_RUN_RESET_REQUIRED_MESSAGE);
        }
        return narrative;
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
        steps.add(DEMO_STEP_ASSISTANT_EXECUTION_BRIEF_EMITTED);
        steps.add(DEMO_STEP_RUN_PREPARING);
        steps.add(DEMO_STEP_RUN_SUBMITTED);
        steps.add(DEMO_STEP_RUN_RUNNING);
        steps.add(DEMO_STEP_RESULT_READY_EMITTED);
        steps.add(DEMO_STEP_ASSISTANT_REVIEWING_EMITTED);
        steps.add(DEMO_STEP_ASSISTANT_FINAL_EXPLANATION_EMITTED);
        steps.add(DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED);
        steps.add(DEMO_STEP_COMPLETED);
        return steps;
    }

    private RunSurfaceProjectionDTO buildRunSurfaceProjection(
            AnalysisSession currentSession,
            DemoLiveSimulationNarrative narrative,
            DemoLiveSimulationSupportDTO support
    ) {
        RunSurfaceProjectionDTO projection = new RunSurfaceProjectionDTO();
        String currentStep = support.getDemoCurrentStep();
        String phase = resolveRunSurfacePhase(currentStep);
        boolean visible = phase != null;

        projection.setVisible(visible);
        projection.setPhase(phase);
        projection.setCompleted("completed".equals(phase));
        projection.setAuthorityNote("demo_orchestrated_run_surface");

        if (!visible || phase == null) {
            projection.setLabel(null);
            projection.setDetail(null);
            return projection;
        }

        switch (phase) {
            case "preparing" -> {
                projection.setLabel("Preparing");
                projection.setDetail(narrative.runPreparingDetail());
            }
            case "submitted" -> {
                projection.setLabel("Submitted");
                projection.setDetail(narrative.runSubmittedDetail());
            }
            case "running" -> {
                projection.setLabel("Running");
                projection.setDetail(narrative.runRunningDetail());
            }
            case "completed" -> {
                projection.setLabel("Completed");
                projection.setDetail(narrative.runCompletedDetail());
            }
            default -> {
                projection.setLabel(null);
                projection.setDetail(null);
            }
        }
        return projection;
    }

    private String resolveRunSurfacePhase(String currentStep) {
        if (currentStep == null) {
            return null;
        }
        return switch (currentStep) {
            case DEMO_STEP_RUN_PREPARING -> "preparing";
            case DEMO_STEP_RUN_SUBMITTED -> "submitted";
            case DEMO_STEP_RUN_RUNNING -> "running";
            case DEMO_STEP_RESULT_READY_EMITTED,
                    DEMO_STEP_ASSISTANT_REVIEWING_EMITTED,
                    DEMO_STEP_ASSISTANT_FINAL_EXPLANATION_EMITTED,
                    DEMO_STEP_FOLLOW_UP_INVITATION_EMITTED,
                    DEMO_STEP_COMPLETED -> "completed";
            default -> null;
        };
    }

    private String normalizeUserGoal(String content, DemoLiveSimulationNarrative narrative) {
        if (content == null || content.isBlank()) {
            return narrative.defaultUserGoal();
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

        private synchronized void finishDemoRun(OffsetDateTime completedAt) {
            this.demoCurrentStep = DEMO_STEP_COMPLETED;
            this.demoCompletedSteps.add(DEMO_STEP_COMPLETED);
            this.demoLastStepEmittedAt = completedAt;
            this.demoNextScheduledStepAt = null;
            this.demoRunActive = false;
        }

        private synchronized void copyInto(DemoLiveSimulationSupportDTO support) {
            support.setDemoRunActive(demoRunActive);
            support.setDemoRunId(demoRunId);
            support.setDemoCurrentStep(demoCurrentStep);
            support.getDemoCompletedSteps().clear();
            support.getDemoCompletedSteps().addAll(demoCompletedSteps);
            support.setDemoStartedAt(demoStartedAt == null ? null : demoStartedAt.toString());
            support.setDemoLastStepEmittedAt(demoLastStepEmittedAt == null ? null : demoLastStepEmittedAt.toString());
            support.setDemoNextScheduledStepAt(demoNextScheduledStepAt == null ? null : demoNextScheduledStepAt.toString());
        }
    }
}
