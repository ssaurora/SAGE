
## 1. 文档定位

能力面是系统的**标准化能力契约层**。它位于认知面、规划面、控制面与执行/数据实现之间，负责把底层工具、资源、运行时服务和结果接口组织为**可注册、可发现、可授权、可审计、可版本化**的 capability contract。能力面的核心任务不是替代认知面做理解、替代规划面做编译、替代控制面做治理，也不是把所有底层系统调用集中转发，而是为上层提供统一、受控、稳定的能力边界。

在整体架构中：

- 认知面负责意图理解、Skill 路由、参数草案与修复意图生成。
    
- 规划面负责模板协商、两程编译、图级约束收敛、运行时断言生成与规划摘要输出。
    
- 控制面负责主状态机、恢复事务、冻结、放行、排队、终局清理与审计闭环。
    
- 能力面负责把这些上层需要调用的“能力”抽象出来，形成标准 contract，并约束谁能调、怎样调、调完产生什么副作用。
    

因此，能力面不是“第二个规划面”，不是“第二个控制面”，也不是“全量底层系统的总线代理”。它更适合被理解为：

> **Registry + Policy + Contract + Thin Gateway + Selected Adapters**

即：能力注册中心、白名单与权限策略层、统一契约层、轻量协议网关，以及少量必要适配器。

---

## 2. 设计目标

能力面至少应满足以下目标。

第一，提供统一 capability contract，使上层调用面对的是稳定能力名与固定 schema，而不是散乱的底层 tool、脚本、SDK 或执行节点私有 API。

第二，保证调用边界清晰。认知面、规划面、控制面看到的能力集合必须不同；高副作用能力必须由控制面统一掌握调用权。

第三，保证白名单与 Skill 作用域生效。每个 Skill 只能通过自己的 `mcp_tools_map` 访问有限能力子集，能力面必须把这一点落实为运行时约束，而不是停留在文档层面。

第四，提供标准化的 inspect、validate、profile、job contract、result contract、workspace contract、artifact contract、checkpoint contract，使控制面和规划面不直接耦合到底层实现细节。

第五，明确副作用等级、取消语义、幂等语义和审计要求，支撑控制面的主状态机、恢复事务、排队治理、终局清理与工件晋升。

第六，避免中间件膨胀。能力面不承担图级业务裁决，不承载海量数据计算，不集中包办所有底层工具包装代码，也不要求所有系统调用都必须多一跳穿过独立厚代理服务。

第七，支持 provider 级扩展。新模型、新算子、新工具包应优先通过底层 provider 自注册或适配器方式纳入能力体系，而不是要求能力面核心代码为每一种工具手写一层巨型 wrapper。

---

## 3. 职责边界

### 3.1 能力面负责什么

能力面负责：

- 定义 capability contract
    
- 定义 capability 分类与命名
    
- 维护 Capability Registry
    
- 维护 capability 版本与兼容性信息
    
- 维护调用者白名单与 Skill scope
    
- 维护副作用级别、取消模式、幂等模式、审计模式
    
- 提供统一的 schema 与错误码约束
    
- 对 provider 做轻量注册、路由和协议适配
    
- 对部分关键跨组件能力提供薄适配器
    
- 为上层返回结构化事实对象，而不是裸工具输出
    

### 3.2 能力面不负责什么

能力面不负责：

- 用户意图理解
    
- Skill 选择
    
- 模板选择
    
- 参数草案生成
    
- 图级静态校验裁决
    
- 图重写裁决
    
- 运行时断言生成裁决
    
- 主状态机推进
    
- 恢复事务裁决
    
- 执行前放行
    
- 终局策略裁决
    
- 大体积业务数据扫描与断言计算
    
- 结果解释与叙事生成
    

也就是说，能力面可以提供“证据执行器”“校验原语”“标准作业契约”“结果抽取契约”，但不承担“业务法官”的角色。

---

## 4. 核心设计原则

### 4.1 Contract First

上层依赖 capability contract，而不是依赖某个 Python 脚本、某个 GDAL 命令、某个 MQ topic、某个对象存储 SDK。capability 是系统语言，provider 是实现细节。

### 4.2 Thin, Not Thick

