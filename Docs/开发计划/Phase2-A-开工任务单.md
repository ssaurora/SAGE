# Phase2-A 开工任务单

## 1. 文档目的

本任务单用于把 `Phase2 正式开发任务树 V2` 中的 **Phase2-A：骨架收口** 转换为可直接开工的执行清单。

本任务单只覆盖当前应立即启动的工作，不覆盖 Phase2-B 的强治理主线。

---

## 2. 开工范围

本轮开工范围限定为以下三个工作包：

- `P2-1` Skill / Capability 基础模型
- `P2-2` Validation / Waiting / Repair 对齐
- `P2-3` Repair Proposal 规范化

本轮**不直接进入**以下工作：

- `P2-4` 控制面强治理恢复链
- `P2-5` Manifest 冻结与版本解耦
- `P2-6` 规划面工业编译器增强
- `P2-7` 断言下推、失败建模、前端边界与验收收口

原因不是这些不重要，而是当前最合理的顺序是：

> 先把 skill/capability/repair 的骨架补齐，再进入治理主线。

---

## 3. 本轮目标

本轮结束时，应至少达到以下状态：

- 系统内出现正式的最小 `SkillDefinition`
- `water_yield` 拥有第一份可消费的 skill 定义
- validation / waiting_context / repair proposal 的输入事实开始与 skill/capability 对齐
- repair proposal 不再只是临时 prompt 输出，而是具备清晰 schema 与 fallback 规则

---

## 4. 执行顺序

建议严格按以下顺序开工：

`A1 -> A2 -> A3 -> A4 -> A5 -> A6`

其中：

- `A1 ~ A2` 是能力骨架建立
- `A3 ~ A4` 是事实派生对齐
- `A5` 是认知输出规范化
- `A6` 是本轮收口与验收

---

## 5. 任务拆解

## A1：定义最小 SkillDefinition

### 目标

定义当前系统可消费的最小 `SkillDefinition`，作为后续 capability、validation、repair 的统一输入对象。

### 涉及层

- 能力面
- 认知面
- 控制面

### 主要任务

- 定义最小 `SkillDefinition` 结构
- 明确 `required_roles / optional_roles`
- 明确 `validation_hints`
- 明确 `repair_hints`
- 明确 `output_contract`
- 明确 `execution_profile`

### 建议落点

- `Service/planning-pass1/app/schemas.py`
- 新增专门 skill schema 文件（如有必要）
- `Docs/架构设计/能力面设计方案-latest.md` 的后续同步更新

### 交付物

- skill schema 定义
- skill schema 设计说明

### 完成标准

- 代码中已存在可实例化的最小 `SkillDefinition`
- 字段能覆盖 waiting/repair 当前最小需求

---

## A2：为 water_yield 写第一份 skill 定义

### 目标

用 `water_yield` 作为第一个正式 skill，把抽象从“类型定义”推进到“真实实例”。

### 涉及层

- 能力面
- 认知面
- 规划面

### 主要任务

- 为 `water_yield` 编写第一份 skill 定义
- 显式列出所需输入角色与关键槽位
- 显式列出 repair hints
- 显式列出 output contract
- 明确与当前 pass1/pass2、runtime 的最低映射关系

### 建议落点

- 新增 skill definition 文件
- `Service/planning-pass1/app/planner.py`
- `BackEnd/src/main/java/com/sage/backend/task/GoalRouteService.java`

### 交付物

- 第一份可被读取的 `water_yield` skill definition
- 最小读取/映射逻辑

### 完成标准

- `water_yield` 不再只是隐式硬编码路径
- 后续 validation / repair 可以引用这份 skill 定义

---

## A3：梳理 validation 输出与 skill/capability 的映射

### 目标

让 validation 的结构化输出，能被明确映射到 skill/capability 事实，而不是继续散落在各处。

### 涉及层

- 控制面
- 能力面
- 认知面

### 主要任务

