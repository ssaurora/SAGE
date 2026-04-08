# Phase3-C-contract治理视图DTO化记录
更新日期：2026-04-05

---

## 0. 文档目的

本文用于记录 `Phase3-C` 在 `contract-first` 方向上的进一步收口：

- 将 `contract_governance` 从 `Map<String, Object>` 读模型推进为独立 DTO
- 固定 `detail / manifest / result` 三类视图上的稳定字段边界

本轮不新增新的 contract 语义，不改变主链控制逻辑，只把已存在的治理语义收成更稳定的 API 契约。

---

## 1. 本轮目标

上一轮已经完成：

- `contract_governance` 作为统一治理视图进入 `detail / manifest / result`
- 该视图已承载：
  - frozen/current contract summary
  - consistency
  - resume contract evaluation

但当时仍有一个明确不足：

> `contract_governance` 仍然是自由 `Map<String, Object>`，缺少稳定的字段边界。

这会带来两个风险：

1. 后续演进时容易继续在 map 上临时加字段
2. 前后端、测试和审计层缺少稳定的 contract governance API 契约

因此本轮目标是把该视图从自由 map 收为共享 DTO。

---

## 2. 本轮完成的实现

### 2.1 新增共享 DTO

新增：

- [ContractGovernanceView.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/ContractGovernanceView.java)

当前该 DTO 包含三层结构：

1. `ContractGovernanceView`
   - `scope`
   - `frozen_contract_summary`
   - `current_contract_summary`
   - `consistency`
   - `resume_contract_evaluation`
2. `ContractIdentityView`
   - `contract_version`
   - `contract_fingerprint`
   - `contract_count`
   - `contract_names`
   - `contract_present`
3. `ContractConsistencyView`
   - 承载当前统一的 `contract_consistency` 字段集合
4. `ResumeContractEvaluationView`
   - 承载 resume 侧的 contract drift 字段集合

### 2.2 三类 response 已切换到稳定类型

以下 DTO 已将 `contract_governance` 从 `Map<String, Object>` 切换为 `ContractGovernanceView`：

- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)

这意味着：

- `contract_governance` 已不再是自由结构
- 其字段集合开始成为更稳定的后端 API 契约

### 2.3 `TaskService` 现在正式组装 DTO

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中：

- `buildContractGovernanceView(...)` 现返回 `ContractGovernanceView`
- 新增：
  - `toContractIdentityView(...)`
  - `toContractConsistencyView(...)`

当前策略是：

- 保留既有 `contract_consistency` 顶层字段，保证旧消费者和现有测试语义不变
- 由新的 DTO 视图复用这些已存在的治理语义，而不是重新定义第二套逻辑

这一步保持了风险最小：

- 控制语义不变
- 读模型更稳定
- 后续如需继续深化，可在 DTO 内演化，而不是继续扩 map

---

## 3. 测试与验证

### 3.1 自动化测试

本轮继续通过：

```powershell
mvn -q "-Dtest=TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,TaskProjectionBuilderTest,CapabilityContractGuardTest" test
```

已覆盖：

- `detail.contract_governance.scope`
- `manifest.contract_governance.scope`
- `result.contract_governance.scope`
- `contract_governance.consistency.compatibility_code`
- `contract_governance.resume_contract_evaluation.mismatch_code`

相关测试文件：

- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

### 3.2 真实链路状态

本轮代码没有改变主链流程语义，因此真实链路仍以前一轮最近的成功回归为有效基线：

- `task_20260404201758378_0e06e704`

如果下一轮继续改动主链路径，再做新的真实回归即可。

---

## 4. 本轮完成后的系统状态

到本轮结束，contract 相关治理语义已经分成两层：

### 4.1 兼容旧消费者的顶层字段

- `contract_consistency`

### 4.2 更稳定的正式治理视图

- `contract_governance`

这意味着当前系统开始同时具备：

- 兼容性
- 稳定 API 边界
- 后续演化空间

---

## 5. 本轮没有完成的部分

本轮仍未完成：

1. `contract_consistency` 顶层字段尚未完全退役，当前仍与 `contract_governance.consistency` 并存
2. audit 侧仍未切换到共享 DTO，而是继续使用 detail JSON 内嵌结构
3. 尚未形成单独的 contract governance API 文档
4. `compatibility_code / migration_hint` 仍然是最小集合，不是完整 policy

因此本轮应被定义为：

- **contract 治理视图 DTO 化成立**

而不是：

- **完整 contract governance schema 平台成立**

---

## 6. 阶段结论

本轮之后，更准确的表述应为：

> `contract-first` 已从“有治理视图”推进到“治理视图具备稳定 DTO 边界”的阶段；系统开始用共享 DTO 承载 contract governance，而不是继续依赖自由 map 扩展。

---

## 7. 下一步建议

下一步最合理的方向有两条：

1. 继续深化 contract 方向
   - 逐步让 `contract_governance` 取代顶层 `contract_consistency`
   - 把 audit 侧也推进到共享 DTO / 共享视图
2. 回到更高一层做阶段总结
   - 将 `catalog persistence + contract freeze + mismatch governance + governance view DTO` 汇总成新的 `Phase3-C` 阶段结论

如果继续以能力侧为优先，建议先做第 1 条。 