能力面应尽量轻：重在契约、策略、注册和路由；不把复杂业务编译逻辑、大体积数据计算和重型执行生命周期统统塞进自己内部。避免形成厚重 ESB。

### 4.3 Evidence, Not Verdict

能力面提供校验原语、事实探针、画像结果、约束检查器和标准返回格式；规划面和控制面分别在自己的职责范围内完成裁决。  
例如：能力面可以提供 CRS 兼容检查、输出契约检查、工具授权检查、资源画像估计；但图级静态合法性结论属于规划面输出，执行放行结论属于控制面输出。

### 4.4 Push-down for Heavy Data

涉及大栅格、大矢量、大结果文件的像素级、要素级、块级计算，应尽量下推到执行面本地完成。能力面可以定义断言 schema 和结果 contract，但不应成为海量 I/O 的中心节点。

### 4.5 Governance by Scope

所有 capability 都必须显式声明：

- 允许调用者
    
- Skill 作用域
    
- 文件边界
    
- 副作用级别
    
- 是否要求控制面授权
    

没有这些元信息的 capability，不应进入系统可用目录。

### 4.6 Append-Only Semantics for Key Facts

能力面返回的关键结构化事实，尤其与作业、工件、checkpoint、结果摘要相关的记录，应支持审计追溯和历史版本查询，不应依赖隐式覆盖式更新。

---

## 5. 实现形态

能力面不要求被实现成一个厚重、统一部署的“万能微服务”。更合理的实现形态是三层组合：

### 5.1 Capability Registry

集中维护 capability 元数据、版本、白名单、provider binding、错误码族、schema 引用、副作用标签与状态。

### 5.2 Thin Gateway / Resolver

在调用时，根据 caller、Skill、template、state、file boundary 等上下文解析 capability，并做权限校验、schema 校验、路由与审计注入。  
这层应尽量无状态、轻量，避免承载复杂业务运算。

### 5.3 Provider Adapters / Native Providers

真正执行 capability 的 provider 可以是：

- 自带 MCP server 的 Python worker
    
- 独立的 GIS / InVEST 工具包服务
    
- Catalog / Registry / Object Store 适配器
    
- 执行面暴露的标准作业接口
    
- 少量由平台维护的薄适配器
    

这意味着新增工具时，优先是“注册一个 provider 并声明 contract”，而不是“先改能力面核心服务代码”。

---

## 6. 能力分类

能力面建议保留八类 capability，但需要重新收紧边界。

### 6.1 Inspect Capabilities

作用：读取目录化元数据与受控探针事实。  
特点：只读、轻量、优先 Catalog、深探测受限。  
用途：服务认知面和规划面获取基础事实。

### 6.2 Validate Primitives

作用：提供底层约束检查原语与标准化 validator contract。  
特点：返回结构化证据或原子校验结果，不承担图级综合裁决。  
用途：供规划面和控制面组合使用。  
这类能力不是“替规划面生成 `static_validation_result`”，而是为其提供基础校验证据。

### 6.3 Planning Support Profiles

作用：提供模板能力画像、算子能力画像、输出契约定义、预处理片段目录、资源画像估计。  
特点：提供 profile 和 estimate，不做模板协商结论。  
用途：为规划面两程编译提供制度化输入。

### 6.4 Job Contract Capabilities

作用：提供标准化的作业提交、状态查询、取消、结果收集 contract。  
特点：属于系统级 contract；治理权在控制面；实现可直连 provider，不要求厚代理中转。  
用途：支撑控制面状态机与执行面连接。

### 6.5 Result Contract Capabilities

作用：把底层结果 bundle 转换成结构化事实、关键指标、质量标记和文件引用。  
特点：提取摘要，不做叙事解释。  
用途：为控制面和认知面的最终解释链提供事实输入。

### 6.6 Workspace Contract Capabilities

作用：标准化 workspace 的创建、查询、清理、归档、销毁语义。  
特点：高副作用；治理权归控制面；实现可由执行面 provider 承载。  
用途：服务控制面的终局闭环与失败治理。

### 6.7 Artifact Contract Capabilities

作用：标准化最终成果与审计工件的晋升、校验、索引与读取。  
特点：不是业务解释，而是结果持久化契约。  
用途：服务控制面的 `ARTIFACT_PROMOTING` 与终局清理。

