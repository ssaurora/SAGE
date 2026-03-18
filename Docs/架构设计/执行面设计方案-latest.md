 
## 1. 文档定位

执行面是系统的**运行时承载层**。它负责承接控制面放行后的执行请求、承载规划面编译出的执行图、在本地运行 preprocess 与正式分析作业、执行运行时断言、维护 workspace 生命周期、生成结果 bundle、配合工件晋升与终局清理。执行面不承担用户意图理解，不承担模板协商，不持有任务主状态机，不负责全局放行，也不负责结果叙事解释。

在六层架构中：

- 认知面决定“用户要做什么、参数草案是什么”。
    
- 规划面决定“应编译成什么执行图、断言与资源画像是什么”。
    
- 控制面决定“何时放行、何时恢复、何时排队、何时终局”。
    
- 能力面提供“标准化 capability contract 与 provider 绑定”。
    
- 执行面负责“真正把作业跑起来”。
    

因此，执行面的职责不是“再做一层编排”，而是把已经冻结或已批准的执行对象，安全、隔离、可观测地运行出来。

---

## 2. 设计目标

执行面的设计目标至少包括以下十项。

第一，承接异步作业控制语义。执行面必须围绕 `submit_job`、`query_job_status`、`cancel_job`、`collect_result_bundle` 等 contract 工作，而不是暴露同步阻塞式执行接口。长任务必须以 job lifecycle 为基本单位。

第二，严格区分预处理执行与正式分析执行。控制面已经定义了 `PRE_PROCESSING` 与正式执行状态；执行面必须以不同 run revision、不同 workspace、不同日志与清理策略承接这两类执行。

第三，保证运行时断言本地下推。任何涉及大栅格、大矢量、大中间产物的断言计算，都必须在 worker 本地完成，不得把大文件上拉到上层统一判断。

第四，提供可隔离的 workspace 生命周期。执行面必须把 workspace 视为 run-scoped scratchpad，而不是长期存储。失败、取消、成功终局都必须对应明确的 cleanup、archive 或 demolish 行为。

第五，保证失败隔离。单个作业失败、单个 worker 故障、单个 workspace 污染，不得蔓延为全局状态污染。执行面必须为控制面提供足够精确的失败节点、失败类型和恢复建议。

第六，提供可观测性。执行面必须输出 heartbeat、阶段进度、资源使用摘要、断言结果、失败位置、workspace 影响、结果 bundle 引用等，供控制面聚合成统一事件流。

第七，支持高负载下的资源调度适配。执行面必须暴露 worker 标签、能力集、高内存节点、磁盘约束和运行环境信息，以便控制面队列重评估与能力面资源画像契约真正落地。

第八，支持 append-only 的执行事实。运行日志、结果摘要、断言结果、artifact 索引和失败现场元信息应尽量采用追加式记录，避免覆盖历史轮次信息。

第九，尽可能支持局部重试与部分重跑，但前提是控制面显式允许且执行图支持安全复用；不能把 partial rerun 作为默认行为。

第十，为数据面提供稳定落盘对象。执行面的输出必须足够结构化，便于后续在 PostgreSQL、Redis、对象存储和文件空间中形成清晰的数据模型。

---

## 3. 职责边界

### 3.1 执行面负责什么

执行面负责：

- 接收预处理或正式分析作业
    
- 将作业绑定到合适 worker / runtime
    
- 创建与管理 run-scoped workspace
    
- 运行 InVEST / GIS / preprocess / postprocess 子任务
    
- 在本地执行 runtime assertions
    
- 周期性上报 heartbeat 与阶段进度
    
- 收集运行日志、失败位置、资源摘要
    
- 生成 result bundle
    
- 响应 cancel 请求
    
- 执行 cleanup / archive / demolish
    
- 配合 artifact promotion 的底层文件准备与上传
    
- 向能力面 provider 与控制面返回结构化执行事实
    

### 3.2 执行面不负责什么

执行面不负责：

- 判断用户真正意图
    
- 选择 Skill
    
- 模板协商
    
- 参数草案生成
    
- 图级静态合法性裁决
    
- 运行前放行裁决
    
- 排队优先级裁决
    
- 恢复事务承认边界
    
- 终局审计裁决
    
- 最终解释文本与报告叙事
    

换句话说，执行面只回答：

> **“给定一个被允许执行的对象，我如何把它真实、安全、可观测地跑出来？”**

