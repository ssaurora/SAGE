# Phase3-B-contract真实消费记录
更新日期：2026-04-05

## 0. 文档目的

本文用于记录 Phase3-B 后续深化中的一项关键收口：

> 将 capability / contract 从“仅在 asset 与读模型中可见”，推进为“在执行前关键控制点被真实消费”。

这份记录不把本轮工作表述为“完整能力面落地”，而是明确限定为：

- 最小 contract 集开始进入真实控制路径
- control layer 开始用 contract 约束 resume / validation / submit_job
- contract 不再只是展示字段或调试信息

本文的目标是回答三个问题：

1. 本轮到底完成了什么
2. 它如何改变系统中的真实执行边界
3. 还有哪些 contract 尚未进入真实消费

---

## 1. 本轮工作的定位

在此前阶段中，系统已经完成：

- `water_yield` skill 资产化
- `mcp_tools_map.yaml` 中最小 contract 集的结构化定义
- `detail / manifest / result` 读模型中的 contract 可见性

但那仍然存在一个明显边界：

> contract 虽然“存在且可见”，却还没有真正成为 control layer 的执行前约束。

换句话说，系统此前更像是：

- contract 已被记录
- contract 已被展示
- 但 resume、validation、submit_job 这些关键路径还没有真正因 contract 缺失或不匹配而停止

本轮工作的目标，就是补上这道边界。

---

## 2. 本轮完成的核心实现

### 2.1 新增最小 contract guard

本轮新增了专门的 contract 守卫组件：

- [CapabilityContractGuard.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CapabilityContractGuard.java)

该组件负责对 `pass1.capability_facts.contracts` 中的 contract 做最小必要校验。

当前实际校验的 contract 包括：

- `checkpoint_resume_ack`
- `validate_bindings`
- `validate_args`
- `submit_job`

当前实际校验的最小字段包括：

- `caller_scope`
- `side_effect_level`
- `input_schema`
- `output_schema`

这意味着：

- contract 缺失，不再等同于“没有影响”
- contract 语义不匹配，不再允许继续执行主链

### 2.2 Contract 已进入三个真实控制点

本轮没有新增新的主流程 API，而是在现有控制路径中加入 contract 守卫。

相关实现见：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

当前 contract 的真实消费点如下：

#### A. Resume 路径消费 `checkpoint_resume_ack`

在 `runResumePipeline(...)` 中，系统会先校验 `checkpoint_resume_ack` 是否存在且满足最小语义。

这意味着：

- `/resume` 不再只是依赖 task state 和 waiting_context
- 它开始同时依赖 capability contract 的显式存在

#### B. Validation 路径消费 `validate_bindings` 与 `validate_args`

在 `runValidationStage(...)` 中，系统会先校验：

- `validate_bindings`
- `validate_args`

只有通过 contract guard，才允许进入后续 validation 主链。

这意味着：

- validation 不再只是“已有代码逻辑”
- 它开始具有 contract 级前置约束

#### C. Submit Job 前消费 `submit_job`

在 `prepareAcceptedJobSubmission(...)` 中，系统会在真正提交 job 前校验 `submit_job` contract。

这意味着：

- job submission 不再只是 runtime availability 问题
- 它开始明确受 capability contract 限制

---

## 3. 本轮形成的系统边界变化

本轮最重要的变化，不是代码文件数量，而是执行边界的变化。

### 3.1 之前的边界

此前系统的 contract 更接近：

- asset 中有定义
- pass1 / read model 中可见
- 调试时可检查

但 control layer 并不会因为 contract 缺失而必然阻断执行。

### 3.2 现在的边界

本轮之后，最小 contract 集已经进入真实控制语义：

- 缺少 `checkpoint_resume_ack` 时，resume 不得继续
- 缺少 `validate_bindings` / `validate_args` 时，validation 不得继续
- 缺少 `submit_job` 时，不得提交 job

因此，contract 现在已从：

- “可见元数据”

推进为：

- “执行前约束”

### 3.3 这一步为什么重要

如果 contract 只停留在 asset 和读模型中，系统很容易再次退回：

- 表面上有能力面
- 实际上 control layer 仍靠硬编码和默认行为运行

本轮收口的意义就在于：

> 至少有一部分 control path 已经开始真正承认 contract 作为前置条件。

这比“展示 contract 名称”要更接近能力面真实落地。

---

## 4. 本轮的测试与验证

### 4.1 新增测试

新增了 contract guard 专项测试：

- [CapabilityContractGuardTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/CapabilityContractGuardTest.java)

该测试覆盖：

- 正常 contract 通过
- contract 缺失时失败
- contract 关键字段不匹配时失败

### 4.2 更新的控制路径测试

更新了以下测试：

- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)

这些测试验证了：

- 缺少 `submit_job` contract 时，不会进入 job submission
- 缺少 `checkpoint_resume_ack` 时，resume 不会继续推进
- contract 失败不会被误判为普通成功链或静默降级

### 4.3 实际验证

本轮已实际执行并通过：

```powershell
mvn -q "-Dtest=CapabilityContractGuardTest,AttachmentCatalogProjectorTest,TaskProjectionBuilderTest,TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,RepairDispatcherServiceTest,MinReadyEvaluatorTest" test
```

以及真实链路回归：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404170324337_0ad33901`

---

## 5. 本轮没有完成什么

这份记录必须明确说明：本轮并没有把“能力面 contract 化”全部做完。

当前仍未进入真实执行约束的 contract 至少包括：

- `query_job_status`
- `collect_result_bundle`

也就是说，目前 contract 的真实消费主要仍集中在：

- resume 前
- validation 前
- submit_job 前

而不是完整覆盖：

- 运行中状态查询
- 运行后结果归集
- artifact promotion
- 全链审计约束

因此，本轮最准确的表述是：

> 系统已完成“最小 contract 集进入执行前关键控制点”的收口，但尚未完成 contract 对运行后路径与结果归集路径的全面覆盖。

---

## 6. 与总体方向的关系

这轮工作与此前阶段的关系可以概括为：

- Week 1：建立最小多 case governed 主链
- Week 2：让 `water_yield` skill 资产开始掌权
- Week 3：让最小 catalog-first slice 成为主链事实源
- 本轮：让最小 capability contract 开始真实约束执行前控制点

因此，本轮不是独立漂浮的一步，而是继续收缩原始偏离：

- 从“能力定义存在，但控制层不依赖它”
- 推进到“控制层至少在部分关键路径开始依赖 contract”

---

## 7. 最终结论

本轮结论如下：

### 7.1 已完成

- 新增 `CapabilityContractGuard`
- contract 已进入 `resume / validation / submit_job` 三个真实控制点
- contract 缺失或关键字段不匹配时，会阻断继续推进
- backend 自动化测试与真实 success 回归均已通过

### 7.2 尚未完成

- `query_job_status / collect_result_bundle` 仍未形成真实消费闭环
- contract 还没有覆盖运行后治理与结果归集的完整阶段

### 7.3 最准确的阶段性表述

当前系统已不再只是“展示 capability contract”，而是已经开始在执行前关键控制点中消费最小 contract 集；但距离完整能力面 contract 驱动的全链治理，仍有后续工作需要完成。

