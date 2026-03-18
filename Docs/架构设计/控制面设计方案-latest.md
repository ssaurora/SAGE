
## 1. 文档定位

控制面是系统的唯一治理主机，负责把认知面、规划面、能力面、执行面与数据面的异构行为组织为一个受状态机约束、可冻结、可恢复、可审计、可回放的任务生命周期。控制面持有全局主状态机，负责跨面调度、修复分流、执行前闸门、Analysis Manifest 冻结、作业提交、结果编排、恢复与重入；认知面与规划面只持有子阶段与局部 revision，不得替代主任务状态。

控制面不承担 GIS 领域语义理解、不承担模型选择知识、不直接构造参数语义、不负责编译执行图，也不解释生态学结果。它解决的问题不是“如何理解任务”或“如何生成执行图”，而是“什么时候允许这些结果生效、何时必须回退、何时可以冻结、何时可以执行、失败后如何收敛”。

---

## 2. 设计目标

控制面必须同时满足以下目标：

第一，持有唯一主状态机，保证认知面与规划面的局部状态不会与任务全局状态发生竞争。

第二，集中掌握跨面调度权。认知面与规划面采用交替协作方式运行，修复编排权归控制面，二者不得直接递归互调。

第三，执行前必须通过前闸门。任何参数草案、图编排结果和执行准备对象，都必须先经过结构化校验、版本校验、确认约束和冻结流程，再进入执行层。

第四，恢复必须建立在系统事实而非用户陈述之上。控制面在 `/resume` 时必须先完成增量输入合并、最小元数据就绪、失效范围分析和状态版本校验，才能恢复进入认知链。

第五，执行生命周期必须可观测、可中断、可清理。控制面不仅要负责作业提交与状态跟踪，还必须负责预处理轮次治理、队列重评估、workspace 生命周期、工件晋升与终局销毁。整体系统的“可运行”不只意味着能发起任务，还意味着能在长时态、网络抖动和资源变化下保持一致性。

---

## 3. 职责边界

### 3.1 控制面负责什么

控制面负责统一 API 接入、鉴权与 RBAC、会话管理、任务生命周期管理、主状态机维护、状态聚合、SSE/事件推流、审计落库、人工确认入口、恢复与重入、执行前冻结、作业提交、结果编排，以及跨认知面、规划面、能力面、执行面、数据面的统一治理。总体设计已明确控制面是全系统唯一主状态机持有者，也是修复编排与跨面调度主机。

### 3.2 控制面不负责什么

控制面不负责 GIS 领域语义理解、Skill 路由知识、模板选择、模板渲染、参数草案构造、执行图编译、图级静态校验、预处理片段插入、图重写、默认物理文件探测、生态结果解释等认知面或规划面的职责。认知面不持有主状态机，规划面也不得以 PlanningState 替代任务主状态。

---

## 4. 核心设计原则

### 4.1 唯一主权原则

控制面是任务状态的唯一事实源。认知面的 Checkpoint、规划面的 revision、执行面的 job 状态和数据面的目录事实，都是受控制面承认和组织的派生状态。任何未经控制面确认的未来态，不得成为后续推进依据。

### 4.2 结构化优先原则

所有进入执行链路的关键中间产物必须是强约束结构化对象，包括任务归一化结果、Skill 路由结果、槽位绑定、参数草案、修复建议、执行图摘要、运行断言与解释结果。控制面不得以自由文本直接驱动执行。

### 4.3 校验优先原则

任何参数草案、图编排对象和执行准备对象，都必须先通过 validate 接口与闸门规则，再进入冻结与执行。软校验与硬校验必须区分；依赖虚拟资产或逻辑中间产物得出的通过结论，必须在执行期以运行时断言再次核验。

### 4.4 治理优先原则

系统优先保证不乱选模型、不乱配参数、不乱解释结果、不在校验失败后继续执行。控制面必须掌握状态机、审计、权限、前端交互、事件流和执行前闸门，不能退化为简单的请求转发器。

### 4.5 生命周期闭环原则

预处理、正式执行、挂起恢复、排队等待、结果持久化和临时空间销毁，都必须纳入控制面的统一生命周期。系统的终局不以“任务显示完成”为准，而以“结果已晋升、审计已留存、工作区已治理”作为闭环条件。

---

## 5. 总体工作方式

控制面围绕认知面与规划面的交替协作链组织任务运行。整体工作方式遵循如下主流程：

```text
Create Task
  -> INIT
  -> COGNITION_A
     -> Goal Parse
     -> Skill Route
  -> PLANNING_1
     -> Template Select
     -> Role Declaration
  -> COGNITION_B
     -> Context Enrich
     -> Slot Binding
     -> Args Draft
     -> Decision Summary Candidate
     -> Manifest Payload Candidate
  -> PLANNING_2
     -> Materialized Compilation
     -> Static Validate
     -> Assertion Build
     -> Graph Digest
     -> Planning Summary
  -> VALIDATION_GATE
  -> REPAIR_DISPATCH (if needed)
     -> Cognition Repair / Planning Repair / Waiting / Confirmation
  -> PRE_PROCESSING (if needed)
  -> RE_VALIDATE
  -> MANIFEST_FREEZE
  -> FEASIBILITY_GATE
  -> QUEUED (if soft limit)
  -> EXECUTION_SUBMIT
  -> EXECUTING
  -> RESULT_PROCESSING
  -> ARTIFACT_PROMOTING
  -> EXPLAINING
  -> TERMINAL_CLEANUP
  -> COMPLETED
```

上述链路与认知面、规划面的契约一致：模板角色先声明，参数绑定后发生；修复编排权归控制面；规划面只输出 `planning_manifest_payload`，认知面只输出 `manifest_payload_candidate`，最终冻结由控制面执行。

---

## 6. 主状态机设计

### 6.1 主状态枚举

控制面主状态建议至少包括：

- `INIT`
    
- `RUNNING`
    
- `WAITING_USER`
    
- `WAITING_SYSTEM`
    
- `RESUMING`
    
- `PENDING_CONFIRMATION`
    
- `PRE_PROCESSING`
    
- `READY_TO_FREEZE`
    
- `FROZEN`
    
- `QUEUED`
    
- `SUBMITTED`
    
- `EXECUTING`
    
- `RESULT_PROCESSING`
    
- `ARTIFACT_PROMOTING`
    
- `EXPLAINING`
    
- `TERMINAL_CLEANUP`
    
- `COMPLETED`
    
- `FAILED`
    
- `CANCELLED`
    
- `STATE_CORRUPTED`
    

其中，`RESUMING` 用于表示恢复事务尚未完成承认；`PRE_PROCESSING` 用于表示冻结前的长耗时前置处理；`QUEUED` 用于表示可执行但暂时不可调度；`ARTIFACT_PROMOTING` 用于表示结果已产生但尚未完成持久化晋升；`STATE_CORRUPTED` 用于表示控制面与下游派生状态已失去一致性，任务进入受限修复态。

### 6.2 阶段与子阶段

控制面除 `main_state` 外，还维护 `phase` 与 `substage`。认知面与规划面的子阶段事件必须上报控制面，但不得替代主状态。认知面可包含 `GOAL_PARSE`、`SKILL_ROUTE`、`CONTEXT_ENRICH`、`ARGS_DRAFT`、`REPAIR_INTENT`、`DECISION_SUMMARY`、`EXPLAIN` 等子阶段；规划面可包含 `TEMPLATE_SELECT`、`LOGICAL_COMPILE`、`MATERIALIZE`、`STATIC_VALIDATE`、`GRAPH_REWRITE`、`ASSERTION_BUILD`、`GRAPH_DIGEST`、`PLANNING_SUMMARY` 等子阶段。

### 6.3 状态转移约束

控制面只允许在规则明确定义的前提下推进状态。未通过验证的对象不得进入 `READY_TO_FREEZE`；未冻结 Manifest 的任务不得进入正式执行；未完成结果晋升与终局清理的任务，不应被视为生命周期闭环完成。状态机的目标不是展示流程图，而是给跨面协作设立明确的停止条件与推进条件。

---

## 7. 控制态对象设计

### 7.1 TaskState

控制面应维护统一的 `TaskState`，至少包括：

- 基础标识：`task_id`、`conversation_id`、`request_id`、`user_id`、`workspace_id`
    
