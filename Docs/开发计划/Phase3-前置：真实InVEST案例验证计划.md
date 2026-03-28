# Phase3-前置：真实 InVEST 案例验证计划

更新日期：2026-03-27

## 1. 文档定位

本文件是 `Phase2-B` 冻结后的下一份正式开发计划。

它不是：

- `Phase2-B` 补丁清单
- 完整 `Phase3` 主计划
- 多 skill / 多 provider 扩展方案

它是：

- 一份用于直接指导研发开工的**前置验证作战单**
- 一份围绕单个真实 `water_yield` 案例的**强 contract、强验收、强防作弊**计划
- 一份用于形成 `go / conditional go / no-go` 结论的正式工程文档

本计划与以下文档配套阅读：

- [Phase2-B-最终验收记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase2-B-最终验收记录.md)
- [Phase2-B-冻结基线说明.md](/e:/paper_project/SAGE/Docs/开发计划/Phase2-B-冻结基线说明.md)
- [渐进式实施路线图.md](/e:/paper_project/SAGE/Docs/开发计划/渐进式实施路线图.md)

---

## 2. 当前工作已经支持什么

### 2.1 已支持的主链能力

当前基线已经支持从自然语言到单 skill `water_yield` 的完整治理链：

- `goal_parse`
- `skill_route`
- `Pass1`
- `PassB`
- `Validation`
- `Pass2`
- `job`
- `result`
- `explanation`

这意味着系统的认知、规划、控制、执行与读模型链条已经贯通，当前并不缺“系统骨架”。

### 2.2 已支持的治理与恢复能力

当前基线已经支持：

- `WAITING_USER`
- upload / override
- `/resume`
- `MIN_READY`
- `RESUMING`
- `STATE_CORRUPTED`
- `force-revert-checkpoint`

这意味着真实案例后续所需的等待、补传、恢复、损坏治理能力，不需要重新从零设计。

### 2.3 已支持的编译治理与结果治理

当前基线已经支持：

- `graph_digest`
- `planning_summary`
- `canonicalization_summary`
- `rewrite_summary`
- `runtime_assertions`
- `ARTIFACT_PROMOTING`

这意味着真实案例一旦引入真实 provider，仍然可以复用现有 planning / manifest / promotion 主链，而不需要旁路执行。

### 2.4 已支持的 authority facts 闭环

当前基线已经支持以下读模型与展示主路径：

- detail
- manifest
- result
- artifacts
- runs

frontend 已经能够消费 backend authority facts，并且已经具备管理员对治理状态的操作入口。

### 2.5 已支持的容器化栈与验收骨架

当前基线已经具备：

- `docker compose --env-file .env.compose`
- `week6-e2e.ps1`

这意味着真实案例的下一步不是另起炉灶，而是在当前 compose 栈和验收脚本体系上增加一条真实 InVEST 路径。

---

## 3. 当前基线的边界与证据缺口

虽然主链已经成立，但当前执行层仍然存在明确边界：

- 运行时仍是 deterministic `water_yield` runtime，不是真实 InVEST provider
- 仓库中没有现成真实样例数据集
- `Service/planning-pass1` 当前镜像没有真实 InVEST 依赖

因此，当前系统已经能证明：

- 治理主线成立
- 恢复主线成立
- 读模型主线成立

但还不能证明：

- 真实 InVEST provider 接入成立
- 真实输入数据与参数装配成立
- 真实结果抽取与 artifact promotion 成立
- 真实 runtime 在 cancel / cleanup / archive 条件下仍与治理主线兼容

本计划要填补的，就是这个“真实调用证据缺口”。

---

## 4. 为什么后续要先做真实案例，而不是直接做完整 Phase3

先做真实案例，不是为了做一条 demo，而是为了证明当前“智能治理 + skill 驱动 + 异步执行 + 结果提取”架构已经能够承载真实 InVEST。

如果在真实 InVEST 案例尚未跑通前直接进入完整 `Phase3`，会把以下核心不确定性一起放大：

- 真实 provider 能否稳定接入
- 真实输入数据与参数装配能否闭环
- 真实输出能否进入 `result / artifact / explanation` 主链
- 真实 runtime 的 cancel / archive / promotion 是否仍与治理主链兼容

因此，当前最需要验证的不是“系统还能扩多少广度”，而是：

- 当前基线是否已经具备承载**至少一个真实 InVEST skill** 的核心条件

本计划完成后，能够证明的上限应明确写成：

> 当前架构至少可以承载一个真实 `water_yield` skill 的端到端调用，并为后续多模型、多 skills 扩展提供工程证据。

