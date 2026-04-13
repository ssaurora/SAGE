package com.sage.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sage.backend.audit.AuditService;
import com.sage.backend.cognition.CognitionFinalExplanationClient;
import com.sage.backend.cognition.CognitionGoalRouteClient;
import com.sage.backend.cognition.CognitionPassBClient;
import com.sage.backend.cognition.dto.CognitionFinalExplanationRequest;
import com.sage.backend.cognition.dto.CognitionFinalExplanationResponse;
import com.sage.backend.cognition.dto.CognitionGoalRouteRequest;
import com.sage.backend.cognition.dto.CognitionGoalRouteResponse;
import com.sage.backend.cognition.dto.CognitionPassBRequest;
import com.sage.backend.cognition.dto.CognitionPassBResponse;
import com.sage.backend.common.TaskIdGenerator;
import com.sage.backend.event.EventService;
import com.sage.backend.execution.JobRuntimeClient;
import com.sage.backend.execution.dto.CancelJobResponse;
import com.sage.backend.execution.dto.CreateJobRequest;
import com.sage.backend.execution.dto.CreateJobResponse;
import com.sage.backend.execution.dto.JobStatusResponse;
import com.sage.backend.mapper.AnalysisManifestMapper;
import com.sage.backend.mapper.JobRecordMapper;
import com.sage.backend.mapper.RepairRecordMapper;
import com.sage.backend.mapper.TaskAttachmentMapper;
import com.sage.backend.mapper.TaskAttemptMapper;
import com.sage.backend.mapper.TaskStateMapper;
import com.sage.backend.model.AnalysisSession;
import com.sage.backend.model.AnalysisManifest;
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
import com.sage.backend.planning.Pass1FactHelper;
import com.sage.backend.planning.Pass2Client;
import com.sage.backend.planning.dto.Pass1Request;
import com.sage.backend.planning.dto.Pass1Response;
import com.sage.backend.planning.dto.Pass2Request;
import com.sage.backend.planning.dto.Pass2Response;
import com.sage.backend.task.dto.CancelTaskResponse;
import com.sage.backend.task.dto.CreateTaskRequest;
import com.sage.backend.task.dto.CreateTaskResponse;
import com.sage.backend.task.dto.ForceRevertCheckpointRequest;
import com.sage.backend.task.dto.ForceRevertCheckpointResponse;
import com.sage.backend.task.dto.ResumeTaskRequest;
import com.sage.backend.task.dto.ResumeTaskResponse;
import com.sage.backend.task.dto.TaskDetailResponse;
import com.sage.backend.task.dto.TaskArtifactsResponse;
import com.sage.backend.task.dto.TaskAuditResponse;
import com.sage.backend.task.dto.TaskCatalogResponse;
import com.sage.backend.task.dto.TaskContractResponse;
import com.sage.backend.task.dto.TaskEventsResponse;
import com.sage.backend.task.dto.TaskManifestResponse;
import com.sage.backend.task.dto.TaskResultResponse;
import com.sage.backend.task.dto.TaskRunsResponse;
import com.sage.backend.task.dto.TaskStreamResponse;
import com.sage.backend.task.dto.ResumeTransactionView;
import com.sage.backend.task.dto.UploadAttachmentResponse;
import com.sage.backend.validationgate.ValidationClient;
import com.sage.backend.validationgate.dto.PrimitiveValidationRequest;
import com.sage.backend.validationgate.dto.PrimitiveValidationResponse;
import com.sage.backend.repair.RepairFactHelper;
import com.sage.backend.repair.RepairDecision;
import com.sage.backend.repair.RepairDispatcherService;
import com.sage.backend.repair.RepairProposalService;
import com.sage.backend.repair.dto.RepairProposalResponse;
import com.sage.backend.security.CurrentUser;
import com.sage.backend.session.SessionLifecycleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskService.class);

    private final TaskStateMapper taskStateMapper;
    private final AnalysisManifestMapper analysisManifestMapper;
    private final JobRecordMapper jobRecordMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;
    private final RepairRecordMapper repairRecordMapper;
    private final TaskAttemptMapper taskAttemptMapper;
    private final TaskCatalogSnapshotService taskCatalogSnapshotService;
    private final EventService eventService;
    private final AuditService auditService;
    private final CognitionFinalExplanationClient cognitionFinalExplanationClient;
    private final CognitionGoalRouteClient cognitionGoalRouteClient;
    private final Pass1Client pass1Client;
    private final CognitionPassBClient cognitionPassBClient;
    private final ValidationClient validationClient;
    private final Pass2Client pass2Client;
    private final JobRuntimeClient jobRuntimeClient;
    private final RepairDispatcherService repairDispatcherService;
    private final RepairProposalService repairProposalService;
    private final AssertionFailureMapper assertionFailureMapper;
    private final GoalRouteService goalRouteService;
    private final ExecutionContractAssembler executionContractAssembler;
    private final RegistryService registryService;
    private final WorkspaceTraceService workspaceTraceService;
    private final TaskDetailQueryService taskDetailQueryService;
    private final TaskResultQueryService taskResultQueryService;
    private final TaskAuditQueryService taskAuditQueryService;
    private final TaskManifestQueryService taskManifestQueryService;
    private final TaskCatalogQueryService taskCatalogQueryService;
    private final TaskContractQueryService taskContractQueryService;
    private final SessionLifecycleService sessionLifecycleService;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    @Autowired
    public TaskService(
            TaskStateMapper taskStateMapper,
            AnalysisManifestMapper analysisManifestMapper,
            JobRecordMapper jobRecordMapper,
            TaskAttachmentMapper taskAttachmentMapper,
            RepairRecordMapper repairRecordMapper,
            TaskAttemptMapper taskAttemptMapper,
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            EventService eventService,
            AuditService auditService,
            CognitionFinalExplanationClient cognitionFinalExplanationClient,
            CognitionGoalRouteClient cognitionGoalRouteClient,
            Pass1Client pass1Client,
            CognitionPassBClient cognitionPassBClient,
            ValidationClient validationClient,
            Pass2Client pass2Client,
            JobRuntimeClient jobRuntimeClient,
            RepairDispatcherService repairDispatcherService,
            RepairProposalService repairProposalService,
            AssertionFailureMapper assertionFailureMapper,
            GoalRouteService goalRouteService,
            ExecutionContractAssembler executionContractAssembler,
            RegistryService registryService,
            WorkspaceTraceService workspaceTraceService,
            TaskDetailQueryService taskDetailQueryService,
            TaskResultQueryService taskResultQueryService,
            TaskAuditQueryService taskAuditQueryService,
            TaskManifestQueryService taskManifestQueryService,
            TaskCatalogQueryService taskCatalogQueryService,
            TaskContractQueryService taskContractQueryService,
            SessionLifecycleService sessionLifecycleService,
            ObjectMapper objectMapper,
            @Value("${sage.upload.root:BackEnd/runtime/uploads}") String uploadRoot
    ) {
        this.taskStateMapper = taskStateMapper;
        this.analysisManifestMapper = analysisManifestMapper;
        this.jobRecordMapper = jobRecordMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
        this.repairRecordMapper = repairRecordMapper;
        this.taskAttemptMapper = taskAttemptMapper;
        this.taskCatalogSnapshotService = taskCatalogSnapshotService;
        this.eventService = eventService;
        this.auditService = auditService;
        this.cognitionFinalExplanationClient = cognitionFinalExplanationClient;
        this.cognitionGoalRouteClient = cognitionGoalRouteClient;
        this.pass1Client = pass1Client;
        this.cognitionPassBClient = cognitionPassBClient;
        this.validationClient = validationClient;
        this.pass2Client = pass2Client;
        this.jobRuntimeClient = jobRuntimeClient;
        this.repairDispatcherService = repairDispatcherService;
        this.repairProposalService = repairProposalService;
        this.assertionFailureMapper = assertionFailureMapper;
        this.goalRouteService = goalRouteService;
        this.executionContractAssembler = executionContractAssembler;
        this.registryService = registryService;
        this.workspaceTraceService = workspaceTraceService;
        this.taskDetailQueryService = taskDetailQueryService;
        this.taskResultQueryService = taskResultQueryService;
        this.taskAuditQueryService = taskAuditQueryService;
        this.taskManifestQueryService = taskManifestQueryService;
        this.taskCatalogQueryService = taskCatalogQueryService;
        this.taskContractQueryService = taskContractQueryService;
        this.sessionLifecycleService = sessionLifecycleService;
        this.objectMapper = objectMapper;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    public TaskService(
            TaskStateMapper taskStateMapper,
            AnalysisManifestMapper analysisManifestMapper,
            JobRecordMapper jobRecordMapper,
            TaskAttachmentMapper taskAttachmentMapper,
            RepairRecordMapper repairRecordMapper,
            TaskAttemptMapper taskAttemptMapper,
            TaskCatalogSnapshotService taskCatalogSnapshotService,
            EventService eventService,
            AuditService auditService,
            CognitionFinalExplanationClient cognitionFinalExplanationClient,
            CognitionGoalRouteClient cognitionGoalRouteClient,
            Pass1Client pass1Client,
            CognitionPassBClient cognitionPassBClient,
            ValidationClient validationClient,
            Pass2Client pass2Client,
            JobRuntimeClient jobRuntimeClient,
            RepairDispatcherService repairDispatcherService,
            RepairProposalService repairProposalService,
            AssertionFailureMapper assertionFailureMapper,
            GoalRouteService goalRouteService,
            ExecutionContractAssembler executionContractAssembler,
            RegistryService registryService,
            WorkspaceTraceService workspaceTraceService,
            TaskDetailQueryService taskDetailQueryService,
            TaskResultQueryService taskResultQueryService,
            TaskAuditQueryService taskAuditQueryService,
            TaskManifestQueryService taskManifestQueryService,
            TaskCatalogQueryService taskCatalogQueryService,
            TaskContractQueryService taskContractQueryService,
            ObjectMapper objectMapper,
            String uploadRoot
    ) {
        this(
                taskStateMapper,
                analysisManifestMapper,
                jobRecordMapper,
                taskAttachmentMapper,
                repairRecordMapper,
                taskAttemptMapper,
                taskCatalogSnapshotService,
                eventService,
                auditService,
                cognitionFinalExplanationClient,
                cognitionGoalRouteClient,
                pass1Client,
                cognitionPassBClient,
                validationClient,
                pass2Client,
                jobRuntimeClient,
                repairDispatcherService,
                repairProposalService,
                assertionFailureMapper,
                goalRouteService,
                executionContractAssembler,
                registryService,
                workspaceTraceService,
                taskDetailQueryService,
                taskResultQueryService,
                taskAuditQueryService,
                taskManifestQueryService,
                taskCatalogQueryService,
                taskContractQueryService,
                new NoOpSessionLifecycleService(),
                objectMapper,
                uploadRoot
        );
    }

    @Transactional
    public CreateTaskResponse createTask(Long userId, CreateTaskRequest request) {
        AnalysisSession session = sessionLifecycleService.createSessionShell(userId, request.getUserQuery(), null, null);
        return createTaskInSession(userId, request, session.getSessionId(), true);
    }

    @Transactional
    public CreateTaskResponse createTaskInSession(Long userId, CreateTaskRequest request, String sessionId, boolean appendUserGoalMessage) {
        String traceId = UUID.randomUUID().toString();
        String taskId = TaskIdGenerator.generate();

        TaskState taskState = new TaskState();
        taskState.setTaskId(taskId);
        taskState.setSessionId(sessionId);
        taskState.setUserId(userId);
        taskState.setCurrentState(TaskStatus.CREATED.name());
        taskState.setStateVersion(0);
        taskState.setUserQuery(request.getUserQuery());
        taskState.setResumeAttemptCount(0);
        taskState.setActiveAttemptNo(1);
        taskState.setPlanningRevision(0);
        taskState.setCheckpointVersion(0);
        taskState.setInventoryVersion(0);
        taskStateMapper.insert(taskState);
        sessionLifecycleService.bindTaskToSession(sessionId, taskId, request.getUserQuery());
        if (appendUserGoalMessage) {
            sessionLifecycleService.recordUserGoal(sessionId, taskId, request.getUserQuery());
        }

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

            CognitionGoalRouteResponse goalRouteResponse = runGoalRoute(taskId, request.getUserQuery(), currentVersion, null);
            ObjectNode goalParseNode = buildGoalParseNode(goalRouteResponse, request.getUserQuery());
            ObjectNode skillRouteNode = buildSkillRouteNode(goalRouteResponse);
            String goalParseJson = writeJson(goalParseNode);
            String skillRouteJson = writeJson(skillRouteNode);
            taskStateMapper.updateGoalAndRoute(taskId, goalParseJson, skillRouteJson);
            taskState.setGoalParseJson(goalParseJson);
            taskState.setSkillRouteJson(skillRouteJson);
            appendEvent(taskId, EventType.GOAL_PARSED.name(), null, null, currentVersion, goalParseJson);
            appendEvent(taskId, EventType.SKILL_ROUTED.name(), null, null, currentVersion, skillRouteJson);
            sessionLifecycleService.recordAssistantUnderstanding(taskState);

            if ("unsupported".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                String failureSummaryJson = writePayload(buildUnsupportedFailureSummaryPayload(goalRouteNodeSummary(goalParseNode)));
                return transitionCreateToFailed(
                        taskId,
                        currentState,
                        failureSummaryJson,
                        "UNSUPPORTED",
                        currentVersion,
                        currentVersion + 1,
                        currentVersion + 1
                );
            }

            if ("ambiguous".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                WaitingStateSnapshot waitingState = buildClarifyWaitingState(taskId, "CLARIFY_INTENT", "Clarify the requested analysis before resuming.", null);
                return transitionCreateToWaiting(
                        taskId,
                        taskState,
                        currentVersion,
                        currentState,
                        waitingState,
                        "AMBIGUOUS",
                        currentVersion + 1
                );
            }

            if (isRealCaseRoute(skillRouteNode)) {
                String cognitionFailureCode = evaluateRequiredLlmMetadata(goalParseNode.path("cognition_metadata"));
                if (cognitionFailureCode != null) {
                    return failCreateForRequiredCognition(
                            taskId,
                            currentVersion,
                            currentState,
                            traceId,
                            "goal-route",
                            cognitionFailureCode,
                            goalParseNode
                    );
                }
                if (isClarifyRequiredCaseProjection(goalParseNode)) {
                    WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                            taskId,
                            resolveCaseProjectionWaitingReason(goalParseNode, "CLARIFY_CASE_SELECTION"),
                            resolveCaseProjectionPrompt(goalParseNode, "Choose a governed case before resuming."),
                            null,
                            goalParseNode.path("case_projection")
                    );
                    return transitionCreateToWaiting(
                            taskId,
                            taskState,
                            currentVersion,
                            currentState,
                            waitingState,
                            "LLM_AMBIGUOUS",
                            currentVersion + 1
                    );
                }
            }

            appendEvent(taskId, EventType.PLANNING_PASS1_STARTED.name(), null, null, currentVersion, null);
            Pass1Response pass1Response = runPass1(
                    taskId,
                    request.getUserQuery(),
                    currentVersion,
                    skillRouteNode.path("capability_key").asText(null),
                    skillRouteNode.path("selected_template").asText(null)
            );
            String pass1Json = objectMapper.writeValueAsString(pass1Response);
            JsonNode pass1Node = objectMapper.readTree(pass1Json);
            skillRouteNode.put("skill_id", pass1Response.getSkillId());
            skillRouteNode.put("skill_version", pass1Response.getSkillVersion());
            skillRouteNode.put("selected_template", pass1Response.getSelectedTemplate());
            skillRouteNode.put("template_version", pass1Response.getTemplateVersion());
            String enrichedGoalParseJson = writeJson(goalParseNode);
            String enrichedSkillRouteJson = writeJson(skillRouteNode);
            taskStateMapper.updateGoalAndRoute(taskId, enrichedGoalParseJson, enrichedSkillRouteJson);
            goalParseJson = enrichedGoalParseJson;
            skillRouteJson = enrichedSkillRouteJson;
            ensureUpdated(taskStateMapper.updateStateAndPass1(taskId, currentVersion, TaskStatus.PLANNING.name(), pass1Json));
            appendEvent(
                    taskId,
                    EventType.PLANNING_PASS1_COMPLETED.name(),
                    null,
                    null,
                    currentVersion + 1,
                    writePayload(TaskControlPayloadBuilder.buildPass1CompletedPayload(pass1Response.getSelectedTemplate()))
            );
            currentVersion += 1;
            currentState = TaskStatus.PLANNING;

            PassBStageResult passBStage = runPassBStage(
                    taskId,
                    request.getUserQuery(),
                    currentVersion,
                    goalParseNode,
                    skillRouteNode,
                    pass1Node,
                    null,
                    null
            );
            String passBJson = passBStage.passBJson();
            JsonNode passBNode = passBStage.passBNode();
            String cognitionVerdict = CognitionVerdictResolver.resolve(goalParseNode, passBNode, passBStage.assemblyResult());
            taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);

            String passBHardFailureCode = evaluatePassBHardFailureMetadata(passBNode.path("cognition_metadata"));
            if (passBHardFailureCode != null) {
                return failCreateForRequiredCognition(
                        taskId,
                        currentVersion,
                        currentState,
                        traceId,
                        "passb",
                        passBHardFailureCode,
                        passBNode
                );
            }

            if (isRealCaseRoute(skillRouteNode)) {
                String cognitionFailureCode = evaluateRequiredLlmMetadata(passBNode.path("cognition_metadata"));
                if (cognitionFailureCode != null) {
                    return failCreateForRequiredCognition(
                            taskId,
                            currentVersion,
                            currentState,
                            traceId,
                            "passb",
                            cognitionFailureCode,
                            passBNode
                    );
                }
                if (isClarifyRequiredCaseProjection(passBNode)) {
                    WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                            taskId,
                            resolveCaseProjectionWaitingReason(passBNode, "CLARIFY_CASE_SELECTION"),
                            resolveCaseProjectionPrompt(passBNode, "Choose a governed case before resuming."),
                            null,
                            passBNode.path("case_projection")
                    );
                    return transitionCreateToWaiting(
                            taskId,
                            taskState,
                            currentVersion,
                            currentState,
                            waitingState,
                            null,
                            currentVersion + 1
                    );
                }
            }

            CapabilityContractGuard.requireResumeAckContract(pass1Node);

            if ("ambiguous".equalsIgnoreCase(passBNode.path("binding_status").asText(""))) {
                WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                        taskId,
                        "CLARIFY_BINDING",
                        "Clarify the requested bindings before resuming.",
                        null
                );
                return transitionCreateToWaiting(
                        taskId,
                        taskState,
                        currentVersion,
                        currentState,
                        waitingState,
                        null,
                        currentVersion + 1
                );
            }

            ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.VALIDATING.name()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.VALIDATING.name(), currentVersion + 1, null);
            currentVersion += 1;
            currentState = TaskStatus.VALIDATING;

            ValidationStageResult validationStage = runValidationStage(taskId, currentVersion, pass1Node, passBNode);
            currentVersion = advanceAfterValidationTransition(
                    taskId,
                    currentVersion,
                    currentState,
                    validationStage.nextState(),
                    passBJson,
                    validationStage.passBProjection(),
                    validationStage.validationSummaryJson(),
                    validationStage.inputChainStatus()
            );
            currentState = validationStage.nextState();

            if (!Boolean.TRUE.equals(validationStage.validationResponse().getIsValid())) {
                if (TaskStatus.FAILED.equals(validationStage.nextState())) {
                    String failureSummaryJson = writePayload(
                            TaskControlPayloadBuilder.buildFatalValidationFailureSummaryPayload(
                                    validationStage.validationResponse(),
                                    OffsetDateTime.now(ZoneOffset.UTC).toString()
                            )
                    );
                    transitionCreateToFailed(
                            taskId,
                            currentState,
                            failureSummaryJson,
                            null,
                            null,
                            null,
                            currentVersion
                    );
                    appendTaskCreateAudit(
                            taskId,
                            "FAILED",
                            traceId,
                            currentState.name(),
                            false,
                            validationStage.inputChainStatus(),
                            false,
                            null,
                            "FATAL_VALIDATION"
                    );
                    return buildCreateTaskResponse(taskId, null, currentState.name(), currentVersion);
                }

                WaitingStateSnapshot waitingState = rebuildWaitingState(taskId, pass1Node, validationStage.validationNode(), null, null);
                recordWaitingUserEntry(
                        taskId,
                        resolveActiveAttemptNo(taskState),
                        waitingState,
                        null,
                        currentVersion
                );

                appendTaskCreateAudit(
                        taskId,
                        "SUCCESS",
                        traceId,
                        currentState.name(),
                        false,
                        validationStage.inputChainStatus(),
                        false,
                        null,
                        null
                );
                return buildCreateTaskResponse(taskId, null, currentState.name(), currentVersion);
            }

            int attemptNo = resolveActiveAttemptNo(taskState);
            PreparedJobSubmission preparedSubmission = prepareAcceptedJobSubmission(
                    taskId,
                    request.getUserQuery(),
                    currentVersion,
                    attemptNo,
                    objectMapper.readTree(goalParseJson),
                    objectMapper.readTree(skillRouteJson),
                    pass1Node,
                    passBNode,
                    validationStage.validationNode()
            );
            taskAttemptMapper.updateSnapshotAndJob(
                    taskId,
                    attemptNo,
                    preparedSubmission.createJobResponse().getJobId(),
                    writePayload(TaskControlPayloadBuilder.buildQueuedAttemptSnapshotPayload(preparedSubmission.createJobResponse().getJobId())),
                    null
            );
            int committedPlanningRevision = nextPlanningRevision(taskState);
            int committedCheckpointVersion = nextCheckpointVersion(taskState);
            TaskExecutionSubmissionSupport.freezeManifestOnCommit(
                    preparedSubmission.manifestCandidate(),
                    currentVersion,
                    analysisManifestMapper,
                    eventService,
                    objectMapper
            );
            ensureUpdated(taskStateMapper.commitQueuedWithGovernance(
                    taskId,
                    currentVersion,
                    TaskStatus.QUEUED.name(),
                    preparedSubmission.pass2Json(),
                    preparedSubmission.createJobResponse().getJobId(),
                    preparedSubmission.manifestCandidate().getManifestId(),
                    preparedSubmission.manifestCandidate().getManifestVersion(),
                    committedPlanningRevision,
                    committedCheckpointVersion,
                    currentInventoryVersion(taskState),
                    cognitionVerdict,
                    null
            ));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.QUEUED.name(), currentVersion + 1, null);
            currentVersion += 1;
            taskState.setCurrentState(TaskStatus.QUEUED.name());
            taskState.setStateVersion(currentVersion);
            taskState.setLatestResultBundleId(null);
            sessionLifecycleService.syncFromTask(taskState);
            sessionLifecycleService.recordProgressUpdate(
                    taskState,
                    TaskStatus.QUEUED.name(),
                    "Queued for runtime execution",
                    "The governed task was accepted and queued.",
                    "Runtime start"
            );

            appendTaskCreateAudit(
                    taskId,
                    "SUCCESS",
                    traceId,
                    TaskStatus.QUEUED.name(),
                    true,
                    validationStage.inputChainStatus(),
                    true,
                    preparedSubmission.createJobResponse().getJobId(),
                    null
            );
            return buildCreateTaskResponse(taskId, preparedSubmission.createJobResponse().getJobId(), TaskStatus.QUEUED.name(), currentVersion);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Task create pipeline failed for task {}", taskId, exception);
            TaskExecutionSubmissionSupport.handlePipelineFailure(
                    taskId,
                    traceId,
                    exception,
                    currentVersion,
                    currentState,
                    taskStateMapper,
                    eventService,
                    auditService,
                    objectMapper
            );
            throw new ResponseStatusException(BAD_GATEWAY, "Task pipeline failed", exception);
        }
    }

    public TaskDetailResponse getTask(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        AnalysisManifest activeManifest = resolveActiveManifest(taskState);
        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        return taskDetailQueryService.buildTaskDetailResponse(
                taskState,
                attachments,
                activeManifest,
                latestRepair,
                jobRecord
        );
    }

    public TaskResultResponse getTaskResult(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        AnalysisManifest activeManifest = resolveActiveManifest(taskState);
        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        return taskResultQueryService.buildTaskResultResponse(
                taskState,
                attachments,
                activeManifest,
                jobRecord,
                latestRepair
        );
    }

    public TaskRunsResponse getTaskRuns(String taskId, Long userId) {
        getOwnedTask(taskId, userId);
        TaskRunsResponse response = new TaskRunsResponse();
        response.setTaskId(taskId);
        Map<Integer, com.sage.backend.model.WorkspaceRegistry> workspacesByAttempt = new LinkedHashMap<>();
        for (com.sage.backend.model.WorkspaceRegistry workspace : workspaceTraceService.findRuns(taskId)) {
            workspacesByAttempt.put(workspace.getAttemptNo(), workspace);
        }

        for (TaskAttempt attempt : taskAttemptMapper.findByTaskId(taskId)) {
            TaskRunsResponse.RunItem item = new TaskRunsResponse.RunItem();
            item.setAttemptNo(attempt.getAttemptNo());
            item.setJobId(attempt.getJobId());
            item.setCreatedAt(toIsoString(attempt.getCreatedAt()));
            item.setFinishedAt(toIsoString(attempt.getFinishedAt()));

            com.sage.backend.model.WorkspaceRegistry workspace = workspacesByAttempt.get(attempt.getAttemptNo());
            if (workspace != null) {
                item.setWorkspaceId(workspace.getWorkspaceId());
                item.setWorkspaceState(workspace.getWorkspaceState());
            }

            JobRecord jobRecord = attempt.getJobId() == null ? null : jobRecordMapper.findByJobId(attempt.getJobId());
            if (jobRecord != null) {
                item.setJobState(jobRecord.getJobState());
                JsonNode resultBundle = readJsonNode(jobRecord.getResultBundleJson());
                if (resultBundle != null) {
                    item.setResultBundleId(resultBundle.path("result_id").asText(null));
                }
            }
            response.getItems().add(item);
        }
        return response;
    }

    public TaskArtifactsResponse getTaskArtifacts(String taskId, Long userId) {
        getOwnedTask(taskId, userId);
        TaskArtifactsResponse response = new TaskArtifactsResponse();
        response.setTaskId(taskId);

        Map<Integer, com.sage.backend.model.WorkspaceRegistry> workspacesByAttempt = new LinkedHashMap<>();
        for (com.sage.backend.model.WorkspaceRegistry workspace : workspaceTraceService.findRuns(taskId)) {
            workspacesByAttempt.put(workspace.getAttemptNo(), workspace);
        }

        Map<Integer, TaskArtifactsResponse.AttemptArtifacts> attempts = new LinkedHashMap<>();
        for (Map.Entry<Integer, com.sage.backend.model.WorkspaceRegistry> entry : workspacesByAttempt.entrySet()) {
            attempts.put(entry.getKey(), buildAttemptArtifacts(entry.getKey(), entry.getValue()));
        }

        for (com.sage.backend.model.ArtifactIndexRecord artifact : workspaceTraceService.findArtifacts(taskId)) {
            TaskArtifactsResponse.AttemptArtifacts attempt = attempts.computeIfAbsent(
                    artifact.getAttemptNo(),
                    key -> buildAttemptArtifacts(key, workspacesByAttempt.get(key))
            );
            List<TaskArtifactsResponse.ArtifactMeta> target = selectArtifactBucket(
                    attempt.getArtifacts(),
                    mapArtifactRoleBucket(artifact.getArtifactRole())
            );
            TaskArtifactsResponse.ArtifactMeta artifactView = new TaskArtifactsResponse.ArtifactMeta();
            artifactView.setArtifactId(artifact.getArtifactId());
            artifactView.setArtifactRole(artifact.getArtifactRole());
            artifactView.setLogicalName(artifact.getLogicalName());
            artifactView.setRelativePath(artifact.getRelativePath());
            artifactView.setAbsolutePath(artifact.getAbsolutePath());
            artifactView.setContentType(artifact.getContentType());
            artifactView.setSizeBytes(artifact.getSizeBytes());
            artifactView.setSha256(artifact.getSha256());
            artifactView.setCreatedAt(toIsoString(artifact.getCreatedAt()));
            target.add(artifactView);
        }

        response.getItems().addAll(attempts.values());
        return response;
    }

    public TaskAuditResponse getTaskAudit(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        return taskAuditQueryService.buildTaskAuditResponse(
                taskId,
                taskState,
                attachments,
                auditService.findByTaskId(taskId)
        );
    }

    public TaskCatalogResponse getTaskCatalog(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        return taskCatalogQueryService.buildTaskCatalogResponse(
                taskId,
                taskState,
                attachments,
                auditService.findByTaskId(taskId)
        );
    }

    public TaskContractResponse getTaskContract(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        AnalysisManifest activeManifest = resolveActiveManifest(taskState);
        return taskContractQueryService.buildTaskContractResponse(
                taskId,
                taskState,
                activeManifest,
                auditService.findByTaskId(taskId)
        );
    }

    public TaskManifestResponse getTaskManifest(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        AnalysisManifest manifest = null;
        if (taskState.getActiveManifestId() != null && !taskState.getActiveManifestId().isBlank()) {
            manifest = analysisManifestMapper.findByManifestId(taskState.getActiveManifestId());
        }
        if (manifest == null) {
        int attemptNo = resolveActiveAttemptNo(taskState);
            manifest = analysisManifestMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        }
        if (manifest == null) {
            throw new ResponseStatusException(NOT_FOUND, "Analysis manifest not available yet");
        }
        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        return taskManifestQueryService.buildTaskManifestResponse(
                taskState,
                manifest,
                attachments,
                latestRepair,
                jobRecord
        );
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
        JsonNode pass1Node = readJsonNode(taskState.getPass1ResultJson());
        CapabilityContractGuard.requireCancelJobContract(pass1Node);
        appendAuditWithContract(
                pass1Node,
                taskId,
                "TASK_CANCEL",
                "REQUESTED",
                taskState.getTaskId(),
                writePayload(TaskControlPayloadBuilder.buildCancelledJobEventPayload(taskState.getJobId(), CANCEL_REASON_USER_REQUESTED))
        );
        appendEvent(
                taskId,
                EventType.CANCEL_REQUESTED.name(),
                null,
                null,
                taskState.getStateVersion(),
                writePayload(TaskControlPayloadBuilder.buildJobReferencePayload(taskState.getJobId()))
        );
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
            if (latestTaskState != null) {
                sessionLifecycleService.syncFromTask(latestTaskState);
            }
            return response;
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(CONFLICT, "Job already terminal", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Cancel failed for task {}", taskId, exception);
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
            attachment.setAttemptNo(resolveActiveAttemptNo(taskState));
            attachment.setLogicalSlot((logicalSlot == null || logicalSlot.isBlank()) ? null : logicalSlot.trim());
            attachment.setAssignmentStatus(attachment.getLogicalSlot() == null ? "UNASSIGNED" : "ASSIGNED");
            attachment.setFileName(cleanedFileName);
            attachment.setContentType(file.getContentType());
            attachment.setSizeBytes(file.getSize());
            attachment.setStoredPath(target.toString());
            attachment.setChecksum(hexSha256(file.getBytes()));
            attachment.setUploadedBy(userId);
            ensureInserted(taskAttachmentMapper.insert(attachment));

            appendEvent(
                    taskId,
                    EventType.ATTACHMENT_UPLOADED.name(),
                    null,
                    null,
                    taskState.getStateVersion(),
                    writeJson(TaskControlPayloadBuilder.buildAttachmentUploadedPayload(attachmentId, attachment))
            );
            taskStateMapper.incrementInventoryVersion(taskId);
            TaskState refreshedTaskState = taskStateMapper.findByTaskId(taskId);
            taskCatalogSnapshotService.persistCatalogSnapshot(taskId, taskAttachmentMapper.findByTaskId(taskId), currentInventoryVersion(refreshedTaskState));

            refreshWaitingContext(taskId);
            TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
            sessionLifecycleService.recordUploadAck(latestTaskState == null ? refreshedTaskState : latestTaskState, attachment);
            sessionLifecycleService.syncFromTask(latestTaskState == null ? refreshedTaskState : latestTaskState);

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

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public ResumeTaskResponse resumeTask(String taskId, Long userId, ResumeTaskRequest request) {
        TaskState taskState = getOwnedTask(taskId, userId);
        String resumeRequestId = request.getResumeRequestId();
        if (resumeRequestId == null || resumeRequestId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "resume_request_id is required");
        }

        RepairRecord existing = repairRecordMapper.findByTaskIdAndResumeRequestId(taskId, resumeRequestId);
        if (existing != null) {
            TaskState latest = taskStateMapper.findByTaskId(taskId);
            return buildResumeTaskResponse(
                    taskId,
                    latest.getCurrentState(),
                    latest.getStateVersion(),
                    "ACCEPTED".equals(existing.getResult()),
                    existing.getAttemptNo()
            );
        }

        if (!TaskStatus.WAITING_USER.name().equals(taskState.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, "Task is not in WAITING_USER state");
        }

        appendEvent(
                taskId,
                EventType.RESUME_REQUESTED.name(),
                null,
                null,
                taskState.getStateVersion(),
                writeJson(TaskControlPayloadBuilder.buildResumeRequestEventPayload(resumeRequestId))
        );

        WaitingStateSnapshot waitingState = rebuildWaitingState(taskState, request.getUserNote());
        if (!isMinReady(taskId, waitingState, request)) {
            insertRepairRecord(
                    taskId,
                    resolveActiveAttemptNo(taskState),
                    resumeRequestId,
                    writeJson(request),
                    "REJECTED",
                    waitingState.repairProposal(),
                    "RECOVERABLE",
                    "WAITING_USER"
            );
            appendEvent(
                    taskId,
                    EventType.RESUME_REJECTED.name(),
                    null,
                    null,
                    taskState.getStateVersion(),
                    writeJson(TaskControlPayloadBuilder.buildResumeRejectedEventPayload(resumeRequestId))
            );
            throw new ResponseStatusException(CONFLICT, "Required user actions are not satisfied");
        }

        validateAttachmentOwnership(taskId, request.getAttachmentIds());

        int newResumeCount = (taskState.getResumeAttemptCount() == null ? 0 : taskState.getResumeAttemptCount()) + 1;
        int newAttemptNo = newResumeCount + 1;
        String resumePayloadJson = writeJson(request);

        int previousAttemptNo = resolveActiveAttemptNo(taskState);
        taskAttemptMapper.updateSnapshotAndJob(
                taskId,
                previousAttemptNo,
                taskState.getJobId(),
                writeJson(TaskControlPayloadBuilder.buildResumeDeactivatedAttemptSnapshotPayload(taskState.getCurrentState())),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts = TaskGovernanceFactSupport.catalogFacts(
                waitingState.waitingContext().getCatalogSummary()
        );
        TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope =
                TaskResumeGovernanceSupport.buildResumeCatalogScope(
                        currentCatalogFacts,
                        taskState.getInventoryVersion() == null ? 0 : taskState.getInventoryVersion() + 1
                );

        String resumeTxnJson = writeJson(buildResumeTransactionPayload(
                resumeRequestId,
                "PREPARING",
                taskState.getCheckpointVersion(),
                taskState.getCheckpointVersion() == null ? 1 : taskState.getCheckpointVersion() + 1,
                resumeCatalogScope,
                null,
                newAttemptNo,
                null,
                null
        ));

        ensureUpdated(taskStateMapper.acceptResume(taskId, taskState.getStateVersion(), TaskStatus.RESUMING.name(),
                resumePayloadJson, resumeTxnJson, newResumeCount, newAttemptNo));

        TaskAttempt taskAttempt = new TaskAttempt();
        taskAttempt.setTaskId(taskId);
        taskAttempt.setAttemptNo(newAttemptNo);
        taskAttempt.setTrigger("RESUME");
        taskAttempt.setStatusSnapshotJson(writeJson(TaskControlPayloadBuilder.buildValidatingAttemptSnapshotPayload(resumeRequestId)));
        ensureInserted(taskAttemptMapper.insert(taskAttempt));

        insertRepairRecord(
                taskId,
                newAttemptNo,
                resumeRequestId,
                resumePayloadJson,
                "ACCEPTED",
                repairProposalService.generate(
                        waitingState.waitingContext(),
                        RepairFactHelper.readValidationSummary(objectMapper, readJsonNode(taskState.getValidationSummaryJson())),
                        null,
                        request.getUserNote()
                ),
                "RECOVERABLE",
                "VALIDATING"
        );

        appendEvent(
                taskId,
                EventType.RESUME_ACCEPTED.name(),
                null,
                null,
                taskState.getStateVersion() + 1,
                writeJson(TaskControlPayloadBuilder.buildResumeAcceptedEventPayload(resumeRequestId, newAttemptNo))
        );
        appendEvent(
                taskId,
                EventType.STATE_CHANGED.name(),
                TaskStatus.WAITING_USER.name(),
                TaskStatus.RESUMING.name(),
                taskState.getStateVersion() + 1,
                null
        );
        TaskState acceptedResumeTask = taskStateMapper.findByTaskId(taskId);
        sessionLifecycleService.recordResumeNotice(acceptedResumeTask == null ? taskState : acceptedResumeTask, resumeRequestId, newAttemptNo);
        sessionLifecycleService.syncFromTask(acceptedResumeTask == null ? taskState : acceptedResumeTask);

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

    @Transactional
    public ForceRevertCheckpointResponse forceRevertCheckpoint(
            String taskId,
            CurrentUser currentUser,
            ForceRevertCheckpointRequest request
    ) {
        if (currentUser == null || !currentUser.isAdmin()) {
            throw new ResponseStatusException(FORBIDDEN, "Admin role required");
        }
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "request_id is required");
        }
        if (request.getTargetCheckpointVersion() == null || request.getTargetCheckpointVersion() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "target_checkpoint_version is required");
        }

        TaskState taskState = taskStateMapper.findByTaskId(taskId);
        if (taskState == null) {
            throw new ResponseStatusException(NOT_FOUND, "Task not found");
        }
        if (!TaskStatus.STATE_CORRUPTED.name().equals(taskState.getCurrentState())) {
            throw new ResponseStatusException(CONFLICT, "Task is not in STATE_CORRUPTED");
        }

        AnalysisManifest manifest = analysisManifestMapper.findLatestFrozenByTaskIdAndCheckpointVersion(
                taskId,
                request.getTargetCheckpointVersion()
        );
        if (manifest == null) {
            throw new ResponseStatusException(NOT_FOUND, "Frozen manifest not found for target checkpoint");
        }

        TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts = TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                taskId,
                taskState,
                taskAttachmentMapper,
                taskCatalogSnapshotService
        );
        TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope =
                TaskResumeGovernanceSupport.buildForceRevertCatalogScope(taskState, currentCatalogFacts);

        String resumeTxnJson = writeJson(buildResumeTransactionPayload(
                request.getRequestId(),
                "FORCE_REVERTED",
                taskState.getCheckpointVersion(),
                manifest.getCheckpointVersion(),
                resumeCatalogScope,
                manifest.getManifestId(),
                manifest.getAttemptNo(),
                null,
                null
        ));

        ensureUpdated(taskStateMapper.forceRevertCheckpoint(
                taskId,
                taskState.getStateVersion(),
                TaskStatus.WAITING_USER.name(),
                manifest.getManifestId(),
                manifest.getManifestVersion(),
                manifest.getAttemptNo(),
                manifest.getPlanningRevision(),
                manifest.getCheckpointVersion(),
                resumeTxnJson
        ));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), TaskStatus.STATE_CORRUPTED.name(), TaskStatus.WAITING_USER.name(), taskState.getStateVersion() + 1, null);
        refreshWaitingContext(taskId);

        TaskState latest = taskStateMapper.findByTaskId(taskId);
        ForceRevertCheckpointResponse response = new ForceRevertCheckpointResponse();
        response.setTaskId(taskId);
        response.setState(latest.getCurrentState());
        response.setStateVersion(latest.getStateVersion());
        response.setCheckpointVersion(latest.getCheckpointVersion());
        response.setManifestId(latest.getActiveManifestId());
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
        List<JobRecord> activeJobs;
        try {
            activeJobs = jobRecordMapper.findActiveJobs();
        } catch (Exception ignored) {
            return;
        }
        for (JobRecord jobRecord : activeJobs) {
            try {
                syncSingleJob(jobRecord);
            } catch (Exception ignored) {
            }
        }
    }

    private void syncSingleJob(JobRecord jobRecord) throws Exception {
        TaskState taskState = taskStateMapper.findByTaskId(jobRecord.getTaskId());
        if (taskState == null || !Objects.equals(taskState.getJobId(), jobRecord.getJobId())) {
            return;
        }
        try {
            CapabilityContractGuard.requireQueryJobStatusContract(readJsonNode(taskState.getPass1ResultJson()));
        } catch (IllegalStateException exception) {
            markTaskCorruptedForCapabilityContract(taskState, exception.getMessage());
            return;
        }

        JobStatusResponse status = jobRuntimeClient.getJob(jobRecord.getJobId());
        String newState = safeString(status.getJobState());
        String previousState = safeString(jobRecord.getJobState());
        boolean terminal = isTerminalJobState(newState);

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
                writeJsonIfPresent(status.getWorkspaceSummary()),
                writeJsonIfPresent(status.getArtifactCatalog()),
                writeJsonIfPresent(status.getErrorObject()),
                status.getCancelRequestedAt(),
                status.getCancelledAt(),
                status.getCancelReason(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        JobRecord refreshedJobRecord = jobRecordMapper.findByJobId(jobRecord.getJobId());
        if (refreshedJobRecord != null) {
            jobRecord = refreshedJobRecord;
        }
        workspaceTraceService.updateWorkspaceFromStatus(jobRecord, status.getWorkspaceSummary(), status.getDockerRuntimeEvidence(), terminal);

        taskState = taskStateMapper.findByTaskId(jobRecord.getTaskId());
        if (taskState == null || !Objects.equals(taskState.getJobId(), jobRecord.getJobId()) || Objects.equals(previousState, newState)) {
            return;
        }

        appendEvent(
                taskState.getTaskId(),
                EventType.JOB_STATE_CHANGED.name(),
                previousState,
                newState,
                taskState.getStateVersion(),
                writePayload(TaskControlPayloadBuilder.buildJobReferencePayload(jobRecord.getJobId()))
        );
        if (JobState.RUNNING.name().equals(newState)) {
            sessionLifecycleService.syncFromTask(taskState);
            sessionLifecycleService.recordProgressUpdate(
                    taskState,
                    TaskStatus.RUNNING.name(),
                    "Runtime execution is in progress",
                    "The current task is actively executing in the runtime.",
                    "Result packaging"
            );
        }

        if (JobState.SUCCEEDED.name().equals(newState)) {
            processSuccess(taskState, jobRecord, status);
            return;
        }

        TerminalFailureHandling terminalHandling = handleNonSuccessTerminalState(taskState, jobRecord, status, newState);
        TaskStatus projected = terminalHandling == null ? mapJobStateToTaskState(newState) : terminalHandling.projectedState();
        if (projected != null && !projected.name().equals(taskState.getCurrentState())) {
            if (projected == TaskStatus.WAITING_USER && terminalHandling != null && terminalHandling.waitingState() != null) {
                transitionTerminalToWaiting(taskState, terminalHandling.waitingState());
                return;
            }
            transitionTerminalToState(taskState, projected, jobRecord);
        }
    }

    private void transitionTerminalToWaiting(TaskState taskState, WaitingStateSnapshot waitingState) {
        ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                taskState.getTaskId(),
                taskState.getStateVersion(),
                TaskStatus.WAITING_USER.name(),
                waitingState.waitingContextJson(),
                safeString(waitingState.decision().waitingContext().getWaitingReasonType()).isBlank()
                        ? "REPAIR_REQUIRED"
                        : waitingState.decision().waitingContext().getWaitingReasonType(),
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
        appendEvent(
                taskState.getTaskId(),
                EventType.STATE_CHANGED.name(),
                taskState.getCurrentState(),
                TaskStatus.WAITING_USER.name(),
                taskState.getStateVersion() + 1,
                null
        );
        recordWaitingUserEntry(
                taskState.getTaskId(),
                resolveActiveAttemptNo(taskState),
                waitingState,
                null,
                taskState.getStateVersion() + 1
        );
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskState.getTaskId());
        sessionLifecycleService.recordWaiting(latestTaskState == null ? taskState : latestTaskState);
    }

    private void transitionTerminalToState(TaskState taskState, TaskStatus projected, JobRecord jobRecord) throws Exception {
        ensureUpdated(taskStateMapper.updateState(taskState.getTaskId(), taskState.getStateVersion(), projected.name()));
        appendEvent(
                taskState.getTaskId(),
                EventType.STATE_CHANGED.name(),
                taskState.getCurrentState(),
                projected.name(),
                taskState.getStateVersion() + 1,
                null
        );
        if (projected == TaskStatus.CANCELLED) {
            appendEvent(
                    taskState.getTaskId(),
                    EventType.TASK_CANCELLED.name(),
                    null,
                    null,
                    taskState.getStateVersion() + 1,
                    writePayload(TaskControlPayloadBuilder.buildJobReferencePayload(jobRecord.getJobId()))
            );
        }
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskState.getTaskId());
        if (projected == TaskStatus.FAILED || projected == TaskStatus.STATE_CORRUPTED) {
            sessionLifecycleService.recordFailureExplanation(latestTaskState == null ? taskState : latestTaskState);
        } else {
            sessionLifecycleService.syncFromTask(latestTaskState == null ? taskState : latestTaskState);
        }
    }

    private void processSuccess(TaskState taskState, JobRecord jobRecord, JobStatusResponse status) throws Exception {
        TaskSuccessLifecycleSupport.PromotionState promotionState = TaskSuccessLifecycleSupport.enterArtifactPromoting(
                taskState,
                taskStateMapper,
                eventService
        );

        try {
            CapabilityContractGuard.requireCollectResultBundleContract(readJsonNode(taskState.getPass1ResultJson()));
            CapabilityContractGuard.requireIndexArtifactsContract(readJsonNode(taskState.getPass1ResultJson()));
            JsonNode finalExplanationNode = buildFinalExplanationNode(taskState, jobRecord, status);
            TaskSuccessProjectionSupport.SuccessProjection successProjection =
                    TaskSuccessProjectionSupport.buildSuccessProjection(status, finalExplanationNode, objectMapper);
            TaskSuccessLifecycleSupport.persistSuccessProjection(
                    taskState,
                    jobRecord,
                    status,
                    finalExplanationNode,
                    successProjection,
                    promotionState.stateVersion(),
                    taskStateMapper,
                    jobRecordMapper,
                    workspaceTraceService,
                    eventService,
                    objectMapper
            );
        } catch (Exception exception) {
            TaskSuccessLifecycleSupport.markArtifactPromotionCorrupted(
                    taskState,
                    promotionState,
                    exception,
                    taskStateMapper,
                    eventService
            );
            throw exception;
        }

        TaskSuccessLifecycleSupport.completeSuccessPromotion(
                taskState.getTaskId(),
                promotionState,
                taskStateMapper,
                eventService
        );
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskState.getTaskId());
        sessionLifecycleService.recordResultSummary(latestTaskState == null ? taskState : latestTaskState);
    }

    private void markTaskCorruptedForCapabilityContract(TaskState taskState, String failureReason) {
        if (taskState == null) {
            return;
        }
        ensureUpdated(taskStateMapper.markCorrupted(
                taskState.getTaskId(),
                taskState.getStateVersion(),
                TaskStatus.STATE_CORRUPTED.name(),
                failureReason,
                OffsetDateTime.now(ZoneOffset.UTC),
                taskState.getResumeTxnJson()
        ));
        appendEvent(
                taskState.getTaskId(),
                EventType.STATE_CHANGED.name(),
                taskState.getCurrentState(),
                TaskStatus.STATE_CORRUPTED.name(),
                taskState.getStateVersion() + 1,
                null
        );
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskState.getTaskId());
        sessionLifecycleService.recordFailureExplanation(latestTaskState == null ? taskState : latestTaskState);
    }

    private void appendAuditWithContract(
            JsonNode pass1Node,
            String taskId,
            String actionType,
            String actionResult,
            String traceId,
            String detailJson
    ) {
        CapabilityContractGuard.requireRecordAuditContract(pass1Node);
        appendAuditJson(taskId, actionType, actionResult, traceId, enrichAuditDetailWithContract(pass1Node, detailJson));
    }

    private void appendTaskCreateAudit(
            String taskId,
            String actionResult,
            String traceId,
            String state,
            boolean validationIsValid,
            String inputChainStatus,
            boolean jobCreated,
            String jobId,
            String failureCode
    ) throws Exception {
        appendAuditJson(
                taskId,
                "TASK_CREATE",
                actionResult,
                traceId,
                writePayload(TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                        state,
                        validationIsValid,
                        inputChainStatus,
                        jobCreated,
                        jobId,
                        failureCode
                ))
        );
    }

    private void appendAuditJson(
            String taskId,
            String actionType,
            String actionResult,
            String traceId,
            String detailJson
    ) {
        auditService.appendAudit(taskId, actionType, actionResult, traceId, detailJson);
    }

    private TerminalFailureHandling handleNonSuccessTerminalState(TaskState taskState, JobRecord jobRecord, JobStatusResponse status, String newState) throws Exception {
        if (!isTerminalJobState(newState) || JobState.SUCCEEDED.name().equals(newState)) {
            return null;
        }
        if (JobState.CANCELLED.name().equals(newState)) {
            appendJobCancelledEvent(taskState, jobRecord, status);
        }
        TerminalFailureProjection terminalFailure = persistTerminalFailureProjection(taskState, jobRecord, status, newState);
        if (JobState.FAILED.name().equals(newState)) {
            TerminalFailureHandling assertionHandling = resolveAssertionFailureHandling(taskState, terminalFailure.failureSummaryNode());
            if (assertionHandling != null) {
                return assertionHandling;
            }
        }
        return new TerminalFailureHandling(mapJobStateToTaskState(newState), null);
    }

    private void appendJobCancelledEvent(TaskState taskState, JobRecord jobRecord, JobStatusResponse status) throws Exception {
        appendEvent(
                taskState.getTaskId(),
                EventType.JOB_CANCELLED.name(),
                null,
                null,
                taskState.getStateVersion(),
                writePayload(TaskControlPayloadBuilder.buildCancelledJobEventPayload(jobRecord.getJobId(), status.getCancelReason()))
        );
    }

    private TerminalFailureProjection persistTerminalFailureProjection(
            TaskState taskState,
            JobRecord jobRecord,
            JobStatusResponse status,
            String newState
    ) throws Exception {
        String failureSummaryJson = writePayload(TaskProjectionBuilder.buildFailureSummaryPayload(
                status.getFailureSummary(),
                status.getErrorObject(),
                OffsetDateTime.now(ZoneOffset.UTC).toString()
        ));
        taskStateMapper.updateOutputSummaries(
                taskState.getTaskId(),
                null,
                null,
                failureSummaryJson,
                null
        );
        workspaceTraceService.persistArtifacts(taskState.getTaskId(), jobRecord, null, status.getArtifactCatalog());
        taskAttemptMapper.updateSnapshotAndJob(
                taskState.getTaskId(),
                resolveActiveAttemptNo(taskState),
                jobRecord.getJobId(),
                writePayload(TaskControlPayloadBuilder.buildAttemptRuntimeSnapshotPayload(newState, taskState.getCurrentState())),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return new TerminalFailureProjection(failureSummaryJson, readJsonNode(failureSummaryJson));
    }

    private TerminalFailureHandling resolveAssertionFailureHandling(TaskState taskState, JsonNode failureSummary) throws Exception {
        RepairDecision assertionDecision = assertionFailureMapper.map(
                failureSummary,
                readJsonNode(taskState.getPass1ResultJson()),
                taskAttachmentMapper.findByTaskId(taskState.getTaskId())
        );
        if (assertionDecision == null) {
            return null;
        }
        if (isFatalRepairDecision(assertionDecision)) {
            return new TerminalFailureHandling(TaskStatus.FAILED, null);
        }
        WaitingStateSnapshot waitingState = buildWaitingStateSnapshot(
                taskState.getTaskId(),
                assertionDecision,
                readJsonNode(taskState.getValidationSummaryJson()),
                failureSummary,
                null,
                false
        );
        return new TerminalFailureHandling(TaskStatus.WAITING_USER, waitingState);
    }

    private ResumeTaskResponse runResumePipeline(TaskState taskState, ResumeTaskRequest request) {
        String taskId = taskState.getTaskId();
        int currentVersion = taskState.getStateVersion();
        TaskStatus currentState = TaskStatus.valueOf(taskState.getCurrentState());
        int baseCheckpointVersion = currentCheckpointVersion(taskState);
        int candidateCheckpointVersion = baseCheckpointVersion + 1;
        int candidateInventoryVersion = nextResumeInventoryVersion(taskState);
        TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts = TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                taskId,
                taskState,
                taskAttachmentMapper,
                taskCatalogSnapshotService
        );
        TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope =
                TaskResumeGovernanceSupport.buildResumeCatalogScope(currentCatalogFacts, candidateInventoryVersion);
        PreparedJobSubmission preparedSubmission = null;

        try {
            ObjectNode goalParseNode;
            ObjectNode skillRouteNode;
            JsonNode pass1Node;
            ObjectNode passBNode;
            String passBJson;
            String cognitionVerdict;

            if (canReusePlanningForResume(taskState)) {
                goalParseNode = requireObjectNode(readJsonNode(taskState.getGoalParseJson()));
                skillRouteNode = requireObjectNode(readJsonNode(taskState.getSkillRouteJson()));
                pass1Node = readJsonNode(taskState.getPass1ResultJson());
                passBNode = requireObjectNode(readJsonNode(taskState.getPassbResultJson()));
                applyResumeInputs(pass1Node, passBNode, request, taskId);
                ExecutionContractAssembler.AssemblyResult assemblyResult = executionContractAssembler.assemble(taskId, skillRouteNode, pass1Node, passBNode);
                passBJson = writeJson(passBNode);
                cognitionVerdict = CognitionVerdictResolver.resolve(goalParseNode, passBNode, assemblyResult);
                taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
            } else if (canReuseGoalAndPass1ForClarifyResume(taskState, request)) {
                goalParseNode = requireObjectNode(readJsonNode(taskState.getGoalParseJson()));
                skillRouteNode = requireObjectNode(readJsonNode(taskState.getSkillRouteJson()));
                pass1Node = readJsonNode(taskState.getPass1ResultJson());
                PassBStageResult passBStage = runPassBStage(
                        taskId,
                        safeString(taskState.getUserQuery()),
                        currentVersion,
                        goalParseNode,
                        skillRouteNode,
                        pass1Node,
                        request,
                        readJsonNode(taskState.getWaitingContextJson())
                );
                passBNode = passBStage.passBNode();
                passBJson = passBStage.passBJson();
                cognitionVerdict = CognitionVerdictResolver.resolve(goalParseNode, passBNode, passBStage.assemblyResult());
                taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
            } else {
                CognitionGoalRouteResponse goalRouteResponse = runGoalRoute(
                        taskId,
                        safeString(taskState.getUserQuery()),
                        currentVersion,
                        request.getUserNote()
                );
                goalParseNode = buildGoalParseNode(goalRouteResponse, safeString(taskState.getUserQuery()));
                skillRouteNode = buildSkillRouteNode(goalRouteResponse);
                taskStateMapper.updateGoalAndRoute(taskId, writeJson(goalParseNode), writeJson(skillRouteNode));

                if ("unsupported".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                    String failureSummaryJson = writePayload(buildUnsupportedFailureSummaryPayload(goalRouteNodeSummary(goalParseNode)));
                    return rollbackResumeToFailed(
                            taskId,
                            taskState,
                            request,
                            currentState,
                            currentVersion,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            resumeCatalogScope,
                            "UNSUPPORTED",
                            failureSummaryJson,
                            "UNSUPPORTED",
                            currentVersion + 1
                    );
                }

                if ("ambiguous".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                    WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                            taskId,
                            "CLARIFY_INTENT",
                            "Clarify the requested analysis before resuming.",
                            request.getUserNote()
                    );
                    return rollbackResumeToWaiting(
                            taskId,
                            taskState,
                            request,
                            currentState,
                            currentVersion,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            resumeCatalogScope,
                            waitingState,
                            "AMBIGUOUS_INTENT",
                            "AMBIGUOUS"
                    );
                }

                if (isRealCaseRoute(skillRouteNode)) {
                    String cognitionFailureCode = evaluateRequiredLlmMetadata(goalParseNode.path("cognition_metadata"));
                    if (cognitionFailureCode != null) {
                        return failResumeForRequiredCognition(
                                taskState,
                                request,
                                currentVersion,
                                currentState,
                                baseCheckpointVersion,
                                candidateCheckpointVersion,
                                candidateInventoryVersion,
                                "goal-route",
                                cognitionFailureCode,
                                goalParseNode
                        );
                    }
                    if (isClarifyRequiredCaseProjection(goalParseNode)) {
                        WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                                taskId,
                                resolveCaseProjectionWaitingReason(goalParseNode, "CLARIFY_CASE_SELECTION"),
                                resolveCaseProjectionPrompt(goalParseNode, "Choose a governed case before resuming."),
                                request.getUserNote(),
                                goalParseNode.path("case_projection")
                        );
                        return rollbackResumeToWaiting(
                                taskId,
                                taskState,
                                request,
                                currentState,
                                currentVersion,
                                baseCheckpointVersion,
                                candidateCheckpointVersion,
                                resumeCatalogScope,
                                waitingState,
                                "CLARIFY_CASE_SELECTION",
                                "LLM_AMBIGUOUS"
                        );
                    }
                }

                appendEvent(taskId, EventType.PLANNING_PASS1_STARTED.name(), null, null, currentVersion, null);
                Pass1Response pass1Response = runPass1(
                        taskId,
                        safeString(taskState.getUserQuery()),
                        currentVersion,
                        skillRouteNode.path("capability_key").asText(null),
                        skillRouteNode.path("selected_template").asText(null)
                );
                String pass1Json = objectMapper.writeValueAsString(pass1Response);
                pass1Node = objectMapper.readTree(pass1Json);
                ensureResumeContractIdentityCompatible(
                        taskId,
                        taskState,
                        request,
                        currentVersion,
                        currentState,
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        resumeCatalogScope,
                        readJsonNode(taskState.getPass1ResultJson()),
                        pass1Node
                );
                skillRouteNode.put("skill_id", pass1Response.getSkillId());
                skillRouteNode.put("skill_version", pass1Response.getSkillVersion());
                skillRouteNode.put("selected_template", pass1Response.getSelectedTemplate());
                skillRouteNode.put("template_version", pass1Response.getTemplateVersion());
                taskStateMapper.updateGoalAndRoute(taskId, writeJson(goalParseNode), writeJson(skillRouteNode));
                ensureUpdated(taskStateMapper.updateStateAndPass1(taskId, currentVersion, TaskStatus.PLANNING.name(), pass1Json));
                appendEvent(
                        taskId,
                        EventType.PLANNING_PASS1_COMPLETED.name(),
                        null,
                        null,
                        currentVersion + 1,
                        writePayload(TaskControlPayloadBuilder.buildPass1CompletedPayload(pass1Response.getSelectedTemplate()))
                );
                currentVersion += 1;
                currentState = TaskStatus.PLANNING;

                PassBStageResult passBStage = runPassBStage(
                        taskId,
                        safeString(taskState.getUserQuery()),
                        currentVersion,
                        goalParseNode,
                        skillRouteNode,
                        pass1Node,
                        request,
                        readJsonNode(taskState.getWaitingContextJson())
                );
                passBNode = passBStage.passBNode();
                passBJson = passBStage.passBJson();
                cognitionVerdict = CognitionVerdictResolver.resolve(goalParseNode, passBNode, passBStage.assemblyResult());
                taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
            }

            String passBHardFailureCode = evaluatePassBHardFailureMetadata(passBNode.path("cognition_metadata"));
            if (passBHardFailureCode != null) {
                return failResumeForRequiredCognition(
                        taskState,
                        request,
                        currentVersion,
                        currentState,
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        candidateInventoryVersion,
                        "passb",
                        passBHardFailureCode,
                        passBNode
                );
            }

            if (isRealCaseRoute(skillRouteNode)) {
                String cognitionFailureCode = evaluateRequiredLlmMetadata(goalParseNode.path("cognition_metadata"));
                if (cognitionFailureCode != null) {
                    return failResumeForRequiredCognition(
                            taskState,
                            request,
                            currentVersion,
                            currentState,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            "goal-route",
                            cognitionFailureCode,
                            goalParseNode
                    );
                }
            }

            if (isRealCaseRoute(skillRouteNode)) {
                String cognitionFailureCode = evaluateRequiredLlmMetadata(passBNode.path("cognition_metadata"));
                if (cognitionFailureCode != null) {
                    return failResumeForRequiredCognition(
                            taskState,
                            request,
                            currentVersion,
                            currentState,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            "passb",
                            cognitionFailureCode,
                            passBNode
                    );
                }
                if (isClarifyRequiredCaseProjection(passBNode)) {
                    WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                            taskId,
                            resolveCaseProjectionWaitingReason(passBNode, "CLARIFY_CASE_SELECTION"),
                            resolveCaseProjectionPrompt(passBNode, "Choose a governed case before resuming."),
                            request.getUserNote(),
                            passBNode.path("case_projection")
                    );
                    return rollbackResumeToWaiting(
                            taskId,
                            taskState,
                            request,
                            currentState,
                            currentVersion,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            resumeCatalogScope,
                            waitingState,
                            "CLARIFY_CASE_SELECTION",
                            "LLM_AMBIGUOUS"
                    );
                }
            }

            if ("ambiguous".equalsIgnoreCase(passBNode.path("binding_status").asText(""))) {
                WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                        taskId,
                        "CLARIFY_BINDING",
                        "Clarify the requested bindings before resuming.",
                        request.getUserNote()
                );
                return rollbackResumeToWaiting(
                        taskId,
                        taskState,
                        request,
                        currentState,
                        currentVersion,
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        resumeCatalogScope,
                        waitingState,
                        "AMBIGUOUS_BINDING",
                        "AMBIGUOUS"
                );
            }

            CapabilityContractGuard.requireResumeAckContract(pass1Node);
            ValidationStageResult validationStage = runValidationStage(taskId, currentVersion, pass1Node, passBNode);
            taskStateMapper.updateInputChainSnapshot(
                    taskId,
                    passBJson,
                    writePayload(TaskProjectionBuilder.buildSlotBindingsSummaryPayload(validationStage.passBProjection())),
                    writePayload(TaskProjectionBuilder.buildArgsDraftSummaryPayload(validationStage.passBProjection())),
                    validationStage.validationSummaryJson(),
                    validationStage.inputChainStatus()
            );

            if (!Boolean.TRUE.equals(validationStage.validationResponse().getIsValid())) {
                if (TaskStatus.FAILED.equals(validationStage.nextState())) {
                    String failureSummaryJson = writePayload(
                            TaskControlPayloadBuilder.buildFatalValidationFailureSummaryPayload(
                                    validationStage.validationResponse(),
                                    OffsetDateTime.now(ZoneOffset.UTC).toString()
                            )
                    );

                    insertRepairRecord(
                            taskId,
                            resolveActiveAttemptNo(taskState),
                            null,
                            writeJson(request),
                    "FAILED",
                    repairProposalService.generate(
                            validationStage.repairDecision().waitingContext(),
                            RepairFactHelper.toValidationSummary(validationStage.validationResponse()),
                            null,
                            request.getUserNote()
                    ),
                    validationStage.repairDecision().severity(),
                    validationStage.repairDecision().routing()
            );

                    return rollbackResumeToFailed(
                            taskId,
                            taskState,
                            request,
                            currentState,
                            currentVersion,
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            resumeCatalogScope,
                            "FATAL_VALIDATION",
                            failureSummaryJson,
                            null,
                            currentVersion
                    );
                }

                WaitingStateSnapshot waitingState = rebuildWaitingState(taskId, pass1Node, validationStage.validationNode(), null, request.getUserNote());
                return rollbackResumeToWaiting(
                        taskId,
                        taskState,
                        request,
                        currentState,
                        currentVersion,
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        resumeCatalogScope,
                        waitingState,
                        "RECOVERABLE_VALIDATION",
                        null
                );
            }

            int attemptNo = resolveActiveAttemptNo(taskState);
            preparedSubmission = prepareAcceptedJobSubmission(
                    taskId,
                    taskState.getUserQuery(),
                    currentVersion,
                    attemptNo,
                    readJsonNode(taskState.getGoalParseJson()),
                    readJsonNode(taskState.getSkillRouteJson()),
                    pass1Node,
                    passBNode,
                    validationStage.validationNode()
            );
            String ackedTxn = writeJson(buildResumeTransactionPayload(
                    request.getResumeRequestId(),
                    "ACKED",
                    baseCheckpointVersion,
                    candidateCheckpointVersion,
                    resumeCatalogScope,
                    preparedSubmission.manifestCandidate().getManifestId(),
                    attemptNo,
                    preparedSubmission.createJobResponse().getJobId(),
                    null
            ));
            ensureUpdated(taskStateMapper.updateResumeTransaction(taskId, ackedTxn));
            String committedTxn = writeJson(buildResumeTransactionPayload(
                    request.getResumeRequestId(),
                    "COMMITTED",
                    baseCheckpointVersion,
                    candidateCheckpointVersion,
                    resumeCatalogScope,
                    preparedSubmission.manifestCandidate().getManifestId(),
                    attemptNo,
                    preparedSubmission.createJobResponse().getJobId(),
                    null
            ));
            TaskExecutionSubmissionSupport.freezeManifestOnCommit(
                    preparedSubmission.manifestCandidate(),
                    currentVersion,
                    analysisManifestMapper,
                    eventService,
                    objectMapper
            );
            ensureUpdated(taskStateMapper.commitQueuedWithGovernance(
                    taskId,
                    currentVersion,
                    TaskStatus.QUEUED.name(),
                    preparedSubmission.pass2Json(),
                    preparedSubmission.createJobResponse().getJobId(),
                    preparedSubmission.manifestCandidate().getManifestId(),
                    preparedSubmission.manifestCandidate().getManifestVersion(),
                    nextPlanningRevision(taskState),
                    candidateCheckpointVersion,
                    candidateInventoryVersion,
                    cognitionVerdict,
                    committedTxn
            ));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.QUEUED.name(), currentVersion + 1, null);
            taskAttemptMapper.updateSnapshotAndJob(
                    taskId,
                    attemptNo,
                    preparedSubmission.createJobResponse().getJobId(),
                    writePayload(TaskControlPayloadBuilder.buildQueuedAttemptSnapshotPayload(preparedSubmission.createJobResponse().getJobId())),
                    null
            );
            TaskState queuedTaskState = taskStateMapper.findByTaskId(taskId);
            sessionLifecycleService.syncFromTask(queuedTaskState == null ? taskState : queuedTaskState);
            sessionLifecycleService.recordProgressUpdate(
                    queuedTaskState == null ? taskState : queuedTaskState,
                    TaskStatus.QUEUED.name(),
                    "Queued for runtime execution",
                    "The resumed task was accepted and queued.",
                    "Runtime start"
            );

            return buildResumeTaskResponse(
                    taskId,
                    TaskStatus.QUEUED.name(),
                    currentVersion + 1,
                    true,
                    taskState.getActiveAttemptNo()
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Task resume pipeline failed for task {}", taskId, exception);
            try {
                String corruptedTxn = writeJson(buildResumeTransactionPayload(
                        request.getResumeRequestId(),
                        "CORRUPTED",
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        resumeCatalogScope,
                        preparedSubmission == null ? null : preparedSubmission.manifestCandidate().getManifestId(),
                        resolveActiveAttemptNo(taskState),
                        preparedSubmission == null ? null : preparedSubmission.createJobResponse().getJobId(),
                        safeString(exception.getMessage())
                ));
                ensureUpdated(taskStateMapper.markCorrupted(
                        taskId,
                        currentVersion,
                        TaskStatus.STATE_CORRUPTED.name(),
                        safeString(exception.getMessage()),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        corruptedTxn
                ));
                appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.STATE_CORRUPTED.name(), currentVersion + 1, null);
                TaskState corruptedTask = taskStateMapper.findByTaskId(taskId);
                sessionLifecycleService.recordFailureExplanation(corruptedTask == null ? taskState : corruptedTask);
            } catch (Exception ignored) {
            }
            throw new ResponseStatusException(BAD_GATEWAY, "Resume pipeline failed", exception);
        }
    }

    private boolean canReusePlanningForResume(TaskState taskState) {
        if (taskState == null) {
            return false;
        }
        JsonNode waitingContext = readJsonNode(taskState.getWaitingContextJson());
        String waitingReasonType = safeString(waitingContext.path("waiting_reason_type").asText(null));
        if (waitingReasonType.startsWith("CLARIFY")) {
            return false;
        }
        return taskState.getGoalParseJson() != null
                && !taskState.getGoalParseJson().isBlank()
                && taskState.getSkillRouteJson() != null
                && !taskState.getSkillRouteJson().isBlank()
                && taskState.getPass1ResultJson() != null
                && !taskState.getPass1ResultJson().isBlank()
                && taskState.getPassbResultJson() != null
                && !taskState.getPassbResultJson().isBlank();
    }

    private void ensureResumeContractIdentityCompatible(
            String taskId,
            TaskState taskState,
            ResumeTaskRequest request,
            int currentVersion,
            TaskStatus currentState,
            int baseCheckpointVersion,
            int candidateCheckpointVersion,
            TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope,
            JsonNode frozenPass1Node,
            JsonNode currentPass1Node
    ) throws Exception {
        TaskResumeGovernanceSupport.ResumeContractDrift contractDrift =
                TaskResumeGovernanceSupport.buildResumeContractDrift(frozenPass1Node, currentPass1Node);
        if (!contractDrift.isPresent()) {
            return;
        }
        String corruptedTxn = writeJson(buildResumeTransactionPayload(
                request.getResumeRequestId(),
                "CORRUPTED",
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope,
                null,
                resolveActiveAttemptNo(taskState),
                null,
                contractDrift.failureReason(),
                contractDrift
        ));
        ensureUpdated(taskStateMapper.markCorrupted(
                taskId,
                currentVersion,
                TaskStatus.STATE_CORRUPTED.name(),
                contractDrift.failureReason(),
                OffsetDateTime.now(ZoneOffset.UTC),
                corruptedTxn
        ));
        appendAuditJson(
                taskId,
                "TASK_RESUME",
                "REJECTED",
                request.getResumeRequestId(),
                writePayload(contractDrift.toAuditDetail())
        );
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.STATE_CORRUPTED.name(), currentVersion + 1, null);
        throw new ResponseStatusException(CONFLICT, contractDrift.conflictReason());
    }

    private boolean canReuseGoalAndPass1ForClarifyResume(TaskState taskState, ResumeTaskRequest request) {
        if (taskState == null || request == null) {
            return false;
        }
        JsonNode waitingContext = readJsonNode(taskState.getWaitingContextJson());
        String waitingReasonType = safeString(waitingContext.path("waiting_reason_type").asText(null));
        if (!waitingReasonType.startsWith("CLARIFY")) {
            return false;
        }
        Object selectedCaseValue = request.getArgsOverrides() == null ? null : request.getArgsOverrides().get("case_id");
        String selectedCaseId = selectedCaseValue == null ? "" : safeString(String.valueOf(selectedCaseValue));
        if (selectedCaseId.isBlank()) {
            return false;
        }
        return taskState.getGoalParseJson() != null
                && !taskState.getGoalParseJson().isBlank()
                && taskState.getSkillRouteJson() != null
                && !taskState.getSkillRouteJson().isBlank()
                && taskState.getPass1ResultJson() != null
                && !taskState.getPass1ResultJson().isBlank();
    }

    private ObjectNode requireObjectNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        throw new IllegalStateException("Expected object node for resume planning reuse");
    }

    private ObjectNode buildAcceptedOverrides(ResumeTaskRequest request) {
        ObjectNode overrides = objectMapper.createObjectNode();
        if (request == null) {
            return overrides;
        }
        if (request.getArgsOverrides() != null) {
            request.getArgsOverrides().forEach((key, value) -> overrides.set(key, objectMapper.valueToTree(value)));
        }
        if (request.getSlotOverrides() != null && !request.getSlotOverrides().isEmpty()) {
            overrides.set("slot_overrides", objectMapper.valueToTree(request.getSlotOverrides()));
        }
        return overrides;
    }

    private void applyResumeInputs(JsonNode pass1Result, ObjectNode passBNode, ResumeTaskRequest request, String taskId) {
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
        refreshDerivedAnalysisArgs(pass1Result, argsDraft, slotBindings);
        if (request.getArgsOverrides() != null) {
            for (Map.Entry<String, Object> entry : request.getArgsOverrides().entrySet()) {
                argsDraft.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }
        }
    }

    private void refreshDerivedAnalysisArgs(JsonNode pass1Result, ObjectNode argsDraft, ArrayNode slotBindings) {
        Set<String> boundRoles = new HashSet<>();
        for (JsonNode node : slotBindings) {
            String roleName = node.path("role_name").asText("");
            String slotName = node.path("slot_name").asText("");
            if (roleName.isBlank() || slotName.isBlank()) {
                continue;
            }
            boundRoles.add(roleName);
            Pass1FactHelper.RoleArgMapping mapping = Pass1FactHelper.resolveRoleArgMapping(pass1Result, roleName);
            if (mapping != null) {
                if (!mapping.slotArgKey().isBlank()) {
                    argsDraft.put(mapping.slotArgKey(), slotName);
                }
                if (!mapping.valueArgKey().isBlank()
                        && mapping.defaultValue() != null
                        && !mapping.defaultValue().isNull()
                        && !mapping.defaultValue().isMissingNode()) {
                    argsDraft.set(mapping.valueArgKey(), mapping.defaultValue().deepCopy());
                }
            }
        }
        if (!argsDraft.has("analysis_template")) {
            String analysisTemplate = Pass1FactHelper.resolveAnalysisTemplate(pass1Result);
            if (!analysisTemplate.isBlank()) {
                argsDraft.put("analysis_template", analysisTemplate);
            }
        }
        if (!boundRoles.contains("depth_to_root_restricting_layer") && !argsDraft.has("root_depth_factor")) {
            JsonNode stableDefault = Pass1FactHelper.resolveStableDefault(pass1Result, "root_depth_factor");
            if (stableDefault != null) {
                argsDraft.set("root_depth_factor", stableDefault.deepCopy());
            }
        }
        if (!boundRoles.contains("plant_available_water_content") && !argsDraft.has("pawc_factor")) {
            JsonNode stableDefault = Pass1FactHelper.resolveStableDefault(pass1Result, "pawc_factor");
            if (stableDefault != null) {
                argsDraft.set("pawc_factor", stableDefault.deepCopy());
            }
        }
    }

    private JsonNode refreshWaitingContext(String taskId) {
        TaskState latest = taskStateMapper.findByTaskId(taskId);
        WaitingStateSnapshot waitingState = rebuildWaitingState(latest, null);
        try {
            insertRepairRecord(
                    taskId,
                    resolveActiveAttemptNo(latest),
                    null,
                    null,
                    "REJECTED",
                    waitingState.repairProposal(),
                    waitingState.decision().severity(),
                    waitingState.decision().routing()
            );
        } catch (Exception ignored) {
        }
        return waitingState.waitingContextNode();
    }

    private WaitingStateSnapshot rebuildWaitingState(TaskState taskState, String userNote) {
        JsonNode existingWaitingContext = readJsonNode(taskState.getWaitingContextJson());
        if (requiresClarify(existingWaitingContext) && (userNote == null || userNote.isBlank())) {
            String waitingReasonType = existingWaitingContext.path("waiting_reason_type").asText("CLARIFY_REQUIRED");
            String actionLabel = "Clarify the request before resuming.";
            JsonNode actions = existingWaitingContext.path("required_user_actions");
            if (actions.isArray() && !actions.isEmpty()) {
                String existingLabel = actions.get(0).path("label").asText("");
                if (!existingLabel.isBlank()) {
                    actionLabel = existingLabel;
                }
            }
            return buildClarifyWaitingState(taskState.getTaskId(), waitingReasonType, actionLabel, userNote);
        }
        return rebuildWaitingState(
                taskState.getTaskId(),
                readJsonNode(taskState.getPass1ResultJson()),
                readJsonNode(taskState.getValidationSummaryJson()),
                readJsonNode(taskState.getLastFailureSummaryJson()),
                userNote
        );
    }

    private WaitingStateSnapshot rebuildWaitingState(
            String taskId,
            JsonNode pass1Result,
            JsonNode validationSummary,
            JsonNode failureSummary,
            String userNote
    ) {
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        RepairDecision decision = assertionFailureMapper.map(failureSummary, pass1Result, attachments);
        if (decision == null || isFatalRepairDecision(decision)) {
            decision = repairDispatcherService.decide(
                    readValidationSummary(validationSummary),
                    pass1Result,
                    attachments,
                    currentInventoryVersion(latestTaskState)
            );
        }
        return buildWaitingStateSnapshot(taskId, decision, validationSummary, failureSummary, userNote, true);
    }

    private WaitingStateSnapshot buildWaitingStateSnapshot(
            String taskId,
            RepairDecision decision,
            JsonNode validationSummary,
            JsonNode failureSummary,
            String userNote,
            boolean persistWaitingContext
    ) {
        JsonNode waitingContextNode = RepairFactHelper.toJsonNode(objectMapper, decision.waitingContext());
        String waitingContextJson = writeJson(decision.waitingContext());
        if (persistWaitingContext) {
            taskStateMapper.updateWaitingContext(
                    taskId,
                    waitingContextJson,
                    safeString(decision.waitingContext().getWaitingReasonType()).isBlank()
                            ? "REPAIR_REQUIRED"
                            : decision.waitingContext().getWaitingReasonType()
            );
        }
        RepairProposalResponse repairProposal = repairProposalService.generate(
                decision.waitingContext(),
                RepairFactHelper.readValidationSummary(objectMapper, validationSummary),
                RepairFactHelper.readFailureSummary(objectMapper, failureSummary),
                userNote
        );
        return new WaitingStateSnapshot(decision, waitingContextNode, waitingContextJson, repairProposal);
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

    private PrimitiveValidationResponse readValidationSummary(JsonNode node) {
        return RepairFactHelper.readPrimitiveValidation(objectMapper, node);
    }

    private boolean requiresClarify(JsonNode waitingContextNode) {
        JsonNode actions = waitingContextNode.path("required_user_actions");
        if (!actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            if ("clarify".equalsIgnoreCase(action.path("action_type").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean isClarifyRequiredCaseProjection(JsonNode stageNode) {
        return "clarify_required".equalsIgnoreCase(stageNode.path("case_projection").path("mode").asText(""));
    }

    private String resolveCaseProjectionWaitingReason(JsonNode stageNode, String fallbackReasonType) {
        if (!isClarifyRequiredCaseProjection(stageNode)) {
            return fallbackReasonType;
        }
        return "CLARIFY_CASE_SELECTION";
    }

    private String resolveCaseProjectionPrompt(JsonNode stageNode, String fallbackLabel) {
        if (!isClarifyRequiredCaseProjection(stageNode)) {
            return fallbackLabel;
        }
        String prompt = stageNode.path("case_projection").path("clarify_prompt").asText("");
        return prompt.isBlank() ? fallbackLabel : prompt;
    }

    private WaitingStateSnapshot buildClarifyWaitingState(
            String taskId,
            String waitingReasonType,
            String actionLabel,
            String userNote
    ) {
        return buildClarifyWaitingState(taskId, waitingReasonType, actionLabel, userNote, null);
    }

    private WaitingStateSnapshot buildClarifyWaitingState(
            String taskId,
            String waitingReasonType,
            String actionLabel,
            String userNote,
            JsonNode caseProjectionNode
    ) {
        boolean caseSelectionClarify = "CLARIFY_CASE_SELECTION".equalsIgnoreCase(waitingReasonType)
                || (caseProjectionNode != null && caseProjectionNode.path("mode").asText("").equalsIgnoreCase("clarify_required"));
        com.sage.backend.repair.dto.RepairProposalRequest.WaitingContext waitingContext =
                new com.sage.backend.repair.dto.RepairProposalRequest.WaitingContext();
        waitingContext.setWaitingReasonType(waitingReasonType);
        waitingContext.setMissingSlots(List.of());
        waitingContext.setInvalidBindings(List.of());

        com.sage.backend.repair.dto.RepairProposalRequest.RequiredUserAction clarifyAction =
                new com.sage.backend.repair.dto.RepairProposalRequest.RequiredUserAction();
        clarifyAction.setActionType("clarify");
        clarifyAction.setKey(caseSelectionClarify ? "clarify_case_selection" : "clarify_" + safeString(waitingReasonType).toLowerCase());
        clarifyAction.setLabel(actionLabel);
        clarifyAction.setRequired(true);
        waitingContext.setRequiredUserActions(List.of(clarifyAction));
        waitingContext.setResumeHint(caseSelectionClarify
                ? "Select a governed case and then resume."
                : "Provide clarification in user_note before resuming.");
        waitingContext.setCanResume(false);
        waitingContext.setCatalogSummary(TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                taskId,
                taskStateMapper.findByTaskId(taskId),
                taskAttachmentMapper,
                taskCatalogSnapshotService
        ).summary());

        RepairDecision decision = new RepairDecision("RECOVERABLE", "WAITING_USER", waitingContext);
        return buildWaitingStateSnapshot(taskId, decision, objectMapper.createObjectNode(), objectMapper.createObjectNode(), userNote, false);
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

    private CognitionGoalRouteResponse runGoalRoute(String taskId, String userQuery, int stateVersion, String userNote) {
        CognitionGoalRouteRequest request = new CognitionGoalRouteRequest();
        request.setTaskId(taskId);
        request.setUserQuery(userQuery);
        request.setStateVersion(stateVersion);
        request.setUserNote(safeString(userNote));
        request.setAllowedCapabilities(List.of("water_yield"));
        request.setAllowedTemplates(List.of("water_yield_v1"));
        request.setKnownCases(List.of());
        return cognitionGoalRouteClient.route(request);
    }

    private ObjectNode buildGoalParseNode(CognitionGoalRouteResponse response, String userQuery) {
        ObjectNode node = response.getGoalParse() instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : objectMapper.createObjectNode();
        node.put("user_query", safeString(userQuery));
        node.put("planning_intent_status", safeString(response.getPlanningIntentStatus()));
        if (response.getConfidence() != null) {
            node.put("confidence", response.getConfidence());
        }
        if (response.getDecisionSummary() != null && !response.getDecisionSummary().isEmpty()) {
            node.set("decision_summary", objectMapper.valueToTree(response.getDecisionSummary()));
        }
        if (response.getCaseProjection() != null && !response.getCaseProjection().isEmpty()) {
            node.set("case_projection", objectMapper.valueToTree(response.getCaseProjection()));
        }
        if (response.getCognitionMetadata() != null && !response.getCognitionMetadata().isEmpty()) {
            node.set("cognition_metadata", objectMapper.valueToTree(response.getCognitionMetadata()));
        }
        return node;
    }

    private ObjectNode buildSkillRouteNode(CognitionGoalRouteResponse response) {
        ObjectNode node = response.getSkillRoute() instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : objectMapper.createObjectNode();
        node.put("planning_intent_status", safeString(response.getPlanningIntentStatus()));
        if (response.getConfidence() != null) {
            node.put("confidence", response.getConfidence());
        }
        if (response.getDecisionSummary() != null && !response.getDecisionSummary().isEmpty()) {
            node.set("decision_summary", objectMapper.valueToTree(response.getDecisionSummary()));
        }
        if (response.getCaseProjection() != null && !response.getCaseProjection().isEmpty()) {
            node.set("case_projection", objectMapper.valueToTree(response.getCaseProjection()));
        }
        if (response.getCognitionMetadata() != null && !response.getCognitionMetadata().isEmpty()) {
            node.set("cognition_metadata", objectMapper.valueToTree(response.getCognitionMetadata()));
        }
        return node;
    }

    private Map<String, Object> goalRouteNodeSummary(JsonNode goalParseNode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("planning_intent_status", goalParseNode.path("planning_intent_status").asText(null));
        summary.put("goal_type", goalParseNode.path("goal_type").asText(null));
        summary.put("analysis_kind", goalParseNode.path("analysis_kind").asText(null));
        summary.put("source", goalParseNode.path("source").asText(null));
        return summary;
    }

    private Map<String, Object> buildUnsupportedFailureSummaryPayload(Map<String, Object> routeSummary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failure_code", "UNSUPPORTED_ANALYSIS");
        payload.put("failure_message", "The request is not supported by the current cognition-enabled capability set.");
        payload.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("details", routeSummary);
        return payload;
    }

    private boolean isRealCaseRoute(JsonNode skillRouteNode) {
        if (skillRouteNode == null || skillRouteNode.isNull() || skillRouteNode.isMissingNode()) {
            return false;
        }
        return "real_case_validation".equalsIgnoreCase(skillRouteNode.path("execution_mode").asText(""));
    }

    private String evaluateRequiredLlmMetadata(JsonNode metadataNode) {
        if (metadataNode == null || metadataNode.isNull() || metadataNode.isMissingNode()) {
            return "COGNITION_UNAVAILABLE";
        }
        String provider = metadataNode.path("provider").asText("");
        boolean fallbackUsed = metadataNode.path("fallback_used").asBoolean(false);
        boolean schemaValid = !metadataNode.path("schema_valid").isBoolean() || metadataNode.path("schema_valid").asBoolean(true);
        String status = metadataNode.path("status").asText("");
        String failureCode = metadataNode.path("failure_code").asText("");
        boolean acceptableFallback = "glm".equalsIgnoreCase(provider)
                && fallbackUsed
                && schemaValid
                && "LLM_FALLBACK".equalsIgnoreCase(status);
        if (!"glm".equalsIgnoreCase(provider)) {
            return "COGNITION_POLICY_VIOLATION";
        }
        if (fallbackUsed && !acceptableFallback) {
            return "COGNITION_POLICY_VIOLATION";
        }
        if (!failureCode.isBlank() && !acceptableFallback) {
            return failureCode;
        }
        if (!schemaValid) {
            return "COGNITION_SCHEMA_INVALID";
        }
        if (acceptableFallback) {
            return null;
        }
        if ("COGNITION_TIMEOUT".equalsIgnoreCase(status)) {
            return "COGNITION_TIMEOUT";
        }
        if ("COGNITION_SCHEMA_INVALID".equalsIgnoreCase(status)) {
            return "COGNITION_SCHEMA_INVALID";
        }
        if ("COGNITION_POLICY_VIOLATION".equalsIgnoreCase(status)) {
            return "COGNITION_POLICY_VIOLATION";
        }
        if ("COGNITION_UNAVAILABLE".equalsIgnoreCase(status) || "EXPLANATION_UNAVAILABLE".equalsIgnoreCase(status)) {
            return "COGNITION_UNAVAILABLE";
        }
        return null;
    }

    private String evaluatePassBHardFailureMetadata(JsonNode metadataNode) {
        if (metadataNode == null || metadataNode.isNull() || metadataNode.isMissingNode()) {
            return null;
        }
        String failureCode = metadataNode.path("failure_code").asText("");
        String status = metadataNode.path("status").asText("");
        for (String value : List.of(failureCode, status)) {
            if ("SKILL_ASSET_UNAVAILABLE".equalsIgnoreCase(value)
                    || "SKILL_ASSET_BINDING_FAILED".equalsIgnoreCase(value)
                    || "PARAMETER_SCHEMA_UNAVAILABLE".equalsIgnoreCase(value)
                    || "PARAMETER_SCHEMA_INVALID".equalsIgnoreCase(value)
                    || "PARAMETER_SCHEMA_BINDING_FAILED".equalsIgnoreCase(value)) {
                return value.toUpperCase();
            }
        }
        return null;
    }

    private CreateTaskResponse failCreateForRequiredCognition(
            String taskId,
            int currentVersion,
            TaskStatus currentState,
            String traceId,
            String stage,
            String failureCode,
            JsonNode payloadNode
    ) throws Exception {
        String failureSummaryJson = writePayload(buildCognitionFailureSummaryPayload(stage, failureCode, payloadNode));
        transitionCreateToFailed(
                taskId,
                currentState,
                failureSummaryJson,
                mapCognitionFailureVerdict(failureCode),
                currentVersion,
                currentVersion + 1,
                currentVersion + 1
        );
        appendTaskCreateAudit(
                taskId,
                "FAILED",
                traceId,
                TaskStatus.FAILED.name(),
                false,
                InputChainStatus.INCOMPLETE.name(),
                false,
                null,
                failureCode
        );
        return buildCreateTaskResponse(taskId, null, TaskStatus.FAILED.name(), currentVersion + 1);
    }

    private CreateTaskResponse transitionCreateToWaiting(
            String taskId,
            TaskState taskState,
            int currentVersion,
            TaskStatus currentState,
            WaitingStateSnapshot waitingState,
            String cognitionVerdict,
            int responseVersion
    ) {
        if (cognitionVerdict != null && !cognitionVerdict.isBlank()) {
            taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
        }
        ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                taskId,
                currentVersion,
                TaskStatus.WAITING_USER.name(),
                waitingState.waitingContextJson(),
                waitingState.decision().waitingContext().getWaitingReasonType(),
                OffsetDateTime.now(ZoneOffset.UTC)
        ));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), responseVersion, null);
        recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, null, responseVersion);
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        sessionLifecycleService.recordWaiting(latestTaskState == null ? taskState : latestTaskState);
        return buildCreateTaskResponse(taskId, null, TaskStatus.WAITING_USER.name(), responseVersion);
    }

    private CreateTaskResponse transitionCreateToFailed(
            String taskId,
            TaskStatus currentState,
            String failureSummaryJson,
            String cognitionVerdict,
            Integer stateUpdateVersion,
            Integer stateChangedEventVersion,
            int responseVersion
    ) {
        taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
        if (cognitionVerdict != null && !cognitionVerdict.isBlank()) {
            taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
        }
        if (stateUpdateVersion != null) {
            ensureUpdated(taskStateMapper.updateState(taskId, stateUpdateVersion, TaskStatus.FAILED.name()));
        }
        if (stateChangedEventVersion != null) {
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), stateChangedEventVersion, null);
        }
        appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, responseVersion, failureSummaryJson);
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        sessionLifecycleService.recordFailureExplanation(latestTaskState);
        return buildCreateTaskResponse(taskId, null, TaskStatus.FAILED.name(), responseVersion);
    }

    private ResumeTaskResponse failResumeForRequiredCognition(
            TaskState taskState,
            ResumeTaskRequest request,
            int currentVersion,
            TaskStatus currentState,
            int baseCheckpointVersion,
            int candidateCheckpointVersion,
            int candidateInventoryVersion,
            String stage,
            String failureCode,
            JsonNode payloadNode
    ) throws Exception {
        String taskId = taskState.getTaskId();
        TaskGovernanceFactSupport.CatalogFacts currentCatalogFacts = TaskGovernanceFactSupport.resolveCurrentCatalogFacts(
                taskId,
                taskState,
                taskAttachmentMapper,
                taskCatalogSnapshotService
        );
        TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope =
                TaskResumeGovernanceSupport.buildResumeCatalogScope(currentCatalogFacts, candidateInventoryVersion);
        String failureSummaryJson = writePayload(buildCognitionFailureSummaryPayload(stage, failureCode, payloadNode));
        return rollbackResumeToFailed(
                taskId,
                taskState,
                request,
                currentState,
                currentVersion,
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope,
                failureCode,
                failureSummaryJson,
                mapCognitionFailureVerdict(failureCode),
                currentVersion + 1
        );
    }

    private ResumeTaskResponse rollbackResumeToWaiting(
            String taskId,
            TaskState taskState,
            ResumeTaskRequest request,
            TaskStatus currentState,
            int currentVersion,
            int baseCheckpointVersion,
            int candidateCheckpointVersion,
            TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope,
            WaitingStateSnapshot waitingState,
            String failureReason,
            String cognitionVerdict
    ) {
        String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                request.getResumeRequestId(),
                "ROLLED_BACK",
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope,
                null,
                resolveActiveAttemptNo(taskState),
                null,
                failureReason
        ));
        if (cognitionVerdict != null && !cognitionVerdict.isBlank()) {
            taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
        }
        ensureUpdated(taskStateMapper.rollbackResumeToWaiting(
                taskId,
                currentVersion,
                TaskStatus.WAITING_USER.name(),
                waitingState.waitingContextJson(),
                safeString(waitingState.decision().waitingContext().getWaitingReasonType()).isBlank()
                        ? "REPAIR_REQUIRED"
                        : waitingState.decision().waitingContext().getWaitingReasonType(),
                OffsetDateTime.now(ZoneOffset.UTC),
                rolledBackTxn
        ));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
        recordWaitingUserEntry(
                taskId,
                resolveActiveAttemptNo(taskState),
                waitingState,
                writeJson(request),
                currentVersion + 1
        );
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        sessionLifecycleService.recordWaiting(latestTaskState == null ? taskState : latestTaskState);
        return buildResumeTaskResponse(
                taskId,
                TaskStatus.WAITING_USER.name(),
                currentVersion + 1,
                true,
                taskState.getActiveAttemptNo()
        );
    }

    private ResumeTaskResponse rollbackResumeToFailed(
            String taskId,
            TaskState taskState,
            ResumeTaskRequest request,
            TaskStatus currentState,
            int currentVersion,
            int baseCheckpointVersion,
            int candidateCheckpointVersion,
            TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope,
            String failureReason,
            String failureSummaryJson,
            String cognitionVerdict,
            int taskFailedEventVersion
    ) {
        String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                request.getResumeRequestId(),
                "ROLLED_BACK",
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope,
                null,
                resolveActiveAttemptNo(taskState),
                null,
                failureReason
        ));
        taskStateMapper.updateResumeTransaction(taskId, rolledBackTxn);
        if (cognitionVerdict != null && !cognitionVerdict.isBlank()) {
            taskStateMapper.updateCognitionVerdict(taskId, cognitionVerdict);
        }
        taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
        ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
        appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, taskFailedEventVersion, failureSummaryJson);
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        sessionLifecycleService.recordFailureExplanation(latestTaskState == null ? taskState : latestTaskState);
        return buildResumeTaskResponse(
                taskId,
                TaskStatus.FAILED.name(),
                currentVersion + 1,
                true,
                taskState.getActiveAttemptNo()
        );
    }

    private Map<String, Object> buildCognitionFailureSummaryPayload(String stage, String failureCode, JsonNode payloadNode) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", stage);
        details.put("cognition_payload", TaskProjectionBuilder.jsonNodeToObject(payloadNode, objectMapper));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("failure_code", failureCode);
        payload.put("failure_message", describeCognitionFailure(failureCode, stage));
        payload.put("created_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("details", details);
        return payload;
    }

    private String describeCognitionFailure(String failureCode, String stage) {
        return switch (failureCode) {
            case "COGNITION_TIMEOUT" -> "Required LLM cognition timed out during " + stage + ".";
            case "COGNITION_SCHEMA_INVALID" -> "Required LLM cognition returned invalid schema during " + stage + ".";
            case "COGNITION_POLICY_VIOLATION" -> "Required LLM cognition policy was violated during " + stage + ".";
            case "SKILL_ASSET_UNAVAILABLE" -> "Skill asset loading failed during " + stage + ".";
            case "SKILL_ASSET_BINDING_FAILED" -> "Skill asset binding failed during " + stage + ".";
            case "PARAMETER_SCHEMA_UNAVAILABLE" -> "Parameter schema is unavailable during " + stage + ".";
            case "PARAMETER_SCHEMA_INVALID" -> "Parameter schema is invalid during " + stage + ".";
            case "PARAMETER_SCHEMA_BINDING_FAILED" -> "Parameter schema binding failed during " + stage + ".";
            default -> "Required LLM cognition was unavailable during " + stage + ".";
        };
    }

    private String mapCognitionFailureVerdict(String failureCode) {
        if ("SKILL_ASSET_UNAVAILABLE".equalsIgnoreCase(failureCode)
                || "SKILL_ASSET_BINDING_FAILED".equalsIgnoreCase(failureCode)
                || "PARAMETER_SCHEMA_UNAVAILABLE".equalsIgnoreCase(failureCode)
                || "PARAMETER_SCHEMA_INVALID".equalsIgnoreCase(failureCode)
                || "PARAMETER_SCHEMA_BINDING_FAILED".equalsIgnoreCase(failureCode)) {
            return "SKILL_ASSET_FAILED";
        }
        if ("COGNITION_POLICY_VIOLATION".equalsIgnoreCase(failureCode)) {
            return "LLM_POLICY_VIOLATION";
        }
        return "LLM_UNAVAILABLE";
    }

    private JsonNode buildFinalExplanationNode(TaskState taskState, JobRecord jobRecord, JobStatusResponse status) {
        boolean realCase = TaskSuccessProjectionSupport.isRealCaseRuntime(jobRecord, status);
        if (!realCase) {
            return status.getFinalExplanation();
        }
        try {
            CognitionFinalExplanationResponse response = runFinalExplanation(taskState, jobRecord, status);
            JsonNode node = objectMapper.valueToTree(response);
            String failureCode = evaluateRequiredLlmMetadata(node.path("cognition_metadata"));
            if (failureCode != null) {
                return TaskSuccessProjectionSupport.buildUnavailableFinalExplanationNode(
                        failureCode,
                        TaskSuccessProjectionSupport.describeExplanationFailure(failureCode),
                        objectMapper
                );
            }
            return node;
        } catch (Exception exception) {
            String failureCode = TaskSuccessProjectionSupport.classifyCognitionException(exception);
            return TaskSuccessProjectionSupport.buildUnavailableFinalExplanationNode(
                    failureCode,
                    exception.getMessage(),
                    objectMapper
            );
        }
    }

    private CognitionFinalExplanationResponse runFinalExplanation(TaskState taskState, JobRecord jobRecord, JobStatusResponse status) {
        CognitionFinalExplanationRequest request = new CognitionFinalExplanationRequest();
        request.setTaskId(taskState.getTaskId());
        request.setUserQuery(safeString(taskState.getUserQuery()));
        request.setCaseId(resolveCaseIdForFinalExplanation(taskState, status));
        request.setProviderKey(jobRecord == null ? null : jobRecord.getProviderKey());
        request.setRuntimeProfile(jobRecord == null ? null : jobRecord.getRuntimeProfile());
        request.setResultBundle(status.getResultBundle());
        request.setArtifactCatalog(status.getArtifactCatalog());
        request.setDockerRuntimeEvidence(status.getDockerRuntimeEvidence());
        request.setWorkspaceSummary(status.getWorkspaceSummary());
        return cognitionFinalExplanationClient.generate(request);
    }

    private String resolveCaseIdForFinalExplanation(TaskState taskState, JobStatusResponse status) {
        return TaskSuccessProjectionSupport.resolveCaseId(resolveActiveManifest(taskState), status, objectMapper);
    }

    private Pass1Response runPass1(
            String taskId,
            String userQuery,
            int stateVersion,
            String capabilityKey,
            String selectedTemplate
    ) {
        Pass1Request req = new Pass1Request();
        req.setTaskId(taskId);
        req.setUserQuery(userQuery);
        req.setStateVersion(stateVersion);
        req.setCapabilityKey(capabilityKey);
        req.setSelectedTemplate(selectedTemplate);
        return pass1Client.runPass1(req);
    }

    private CognitionPassBResponse runPassB(
            String taskId,
            String userQuery,
            int stateVersion,
            JsonNode pass1Result,
            JsonNode goalParse,
            JsonNode skillRoute,
            String userNote,
            ResumeTaskRequest resumeRequest,
            JsonNode resumeContext
    ) {
        CognitionPassBRequest req = new CognitionPassBRequest();
        req.setTaskId(taskId);
        req.setUserQuery(userQuery);
        req.setStateVersion(stateVersion);
        req.setPass1Result(pass1Result);
        req.setGoalParse(goalParse);
        req.setSkillRoute(skillRoute);
        req.setUserNote(safeString(userNote));
        req.setAttachmentFacts(objectMapper.createArrayNode());
        req.setAcceptedOverrides(objectMapper.createObjectNode());
        req.setResumeContext(objectMapper.createObjectNode());
        if (resumeRequest != null) {
            req.setAcceptedOverrides(buildAcceptedOverrides(resumeRequest));
        }
        if (resumeContext != null && !resumeContext.isMissingNode() && !resumeContext.isNull()) {
            req.setResumeContext(resumeContext);
        }
        return cognitionPassBClient.runPassB(req);
    }

    private PassBStageResult runPassBStage(
            String taskId,
            String userQuery,
            int stateVersion,
            JsonNode goalParseNode,
            JsonNode skillRouteNode,
            JsonNode pass1Node,
            ResumeTaskRequest resumeRequest,
            JsonNode resumeContext
    ) throws Exception {
        appendEvent(taskId, EventType.COGNITION_PASSB_STARTED.name(), null, null, stateVersion, null);
        CognitionPassBResponse passBResponse = runPassB(
                taskId,
                userQuery,
                stateVersion,
                pass1Node,
                goalParseNode,
                skillRouteNode,
                resumeRequest == null ? null : resumeRequest.getUserNote(),
                resumeRequest,
                resumeContext
        );
        String passBJson = objectMapper.writeValueAsString(passBResponse);
        ObjectNode passBNode = (ObjectNode) objectMapper.readTree(passBJson);
        boolean resume = resumeRequest != null;
        if (resume) {
            applyResumeInputs(pass1Node, passBNode, resumeRequest, taskId);
        }
        ExecutionContractAssembler.AssemblyResult assemblyResult = executionContractAssembler.assemble(taskId, skillRouteNode, pass1Node, passBNode);
        passBNode.putPOJO("blocked_mutations", assemblyResult.blockedMutations());
        passBNode.putPOJO("overruled_fields", assemblyResult.overruledFields());
        passBNode.put("assembly_blocked", assemblyResult.assemblyBlocked());
        passBJson = writeJson(passBNode);
        appendEvent(
                taskId,
                EventType.COGNITION_PASSB_COMPLETED.name(),
                null,
                null,
                stateVersion,
                writePayload(TaskControlPayloadBuilder.buildPassBCompletionPayload(passBNode, resume))
        );
        return new PassBStageResult(passBNode, passBJson, assemblyResult);
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
        req.setMetadataCatalogFacts(objectMapper.valueToTree(
                AttachmentCatalogProjector.project(taskAttachmentMapper.findByTaskId(taskId))
        ));
        return pass2Client.runPass2(req);
    }

    private CreateJobResponse submitJob(
            String taskId,
            JsonNode executionGraph,
            JsonNode runtimeAssertions,
            JsonNode slotBindings,
            JsonNode argsDraft,
            String caseId,
            String workspaceId,
            int attemptNo,
            RegistryService.ProviderResolution providerResolution
    ) {
        CreateJobRequest req = new CreateJobRequest();
        req.setTaskId(taskId);
        req.setMaterializedExecutionGraph(executionGraph);
        req.setRuntimeAssertions(runtimeAssertions);
        req.setSlotBindings(slotBindings);
        req.setArgsDraft(argsDraft);
        req.setCaseId(caseId);
        req.setWorkspaceId(workspaceId);
        req.setAttemptNo(attemptNo);
        req.setCapabilityKey(providerResolution.capabilityKey());
        req.setProviderKey(providerResolution.providerKey());
        req.setRuntimeProfile(providerResolution.runtimeProfile());
        return jobRuntimeClient.createJob(req);
    }

    private PreparedJobSubmission prepareAcceptedJobSubmission(
            String taskId,
            String userQuery,
            int stateVersion,
            int attemptNo,
            JsonNode goalParseNode,
            JsonNode skillRouteNode,
            JsonNode pass1Node,
            JsonNode passBNode,
            JsonNode validationNode
    ) {
        appendEvent(taskId, EventType.PLANNING_PASS2_STARTED.name(), null, null, stateVersion, null);
        Pass2Response pass2Response = runPass2(taskId, stateVersion, pass1Node, passBNode, validationNode);
        String pass2Json = writeJson(pass2Response);
        appendEvent(
                taskId,
                EventType.PLANNING_PASS2_COMPLETED.name(),
                null,
                null,
                stateVersion,
                writeJson(TaskControlPayloadBuilder.buildPass2CompletedPayload(pass2Response.getMaterializedExecutionGraph()))
        );

        AnalysisManifest manifestCandidate = buildManifestCandidate(
                taskId,
                userQuery,
                attemptNo,
                pass1Node,
                passBNode,
                validationNode,
                pass2Response,
                pass2Response.getMaterializedExecutionGraph(),
                pass2Response.getRuntimeAssertions()
        );

        RegistryService.ProviderResolution providerResolution = registryService.resolve(
                goalParseNode,
                skillRouteNode,
                pass1Node
        );
        CapabilityContractGuard.requireSubmitJobContract(pass1Node);
        String workspaceId = generateWorkspaceId();
        CreateJobResponse createJobResponse = submitJob(
                taskId,
                pass2Response.getMaterializedExecutionGraph(),
                pass2Response.getRuntimeAssertions(),
                passBNode.path("slot_bindings"),
                passBNode.path("args_draft"),
                safeString(passBNode.path("args_draft").path("case_id").asText(null)),
                workspaceId,
                attemptNo,
                providerResolution
        );
        TaskExecutionSubmissionSupport.persistAcceptedJobAttempt(
                taskId,
                attemptNo,
                createJobResponse,
                pass2Response,
                workspaceId,
                providerResolution,
                jobRecordMapper,
                workspaceTraceService,
                objectMapper
        );
        TaskExecutionSubmissionSupport.appendAcceptedJobEvents(
                taskId,
                stateVersion,
                createJobResponse.getJobId(),
                eventService,
                objectMapper
        );
        return new PreparedJobSubmission(pass2Json, pass2Response, createJobResponse, manifestCandidate);
    }

    private void recordWaitingUserEntry(
            String taskId,
            int attemptNo,
            WaitingStateSnapshot waitingState,
            String resumePayloadJson,
            int stateVersion
    ) {
        insertRepairRecord(
                taskId,
                attemptNo,
                null,
                resumePayloadJson,
                "REJECTED",
                waitingState.repairProposal(),
                waitingState.decision().severity(),
                waitingState.decision().routing()
        );
        appendEvent(taskId, EventType.WAITING_USER_ENTERED.name(), null, null, stateVersion, waitingState.waitingContextJson());
    }

    private void insertRepairRecord(
            String taskId,
            int attemptNo,
            String resumeRequestId,
            String resumePayloadJson,
            String result,
            RepairProposalResponse repairProposal,
            String severity,
            String routing
    ) {
        RepairRecord record = new RepairRecord();
        record.setTaskId(taskId);
        record.setAttemptNo(attemptNo);
        record.setResumeRequestId(resumeRequestId);
        record.setDispatcherOutputJson(writeJson(TaskControlPayloadBuilder.buildDispatcherOutputPayload(severity, routing)));
        record.setRepairProposalJson(writeJson(repairProposal));
        record.setResumePayloadJson(resumePayloadJson);
        record.setResult(result);
        repairRecordMapper.insert(record);
    }

    private int resolveActiveAttemptNo(TaskState taskState) {
        return taskState.getActiveAttemptNo() == null ? 1 : taskState.getActiveAttemptNo();
    }

    private String generateWorkspaceId() {
        return "ws_" + UUID.randomUUID().toString().replace("-", "");
    }

    private TaskArtifactsResponse.AttemptArtifacts buildAttemptArtifacts(Integer attemptNo, com.sage.backend.model.WorkspaceRegistry workspace) {
        TaskArtifactsResponse.AttemptArtifacts item = new TaskArtifactsResponse.AttemptArtifacts();
        item.setAttemptNo(attemptNo);
        if (workspace != null) {
            TaskArtifactsResponse.WorkspaceSummary workspaceView = new TaskArtifactsResponse.WorkspaceSummary();
            workspaceView.setWorkspaceId(workspace.getWorkspaceId());
            workspaceView.setWorkspaceState(workspace.getWorkspaceState());
            workspaceView.setRuntimeProfile(workspace.getRuntimeProfile());
            workspaceView.setContainerName(workspace.getContainerName());
            workspaceView.setHostWorkspacePath(workspace.getHostWorkspacePath());
            workspaceView.setArchivePath(workspace.getArchivePath());
            workspaceView.setCreatedAt(toIsoString(workspace.getCreatedAt()));
            workspaceView.setStartedAt(toIsoString(workspace.getStartedAt()));
            workspaceView.setFinishedAt(toIsoString(workspace.getFinishedAt()));
            workspaceView.setCleanedAt(toIsoString(workspace.getCleanedAt()));
            workspaceView.setArchivedAt(toIsoString(workspace.getArchivedAt()));
            item.setWorkspace(workspaceView);
        }
        TaskArtifactsResponse.ArtifactCatalog buckets = new TaskArtifactsResponse.ArtifactCatalog();
        buckets.setPrimaryOutputs(new ArrayList<>());
        buckets.setIntermediateOutputs(new ArrayList<>());
        buckets.setAuditArtifacts(new ArrayList<>());
        buckets.setDerivedOutputs(new ArrayList<>());
        buckets.setLogs(new ArrayList<>());
        item.setArtifacts(buckets);
        return item;
    }

    private List<TaskArtifactsResponse.ArtifactMeta> selectArtifactBucket(
            TaskArtifactsResponse.ArtifactCatalog catalog,
            String bucketName
    ) {
        if (catalog == null) {
            throw new IllegalArgumentException("artifact catalog must be initialized");
        }
        if ("primary_outputs".equals(bucketName)) return catalog.getPrimaryOutputs();
        if ("intermediate_outputs".equals(bucketName)) return catalog.getIntermediateOutputs();
        if ("audit_artifacts".equals(bucketName)) return catalog.getAuditArtifacts();
        if ("derived_outputs".equals(bucketName)) return catalog.getDerivedOutputs();
        return catalog.getLogs();
    }

    private String mapArtifactRoleBucket(String artifactRole) {
        if ("PRIMARY_OUTPUT".equalsIgnoreCase(artifactRole)) return "primary_outputs";
        if ("INTERMEDIATE_OUTPUT".equalsIgnoreCase(artifactRole)) return "intermediate_outputs";
        if ("AUDIT_ARTIFACT".equalsIgnoreCase(artifactRole)) return "audit_artifacts";
        if ("DERIVED_OUTPUT".equalsIgnoreCase(artifactRole)) return "derived_outputs";
        return "logs";
    }

    private String toIsoString(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }

    private AnalysisManifest resolveActiveManifest(TaskState taskState) {
        if (taskState == null) {
            return null;
        }
        if (taskState.getActiveManifestId() != null && !taskState.getActiveManifestId().isBlank()) {
            AnalysisManifest manifest = analysisManifestMapper.findByManifestId(taskState.getActiveManifestId());
            if (manifest != null) {
                return manifest;
            }
        }
        return analysisManifestMapper.findLatestByTaskIdAndAttemptNo(taskState.getTaskId(), resolveActiveAttemptNo(taskState));
    }

    private com.sage.backend.task.dto.CorruptionStateView buildCorruptionState(TaskState taskState) {
        com.sage.backend.task.dto.CorruptionStateView view = new com.sage.backend.task.dto.CorruptionStateView();
        view.setCorrupted(TaskStatus.STATE_CORRUPTED.name().equals(taskState.getCurrentState()));
        view.setReason(taskState.getCorruptionReason());
        view.setCorruptedSince(toIsoString(taskState.getCorruptedSince()));
        return view;
    }

    private String derivePromotionStatus(String taskState, String corruptionReason) {
        if (TaskStatus.ARTIFACT_PROMOTING.name().equals(taskState)) {
            return "PROMOTING";
        }
        if (TaskStatus.SUCCEEDED.name().equals(taskState)) {
            return "PROMOTED";
        }
        if (TaskStatus.STATE_CORRUPTED.name().equals(taskState)
                && corruptionReason != null
                && corruptionReason.startsWith("ARTIFACT_PROMOTION_FAILED")) {
            return "FAILED";
        }
        return "NOT_PROMOTED";
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

    private ValidationStageResult runValidationStage(
            String taskId,
            int stateVersion,
            JsonNode pass1Node,
            JsonNode passBNode
    ) throws Exception {
        CapabilityContractGuard.requireValidationContracts(pass1Node);
        appendEvent(taskId, EventType.VALIDATION_STARTED.name(), null, null, stateVersion, null);
        PrimitiveValidationResponse validationResponse = runValidation(taskId, stateVersion, pass1Node, passBNode);
        String validationSummaryJson = objectMapper.writeValueAsString(validationResponse);
        JsonNode validationNode = objectMapper.readTree(validationSummaryJson);
        String inputChainStatus = deriveInputChainStatus(validationResponse);
        CognitionPassBResponse passBProjection = objectMapper.treeToValue(passBNode, CognitionPassBResponse.class);
        appendEvent(
                taskId,
                Boolean.TRUE.equals(validationResponse.getIsValid()) ? EventType.VALIDATION_PASSED.name() : EventType.VALIDATION_FAILED.name(),
                null,
                null,
                stateVersion,
                writePayload(TaskControlPayloadBuilder.buildValidationEventPayload(validationResponse))
        );

        RepairDecision repairDecision = null;
        TaskStatus nextState = TaskStatus.PLANNING;
        if (!Boolean.TRUE.equals(validationResponse.getIsValid())) {
            TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
            repairDecision = repairDispatcherService.decide(
                    validationResponse,
                    pass1Node,
                    taskAttachmentMapper.findByTaskId(taskId),
                    currentInventoryVersion(latestTaskState)
            );
            nextState = isFatalRepairDecision(repairDecision) ? TaskStatus.FAILED : TaskStatus.WAITING_USER;
        }
        return new ValidationStageResult(
                validationResponse,
                validationSummaryJson,
                validationNode,
                inputChainStatus,
                passBProjection,
                repairDecision,
                nextState
        );
    }

    private int advanceAfterValidationTransition(
            String taskId,
            int currentVersion,
            TaskStatus currentState,
            TaskStatus nextState,
            String passBJson,
            CognitionPassBResponse passBProjection,
            String validationSummaryJson,
            String inputChainStatus
    ) throws Exception {
        ensureUpdated(taskStateMapper.updateStateWithInputChain(
                taskId,
                currentVersion,
                nextState.name(),
                passBJson,
                writePayload(TaskProjectionBuilder.buildSlotBindingsSummaryPayload(passBProjection)),
                writePayload(TaskProjectionBuilder.buildArgsDraftSummaryPayload(passBProjection)),
                validationSummaryJson,
                inputChainStatus
        ));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), nextState.name(), currentVersion + 1, null);
        return currentVersion + 1;
    }

    private boolean isFatalRepairDecision(RepairDecision repairDecision) {
        return repairDecision != null
                && ("FAILED".equalsIgnoreCase(repairDecision.routing()) || "FATAL".equalsIgnoreCase(repairDecision.severity()));
    }

    private String deriveInputChainStatus(PrimitiveValidationResponse validationResponse) {
        return Boolean.TRUE.equals(validationResponse.getIsValid()) ? InputChainStatus.COMPLETE.name() : InputChainStatus.INCOMPLETE.name();
    }

    private CreateTaskResponse buildCreateTaskResponse(String taskId, String jobId, String state, int stateVersion) {
        CreateTaskResponse response = new CreateTaskResponse();
        response.setTaskId(taskId);
        response.setJobId(jobId);
        response.setState(state);
        response.setStateVersion(stateVersion);
        return response;
    }

    private ResumeTaskResponse buildResumeTaskResponse(
            String taskId,
            String state,
            int stateVersion,
            boolean resumeAccepted,
            Integer resumeAttempt
    ) {
        ResumeTaskResponse response = new ResumeTaskResponse();
        response.setTaskId(taskId);
        response.setState(state);
        response.setStateVersion(stateVersion);
        response.setResumeAccepted(resumeAccepted);
        response.setResumeAttempt(resumeAttempt);
        return response;
    }

    private boolean isTerminalJobState(String jobState) {
        return JobState.SUCCEEDED.name().equals(jobState)
                || JobState.FAILED.name().equals(jobState)
                || JobState.CANCELLED.name().equals(jobState);
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

    private AnalysisManifest buildManifestCandidate(
            String taskId,
            String userQuery,
            int attemptNo,
            JsonNode pass1Node,
            JsonNode passBNode,
            JsonNode validationNode,
            Pass2Response pass2Response,
            JsonNode executionGraph,
            JsonNode runtimeAssertions
    ) {
        AnalysisManifest manifest = new AnalysisManifest();
        manifest.setManifestId("manifest_" + UUID.randomUUID().toString().replace("-", ""));
        manifest.setTaskId(taskId);
        manifest.setAttemptNo(attemptNo);
        manifest.setManifestVersion(1);
        manifest.setFreezeStatus("CANDIDATE");
        TaskState latestTaskState = taskStateMapper.findByTaskId(taskId);
        manifest.setPlanningRevision(nextPlanningRevision(latestTaskState));
        manifest.setCheckpointVersion(nextCheckpointVersion(latestTaskState));
        manifest.setGraphDigest(pass2Response == null ? null : pass2Response.getGraphDigest());
        manifest.setPlanningSummaryJson(writeJsonIfPresent(pass2Response == null ? null : pass2Response.getPlanningSummary()));
        TaskManifestGovernanceSupport.ManifestGovernanceProjection governanceProjection =
                TaskManifestGovernanceSupport.build(
                        taskId,
                        userQuery,
                        latestTaskState,
                        pass1Node,
                        taskAttachmentMapper,
                        taskCatalogSnapshotService,
                        goalRouteService,
                        objectMapper
                );
        manifest.setCatalogSummaryJson(governanceProjection.catalogSummaryJson());
        manifest.setContractSummaryJson(governanceProjection.contractSummaryJson());
        manifest.setCapabilityKey(governanceProjection.capabilityKey());
        manifest.setSelectedTemplate(governanceProjection.selectedTemplate());
        manifest.setTemplateVersion(governanceProjection.templateVersion());
        manifest.setGoalParseJson(governanceProjection.goalParseJson());
        manifest.setSkillRouteJson(governanceProjection.skillRouteJson());
        manifest.setLogicalInputRolesJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestLogicalInputRolesPayload(pass1Node, objectMapper)));
        manifest.setSlotSchemaViewJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestSlotSchemaViewPayload(pass1Node, objectMapper)));
        manifest.setSlotBindingsJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestSlotBindingsPayload(passBNode, objectMapper)));
        manifest.setArgsDraftJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestArgsDraftPayload(passBNode, objectMapper)));
        manifest.setValidationSummaryJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestValidationSummaryPayload(validationNode, objectMapper)));
        manifest.setExecutionGraphJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestExecutionGraphPayload(executionGraph, objectMapper)));
        manifest.setRuntimeAssertionsJson(writeJsonIfPresent(TaskProjectionBuilder.buildManifestRuntimeAssertionsPayload(runtimeAssertions, objectMapper)));
        ensureInserted(analysisManifestMapper.insert(manifest));
        return manifest;
    }

    private Object readJsonObject(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) return null;
        try {
            return objectMapper.readValue(sourceJson, Object.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private String writePayload(Object value) throws Exception {
        if (value == null) {
            return null;
        }
        return objectMapper.writeValueAsString(value);
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

    private boolean isMinReady(String taskId, WaitingStateSnapshot waitingState, ResumeTaskRequest request) {
        return MinReadyEvaluator.isReady(
                waitingState == null ? null : waitingState.waitingContext(),
                taskAttachmentMapper.findByTaskId(taskId),
                request
        );
    }

    private String enrichAuditDetailWithContract(JsonNode pass1Node, String detailJson) {
        try {
            return TaskGovernanceFactSupport.enrichAuditDetailWithContract(pass1Node, detailJson, objectMapper);
        } catch (Exception exception) {
            LOGGER.warn("Failed to enrich audit detail with contract identity", exception);
            return detailJson;
        }
    }

    private Map<String, Object> buildResumeTransactionPayload(
            String resumeRequestId,
            String status,
            Integer baseCheckpointVersion,
            Integer candidateCheckpointVersion,
            TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope,
            String candidateManifestId,
            Integer candidateAttemptNo,
            String candidateJobId,
            String failureReason
    ) {
        return buildResumeTransactionPayload(
                resumeRequestId,
                status,
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope,
                candidateManifestId,
                candidateAttemptNo,
                candidateJobId,
                failureReason,
                null
        );
    }

    private Map<String, Object> buildResumeTransactionPayload(
            String resumeRequestId,
            String status,
            Integer baseCheckpointVersion,
            Integer candidateCheckpointVersion,
            TaskResumeGovernanceSupport.ResumeCatalogScope resumeCatalogScope,
            String candidateManifestId,
            Integer candidateAttemptNo,
            String candidateJobId,
            String failureReason,
            TaskResumeGovernanceSupport.ResumeContractDrift contractDrift
    ) {
        return buildResumeTransactionPayload(
                resumeRequestId,
                status,
                baseCheckpointVersion,
                candidateCheckpointVersion,
                resumeCatalogScope == null ? null : resumeCatalogScope.candidateInventoryVersion(),
                resumeCatalogScope == null ? null : resumeCatalogScope.baseCatalogInventoryVersion(),
                resumeCatalogScope == null ? null : resumeCatalogScope.baseCatalogRevision(),
                resumeCatalogScope == null ? null : resumeCatalogScope.baseCatalogFingerprint(),
                resumeCatalogScope == null ? null : resumeCatalogScope.candidateCatalogInventoryVersion(),
                resumeCatalogScope == null ? null : resumeCatalogScope.candidateCatalogRevision(),
                resumeCatalogScope == null ? null : resumeCatalogScope.candidateCatalogFingerprint(),
                candidateManifestId,
                candidateAttemptNo,
                candidateJobId,
                failureReason,
                contractDrift == null ? null : contractDrift.mismatchCode(),
                contractDrift == null ? null : contractDrift.baseContractVersion(),
                contractDrift == null ? null : contractDrift.baseContractFingerprint(),
                contractDrift == null ? null : contractDrift.candidateContractVersion(),
                contractDrift == null ? null : contractDrift.candidateContractFingerprint()
        );
    }

    private Map<String, Object> buildResumeTransactionPayload(
            String resumeRequestId,
            String status,
            Integer baseCheckpointVersion,
            Integer candidateCheckpointVersion,
            Integer candidateInventoryVersion,
            Integer baseCatalogInventoryVersion,
            Integer baseCatalogRevision,
            String baseCatalogFingerprint,
            Integer candidateCatalogInventoryVersion,
            Integer candidateCatalogRevision,
            String candidateCatalogFingerprint,
            String candidateManifestId,
            Integer candidateAttemptNo,
            String candidateJobId,
            String failureReason
    ) {
        return buildResumeTransactionPayload(
                resumeRequestId,
                status,
                baseCheckpointVersion,
                candidateCheckpointVersion,
                candidateInventoryVersion,
                baseCatalogInventoryVersion,
                baseCatalogRevision,
                baseCatalogFingerprint,
                candidateCatalogInventoryVersion,
                candidateCatalogRevision,
                candidateCatalogFingerprint,
                candidateManifestId,
                candidateAttemptNo,
                candidateJobId,
                failureReason,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Map<String, Object> buildResumeTransactionPayload(
            String resumeRequestId,
            String status,
            Integer baseCheckpointVersion,
            Integer candidateCheckpointVersion,
            Integer candidateInventoryVersion,
            Integer baseCatalogInventoryVersion,
            Integer baseCatalogRevision,
            String baseCatalogFingerprint,
            Integer candidateCatalogInventoryVersion,
            Integer candidateCatalogRevision,
            String candidateCatalogFingerprint,
            String candidateManifestId,
            Integer candidateAttemptNo,
            String candidateJobId,
            String failureReason,
            String failureCode,
            String baseContractVersion,
            String baseContractFingerprint,
            String candidateContractVersion,
            String candidateContractFingerprint
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resume_request_id", resumeRequestId);
        payload.put("status", status);
        payload.put("base_checkpoint_version", baseCheckpointVersion);
        payload.put("candidate_checkpoint_version", candidateCheckpointVersion);
        payload.put("candidate_inventory_version", candidateInventoryVersion);
        payload.put("base_catalog_inventory_version", baseCatalogInventoryVersion);
        payload.put("base_catalog_revision", baseCatalogRevision);
        payload.put("base_catalog_fingerprint", baseCatalogFingerprint);
        payload.put("candidate_catalog_inventory_version", candidateCatalogInventoryVersion);
        payload.put("candidate_catalog_revision", candidateCatalogRevision);
        payload.put("candidate_catalog_fingerprint", candidateCatalogFingerprint);
        payload.put("candidate_manifest_id", candidateManifestId);
        payload.put("candidate_attempt_no", candidateAttemptNo);
        payload.put("candidate_job_id", candidateJobId);
        payload.put("failure_reason", failureReason);
        payload.put("failure_code", failureCode);
        payload.put("base_contract_version", baseContractVersion);
        payload.put("base_contract_fingerprint", baseContractFingerprint);
        payload.put("candidate_contract_version", candidateContractVersion);
        payload.put("candidate_contract_fingerprint", candidateContractFingerprint);
        payload.put("updated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return payload;
    }

    private int currentCheckpointVersion(TaskState taskState) {
        return taskState == null || taskState.getCheckpointVersion() == null ? 0 : taskState.getCheckpointVersion();
    }

    private int currentInventoryVersion(TaskState taskState) {
        return taskState == null || taskState.getInventoryVersion() == null ? 0 : taskState.getInventoryVersion();
    }

    private int nextPlanningRevision(TaskState taskState) {
        int current = taskState == null || taskState.getPlanningRevision() == null ? 0 : taskState.getPlanningRevision();
        return current + 1;
    }

    private int nextCheckpointVersion(TaskState taskState) {
        return currentCheckpointVersion(taskState) + 1;
    }

    private int nextResumeInventoryVersion(TaskState taskState) {
        return currentInventoryVersion(taskState) + 1;
    }

    private String safeString(String value) { return value == null ? "" : value; }

    private record WaitingStateSnapshot(
            RepairDecision decision,
            JsonNode waitingContextNode,
            String waitingContextJson,
            RepairProposalResponse repairProposal
    ) {
        private boolean canResume() {
            return Boolean.TRUE.equals(decision.waitingContext().getCanResume());
        }

        private com.sage.backend.repair.dto.RepairProposalRequest.WaitingContext waitingContext() {
            return decision.waitingContext();
        }
    }

    private record ValidationStageResult(
            PrimitiveValidationResponse validationResponse,
            String validationSummaryJson,
            JsonNode validationNode,
            String inputChainStatus,
            CognitionPassBResponse passBProjection,
            RepairDecision repairDecision,
            TaskStatus nextState
    ) {
    }

    private record PassBStageResult(
            ObjectNode passBNode,
            String passBJson,
            ExecutionContractAssembler.AssemblyResult assemblyResult
    ) {
    }

    private record TerminalFailureHandling(
            TaskStatus projectedState,
            WaitingStateSnapshot waitingState
    ) {
    }

    private record TerminalFailureProjection(
            String failureSummaryJson,
            JsonNode failureSummaryNode
    ) {
    }

    private record PreparedJobSubmission(
            String pass2Json,
            Pass2Response pass2Response,
            CreateJobResponse createJobResponse,
            AnalysisManifest manifestCandidate
    ) {
    }

    private static final class NoOpSessionLifecycleService extends SessionLifecycleService {
        private NoOpSessionLifecycleService() {
            super(null, null, new ObjectMapper());
        }

        @Override
        public AnalysisSession createSessionShell(Long userId, String userGoal, String title, String sceneId) {
            AnalysisSession session = new AnalysisSession();
            session.setSessionId("sess_test_" + UUID.randomUUID().toString().replace("-", ""));
            session.setUserId(userId);
            session.setTitle(title);
            session.setUserGoal(userGoal);
            session.setStatus("RUNNING");
            session.setSceneId(sceneId);
            return session;
        }

        @Override
        public void bindTaskToSession(String sessionId, String taskId, String userGoal) {
        }

        @Override
        public void syncFromTask(TaskState taskState) {
        }

        @Override
        public void recordUserGoal(String sessionId, String taskId, String userGoal) {
        }

        @Override
        public void recordAssistantUnderstanding(TaskState taskState) {
        }

        @Override
        public void recordProgressUpdate(TaskState taskState, String phaseLabel, String systemAction, String latestProgressNote, String estimatedNextMilestone) {
        }

        @Override
        public void recordWaiting(TaskState taskState) {
        }

        @Override
        public void recordResumeNotice(TaskState taskState, String resumeRequestId, Integer attemptNo) {
        }

        @Override
        public void recordUploadAck(TaskState taskState, TaskAttachment attachment) {
        }

        @Override
        public void recordResultSummary(TaskState taskState) {
        }

        @Override
        public void recordFailureExplanation(TaskState taskState) {
        }

        @Override
        public void recordSystemNote(String sessionId, String taskId, String text) {
        }

        @Override
        public void recordUserReply(String sessionId, String taskId, String content, String clientRequestId, List<String> attachmentIds) {
        }

        @Override
        public void recordUserClarification(String sessionId, String taskId, String content, String clientRequestId, List<String> attachmentIds) {
        }
    }
}