- 状态字段：`main_state`、`phase`、`substage`、`state_version`
    
- 协作版本：`planning_revision`、`checkpoint_version`
    
- 资源版本：`inventory_version`、`blacklist_version`
    
- 快照字段：`available_asset_snapshot_ref`、`snapshot_signature`
    
- 冻结字段：`manifest_id`、`manifest_version`
    
- 执行字段：`job_id`、`job_status`
    
- 队列字段：`queue_entered_at`、`queue_ttl`、`required_resource_profile`
    
- 终局字段：`promotion_status`、`workspace_cleanup_status`
    
- 审计字段：`audit_trace_id`、`latest_error_code`、`risk_flags`
    

这类对象是主治理载体，而不是简单的任务表行。它必须能承载恢复、重试、回放、调度、冻结与清理等复杂治理需求。

### 7.2 版本解耦

控制面应显式区分以下版本域：

- `state_version`：主状态机版本
    
- `planning_revision`：规划图与编译结果版本
    
- `checkpoint_version`：认知面检查点版本
    
- `preprocess_run_revision_id`：预处理执行轮次
    
- `run_revision_id`：正式执行轮次
    

不同 revision 解决不同层面的重放与冲突问题，不得混用单一 revision 覆盖所有语义。

---

## 8. 跨面调度设计

### 8.1 调度原则

控制面不得直接依赖认知面或规划面的内部节点实现，而应以阶段级协议进行调度。推荐的调度入口包括：

- `run_cognition_pass_a`
    
- `run_planning_pass_1`
    
- `run_cognition_pass_b`
    
- `run_planning_pass_2`
    
- `run_validation_gate`
    
- `dispatch_repair`
    
- `run_preprocess`
    
- `freeze_manifest`
    
- `submit_job`
    
- `collect_results`
    
- `run_explanation`
    

这种分层方式可避免控制面对具体 prompt 和内部图结构形成硬耦合。

### 8.2 与认知面的契约

控制面向认知面提供当前任务上下文、达到 `MIN_READY` 的 inventory、`inventory_merge_result`、规划面 Pass 1 返回的最小视图、校验结果与错误码、恢复提示与状态版本。认知面返回 `goal_parse`、`skill_route`、`slot_bindings`、`args_draft`、`repair_proposal`、`decision_summary`、`manifest_payload_candidate` 与 `final_explanation`。控制面负责承认、冻结和调度，不由认知面越权完成执行控制。

### 8.3 与规划面的契约

控制面向规划面下发 `available_asset_snapshot`、`blacklist_context`、来自认知面的结构化输入与策略标志；规划面返回 `materialized_execution_graph`、`execution_graph_digest`、`static_validation_result`、`runtime_assertions`、`computation_load_metrics`、`planning_summary`、`planning_manifest_payload`、`rewrite_registry` 和 `planning_revision`。规划面只提供 payload，不冻结 Manifest。

---

## 9. 校验与修复分流

### 9.1 校验门

控制面必须统一调度 validate 类能力。参数草案、绑定结果、图结构、工具授权、运行约束和资源画像都必须进入校验链。规划面和认知面都可以提供候选对象，但放行权只能归控制面。

### 9.2 Repair Dispatcher

Repair Dispatcher 是控制面的核心中枢。它必须依据结构化错误码进行分流：

- 参数或绑定类错误回认知面
    
- 图结构、预处理链和图重写类错误回规划面
    
- 元数据未就绪类进入 `WAITING_SYSTEM` 或 `WAITING_USER`
    
- 科学降级类进入 `PENDING_CONFIRMATION`
    
- 致命错误进入失败治理
    

认知面的 `repair_proposal` 只表达修复意图，不直接修改执行图；图结构级修复必须交由控制面调度规划面。

### 9.3 修复预算与熔断

控制面必须维护全局修复轮次预算、同类错误重复上限、模板协商上限、图重写上限与等待超时。认知面和规划面虽然各自具有预算与熔断机制，但最终分支终止与全局回退必须由控制面裁决。

---

## 10. 恢复与重入设计

### 10.1 恢复前提

当用户上传新数据并请求恢复时，控制面不得立即把任务送回认知链。必须先让这些新资产进入 `MIN_READY` 状态，即最小元数据已同步入库，再完成输入合并、元数据最小就绪、失效范围分析和状态版本校验，最后才允许恢复。用户“我已经上传了”的表述只能作为意图线索，不构成系统事实。

### 10.2 恢复事务语义

恢复不是普通 API 调用，而是带承认边界的事务过程。控制面在恢复时进入 `RESUMING`，执行以下四步：

第一步，Prepare：生成 `inventory_merge_candidate`、`snapshot_signature_candidate` 和 `resume_request_id`，但不修改正式任务事实。  
第二步，Ack Request：调用认知面恢复入口，并要求认知面仅在 Checkpoint 已落盘后返回 `RESUME_ACKED`。  
第三步，Commit：控制面收到有效 Ack 后，才提升 `inventory_version`、更新正式快照、推进 `state_version`，并重入后续阶段。  
第四步，Rollback：若未收到 Ack、发生超时、网络中断或版本冲突，则丢弃候选合并结果，不得进入伪恢复态。

### 10.3 主权与强制对齐

控制面是唯一事实源。任何未经控制面 Commit 承认的认知面 Checkpoint，都不得作为后续推进依据。如果控制面判定恢复失败，但认知面可能已写入未承认的未来态，控制面必须下发 `FORCE_REVERT_CHECKPOINT`，要求认知面回退到最近一次被控制面承认的 Checkpoint。若对齐失败或认知面失联，则任务进入 `STATE_CORRUPTED`，在状态修复完成之前禁止发起新的恢复、重新规划和执行提交。

### 10.4 重入点判定

控制面应根据失效范围决定重入点：若仅用户说明变化且结构未变，可回 `COGNITION_B`；若新增资产改变模板适配性，应回 `PLANNING_1`；若仅图级对象失效，可回 `PLANNING_2`。恢复的关键不是“接着跑”，而是基于增量事实判断哪些对象仍可复用、哪些对象必须失效重算。

---

## 11. 预处理治理

### 11.1 预处理状态的独立性

当规划面输出可插入的 preprocess 片段，且控制面决定调用执行面运行预处理子图时，任务主状态必须切换为 `PRE_PROCESSING`。预处理既不属于认知态，也不属于正式执行态，因为此时 Manifest 尚未冻结，但系统已进入真实的长耗时资源占用阶段。

### 11.2 预处理执行轮次化

所有预处理作业必须分配独立的 `preprocess_run_revision_id`、`workspace_id`、`job_id` 和 `cleanup_policy`。预处理虽然发生在正式执行之前，但在资源生命周期治理上必须与正式执行等价对待。否则，冻结前的临时文件将失去归属，成为不可治理的残留物。

### 11.3 预处理异常流转

预处理成功后，控制面更新预处理产物元数据与 `available_asset_snapshot`，再回流到 `COGNITION_B` 或 `PLANNING_2`。预处理失败时，控制面必须收集失败原因并生成结构化交互对象；若错误属于用户可修正问题，则进入 `WAITING_USER`，若属于系统不可恢复问题，则进入 `FAILED`。若用户在预处理中取消任务，控制面必须中止执行、回收工作区，并进入取消治理。

### 11.4 预处理工作区治理

预处理产生的 workspace 与临时工件，受与正式执行相同的 cleanup、rotate、archive 策略约束。凡进入新的预处理轮次，不得默认复用上一次失败轮次的 workspace。若不存在经过验证的可复用中间产物白名单，则应默认分配新的预处理 workspace，并对旧 workspace 执行清理或只读归档。

---

## 12. Manifest 冻结与执行前闸门

### 12.1 冻结职责

认知面只提供 `manifest_payload_candidate`，规划面只提供 `planning_manifest_payload`。真正将这些候选对象整合为正式 `AnalysisManifest` 的，只能是控制面。冻结标志着任务从“候选执行对象”进入“正式执行对象”状态，执行面只能消费冻结后的 Manifest。

### 12.2 冻结输入

控制面在冻结时应整合以下对象：

- 来自认知面的 `goal_parse`、`skill_route`、`slot_bindings`、`args_draft`、`decision_summary`、`manifest_payload_candidate`
    
