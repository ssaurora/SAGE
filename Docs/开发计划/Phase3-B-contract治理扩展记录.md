# Phase3-B-contract治理扩展记录
更新日期：2026-04-05

## 0. 文档目的

本文用于记录 Phase3-B 在最小 contract 集全链消费之后的进一步深化：

> 将 `cancel_job`、`index_artifacts`、`record_audit` 三类 contract 接入真实控制路径，使 contract 约束从执行主链继续扩展到治理与收口侧路径。

本文不讨论新的阶段计划，只记录本轮已经真实落地的边界变化。

---

## 1. 本轮工作的背景

在上一轮收口后，系统已经完成：

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`
- `query_job_status`
- `collect_result_bundle`

这 6 个 contract 的真实消费。

但当时仍存在三个剩余缺口：

- `cancel_job`
- `index_artifacts`
- `record_audit`

也就是说，系统已经能说：

- contract 已约束执行前关键路径
- contract 已约束运行后结果归集关键路径

但还不能完整地说：

- contract 已约束取消治理
- contract 已约束 artifact indexing
- contract 已约束治理链上的 audit 写入

本轮工作的目标，就是补上这三个边界。

---

## 2. 本轮完成的核心实现

### 2.1 Skill asset 中新增三类 contract

本轮首先在：

- [mcp_tools_map.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/mcp_tools_map.yaml)

中新增了三类 contract：

- `cancel_job`
- `index_artifacts`
- `record_audit`

其最小语义如下：

#### `cancel_job`

- `caller_scope = control_only`
- `side_effect_level = runtime_cancellation`

#### `index_artifacts`

- `caller_scope = control_only`
- `side_effect_level = artifact_indexing`

#### `record_audit`

- `caller_scope = control_only`
- `side_effect_level = audit_write`

这一步的意义是：

> skill asset 中的最小 contract 集开始从“执行相关 contract”扩展到“治理相关 contract”。

### 2.2 CapabilityContractGuard 已扩展到三类治理 contract

本轮扩展了：

- [CapabilityContractGuard.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CapabilityContractGuard.java)

新增：

- `requireCancelJobContract(...)`
- `requireIndexArtifactsContract(...)`
- `requireRecordAuditContract(...)`

和此前 contract guard 一样，这三类 contract 仍只校验最小必要字段：

- `caller_scope`
- `side_effect_level`
- `input_schema`
- `output_schema`

这保证了：

- 本轮仍然是“最小真实消费”
- 没有演变成重平台或复杂 negotiation 系统

### 2.3 `cancel_job` 与 `record_audit` 已进入 cancel 主链

本轮在：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

中的 `cancelTask(...)` 路径加入了新的真实消费顺序：

1. 先读取 `pass1_result_json`
2. 校验 `cancel_job`
3. 校验 `record_audit`
4. 只有通过之后，才允许：
   - 写 cancel audit
   - 写 cancel event
   - 调用 `jobRuntimeClient.cancelJob(...)`

这意味着：

- 缺少 `cancel_job` contract 时，不会发出 runtime cancellation
- 缺少 `record_audit` contract 时，也不会继续 cancel side effect

这一步非常关键，因为它避免了一个常见的半治理状态：

- 任务已经被取消
- 但治理链并没有合法记录这一动作

当前系统通过 contract guard 避免了这种状态。

### 2.4 `index_artifacts` 已进入 success promotion 主链

本轮同样在：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

中的 `processSuccess(...)` 路径加入了：

- `requireIndexArtifactsContract(...)`

并将其与已有的：

- `requireCollectResultBundleContract(...)`

并列使用。

当前 success promotion 的关键约束顺序变为：

1. 校验 `collect_result_bundle`
2. 校验 `index_artifacts`
3. 只有通过之后，才允许执行：
   - `persistSuccess(...)`
   - result bundle persistence
   - artifact persistence / indexing

这意味着：

- artifact indexing 不再只是 success path 的默认副产物
- 它开始成为受 contract 明确约束的 control-side side effect

---

## 3. 本轮形成的系统边界变化

本轮最重要的变化是：contract 已不再只围绕“分析执行本身”。

### 3.1 之前的状态

此前系统已经完成：

- resume / validation / submit_job
- runtime polling
- result bundle collection

这些路径的 contract 消费。

但治理链仍有一部分是默认行为：

- cancel
- audit
- artifact indexing

### 3.2 现在的状态

本轮之后，系统更接近下面这个状态：

- 没有合法 `cancel_job` contract，不允许取消 runtime job
- 没有合法 `record_audit` contract，不允许继续 cancel 主链
- 没有合法 `index_artifacts` contract，不允许继续 success promotion 主链

因此，最小 contract 集已经不只影响：

- 能不能开始执行
- 能不能完成结果归集

还开始影响：

- 能不能执行治理动作
- 能不能完成治理痕迹记录
- 能不能完成 artifact 收口

---

## 4. 当前最小 contract 集覆盖范围

截至本轮，最小 contract 集已覆盖的真实控制路径包括：

### 4.1 执行前

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`

### 4.2 运行中

- `cancel_job`
- `query_job_status`

### 4.3 运行后

- `collect_result_bundle`
- `index_artifacts`

### 4.4 治理侧

- `record_audit`

因此，当前系统已经形成：

> 最小 contract 集覆盖执行前、运行中、运行后和治理侧关键路径的状态。

---

## 5. 测试与验证

### 5.1 更新的测试

本轮更新了：

- [CapabilityContractGuardTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/CapabilityContractGuardTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

重点新增断言包括：

#### A. `cancel_job` 缺失时

- `cancelTask(...)` 不得调用 runtime cancel

#### B. `record_audit` 缺失时

- `cancelTask(...)` 不得继续 cancel 主链 side effect

#### C. `index_artifacts` 缺失时

- success promotion 不得继续 artifact persistence / indexing

### 5.2 实际验证

本轮已实际执行并通过：

```powershell
mvn -q "-Dtest=CapabilityContractGuardTest,TaskServiceGovernanceTest,TaskServiceCognitionFlowTest" test
```

以及补充定向回归：

```powershell
mvn -q "-Dtest=AttachmentCatalogProjectorTest,TaskProjectionBuilderTest,RepairDispatcherServiceTest,MinReadyEvaluatorTest" test
```

以及真实链路 success 回归：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404172321545_c43a69c4`

---

## 6. 本轮仍未完成什么

这份记录必须继续收口，不把当前结果夸大成“完整 contract 平台”。

当前仍未完成的方面包括：

- cancel 之后更细粒度的 artifact / audit 后续 contract 编排
- provider negotiation / capability registry 的更高层协同
- 多 capability / 多 skill 条件下的更复杂 contract 演化策略

因此，本轮最准确的表述仍然是：

- 完成了最小 contract 集的治理侧扩展
- 但还没有完成完整能力面平台

---

## 7. 最终结论

### 7.1 已完成

- `cancel_job` 已进入 cancel 主链
- `record_audit` 已进入 cancel 主链
- `index_artifacts` 已进入 success promotion 主链
- 最小 contract 集已覆盖执行前、运行中、运行后与治理侧关键路径

### 7.2 最准确的阶段性表述

当前系统已经完成从“contract 只约束分析执行主链”向“contract 同时约束治理动作、artifact 收口与治理痕迹写入”的推进；但距离完整能力面 contract 驱动平台，仍有继续深化空间。