- 盘点当前 validation 返回字段
- 盘点当前 `missing_roles / error_code / invalid_bindings` 的来源
- 建立 validation 输出与 `SkillDefinition` 的对齐关系
- 明确哪些字段可直接驱动 waiting_context
- 明确哪些字段只能作为 repair proposal 输入

### 建议落点

- `BackEnd/src/main/java/com/sage/backend/validationgate`
- `BackEnd/src/main/java/com/sage/backend/task/TaskService.java`
- `Service/planning-pass1/app/schemas.py`

### 交付物

- validation-to-skill 映射表
- 字段来源说明

### 完成标准

- 当前 validation 输出中的关键字段都有明确语义归属
- 可以回答“某个 waiting_context 字段到底由哪个 validation/capability 事实推导而来”

---

## A4：梳理 waiting_context 与 repair 输入上下文派生规则

### 目标

收紧 `waiting_context` 和 repair proposal 输入上下文的派生规则，让它们统一依赖 capability + validation 事实。

### 涉及层

- 控制面
- 认知面
- 前端展示输入契约

### 主要任务

- 盘点当前 `waiting_context` 字段来源
- 盘点当前 repair proposal request 的输入来源
- 建立 `validation_summary -> waiting_context` 映射规则
- 建立 `waiting_context + validation_summary + failure_summary -> repair request` 规则
- 明确 `can_resume` 重计算触发点

### 建议落点

- `BackEnd/src/main/java/com/sage/backend/task/TaskService.java`
- `BackEnd/src/main/java/com/sage/backend/repair/RepairProposalService.java`
- `Service/planning-pass1/app/repair.py`

### 交付物

- 派生规则文档
- 关键映射代码收敛

### 完成标准

- `waiting_context` 不再依赖隐式前端假设
- repair 输入上下文不再脱离 waiting/validation 事实

---

## A5：规范 repair proposal schema 与 fallback 策略

### 目标

将 repair proposal 从“可工作”推进到“可治理”。

### 涉及层

- 认知面
- 控制面

### 主要任务

- 固化 repair proposal 输入 schema
- 固化 repair proposal 输出 schema
- 规范 fallback 输出格式
- 区分开发态 debug 字段与正式输出字段
- 收紧 prompt 与模型响应清洗逻辑

### 建议落点

- `Service/planning-pass1/app/repair.py`
- `Service/planning-pass1/app/schemas.py`
- `BackEnd/src/main/java/com/sage/backend/repair`

### 交付物

- repair schema 文档
- fallback 策略说明
- 调试字段管理规则

### 完成标准

- repair proposal 成功路径与 fallback 路径都能稳定输出统一结构
- 开发态调试信息不会污染正式 authority 结构

---

## A6：本轮收口与最小验收

### 目标

确保本轮不是“加了几段新逻辑”，而是真的形成下一阶段可承接的骨架。

### 主要任务

- 补齐文档
- 补齐最小验收样例
- 复核前后端边界
- 复核 Java backend 中 provider/prompt 是否继续扩散
- 记录进入 Phase2-B 前的明确未完成项

### 建议落点

- `Docs/开发计划`
- `Docs/架构设计`
- 相关最小 E2E / 验收脚本

### 交付物

- 本轮变更说明
- 验收记录
- Phase2-B 进入条件清单

### 完成标准

- 可以明确回答“Phase2-A 已完成什么、仍未完成什么”
- 可以平滑进入 `P2-4` 强治理恢复链开发

---

## 6. 模块级建议分工

### Java BackEnd

重点负责：

- validation / waiting_context 映射收敛
- repair request 组装收敛
- skill/capability 对接入口

优先关注目录：

- `BackEnd/src/main/java/com/sage/backend/task`
- `BackEnd/src/main/java/com/sage/backend/repair`
- `BackEnd/src/main/java/com/sage/backend/validationgate`

### Python Service

重点负责：

- skill schema 承载
- repair schema/fallback 规范化
- planner / repair 输入输出边界收紧

优先关注目录：

- `Service/planning-pass1/app/schemas.py`
- `Service/planning-pass1/app/repair.py`
- `Service/planning-pass1/app/planner.py`

### FrontEnd