- 来自规划面的 `selected_template`、`template_version`、`execution_graph_digest`、`runtime_assertions`、`computation_load_metrics`、`planning_summary`、`planning_manifest_payload`、`rewrite_registry`、`planning_revision`
    
- 来自控制面的确认记录、风险标记、资源校验结果、快照版本和策略标志
    

### 12.3 冻结条件

仅当以下条件同时满足时，控制面才允许进入 `FROZEN`：

- 参数与绑定校验通过
    
- 图级静态校验通过
    
- 预处理已完成或无需预处理
    
- 所有必要运行时断言已生成
    
- 所有必需资产达到 `MIN_READY`
    
- 所有待确认项已经处理
    
- 当前状态版本未失效
    
- 不存在未解决的修复分支
    

### 12.4 执行前闸门

执行前闸门是控制面对执行面放行的最终控制点。其判断结果必须至少分为三类：

- `BLOCK`：绝对禁止执行
    
- `CONFIRM`：需人工确认后继续
    
- `WARN`：允许执行，但必须进入审计与解释链
    

控制面必须把执行前闸门与 Manifest 冻结绑定。未冻结的对象不得进入正式执行；冻结后的对象若关键输入发生变化，旧 Manifest 必须失效并回到上游阶段重建。

---

## 13. 资源可执行性与排队治理

### 13.1 双层资源判定

控制面对资源问题的判断必须分为两层：

第一层是 **Feasibility Gate**，判断任务在系统物理能力边界内是否原则上可执行。  
第二层是 **Scheduling Gate**，判断任务在当前时刻是否可被调度。

这两层不可混淆。

### 13.2 Hard Limit 与 Soft Limit

若任务的资源需求超过系统任何可用执行节点的物理上限，例如最大内存、最大磁盘、最大 TTL、特定工具环境缺失等，则属于 `Hard Limit`。此时控制面可触发降级、裁剪、模板协商、分块建议或人工确认。

若任务在原则上可执行，但当前只是因资源繁忙、队列拥堵、节点暂时占满而无法立即运行，则属于 `Soft Limit`。此时控制面不得修改 Manifest，不得因瞬时拥堵改变分析范围或执行图结构，而应将任务置于 `QUEUED`。

### 13.3 队列态治理

进入 `QUEUED` 的任务必须记录至少以下字段：

- `queue_entered_at`
    
- `queue_ttl`
    
- `last_feasibility_check_at`
    
- `feasibility_basis_version`
    
- `required_resource_profile`
    

其中 `required_resource_profile` 应覆盖预估内存、CPU/GPU、磁盘、执行时长、特殊节点要求等关键信息。

### 13.4 队列重评估机制

`QUEUED` 不是静态等待态，而是待重评估态。控制面必须在以下情况下重新过一次 `Feasibility Gate`：

- 周期性时间触发
    
- 底层集群拓扑变化
    
- 特定执行环境变化
    
- 配额或策略变化
    
- 高规格节点上下线
    

若重评估后仍为 `Soft Limit`，任务继续排队；若已降级为 `Hard Limit`，必须立刻退出队列，转入 `PENDING_CONFIRMATION`、`WAITING_USER` 或 `FAILED`。任何任务都不得无限期停留在 `QUEUED` 而不被重新裁决。

### 13.5 科学语义保护

控制面不得因瞬时资源拥堵而擅自改变用户分析范围、模板选择或执行图结构。只有在物理不可执行、平台策略硬上限触发或经用户确认的情况下，才允许进入降级、裁剪、分块或替代策略流程。

---

## 14. Job Control 与执行治理

### 14.1 作业提交职责

控制面是 Job Control 的唯一上层协调者，负责：

- 构造并提交作业请求
    
- 记录 `job_id`
    
- 跟踪作业状态
    
- 处理中断、取消和心跳丢失
    
- 拉取结果 bundle
    
- 组织失败治理和后处理
    

执行面负责运行，不负责全局任务治理。

### 14.2 执行状态映射

执行面状态可映射为控制面状态：

- `QUEUED` → 控制面 `QUEUED`
    
- `ACCEPTED` / `DISPATCHED` → 控制面 `SUBMITTED`
    
- `RUNNING` → 控制面 `EXECUTING`
    
- `SUCCEEDED` → 控制面 `RESULT_PROCESSING`
    
- `FAILED` → 控制面进入失败治理
    
- `CANCELLED` → 控制面 `CANCELLED`
    

### 14.3 运行时断言失败

当执行面返回 `ASSERTION_FAILED` 或其他运行时断言失败时，控制面不得只记录错误并简单回退。它必须：

- 记录失败节点、失败断言、上下文与 run revision
    
- 判定该失败属于用户可修正、规划可修正还是系统致命错误
    
- 在进入 `WAITING_USER`、`REPAIR` 或 `FAILED` 之前先裁决工作区策略
    
- 明确本轮次中间产物是否可保留、清理或归档
    

---

## 15. Workspace 生命周期治理

### 15.1 Workspace 的角色

Workspace 只能是执行轮次的临时草稿空间，不得承担长期结果存储职责。它用于保存运行时中间产物、临时切片、预处理文件和节点级输出，但这些内容不能被默认为永久资产。

### 15.2 执行轮次与工作区绑定

每次正式执行必须绑定独立的 `run_revision_id` 和对应 workspace。每次预处理执行必须绑定独立的 `preprocess_run_revision_id` 和对应 workspace。不同 revision 的执行，不得默认共享同一个 workspace。

### 15.3 失败态清理与轮换

当执行轮次因断言失败、运行失败、取消或挂起修复而中断时，控制面必须先裁决 workspace 策略，再进入下一状态。策略至少包括：

- 清理非持久化中间产物
    
- 旧 workspace 只读归档
    
- 分配新的 workspace 给后续 revision
    
- 保留有限审计窗口后再销毁
    

若无显式白名单证明某些中间产物可安全复用，则下一轮执行应默认使用新的 workspace。

### 15.4 成功态治理

成功任务同样不能忽略 workspace 问题。任务一旦进入结果处理与解释阶段，控制面必须识别哪些文件属于：

- 最终成果文件
    
- 审计必需工件
    
- 可选保留派生物
    
- 纯临时垃圾
    

在最终成果未晋升前，workspace 不得销毁；在晋升完成后，workspace 不能长期保留。

---

## 16. 工件晋升与终局销毁

### 16.1 Artifact Promotion

当任务完成正式执行并进入 `RESULT_PROCESSING` 后，控制面必须将需要长期保存的 Final Outputs 和审计保留工件晋升至持久化对象存储。晋升结果至少应记录：

- `artifact_id`
    
- `artifact_class`
    
- `storage_uri`
    
- `checksum`
    
- `source_run_revision_id`
    
- `promotion_status`
    

### 16.2 ARTIFACT_PROMOTING 状态

如果对象存储上传、校验或归档是一个显著耗时过程，控制面必须进入 `ARTIFACT_PROMOTING`。只有当工件晋升成功后，任务才允许继续进入解释与终局清理。

### 16.3 Terminal Demolition

当满足以下条件时：

- Final Outputs 已成功晋升
    
- 审计保留项已归档或标记
    
- 不存在管理员保留要求
    

控制面必须下发 `WORKSPACE_DEMOLISH`，销毁该执行轮次的临时 workspace。工作区销毁是生命周期闭环的一部分，而不是后台可有可无的清洁动作。

### 16.4 保留策略

控制面应支持明确的 `workspace_retention_policy`，例如：

- `DESTROY_IMMEDIATELY`
    
- `DESTROY_AFTER_RETENTION_WINDOW`
    
- `ARCHIVE_READONLY`
    
- `ADMIN_HOLD`
    

默认情况下，成功任务应尽快销毁临时 workspace，失败任务则依据审计策略决定短期保留或销毁。

---

## 17. 人工确认与人工介入

### 17.1 必须确认的场景

控制面应在以下情形进入 `PENDING_CONFIRMATION`：

- 模板降级改变科学假设
    
- 分析范围需裁剪或自动分块
    
- 使用替代模板或替代数据源
    
- 资源代价超阈值
    
- 输出语义与用户原始意图显著偏离
    
- 某些高风险断言仅能基于不完备事实成立
    

### 17.2 确认对象结构

建议定义统一 `ConfirmationRequest`，至少包含：

