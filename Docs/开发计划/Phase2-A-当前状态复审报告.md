# Phase2-A 当前状态复审报告

更新日期：2026-03-27

> 补充说明：本报告形成后，已完成基于最新工作树的完整回归；最终验收记录见 `Docs/开发计划/Phase2-A-最终验收记录.md`。

## 1. 结论

当前项目最准确的阶段判断是：

- `Phase2-A` 的**核心代码目标已经基本落地**
- 项目已经从“建立最小 skill/capability 骨架”推进到“把 capability facts 贯穿到 control / repair / runtime / read model / frontend 展示”
- 但**还不建议现在宣布 Phase2-A 正式结束**

原因不是主链还没做出来，而是：

- 最新工作树上的 **全链路回归尚未重新补跑完整**
- `A6` 所要求的“本轮结项与验收封箱”还没有完全完成
- 当前工作树仍处于**大批量未提交改动**状态，不适合直接把这一轮当作稳定基线对外宣告完成

一句话判断：

> `Phase2-A` 已进入收口阶段，主问题已经不是“缺功能”，而是“缺最后一次完整验收与结项冻结”。

---

## 2. 对照 `A1 ~ A6` 的当前判断

| 项目 | 当前判断 | 说明 |
|---|---|---|
| `A1` 最小 `SkillDefinition` / `CapabilityDefinitionLite` | 已完成 | capability 骨架已落到 service schema |
| `A2` `water_yield` 第一份真实 skill | 已完成 | 已有集中定义，并进入 pass1/passB/runtime |
| `A3` validation 与 skill/capability 映射 | 基本完成 | 代码链已成立，映射说明文档也已有，但仍需用最新快照验收 |
| `A4` `waiting_context` / repair 输入派生规则 | 基本完成 | Dispatcher + TaskService + typed repair request 已成形 |
| `A5` repair schema / fallback 规范化 | 基本完成 | service 与 backend 两侧都已 typed 化并有 fallback |
| `A6` 本轮收口与最小验收 | 部分完成 | 之前做过回归，但不是基于当前最新工作树的最终封箱回归 |

因此，当前更准确的结论不是“Phase2-A 未完成”，而是：

- `A1 ~ A5` 已经进入**代码层面基本闭环**
- `A6` 仍然是当前真正的尾项

---

## 3. 当前已经落地到什么程度

## 3.1 Service / Capability 主线

当前 service 侧已经不再只是“有一个 skill_catalog 文件”，而是已经具备下面这条可工作的事实链：

`SkillDefinition -> pass1 facts -> passB arg draft -> validation facts -> repair input -> runtime output contract`

具体已落地内容包括：

- 最小 `SkillDefinition` / `CapabilityDefinitionLite`
- `water_yield` skill 的 canonical 定义
- `capability_key`
- `capability_facts`
- `role_arg_mappings`
- `stable_defaults`
- `validation_hints`
- `repair_hints`
- `output_contract`

这意味着：

- capability facts 已经不是“文档术语”
- 而是已经成为 service 的真实输出 contract

## 3.2 Java Control Layer 主线

当前 Java backend 最重要的推进，不是“又加了更多业务逻辑”，而是把原先散落的事实读取与派生规则逐步收到了统一边界里。

已经完成的关键收口包括：

- `Pass1FactHelper`
  - 集中读取 `capability_key`
  - `selected_template`
  - `template_version`
  - `runtime_profile_hint`
  - `role_arg_mappings`
  - `expected_slot_type`
  - `repair_hints`
- `GoalRouteService`
  - route enrichment
  - manifest fallback route derivation
- `RepairDispatcherService`
  - 不再直接拼 raw waiting JSON
  - 开始构造 typed `waiting_context`
- `RepairProposalService`
  - 以 typed request 为主入口
  - fallback 逻辑不再依赖散读 `JsonNode`
- `RepairFactHelper`
  - 统一 repair request / validation / failure typed 归一化
- `TaskProjectionBuilder`
  - detail / manifest / result 的 read model 投影集中化
- `TaskControlPayloadBuilder`
  - 事件、audit、snapshot payload 集中化
- `TaskService`
  - create / resume / validation / waiting / accepted job / runtime terminal / success output 等路径的大量重复块已被抽出

这说明：

- control layer 的职责边界比之前清楚很多
- `TaskService` 虽然仍大，但已经从“直接散写所有派生规则”明显转向“调用 helper / builder 编排”

## 3.3 Repair / Waiting 主线

这条链是本轮最重要的实质性成果之一。

当前已经形成：

`validation facts + pass1 facts + attachments -> Dispatcher recomposition -> waiting_context -> repair request -> repair proposal / fallback`

并且具备以下约束：