本计划不证明：

- skill schema 已经一般化完成
- provider abstraction 已经足够支撑全部模型
- 多 skill orchestration 已经可直接铺开

---

## 5. 后续做真实案例整体需要什么

### 5.1 真实数据基线

本计划默认采用**外部样例集**，不把大体量样例数据提交到仓库。

必须明确：

- 样例集来源
- 目录结构
- 数据准备步骤
- 最小必需文件集合
- 样例集版本标识

建议样例目录约定为：

- `sample-data/water_yield/<case_id>/...`

### 5.2 真实 InVEST provider

本计划默认：

- 不替换当前 deterministic runtime
- 新增独立真实 provider / image
- 保留当前 runtime 作为治理回归基线

### 5.3 输入与参数装配

后续需要从当前 index/value 型 demo 参数，升级为真实 InVEST 输入契约，包括：

- raster 输入
- vector 输入
- table 输入
- workspace / output 目录
- 模型必要参数

同时保持以下主链不变：

- skill
- slot
- `args_draft`
- `runtime_assertions`

### 5.4 结果提取与工件治理

真实 InVEST 输出必须进入现有主链，而不是旁路保存：

- `result_bundle`
- `artifact_catalog`
- `workspace_summary`
- `final_explanation`

### 5.5 验收链

后续需要新增一条**真实案例 E2E**，证明真实 InVEST 路径与现有治理链兼容，而不是只证明“脚本能跑”。

---

## 6. 真实 `water_yield` case contract（V1）

这一节是本计划的硬约束核心。后续所有实现、脚本和验收必须以本节 contract 为准，不允许由 runtime 开发者自行拍脑袋重定义。

### 6.1 Contract 目标

本 contract 用于定义：

- 真实案例的最小输入集合
- 参数来源与默认策略
- 输出分类与 artifact 提升规则
- runtime provider 允许消费的 I/O 形态

### 6.2 输入文件 contract

| 逻辑输入 | 必填 | 文件类型 | 基线约束 | 来源 |
|---|---|---|---|---|
| `watersheds` | 是 | vector | 必须为可读矢量数据；需包含稳定 watershed 标识字段；必须与主 raster 使用同一投影坐标系 | 外部样例集或用户上传 |
| `lulc` | 是 | raster | GeoTIFF 或等价可读栅格；必须与主 raster 基线对齐 | 外部样例集或用户上传 |
| `biophysical_table` | 是 | table | CSV；必须能映射 `lulc` 分类值 | 外部样例集或用户上传 |
| `precipitation` | 是 | raster | GeoTIFF 或等价可读栅格；必须有可识别 NoData；必须与主 raster 基线对齐 | 外部样例集或用户上传 |
| `eto` | 是 | raster | GeoTIFF 或等价可读栅格；必须有可识别 NoData；必须与主 raster 基线对齐 | 外部样例集或用户上传 |
| `depth_to_root_restricting_layer` | 否 | raster | 若提供，必须与主 raster 基线对齐 | 外部样例集或用户上传 |
| `plant_available_water_content` | 否 | raster | 若提供，必须与主 raster 基线对齐 | 外部样例集或用户上传 |

### 6.3 参数 contract

| 参数 | 必填 | 默认来源 | 说明 |
|---|---|---|---|
| `analysis_template` | 是 | skill stable defaults | 固定为 `water_yield_v1` |
| `workspace_dir` | 是 | runtime / control layer | 由控制层和 runtime 生成，不允许用户指定 |
| `results_suffix` | 是 | control layer | 由 attempt / job 生成，不允许 provider 自行写死 |
| `seasonality_constant_z` | 是 | case profile | 本次真实案例固定写入 case profile，不作为首轮用户交互项 |
| `provider_key` | 是 | control layer | 用于选择真实 provider，不允许 frontend 推断 |
| `runtime_profile` | 是 | planning / registry | 用于选择固定运行镜像和资源画像 |

### 6.4 当前 skill 定义与真实 contract 的差异

当前 `water_yield` skill 已经具备：

- `watersheds`
- `lulc`
- `biophysical_table`
- `precipitation`
- `eto`

等 slot 事实，但当前 required role 和 arg 映射仍偏 demo 形态。

因此，真实案例 V1 必须把 `water_yield` 的 contract 明确收紧为：

- 必填输入按本节表格执行
- `depth_to_root_restricting_layer` 与 `plant_available_water_content` 维持 optional
- demo 用的 index/value 型 fallback 不能冒充真实 provider 输入

### 6.5 输出与 artifact contract

