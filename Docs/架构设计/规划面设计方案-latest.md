
**面向生态问题智能分析的确定性编排层（最终版）**

## 1. 文档定位

规划面是整个系统中的确定性编排层，负责把认知面输出的结构化认知结果编译为可执行的分析草案与执行图，并在校验反馈驱动下完成受控图重写、标准预处理片段插入、图规范化、运行时断言生成和执行前编排对象输出。

规划面位于认知面与执行面之间，由控制面统一调度。它不负责用户语义理解，不持有主状态机，不直接提交作业，也不直接执行任何长时计算任务。规划面的职责是把“已经理解的问题”和“已经绑定的输入”转化为“结构合法、约束明确、可验证、可审计、可执行”的正式编排对象。

规划面的核心定位是：

> **一个受模板、策略、工具授权和科学约束共同驱动的工业级确定性编译器。**

---

## 2. 设计目标

规划面的设计目标包括以下九项。

第一，规划面必须以模板为核心完成执行图编译。模板不是提示文本，而是某类分析任务的受约束执行骨架，定义逻辑输入角色、节点骨架、工具映射、预处理片段、输出契约和可接受的修复空间。

第二，规划面必须采用**两程编译（Two-Pass Compilation）**。第一程只完成逻辑编译，声明模板、角色、槽位和逻辑中间产物；第二程在真实绑定结果和目录元数据到位后，完成实体化编译、派生虚拟资产推导和运行时断言生成。

第三，规划面必须承担图级修复，而不是语义级修复。需要改变图结构的修复，包括模板变体切换、预处理节点插入、图结构调整、边重连和片段替换，全部由规划面完成。

第四，规划面必须在真正执行前完成静态合法性校验，确保图结构、工具授权、角色绑定、输出路径和断言需求完整。

第五，规划面必须支持模板协商。当当前模板无法满足输入条件时，系统应在控制面调度下尝试替代模板或降级模板，但任何影响核心科学假设的模板变化都必须受用户确认约束。

第六，规划面必须具备有限的模式匹配式预处理链规范化能力，用于消除重复、压缩等价空间变换链、稳定图表示，并避免不必要的局部膨胀。

第七，规划面必须具备**计算量感知**而不是资源调度能力。它需要输出标准化的计算负载指标和修复建议，但不负责最终放行决策。

第八，规划面必须能够识别空间覆盖风险、空间对齐风险和黑名单约束，并把这些风险表达为断言、标记和建议，而不是伪精确结论。

第九，规划面必须输出 revision 化、可签名、可审计、可回放的编排对象，供控制面冻结 Analysis Manifest，并供执行面直接消费。

---

## 3. 职责边界

### 3.1 规划面负责什么

规划面负责：

- 选择模板并声明逻辑输入角色与槽位定义
    
- 生成逻辑执行图骨架
    
- 构造逻辑中间产物规格
    
- 在认知面返回真实绑定结果后完成实体化编译
    
- 推导派生虚拟资产
    
- 执行图级静态校验
    
- 执行图级修复与图重写
    
- 插入标准 GIS 预处理片段
    
- 执行预处理链规范化
    
- 生成运行时断言
    
- 生成图签名与规划摘要
    
- 输出执行前 Manifest 所需的规划侧载荷
    
- 输出计算负载指标与修复建议
    
- 在模板协商中选择可行替代模板
    

### 3.2 规划面不负责什么

规划面不负责：

- 用户意图理解
    
- Skill 路由
    
- 参数语义生成
    
- 用户交互与确认呈现
    
- 主状态机持有
    
- Manifest 最终冻结
    
- 作业提交、取消、状态查询与结果拉取
    
- Worker 调度
    
- 执行后解释文本生成
    
- 最终资源放行判断
    
- 像素级事实验证
    

---

## 4. 设计原则

### 4.1 模板优先于自由规划

执行图必须来源于模板体系、工具授权规则和确定性编译逻辑，不依赖模型自由输出完整 DAG。

### 4.2 确定性优先于生成式

规划面的核心逻辑必须由确定性代码实现。模型可以通过认知面提供策略标记，但规划面不承担开放式自由生成。

### 4.3 两程编译优先

未完成 Pass 1 的角色声明，不得进入参数绑定；未完成 Pass 2 的实体化编译，不得进入最终静态放行。

### 4.4 图级修复优先于用户返工

