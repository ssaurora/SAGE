package com.sage.backend.task;

import com.sage.backend.security.CurrentUser;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

