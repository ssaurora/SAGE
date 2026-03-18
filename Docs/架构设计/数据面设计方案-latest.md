
## 1. 文档定位

数据面是系统的**状态、事实、工件与运行痕迹的分层存储体系**。它不是单一数据库，也不是简单的“持久化层”，而是为认知面、规划面、控制面、能力面、执行面共同服务的落盘基础设施。数据面负责把前五层产生的核心对象按一致性级别、生命周期、访问模式和体量特征，分别落入关系型数据库、缓存协调存储、对象存储与文件工作空间中，形成可恢复、可审计、可查询、可回放、可清理的数据闭环。

在整体架构中：

- 认知面产生 `goal_parse`、`skill_route`、`args_draft`、`repair_proposal`、`decision_summary` 等认知事实对象。
    
- 规划面产生 `selected_template`、`materialized_execution_graph`、`runtime_assertions`、`planning_summary`、`planning_manifest_payload`、`planning_revision` 等编译产物。
    
- 控制面持有 `TaskState`、`AnalysisManifest`、状态机、恢复事务、排队信息、确认记录、事件流与审计链。
    
- 能力面持有 `Capability Registry`、`Provider Registry`、contract 版本、scope 约束与路由元信息。
    
- 执行面产生 `JobRecord`、`WorkspaceRecord`、`ResultBundle`、`ArtifactRecord`、lease 与 heartbeat 等运行事实。
    

数据面的任务不是重新解释这些对象，而是把它们**正确地放到正确的地方**，并保证：

- 可变状态与不可变快照严格分离
    
- 结构化事实与大文件工件严格分离
    
- 高频短期运行态与长期审计事实严格分离
    
- 逻辑删除与物理销毁严格分离
    
- 运行时缓存丢失后，核心事实仍可重建
    

---

## 2. 设计目标

数据面至少应满足以下十项目标。

第一，支撑控制面的唯一主状态机。`TaskState` 必须具备强一致、可版本控制、可恢复、可查询的存储基础，不能依赖缓存或文件系统临时状态。

第二，支撑冻结对象的不可变性。`AnalysisManifest`、`EventLog`、`AuditRecord`、`ResultBundleRecord` 等关键对象必须支持 append-only 语义，禁止覆盖历史快照。

第三，支撑运行时高频协调。队列元信息、heartbeat、lease、幂等键、限流计数等高频短期状态应落在低延迟协调存储中，而不是把所有高频写压到关系库。

第四，支撑大文件与最终工件的长期保存。final outputs、审计保留工件、归档日志、失败现场保留件不能保存在 workspace 临时目录中，必须进入对象存储体系。

第五，支撑 scratch data 的临时高吞吐读写。preprocess 与 analysis workspace 应在文件空间中运行，以适配大 TIFF、大矢量和中间产物的本地 I/O。

第六，支撑全链审计与回放。事件、状态变化、Manifest 版本、artifact 索引、provider 版本与断言结果必须可追溯、可定位、可串联。

第七，支撑恢复与重建。即使 Redis 或局部缓存失效，系统仍可依赖 PostgreSQL + 对象存储 + workspace registry 重建核心任务事实。

第八，支撑版本并存。provider version、manifest version、planning revision、run revision、checkpoint version 等对象必须可以同时存在，不得因“最新值覆盖旧值”而破坏旧任务恢复与历史审计。

第九，支撑生命周期治理。对象应具备明确的 retention、archive、destroy、expire 策略，避免“成功态垃圾堆积”或“失败现场无限保留”。

第十，支撑多环境与多租户隔离。不同 tenant、不同环境、不同 workspace、不同 bucket / schema / prefix 必须有清晰隔离边界。

---

## 3. 存储分层模型

数据面建议分为四类存储层：

### 3.1 PostgreSQL：核心事实与可审计状态层

用于保存**强一致、结构化、可查询、可审计**的核心对象，典型包括：

- `task_state`
    
- `analysis_manifest`
    
- `job_record`
    
- `workspace_registry`
    
- `result_bundle_record`
    
- `artifact_index`
    
- `event_log`
    
- `audit_record`
    
- `confirmation_request`
    
- `capability_registry`
    
- `provider_registry`
    
- `queue_record`
    
- `checkpoint_alignment_record`
    

这些对象要么是主状态，要么是追加式事实，要么是全链审计锚点，必须具备事务性和长期可查询性。

### 3.2 Redis：高频运行态与短期协调层

用于保存**高频、短期、可丢失重建**的运行态协调对象，典型包括：

- 当前 heartbeat 快照
    
- workspace 活跃 lease / 引用计数
    
- queue 实时槽位占用
    
- 幂等键与短期请求去重
    
- cancel token
    
- 限流 / 节流计数
    
- 恢复过程中的临时 token
    
- delayed tombstone / demolish 延迟队列
    

Redis 不应承载唯一事实；它保存的是“运行时效率状态”，不是“唯一审计真相”。

### 3.3 对象存储：持久工件层

