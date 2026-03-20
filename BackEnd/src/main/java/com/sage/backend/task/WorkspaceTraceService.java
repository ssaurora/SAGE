package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.mapper.ArtifactIndexMapper;
import com.sage.backend.mapper.ResultBundleRecordMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.mapper.WorkspaceRegistryMapper;
import com.sage.backend.model.ArtifactIndexRecord;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.ResultBundleRecord;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.WorkspaceRegistry;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class WorkspaceTraceService {
    private final WorkspaceRegistryMapper workspaceRegistryMapper;
    private final ResultBundleRecordMapper resultBundleRecordMapper;
    private final ArtifactIndexMapper artifactIndexMapper;
    private final TaskStateMapper taskStateMapper;
    private final ObjectMapper objectMapper;

    public WorkspaceTraceService(
            WorkspaceRegistryMapper workspaceRegistryMapper,
            ResultBundleRecordMapper resultBundleRecordMapper,
            ArtifactIndexMapper artifactIndexMapper,
            TaskStateMapper taskStateMapper,
            ObjectMapper objectMapper
    ) {
        this.workspaceRegistryMapper = workspaceRegistryMapper;
        this.resultBundleRecordMapper = resultBundleRecordMapper;
        this.artifactIndexMapper = artifactIndexMapper;
        this.taskStateMapper = taskStateMapper;
        this.objectMapper = objectMapper;
    }

    public void createWorkspaceRecord(String workspaceId, String taskId, String jobId, int attemptNo, String runtimeProfile) {
        WorkspaceRegistry record = new WorkspaceRegistry();
        record.setWorkspaceId(workspaceId);
        record.setTaskId(taskId);
        record.setJobId(jobId);
        record.setAttemptNo(attemptNo);
        record.setRuntimeProfile(runtimeProfile);
        record.setHostWorkspacePath("PENDING");
        record.setWorkspaceState("CREATED");
        workspaceRegistryMapper.insert(record);
        taskStateMapper.updateLatestTracePointers(taskId, null, workspaceId);
    }

    public void updateWorkspaceFromStatus(JobRecord jobRecord, JsonNode workspaceSummary, JsonNode dockerRuntimeEvidence, boolean terminal) {
        if (jobRecord.getWorkspaceId() == null || jobRecord.getWorkspaceId().isBlank()) {
            return;
        }
        WorkspaceRegistry existing = workspaceRegistryMapper.findByWorkspaceId(jobRecord.getWorkspaceId());
        String containerName = dockerRuntimeEvidence == null ? null : dockerRuntimeEvidence.path("container_name").asText(null);
        String hostWorkspacePath = workspaceSummary == null ? null : workspaceSummary.path("workspace_output_path").asText(null);
        String archivePath = workspaceSummary == null ? null : workspaceSummary.path("archive_path").asText(null);
        if (containerName == null && existing != null) {
            containerName = existing.getContainerName();
        }
        if (hostWorkspacePath == null && existing != null) {
            hostWorkspacePath = existing.getHostWorkspacePath();
        }
        if (archivePath == null && existing != null) {
            archivePath = existing.getArchivePath();
        }
        boolean cleaned = workspaceSummary != null && workspaceSummary.path("cleanup_completed").asBoolean(false);
        boolean archived = workspaceSummary != null && workspaceSummary.path("archive_completed").asBoolean(false);
        String state = terminal ? (cleaned ? "CLEANED" : (archived ? "ARCHIVED" : "FAILED_CLEANUP")) : "RUNNING";
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        workspaceRegistryMapper.updateSnapshot(
                jobRecord.getWorkspaceId(),
                containerName,
                hostWorkspacePath,
                archivePath,
                state,
                jobRecord.getStartedAt(),
                terminal ? jobRecord.getFinishedAt() : null,
                cleaned ? now : null,
                archived ? now : null,
                now
        );
        taskStateMapper.updateLatestTracePointers(jobRecord.getTaskId(), null, jobRecord.getWorkspaceId());
    }

    public void persistSuccess(TaskState taskState, JobRecord jobRecord, JsonNode resultBundle, JsonNode finalExplanation, JsonNode artifactCatalog) throws Exception {
        String resultBundleId = resultBundle.path("result_id").asText();
        ResultBundleRecord record = new ResultBundleRecord();
        record.setResultBundleId(resultBundleId);
        record.setTaskId(taskState.getTaskId());
        record.setJobId(jobRecord.getJobId());
        record.setAttemptNo(jobRecord.getAttemptNo());
        record.setManifestId(taskState.getActiveManifestId());
        record.setWorkspaceId(jobRecord.getWorkspaceId());
        record.setResultBundleJson(objectMapper.writeValueAsString(resultBundle));
        record.setFinalExplanationJson(finalExplanation == null || finalExplanation.isNull() ? null : objectMapper.writeValueAsString(finalExplanation));
        record.setSummaryText(resultBundle.path("summary").asText(""));
        resultBundleRecordMapper.insert(record);
        taskStateMapper.updateLatestTracePointers(taskState.getTaskId(), resultBundleId, jobRecord.getWorkspaceId());
        persistArtifacts(taskState.getTaskId(), jobRecord, resultBundleId, artifactCatalog);
    }

    public void persistArtifacts(String taskId, JobRecord jobRecord, String resultBundleId, JsonNode artifactCatalog) {
        if (artifactCatalog == null || artifactCatalog.isNull()) {
            return;
        }
        insertArtifacts(taskId, jobRecord, resultBundleId, artifactCatalog.path("primary_outputs"), "PRIMARY_OUTPUT");
        insertArtifacts(taskId, jobRecord, resultBundleId, artifactCatalog.path("intermediate_outputs"), "INTERMEDIATE_OUTPUT");
        insertArtifacts(taskId, jobRecord, resultBundleId, artifactCatalog.path("audit_artifacts"), "AUDIT_ARTIFACT");
        insertArtifacts(taskId, jobRecord, resultBundleId, artifactCatalog.path("derived_outputs"), "DERIVED_OUTPUT");
        insertArtifacts(taskId, jobRecord, resultBundleId, artifactCatalog.path("logs"), "LOG");
    }

    private void insertArtifacts(String taskId, JobRecord jobRecord, String resultBundleId, JsonNode items, String expectedRole) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (Iterator<JsonNode> it = items.elements(); it.hasNext(); ) {
            JsonNode item = it.next();
            ArtifactIndexRecord record = new ArtifactIndexRecord();
            record.setArtifactId(item.path("artifact_id").asText());
            record.setTaskId(taskId);
            record.setJobId(jobRecord.getJobId());
            record.setAttemptNo(jobRecord.getAttemptNo());
            record.setWorkspaceId(jobRecord.getWorkspaceId());
            record.setResultBundleId(resultBundleId);
            record.setArtifactRole(item.path("artifact_role").asText(expectedRole));
            record.setLogicalName(item.path("logical_name").asText(""));
            record.setRelativePath(item.path("relative_path").asText(""));
            record.setAbsolutePath(item.path("absolute_path").asText(null));
            record.setContentType(item.path("content_type").asText(null));
            record.setSizeBytes(item.path("size_bytes").isNumber() ? item.path("size_bytes").asLong() : null);
            record.setSha256(item.path("sha256").asText(null));
            artifactIndexMapper.insert(record);
        }
    }

    public List<WorkspaceRegistry> findRuns(String taskId) {
        return workspaceRegistryMapper.findByTaskId(taskId);
    }

    public List<ArtifactIndexRecord> findArtifacts(String taskId) {
        return artifactIndexMapper.findByTaskId(taskId);
    }
}
