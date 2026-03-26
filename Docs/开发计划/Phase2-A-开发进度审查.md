# Phase2-A 开发进度审查

更新时间：2026-03-26

## 1. 审查结论

对照 `Docs/开发计划/Phase2-A-开工任务单.md`，当前进度判断如下：

- `A1` 最小 `SkillDefinition`：**已完成**
- `A2` `water_yield` 第一份 skill 定义：**已完成**
- `A3` validation 与 skill/capability 映射：**部分完成**
- `A4` `waiting_context` 与 repair 输入派生规则：**部分完成**
- `A5` repair proposal schema 与 fallback：**部分完成**
- `A6` 本轮收口与最小验收：**部分完成**

一句话判断：

- `Phase2-A` 的**代码骨架已经明显落地**
- 但**还不能宣称 Phase2-A 全量收口完成**
- 主要差距不再是主链代码缺失，而是：
  - 派生规则文档化不完整
  - repair schema/fallback 文档未独立沉淀
  - 最新一轮 `output_contract -> runtime/artifact` 改动后，尚未重新跑 Week5/Week6 E2E

---

## 2. 总体完成情况

当前已经形成的主链是：

`GoalRoute capability_key -> pass1 skill facts -> validation facts -> Dispatcher 重组 waiting_context -> repair proposal -> runtime output_contract`

这说明本轮开发已经不只是“把 skill 写进一个 catalog 文件”，而是已经把 skill/capability 事实接入了：

- pass1
- passB
- validation
- waiting_context
- repair proposal
- runtime 输出与 artifact 语义

这满足了 `Phase2-A` “骨架收口”的主要方向要求。

---

## 3. 分项审查

## A1：定义最小 SkillDefinition

### 状态

**已完成**

### 已完成内容

- 在 `Service/planning-pass1/app/schemas.py` 中落地了最小能力骨架：
  - `CapabilityDefinitionLite`
  - `SkillDefinition`
  - `CapabilityValidationHint`
  - `CapabilityRepairHint`
  - `CapabilityOutputContract`
  - `SkillSlotSpec`
  - `SkillRoleArgMapping`
- `CapabilityDefinitionLite` 以内嵌方式存在于 `SkillDefinition.capability`
- capability 级命名统一使用：
  - `validation_hints`
  - `repair_hints`
  - `output_contract`
  - `runtime_profile_hint`

### 代码证据

- `Service/planning-pass1/app/schemas.py:9`
- `Service/planning-pass1/app/schemas.py:41`
- `Service/planning-pass1/app/schemas.py:63`

### 说明

- 这一项在代码层面已经满足任务单中的完成标准。
- 目前仍然是 `CapabilityDefinitionLite`，不是正式 registry object，这符合 `Phase2-A` 的约束。

---

## A2：为 water_yield 写第一份 skill 定义

### 状态

**已完成**

### 已完成内容

- 新增 `Service/planning-pass1/app/skill_catalog.py`
- `water_yield` 已成为第一份真实 skill 实例
- skill 中已显式定义：
  - required / optional roles
  - slot specs
  - repair hints
  - output contract
  - runtime profile hint
  - role arg mappings
- `planner.py` 已从 skill 读取 pass1/passB 所需事实
- `GoalRoute -> Pass1` 的 `capability_key` 传递链已补齐，避免 service 端长期写死入口能力

### 代码证据

- `Service/planning-pass1/app/skill_catalog.py:16`
- `Service/planning-pass1/app/skill_catalog.py:37`
- `Service/planning-pass1/app/planner.py:33`
- `BackEnd/src/main/java/com/sage/backend/planning/dto/Pass1Request.java:5`
- `BackEnd/src/main/java/com/sage/backend/task/TaskService.java:207`
- `BackEnd/src/main/java/com/sage/backend/task/TaskService.java:1233`

### 说明

- `water_yield` 已经不只是隐式硬编码路径，而是有了集中定义的 skill contract。
- 但当前仍然是**单 skill**，且 `skill_catalog.py` 仍是临时 canonical source；这不是问题，因为这属于 Phase2-B 的治理债务范围。

---

## A3：梳理 validation 输出与 skill/capability 的映射

### 状态

**部分完成**

### 已完成内容

- service 侧 validation 已开始消费 pass1 skill facts：
  - required roles 来自 `pass1_result.logical_input_roles`
  - valid slots 来自 `pass1_result.slot_schema_view`
- backend `RepairDispatcherService` 已从多个事实源重组 waiting authority：
  - `validationSummary`
  - `pass1Result`
  - `attachments`
- `missing_slots.expected_type` 已可由 capability `validation_hints` 和 pass1 slot schema 推导
- `required_user_actions` 已优先读取 capability `repair_hints`
- `RegistryService` 已优先消费显式 `capability_key`

### 代码证据

- `Service/planning-pass1/app/planner.py:133`
- `BackEnd/src/main/java/com/sage/backend/repair/RepairDispatcherService.java:23`
- `BackEnd/src/main/java/com/sage/backend/task/RegistryService.java:22`

### 未完成项

- 任务单要求的“validation-to-skill 映射表”“字段来源说明”尚未形成独立文档
- 当前映射关系已在代码里成立，但文档层还不够可审计

### 判断

- 代码骨架已经到位
- 文档化交付物仍未完成，所以只能判定为**部分完成**

---

## A4：梳理 waiting_context 与 repair 输入上下文派生规则

### 状态

**部分完成**

### 已完成内容

- backend 已把 waiting authority 的重组收敛到 Dispatcher + `TaskService` 统一入口
- `TaskService` 中已新增统一重建入口，用于：
  - create 流程进入 `WAITING_USER`
  - upload 后 refresh
  - resume 前重算