若错误属于系统可治理的标准 GIS 不兼容问题，应优先通过图重写插入标准预处理片段，而不是立即要求用户返工。

### 4.5 修复受策略约束

所有图重写都必须受 `repair_policy`、模板约束、工具授权、科学要求和控制面调度约束。

### 4.6 优化受限于模式白名单

规划面不是通用优化器。预处理链规范化只能针对已注册、可证明安全、可调试的算子模式执行，禁止对黑盒业务节点或未知算子进行自动合并。

### 4.7 风险表达优先于伪精确预估

在缺乏像素级事实的前提下，规划面不能输出伪精确的有效像元数，而应输出风险标签、断言需求和必要的后续检查建议。

### 4.8 放行决策权属于控制面

规划面只输出标准化的计算量指标与修复建议，不负责最终资源可执行性判断。

### 4.9 规划面必须无状态

规划面不得私自缓存黑名单、候选可用性或版本状态；所有选择与编译必须基于控制面下发的当前快照重新计算。

---

## 5. 总体工作方式

规划面在控制面调度下分两程工作：

```text
Pass 1：Logical Compilation
输入：
- goal_parse
- skill_route
- strategy_flags
- available_asset_snapshot
- blacklist_context
输出：
- selected_template
- template_version
- logical_input_roles
- slot_schema_view
- binding_constraints
- graph_skeleton
- logical_artifact_specs
- template_negotiation_metadata

Pass 2：Materialized Compilation
输入：
- slot_bindings
- args_draft
- metadata_catalog_facts
- graph_skeleton
- available_asset_snapshot
输出：
- materialized_execution_graph
- derived_virtual_assets
- runtime_assertions
- computation_load_metrics
- execution_graph_digest
- planning_summary
- planning_manifest_payload
```

控制面负责在两程之间切换，调度 validator，决定是否触发模板协商、图重写、断言强化、执行前冻结和执行放行。

---

## 6. 规划状态模型

### 6.1 PlanningState

规划面维护独立的 `PlanningState`，建议至少包含：

- `selected_template`
    
- `template_version`
    
- `template_selection_reason`
    
- `logical_input_roles`
    
- `slot_schema_view`
    
- `binding_constraints`
    
- `template_negotiation_metadata`
    
- `graph_skeleton`
    
- `logical_artifact_specs`
    
- `materialized_execution_graph`
    
- `derived_virtual_assets`
    
- `rewrite_registry`
    
- `rewrite_rounds`
    
- `static_validation_result`
    
- `runtime_assertions`
    
- `computation_load_metrics`
    
- `execution_graph_digest`
    
- `planning_summary`
    
- `planning_manifest_payload`
    
- `planning_revision`
    

### 6.2 状态要求

1. `graph_skeleton` 与 `materialized_execution_graph` 必须分离保存。
    
2. 每次模板重选、实体化编译、图重写或规范化后都必须提升 `planning_revision`。
    
3. 任意 revision 都必须可回放。
    
4. `rewrite_registry` 必须可审计。
    
5. 图签名必须以规范化后的图为准。
    
6. 规划状态不得替代控制面的主任务状态。
    

---

## 7. 模板体系设计

### 7.1 模板定义

模板是某类生态分析任务在给定 Skill 下的受约束执行骨架。每个模板至少应表达：

- 适用 Skill
    
- 必需逻辑输入角色
    
- 可选逻辑输入角色
    
- 节点骨架
    
- 工具绑定
    
- 允许的重写范围
    
- 可插入的预处理片段
    
- 输出契约
    
- 断言注入点
    
- 降级关系与替代关系
    
- 科学假设画像
    

### 7.2 模板最小字段

每个模板至少包含：

- `template_id`
    
- `template_version`
    
- `applicable_skill`
    
- `required_roles`
    
- `optional_roles`
    
- `required_nodes`
    
- `optional_nodes`
    
- `edge_blueprint`
    
- `tool_bindings`
    
- `rewrite_rules_allowed`
    
- `runtime_assertion_points`
    
- `output_contract`
    
- `downgrade_candidates`
    
- `scientific_assumption_profile`
    
- `template_strictness_impact`
    

### 7.3 模板层级

规划面应支持三层模板资产：

- **主模板**：定义主流程骨架
    
- **变体模板**：定义不同输入条件或策略条件下的模板分支
    
- **片段模板**：定义可插入的 preprocess、summarize、extract 子图
    

