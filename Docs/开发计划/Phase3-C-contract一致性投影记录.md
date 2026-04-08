# Phase3-C Contract 一致性投影记录

更新日期：2026-04-05

---

## 0. 文档目的

本文用于记录 `Phase3-C` 中关于 **contract identity 从恢复交易扩展到 detail / manifest / result 正式读模型** 的实现结果。

这一轮的目标不是新增新的能力 contract，也不是扩前端交互，而是把已经进入治理语义的：

- `contract_version`
- `contract_fingerprint`

继续推进成：

- 可冻结
- 可比较
- 可投影
- 可审计

的正式系统对象。

---

## 1. 本轮目标

本轮需要完成两件事：

1. 把 frozen contract identity 正式写入 manifest 本体，而不是只停留在 pass1 输出或 resume transaction 中
2. 在 `detail / manifest / result` 读模型中给出 contract consistency 投影，让系统能直接回答：
   - 当前对象基于哪一版 contract
   - 当前运行时看到的 contract 是否与 frozen contract 一致
   - 如果发生漂移，漂移发生在什么边界

本轮不追求：

- 完整 contract 生命周期平台
- 所有 contract 类型的独立持久化表
- 对所有读模型做全量 contract drift 可视化

---

## 2. 已完成内容

### 2.1 Frozen contract summary 进入 manifest 本体

`analysis_manifest` 新增了：

- `contract_summary_json`

对应改动包括：

- [AnalysisManifest.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/model/AnalysisManifest.java)
- [AnalysisManifestMapper.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/mapper/AnalysisManifestMapper.java)
- [V15__phase3c_manifest_contract_summary.sql](/e:/paper_project/SAGE/BackEnd/src/main/resources/db/migration/V15__phase3c_manifest_contract_summary.sql)

当前 `buildManifestCandidate(...)` 在冻结 manifest 时会把当时的 contract summary 一并写入：

- `contract_version`
- `contract_fingerprint`
- `contract_count`
- `contract_names`
- `contract_present`

这意味着 contract identity 已不再只是 task 级瞬时投影，而开始进入冻结执行对象。

### 2.2 detail / manifest / result 新增 contract consistency 投影

以下三个正式读模型现在都承载：

- `contract_consistency`

对应 DTO：

- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)

### 2.3 TaskService 已形成三类 consistency 语义

在 [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java) 中，本轮新增并消费了：

- `resolveManifestContractSummary(...)`
- `buildContractSummary(...)`
- `buildFrozenContractConsistency(...)`
- `buildDetailContractConsistency(...)`

当前三类 scope 为：

- `task_contract`
  - 用于 `detail`
  - 比较 active/frozen contract 与当前 pass1 contract
  - 额外吸收 `resume transaction` 中的 mismatch 结构化字段
- `manifest_contract`
  - 用于 `manifest`
  - 比较 frozen manifest contract 与当前 task pass1 contract
- `result_manifest_contract`
  - 用于 `result`
  - 比较 result 关联 manifest contract 与当前 task pass1 contract

### 2.4 Resume mismatch 结构化信息继续保留，并进入 detail consistency

此前已经进入 `resume transaction` 的字段：

- `failure_code`
- `base_contract_version`
- `base_contract_fingerprint`
- `candidate_contract_version`
- `candidate_contract_fingerprint`

本轮没有推翻这条线，而是让 `detail.contract_consistency` 直接吸收这些结构化 mismatch 事实。

这意味着 detail 读模型不再需要依赖解析 `failure_reason` 文本，便可得知：

- 是否检测到 contract drift
- drift 发生时 frozen/current 分别是哪一版 identity

---

## 3. 当前系统形成的 contract 一致性语义

到本轮结束，系统已经可以区分三类 contract identity：

1. **Current contract identity**
   - 来自当前 `task_state.pass1_result_json`
2. **Frozen manifest contract identity**
   - 来自 `analysis_manifest.contract_summary_json`
3. **Resume transaction contract identity**
   - 来自 `/resume` 过程中冻结前后的 base/candidate 对比

因此系统已经可以在不同边界回答：

- 当前 task 正在使用哪一版 contract
- frozen manifest 基于哪一版 contract
- `/resume` 时是否发生了 contract 漂移
- 当前读模型看到的 contract 是否与 frozen 对象一致

---

## 4. 测试与验证

### 4.1 自动化测试

本轮主要覆盖：

- frozen manifest 优先读取 `contract_summary_json`
- `manifest / result` 会给出正确的 `contract_consistency.scope`
- frozen/current contract 一致时，`matches_current_contract=true`
- `detail` 继续保留对 resume mismatch 的结构化投影

相关测试文件：

- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)
- [CapabilityContractGuardTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/CapabilityContractGuardTest.java)

实际通过命令：

```powershell
mvn -q "-Dtest=TaskServiceCognitionFlowTest,TaskServiceGovernanceTest,TaskProjectionBuilderTest,CapabilityContractGuardTest" test
```

### 4.2 真实链路回归

实际通过：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新真实链路任务：

- `task_20260404191839645_d14e0b86`

---

## 5. 本轮结论

本轮完成后，可以更准确地说：

> contract identity 已从 pass1 输出与 resume transaction，推进到 frozen manifest 本体以及 detail / manifest / result 正式读模型，系统已具备最小的 contract consistency 投影能力。

这带来的实际改进是：

- contract 不再只是“当前 task 的一组字段”
- frozen 执行对象开始携带自己的 contract identity
- 读模型可以直接给出 frozen/current 是否一致的结论
- `/resume` 上的 contract mismatch 不再只停留在交易记录，而已能进入 detail 级解释视图

---

## 6. 仍未完成的部分

本轮仍然没有完成以下事项：

1. 还没有为 `detail / manifest / result` 统一定义更细粒度的结构化 drift code
2. 还没有把 contract consistency 推进到更完整的审计记录对象
3. 还没有为 manifest/result 建立独立的“contract revision 生命周期”，目前仍是 summary + identity 比较
4. 还没有覆盖所有 contract 类型的逐项 drift 可见性，当前仍以整体 identity 为主

因此，本轮应被定义为：

- **contract consistency 投影成立**

而不是：

- **完整 contract 生命周期平台成立**

---

## 7. 下一步建议

下一步最合理的方向有两条：

1. 继续深化 contract 侧
   - 为 `detail / manifest / result` 增加更统一的 drift code / mismatch code
   - 把 contract identity 进一步推进到审计对象

2. 回到更高一层做阶段收口
   - 把 `catalog persistence + contract freeze + mismatch governance + consistency projection` 合并成新的 `Phase3-C` 阶段总结

如果继续以能力侧为优先，本轮之后更推荐先做第 1 条。
