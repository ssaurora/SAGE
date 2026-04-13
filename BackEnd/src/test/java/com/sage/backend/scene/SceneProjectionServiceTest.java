package com.sage.backend.scene;

import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.scene.dto.SceneDetailDTO;
import com.sage.backend.scene.dto.SceneListResponse;
import com.sage.backend.session.SessionService;
import com.sage.backend.session.dto.AnalysisSessionResponse;
import com.sage.backend.task.TaskService;
import com.sage.backend.task.dto.CorruptionStateView;
import com.sage.backend.task.dto.TaskAuditResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneProjectionServiceTest {

    @Test
    void getScenesBuildsWaitingSceneWithAttention() {
        Harness harness = new Harness();
        SceneProjectionContext context = context("scene-wait", session("sess-wait", "scene-wait", "WAITING_USER", "task-wait"), task("task-wait", "sess-wait", TaskStatus.WAITING_USER.name(), "result-none"));
        TaskDetailResponse detail = waitingDetail("task-wait");

        when(harness.queryService.loadAllSceneContexts(42L)).thenReturn(List.of(context));
        when(harness.taskService.getTask("task-wait", 42L)).thenReturn(detail);

        SceneListResponse response = harness.service.getScenes(42L, null, null, 50);

        assertEquals("WAITING_USER", response.getItems().get(0).getSceneStatus());
        assertTrue(response.getItems().get(0).getNeedsAttention());
        assertEquals("WAITING_USER", response.getItems().get(0).getBlockingSummary().getBlockingType());
    }

    @Test
    void getScenesBuildsRunningSceneWithoutAttention() {
        Harness harness = new Harness();
        SceneProjectionContext context = context("scene-run", session("sess-run", "scene-run", "RUNNING", "task-run"), task("task-run", "sess-run", TaskStatus.RUNNING.name(), null));
        TaskDetailResponse detail = baseDetail("task-run", TaskStatus.RUNNING.name(), null);

        when(harness.queryService.loadAllSceneContexts(42L)).thenReturn(List.of(context));
        when(harness.taskService.getTask("task-run", 42L)).thenReturn(detail);

        SceneListResponse response = harness.service.getScenes(42L, null, null, 50);

        assertEquals("RUNNING", response.getItems().get(0).getSceneStatus());
        assertFalse(response.getItems().get(0).getNeedsAttention());
    }

    @Test
    void getScenesCountsOnlyQualifiedReadyResults() {
        Harness harness = new Harness();
        SceneProjectionContext context = context(
                "scene-ready",
                session("sess-ready", "scene-ready", "READY_RESULT", "task-current"),
                task("task-current", "sess-ready", TaskStatus.SUCCEEDED.name(), "rb-not-ready"),
                task("task-ready", "sess-ready", TaskStatus.SUCCEEDED.name(), "rb-ready")
        );
        TaskDetailResponse currentDetail = baseDetail("task-current", TaskStatus.SUCCEEDED.name(), "rb-not-ready");
        TaskDetailResponse readyDetail = readyDetail("task-ready", "rb-ready", "2026-04-13T11:00:00Z");

        when(harness.queryService.loadAllSceneContexts(42L)).thenReturn(List.of(context));
        when(harness.taskService.getTask("task-current", 42L)).thenReturn(currentDetail);
        when(harness.taskService.getTask("task-ready", 42L)).thenReturn(readyDetail);

        SceneListResponse response = harness.service.getScenes(42L, null, null, 50);

        assertEquals("READY_RESULT", response.getItems().get(0).getSceneStatus());
        assertEquals(1, response.getItems().get(0).getResultSummary().getReadyResultCount());
        assertTrue(response.getItems().get(0).getResultSummary().getHasReadyResult());
        assertEquals("rb-ready", response.getItems().get(0).getResultSummary().getLatestResultBundleId());
    }

    @Test
    void getSceneBuildsFailedSceneWithAttention() {
        Harness harness = new Harness();
        SceneProjectionContext context = context("scene-failed", session("sess-failed", "scene-failed", "FAILED", "task-failed"), task("task-failed", "sess-failed", TaskStatus.FAILED.name(), null));
        TaskDetailResponse detail = failedDetail("task-failed", true);

        when(harness.queryService.loadSceneContext(42L, "scene-failed")).thenReturn(context);
        when(harness.taskService.getTask("task-failed", 42L)).thenReturn(detail);
        when(harness.taskService.getTaskAudit("task-failed", 42L)).thenReturn(new TaskAuditResponse());
        when(harness.taskService.getEvents("task-failed", 42L)).thenReturn(new TaskEventsResponse());
        when(harness.sessionService.getSession(42L, "sess-failed")).thenReturn(sessionResponse("sess-failed", "scene-failed", "FAILED", "task-failed"));

        SceneDetailDTO response = harness.service.getScene(42L, "scene-failed");

        assertEquals("FAILED", response.getSceneStatus());
        assertTrue("FAILED_RECOVERABLE".equals(response.getBlockingSummary().getBlockingType()) || "FAILED_FATAL".equals(response.getBlockingSummary().getBlockingType()));
        assertEquals("governance", response.getActionRecommendation().getRecommendedEntry());
    }

    @Test
    void getSceneBuildsCorruptedSceneAsFailedWithAttention() {
        Harness harness = new Harness();
        SceneProjectionContext context = context("scene-corrupt", session("sess-corrupt", "scene-corrupt", "FAILED", "task-corrupt"), task("task-corrupt", "sess-corrupt", TaskStatus.STATE_CORRUPTED.name(), null));
        TaskDetailResponse detail = corruptedDetail("task-corrupt");

        when(harness.queryService.loadSceneContext(42L, "scene-corrupt")).thenReturn(context);
        when(harness.taskService.getTask("task-corrupt", 42L)).thenReturn(detail);
        when(harness.taskService.getTaskAudit("task-corrupt", 42L)).thenReturn(new TaskAuditResponse());
        when(harness.taskService.getEvents("task-corrupt", 42L)).thenReturn(new TaskEventsResponse());
        when(harness.sessionService.getSession(42L, "sess-corrupt")).thenReturn(sessionResponse("sess-corrupt", "scene-corrupt", "FAILED", "task-corrupt"));

        SceneDetailDTO response = harness.service.getScene(42L, "scene-corrupt");

        assertEquals("FAILED", response.getSceneStatus());
        assertEquals("STATE_CORRUPTED", response.getBlockingSummary().getBlockingType());
    }

    @Test
    void getScenesMapsCancelledSceneToArchived() {
        Harness harness = new Harness();
        SceneProjectionContext context = context("scene-cancelled", session("sess-cancelled", "scene-cancelled", "CANCELLED", "task-cancelled"), task("task-cancelled", "sess-cancelled", TaskStatus.CANCELLED.name(), null));
        TaskDetailResponse detail = baseDetail("task-cancelled", TaskStatus.CANCELLED.name(), null);

        when(harness.queryService.loadAllSceneContexts(42L)).thenReturn(List.of(context));
        when(harness.taskService.getTask("task-cancelled", 42L)).thenReturn(detail);

        SceneListResponse response = harness.service.getScenes(42L, null, null, 50);

        assertEquals("ARCHIVED", response.getItems().get(0).getSceneStatus());
        assertFalse(response.getItems().get(0).getNeedsAttention());
    }

    private SceneProjectionContext context(String sceneId, AnalysisSession session, TaskState... tasks) {
        SceneProjectionContext context = new SceneProjectionContext();
        context.setSceneId(sceneId);
        context.getSessions().add(session);
        context.setCurrentSession(session);
        context.getTasks().addAll(List.of(tasks));
        context.setCurrentTask(tasks.length == 0 ? null : tasks[0]);
        return context;
    }

    private AnalysisSession session(String sessionId, String sceneId, String status, String currentTaskId) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(sessionId);
        session.setSceneId(sceneId);
        session.setStatus(status);
        session.setCurrentTaskId(currentTaskId);
        session.setUserGoal("Investigate " + sceneId);
        session.setCreatedAt(OffsetDateTime.parse("2026-04-10T00:00:00Z"));
        session.setUpdatedAt(OffsetDateTime.parse("2026-04-13T10:00:00Z"));
        return session;
    }

    private TaskState task(String taskId, String sessionId, String state, String latestResultBundleId) {
        TaskState task = new TaskState();
        task.setTaskId(taskId);
        task.setSessionId(sessionId);
        task.setCurrentState(state);
        task.setLatestResultBundleId(latestResultBundleId);
        task.setStateVersion(5);
        task.setUpdatedAt(OffsetDateTime.parse("2026-04-13T10:05:00Z"));
        return task;
    }

    private TaskDetailResponse baseDetail(String taskId, String state, String latestResultBundleId) {
        TaskDetailResponse detail = new TaskDetailResponse();
        detail.setTaskId(taskId);
        detail.setState(state);
        detail.setLatestResultBundleId(latestResultBundleId);
        return detail;
    }

    private TaskDetailResponse waitingDetail(String taskId) {
        TaskDetailResponse detail = baseDetail(taskId, TaskStatus.WAITING_USER.name(), null);
        TaskDetailResponse.WaitingContext waiting = new TaskDetailResponse.WaitingContext();
        waiting.setWaitingReasonType("MISSING_REQUIRED_INPUT");
        waiting.setCanResume(true);
        TaskDetailResponse.MissingSlot missingSlot = new TaskDetailResponse.MissingSlot();
        missingSlot.setSlotName("portfolio_snapshot");
        missingSlot.setExpectedType("file/csv");
        missingSlot.setRequired(true);
        waiting.setMissingSlots(List.of(missingSlot));
        TaskDetailResponse.RequiredUserAction action = new TaskDetailResponse.RequiredUserAction();
        action.setActionType("UPLOAD");
        action.setKey("portfolio_snapshot");
        action.setLabel("Upload portfolio snapshot");
        action.setRequired(true);
        waiting.setRequiredUserActions(List.of(action));
        detail.setWaitingContext(waiting);
        return detail;
    }

    private TaskDetailResponse readyDetail(String taskId, String resultBundleId, String createdAt) {
        TaskDetailResponse detail = baseDetail(taskId, TaskStatus.SUCCEEDED.name(), resultBundleId);
        TaskDetailResponse.ResultBundleSummary resultBundleSummary = new TaskDetailResponse.ResultBundleSummary();
        resultBundleSummary.setResultId(resultBundleId);
        resultBundleSummary.setSummary("Governed result is ready");
        resultBundleSummary.setCreatedAt(createdAt);
        detail.setResultBundleSummary(resultBundleSummary);
        return detail;
    }

    private TaskDetailResponse failedDetail(String taskId, boolean repairable) {
        TaskDetailResponse detail = baseDetail(taskId, TaskStatus.FAILED.name(), null);
        TaskDetailResponse.FailureSummary failure = new TaskDetailResponse.FailureSummary();
        failure.setFailureCode("ASSERTION_FAILED");
        failure.setFailureMessage("Execution failed");
        failure.setRepairable(repairable);
        detail.setLastFailureSummary(failure);
        TaskDetailResponse.RepairProposal proposal = new TaskDetailResponse.RepairProposal();
        proposal.setAvailable(repairable);
        proposal.setUserFacingReason("Inputs can be corrected before retrying.");
        detail.setRepairProposal(proposal);
        return detail;
    }

    private TaskDetailResponse corruptedDetail(String taskId) {
        TaskDetailResponse detail = baseDetail(taskId, TaskStatus.STATE_CORRUPTED.name(), null);
        CorruptionStateView state = new CorruptionStateView();
        state.setCorrupted(true);
        state.setReason("State checkpoint is inconsistent.");
        detail.setCorruptionState(state);
        return detail;
    }

    private AnalysisSessionResponse sessionResponse(String sessionId, String sceneId, String status, String taskId) {
        AnalysisSessionResponse response = new AnalysisSessionResponse();
        response.setSessionId(sessionId);
        response.setSceneId(sceneId);
        response.setStatus(status);
        response.setCurrentTaskId(taskId);
        return response;
    }

    private static class Harness {
        private final SceneProjectionQueryService queryService = mock(SceneProjectionQueryService.class);
        private final SessionService sessionService = mock(SessionService.class);
        private final TaskService taskService = mock(TaskService.class);
        private final SceneProjectionService service = new SceneProjectionService(queryService, sessionService, taskService);
    }
}