- `confirmation_id`
    
- `task_id`
    
- `type`
    
- `severity`
    
- `reason_code`
    
- `impact_summary`
    
- `options`
    
- `expires_at`
    

确认动作必须写回结构化对象，而不能直接通过前端临时按钮修改内部状态。

### 17.3 人工接管边界

人工可参与模板选择、降级确认、绑定选择和高成本任务放行，但不能绕过控制面的校验、冻结与审计机制。人工介入必须被结构化记录，并成为后续执行与审计的一部分。

---

## 18. 事件流与观测设计

### 18.1 统一事件模型

控制面必须汇聚认知面、规划面、执行面和数据面的事件，并组织为统一事件流。前端看到的是治理后的统一事件，而不是各面原始内部细节。

### 18.2 事件类型

建议至少定义：

- `TASK_CREATED`
    
- `STATE_CHANGED`
    
- `PHASE_CHANGED`
    
- `SUBSTAGE_PROGRESS`
    
- `WAITING_ENTERED`
    
- `CONFIRMATION_REQUIRED`
    
- `RESUME_PREPARED`
    
- `RESUME_ACKED`
    
- `RESUME_ROLLED_BACK`
    
- `PREPROCESS_STARTED`
    
- `PREPROCESS_FAILED`
    
- `MANIFEST_FROZEN`
    
- `TASK_QUEUED`
    
- `JOB_SUBMITTED`
    
- `JOB_HEARTBEAT_MISSED`
    
- `JOB_COMPLETED`
    
- `ARTIFACT_PROMOTION_STARTED`
    
- `ARTIFACT_PROMOTION_FINISHED`
    
- `WORKSPACE_CLEANUP_STARTED`
    
- `WORKSPACE_DEMOLISHED`
    
- `TASK_FAILED`
    
- `TASK_CANCELLED`
    
- `TASK_CORRUPTED`
    

### 18.3 事件持久化与节流

重要事件必须持久化，支持任务回放与审计；高频事件必须节流并聚合。事件模型不只是为了前端进度展示，更是为了让复杂任务的生命周期在失败后可诊断、在审计时可还原。

---

## 19. 审计模型设计

### 19.1 审计目标

控制面必须实现从用户请求到最终解释的全链审计，确保每个关键对象都能追溯到其生成阶段、版本边界和承认关系。

### 19.2 审计分层

建议至少包括以下审计域：

- **决策审计**：`goal_parse`、`skill_route`、`decision_summary`
    
- **规划审计**：模板选择、`planning_revision`、图摘要、重写记录
    
- **修复审计**：错误码、repair route、轮次预算、是否熔断
    
- **冻结审计**：Manifest 输入集合、冻结时间、确认记录
    
- **执行审计**：`job_id`、run revision、运行轨迹、失败节点
    
- **恢复审计**：`resume_request_id`、Ack、Commit、Rollback、强制回退记录
    
- **工件审计**：promotion 记录、存储 URI、workspace 销毁记录
    
- **解释审计**：最终解释文本引用、事实来源对象、风险提示
    

### 19.3 审计要求

所有 revision 必须可追溯；所有冻结对象必须可复现；所有失败点必须能定位到层面、错误码和轮次；所有恢复对齐动作都必须有记录。审计不是附属日志，而是控制面治理能力的组成部分。

---

## 20. 并发、一致性与冲突控制

### 20.1 幂等性

控制面必须基于 `request_id` 和 `resume_request_id` 实现幂等。重复创建任务、重复恢复请求、重复确认请求不得造成多份状态推进。

### 20.2 乐观锁与版本冲突

所有状态修改必须携带 `expected_state_version`。若版本不一致，控制面必须拒绝写入并触发冲突处理。恢复、冻结、提交、取消与确认都必须受版本约束。

### 20.3 提交/取消/恢复竞态

控制面必须避免以下竞态：

- 已冻结但取消尚未生效时重复提交
    
- 正在恢复时再次发起恢复
    
- 工件晋升未完成时提前销毁 workspace
    
- 状态损坏任务被误认为可继续执行
    

### 20.4 状态损坏处理

`STATE_CORRUPTED` 不是普通失败态，而是高危受限态。进入该状态后，除管理员修复、对齐确认和终止外，禁止继续接受一般用户操作。该状态的存在是为了防止系统在不一致条件下继续推进，进一步放大损害。

---

## 21. 对外 API 设计建议

控制面建议至少暴露以下接口：

### 21.1 任务生命周期

- `POST /tasks`
    
- `GET /tasks/{taskId}`
    
- `POST /tasks/{taskId}/resume`
    
- `POST /tasks/{taskId}/cancel`
    

### 21.2 事件与审计

- `GET /tasks/{taskId}/events`
    
- `GET /tasks/{taskId}/stream`
    
- `GET /tasks/{taskId}/audit`
    

### 21.3 人工确认

- `GET /tasks/{taskId}/confirmations`
    
- `POST /tasks/{taskId}/confirmations/{confirmationId}`
    

### 21.4 结果与工件

- `GET /tasks/{taskId}/result`
    
- `GET /tasks/{taskId}/artifacts`
    
- `GET /tasks/{taskId}/manifest`
    

### 21.5 管理与修复

- `POST /tasks/{taskId}/force-revert-checkpoint`
    
- `POST /tasks/{taskId}/retry-queue-evaluation`
    
- `POST /tasks/{taskId}/workspace-cleanup`
    
- `POST /tasks/{taskId}/terminate`
    

这些接口应全部围绕控制面对象组织，而不直接暴露认知面、规划面或执行面的内部实现细节。

---

## 22. 最小工程落地顺序

控制面的工程落地建议分五步进行。

第一步，建立治理骨架：  
完成 `TaskState`、主状态机、`state_version`、基础事件流、幂等与冲突控制。

第二步，接入跨面编排：  
实现认知面 Pass A/B、规划面 Pass 1/2、校验门、Repair Dispatcher。

第三步，补齐恢复与等待：  
实现 `MIN_READY`、`/resume`、恢复事务、Ack/Commit/Rollback、状态损坏治理。

第四步，补齐预处理与正式执行：  
实现 `PRE_PROCESSING`、Feasibility Gate、`QUEUED`、Job Control、workspace 生命周期。

第五步，补齐终局闭环：  
实现 Artifact Promotion、结果编排、解释协调、终局销毁、全链审计与事件回放。

---

## 23. 结论

控制面的本质不是请求入口，也不是任务转发层，而是全系统唯一的治理闭环。它把认知面的不确定性输出、规划面的确定性编译、能力面的标准化能力、执行面的异步运行和数据面的事实状态组织在同一个受控状态机之下。

一套可落地的控制面，至少必须满足以下条件：

- 持有唯一主状态机与事实主权
    
- 掌握跨面调度与修复分流权
    
- 以结构化校验和冻结规则阻止失控执行
    
- 以恢复事务和强制对齐机制维护跨面一致性
    
- 以预处理轮次化、队列重评估和工作区生命周期治理支撑长时运行
    
- 以工件晋升与终局销毁保证系统不会因成功或失败任务而积累不可治理残留
    
- 以统一事件流和全链审计确保系统可观测、可回放、可复盘
    

只有在这些条件全部成立时，控制面才真正构成整个系统的稳定中枢，而不只是一个把多个模块勉强串联起来的协调层。

## 24. 控制面状态机表

以下状态机表定义控制面主状态的正式流转规则。表中“动作”均指控制面侧必须执行的治理动作，而非下游组件的内部实现。

### 24.1 主状态转移表

