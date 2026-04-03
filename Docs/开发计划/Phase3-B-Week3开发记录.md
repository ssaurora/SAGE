# Phase3-B / Week 3 开发记录
更新日期：2026-04-03

## 0. 文档目的

本文用于记录 Phase3-B Week 3 的实际开发结果，并对照四周推进计划中 Week 3 的目标，说明：

- 本周真正完成了什么
- 哪些旧逻辑已开始被替换
- 哪些证据已经形成
- 哪些边界仍未完成，不应被夸大

Week 3 的目标不是建设完整 Metadata Catalog 平台，而是：

> 交付一个最小但真实被主链消费的 catalog slice，让系统开始从“附件元数据直读”走向“catalog facts 驱动”。

---

## 1. 本周目标与成功定义

Week 3 的成功不以“新增了一个叫 catalog 的对象”为准，而以以下两条是否真实成立为准：

1. `pass2` 已开始接收并消费 `metadata_catalog_facts`
2. 至少一条旧的 attachment-driven readiness 判断已被 catalog slice 替换

换句话说，Week 3 关注的不是 catalog 平台化，而是：

- catalog facts 是否进入主链
- catalog facts 是否真的替换旧判断

---

## 2. 本周实际完成内容

## 2.1 Service 侧：为 `pass2` 增加最小 catalog contract

本周在 service 侧新增了最小 catalog schema：

- `MetadataCatalogFact`
- `PlanningPass2Request.metadata_catalog_facts`

对应文件：

- [schemas.py](/e:/paper_project/SAGE/Service/planning-pass1/app/schemas.py)

当前最小 catalog fact 结构至少包含：

- `asset_id`
- `logical_role_candidates`
- `file_type`
- `crs`
- `extent`
- `resolution`
- `nodata_info`
- `source`
- `checksum_version`
- `availability_status`
- `blacklist_flag`

这一步的意义是：

- `pass2` 不再只能消费 `pass1 / passb / validation`
- catalog facts 已成为 planning-side 的正式输入之一

## 2.2 Service 侧：`pass2` 开始真实消费 catalog facts

本周新增了最小 catalog helper，并接入 `build_pass2_response(...)`：

- [metadata_catalog.py](/e:/paper_project/SAGE/Service/planning-pass1/app/metadata_catalog.py)
- [planner.py](/e:/paper_project/SAGE/Service/planning-pass1/app/planner.py)

当前 `pass2` 会把 `metadata_catalog_facts` 归一化并汇总为 planning summary 中的 catalog 字段，例如：

- `catalog_asset_count`
- `catalog_ready_asset_count`
- `catalog_blacklisted_asset_count`
- `catalog_role_coverage_count`
- `catalog_materialized_role_count`
- `catalog_used_for_materialization`
- `catalog_source`

这说明 Week 3 的 catalog-first 已经开始进入 planning 主链，而不是只存在于设计文档中。

## 2.3 Backend 侧：新增附件到 catalog 的薄投影器

本周没有直接把附件表改造成 catalog 表，而是采用了更克制的 Week 3 路线：

- 先把 `TaskAttachment` 投影成最小 catalog facts

新增文件：

- [AttachmentCatalogProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/AttachmentCatalogProjector.java)

当前投影规则为：

- `asset_id <- attachment.id`
- `logical_role_candidates <- attachment.logicalSlot`
- `file_type <- contentType 或文件后缀推断`
- `source = task_attachment`
- `checksum_version <- checksum`
- `availability_status = READY / MISSING_METADATA`
- `blacklist_flag <- assignmentStatus == BLACKLISTED`

这一步的意义是：

- backend 已经开始用 catalog 语义表达附件事实
- Week 3 没有引入新的厚重平台层

## 2.4 Backend 侧：`MinReadyEvaluator` 已改为 catalog 语义

本周最关键的“替换旧债务”动作发生在：

- [MinReadyEvaluator.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/MinReadyEvaluator.java)

之前：

- `MinReadyEvaluator` 直接读取附件元数据
- 判断逻辑基于 `logicalSlot / fileName / sizeBytes / storedPath / checksum`

现在：

- 先通过 `AttachmentCatalogProjector` 生成 catalog facts
- 再以 `availability_status == READY`
- 且 `blacklist_flag == false`
- 且 `logical_role_candidates` 覆盖目标 slot

来判断 ready slots

这意味着：

> Week 3 已经完成了至少一条旧 attachment-driven readiness 判断向 catalog slice 的替换。

## 2.5 Backend 侧：`pass2` 请求正式带上 catalog facts

本周把 backend 到 service 的 `pass2` 调用也接上了 catalog facts：