### 6.8 Checkpoint / Recovery Contract Capabilities

作用：标准化 checkpoint ack、对齐查询、强制回退等恢复事务接口。  
特点：高度敏感，仅控制面可调用。  
用途：服务控制面的恢复事务与 `STATE_CORRUPTED` 治理。

---

## 7. 调用边界

### 7.1 认知面可直接调用

认知面只允许直接调用轻量只读能力和受控 validate primitive，主要包括：

- `inspect_catalog_view`
    
- `inspect_schema_view`
    
- `inspect_asset_probe_lite`（默认关闭，需受控放行）
    
- `validate_args_draft`
    
- `validate_slot_binding_primitive`
    
- `validate_input_compatibility_primitive`
    

认知面不允许直接调用作业、workspace、artifact、checkpoint 等高副作用 capability。认知面文档已经明确：作业控制接口由控制面协调，不属于其直接调用范围。

### 7.2 规划面可直接调用

规划面可直接调用 profile、estimate 和 validate primitive，主要包括：

- `get_template_capability_profile`
    
- `get_operator_capability_profile`
    
- `get_preprocess_fragment_catalog`
    
- `estimate_resource_profile`
    
- `check_tool_authorization`
    
- `check_output_contract`
    
- `probe_spatial_alignment_risk`
    
- `build_runtime_assertion_contract`
    
- `validate_graph_fragment_primitive`
    

这里的重点是：  
规划面可以调用“图级片段原语”与“图约束检查器”，但最终的 `static_validation_result`、`runtime_assertions`、`computation_load_metrics` 仍由规划面产出，而不是能力面产出。

### 7.3 控制面可直接调用

控制面可以调用所有治理相关 capability，包括：

- `submit_job_contract`
    
- `query_job_status_contract`
    
- `cancel_job_contract`
    
- `collect_result_bundle`
    
- `extract_result_facts`
    
- `create_workspace_contract`
    
- `cleanup_workspace_contract`
    
- `archive_workspace_contract`
    
- `destroy_workspace_contract`
    
- `promote_artifacts_contract`
    
- `verify_artifact_promotion`
    
- `checkpoint_resume_ack`
    
- `force_revert_checkpoint`
    
- `queue_re_evaluate_contract`
    

这些 capability 的治理权、调用时机和状态推进权在控制面；能力面只提供受控 contract。

### 7.4 执行面与能力面的关系

执行面不是能力面的上级或替代者，而是多个 capability 的 provider。  
尤其是：

- job contract
    
- workspace contract
    
- result contract 的原始数据
    
- runtime assertion compute
    
- artifact upload 的底层实现
    

都可能由执行面承载。能力面对这些能力的作用是标准化与治理，而不是吞掉执行面本身。

---

## 8. Capability Contract 结构

每个 capability 至少应包含以下元信息。

### 8.1 基础字段

- `capability_id`
    
- `capability_name`
    
- `capability_version`
    
- `category`
    
- `provider_type`
    
- `provider_binding`
    
- `status`
    

### 8.2 权限与作用域

- `allowed_callers`
    
- `skill_scope`
    
- `template_scope`
    
- `requires_control_plane_authority`
    
- `file_boundary_policy`
    

### 8.3 契约字段

- `input_schema_ref`
    
- `output_schema_ref`
    
- `error_schema_ref`
    
- `audit_schema_ref`
    

### 8.4 行为字段

- `side_effect_level`
    
- `idempotency_mode`
    
- `retry_policy`
    
- `timeout_policy`
    
- `cancellation_mode`
    
- `consistency_hint`
    

### 8.5 运行约束

- `expected_latency_class`
    
- `max_payload_size`
    
- `concurrency_limit`
    
- `quota_key`
    

---

## 9. 核心能力清单

下面给出建议保留的核心 capability，但按“contract / primitive / provider”三层理解，而不是理解为都由能力面核心代码亲自实现。

### 9.1 Inspect 类

#### `inspect_catalog_view`

读取 Metadata Catalog 视图，返回格式、CRS、bbox、字段摘要、宽高、版本、hash、parse_status 等基础事实。  
只读，优先级最高。

#### `inspect_asset_probe_lite`

