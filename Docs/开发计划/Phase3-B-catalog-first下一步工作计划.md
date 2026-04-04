# Phase3-B / catalog-first 下一步工作计划
更新日期：2026-04-03

## 0. 文档目的

本文用于承接 [Phase3-B-catalog-first深化记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-B-catalog-first深化记录.md) 的结论，定义 catalog-first 路线的下一步工作。

当前系统已经做到：

- `pass2` 消费 `metadata_catalog_facts`
- `MinReadyEvaluator` 与 `RepairDispatcherService` 复用同一套 catalog-ready 语义
- `waiting_context` 已承载 `catalog_summary`
- `TaskDetail / TaskManifest / TaskResult` 已承载 `catalog_summary / catalog_consistency`

但这些能力仍主要停留在 backend 读模型层。下一步工作的重点，不再是“继续加 backend 字段”，而是：

1. 把已有 catalog facts 和一致性结论真正投影到前端页面
2. 让 catalog 逐步从 attachment projection 走向更正式的生命周期事实

---

## 1. 当前基线

## 1.1 backend 已具备的事实

当前 backend 已能稳定输出：

- `waiting_context.catalog_summary`
- `task.catalog_summary`
- `task.catalog_consistency`
- `manifest.catalog_summary`
- `manifest.catalog_consistency`
- `result.catalog_summary`
- `result.catalog_consistency`

这些字段的来源已经统一到：

