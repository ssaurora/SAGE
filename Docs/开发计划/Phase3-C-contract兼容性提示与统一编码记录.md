# Phase3-C-contract兼容性提示与统一编码记录
更新日期：2026-04-05

---

## 0. 文档目的

本文用于记录 `Phase3-C` 在 `contract-first` 方向上的又一轮收口：

1. 把 `detail / manifest / result / resume mismatch` 使用的 contract 漂移语义继续统一成同一套结构化 code
2. 在 identity mismatch 之上增加最小的 `compatibility_code / migration_hint`
3. 修正此前 `/resume` 路径将 fingerprint 漂移一律记成 `CONTRACT_VERSION_MISMATCH` 的问题

本轮不扩新 contract 类型，不扩前端，不把当前状态表述成“完整 contract migration 平台”。

---

## 1. 本轮目标

上一轮已经完成：

- `contract_consistency` 从布尔投影推进到结构化 `consistency_code / mismatch_code`
- `resume transaction` 对 contract mismatch 已有结构化字段
- 审计记录中已开始写入 `contract_identity`

但当时仍有两个明显缺口：

1. 结构化 code 只回答了“哪里不一致”，还没有回答“这种不一致在治理上意味着什么”
2. `/resume` 的 mismatch 仍把版本漂移和 fingerprint 漂移混成同一个 `failure_code`

因此本轮目标是继续做最小加厚，而不是扩平台宽度。

---

## 2. 本轮完成的实现

### 2.1 统一的 mismatch 判定 helper

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中，新增并统一使用了：

- `determineContractMismatchCode(...)`

这意味着以下判断不再散落在多个局部逻辑中，而是共用同一套来源：

- `CONTRACT_IDENTITY_UNAVAILABLE`
- `FROZEN_CONTRACT_MISSING`
- `CURRENT_CONTRACT_MISSING`
- `CONTRACT_VERSION_MISMATCH`
- `CONTRACT_FINGERPRINT_MISMATCH`

本轮之后：

- `buildFrozenContractConsistency(...)`
- `/resume` 的 contract identity 比较

已经使用同一套 mismatch 判定函数。

### 2.2 统一的 compatibility / migration 视图

在同一文件中，新增：

- `buildContractCompatibilityView(...)`

当前最小输出包括：

- `compatibility_code`
- `migration_hint`

已覆盖语义如下：

- `CONTRACT_MATCHED`
  - `compatibility_code = COMPATIBLE`
  - `migration_hint = NO_ACTION`
- `CONTRACT_VERSION_MISMATCH`
  - `compatibility_code = INCOMPATIBLE`
  - `migration_hint = REGENERATE_PASS1_AND_REFREEZE`
- `CONTRACT_FINGERPRINT_MISMATCH`
  - `compatibility_code = INCOMPATIBLE`
  - `migration_hint = REFREEZE_REQUIRED`
- `FROZEN_CONTRACT_MISSING`
  - `compatibility_code = IDENTITY_INCOMPLETE`
  - `migration_hint = REBUILD_FROZEN_CONTRACT_IDENTITY`
- `CURRENT_CONTRACT_MISSING`
  - `compatibility_code = IDENTITY_INCOMPLETE`
  - `migration_hint = RELOAD_CURRENT_CONTRACT_IDENTITY`
- `CONTRACT_IDENTITY_UNAVAILABLE`
  - `compatibility_code = IDENTITY_UNKNOWN`
  - `migration_hint = RECONSTRUCT_CONTRACT_IDENTITY`

这一步的意义是：

> 系统现在不仅能说“不一致”，还能给出最小的治理动作提示，而不是只返回一段失败文本。

### 2.3 `detail / manifest / result` 继续说同一种语言

`buildFrozenContractConsistency(...)` 现在除已有字段外，还统一输出：

- `compatibility_code`
- `migration_hint`

因此以下三个正式读模型已共享同一套 contract 一致性解释语言：

- `detail.contract_consistency`
- `manifest.contract_consistency`
- `result.contract_consistency`

这使不同页面或审计消费者不再需要自行解释“这个 mismatch 到底该怎么理解”。

### 2.4 `detail` 新增 resume 侧兼容性提示

`buildDetailContractConsistency(...)` 现在在已有：

- `resume_mismatch_code`
- `resume_detected_contract_drift`

之外，还新增：

