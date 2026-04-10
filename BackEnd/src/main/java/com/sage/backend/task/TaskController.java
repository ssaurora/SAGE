package com.sage.backend.task;

import com.sage.backend.security.CurrentUser;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.CancelTaskResponse;
import com.sage.backend.task.dto.ForceRevertCheckpointRequest;
import com.sage.backend.task.dto.ForceRevertCheckpointResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import com.sage.backend.task.dto.TaskArtifactsResponse;
import com.sage.backend.task.dto.TaskAuditResponse;
import com.sage.backend.task.dto.TaskCatalogResponse;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.ResumeTaskResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskRunsResponse;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<CreateTaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.createTask(currentUser.userId(), request));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskDetailResponse> getTask(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTask(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/events")
    public ResponseEntity<TaskEventsResponse> getTaskEvents(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getEvents(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/result")
    public ResponseEntity<TaskResultResponse> getTaskResult(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskResult(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/manifest")
    public ResponseEntity<TaskManifestResponse> getTaskManifest(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskManifest(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/runs")
    public ResponseEntity<TaskRunsResponse> getTaskRuns(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskRuns(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/artifacts")
    public ResponseEntity<TaskArtifactsResponse> getTaskArtifacts(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskArtifacts(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/audit")
    public ResponseEntity<TaskAuditResponse> getTaskAudit(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskAudit(taskId, currentUser.userId()));
    }

    @GetMapping("/{taskId}/catalog")
    public ResponseEntity<TaskCatalogResponse> getTaskCatalog(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskCatalog(taskId, currentUser.userId()));
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<CancelTaskResponse> cancelTask(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.accepted().body(taskService.cancelTask(taskId, currentUser.userId()));
    }

    @PostMapping(value = "/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAttachmentResponse> uploadAttachment(
            @PathVariable String taskId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "logical_slot", required = false) String logicalSlot,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        UploadAttachmentResponse response = taskService.uploadAttachment(taskId, currentUser.userId(), file, logicalSlot);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<ResumeTaskResponse> resumeTask(
            @PathVariable String taskId,
            @RequestBody ResumeTaskRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.resumeTask(taskId, currentUser.userId(), request));
    }

    @PostMapping("/{taskId}/force-revert-checkpoint")
    public ResponseEntity<ForceRevertCheckpointResponse> forceRevertCheckpoint(
            @PathVariable String taskId,
            @RequestBody ForceRevertCheckpointRequest request,
            Authentication authentication
    ) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.forceRevertCheckpoint(taskId, currentUser, request));
    }

    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@PathVariable String taskId, Authentication authentication) {
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return taskService.streamTask(taskId, currentUser.userId());
    }
}