用于保存**最终成果、归档工件、保留证据和可下载对象**，典型包括：

- final outputs
    
- 审计保留日志
    
- 归档 Manifest 快照
    
- 失败现场保留件
    
- 结构化结果导出
    
- 报告与图表
    
- 归档后的断言结果明细
    

对象存储保存的是“应长期保留或应跨服务共享的大对象”。

### 3.4 文件空间 / 本地磁盘 / NAS：运行工作空间层

用于保存**运行中的 scratch data**，典型包括：

- preprocess workspace
    
- analysis workspace
    
- 大 TIFF 临时文件
    
- 中间矢量层
    
- runtime assertion 临时结果
    
- 本地日志缓冲
    
- promotion 前待上传文件
    

文件空间只承载运行期局部性数据，不承载长期事实真相。

---

## 4. 对象分类规则

为了避免数据面变成“什么都能存的桶”，必须先给对象分类。

### 4.1 可变控制对象

允许更新，但必须受版本控制与乐观锁保护：

- `TaskState`
    
- `QueueRecord`
    
- `WorkspaceRegistry`（状态字段）
    
- `JobRecord`（状态字段）
    
- `ConfirmationRequest`（状态字段）
    
- `ProviderRegistry`（状态字段，如 ACTIVE / DEPRECATED）
    

### 4.2 不可变冻结对象

一旦写入，不允许业务路径更新，只能追加新版本：

- `AnalysisManifest`
    
- `CapabilitySnapshot`
    
- `ProviderBindingSnapshot`
    
- `PlanningSnapshot`
    
- `ResultBundleRecord`
    
- `AuditRecord`
    
- `EventLog`
    

### 4.3 高速短期运行对象

允许高频读写、失效重建、过期清理：

- heartbeat cache
    
- active leases
    
- idempotency keys
    
- cancel tokens
    
- in-flight promotion lock
    
- queue slot counters
    

### 4.4 大文件工件对象

只以索引与指针进入关系库，本体进入对象存储或文件空间：

- raster outputs
    
- vector outputs
    
- archive logs
    
- exported report bundles
    
- failed snapshot tarballs
    

---

## 5. 核心数据对象总览

数据面至少应正式承载以下对象：

- `TaskState`
    
- `AnalysisManifest`
    
- `JobRecord`
    
- `WorkspaceRecord`
    
- `ArtifactRecord`
    
- `ResultBundleRecord`
    
- `EventRecord`
    
- `AuditRecord`
    
- `ConfirmationRequest`
    
- `QueueRecord`
    
- `LeaseRecord`
    
- `CapabilityRecord`
    
- `ProviderRecord`
    
- `CheckpointAlignmentRecord`
    

这些对象共同覆盖：

- 主状态
    
- 冻结执行契约
    
- 运行轮次
    
- 临时空间
    
- 最终工件
    
- 结构化结果
    
- 事件
    
- 审计
    
- 协议能力
    
- provider 版本
    
- 恢复对齐
    

---

## 6. TaskState 存储设计

### 6.1 定位

`TaskState` 是控制面的唯一主控制对象，必须落在 PostgreSQL 中，并以乐观锁方式更新。它是系统最核心的可变对象。

### 6.2 建议表：`task_state`

核心字段建议包括：

- `task_id` (PK)
    
- `conversation_id`
    
- `request_id`
    
- `user_id`
    
- `tenant_id`
    
- `main_state`
    
- `phase`
    
- `substage`
    
- `state_version`
    
- `planning_revision`
    
- `checkpoint_version`
    
- `inventory_version`
    
- `blacklist_version`
    
- `snapshot_signature`
    
- `manifest_id`
    
- `manifest_version`
    
- `current_job_id`
    
- `current_run_revision_id`
    
- `current_preprocess_run_revision_id`
    
- `queue_record_id`
    
- `pending_confirmation_id`
    
- `audit_trace_id`
    
- `latest_error_code`
    
- `risk_flags`（JSONB）
    
- `policy_flags`（JSONB）
    
- `created_at`
    
- `updated_at`
    
- `last_event_at`
    

### 6.3 更新规则

- 所有更新必须带 `expected_state_version`
    
- 成功更新后 `state_version += 1`
    
- 不允许无版本条件覆盖写
    
- 历史变化通过 `event_log` 和 `audit_record` 追溯，不在 `task_state` 中保留全量历史列
    

---

## 7. AnalysisManifest 存储设计

### 7.1 定位

`AnalysisManifest` 是冻结后的正式执行契约，必须 append-only，严禁业务路径更新旧记录。

### 7.2 建议表：`analysis_manifest`

核心字段建议包括：

- `manifest_id` (PK)
    
- `manifest_version`
    
- `task_id`
    
- `state_version_at_freeze`
    
- `planning_revision`
    
- `goal_snapshot`（JSONB）
    
- `skill_snapshot`（JSONB）
    
- `template_snapshot`（JSONB）
    
- `bindings_snapshot`（JSONB）
    
- `args_final`（JSONB）
    
