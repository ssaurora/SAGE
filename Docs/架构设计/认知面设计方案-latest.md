
**面向生态问题智能分析的高不确定性语义层（最终发布版）**

## 1. 文档定位

认知面是整个系统中的高不确定性语义处理层，负责把用户自然语言问题转化为后续可编排、可校验、可执行的结构化认知对象。认知面的核心职责是：

- 任务理解与任务归一化
    
- Skill 路由
    
- 基于模板角色声明的上下文富化
    
- 参数草案构造与槽位绑定
    
- 修复意图生成
    
- 执行结果解释与报告生成
    

认知面不负责编译最终执行图，不负责图级重写，不负责任何作业控制与运行时调度，也不持有主状态机。认知面是一个纯语义计算层，其产物必须始终通过控制面、规划面和执行面的协作进入下游链路。

---

## 2. 设计目标

认知面的设计目标包括以下七项。

第一，认知面必须能够处理真实多轮会话，而不是只处理单轮输入。系统必须把用户的补充说明、修正、恢复输入、结构化交互结果和历史摘要统一纳入任务语境中。

第二，认知面必须在已注册 Skill 范围内进行受约束决策。系统不得允许模型绕过 Skill 直接下沉到工具选择或执行策略层。

第三，认知面必须围绕规划面声明的模板槽位与逻辑角色组织参数绑定。参数草案只能在模板角色清晰后生成，不能在模板未知时盲目猜测。

第四，认知面必须优先消费目录化元数据，而不是默认触碰物理文件。目录元数据是默认事实来源，物理文件探测只作为受控补充手段。

第五，认知面必须具备有限轮次自动修复能力，但修复只表达为意图，不直接修改执行图。图结构级修复必须交由控制面调度规划面完成。

第六，认知面必须能够对分析过程中的妥协、降级、替换、预处理和失败记录进行结构化追踪，以支撑最终解释中的局限性、风险和不确定性表达。

第七，认知面必须具备挂起恢复、节点级缓存、版本控制、事件回放、记忆压缩和错误熔断能力，确保长任务和多轮协作场景下的稳定运行。

---

## 3. 职责边界

### 3.1 认知面负责什么

认知面负责：

- 接收用户自然语言输入与结构化交互结果
    
- 归一化当前任务目标
    
- 在 Skill Registry 中完成受约束 Skill 路由
    
- 接收规划面 Pass 1 返回的模板角色声明与槽位定义
    
- 基于槽位定义做目录化上下文富化
    
- 构造 `slot_bindings` 与 `args_draft`
    
- 识别候选不足、角色缺失、参数冲突和绑定错误
    
- 生成 `repair_proposal`
    
- 发起模板协商请求
    
- 构造执行前 `decision_summary`
    
- 构造 `manifest_payload_candidate`
    
- 在执行完成后生成 `final_explanation`
    

### 3.2 认知面不负责什么

认知面不负责：

- 模板选择
    
- 模板渲染
    
- 最终 `execution_graph` 编译
    
- 图级静态校验
    
- preprocess 片段插入
    
- 图重写
    
- Manifest 最终冻结
    
- 作业提交、取消、状态查询和结果拉取
    
- Worker 调度与运行时资源管理
    
- 默认物理文件探测
    
- 主状态机持有
    

---

## 4. 设计原则

### 4.1 多轮上下文优先

认知面必须显式维护多轮原始消息、对话摘要和任务上下文，任何任务理解都不得建立在单轮 `user_query` 上。

### 4.2 Skill 绑定优先

所有路由、参数构造、修复和解释都必须围绕 Skill 闭环展开。模型不得绕过 Skill 自由决定能力路径。

### 4.3 结构化优先

凡会进入控制面、规划面、执行面或解释链路的认知产物，必须满足预定义 schema。自由文本不得直接驱动执行。

### 4.4 前摄检查优先

能在参数构造前通过目录化元数据发现的问题，必须尽量前置暴露，避免把所有问题都留给 validator 和执行期。

### 4.5 按需富化优先

认知面不得对全量 `data_inventory` 做无差别深度扫描；富化必须围绕当前模板声明的 `logical_input_roles` 与 `slot_schema_view` 展开。

### 4.6 节点投影优先

完整运行态状态对象不得整体注入任何 LLM 节点。每个节点仅接收完成职责所需的最小必要状态。

### 4.7 Catalog 优先

Context Enricher 和 Parameter Builder 默认必须优先读取 Metadata Catalog；只有目录缺失、目录失效或控制面明确要求深度复核时，才允许触发物理文件级检查。

