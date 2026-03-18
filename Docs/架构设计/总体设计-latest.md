
**面向生态问题智能分析的 LLM + Skills + MCP + InVEST 协同系统**

## 1. 建设目标

本系统旨在构建一套面向生态问题分析的智能体体系：由 LLM 负责高不确定性语义任务，由 Skills 组织领域知识与分析策略，由 MCP 暴露受控能力，由异步执行面承接 InVEST 与 GIS 重计算任务，最终形成从用户自然语言输入到结果解释与报告生成的完整闭环。系统目标不是“自动调用某个工具”，而是完成任务理解、技能路由、参数构造与校验修复、执行治理、结果提取与可信解释的全链条智能分析。

本系统采用的总体原则是：**以 Skills 组织领域认知，以 MCP 组织能力执行**。Skills 承载问题类型、模型映射、参数模式、校验规则、修复策略与解释规范；MCP 负责数据检查、参数校验、作业控制、结果提取与上下文资源访问。LLM 参与理解、路由、参数草案、修复建议和解释，但不直接替代校验、执行、审计和治理。

## 2. 总体设计原则

### 2.1 Skill-first

系统主链路必须先确定问题属于哪一类分析任务，再进入具体模型与能力调用。Skill 是系统的领域认知单元，而不是说明文档或提示附件。每个 Skill 至少应封装适用问题类型、模型映射规则、参数 schema、校验规则、修复策略、结果解释规则和可用 MCP 能力映射。这样做的目的，是把“问题→工具”的脆弱映射升级为“问题→分析认知→模型策略→能力执行”的稳定映射。

### 2.2 LLM 是受控认知核心

LLM 只承担高不确定性语义职责，包括任务理解、Skill 候选选择、参数草案构造、修复建议生成和结果解释。LLM 的产物不能直接替代数据检查、参数校验、GIS/InVEST 执行、结果提取、审批和审计决策。LLM 的调用必须受状态机约束，只能在明确允许的阶段被显式调度。

### 2.3 结构化优先

所有进入执行链路的关键中间产物都必须采用强约束结构化对象，而不能让自由文本直接驱动执行。至少包括任务归一化结果、Skill 路由结果、槽位绑定、参数草案、修复建议、执行图摘要、解释结果等。结构化的目标是确保可校验、可持久化、可审计、可恢复和可回放。

### 2.4 治理优先

系统优先保证不乱选模型、不乱配参数、不乱解释结果、不在校验失败后继续执行。控制面必须掌握状态机、审计、权限、前端交互、事件流和执行前闸门；能力面只负责“能做什么”和“怎么做出来”，不负责“为什么做”和“是否该做”。

### 2.5 知识配置化

凡是会随领域经验变化而变化的知识，尽量进入 Skill 资产，而不散落在 prompt 或 if-else 中。这包括问题类型到模型的映射、参数必填项、校验逻辑、修复策略、解释禁语和结果关注点。

### 2.6 校验优先于执行

任何参数草案和执行准备对象，都必须先通过底层校验能力，再进入执行层。软校验与硬校验必须区分；基于逻辑占位或派生虚拟资产通过的结论，必须在执行期通过运行时断言再次核验。

## 3. 系统总体架构

系统采用六层架构：

1. **控制面（Control Plane）**
    
2. **认知面（Cognition Plane）**
    
3. **规划面（Planning Plane）**
    
4. **能力面（Capability Plane / MCP）**
    
5. **执行面（Execution Plane）**
    
6. **数据面（Data Plane）**
    

这个架构的核心含义是：认知面负责语义理解与高不确定性决策，规划面负责模板选择、执行图编译与图级修复，能力面以 MCP 形式暴露工具、资源和提示模板，执行面负责异步重计算，数据面负责状态、审计、目录化元数据和工件存储，控制面作为唯一治理主机协调全链路。原始方案中已经明确了 Skills 决策、MCP 执行、控制面治理、执行面承接长任务和数据面保存状态与工件的总体思想；本设计将其进一步组织为六层协同结构。

## 4. 分层职责