---

## 4. 核心设计原则

### 4.1 Run-Scoped Isolation

执行面的所有物理运行都必须绑定明确的 execution revision。不同 run 之间默认隔离，不共享临时目录、不共享未声明可复用中间产物、不共享隐式状态。

### 4.2 Locality First

重计算、重 I/O、断言计算、结果扫描优先在 worker 本地完成。控制面与能力面获取的是结构化结果，而不是把海量中间数据搬上去处理。

### 4.3 Scratchpad Only

workspace 只能是临时运行空间。最终成果必须晋升到持久化对象存储；workspace 不能承担长期保存职责。

### 4.4 Explicit Lifecycle

作业、workspace、结果 bundle、artifact、日志和断言结果都必须有明确生命周期状态，不能依赖“跑完了大概就结束了”的隐式约定。

### 4.5 Failure is First-Class

失败不是异常分支里的附属事件，而是必须被显式建模、显式分类、显式输出的运行结果。失败轮次也必须可清理、可归档、可审计。

### 4.6 Provider, Not Policy

执行面是多个 capability contract 的 provider，但不承担 capability policy、Skill 白名单、调用授权和主状态机治理。它提供运行时事实，不提供制度裁决。

---

## 5. 执行面总体结构

执行面建议由以下组件构成：

### 5.1 Job Ingress

接收来自能力面 / 控制面解析后的 job contract，请求类型至少包括：

- preprocess job
    
- analysis job
    
- workspace cleanup job
    
- workspace archive job
    
- workspace demolish job
    
- artifact preparation / promotion assist job
    

### 5.2 Scheduler / Dispatcher

根据 worker 能力、资源画像、队列状态和 job type 把作业派发给合适的 worker 池。  
调度逻辑不替代控制面的排队裁决，但负责在执行面内部完成具体落点选择。

### 5.3 Worker Runtime

真正承载运行的执行单元，可为：

- Python runtime + InVEST environment
    
- GDAL / raster / vector preprocess runtime
    
- postprocess / summarize runtime
    
- artifact mover / uploader runtime
    

### 5.4 Workspace Manager

负责创建、挂载、校验、清理、归档、销毁 run-scoped workspace。

### 5.5 Assertion Engine

在 worker 本地执行 runtime assertions，并把结果写入结构化断言结果对象。

### 5.6 Result Bundle Builder

收集日志、关键输出、摘要、断言结果、资源使用、失败信息，形成 result bundle。

### 5.7 Artifact Handler

把 final outputs 与审计工件准备为可晋升对象，必要时协助上传并校验。

### 5.8 Execution Telemetry

负责 heartbeat、阶段事件、资源摘要、失败详情、workspace 事件上报。

---

## 6. 执行单元模型

### 6.1 Worker 类型

建议至少区分以下 worker 类型：

- `PREPROCESS_WORKER`
    
- `ANALYSIS_WORKER`
    
- `POSTPROCESS_WORKER`
    
- `ARTIFACT_WORKER`
    

对于小规模系统，也可以物理上由一类 worker 承担多种角色，但在逻辑上仍应保留类型区分，以支持资源隔离、日志隔离和故障隔离。

### 6.2 Worker 能力标签

每个 worker 至少应声明：

- `worker_id`
    
- `worker_type`
    
- `capability_tags`
    
- `memory_class`
    
- `cpu_class`
    
- `disk_class`
    
- `supports_gpu`
    
- `runtime_profiles`
    
- `max_concurrent_jobs`
    
- `health_state`
    

这些标签会成为调度器选择执行落点和控制面重评估的事实基础。

### 6.3 Runtime Profile

执行面应支持显式 runtime profile，例如：

- `invest_py310_cpu`
    
- `gdal_preprocess_large_mem`
    
- `postprocess_summary_light`
    
- `artifact_uploader_io_heavy`
    

profile 不是业务模板，而是运行环境画像。它帮助执行面把不同 job 放到合适环境中执行。

---

## 7. 作业模型

### 7.1 Job 分类

执行面至少支持以下 job type：

- `PREPROCESS_JOB`
    
- `ANALYSIS_JOB`
    
- `RESULT_EXTRACTION_JOB`
    
- `WORKSPACE_CLEANUP_JOB`
    
- `WORKSPACE_ARCHIVE_JOB`
    
