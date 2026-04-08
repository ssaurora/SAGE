# Phase3-C：Catalog 治理视图收口记录

更新日期：2026-04-08

---

## 0. 文档目的

本文记录 Phase3-C 中 `catalog-first` 继续深化的一次关键收口：

> 将 `detail / manifest / result` 上分散的 catalog identity 与 coverage 计算，进一步收成共享 projector / assembler，并形成正式的 `catalog_governance` 读模型。

本文关注的不是前端展示，而是后端治理语义是否真正开始稳定。

---

## 1. 本轮要解决的问题

在上一阶段中，系统已经具备：

- task 级 catalog snapshot 持久化
- `waiting_context.catalog_summary`
- `detail / manifest / result` 上的 `catalog_summary`
- `catalog_consistency` 的最小 identity / coverage 语义

但当时仍存在两个不够干净的问题：

1. catalog 一致性计算和治理视图组装仍部分散落在 `TaskService`
2. `TaskResult` 路径上的 catalog consistency 计算顺序不稳定，存在“先写一版、后覆盖一版”的问题

如果继续维持这种状态，系统虽然“能跑”，但 catalog-first 仍然更像一组局部补丁，而不是稳定边界。

---

## 2. 本轮实现

### 2.1 新增共享治理视图 DTO

新增：

- [CatalogGovernanceView.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/CatalogGovernanceView.java)

该 DTO 统一承载：

- `scope`
- `baseline_catalog_summary`
- `current_catalog_summary`
- `consistency`

其中：

- `baseline_catalog_summary` 表示冻结或等待态所基于的 catalog identity
- `current_catalog_summary` 表示当前 attachment projection / snapshot 所代表的 catalog identity
- `consistency` 表示 identity 与 coverage 的统一评估结果

这意味着 catalog 治理视图不再只是一组零散 `Map<String,Object>`。

### 2.2 新增共享 projector

新增：

- [CatalogConsistencyProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CatalogConsistencyProjector.java)

该 projector 负责统一产出三类语义：

1. frozen/current catalog identity 一致性  
   例如：
   - `CATALOG_MATCHED`
   - `CATALOG_REVISION_MISMATCH`
   - `CATALOG_FINGERPRINT_MISMATCH`
   - `BASELINE_CATALOG_MISSING`
   - `CURRENT_CATALOG_MISSING`
   - `CATALOG_IDENTITY_UNAVAILABLE`

2. waiting_context 的 stale 判断  
   即：
   - `waiting_context.missing_slots` 是否已被当前 ready roles 覆盖

3. role coverage 评估  
   即：
   - `expected_role_names`
   - `catalog_ready_role_names`
   - `missing_catalog_roles`
   - `covered`

这一步的意义在于：catalog 相关事实计算开始脱离 `TaskService` 本体。

### 2.3 新增共享 assembler

新增：

- [CatalogGovernanceAssembler.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/CatalogGovernanceAssembler.java)

该 assembler 将 raw `catalog_summary / catalog_consistency` 转换为正式 `CatalogGovernanceView`。

结果是：

- `TaskService` 不再自己拼 catalog governance DTO
- catalog 治理视图的 API contract 开始固定

### 2.4 `TaskService` 路径收口

修改：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

收口后：

- `getTask(...)` 通过 shared projector + assembler 组装 `catalog_consistency / catalog_governance`
- `getTaskManifest(...)` 通过 shared projector + assembler 组装 `catalog_consistency / catalog_governance`
- `getTaskResult(...)` 修正为：
  - 先建立基础 frozen/current identity consistency
  - 再在 runtime evidence 就绪后，使用最终 `input_bindings` 计算 coverage
  - 最后再写入 `catalog_consistency / catalog_governance`

这修掉了之前 result 路径上“使用不存在 helper”以及“先写临时 consistency、再覆写”的问题。

### 2.5 Response DTO 正式接入

修改：

- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)

三类读模型现在都正式包含：

- `catalog_governance`

---

## 3. 本轮验证

### 3.1 打包验证

已通过：

```powershell
mvn -q -f BackEnd/pom.xml clean "-Dmaven.test.skip=true" package
```

这说明 backend 当前代码在干净构建下可正常产出 jar。

### 3.2 真实链路验证

已通过：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

最新任务：

- `task_20260408132716496_e9649f28`

这说明本轮 catalog 治理视图收口没有打坏 Phase3 真实 success 主链。

---

## 4. 本轮结论

本轮不是新增新能力，而是把已有的 catalog-first 语义收成更稳定的后端边界。

可以明确确认的结果是：

- catalog identity / coverage 的事实计算已开始脱离 `TaskService`
- `detail / manifest / result` 已共享同一套 catalog governance DTO
- result 路径上的 catalog consistency 计算顺序已被修正
- `catalog-first` 的治理视图已经从零散字段推进到正式读模型

---

## 5. 仍未完成的部分

本轮完成后，仍需保持克制，不能夸大为“完整 Metadata Catalog 平台已落地”。

当前仍未完成的部分包括：

- catalog 生命周期仍主要围绕 task snapshot，而不是独立 catalog domain object
- catalog parse / ingestion / blacklist 生命周期仍未独立成完整状态机
- audit 侧还没有 catalog 专属的共享治理视图
- catalog consistency 仍以 API 读模型为主，还没有单独的公共 query contract

---

## 6. 下一步建议

下一步最合理的方向不是扩前端，而是继续沿能力边界深化：

1. 将 `waiting_context / manifest / result / audit` 上的 catalog governance 再统一一层
2. 继续把 catalog 事实计算从 `TaskService` 中抽离，收成更稳定的 projector / service 边界
3. 在后续阶段考虑 catalog 专属 audit / query contract，而不是停留在 task read model 附属字段

一句话概括本轮成果：

> 本轮已将 catalog-first 从“可见 summary + consistency 字段”推进到“共享治理视图 + 共享事实投影”，使 catalog 语义开始具备更稳定的后端边界。