物理文件名允许因 provider 实现不同而变化，但逻辑输出名必须固定。

| 逻辑输出 | artifact 角色 | 必须存在 | 说明 |
|---|---|---|---|
| `watershed_results` | `PRIMARY_OUTPUT` | 是 | 真实案例的主要汇总输出 |
| `water_yield_raster` | `PRIMARY_OUTPUT` | 否 | 若 provider 产出稳定 raster，则进入主输出 |
| `aet_raster` | `DERIVED_OUTPUT` | 否 | 若 provider 产出该结果，则作为派生输出 |
| `run_manifest` | `AUDIT_ARTIFACT` | 是 | 记录实际运行参数、输入引用、provider 信息 |
| `runtime_request` | `AUDIT_ARTIFACT` | 是 | 记录 provider 实际消费的 contract |
| `runtime.log` | `LOG` | 是 | 记录真实 provider 的标准输出与错误输出 |

### 6.6 runtime provider contract I/O 示例

真实 provider 不拥有业务裁决权，只负责按 contract 执行。

provider 必须消费的最小输入事实包括：

- `task_id`
- `job_id`
- `workspace_id`
- `attempt_no`
- `capability_key`
- `provider_key`
- `runtime_profile`
- `slot_bindings`
- `args_draft`
- `runtime_assertions`

provider 必须产出的最小输出事实包括：

- 真实输出文件集合
- 运行日志
- 可供 extractor 读取的 run manifest
- 标准失败摘要或成功摘要

provider 不允许自行决定：

- 缺什么输入
- 是否可 resume
- 是否可 promotion
- 是否算 repairable

这些仍然全部保留在现有 control/planning/governance 主链。

---

## 7. 真实 provider 风险清单

| 风险项 | 触发条件 | 影响 | fallback / diagnostic strategy |
|---|---|---|---|
| 依赖矩阵风险 | InVEST 与 Python / GDAL / rasterio / pygeoprocessing 版本不兼容 | 镜像无法构建或运行时崩溃 | 固定 provider image 版本；在镜像构建阶段单独跑依赖自检；保留 deterministic runtime 作为回归基线 |
| 容器构建风险 | 原生库缺失或 wheel 不可用 | 真实 provider 无法交付 | provider image 单独构建；不与当前 service image 混合；输出单独 build log |
| 文件挂载风险 | 外部样例目录未挂载、路径不一致、权限不够 | provider 读取不到真实输入 | 固定样例目录 contract；在 job 开始前写入 `run_manifest` 并校验所有物理路径存在 |
| 输入基线风险 | CRS、像元对齐、NoData、分类表不符合要求 | 模型运行失败或结果无效 | 前置 validation / runtime assertions 明确检查；失败必须回到既有失败治理主链 |
| 运行资源风险 | 临时目录、磁盘、内存或 CPU 不足 | 任务半途失败、遗留脏 workspace | 固定 runtime profile；记录 workspace / temp 占用；失败时保留可诊断审计工件 |
| 取消与清理风险 | 真正运行 InVEST 时 cancel 只停逻辑状态未停物理进程 | 残留进程、脏 workspace、假取消 | 保留物理取消路径；cancel 后记录进程终止和 cleanup 状态；若失败写入 failure summary |
| 输出解析风险 | provider 输出文件命名与 extractor 约定不一致 | result/artifact 主链断裂 | 以逻辑输出名 contract 为准；provider 必须写 `run_manifest`；extractor 只按 contract 解析，不按 case 特判 |
| provider 越层风险 | runtime 自己决定绑定、缺失输入、恢复资格 | 分层被破坏，治理失真 | 明文禁止 provider 拥有业务裁决权；任何缺失判断必须回 control/planning 主链 |

---

## 8. 目标

本计划的目标固定为：

- 用单个真实 `water_yield` 案例，证明当前系统已经可以完成“真实智能调用 InVEST”的端到端闭环

证明对象不是：

- 多 skill
- 多 provider
- 完整 `Phase3`

证明对象是：

- 自然语言触发
- skill 路由
- 真实输入绑定
- 真实 InVEST runtime
- 真实结果抽取
- authority facts 展示
- 治理链兼容

---

## 9. 范围

### 9.1 本轮要做什么

- 单 skill：`water_yield`
- 单真实案例
- 单真实 InVEST provider
- 单固定 Docker runtime
- 单套 canonical 外部样例数据
- 保持现有 backend / frontend 主路径兼容

### 9.2 本轮不做什么

- 不引入多 skill 编排
- 不同时做多 provider 竞争
- 不重开 `Phase2-B`
- 不做完整 `Phase3` 广度扩展
- 不把真实案例计划膨胀成通用平台重构