- `WORKSPACE_DEMOLISH_JOB`
    
- `ARTIFACT_PROMOTION_JOB`
    
- `ARTIFACT_VERIFY_JOB`
    

### 7.2 Job 标识

每个 job 至少包含：

- `job_id`
    
- `job_type`
    
- `task_id`
    
- `state_version`
    
- `planning_revision`
    
- `preprocess_run_revision_id` 或 `run_revision_id`
    
- `manifest_id`（正式分析时）
    
- `workspace_runtime_id`
    
- `provider_version`
    
- `created_at`
    

### 7.3 Job 状态

建议执行面内部维护以下 job state：

- `ACCEPTED`
    
- `QUEUED`
    
- `DISPATCHING`
    
- `RUNNING`
    
- `SUCCEEDED`
    
- `FAILED`
    
- `CANCELLING`
    
- `CANCELLED`
    
- `HEARTBEAT_LOST`
    

这些状态再通过能力面 job contract 映射给控制面。  
控制面不必看到执行面的所有内部子状态，但执行面内部必须建模它们。

### 7.4 Job 状态约束

- `ACCEPTED` 必须快速返回，不能等待执行完成。
    
- `QUEUED` 表示执行面内部已受理但暂未开始。
    
- `DISPATCHING` 表示正在分配 worker / environment。
    
- `CANCELLING` 表示收到取消，但当前原子动作尚未安全结束。
    
- `HEARTBEAT_LOST` 表示 worker 存活不可判定，应触发控制面失败治理或重评估。
    

---

## 8. 两类执行轮次

### 8.1 预处理轮次

预处理轮次以 `preprocess_run_revision_id` 标识。  
它服务于冻结前输入构造，例如裁剪、重投影、重采样、格式转换、局部统计等。  
其特点是：

- 尚未进入正式分析输出契约
    
- 可以改变后续可绑定输入事实
    
- 默认写入独立 preprocess workspace
    
- 失败后通常回流到 `WAITING_USER` 或 `FAILED`
    
- 不得与正式分析 run 共用 workspace
    

### 8.2 正式分析轮次

正式分析轮次以 `run_revision_id` 标识。  
它服务于已冻结 `AnalysisManifest` 的正式执行。  
其特点是：

- 绑定 `manifest_id`
    
- 必须产出 result bundle
    
- 必须执行 runtime assertions
    
- 成功后进入结果提取、artifact promotion 和终局清理
    
- 默认使用独立 analysis workspace
    

### 8.3 二者关系

`preprocess_run_revision_id` 与 `run_revision_id` 不能混用，也不能共用目录命名空间。  
推荐目录组织形如：

```text
/workspaces/{task_id}/preprocess/{preprocess_run_revision_id}/
/workspaces/{task_id}/analysis/{run_revision_id}/
```

这样可以避免冻结前后产物混杂，并简化清理策略。

---

## 9. Workspace 生命周期

### 9.1 Workspace 创建

执行面在接到 preprocess 或 analysis job 后，不得复用旧 workspace，除非控制面明确声明允许复用特定白名单中间产物。  
创建时至少应绑定：

- `workspace_runtime_id`
    
- `task_id`
    
- `run_revision`
    
- `workspace_type`
    
- `retention_policy`
    
- `root_path`
    

### 9.2 Workspace 状态

建议至少包括：

- `CREATED`
    
- `ACTIVE`
    
- `CLEANUP_PENDING`
    
- `ARCHIVED`
    
- `DEMOLISH_PENDING`
    
- `DESTROYED`
    
- `CLEANUP_FAILED`
    

### 9.3 Workspace 内容分类

执行面必须区分：

- 临时中间产物
    
- 断言计算缓存
    
- 最终成果候选
    
- 审计保留文件
    
- 日志与运行元信息
    

不同内容在 cleanup、archive、promotion、demolish 时处理方式不同。

### 9.4 失败态治理

若 job 因断言失败、运行失败、取消或挂起修复而中断，执行面必须在控制面策略指导下执行以下之一：

- 清理临时中间产物
    
- 只读归档失败现场
    
- 保留有限窗口后销毁
    
- 为下一轮分配新 workspace
    

### 9.5 成功态治理

成功态也必须治理。  
当 final outputs 已晋升、审计工件已归档后，执行面必须配合销毁原始 workspace。成功不代表 workspace 可长期保留。

---

## 10. Runtime Assertion Push-down

