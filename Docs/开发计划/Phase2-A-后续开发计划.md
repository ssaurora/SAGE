# Phase2-A 后续开发计划

更新时间：2026-03-26

## 1. 计划依据

本计划直接依据以下两份文档制定：

- `Docs/开发计划/Phase2-A-开发进度审查.md`
- `Docs/开发计划/Phase2-A-开工任务单.md`

当前判断已经明确：

- `A1`、`A2` 已完成
- `A3`、`A4`、`A5`、`A6` 仅部分完成

因此，接下来的开发不再以“补新的能力骨架”为主，而是以：

- 补齐规则文档
- 强化关键派生链的可审计性
- 基于最新代码快照完成收口验收

为主。

---

## 2. 总目标

下一阶段的目标不是进入 `Phase2-B`，而是把 `Phase2-A` 从“代码骨架已落地”推进到“文档、规则、验收三者闭环”。

本轮结束后，应能明确回答三件事：

- validation 与 capability facts 的映射到底是什么
- `waiting_context` 与 repair proposal 的 authority / advisory 边界到底是什么
- 当前最新代码快照是否已经稳定通过 `Phase2-A` 的最小验收

---

## 3. 总体顺序

建议按以下顺序执行：

`P1 -> P2 -> P3 -> P4`

其中：

- `P1`：补 `A3` 文档与映射说明
- `P2`：补 `A4` 派生规则文档与边界说明
- `P3`：补 `A5` repair schema / fallback 治理文档
- `P4`：完成 `A6` 最新代码快照下的回归验收与收口文档

---

## 4. 分阶段计划

## P1：完成 A3 收口 —— validation 与 skill/capability 映射文档化

### 目标

把当前已经落地到代码中的 validation-to-skill/capability 关系，整理成独立文档，形成可审计的字段来源说明。

### 主要任务

- 盘点当前 validation 输出字段：
  - `is_valid`
  - `missing_roles`
  - `missing_params`
  - `error_code`
  - `invalid_bindings`
- 明确每个字段的事实来源：
  - 来自 pass1 skill facts
  - 来自 passB bindings
  - 来自 capability validation hints
- 明确哪些字段：
  - 可直接驱动 `waiting_context`
  - 只能作为 repair proposal 输入
- 形成 validation-to-skill/capability 映射表

### 主要交付物

- 新文档：`Phase2-A-validation-skill-映射说明.md`

### 代码侧动作

- 如发现映射关系仍有隐式逻辑，再做小范围补强
- 不新增新流程，不改架构边界

### 完成判据

- 能直接回答“某个 `waiting_context` 字段由哪些 validation/capability 事实推导而来”
- 文档与当前代码实现一致

---

## P2：完成 A4 收口 —— waiting_context 与 repair 输入派生规则显式化

### 目标

把目前散落在 `RepairDispatcherService`、`TaskService`、`repair.py` 里的派生规则整理为正式说明，消除“知道代码怎么跑，但说不清规则”的状态。

### 主要任务

- 明确 `waiting_context` 的 authority 来源：
  - `pass1Result`
  - `validationSummary`
  - `attachments`
- 明确 `repair proposal` 的 advisory 来源：
  - `waiting_context`
  - `validation_summary`
  - `failure_summary`
  - `user_note`
- 明确 `can_resume` 的重计算触发点：
  - create 后首次 WAITING_USER
  - 上传附件后 refresh
  - resume 前重算
- 明确禁止事项：
  - 不能把单一输入对象直接透传成 waiting authority
  - 前端不能重建 repair 语义

### 主要交付物

- 新文档：`Phase2-A-waiting-repair-派生规则.md`

### 代码侧动作

- 只做必要的收敛性补丁
- 不引入新状态机，不提前做 `P2-4`

### 完成判据

- `waiting_context` 的字段来源、派生链、authority 边界均可被文档化描述
- 文档中的派生规则能对应到当前 Java/Python 代码实现

---

## P3：完成 A5 收口 —— repair proposal schema / fallback / debug 规范文档化

### 目标

把 repair proposal 从“代码已经可工作”推进到“外部可审查、可治理”。

### 主要任务

- 明确 repair request schema
- 明确 repair response schema
- 明确 fallback 触发矩阵：
  - timeout
  - 空内容
  - 非 JSON
  - fenced JSON
- 明确 debug 信息暴露规则：
  - 哪些只在 `SAGE_REPAIR_DEBUG=true` 下出现
  - 哪些绝不进入正式 authority 结构
- 明确 Java backend 与 Python service 的边界

### 主要交付物

- 新文档：`Phase2-A-repair-schema与fallback说明.md`

### 代码侧动作

- 只在发现文档与实现不一致时做最小修复
- 不扩散 Java prompt/provider/model 逻辑

### 完成判据

- repair schema 与 fallback 规则可独立阅读
- debug 字段规则清晰
- 与当前 `repair.py` 和 backend client 实现一致

---

## P4：完成 A6 收口 —— 用最新代码快照做回归验收并形成结项记录

### 目标

基于当前最新代码快照，而不是旧快照，完成一次 Phase2-A 的最小验收闭环。

### 主要任务

- 运行并记录：
  - `conda run -n sage-cognitive python -m pytest tests/test_pass1_api.py`
  - `mvn -q test`
  - `scripts/week5-e2e.ps1`
  - `scripts/week6-e2e.ps1`
- 由于 Docker Desktop 稳定性问题，验收策略继续采用：
  - 分步执行
  - 周与周之间单独清理 / 重启 Docker
- 形成验收记录文档
- 形成 `Phase2-B` 进入条件清单
- 明确尚未完成项与技术债

### 主要交付物

- 新文档：`Phase2-A-验收记录.md`
- 新文档：`Phase2-B-进入条件清单.md`

### 完成判据

- 最新代码快照下的 service / backend / Week5 / Week6 回归都有明确记录
- 可以明确写出：
  - `Phase2-A` 已完成什么
  - 仍未完成什么
  - 哪些项必须进入 `Phase2-B`

---

## 5. 本轮不做的事情

本计划仍然明确不进入以下内容：

- `P2-4` 控制面强治理恢复链
- `P2-5` Manifest freeze / versioning
- `P2-6` planning compiler 增强
- `P2-7` assertion push-down / failure 建模 / 前端大收口

原因很简单：

- 当前审查文档指出的主要缺口已经不是“主干逻辑没有写”，而是“收口物还没补齐”
- 如果此时提前进入 `Phase2-B`，会造成 `Phase2-A` 未封箱、规则未显式化、验收记录不完整

---

## 6. 优先级与预期耗时

建议优先级：

- **第一优先**：`P1` + `P2`
- **第二优先**：`P3`
- **第三优先**：`P4`

建议执行方式：

- 先完成三份规则文档
- 再统一做一次最新快照验收

原因：

- 先文档化规则，再做最终回归，能减少“测过了但说不清测的是什么”的问题

---

## 7. 阶段完成定义

以下条件全部满足后，才可考虑关闭 `Phase2-A`：

- `A3` 映射文档完成
- `A4` 派生规则文档完成
- `A5` repair schema / fallback 文档完成
- 最新代码快照下：
  - service pytest 通过
  - backend unit test 通过
  - Week5 E2E 通过
  - Week6 E2E 通过
- 已形成 `Phase2-A` 验收记录
- 已形成 `Phase2-B` 进入条件清单

---

## 8. 一句话执行建议

接下来的开发重点不是“继续扩 capability 功能面”，而是把已经落地的 capability/repair/waiting 主链：

- 说清楚
- 记清楚
- 验清楚

然后再进入 `Phase2-B`。