---

## 10. 最小改造点

### 10.1 控制面 / BackEnd

需要做的最小改造：

- 增加真实 provider 识别与路由配置，但不改变治理状态机主结构
- 允许 `water_yield` 真实案例的 args / attachment 被编译成真实 runtime 所需参数
- 保持 `WAITING_USER / resume / promotion / corruption` 语义不变
- 对真实案例需要展示的真实输入与真实输出引用，增加最小 DTO / manifest / result 扩展

必须额外补上的硬要求：

- 后端读模型中必须能看到：
  - `provider_key`
  - `runtime_profile`
  - `case_id`
  - 本次运行消费的关键输入引用
  - 输出 artifact 的逻辑分类

### 10.2 规划面 / Service-planning

需要做的最小改造：

- 将 `water_yield` 的 role / slot / args 映射从 demo 参数扩展为真实 InVEST 输入契约
- 继续由 Pass2 产出 `runtime_assertions`
- 将断言目标切换为真实输入文件与真实参数要求
- 增加 provider 选择到真实 runtime profile 的映射

必须额外补上的硬要求：

- `Pass1 / PassB / Pass2` 的产物必须真实参与 provider 参数装配
- 不允许 provider 侧通过写死 case profile 绕过 planning 产物

### 10.3 执行面 / Runtime Provider

需要做的最小改造：

- 新增独立真实 InVEST provider / image
- 在独立镜像内安装真实 InVEST 运行依赖
- 读取外部样例集目录，按固定 contract 挂载到 workspace
- 调用真实 InVEST `water_yield` 流程，而不是当前 inline deterministic code
- 输出真实结果文件，并回填当前：
  - `result_bundle`
  - `artifact_catalog`
  - `workspace_summary`
  - `final_explanation`

必须额外补上的硬要求：

- provider 只负责按 contract 执行，不拥有 case 级业务裁决权
- provider 必须把实际消费的输入、参数、镜像信息写入 `run_manifest`
- provider 失败时必须回到既有失败治理主链，不允许吞错

### 10.4 样例数据与运行环境

需要做的最小改造：

- 明确样例集目录约定
- 明确哪些数据由仓库外提供
- 明确 compose 或脚本层如何挂载样例目录到真实 provider 容器
- 明确真实 provider 所使用的固定镜像、固定依赖和固定运行 profile

### 10.5 前端

前端不新增业务推理，但必须强化真实证明能力。

前端最小交付要求：

- 明确显示本次运行使用的 `provider_key`
- 明确显示本次运行的 `runtime_profile`
- 明确显示本次运行的 `case_id`
- 明确显示关键输入绑定到了哪些真实文件
- 明确显示 primary / derived / audit / logs 的工件分类
- `WAITING_USER` 时明确显示缺失的是哪类真实输入，而不是泛化文案

### 10.6 脚本与验收

需要做的最小改造：

- 新增一条“真实案例 E2E”脚本
- 保留当前 deterministic E2E 作为治理回归基线
- 明确两套脚本职责分离：
  - deterministic：验证治理与状态机
  - real-case：验证真实 InVEST 路径

---

## 11. 验收标准与反作弊规则

### 11.1 基础验收标准

本计划完成后，必须全部满足以下条件：

- 用户一句自然语言可触发真实 `water_yield` 案例
- 系统实际调用真实 InVEST runtime，而不是 deterministic stub
- 输入不完整时，仍进入 `WAITING_USER`
- 用户补传后，仍能通过 `/resume` 恢复并成功完成
- 结果页能展示真实 output 对应的结构化结果与工件
- `artifact_catalog`、`workspace_summary`、`promotion_status` 与真实 runtime 兼容
- `cancel` 在真实 runtime 下仍可用
- 至少有一条稳定脚本可重复跑通整条真实案例链

### 11.2 反作弊验收

以下检查项必须显式纳入验收，防止“写死路径 / 写死参数 / case 特判”伪跑通：

- manifest 中的关键参数与 provider 实际消费参数一致
- `slot_bindings` 与 provider 实际消费的物理输入文件路径一致
- `provider_key / runtime_profile / case_id` 必须来自主链 authority facts，不允许 provider 自行伪造
- 切换不同 `case_id` 后，系统仍按同一 contract 运行，而不是只适配一个目录
- result extractor 必须按 contract 解析输出，不允许按单 case 特判
- provider 失败时必须进入既有失败治理主链

### 11.3 验收输出物

验收输出固定包括：