### 4.8 黑名单硬约束

任何被执行面、底层能力或控制面判定为物理损坏、内容异常或与目录事实显著不符的资产，必须进入黑名单，并在认知面候选筛选时被无条件过滤。

### 4.9 虚拟资产弱事实化

逻辑中间产物占位与派生虚拟资产只能作为软校验依据，不得与真实资产的硬校验同权处理。依赖它们通过的参数草案必须附带运行时断言。

### 4.10 模板降级必须受科学约束

模板降级不是系统高可用策略，而是科学假设调整行为。只要降级改变了核心模型假设、核心输入要求或结果解释口径，就不得静默执行，必须触发用户确认。

### 4.11 过程历史必须进入解释链

任何模板降级、预处理强改、候选剔除和多轮修复历史，都必须进入解释器输入，并在报告中转化为局限性、风险和不确定性说明。

### 4.12 恢复时系统事实优先于用户陈述

用户关于“我已经上传了数据”的陈述，只能作为意图线索；资源是否真的可用，只能由系统凭证更新任务事实。

---

## 5. 协作时序

认知面与规划面、控制面采用交替协作方式：

```text
User Query
  -> Goal Parse（认知面）
  -> Skill Route（认知面）
  -> Template Select / Role Declaration（规划面 Pass 1）
  -> Context Enrich（认知面）
  -> Slot Binding / Args Draft（认知面）
  -> Materialized Compilation / Virtual Derivation（规划面 Pass 2）
  -> Validate（控制面调度）
  -> Repair Dispatch（控制面）
     -> 参数/绑定类 -> 认知面
     -> 图结构/预处理类 -> 规划面
     -> 元数据未就绪类 -> 数据面
  -> Re-Validate（控制面调度）
  -> Manifest Freezing（控制面）
  -> Submit Job（控制面）
  -> Execution（执行面）
  -> Result Extract / Summarize
  -> Skill-grounded Explanation（认知面）
```

该时序遵守三条硬规则：

1. 模板角色先声明，参数绑定后发生。
    
2. 修复编排权归控制面，认知面与规划面不得直接递归互调。
    
3. 恢复流程必须先校验系统资源就绪，再进入认知链路。
    

---

## 6. 状态模型

### 6.1 CognitiveState

认知面维护统一的 `CognitiveState`，建议至少包含以下字段：

- `messages`
    
- `conversation_summary`
    
- `task_context`
    
- `reentry_hint`
    
- `goal_parse`
    
- `skill_route`
    
- `slot_schema_view`
    
- `logical_input_roles`
    
- `candidate_inventory_view`
    
- `enriched_inventory`
    
- `asset_blacklist`
    
- `asset_blacklist_reasons`
    
- `slot_bindings`
    
- `strategy_flags`
    
- `args_draft`
    
- `validation_result_view`
    
- `repair_proposals`
    
- `decision_summary`
    
- `task_trace_summary`
    
- `final_explanation`
    
- `manifest_payload_candidate`
    
- `state_version`
    
- `escalation_state`
    

### 6.2 task_context

`task_context` 至少应包含：

- `pending_questions`
    
- `missing_information`
    
- `core_requirements`
    
- `strictness_level`
    
- `downgrade_policy`
    
- `resource_status`
    
- `latest_user_commitments`
    

其中：

- `core_requirements` 用于表达不可擅自降级的核心科学要求
    
- `strictness_level` 用于表达任务对近似替代、简化模型和输入替代的容忍度
    
- `downgrade_policy` 用于定义模板降级时是否需要强确认
    

### 6.3 task_trace_summary

`task_trace_summary` 不是简单状态日志，必须至少包括：

- `downgrade_history`
    
- `preprocess_history`
    
- `repair_history`
    
- `candidate_rejection_history`
    
- `binding_retry_history`
    
- `assertion_risk_history`
    

这些历史必须服务于最终解释中的局限性与风险表达。

### 6.4 状态要求

认知状态必须满足以下要求：

1. 所有关键对象必须结构化存储
    
2. 状态必须支持序列化、持久化与审计回放
    
3. 运行态状态与入模态状态必须严格区分
    
4. 黑名单必须是候选过滤硬约束
    
5. 认知状态不得持有最终执行图
    
6. 恢复时必须支持 `delta_inventory` 合并和系统凭证对齐
    

---

## 7. 节点级状态投影

### 7.1 基本要求

- 禁止整包状态入模
    
- 每个节点只接收完成职责所需的最小字段集合
    