在 Catalog 不足时做受限探针，例如波段数、NODATA 声明、局部空间统计。  
默认关闭，受策略控制。

#### `inspect_spatial_extent_profile`

返回范围、分辨率、对齐风险、投影冲突风险等摘要，为规划面提供输入证据。

### 9.2 Validate Primitive 类

#### `validate_args_draft`

基于 Skill 的 `parameter_schema.yaml` 与 `validation_policy.yaml`，校验参数草案。  
输出结构化缺失、非法值、范围警告。

#### `validate_slot_binding_primitive`

校验单个或局部 role binding 是否满足类型、来源、黑名单与版本要求。  
注意：它不产出全局图判决。

#### `validate_input_compatibility_primitive`

检查 CRS 兼容、栅格对齐、字段存在性、几何类型可接受性等输入兼容事实。

#### `validate_graph_fragment_primitive`

对局部图片段、节点依赖、输出契约或算子组合做原子级检查。  
它服务规划面，但不替代规划面产出完整静态图结论。

### 9.3 Planning Support 类

#### `get_operator_capability_profile`

返回算子允许输入、输出约束、可重写模式、代价等级、可并行性等。

#### `get_template_capability_profile`

返回模板可绑定逻辑角色、可插入预处理片段、输出契约、降级关系与科学假设画像。

#### `get_preprocess_fragment_catalog`

返回允许插入的 preprocess 片段目录。

#### `estimate_resource_profile`

返回内存、磁盘、时间、能力需求的资源画像估计。  
这里是“画像估计”，不是“最终放行结论”。

#### `check_output_contract`

检查某模板/图片段是否满足输出契约要求。

#### `build_runtime_assertion_contract`

生成运行时断言 contract 的标准 schema 和表达容器，但不执行大数据断言计算。

### 9.4 Job Contract 类

#### `submit_job_contract`

提供统一的作业提交 contract。  
控制面调用时，背后可直达执行面 provider。  
不要求厚重中转服务。

#### `query_job_status_contract`

返回作业状态、heartbeat、阶段摘要、失败节点信息。

#### `cancel_job_contract`

提供标准取消语义。  
需支持 `SOFT_CANCEL` / `HARD_CANCEL` 或等价取消模式声明。

#### `collect_result_bundle`

返回结果 bundle 引用与原始摘要。

### 9.5 Result Contract 类

#### `extract_result_facts`

将结果 bundle 转换为关键指标、主要输出引用、质量标记和统计摘要。

#### `summarize_quality_flags`

输出结果质量、局限性、不确定性和异常标志。

### 9.6 Workspace Contract 类

#### `create_workspace_contract`

创建运行 workspace。

#### `cleanup_workspace_contract`

清理非持久化中间产物。

#### `archive_workspace_contract`

只读归档失败现场或保留现场。

#### `destroy_workspace_contract`

终局销毁 workspace。

### 9.7 Artifact Contract 类

#### `promote_artifacts_contract`

将 final outputs 与审计工件晋升至持久化存储。

#### `verify_artifact_promotion`

校验上传成功、checksum 一致性与可读性。

### 9.8 Checkpoint / Recovery Contract 类

#### `checkpoint_resume_ack`

返回已落盘 checkpoint 的正式 ack。

#### `force_revert_checkpoint`

要求 provider 回退到最近承认 checkpoint。

---

## 10. Runtime Assertion 的职责边界

这一部分必须单独写清楚。

### 10.1 规划面负责什么

规划面负责生成 `runtime_assertions`，即断言规则、断言类型、严重性和插入点。

### 10.2 能力面负责什么

能力面负责：

- 断言 contract 的 schema
    
- 断言结果的标准返回格式
    
- 断言 provider 的注册与路由规则
    
- 断言相关错误码与审计语义
    

### 10.3 执行面负责什么

执行面负责对海量数据进行本地断言计算。  
例如 NODATA 比例、像元范围、局部统计、文件完整性、临时输出一致性等，应在 worker 本地就近执行，然后把结果以标准 contract 回传控制面。

### 10.4 明确禁止

能力面不得成为大 TIFF、大矢量、大结果文件的中心化断言计算节点。  
任何需要跨网络搬运大量业务数据到能力面再做判断的实现，都应视为错误实现。

---