### 10.1 断言输入

规划面输出 `runtime_assertions`，能力面定义 assertion contract；执行面在接到正式分析 job 时，同时接收断言规则、断言严重性和断言插入点。

### 10.2 执行方式

断言计算必须在 worker 本地执行，典型包括：

- 文件存在性
    
- 输出尺寸/空间范围
    
- CRS 一致性
    
- NODATA 比例
    
- 值域范围
    
- 必需输出完整性
    
- 关键中间节点产物的最小可用性
    

### 10.3 断言结果

断言结果至少应输出：

- `assertion_id`
    
- `result`: `PASS` / `WARN` / `FAIL`
    
- `severity`
    
- `observed_value`
    
- `evidence_ref`
    
- `checked_at`
    

### 10.4 失败后行为

若断言失败，执行面不得继续假装成功。  
必须：

- 停止后续依赖节点（若为 BLOCK 级）
    
- 记录失败位置
    
- 输出断言结果
    
- 等待控制面决定是进入 `WAITING_USER`、`FAILED` 还是修复分流
    

---

## 11. 结果 bundle 模型

### 11.1 Result Bundle 的定位

result bundle 是执行面对控制面与能力面输出的**结构化运行结果容器**。  
它不是解释报告，不是最终用户视图，也不是对象存储目录本身；它是后续 `extract_result_facts`、`summarize_quality_flags`、`promote_artifacts_contract` 的事实基础。

### 11.2 建议结构

result bundle 至少应包含：

- `task_id`
    
- `job_id`
    
- `run_revision_id`
    
- `manifest_id`
    
- `job_status`
    
- `started_at`
    
- `finished_at`
    
- `primary_outputs[]`
    
- `intermediate_outputs[]`
    
- `runtime_assertion_results[]`
    
- `resource_usage_summary`
    
- `quality_flags`
    
- `failure_info`
    
- `log_refs[]`
    
- `workspace_ref`
    

### 11.3 输出分层

执行面必须明确区分：

- `primary_outputs`：候选最终成果
    
- `intermediate_outputs`：默认不持久化的中间文件
    
- `audit_artifacts`：日志、断言结果、失败快照、资源摘要
    
- `derived_outputs`：统计表、临时图表、局部切片
    

这种分层是后续 artifact promotion 与 cleanup 的前提。

---

## 12. Artifact Promotion 协作

### 12.1 执行面的职责

执行面不拥有“最终是否晋升”的治理权，但负责：

- 提供可晋升文件列表
    
- 准备上传源文件
    
- 在必要时执行实际上传
    
- 计算 checksum
    
- 返回上传结果
    
- 在 verify 阶段提供本地校验支持
    

### 12.2 Promotion Job

对大文件和大批量文件，artifact promotion 应被建模为独立 job，而不是顺手在主执行线程里上传。  
这样可以避免：

- 分析 job 看似成功但长期卡在上传
    
- cancel 与 promotion 逻辑互相污染
    
- 终局清理提早触发
    

### 12.3 终局销毁前置条件

执行面只有在收到控制面明确指令，且确认：

- final outputs 已成功晋升
    
- 保留策略已确定
    
- verify 已通过
    

之后，才允许 demolish workspace。

---

## 13. Heartbeat 与执行观测

### 13.1 Heartbeat

每个运行中的 job 都必须周期性上报 heartbeat，至少包含：

- `job_id`
    
- `worker_id`
    
- `timestamp`
    
- `current_stage`
    
- `progress_hint`
    
- `last_output_touch_at`
    
- `resource_snapshot`
    

### 13.2 Stage Progress

执行面建议细分以下运行阶段：

- `WORKSPACE_PREPARED`
    
- `INPUTS_MATERIALIZED`
    
- `PREPROCESS_RUNNING`
    
- `MODEL_RUNNING`
    
- `ASSERTIONS_RUNNING`
    
- `RESULT_PACKING`
    
- `PROMOTION_RUNNING`
    
- `CLEANUP_RUNNING`
    

控制面可以将这些阶段映射为更抽象的用户可见状态。

### 13.3 Resource Snapshot

资源摘要至少包括：

- `memory_used_mb`
    
- `cpu_percent`
    
- `disk_used_mb`
    
- `io_bytes`
    
- `elapsed_seconds`
    

这有助于控制面失败诊断与队列重评估。