---

## 8. Operator Capability Profile

### 8.1 目标

规划面中的算子合并与规范化能力不得硬编码在编译器内部，而必须来源于外部能力画像。

### 8.2 定义

`Operator Capability Profile` 是全局算子能力描述资产，用于定义：

- 算子归属家族
    
- 是否为黑盒
    
- 与哪些算子兼容
    
- 哪些模式允许合并
    
- 哪些模式仅允许去重
    
- 哪些模式禁止重排
    
- 是否具备可调试中间输出
    

### 8.3 最小字段

每个算子能力画像至少包含：

- `operator_id`
    
- `operator_family`
    
- `blackbox`
    
- `compatible_with`
    
- `mergeable_patterns`
    
- `normalization_constraints`
    
- `debug_visibility_level`
    

### 8.4 规则

1. 规划面不得硬编码通用算子合并规则。
    
2. 所有 Rewrite Normalization 的可合并模式，必须来源于 `Operator Capability Profile`。
    
3. 规划面只负责模式匹配与安全性检查，不负责定义算子能力本身。
    

---

## 9. Pass 1：逻辑编译

## 9.1 Template Selector

### 职责

根据 `skill_route`、`goal_parse`、`strategy_flags`、`available_asset_snapshot` 和 `blacklist_context` 选择当前最合适的模板，并声明逻辑输入角色和槽位定义。

### 输入

- `goal_parse`
    
- `skill_route`
    
- `strategy_flags`
    
- `template_catalog`
    
- `available_asset_snapshot`
    
- `blacklist_context`
    
- `state_version`
    

### 输出

- `selected_template`
    
- `template_version`
    
- `template_selection_reason`
    
- `logical_input_roles`
    
- `slot_schema_view`
    
- `binding_constraints`
    
- `template_negotiation_metadata`
    

### 设计要求

- 选择过程必须确定性
    
- 多模板候选时必须有显式优先级
    
- 若某模板的 `required_roles` 当前只能由黑名单资产满足，且无其他合法候选，则该模板分数必须归零
    
- 失败时必须返回结构化原因
    
- 只要模板变化影响核心科学假设，就必须设置 `requires_confirmation = true`
    

---

## 9.2 Graph Skeleton Builder

### 职责

将模板渲染为逻辑执行图骨架 `graph_skeleton`，但不推导具体实体属性。

### 输出

- `graph_skeleton`
    

### 设计要求

- 图必须是 DAG 骨架
    
- 必须声明 required outputs
    
- 必须声明关键节点和逻辑边
    
- 不得在此阶段推导具体 CRS、BBox、grid、内存占用或文件路径
    

---

## 9.3 Logical Artifact Builder

### 职责

构造逻辑中间产物规格 `logical_artifact_specs`。

### 输出

- `logical_artifact_specs`
    

### 要求

每个逻辑占位至少包含：

- `artifact_id`
    
- `artifact_type`
    
- `producer_node`
    
- `consumer_nodes`
    
- `role`
    
- `expected_generation_mode`
    

Pass 1 逻辑占位不包含具体空间元数据，仅描述“会产生什么”和“由谁产生”。

---

## 10. 模板协商机制（Template Negotiation）

### 10.1 目标

当当前模板的关键角色无法满足时，系统应优先尝试模板协商，而不是立即挂起用户。

### 10.2 触发来源

模板协商由控制面调度，但通常由认知面发起 `REQUEST_TEMPLATE_DOWNGRADE` 意图。规划面负责重新选择模板。

### 10.3 协商输入

- `missing_roles`
    
- `goal_parse.core_requirements`
    
- `strictness_level`
    
- `downgrade_policy`
    
- 当前模板的 `downgrade_candidates`
    
- `blacklist_context`
    

### 10.4 协商规则

1. 仅当替代模板不违背核心科学要求时，允许静默切换。
    
2. 一旦替代模板改变核心模型、核心输入或结果口径，必须设置 `requires_confirmation = true`。
    
3. 若无可行替代模板，则返回结构化失败并由控制面进入 `WAITING_USER`。
    
4. 黑名单导致的角色不可满足，必须作为正式协商原因进入记录。
    

### 10.5 协商记录

所有模板协商与降级必须写入：

- `planning_summary.downgrade_history`
    
- `planning_manifest_payload`
    
- `task_trace_summary.downgrade_history`
    

