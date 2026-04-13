package com.sage.backend.scene;

import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneProjectionQueryServiceTest {

    @Test
    void loadSceneContextSelectsCurrentSessionByStatusThenCurrentTaskThenTime() {
        AnalysisSessionMapper sessionMapper = mock(AnalysisSessionMapper.class);
        TaskStateMapper taskStateMapper = mock(TaskStateMapper.class);
        SceneProjectionQueryService service = new SceneProjectionQueryService(sessionMapper, taskStateMapper);

        AnalysisSession waitingWithoutTask = session("sess_wait_1", "scene-1", "WAITING_USER", null, "2026-04-13T12:00:00Z");
        AnalysisSession readyResult = session("sess_ready", "scene-1", "READY_RESULT", "task-ready", "2026-04-13T15:00:00Z");
        AnalysisSession running = session("sess_run", "scene-1", "RUNNING", "task-run", "2026-04-13T14:00:00Z");
        AnalysisSession waitingWithTask = session("sess_wait_2", "scene-1", "WAITING_USER", "task-wait", "2026-04-13T10:00:00Z");

        when(sessionMapper.findByUserIdAndSceneIdAll(42L, "scene-1"))
                .thenReturn(List.of(waitingWithoutTask, readyResult, running, waitingWithTask));
        when(taskStateMapper.findByUserIdAndSessionIds(42L, List.of("sess_wait_1", "sess_ready", "sess_run", "sess_wait_2")))
                .thenReturn(List.of(task("task-wait", "sess_wait_2", TaskStatus.WAITING_USER.name(), 3, "2026-04-13T10:05:00Z")));

        SceneProjectionContext context = service.loadSceneContext(42L, "scene-1");

        assertEquals("sess_wait_2", context.getCurrentSession().getSessionId());
        assertEquals("task-wait", context.getCurrentTask().getTaskId());
    }

    @Test
    void loadSceneContextFallsBackToSelectedCurrentSessionTasksBeforeOtherSessionTasks() {
        AnalysisSessionMapper sessionMapper = mock(AnalysisSessionMapper.class);
        TaskStateMapper taskStateMapper = mock(TaskStateMapper.class);
        SceneProjectionQueryService service = new SceneProjectionQueryService(sessionMapper, taskStateMapper);

        AnalysisSession runningSession = session("sess-run", "scene-2", "RUNNING", "missing-task", "2026-04-13T12:00:00Z");
        AnalysisSession readySession = session("sess-ready", "scene-2", "READY_RESULT", null, "2026-04-13T13:00:00Z");

        TaskState runningTaskInCurrentSession = task("task-run", "sess-run", TaskStatus.RUNNING.name(), 4, "2026-04-13T12:01:00Z");
        TaskState waitingTaskInOtherSession = task("task-wait", "sess-ready", TaskStatus.WAITING_USER.name(), 9, "2026-04-13T12:02:00Z");

        when(sessionMapper.findByUserIdAndSceneIdAll(42L, "scene-2"))
                .thenReturn(List.of(runningSession, readySession));
        when(taskStateMapper.findByUserIdAndSessionIds(42L, List.of("sess-run", "sess-ready")))
                .thenReturn(List.of(runningTaskInCurrentSession, waitingTaskInOtherSession));

        SceneProjectionContext context = service.loadSceneContext(42L, "scene-2");

        assertEquals("sess-run", context.getCurrentSession().getSessionId());
        assertEquals("task-run", context.getCurrentTask().getTaskId());
    }

    private AnalysisSession session(String sessionId, String sceneId, String status, String currentTaskId, String updatedAt) {
        AnalysisSession session = new AnalysisSession();
        session.setSessionId(sessionId);
        session.setSceneId(sceneId);
        session.setStatus(status);
        session.setCurrentTaskId(currentTaskId);
        session.setCreatedAt(OffsetDateTime.parse("2026-04-10T00:00:00Z"));
        session.setUpdatedAt(OffsetDateTime.parse(updatedAt));
        return session;
    }

    private TaskState task(String taskId, String sessionId, String state, int stateVersion, String updatedAt) {
        TaskState task = new TaskState();
        task.setTaskId(taskId);
        task.setSessionId(sessionId);
        task.setCurrentState(state);
        task.setStateVersion(stateVersion);
        task.setUpdatedAt(OffsetDateTime.parse(updatedAt));
        return task;
    }
}