### 13.4 Heartbeat Lost

若 heartbeat 超时，执行面应把 job 标记为 `HEARTBEAT_LOST`，而不是继续沉默。  
此时控制面可决定：

- 进入失败治理
    
- 尝试恢复 worker 连接
    
- 标记 run 为不可判定并要求人工介入
    

---

## 14. 失败分类与重试策略

### 14.1 失败分类

建议执行面至少区分：

- `USER_INPUT_FAILURE`
    
- `RUNTIME_ASSERTION_FAILURE`
    
- `ENVIRONMENT_FAILURE`
    
- `WORKER_FAILURE`
    
- `IO_FAILURE`
    
- `PROMOTION_FAILURE`
    
- `CANCELLED_FAILURE`
    
- `UNKNOWN_FAILURE`
    

### 14.2 可重试与不可重试

#### 可重试示例

- 短暂对象存储上传失败
    
- 临时网络抖动
    
- 非确定性的环境初始化失败
    

#### 不可重试示例

- 参数错误
    
- 输入不兼容
    
- 断言 BLOCK 级失败
    
- 模板与输入根本不匹配
    

### 14.3 Partial Rerun 约束

只有在同时满足以下条件时，才允许 partial rerun：

- 控制面明确允许
    
- 规划面支持安全局部复用
    
- 中间产物被明确标记为可复用白名单
    
- workspace 未污染
    
- run revision 策略允许在同一 logical run 下生成子尝试
    

否则，应默认开新 workspace、新 run revision 重跑。

---

## 15. 取消语义

### 15.1 Soft Cancel 与 Hard Cancel

执行面必须支持区分：

- `SOFT_CANCEL`：等待当前原子动作结束后再中止
    
- `HARD_CANCEL`：对可安全中断的计算强制终止
    

### 15.2 事务敏感阶段

在以下阶段应优先采用 soft cancel：

- checkpoint 相关持久化协作
    
- artifact promotion
    
- workspace archive
    
- result packing
    

### 15.3 Cancel 后行为

收到取消并最终成功中止后，执行面必须：

- 输出取消结果
    
- 记录取消发生阶段
    
- 执行 cleanup 或 archive
    
- 禁止继续推进到成功终局链
    

---

## 16. 与能力面的关系

### 16.1 执行面是 provider

执行面是多个 capability contract 的 provider，尤其是：

- job contract
    
- workspace contract
    
- result contract
    
- artifact contract
    
- runtime assertion execution support
    

### 16.2 不反向拥有 contract 治理权

执行面提供事实与执行能力，但不定义：

- 哪些 caller 可调用
    
- 哪些 Skill 可使用
    
- schema 如何版本路由
    
- capability 是否 deprecated
    
- control plane 是否允许当前任务提交
    

这些仍属于能力面与控制面的职责。

### 16.3 Provider 输出要求

执行面向能力面暴露的 provider 输出必须保持：

- 结构化
    
- 可版本化
    
- 错误码稳定
    
- 不返回隐式业务裁决文本
    
- 不隐藏 workspace / artifact / heartbeat 等关键执行事实
    

---

## 17. 与控制面的关系

### 17.1 控制面是唯一治理主机

控制面决定是否 submit、是否 queue、是否 cancel、是否 cleanup、是否 archive、是否 destroy。执行面执行这些决定，但不反客为主。

### 17.2 控制面需要的执行事实

执行面必须为控制面提供：

- `job_id`
    
- job state
    
- heartbeat
    
- current stage
    
- assertion results
    
- failure_info
    
- workspace impact
    
- result bundle ref
    
- promotion status
    
- cleanup status
    

### 17.3 状态映射

执行面状态到控制面的映射应清晰固定，不应让控制面猜测。例如：

- `ACCEPTED` → 控制面 `SUBMITTED`
    
- `QUEUED` → 控制面 `QUEUED`
    
- `RUNNING` → 控制面 `EXECUTING` 或 `PRE_PROCESSING`
    
- `SUCCEEDED` → 控制面 `RESULT_PROCESSING`
    
- `FAILED` → 控制面失败治理
    
- `CANCELLED` → 控制面 `CANCELLED`
    
- `HEARTBEAT_LOST` → 控制面失败治理或 `STATE_CORRUPTED` 分支
    

---

## 18. 安全与隔离

### 18.1 文件边界