---

## 11. Pass 2：实体编译

## 11.1 Materialized Compilation

### 职责

在认知面返回 `slot_bindings` 和 `args_draft` 后，结合目录元数据和逻辑骨架，把执行图实体化。

### 输入

- `slot_bindings`
    
- `args_draft`
    
- `metadata_catalog_facts`
    
- `graph_skeleton`
    
- `available_asset_snapshot`
    

### 输出

- `materialized_execution_graph`
    

### 设计要求

- 必须把每个角色绑定到具体输入
    
- 必须补齐工具绑定、输入绑定、输出占位
    
- 必须为静态校验和运行时断言生成必要上下文
    

---

## 11.2 Derived Virtual Asset Builder

### 职责

根据真实绑定后的目录元数据，推导派生虚拟资产。

### 输出

- `derived_virtual_assets`
    

### 要求

每个派生虚拟资产至少包含：

- `artifact_id`
    
- `source_bindings`
    
- `derived_crs`
    
- `derived_extent_expectation`
    
- `derived_grid_expectation`
    
- `snap_to_grid_requirement`
    
- `alignment_expectation`
    
- `spatial_coverage_risk_tag`
    
- `nodata_risk_tag`
    
- `soft_validation_scope`
    
- `assertion_requirements`
    

### 规则

1. 派生虚拟资产是条件性事实对象，只能用于软校验和断言生成。
    
2. 规划面不得在缺乏像素级事实时输出伪精确的 `valid_pixel_estimate`。
    
3. 对空间覆盖质量的判断必须降级为风险标签，例如：
    
    - `LOW`
        
    - `MEDIUM`
        
    - `HIGH`
        
    - `UNKNOWN`
        

### 风险升级契约

当 `spatial_coverage_risk_tag` 或 `nodata_risk_tag` 为 `HIGH` 或 `UNKNOWN` 时，控制面不得忽略。必须至少执行以下之一：

- 强制注入对应 Runtime Assertion
    
- 触发轻量级额外校验能力
    
- 请求用户确认风险接受
    

---

## 12. Execution Graph 对象设计

### 12.1 节点字段

每个节点至少包含：

- `node_id`
    
- `node_type`
    
- `tool_ref`
    
- `input_bindings`
    
- `output_artifacts`
    
- `node_constraints`
    
- `assertion_flags`
    

### 12.2 边字段

每条边至少包含：

- `from_node`
    
- `to_node`
    
- `artifact_ref`
    
- `binding_role`
    

### 12.3 图级字段

执行图至少包含：

- `graph_id`
    
- `graph_version`
    
- `skill_id`
    
- `template_ref`
    
- `nodes`
    
- `edges`
    
- `required_outputs`
    
- `graph_constraints`
    

### 12.4 图要求

- 必须是 DAG
    
- 必须可拓扑排序
    
- 必须存在 summarize / extract 路径
    
- 所有工具必须来自授权集合
    
- 节点总数、最大链深和最大 preprocess 链长度必须受限
    

---

## 13. Static Graph Validator

### 13.1 职责

对实体化后的执行图做静态合法性检查。

### 13.2 校验范围

#### 图结构级

- 是否为 DAG
    
- 是否存在孤立节点
    
- 是否存在断裂边
    
- 是否存在循环依赖
    

#### 语义级

- 是否缺失 required node
    
- 是否缺失 summarize / extract
    
- 是否存在未绑定 required role
    
- 是否存在未声明工具
    

#### 策略级

- 是否违反 `repair_policy`
    
- 是否违反 `mcp_tools_map`
    
- 是否违反模板允许的 rewrite 范围
    
- 是否违反 `goal_parse.core_requirements`
    

#### 复杂度级

- 节点数上限
    
- 链深上限
    
- preprocess 连续链长度上限
    

#### 负载统计级

- `pixel_count_estimate`
    
- `spatial_scope_ratio`
    
- `estimated_io_class`
    
- `load_risk_tag`
    

### 13.3 输出

- `static_validation_result`
    
- `graph_warnings`
    
- `graph_error_code`
    
- `graph_error_context`
    
- `computation_load_metrics`
    

### 13.4 负载规则

规划面只负责输出**计算量统计与修复建议**，不负责最终放行。  
若图中存在与 `goal_parse.spatial_scope` 明显不匹配的大范围高分辨率输入，且缺乏合理降载措施，则规划面必须输出诸如 `REQUEST_AUTO_CLIP` 的建议，但最终是否采纳由控制面决定。