- `execution_graph_digest`
    
- `execution_graph_ref`
    
- `runtime_assertions`（JSONB）
    
- `resource_profile`（JSONB）
    
- `risk_and_confirmation`（JSONB）
    
- `artifacts_contract`（JSONB）
    
- `audit_refs`（JSONB）
    
- `created_at`
    

### 7.3 关键规则

- 永远 `INSERT`
    
- 严禁基于 `task_id` 对旧 Manifest 执行 `UPDATE`
    
- 当前任务使用哪个 Manifest，由 `task_state.manifest_id` 指针决定
    
- 历史 Manifest 长期保留，用于审计、恢复与回放
    

---

## 8. JobRecord 存储设计

### 8.1 定位

`JobRecord` 是执行面每个作业轮次的结构化记录，既要支持状态更新，也要保留历史运行事实。

### 8.2 建议表：`job_record`

核心字段建议包括：

- `job_id` (PK)
    
- `task_id`
    
- `job_type`
    
- `job_state`
    
- `state_version_at_submit`
    
- `manifest_id`（分析作业时）
    
- `planning_revision`
    
- `preprocess_run_revision_id`
    
- `run_revision_id`
    
- `workspace_runtime_id`
    
- `worker_id`
    
- `worker_type`
    
- `runtime_profile`
    
- `provider_version`
    
- `cancel_mode_requested`
    
- `physical_termination_confirmed`
    
- `heartbeat_lost_at`
    
- `failure_class`
    
- `failure_code`
    
- `failure_info`（JSONB）
    
- `resource_usage_summary`（JSONB）
    
- `started_at`
    
- `finished_at`
    
- `created_at`
    
- `updated_at`
    

### 8.3 状态语义

允许更新 `job_state`，但重要状态转移必须伴随 `event_log` 追加写。

---

## 9. WorkspaceRegistry 设计

### 9.1 定位

`WorkspaceRegistry` 是 workspace 的主索引与生命周期注册表。  
本体目录在文件系统，元数据在 PostgreSQL，短期活跃租约在 Redis。

### 9.2 建议表：`workspace_registry`

核心字段建议包括：

- `workspace_runtime_id` (PK)
    
- `task_id`
    
- `workspace_type`：`PREPROCESS` / `ANALYSIS`
    
- `preprocess_run_revision_id`
    
- `run_revision_id`
    
- `root_path`
    
- `retention_policy`
    
- `workspace_state`
    
- `promotion_in_progress`
    
- `finalization_in_progress`
    
- `demolish_requested_at`
    
- `demolish_block_reason`
    
- `last_cleanup_status`
    
- `last_cleanup_at`
    
- `created_at`
    
- `updated_at`
    
- `destroyed_at`
    

### 9.3 Redis 侧 lease

建议 Redis 使用：

- key: `workspace:lease:{workspace_runtime_id}`
    
- value: `lease_count`, `lease_holders`, `lease_expires_at`
    

若 Redis 丢失，lease 可视为运行态协调信息；必要时由执行面重新上报或通过活跃 job 恢复。

---

## 10. ResultBundleRecord 设计

### 10.1 定位

`ResultBundleRecord` 是执行结果的结构化索引，而不是大文件载体。

### 10.2 建议表：`result_bundle_record`

核心字段建议包括：

- `result_bundle_id` (PK)
    
- `task_id`
    
- `job_id`
    
- `run_revision_id`
    
- `manifest_id`
    
- `job_status`
    
- `primary_outputs`（JSONB）
    
- `intermediate_outputs`（JSONB）
    
- `audit_artifacts`（JSONB）
    
- `runtime_assertion_results`（JSONB）
    
- `quality_flags`（JSONB）
    
- `resource_usage_summary`（JSONB）
    
- `failure_info`（JSONB）
    
- `workspace_ref`
    
- `created_at`
    

### 10.3 铁律

- 只存结构化摘要和文件指针
    
- 不存大文件二进制
    
- 允许一任务多 bundle（多 run、多失败轮次）
    

---

## 11. ArtifactIndex 设计

### 11.1 定位

`ArtifactIndex` 是对象存储与结果文件的索引层，关系库存元数据，对象存储存本体。

### 11.2 建议表：`artifact_index`

核心字段建议包括：

- `artifact_id` (PK)
    
- `task_id`
    
- `job_id`
    
- `run_revision_id`
    
- `artifact_class`：`FINAL_OUTPUT` / `AUDIT_RECORD` / `DERIVED_OUTPUT` / `FAILURE_EVIDENCE`
    
- `artifact_name`
    
- `storage_uri`
    
- `media_type`
    
- `size_bytes`
    
- `checksum`
    
- `promotion_status`
    
- `source_workspace_runtime_id`
    
- `retention_policy`
    
- `created_at`
    
- `verified_at`
    
- `deleted_at`
    

### 11.3 状态规则

- artifact 本体可能先在 workspace，再被 promotion 到对象存储
    
- 索引可更新 `promotion_status`、`verified_at`
    
