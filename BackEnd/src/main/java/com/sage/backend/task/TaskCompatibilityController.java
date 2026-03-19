package com.sage.backend.task;

import com.sage.backend.security.CurrentUser;
import com.sage.backend.task.dto.CancelTaskResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Deprecated(forRemoval = false)
@RestController
@RequestMapping
public class TaskCompatibilityController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskCompatibilityController.class);

    private final TaskService taskService;

    public TaskCompatibilityController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Deprecated(forRemoval = false)
    @GetMapping("/result")
    public ResponseEntity<TaskResultResponse> getResultAlias(
            @RequestParam("task_id") String taskId,
            Authentication authentication
    ) {
        LOGGER.warn("Deprecated alias endpoint called: GET /result?task_id=... ; use GET /tasks/{}/result", taskId);
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.ok(taskService.getTaskResult(taskId, currentUser.userId()));
    }

    @Deprecated(forRemoval = false)
    @PostMapping("/cancel")
    public ResponseEntity<CancelTaskResponse> cancelAlias(
            @RequestParam("task_id") String taskId,
            Authentication authentication
    ) {
        LOGGER.warn("Deprecated alias endpoint called: POST /cancel?task_id=... ; use POST /tasks/{}/cancel", taskId);
        CurrentUser currentUser = (CurrentUser) authentication.getPrincipal();
        return ResponseEntity.accepted().body(taskService.cancelTask(taskId, currentUser.userId()));
    }
}

