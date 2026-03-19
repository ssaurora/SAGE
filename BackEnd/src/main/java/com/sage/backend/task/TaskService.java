package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.audit.AuditService;
import com.sage.backend.cognition.CognitionPassBClient;
import com.sage.backend.cognition.dto.CognitionPassBRequest;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.common.TaskIdGenerator;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.JobRuntimeClient;
import com.sage.backend.execution.dto.CancelJobResponse;
import com.sage.backend.execution.dto.CreateJobRequest;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.execution.dto.JobStatusResponse;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.RepairRecordMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.mapper.TaskAttemptMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.EventLog;
import com.sage.backend.model.EventType;
import com.sage.backend.model.InputChainStatus;
import com.sage.backend.model.JobRecord;
import com.sage.backend.model.JobState;
import com.sage.backend.model.RepairRecord;
import com.sage.backend.model.TaskAttachment;
import com.sage.backend.model.TaskAttempt;
import com.sage.backend.model.TaskState;
import com.sage.backend.model.TaskStatus;
import com.sage.backend.planning.Pass1Client;
import com.sage.backend.planning.Pass2Client;
import com.sage.backend.planning.dto.Pass1Request;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.planning.dto.Pass2Request;
import com.sage.backend.planning.dto.Pass2Response;
import com.sage.backend.task.dto.CancelTaskResponse;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.ResumeTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.task.dto.TaskStreamResponse;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import com.sage.backend.validationgate.ValidationClient;
import com.sage.backend.validationgate.dto.PrimitiveValidationRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import com.sage.backend.repair.RepairDecision;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class TaskService {
    private static final String CANCEL_REASON_USER_REQUESTED = "USER_REQUESTED";

    private final TaskStateMapper taskStateMapper;
    private final JobRecordMapper jobRecordMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;
    private final RepairRecordMapper repairRecordMapper;
    private final TaskAttemptMapper taskAttemptMapper;
    private final EventService eventService;
    private final AuditService auditService;
    private final Pass1Client pass1Client;
    private final CognitionPassBClient cognitionPassBClient;
    private final ValidationClient validationClient;
    private final Pass2Client pass2Client;
    private final JobRuntimeClient jobRuntimeClient;
    private final RepairDispatcherService repairDispatcherService;
    private final RepairProposalService repairProposalService;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public TaskService(
            TaskStateMapper taskStateMapper,
            JobRecordMapper jobRecordMapper,
            TaskAttachmentMapper taskAttachmentMapper,
            RepairRecordMapper repairRecordMapper,
            TaskAttemptMapper taskAttemptMapper,
            EventService eventService,
            AuditService auditService,
            Pass1Client pass1Client,
            CognitionPassBClient cognitionPassBClient,
            ValidationClient validationClient,
            Pass2Client pass2Client,
            JobRuntimeClient jobRuntimeClient,
            RepairDispatcherService repairDispatcherService,
            RepairProposalService repairProposalService,
            ObjectMapper objectMapper,
            @Value("${sage.upload.root:BackEnd/runtime/uploads}") String uploadRoot
    ) {
        this.taskStateMapper = taskStateMapper;
        this.jobRecordMapper = jobRecordMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
        this.repairRecordMapper = repairRecordMapper;
        this.taskAttemptMapper = taskAttemptMapper;
        this.eventService = eventService;
        this.auditService = auditService;
        this.pass1Client = pass1Client;
        this.cognitionPassBClient = cognitionPassBClient;
        this.validationClient = validationClient;
        this.pass2Client = pass2Client;
        this.jobRuntimeClient = jobRuntimeClient;
        this.repairDispatcherService = repairDispatcherService;
        this.repairProposalService = repairProposalService;
        this.objectMapper = objectMapper;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
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
        taskState.setResumeAttemptCount(0);
        taskState.setActiveAttemptNo(1);
        taskStateMapper.insert(taskState);

        TaskAttempt initialAttempt = new TaskAttempt();
        initialAttempt.setTaskId(taskId);
        initialAttempt.setAttemptNo(1);
        initialAttempt.setTrigger("CREATE");
        initialAttempt.setStatusSnapshotJson("{\"state\":\"CREATED\",\"state_version\":0}");
        ensureInserted(taskAttemptMapper.insert(initialAttempt));

        int currentVersion = 0;
        TaskStatus currentState = TaskStatus.CREATED;
        appendEvent(taskId, EventType.TASK_CREATED.name(), null, TaskStatus.CREATED.name(), currentVersion, null);

        try {
            ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.COGNIZING.name()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.COGNIZING.name(), currentVersion + 1, null);
            currentVersion += 1;
            currentState = TaskStatus.COGNIZING;

            appendEvent(taskId, EventType.PLANNING_PASS1_STARTED.name(), null, null, currentVersion, null);
            Pass1Response pass1Response = runPass1(taskId, request.getUserQuery(), currentVersion);
            String pass1Json = objectMapper.writeValueAsString(pass1Response);
            ensureUpdated(taskStateMapper.updateStateAndPass1(taskId, currentVersion, TaskStatus.PLANNING.name(), pass1Json));
            appendEvent(taskId, EventType.PLANNING_PASS1_COMPLETED.name(), null, null, currentVersion + 1, objectMapper.writeValueAsString(Map.of("selected_template", safeString(pass1Response.getSelectedTemplate()))));
            currentVersion += 1;
            currentState = TaskStatus.PLANNING;

            JsonNode pass1Node = objectMapper.readTree(pass1Json);
            appendEvent(taskId, EventType.COGNITION_PASSB_STARTED.name(), null, null, currentVersion, null);
            CognitionPassBResponse passBResponse = runPassB(taskId, request.getUserQuery(), currentVersion, pass1Node);
            String passBJson = objectMapper.writeValueAsString(passBResponse);
            JsonNode passBNode = objectMapper.readTree(passBJson);
            appendEvent(taskId, EventType.COGNITION_PASSB_COMPLETED.name(), null, null, currentVersion, objectMapper.writeValueAsString(Map.of("binding_count", safeSize(passBResponse.getSlotBindings()))));

            ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.VALIDATING.name()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.VALIDATING.name(), currentVersion + 1, null);
            currentVersion += 1;
            currentState = TaskStatus.VALIDATING;

            appendEvent(taskId, EventType.VALIDATION_STARTED.name(), null, null, currentVersion, null);
            PrimitiveValidationResponse validationResponse = runValidation(taskId, currentVersion, pass1Node, passBNode);
            String validationSummaryJson = objectMapper.writeValueAsString(validationResponse);
            JsonNode validationNode = objectMapper.readTree(validationSummaryJson);
            String inputChainStatus = deriveInputChainStatus(validationResponse);
            CognitionPassBResponse effectivePassB = objectMapper.treeToValue(passBNode, CognitionPassBResponse.class);
            appendEvent(taskId, Boolean.TRUE.equals(validationResponse.getIsValid()) ? EventType.VALIDATION_PASSED.name() : EventType.VALIDATION_FAILED.name(), null, null, currentVersion,
                    objectMapper.writeValueAsString(Map.of("error_code", safeString(validationResponse.getErrorCode()), "missing_roles", safeList(validationResponse.getMissingRoles()), "missing_params", safeList(validationResponse.getMissingParams()))));

            RepairDecision repairDecision = null;
            TaskStatus nextStateAfterValidation = TaskStatus.PLANNING;
            if (!Boolean.TRUE.equals(validationResponse.getIsValid())) {
                List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
                repairDecision = repairDispatcherService.decide(validationNode, attachments);
                if ("FAILED".equalsIgnoreCase(repairDecision.routing()) || "FATAL".equalsIgnoreCase(repairDecision.severity())) {
                    nextStateAfterValidation = TaskStatus.FAILED;
                } else {
                    nextStateAfterValidation = TaskStatus.WAITING_USER;
                }
            }
            ensureUpdated(taskStateMapper.updateStateWithInputChain(taskId, currentVersion, nextStateAfterValidation.name(), passBJson,
                    buildSlotBindingsSummaryJson(passBResponse), buildArgsDraftSummaryJson(passBResponse), validationSummaryJson, inputChainStatus));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), nextStateAfterValidation.name(), currentVersion + 1, null);
            currentVersion += 1;
            currentState = nextStateAfterValidation;

            if (!Boolean.TRUE.equals(validationResponse.getIsValid())) {
                if (TaskStatus.FAILED.equals(nextStateAfterValidation)) {
                    String failureSummaryJson = writeJson(Map.of(
                            "failure_code", "FATAL_VALIDATION",
                            "failure_message", "Task validation is fatal and not recoverable in Week5.",
                            "error_code", safeString(validationResponse.getErrorCode()),
                            "invalid_bindings", safeList(validationResponse.getInvalidBindings()),
                            "missing_roles", safeList(validationResponse.getMissingRoles()),
                            "missing_params", safeList(validationResponse.getMissingParams()),
                            "created_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    ));
                    taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                    appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion, failureSummaryJson);
                    auditService.appendAudit(taskId, "TASK_CREATE", "FAILED", traceId,
                            objectMapper.writeValueAsString(Map.of("state", currentState.name(), "validation_is_valid", false, "input_chain_status", inputChainStatus, "job_created", false, "failure_code", "FATAL_VALIDATION")));

                    CreateTaskResponse response = new CreateTaskResponse();
                    response.setTaskId(taskId);
                    response.setState(currentState.name());
                    response.setStateVersion(currentVersion);
                    return response;
                }

                String waitingContextJson = writeJson(repairDecision.waitingContext());
                taskStateMapper.updateWaitingContext(taskId, waitingContextJson, repairDecision.waitingContext().path("waiting_reason_type").asText("REPAIR_REQUIRED"));

                JsonNode repairProposal = repairProposalService.generate(repairDecision.waitingContext(), validationNode, null, null);
                RepairRecord repairRecord = new RepairRecord();
                repairRecord.setTaskId(taskId);
                repairRecord.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
                repairRecord.setDispatcherOutputJson(writeJson(Map.of("severity", repairDecision.severity(), "routing", repairDecision.routing())));
                repairRecord.setRepairProposalJson(writeJson(repairProposal));
                repairRecord.setResult("REJECTED");
                repairRecordMapper.insert(repairRecord);

                appendEvent(taskId, EventType.WAITING_USER_ENTERED.name(), null, null, currentVersion, waitingContextJson);

                auditService.appendAudit(taskId, "TASK_CREATE", "SUCCESS", traceId,
                        objectMapper.writeValueAsString(Map.of("state", currentState.name(), "validation_is_valid", false, "input_chain_status", inputChainStatus, "job_created", false)));
                CreateTaskResponse response = new CreateTaskResponse();
                response.setTaskId(taskId);
                response.setState(currentState.name());
                response.setStateVersion(currentVersion);
                return response;
            }

            appendEvent(taskId, EventType.PLANNING_PASS2_STARTED.name(), null, null, currentVersion, null);
            Pass2Response pass2Response = runPass2(taskId, currentVersion, pass1Node, passBNode, validationNode);
            String pass2Json = objectMapper.writeValueAsString(pass2Response);
            appendEvent(taskId, EventType.PLANNING_PASS2_COMPLETED.name(), null, null, currentVersion,
                    objectMapper.writeValueAsString(Map.of("node_count", pass2Response.getMaterializedExecutionGraph() != null && pass2Response.getMaterializedExecutionGraph().path("nodes").isArray() ? pass2Response.getMaterializedExecutionGraph().path("nodes").size() : 0)));

            CreateJobResponse createJobResponse = submitJob(taskId, pass2Response.getMaterializedExecutionGraph(), passBNode.path("args_draft"));
            JobRecord jobRecord = new JobRecord();
            jobRecord.setJobId(createJobResponse.getJobId());
            jobRecord.setTaskId(taskId);
            jobRecord.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
            jobRecord.setJobState(createJobResponse.getJobState());
            jobRecord.setExecutionGraphJson(writeJson(pass2Response.getMaterializedExecutionGraph()));
            jobRecord.setRuntimeAssertionsJson(writeJson(pass2Response.getRuntimeAssertions()));
            jobRecord.setPlanningPass2SummaryJson(writeJson(pass2Response.getPlanningSummary()));
            jobRecord.setAcceptedAt(createJobResponse.getAcceptedAt());
            jobRecord.setLastHeartbeatAt(createJobResponse.getAcceptedAt());
            ensureInserted(jobRecordMapper.insert(jobRecord));
            taskAttemptMapper.updateSnapshotAndJob(taskId, jobRecord.getAttemptNo(), createJobResponse.getJobId(),
                    writeJson(Map.of("state", TaskStatus.QUEUED.name(), "job_id", createJobResponse.getJobId())), null);

            appendEvent(taskId, EventType.JOB_SUBMITTED.name(), null, null, currentVersion, objectMapper.writeValueAsString(Map.of("job_id", createJobResponse.getJobId())));
            appendEvent(taskId, EventType.JOB_STATE_CHANGED.name(), null, JobState.ACCEPTED.name(), currentVersion, objectMapper.writeValueAsString(Map.of("job_id", createJobResponse.getJobId())));

            ensureUpdated(taskStateMapper.updateStateWithPass2AndJob(taskId, currentVersion, TaskStatus.QUEUED.name(), pass2Json, createJobResponse.getJobId()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.QUEUED.name(), currentVersion + 1, null);
            currentVersion += 1;

            auditService.appendAudit(taskId, "TASK_CREATE", "SUCCESS", traceId,
                    objectMapper.writeValueAsString(Map.of("state", TaskStatus.QUEUED.name(), "validation_is_valid", true, "input_chain_status", inputChainStatus, "job_created", true, "job_id", createJobResponse.getJobId())));

            CreateTaskResponse response = new CreateTaskResponse();
            response.setTaskId(taskId);
            response.setJobId(createJobResponse.getJobId());
            response.setState(TaskStatus.QUEUED.name());
            response.setStateVersion(currentVersion);
            return response;
        } catch (Exception exception) {
            handlePipelineFailure(taskId, traceId, exception, currentVersion, currentState);
            throw new ResponseStatusException(BAD_GATEWAY, "Task pipeline failed", exception);
        }
    }

    public TaskDetailResponse getTask(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        TaskDetailResponse response = new TaskDetailResponse();
        response.setTaskId(taskState.getTaskId());
        response.setState(taskState.getCurrentState());
        response.setStateVersion(taskState.getStateVersion());
        response.setPass1Summary(buildPass1Summary(taskState.getPass1ResultJson()));
        response.setSlotBindingsSummary(readJsonObject(taskState.getSlotBindingsSummaryJson()));
        response.setArgsDraftSummary(readJsonObject(taskState.getArgsDraftSummaryJson()));
        response.setValidationSummary(readJsonObject(taskState.getValidationSummaryJson()));
        response.setInputChainStatus(taskState.getInputChainStatus());
        response.setPass2Summary(buildPass2Summary(taskState.getPass2ResultJson()));
        response.setResultObjectSummary(readJsonObject(taskState.getResultObjectSummaryJson()));
        response.setResultBundleSummary(readJsonObject(taskState.getResultBundleSummaryJson()));
        response.setFinalExplanationSummary(readJsonObject(taskState.getFinalExplanationSummaryJson()));
        response.setLastFailureSummary(readJsonObject(taskState.getLastFailureSummaryJson()));
        response.setWaitingContext(readJsonObject(taskState.getWaitingContextJson()));

        int attemptNo = taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo();
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        if (latestRepair != null) {
            response.setRepairProposal(readJsonObject(latestRepair.getRepairProposalJson()));
        }

        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        if (jobRecord != null) {
            TaskDetailResponse.JobSummary jobSummary = new TaskDetailResponse.JobSummary();
            jobSummary.setJobId(jobRecord.getJobId());
            jobSummary.setJobState(jobRecord.getJobState());
            jobSummary.setLastHeartbeatAt(jobRecord.getLastHeartbeatAt() == null ? null : jobRecord.getLastHeartbeatAt().toString());
            response.setJob(jobSummary);
        }
        return response;
    }

    public TaskResultResponse getTaskResult(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());

        TaskResultResponse response = new TaskResultResponse();
        response.setTaskId(taskId);
        response.setTaskState(taskState.getCurrentState());
        if (jobRecord != null) {
            response.setJobId(jobRecord.getJobId());
            response.setJobState(jobRecord.getJobState());
            response.setResultBundle(readJsonObject(jobRecord.getResultBundleJson()));
            response.setFinalExplanation(readJsonObject(jobRecord.getFinalExplanationJson()));
            response.setFailureSummary(readJsonObject(jobRecord.getFailureSummaryJson()));
            response.setDockerRuntimeEvidence(readJsonObject(jobRecord.getDockerRuntimeEvidenceJson()));
        }
        return response;
    }

    @Transactional
    public CancelTaskResponse cancelTask(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        if (taskState.getJobId() == null || taskState.getJobId().isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Task does not have an active job");
        }
        if (!TaskStatus.QUEUED.name().equals(taskState.getCurrentState()) && !TaskStatus.RUNNING.name().equals(taskState.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, "Task is already terminal");
        }

        try {
            appendEvent(taskId, EventType.CANCEL_REQUESTED.name(), null, null, taskState.getStateVersion(), objectMapper.writeValueAsString(Map.of("job_id", taskState.getJobId())));
            CancelJobResponse cancelResponse = jobRuntimeClient.cancelJob(taskState.getJobId(), CANCEL_REASON_USER_REQUESTED);
            if (!Boolean.TRUE.equals(cancelResponse.getAccepted())) {
                throw new ResponseStatusException(CONFLICT, "Job is already terminal");
            }
            JobRecord jobRecord = jobRecordMapper.findByJobId(taskState.getJobId());
            if (jobRecord != null) {
                syncSingleJob(jobRecord);
            }

            TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
            JobRecord latestJobRecord = latestTaskState == null || latestTaskState.getJobId() == null
                    ? null
                    : jobRecordMapper.findByJobId(latestTaskState.getJobId());
            CancelTaskResponse response = new CancelTaskResponse();
            response.setTaskId(taskId);
            response.setJobId(taskState.getJobId());
            response.setState(latestTaskState == null ? taskState.getCurrentState() : latestTaskState.getCurrentState());
            response.setJobState(latestJobRecord == null ? null : latestJobRecord.getJobState());
            response.setAccepted(true);
            return response;
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(CONFLICT, "Job already terminal", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Cancel failed", exception);
        }
    }

    @Transactional
    public UploadAttachmentResponse uploadAttachment(String taskId, Long userId, MultipartFile file, String logicalSlot) {
        TaskState taskState = getOwnedTask(taskId, userId);
        if (!TaskStatus.WAITING_USER.name().equals(taskState.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, "Attachments are only accepted in WAITING_USER");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "file is required");
        }

        String attachmentId = "att_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String cleanedFileName = file.getOriginalFilename() == null ? "upload.bin" : Path.of(file.getOriginalFilename()).getFileName().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            Path taskDir = uploadRoot.resolve(taskId).resolve(attachmentId);
            Files.createDirectories(taskDir);
            Path target = taskDir.resolve(cleanedFileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            TaskAttachment attachment = new TaskAttachment();
            attachment.setId(attachmentId);
            attachment.setTaskId(taskId);
            attachment.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
            attachment.setLogicalSlot((logicalSlot == null || logicalSlot.isBlank()) ? null : logicalSlot.trim());
            attachment.setAssignmentStatus(attachment.getLogicalSlot() == null ? "UNASSIGNED" : "ASSIGNED");
            attachment.setFileName(cleanedFileName);
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSize());
            attachment.setStoredPath(target.toString());
            attachment.setChecksum(hexSha256(file.getBytes()));
            attachment.setUploadedBy(userId);
            ensureInserted(taskAttachmentMapper.insert(attachment));

            appendEvent(taskId, EventType.ATTACHMENT_UPLOADED.name(), null, null, taskState.getStateVersion(),
                    writeJson(Map.of("attachment_id", attachmentId, "logical_slot", safeString(attachment.getLogicalSlot()), "assignment_status", attachment.getAssignmentStatus())));

            refreshWaitingContext(taskId);

            UploadAttachmentResponse response = new UploadAttachmentResponse();
            response.setAttachmentId(attachmentId);
            response.setTaskId(taskId);
            response.setLogicalSlot(attachment.getLogicalSlot());
            response.setStoredPath(target.toString());
            response.setSizeBytes(file.getSize());
            response.setCreatedAt(now.toString());
            response.setAssignmentStatus(attachment.getAssignmentStatus());
            return response;
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to store attachment", exception);
        }
    }

    @Transactional
    public ResumeTaskResponse resumeTask(String taskId, Long userId, ResumeTaskRequest request) {
        TaskState taskState = getOwnedTask(taskId, userId);
        String resumeRequestId = request.getResumeRequestId();
        if (resumeRequestId == null || resumeRequestId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "resume_request_id is required");
        }

        RepairRecord existing = repairRecordMapper.findByTaskIdAndResumeRequestId(taskId, resumeRequestId);
        if (existing != null) {
            ResumeTaskResponse idempotent = new ResumeTaskResponse();
            TaskState latest = taskStateMapper.findByTaskId(taskId);
            idempotent.setTaskId(taskId);
            idempotent.setState(latest.getCurrentState());
            idempotent.setStateVersion(latest.getStateVersion());
            idempotent.setResumeAccepted("ACCEPTED".equals(existing.getResult()));
            idempotent.setResumeAttempt(existing.getAttemptNo());
            return idempotent;
        }

        if (!TaskStatus.WAITING_USER.name().equals(taskState.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, "Task is not in WAITING_USER state");
        }

        appendEvent(taskId, EventType.RESUME_REQUESTED.name(), null, null, taskState.getStateVersion(),
                writeJson(Map.of("resume_request_id", resumeRequestId)));

        JsonNode waitingContext = refreshWaitingContext(taskId);
        if (!waitingContext.path("can_resume").asBoolean(false)) {
            RepairRecord rejected = new RepairRecord();
            rejected.setTaskId(taskId);
            rejected.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
            rejected.setResumeRequestId(resumeRequestId);
            rejected.setDispatcherOutputJson(writeJson(Map.of("severity", "RECOVERABLE", "routing", "WAITING_USER")));
            rejected.setRepairProposalJson(writeJson(repairProposalService.generate(waitingContext, readJsonNode(taskState.getValidationSummaryJson()), null, request.getUserNote())));
            rejected.setResumePayloadJson(writeJson(request));
            rejected.setResult("REJECTED");
            repairRecordMapper.insert(rejected);
            appendEvent(taskId, EventType.RESUME_REJECTED.name(), null, null, taskState.getStateVersion(),
                    writeJson(Map.of("resume_request_id", resumeRequestId, "reason", "required actions not satisfied")));
            throw new ResponseStatusException(CONFLICT, "Required user actions are not satisfied");
        }

        validateAttachmentOwnership(taskId, request.getAttachmentIds());

        int newResumeCount = (taskState.getResumeAttemptCount() == null ? 0 : taskState.getResumeAttemptCount()) + 1;
        int newAttemptNo = newResumeCount + 1;
        String resumePayloadJson = writeJson(request);

        int previousAttemptNo = taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo();
        taskAttemptMapper.updateSnapshotAndJob(taskId, previousAttemptNo, taskState.getJobId(),
                writeJson(Map.of("state", taskState.getCurrentState(), "deactivated_by", "resume")),
                OffsetDateTime.now(ZoneOffset.UTC));

        ensureUpdated(taskStateMapper.acceptResume(taskId, taskState.getStateVersion(), TaskStatus.VALIDATING.name(),
                resumePayloadJson, newResumeCount, newAttemptNo));

        TaskAttempt taskAttempt = new TaskAttempt();
        taskAttempt.setTaskId(taskId);
        taskAttempt.setAttemptNo(newAttemptNo);
        taskAttempt.setTrigger("RESUME");
        taskAttempt.setStatusSnapshotJson(writeJson(Map.of("state", TaskStatus.VALIDATING.name(), "resume_request_id", resumeRequestId)));
        ensureInserted(taskAttemptMapper.insert(taskAttempt));

        RepairRecord accepted = new RepairRecord();
        accepted.setTaskId(taskId);
        accepted.setAttemptNo(newAttemptNo);
        accepted.setResumeRequestId(resumeRequestId);
        accepted.setDispatcherOutputJson(writeJson(Map.of("severity", "RECOVERABLE", "routing", "VALIDATING")));
        accepted.setRepairProposalJson(writeJson(repairProposalService.generate(waitingContext, readJsonNode(taskState.getValidationSummaryJson()), null, request.getUserNote())));
        accepted.setResumePayloadJson(resumePayloadJson);
        accepted.setResult("ACCEPTED");
        repairRecordMapper.insert(accepted);

        appendEvent(taskId, EventType.RESUME_ACCEPTED.name(), null, null, taskState.getStateVersion() + 1,
                writeJson(Map.of("resume_request_id", resumeRequestId, "resume_attempt", newAttemptNo)));

        TaskState resumedTask = taskStateMapper.findByTaskId(taskId);
        ResumeTaskResponse resumeResponse = runResumePipeline(resumedTask, request);
        return resumeResponse;
    }

    public TaskEventsResponse getEvents(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<EventLog> events = eventService.findByTaskId(taskState.getTaskId());
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

    public SseEmitter streamTask(String taskId, Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean running = new AtomicBoolean(true);

        Runnable emitSnapshot = () -> {
            try {
                TaskStreamResponse payload = new TaskStreamResponse();
                payload.setTask(getTask(taskId, userId));
                payload.setEventsResponse(getEvents(taskId, userId));
                emitter.send(SseEmitter.event().name("task_update").data(payload));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        };

        emitter.onCompletion(() -> running.set(false));
        emitter.onTimeout(() -> { running.set(false); emitter.complete(); });
        emitter.onError(error -> running.set(false));

        streamExecutor.execute(() -> {
            try {
                emitSnapshot.run();
                while (running.get()) {
                    Thread.sleep(3000);
                    emitSnapshot.run();
                }
            } catch (Exception exception) {
                if (running.get()) {
                    emitter.completeWithError(exception);
                }
            }
        });
        return emitter;
    }

    public void syncActiveJobs() {
        for (JobRecord jobRecord : jobRecordMapper.findActiveJobs()) {
            try {
                syncSingleJob(jobRecord);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncSingleJob(JobRecord jobRecord) throws Exception {
        JobStatusResponse status = jobRuntimeClient.getJob(jobRecord.getJobId());
        String newState = safeString(status.getJobState());

        jobRecordMapper.updateRuntimeSnapshot(
                jobRecord.getJobId(),
                newState,
                status.getStartedAt(),
                status.getFinishedAt(),
                status.getLastHeartbeatAt(),
                writeJsonIfPresent(status.getResultObject()),
                writeJsonIfPresent(status.getResultBundle()),
                writeJsonIfPresent(status.getFinalExplanation()),
                writeJsonIfPresent(status.getFailureSummary()),
                writeJsonIfPresent(status.getDockerRuntimeEvidence()),
                writeJsonIfPresent(status.getErrorObject()),
                status.getCancelRequestedAt(),
                status.getCancelledAt(),
                status.getCancelReason(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        TaskState taskState = taskStateMapper.findByTaskId(jobRecord.getTaskId());
        if (taskState == null || !Objects.equals(taskState.getJobId(), jobRecord.getJobId()) || Objects.equals(jobRecord.getJobState(), newState)) {
            return;
        }

        appendEvent(taskState.getTaskId(), EventType.JOB_STATE_CHANGED.name(), safeString(jobRecord.getJobState()), newState, taskState.getStateVersion(), objectMapper.writeValueAsString(Map.of("job_id", jobRecord.getJobId())));

        if (JobState.SUCCEEDED.name().equals(newState)) {
            processSuccess(taskState, status);
            return;
        }

        if (JobState.CANCELLED.name().equals(newState)) {
            appendEvent(taskState.getTaskId(), EventType.JOB_CANCELLED.name(), null, null, taskState.getStateVersion(), objectMapper.writeValueAsString(Map.of("job_id", jobRecord.getJobId(), "cancel_reason", safeString(status.getCancelReason()))));
        }

        if (JobState.FAILED.name().equals(newState) || JobState.CANCELLED.name().equals(newState)) {
            taskStateMapper.updateOutputSummaries(taskState.getTaskId(), null, null, buildFailureSummaryJson(status), null);
        }

        if (JobState.SUCCEEDED.name().equals(newState) || JobState.FAILED.name().equals(newState) || JobState.CANCELLED.name().equals(newState)) {
            int attemptNo = taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo();
            taskAttemptMapper.updateSnapshotAndJob(taskState.getTaskId(), attemptNo, jobRecord.getJobId(),
                    writeJson(Map.of("job_state", newState, "task_state", safeString(taskState.getCurrentState()))),
                    OffsetDateTime.now(ZoneOffset.UTC));
        }

        TaskStatus projected = mapJobStateToTaskState(newState);
        if (projected != null && !projected.name().equals(taskState.getCurrentState())) {
            ensureUpdated(taskStateMapper.updateState(taskState.getTaskId(), taskState.getStateVersion(), projected.name()));
            appendEvent(taskState.getTaskId(), EventType.STATE_CHANGED.name(), taskState.getCurrentState(), projected.name(), taskState.getStateVersion() + 1, null);
            if (projected == TaskStatus.CANCELLED) {
                appendEvent(taskState.getTaskId(), EventType.TASK_CANCELLED.name(), null, null, taskState.getStateVersion() + 1, objectMapper.writeValueAsString(Map.of("job_id", jobRecord.getJobId())));
            }
        }
    }

    private void processSuccess(TaskState taskState, JobStatusResponse status) throws Exception {
        int version = taskState.getStateVersion();
        String taskId = taskState.getTaskId();
        String currentState = taskState.getCurrentState();

        if (!TaskStatus.RESULT_PROCESSING.name().equals(currentState)) {
            ensureUpdated(taskStateMapper.updateState(taskId, version, TaskStatus.RESULT_PROCESSING.name()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState, TaskStatus.RESULT_PROCESSING.name(), version + 1, null);
            version += 1;
            currentState = TaskStatus.RESULT_PROCESSING.name();
        }

        String resultBundleSummary = buildResultBundleSummaryJson(status.getResultBundle());
        String finalExplanationSummary = buildFinalExplanationSummaryJson(status.getFinalExplanation());
        String resultObjectSummary = buildResultObjectSummaryJson(status.getResultObject(), status.getResultBundle());
        taskStateMapper.updateOutputSummaries(taskId, resultBundleSummary, finalExplanationSummary, null, resultObjectSummary);

        appendEvent(taskId, EventType.RESULT_BUNDLE_READY.name(), null, null, version, resultBundleSummary);
        appendEvent(taskId, EventType.FINAL_EXPLANATION_STARTED.name(), null, null, version, null);
        appendEvent(taskId, EventType.FINAL_EXPLANATION_COMPLETED.name(), null, null, version, finalExplanationSummary);
        if (resultObjectSummary != null) {
            appendEvent(taskId, EventType.RESULT_OBJECT_READY.name(), null, null, version, resultObjectSummary);
        }

        ensureUpdated(taskStateMapper.updateState(taskId, version, TaskStatus.SUCCEEDED.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState, TaskStatus.SUCCEEDED.name(), version + 1, null);
    }

    private ResumeTaskResponse runResumePipeline(TaskState taskState, ResumeTaskRequest request) {
        String taskId = taskState.getTaskId();
        int currentVersion = taskState.getStateVersion();
        TaskStatus currentState = TaskStatus.valueOf(taskState.getCurrentState());

        try {
            JsonNode pass1Node = objectMapper.readTree(taskState.getPass1ResultJson());
            appendEvent(taskId, EventType.COGNITION_PASSB_STARTED.name(), null, null, currentVersion, null);
            CognitionPassBResponse passBResponse = runPassB(taskId, safeString(taskState.getUserQuery()), currentVersion, pass1Node);
            ObjectNode passBNode = (ObjectNode) objectMapper.readTree(objectMapper.writeValueAsString(passBResponse));
            applyResumeInputs(passBNode, request, taskId);
            String passBJson = writeJson(passBNode);
            appendEvent(taskId, EventType.COGNITION_PASSB_COMPLETED.name(), null, null, currentVersion,
                    writeJson(Map.of("binding_count", passBNode.path("slot_bindings").isArray() ? passBNode.path("slot_bindings").size() : 0, "resume", true)));

            appendEvent(taskId, EventType.VALIDATION_STARTED.name(), null, null, currentVersion, null);
            PrimitiveValidationResponse validationResponse = runValidation(taskId, currentVersion, pass1Node, passBNode);
            String validationSummaryJson = objectMapper.writeValueAsString(validationResponse);
            JsonNode validationNode = objectMapper.readTree(validationSummaryJson);
            String inputChainStatus = deriveInputChainStatus(validationResponse);
            CognitionPassBResponse effectivePassB = objectMapper.treeToValue(passBNode, CognitionPassBResponse.class);
            appendEvent(taskId,
                    Boolean.TRUE.equals(validationResponse.getIsValid()) ? EventType.VALIDATION_PASSED.name() : EventType.VALIDATION_FAILED.name(),
                    null,
                    null,
                    currentVersion,
                    writeJson(Map.of("error_code", safeString(validationResponse.getErrorCode()), "missing_roles", safeList(validationResponse.getMissingRoles()), "missing_params", safeList(validationResponse.getMissingParams()))));

            RepairDecision decision = null;
            TaskStatus stateAfterValidation = TaskStatus.PLANNING;
            if (!Boolean.TRUE.equals(validationResponse.getIsValid())) {
                decision = repairDispatcherService.decide(validationNode, taskAttachmentMapper.findByTaskId(taskId));
                if ("FAILED".equalsIgnoreCase(decision.routing()) || "FATAL".equalsIgnoreCase(decision.severity())) {
                    stateAfterValidation = TaskStatus.FAILED;
                } else {
                    stateAfterValidation = TaskStatus.WAITING_USER;
                }
            }
            ensureUpdated(taskStateMapper.updateStateWithInputChain(taskId, currentVersion, stateAfterValidation.name(), passBJson,
                    buildSlotBindingsSummaryJson(effectivePassB), buildArgsDraftSummaryJson(effectivePassB), validationSummaryJson, inputChainStatus));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), stateAfterValidation.name(), currentVersion + 1, null);
            currentVersion += 1;
            currentState = stateAfterValidation;

            if (!Boolean.TRUE.equals(validationResponse.getIsValid())) {
                if (TaskStatus.FAILED.equals(stateAfterValidation)) {
                    String failureSummaryJson = writeJson(Map.of(
                            "failure_code", "FATAL_VALIDATION",
                            "failure_message", "Task validation is fatal and not recoverable in Week5.",
                            "error_code", safeString(validationResponse.getErrorCode()),
                            "invalid_bindings", safeList(validationResponse.getInvalidBindings()),
                            "missing_roles", safeList(validationResponse.getMissingRoles()),
                            "missing_params", safeList(validationResponse.getMissingParams()),
                            "created_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
                    ));
                    taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                    appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion, failureSummaryJson);

                    RepairRecord fatal = new RepairRecord();
                    fatal.setTaskId(taskId);
                    fatal.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
                    fatal.setDispatcherOutputJson(writeJson(Map.of("severity", decision.severity(), "routing", decision.routing())));
                    fatal.setRepairProposalJson(writeJson(repairProposalService.generate(decision.waitingContext(), validationNode, null, request.getUserNote())));
                    fatal.setResumePayloadJson(writeJson(request));
                    fatal.setResult("FAILED");
                    repairRecordMapper.insert(fatal);

                    ResumeTaskResponse response = new ResumeTaskResponse();
                    response.setTaskId(taskId);
                    response.setState(TaskStatus.FAILED.name());
                    response.setStateVersion(currentVersion);
                    response.setResumeAccepted(true);
                    response.setResumeAttempt(taskState.getActiveAttemptNo());
                    return response;
                }

                String waitingContextJson = writeJson(decision.waitingContext());
                taskStateMapper.updateWaitingContext(taskId, waitingContextJson, decision.waitingContext().path("waiting_reason_type").asText("REPAIR_REQUIRED"));
                RepairRecord repairRecord = new RepairRecord();
                repairRecord.setTaskId(taskId);
                repairRecord.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
                repairRecord.setDispatcherOutputJson(writeJson(Map.of("severity", decision.severity(), "routing", decision.routing())));
                repairRecord.setRepairProposalJson(writeJson(repairProposalService.generate(decision.waitingContext(), validationNode, null, request.getUserNote())));
                repairRecord.setResumePayloadJson(writeJson(request));
                repairRecord.setResult("REJECTED");
                repairRecordMapper.insert(repairRecord);
                appendEvent(taskId, EventType.WAITING_USER_ENTERED.name(), null, null, currentVersion, waitingContextJson);

                ResumeTaskResponse response = new ResumeTaskResponse();
                response.setTaskId(taskId);
                response.setState(TaskStatus.WAITING_USER.name());
                response.setStateVersion(currentVersion);
                response.setResumeAccepted(true);
                response.setResumeAttempt(taskState.getActiveAttemptNo());
                return response;
            }

            appendEvent(taskId, EventType.PLANNING_PASS2_STARTED.name(), null, null, currentVersion, null);
            Pass2Response pass2Response = runPass2(taskId, currentVersion, pass1Node, passBNode, validationNode);
            String pass2Json = writeJson(pass2Response);
            appendEvent(taskId, EventType.PLANNING_PASS2_COMPLETED.name(), null, null, currentVersion,
                    writeJson(Map.of("node_count", pass2Response.getMaterializedExecutionGraph() != null && pass2Response.getMaterializedExecutionGraph().path("nodes").isArray() ? pass2Response.getMaterializedExecutionGraph().path("nodes").size() : 0)));

            CreateJobResponse createJobResponse = submitJob(taskId, pass2Response.getMaterializedExecutionGraph(), passBNode.path("args_draft"));
            JobRecord jobRecord = new JobRecord();
            jobRecord.setJobId(createJobResponse.getJobId());
            jobRecord.setTaskId(taskId);
            jobRecord.setAttemptNo(taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo());
            jobRecord.setJobState(createJobResponse.getJobState());
            jobRecord.setExecutionGraphJson(writeJson(pass2Response.getMaterializedExecutionGraph()));
            jobRecord.setRuntimeAssertionsJson(writeJson(pass2Response.getRuntimeAssertions()));
            jobRecord.setPlanningPass2SummaryJson(writeJson(pass2Response.getPlanningSummary()));
            jobRecord.setAcceptedAt(createJobResponse.getAcceptedAt());
            jobRecord.setLastHeartbeatAt(createJobResponse.getAcceptedAt());
            ensureInserted(jobRecordMapper.insert(jobRecord));

            appendEvent(taskId, EventType.JOB_SUBMITTED.name(), null, null, currentVersion, writeJson(Map.of("job_id", createJobResponse.getJobId())));
            appendEvent(taskId, EventType.JOB_STATE_CHANGED.name(), null, JobState.ACCEPTED.name(), currentVersion, writeJson(Map.of("job_id", createJobResponse.getJobId())));

            ensureUpdated(taskStateMapper.updateStateWithPass2AndJob(taskId, currentVersion, TaskStatus.QUEUED.name(), pass2Json, createJobResponse.getJobId()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.QUEUED.name(), currentVersion + 1, null);
            taskAttemptMapper.updateSnapshotAndJob(taskId, jobRecord.getAttemptNo(), createJobResponse.getJobId(),
                    writeJson(Map.of("state", TaskStatus.QUEUED.name(), "job_id", createJobResponse.getJobId())), null);

            ResumeTaskResponse response = new ResumeTaskResponse();
            response.setTaskId(taskId);
            response.setState(TaskStatus.QUEUED.name());
            response.setStateVersion(currentVersion + 1);
            response.setResumeAccepted(true);
            response.setResumeAttempt(taskState.getActiveAttemptNo());
            return response;
        } catch (Exception exception) {
            handlePipelineFailure(taskId, UUID.randomUUID().toString(), exception, currentVersion, currentState);
            throw new ResponseStatusException(BAD_GATEWAY, "Resume pipeline failed", exception);
        }
    }

    private void applyResumeInputs(ObjectNode passBNode, ResumeTaskRequest request, String taskId) {
        if (passBNode == null) {
            return;
        }
        ArrayNode slotBindings = passBNode.withArray("slot_bindings");
        Set<String> existingRoles = new HashSet<>();
        for (JsonNode node : slotBindings) {
            existingRoles.add(node.path("role_name").asText(""));
        }

        for (TaskAttachment attachment : taskAttachmentMapper.findByTaskId(taskId)) {
            String slot = attachment.getLogicalSlot();
            if (slot == null || slot.isBlank()) {
                continue;
            }
            if (!existingRoles.contains(slot)) {
                ObjectNode binding = slotBindings.addObject();
                binding.put("role_name", slot);
                binding.put("slot_name", slot);
                binding.put("source", "resume_attachment");
                existingRoles.add(slot);
            }
        }

        if (request.getSlotOverrides() != null) {
            for (Map.Entry<String, Object> entry : request.getSlotOverrides().entrySet()) {
                String role = safeString(entry.getKey());
                String slot = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                if (role.isBlank() || slot.isBlank()) {
                    continue;
                }
                boolean replaced = false;
                for (JsonNode node : slotBindings) {
                    if (role.equals(node.path("role_name").asText("")) && node instanceof ObjectNode objectNode) {
                        objectNode.put("slot_name", slot);
                        objectNode.put("source", "resume_override");
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    ObjectNode binding = slotBindings.addObject();
                    binding.put("role_name", role);
                    binding.put("slot_name", slot);
                    binding.put("source", "resume_override");
                }
            }
        }

        ObjectNode argsDraft = passBNode.with("args_draft");
        if (request.getArgsOverrides() != null) {
            for (Map.Entry<String, Object> entry : request.getArgsOverrides().entrySet()) {
                argsDraft.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }
        }
    }

    private JsonNode refreshWaitingContext(String taskId) {
        TaskState latest = taskStateMapper.findByTaskId(taskId);
        JsonNode validationSummary = readJsonNode(latest.getValidationSummaryJson());
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        RepairDecision decision = repairDispatcherService.decide(validationSummary, attachments);
        String waitingContextJson;
        try {
            waitingContextJson = writeJson(decision.waitingContext());
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to build waiting context", exception);
        }
        taskStateMapper.updateWaitingContext(taskId, waitingContextJson, decision.waitingContext().path("waiting_reason_type").asText("REPAIR_REQUIRED"));
        try {
            RepairRecord refreshRecord = new RepairRecord();
            refreshRecord.setTaskId(taskId);
            refreshRecord.setAttemptNo(latest.getActiveAttemptNo() == null ? 1 : latest.getActiveAttemptNo());
            refreshRecord.setDispatcherOutputJson(writeJson(Map.of("severity", decision.severity(), "routing", decision.routing())));
            refreshRecord.setRepairProposalJson(writeJson(repairProposalService.generate(decision.waitingContext(), validationSummary, readJsonNode(latest.getLastFailureSummaryJson()), null)));
            refreshRecord.setResult("REJECTED");
            repairRecordMapper.insert(refreshRecord);
        } catch (Exception ignored) {
        }
        return decision.waitingContext();
    }

    private JsonNode readJsonNode(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(sourceJson);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private void validateAttachmentOwnership(String taskId, List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Set<String> existingIds = new HashSet<>();
        for (TaskAttachment attachment : taskAttachmentMapper.findByTaskId(taskId)) {
            existingIds.add(attachment.getId());
        }
        for (String attachmentId : attachmentIds) {
            if (!existingIds.contains(attachmentId)) {
                throw new ResponseStatusException(CONFLICT, "Attachment does not belong to task: " + attachmentId);
            }
        }
    }

    private String hexSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            return "";
        }
    }

    private Pass1Response runPass1(String taskId, String userQuery, int stateVersion) {
        Pass1Request req = new Pass1Request();
        req.setTaskId(taskId);
        req.setUserQuery(userQuery);
        req.setStateVersion(stateVersion);
        return pass1Client.runPass1(req);
    }

    private CognitionPassBResponse runPassB(String taskId, String userQuery, int stateVersion, JsonNode pass1Result) {
        CognitionPassBRequest req = new CognitionPassBRequest();
        req.setTaskId(taskId);
        req.setUserQuery(userQuery);
        req.setStateVersion(stateVersion);
        req.setPass1Result(pass1Result);
        return cognitionPassBClient.runPassB(req);
    }

    private PrimitiveValidationResponse runValidation(String taskId, int stateVersion, JsonNode pass1Result, JsonNode passBResult) {
        PrimitiveValidationRequest req = new PrimitiveValidationRequest();
        req.setTaskId(taskId);
        req.setStateVersion(stateVersion);
        req.setPass1Result(pass1Result);
        req.setPassbResult(passBResult);
        return validationClient.validatePrimitive(req);
    }

    private Pass2Response runPass2(String taskId, int stateVersion, JsonNode pass1Result, JsonNode passBResult, JsonNode validationSummary) {
        Pass2Request req = new Pass2Request();
        req.setTaskId(taskId);
        req.setStateVersion(stateVersion);
        req.setPass1Result(pass1Result);
        req.setPassbResult(passBResult);
        req.setValidationSummary(validationSummary);
        return pass2Client.runPass2(req);
    }

    private CreateJobResponse submitJob(String taskId, JsonNode executionGraph, JsonNode argsDraft) {
        CreateJobRequest req = new CreateJobRequest();
        req.setTaskId(taskId);
        req.setMaterializedExecutionGraph(executionGraph);
        req.setArgsDraft(argsDraft);
        return jobRuntimeClient.createJob(req);
    }

    private TaskState getOwnedTask(String taskId, Long userId) {
        TaskState taskState = taskStateMapper.findByTaskId(taskId);
        if (taskState == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        if (!taskState.getUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }
        return taskState;
    }

    private String buildSlotBindingsSummaryJson(CognitionPassBResponse passBResponse) throws Exception {
        List<CognitionPassBResponse.SlotBinding> bindings = safeList(passBResponse.getSlotBindings());
        List<String> roleNames = new ArrayList<>();
        for (CognitionPassBResponse.SlotBinding binding : bindings) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        return objectMapper.writeValueAsString(Map.of("bound_slots_count", bindings.size(), "bound_role_names", roleNames));
    }

    private String buildArgsDraftSummaryJson(CognitionPassBResponse passBResponse) throws Exception {
        Map<String, Object> argsDraft = passBResponse.getArgsDraft() == null ? Collections.emptyMap() : passBResponse.getArgsDraft();
        List<String> keys = new ArrayList<>(argsDraft.keySet());
        Collections.sort(keys);
        return objectMapper.writeValueAsString(Map.of("param_count", argsDraft.size(), "param_keys", keys));
    }

    private String buildResultBundleSummaryJson(JsonNode resultBundle) throws Exception {
        if (resultBundle == null || resultBundle.isNull()) {
            return null;
        }
        JsonNode outputs = resultBundle.path("main_outputs");
        return objectMapper.writeValueAsString(Map.of(
                "result_id", resultBundle.path("result_id").asText(""),
                "summary", resultBundle.path("summary").asText(""),
                "main_output_count", outputs.isArray() ? outputs.size() : 0,
                "created_at", resultBundle.path("created_at").asText("")
        ));
    }

    private String buildFinalExplanationSummaryJson(JsonNode finalExplanation) throws Exception {
        if (finalExplanation == null || finalExplanation.isNull()) {
            return null;
        }
        JsonNode highlights = finalExplanation.path("highlights");
        return objectMapper.writeValueAsString(Map.of(
                "title", finalExplanation.path("title").asText(""),
                "highlight_count", highlights.isArray() ? highlights.size() : 0,
                "generated_at", finalExplanation.path("generated_at").asText("")
        ));
    }

    private String buildFailureSummaryJson(JobStatusResponse status) throws Exception {
        if (status.getFailureSummary() != null && !status.getFailureSummary().isNull()) {
            return writeJson(status.getFailureSummary());
        }
        if (status.getErrorObject() != null && !status.getErrorObject().isNull()) {
            return objectMapper.writeValueAsString(Map.of(
                    "failure_code", status.getErrorObject().path("error_code").asText("JOB_RUNTIME_ERROR"),
                    "failure_message", status.getErrorObject().path("message").asText("Job runtime error"),
                    "created_at", status.getErrorObject().path("created_at").asText(OffsetDateTime.now(ZoneOffset.UTC).toString())
            ));
        }
        return objectMapper.writeValueAsString(Map.of(
                "failure_code", "UNKNOWN_FAILURE",
                "failure_message", "Job failed without detail",
                "created_at", OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
    }

    private String buildResultObjectSummaryJson(JsonNode resultObject, JsonNode resultBundle) throws Exception {
        JsonNode source = resultObject;
        if (source == null || source.isNull()) {
            source = resultBundle;
        }
        if (source == null || source.isNull()) {
            return null;
        }
        JsonNode artifacts = source.path("artifacts");
        return objectMapper.writeValueAsString(Map.of(
                "result_id", source.path("result_id").asText(""),
                "summary", source.path("summary").asText(""),
                "artifact_count", artifacts.isArray() ? artifacts.size() : 0,
                "created_at", source.path("created_at").asText("")
        ));
    }

    private String deriveInputChainStatus(PrimitiveValidationResponse validationResponse) {
        return Boolean.TRUE.equals(validationResponse.getIsValid()) ? InputChainStatus.COMPLETE.name() : InputChainStatus.INCOMPLETE.name();
    }

    private TaskStatus mapJobStateToTaskState(String jobState) {
        if (JobState.ACCEPTED.name().equals(jobState)) return TaskStatus.QUEUED;
        if (JobState.RUNNING.name().equals(jobState)) return TaskStatus.RUNNING;
        if (JobState.FAILED.name().equals(jobState)) return TaskStatus.FAILED;
        if (JobState.CANCELLED.name().equals(jobState)) return TaskStatus.CANCELLED;
        return null;
    }

    private void appendEvent(String taskId, String eventType, String fromState, String toState, int stateVersion, String payloadJson) {
        eventService.appendEvent(taskId, eventType, fromState, toState, stateVersion, payloadJson);
    }

    private TaskDetailResponse.Pass1Summary buildPass1Summary(String pass1ResultJson) {
        if (pass1ResultJson == null || pass1ResultJson.isBlank()) return null;
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

    private Object buildPass2Summary(String pass2ResultJson) {
        if (pass2ResultJson == null || pass2ResultJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(pass2ResultJson);
            return objectMapper.treeToValue(root.path("planning_summary"), Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private Object readJsonObject(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) return null;
        try {
            return objectMapper.readValue(sourceJson, Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private void handlePipelineFailure(String taskId, String traceId, Exception exception, int expectedVersion, TaskStatus fromState) {
        try {
            int failedVersion = expectedVersion;
            if (taskStateMapper.updateState(taskId, expectedVersion, TaskStatus.FAILED.name()) > 0) {
                failedVersion = expectedVersion + 1;
                appendEvent(taskId, EventType.STATE_CHANGED.name(), fromState.name(), TaskStatus.FAILED.name(), failedVersion, null);
            }
            appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, failedVersion, null);
            auditService.appendAudit(taskId, "TASK_CREATE", "FAILED", traceId,
                    objectMapper.writeValueAsString(Map.of("error", exception.getClass().getSimpleName(), "message", safeString(exception.getMessage()), "at", OffsetDateTime.now().toString(), "from_state", fromState.name(), "expected_state_version", expectedVersion)));
        } catch (Exception ignored) {
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private String writeJsonIfPresent(JsonNode value) {
        if (value == null || value.isNull()) return null;
        return writeJson(value);
    }

    private void ensureUpdated(int updatedRows) {
        if (updatedRows != 1) throw new IllegalStateException("State version conflict");
    }

    private void ensureInserted(int insertedRows) {
        if (insertedRows != 1) throw new IllegalStateException("Insert failed");
    }

    private int safeSize(List<?> values) { return values == null ? 0 : values.size(); }

    private <T> List<T> safeList(List<T> values) { return values == null ? Collections.emptyList() : values; }

    private String safeString(String value) { return value == null ? "" : value; }
}
