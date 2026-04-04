# Phase3-B-contract全链最小消费记录
更新日期：2026-04-05

## 0. 文档目的

本文用于记录 Phase3-B 在 capability / contract 方向上的又一次收口：

> 将最小 contract 集从“执行前关键控制点已消费”，进一步推进到“覆盖执行前与运行后关键路径的最小全链消费”。

这份记录不把当前状态表述为“完整能力面已经落地”，而是明确限定为：

- 最小 contract 集已进入真实控制语义
- contract 已覆盖 resume、validation、submit job、job status polling、result bundle promotion
- control layer 不再仅把 contract 当作展示字段或调试信息

---

## 1. 本轮工作的背景

在上一轮收口后，系统已经完成：

- `checkpoint_resume_ack` 进入 resume 前控制点
- `validate_bindings / validate_args` 进入 validation 前控制点
- `submit_job` 进入 job submission 前控制点

但当时仍然存在一个明显缺口：

> 运行后路径中的 `query_job_status` 与 `collect_result_bundle` 仍然只是 asset/read model 中的 contract，可见但未被真实消费。

这意味着系统当时虽然已经开始承认 contract 的执行前约束，但还没有把这种约束延伸到：

- runtime 轮询
- result bundle promotion
- artifact promotion 前的控制边界

本轮工作的目标，就是补齐这两个运行后 contract 的真实消费。

---

## 2. 本轮完成的核心实现

### 2.1 CapabilityContractGuard 扩展到运行后 contract

本轮扩展了 contract guard：

- [CapabilityContractGuard.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CapabilityContractGuard.java)

新增了两个 guard：

- `requireQueryJobStatusContract(...)`
- `requireCollectResultBundleContract(...)`

其校验逻辑延续此前的最小必要字段约束：

- `caller_scope`
- `side_effect_level`
- `input_schema`
- `output_schema`

当前要求为：

- `query_job_status`
  - `caller_scope = control_or_presentation`
  - `side_effect_level = read_only`
- `collect_result_bundle`
  - `caller_scope = control_only`
  - `side_effect_level = artifact_collection`

### 2.2 `query_job_status` 已进入真实轮询路径

本轮在：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

中，将 `query_job_status` 接入了 `syncSingleJob(...)`。

当前行为是：

1. 先读取当前任务的 `pass1_result_json`
2. 校验 `query_job_status` contract 是否存在且语义匹配
3. 只有校验通过，才允许调用：

```java
jobRuntimeClient.getJob(jobRecord.getJobId())
```

若 contract 缺失或不匹配：

- 不再轮询 runtime
- 不再继续同步 job status
- 任务直接以 `STATE_CORRUPTED` 收口

这意味着：

- 运行中轮询路径现在也受 capability contract 约束
- contract 缺失不再只是“无影响的元数据问题”

### 2.3 `collect_result_bundle` 已进入 artifact promotion 路径

本轮同样在：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

中，将 `collect_result_bundle` 接入了 `processSuccess(...)`。

当前行为是：

1. 任务进入 `ARTIFACT_PROMOTING`
2. 在真正执行 `persistSuccess(...)` 前，先校验 `collect_result_bundle`
3. 只有 contract 通过，才允许继续：

- persist success outputs
- persist artifact catalog
- persist result bundle / final explanation side effects

若 contract 缺失或不匹配：

- 不再继续 result bundle promotion
- 不再继续 artifact promotion success path
- 任务通过现有异常封装进入：
  - `ARTIFACT_PROMOTION_FAILED`
  - `STATE_CORRUPTED`

这意味着：

- contract 已从“提交前约束”
- 推进到“运行后结果归集约束”

### 2.4 `checkpoint_resume_ack` 在 resume 主链中补成真实消费

本轮还补了一个此前的漏洞：

虽然前一轮文档口径已经将 `checkpoint_resume_ack` 视为 resume contract，但代码里它此前没有稳定落在 `runResumePipeline(...)` 的真正控制点。

本轮已在：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

中明确补上：

