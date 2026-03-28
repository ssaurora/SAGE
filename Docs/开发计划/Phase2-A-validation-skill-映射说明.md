# Phase2-A validation 与 skill/capability 映射说明

更新时间：2026-03-26

## 1. 文档目的

本文件用于补齐 `Phase2-A` 中 `A3` 的交付物：

- validation-to-skill 映射表
- 字段来源说明

目标不是重复贴代码，而是明确回答以下问题：

- validation 输出中的每个关键字段来自哪里
- 它们与 `SkillDefinition / CapabilityDefinitionLite` 的关系是什么
- 哪些字段可以直接驱动 `waiting_context`
- 哪些字段只能作为 repair proposal 的输入事实

---

## 2. 当前作用域

当前说明仅覆盖 `Phase2-A` 已落地的最小单 skill 场景：

- capability / skill：`water_yield`
- pass1 capability facts 来源：`skill_catalog.py`
- validation 执行位置：`Service/planning-pass1`
- waiting authority 重组位置：`BackEnd RepairDispatcherService`

本说明**不覆盖**：

- 多 skill orchestration
- Phase2-B 的恢复事务
- Manifest freeze
- planner compiler 增强

---

## 3. 当前事实链

当前 validation 到 waiting/repair 的事实链是：

`SkillDefinition(with CapabilityDefinitionLite)`
-> `PlanningPass1Response`
-> `CognitionPassBResponse`
-> `PrimitiveValidationResponse`
-> `RepairDispatcherService.decide(...)`
-> `waiting_context`
-> `repair proposal request`

其中各层角色如下：

- `SkillDefinition`
  - 提供 role / slot / arg mapping / output contract 的结构事实
- `CapabilityDefinitionLite`
  - 提供 `validation_hints / repair_hints / output_contract / runtime_profile_hint`
- `PrimitiveValidationResponse`
  - 提供当前任务在 pass1/passB 候选结果下的结构化缺口
- `RepairDispatcherService`
  - 将 `validationSummary + pass1Result + attachments` 重组为 authority waiting facts
- `repair.py`
  - 仅消费 authority/advisory 输入，输出 suggestion，不拥有工作流 authority

---

## 4. 上游事实来源

## 4.1 Skill / Capability 事实来源

当前 `water_yield` 的事实来源于 `Service/planning-pass1/app/skill_catalog.py`，包含：

- `required_roles`
  - `precipitation`
  - `eto`
- `optional_roles`
  - `depth_to_root_restricting_layer`
  - `plant_available_water_content`
- `slot_specs`
  - `watersheds`
  - `lulc`
  - `biophysical_table`
  - `precipitation`
  - `eto`
- `validation_hints`
  - 为 role 指定 `expected_slot_type`
- `repair_hints`
  - 为 role 指定默认 repair action

这些事实通过 pass1 被暴露为：

- `capability_key`
- `capability_facts`
- `logical_input_roles`
- `slot_schema_view`
- `role_arg_mappings`

---

## 4.2 PassB 候选事实来源

`CognitionPassBResponse` 当前提供两类候选事实：

- `slot_bindings`
  - role -> slot 的候选绑定
- `args_draft`
  - 执行参数草案

这些数据本质上仍然是**候选结构**，不是 authority。

validation 的职责就是判断：

- 候选绑定是否完整
- 候选绑定是否合法
- 必需参数是否齐全

---

## 5. validation 输出字段映射

## 5.1 `is_valid`

### 含义

表示当前 pass1/passB 候选组合是否通过最小结构校验。

### 来源

由以下条件共同决定：

- `missing_roles` 是否为空
- `missing_params` 是否为空
- `invalid_bindings` 是否为空

### 与 skill/capability 的关系

- skill 通过 `logical_input_roles.required` 定义 required roles
- skill 通过 `slot_schema_view` 定义 valid slots
- validation 再拿 passB 候选结果去检查是否满足这些 skill facts

### 后续用途

- 直接影响 `input_chain_status`
- 直接决定是否需要进入 Dispatcher 重组 repair / waiting
- 但**不直接等于** `WAITING_USER` 或 `FAILED`

---

## 5.2 `missing_roles`

### 含义

表示当前 required role 中，哪些 role 没有在 passB `slot_bindings` 中出现。

