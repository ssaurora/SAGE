# Phase3-B 阶段总结与 Go 结论
更新日期：2026-04-03

## 0. 文档目的

本文用于对 Phase3-B 的四周推进结果做统一收束，并形成正式阶段结论。

本文不重复每周开发记录中的细节，而重点回答四个问题：

- Phase3-B 实际完成了什么
- 这些完成项对应了哪些原始偏离收缩
- 哪些证据已经形成，可以支持阶段结论
- 哪些边界仍未完成，不能被夸大为“总体设计已落地”

本文对应的周记录为：

- [Phase3-B-Week1验收记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-B-Week1验收记录.md)
- [Phase3-B-Week2开发记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-B-Week2开发记录.md)
- [Phase3-B-Week3开发记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-B-Week3开发记录.md)
- [Phase3-B-Week4开发记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-B-Week4开发记录.md)

---

## 1. 阶段目标回顾

Phase3-B 的目标不是把系统扩成完整平台，也不是继续把演示界面做得更像产品，而是按四周计划，优先收缩当前最危险的几个偏离：

1. 单 case 依赖
2. skill 主要存在于代码中
3. catalog-first 只存在于设计口径中
4. control layer 仍残留过多领域语义和隐式默认值

对应的四周目标分别是：

- Week 1：把系统从单 case 成功链推进到最小 case space
- Week 2：把 `water_yield` 从代码知识推进到真实 skill 资产
- Week 3：让最小 catalog slice 进入主链
- Week 4：收紧治理边界，继续清理控制层硬编码，并让最小 contract 进入真实消费路径

---

## 2. Phase3-B 实际完成的核心结果

## 2.1 已从单 case 成功链进入最小多 case governed projection

截至 Phase3-B 结束时，`water_yield` 已不再只有单一 executable case。

当前已形成两个真实可执行 case：

- `annual_water_yield_gura`
- `annual_water_yield_gtm_national`

对应的 case descriptor 位于：
- [water_yield_cases.json](/e:/paper_project/SAGE/sample%20data/case-descriptors/water_yield_cases.json)

这一步的意义不是“多了一个样例”，而是：

- clarify 有了真实候选空间
- `case_projection` 不再只是结构存在
- 第二个 case 没有依赖 Java 旁路接入
- real execution 继续走同一条 governed 主链

---

## 2.2 已完成一个真实 skill 的最小资产化闭环

`water_yield` 已从主要存在于代码中的 capability/skill，推进到一套真实被运行时消费的 skill asset。

当前 skill asset 位于：
- [SKILL.md](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/SKILL.md)
- [skill_profile.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/skill_profile.yaml)
- [parameter_schema.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/parameter_schema.yaml)
- [validation_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/validation_policy.yaml)
- [repair_policy.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/repair_policy.yaml)
- [interpretation_guide.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/interpretation_guide.yaml)
- [plan_templates.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/plan_templates.yaml)
- [mcp_tools_map.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/mcp_tools_map.yaml)

更关键的是，Week 2 达成了“资产掌权”，而不是“文件存在”：

- `PassB -> parameter_schema -> args_draft` 已成为硬 Gate
- schema 缺失、损坏或绑定失败时，不得静默 fallback 到旧 `skill_catalog.py`
- `goal-route`、`validation`、`repair`、`final explanation` 都至少形成了一个真实消费点
- `skill_id / skill_version` 已进入主链输出、冻结语义和任务投影

---

## 2.3 最小 catalog slice 已进入主链

Phase3-B 并没有交付完整 Metadata Catalog 平台，但已经交付了一个最小 catalog-first slice，并让它进入真实主链。

当前相关实现位于：

- [metadata_catalog.py](/e:/paper_project/SAGE/Service/planning-pass1/app/metadata_catalog.py)
- [schemas.py](/e:/paper_project/SAGE/Service/planning-pass1/app/schemas.py)
- [AttachmentCatalogProjector.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/AttachmentCatalogProjector.java)
- [MinReadyEvaluator.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/MinReadyEvaluator.java)

这一步的关键不是“有 catalog 对象”，而是：

- `pass2` 已正式接收并消费 `metadata_catalog_facts`
- 至少一条旧的 attachment-driven readiness 判断已被 catalog 语义替换
- blacklisted asset 已能在候选阶段被过滤