## 11. Job / Workspace / Artifact 的地位

这一部分也必须明确，避免“完全剥离”与“厚代理”两种极端。

### 11.1 它们仍属于 capability contract 范畴

`submit_job`、`cleanup_workspace`、`promote_artifacts` 等仍应在能力面中以 contract 存在。  
原因是控制面需要统一、稳定、可审计、可版本化的调用语义，而不应直接散乱耦合到底层 MQ、对象存储 SDK、执行节点私有 RPC。

### 11.2 但不要求集中厚代理

这些 capability 不要求必须先到一个独立部署的能力面服务再二次转发。  
更合理的做法是：

- 由 Capability Registry 解析到对应 provider
    
- 由 thin gateway / internal client 完成权限校验和 schema 校验
    
- 再直接调用底层 provider
    

也就是说：

> **它们属于能力面契约层，但不必属于能力面重型中转层。**

### 11.3 治理权归控制面

能力面只提供 contract；  
什么时候调用、调用失败如何裁决、是否进入排队、是否进入终局清理，都由控制面决定。

---

## 12. Provider 扩展模型

为了避免能力面变成巨型 wrapper 服务，扩展应遵循 provider-first 模式。

### 12.1 Provider 自注册

新的 Python worker、InVEST runtime 包、GIS 工具包、结果抽取器、workspace handler 可以通过标准 provider manifest 注册到 Capability Registry。

### 12.2 Registry 只收元信息

注册内容包括：

- provider id
    
- provider version
    
- 暴露的 capability 列表
    
- schema 版本
    
- 副作用等级
    
- 调用边界
    
- 依赖环境
    
- 健康检查信息
    

### 12.3 适配器只做少量薄封装

平台只为少量跨语言、跨协议、跨系统边界的关键能力写适配器，例如：

- 执行面 job adapter
    
- 对象存储 artifact adapter
    
- checkpoint adapter
    
- catalog adapter
    

而不是为每个工具都手工重包一层。

### 12.4 Skill 与 Provider 的关系

Skill 不直接绑定 provider；  
Skill 绑定的是 capability 名。Registry 再把 capability 解析到合适 provider。  
这样 Skill 才能保持配置式扩展，而不被底层实现细节锁死。

---

## 13. Capability Registry 设计

Registry 至少应维护以下信息：

- `capability_id`
    
- `capability_name`
    
- `capability_version`
    
- `category`
    
- `status`
    
- `provider_binding`
    
- `allowed_callers`
    
- `skill_scope`
    
- `template_scope`
    
- `side_effect_level`
    
- `input_schema_ref`
    
- `output_schema_ref`
    
- `error_codes`
    
- `audit_level`
    
- `deprecation_policy`
    

Registry 还应支持：

- capability 查询
    
- Skill scope 校验
    
- provider 健康状态感知
    
- 版本兼容判断
    
- deprecated / disabled 状态检查
    

---

## 14. 错误码与审计语义

### 14.1 错误码分类

建议至少分为：

- Inspect 类：`CATALOG_MISS`、`PROBE_FORBIDDEN`、`ASSET_UNREADABLE`
    
- Validate Primitive 类：`SCHEMA_INVALID`、`ROLE_BINDING_INVALID`、`INPUT_INCOMPATIBLE`
    
- Planning Support 类：`PROFILE_NOT_FOUND`、`PREPROCESS_FRAGMENT_UNAVAILABLE`
    
- Job Contract 类：`JOB_SUBMIT_FAILED`、`JOB_NOT_FOUND`、`JOB_CANCEL_REJECTED`
    
- Workspace 类：`WORKSPACE_CREATE_FAILED`、`WORKSPACE_CLEANUP_FAILED`
    
- Artifact 类：`PROMOTION_FAILED`、`CHECKSUM_MISMATCH`
    
- Checkpoint 类：`CHECKPOINT_ACK_TIMEOUT`、`CHECKPOINT_REVERT_FAILED`
    

### 14.2 审计字段

每次 capability 调用至少应记录：

- `capability_id`
    
- `capability_version`
    
- `caller_layer`
    
- `task_id`
    
- `state_version`
    
- `planning_revision` / `checkpoint_version` / `run_revision_id`（按适用情况）
    
- `input_hash`
    
- `provider_binding`
    
