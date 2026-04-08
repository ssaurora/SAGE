# Phase3-C-边界加厚开发计划
更新日期：2026-04-05

## 0. 文档目的

本文用于定义 Phase3-C 的下一阶段开发重点。

Phase3-B 已经完成：

- 最小双 case governed projection
- `water_yield` 的真实 skill 资产化
- `catalog-first` 进入规划链、治理链、恢复链和冻结 manifest
- 最小 contract 集进入执行前、运行中、运行后和治理侧关键路径

因此，Phase3-C 的目标不再是继续扩广度，而是继续加厚两类边界：

- `catalog` 边界
- `contract` 边界

一句话概括：

> Phase3-C 不优先扩新的 skill 或更复杂的前端，而是把当前已经开始成立的事实层与 contract 层，推进到可冻结、可演化、可审计的工程状态。

---

## 1. 阶段目标

Phase3-C 的总目标是：

> 把当前系统从“catalog/contract 已进入真实主链”推进到“catalog/contract 具备明确生命周期、冻结语义、版本语义和一致性约束”的阶段。

当前最值得优先解决的不是“再多做一个能力”，而是以下两个问题：

1. 当前 catalog 仍偏向 `attachment projection + readiness facts`，还不是独立、稳定的事实生命周期对象
2. 当前 contract 已可真实消费，但版本冻结、兼容边界和 mismatch 审计语义还不够厚

---

## 2. 总体策略

Phase3-C 采用以下原则：

### 2.1 先加厚边界，再扩系统广度

在 catalog 与 contract 的冻结、版本和审计语义没有继续稳定之前：

- 不优先扩第二个真实 skill
- 不优先扩更多 capability
- 不优先做前端层面的更复杂表达

### 2.2 继续遵守层级边界

- catalog 生命周期属于事实层和控制层协调，不交给前端推断
- contract 约束属于 capability/control 交界，不交给 runtime 或前端兜底
- `WAITING_USER / can_resume / required_user_actions` 仍由 control layer 决定

### 2.3 继续坚持冻结优先

凡是已经进入：

- `WAITING_USER`
- `RESUMING`
- `QUEUED`
- frozen manifest

的对象，都不得隐式漂移到另一套事实或另一版 contract。

---

## 3. 工作主线 A：Catalog 生命周期加厚

## 3.1 目标

把当前的 `catalog_summary / catalog_consistency / catalog_revision / catalog_fingerprint`，从“主链可见事实”推进到“更独立、可持续演化的 catalog lifecycle”。

## 3.2 当前问题

虽然 catalog 已进入：

- `pass2`
- `MinReadyEvaluator`
- `RepairDispatcherService`
- `waiting_context`
- `detail / manifest / result`

但它仍主要来自 attachment projection，而不是完整的 catalog lifecycle。

## 3.3 本阶段要完成的工作

### A1. 建立更正式的 catalog persistence 语义

至少要让系统能明确区分：

- attachment 原始事实
- catalog 派生事实
- catalog revision 身份
- readiness 判定所依据的 catalog 版本

### A2. 引入更清晰的 catalog 状态字段

至少补齐以下语义中的最小子集：

- `parse_status`
- `availability_status`
- `revision`
- `fingerprint`
- `blacklist_flag`
- `stale / superseded` 判定

### A3. 统一 `resume / manifest / result / waiting_context` 的 catalog identity

后续要求不是“这些地方都能看到 catalog”，而是：

- 它们必须能明确回答基于哪一版 catalog facts 进行判断
- 发生 catalog 漂移时必须能显式识别，而不是隐式吞掉

### A4. 明确 catalog miss / stale / blacklist 的结构化错误语义

不能只以文案方式体现，至少要形成：

- 结构化状态
- 结构化原因
- 结构化审计记录

## 3.4 本线硬 gate

满足以下任一条件时，本线不算完成：

- catalog 仍然只是 attachment metadata 的别名投影
- `resume` 仍可能基于隐式变化后的 catalog facts 继续执行
- frozen manifest 与当前 detail/result 之间无法比较 catalog identity
- stale / blacklist / miss 仍主要靠非结构化文案表达