worker 只能访问被当前 run 授权的输入、workspace 和对象存储路径，不得越权扫描其他任务目录。

### 18.2 环境隔离

不同 runtime profile 应尽量隔离依赖环境，避免 GDAL / Python / InVEST 版本互相污染。

### 18.3 任务隔离

不同任务、不同 revision 默认不能共享临时目录、日志路径和缓存写入位置。

### 18.4 失败现场隔离

失败现场如需保留，应转入只读归档，不得继续被下一轮执行复用。

---

## 19. 工程落地顺序

### 第一阶段：Job Runtime 骨架

先实现：

- `ACCEPTED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`
    
- `job_id`
    
- worker dispatch
    
- heartbeat
    

### 第二阶段：Workspace 生命周期

实现：

- create
    
- cleanup
    
- archive
    
- demolish
    
- run-scoped directory layout
    

### 第三阶段：Preprocess 与 Analysis 分流

实现：

- `PREPROCESS_JOB`
    
- `ANALYSIS_JOB`
    
- `preprocess_run_revision_id`
    
- `run_revision_id`
    

### 第四阶段：Runtime Assertion Push-down

实现：

- assertion contract consumption
    
- local assertion execution
    
- assertion result output
    

### 第五阶段：Result Bundle 与 Artifact Promotion

实现：

- result bundle builder
    
- artifact preparation
    
- promotion job
    
- verify job
    

### 第六阶段：失败隔离与 partial rerun

实现：

- failure classification
    
- retry policy
    
- safe reuse whitelist
    
- cleanup-before-rerun discipline
    

---

## 20. 结论

执行面是系统从“可编译”走向“可运行”的关键一层。它的价值不在于拥有更强的业务判断，而在于把上层已经做出的受控决策，转化为：

- 真实运行
    
- 本地断言
    
- 结构化结果
    
- 可清理工作区
    
- 可追溯执行事实
    
- 可终局闭环的产物体系
    

一套成熟的执行面，至少应满足以下条件：

- 以异步 job 为运行基本单位
    
- 以 run revision 为隔离边界
    
- 以本地 push-down 方式执行重 I/O 与断言
    
- 以 workspace lifecycle 管理临时空间
    
- 以 result bundle 输出结构化事实
    
- 以 artifact promotion 与 demolish 完成终局闭环
    
- 以 heartbeat、failure class 和 cleanup status 支撑控制面治理
    

当执行面按这个方式收敛后，前面已经完成的认知面、规划面、控制面和能力面，才真正拥有了落地运行的物理承载层。

## 21. 运行时实现约束

本节用于约束执行面在工程实现阶段的物理运行方式、进程终止方式、结果返回形态、断言执行方式与 workspace 销毁条件，防止执行层在真实操作系统、磁盘 I/O、对象存储和 C/C++ 运行库环境中出现“逻辑正确但物理失控”的实现偏差。

### 21.1 Cancel 必须同时完成逻辑收口与物理终止

执行面的取消不能只停留在状态机层。  
对于 InVEST、GDAL 及其他包含 C/C++ 扩展、多进程池或重 CPU 计算的任务，仅将 job 状态改为 `CANCELLED` 或在 Python 层抛出取消异常，并不能保证底层计算进程停止。逻辑取消与物理取消必须同时成立，取消才算真正完成。

#### 21.1.1 隔离运行单元要求

每个 preprocess job 和 analysis job 在启动时，必须绑定独立的物理运行单元，至少满足以下之一：

- 独立进程组（PGID）
    
- 独立轻量级容器
    
- 独立 Pod
    
- 等价的可整体终止运行沙箱
    

执行面必须能够定位并控制该运行单元，而不能只知道一个抽象 `job_id`。

#### 21.1.2 取消分级

执行面必须支持：

- `SOFT_CANCEL`：先请求优雅退出，等待当前原子动作结束
    
- `HARD_CANCEL`：对运行单元执行强制终止
    

以下阶段默认优先尝试 `SOFT_CANCEL`：

- result packing
    
- artifact promotion
    
- checkpoint 协作相关持久化
    
- workspace archive
    
- log flush
    

以下场景允许自动升级到 `HARD_CANCEL`：

- 超过 soft cancel 等待窗口
    
- worker 不再响应
    
- 运行单元持续占用资源且无退出迹象
    
- 控制面明确要求强终止
    

#### 21.1.3 物理终止要求