- `result_status`
    
- `error_code`
    
- `side_effect_level`
    
- `started_at`
    
- `finished_at`
    

高副作用能力还应追加：

- workspace 影响
    
- artifact 影响
    
- checkpoint 影响
    
- 取消模式
    
- 幂等键
    

---

## 15. 落地顺序

### 第一阶段：Registry 与 Scope

先实现 Capability Registry、Skill scope 解析、caller 白名单和 schema 注册。  
如果这一步不先完成，后续所有 capability 都没有治理边界。

### 第二阶段：只读与轻量 primitive

优先落地：

- `inspect_catalog_view`
    
- `inspect_schema_view`
    
- `validate_args_draft`
    
- `validate_slot_binding_primitive`
    
- `validate_input_compatibility_primitive`
    

因为认知面最先依赖这些能力。

### 第三阶段：规划支撑 profile 与 estimate

实现：

- `get_template_capability_profile`
    
- `get_operator_capability_profile`
    
- `get_preprocess_fragment_catalog`
    
- `estimate_resource_profile`
    
- `check_output_contract`
    
- `build_runtime_assertion_contract`
    

这样规划面的两程编译才有制度化输入。

### 第四阶段：Job / Workspace / Artifact contract

实现 contract 和 provider binding，而不是先做厚代理服务：

- `submit_job_contract`
    
- `query_job_status_contract`
    
- `cancel_job_contract`
    
- `create_workspace_contract`
    
- `cleanup_workspace_contract`
    
- `promote_artifacts_contract`
    

这样控制面的主状态机才能真正落到运行系统上。

### 第五阶段：Checkpoint / Recovery contract

实现：

- `checkpoint_resume_ack`
    
- `force_revert_checkpoint`
    

这一步直接服务控制面的恢复事务与 `STATE_CORRUPTED` 治理。

### 第六阶段：Provider 生态扩展

最后再逐步让 Python worker、模型 runtime、结果抽取器、artifact handler 等 provider 通过注册机制接入，而不是不断改能力面核心逻辑。

---

## 16. 结论

能力面应当被定义为**系统的能力契约层**，而不是厚重的万能代理层。它既不能上浮成第二个规划面和第二个控制面，也不能降格成只做透明转发的空心网关。

更合适的形态是：

- 在语义上，它是 capability contract 的唯一合法出口；
    
- 在治理上，它是白名单、作用域、schema、副作用和审计的实施层；
    
- 在实现上，它应尽量轻，优先采用 registry、thin gateway 和 provider-first 模式；
    
- 在边界上，它提供证据、原语、contract 和路由，不承担业务裁决与海量数据计算；
    
- 在系统协同上，它为认知面、规划面、控制面提供稳定调用语言，而不抢夺它们各自的职责。
    

当能力面按这个方式收紧之后，它既能保住总体设计里“标准化能力暴露、确定性调用、可审计约束”的核心价值，也能避免膨胀成性能最差、发版最频繁、边界最混乱的中间大泥球。

---
## 17. 协议实现约束

本节用于约束能力面在工程实现阶段的协议形态、网关行为、上下文隔离方式与版本注册规则，防止能力契约层在实际开发中退化为阻塞式中转服务、隐式有状态网关或不可回放的版本覆盖系统。

### 17.1 长时能力必须采用异步契约，禁止同步阻塞调用

能力面虽然统一暴露 capability contract，但并非所有 capability 都适合采用同步工具调用语义。凡涉及长时运行、跨节点执行、对象存储上传、终局清理等可能超过短请求窗口的能力，必须采用**异步受理 + 后续查询/事件跟踪**模式，而不得在网关层维持长连接阻塞等待。

以下 capability 默认必须采用异步契约：

- `submit_job_contract`
    
- `promote_artifacts_contract`
    
- `destroy_workspace_contract`
    
- 其他预计无法在短时窗口内稳定完成的高副作用能力
    

其协议要求如下：

第一，受理请求后必须在短时间内立即返回受理结果，例如：

```json
{
  "request_id": "req_xxx",
  "operation_id": "op_xxx",
  "status": "ACCEPTED"
}
```

或：

```json
{
  "job_id": "job_xxx",
  "status": "ACCEPTED"
}
```