### 4.1 控制面

控制面负责统一 API 接入、鉴权与 RBAC、会话管理、任务生命周期管理、状态聚合、SSE/事件推流、审计落库、人工审批入口、恢复与重入、执行前冻结、作业提交与结果编排。控制面不负责 GIS 领域语义、模型选择知识、参数语义推理和生态学解释。控制面是全系统唯一主状态机持有者，也是修复编排与跨面调度主机。

### 4.2 认知面

认知面负责任务理解、Skill 路由、上下文富化、参数草案构造、修复意图生成和结果解释。认知面必须显式维护多轮上下文，执行节点级状态投影、按需富化、记忆压缩和结构化中间产物管理。认知面不直接发起最终执行，也不直接负责资源调度。

### 4.3 规划面

规划面负责模板选择、槽位与角色声明、执行图骨架编译、逻辑中间产物占位构造、实体化编译、图级静态校验、图重写、标准预处理节点插入、图规范化、运行时断言生成和执行图摘要生成。规划面是确定性编排层，而不是第二个自由发挥的 LLM 层。其核心输入来自认知面，核心输出交给控制面冻结和执行面消费。规划面这一职责来自对“模板选择 + 声明式模板解释 + 受控 DAG 编译”思想的系统化展开。

### 4.4 能力面（MCP）

能力面通过 MCP 暴露可调用能力，至少包括工具、资源、提示模板和文件边界等能力类型。能力面主要负责 inspect、validate、job control、summarize 等受控能力的标准化暴露，保证能力调用的确定性、标准化和可审计。每个 Skill 只能通过自己的 `mcp_tools_map` 访问有限工具子集。

### 4.5 执行面

执行面负责长任务执行、workspace 管理、InVEST 和 GIS 运行时、heartbeat 上报、输出文件写出、节点状态记录、重试、超时和部分重跑。执行面是异步重计算承载层，不解释业务语义，不选择 Skill，也不改写执行图。

### 4.6 数据面

数据面负责 Redis、PostgreSQL、对象存储/文件空间以及目录化元数据服务。Redis 用于 session 状态、task 状态、event stream、checkpoint、analysis cache 和短期 memory；PostgreSQL 用于审计记录、任务记录、决策记录、Skill 命中记录、运行指标、Manifest 与 revision 链；文件空间用于输入数据、workspace、InVEST 输出和中间工件。

## 5. 端到端主流程

系统主流程采用认知面与规划面的交替协作，而不是单向流水线：

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
  -> Result Extract / Summarize（能力面/执行面）
  -> Skill-grounded Explanation（认知面）
  -> Final Response
