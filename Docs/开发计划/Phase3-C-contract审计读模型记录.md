# Phase3-C contract 审计读模型记录
更新日期：2026-04-05

## 0. 文档目的

本文记录 Phase3-C 在 `contract-first` 方向上的一轮继续深化：

- 不再只在 `detail / manifest / result` 中投影 contract 治理语义
- 将 audit 侧也纳入正式读模型
- 让 contract drift / mismatch 的治理证据能够通过 `/tasks/{taskId}/audit` 被结构化读取

本轮目标不是扩前端，也不是引入新的 workflow authority，而是把已有的治理语义延伸到审计读取边界。

---

## 1. 本轮结论

本轮已经完成两件事：

1. backend 新增了正式的 task-audit 读取接口：
   - `GET /tasks/{taskId}/audit`

2. audit record 中已有的 contract mismatch 细节，现在会被投影成共享的 `ContractGovernanceView`，而不再只能依赖原始 `detail_json` 或文本字段理解。

这意味着：

> contract 相关治理语义现在已经覆盖 `detail / manifest / result / audit` 四类正式读取视图，不再只停留在任务详情和恢复事务内部。

---

## 2. 本轮实现内容

### 2.1 新增 task audit 读取接口

backend 新增 task 审计读取路径：

- [TaskController.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskController.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)
- [TaskAuditResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskAuditResponse.java)

新增接口：

- `GET /tasks/{taskId}/audit`

返回内容包括：

- task 级 audit items 列表
- 每条 audit item 的：
  - `action_type`
  - `action_result`
  - `trace_id`
  - `created_at`
  - 原始 `detail`
  - 结构化 `contract_governance`

### 2.2 AuditService / AuditRecordMapper 支持按 task 读取

为支撑上述接口，本轮补充了审计读取能力：

- [AuditService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/audit/AuditService.java)
- [AuditRecordMapper.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/mapper/AuditRecordMapper.java)

新增能力：

- `findByTaskId(taskId)`

这一步的意义不是“做一个新的审计平台”，而是让已有 `audit_record` 可以进入正式的任务级读模型。

### 2.3 Audit 侧 contract_governance 投影

本轮新增了 audit 侧治理视图组装逻辑：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

新增 helper：

- `buildAuditContractGovernanceView(detail)`

它将 audit `detail_json` 中已有的 contract 相关字段，统一投影为共享 DTO：

- `scope = audit_contract_governance`
- `frozen_contract_summary`
- `current_contract_summary`
- `consistency`
- `resume_contract_evaluation`

当前至少支持的字段包括：

- `failure_code`
- `mismatch_code`
- `compatibility_code`
- `migration_hint`
- `frozen_contract_version`
- `frozen_contract_fingerprint`
- `contract_identity.contract_version`
- `contract_identity.contract_fingerprint`

也就是说，审计侧现在不再需要直接解析原始 detail 文本或自由 JSON，才能理解一次 contract mismatch 到底发生了什么。

---

## 3. 本轮验证

### 3.1 自动化测试

本轮补充并通过了最小治理测试：

- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)

新增覆盖点：

- `getTaskAuditProjectsContractGovernanceFromAuditDetail`

该测试验证：

- audit detail 中的 frozen/current contract identity 能被正确投影
- `CONTRACT_VERSION_MISMATCH` 能进入 `consistency.mismatch_code`
- `migration_hint` 能进入 `resume_contract_evaluation`

执行命令：

```powershell
mvn -q -f BackEnd/pom.xml "-Dtest=TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,TaskProjectionBuilderTest,CapabilityContractGuardTest" test
```

结果：通过

说明：

- 测试日志中出现的 `State version conflict`、`CAPABILITY_CONTRACT_UNAVAILABLE` 是既有负向用例的预期异常，不代表本轮新增代码失败。

### 3.2 真实链路回归

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：通过

最新真实链路任务：

- `task_20260404204054350_e990c2ef`

这说明：

- 本轮增加 audit 读取与审计投影后，没有打坏当前 real-case governed success 主链。

---

## 4. 当前阶段结论

本轮完成后，`contract-first` 的正式治理视图已经覆盖：

- `detail`
- `manifest`
- `result`
- `audit`

这比上一轮前进了一步，因为 contract mismatch / drift 的治理证据现在不仅能在任务详情中看见，也能在审计读取边界中被结构化消费。

但本轮仍然有明确边界：

- 还没有单独抽出 audit 侧共享 DTO 组装器
- audit 仍然是从已有 `detail_json` 做受控投影，而不是独立的 contract audit source of truth
- 这仍然不是“完整 contract 审计平台”

因此，本轮最诚实的表述是：

> Phase3-C 已将 contract 治理语义进一步推进到 audit 读模型，形成 `detail / manifest / result / audit` 四类正式视图的一致 contract 投影；但 audit 仍是治理事实的受控读取层，而不是独立真相源。

---

## 5. 下一步建议

下一步不建议扩前端，而应继续收治理语义本身。

优先项：

1. 继续统一 audit / detail / manifest / result 之间的 contract drift code/view
2. 如有必要，再把 audit 侧 contract 投影抽成共享 assembler，而不是继续留在 `TaskService`
3. 只有在这些治理视图稳定后，再考虑是否增加前端最小投影