- 不得把对象存储 URI 直接写死到 `TaskState` 中，统一经 `artifact_index` 查询
    

---

## 12. EventLog 与 AuditRecord 设计

### 12.1 EventLog

`event_log` 用于记录系统事件流，是 append-only。

核心字段建议：

- `event_id` (PK)
    
- `task_id`
    
- `event_type`
    
- `main_state`
    
- `phase`
    
- `substage`
    
- `state_version`
    
- `job_id`
    
- `run_revision_id`
    
- `manifest_id`
    
- `summary`
    
- `payload_ref` 或 `payload_json`
    
- `created_at`
    

### 12.2 AuditRecord

`audit_record` 用于记录可审计事实链，是 append-only。

核心字段建议：

- `audit_id` (PK)
    
- `audit_trace_id`
    
- `task_id`
    
- `audit_domain`：`DECISION` / `PLANNING` / `REPAIR` / `FREEZE` / `EXECUTION` / `RECOVERY` / `ARTIFACT` / `EXPLANATION`
    
- `ref_type`
    
- `ref_id`
    
- `snapshot_version`
    
- `summary`
    
- `details_json`
    
- `created_at`
    

### 12.3 分工区别

- `event_log` 强调时序事件
    
- `audit_record` 强调审计证据与事实引用
    

两者都应长期保留，但索引与查询方式不同。

---

## 13. QueueRecord 设计

### 13.1 定位

队列状态不能只活在 Redis。  
由于控制面定义了 `QUEUED`、`queue_ttl`、重评估与出队裁决，必须在 PostgreSQL 中保留正式 `QueueRecord`，Redis 仅保留实时协调状态。

### 13.2 建议表：`queue_record`

核心字段建议包括：

- `queue_record_id` (PK)
    
- `task_id`
    
- `manifest_id`
    
- `required_resource_profile`（JSONB）
    
- `queue_entered_at`
    
- `queue_ttl_seconds`
    
- `last_feasibility_check_at`
    
- `feasibility_basis_version`
    
- `queue_state`
    
- `dequeue_reason`
    
- `created_at`
    
- `updated_at`
    

### 13.3 Redis 协同

Redis 可保存：

- 实时优先级队列
    
- 可用槽位计数
    
- worker class occupancy
    

但 PostgreSQL 中的 `queue_record` 才是任务是否处于排队态的正式事实。

---

## 14. CapabilityRegistry 与 ProviderRegistry 设计

### 14.1 Capability Registry

建议表：`capability_registry`

核心字段：

- `capability_id` (PK)
    
- `capability_name`
    
- `capability_version`
    
- `category`
    
- `status`
    
- `allowed_callers`（JSONB）
    
- `skill_scope`（JSONB）
    
- `template_scope`（JSONB）
    
- `side_effect_level`
    
- `input_schema_ref`
    
- `output_schema_ref`
    
- `error_schema_ref`
    
- `provider_binding_id`
    
- `created_at`
    
- `deprecated_at`
    

### 14.2 Provider Registry

建议表：`provider_registry`

核心字段：

- `provider_binding_id` (PK)
    
- `provider_id`
    
- `provider_version`
    
- `provider_type`
    
- `endpoint`
    
- `health_state`
    
- `capabilities`（JSONB）
    
- `runtime_profiles`（JSONB）
    
- `lifecycle_state`：`ACTIVE` / `PREFERRED` / `DEPRECATED` / `DISABLED`
    
- `registered_at`
    
- `updated_at`
    

### 14.3 版本并存

必须允许：

- `invest_water_yield@3.13`
    
- `invest_water_yield@3.14`
    

并存。  
Registry 不能让新 provider 覆盖旧 contract，旧任务仍需严格绑定旧版本。

---

## 15. ConfirmationRequest 与 Recovery 对齐对象

### 15.1 `confirmation_request`

用于保存待确认事项。

核心字段建议包括：

- `confirmation_id` (PK)
    
- `task_id`
    
- `type`
    
- `severity`
    
- `reason_code`
    
- `impact_summary`
    
- `options_json`
    
- `decision`
    
- `decided_by`
    
- `decided_at`
    
- `expires_at`
    
- `created_at`
    

### 15.2 `checkpoint_alignment_record`

用于保存恢复事务与强制对齐动作。

核心字段建议包括：

- `alignment_id` (PK)
    
- `task_id`
    
- `resume_request_id`
    
- `target_checkpoint_version`
    
- `acked_checkpoint_version`
    
- `alignment_action`：`ACK` / `FORCE_REVERT` / `ROLLBACK` / `MANUAL_RECOVERY`
    
- `alignment_status`
    
- `error_code`
    
- `created_at`
    
- `updated_at`
    

这能保证恢复事务与 `STATE_CORRUPTED` 修复过程可审计、可回放。

---

## 16. ID 与版本体系

数据面必须统一几类关键标识，不得混用。

### 16.1 主标识

- `task_id`
    
- `job_id`
    
- `manifest_id`
    