- 富化数据必须二次裁剪
    
- 长会话必须依赖摘要而不是全量历史
    
- 黑名单必须在进入节点前完成过滤
    
- 恢复态必须带系统凭证视图，而不是只带用户消息
    

### 7.2 典型投影

#### Intent Parser Projection

- 最近 N 轮 `messages`
    
- `conversation_summary`
    
- `task_context.pending_questions`
    
- `task_context.missing_information`
    
- 上一次结构化交互结果摘要
    
- `inventory_merge_result`
    

#### Skill Router Projection

- `goal_parse`
    
- Skill Registry 摘要
    
- 高层约束摘要
    
- 当前任务可用数据概览
    

#### Context Enricher Projection

- `goal_parse`
    
- `skill_route`
    
- `slot_schema_view`
    
- `logical_input_roles`
    
- `candidate_inventory_view`
    
- `asset_blacklist`
    
- Catalog 摘要视图
    

#### Parameter Builder Projection

- `goal_parse`
    
- `skill_route`
    
- `slot_schema_view`
    
- `logical_input_roles`
    
- `strategy_flags`
    
- 针对槽位筛选后的 `enriched_inventory`
    
- `parameter_schema`
    
- `task_context.core_requirements`
    
- `task_context.strictness_level`
    

#### Repair Projection

- `args_draft`
    
- `validation_result_view`
    
- `repair_policy`
    
- `planning_feedback_view`
    
- `asset_blacklist`
    

#### Explainer Projection

- `metrics`
    
- `quality_flags`
    
- `uncertainty`
    
- `interpretation_guide`
    
- `task_trace_summary`
    

---

## 8. 核心节点设计

## 8.1 Intent Parser

### 职责

将输入归一化为统一任务对象，判断当前输入属于：

- 新任务
    
- 补充信息
    
- 修正性反馈
    
- 恢复性输入
    

### 输出

`goal_parse`

### 设计要求

- 必须同时读取最近消息与会话摘要
    
- 必须识别当前输入的语用类型
    
- 缺失信息必须显式写入 `missing_information`
    
- 不在此阶段做模板选择或参数绑定
    
- 不得仅凭用户陈述更新“资源已就绪”状态
    

### 系统凭证规则

Intent Parser 必须读取 `inventory_merge_result`。  
若用户声称“已上传数据”，但系统凭证显示：

- `added = 0`
    
- 或 `failed_parse > 0`
    
- 或没有任何资产进入 `MIN_READY`
    

则不得将任务上下文更新为“资源已补齐”，而应输出结构化驳回或重新挂起。

---

## 8.2 Skill Router

### 职责

在已注册 Skill 中完成受约束路由。

### 输出

`skill_route`

### 设计要求

- 只能在候选 Skill 集中选择
    
- 不得发明未注册 Skill
    
- 不得绕过 Skill 直接下沉到工具选择
    
- 路由失败时必须输出结构化失败原因
    

---

## 8.3 Context Enricher

### 职责

围绕规划面 Pass 1 返回的 `logical_input_roles` 和 `slot_schema_view` 做目录化上下文富化。

### 输出

`enriched_inventory`

### 设计要求

- 不得在模板角色未声明前盲目富化
    
- 不得对全量 inventory 做无差别深度扫描
    
- 默认只读 Catalog
    
- 黑名单资产必须无条件过滤
    
- 仅在目录失效或深度复核场景下才触发重度 inspect
    

### 黑名单过滤规则

黑名单不得只按逻辑文件名过滤，必须按**具体版本实例**过滤。过滤主键至少应包含：

- `asset_version`  
    或
    
- `asset_hash`
    

推荐同时保存：

- `asset_id`
    
- `asset_version`
    
- `asset_hash`
    

这样同名但内容已修复的新上传文件可以合法穿透旧黑名单。

---

## 8.4 Parameter Builder

### 职责

在模板角色已声明、目录元数据已富化的前提下构造 `slot_bindings` 和 `args_draft`。

### 输出

- `slot_bindings`
    
- `args_draft`
    

### 设计要求

- 参数草案必须是模板绑定后的参数草案
    
- 必须显式给出参数来源
    
- 候选不足时不能静默失败
    
- 不得跳过 validator
    
- 不得宣告执行可放行
    

### 模板协商触发

若 `required_roles` 中存在无法满足的核心角色，且扩召回预算已耗尽，则 Parameter Builder 不得直接进入 `WAITING_USER`，而应优先输出模板协商意图：

