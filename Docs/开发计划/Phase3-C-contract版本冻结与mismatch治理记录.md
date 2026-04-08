# Phase3-C-contract版本冻结与mismatch治理记录
更新日期：2026-04-05

## 0. 文档目的

本文用于记录 Phase3-C 在 `contract-first` 方向上的一轮关键收口：

> 将 contract 从“已有版本字段与最小消费”推进到“具备冻结语义、resume 漂移检测，以及结构化 mismatch 治理对象”的状态。

本文不把当前状态表述为“完整能力面版本平台已落地”，而是明确限定为：

- `contract_version / contract_fingerprint` 已进入 asset、pass1、manifest/detail 投影与 guard 前置条件
- resume 在重新运行 `pass1` 时，已能显式检测 frozen contract identity 与 current contract identity 的不一致
- mismatch 已不再只依赖字符串日志，而是进入结构化 `resume transaction` 视图

---

## 1. 本轮工作的背景

在上一轮收口后，系统已经完成：

- 最小 contract 集进入执行前、运行中、运行后和治理侧关键路径
- `CapabilityContractGuard` 已成为真实控制语义的一部分
- `contract_version / contract_fingerprint` 已进入：
  - skill asset
  - service `pass1`
  - backend manifest/detail 投影

但当时仍存在一个明显缺口：

> contract drift 虽然已经可以被代码检测，但治理侧仍主要通过 `failure_reason` 字符串表达，不够稳定，也不利于后续读模型、前端或审计做结构化消费。

因此，本轮的目标不是继续扩 contract 种类，而是把已经存在的 contract identity 语义再收紧一层：

- 明确“冻结的 contract identity 是什么”
- 明确“当前重新计算得到的 contract identity 是什么”
- 明确“二者不一致时，系统如何用结构化对象收口”

---

## 2. 本轮完成的核心实现

### 2.1 Resume transaction 增加结构化 mismatch 字段

本轮扩展了 resume transaction 的结构化字段：

- [ResumeTransactionView.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/ResumeTransactionView.java)

新增字段包括：

- `failure_code`
- `base_contract_version`
- `base_contract_fingerprint`
- `candidate_contract_version`
- `candidate_contract_fingerprint`

这意味着：

- `failure_reason` 仍可保留给人读
- 但 contract mismatch 的核心语义不再只靠文本描述

### 2.2 TaskProjectionBuilder 已能正式投影这些字段

本轮同步扩展了：

- [TaskProjectionBuilder.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskProjectionBuilder.java)

`buildResumeTransaction(...)` 现在会正式读取并投影：

- `failure_code`
- `base_contract_version`
- `base_contract_fingerprint`
- `candidate_contract_version`
- `candidate_contract_fingerprint`

这一步的意义是：

> mismatch 现在已经进入正式读模型，而不是只存在于控制层内部异常文本。

### 2.3 TaskService 在 mismatch 分支中写入结构化 contract identity

本轮继续收口了：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

在 `ensureResumeContractIdentityCompatible(...)` 中，若检测到：

- frozen pass1 的 `contract_version / contract_fingerprint`
- current pass1 的 `contract_version / contract_fingerprint`

不一致，则系统会：

1. 构造结构化 `resume transaction`
2. 写入：
   - `failure_code = CONTRACT_VERSION_MISMATCH`
   - frozen contract identity
   - candidate/current contract identity
3. `markCorrupted(...)`
4. append `STATE_CHANGED -> STATE_CORRUPTED`
5. 以 `409 CONFLICT` 收口

当前对外错误原因仍为：

- `Contract version mismatch`

但内部治理对象已同时具备结构化字段，不再只剩字符串 reason。

---

## 3. 当前 contract mismatch 的治理语义

截至本轮，系统对这类漂移的最准确语义已经变为：

### 3.1 正常情况

若 frozen contract identity 与 current contract identity 一致：

- `/resume` 可继续进入 validation / pass2 / submit 主链

### 3.2 漂移情况

