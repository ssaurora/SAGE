# Phase3-C catalog 快照服务化记录
更新日期：2026-04-05

## 0. 文档目的

本文记录 Phase3-C 在 `catalog-first` 方向上的又一轮收口：

- 不新增新的 catalog 业务语义
- 将 `TaskService` 中残留的 task-level catalog snapshot 解析与持久化逻辑抽离为独立服务
- 明确 `TaskService` 只负责 workflow orchestration 和持久化协调，不再直接兼任 catalog 快照服务实现

本轮目标不是扩前端，也不是建设完整 Metadata Catalog 平台，而是继续加厚 backend 内部边界。

---

## 1. 本轮结论

本轮已经完成：

> task 级 catalog snapshot 的解析、复用和持久化，已从 `TaskService` 内部 helper 收口到独立的 `TaskCatalogSnapshotService`；`TaskService` 只保留对该服务的调用，不再直接操作 `TaskCatalogSnapshotMapper` 或自己拼装 snapshot payload。

这意味着当前 `catalog-first` 已形成更清晰的三层分工：

- `AttachmentCatalogProjector` 负责 attachment -> catalog facts / summary 投影
- `TaskCatalogSnapshotService` 负责 task 级 snapshot 复用与持久化
- `TaskService` 负责在上传、resume、manifest freeze、detail/result/manifest 读取路径中协调调用

---

## 2. 本轮实现内容

### 2.1 新增 `TaskCatalogSnapshotService`

新增文件：

- [TaskCatalogSnapshotService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskCatalogSnapshotService.java)

该服务统一承载：

- `resolveCatalogSummary(...)`
- `resolveManifestCatalogSummary(...)`
- `persistCatalogSnapshot(...)`

内部职责包括：

- 复用已存在的 `task_catalog_snapshot`
- 在缺失时按 `task_id + inventory_version` 生成并持久化 snapshot
- 统一写入：
  - `catalog_revision`
  - `catalog_fingerprint`
  - `catalog_summary_json`
  - `catalog_facts_json`

这一步的关键意义是：

- task 级 catalog snapshot 不再只是 `TaskService` 里的隐式 persistence helper
- catalog snapshot 生命周期开始具备独立服务边界

### 2.2 `TaskService` 改为消费 snapshot service

修改文件：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

主要调整：

- 构造注入从 `TaskCatalogSnapshotMapper` 改为 `TaskCatalogSnapshotService`
- 上传附件后触发的 snapshot 持久化，改为通过 service 完成
- `buildCatalogSummary(...)` 不再直接操作 mapper，而是通过 `TaskCatalogSnapshotService.resolveCatalogSummary(...)`
- `resolveManifestCatalogSummary(...)` 不再自己判断 snapshot/fallback，而是通过 service 统一处理
- 删除 `TaskService` 中原有的：
  - `resolveCatalogSnapshot(...)`
  - `persistCatalogSnapshot(...)`

这一步之后，`TaskService` 仍负责：

- 在哪些 workflow 节点调用 snapshot service
- 将 snapshot summary 写入 manifest / detail / result / waiting_context

但不再负责 snapshot 的底层构造和持久化细节。

### 2.3 同步修复脚本打包阶段的测试构造

修改文件：

- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)

修复内容：

- 两个 static harness 内部新增的 `TaskCatalogSnapshotService` 初始化改为在构造函数中完成
- 避免在 static 上下文中直接引用外层 `objectMapper`

这是为了保证脚本中的 backend 打包阶段不会因为 `testCompile` 卡在这两个测试文件上。

---

## 3. 验证情况

### 3.1 backend 干净打包

执行命令：

```powershell
mvn -q -f BackEnd/pom.xml clean "-Dmaven.test.skip=true" package
```

结果：通过

说明：

- backend 可在干净构建下正常产出 jar
- 本轮 catalog snapshot 服务化没有引入新的构建级问题

### 3.2 脚本打包入口恢复

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：通过

最新真实链路任务：

- `task_20260405070337874_47b8217a`

这说明两件事：

1. backend 打包、compose 启动、真实 success 主链都保持通过
2. 本轮修复后，脚本内使用的打包入口不再因为测试 harness 的构造问题卡住

---

## 4. 当前阶段判断

本轮最准确的判断是：

> Phase3-C 已把 task 级 catalog snapshot 的服务化边界独立出来；当前 `TaskService` 更接近 orchestration service，而不是继续兼任 catalog snapshot repository facade。

这不是新功能扩展，而是 catalog 生命周期边界继续加厚。

---

## 5. 下一步建议

当前最合理的下一步不是扩前端，而是继续做下面两件事之一：

1. 继续沿 `catalog-first` 收边界，把 `waiting_context / manifest / result` 上分散的 catalog identity 评估也逐步收成共享 projector / assembler
2. 单独治理 Maven 定向测试入口，恢复 `-Dtest=... test` 的稳定性

如果继续优先做能力边界，我建议先做第 1 条。
