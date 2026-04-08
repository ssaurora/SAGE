# Phase3-C-contract治理视图记录
更新日期：2026-04-05

---

## 0. 文档目的

本文用于记录 `Phase3-C` 在 `contract-first` 方向上的下一步收口：

- 不再继续给 `detail / manifest / result` 顶层堆散字段
- 把现有的 contract identity、consistency、compatibility 与 resume drift 语义收成统一的 `contract_governance` 读模型

本轮目标不是扩新 contract，也不是扩前端，而是把已有治理语义收成更稳定的正式对象。

---

## 1. 本轮目标

上一轮已经完成：

- `contract_consistency` 的统一结构化 code
- `compatibility_code / migration_hint`
- `/resume` 对 `CONTRACT_VERSION_MISMATCH` 与 `CONTRACT_FINGERPRINT_MISMATCH` 的区分
- mismatch 审计记录中写入 `compatibility_code / migration_hint`

但当前仍有一个明显问题：

> 这些语义虽然已经存在，但仍散落在 `contract_consistency`、`resume transaction` 和局部审计 detail 中，缺少一个更稳定的治理视图承载点。

因此，本轮目标是把现有语义收束为统一的 `contract_governance` 读模型。

---

## 2. 本轮完成的实现

### 2.1 `detail / manifest / result` 新增 `contract_governance`

以下 DTO 现已新增：

- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)

新增字段：

- `contract_governance`

这意味着 contract 治理信息现在有了独立落点，而不是继续依赖调用方自行拼装 `contract_summary + contract_consistency + resume transaction`。

### 2.2 `TaskService` 增加统一治理视图组装

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中，新增：

- `buildContractGovernanceView(...)`

当前该治理视图统一承载：

- `scope`
- `frozen_contract_summary`
- `current_contract_summary`
- `consistency`
- `resume_contract_evaluation`

其中：

- `consistency` 直接复用当前统一的 `contract_consistency`
- `resume_contract_evaluation` 在存在 `resume transaction` 时承载：
  - `failure_code`
  - `base_contract_version`
  - `base_contract_fingerprint`
  - `candidate_contract_version`
  - `candidate_contract_fingerprint`
  - `mismatch_code`
  - `compatibility_code`
  - `migration_hint`
  - `drift_detected`

### 2.3 三类读模型共享同一组 contract 语言

本轮之后，以下对象已经可以通过 `contract_governance` 统一表达当前治理语义：

- `detail`
  - `scope = task_contract_governance`
- `manifest`
  - `scope = manifest_contract_governance`
- `result`
  - `scope = result_contract_governance`

这一步的意义是：

> contract 相关治理语义不再只是若干零散 map 字段，而开始形成更稳定的公共视图。

---

## 3. 测试与验证

### 3.1 自动化测试

本轮更新了：

- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

新增覆盖包括：

- `detail.contract_governance.scope`
- `manifest.contract_governance.scope`
- `result.contract_governance.scope`
- `contract_governance.consistency` 中的 `compatibility_code`
- `detail.contract_governance.resume_contract_evaluation` 中的 `mismatch_code`

实际通过命令：

```powershell
mvn -q "-Dtest=TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,TaskProjectionBuilderTest,CapabilityContractGuardTest" test
```

### 3.2 真实链路回归

实际通过：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404201758378_0e06e704`

这说明本轮 `contract_governance` 只是在治理层加厚读模型，没有破坏当前真实主链。

---

## 4. 本轮完成后的系统状态

到本轮结束，系统在 contract 方向上已经形成四层语义：

1. **identity**
   - `contract_version / contract_fingerprint`
2. **consistency**
   - `mismatch_code / consistency_code`
3. **compatibility**
   - `compatibility_code / migration_hint`
4. **governance view**
   - `contract_governance`

这意味着 contract 相关治理语义开始从“若干字段集合”推进到“稳定的正式视图”。

---

## 5. 本轮没有完成的部分

本轮仍未完成以下事项：

1. `contract_governance` 目前仍是 `Map<String, Object>` 视图，尚未抽成独立 DTO
2. audit 记录仍以 detail JSON 内嵌为主，尚未形成独立审计读模型
3. `compatibility_code / migration_hint` 仍是最小集合，尚未形成更完整的 policy
4. 还没有把这套治理视图推进到更独立的 API contract 文档

因此，本轮应被定义为：

- **contract 治理视图成立**

而不是：

- **完整 contract governance model 平台成立**

---

## 6. 阶段结论

本轮之后，更准确的表述应为：

> `contract-first` 已从“可冻结、可比较、可审计、可解释兼容性”，进一步推进到“具备统一治理视图”的阶段；系统开始用稳定的 `contract_governance` 读模型，而不是继续在多个读模型顶层堆散字段。

---

## 7. 下一步建议

下一步最合理的方向仍然是继续加厚治理对象，而不是扩前端：

1. 若继续深化，可把 `contract_governance` 从 `Map` 进一步收成独立 DTO
2. 把 audit 侧的 contract 治理语义继续收成更正式的审计读模型
3. 若需要更高规范性，再补 API contract 文档，固定 `contract_governance` 的稳定字段集合

一句话概括：

> 本轮解决的是“contract 治理语义终于有了正式视图承载点”；下一轮要解决的是“这个视图如何进一步脱离自由 map，成为更稳定的系统契约”。 
