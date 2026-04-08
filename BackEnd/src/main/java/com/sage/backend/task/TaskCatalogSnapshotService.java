package com.sage.backend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.TaskCatalogSnapshotMapper;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskCatalogSnapshot;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskCatalogSnapshotService {

    private final TaskCatalogSnapshotMapper taskCatalogSnapshotMapper;
    private final ObjectMapper objectMapper;

    public TaskCatalogSnapshotService(TaskCatalogSnapshotMapper taskCatalogSnapshotMapper, ObjectMapper objectMapper) {
        this.taskCatalogSnapshotMapper = taskCatalogSnapshotMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> resolveCatalogSummary(String taskId, List<TaskAttachment> attachments, int inventoryVersion) {
        TaskCatalogSnapshot snapshot = resolveCatalogSnapshot(taskId, attachments, inventoryVersion);
        if (snapshot != null) {
            Map<String, Object> snapshotSummary = readJsonMap(snapshot.getCatalogSummaryJson());
            if (snapshotSummary != null && !snapshotSummary.isEmpty()) {
                return snapshotSummary;
            }
        }
        return AttachmentCatalogProjector.buildCatalogSummary(attachments, inventoryVersion);
    }

    public Map<String, Object> resolveManifestCatalogSummary(
            Map<String, Object> frozenSummary,
            String taskId,
            List<TaskAttachment> attachments,
            int inventoryVersion
    ) {
        if (frozenSummary != null && !frozenSummary.isEmpty()) {
            return frozenSummary;
        }
        return resolveCatalogSummary(taskId, attachments, inventoryVersion);
    }

    public TaskCatalogSnapshot persistCatalogSnapshot(String taskId, List<TaskAttachment> attachments, int inventoryVersion) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        TaskCatalogSnapshot existing = taskCatalogSnapshotMapper.findByTaskIdAndInventoryVersion(taskId, inventoryVersion);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> projectedSummary = AttachmentCatalogProjector.buildCatalogSummary(attachments, inventoryVersion);
        Map<String, Object> persistedSummary = new LinkedHashMap<>(projectedSummary);
        if (!persistedSummary.isEmpty()) {
            persistedSummary.put("catalog_source", "task_catalog_snapshot");
        }
        TaskCatalogSnapshot snapshot = new TaskCatalogSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setInventoryVersion(inventoryVersion);
        snapshot.setCatalogRevision(AttachmentCatalogProjector.extractCatalogRevision(persistedSummary));
        snapshot.setCatalogFingerprint(AttachmentCatalogProjector.extractCatalogFingerprint(persistedSummary));
        snapshot.setCatalogSummaryJson(writeJson(persistedSummary));
        snapshot.setCatalogFactsJson(writeJson(AttachmentCatalogProjector.project(attachments)));
        snapshot.setCatalogSource("task_attachment_projection");
        int inserted = taskCatalogSnapshotMapper.insert(snapshot);
        if (inserted <= 0) {
            throw new IllegalStateException("Expected insert to affect at least one row");
        }
        return snapshot;
    }

    private TaskCatalogSnapshot resolveCatalogSnapshot(String taskId, List<TaskAttachment> attachments, int inventoryVersion) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        TaskCatalogSnapshot existing = taskCatalogSnapshotMapper.findByTaskIdAndInventoryVersion(taskId, inventoryVersion);
        if (existing != null) {
            return existing;
        }
        return persistCatalogSnapshot(taskId, attachments, inventoryVersion);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize task catalog snapshot payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            Object value = objectMapper.readValue(sourceJson, Map.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