```json
{
  "intent": "REQUEST_TEMPLATE_DOWNGRADE",
  "missing_roles": ["soil"],
  "reason": "required_roles_unsatisfied"
}
```

### 科学降级确认规则

若降级模板会违背 `goal_parse.core_requirements`，或改变核心科学假设、核心输入要求、结果解释口径，则不得静默降级。系统必须输出 `CONFIRMATION` 型交互，请求用户显式确认后才能继续。

### 降级记录规则

任何发生的模板降级必须强制写入：

- `task_trace_summary.downgrade_history`
    
- `decision_summary`
    
- `manifest_payload_candidate`
    

---

## 8.5 Validator Adapter

### 职责

将 `args_draft` 与必要上下文提交给底层校验能力，并转换为认知面可消费的 `validation_result_view`。

### 设计要求

- 校验必须由底层能力完成
    
- 真实资产上的结论属于硬校验
    
- 派生虚拟资产上的结论属于软校验
    
- 软校验必须带出断言需求
    
- 认知面不得自行放行执行
    

---

## 8.6 Repair Intent Generator

### 职责

根据 `validation_result_view` 生成修复意图。

### 输出

`repair_proposal`

### 设计要求

- 只表达意图，不直接执行修复
    
- 任何修复都必须重新进入校验
    
- 需要图结构改动时必须由控制面转调规划面
    

### RepairProposal 原语集

#### `SET`

设置或替换某个参数值或绑定值

#### `CLEAR`

清空当前无效值或解除绑定

#### `MARK_AS_BAD_CANDIDATE`

将当前候选资产标记为不可再用，并请求从候选集中剔除后重新绑定

```json
{
  "op": "MARK_AS_BAD_CANDIDATE",
  "target": {
    "asset_id": "asset_precip_2020",
    "asset_version": 3,
    "asset_hash": "sha256:abcd..."
  },
  "reason": "content_mismatch"
}
```

#### `REQUEST_PREPROCESS`

请求规划面插入标准预处理片段

```json
{
  "op": "REQUEST_PREPROCESS",
  "action": "reproject_raster",
  "params": {
    "target_crs": "EPSG:3857"
  },
  "reason": "crs_mismatch"
}
```

#### `REQUEST_TEMPLATE_DOWNGRADE`

请求模板降级或替代模板协商

#### `REQUEST_USER_INPUT`

请求用户补充输入、确认选择或上传新文件

### 修复分流规则

- 参数值、参数来源、slot 绑定类错误 → 认知面继续处理
    
- 模板不满足、图结构非法、需要插入 preprocess → 上交控制面调度规划面
    
- 元数据未就绪、物理资产异常 → 上交控制面调度数据面或执行面反馈链路
    

### 修复历史记录

所有修复意图必须写入 `task_trace_summary.repair_history`。

---

## 8.7 Decision Summary Builder

### 职责

为执行前冻结与前端可视化生成可读决策摘要。

### 输出

`decision_summary`

### 内容建议

- 已理解的任务目标
    
- 选中的 Skill
    
- 当前模板角色绑定概况
    
- 当前参数草案概况
    
- 缺失项与风险项
    
- 是否已触发模板协商
    
- 是否已触发用户交互
    
- 是否存在黑名单候选
    
- 是否存在降级、替代或预处理需求
    

---

## 8.8 Skill-grounded Explainer

### 职责

基于结构化结果对象和 `interpretation_guide` 生成受约束解释。

### 输出

`final_explanation`

### 设计要求

- 只能基于结构化事实对象解释
    
- 不能把模型结果表述为监测实测值
    
- 不能把相关性说成严格因果关系
    
- 必须说明局限性、风险和下一步建议
    

### 过程历史强制入解释链

Explainer 必须显式消费：

- `task_trace_summary.downgrade_history`
    
- `task_trace_summary.preprocess_history`
    
- `task_trace_summary.repair_history`
    

并将这些历史转化为报告中的 `limitations`、`risk_notes` 和 `uncertainty_notes`。

### 解释规则

- 发生模板降级时，必须说明降级的原因、是否得到用户确认，以及对结果语义的影响
    
- 发生预处理强改时，必须说明其可能带来的空间误差或分辨率偏差
    
- 发生候选剔除和替换时，必须说明数据质量风险和对稳健性的影响
    

---

## 8.9 Memory Summarizer

### 职责

压缩长会话历史，控制上下文膨胀。

### 设计要求

- 永远只保留最近 N 轮原始消息
    
- 更早消息压缩为结构化 `conversation_summary`
    
