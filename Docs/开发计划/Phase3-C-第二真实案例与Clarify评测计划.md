# Phase3-C-第二真实案例与 Clarify 评测计划
更新日期：2026-04-02

## 0. 文档目的

本文用于定义 Phase3 的下一阶段工作重点。

当前系统已经证明：

- 自然语言请求可以进入主链
- `goal-route / passb / repair proposal / final explanation` 已可由 LLM 参与
- 请求可以被映射到受治理的 `case contract`
- 真实 InVEST `water_yield` 案例可以在 `docker-invest-real` runtime 中完成执行
- `WAITING_USER / resume / cancel / artifact / audit` 已形成完整闭环
- clarify 机制、`case_projection` 与前端可视化已经初步落地

但当前系统仍有一个关键边界：

> 我们已经证明了“自然语言到真实执行 contract”的主链成立，但尚未充分证明系统能够在一个真正的 case 空间内稳定工作。

原因很明确：

- 当前仓库中只有一个真正可执行的真实案例：`annual_water_yield_gura`
- 另一个 case 目前只是 metadata-only fixture，用于验证 registry / clarify 结构，不足以证明多 case 能力

因此，Phase3-C 的核心目标不是继续扩展 UI，也不是进入多 skill orchestration，而是：

> 把当前的“单 case 成功链”推进为“多 case-ready 且有证据支撑的 governed projection 能力”。

---

## 1. 阶段判断

### 1.1 当前已完成的能力

截至本文编写时，系统已具备以下能力：

- `LLM -> governed case projection -> real execution` 主链成立
- `goal-route`、`passb`、`repair proposal`、`final explanation` 已具备结构化 cognition metadata
- `resolved / clarify_required / unavailable` 的 `case_projection` 已形成稳定 contract
- clarify 已纳入 control layer 治理，并通过 `/resume` 收口
- 详情页、结果页已可展示 LLM trace、case projection、runtime evidence、artifact catalog

### 1.2 当前最大的缺口

当前最大的缺口不是“前端不够正式”，也不是“模型还不够自由”，而是：

- 还没有第二个真实可执行 case，无法真正证明 case 空间内的选择能力
- 还没有一组正式的 ambiguity / clarify 评测集，无法稳定证明 `resolved / clarify_required / unavailable` 的分流质量
- 当前验收主要证明了“机制成立”，还没有形成“跨 query、跨 case、跨 clarify 场景”的能力证据

因此，Phase3-C 的正确目标应当是：

> 用第二个真实 case 和一套 clarify 评测，把现有结构升级为可验证、可量化、可用于论文和汇报的能力证据。

---

## 2. 本阶段目标

本阶段完成后，系统应当能够明确证明以下结论：

- 系统不再只会把自然语言请求静默投影到 `gura`
- LLM 可以在受治理的 `water_yield` case 空间内做 case 选择或发起 clarify
- clarify 是正式 workflow 行为，而不是调试辅助逻辑
- control layer 仍然独占 `WAITING_USER / can_resume / required_user_actions` authority
- 第二个真实 case 可以通过 registry/data onboarding 接入，而不是新增硬编码旁路
- 系统已经具备一套正式的 ambiguity / clarify 评测方法，而不是只展示少量单点成功样例

---

## 3. 总体策略

本阶段采用“三条主线并行，但能力优先”的推进方式：

1. 第二真实案例接入
2. ambiguity / clarify 评测集与验收链
3. 最小必要的前端补完与证据文档

优先级顺序固定为：

1. 先补第二个真实可执行 case
2. 再补 ambiguity / clarify 评测和自动化验收
3. 最后补页面和文档中的正式呈现

原因：

- 没有第二个真实 case，多 case-ready 只能停留在结构层
- 没有 clarify 评测，LLM 的 case projection 无法形成正式证据
- 页面和展示应承接稳定能力边界，而不应先行定义能力

---

## 4. 工作流 A：第二真实案例接入

### 4.1 目标

在同一 `water_yield` skill 内接入第二个真实可执行案例，并保证：

- 接入方式完全通过 case registry 和 data onboarding 完成
- 不新增面向单个 case 的硬编码主路径
- 新 case 能参与 `resolved / clarify_required` 分流
- 新 case 能跑通真实 InVEST 执行链

### 4.2 交付要求

新增 case 至少应包含以下 registry 元数据：

- `case_id`
- `display_name`
- `aliases`
- `intent_signals`
- `required_roles`
- `provider_key`
- `runtime_profile`
- `sample_root`
- `default_args`
- `descriptor_version`
- `executable`

数据接入要求：

- 样例数据目录必须符合当前 real-case runtime 的挂载约定
- `run_manifest` 和 `runtime_request` 必须记录新 case 的 `case_descriptor_version`
- `slot_bindings`、`args_draft`、provider 实际消费路径必须保持一致

### 4.3 代码要求

- Python service 中新增 case 仅允许修改 registry / loader / projection 逻辑
- Java backend 不允许新增新的 case-specific path assembly
- `ExecutionContractAssembler` 继续只注入 control-owned authority 字段
- runtime extractor 不允许为第二 case 新增单独硬编码解析分支

### 4.4 完成判据

- 第二 case 能通过 registry 被识别
- 直接 query 第二 case 时，系统可 `resolved`
- 真实执行成功并产出 artifact / result / runtime evidence
- 不引入新的 case-specific shortcut 分支

---

## 5. 工作流 B：ambiguity / clarify 能力固化

### 5.1 目标

把当前已有的 `case_projection` 与 clarify 机制从“功能存在”推进到“正式评测对象”。

### 5.2 要完成的行为

系统必须能稳定区分三种认知结果：

- `resolved`
- `clarify_required`
- `unavailable`

对应的治理结果必须固定为：