- [Pass2Request.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/dto/Pass2Request.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

当前 `runPass2(...)` 会：

- 查询当前 task attachments
- 通过 `AttachmentCatalogProjector.project(...)` 转成最小 catalog facts
- 将结果放入 `metadata_catalog_facts`
- 再调用 service `/planning/pass2`

这说明 Week 3 的 catalog slice 已经形成了真实的：

- backend facts
- service contract
- pass2 consumption

闭环。

---

## 3. 本周测试与验证

本周已完成以下验证。

## 3.1 Service 测试

执行：

```powershell
pytest Service/planning-pass1/tests/test_pass1_api.py -q
```

结果：

- `25 passed`

新增验证点包括：

- `pass2` 接收 `metadata_catalog_facts`
- blacklisted asset 不参与 materialization
- 缺失 catalog facts 时，`catalog_used_for_materialization = false`

对应文件：

- [test_pass1_api.py](/e:/paper_project/SAGE/Service/planning-pass1/tests/test_pass1_api.py)

## 3.2 Backend 测试

执行：

```powershell
mvn -q "-Dtest=MinReadyEvaluatorTest,TaskServiceGovernanceTest" test
```

结果：

- 通过

新增验证点包括：

- blacklist catalog fact 不得被视为 ready
- `runPass2(...)` 已向 service 请求中注入 `metadata_catalog_facts`

对应文件：

- [MinReadyEvaluatorTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/MinReadyEvaluatorTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)

## 3.3 真实链路回归

本周实际回归了两条 Week 1 主链：

### Success

- 场景：`Success`
- 结果：通过
- task id：`task_20260403062652045_6662b696`

### Clarify

- 场景：`Clarify`
- 结果：通过
- task id：`task_20260403063339794_20ce7311`

这说明：

- Week 3 的 catalog slice 没有破坏 success 主链
- Week 3 的 catalog slice 没有破坏 clarify / resume 主链

---

## 4. 本周替换了哪些旧逻辑

Week 3 不是纯新增，本周已经有真实“替换旧债务”的证据。

### 已替换

- `MinReadyEvaluator` 的 ready slot 判断
  - 从“直接读附件元数据”
  - 替换为“先投影到 catalog facts，再按 catalog 语义判断”

### 已接入但尚未全面替换

- `pass2`
  - 已从“完全不认识 catalog”
  - 升级为“正式接收并消费 catalog facts”

### 尚未替换

- 目前 catalog facts 仍主要来源于 attachment projection
- 还没有独立的 catalog persistence / inventory version / parse status 体系
- 还没有真正的 Context Enricher catalog-first 路径

因此，Week 3 的正确表述应是：

> catalog slice 已开始进入主链并替换一部分旧判断，但完整 Metadata Catalog 体系尚未建立。

---

## 5. 本周没有完成的内容

为了避免夸大阶段完成度，这里明确写出 Week 3 尚未完成的边界。

### 5.1 尚未建设完整 Metadata Catalog 平台

当前没有：

- 独立 catalog 存储
- `asset_version`
- `parse_status`
- `FULL_READY`
- `delta_inventory`
- 多来源 catalog merge

因此，Week 3 不能对外表述为：

- “Metadata Catalog 已完整落地”

### 5.2 Context Enricher 还未真正 catalog-first

Week 3 本周聚焦在：

- `pass2`
- readiness gate

尚未把 catalog facts 推进到：

- cognition context enrichment
- 候选筛选优先读 catalog

这部分仍属于后续工作。

### 5.3 仍未引入独立 catalog 生命周期治理

当前 catalog facts 还没有独立版本冻结与恢复语义，只是 Week 3 的最小 slice。

因此，Week 3 尚不能宣称：

- resume 已基于独立 catalog 生命周期治理

---

## 6. 阶段结论

本周阶段结论为：

- `Go`

原因如下：

1. `pass2` 已正式接收并消费 `metadata_catalog_facts`
2. 至少一条旧 attachment-driven readiness 判断已被 catalog slice 替换
3. service 测试通过
4. backend 测试通过
5. Week 1 的 `Success / Clarify` 真实链路回归通过

因此，Week 3 已满足“最小 catalog-first slice 进入主链”的目标。

但同时必须强调：

> Week 3 的完成并不意味着完整 Metadata Catalog 已落地，而只是意味着 catalog facts 已经开始成为系统主链中的真实事实对象。

---

## 7. 下一步建议

Week 3 完成后，下一步应进入 Week 4：

- 收紧治理边界
- 清理控制层领域硬编码
- 形成最小能力面 contract

优先级建议如下：

1. 先把 `GoalRouteService`、`ExecutionContractAssembler` 中仍残留的领域硬编码继续外迁
2. 明确最小 contract 集：
   - `inspect_asset_facts`
   - `validate_bindings`
   - `validate_args`
   - `checkpoint_resume_ack`
   - `submit_job`
   - `query_job_status`
   - `collect_result_bundle`
3. 验证控制层与领域语义的边界能否通过“删除旧逻辑后主链仍成立”来证明

一句话概括 Week 3 的意义：

> Week 3 不是把 catalog 做“大”，而是把 catalog 变成了主链里真正会被消费、会替换旧判断的事实对象。