第二，后续状态必须通过以下至少一种方式获取：

- `query_*_status_contract`
    
- 事件流 / 回调
    
- 控制面主状态机中的异步轮询链路
    

第三，网关层不得为了“等一个完整结果”而把 HTTP / gRPC 请求挂到执行结束。能力面提供的是**异步 contract**，不是长时阻塞通道。

第四，任何高耗时 capability 若需要同步模式，必须明确标注为仅限内部短任务场景使用，不得作为默认协议暴露给控制面主链路。

---

### 17.2 Adapter 只允许做协议与安全适配，禁止承载数据计算与聚合逻辑

能力面中的 Selected Adapters 只用于解决少量跨协议、跨认证机制、跨系统边界的必要适配问题，不得成为逃逸式业务逻辑容器。

Adapter 允许承担的职责仅包括：

- 协议映射，例如 HTTP ↔ gRPC、内部 RPC ↔ MCP
    
- 请求签名追加
    
- caller 身份透传
    
- capability 元信息注入
    
- schema 外层封套统一化
    
- 审计字段附加
    

Adapter 明确禁止承担以下职责：

- 业务计算
    
- 图级判断
    
- 结果聚合
    
- 大 payload 解析与重组
    
- 多 provider 联合编排
    
- 数据清洗与字段再解释
    
- provider 输出的语义补丁式修正
    
- 跨节点结果拼接
    

凡出现“因为 provider 输出太丑，所以在 adapter 里改一下”的实现倾向，都应视为越界信号。  
数据格式、字段约定和返回语义的修正，必须由对应 provider 自身收敛，而不是把问题转嫁到能力面 adapter。

可以用一句硬约束概括：

> **Adapter 只做 transport / auth / envelope，不做 compute / aggregate / reinterpret。**

---

### 17.3 Policy 层必须基于显式注入上下文做沙箱判定，禁止运行时查库补语义

能力面的 Policy 层承担白名单、调用者约束、文件边界和作用域隔离等职责，但它本身应保持轻量和近似无状态。为了避免在每次 capability 调用时回查数据库、联表计算文件边界，调用链必须遵循**上下文注入规范**。

#### 17.3.1 调用方必须注入受控上下文

认知面、规划面、控制面在调用能力面时，必须显式注入 `Task_Context` 或等价结构，至少包括：

- `task_id`
    
- `state_version`
    
- `caller_layer`
    
- `workspace_id`
    
- `allowed_paths`
    
- `skill_scope`
    
- `template_scope`（如适用）
    
- `run_revision_id` / `preprocess_run_revision_id`（如适用）
    

建议形式可以是请求头签名对象，或 payload 根节点中的受签名上下文块，例如：

```json
{
  "task_context": {
    "task_id": "task_001",
    "state_version": 17,
    "caller_layer": "CONTROL_PLANE",
    "workspace_id": "workspace_A",
    "allowed_paths": ["/data/workspace_A"],
    "skill_scope": ["invest_water_yield"],
    "run_revision_id": "run_v3",
    "signature": "..."
  },
  "input": { ... }
}
```

#### 17.3.2 Policy 层只做轻量判定

Policy 层只应做：

- caller 是否允许
    
- capability 是否在 Skill scope 内
    
- 请求路径是否落在 `allowed_paths` 内
    
- provider 是否与当前 capability 绑定
    
- 文件边界是否越权
    

这些判断必须尽量基于当前请求已注入的上下文和 registry 元信息完成，而不是运行时回查业务库推导“这个路径到底归谁”。

#### 17.3.3 禁止隐式恢复上下文

如果请求缺失关键上下文，能力面应直接拒绝调用，而不是尝试通过数据库或外部系统“自动猜出来”。  
能力面的策略层不负责补全业务上下文，只负责校验已提供上下文是否足以支撑安全调用。

可以用一句制度化表述概括：

> **Policy 只校验上下文，不推导上下文。**

---

### 17.4 Registry 必须支持多版本并存，禁止新 provider 覆盖旧 contract

Capability Registry 不能把 provider 新注册的版本直接覆盖为“当前唯一版本”。  
对于带 schema 约束、参数规则和行为语义的 capability，必须强制执行**语义化版本并存策略**，以保证已经生成中的任务、已冻结的 Manifest、排队中的作业和待恢复的任务不会因为 provider 升级而失效。