- `artifact_id`
    
- `workspace_runtime_id`
    
- `event_id`
    
- `audit_id`
    
- `confirmation_id`
    

### 16.2 版本标识

- `state_version`
    
- `planning_revision`
    
- `checkpoint_version`
    
- `manifest_version`
    
- `preprocess_run_revision_id`
    
- `run_revision_id`
    
- `capability_version`
    
- `provider_version`
    

### 16.3 原则

- 主标识唯一定位对象
    
- 版本标识刻画对象演化
    
- 不得用单一 `revision` 充当所有版本概念
    

---

## 17. 一致性与事务策略

### 17.1 PostgreSQL 强一致对象

以下对象必须以 PostgreSQL 事务保证一致：

- `task_state`
    
- `analysis_manifest`
    
- `confirmation_request`
    
- `queue_record`
    
- `checkpoint_alignment_record`
    

### 17.2 Append-only 策略

以下对象必须 append-only：

- `analysis_manifest`
    
- `event_log`
    
- `audit_record`
    
- `result_bundle_record`
    

### 17.3 双写策略

以下场景涉及双写，需要明确顺序：

#### Artifact Promotion

1. 对象存储上传成功
    
2. verify 成功
    
3. 更新 `artifact_index.promotion_status = DONE`
    
4. 再允许控制面推进终局清理
    

#### Workspace Demolish

1. 执行面物理删除成功
    
2. 更新 `workspace_registry.workspace_state = DESTROYED`
    
3. 追加事件日志
    

#### Resume Commit

1. 认知面 ack 已落盘
    
2. 控制面提交 `TaskState`
    
3. 更新 inventory / snapshot 相关事实
    
4. 追加审计记录
    

### 17.4 Redis 与 PG 的关系

Redis 永远不能成为唯一事实来源。  
任何 Redis 中的运行态，只要重要到需要长期恢复和审计，就必须在 PostgreSQL 中存在正式锚点。

---

## 18. 生命周期与保留策略

### 18.1 长期保留

建议长期保留：

- `analysis_manifest`
    
- `event_log`
    
- `audit_record`
    
- `artifact_index`
    
- `confirmation_request`
    
- `checkpoint_alignment_record`
    

### 18.2 中期保留

建议按窗口保留：

- `job_record`
    
- `workspace_registry`
    
- `result_bundle_record`
    
- 失败现场 artifact
    
- archived logs
    

### 18.3 短期过期

建议短期 TTL：

- Redis heartbeat
    
- Redis lease
    
- idempotency keys
    
- throttling counters
    
- delayed tombstone queue items
    

### 18.4 物理销毁与逻辑保留

即使 workspace 已物理销毁，其 registry 记录、事件、audit、artifact index 仍应保留。  
数据面的基本原则是：

> 物理数据可以删，事实轨迹不能消失。

---

## 19. 恢复与重建能力

### 19.1 Redis 丢失后的恢复

Redis 丢失后可从以下对象重建核心事实：

- 从 `task_state` 重建当前任务主状态
    
- 从 `queue_record` 重建正式排队事实
    
- 从 `job_record` 重建当前作业态
    
- 从 `workspace_registry` 重建 workspace 目录事实
    
- 从 `artifact_index` 重建持久化工件清单
    

lease / heartbeat 这类短期态则由执行面重新上报或由活跃 job 推断恢复。

### 19.2 对象存储与索引不一致时

必须支持 reconcile 流程：

- 以 `artifact_index` 为索引主表
    
- 对对象存储做后台校验
    
- 缺失对象标记为 `BROKEN_REFERENCE`
    
- 多余对象标记为 `ORPHAN_OBJECT`
    
- 不自动静默修复为“最新值覆盖”
    

### 19.3 Manifest 回放能力

只要 `analysis_manifest` 与 `artifact_index`、`event_log`、`audit_record` 还在，系统就应能回放：

- 当时使用的模板与参数
    
- 当时绑定的 provider / capability version
    
- 当时的结果指针与失败位置
    

---

## 20. 多租户与多环境隔离

### 20.1 PostgreSQL 隔离

建议至少在逻辑上支持：

- `tenant_id`
    
- `environment`：`dev` / `test` / `prod`
    

可采用：

- 单库多 schema
    
- 单 schema + tenant column
    
- 多库隔离
    

### 20.2 对象存储隔离

建议按：

- bucket
    
- prefix
    
- tenant
    
- environment
    
- task_id / run_revision
    

组织路径，例如：

```text
s3://prod-artifacts/{tenant_id}/{task_id}/{run_revision_id}/
```

### 20.3 文件空间隔离

workspace 路径必须至少按：

- environment
    
- tenant
    
- task_id
    
- run revision
    

分层，避免不同租户与环境混写目录。

---

## 21. 目录与路径约定

建议统一以下目录范式：

### 21.1 Workspace

```text
/workspaces/{env}/{tenant}/{task_id}/preprocess/{preprocess_run_revision_id}/
/workspaces/{env}/{tenant}/{task_id}/analysis/{run_revision_id}/
```