|当前状态|触发事件|前置条件|控制面动作|下一状态|失败/异常分支|审计记录点|
|---|---|---|---|---|---|---|
|`INIT`|`TASK_CREATED`|请求通过鉴权与基础校验|创建 `TaskState`，初始化 `state_version`，生成首个事件流上下文|`RUNNING`|创建失败转 `FAILED`|任务创建审计|
|`RUNNING`|`ENTER_COGNITION_A`|任务已初始化|调度 `run_cognition_pass_a`|`RUNNING`|调度失败转失败治理|调度审计|
|`RUNNING`|`ENTER_PLANNING_1`|已获得 `goal_parse` 与 `skill_route`|调度 `run_planning_pass_1`|`RUNNING`|调度失败转失败治理|规划调度审计|
|`RUNNING`|`ENTER_COGNITION_B`|已完成模板角色声明|调度 `run_cognition_pass_b`|`RUNNING`|调度失败转失败治理|认知调度审计|
|`RUNNING`|`ENTER_PLANNING_2`|已获得绑定与参数草案|调度 `run_planning_pass_2`|`RUNNING`|调度失败转失败治理|规划调度审计|
|`RUNNING`|`VALIDATION_REQUIRED`|已具备候选执行对象|调度 `run_validation_gate`|`RUNNING`|校验系统异常转失败治理|校验审计|
|`RUNNING`|`REPAIR_REQUIRED`|校验返回结构化错误码|调度 `dispatch_repair`|`RUNNING` / `WAITING_USER` / `PENDING_CONFIRMATION`|分流异常转失败治理|修复分流审计|
|`RUNNING`|`PREPROCESS_REQUIRED`|规划面输出可执行预处理片段|生成 `preprocess_run_revision_id`，分配 workspace，提交预处理作业|`PRE_PROCESSING`|提交失败转失败治理|预处理启动审计|
|`RUNNING`|`READY_FOR_FREEZE`|全部校验通过且无待确认项|冻结 `AnalysisManifest`|`READY_TO_FREEZE` → `FROZEN`|冻结失败转失败治理|冻结审计|
|`FROZEN`|`SOFT_LIMIT_DETECTED`|任务原则上可执行但暂不可调度|记录资源画像与队列元信息|`QUEUED`|队列写入失败转失败治理|队列入列审计|
|`FROZEN`|`EXECUTION_ALLOWED`|通过可执行性与调度判定|提交正式作业，记录 `run_revision_id` 与 `job_id`|`SUBMITTED`|提交失败转失败治理|作业提交审计|
|`SUBMITTED`|`JOB_ACCEPTED`|执行面接受作业|更新状态与心跳锚点|`EXECUTING`|接受超时转失败治理|作业接受审计|
|`EXECUTING`|`JOB_SUCCEEDED`|结果 bundle 可读取|拉取结果，进入结果处理|`RESULT_PROCESSING`|拉取失败转失败治理|执行完成审计|
|`EXECUTING`|`ASSERTION_FAILED`|执行面返回断言失败|记录失败节点，裁决 workspace 策略|`WAITING_USER` / `RUNNING` / `FAILED`|清理失败可转 `STATE_CORRUPTED`|断言失败审计|
|`EXECUTING`|`JOB_FAILED`|执行面返回失败|归类错误并治理|`FAILED` / `RUNNING` / `WAITING_USER`|无法归类则转 `FAILED`|执行失败审计|
|`RESULT_PROCESSING`|`PROMOTION_REQUIRED`|已识别 final outputs|启动工件晋升|`ARTIFACT_PROMOTING`|晋升准备失败转失败治理|结果编排审计|
|`ARTIFACT_PROMOTING`|`PROMOTION_SUCCEEDED`|Final outputs 已持久化|写入工件索引，允许进入解释|`EXPLAINING`|-|工件晋升审计|
|`ARTIFACT_PROMOTING`|`PROMOTION_FAILED`|上传/校验失败|重试或进入失败治理|`FAILED` / `ARTIFACT_PROMOTING`|多次失败转失败治理|工件失败审计|
|`EXPLAINING`|`EXPLANATION_READY`|解释生成完成|汇总最终结果视图|`TERMINAL_CLEANUP`|解释失败可降级为结构化结果输出|解释审计|
|`TERMINAL_CLEANUP`|`WORKSPACE_DEMOLISHED`|已完成 promotion 与保留策略判定|销毁或归档 workspace|`COMPLETED`|清理失败转 `FAILED` 或 `STATE_CORRUPTED`|终局清理审计|
|任意非终态|`USER_CANCELLED`|用户有权限取消|发取消指令，裁决 workspace 策略|`CANCELLED`|取消失败进入失败治理|取消审计|
|任意状态|`STATE_DIVERGED`|控制面与派生状态不一致|冻结一般操作，进入受限修复|`STATE_CORRUPTED`|-|状态损坏审计|

### 24.2 恢复事务状态表

|当前状态|触发事件|前置条件|控制面动作|下一状态|失败/异常分支|
|---|---|---|---|---|---|
|`WAITING_USER` / `WAITING_SYSTEM`|`RESUME_REQUESTED`|有新增输入或补充说明|生成 `resume_request_id`、`inventory_merge_candidate`、`snapshot_signature_candidate`|`RESUMING`|准备失败转原等待态|
|`RESUMING`|`RESUME_ACKED`|认知面已持久化 checkpoint|Commit：提升 inventory / snapshot / `state_version`|`RUNNING`|Commit 失败转 `STATE_CORRUPTED`|
|`RESUMING`|`RESUME_TIMEOUT`|Ack 未收到|Rollback：丢弃 candidate，发送 `FORCE_REVERT_CHECKPOINT`|`WAITING_USER` / `WAITING_SYSTEM`|对齐失败转 `STATE_CORRUPTED`|
|`RESUMING`|`RESUME_VERSION_CONFLICT`|版本不一致|终止本次恢复，记录冲突|原等待态|连续冲突可转 `FAILED`|
|`STATE_CORRUPTED`|`ALIGNMENT_CONFIRMED`|已完成主从状态重新对齐|恢复主状态机可操作性|`WAITING_USER` / `RUNNING`|对齐失败保持 `STATE_CORRUPTED`|

### 24.3 预处理状态表

|当前状态|触发事件|前置条件|控制面动作|下一状态|失败/异常分支|
|---|---|---|---|---|---|
|`RUNNING`|`PREPROCESS_REQUIRED`|存在冻结前必需预处理|创建 `preprocess_run_revision_id`、workspace、job|`PRE_PROCESSING`|提交失败转 `FAILED`|
|`PRE_PROCESSING`|`PREPROCESS_SUCCEEDED`|预处理结果有效|更新元数据和快照，选择回流点|`RUNNING`|回流决策失败转 `FAILED`|
|`PRE_PROCESSING`|`PREPROCESS_FAILED`|预处理失败|记录失败原因，生成交互对象，裁决 cleanup|`WAITING_USER` / `FAILED`|清理失败转 `STATE_CORRUPTED`|
|`PRE_PROCESSING`|`PREPROCESS_CANCELLED`|用户或系统取消|中止作业并清理 workspace|`CANCELLED` / `WAITING_USER`|清理失败转 `STATE_CORRUPTED`|

### 24.4 队列重评估状态表

|当前状态|触发事件|前置条件|控制面动作|下一状态|失败/异常分支|
|---|---|---|---|---|---|
|`QUEUED`|`QUEUE_REEVAL_TIMER`|到达重评估周期|重新执行 Feasibility Gate|`QUEUED` / `FROZEN` / `PENDING_CONFIRMATION` / `FAILED`|重评估异常保留 `QUEUED` 并告警|
|`QUEUED`|`TOPOLOGY_CHANGED`|资源拓扑变化|重新计算 `required_resource_profile` 与系统可执行性|同上|同上|
|`QUEUED`|`SLOT_AVAILABLE`|可调度资源出现|直接放行提交|`SUBMITTED`|提交失败转失败治理|

---

## 25. 控制面 API 表

以下 API 表定义控制面对外契约。具体字段名可按实现栈细化，但语义应保持不变。

### 25.1 任务生命周期 API

|方法|路径|作用|关键请求字段|关键响应字段|幂等键|权限|状态影响|常见错误码|
|---|---|---|---|---|---|---|---|---|
|`POST`|`/tasks`|创建新任务|`request_id`, `user_query`, `attachments[]`, `workspace_id?`|`task_id`, `main_state`, `state_version`|`request_id`|用户|`INIT -> RUNNING`|`INVALID_INPUT`, `UNAUTHORIZED`, `IDEMPOTENCY_CONFLICT`|
|`GET`|`/tasks/{taskId}`|获取任务摘要|路径参数|`TaskStateView`|无|用户/管理员|无|`TASK_NOT_FOUND`, `FORBIDDEN`|
|`POST`|`/tasks/{taskId}/resume`|恢复挂起任务|`resume_request_id`, `delta_inventory[]`, `user_patch?`|`task_id`, `main_state=RESUMING`, `resume_request_id`|`resume_request_id`|用户|`WAITING_* -> RESUMING`|`INVALID_STATE`, `VERSION_CONFLICT`, `RESUME_CONFLICT`|
|`POST`|`/tasks/{taskId}/cancel`|取消任务|`request_id`, `reason?`|`task_id`, `main_state`|`request_id`|用户/管理员|非终态 -> `CANCELLED`|`INVALID_STATE`, `CANCEL_REJECTED`|
|`POST`|`/tasks/{taskId}/terminate`|管理员强制终止|`request_id`, `reason`|`task_id`, `main_state`|`request_id`|管理员|任意态 -> `CANCELLED` / `FAILED`|`FORBIDDEN`, `INVALID_STATE`|

