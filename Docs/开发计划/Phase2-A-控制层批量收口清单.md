# Phase2-A 控制层批量收口清单

本文档用于收敛 `BackEnd/src/main/java/com/sage/backend/task/TaskService.java` 一类的重复控制流清理工作。
目标不是改状态机语义，而是把已经稳定的 Phase1/Phase2-A 控制路径收成更清晰的 helper 和 payload builder。

## 一、这类工作的定义

这里的“同类工作”指：

- 同一条 workflow 语义在多个入口重复实现
- 相同 facts 在多个调用点重复读取/重复序列化
- 相同 event / repair / snapshot payload 由不同分支各自手写
- manifest / detail / runtime sync 各自维护一套近似但不完全一致的派生规则

这类工作属于 **控制层收口**，不是新功能开发。

## 二、当前已识别的批处理分组

### B1 已完成：accepted job 编排收口

- `prepareAcceptedJobSubmission(...)`
- `persistAcceptedJobAttempt(...)`
- `appendAcceptedJobEvents(...)`

目的：

- 统一 create/resume 的 pass2 -> freeze -> resolve -> submit -> accepted event 路径

### B2 已完成：waiting / repair 记录收口

- `insertRepairRecord(...)`
- `recordWaitingUserEntry(...)`
- `resolveActiveAttemptNo(...)`
- `rebuildWaitingState(TaskState, ...)`

目的：

- 统一 WAITING_USER / resume reject / refresh waiting / fatal validation 的 repair record 写入

### B3 已完成：route facts 收口

- `buildRouteProjection(...)`
- `RouteProjection`

目的：

- 统一 detail / manifest / manifest freeze 的 route enrichment

### B4 已完成：runtime terminal 收口

- `handleNonSuccessTerminalState(...)`
- `isTerminalJobState(...)`
- `buildSuccessOutputSummaries(...)`
- `appendSuccessOutputEvents(...)`

目的：

- 统一 success / failed / cancelled 的 runtime side-effects

### B5 已完成：validation 分流收口

- `runValidationStage(...)`
- `advanceAfterValidationTransition(...)`
- `buildValidationEventPayload(...)`
- `buildFatalValidationFailureSummaryPayload(...)`
- `buildQueuedAttemptSnapshotPayload(...)`
- `ValidationStageResult`

目的：

- 统一 createTask / runResumePipeline 的 validation 调用、dispatcher 分流、input-chain 落库、fatal summary、queued snapshot

### B6 已完成：event / snapshot payload 收口

- `TaskControlPayloadBuilder.buildAttachmentUploadedPayload(...)`
- `TaskControlPayloadBuilder.buildPass1CompletedPayload(...)`
- `TaskControlPayloadBuilder.buildTaskCreateAuditPayload(...)`
- `TaskControlPayloadBuilder.buildResumeRequestEventPayload(...)`
- `TaskControlPayloadBuilder.buildResumeRejectedEventPayload(...)`
- `TaskControlPayloadBuilder.buildResumeAcceptedEventPayload(...)`
- `TaskControlPayloadBuilder.buildResumeDeactivatedAttemptSnapshotPayload(...)`
- `TaskControlPayloadBuilder.buildValidatingAttemptSnapshotPayload(...)`
- `TaskControlPayloadBuilder.buildJobReferencePayload(...)`
- `TaskControlPayloadBuilder.buildCancelledJobEventPayload(...)`
- `TaskControlPayloadBuilder.buildAttemptRuntimeSnapshotPayload(...)`
- `TaskControlPayloadBuilder.buildPassBCompletionPayload(...)`
- `TaskControlPayloadBuilder.buildPass2CompletedPayload(...)`
- `TaskControlPayloadBuilder.buildManifestFrozenPayload(...)`
- `TaskControlPayloadBuilder.buildPipelineFailureAuditPayload(...)`
- `TaskControlPayloadBuilder.buildDispatcherOutputPayload(...)`

目的：

- 统一 `TaskService` 里仍然散落的 `writeJson(Map.of(...))`
- 让 resume / runtime sync / manifest freeze 的 payload 语义有单一入口

### B7 已完成：payload builder 独立化

- 新增 `TaskControlPayloadBuilder`
- `TaskService` 不再持有大批 `build*PayloadJson(...)` / `build*SnapshotJson(...)` wrapper
- `PLANNING_PASS1_COMPLETED` / `COGNITION_PASSB_COMPLETED` / `PLANNING_PASS2_COMPLETED`
- `ATTACHMENT_UPLOADED` / `RESUME_*` / `JOB_*` / `TASK_CANCELLED`
- dispatcher output / fatal validation summary / pipeline failure audit

目的：

- 让控制层 event / audit / snapshot payload 有独立归口
- 缩小 `TaskService` 的序列化职责
- 为后续继续收控制层编排留出更清晰边界

## 三、剩余同类工作

当前剩余的同类工作已经降到低优先级，主要还有 1 组：

### R1：create / resume 响应组装

现状：

- `CreateTaskResponse` / `ResumeTaskResponse` 仍各自手写
- 但这部分重复不高，而且语义差异明确

处理策略：

- 暂不优先抽象，避免为了“统一”而损伤可读性

## 四、批处理原则

- 不改变 dispatcher authority
- 不改变 `waiting_context` 语义
- 不改变 `/resume` 边界
- 不把 prompt / provider / model 逻辑扩散进 Java backend
- 每一批只做 **control-layer dedup + fact centralization**
- 每一批改完直接跑 `mvn -q test`

## 五、执行方式

后续对这类工作不再逐条请示。
按本清单分批继续收口，除非：

- 需要改状态机语义
- 需要改 API contract
- 需要跨层转移职责
- 需要引入 Phase2-B 范围的治理对象