- `resolved`：进入 validation / pass2 / submit
- `clarify_required`：进入 `WAITING_USER`
- `unavailable`：按 cognition failure 治理，不伪成功

### 5.3 Clarify 正式行为定义

clarify 继续通过现有 `/tasks/{taskId}/resume` 收口，不新增新的主流程接口。

正式约定如下：

- `args_overrides.case_id` 作为 case selection 的标准输入
- `required_user_actions` 中 clarify 动作固定为：
  - `action_type = clarify`
  - `key = clarify_case_selection`
- `can_resume` 只有在 waiting-context 重评估通过后才能为 true

### 5.4 评测集建设

需要整理一组最小 clarify 数据集，至少分成四类 query：

- 明确命中 case A
- 明确命中 case B
- 模糊 query，需要 clarify
- 无法识别 / 不可执行 query

每类 query 至少准备：

- query 文本
- 预期 `case_projection.mode`
- 预期 candidate cases
- 预期 clarify prompt
- 最终应达成的 task outcome

### 5.5 完成判据

- ambiguity query 能稳定进入 `WAITING_USER`
- clarify 选择后能稳定恢复执行
- 非法 `case_id` resume 被拒绝
- clarify 场景能够形成正式 E2E 与手动验收记录

---

## 6. 工作流 C：评测、验收与证据化

### 6.1 自动化测试

需要新增或补强以下测试：

#### Service 侧

- registry lookup 正确
- alias / intent signal 命中正确
- known-case query 返回 `resolved`
- ambiguous query 返回 `clarify_required`
- `clarify_required` 时不得生成可执行 `args_draft`

#### Backend 侧

- `case_projection.mode=resolved` 才允许进入提交链
- `clarify_required` 必须进入 `WAITING_USER`
- clarify 场景下 `required_user_actions` 与 `can_resume` 只由 control layer 决定
- `/resume` 仅在合法 `case_id` 且 waiting-context 重评估通过时接受

#### E2E

至少保留以下场景：

- query 命中 case A -> `SUCCEEDED`
- query 命中 case B -> `SUCCEEDED`
- ambiguous query -> `WAITING_USER`
- 选择 case A -> `/resume` -> `SUCCEEDED`
- 选择 case B -> `/resume` -> `SUCCEEDED`
- 非法 clarify case_id -> resume 被拒绝
- 当前 success / repair / cancel 链继续保持通过

### 6.2 手动验收

需要更新手动验收清单，新增明确步骤：

- 如何触发 ambiguity query
- 如何查看 `Case Projection` 卡片
- 如何在页面中选择 candidate case
- 如何验证 `result / manifest / runtime evidence` 中的最终 `case_id` 一致

### 6.3 能力指标

建议为当前阶段引入最小评测指标，而不是只看样例是否通过：

- case resolution success rate
- clarify trigger precision
- clarify 后恢复成功率
- invalid clarify rejection rate
- real-case runtime success rate

这些指标即便初期只做小样本，也比单点案例展示更适合作为论文和汇报证据。

---

## 7. 工作流 D：前端最小补完

本阶段前端不是主线，但需要做最小必要补完，保证 clarify 和多 case 能被清晰展示。

### 7.1 需要补的页面

- task detail
- result page
- artifacts page
- task list

### 7.2 需要补的展示

- `Case Projection` 的 candidate case 列表应更清晰可读
- clarify 选择应从“可用”变成“明显可操作”
- task list 至少应显示：
  - `task_state`
  - `case_id`
  - `projection mode`
  - `runtime_profile`
- artifacts page 需要更正式地呈现：
  - primary
  - intermediate
  - audit
  - derived
  - logs

### 7.3 本阶段不做的事

- 不进行大规模首页重做
- 不进入复杂 dashboard 设计
- 不在前端加入业务裁决逻辑
- 不把前端变成新的 workflow authority

---

## 8. 正式交付物

本阶段结束时，仓库中至少应新增或更新以下交付物：

- 第二真实案例输入说明
- 第二真实案例输出与 artifact 映射说明
- ambiguity / clarify 测试集说明
- clarify E2E 验收脚本
- clarify 手动验收清单
- Phase3-C 验收记录
- Phase3-C Go / Conditional Go / No-Go 结论

---

## 9. 验收标准

只有同时满足以下条件，Phase3-C 才可判定为完成：

- 同一 `water_yield` skill 下至少两个真实 case 可执行成功
- ambiguity query 可稳定进入 clarify
- clarify 选择可通过 `/resume` 成功回到执行主链
- `WAITING_USER / can_resume / required_user_actions` 仍严格由 control layer 决定
- 第二 case 接入过程中未引入新的硬编码主路径
- 自动化测试、E2E、手动验收三类证据均齐全

如果只完成了第二 case 接入，但 clarify 评测证据不足，则结论只能是：

- `Conditional Go`

如果第二 case 仍依赖 case-specific shortcut 或 clarify 仍不稳定，则结论应为：

- `No-Go`

---

## 10. 推荐执行顺序

### Step 1

先确定第二真实可执行 case 数据包与 registry 元数据。

### Step 2

完成 registry/data onboarding，并打通第二 case 的真实执行链。

### Step 3

补 ambiguity / clarify query 集，并跑通 clarify -> resume -> success。

### Step 4

把 clarify 场景纳入 E2E 与手动验收。

### Step 5

最后补齐最小前端展示与正式文档。

---

## 11. 一句统一口径

如果需要给团队、导师、答辩或论文使用一句统一的阶段表述，建议采用：

> Phase3-C 的目标不是扩展 workflow authority，也不是追求开放域自主规划，而是通过第二真实案例接入与 ambiguity/clarify 评测，证明系统已经具备在受治理 case 空间内进行稳定 projection、clarify 与真实执行的能力。