### 25.2 事件与审计 API

|方法|路径|作用|关键请求字段|关键响应字段|权限|状态影响|常见错误码|
|---|---|---|---|---|---|---|---|
|`GET`|`/tasks/{taskId}/events`|拉取任务事件列表|`cursor?`, `limit?`|`events[]`, `next_cursor?`|用户/管理员|无|`TASK_NOT_FOUND`|
|`GET`|`/tasks/{taskId}/stream`|SSE 订阅任务事件|无|事件流|用户/管理员|无|`TASK_NOT_FOUND`, `STREAM_UNAVAILABLE`|
|`GET`|`/tasks/{taskId}/audit`|查询审计视图|`scope?`, `revision?`|审计摘要 / 明细|用户/管理员|无|`TASK_NOT_FOUND`, `FORBIDDEN`|

### 25.3 确认与人工介入 API

|方法|路径|作用|关键请求字段|关键响应字段|幂等键|权限|状态影响|常见错误码|
|---|---|---|---|---|---|---|---|---|
|`GET`|`/tasks/{taskId}/confirmations`|查询待确认事项|无|`confirmations[]`|无|用户/管理员|无|`TASK_NOT_FOUND`|
|`POST`|`/tasks/{taskId}/confirmations/{confirmationId}`|提交确认结果|`request_id`, `decision`, `option`, `comment?`|更新后的状态摘要|`request_id`|用户/管理员|可能 `PENDING_CONFIRMATION -> RUNNING/FROZEN/FAILED`|`INVALID_CONFIRMATION`, `EXPIRED_CONFIRMATION`, `IDEMPOTENCY_CONFLICT`|

### 25.4 结果与工件 API

|方法|路径|作用|关键请求字段|关键响应字段|权限|状态影响|常见错误码|
|---|---|---|---|---|---|---|---|
|`GET`|`/tasks/{taskId}/result`|获取最终结果视图|无|结果摘要、指标、解释、状态|用户/管理员|无|`RESULT_NOT_READY`, `TASK_NOT_FOUND`|
|`GET`|`/tasks/{taskId}/artifacts`|获取工件列表|`class?`|`artifacts[]`|用户/管理员|无|`TASK_NOT_FOUND`|
|`GET`|`/tasks/{taskId}/manifest`|获取冻结 Manifest 摘要|`version?`|`AnalysisManifestView`|用户/管理员|无|`MANIFEST_NOT_FOUND`, `FORBIDDEN`|

### 25.5 管理与修复 API

|方法|路径|作用|关键请求字段|关键响应字段|权限|状态影响|常见错误码|
|---|---|---|---|---|---|---|---|
|`POST`|`/tasks/{taskId}/force-revert-checkpoint`|强制认知面对齐|`request_id`, `target_checkpoint_version`|对齐任务状态|管理员|可能 `STATE_CORRUPTED -> WAITING_USER/RUNNING`|`ALIGNMENT_FAILED`, `FORBIDDEN`|
|`POST`|`/tasks/{taskId}/retry-queue-evaluation`|手动触发队列重评估|`request_id`|重评估结果|管理员|`QUEUED -> QUEUED/FROZEN/PENDING_CONFIRMATION/FAILED`|`INVALID_STATE`, `FORBIDDEN`|
|`POST`|`/tasks/{taskId}/workspace-cleanup`|手动清理工作区|`request_id`, `policy?`|清理结果|管理员|无或推进终局|`CLEANUP_FAILED`, `FORBIDDEN`|

### 25.6 标准错误响应建议

所有 API 建议采用统一错误体：

```json
{
  "error_code": "VERSION_CONFLICT",
  "message": "state_version mismatch",
  "task_id": "task_xxx",
  "state_version": 17,
  "retryable": false,
  "details": {}
}
```

---

## 26. TaskState Schema

以下给出控制面主状态对象的建议结构。可作为数据库模型、服务内 DTO 和 API View 的语义基础。

### 26.1 顶层结构

```json
{
  "task_id": "string",
  "conversation_id": "string",
  "request_id": "string",
  "user_id": "string",
  "tenant_id": "string",
  "workspace_id": "string",
  "main_state": "string",
  "phase": "string",
  "substage": "string",
  "state_version": 0,
  "planning_revision": "string",
  "checkpoint_version": "string",
  "inventory_version": 0,
  "blacklist_version": 0,
  "snapshot_signature": "string",
  "resume_context": {},
  "confirmation_context": {},
  "queue_context": {},
  "execution_context": {},
  "artifact_context": {},
  "audit_context": {},
  "created_at": "date-time",
  "updated_at": "date-time",
  "last_event_at": "date-time"
}
```

### 26.2 字段分组说明

#### A. 标识字段

|字段|类型|必填|说明|
|---|---|---|---|
|`task_id`|`string`|是|全局任务标识|
|`conversation_id`|`string`|否|会话标识|
|`request_id`|`string`|是|任务创建幂等键|
|`user_id`|`string`|是|发起者标识|
|`tenant_id`|`string`|否|多租户场景租户标识|
|`workspace_id`|`string`|否|逻辑工作域标识|

#### B. 状态字段

|字段|类型|必填|说明|
|---|---|---|---|
|`main_state`|`enum`|是|主状态机状态|
|`phase`|`enum`|是|当前阶段|
|`substage`|`enum`|否|细粒度子阶段|
|`state_version`|`integer`|是|乐观锁版本|
|`last_event_at`|`date-time`|是|最近事件时间|

#### C. 版本与快照字段

|字段|类型|必填|说明|
|---|---|---|---|
|`planning_revision`|`string`|否|当前规划版本|
|`checkpoint_version`|`string`|否|当前承认的认知 checkpoint|
|`inventory_version`|`integer`|否|资产库存版本|
|`blacklist_version`|`integer`|否|黑名单/策略版本|
|`snapshot_signature`|`string`|否|当前资源快照签名|

#### D. 恢复上下文 `resume_context`

```json
{
  "resume_request_id": "string",
  "status": "PREPARED|ACKED|COMMITTED|ROLLED_BACK",
  "inventory_merge_candidate_ref": "string",
  "inventory_merge_result_ref": "string",
  "snapshot_signature_candidate": "string",
  "target_checkpoint_version": "string",
  "last_resume_error_code": "string",
  "last_resume_at": "date-time"
}
```

#### E. 确认上下文 `confirmation_context`

```json
{
  "pending_confirmation_id": "string",
  "type": "string",
  "severity": "INFO|WARN|BLOCK",
  "reason_code": "string",
  "expires_at": "date-time",
  "decision": "APPROVED|REJECTED|EXPIRED|null"
}
```

#### F. 队列上下文 `queue_context`

```json
{
  "queue_entered_at": "date-time",
  "queue_ttl_seconds": 0,
  "last_feasibility_check_at": "date-time",
  "feasibility_basis_version": "string",
  "required_resource_profile": {
    "memory_gb": 0,
    "cpu_cores": 0,
    "gpu_count": 0,
    "disk_gb": 0,
    "ttl_minutes": 0,
    "special_capabilities": []
  }
}
```

#### G. 执行上下文 `execution_context`

```json
{
  "preprocess_run_revision_id": "string",
  "run_revision_id": "string",
  "job_id": "string",
  "job_status": "string",
  "heartbeat_at": "date-time",
  "workspace_runtime_id": "string",
  "workspace_retention_policy": "DESTROY_IMMEDIATELY|DESTROY_AFTER_RETENTION_WINDOW|ARCHIVE_READONLY|ADMIN_HOLD",
  "workspace_cleanup_status": "PENDING|RUNNING|DONE|FAILED",
  "last_runtime_error_code": "string"
}
```