- `resume_compatibility_code`
- `resume_migration_hint`

这使 detail 读模型能够直接表达：

- resume 上发生了哪类 contract drift
- 对应应采取的最小治理动作是什么

### 2.5 `/resume` 纠正 fingerprint mismatch 的结构化收口

本轮修正了一个实质性缺陷：

此前 `/resume` 检测到 frozen/current contract identity 不一致时，无论是 version 漂移还是 fingerprint 漂移，都会固定写成：

- `failure_code = CONTRACT_VERSION_MISMATCH`

本轮之后：

- mismatch code 由统一 helper 计算
- fingerprint 漂移会正式写成：
  - `CONTRACT_FINGERPRINT_MISMATCH`
- 返回的 `409` reason 也会随之区分，例如：
  - `Contract fingerprint mismatch`

这让治理对象与真实问题类型开始一致。

### 2.6 Resume mismatch 审计吸收 compatibility / migration 语义

在 `ensureResumeContractIdentityCompatible(...)` 中，写入治理审计时现在除已有字段外，还会写入：

- `mismatch_code`
- `compatibility_code`
- `migration_hint`

因此审计记录现在不只回答：

- frozen/current contract 各是什么

还开始回答：

- 这类漂移在治理上属于什么兼容性等级
- 最小建议动作是什么

---

## 3. 测试与验证

### 3.1 自动化测试

本轮新增或更新了以下重点覆盖：

- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
  - version mismatch 审计新增 `compatibility_code / migration_hint`
  - 新增 fingerprint mismatch 测试，确认 failure code 与 `409` reason 正确区分
  - matched manifest/result consistency 新增 `COMPATIBLE / NO_ACTION` 断言
  - detail resume mismatch 新增 `resume_compatibility_code / resume_migration_hint`
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
  - `FROZEN_CONTRACT_MISSING` 新增 `IDENTITY_INCOMPLETE / REBUILD_FROZEN_CONTRACT_IDENTITY` 断言

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

- `task_20260404195941589_5b11bfe0`

这说明本轮新增的兼容性提示层没有破坏现有真实主链。

---

## 4. 本轮完成后系统新增的能力

到本轮结束，系统在 contract 方向上新增了两类更强的语义：

### 4.1 一致性结果不再只是“相等 / 不相等”

系统现在能够同时给出：

- mismatch 的结构化原因
- compatibility 的结构化级别
- 最小 migration / remediation hint

### 4.2 Resume drift 的问题类型开始被正确区分

系统现在能明确区分：

- version 漂移
- fingerprint 漂移
- frozen identity 缺失
- current identity 缺失

而不是把所有情况都压缩成单一的 “Contract version mismatch”。

---

## 5. 本轮没有完成的部分

本轮仍未完成以下事项：

1. `compatibility_code / migration_hint` 目前仍是最小集合，尚未形成更细粒度的 compatibility policy
2. 审计中虽然已带这些字段，但还没有抽成跨对象统一的 contract governance view
3. `detail / manifest / result / audit` 之间虽已共享同一套 helper，但还没有独立的公共 DTO/contract 定义
4. 还没有做真正的 migration execution，只是给出结构化 hint

因此，本轮应被定义为：

- **contract 兼容性提示与统一编码成立**

而不是：

- **完整 contract compatibility / migration 平台成立**

---

## 6. 阶段结论

本轮之后，更准确的表述应为：

> `contract-first` 已从“可冻结、可比较、可审计”进一步推进到“可结构化解释兼容性与最小迁移动作”的阶段；系统已开始用统一 code 解释 detail / manifest / result / resume drift，而不再依赖各自局部的文本语义。

---

## 7. 下一步建议

下一步最合理的方向仍然不是扩前端，而是继续把这些语义收成更稳定的治理对象：

1. 把 `detail / manifest / result / audit` 中的 contract drift 继续统一成更正式的公共 view / code
2. 若继续深化，再考虑把 `compatibility_code / migration_hint` 纳入更正式的审计读模型
3. 在后续阶段，如确有必要，再把 hint 推进到受控 migration policy，而不是仅停留在说明层

一句话概括：

> 本轮解决的是“系统不仅知道 contract 漂移了，还能开始统一地说明这种漂移意味着什么”；下一轮要解决的是“这些说明如何进一步变成更稳定的治理对象，而不是散在多个 map 字段里”。
