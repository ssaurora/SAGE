package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sage.backend.audit.AuditService;
import com.sage.backend.common.TaskIdGenerator;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.event.EventService;
import com.sage.backend.model.EventLog;
import com.sage.backend.model.EventType;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.dto.Pass1Request;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TaskService {

    private final TaskStateMapper taskStateMapper;
    private final EventService eventService;
    private final AuditService auditService;
    private final Pass1Client pass1Client;
    private final ObjectMapper objectMapper;

    public TaskService(
            TaskStateMapper taskStateMapper,
            EventService eventService,
            AuditService auditService,
            Pass1Client pass1Client,
            ObjectMapper objectMapper
    ) {
        this.taskStateMapper = taskStateMapper;
        this.eventService = eventService;
        this.auditService = auditService;
        this.pass1Client = pass1Client;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CreateTaskResponse createTask(Long userId, CreateTaskRequest request) {
        String traceId = UUID.randomUUID().toString();
        String taskId = TaskIdGenerator.generate();

        TaskState taskState = new TaskState();
        taskState.setTaskId(taskId);
        taskState.setUserId(userId);
        taskState.setCurrentState(TaskStatus.CREATED.name());
        taskState.setStateVersion(0);
        taskState.setUserQuery(request.getUserQuery());
        taskStateMapper.insert(taskState);

        appendEvent(taskId, EventType.TASK_CREATED.name(), null, TaskStatus.CREATED.name(), 0, null);

        ensureUpdated(taskStateMapper.updateState(taskId, 0, TaskStatus.COGNIZING.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), TaskStatus.CREATED.name(), TaskStatus.COGNIZING.name(), 1, null);
        appendEvent(taskId, EventType.PLANNING_PASS1_STARTED.name(), null, null, 1, null);

        try {
            Pass1Request pass1Request = new Pass1Request();
            pass1Request.setTaskId(taskId);
            pass1Request.setUserQuery(request.getUserQuery());
            pass1Request.setStateVersion(1);

            Pass1Response pass1Response = pass1Client.runPass1(pass1Request);
            String pass1Json = objectMapper.writeValueAsString(pass1Response);

            ensureUpdated(taskStateMapper.updateStateAndPass1(taskId, 1, TaskStatus.PLANNING.name(), pass1Json));
            appendEvent(
                    taskId,
                    EventType.PLANNING_PASS1_COMPLETED.name(),
                    null,
                    null,
                    2,
                    objectMapper.writeValueAsString(Map.of("selected_template", pass1Response.getSelectedTemplate()))
            );

            auditService.appendAudit(
                    taskId,
                    "TASK_CREATE",
                    "SUCCESS",
                    traceId,
                    objectMapper.writeValueAsString(Map.of("state", "PLANNING"))
            );

            CreateTaskResponse response = new CreateTaskResponse();
            response.setTaskId(taskId);
            response.setState(TaskStatus.PLANNING.name());
            response.setStateVersion(2);
            return response;
        } catch (Exception exception) {
            handlePass1Failure(taskId, traceId, exception);
            throw new ResponseStatusException(BAD_GATEWAY, "Pass1 invocation failed", exception);
        }
    }

    public TaskDetailResponse getTask(String taskId, Long userId) {
        TaskState taskState = taskStateMapper.findByTaskId(taskId);
        if (taskState == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        if (!taskState.getUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        TaskDetailResponse response = new TaskDetailResponse();
        response.setTaskId(taskState.getTaskId());
        response.setState(taskState.getCurrentState());
        response.setStateVersion(taskState.getStateVersion());
        response.setPass1Summary(buildPass1Summary(taskState.getPass1ResultJson()));
        return response;
    }

    public TaskEventsResponse getEvents(String taskId, Long userId) {
        TaskState taskState = taskStateMapper.findByTaskId(taskId);
        if (taskState == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        if (!taskState.getUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        List<EventLog> events = eventService.findByTaskId(taskId);
        TaskEventsResponse response = new TaskEventsResponse();
        for (EventLog event : events) {
            TaskEventsResponse.EventItem item = new TaskEventsResponse.EventItem();
            item.setEventType(event.getEventType());
            item.setFromState(event.getFromState());
            item.setToState(event.getToState());
            item.setStateVersion(event.getStateVersion());
            item.setCreatedAt(event.getCreatedAt().toString());
            response.getItems().add(item);
        }
        return response;
    }

    private void appendEvent(
            String taskId,
            String eventType,
            String fromState,
            String toState,
            int stateVersion,
            String payloadJson
    ) {
        eventService.appendEvent(taskId, eventType, fromState, toState, stateVersion, payloadJson);
    }

    private TaskDetailResponse.Pass1Summary buildPass1Summary(String pass1ResultJson) {
        if (pass1ResultJson == null || pass1ResultJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(pass1ResultJson);
            TaskDetailResponse.Pass1Summary summary = new TaskDetailResponse.Pass1Summary();
            summary.setSelectedTemplate(root.path("selected_template").asText(null));
            JsonNode rolesNode = root.path("logical_input_roles");
            summary.setLogicalInputRolesCount(rolesNode.isArray() ? rolesNode.size() : 0);
            summary.setSlotSchemaViewVersion("v1");
            return summary;
        } catch (Exception exception) {
            return null;
        }
    }

    private void handlePass1Failure(String taskId, String traceId, Exception exception) {
        try {
            if (taskStateMapper.updateState(taskId, 1, TaskStatus.FAILED.name()) > 0) {
                appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, 2, null);
            }

            String detailJson = objectMapper.writeValueAsString(
                    Map.of(
                            "error", exception.getClass().getSimpleName(),
                            "message", exception.getMessage(),
                            "at", OffsetDateTime.now().toString()
                    )
            );
            auditService.appendAudit(taskId, "TASK_CREATE", "FAILED", traceId, detailJson);
        } catch (Exception ignored) {
        }
    }

    private void ensureUpdated(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("State version conflict");
        }
    }
}