- [AttachmentCatalogProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/AttachmentCatalogProjector.java)
- [RepairDispatcherService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/repair/RepairDispatcherService.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

## 1.2 前端当前仍缺的部分

当前前端代码基线显示：

- [api.ts](/e:/paper_project/SAGE/FrontEnd/src/lib/api.ts) 尚未把 `catalog_summary / catalog_consistency` 类型透传到 `TaskDetailResponse / TaskManifestResponse / TaskResultResponse`
- [tasks/[taskId]/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/[taskId]/page.tsx) 虽已展示 governance、manifest、repair、result summaries，但尚未展示 catalog summary / consistency
- [tasks/[taskId]/result/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/[taskId]/result/page.tsx) 已展示 runtime evidence 和 input bindings，但尚未展示 result 侧的 catalog coverage 结论

因此当前最大的缺口是：

> backend 已经有了结构化 catalog 解释对象，但前端尚未把这些对象转化为稳定、可读、可用于审计的用户界面。

---

## 2. 下一步总体目标

下一步目标不是扩更多字段，而是完成两个最小闭环：

### A. catalog-first 前端投影闭环

让用户和审计侧能够在实际页面上看见：

- 当前 catalog 摘要
- 当前 waiting 是否和 catalog facts 一致
- manifest slot bindings 是否被 catalog-ready roles 覆盖
- runtime input bindings 是否被 catalog-ready roles 覆盖

### B. catalog 生命周期语义闭环

让 catalog 不再只是 attachment projection 的副产品，而是开始具备最小生命周期语义，例如：

- catalog source
- catalog version / projection revision
- freeze / resume 过程中使用的是哪一份 catalog 事实

这两个闭环中，A 必须先于 B。

原因很简单：

- 如果前端还看不到 backend 已经给出的 catalog 结论，再继续加 catalog 生命周期字段，收益很低
- 先把已有事实投影到页面，才能反过来检验哪些 catalog 生命周期字段是必须补的

---

## 3. 工作流 A：前端投影

## 3.1 目标

把已有的 `catalog_summary / catalog_consistency` 接到前端类型和页面中，但保持边界清晰：

- 前端只展示 backend 已给出的结论
- 前端不自行重算 catalog coverage
- 前端不自行解释 `WAITING_USER` authority

## 3.2 要完成的内容

### 3.2.1 更新 API 类型

先在 [api.ts](/e:/paper_project/SAGE/FrontEnd/src/lib/api.ts) 中补齐：

- `TaskDetailResponse.catalog_summary`
- `TaskDetailResponse.catalog_consistency`
- `TaskDetailResponse.waiting_context.catalog_summary`
- `TaskManifestResponse.catalog_summary`
- `TaskManifestResponse.catalog_consistency`
- `TaskResultResponse.catalog_summary`
- `TaskResultResponse.catalog_consistency`

这是第一步硬前提。没有类型透传，页面改动会继续依赖隐式 JSON 调试。

### 3.2.2 在 Task Detail 页增加 catalog 面板

在 [tasks/[taskId]/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/[taskId]/page.tsx) 中，至少新增两块：

#### A. Governance / Waiting Context 侧

展示：

- `waiting_context.catalog_summary`
- `task.catalog_consistency`

用户应该能够直接看见：

- 当前 waiting 是否基于 catalog facts
- `stale_missing_slots` 是否非空
- `waiting_context_matches_current_catalog` 是否为 true

这块的重点是：

- 把“为什么系统还判你缺输入”解释清楚
- 而不是只重复一遍 `missing_slots`

#### B. Manifest 侧

展示：

- `manifest.catalog_summary`
- `manifest.catalog_consistency`

用户应该能够看见：

- 当前 frozen slot bindings 的 expected roles
- 当前 catalog 是否覆盖这些 roles
- 哪些 roles 仍不被 catalog-ready facts 覆盖

### 3.2.3 在 Result 页增加 catalog consistency 面板

在 [tasks/[taskId]/result/page.tsx](/e:/paper_project/SAGE/FrontEnd/src/app/tasks/[taskId]/result/page.tsx) 中，新增一块：

- `Result Catalog Coverage`

展示：

- `result.catalog_summary`
- `result.catalog_consistency`

重点解释：

- runtime `input_bindings` 是否与当前 catalog-ready roles 一致
- `covered` 是否为 true
- `missing_catalog_roles` 是否为空

这块的作用不是替代 runtime evidence，而是给 runtime evidence 一层更容易读的 consistency 结论。

## 3.3 本阶段不做的事情

- 不在前端重新计算 catalog consistency
- 不引入新的前端 workflow authority
- 不把 catalog summary 渲染成大量调试 JSON 作为唯一展示方式
- 不把普通用户页面变成内部开发控制台

## 3.4 前端验收标准

只有同时满足以下条件才算通过：

- `api.ts` 正式透传相关字段
- Task Detail 页面能看到 waiting / manifest 的 catalog summary 与 consistency
- Task Result 页面能看到 result catalog coverage
- 页面只消费 backend 提供的结构化字段，不自行做 authority 判断
- 至少一个 waiting 场景能在页面上明确显示 `stale_missing_slots` 或一致性结论

---

## 4. 工作流 B：catalog 生命周期语义

## 4.1 目标

在不建设完整 Metadata Catalog 平台的前提下，引入最小生命周期语义，让 catalog facts 不再只是“当下 attachment projection 的快照”。

## 4.2 要完成的内容

### 4.2.1 最小 catalog revision 语义

在 backend 内部至少引入一种轻量 revision 概念，用于标识：

- 当前 `catalog_summary` 来自哪一轮 attachment projection
- waiting / manifest / result 读到的 catalog facts 是否同源

本阶段不要求独立数据库表，但至少要做到：

- 有明确 revision 或 fingerprint
- 能在 resume / waiting-context recompute 时保留该值

### 4.2.2 resume / manifest / result 的 catalog freeze 边界

至少写清并逐步落一条规则：

- `waiting_context` 使用的是当前 catalog facts
- `manifest` 使用的是 freeze 时刻的 slot bindings
- `result` 使用的是 runtime evidence 中的 input bindings

下一步要做的不是把三者混成一个 source of truth，而是让三者的 relation 更可解释。

因此建议新增最小字段或内部语义：

- current catalog revision
- manifest catalog relation
- result catalog relation

### 4.2.3 为未来独立 catalog 生命周期留边界

当前不落地完整 `asset_version / parse_status / FULL_READY / delta_inventory`，但要为后续留边界：

- catalog facts 的构造入口
- revision/fingerprint 的计算入口
- future persistence 的接入点

## 4.3 本阶段不做的事情

- 不建设完整 catalog persistence 平台
- 不引入新的 source of truth 替代 `task_state / manifest / job_record`
- 不把 catalog lifecycle 直接做成重型 inventory 系统

## 4.4 生命周期验收标准

只有同时满足以下条件才算通过：

- backend 内部存在最小 catalog revision / fingerprint 语义
- waiting / manifest / result 至少能解释彼此与 catalog 的 relation
- 该语义不破坏既有 `WAITING_USER / resume / freeze manifest / result evidence` 边界

---

## 5. 推荐执行顺序

### Step 1

先完成前端 API 类型透传。

### Step 2

完成 Task Detail 页面中的：

- waiting catalog summary
- waiting catalog consistency
- manifest catalog summary
- manifest catalog consistency

### Step 3

完成 Task Result 页中的：

- result catalog summary
- result catalog consistency

### Step 4

在 backend 中补最小 catalog revision / fingerprint 语义。

### Step 5

最后再决定是否把这一层上升为更正式的 catalog lifecycle 计划。

---

## 6. 一句统一口径

如果需要一句统一表述，建议使用：

> 下一步工作的重点不是继续新增 catalog 字段，而是把已形成的 catalog summary 与 consistency 真正投影到前端和治理读模型中，并为后续独立 catalog 生命周期语义打下最小边界。