---

## 14. Graph Rewriter

### 14.1 定位

Graph Rewriter 是规划面的图级修复执行器。它只在控制面调度下触发。

### 14.2 触发来源

- Validator 返回图级修复错误
    
- 控制面根据 `repair_proposal` 判定需要图级变更
    
- 静态图校验失败
    
- 模板协商结果要求切换模板或片段
    

### 14.3 可执行动作

- 插入 preprocess 片段
    
- 替换工具实现
    
- 调整局部边连接
    
- 补充 summarize / extract 路径
    
- 调整中间产物绑定
    
- 重新选择模板变体
    

### 14.4 不可执行动作

- 自由创造模板外主流程
    
- 绕过 required node
    
- 忽略工具授权
    
- 修改执行结果语义
    
- 替代认知面的 Skill 决策
    

---

## 15. 标准 GIS 预处理片段

规划面应支持以下标准预处理片段：

- `reproject_raster`
    
- `clip_raster_to_aoi`
    
- `resample_to_target_grid`
    
- `reproject_vector`
    
- `normalize_table_fields`
    

### 15.1 典型触发规则

#### `CRS_MISMATCH`

插入：

- `reproject_raster`  
    或
    
- `reproject_vector`
    

#### `EXTENT_NOT_OVERLAP`

插入：

- `clip_to_aoi`
    

#### `RESOLUTION_MISMATCH`

插入：

- `resample_to_target_grid`
    

#### `FIELD_MAPPING_REQUIRED`

插入：

- `normalize_table_fields`
    

### 15.2 插入要求

- 必须来自模板允许的片段集合
    
- 插入后必须重新静态校验
    
- 插入后必须更新虚拟资产 lineage
    
- 插入后必须更新运行时断言
    

---

## 16. Rewrite Normalization

### 16.1 定位

Rewrite Normalization 不是通用空间优化器，而是**受限的模式匹配式预处理链规范化器**。

### 16.2 职责

- 去除重复 preprocess 节点
    
- 合并已注册白名单规则允许的线性空间算子链
    
- 稳定节点顺序与图表示
    
- 避免不必要的重复变换
    

### 16.3 合并边界

只允许对以下条件同时满足的链条执行有限合并：

- 算子来自 `Operator Capability Profile`
    
- 算子 family 属于已注册可合并集合
    
- 合并后可保持可调试性
    
- 不涉及黑盒业务节点
    
- 不改变科学语义
    

### 16.4 明确禁止

- 不做通用空间优化器
    
- 不对复合业务逻辑节点做黑盒合并
    
- 不对不可证明等价的链条做自动重排
    

### 16.5 振荡检测

若某次 rewrite 后的新错误仍指向同一资产链，且可合并空间已耗尽，则不得继续局部微调。控制面必须提升风险等级或进入人工/用户决策流。

---

## 17. 图重写幂等性

### 17.1 原则

对同一修复意图、同一输入资产对、同一策略动作的重复应用，不得产生语义重复节点。

### 17.2 Repair Fingerprint

每个修复动作必须生成：

```text
fingerprint = hash(
  action,
  source_asset_id,
  source_asset_version,
  source_asset_hash,
  target_grid_id,
  target_crs,
  policy_version
)
```

### 17.3 Rewrite Registry

规划面必须维护：

- `rewrite_registry`
    
- `applied_repairs`
    
- `repair_fingerprint_history`
    

### 17.4 无效重写处理

若相同 fingerprint 已存在，但同类错误仍持续返回，则不得继续盲目 rewrite。控制面必须将其视为：

- 修复无效
    
- 需要用户补充
    
- 或需要人工接管
    

---

## 18. Canonicalizer

### 18.1 职责

在每次 build / rewrite 后，对图进行规范化。

### 18.2 规范化内容

- 去除重复 preprocess 节点
    
- 合并已证明等价的节点
    
- 标准化 node id / edge 顺序
    
- 清理无用中间产物占位
    
- 重新生成 digest
    

### 18.3 目的

- 保证图表示稳定
    
- 减少审计噪声
    
- 为 Manifest 冻结提供一致对象
    

---

## 19. Runtime Assertion Builder

### 19.1 职责

为执行期生成必须核验的断言集合。