```

这样的时序安排解决了参数草案必须依赖模板槽位定义、图级修复不应在认知面与规划面之间私下循环、虚拟资产派生依赖真实输入绑定以及恢复流程依赖目录元数据就绪等关键问题。与此同时，它仍然保留了原始方案中的“Goal Parse → Skill Route → 参数构造 → 校验 → 修复 → 执行 → 解释”的主线，只是把时序关系和跨面协作方式定义得更明确。

## 6. Skills 体系设计

### 6.1 Skill 的定位

Skill 是独立、可版本化、可路由、可审计的领域认知单元。一个 Skill 代表“解决某类生态分析问题的方法包”，而不是单个 InVEST 模型的薄包装。一个 Skill 可以映射到一个模型、多个模型、固定 DAG 或受约束的 DAG 空间。

### 6.2 Skill Registry

Skill Registry 负责注册系统支持的 Skill、按问题类型检索 Skill、返回 Skill 元数据、提供版本治理和审计能力。路由阶段只能在注册 Skill 集中选择，不允许自由发明未注册能力。

### 6.3 Skill 最小资产集合

每个 Skill 至少包含：

- `SKILL.md`
    
- `skill_profile.yaml`
    
- `model_mapping.yaml`
    
- `parameter_schema.yaml`
    
- `validation_policy.yaml`
    
- `repair_policy.yaml`
    
- `interpretation_guide.yaml`
    
- `plan_templates.yaml`
    
- `mcp_tools_map.yaml`
    

其中，Skill Profile 描述问题类型和优先级；Model Mapping 定义模型选择策略；Parameter Schema 定义参数结构；Validation Policy 定义校验规则；Repair Policy 定义修复规则；Interpretation Guide 定义解释规范；Plan Templates 定义允许的执行骨架；MCP Tools Map 定义可调用能力范围。

## 7. 认知面设计

### 7.1 认知面职责

认知面是高不确定性语义层，负责：

- 多轮问题理解与任务归一化
    
- 在已注册 Skill 中进行受约束路由
    
- 根据规划面返回的角色与槽位声明进行上下文富化
    
- 基于参数 schema 和目录元数据生成 `slot_bindings` 与 `args_draft`
    
- 基于结构化错误生成 `repair_proposal`
    
- 基于结构化结果和解释规范生成 `final_explanation`
    

认知面不再负责模板选择、模板解释、执行图生成和图级重写。

### 7.2 设计原则

认知面坚持以下原则：多轮上下文优先、Skill 绑定优先、结构化优先于自由文本、前摄检查优先于事后修正、按需富化优先于全量富化、节点投影优先于整态输入、记忆压缩优先于历史无限增长、虚拟资产仅作为弱校验依据。

### 7.3 状态与节点

认知面维护的关键结构化对象包括：

- `goal_parse`
    
- `skill_route`
    
- `slot_schema_view`
    
- `logical_input_roles`
    
- `enriched_inventory`
    
- `slot_bindings`
    
- `args_draft`
    
- `validation_result_view`
    
- `repair_proposals`
    
- `decision_summary`
    
- `final_explanation`
    
- `manifest_payload_candidate`
    

其关键节点包括 Intent Parser、Skill Router、Context Enricher、Parameter Builder、Validator Adapter、Repair Intent Generator、Decision Summary Builder、Skill-grounded Explainer 和 Memory Summarizer。

## 8. 规划面设计

### 8.1 规划面的核心角色

规划面负责把认知面输出的结构化认知结果编译为可执行分析草案与执行图，并完成图级静态校验和图级修复。它是从语义层到执行层之间的编排层。

### 8.2 Two-Pass Compilation

规划面必须采用两程编译：

**Pass 1：逻辑编译**  
输入 `goal_parse`、`skill_route`、`strategy_flags`，输出：

- `selected_template`
    
- `logical_input_roles`
    
- `slot_schema_view`
    
- `graph_skeleton`
    
- `logical_artifact_specs`
    

此阶段只声明逻辑骨架与输入角色，不推导具体空间元数据。

**Pass 2：实体编译**  
输入 `slot_bindings`、`args_draft`、目录元数据事实和 `graph_skeleton`，输出：

- `materialized_execution_graph`
    
- `derived_virtual_assets`
    
- `runtime_assertions`
    

此阶段才结合真实输入和目录化元数据推导具体中间产物条件属性。虚拟资产因此被明确分为“逻辑占位”和“派生虚拟资产”两层。软校验依赖后者，执行期断言进一步核验。

### 8.3 图级能力

规划面包括：

- Template Selector
    
- Template Renderer
    
- Logical Artifact Builder
    
- Execution Graph Builder
    
- Static Graph Validator
    
- Graph Rewriter
    
- Canonicalizer
    
- Runtime Assertion Builder
    
- Graph Digest Builder
    
- Planning Summary Builder
    

图重写仅在控制面调度下触发，不由认知面直接调用。

## 9. 能力面（MCP）设计

能力面不承担领域决策，只承担标准化能力暴露。它至少包括四类核心能力：

### 9.1 Inspect

用于读取目录元数据、必要时做受限深度探测、检查空间范围和字段结构。认知面可调用 inspect 作为辅助能力，但默认应优先使用目录化元数据事实。

### 9.2 Validate

用于对参数草案、输入兼容性、图级约束和逻辑规则进行硬校验或软校验。校验必须由底层能力完成，而不是由 LLM 主观放行。

### 9.3 Job Control

执行类能力不再采用同步“execute 一跑到底”的模式，而是以作业控制语义暴露，如提交作业、查询状态、获取结果和取消作业。这样才能适配长时、重 I/O、重计算的 InVEST 执行模式。原始方案强调了 execute 作为能力类别，本设计将其严格收敛为异步作业控制。

### 9.4 Summarize / Extract

执行完成后，应返回结构化事实对象，包括关键指标、统计摘要、质量标记、不确定性提示和关键文件引用，而不是只返回一堆路径。这样认知面的解释器才能基于事实对象生成可信解释。

## 10. 执行面设计

执行面负责重计算任务的异步承载。其职责包括：

- 队列消费与 worker 调度
    
- workspace 管理
    
- InVEST / GIS 工具运行
    
- 输出与中间文件写出
    
- heartbeat 上报
    
- 节点状态记录
    
- 超时、重试和部分重跑
    

执行面不直接接受用户语义，不解释业务结果，不重写执行图。控制面通过作业控制接口向执行面提交任务。

## 11. 数据面与 Metadata Catalog

### 11.1 数据面角色

数据面负责状态、审计、工件和目录事实的保存。Redis 负责短期态与事件流，PostgreSQL 负责审计与业务记录，文件空间负责输入数据、workspace 和输出工件。

### 11.2 Spatial Metadata Catalog

系统必须维护目录化元数据服务，作为认知面与规划面的默认事实来源。目录中至少应保存：

- `asset_id`
    
- `uri`
    
- `format`
    
- `hash`
    
- `asset_version`
    
- `crs`
    
- `bbox` 或基础空间范围信息
    
- `width/height` 或 `geometry_type`
    
- `parse_status`
    

认知期默认使用目录元数据；更深层统计和复杂空间画像可异步补齐。

### 11.3 delta_inventory 恢复门槛

当用户上传新数据并触发 `/resume` 时，控制面不得立即把任务重新送入认知链路。必须先让这些新资产进入 `MIN_READY` 状态，即最小元数据集已同步入库，然后才允许恢复。更深层元数据进入 `FULL_READY` 后再用于更复杂的校验或优化。这样可以避免恢复刚开始就因为目录缺失再次挂起。

## 12. 状态机与跨面编排

### 12.1 主从状态机

控制面持有唯一主状态机；认知面和规划面只持有子阶段状态。LLM 的参与必须受状态机约束，不能在校验、执行或失败阶段擅自越权。

### 12.2 认知子阶段

认知面子阶段可包括：

- `GOAL_PARSE`
    
- `SKILL_ROUTE`
    
- `CONTEXT_ENRICH`
    
- `ARGS_DRAFT`
    
- `VALIDATE_VIEW`
    
- `REPAIR_INTENT`
    
- `DECISION_SUMMARY`
    
- `EXPLAIN`
    

这些子阶段必须事件化上报，但允许分级聚合与节流。

### 12.3 规划子阶段

规划面子阶段可包括：

- `TEMPLATE_SELECT`
    
- `LOGICAL_COMPILE`
    
- `MATERIALIZE`
    
- `STATIC_VALIDATE`
    
- `GRAPH_REWRITE`
    
- `CANONICALIZE`
    
- `ASSERTION_BUILD`
    
- `DIGEST_BUILD`
    
- `PLANNING_SUMMARY`
    

### 12.4 修复编排权归控制面

跨面 Repair Loop 不允许由认知面和规划面相互递归调用。控制面必须作为唯一修复编排主机，基于错误类型决定回认知面、回规划面，或先进入数据面刷新元数据。这样可以避免分布式乒乓效应和状态死循环。

## 13. Validation、Repair 与 Runtime Assertions

### 13.1 Validation

校验分为硬校验和软校验。真实资产上的参数完整性、字段兼容性和空间一致性属于硬校验；基于逻辑占位和派生虚拟资产的预测性结论属于软校验。软校验的通过不能直接视为执行安全，而必须附带运行时断言。

### 13.2 Repair

Repair 分为两类：

- **参数/绑定类修复**：由认知面生成修复建议并重构参数草案
    
- **图结构/预处理类修复**：由规划面执行图重写
    

控制面必须根据结构化错误码决定修复流向。

### 13.3 Runtime Assertions

凡依赖逻辑占位、软校验或条件性兼容假设的执行图，都必须由规划面生成 `runtime_assertions`，并在执行面实际核验。例如投影转换后 CRS 必须满足目标要求、裁剪后 extent 必须与 AOI 相交、重采样后 grid 对齐必须达标等。

## 14. Manifest、审计与回放

### 14.1 Analysis Manifest

系统必须在执行前生成并冻结 Analysis Manifest，记录本次分析的任务、Skill、模板、图签名、输入版本、参数草案、修复记录和运行约束。认知面负责输出 `manifest_payload_candidate`，规划面负责输出 `planning_manifest_payload`，控制面负责在执行前冻结正式 Manifest。

### 14.2 审计对象

至少应审计：

- 路由决策
    
- 模板选择
    
- 参数草案
    
- 校验结果
    
- 修复记录
    
- planning revision
    
- graph digest
    
- runtime assertion
    
- 最终解释与报告
    

### 14.3 事件流与回放

系统必须支持事件流推送与断线回放。Redis Stream 可作为有限历史事件流；事件必须具备单调递增 ID，支持 `Last-Event-ID` 恢复。长任务期间的状态变迁和关键节点结果都应可回放。

## 15. WAITING_USER 与恢复机制

### 15.1 WAITING_USER

当关键信息缺失、候选冲突无法自动消解或输入资源确实不足时，系统应进入 `WAITING_USER`。此时返回的不是一句自然语言提示，而应是结构化交互对象，包含缺什么、为什么缺、为什么刚才失败以及用户下一步该怎么补。认知面负责生成结构化交互描述，控制面负责驱动前端渲染和恢复。

### 15.2 Resume

恢复流程统一回到 Intent Parser，但前提是控制面已完成新输入合并、元数据最小就绪、失效范围分析和状态版本校验。恢复不是简单“接着往下跑”，而是携带增量信息重新评估哪个子阶段失效、哪些对象可复用、哪些节点必须重算。

## 16. 结果提取、解释与报告

执行结束后，系统不能把一堆路径直接交给 LLM 猜结论，而应先通过 Result Extractor / Summarize 返回结构化结果对象，包括：

- 输出目录
    
- 关键文件
    
- 核心指标
    
- 统计摘要
    
- 质量标记
    
- 不确定性提示
    

然后认知面的 Skill-grounded Explainer 基于 `interpretation_guide` 生成业务解释与报告。解释必须受约束，不能把模型结果说成监测实测值，也不能把相关关系表述成严格因果关系。

## 17. 实施策略

建议按单 Skill、单主题最小闭环启动，优先围绕 `water_yield` 建立端到端链路。初期重点应放在：

- 异步任务模型
    
- 状态持久化与并发控制
    
- 单 Skill 路由
    
- 模板/规划最小闭环
    
- 候选筛选与定向富化
    
- 参数草案与基础校验
    
- 有限轮次修复
    
- WAITING_USER
    
- 运行时断言
    
- 执行前摘要与执行后解释
    

不建议一开始就追求多 Skill 并行规划、开放式 DSL、全自动复杂空间预处理和复杂多模型协同。先把单主题闭环做稳，再扩展能力广度。

## 18. 结论

本系统是一套以 Skills 为认知核心、以规划面为编排核心、以 MCP 为能力暴露核心、以控制面为治理核心、以执行面为重计算核心、以数据面为状态与事实基础的生态分析智能体系统。它不是“让大模型自由调工具”的原型，也不是普通工作流系统，而是一个将领域认知、确定性编排、异步执行、目录化元数据、审计治理和可信解释整合在一起的工程化体系。其目标是在生态问题场景下，实现从自然语言任务理解到 InVEST 驱动分析执行再到结构化解释输出的稳定闭环。