### 来源

- required roles：来自 `pass1_result.logical_input_roles` 中 `required=true` 的角色
- bound roles：来自 `passb_result.slot_bindings.role_name`
- 差集计算后得到 `missing_roles`

### 与 skill/capability 的关系

- 这是最直接的 skill contract 缺口字段
- 它的定义完全依赖 skill 暴露的 required roles

### 后续用途

- 可直接进入 Dispatcher，作为 `waiting_context.missing_slots` 的候选来源
- 会进一步和：
  - `attachments`
  - `capability.validation_hints`
  - `capability.repair_hints`
 共同重组为 authority waiting facts

### 注意

`missing_roles` 本身**不能直接透传**为完整的 `waiting_context.missing_slots`，因为：

- 还需要补 `expected_type`
- 还需要考虑 attachment 是否已经提供了对应 logical slot

---

## 5.3 `missing_params`

### 含义

表示执行所需的关键参数当前缺失。

### 当前来源

当前最小实现里，固定检查：

- `workspace_dir`
- `results_suffix`

### 与 skill/capability 的关系

- 当前仍然是 Phase2-A 的简化实现
- 它尚未完全由独立 capability param schema 驱动
- 但它已经进入 Dispatcher 的 authority 重组链

### 后续用途

- 不能转成 `missing_slots`
- 会在 Dispatcher 中转成 `required_user_actions` 里的 `override` 类型动作
- 同时影响：
  - `waiting_reason_type = MISSING_PARAM`
  - `can_resume = false`

### 当前阶段判断

- 已进入 waiting authority 派生链
- 但尚未能力模型化到底，后续仍有提升空间

---

## 5.4 `invalid_bindings`

### 含义

表示 passB 给出的某些 role -> slot 绑定，其 slot 并不在 pass1 声明的 valid slot 集合内。

### 来源

- valid slots：来自 `pass1_result.slot_schema_view.slots[].slot_name`
- candidate bindings：来自 `passb_result.slot_bindings`
- 若 binding.slot_name 不在 valid slots 中，则将对应 `role_name` 记入 `invalid_bindings`

### 与 skill/capability 的关系

- 它由 skill 暴露的 slot contract 间接定义
- 本质上是 “passB 候选结构偏离 skill slot schema” 的结构化错误

### 后续用途

- 进入 Dispatcher 后会转成 `required_user_actions` 中的 `override` 修复动作
- 当前控制面中，`error_code == INVALID_BINDING` 会被判为：
  - `routing = FAILED`
  - `severity = FATAL`

### 注意

`invalid_bindings` 是一个典型例子：

- 它来自 validation
- 但最终工作流含义由 Dispatcher 决定
- validation 本身不直接拥有 `FAILED` authority

---

## 5.5 `error_code`

### 含义

当前 validation 的结构化错误分类。

### 当前分类规则

- `NONE`
- `MISSING_ROLE`
- `INVALID_BINDING`
- `INVALID_PARAM`

### 来源

由 validation 根据：

- `missing_roles`
- `invalid_bindings`
- `missing_params`

按优先顺序归类得出。

### 与 skill/capability 的关系

- 它不是 capability 中直接声明的字段
- 它是 validation 对 skill contract 违规情况做出的结构化汇总标签

### 后续用途

- 进入 event / audit / failure summary / repair notes
- Dispatcher 使用它做严重性与路由判断

---

## 6. validation 字段与 waiting_context 的映射

以下是当前已落地的 authority 派生关系。

## 6.1 可直接进入 waiting authority 重组的字段

### `missing_roles`

用途：

- 候选 `missing_slots` 来源
- 候选 `required_user_actions(upload)` 来源

但仍需经过 Dispatcher 补充：

- `attachments` 去重
- `expected_type` 推导
- `repair_hints` 补 action 元数据

### `missing_params`

用途：

- 候选 `required_user_actions(override)` 来源
- 候选 `waiting_reason_type = MISSING_PARAM`

### `invalid_bindings`

用途：

- 候选 `invalid_bindings`
- 候选 `required_user_actions(override)` 来源
- 候选 fatal routing 输入

### `error_code`

用途：

- Dispatcher 路由与严重性判断

---

## 6.2 不能直接透传为 waiting authority 的字段

