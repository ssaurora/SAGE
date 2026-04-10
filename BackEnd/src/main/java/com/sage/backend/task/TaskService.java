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
import com.sage.backend.model.AnalysisManifest;
import com.sage.backend.model.AuditRecord;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

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
        taskState.setPlanningRevision(0);
        taskState.setCheckpointVersion(0);
        taskState.setInventoryVersion(0);
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

            CognitionGoalRouteResponse goalRouteResponse = runGoalRoute(taskId, request.getUserQuery(), currentVersion, null);
            ObjectNode goalParseNode = buildGoalParseNode(goalRouteResponse, request.getUserQuery());
            ObjectNode skillRouteNode = buildSkillRouteNode(goalRouteResponse);
            String goalParseJson = writeJson(goalParseNode);
            String skillRouteJson = writeJson(skillRouteNode);
            taskStateMapper.updateGoalAndRoute(taskId, goalParseJson, skillRouteJson);
            appendEvent(taskId, EventType.GOAL_PARSED.name(), null, null, currentVersion, goalParseJson);
            appendEvent(taskId, EventType.SKILL_ROUTED.name(), null, null, currentVersion, skillRouteJson);

            if ("unsupported".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                String failureSummaryJson = writePayload(buildUnsupportedFailureSummaryPayload(goalRouteNodeSummary(goalParseNode)));
                taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                taskStateMapper.updateCognitionVerdict(taskId, "UNSUPPORTED");
                ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
                appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
                appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion + 1, failureSummaryJson);
                return buildCreateTaskResponse(taskId, null, TaskStatus.FAILED.name(), currentVersion + 1);
            }

            if ("ambiguous".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                WaitingStateSnapshot waitingState = buildClarifyWaitingState(taskId, "CLARIFY_INTENT", "Clarify the requested analysis before resuming.", null);
                taskStateMapper.updateCognitionVerdict(taskId, "AMBIGUOUS");
                ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                        taskId,
                        currentVersion,
                        TaskStatus.WAITING_USER.name(),
                        waitingState.waitingContextJson(),
                        waitingState.decision().waitingContext().getWaitingReasonType(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                ));
                appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, null, currentVersion + 1);
                return buildCreateTaskResponse(taskId, null, TaskStatus.WAITING_USER.name(), currentVersion + 1);
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
                    taskStateMapper.updateCognitionVerdict(taskId, "LLM_AMBIGUOUS");
                    ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                            taskId,
                            currentVersion,
                            TaskStatus.WAITING_USER.name(),
                            waitingState.waitingContextJson(),
                            waitingState.decision().waitingContext().getWaitingReasonType(),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    ));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                    recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, null, currentVersion + 1);
                    return buildCreateTaskResponse(taskId, null, TaskStatus.WAITING_USER.name(), currentVersion + 1);
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
                    ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                            taskId,
                            currentVersion,
                            TaskStatus.WAITING_USER.name(),
                            waitingState.waitingContextJson(),
                            waitingState.decision().waitingContext().getWaitingReasonType(),
                            OffsetDateTime.now(ZoneOffset.UTC)
                    ));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                    recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, null, currentVersion + 1);
                    return buildCreateTaskResponse(taskId, null, TaskStatus.WAITING_USER.name(), currentVersion + 1);
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
                ensureUpdated(taskStateMapper.updateStateWithWaitingContext(
                        taskId,
                        currentVersion,
                        TaskStatus.WAITING_USER.name(),
                        waitingState.waitingContextJson(),
                        waitingState.decision().waitingContext().getWaitingReasonType(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                ));
                appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, null, currentVersion + 1);
                return buildCreateTaskResponse(taskId, null, TaskStatus.WAITING_USER.name(), currentVersion + 1);
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
                    taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                    appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion, failureSummaryJson);
                    auditService.appendAudit(
                            taskId,
                            "TASK_CREATE",
                            "FAILED",
                            traceId,
                            writePayload(TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                                    currentState.name(),
                                    false,
                                    validationStage.inputChainStatus(),
                                    false,
                                    null,
                                    "FATAL_VALIDATION"
                            ))
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

                auditService.appendAudit(
                        taskId,
                        "TASK_CREATE",
                        "SUCCESS",
                        traceId,
                        writePayload(TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                                currentState.name(),
                                false,
                                validationStage.inputChainStatus(),
                                false,
                                null,
                                null
                        ))
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
            freezeManifestOnCommit(preparedSubmission.manifestCandidate(), currentVersion);
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

            auditService.appendAudit(
                    taskId,
                    "TASK_CREATE",
                    "SUCCESS",
                    traceId,
                    writePayload(TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                            TaskStatus.QUEUED.name(),
                            true,
                            validationStage.inputChainStatus(),
                            true,
                            preparedSubmission.createJobResponse().getJobId(),
                            null
                    ))
            );
            return buildCreateTaskResponse(taskId, preparedSubmission.createJobResponse().getJobId(), TaskStatus.QUEUED.name(), currentVersion);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Task create pipeline failed for task {}", taskId, exception);
            handlePipelineFailure(taskId, traceId, exception, currentVersion, currentState);
            throw new ResponseStatusException(BAD_GATEWAY, "Task pipeline failed", exception);
        }
    }

    public TaskDetailResponse getTask(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        AnalysisManifest activeManifest = resolveActiveManifest(taskState);
        JsonNode pass1Projection = readJsonNode(taskState.getPass1ResultJson());
        RouteProjection routeProjection = buildRouteProjection(
                taskState.getGoalParseJson(),
                taskState.getSkillRouteJson(),
                pass1Projection
        );
        Map<String, Object> catalogSummary = buildCatalogSummary(taskId, attachments, taskState);
        TaskDetailResponse response = new TaskDetailResponse();
        response.setTaskId(taskState.getTaskId());
        response.setState(taskState.getCurrentState());
        response.setStateVersion(taskState.getStateVersion());
        response.setPlanningRevision(taskState.getPlanningRevision());
        response.setCheckpointVersion(taskState.getCheckpointVersion());
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        response.setResumeTransaction(TaskProjectionBuilder.buildResumeTransaction(readJsonNode(taskState.getResumeTxnJson())));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(taskState.getCurrentState(), taskState.getCorruptionReason()));
        response.setSkillId(routeProjection.skillRoute().path("skill_id").asText(null));
        response.setSkillVersion(routeProjection.skillRoute().path("skill_version").asText(null));
        response.setGoalParseSummary(TaskProjectionBuilder.buildGoalParseSummary(routeProjection.goalParse()));
        response.setSkillRouteSummary(TaskProjectionBuilder.buildSkillRouteSummary(routeProjection.skillRoute()));
        response.setPass1Summary(buildPass1Summary(taskState.getPass1ResultJson()));
        response.setSlotBindingsSummary(TaskProjectionBuilder.buildSlotBindingsSummary(readJsonNode(taskState.getSlotBindingsSummaryJson())));
        response.setArgsDraftSummary(TaskProjectionBuilder.buildArgsDraftSummary(readJsonNode(taskState.getArgsDraftSummaryJson())));
        response.setValidationSummary(TaskProjectionBuilder.buildValidationSummary(readJsonNode(taskState.getValidationSummaryJson())));
        response.setInputChainStatus(taskState.getInputChainStatus());
        response.setPass2Summary(buildPass2Summary(taskState.getPass2ResultJson()));
        response.setResultObjectSummary(TaskProjectionBuilder.buildResultObjectSummary(readJsonNode(taskState.getResultObjectSummaryJson())));
        response.setResultBundleSummary(TaskProjectionBuilder.buildResultBundleSummaryView(readJsonNode(taskState.getResultBundleSummaryJson())));
        response.setFinalExplanationSummary(TaskProjectionBuilder.buildFinalExplanationSummary(readJsonNode(taskState.getFinalExplanationSummaryJson())));
        response.setLastFailureSummary(TaskProjectionBuilder.buildFailureSummary(readJsonNode(taskState.getLastFailureSummaryJson())));
        response.setCatalogSummary(catalogSummary);
        response.setWaitingContext(TaskProjectionBuilder.buildWaitingContext(readJsonNode(taskState.getWaitingContextJson())));
        Map<String, Object> detailCatalogConsistency = CatalogConsistencyProjector.buildDetailCatalogConsistency(
                response.getWaitingContext(),
                catalogSummary
        );
        response.setCatalogConsistency(detailCatalogConsistency);
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "task_catalog_governance",
                response.getWaitingContext() == null ? null : response.getWaitingContext().getCatalogSummary(),
                catalogSummary,
                detailCatalogConsistency
        ));
        Map<String, Object> detailFrozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                readJsonMap(activeManifest == null ? null : activeManifest.getContractSummaryJson()),
                pass1Projection
        );
        Map<String, Object> detailCurrentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> detailContractConsistency = ContractConsistencyProjector.buildDetailContractConsistency(
                detailFrozenSummary,
                detailCurrentSummary,
                response.getResumeTransaction()
        );
        response.setContractConsistency(detailContractConsistency);
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "task_contract_governance",
                detailFrozenSummary,
                detailCurrentSummary,
                detailContractConsistency,
                response.getResumeTransaction()
        ));
        response.setLatestResultBundleId(taskState.getLatestResultBundleId());
        response.setLatestWorkspaceId(taskState.getLatestWorkspaceId());
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson());
        response.setGraphDigest(pass2Root.path("graph_digest").asText(null));
        response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(pass2Root.path("planning_summary"), objectMapper));
        response.setPlanningIntentStatus(readJsonNode(taskState.getGoalParseJson()).path("planning_intent_status").asText(null));
        JsonNode passBRoot = readJsonNode(taskState.getPassbResultJson());
        response.setBindingStatus(passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null);
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(routeProjection.goalParse(), passBRoot, objectMapper));
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(routeProjection.goalParse(), objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(routeProjection.goalParse(), routeProjection.skillRoute(), objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
        String activeCaseId = extractCaseId(activeManifest);

        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        if (latestRepair != null) {
            JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson());
            response.setRepairProposal(TaskProjectionBuilder.buildRepairProposal(repairProposalNode));
            response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
            response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
        }

        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        if (jobRecord != null) {
            TaskDetailResponse.JobSummary jobSummary = new TaskDetailResponse.JobSummary();
            jobSummary.setJobId(jobRecord.getJobId());
            jobSummary.setJobState(jobRecord.getJobState());
            jobSummary.setLastHeartbeatAt(jobRecord.getLastHeartbeatAt() == null ? null : jobRecord.getLastHeartbeatAt().toString());
            jobSummary.setProviderKey(jobRecord.getProviderKey());
            jobSummary.setCapabilityKey(jobRecord.getCapabilityKey());
            jobSummary.setRuntimeProfile(jobRecord.getRuntimeProfile());
            jobSummary.setCaseId(activeCaseId);
            response.setJob(jobSummary);
            JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson());
            response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
            response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
        }
        return response;
    }

    public TaskResultResponse getTaskResult(String taskId, Long userId) {
        TaskState taskState = getOwnedTask(taskId, userId);
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        AnalysisManifest activeManifest = resolveActiveManifest(taskState);
        String activeCaseId = extractCaseId(activeManifest);

        TaskResultResponse response = new TaskResultResponse();
        response.setTaskId(taskId);
        response.setTaskState(taskState.getCurrentState());
        response.setResumeTransaction(TaskProjectionBuilder.buildResumeTransaction(readJsonNode(taskState.getResumeTxnJson())));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(taskState.getCurrentState(), taskState.getCorruptionReason()));
        response.setPlanningRevision(taskState.getPlanningRevision());
        response.setCheckpointVersion(taskState.getCheckpointVersion());
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        response.setCaseId(activeCaseId);
        response.setPlanningIntentStatus(readJsonNode(taskState.getGoalParseJson()).path("planning_intent_status").asText(null));
        JsonNode passBRoot = readJsonNode(taskState.getPassbResultJson());
        JsonNode skillRouteRoot = readJsonNode(taskState.getSkillRouteJson());
        response.setSkillId(skillRouteRoot.path("skill_id").asText(passBRoot.path("skill_id").asText(null)));
        response.setSkillVersion(skillRouteRoot.path("skill_version").asText(passBRoot.path("skill_version").asText(null)));
        response.setBindingStatus(passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null);
        JsonNode goalParseRoot = readJsonNode(taskState.getGoalParseJson());
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
        Map<String, Object> frozenCatalogSummary = readJsonMap(activeManifest == null ? null : activeManifest.getCatalogSummaryJson());
        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, attachments, taskState);
        Map<String, Object> catalogSummary = resolveManifestCatalogSummary(activeManifest, attachments, taskState);
        response.setCatalogSummary(catalogSummary);
        Map<String, Object> resultCatalogConsistency = CatalogConsistencyProjector.mergeCoverageConsistency(
                CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                        "result_catalog",
                        frozenCatalogSummary,
                        currentCatalogSummary
                ),
                List.of(),
                currentCatalogSummary,
                "result_input_bindings"
        );
        response.setCatalogConsistency(resultCatalogConsistency);
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "result_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                resultCatalogConsistency
        ));
        Map<String, Object> resultFrozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                readJsonMap(activeManifest == null ? null : activeManifest.getContractSummaryJson()),
                readJsonNode(taskState.getPass1ResultJson())
        );
        Map<String, Object> resultCurrentSummary = ContractConsistencyProjector.buildContractSummary(readJsonNode(taskState.getPass1ResultJson()));
        Map<String, Object> resultContractConsistency = ContractConsistencyProjector.buildFrozenContractConsistency(
                "result_manifest_contract",
                resultFrozenSummary,
                resultCurrentSummary
        );
        response.setContractConsistency(resultContractConsistency);
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "result_contract_governance",
                resultFrozenSummary,
                resultCurrentSummary,
                resultContractConsistency,
                response.getResumeTransaction()
        ));
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson());
        response.setCanonicalizationSummary(TaskProjectionBuilder.buildJsonObjectView(pass2Root.path("canonicalization_summary"), objectMapper));
        response.setRewriteSummary(TaskProjectionBuilder.buildJsonObjectView(pass2Root.path("rewrite_summary"), objectMapper));
        response.setFailureSummary(TaskProjectionBuilder.buildTaskResultFailureSummary(readJsonNode(taskState.getLastFailureSummaryJson())));
        if (jobRecord != null) {
            response.setJobId(jobRecord.getJobId());
            response.setJobState(jobRecord.getJobState());
            response.setProviderKey(jobRecord.getProviderKey());
            response.setRuntimeProfile(jobRecord.getRuntimeProfile());
            response.setCaseId(activeCaseId);
            response.setResultBundle(TaskProjectionBuilder.buildTaskResultBundle(readJsonNode(jobRecord.getResultBundleJson())));
            JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson());
            response.setFinalExplanation(TaskProjectionBuilder.buildTaskFinalExplanation(finalExplanationNode));
            response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
            response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
            TaskResultResponse.FailureSummary jobFailureSummary = TaskProjectionBuilder.buildTaskResultFailureSummary(readJsonNode(jobRecord.getFailureSummaryJson()));
            if (jobFailureSummary != null) {
                response.setFailureSummary(jobFailureSummary);
            }
            TaskResultResponse.DockerRuntimeEvidence dockerRuntimeEvidence =
                    TaskProjectionBuilder.buildDockerRuntimeEvidence(readJsonNode(jobRecord.getDockerRuntimeEvidenceJson()));
            response.setDockerRuntimeEvidence(dockerRuntimeEvidence);
            if (response.getCaseId() == null && dockerRuntimeEvidence != null) {
                response.setCaseId(dockerRuntimeEvidence.getCaseId());
            }
            response.setWorkspaceSummary(TaskProjectionBuilder.buildWorkspaceSummary(readJsonNode(jobRecord.getWorkspaceSummaryJson())));
            response.setArtifactCatalog(TaskProjectionBuilder.buildArtifactCatalog(readJsonNode(jobRecord.getArtifactCatalogJson())));
            response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(readJsonNode(jobRecord.getPlanningPass2SummaryJson()), objectMapper));
            List<String> resultRoleNames = extractResultInputRoleNames(response);
            resultCatalogConsistency = CatalogConsistencyProjector.mergeCoverageConsistency(
                    CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                            "result_catalog",
                            frozenCatalogSummary,
                            currentCatalogSummary
                    ),
                    resultRoleNames,
                    currentCatalogSummary,
                    "result_input_bindings"
            );
            response.setCatalogConsistency(resultCatalogConsistency);
            response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                    "result_catalog_governance",
                    frozenCatalogSummary,
                    currentCatalogSummary,
                    resultCatalogConsistency
            ));
        }
        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        if (latestRepair != null) {
            JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson());
            response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
            response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
        }
        if (activeManifest != null) {
            response.setFreezeStatus(activeManifest.getFreezeStatus());
            response.setGraphDigest(activeManifest.getGraphDigest());
            if (response.getPlanningSummary() == null) {
                response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(readJsonNode(activeManifest.getPlanningSummaryJson()), objectMapper));
            }
        }
        return response;
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
        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, attachments, taskState);
        TaskAuditResponse response = new TaskAuditResponse();
        response.setTaskId(taskId);
        for (AuditRecord auditRecord : auditService.findByTaskId(taskId)) {
            TaskAuditResponse.AuditItem item = new TaskAuditResponse.AuditItem();
            item.setId(auditRecord.getId());
            item.setActionType(auditRecord.getActionType());
            item.setActionResult(auditRecord.getActionResult());
            item.setTraceId(auditRecord.getTraceId());
            item.setCreatedAt(auditRecord.getCreatedAt() == null ? null : auditRecord.getCreatedAt().toString());
            Map<String, Object> detail = readJsonMap(auditRecord.getDetailJson());
            item.setDetail(detail == null ? Map.of() : detail);
            item.setContractGovernance(ContractGovernanceAssembler.buildAudit(detail));
            item.setCatalogGovernance(CatalogGovernanceAssembler.buildAudit(detail, currentCatalogSummary));
            response.getItems().add(item);
        }
        return response;
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

        TaskManifestResponse response = new TaskManifestResponse();
        response.setManifestId(manifest.getManifestId());
        response.setTaskId(manifest.getTaskId());
        response.setAttemptNo(manifest.getAttemptNo());
        response.setManifestVersion(manifest.getManifestVersion());
        response.setFreezeStatus(manifest.getFreezeStatus());
        response.setPlanningRevision(manifest.getPlanningRevision());
        response.setCheckpointVersion(manifest.getCheckpointVersion());
        response.setGraphDigest(manifest.getGraphDigest());
        response.setPlanningSummary(TaskProjectionBuilder.buildJsonObjectView(readJsonNode(manifest.getPlanningSummaryJson()), objectMapper));
        Map<String, Object> frozenCatalogSummary = readJsonMap(manifest == null ? null : manifest.getCatalogSummaryJson());
        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, attachments, taskState);
        Map<String, Object> catalogSummary = resolveManifestCatalogSummary(manifest, attachments, taskState);
        response.setCatalogSummary(catalogSummary);
        response.setCognitionVerdict(taskState.getCognitionVerdict());
        JsonNode goalParseRoot = readJsonNode(taskState.getGoalParseJson());
        JsonNode skillRouteRoot = readJsonNode(taskState.getSkillRouteJson());
        response.setSkillId(skillRouteRoot.path("skill_id").asText(null));
        response.setSkillVersion(skillRouteRoot.path("skill_version").asText(null));
        response.setPlanningIntentStatus(goalParseRoot.path("planning_intent_status").asText(null));
        JsonNode passBRoot = readJsonNode(taskState.getPassbResultJson());
        response.setBindingStatus(passBRoot.path("binding_status").asText(null));
        response.setOverruledFields(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("overruled_fields")));
        response.setBlockedMutations(TaskProjectionBuilder.jsonArrayToStrings(passBRoot.path("blocked_mutations")));
        response.setAssemblyBlocked(passBRoot.path("assembly_blocked").isBoolean() ? passBRoot.path("assembly_blocked").asBoolean() : null);
        response.setCaseProjection(TaskProjectionBuilder.buildCaseProjection(goalParseRoot, passBRoot, objectMapper));
        response.setGoalRouteCognition(TaskProjectionBuilder.buildCognitionView(goalParseRoot, objectMapper));
        response.setGoalRouteOutput(TaskProjectionBuilder.buildGoalRouteOutput(goalParseRoot, skillRouteRoot, objectMapper));
        response.setPassbCognition(TaskProjectionBuilder.buildCognitionView(passBRoot, objectMapper));
        response.setPassbOutput(TaskProjectionBuilder.buildStageOutput(passBRoot, objectMapper));
        response.setResumeTransaction(TaskProjectionBuilder.buildResumeTransaction(readJsonNode(taskState.getResumeTxnJson())));
        response.setCorruptionState(buildCorruptionState(taskState));
        response.setPromotionStatus(derivePromotionStatus(taskState.getCurrentState(), taskState.getCorruptionReason()));
        JsonNode pass2Root = readJsonNode(taskState.getPass2ResultJson());
        response.setCanonicalizationSummary(TaskProjectionBuilder.buildJsonObjectView(pass2Root.path("canonicalization_summary"), objectMapper));
        response.setRewriteSummary(TaskProjectionBuilder.buildJsonObjectView(pass2Root.path("rewrite_summary"), objectMapper));
        JsonNode pass1Projection = readJsonNode(taskState.getPass1ResultJson());
        Map<String, Object> manifestFrozenSummary = ContractConsistencyProjector.resolveManifestContractSummary(
                readJsonMap(manifest == null ? null : manifest.getContractSummaryJson()),
                pass1Projection
        );
        Map<String, Object> manifestCurrentSummary = ContractConsistencyProjector.buildContractSummary(pass1Projection);
        Map<String, Object> manifestContractConsistency = ContractConsistencyProjector.buildFrozenContractConsistency(
                "manifest_contract",
                manifestFrozenSummary,
                manifestCurrentSummary
        );
        response.setContractConsistency(manifestContractConsistency);
        response.setContractGovernance(ContractGovernanceAssembler.build(
                "manifest_contract_governance",
                manifestFrozenSummary,
                manifestCurrentSummary,
                manifestContractConsistency,
                response.getResumeTransaction()
        ));
        RouteProjection routeProjection = buildRouteProjection(
                manifest.getGoalParseJson(),
                manifest.getSkillRouteJson(),
                pass1Projection
        );
        response.setGoalParse(TaskProjectionBuilder.buildManifestGoalParse(routeProjection.goalParse()));
        response.setSkillRoute(TaskProjectionBuilder.buildManifestSkillRoute(routeProjection.skillRoute()));
        TaskProjectionBuilder.applyPass1Projection(response, pass1Projection, objectMapper);
        response.setLogicalInputRoles(TaskProjectionBuilder.buildManifestLogicalInputRoles(readJsonNode(manifest.getLogicalInputRolesJson())));
        response.setSlotSchemaView(TaskProjectionBuilder.buildManifestSlotSchemaView(readJsonNode(manifest.getSlotSchemaViewJson())));
        response.setSlotBindings(TaskProjectionBuilder.buildManifestSlotBindings(readJsonNode(manifest.getSlotBindingsJson())));
        Map<String, Object> manifestCatalogConsistency = CatalogConsistencyProjector.mergeCoverageConsistency(
                CatalogConsistencyProjector.buildFrozenCatalogConsistency(
                        "manifest_catalog",
                        frozenCatalogSummary,
                        currentCatalogSummary
                ),
                extractManifestRoleNames(response.getSlotBindings()),
                currentCatalogSummary,
                "manifest_slot_bindings"
        );
        response.setCatalogConsistency(manifestCatalogConsistency);
        response.setCatalogGovernance(CatalogGovernanceAssembler.build(
                "manifest_catalog_governance",
                frozenCatalogSummary,
                currentCatalogSummary,
                manifestCatalogConsistency
        ));
        response.setArgsDraft(TaskProjectionBuilder.buildJsonObjectView(readJsonNode(manifest.getArgsDraftJson()), objectMapper));
        response.setValidationSummary(TaskProjectionBuilder.buildManifestValidationSummary(readJsonNode(manifest.getValidationSummaryJson())));
        response.setExecutionGraph(TaskProjectionBuilder.buildManifestExecutionGraph(readJsonNode(manifest.getExecutionGraphJson())));
        response.setRuntimeAssertions(TaskProjectionBuilder.buildManifestRuntimeAssertions(readJsonNode(manifest.getRuntimeAssertionsJson())));
        response.setCreatedAt(manifest.getCreatedAt() == null ? null : manifest.getCreatedAt().toString());
        int attemptNo = resolveActiveAttemptNo(taskState);
        RepairRecord latestRepair = repairRecordMapper.findLatestByTaskIdAndAttemptNo(taskId, attemptNo);
        if (latestRepair != null) {
            JsonNode repairProposalNode = readJsonNode(latestRepair.getRepairProposalJson());
            response.setRepairProposalCognition(TaskProjectionBuilder.buildCognitionView(repairProposalNode, objectMapper));
            response.setRepairProposalOutput(TaskProjectionBuilder.buildStageOutput(repairProposalNode, objectMapper));
        }
        JobRecord jobRecord = taskState.getJobId() == null ? null : jobRecordMapper.findByJobId(taskState.getJobId());
        if (jobRecord != null) {
            JsonNode finalExplanationNode = readJsonNode(jobRecord.getFinalExplanationJson());
            response.setFinalExplanationCognition(TaskProjectionBuilder.buildCognitionView(finalExplanationNode, objectMapper));
            response.setFinalExplanationOutput(TaskProjectionBuilder.buildStageOutput(finalExplanationNode, objectMapper));
        }
        return response;
    }

    private List<String> extractManifestRoleNames(List<TaskManifestResponse.SlotBinding> slotBindings) {
        if (slotBindings == null || slotBindings.isEmpty()) {
            return List.of();
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (TaskManifestResponse.SlotBinding binding : slotBindings) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        return new ArrayList<>(roleNames);
    }

    private List<String> extractResultInputRoleNames(TaskResultResponse response) {
        if (response == null || response.getDockerRuntimeEvidence() == null || response.getDockerRuntimeEvidence().getInputBindings() == null) {
            return List.of();
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (TaskResultResponse.InputBinding binding : response.getDockerRuntimeEvidence().getInputBindings()) {
            if (binding != null && binding.getRoleName() != null && !binding.getRoleName().isBlank()) {
                roleNames.add(binding.getRoleName());
            }
        }
        return new ArrayList<>(roleNames);
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

        Map<String, Object> currentCatalogSummary = waitingState.waitingContext().getCatalogSummary();
        CatalogConsistencyProjector.CatalogIdentity currentCatalogIdentity = CatalogConsistencyProjector.catalogIdentity(currentCatalogSummary);
        int candidateCatalogRevision = taskState.getInventoryVersion() == null ? 0 : taskState.getInventoryVersion() + 1;

        String resumeTxnJson = writeJson(buildResumeTransactionPayload(
                resumeRequestId,
                "PREPARING",
                taskState.getCheckpointVersion(),
                taskState.getCheckpointVersion() == null ? 1 : taskState.getCheckpointVersion() + 1,
                taskState.getInventoryVersion() == null ? 0 : taskState.getInventoryVersion() + 1,
                currentCatalogIdentity.inventoryVersion(),
                currentCatalogIdentity.revision(),
                currentCatalogIdentity.fingerprint(),
                taskState.getInventoryVersion() == null ? 0 : taskState.getInventoryVersion() + 1,
                candidateCatalogRevision,
                currentCatalogIdentity.fingerprint(),
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

        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, taskState);
        CatalogConsistencyProjector.CatalogIdentity currentCatalogIdentity = CatalogConsistencyProjector.catalogIdentity(currentCatalogSummary);

        String resumeTxnJson = writeJson(buildResumeTransactionPayload(
                request.getRequestId(),
                "FORCE_REVERTED",
                taskState.getCheckpointVersion(),
                manifest.getCheckpointVersion(),
                currentInventoryVersion(taskState),
                currentCatalogIdentity.inventoryVersion(),
                currentCatalogIdentity.revision(),
                currentCatalogIdentity.fingerprint(),
                currentInventoryVersion(taskState),
                currentCatalogIdentity.revision(),
                currentCatalogIdentity.fingerprint(),
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

        if (JobState.SUCCEEDED.name().equals(newState)) {
            processSuccess(taskState, jobRecord, status);
            return;
        }

        TerminalFailureHandling terminalHandling = handleNonSuccessTerminalState(taskState, jobRecord, status, newState);
        TaskStatus projected = terminalHandling == null ? mapJobStateToTaskState(newState) : terminalHandling.projectedState();
        if (projected != null && !projected.name().equals(taskState.getCurrentState())) {
            if (projected == TaskStatus.WAITING_USER && terminalHandling != null && terminalHandling.waitingState() != null) {
                WaitingStateSnapshot waitingState = terminalHandling.waitingState();
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
                appendEvent(taskState.getTaskId(), EventType.STATE_CHANGED.name(), taskState.getCurrentState(), TaskStatus.WAITING_USER.name(), taskState.getStateVersion() + 1, null);
                recordWaitingUserEntry(
                        taskState.getTaskId(),
                        resolveActiveAttemptNo(taskState),
                        waitingState,
                        null,
                        taskState.getStateVersion() + 1
                );
                return;
            }
            ensureUpdated(taskStateMapper.updateState(taskState.getTaskId(), taskState.getStateVersion(), projected.name()));
            appendEvent(taskState.getTaskId(), EventType.STATE_CHANGED.name(), taskState.getCurrentState(), projected.name(), taskState.getStateVersion() + 1, null);
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
        }
    }

    private void processSuccess(TaskState taskState, JobRecord jobRecord, JobStatusResponse status) throws Exception {
        int version = taskState.getStateVersion();
        String taskId = taskState.getTaskId();
        String currentState = taskState.getCurrentState();

        if (!TaskStatus.ARTIFACT_PROMOTING.name().equals(currentState)) {
            ensureUpdated(taskStateMapper.updateState(taskId, version, TaskStatus.ARTIFACT_PROMOTING.name()));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState, TaskStatus.ARTIFACT_PROMOTING.name(), version + 1, null);
            version += 1;
            currentState = TaskStatus.ARTIFACT_PROMOTING.name();
        }

        try {
            CapabilityContractGuard.requireCollectResultBundleContract(readJsonNode(taskState.getPass1ResultJson()));
            CapabilityContractGuard.requireIndexArtifactsContract(readJsonNode(taskState.getPass1ResultJson()));
            JsonNode finalExplanationNode = buildFinalExplanationNode(taskState, jobRecord, status);
            SuccessOutputSummaries outputSummaries = buildSuccessOutputSummaries(status, finalExplanationNode);
            workspaceTraceService.persistSuccess(taskState, jobRecord, status.getResultBundle(), finalExplanationNode, status.getArtifactCatalog());
            jobRecordMapper.updateFinalExplanation(jobRecord.getJobId(), writeJsonIfPresent(finalExplanationNode), OffsetDateTime.now(ZoneOffset.UTC));
            taskStateMapper.updateOutputSummaries(
                    taskId,
                    outputSummaries.resultBundleSummary(),
                    outputSummaries.finalExplanationSummary(),
                    null,
                    outputSummaries.resultObjectSummary()
            );

            appendSuccessOutputEvents(taskId, version, outputSummaries);
        } catch (Exception exception) {
            ensureUpdated(taskStateMapper.markCorrupted(
                    taskId,
                    version,
                    TaskStatus.STATE_CORRUPTED.name(),
                    "ARTIFACT_PROMOTION_FAILED: " + safeString(exception.getMessage()),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    taskState.getResumeTxnJson()
            ));
            appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState, TaskStatus.STATE_CORRUPTED.name(), version + 1, null);
            throw exception;
        }

        ensureUpdated(taskStateMapper.updateState(taskId, version, TaskStatus.SUCCEEDED.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState, TaskStatus.SUCCEEDED.name(), version + 1, null);
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
        auditService.appendAudit(taskId, actionType, actionResult, traceId, enrichAuditDetailWithContract(pass1Node, detailJson));
    }

    private TerminalFailureHandling handleNonSuccessTerminalState(TaskState taskState, JobRecord jobRecord, JobStatusResponse status, String newState) throws Exception {
        if (!isTerminalJobState(newState) || JobState.SUCCEEDED.name().equals(newState)) {
            return null;
        }
        if (JobState.CANCELLED.name().equals(newState)) {
            appendEvent(
                taskState.getTaskId(),
                EventType.JOB_CANCELLED.name(),
                null,
                    null,
                    taskState.getStateVersion(),
                        writePayload(TaskControlPayloadBuilder.buildCancelledJobEventPayload(jobRecord.getJobId(), status.getCancelReason()))
            );
        }
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
        int attemptNo = resolveActiveAttemptNo(taskState);
        taskAttemptMapper.updateSnapshotAndJob(
                taskState.getTaskId(),
                attemptNo,
                jobRecord.getJobId(),
                writePayload(TaskControlPayloadBuilder.buildAttemptRuntimeSnapshotPayload(newState, taskState.getCurrentState())),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        if (JobState.FAILED.name().equals(newState)) {
            JsonNode failureSummary = readJsonNode(failureSummaryJson);
            RepairDecision assertionDecision = assertionFailureMapper.map(
                    failureSummary,
                    readJsonNode(taskState.getPass1ResultJson()),
                    taskAttachmentMapper.findByTaskId(taskState.getTaskId())
            );
            if (assertionDecision != null) {
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
        }
        return new TerminalFailureHandling(mapJobStateToTaskState(newState), null);
    }

    private SuccessOutputSummaries buildSuccessOutputSummaries(JobStatusResponse status, JsonNode finalExplanation) throws Exception {
        return new SuccessOutputSummaries(
                writePayload(TaskProjectionBuilder.buildResultBundleSummary(status.getResultBundle())),
                writePayload(TaskProjectionBuilder.buildFinalExplanationSummaryPayload(finalExplanation)),
                writePayload(TaskProjectionBuilder.buildResultObjectSummaryPayload(status.getResultObject(), status.getResultBundle()))
        );
    }

    private void appendSuccessOutputEvents(String taskId, int stateVersion, SuccessOutputSummaries outputSummaries) {
        appendEvent(taskId, EventType.RESULT_BUNDLE_READY.name(), null, null, stateVersion, outputSummaries.resultBundleSummary());
        appendEvent(taskId, EventType.FINAL_EXPLANATION_STARTED.name(), null, null, stateVersion, null);
        appendEvent(taskId, EventType.FINAL_EXPLANATION_COMPLETED.name(), null, null, stateVersion, outputSummaries.finalExplanationSummary());
        if (outputSummaries.resultObjectSummary() != null) {
            appendEvent(taskId, EventType.RESULT_OBJECT_READY.name(), null, null, stateVersion, outputSummaries.resultObjectSummary());
        }
    }

    private ResumeTaskResponse runResumePipeline(TaskState taskState, ResumeTaskRequest request) {
        String taskId = taskState.getTaskId();
        int currentVersion = taskState.getStateVersion();
        TaskStatus currentState = TaskStatus.valueOf(taskState.getCurrentState());
        int baseCheckpointVersion = currentCheckpointVersion(taskState);
        int candidateCheckpointVersion = baseCheckpointVersion + 1;
        int candidateInventoryVersion = nextResumeInventoryVersion(taskState);
        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, taskState);
        CatalogConsistencyProjector.CatalogIdentity baseCatalogIdentity = CatalogConsistencyProjector.catalogIdentity(currentCatalogSummary);
        Integer baseCatalogInventoryVersion = baseCatalogIdentity.inventoryVersion();
        Integer baseCatalogRevision = baseCatalogIdentity.revision();
        String baseCatalogFingerprint = baseCatalogIdentity.fingerprint();
        Integer candidateCatalogRevision = candidateInventoryVersion;
        String candidateCatalogFingerprint = baseCatalogFingerprint;
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
                    String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                            request.getResumeRequestId(),
                            "ROLLED_BACK",
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            baseCatalogInventoryVersion,
                            baseCatalogRevision,
                            baseCatalogFingerprint,
                            candidateInventoryVersion,
                            candidateCatalogRevision,
                            candidateCatalogFingerprint,
                            null,
                            resolveActiveAttemptNo(taskState),
                            null,
                            "UNSUPPORTED"
                    ));
                    taskStateMapper.updateResumeTransaction(taskId, rolledBackTxn);
                    taskStateMapper.updateCognitionVerdict(taskId, "UNSUPPORTED");
                    String failureSummaryJson = writePayload(buildUnsupportedFailureSummaryPayload(goalRouteNodeSummary(goalParseNode)));
                    taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                    ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
                    appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion + 1, failureSummaryJson);
                    return buildResumeTaskResponse(taskId, TaskStatus.FAILED.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
                }

                if ("ambiguous".equalsIgnoreCase(goalRouteResponse.getPlanningIntentStatus())) {
                    WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                            taskId,
                            "CLARIFY_INTENT",
                            "Clarify the requested analysis before resuming.",
                            request.getUserNote()
                    );
                    String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                            request.getResumeRequestId(),
                            "ROLLED_BACK",
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            baseCatalogInventoryVersion,
                            baseCatalogRevision,
                            baseCatalogFingerprint,
                            candidateInventoryVersion,
                            candidateCatalogRevision,
                            candidateCatalogFingerprint,
                            null,
                            resolveActiveAttemptNo(taskState),
                            null,
                            "AMBIGUOUS_INTENT"
                    ));
                    taskStateMapper.updateCognitionVerdict(taskId, "AMBIGUOUS");
                    ensureUpdated(taskStateMapper.rollbackResumeToWaiting(
                            taskId,
                            currentVersion,
                            TaskStatus.WAITING_USER.name(),
                            waitingState.waitingContextJson(),
                            waitingState.decision().waitingContext().getWaitingReasonType(),
                            OffsetDateTime.now(ZoneOffset.UTC),
                            rolledBackTxn
                    ));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                    recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, writeJson(request), currentVersion + 1);
                    return buildResumeTaskResponse(taskId, TaskStatus.WAITING_USER.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
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
                        String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                                request.getResumeRequestId(),
                                "ROLLED_BACK",
                                baseCheckpointVersion,
                                candidateCheckpointVersion,
                                candidateInventoryVersion,
                                baseCatalogInventoryVersion,
                                baseCatalogRevision,
                                baseCatalogFingerprint,
                                candidateInventoryVersion,
                                candidateCatalogRevision,
                                candidateCatalogFingerprint,
                                null,
                                resolveActiveAttemptNo(taskState),
                                null,
                                "CLARIFY_CASE_SELECTION"
                        ));
                        taskStateMapper.updateCognitionVerdict(taskId, "LLM_AMBIGUOUS");
                        ensureUpdated(taskStateMapper.rollbackResumeToWaiting(
                                taskId,
                                currentVersion,
                                TaskStatus.WAITING_USER.name(),
                                waitingState.waitingContextJson(),
                                waitingState.decision().waitingContext().getWaitingReasonType(),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                rolledBackTxn
                        ));
                        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                        recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, writeJson(request), currentVersion + 1);
                        return buildResumeTaskResponse(taskId, TaskStatus.WAITING_USER.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
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
                        candidateInventoryVersion,
                        baseCatalogInventoryVersion,
                        baseCatalogRevision,
                        baseCatalogFingerprint,
                        candidateCatalogRevision,
                        candidateCatalogFingerprint,
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
                    String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                            request.getResumeRequestId(),
                            "ROLLED_BACK",
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            baseCatalogInventoryVersion,
                            baseCatalogRevision,
                            baseCatalogFingerprint,
                            candidateInventoryVersion,
                            candidateCatalogRevision,
                            candidateCatalogFingerprint,
                            null,
                            resolveActiveAttemptNo(taskState),
                            null,
                            "CLARIFY_CASE_SELECTION"
                    ));
                    taskStateMapper.updateCognitionVerdict(taskId, "LLM_AMBIGUOUS");
                    ensureUpdated(taskStateMapper.rollbackResumeToWaiting(
                            taskId,
                            currentVersion,
                            TaskStatus.WAITING_USER.name(),
                            waitingState.waitingContextJson(),
                            waitingState.decision().waitingContext().getWaitingReasonType(),
                            OffsetDateTime.now(ZoneOffset.UTC),
                            rolledBackTxn
                    ));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                    recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, writeJson(request), currentVersion + 1);
                    return buildResumeTaskResponse(taskId, TaskStatus.WAITING_USER.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
                }
            }

            if ("ambiguous".equalsIgnoreCase(passBNode.path("binding_status").asText(""))) {
                WaitingStateSnapshot waitingState = buildClarifyWaitingState(
                        taskId,
                        "CLARIFY_BINDING",
                        "Clarify the requested bindings before resuming.",
                        request.getUserNote()
                );
                    String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                            request.getResumeRequestId(),
                            "ROLLED_BACK",
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            baseCatalogInventoryVersion,
                            baseCatalogRevision,
                            baseCatalogFingerprint,
                            candidateInventoryVersion,
                            candidateCatalogRevision,
                            candidateCatalogFingerprint,
                            null,
                        resolveActiveAttemptNo(taskState),
                        null,
                        "AMBIGUOUS_BINDING"
                ));
                taskStateMapper.updateCognitionVerdict(taskId, "AMBIGUOUS");
                ensureUpdated(taskStateMapper.rollbackResumeToWaiting(
                        taskId,
                        currentVersion,
                        TaskStatus.WAITING_USER.name(),
                        waitingState.waitingContextJson(),
                        waitingState.decision().waitingContext().getWaitingReasonType(),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        rolledBackTxn
                ));
                appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.WAITING_USER.name(), currentVersion + 1, null);
                recordWaitingUserEntry(taskId, resolveActiveAttemptNo(taskState), waitingState, writeJson(request), currentVersion + 1);
                return buildResumeTaskResponse(taskId, TaskStatus.WAITING_USER.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
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
                    String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                            request.getResumeRequestId(),
                            "ROLLED_BACK",
                            baseCheckpointVersion,
                            candidateCheckpointVersion,
                            candidateInventoryVersion,
                            baseCatalogInventoryVersion,
                            baseCatalogRevision,
                            baseCatalogFingerprint,
                            candidateInventoryVersion,
                            candidateCatalogRevision,
                            candidateCatalogFingerprint,
                            null,
                            resolveActiveAttemptNo(taskState),
                            null,
                            "FATAL_VALIDATION"
                    ));
                    taskStateMapper.updateResumeTransaction(taskId, rolledBackTxn);
                    String failureSummaryJson = writePayload(
                            TaskControlPayloadBuilder.buildFatalValidationFailureSummaryPayload(
                                    validationStage.validationResponse(),
                                    OffsetDateTime.now(ZoneOffset.UTC).toString()
                            )
                    );
                    taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
                    ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
                    appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
                    appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion, failureSummaryJson);

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

                    return buildResumeTaskResponse(
                            taskId,
                            TaskStatus.FAILED.name(),
                            currentVersion + 1,
                            true,
                            taskState.getActiveAttemptNo()
                    );
                }

                WaitingStateSnapshot waitingState = rebuildWaitingState(taskId, pass1Node, validationStage.validationNode(), null, request.getUserNote());
                String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                        request.getResumeRequestId(),
                        "ROLLED_BACK",
                        baseCheckpointVersion,
                        candidateCheckpointVersion,
                        candidateInventoryVersion,
                        baseCatalogInventoryVersion,
                        baseCatalogRevision,
                        baseCatalogFingerprint,
                        candidateInventoryVersion,
                        candidateCatalogRevision,
                        candidateCatalogFingerprint,
                        null,
                        resolveActiveAttemptNo(taskState),
                        null,
                        "RECOVERABLE_VALIDATION"
                ));
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

                return buildResumeTaskResponse(
                        taskId,
                        TaskStatus.WAITING_USER.name(),
                        currentVersion + 1,
                        true,
                        taskState.getActiveAttemptNo()
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
                    candidateInventoryVersion,
                    baseCatalogInventoryVersion,
                    baseCatalogRevision,
                    baseCatalogFingerprint,
                    candidateInventoryVersion,
                    candidateCatalogRevision,
                    candidateCatalogFingerprint,
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
                    candidateInventoryVersion,
                    baseCatalogInventoryVersion,
                    baseCatalogRevision,
                    baseCatalogFingerprint,
                    candidateInventoryVersion,
                    candidateCatalogRevision,
                    candidateCatalogFingerprint,
                    preparedSubmission.manifestCandidate().getManifestId(),
                    attemptNo,
                    preparedSubmission.createJobResponse().getJobId(),
                    null
            ));
            freezeManifestOnCommit(preparedSubmission.manifestCandidate(), currentVersion);
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
                        candidateInventoryVersion,
                        baseCatalogInventoryVersion,
                        baseCatalogRevision,
                        baseCatalogFingerprint,
                        candidateInventoryVersion,
                        candidateCatalogRevision,
                        candidateCatalogFingerprint,
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
            int candidateInventoryVersion,
            Integer baseCatalogInventoryVersion,
            Integer baseCatalogRevision,
            String baseCatalogFingerprint,
            Integer candidateCatalogRevision,
            String candidateCatalogFingerprint,
            JsonNode frozenPass1Node,
            JsonNode currentPass1Node
    ) throws Exception {
        Map<String, Object> drift = ContractConsistencyProjector.buildResumeContractDriftEvaluation(frozenPass1Node, currentPass1Node);
        if (drift.isEmpty()) {
            return;
        }
        String mismatchCode = safeString((String) drift.get("mismatch_code"));
        String failureReason = safeString((String) drift.get("failure_reason"));
        String frozenContractVersion = safeString((String) drift.get("frozen_contract_version"));
        String frozenContractFingerprint = safeString((String) drift.get("frozen_contract_fingerprint"));
        String currentContractVersion = safeString((String) drift.get("current_contract_version"));
        String currentContractFingerprint = safeString((String) drift.get("current_contract_fingerprint"));
        String corruptedTxn = writeJson(buildResumeTransactionPayload(
                request.getResumeRequestId(),
                "CORRUPTED",
                baseCheckpointVersion,
                candidateCheckpointVersion,
                candidateInventoryVersion,
                baseCatalogInventoryVersion,
                baseCatalogRevision,
                baseCatalogFingerprint,
                candidateInventoryVersion,
                candidateCatalogRevision,
                candidateCatalogFingerprint,
                null,
                resolveActiveAttemptNo(taskState),
                null,
                failureReason,
                mismatchCode,
                frozenContractVersion,
                frozenContractFingerprint,
                currentContractVersion,
                currentContractFingerprint
        ));
        ensureUpdated(taskStateMapper.markCorrupted(
                taskId,
                currentVersion,
                TaskStatus.STATE_CORRUPTED.name(),
                failureReason,
                OffsetDateTime.now(ZoneOffset.UTC),
                corruptedTxn
        ));
        auditService.appendAudit(
                taskId,
                "TASK_RESUME",
                "REJECTED",
                request.getResumeRequestId(),
                writePayload(ContractConsistencyProjector.buildResumeContractAuditDetail(drift))
        );
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.STATE_CORRUPTED.name(), currentVersion + 1, null);
        throw new ResponseStatusException(CONFLICT, ContractConsistencyProjector.contractConflictReason(mismatchCode));
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
        waitingContext.setCatalogSummary(buildCatalogSummary(taskId, taskStateMapper.findByTaskId(taskId)));

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
        taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
        taskStateMapper.updateCognitionVerdict(taskId, mapCognitionFailureVerdict(failureCode));
        ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
        appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion + 1, failureSummaryJson);
        auditService.appendAudit(
                taskId,
                "TASK_CREATE",
                "FAILED",
                traceId,
                writePayload(TaskControlPayloadBuilder.buildTaskCreateAuditPayload(
                        TaskStatus.FAILED.name(),
                        false,
                        InputChainStatus.INCOMPLETE.name(),
                        false,
                        null,
                        failureCode
                ))
        );
        return buildCreateTaskResponse(taskId, null, TaskStatus.FAILED.name(), currentVersion + 1);
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
        Map<String, Object> currentCatalogSummary = buildCatalogSummary(taskId, taskState);
        CatalogConsistencyProjector.CatalogIdentity currentCatalogIdentity = CatalogConsistencyProjector.catalogIdentity(currentCatalogSummary);
        String rolledBackTxn = writeJson(buildResumeTransactionPayload(
                request.getResumeRequestId(),
                "ROLLED_BACK",
                baseCheckpointVersion,
                candidateCheckpointVersion,
                candidateInventoryVersion,
                currentCatalogIdentity.inventoryVersion(),
                currentCatalogIdentity.revision(),
                currentCatalogIdentity.fingerprint(),
                candidateInventoryVersion,
                candidateInventoryVersion,
                currentCatalogIdentity.fingerprint(),
                null,
                resolveActiveAttemptNo(taskState),
                null,
                failureCode
        ));
        taskStateMapper.updateResumeTransaction(taskId, rolledBackTxn);
        taskStateMapper.updateCognitionVerdict(taskId, mapCognitionFailureVerdict(failureCode));
        String failureSummaryJson = writePayload(buildCognitionFailureSummaryPayload(stage, failureCode, payloadNode));
        taskStateMapper.updateOutputSummaries(taskId, null, null, failureSummaryJson, null);
        ensureUpdated(taskStateMapper.updateState(taskId, currentVersion, TaskStatus.FAILED.name()));
        appendEvent(taskId, EventType.STATE_CHANGED.name(), currentState.name(), TaskStatus.FAILED.name(), currentVersion + 1, null);
        appendEvent(taskId, EventType.TASK_FAILED.name(), null, null, currentVersion + 1, failureSummaryJson);
        return buildResumeTaskResponse(taskId, TaskStatus.FAILED.name(), currentVersion + 1, true, taskState.getActiveAttemptNo());
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
        boolean realCase = isRealCaseRuntime(jobRecord, status);
        if (!realCase) {
            return status.getFinalExplanation();
        }
        try {
            CognitionFinalExplanationResponse response = runFinalExplanation(taskState, jobRecord, status);
            JsonNode node = objectMapper.valueToTree(response);
            String failureCode = evaluateRequiredLlmMetadata(node.path("cognition_metadata"));
            if (failureCode != null) {
                return buildUnavailableFinalExplanationNode(failureCode, describeExplanationFailure(failureCode));
            }
            return node;
        } catch (Exception exception) {
            return buildUnavailableFinalExplanationNode(classifyCognitionException(exception), exception.getMessage());
        }
    }

    private boolean isRealCaseRuntime(JobRecord jobRecord, JobStatusResponse status) {
        if (jobRecord != null && "docker-invest-real".equalsIgnoreCase(jobRecord.getRuntimeProfile())) {
            return true;
        }
        JsonNode runtimeEvidence = status == null ? null : status.getDockerRuntimeEvidence();
        return runtimeEvidence != null
                && ("docker-invest-real".equalsIgnoreCase(runtimeEvidence.path("runtime_profile").asText(""))
                || !runtimeEvidence.path("case_id").asText("").isBlank());
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
        String caseId = extractCaseId(resolveActiveManifest(taskState));
        if (caseId != null && !caseId.isBlank()) {
            return caseId;
        }
        JsonNode runtimeEvidence = status == null ? null : status.getDockerRuntimeEvidence();
        if (runtimeEvidence == null || runtimeEvidence.isNull() || runtimeEvidence.isMissingNode()) {
            return null;
        }
        String runtimeCaseId = runtimeEvidence.path("case_id").asText(null);
        return runtimeCaseId == null || runtimeCaseId.isBlank() ? null : runtimeCaseId;
    }

    private JsonNode buildUnavailableFinalExplanationNode(String failureCode, String failureMessage) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("available", false);
        node.putNull("title");
        node.putArray("highlights");
        node.putNull("narrative");
        node.putNull("generated_at");
        node.put("failure_code", "EXPLANATION_UNAVAILABLE");
        node.put("failure_message", normalizeFailureMessage(failureMessage, describeExplanationFailure(failureCode)));
        ObjectNode metadata = node.putObject("cognition_metadata");
        metadata.put("source", "glm_required_failure");
        metadata.put("provider", "glm");
        metadata.putNull("model");
        metadata.put("prompt_version", "final_explanation_v1");
        metadata.put("fallback_used", false);
        metadata.put("schema_valid", false);
        metadata.putNull("response_id");
        metadata.put("status", "EXPLANATION_UNAVAILABLE");
        metadata.put("failure_code", failureCode);
        metadata.put("failure_message", normalizeFailureMessage(failureMessage, describeExplanationFailure(failureCode)));
        return node;
    }

    private String classifyCognitionException(Exception exception) {
        if (exception instanceof org.springframework.web.client.ResourceAccessException) {
            return "COGNITION_TIMEOUT";
        }
        return "COGNITION_UNAVAILABLE";
    }

    private String describeExplanationFailure(String failureCode) {
        return switch (failureCode) {
            case "COGNITION_TIMEOUT" -> "Final explanation cognition timed out.";
            case "COGNITION_SCHEMA_INVALID" -> "Final explanation cognition returned invalid schema.";
            case "COGNITION_POLICY_VIOLATION" -> "Final explanation cognition did not satisfy the real-case LLM policy.";
            default -> "Final explanation cognition unavailable.";
        };
    }

    private String normalizeFailureMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
        persistAcceptedJobAttempt(
                taskId,
                attemptNo,
                createJobResponse,
                pass2Response,
                workspaceId,
                providerResolution
        );
        appendAcceptedJobEvents(taskId, stateVersion, createJobResponse.getJobId());
        return new PreparedJobSubmission(pass2Json, pass2Response, createJobResponse, manifestCandidate);
    }

    private JobRecord persistAcceptedJobAttempt(
            String taskId,
            int attemptNo,
            CreateJobResponse createJobResponse,
            Pass2Response pass2Response,
            String workspaceId,
            RegistryService.ProviderResolution providerResolution
    ) {
        JobRecord jobRecord = new JobRecord();
        jobRecord.setJobId(createJobResponse.getJobId());
        jobRecord.setTaskId(taskId);
        jobRecord.setAttemptNo(attemptNo);
        jobRecord.setJobState(createJobResponse.getJobState());
        jobRecord.setExecutionGraphJson(writeJsonIfPresent(TaskProjectionBuilder.buildExecutionGraphPayload(
                pass2Response.getMaterializedExecutionGraph(),
                objectMapper
        )));
        jobRecord.setRuntimeAssertionsJson(writeJsonIfPresent(TaskProjectionBuilder.buildRuntimeAssertionsPayload(
                pass2Response.getRuntimeAssertions(),
                objectMapper
        )));
        jobRecord.setPlanningPass2SummaryJson(writeJsonIfPresent(TaskProjectionBuilder.buildPlanningPass2SummaryPayload(
                pass2Response.getPlanningSummary(),
                objectMapper
        )));
        jobRecord.setWorkspaceId(workspaceId);
        jobRecord.setProviderKey(providerResolution.providerKey());
        jobRecord.setCapabilityKey(providerResolution.capabilityKey());
        jobRecord.setRuntimeProfile(providerResolution.runtimeProfile());
        jobRecord.setAcceptedAt(createJobResponse.getAcceptedAt());
        jobRecord.setLastHeartbeatAt(createJobResponse.getAcceptedAt());
        ensureInserted(jobRecordMapper.insert(jobRecord));
        workspaceTraceService.createWorkspaceRecord(
                workspaceId,
                taskId,
                createJobResponse.getJobId(),
                attemptNo,
                providerResolution.runtimeProfile()
        );
        return jobRecord;
    }

    private void appendAcceptedJobEvents(String taskId, int stateVersion, String jobId) {
        String payloadJson = writeJson(TaskControlPayloadBuilder.buildJobReferencePayload(jobId));
        appendEvent(taskId, EventType.JOB_SUBMITTED.name(), null, null, stateVersion, payloadJson);
        appendEvent(taskId, EventType.JOB_STATE_CHANGED.name(), null, JobState.ACCEPTED.name(), stateVersion, payloadJson);
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

    private String extractCaseId(AnalysisManifest manifest) {
        if (manifest == null) {
            return null;
        }
        JsonNode argsDraft = readJsonNode(manifest.getArgsDraftJson());
        if (argsDraft == null || argsDraft.isNull() || argsDraft.isMissingNode()) {
            return null;
        }
        String caseId = argsDraft.path("case_id").asText(null);
        return caseId == null || caseId.isBlank() ? null : caseId;
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

    private RouteProjection buildRouteProjection(String goalParseJson, String skillRouteJson, JsonNode pass1Projection) {
        return new RouteProjection(
                goalRouteService.enrichGoalParse(readJsonNode(goalParseJson), pass1Projection),
                goalRouteService.enrichSkillRoute(readJsonNode(skillRouteJson), pass1Projection)
        );
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
        manifest.setCatalogSummaryJson(writeJson(buildCatalogSummary(taskId, latestTaskState)));
        manifest.setContractSummaryJson(writeJson(ContractConsistencyProjector.buildContractSummary(pass1Node)));
        manifest.setCapabilityKey(pass1Node == null || pass1Node.isMissingNode()
                ? null
                : Pass1FactHelper.normalizeCapabilityKey(pass1Node.path("capability_key").asText(null)));
        manifest.setSelectedTemplate(pass1Node == null || pass1Node.isMissingNode() ? null : pass1Node.path("selected_template").asText(null));
        manifest.setTemplateVersion(pass1Node == null || pass1Node.isMissingNode() ? null : pass1Node.path("template_version").asText(null));
        String goalParseJson = latestTaskState == null ? null : latestTaskState.getGoalParseJson();
        String skillRouteJson = latestTaskState == null ? null : latestTaskState.getSkillRouteJson();
        if ((goalParseJson == null || goalParseJson.isBlank()) || (skillRouteJson == null || skillRouteJson.isBlank())) {
            GoalRouteService.GoalRouteDecision fallbackDecision = goalRouteService.deriveFallback(userQuery, pass1Node);
            if (goalParseJson == null || goalParseJson.isBlank()) {
                goalParseJson = writeJson(fallbackDecision.goalParse());
            }
            if (skillRouteJson == null || skillRouteJson.isBlank()) {
                skillRouteJson = writeJson(fallbackDecision.skillRoute());
            }
        }
        if ((goalParseJson != null && !goalParseJson.isBlank()) || (skillRouteJson != null && !skillRouteJson.isBlank())) {
            RouteProjection routeProjection = buildRouteProjection(goalParseJson, skillRouteJson, pass1Node);
            goalParseJson = writeJson(routeProjection.goalParse());
            skillRouteJson = writeJson(routeProjection.skillRoute());
        }
        manifest.setGoalParseJson(goalParseJson);
        manifest.setSkillRouteJson(skillRouteJson);
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

    private void freezeManifestOnCommit(AnalysisManifest manifest, int stateVersion) {
        ensureUpdated(analysisManifestMapper.updateFreezeStatus(
                manifest.getManifestId(),
                "CANDIDATE",
                "FROZEN"
        ));
        appendEvent(
                manifest.getTaskId(),
                EventType.ANALYSIS_MANIFEST_FROZEN.name(),
                null,
                null,
                stateVersion,
                writeJson(TaskControlPayloadBuilder.buildManifestFrozenPayload(
                        manifest.getManifestId(),
                        manifest.getAttemptNo(),
                        manifest.getManifestVersion()
                ))
        );
    }

    private TaskDetailResponse.Pass1Summary buildPass1Summary(String pass1ResultJson) {
        if (pass1ResultJson == null || pass1ResultJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(pass1ResultJson);
            return TaskProjectionBuilder.buildPass1Summary(root);
        } catch (Exception exception) {
            return null;
        }
    }

    private TaskDetailResponse.Pass2Summary buildPass2Summary(String pass2ResultJson) {
        if (pass2ResultJson == null || pass2ResultJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(pass2ResultJson);
            return TaskProjectionBuilder.buildPass2Summary(root);
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
            auditService.appendAudit(
                    taskId,
                    "TASK_CREATE",
                    "FAILED",
                    traceId,
                    writePayload(TaskControlPayloadBuilder.buildPipelineFailureAuditPayload(
                            exception,
                            fromState,
                            expectedVersion,
                            OffsetDateTime.now().toString()
                    ))
            );
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

    private Map<String, Object> buildCatalogSummary(String taskId, TaskState taskState) {
        List<TaskAttachment> attachments = taskAttachmentMapper.findByTaskId(taskId);
        return buildCatalogSummary(taskId, attachments, taskState);
    }

    private Map<String, Object> buildCatalogSummary(List<TaskAttachment> attachments, TaskState taskState) {
        return buildCatalogSummary(null, attachments, taskState);
    }

    private Map<String, Object> buildCatalogSummary(String taskId, List<TaskAttachment> attachments, TaskState taskState) {
        return taskCatalogSnapshotService.resolveCatalogSummary(taskId, attachments, currentInventoryVersion(taskState));
    }

    private Map<String, Object> resolveManifestCatalogSummary(
            AnalysisManifest manifest,
            List<TaskAttachment> attachments,
            TaskState taskState
    ) {
        Map<String, Object> frozenSummary = readJsonMap(manifest == null ? null : manifest.getCatalogSummaryJson());
        if (frozenSummary != null && !frozenSummary.isEmpty()) {
            return frozenSummary;
        }
        return taskCatalogSnapshotService.resolveManifestCatalogSummary(
                frozenSummary,
                taskState == null ? null : taskState.getTaskId(),
                attachments,
                currentInventoryVersion(taskState)
        );
    }

    private String enrichAuditDetailWithContract(JsonNode pass1Node, String detailJson) {
        try {
            return writePayload(ContractConsistencyProjector.enrichAuditDetailWithContract(pass1Node, readJsonMap(detailJson)));
        } catch (Exception exception) {
            LOGGER.warn("Failed to enrich audit detail with contract identity", exception);
            return detailJson;
        }
    }

    private Map<String, Object> readJsonMap(String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            return TaskProjectionBuilder.buildJsonObjectView(objectMapper.readTree(sourceJson), objectMapper);
        } catch (Exception exception) {
            return null;
        }
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

    private record RouteProjection(
            JsonNode goalParse,
            JsonNode skillRoute
    ) {
    }

    private record SuccessOutputSummaries(
            String resultBundleSummary,
            String finalExplanationSummary,
            String resultObjectSummary
    ) {
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

    private record PreparedJobSubmission(
            String pass2Json,
            Pass2Response pass2Response,
            CreateJobResponse createJobResponse,
            AnalysisManifest manifestCandidate
    ) {
    }
}