#### H. 工件上下文 `artifact_context`

```json
{
  "manifest_id": "string",
  "manifest_version": "string",
  "result_bundle_ref": "string",
  "promotion_status": "NOT_STARTED|RUNNING|DONE|FAILED",
  "artifacts": [
    {
      "artifact_id": "string",
      "artifact_class": "FINAL_OUTPUT|AUDIT_RECORD|DERIVED_OUTPUT",
      "storage_uri": "string",
      "checksum": "string",
      "source_run_revision_id": "string"
    }
  ]
}
```

#### I. 审计上下文 `audit_context`

```json
{
  "audit_trace_id": "string",
  "latest_error_code": "string",
  "risk_flags": ["string"],
  "policy_flags": ["string"],
  "last_operator": "string"
}
```

### 26.3 主状态枚举建议

```json
[
  "INIT",
  "RUNNING",
  "WAITING_USER",
  "WAITING_SYSTEM",
  "RESUMING",
  "PENDING_CONFIRMATION",
  "PRE_PROCESSING",
  "READY_TO_FREEZE",
  "FROZEN",
  "QUEUED",
  "SUBMITTED",
  "EXECUTING",
  "RESULT_PROCESSING",
  "ARTIFACT_PROMOTING",
  "EXPLAINING",
  "TERMINAL_CLEANUP",
  "COMPLETED",
  "FAILED",
  "CANCELLED",
  "STATE_CORRUPTED"
]
```

### 26.4 TaskState 设计约束

- `state_version` 每次成功状态变更必须递增。
    
- `checkpoint_version` 只能记录已被控制面承认的 checkpoint。
    
- `preprocess_run_revision_id` 与 `run_revision_id` 不得混用。
    
- `promotion_status != DONE` 时，不应进入 `TERMINAL_CLEANUP`。
    
- `main_state = STATE_CORRUPTED` 时，仅允许有限管理操作。
    
- `queue_context` 仅在 `QUEUED` 或相关审计场景下必填。
    
- `resume_context.status = ACKED` 但未 Commit 时，任务必须仍处于 `RESUMING`。
    

---

## 27. AnalysisManifest Schema

`AnalysisManifest` 是控制面冻结后的正式执行对象。它不是认知面 payload，也不是规划面 payload，而是两者与控制面治理结果整合后的、可供执行面消费的唯一正式对象。

### 27.1 顶层结构

```json
{
  "manifest_id": "string",
  "manifest_version": "string",
  "task_id": "string",
  "state_version": 0,
  "planning_revision": "string",
  "created_at": "date-time",
  "goal": {},
  "skill": {},
  "template": {},
  "bindings": {},
  "args_final": {},
  "execution_graph": {},
  "runtime_assertions": [],
  "resource_profile": {},
  "risk_and_confirmation": {},
  "artifacts_contract": {},
  "audit_refs": {}
}
```

### 27.2 字段分组说明

#### A. 基础标识

|字段|类型|必填|说明|
|---|---|---|---|
|`manifest_id`|`string`|是|Manifest 唯一标识|
|`manifest_version`|`string`|是|Manifest 版本|
|`task_id`|`string`|是|所属任务|
|`state_version`|`integer`|是|冻结时控制面版本|
|`planning_revision`|`string`|是|对应规划版本|
|`created_at`|`date-time`|是|冻结时间|

#### B. 任务语义 `goal`

```json
{
  "goal_parse_ref": "string",
  "intent_type": "string",
  "analysis_objective": "string",
  "scope_summary": "string"
}
```

#### C. 技能信息 `skill`

```json
{
  "skill_id": "string",
  "skill_version": "string",
  "route_reason_ref": "string",
  "decision_summary_ref": "string"
}
```

#### D. 模板信息 `template`

```json
{
  "template_id": "string",
  "template_version": "string",
  "template_variant": "string",
  "downgrade_applied": false,
  "downgrade_history_ref": "string"
}
```

#### E. 绑定信息 `bindings`

```json
{
  "slot_bindings_ref": "string",
  "logical_roles": [
    {
      "role": "string",
      "asset_ref": "string",
      "asset_version": "string",
      "provenance": "USER|PREPROCESS|SYSTEM"
    }
  ],
  "available_asset_snapshot_ref": "string",
  "inventory_version": 0,
  "blacklist_version": 0
}
```

#### F. 定稿参数 `args_final`

```json
{
  "args_schema_version": "string",
  "values": {
    "workspace_dir": "/work/run_xxx",
    "results_suffix": "task_xxx",
    "lulc_path": "artifact://...",
    "precipitation_path": "artifact://..."
  },
  "source_ref": "args_draft_ref"
}
```

#### G. 执行图 `execution_graph`

```json
{
  "graph_ref": "string",
  "execution_graph_digest": "string",
  "rewrite_registry_ref": "string",
  "preprocess_included": true,
  "node_count": 0
}
```

#### H. 运行断言 `runtime_assertions`

```json
[
  {
    "assertion_id": "string",
    "type": "ASSET_SHAPE|CRS_MATCH|NODATA_COMPAT|VALUE_DOMAIN|FILE_EXISTENCE",
    "severity": "BLOCK|WARN",
    "expression_ref": "string"
  }
]
```

#### I. 资源画像 `resource_profile`

```json
{
  "computation_load_metrics_ref": "string",
  "estimated_memory_gb": 0,
  "estimated_disk_gb": 0,
  "estimated_runtime_minutes": 0,
  "requires_special_capabilities": []
}
```

#### J. 风险与确认 `risk_and_confirmation`

```json
{
  "risk_flags": ["string"],
  "confirmation_records": [
    {
      "confirmation_id": "string",
      "type": "string",
      "decision": "APPROVED|REJECTED",
      "decided_at": "date-time"
    }
  ],
  "policy_flags": ["string"]
}
```

#### K. 输出契约 `artifacts_contract`

```json
{
  "final_outputs": [
    {
      "name": "water_yield_raster",
      "format": "GeoTIFF",
      "required": true
    }
  ],
  "audit_artifacts": [
    {
      "name": "execution_log",
      "required": true
    }
  ],
  "promotion_required": true
}
```

#### L. 审计引用 `audit_refs`

```json
{
  "goal_parse_ref": "string",
  "skill_route_ref": "string",
  "planning_summary_ref": "string",
  "validation_result_ref": "string",
  "freeze_event_id": "string"
}
```

### 27.3 AnalysisManifest 设计约束

- Manifest 一经冻结，执行面只能消费该对象，不得再回读 candidate。
    
- `state_version` 必须与冻结瞬间控制面状态一致。
    
- 所有 `asset_ref` 必须指向已承认的资源版本。
    
- `confirmation_records` 中若存在 `REJECTED`，Manifest 不得进入正式执行。
    
- `promotion_required = true` 时，任务不得跳过 `ARTIFACT_PROMOTING`。
    
- Manifest 失效条件包括关键绑定变化、参数变化、模板变化、策略版本变化和受影响资产版本变化。
    

### 27.4 Manifest 失效与重建规则

当发生以下任一情形时，旧 Manifest 必须失效：

- `slot_bindings` 改变
    
- `args_final` 改变
    
- `template_id` 或 `template_version` 改变
    
- 关键资产版本变更
    
- 黑名单或策略使原图不可再执行
    
- 用户拒绝原确认项并选择其他分支
    

Manifest 失效后，任务必须回到相应上游阶段重新生成，不得对旧 Manifest 进行原地修补式执行。

---

## 28. 附录：错误码、事件码与策略字典

### 28.1 建议错误码

#### 恢复类

- `RESUME_CONFLICT`
    
- `RESUME_TIMEOUT`
    
- `RESUME_ACK_LOST`
    
- `CHECKPOINT_ALIGNMENT_FAILED`
    

#### 校验类

- `PARAM_SCHEMA_INVALID`
    
- `PARAM_BINDING_CONFLICT`
    
- `GRAPH_STRUCTURE_INVALID`
    
- `UNAUTHORIZED_TOOL`
    
- `RUNTIME_ASSERTION_GAP`
    

#### 资源类

- `HARD_LIMIT_EXCEEDED`
    
- `SOFT_LIMIT_QUEUED`
    