---

## 4. 工作主线 B：Contract 版本与冻结语义加厚

## 4.1 目标

把当前“已进入真实消费”的 contract，从最小 guard 状态推进到具备明确版本、冻结、兼容和 mismatch 审计语义的状态。

## 4.2 当前问题

当前 contract 已进入：

- resume
- validation
- submit
- runtime polling
- result collection
- artifact indexing
- cancel
- audit

但仍存在两个边界不足：

1. contract version / freeze 语义还不够明确
2. mismatch / drift / incompatible evolution 的收口语义还不够厚

## 4.3 本阶段要完成的工作

### B1. 给 contract 增加正式版本语义

至少要让关键 contract 能明确带出：

- `contract_name`
- `contract_version`
- `schema identity`
- `compatibility hint`

### B2. 建立冻结语义

一旦任务进入：

- `WAITING_USER`
- `RESUMING`
- `QUEUED`
- frozen manifest

后续继续执行时不得隐式切换到新的 contract 版本。

### B3. 明确 mismatch handling

至少要把以下情况结构化：

- contract 缺失
- contract 版本不匹配
- schema identity 不兼容
- caller scope 不匹配
- side effect level 不匹配

### B4. 强化审计语义

系统要能回答：

- 当时按哪份 contract 放行
- 当前失败是 contract 缺失还是 contract 漂移
- 当前任务为何不能 resume / submit / cancel / collect

## 4.4 本线硬 gate

满足以下任一条件时，本线不算完成：

- contract version 只是展示字段，没有冻结语义
- 已冻结任务仍可能隐式切换到 latest contract
- mismatch 仍主要依赖日志或字符串报错理解
- 审计层无法指出“基于哪份 contract”做出治理决策

---

## 5. 推荐实施顺序

Phase3-C 建议按以下顺序推进：

1. `Catalog persistence + revision model`
2. `resume / waiting_context / manifest / result` 的 catalog identity 全统一
3. `contract version / schema identity / freeze semantics`
4. `contract mismatch / drift / audit trace`
5. 仅在上述边界稳定后，再考虑新增 capability 或第二个真实 skill

这个顺序的原因很直接：

- catalog 与 contract 已经进入真实主链
- 现在最危险的不是“它们不存在”，而是“它们存在但还不够厚”
- 如果过早扩新的 capability，会把系统重新拉回广度先于边界的状态

---

## 6. 验收口径

Phase3-C 的阶段性验收，不以“页面更多”或“能力更多”为准，而以以下问题能否被系统清楚回答为准：

### Catalog 侧

- 当前任务的 waiting 判定基于哪一版 catalog
- 当前 manifest 冻结了哪一版 catalog
- 当前 result 关联的是哪一版 catalog
- 当前 `resume` 是否面临 catalog drift

### Contract 侧

- 当前任务的 resume / validate / submit / cancel / collect 基于哪一版 contract
- contract 不匹配时，系统是否给出结构化失败而不是静默 fallback
- 审计记录能否指出治理决策依据的是哪份 contract

如果这些问题还无法清楚回答，则 Phase3-C 不能算完成。

---

## 7. 暂不优先做的事项

在 Phase3-C 中，下列事项不应优先：

- 做更复杂的前端表达
- 扩第二个真实 skill
- 扩更多 capability 广度
- 重新包装 UI 叙事
- 提前做完整平台化抽象

这些工作不是永远不做，而是不应抢在 catalog/contract 边界加厚之前做。

---

## 8. 最终建议

当前最合理的下一步不是“让系统看起来更完整”，而是继续让系统的事实与约束边界变得更硬。

因此，Phase3-C 的工作重点应明确为：

> 继续加厚 `catalog-first` 和 `contract-first`，把它们从“已进入主链”推进到“可冻结、可演化、可一致性校验、可审计”的工程状态。

一句话概括：

> Phase3-B 完成了最小可治理框架；Phase3-C 应继续把这个框架的事实层和约束层做厚，而不是过早扩展系统广度。