以下字段虽然参与派生，但**不允许直接透传**：

### `pass1Result.slot_schema_view`

原因：

- 它只是 slot contract
- 不是当前缺口清单
- 必须结合 validation 结果和 attachments 才能形成 authority waiting facts

### `validationSummary.missing_roles`

原因：

- 它只是角色缺口
- 不是完整的 `missing_slots`
- 缺少：
  - `expected_type`
  - attachment 去重
  - repair action 元信息

### `pass1Result.capability_facts.repair_hints`

原因：

- 它只提供默认 repair action 模板
- 不能单独决定当前任务是否需要这些动作
- 必须与 validation 缺口结合

---

## 7. 当前 waiting_context 派生规则摘要

当前 `RepairDispatcherService` 的 authority 重组逻辑可概括为：

### 输入

- `validationSummary`
- `pass1Result`
- `attachments`

### 规则

- `missing_roles`
  - 经过 attachment 去重后，形成 unresolved missing roles
- unresolved missing roles
  - 生成 `missing_slots`
  - `expected_type` 优先取 capability `validation_hints`
  - 其次回退到 pass1 `slot_schema_view`
  - 否则回退 `"unknown"`
- unresolved missing roles
  - 生成 `required_user_actions(upload)`，优先取 capability `repair_hints`
- `missing_params`
  - 生成 `required_user_actions(override)`
- `invalid_bindings`
  - 生成 `required_user_actions(override)`
- `required_user_actions.isEmpty()`
  - 决定 `can_resume`
- `error_code == INVALID_BINDING`
  - 决定 fatal routing

---

## 8. 哪些字段只能作为 repair proposal 输入

下列字段可进入 repair proposal request，但不拥有 waiting authority：

- `waiting_context`
  - authority facts
- `validation_summary`
  - 结构化辅助事实
- `failure_summary`
  - fatal 或失败场景补充事实
- `user_note`
  - 用户补充说明

其中：

- `waiting_context` 是 authority 视图
- `repair proposal` 是 advisory suggestion
- repair proposal **不能反过来决定** `can_resume` / `required_user_actions`

---

## 9. 当前映射表

| validation 字段 | 直接来源 | skill/capability 依赖 | 是否直接驱动 waiting | 当前用途 |
|---|---|---|---|---|
| `is_valid` | validation 聚合判断 | 间接依赖 required roles / valid slots | 否 | 决定是否进入 Dispatcher |
| `missing_roles` | required roles - bound roles | `logical_input_roles.required` | 是，但需重组 | 生成 `missing_slots` / upload actions |
| `missing_params` | `args_draft` 缺口 | 当前仅部分能力化 | 是，但不转 slot | 生成 override actions |
| `invalid_bindings` | slot 不在 valid slots | `slot_schema_view` | 是 | 生成 invalid binding / override / fatal 路由输入 |
| `error_code` | validation 分类汇总 | 间接依赖前三者 | 是 | Dispatcher 路由与严重性判断 |

---

## 10. 当前已解决的问题

通过这轮 `Phase2-A` 开发，以下问题已得到明显改善：

- validation 不再只是“孤立的一段 JSON”，而是能追溯到 skill facts
- `waiting_context` 的关键字段已不再依赖前端隐式推理
- `expected_type` 已开始来自 capability validation hints
- `required_user_actions` 已开始来自 capability repair hints
- `pass1Result` 已被制度化为“事实源之一”，而不是 waiting authority 本体

---

## 11. 当前仍未完全解决的问题

以下问题在 `Phase2-A` 中仍然是部分完成：

- `missing_params` 还没有完整提升为 capability param schema
- 当前 validation error_code 仍是简化版分类，尚未形成更强的治理型错误体系
- `runtime_assertions` 还未接入 capability/validation hints
- 文档已能说明映射，但还未进入 `Phase2-B` 的正式治理对象体系

---

## 12. 结论

当前可以明确说：

- validation 与 skill/capability 的映射已经不是隐式关系，而是可以被代码和文档同时解释的显式关系
- 这满足了 `Phase2-A` 中 `A3` 的核心方向

但也要保持边界清晰：

- 当前完成的是 `Phase2-A` 的最小映射收口
- 不是 `Phase2-B` 的强治理 validation/capability 体系