### 21.2 对象存储

```text
/{env}/{tenant}/artifacts/{task_id}/{run_revision_id}/final/
/{env}/{tenant}/artifacts/{task_id}/{run_revision_id}/audit/
/{env}/{tenant}/archive/{task_id}/{manifest_version}/
```

### 21.3 日志与归档

```text
/{env}/{tenant}/logs/{task_id}/{job_id}/
/{env}/{tenant}/failure-evidence/{task_id}/{run_revision_id}/
```

路径约定本身不是装饰，而是后续清理、隔离、审计与权限控制的前提。

---

## 22. 工程落地顺序

### 第一阶段：核心表

先建：

- `task_state`
    
- `analysis_manifest`
    
- `event_log`
    
- `audit_record`
    

这是系统主状态与冻结对象的基础。

### 第二阶段：执行相关表

再建：

- `job_record`
    
- `workspace_registry`
    
- `result_bundle_record`
    
- `artifact_index`
    
- `queue_record`
    

这样控制面与执行面才能闭环。

### 第三阶段：能力与 provider 表

再建：

- `capability_registry`
    
- `provider_registry`
    

这样能力面 contract 与版本治理才能正式落盘。

### 第四阶段：恢复与确认表

再建：

- `confirmation_request`
    
- `checkpoint_alignment_record`
    

这样恢复事务与人工确认才具备正式历史追踪。

### 第五阶段：Redis keyspace 与对象存储约定

最后补：

- heartbeat keyspace
    
- lease keyspace
    
- cancel token keyspace
    
- rate limit keyspace
    
- bucket / prefix 规则
    
- retention lifecycle rules
    

---

## 23. 结论

数据面不是附属工程，而是前五层设计真正落地的承重结构。

一套成熟的数据面，至少应做到：

- 用 PostgreSQL 保存主状态与冻结事实
    
- 用 Redis 承担短期协调与高频运行态
    
- 用对象存储承载长期工件与归档证据
    
- 用文件空间承载运行期 scratch data
    
- 用 append-only 规则保护 Manifest、事件与审计历史
    
- 用清晰 ID / revision 体系防止版本混淆
    
- 用 retention 与 destroy 策略防止成功态和失败态共同堆积垃圾
    
- 用 reconcile 与重建能力保证局部存储故障后系统仍可恢复核心事实
    

当数据面按这个方式收敛后，整个六层架构才真正闭环：

- 认知面负责理解
    
- 规划面负责编译
    
- 控制面负责治理
    
- 能力面负责契约
    
- 执行面负责运行
    
- 数据面负责落盘
    

## 24. 数据面运行红线

本节用于约束数据面在工程实现阶段的存储行为、自我保护能力与跨介质一致性策略，防止系统在真实运行中因缓存泄漏、物理文件覆盖、对象存储孤儿堆积或主状态聚合撕裂而失去一致性与可恢复性。

### 24.1 Redis 运行态必须强制 TTL，禁止依赖人工删除维持正确性

Redis 在数据面中的角色是高频、短期、可重建的运行态协调层，而不是永久事实层。因此，凡落入 Redis 的运行态键，都必须被视为**易失、需自动回收、不可依赖人工释放**的对象。

#### 24.1.1 强制 TTL 原则

除极少数静态配置外，Redis 中不得存在永久键。  
以下类型键必须强制绑定 TTL：

- `heartbeat:*`
    
- `workspace:lease:*`
    
- `cancel_token:*`
    
- `idempotency:*`
    
- `delayed_tombstone:*`
    
- `rate_limit:*`
    
- `queue:slot:*`
    
- 其他所有运行态短期协调键
    

系统不得依赖“任务完成后记得 DEL”来回收这些键。

#### 24.1.2 续租原则

上述键的有效性必须通过上层组件主动续租维持，例如：

- heartbeat 通过周期性写入或过期续期维持
    
- workspace lease 通过 active lease refresh 维持
    
- cancel token 通过短窗口存在完成传播
    
- delayed tombstone 通过有限窗口等待后自动失效
    

一旦上层组件失联、崩溃、断电或 OOM，Redis 必须能依靠 TTL 自动回收这些键，而不是让失效状态永久滞留。

#### 24.1.3 正式事实与运行态分离

Redis 中的键只能表达**运行态活跃性**，不能替代正式事实。  
例如：

- workspace 是否存在 lease 锚点，可在 PostgreSQL `workspace_registry` 中保留正式记录
    
- 但 lease 当前是否活跃，只能由 Redis TTL 续期状态表达
    

这意味着：

- PostgreSQL 负责“有无这个对象”
    
- Redis 负责“它现在是否还活着”
    

#### 24.1.4 恢复原则

即使 Redis 全量丢失，系统也应能依赖 PostgreSQL 中的正式锚点重建大部分状态，而不是因为 TTL 键消失就失去主状态真相。  
因此，Redis 只可保存“当前协调态”，不能保存“唯一业务事实”。

