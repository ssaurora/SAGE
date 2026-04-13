package com.sage.backend.scene;

import com.sage.backend.mapper.AnalysisSessionMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class SceneProjectionQueryService {

    private final AnalysisSessionMapper analysisSessionMapper;
    private final TaskStateMapper taskStateMapper;

    public SceneProjectionQueryService(
            AnalysisSessionMapper analysisSessionMapper,
            TaskStateMapper taskStateMapper
    ) {
        this.analysisSessionMapper = analysisSessionMapper;
        this.taskStateMapper = taskStateMapper;
    }

    public List<SceneProjectionContext> loadAllSceneContexts(Long userId) {
        return buildContexts(userId, analysisSessionMapper.findSceneLinkedByUserId(userId));
    }

    public SceneProjectionContext loadSceneContext(Long userId, String sceneId) {
        List<SceneProjectionContext> contexts = buildContexts(userId, analysisSessionMapper.findByUserIdAndSceneIdAll(userId, sceneId));
        if (contexts.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Scene not found");
        }
        return contexts.get(0);
    }

    private List<SceneProjectionContext> buildContexts(Long userId, List<AnalysisSession> sessions) {
        Map<String, List<AnalysisSession>> sessionsBySceneId = new LinkedHashMap<>();
        for (AnalysisSession session : sessions) {
            if (!hasText(session.getSceneId())) {
                continue;
            }
            sessionsBySceneId.computeIfAbsent(session.getSceneId(), ignored -> new ArrayList<>()).add(session);
        }

        List<String> sessionIds = sessions.stream()
                .map(AnalysisSession::getSessionId)
                .filter(this::hasText)
                .toList();
        List<TaskState> allTasks = sessionIds.isEmpty()
                ? List.of()
                : taskStateMapper.findByUserIdAndSessionIds(userId, sessionIds);

        Map<String, List<TaskState>> tasksBySessionId = new LinkedHashMap<>();
        for (TaskState task : allTasks) {
            if (!hasText(task.getSessionId())) {
                continue;
            }
            tasksBySessionId.computeIfAbsent(task.getSessionId(), ignored -> new ArrayList<>()).add(task);
        }

        List<SceneProjectionContext> contexts = new ArrayList<>();
        for (Map.Entry<String, List<AnalysisSession>> entry : sessionsBySceneId.entrySet()) {
            SceneProjectionContext context = new SceneProjectionContext();
            context.setSceneId(entry.getKey());
            context.getSessions().addAll(entry.getValue());
            for (AnalysisSession session : entry.getValue()) {
                context.getTasks().addAll(tasksBySessionId.getOrDefault(session.getSessionId(), List.of()));
            }
            context.setCurrentSession(selectCurrentSession(context.getSessions()));
            context.setCurrentTask(selectCurrentTask(context.getCurrentSession(), context.getTasks()));
            contexts.add(context);
        }

        return contexts;
    }

    private AnalysisSession selectCurrentSession(List<AnalysisSession> sessions) {
        return sessions.stream()
                .min(
                        Comparator.comparingInt((AnalysisSession session) -> sessionStatusRank(session.getStatus()))
                                .thenComparingInt(session -> hasText(session.getCurrentTaskId()) ? 0 : 1)
                                .thenComparing(AnalysisSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(AnalysisSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(AnalysisSession::getSessionId, Comparator.nullsLast(String::compareTo))
                )
                .orElse(null);
    }

    private TaskState selectCurrentTask(AnalysisSession currentSession, List<TaskState> tasks) {
        if (currentSession != null && hasText(currentSession.getCurrentTaskId())) {
            for (TaskState task : tasks) {
                if (Objects.equals(currentSession.getCurrentTaskId(), task.getTaskId())) {
                    return task;
                }
            }
        }

        return tasks.stream()
                .min(
                        Comparator.comparingInt((TaskState task) -> belongsToCurrentSessionRank(currentSession, task))
                                .thenComparingInt(task -> taskStateClassRank(task.getCurrentState()))
                                .thenComparingInt(task -> taskStatePrecedence(task.getCurrentState()))
                                .thenComparing(TaskState::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(TaskState::getStateVersion, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(TaskState::getTaskId, Comparator.nullsLast(String::compareTo))
                )
                .orElse(null);
    }

    private int sessionStatusRank(String status) {
        return switch (safe(status)) {
            case "WAITING_USER" -> 0;
            case "RUNNING" -> 1;
            case "READY_RESULT" -> 2;
            case "FAILED" -> 3;
            case "CANCELLED" -> 4;
            default -> 5;
        };
    }

    private int belongsToCurrentSessionRank(AnalysisSession currentSession, TaskState task) {
        if (currentSession == null || !hasText(currentSession.getSessionId())) {
            return 1;
        }
        return Objects.equals(currentSession.getSessionId(), task.getSessionId()) ? 0 : 1;
    }

    private int taskStateClassRank(String state) {
        String safeState = safe(state);
        if (TaskStatus.WAITING_USER.name().equals(safeState)) {
            return 0;
        }
        if (isRunningClassState(safeState)) {
            return 1;
        }
        if (TaskStatus.FAILED.name().equals(safeState)) {
            return 2;
        }
        if (TaskStatus.STATE_CORRUPTED.name().equals(safeState)) {
            return 3;
        }
        if (TaskStatus.SUCCEEDED.name().equals(safeState)) {
            return 4;
        }
        if (TaskStatus.CANCELLED.name().equals(safeState)) {
            return 5;
        }
        return 6;
    }

    private int taskStatePrecedence(String state) {
        return switch (safe(state)) {
            case "WAITING_USER" -> 0;
            case "RESUMING" -> 1;
            case "RUNNING" -> 2;
            case "RESULT_PROCESSING" -> 3;
            case "ARTIFACT_PROMOTING" -> 4;
            case "QUEUED" -> 5;
            case "VALIDATING" -> 6;
            case "PLANNING" -> 7;
            case "COGNIZING" -> 8;
            case "CREATED" -> 9;
            case "FAILED" -> 10;
            case "STATE_CORRUPTED" -> 11;
            case "SUCCEEDED" -> 12;
            case "CANCELLED" -> 13;
            default -> 14;
        };
    }

    private boolean isRunningClassState(String state) {
        return TaskStatus.RESUMING.name().equals(state)
                || TaskStatus.RUNNING.name().equals(state)
                || TaskStatus.RESULT_PROCESSING.name().equals(state)
                || TaskStatus.ARTIFACT_PROMOTING.name().equals(state)
                || TaskStatus.QUEUED.name().equals(state)
                || TaskStatus.VALIDATING.name().equals(state)
                || TaskStatus.PLANNING.name().equals(state)
                || TaskStatus.COGNIZING.name().equals(state)
                || TaskStatus.CREATED.name().equals(state);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