也就是说，catalog 已开始作为事实层进入系统，而不再只是文档里的原则。

---

## 2.4 控制层已开始退出部分领域语义承担者角色

Phase3-B 没有把 control layer 彻底清空为纯 host，这不现实；但已经收紧了几类最危险的控制层耦合。

当前已完成的收口包括：

- `GoalRouteService` 不再把 case 名称当作确定性 skill 路由信号
- Java 侧不再硬编码 `seasonality_constant / root_depth_factor / pawc_factor` 作为兜底语义默认值
- deterministic Java fallback 不再注入 `provider_preference / runtime_profile_preference`

相关文件：

- [GoalRouteService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/GoalRouteService.java)
- [ExecutionContractAssembler.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/ExecutionContractAssembler.java)
- [SemanticDefaultResolver.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/SemanticDefaultResolver.java)
- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

这意味着：

- control layer 仍然拥有治理 authority
- 但已经开始减少对 case 语义、参数语义、provider 偏好的暗中持有

---

## 2.5 最小 contract 已开始进入真实输出与读模型

Phase3-B 没有建设完整能力面平台，但已经让最小 contract 集从 skill asset 进入：

1. service 的真实输出
2. backend 的正式读模型

当前 contract 集包括：

- `inspect_asset_facts`
- `validate_bindings`
- `validate_args`
- `checkpoint_resume_ack`
- `submit_job`
- `query_job_status`
- `collect_result_bundle`

相关文件：

- [mcp_tools_map.yaml](/e:/paper_project/SAGE/Service/planning-pass1/skills/water_yield/mcp_tools_map.yaml)
- [skill_assets.py](/e:/paper_project/SAGE/Service/planning-pass1/app/skill_assets.py)
- [Pass1Response.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/planning/dto/Pass1Response.java)
- [TaskProjectionBuilder.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskProjectionBuilder.java)
- [TaskManifestResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskManifestResponse.java)
- [TaskDetailResponse.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/dto/TaskDetailResponse.java)

当前 contract 的真实消费状态是：

- service `capability_facts.contracts` 已带出正式字段
- manifest `capability_facts.contracts` 已可见
- detail `pass1_summary.contract_count / contract_names` 已可见

这说明 contract 已开始从“内部资产文件”变成“真实系统语言”。

---

## 3. Phase3-B 收缩了哪些原始偏离

对照 [总体设计与当前实现的偏离评估.md](/e:/paper_project/SAGE/Docs/架构设计/总体设计与当前实现的偏离评估.md)，Phase3-B 至少收缩了以下四类偏离。

## 3.1 从单 case 偏离收缩到最小 case space

此前问题：
- 只有一个真实 executable case
- clarify 和 projection 结构成立，但证据不足

当前结果：
- 两个真实 case
- `Success / CaseBSuccess / Clarify` 已形成真实回归证据

## 3.2 从代码知识收缩到一个真实 assetized skill

此前问题：
- skill 主要存活于 `skill_catalog.py`
- asset 文件缺位或不掌权

当前结果：
- `water_yield` 已形成真实 asset 包
- `parameter_schema` 成为执行 contract 的硬 Gate

## 3.3 从附件直读收缩到最小 catalog facts 路径

此前问题：
- ready gate 主要直接依赖附件元数据
- catalog-first 只在设计文档里存在

当前结果：
- `pass2` 和 `MinReadyEvaluator` 已真实消费 `metadata_catalog_facts`

## 3.4 从控制层领域耦合收缩到更薄的治理主机

此前问题：
- Java 控制层仍保留 case 语义、参数默认值语义、provider/runtime 暗示

当前结果：
- 这些语义已部分迁回 asset / pass1 冻结事实 / registry 路径

---

## 4. 已形成的证据

Phase3-B 的结论不是基于主观判断，而是基于已有的自动化与真实链路证据。

## 4.1 Service 测试

当前 service 侧关键测试已通过：

```powershell
pytest Service/planning-pass1/tests/test_pass1_api.py -q
```

已验证内容包括：

- two-case projection / clarify
- `parameter_schema` 失效时不得静默 fallback
- `metadata_catalog_facts` 进入 `pass2`
- contract 元信息进入 `capability_facts`

---

## 4.2 Backend 测试

当前 backend 侧关键测试已通过：