### 19.2 必须生成断言的情形

- 依赖派生虚拟资产通过软校验
    
- 插入了 preprocess 片段
    
- 模板降级后结果适用范围发生变化
    
- 某些兼容性无法在静态阶段完全确认
    
- 空间覆盖或 NoData 风险标签为 `HIGH` / `UNKNOWN`
    

### 19.3 典型断言

- 预处理后 CRS 必须等于目标 CRS
    
- clip 结果 extent 必须与 AOI 相交
    
- resample 后 grid 对齐必须满足阈值
    
- 中间表字段必须包含特定列名
    
- 高风险区域中有效像元比例不得低于阈值
    
- 模板降级后输出必须打上相应标记
    

### 19.4 输出

- `runtime_assertions`
    

---

## 20. Computation Load Metrics

### 20.1 职责

规划面输出标准化计算量指标，供控制面做最终放行判断。

### 20.2 最小字段

- `pixel_count_estimate`
    
- `spatial_scope_ratio`
    
- `estimated_peak_memory_class`
    
- `io_intensity_level`
    
- `load_risk_tag`
    

### 20.3 规则

1. 规划面只输出标准化指标与建议，不做最终资源决策。
    
2. 控制面可结合资源池状态、业务规则和用户要求决定是否覆盖建议。
    
3. 任何覆盖都必须进入审计与任务追踪。
    

### 20.4 典型建议

- `REQUEST_AUTO_CLIP`
    
- `REQUEST_RESOLUTION_REDUCTION`
    
- `REQUEST_SCOPE_CONFIRMATION`
    

---

## 21. Planning Summary Builder

### 21.1 职责

为控制面、前端和审计系统生成可读的规划摘要。

### 21.2 内容建议

- 选中了哪个模板
    
- 为什么选这个模板
    
- 是否发生模板协商
    
- 是否发生模板降级
    
- 降级是否经用户确认
    
- 插入了哪些 preprocess
    
- 当前图的关键节点
    
- 哪些约束仍需运行时断言确认
    
- 哪些输入是用户提供的，哪些是中间产物
    
- 当前计算量等级与主要风险
    

### 21.3 输出

- `planning_summary`
    
- `planning_manifest_payload`
    

---

## 22. 规划子阶段

规划面只持有子阶段，不持有主状态机。建议子阶段包括：

- `TEMPLATE_SELECT`
    
- `LOGICAL_COMPILE`
    
- `MATERIALIZE`
    
- `STATIC_VALIDATE`
    
- `GRAPH_REWRITE`
    
- `REWRITE_NORMALIZE`
    
- `CANONICALIZE`
    
- `ASSERTION_BUILD`
    
- `GRAPH_DIGEST`
    
- `PLANNING_SUMMARY`
    

这些子阶段必须事件化上报给控制面，并支持 revision 跟踪。

---

## 23. 对外契约

### 23.1 与认知面的契约

#### 认知面 → 规划面

输入：

- `goal_parse`
    
- `skill_route`
    
- `strategy_flags`
    
- `slot_bindings`
    
- `args_draft`
    
- 绑定后的目录元数据摘要
    

#### 规划面 → 认知面（Pass 1）

输出：

- `selected_template_ref`
    
- `logical_input_roles`
    
- `slot_schema_view`
    
- `binding_constraints`
    
- `planning_context_view`
    

### 23.2 与控制面的契约

#### 规划面 → 控制面

输出：

- `materialized_execution_graph`
    
- `execution_graph_digest`
    
- `static_validation_result`
    
- `runtime_assertions`
    
- `computation_load_metrics`
    
- `planning_summary`
    
- `planning_manifest_payload`
    
- `rewrite_registry`
    
- `planning_revision`
    

#### 控制面职责

- 下发当前 `available_asset_snapshot`
    
- 下发最新 `blacklist_context`
    
- 记录 planning revision
    
- 调度 validator
    
- 决定是否触发模板协商
    
- 决定是否覆盖规划建议
    
- 决定是否进入冻结和放行
    
- 与执行面对接进行作业提交
    

### 23.3 与执行面的契约

规划面输出执行面可直接消费的正式编排对象：

- `execution_graph`
    
- `runtime_assertions`
    
- `artifact_lineage`
    
- `tool_bindings`
    
- `node_constraints`
    

执行面只执行图，不重新解释图。

---

## 24. 可用资产快照（Available Asset Snapshot）