---

### 24.2 Catalog 登记后的资产必须物理不可变，禁止原地覆盖

Metadata Catalog 是认知面与规划面的默认事实来源。只要资产已进入 Catalog，并以 `asset_id`、`asset_version`、`asset_hash` 被引用，它在物理层就必须具有稳定性。否则，目录事实与物理底座之间会发生“幽灵偏差”，从而使前摄校验、模板协商、绑定结果和执行期读到的真实文件彼此矛盾。

#### 24.2.1 资产不可变原则

一旦一个资产对象被 Catalog 登记，其对应物理对象不得原地覆盖更新。  
无论发生下列哪种情况，都必须视为**新资产版本**，而不是旧资产被修改：

- 用户重新上传同名文件
    
- 预处理生成新的同逻辑产物
    
- 纠错脚本重新写出文件
    
- 运维或外部服务在 NAS / S3 中直接同路径覆盖文件
    

#### 24.2.2 版本化路径原则

资产的物理路径必须内含版本或哈希信息，保证“同一路径 = 同一物理内容”。  
禁止存在“路径不变、内容变了”的情况。

推荐目录组织示意如下：

/{env}/{tenant}/assets/{asset_id}/{asset_version}_{asset_hash}/source.tif

或：

s3://bucket/{env}/{tenant}/assets/{asset_id}/{asset_hash}/source.tif

#### 24.2.3 Catalog 与物理对象绑定字段

Catalog 至少应绑定：

- `asset_id`
    
- `asset_version`
    
- `asset_hash`
    
- `storage_uri`
    
- `format`
    
- `crs`
    
- `bbox`
    
- `parse_status`
    
- `created_at`
    

认知面、规划面、控制面都应通过这些字段引用资产，而不是通过裸文件名或逻辑别名隐式指向物理文件。

#### 24.2.4 预处理产物的特殊规则

预处理输出虽然由系统生成，但一旦被 Catalog 引用并进入后续编译与绑定链，也必须遵循同样的不可变原则。  
预处理不是“在原文件上改一改”，而是“生成一个新资产版本”。

---

### 24.3 Artifact Promotion 必须配套孤儿对账与垃圾回收机制

对象存储上的 artifact 上传与关系库中的 `artifact_index` 记录落盘，不属于同一物理事务。因此，只要系统真实运行，就一定会出现以下中间失败场景：

- 文件已上传成功，对象已存在
    
- 数据库索引未成功插入
    
- 状态机未成功推进
    
- 验证未完成
    
- 任务中途取消或网络闪断
    

如果没有后续对账与回收机制，系统将不可避免地产生孤儿对象（Orphan Objects），并随时间推移形成对象存储垃圾场与成本黑洞。

#### 24.3.1 Artifact 三阶段状态

建议 `artifact_index` 至少区分以下阶段：

- `STAGED`
    
- `INDEXED`
    
- `VERIFIED`
    

其语义如下：

- `STAGED`：对象已上传到对象存储，但尚未完成索引闭环
    
- `INDEXED`：数据库中已有索引记录，系统可识别该对象
    
- `VERIFIED`：对象已通过校验，可作为正式工件对外引用
    

#### 24.3.2 路径前缀分层

建议对象存储至少区分以下前缀：

- `staged/`
    
- `artifacts/`
    
- `archive/`
    
- `orphan/`
    

典型示意如下：

/{env}/{tenant}/staged/{task_id}/{run_revision_id}/...  
/{env}/{tenant}/artifacts/{task_id}/{run_revision_id}/...  
/{env}/{tenant}/archive/{task_id}/{manifest_version}/...  
/{env}/{tenant}/orphan/{task_id}/...

这样可使后台对账程序与对象存储生命周期策略有明确的治理范围。

#### 24.3.3 Reconcile / GC 机制

数据面必须提供周期性对账与垃圾回收能力，至少包括：

- 扫描 staged / artifacts 路径下的对象
    
- 按 `task_id`、`run_revision_id`、`artifact_id` 与 `artifact_index` 对账
    
- 对数据库无索引的对象标记为 `ORPHAN_OBJECT`
    
- 对超过保留窗口的孤儿对象执行清理
    
- 对索引存在但对象丢失的情况标记为 `BROKEN_REFERENCE`
    

#### 24.3.4 Lifecycle Policy 配合

建议对象存储配置生命周期规则：

- `staged/` 前缀短 TTL
    
- `orphan/` 前缀更短 TTL
    
- `archive/` 前缀长期保留
    
- `artifacts/` 前缀按 retention policy 保留
    

这样即使定期对账任务偶尔延迟，孤儿对象也不会无限增长。

#### 24.3.5 正式原则

可以将其归纳为一条根规则：

> 对象存储中的任何 artifact 最终都必须能被 `artifact_index` 解释；不能解释的对象必须进入孤儿回收链，而不能无限期漂浮在存储中。

---

### 24.4 TaskState 必须作为聚合根原子更新，禁止过度范式化拆表造成状态撕裂

