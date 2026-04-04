# Phase3-B / catalog-first 深化记录
更新日期：2026-04-03

## 0. 文档目的

本文用于记录在 `Phase3-B-阶段总结与Go结论` 之后，围绕 `catalog-first` 路线继续推进的一轮深化开发。

本轮目标不是新增新的架构层，也不是扩展新的 capability，而是把已经进入主链的最小 catalog facts，进一步推进到：

- `waiting_context`
- task detail 读模型
- manifest 读模型
- result 读模型

并形成一组统一的 `catalog_consistency` 投影，减少前端和审计侧自行比对原始字段的负担。

---

## 1. 本轮目标

本轮成功标准不是“再多展示几个 catalog 字段”，而是以下两条同时成立：

1. `waiting_context` 本身能够带出受控的 `catalog_summary`
2. backend 能直接给出 detail / manifest / result 三类视图与当前 catalog facts 的一致性结论，而不是把比对责任留给前端

换句话说，本轮交付的是：

> 从“catalog facts 已进入主链”推进到“catalog facts 已能被结构化解释和审计”。

---

## 2. 实际完成内容

## 2.1 `waiting_context` 已正式承载 `catalog_summary`

当前 `WAITING_USER` 的 canonical repair view model 不再只包含：

- `waiting_reason_type`
- `missing_slots`
- `invalid_bindings`
- `required_user_actions`
- `resume_hint`
- `can_resume`

还会额外带出：

- `catalog_summary`

对应文件：

- [RepairProposalRequest.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/repair/dto/RepairProposalRequest.java)
- [RepairDispatcherService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/repair/RepairDispatcherService.java)

这意味着当前 `waiting_context` 已经能够直接告诉前端和审计：

- 当前 catalog 里有多少 attachment facts
- 有多少 ready assets
- 有多少 blacklisted assets
- 当前 ready role names 是什么
- 当前 catalog source 是什么

这一步仍然遵守既有边界：

- `waiting_context` 依然由 control layer 生成
- 前端依然只消费，不自行推断 repair 语义

## 2.2 detail / manifest / result 增加统一的 `catalog_consistency`

本轮新增的关键读模型字段是：

- `catalog_consistency`

对应文件：

- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskResultResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskResultResponse.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

当前三类一致性语义分别是：

### detail

scope:
- `waiting_context`

结论字段包括：
- `waiting_context_catalog_present`
- `waiting_context_matches_current_catalog`
- `stale_missing_slots`

其含义是：
- 当前 `waiting_context` 是否已经带了 catalog 视图
- 它和当前 attachment 投影出来的 catalog summary 是否一致
- 当前 still-missing 的 slots 里，是否有其实已经被 current catalog-ready roles 覆盖的 stale 项

### manifest

scope:
- `manifest_slot_bindings`

结论字段包括：
- `expected_role_names`
- `missing_catalog_roles`
- `covered`

其含义是：
- manifest 冻结下来的 `slot_bindings.role_name`
- 是否都被当前 catalog-ready roles 覆盖

### result

scope:
- `result_input_bindings`

结论字段包括：
- `expected_role_names`
- `missing_catalog_roles`
- `covered`

其含义是：
- runtime evidence 中的 `input_bindings.role_name`
- 是否都被当前 catalog-ready roles 覆盖

## 2.3 `AttachmentCatalogProjector` 新增一致性 helper

为了避免 detail / manifest / result 三处各自重新解释 catalog，本轮把一致性计算统一收敛到：

- [AttachmentCatalogProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/AttachmentCatalogProjector.java)

新增 helper 包括：

- `extractReadyRoleNames(...)`
- `buildCoverageConsistency(...)`

这一步的意义是：

- 继续维持 catalog 解释逻辑的单点收口
- 避免前端、TaskService、读模型 builder 各自复制一套 ready role 解释规则

---

## 3. 本轮测试与验证

## 3.1 Backend 定向测试

执行：

```powershell
mvn -q "-Dtest=TaskServiceCognitionFlowTest,TaskServiceGovernanceTest,RepairDispatcherServiceTest,TaskProjectionBuilderTest,MinReadyEvaluatorTest,AttachmentCatalogProjectorTest" test
```

结果：

- 通过

本轮新增或补强的验证点包括：