当触发 `HARD_CANCEL` 时，执行面必须对独立运行单元执行整体终止。  
若使用进程组模型，则必须对整个 PGID 发送强制终止信号；若使用容器 / Pod 模型，则必须销毁整个容器实例，而不是只杀死入口 Python 线程。

#### 21.1.4 取消完成判据

取消成功必须至少同时满足以下条件：

- job 状态进入 `CANCELLED`
    
- 底层运行单元已不存在或已彻底退出
    
- heartbeat 停止
    
- CPU / 内存 / 文件句柄占用已释放
    
- 不再继续写 workspace 输出
    
- 活跃 workspace 租约已解除或转入清理态
    

仅有状态变化而无物理资源收口，视为取消失败。

---

### 21.2 Result Bundle 必须是指针清单，禁止承载大二进制负载

执行面返回的 result bundle 是结构化运行结果容器，不是大文件传输通道。  
任何 TIFF、GeoPackage、Shapefile、CSV 批量结果、影像切片、大日志等，都不得以内联二进制、Base64 或长流方式通过控制面主链路返回。

#### 21.2.1 Result Bundle 的允许内容

result bundle 只能包含：

- 关键指标摘要
    
- 统计结果
    
- 质量标记
    
- 断言结果
    
- 文件指针 / URI
    
- 文件大小、checksum、媒体类型
    
- artifact 候选标记
    
- 错误与失败摘要
    
- 审计引用
    

推荐形式应为“指针清单”，例如：

```json
{
  "primary_outputs": [
    {
      "name": "water_yield",
      "uri": "file:///workspace_xxx/output/water_yield.tif",
      "size_bytes": 2147483648,
      "media_type": "image/tiff",
      "promotion_candidate": true
    }
  ],
  "quality_flags": ["NODATA_RATIO_WARN"],
  "metrics": {
    "nodata_ratio": 0.05
  }
}
```

#### 21.2.2 明确禁止的实现

以下实现应视为错误实现：

- 在 result bundle 中内联大 TIFF、ZIP、日志包
    
- 通过 HTTP/gRPC 将大文件直接发回控制面
    
- 对大文件进行 Base64 编码后塞入 JSON
    
- 将 result bundle 误实现为“所有结果的物理打包流”
    

#### 21.2.3 大文件移动的唯一合法路径

大体积业务文件只能通过独立的 artifact promotion 路径移动：

- 从 worker 本地文件系统
    
- 直接上传到对象存储
    
- 由 promotion job 或等价上传任务完成
    
- 再由上层消费对象存储 URI 与校验结果
    

控制面、能力面和 result extraction 链路不得承载业务大文件传输。

---

### 21.3 Runtime Assertion 必须采用 evaluator 模式，禁止在执行面核心硬编码业务断言

执行面负责本地断言执行，但执行面核心守护进程不应直接内嵌越来越多的断言业务逻辑。  
任何新增断言类型若都需要修改执行面核心代码并重新发版，将迅速导致执行面腐化为业务规则宿主。

#### 21.3.1 三层分工

Runtime Assertion 必须拆分为三层：

第一层，**Assertion Contract**  
由规划面生成、能力面标准化，包含：

- `assertion_id`
    
- `assertion_type`
    
- `target_ref`
    
- `params`
    
- `severity`
    
- `evaluator_id`
    

第二层，**Assertion Evaluator**  
由执行面本地调用的 evaluator 脚本、插件、provider 或预置小程序完成具体断言计算，例如：

- `raster_nodata_ratio_evaluator`
    
- `crs_match_evaluator`
    
- `value_domain_evaluator`
    

第三层，**Assertion Result**  
统一返回：

- `passed`
    
- `severity`
    
- `observed_value`
    
- `reason`
    
- `evidence_ref`
    
- `checked_at`
    

#### 21.3.2 执行面核心职责

执行面核心只负责：

- 加载 assertion contract
    
- 选择对应 evaluator
    
- 调度 evaluator 在本地执行
    
- 收集 evaluator 退出码与结果
    
- 将结果写入 runtime assertion result 集合
    

执行面核心不得直接承担不断扩张的业务规则判断代码。

#### 21.3.3 evaluator 扩展要求

新增断言类型时，优先新增 evaluator，而不是修改执行面核心守护进程。  
这保证执行面保持“运行承载层”定位，而不是变成“断言业务逻辑中心”。

---