控制面中的 `TaskState` 是唯一主控制对象，承载主状态、版本、排队信息、恢复上下文、确认上下文、执行上下文、artifact 上下文和审计上下文等高频演进信息。如果在数据库设计中将这些上下文过度拆解为多个外键表，再依赖 ORM 分多次更新，就会在真实开发中产生最危险的数据撕裂问题：

- 主状态已推进
    
- queue_context 还停留在旧值
    
- resume_context 未同步
    
- execution_context 尚未更新
    
- artifact_context 与 audit_context 延后一拍
    

这会直接破坏控制面强调的 Ack / Commit / Rollback 与 `state_version` 原子推进语义。

#### 24.4.1 聚合根原则

`TaskState` 必须被建模为一个**聚合根（Aggregate Root）**。  
凡属于“当前主控制态”的高频一致性上下文，应优先内联存放于 `task_state` 单表内，而不是过度范式化拆散。

#### 24.4.2 JSONB 内联建议

建议以下上下文以内联 `JSONB` 字段形式保存在 `task_state` 中：

- `resume_context`
    
- `confirmation_context`
    
- `queue_context`
    
- `execution_context`
    
- `artifact_context`
    
- `audit_context`
    

这样，控制面在推进一次状态机时，可以通过：

- 单行更新
    
- 单事务提交
    
- `state_version` 乐观锁校验
    

一次性完成整个任务聚合状态的原子变更。

#### 24.4.3 哪些对象不应内联

并不是所有对象都应进入 `TaskState`。以下对象应继续保持独立 append-only 表：

- `analysis_manifest`
    
- `event_log`
    
- `audit_record`
    
- `result_bundle_record`
    
- `artifact_index`
    

边界应明确为：

- `TaskState` 保存“当前控制态”
    
- 独立表保存“历史事实链”
    

#### 24.4.4 乐观锁原则

`task_state` 的任意更新都必须带 `expected_state_version`。  
只有当版本一致时，才允许：

- 更新主状态字段
    
- 更新 JSONB 上下文字段
    
- 增加 `state_version`
    
- 提交该次原子事务
    

不允许出现“先改主状态、后补上下文”的分步提交模式。

#### 24.4.5 正式原则

可概括为：

> `TaskState` 的一致性必须依赖聚合根原子更新，而不是依赖上层开发人员“记得把几个表一起改”。

---

### 24.5 建议追加的存储级字段与约束

为使上述四条红线真正可执行，建议在数据模型中补充以下字段与约束。

#### Redis 侧建议

- 所有运行态 key 必须写入 TTL
    
- 所有 lease key 必须支持续租与到期自动失效
    
- 所有 cancel token 必须设置短窗口失效时间
    

#### Asset 侧建议新增字段

- `immutable = true`
    
- `asset_hash`
    
- `asset_version`
    
- `physical_storage_uri`
    
- `derived_from_asset_id`
    
- `derived_from_asset_version`
    

#### Artifact 侧建议新增字段

- `artifact_stage`: `STAGED` / `INDEXED` / `VERIFIED`
    
- `orphan_marked_at`
    
- `reconcile_status`
    
- `storage_prefix_class`
    

#### TaskState 侧建议新增字段

- `resume_context JSONB`
    
- `confirmation_context JSONB`
    
- `queue_context JSONB`
    
- `execution_context JSONB`
    
- `artifact_context JSONB`
    
- `audit_context JSONB`
    

并确保：

- 单行更新
    
- `state_version` 乐观锁
    
- 不以 ORM 多表级联更新替代聚合根事务
    

---

### 24.6 联调与压测必须覆盖的场景

为了保证这些红线不是纸面制度，联调与压测必须覆盖以下场景：

第一，worker 在持有 workspace lease 和 heartbeat 时突然宕机，确认 Redis 中相应键会在 TTL 到期后自动回收，而不会永久残留。

第二，用户重新上传同名文件，系统必须生成新的 `asset_version` / `asset_hash` 和新物理路径，而不是覆盖旧路径。

第三，artifact 上传成功但数据库索引写入失败，后台对账流程能识别该对象并将其标记为孤儿或自动清理。

第四，控制面推进一次高频状态更新时，`task_state` 的主状态与 queue / resume / execution 上下文在一个本地事务中一起成功或一起失败，不出现半更新态。

---

### 24.7 结论

数据面是整个系统最接近“不可逆事实”的一层，因此它必须具备**存储层自我保护能力**。  
这四条红线共同保证了：

- Redis 不会因运行态键泄漏而变成内存黑洞
    
- Catalog 不会因物理文件被覆盖而退化为幽灵事实源
    
- 对象存储不会因 promotion 半失败而积累无法治理的孤儿对象
    
- `TaskState` 不会因过度拆表和 ORM 分步更新而发生状态撕裂
    

当这些约束落实到建库、建表、建 keyspace、设计对象路径和编写 reconcile 任务时，数据面才能真正成为整个系统“如磐石般稳固”的底层承重层。