- `RepairDispatcherService` 会把 `catalog_summary` 写入 `waiting_context`
- `TaskProjectionBuilder` 能正确投影 `waiting_context.catalog_summary`
- task detail 能识别 `stale_missing_slots`
- task manifest 能投影 `manifest_slot_bindings` 的 catalog coverage
- task result 能投影 `result_input_bindings` 的 catalog coverage

对应测试文件：

- [RepairDispatcherServiceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/repair/RepairDispatcherServiceTest.java)
- [TaskProjectionBuilderTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskProjectionBuilderTest.java)
- [TaskServiceCognitionFlowTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceCognitionFlowTest.java)
- [TaskServiceGovernanceTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/TaskServiceGovernanceTest.java)
- [MinReadyEvaluatorTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/MinReadyEvaluatorTest.java)
- [AttachmentCatalogProjectorTest.java](/e:/paper_project/SAGE/BackEnd/src/test/java/com/sage/backend/task/AttachmentCatalogProjectorTest.java)

## 3.2 真实链路回归

本轮实际回归了：

```powershell
scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：

- 通过

task id：

- `task_20260403083525297_b06dd5c0`

需要明确说明：

- 本轮没有把 `Clarify` 重新作为正式通过证据回填
- 之前尝试重跑 `Clarify` 时，compose 网络/容器环境出现冲突和远程连接失败噪音
- 这次文档只把 `Success` 作为本轮新增代码的真实链路回归证据

---

## 4. 本轮完成后，catalog-first 的位置

截至本轮结束，以下路径已经围绕同一套 catalog facts 或其受控投影工作：

1. `pass2` planning-side catalog consumption
2. `MinReadyEvaluator` resume gate
3. `RepairDispatcherService` waiting-context recompute
4. `waiting_context.catalog_summary`
5. `TaskDetailResponse.catalog_summary`
6. `TaskManifestResponse.catalog_summary`
7. `TaskResultResponse.catalog_summary`
8. `TaskDetailResponse.catalog_consistency`
9. `TaskManifestResponse.catalog_consistency`
10. `TaskResultResponse.catalog_consistency`

这说明当前 catalog-first 已经不只是“主链内部事实”，而开始成为：

- 用户可见的治理解释对象
- 审计可见的一致性解释对象

---

## 5. 本轮没有完成的内容

为了避免夸大，这里明确写出边界。

## 5.1 `catalog_consistency` 仍是读模型解释，不是新的 source of truth

当前 `catalog_consistency` 的定位是：

- backend 读模型投影
- 用于解释一致性
- 不用于替代 `task_state`、`job_record`、`analysis_manifest` 或 `waiting_context_json`

因此不能把本轮表述成：

- “catalog consistency 已成为新的治理 authority”

## 5.2 仍未建立独立 catalog 生命周期

当前仍然没有：

- 独立 catalog persistence
- `asset_version`
- `parse_status`
- `FULL_READY`
- `delta_inventory`
- catalog 版本冻结与恢复体系

因此本轮不能表述成：

- “完整 Metadata Catalog 治理体系已建立”

## 5.3 前端展示规范尚未同步

虽然 detail / manifest / result 已经带出：

- `catalog_summary`
- `catalog_consistency`

但前端页面规范和实际页面投影还没有在本轮同步收口。

因此本轮仍主要属于：

- backend facts / projection 深化

而不是：

- 用户界面完成态

---

## 6. 本轮结论

本轮阶段结论为：

- `Go`

原因：

1. `waiting_context` 已正式承载 `catalog_summary`
2. detail / manifest / result 已形成统一的 `catalog_consistency` 投影
3. backend 定向测试通过
4. 真实 `Success` 链路回归通过
5. 本轮改动继续沿用现有 authority 边界，没有把 catalog 解释权下放给前端

更准确的一句总结是：

> 本轮把 catalog-first 从“进入主链的事实对象”进一步推进为“可被治理层、读模型和审计层直接解释的一致性对象”，但尚未建立完整的独立 Metadata Catalog 生命周期体系。

---

## 7. 下一步建议

如果继续沿 `catalog-first` 深化，下一步最合理的是：

1. 把 `catalog_summary / catalog_consistency` 对应到前端展示规范和实际页面投影
2. 继续推进 catalog 生命周期事实，而不只是 attachment projection
3. 在 resume / manifest / result 之间建立更正式的 catalog version / freeze 语义

如果不继续沿 `catalog-first` 深化，则另一条自然路线是：

1. 回到 capability / contract 消费扩展
2. 把当前 skill asset 和最小 contract 再向下游消费链推深一层