- `pass1Result` 只是事实源之一，不是 authority 本体
- `waiting_context` 由控制层重组，不由前端或 LLM 决定
- `repair_proposal` 已 typed 化
- fallback 已 typed 化
- Java backend 没有继续扩散 prompt/provider/model 逻辑

这和 `AGENTS.md` 里的约束是对齐的。

## 3.4 Runtime / Traceability / Read Model 主线

本轮工作不只影响输入修复链，也已经开始影响结果和可追踪性语义：

- `output_contract` 已进入 runtime 结果产出
- `result_bundle` / `artifact_catalog` 已带 capability 驱动的输出语义
- `TaskDetailResponse` / `TaskManifestResponse` / `TaskResultResponse` 的主要 read-model 字段已经大幅 typed 化
- 前端 detail/result/artifacts 页面的结构化面板已经基本替代了原先的大量通用 JSON 网格

这意味着：

- capability facts 已开始影响“系统如何解释结果”
- 不再只停留在“输入校验与 repair”层

---

## 4. 当前验证状态

## 4.1 已确认通过的验证

本轮开发过程中，已经确认通过过以下验证：

- `BackEnd`
  - `mvn -q test`
- `Service/planning-pass1`
  - `conda run -n sage-cognitive python -m pytest tests/test_pass1_api.py`
- `FrontEnd`
  - `npm run build`
- 集成链
  - `Week5 E2E` 通过
  - `Week6 E2E` 通过

## 4.2 需要特别说明的地方

虽然上面这些验证都跑通过过，但当前必须严格区分：

- **“本轮过程中曾通过”**
- 和
- **“针对当前最新工作树快照重新完整跑通过”**

这两件事不是同一件事。

目前更准确的说法是：

- backend 最新一轮改动后的 `mvn -q test` 已通过
- 但 service / frontend / Week5 / Week6 **尚未基于当前最新快照重新封箱复跑**

因此，不能把当前状态直接表述为“Phase2-A 已正式验收完成”。

---

## 5. 当前剩余差距

当前剩余差距已经不再是“主功能没做”，而主要集中在三个方面。

## 5.1 最新快照的全链路回归还没重新补齐

这是当前最实际、最关键的未完成项。

建议至少重跑：

- `BackEnd`: `mvn -q test`
- `Service`: `python -m pytest tests/test_pass1_api.py`
- `FrontEnd`: `npm run build`
- `scripts/week5-e2e.ps1`
- `scripts/week6-e2e.ps1`

并把结果作为本轮最终封箱记录。

## 5.2 A6 结项动作还没完成

当前仍缺：

- 基于最新快照的验收记录
- 明确的 Phase2-A 收口结论
- 进入 Phase2-B 前的剩余债务清单冻结

## 5.3 工作树还没有冻结成稳定基线

当前工作树仍有大批量未提交改动，横跨：

- `BackEnd`
- `Service`
- `FrontEnd`
- `Docs`

这说明当前更像是：

- 一轮大改已经基本完成
- 但还处于“准备收口”的开发态

而不是“已经冻结的阶段基线”

---

## 6. 现在是否可以宣告 Phase2-A 完成

当前我的判断是：

**不能正式宣告完成，但已经非常接近。**

如果只看代码骨架与主链设计，`Phase2-A` 已经基本成立。

如果要严格按阶段验收来判定，还缺最后一步：

- 用当前最新工作树重新做完整回归
- 形成一次明确的收口记录

也就是说，当前项目状态最准确的表达应是：

> `Phase2-A` 已经完成核心开发，处于最终验收与封箱前状态。

---

## 7. 对下一步的建议

当前不建议再继续做大量低收益微重构。

最合理的下一步顺序是：

1. 先对当前最新工作树做一次完整回归
2. 补齐 `A6` 的最终验收记录
3. 冻结本轮剩余债务
4. 再决定是否正式关闭 `Phase2-A`

如果这一步做完，后续才适合进入：

- `P2-4` 控制面强治理恢复链
- `P2-5` Manifest 冻结与版本治理
- `P2-6` 规划面工业编译器增强

---

## 8. 最终判断

截至当前代码状态，项目已经达到以下程度：

- capability/skill 不再是概念层补丁，而是已经进入系统真实事实链
- waiting/repair 已经从 raw JSON + scattered rules 明显转向 typed + centralized derivation
- control layer 的大量重复编排与 payload 构造已经得到系统性收口
- frontend/read-model 已基本摆脱“通用 JSON 展示”阶段

当前尚未完成的，不是核心方向，而是最后一次严格的封箱动作。

因此，本报告的最终结论是：

> 项目当前处于 `Phase2-A` 的收口前夜；主链已成，验收未封箱。