### 24.1 目标

在保持规划面无状态的前提下，避免高并发场景下反复重算全量黑名单和候选过滤。

### 24.2 职责归属

由控制面预先完成当前任务上下文下的候选过滤与可用性计算，并将结果快照下发给规划面。

### 24.3 最小字段

- `inventory_version`
    
- `state_version`
    
- `available_assets`
    
- `excluded_assets_summary`
    
- `blacklist_version`
    

### 24.4 规则

1. 规划面不得私自缓存黑名单状态。
    
2. 规划面模板选择和实体化编译必须基于控制面下发的当前快照。
    
3. 一旦 `state_version` 或 `blacklist_version` 变化，旧快照立即失效。
    

---

## 25. Manifest 配合机制

规划面必须为控制面冻结 Analysis Manifest 提供以下内容：

- `selected_template`
    
- `template_version`
    
- `execution_graph_digest`
    
- `rewrite_registry`
    
- `runtime_assertions`
    
- `computation_load_metrics`
    
- `planning_summary`
    
- `planning_revision`
    
- `downgrade_history`
    

规划面只提供 payload，不冻结 Manifest。每次模板重选、实体化编译、图重写和规范化后，都必须生成新的 planning revision 与新的 planning payload。

---

## 26. 运行保障

### 26.1 纯函数优先

Template Select、Logical Compile、Digest、Canonicalize 等步骤应尽量纯函数化，便于缓存与回放。

### 26.2 Revision 管理

每次模板重选、图重写、规范化后必须提升 `planning_revision`。

### 26.3 缓存策略

可缓存：

- 模板选择结果
    
- 规范化图
    
- 图签名
    
- rewrite 指纹查找结果
    

### 26.4 错误分类

- 模板匹配失败
    
- 角色声明失败
    
- 实体化编译失败
    
- 图结构非法
    
- 未授权工具
    
- 重写冲突
    
- 幂等冲突
    
- 断言生成失败
    
- 科学降级未确认
    

### 26.5 熔断条件

- `max_rewrite_rounds`
    
- `max_graph_nodes`
    
- `max_preprocess_chain_length`
    
- `max_same_repair_fingerprint_repeats`
    
- `max_template_negotiation_rounds`
    

---

## 27. 观测与审计

规划面必须记录：

- 模板选择记录
    
- 模板协商记录
    
- 降级与确认记录
    
- 图构建记录
    
- 每轮 rewrite 前后 diff
    
- fingerprint 历史
    
- normalization 记录
    
- canonicalization 记录
    
- digest 历史
    
- assertion 生成记录
    
- planning revision 链
    
- override 建议与实际决策差异
    

建议指标包括：

- 模板命中率
    
- 协商重选率
    
- 降级发生率
    
- 用户确认率
    
- preprocess 插入率
    
- 幂等冲突率
    
- 静态图校验失败率
    
- 平均图节点数
    
- 高风险标签占比
    
- override 发生率
    

---

## 28. 最小落地顺序

建议按以下顺序落地：

1. `plan_templates.yaml` 与模板目录
    
2. `Operator Capability Profile`
    
3. Template Selector
    
4. Logical Compile（角色声明、槽位声明、骨架生成）
    
5. Available Asset Snapshot 接口
    
6. Materialized Compile（实体化编译）
    
7. Derived Virtual Asset Builder
    
8. Static Graph Validator
    
9. Computation Load Metrics 生成
    
10. Graph Digest Builder
    
11. Graph Rewriter
    
12. preprocess 片段与 fingerprint registry
    
13. Rewrite Normalization
    
14. Canonicalizer
    
15. Runtime Assertion Builder
    
16. Planning Summary 与 Manifest payload
    
17. 模板协商与科学降级确认闭环
    

---

## 29. 结论

规划面是整个系统中的确定性编排层。它负责在 Skill、模板、repair policy、工具授权和科学约束共同作用下，把认知结果编译为可执行图、完成图级修复、生成运行时断言、表达负载风险并输出执行前正式编排对象。它不理解用户语义，不持有主状态机，不提交作业，不做结果解释，也不替控制面做资源调度决定。通过两程编译、模板协商、科学降级确认、受限模式匹配式规范化、图重写幂等性、风险标签、负载指标和 revision 化审计，规划面能够在生态分析场景下把“可解释的认知”稳定转化为“可治理的执行”。