- 被裁剪原始消息进入审计存储，不再进入模型上下文
    
- 压缩结果必须服务于任务理解而非仅做字数缩减
    

---

## 9. 模板协商机制（Template Negotiation）

### 9.1 目标

当当前模板对输入要求过高，而系统存在可行替代模板时，优先尝试模板协商，而不是立即要求用户补数。

### 9.2 触发条件

满足任一条件可触发：

- `required_roles` 无合格候选
    
- 扩召回预算耗尽仍无关键角色
    
- 某关键角色全部命中黑名单资产
    
- 缺失的是高阶增强输入，而存在低配分析路径
    

### 9.3 执行方式

认知面输出 `REQUEST_TEMPLATE_DOWNGRADE`，控制面据此驱动规划面重新执行 Pass 1，并过滤掉需要缺失角色的模板。

### 9.4 强阻断规则

若降级模板违背 `goal_parse.core_requirements`，则绝对禁止静默降级，必须进入 `WAITING_USER`，并返回 `CONFIRMATION` 型交互。

### 9.5 追踪要求

所有模板协商与降级结果必须写入 `task_trace_summary.downgrade_history`。

---

## 10. 资产黑名单机制（Asset Blacklist）

### 10.1 目标

阻止物理损坏、内容异常或与目录事实显著不符的资产被重复选中。

### 10.2 加入黑名单的触发来源

控制面必须在以下情形下把资产写入黑名单：

- 执行面返回物理文件损坏
    
- 数据块读取失败
    
- 内容与 Catalog 元数据显著不符
    
- 持续 runtime assertion 失败且可归因到资产本身
    
- validator 明确返回内容失真类硬错误
    

### 10.3 黑名单粒度

黑名单必须绑定到具体版本实例，不得仅绑定逻辑文件名、路径或稳定 `asset_id`。建议绑定字段：

- `asset_id`
    
- `asset_version`
    
- `asset_hash`
    

### 10.4 数据结构建议

```json
{
  "asset_blacklist": [
    {
      "asset_id": "asset_precip_2020",
      "asset_version": 3,
      "asset_hash": "sha256:abcd...",
      "reason": "physical_corruption",
      "source": "execution_plane"
    }
  ]
}
```

### 10.5 过滤规则

Context Enricher 和 Parameter Builder 在筛选候选资产时，必须按 `asset_version` 或 `asset_hash` 过滤黑名单。  
同名但哈希值已变化的新上传修复文件应被视为新版本候选，不得被旧黑名单误杀。

---

## 11. WAITING_USER 与结构化交互契约

### 11.1 进入条件

- 关键角色缺失且无可行降级模板
    
- 多个候选无法自动消歧
    
- 用户必须确认模板降级或关键策略变化
    
- 外部资源必须补充
    
- 扩召回预算已耗尽
    

### 11.2 输出对象

至少包含：

- `type`
    
- `prompt`
    
- `field_key`
    
- `constraints`
    
- `context_reason`
    
- `rejected_cause`
    
- `suggested_action`
    

### 11.3 设计要求

认知面必须明确说明：

- 缺什么
    
- 为什么缺
    
- 为什么现有候选被拒绝
    
- 模板协商是否已尝试
    
- 是否涉及科学降级确认
    
- 用户下一步怎么补
    

---

## 12. Manifest 候选载荷

认知面必须在执行前生成 `manifest_payload_candidate`，供控制面冻结 Analysis Manifest。

### 12.1 至少包含

- `goal_parse`
    
- `skill_route`
    
- `slot_bindings`
    
- `strategy_flags`
    
- `args_draft`
    
- `validation_result_view`
    
- `decision_summary`
    
- `task_trace_summary`
    
- `skill_version_ref`
    

### 12.2 规则

- 认知面只组装，不冻结
    
- 认知面只产出当前 revision 的 candidate
    
- 发生补数、模板协商、重新绑定或重大修复后，必须生成新 candidate
    

---

## 13. 恢复、缓存与重算

### 13.1 恢复入口

所有恢复统一回到 Intent Parser，但控制面必须先完成：

- `delta_inventory` 合并
    
- Metadata Catalog `MIN_READY` 检查
    
- `inventory_merge_result` 构造
    
- 失效范围分析
    
- 状态版本校验
    

### 13.2 inventory_merge_result

Intent Parser 必须读取系统凭证对象 `inventory_merge_result`，至少包含：

- `added`
    
- `updated`
    
- `failed_parse`
    
- `rejected`
    
- `accepted_assets`
    
- `failed_assets`
    