- `QUEUE_TTL_EXPIRED`
    
- `TOPOLOGY_DOWNGRADED_TO_HARD_LIMIT`
    

#### 执行类

- `JOB_SUBMIT_FAILED`
    
- `JOB_HEARTBEAT_LOST`
    
- `ASSERTION_FAILED`
    
- `WORKSPACE_CLEANUP_FAILED`
    
- `ARTIFACT_PROMOTION_FAILED`
    

#### 状态类

- `VERSION_CONFLICT`
    
- `INVALID_STATE`
    
- `STATE_CORRUPTED`
    
- `IDEMPOTENCY_CONFLICT`
    

### 28.2 建议事件码

- `TASK_CREATED`
    
- `TASK_STATE_CHANGED`
    
- `RESUME_PREPARED`
    
- `RESUME_ACKED`
    
- `RESUME_ROLLED_BACK`
    
- `CHECKPOINT_FORCE_REVERTED`
    
- `PREPROCESS_STARTED`
    
- `PREPROCESS_FAILED`
    
- `MANIFEST_FROZEN`
    
- `QUEUE_ENTERED`
    
- `QUEUE_REEVALUATED`
    
- `JOB_SUBMITTED`
    
- `JOB_COMPLETED`
    
- `ARTIFACT_PROMOTION_STARTED`
    
- `ARTIFACT_PROMOTION_FINISHED`
    
- `WORKSPACE_DEMOLISHED`
    
- `TASK_COMPLETED`
    
- `TASK_FAILED`
    
- `TASK_CORRUPTED`
    

### 28.3 Workspace 保留策略字典

|策略值|含义|
|---|---|
|`DESTROY_IMMEDIATELY`|终局后立即销毁|
|`DESTROY_AFTER_RETENTION_WINDOW`|保留一段窗口后销毁|
|`ARCHIVE_READONLY`|只读归档后保留|
|`ADMIN_HOLD`|管理员挂起保留|

### 28.4 队列重评估触发器字典

|触发器|说明|
|---|---|
|`TIMER`|周期性重评估|
|`NODE_POOL_CHANGED`|节点池变化|
|`HIGH_MEMORY_NODE_REMOVED`|高内存节点剔除|
|`CAPABILITY_SET_CHANGED`|能力集合变化|
|`POLICY_UPDATED`|配额或调度策略更新|

### 28.5 确认类型字典

|类型|说明|
|---|---|
|`TEMPLATE_DOWNGRADE_CONFIRM`|模板降级确认|
|`AUTO_CLIP_CONFIRM`|自动裁剪确认|
|`RUNTIME_COST_CONFIRM`|高成本执行确认|
|`ALTERNATIVE_DATA_CONFIRM`|替代数据源确认|
|`SCIENCE_SCOPE_CHANGE_CONFIRM`|科学口径变化确认|

---
## 29. 工程实现约束

本节用于约束控制面在工程实现阶段的具体落地方式，防止架构语义在状态机框架、数据库操作和异步 I/O 处理中被错误实现。

### 29.1 终局清理必须是全局收口路径，而非仅成功路径节点

`TERMINAL_CLEANUP` 不能只被实现为 `EXPLAINING -> TERMINAL_CLEANUP -> COMPLETED` 的线性成功路径状态。  
在代码实现中，失败和取消同样必须物理经过清理闸门，否则 workspace 清理、工件回收、上传中止和审计收口会失去一致的执行位置。

因此，建议将 `TERMINAL_CLEANUP` 实现为**全局收口伪状态、统一退出动作或专门的终局治理步骤**，而不是仅作为 happy path 上的普通状态。其语义应当是：

- 成功任务：结果解释完成后进入终局清理，再进入 `COMPLETED`
    
- 失败任务：失败裁决后进入终局清理，再进入 `FAILED`
    
- 取消任务：取消裁决后进入终局清理，再进入 `CANCELLED`
    

也就是说，代码层的真实拓扑应更接近：

`ANY_NON_TERMINAL_STATE -> cleanup orchestration -> terminal state`

而不是让 `FAILED` / `CANCELLED` 直接成为跳转终点。

这条约束的目的，是保证所有终局路径都共享统一的物理清理与审计收口流程。

---

### 29.2 敏感事务态中的取消必须采用“标记中止”，不得直接强刷终态

`Cancel` 不能在所有状态下一律被实现为“收到请求后立即把 `main_state` 改成 `CANCELLED`”。

尤其在以下事务敏感状态中：

- `RESUMING`
    
- `ARTIFACT_PROMOTING`
    

直接强刷 `CANCELLED` 会破坏原子 I/O 过程，造成严重副作用，例如：

- 认知面 checkpoint 已写入但控制面尚未完成 Ack/Commit 对齐
    
- 对象存储 multipart upload 被中途截断，留下不可回收碎片
    
- 清理逻辑在资源尚未释放前提前执行，导致状态与物理资源脱节
    

因此，在这些状态下，取消必须实现为**标记中止**：

- 先记录 `cancellation_requested = true`
    
- 主状态进入 `CANCELLING` 或等价的受控中止态
    
- 等当前原子 I/O 动作自然完成、可回滚、或优雅中断后
    
- 再进入统一终局清理流程
    
- 清理完成后最终落入 `CANCELLED`
    

换言之，取消并不总是“立即终止”，而应区分：

- **Soft Cancel**：请求取消，但等待当前原子事务收束
    
- **Hard Cancel**：仅对可安全中断的计算任务下发强终止
    

建议研发在状态机实现里，把 `CANCELLED` 视为**终局结果态**，而不是**取消命令收到时的即时态**。

---

### 29.3 AnalysisManifest 必须物理不可变，数据库层禁止更新旧 Manifest

`AnalysisManifest` 的架构语义是冻结后的正式执行契约，因此它在数据库层必须是**Append-Only** 的，不得对历史 Manifest 做原地更新。

在实现中，严禁出现以下模式：

UPDATE analysis_manifest  
SET bindings = ..., args_final = ..., execution_graph = ...  
WHERE manifest_id = ...;

正确做法必须是：

- 每次重新冻结都 `INSERT` 一条全新的 Manifest 记录
    
- 生成新的 `manifest_id` 与 `manifest_version`
    
- 保留旧 Manifest 作为历史快照
    
- 仅更新 `TaskState.manifest_id` 指针，使当前任务指向最新冻结对象
    

也就是说：

- `TaskState` 是可变控制对象
    
- `AnalysisManifest` 是不可变历史对象
    

这条约束是全链审计、失败复盘、执行回放和历史追踪的物理基础。  
如果研发在数据库层偷懒做 `UPDATE`，那么前面文档中关于“冻结对象不可变”“审计可回放”“历史快照可复现”的所有承诺都会失效。

建议在数据库设计中明确加入以下约束：

- Manifest 表仅允许 `INSERT`
    
- 禁止业务路径对历史 Manifest 执行 `UPDATE`
    
- 如确需做维护操作，也只能以管理员离线修复方式处理，不得进入正常运行链路
    

---

### 29.4 实现层推荐补充约束

为避免上述问题在代码中被无意破坏，建议研发同时遵守以下实现细则：

第一，状态机框架层要区分：

- 业务状态
    
- 事务中间态
    
- 终局结果态
    
- 终局治理动作
    

不要把“终态显示名”和“物理治理动作”混成一个状态节点。

第二，所有跨服务 I/O 事务都应带：

- 幂等键
    
- cancellation token
    
- 超时控制
    
- 补偿入口
    
- 审计事件
    

第三，数据库层对以下对象应明确可变性策略：

- `TaskState`：可变
    
- `AnalysisManifest`：不可变
    
- `EventLog`：追加写
    
- `AuditRecord`：追加写
    
- `ArtifactIndex`：追加或状态派生更新
    
- `WorkspaceRegistry`：状态更新但保留历史轨迹
    

第四，研发在写单元测试和联调测试时，应专门覆盖以下场景：

- `FAILED` 前必须经过 cleanup
    
- `CANCELLED` 前必须经过 cleanup
    
- `RESUMING` 中取消请求不直接强刷终态
    
- `ARTIFACT_PROMOTING` 中取消请求不破坏原子上传
    
- Manifest 重冻时旧记录不被更新
    
- 旧 Manifest 仍可被审计查询和回放引用

