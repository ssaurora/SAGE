# Phase3-C contract 事实投影进一步抽离记录
更新日期：2026-04-05

## 0. 文档目的

本文记录 Phase3-C 在 `contract-first` 方向上的又一轮收口：

- 不新增新的 contract 语义
- 继续把 contract drift / mismatch 的事实计算从 `TaskService` 内部逻辑中抽离
- 让 `TaskService` 更明确地只承担工作流编排、状态迁移和审计写入

本轮目标不是扩前端，也不是引入新的能力面平台，而是继续收紧 control layer 内部边界。

---

## 1. 本轮结论

本轮已经完成：

> `resume` 路径上的 contract drift 判定，已从 `TaskService` 的内联逻辑进一步收口到共享的 `ContractConsistencyProjector`；`TaskService` 只保留状态收口、resume transaction 落盘、审计写入和错误响应。

这意味着当前 `contract-first` 已形成两层明确边界：

- `ContractConsistencyProjector` 负责 contract identity / consistency / drift 事实计算
- `ContractGovernanceAssembler` 负责治理读模型 DTO 组装

---

## 2. 本轮实现内容

### 2.1 `ContractConsistencyProjector` 新增 resume drift 事实入口

修改文件：

- [ContractConsistencyProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/ContractConsistencyProjector.java)

本轮新增：

- `buildResumeContractDriftEvaluation(JsonNode frozenPass1Node, JsonNode currentPass1Node)`

该方法统一产出：

- `failure_code`
- `mismatch_code`
- `failure_reason`
- `frozen_contract_version`
- `frozen_contract_fingerprint`
- `current_contract_version`
- `current_contract_fingerprint`
- `compatibility_code`
- `migration_hint`

这使得 `resume` contract mismatch 不再需要由 `TaskService` 自己拼接字符串并重复做 compatibility 映射。

### 2.2 `TaskService` 改为消费共享 drift 事实

修改文件：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

调整内容：

- `ensureResumeContractIdentityCompatible(...)` 改为直接消费 `ContractConsistencyProjector.buildResumeContractDriftEvaluation(...)`
- 删除 `TaskService` 中只为该路径存在的局部 contract identity 提取逻辑：
  - `extractContractVersion(...)`
  - `extractContractFingerprint(...)`

保留在 `TaskService` 中的内容：

- `STATE_CORRUPTED` 状态收口
- `resume_transaction` 持久化
- 审计记录写入
- `409 CONFLICT` 响应返回

这符合当前仓库的层边界要求：

- 事实计算归 projector
- 工作流 authority 仍在 control layer service

---

## 3. 验证情况

### 3.1 backend 干净打包

执行命令：

```powershell
mvn -q -f BackEnd/pom.xml clean "-Dmaven.test.skip=true" package
```

结果：通过

说明：

- 本轮已确认 backend 可在干净构建下正常产出 jar
- 先前出现的 `NoClassDefFoundError: ForceRevertCheckpointRequest`，本质上是旧 `target` 产物污染导致的增量构建问题，而不是本轮源码边界调整本身的问题

### 3.2 真实链路回归

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：通过

最新真实链路任务：

- `task_20260405065124036_d6182a6b`

这说明：

- 本轮 projector 抽离没有打坏当前 governed success 主链
- backend 容器在干净构建后可正常启动并通过 healthcheck

### 3.3 关于 Maven 定向测试

当前环境中，`mvn ... -Dtest=... test` 仍会触发仓库内既有的测试源编译问题。  
这不是本轮 `ContractConsistencyProjector` 抽离新引入的问题，因为：

- backend 干净打包通过
- 真实 `Success` 链路通过

因此，本轮代码可视为运行链稳定，但测试入口本身仍应单独治理。

---

## 4. 当前阶段判断

本轮最准确的判断是：

> Phase3-C 已进一步把 contract drift 的事实计算从 `TaskService` 中抽离；当前 `TaskService` 更接近控制层 orchestrator，而不是继续兼任 contract 事实解释器。

这不是新功能扩展，而是内部边界继续加厚。

---

## 5. 下一步建议

当前最合理的下一步不是扩前端，而是继续做下面两件事之一：

1. 继续抽离 `contract-first` 的剩余事实计算，把 audit 侧 enrichment / conflict reason 也尽量减少在 `TaskService` 中的专有拼装
2. 单独治理 Maven 定向测试入口，让 `-Dtest=...` 不再和业务代码演进纠缠在一起

如果仍优先做能力边界，我建议先做第 1 条。