若 frozen 与 current 不一致：

- 不再进入 validation
- 不再进入 job submission
- 直接进入 `STATE_CORRUPTED`
- resume API 返回 `409 CONFLICT`
- `resume transaction` 中记录：
  - `failure_code`
  - `base_contract_version / fingerprint`
  - `candidate_contract_version / fingerprint`

因此，当前 contract drift 已经具备：

- 显式控制语义
- 显式错误状态
- 显式结构化证据

---

## 4. 与上一轮相比，本轮真正新增了什么

本轮最重要的变化不是“又多了几个字段”，而是治理对象的表达质量提升。

### 4.1 之前的状态

之前虽然已经能发现 mismatch，但更接近：

- 控制层知道有问题
- 通过 `failure_reason` 记录一条文本
- 读模型与后续消费仍需理解字符串

### 4.2 现在的状态

现在 resume mismatch 已经是一个更正式的结构化对象：

- 有稳定的 `failure_code`
- 有 frozen identity
- 有 current identity
- 有控制层显式收口语义

这意味着后续如果继续推进：

- 审计层
- 前端调试模式
- 更高层的一致性检查

都不需要再去解析自由文本，能够直接消费结构化 contract mismatch 事实。

---

## 5. 测试与验证

### 5.1 新增与更新的测试

本轮主要更新了：

- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)

重点覆盖：

#### A. Resume transaction 结构化投影

- `failure_code`
- `base_contract_version / fingerprint`
- `candidate_contract_version / fingerprint`

#### B. Resume mismatch 控制语义

- mismatch 返回 `409`
- 不进入 validation
- 不进入 execution
- 写入 `STATE_CORRUPTED`
- 写入结构化 corrupted transaction

### 5.2 实际验证

本轮已实际执行并通过：

```powershell
pytest Service/planning-pass1/tests/test_pass1_api.py -q
```

```powershell
mvn -q "-Dtest=TaskProjectionBuilderTest,TaskServiceGovernanceTest,CapabilityContractGuardTest,TaskServiceCognitionFlowTest" test
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404185645282_805499e1`

---

## 6. 本轮仍未完成什么

这份记录必须明确说明：本轮仍不是“完整 contract 版本治理平台”。

当前仍未完成的部分包括：

- manifest / result / detail 还没有统一投影 frozen contract identity 与 current contract identity 的一致性结论
- mismatch handling 主要覆盖的是“resume 重新运行 pass1”的分支
- clarify-resume 仍主要依赖 frozen pass1 复用，而不是做 current-latest 比较
- 更细粒度的 contract compatibility hint 还没有形成正式语义

因此，本轮完成的是：

- resume mismatch 的结构化治理收口

而不是：

- 全系统 contract identity 一致性平台

---

## 7. 阶段结论

本轮之后，Phase3-C 在 contract 方向上可以更准确地表述为：

> contract 已不只是被真实消费，而且开始具备冻结后漂移检测与结构化 mismatch 治理语义；resume 在重新计算能力约束时，能够显式识别 frozen/current contract identity 不一致，并以结构化事务对象和 `409 CONFLICT` 收口。

这一步的价值不在于“字段更全”，而在于：

- contract drift 不再是隐性风险
- mismatch 不再只是日志语义
- 治理链开始具备可继续加厚的结构化 contract identity 边界

---

## 8. 下一步建议

当前最合理的下一步不是扩新 capability，而是继续把 contract identity 从 resume 侧推进到更完整的一致性投影：

1. 把 frozen/current contract identity 的一致性信息推进到 detail / manifest / result 读模型
2. 明确哪些路径必须只读 frozen contract，哪些路径允许 current re-evaluation
3. 为 mismatch 增加更明确的 compatibility / migration 语义，而不只是“相等或不相等”

一句话概括：

> 本轮解决的是“系统已能结构化地知道 contract 漂移了”；下一轮要解决的是“系统还能结构化地告诉我们，这种漂移影响了哪些对象、哪些阶段以及哪些决策”。