只有系统凭证证明资产已真正进入可用状态，认知面才能更新 “资源已就绪” 相关任务事实。

### 13.3 节点级缓存

采用双层机制：

- 第一层：业务级失效规则
    
- 第二层：节点级输入签名缓存
    

### 13.4 预算控制

认知面必须有：

- 扩召回预算
    
- 修复轮次预算
    
- 模板协商预算
    
- 同类错误重复上限
    

达到上限后必须转控制面治理。

---

## 14. LLM 节点重试策略

### 14.1 可重试错误

- 429
    
- 502 / 503 / 504
    
- 网络超时
    
- 连接中断
    

### 14.2 不可盲重试错误

- 400
    
- token 超限
    
- schema 不合法
    
- 输入缺失
    
- 投影视图构造失败
    

### 14.3 规则

- 节点级重试优先于全局重试
    
- 400 类错误必须先压缩上下文或修正投影
    
- 重试次数和退避策略必须可配置
    

---

## 15. 错误分类与熔断

### 15.1 挂起类错误

进入 `WAITING_USER`

### 15.2 可修正错误

生成 `repair_proposal`

### 15.3 模板协商类错误

触发 `REQUEST_TEMPLATE_DOWNGRADE`

### 15.4 科学降级确认类错误

触发 `CONFIRMATION`

### 15.5 图级修复类错误

上交控制面调度规划面

### 15.6 资产失真类错误

加入黑名单并重试候选筛选

### 15.7 致命错误

由控制面终止当前分支并进入失败治理

---

## 16. 对外契约

### 16.1 与控制面的契约

控制面提供：

- 当前任务上下文
    
- 已达到 `MIN_READY` 的 inventory
    
- `inventory_merge_result`
    
- 规划面 Pass 1 返回的最小视图
    
- 校验结果与错误码
    
- 恢复提示与状态版本
    

认知面返回：

- `goal_parse`
    
- `skill_route`
    
- `slot_bindings`
    
- `args_draft`
    
- `repair_proposal`
    
- `decision_summary`
    
- `manifest_payload_candidate`
    
- `final_explanation`
    

### 16.2 与规划面的契约

规划面 Pass 1 向认知面返回：

- `selected_template_ref`
    
- `logical_input_roles`
    
- `slot_schema_view`
    
- `binding_constraints`
    
- `planning_context_view`
    

认知面向规划面回传：

- `slot_bindings`
    
- `args_draft`
    
- `strategy_flags`
    
- 绑定后的目录元数据摘要
    

### 16.3 与能力面的契约

认知面直接依赖的能力面能力仅包括：

- 轻量 inspect 视图
    
- validate 结构化接口
    

作业控制接口由控制面协调，不属于认知面直接调用范围。

---

## 17. 运行保障

认知面必须具备以下工程护栏：

- 状态持久化
    
- `request_id` 幂等
    
- `state_version` 乐观锁
    
- SSE / 事件流回放
    
- 节点级缓存
    
- 长会话记忆压缩
    
- 扩召回预算与熔断
    
- 模板协商预算
    
- 黑名单全链路过滤
    
- 节点级重试
    

这些机制共同保证认知面在长时任务、多轮对话、挂起恢复和复杂校验场景下仍然可控。

---

## 18. 最小落地顺序

建议按以下顺序落地：

1. `CognitiveState`、幂等、版本与事件流骨架
    
2. Intent Parser、Skill Router
    
3. WAITING_USER 结构化交互契约
    
4. Metadata Catalog 接入与黑名单过滤
    
5. `inventory_merge_result` 恢复凭证机制
    
6. Context Enricher、Parameter Builder
    
7. Validator Adapter
    
8. Repair Intent Generator 与 GIS 修复意图原语
    
9. Template Negotiation 与科学降级确认
    
10. Decision Summary、`task_trace_summary` 与 `manifest_payload_candidate`
    
11. Skill-grounded Explainer
    
12. 恢复、缓存、回放与审计补齐
    

---

## 19. 结论

认知面是整个系统中的高不确定性语义层。它负责听懂问题、选定技能、围绕模板角色组织数据、构造参数草案、表达修复意图，并在执行完成后给出受约束解释。它不编译执行图，不直接调度执行，也不持有主状态机。通过模板协商、科学降级确认、GIS 修复意图原语、版本级资产黑名单、系统凭证驱动的恢复入口、过程历史入解释链、记忆压缩、恢复重算与错误熔断，认知面能够在复杂生态分析场景下保持纯度、稳定性和可治理性。