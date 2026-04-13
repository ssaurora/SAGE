package com.sage.backend.scene;

import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.TaskState;

import java.util.ArrayList;
import java.util.List;

class SceneProjectionContext {
    private String sceneId;
    private final List<AnalysisSession> sessions = new ArrayList<>();
    private final List<TaskState> tasks = new ArrayList<>();
    private AnalysisSession currentSession;
    private TaskState currentTask;

    public String getSceneId() {
        return sceneId;
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId;
    }

    public List<AnalysisSession> getSessions() {
        return sessions;
    }

    public List<TaskState> getTasks() {
        return tasks;
    }

    public AnalysisSession getCurrentSession() {
        return currentSession;
    }

    public void setCurrentSession(AnalysisSession currentSession) {
        this.currentSession = currentSession;
    }

    public TaskState getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(TaskState currentTask) {
        this.currentTask = currentTask;
    }
}
