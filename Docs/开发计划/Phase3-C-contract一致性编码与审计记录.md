# Phase3-C-contract一致性编码与审计记录
更新日期：2026-04-05

---

## 0. 文档目的

本文用于记录 `Phase3-C` 在 `contract-first` 方向上的又一轮收口：

1. 把 `detail / manifest / result` 中的 `contract_consistency` 从布尔比较推进到结构化 code
2. 把 `contract_identity` 正式推进到审计记录，而不是只停留在读模型投影

本轮不扩新 capability，不扩前端，不把当前状态表述为“完整 contract 平台已落地”。

---

## 1. 本轮目标

上一轮已经完成了三件基础工作：

- `contract_version / contract_fingerprint` 进入了 skill asset、`pass1`、frozen manifest 与读模型
- `/resume` 已能显式检测 frozen/current contract identity 漂移
- mismatch 已进入结构化 `resume transaction`

但当时仍有两个缺口：

1. `contract_consistency` 仍主要停留在 `matches_current_contract` 这类布尔层级，表达力不足
2. contract identity 还没有进入审计对象，治理侧无法直接追溯“当时按哪一版 contract 放行/拒绝”

因此本轮目标是把 contract 语义继续加厚，但仍保持在最小必要范围内。

---

## 2. 本轮完成的实现

### 2.1 `contract_consistency` 增加结构化 code

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中，`buildFrozenContractConsistency(...)` 现已输出：

- `consistency_code`
- `mismatch_code`

当前已覆盖的 code 包括：

- `CONTRACT_MATCHED`
- `CONTRACT_VERSION_MISMATCH`
- `CONTRACT_FINGERPRINT_MISMATCH`
- `FROZEN_CONTRACT_MISSING`
- `CURRENT_CONTRACT_MISSING`
- `CONTRACT_IDENTITY_UNAVAILABLE`

当前语义为：

- 若 frozen/current identity 匹配：
  - `consistency_code = CONTRACT_MATCHED`
  - `mismatch_code = null`
- 若不匹配：
  - `consistency_code = <具体不一致原因>`
  - `mismatch_code = <同一具体 code>`

这意味着后续读模型、审计或前端不再需要依赖自由文本去判断 contract 漂移类型。

### 2.2 `detail` 增加 resume 漂移投影

在同一文件中，`buildDetailContractConsistency(...)` 进一步吸收了 `/resume` 交易中的结构化 mismatch 信息。

当前 `detail.contract_consistency` 已可额外提供：

- `resume_mismatch_code`
- `resume_detected_contract_drift`

这使 `detail` 读模型能够直接回答：

- 是否发生过 resume contract drift
- drift 属于哪一种结构化类型

而不再需要解析 `failure_reason` 文本。

### 2.3 审计记录正式携带 `contract_identity`

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中：

- `appendAuditWithContract(...)` 现会把当前 contract identity 注入审计 detail
- 新增内部 helper：`enrichAuditDetailWithContract(...)`

当前审计 detail 中新增的结构化字段为：

- `contract_identity.contract_version`
- `contract_identity.contract_fingerprint`
- `contract_identity.contract_count`
- `contract_identity.contract_names`
- `contract_identity.contract_present`

这一步的意义是：

> contract 已不只是运行时比较对象，也开始进入治理证据。

### 2.4 Resume contract mismatch 会写入治理审计

在 `ensureResumeContractIdentityCompatible(...)` 中，当检测到 frozen/current contract identity 不一致时，系统现在除了：

- 构造结构化 `resume transaction`
- 写入 `STATE_CORRUPTED`
- 返回 `409 CONFLICT`

之外，还会追加一条治理审计记录：

- `action_type = TASK_RESUME`
- `action_result = REJECTED`

其 detail 中显式包含：

- `failure_code`
- `failure_reason`
- `frozen_contract_version`
- `frozen_contract_fingerprint`
- `current_contract_version`
- `current_contract_fingerprint`

这使 contract drift 不再只是控制层内部判断，而是正式进入治理审计轨迹。

---

## 3. 测试与验证

### 3.1 自动化测试

本轮新增或更新的重点测试包括：

- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)
- [CapabilityContractGuardTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/CapabilityContractGuardTest.java)

覆盖点包括：

- `contract_consistency` 的结构化 code 输出
- `detail` 对 resume mismatch 的结构化投影
- resume contract mismatch 的治理审计记录
- cancel 成功路径中的 audit `contract_identity`

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

- `task_20260404193801559_7c66afce`

这说明本轮 `contract consistency + audit` 的加厚没有破坏当前真实主链。

---

## 4. 当前系统形成的更强治理语义

到本轮结束，系统已不只具备“contract identity 可冻结、可比较”，还进一步具备：

### 4.1 结构化一致性表达

系统现在能正式回答：

- frozen/current contract 是否一致
- 若不一致，属于哪一类不一致
- 该不一致是否发生在 resume 重评估路径上

### 4.2 结构化治理证据

系统现在能正式追溯：

- 一次 cancel / resume / 关键治理动作发生时，当时引用的是哪一版 contract
- 某次 `/resume` 被拒绝时，冻结 contract 与当前 contract 的 identity 差异是什么

这比“只有日志”和“只有 failure_reason 文本”更接近真正的治理边界。

---

## 5. 本轮没有完成的部分

本轮仍未完成以下事项：

1. 审计对象中尚未统一形成更高层级的 `contract_consistency_code` 视图，目前仍以 detail/result/manifest 投影和局部 audit detail 为主
2. 还没有把 manifest/result/detail 的 contract drift 全部统一成一套跨对象的公共错误语义枚举
3. 还没有形成完整的 contract compatibility / migration 语义，当前仍以 identity 相等/不相等为主
4. 还没有把 contract identity 与 audit record 做独立持久化建模，当前仍属于 audit detail 内嵌结构

因此，本轮应被定义为：

- **contract 一致性编码与治理审计成立**

而不是：

- **完整 contract 生命周期与迁移平台成立**

---

## 6. 阶段结论

本轮之后，更准确的表述应为：

> `contract-first` 已从“contract 可见、可冻结、可比较”，推进到“contract consistency 可结构化编码，且关键治理动作可携带 contract identity 进入审计记录”的阶段。

这意味着当前系统已经具备：

- 可冻结的 contract identity
- 可检测的 contract drift
- 可投影的结构化 mismatch code
- 可追溯的 contract governance evidence

但距离完整的 contract version lifecycle、compatibility policy 与 migration policy，仍然有明显距离。

---

## 7. 下一步建议

下一步最合理的方向不是扩前端，也不是扩新 skill，而是继续把当前 contract 语义加厚到更统一的治理对象：

1. 把 `detail / manifest / result / audit` 的 contract drift 语义继续统一成稳定的公共 code/view
2. 继续补 contract compatibility / migration hint，而不只停留在 identity mismatch
3. 若后续需要更强审计能力，再考虑将 contract identity 从 audit detail 内嵌结构推进到更正式的持久化对象

一句话概括：

> 本轮解决的是“系统已经能结构化地知道 contract 不一致，并把它记进治理证据”；下一轮要解决的是“系统还能更统一地解释这种不一致影响了哪些对象、哪些阶段，以及后续是否存在受控迁移语义”。