- `pass1Result` 现在只作为事实源之一，不直接透传为 waiting authority
- service 侧 `/repair/proposal` 已消费 typed request model，而不是散装 dict

### 代码证据

- `BackEnd/src/main/java/com/sage/backend/repair/RepairDispatcherService.java:23`
- `BackEnd/src/main/java/com/sage/backend/task/TaskService.java:1173`
- `Service/planning-pass1/app/schemas.py:205`
- `Service/planning-pass1/app/repair.py:46`

### 未完成项

- 任务单要求的“派生规则文档”还未单独沉淀
- 前端边界本轮主要是**未扩散**，但没有新增专门的契约回归说明文档

### 判断

- 关键收敛代码已完成
- 文档交付仍未闭环，因此暂定为**部分完成**

---

## A5：规范 repair proposal schema 与 fallback 策略

### 状态

**部分完成**

### 已完成内容

- repair request 已从原始 dict 收紧为 typed nested schema：
  - `WaitingContextView`
  - `ValidationSummaryView`
  - `FailureSummaryView`
- deterministic 路径与 GLM 路径已共享 normalized request
- fenced JSON 解析已支持
- 空内容 / 坏 JSON / timeout 会进入 deterministic fallback
- debug 信息只在 `SAGE_REPAIR_DEBUG=true` 时进入返回 notes
- Java backend 没有新增 prompt/provider/model 逻辑扩散

### 代码证据

- `Service/planning-pass1/app/schemas.py:169`
- `Service/planning-pass1/app/schemas.py:205`
- `Service/planning-pass1/app/repair.py:46`
- `Service/planning-pass1/app/repair.py:102`
- `Service/planning-pass1/app/repair.py:233`
- `BackEnd/src/main/java/com/sage/backend/repair/RepairProposalService.java:42`

### 未完成项

- 任务单要求的独立“repair schema 文档”“fallback 策略说明”“调试字段管理规则”还没有形成单独文档

### 判断

- 代码规范化已经完成到可以稳定运行的程度
- 但文档交付物还没有单独沉淀，因此暂定为**部分完成**

---

## A6：本轮收口与最小验收

### 状态

**部分完成**

### 已完成内容

- 已补充 `Phase2-A` 任务单中的实施约束与 Phase2-B 技术债登记：
  - `Docs/开发计划/Phase2-A-开工任务单.md:280`
  - `Docs/开发计划/Phase2-A-开工任务单.md:332`
- 已完成的验证：
  - `conda run -n sage-cognitive python -m pytest tests/test_pass1_api.py` -> `8 passed`
  - `mvn -q test` -> passed
- 已有更高层回归记录：
  - `Week5 E2E` 通过
  - `Week6 E2E` 通过

### 说明

- `Week5/Week6 E2E` 是在本轮较早的 Phase2-A 快照上通过的
- 最新一轮 `output_contract -> runtime/artifact` 接入之后，尚未重新跑 Week5/Week6
- 因此当前不能把本轮最新代码直接视为“Phase2-A 全量验收完成”

### 未完成项

- 缺少独立的“本轮变更说明 / 验收记录 / Phase2-B 进入条件清单”文档
- 缺少基于最新代码快照的 Week5/Week6 E2E 复跑记录

### 判断

- 最小验收能力已具备
- 但“本轮收口”还没有完全封箱，因此只能判定为**部分完成**

---

## 4. 本轮新增的关键推进

除了任务单中最初定义的核心目标，本轮还额外推进了两条重要主线：

### 4.1 `GoalRoute -> Pass1` capability 传递链

- `GoalRouteService` 产出的 `capability_key` 已显式传到 `Pass1`
- 这避免了 service 端长期以 `"water_yield"` 为硬编码入口

### 4.2 `output_contract` 开始进入 runtime 真实输出

- `capability_facts.output_contract` 已不再只是 pass1 声明
- runtime 现在会按 contract 写出真实工件，并将其注入：
  - `result_bundle.main_outputs`
  - `artifact_catalog.primary_outputs`
  - `artifact_catalog.audit_artifacts`

代码证据：

- `Service/planning-pass1/app/runtime.py:223`
- `Service/planning-pass1/app/runtime.py:430`
- `Service/planning-pass1/tests/test_pass1_api.py:134`

这说明 `Phase2-A` 已经从“输入与修复事实对齐”推进到了“输出与工件语义开始 capability 化”。

---

## 5. 当前明确未完成项

如果要严格对照任务单，当前还未完成的点主要有：

- 尚未形成独立的 validation-to-skill 映射文档
- 尚未形成独立的 waiting_context 派生规则文档
- 尚未形成独立的 repair schema / fallback 策略文档
- 尚未形成独立的本轮验收记录与 Phase2-B 进入条件清单
- 最新代码快照尚未重新跑 Week5/Week6 E2E

---

## 6. 当前阶段判断

当前最准确的阶段判断是：

- `Phase2-A` **已进入后半段**
- 代码主骨架已经成型
- 主要剩余工作从“补功能”转为：
  - 文档化
  - 规则显式化
  - 用最新代码快照完成一次完整回归封箱

也就是说：

- 现在不能说 `Phase2-A` 已完全结束
- 但也不再处于“刚开工”或“只完成概念设计”的状态

---

## 7. 建议的下一步

建议按以下顺序继续推进：

- 第一优先：补齐 `A3/A4/A5` 对应的派生规则与 schema 文档
- 第二优先：基于当前最新代码快照重新跑 `Week5/Week6 E2E`
- 第三优先：整理 `A6` 收口文档，明确哪些项进入 `Phase2-B`

完成这三步之后，再判断是否可以正式关闭 `Phase2-A` 并进入 `P2-4`