#### 17.4.1 注册粒度必须包含版本

Registry 中的 capability 唯一标识必须至少包含：

- `capability_name`
    
- `capability_version`
    

例如：

- `invest_water_yield@3.13`
    
- `invest_water_yield@3.14`
    

而不是只保留一个裸名字再被新版本覆盖。

#### 17.4.2 路由必须严格按绑定版本解析

能力解析时，不得默认“路由到最新版本”。  
必须优先根据以下对象显式锁定版本：

- Skill profile 中锁定的 capability version
    
- Planning 结果中声明的 template / operator capability version
    
- TaskState 中已有的 provider binding
    
- AnalysisManifest 中冻结的 contract version
    

#### 17.4.3 旧版本必须允许与新版本并存

只要系统中仍存在下列任一对象引用旧版本 capability，就不得直接移除旧版本：

- `WAITING_USER` 任务
    
- `WAITING_SYSTEM` 任务
    
- `RESUMING` 任务
    
- `QUEUED` 任务
    
- 已冻结但尚未终局的 Manifest
    
- 可审计回放仍依赖旧 contract 的历史任务
    

旧版本可以被标记为 `DEPRECATED`，但不能在仍被活动任务引用时直接 `DISABLED` 或被新版本覆盖。

#### 17.4.4 升级策略必须分为注册、默认切换、最终下线三个阶段

建议采用三阶段版本治理：

- **注册（Registered）**：新版本进入 Registry，但不自动成为默认路由目标
    
- **切换（Preferred）**：仅新任务默认使用新版本，旧任务继续绑定旧版本
    
- **下线（Disabled/Retired）**：在确认无活动任务和无审计依赖后再下线旧版本
    

可以用一句硬约束概括：

> **Registry 解析版本，不替任务改版本。**

---

## 17.5 建议追加的协议元字段

为了让上述四条约束在代码层真正可执行，建议 capability contract 再增加以下字段：

- `interaction_mode`：`SYNC` / `ASYNC`
    
- `adapter_restrictions`：声明该 capability 的 adapter 是否允许仅做 envelope/auth 映射
    
- `requires_task_context`：是否必须注入 `Task_Context`
    
- `context_fields_required`：所需上下文字段列表
    
- `version_resolution_mode`：`EXACT` / `COMPATIBLE_RANGE` / `LATEST_ALLOWED`
    
- `provider_lifecycle_state`：`ACTIVE` / `PREFERRED` / `DEPRECATED` / `DISABLED`
    

其中：

- Job、Artifact、Workspace 这类高副作用 contract，默认应为 `ASYNC`
    
- 大多数生产链路 capability，默认应要求 `requires_task_context = true`
    
- 与已冻结 Manifest 相关的 capability，默认应采用 `version_resolution_mode = EXACT`
    

---

## 17.6 研发测试约束

为了防止这些协议陷阱只停留在文档里，建议在联调测试中必须覆盖以下场景：

第一，调用 `submit_job_contract` 后，网关在短时间内返回 `ACCEPTED`，而不是等待执行完成。

第二，adapter 在面对畸形 provider 输出时，不允许在网关内做业务字段重算或跨 provider 聚合。

第三，请求缺失 `allowed_paths` 或 `workspace_id` 时，Policy 层直接拒绝，而不是回查数据库猜测路径归属。

第四，注册 `invest_water_yield@3.14` 后，仍能正确恢复和执行绑定 `invest_water_yield@3.13` 的旧任务。

第五，已冻结 Manifest 在 provider 升级后，仍能按原 capability version 被正确解析。

---

## 17.7 结论

能力面的宏观边界已经明确；这一节补上的，是**协议落地时不该踩的坑**。

这四条约束共同保证了三件事：

- 长时能力不会被错误实现为同步阻塞工具调用
    
- adapter 不会重新膨胀成业务逻辑垃圾场
    
- 轻量 policy 不会因为隐式查库而变成伪状态服务
    
- provider 升级不会摧毁已在运行中的旧任务和历史冻结对象
    

这样，能力面才能真正保持“轻实现、重契约”的定位，而不是在研发落地过程中再次滑回厚重中间层。