本轮不作为主战场，但应配合验证：

- authority facts 与 advisory suggestion 的数据边界
- waiting_context / repair_proposal 的输入契约是否稳定

---

## 7. 本轮不做的事

本轮明确不做：

- Ack / Commit / Rollback 事务实现
- Manifest freeze gate 正式实现
- planning compiler 增强实现
- runtime assertion push-down 实现
- `STATE_CORRUPTED` 完整治理实现
- 大规模前端展示重构

这些内容属于 **Phase2-B 主战场**，不在本轮开工任务单范围内。

---

## 8. 风险提醒

### 风险 1：把 schema 设计成文档，而不是运行时对象

若只写文档、不形成真实代码对象，Phase2-A 会退化成“术语整理”。

### 风险 2：waiting_context 继续依赖隐式规则

若 capability 只是新名字，而 waiting_context 派生逻辑仍散落不收敛，Phase2-A 价值会大幅下降。

### 风险 3：repair 继续变成 prompt 细节堆叠

若不先收紧 schema 与 fallback，后面 capability 化会被 prompt 细节拖住。

---

## 9. 实施约束

### 9.1 SkillDefinition 与 CapabilityDefinition 的关系

- `Phase2-A` 中允许先落地最小 `SkillDefinition`
- `CapabilityDefinition` 暂以内嵌 `CapabilityDefinitionLite` 的形式存在于 skill 定义中
- 本阶段 **不单独落库、不独立建 registry object**
- 但 capability 级字段命名和语义必须为后续独立 capability 层预留演进空间

这意味着：

- capability 级提示统一使用 `validation_hints / repair_hints`
- 不再引入与 capability 平行冲突的 skill 私有提示命名

### 9.2 `ROLE_DEFAULTS` 的迁移边界

- `required_roles / optional_roles / role-slot mapping / output_contract / repair_hints` 应迁入 skill/capability 定义
- 但 execution 默认参数 **不做一步清仓迁移**
- 只迁移稳定、语义级默认值
- 环境相关或 runtime policy 相关默认值继续暂留在 planner/runtime/template 一侧

### 9.3 `pass1Result` 只是事实源之一

`pass1Result`、`validationSummary`、`attachments / overrides` 在 waiting 派生中的职责必须区分：

- `pass1Result` 提供 skill facts 与 slot expectations
- `validationSummary` 提供当前缺口与不一致事实
- `attachments / overrides` 提供最新用户侧状态
- authority judgment 必须由 Dispatcher 重组后形成

严禁：

- 直接把 `pass1Result.slot_schema_view` 透传为 `waiting_context.missing_slots`
- 仅凭 `validationSummary.missing_roles` 直接生成完整 `required_user_actions`

### 9.4 本轮禁止事项

- 禁止 Java backend 新增 prompt 拼接、provider 选择、模型回退分支
- 禁止前端基于 `repair_proposal` 或 `validation_summary` 自行推导 `can_resume`、`missing_slots` 类型或 `required_user_actions`

---

## 10. 本轮完成判据

满足以下条件，才可判定本轮完成：

- 已有正式 `SkillDefinition`
- 已有 `water_yield` skill 实例
- validation 输出与 skill/capability 的映射已说明并部分落地
- `waiting_context` 与 repair 输入上下文派生规则已收敛
- repair proposal schema 与 fallback 规则已规范化
- 文档、代码、最小验收三者一致

---

## 11. Phase2-B 技术债登记

本轮允许把 `skill_catalog.py` 作为 **临时 canonical source**，但必须明确：

- 它不等同于正式 capability registry
- 它不能长期承担正式 version / scope / provider binding 治理职责

Phase2-B 必须补齐：

- registry
- version
- scope
- provider binding
- manifest / recovery / governance 对 capability truth 的正式承认

---

## 12. 一句话开工说明

本轮开工不是为了“再加一个功能”，而是为了把当前 Phase1 基线背后的隐式规则抽出来，变成：

- 可定义
- 可引用
- 可派生
- 可治理

的 Phase2-A 能力骨架。