- `runResumePipeline(...)` 在进入 validation 前，强制校验 `checkpoint_resume_ack`

这一步的意义是：

> 把此前“文档已声明、代码未完全兑现”的 resume contract，补成真实控制语义。

---

## 3. 当前最小 contract 集已覆盖的关键路径

截至本轮，最小 contract 集的真实消费覆盖如下：

### 3.1 执行前路径

- `checkpoint_resume_ack`
  - `/resume` 主链
- `validate_bindings`
  - validation 前
- `validate_args`
  - validation 前
- `submit_job`
  - job submission 前

### 3.2 运行后路径

- `query_job_status`
  - runtime polling 前
- `collect_result_bundle`
  - result bundle / artifact promotion 前

因此，本轮之后最准确的表述是：

> 最小 contract 集已经覆盖了执行前与运行后关键控制路径，而不再只停留在 asset/read model 可见层。

---

## 4. 本轮形成的治理语义变化

这轮最重要的变化，不是“多了两个 guard 方法”，而是治理语义的变化。

### 4.1 之前的状态

此前系统已经可以说：

- contract 对 resume / validation / submit_job 有执行前约束

但还不能说：

- contract 已参与运行后治理

### 4.2 现在的状态

本轮之后，系统已经可以说：

- 如果缺失 `query_job_status`，control layer 不会继续轮询 runtime
- 如果缺失 `collect_result_bundle`，control layer 不会继续 promotion success path

也就是说，contract 不再只是：

- 能否开始执行

而是已经开始影响：

- 执行后如何进入结果归集与 artifact promotion

### 4.3 为什么这一步重要

如果运行后路径不消费 contract，系统会形成一种危险的“半 contract 化”状态：

- 提交前看起来受 contract 约束
- 运行后仍然主要靠旧流程默认继续

这会导致能力面仍然只是半迁移状态。

本轮工作的意义就在于：

> contract 的控制语义已经延伸到运行后关键路径，能力面不再只影响“起跑”，也开始影响“收口”。

---

## 5. 测试与验证

### 5.1 新增与更新的测试

本轮更新了以下测试：

- [CapabilityContractGuardTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/CapabilityContractGuardTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

重点覆盖了两类新增断言：

#### A. `query_job_status` 缺失时

- 不再调用 runtime polling
- 直接进入 `STATE_CORRUPTED`

#### B. `collect_result_bundle` 缺失时

- 不再继续 `persistSuccess(...)`
- 通过现有 `ARTIFACT_PROMOTION_FAILED -> STATE_CORRUPTED` 路径收口

### 5.2 实际验证

本轮已实际执行并通过：

```powershell
mvn -q "-Dtest=CapabilityContractGuardTest,AttachmentCatalogProjectorTest,TaskProjectionBuilderTest,TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,RepairDispatcherServiceTest,MinReadyEvaluatorTest" test
```

以及真实链路回归：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404171350906_66786383`

---

## 6. 本轮仍未完成什么

这份记录必须明确说明：本轮仍不是“完整能力面 contract 驱动”。

当前仍未完成的方面包括：

- contract 对 cancel / artifact indexing / audit record 的更细粒度消费
- contract 与 provider negotiation / capability registry 的更正式联动
- contract 与多 capability / 多 skill 版本并存下的更复杂约束

因此，本轮完成的是：

- 最小 contract 集的全链关键路径消费

而不是：

- 完整能力面 contract 驱动平台

---

## 7. 最终结论

### 7.1 已完成

- `query_job_status` 进入 runtime polling 控制点
- `collect_result_bundle` 进入 result/artifact promotion 控制点
- `checkpoint_resume_ack` 在 resume 主链中补成真实消费
- 最小 contract 集现已覆盖执行前与运行后关键路径

### 7.2 最准确的阶段性表述

当前系统已经完成从“contract 可见”到“最小 contract 集在执行前与运行后关键路径中被真实消费”的推进；但距离完整能力面 contract 驱动的全链治理与更广义 capability abstraction，仍然存在后续工作。