### 21.4 Workspace Demolish 必须受活跃租约保护，禁止并发强删

workspace 的销毁不能只依赖控制面“理论上此刻应该可以删”的判断。  
在异步系统中，artifact promotion、runtime assertion、log flush、result packing 等动作可能仍在物理层占用文件句柄或写入目录。如果执行面收到 demolish 指令就直接 `rm -rf`，将导致：

- promotion 失败
    
- 日志截断
    
- 断言计算中断
    
- 文件锁异常
    
- workspace 状态与物理目录失配
    

#### 21.4.1 活跃租约机制

执行面必须为 workspace 引入显式活跃租约（Active Lease）或等价引用计数机制。  
每个 workspace 至少应维护：

- `lease_count`
    
- `lease_holders`
    
- `lease_type`
    
- `lease_expires_at`
    
- `demolish_requested`
    
- `demolish_block_reason`
    

#### 21.4.2 必须申请租约的动作

以下动作在执行前必须获得 workspace 活跃租约：

- preprocess execution
    
- analysis execution
    
- runtime assertion scan
    
- result packing
    
- artifact promotion
    
- log flush / finalization
    
- archive 操作
    

#### 21.4.3 demolish 前置条件

只有当以下条件同时满足时，才允许执行真正的 workspace 销毁：

- `lease_count == 0`
    
- 当前无 promotion 进行中
    
- 当前无 result packing 进行中
    
- 当前无 log flush / finalization 进行中
    
- 控制面已完成终局策略裁决
    

若收到 demolish 指令时仍存在活跃租约，执行面必须：

- 返回 `409 CONFLICT`
    
- 或进入 `DEMOLISH_PENDING`
    
- 或放入 delayed tombstone queue
    

禁止暴力强删仍在使用中的物理目录。

#### 21.4.4 租约与 revision 绑定

活跃租约必须绑定到具体的 `preprocess_run_revision_id` 或 `run_revision_id`，不能仅绑定到一个裸 workspace 路径。  
否则会在多轮次复用路径时引发租约污染或误删。

---

### 21.5 建议补充的执行元数据字段

为了使上述四条约束在工程实现中可执行，建议执行面为 job 和 workspace 补充以下字段：

#### Job 侧建议新增

- `runtime_unit_id`
    
- `runtime_unit_type`：`PGID` / `CONTAINER` / `POD`
    
- `cancel_mode_requested`
    
- `cancel_escalated_to_hard_kill`
    
- `physical_termination_confirmed`
    
- `heartbeat_lost_at`
    

#### Workspace 侧建议新增

- `lease_count`
    
- `lease_holders`
    
- `demolish_requested_at`
    
- `demolish_block_reason`
    
- `promotion_in_progress`
    
- `finalization_in_progress`
    

#### Result Bundle 侧建议新增

- `inline_allowed`
    
- `artifact_pointer_only`
    
- `locality`
    
- `promotion_candidate`
    

#### Assertion 侧建议新增

- `evaluator_id`
    
- `evaluator_version`
    
- `assertion_execution_mode`
    
- `assertion_result_ref`
    

---

### 21.6 联调与压测必须覆盖的场景

为了确保这些约束真正落地，研发与运维联调时必须覆盖以下场景：

第一，analysis job 在 C/C++ 重计算过程中被取消，确认进程组 / 容器实例被真正销毁，而不只是状态被改写。

第二，大结果文件存在时，`collect_result_bundle` 仍然只返回指针清单，不发生控制面或网关内存暴涨。

第三，新增一种断言逻辑时，只新增 evaluator，不修改执行面核心守护进程代码。

第四，promotion 过程中提前到达 demolish 指令，执行面返回冲突或延迟删除，而不是立即删目录。

第五，workspace 上存在多个活跃租约时，任何清理与销毁动作都不会绕过租约保护。

---

### 21.7 结论

执行面是最接近操作系统、文件系统、对象存储和原生运行库的一层，因此它的“正确性”不能只停留在状态机和契约层，还必须落实到：

- 取消是否真的杀死底层运行单元
    
- 结果是否真的不经主链路搬运大文件
    
- 断言是否真的通过可扩展 evaluator 执行
    
- workspace 是否真的不会在并发使用中被误删
    

这四条约束写入之后，执行面才真正从“逻辑上可运行”升级为“物理上可承载、可停止、可清理、可扩展”的运行时承载层。