```powershell
mvn -q "-Dtest=TaskProjectionBuilderTest,GoalRouteServiceTest,ExecutionContractAssemblerTest,TaskServiceGovernanceTest,TaskServiceCognitionFlowTest" test
```

已验证内容包括：

- control-layer fallback 变薄
- assembler 不再硬兜底默认值
- task detail / manifest / result skill facts 一致
- contract 投影进入 backend 读模型

---

## 4.3 真实链路回归

Phase3-B 期间已反复跑通真实场景回归，至少包括：

- `Success`
- `CaseBSuccess`
- `Clarify`

阶段内代表性 task id 包括：

- Week 1 `Success`: `task_20260402141017241_52d27c91`
- Week 2 `CaseBSuccess`: `task_20260403031313311_d68088f1`
- Week 2 `Clarify`: `task_20260403025925211_45b92197`
- Week 3 `Success`: `task_20260403062652045_6662b696`
- Week 3 `Clarify`: `task_20260403063339794_20ce7311`
- Week 4 `Clarify`: `task_20260403072837290_e28ead7b`
- Week 4 `Success`: `task_20260403074448715_eccb5855`

这些证据足以说明：

- 双 case governed 主链成立
- skill 资产化没有打坏主链
- catalog slice 没有打坏主链
- Week 4 控制层去领域化没有打坏主链

---

## 5. Phase3-B 没有完成的内容

为了避免阶段表述失真，这里明确写出 Phase3-B 没有完成的边界。

## 5.1 没有完成完整 Metadata Catalog 平台

当前仍没有：

- 独立 catalog persistence
- `asset_version`
- `parse_status`
- `FULL_READY`
- `delta_inventory`
- 完整 inventory/version 治理

因此不能把当前结果表述成“完整 Metadata Catalog 已落地”。

## 5.2 没有完成完整能力面平台

当前虽然有最小 contract 集，但仍没有：

- 独立 capability gateway
- 完整 contract registry
- 统一 contract 级 auth / audit enforcement
- 广义多 capability contract 消费体系

因此不能把当前结果表述成“完整能力面已落地”。

## 5.3 没有完成完整多 skill 系统

当前真正资产化且进入真实主链的只有：

- `water_yield`

因此不能把当前结果表述成“多 skill 已完成”。

## 5.4 没有完成总体设计中的完整六层协同形态

当前 service 仍是压缩形态：

- cognition / planning / validate / repair / jobs 仍在一个 Python service 中

因此不能把当前结果表述成“总体设计已全部落地”。

---

## 6. 阶段结论

Phase3-B 的正式结论为：

- `Go`

理由如下：

1. 已形成最小多 case governed projection 证据  
2. 已完成一个真实 skill 的最小资产化闭环  
3. 最小 catalog slice 已进入主链并替换部分旧判断  
4. control layer 已进一步收缩不该长期保留的领域耦合  
5. 最小 contract 已进入真实输出与读模型  
6. 自动化测试与真实链路回归均已形成证据  

但必须保持统一口径：

> Phase3-B 完成的是从“单纵切验证”向“最小多 case、单真实 skill 资产化、最小 catalog facts、较薄控制层、最小 contract 消费”的过渡，而不是总体设计的完整落地。

---

## 7. 建议的对外表述

如果需要向团队、导师、评审或文档读者统一表述当前阶段成果，建议使用：

> 当前系统已完成从单 case 验证型纵切，向“最小多 case、单真实 skill 资产化、最小 catalog facts 驱动、较薄控制层、受治理 contract 执行框架”的过渡；但距离总体设计中的完整六层协同、完整 Skill-first 体系、完整 Metadata Catalog 与完整能力面平台，仍处于早期工程化阶段。

---

## 8. 下一步建议

Phase3-B 结束后，下一阶段不应回头扩散 UI 或重新膨胀 control layer。更合理的下一步有两条主线：

1. 继续做更正式的 catalog-first 收口  
   把当前 attachment projection 型 catalog slice，推进到更稳定的 inventory/catalog 事实体系。

2. 继续做 capability / contract 的真实消费扩展  
   让 contract 不只停留在 `water_yield` 与调试读模型，而是进入更稳定的调用和治理边界。

无论选哪条线，Phase3-B 的已完成边界都应被视为新的稳定基线，而不是继续用快捷逻辑回退。