- 一条真实案例 E2E 通过记录
- 一份真实 case 输入说明
- 一份真实输出目录与 artifact 映射说明
- 一份结果页或读模型快照
- 一份 provider build / run / failure diagnostic 记录

---

## 12. 实施顺序

本计划的实施顺序固定为 6 步，不与完整 `Phase3` 混合。

### 第 1 步：定义真实案例 contract

先固定 `water_yield` 所需真实输入、参数、输出与样例目录约定。

本步产出：

- 输入文件清单
- 输出文件清单
- 样例集目录约定
- runtime profile 约定
- provider contract 约定

### 第 2 步：新增真实 InVEST provider

保留 deterministic runtime，不直接替换。

本步产出：

- 独立真实 provider image
- 真实依赖安装方式
- workspace 挂载方式
- 真实 runtime 调用入口

### 第 3 步：打通 planning 到真实 provider 的参数装配

让 `Pass1 / PassB / Pass2` 产物真正落到真实 runtime 输入。

本步重点：

- role / slot / args 到真实 InVEST 参数映射
- `runtime_assertions` 到真实输入断言映射
- `provider_key / runtime_profile` 到真实 provider 选择映射

### 第 4 步：打通真实 output 到现有 result / artifact 主链

本步重点：

- 真实输出文件收集
- artifact 分类
- result bundle 构造
- 解释输入整理
- promotion 主链兼容

### 第 5 步：增加真实案例 E2E

覆盖：

- create
- missing input
- upload
- resume
- success
- cancel

### 第 6 步：形成首个真实案例验收记录

用真实案例验收结果形成 go/no-go 判定，而不是只给出模糊结论。

---

## 13. Go / No-Go 判定

### 13.1 Go

满足以下条件，可进入更广扩展：

- 真实案例稳定通过
- 反作弊验收全部通过
- provider build / run / cancel / cleanup 均稳定
- 失败治理链与结果主链无越层现象

### 13.2 Conditional Go

满足以下条件，可条件性推进，但必须先清偿环境债务：

- 主链已跑通
- 反作弊验收大部分通过
- provider 或环境仍存在可定位、可控的工程债务
- 债务不涉及分层破坏与 authority 失真

### 13.3 No-Go

出现以下任一情况，应停止扩广度：

- 真实 provider 只能依靠写死路径 / 写死参数跑通
- `Pass1 / PassB / Pass2` 产物未真实进入 provider 执行
- result extractor 依赖 case 特判
- provider 拥有 case 级业务裁决权
- 失败治理链被绕开或 authority facts 失真

---

## 14. 模块交付件

### 14.1 BackEnd 交付件

- 真实 provider 路由与 registry 配置
- 真实案例所需 manifest / result / detail 最小扩展
- provider_key / runtime_profile / case_id 的 authority 投影
- 失败治理兼容验证

### 14.2 planning-pass1 交付件

- `water_yield` 真实 contract 映射
- 真实输入断言与 runtime_assertions 规则
- planning 产物到真实 provider 参数装配逻辑

### 14.3 runtime provider 交付件

- 独立真实 InVEST provider image
- 真实运行入口
- `run_manifest` 写出
- 输出收集与 artifact 映射
- failure / cancel / cleanup diagnostic

### 14.4 前端交付件

- provider_key / runtime_profile / case_id 展示
- 关键输入引用展示
- 真实 artifact 分类展示
- `WAITING_USER` 真实缺失输入展示

### 14.5 脚本 / E2E 交付件

- 真实案例 E2E 脚本
- deterministic 回归脚本保持可用
- 真实案例验收记录模板

### 14.6 文档交付件

- 本计划文档
- 样例输入说明
- 输出与 artifact 映射说明
- 首个真实案例验收记录

---

## 15. 假设与默认决策

- 文档作为新计划立项，不修改 `Phase2-B` 关闭 / 冻结结论
- 范围锁定为“单个真实 `water_yield` 案例先通”
- 真实样例数据采用外部样例集，不入仓库
- 真实 InVEST 采用“新增独立 provider / image”方式接入
- 当前 deterministic runtime 保留，作为治理回归与开发基线
- 只有这份计划完成后，下一步才有资格讨论完整 `Phase3`

---

## 16. 一句话结论

当前系统已经足以证明“治理主线成立”，但还不足以证明“真实 InVEST 调用成立”。

因此，下一步最合理的工作不是直接扩到完整 `Phase3`，而是先用一个真实 `water_yield` 案例，把当前系统从“治理闭环已完成”推进到“真实智能调用已被证明”，并以强 contract、强验收和 go/no-go 结论决定是否继续扩大投资。
