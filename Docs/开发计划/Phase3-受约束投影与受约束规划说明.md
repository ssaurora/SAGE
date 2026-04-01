# Phase3-受约束投影与受约束规划说明

更新日期：2026-04-01

## 0. 文档时态说明

本文描述的是 `2026-04-01` 时点的**当前能力定义**，不是对更早阶段文档的否定。

与 [真实案例-智能调用过程说明.md](/e:/paper_project/SAGE/Docs/架构设计/真实案例-智能调用过程说明.md) 的关系是：

- 前者记录的是 `2026-03-28` 左右“首次真实成功案例”的历史事实
- 本文定义的是 `2026-04-01` 左右“当前系统能力应如何表述”的阶段判断

二者不是逻辑冲突，而是对应**不同时间切片**：

- 历史切片：deterministic canonical-case routing 为主，真实执行闭环先成立
- 当前切片：系统进入 `LLM-assisted governed case projection` 阶段，但仍未进入开放域自主规划

因此，阅读本文时应采用如下统一口径：

> 当前系统不是“纯 deterministic 直连”，也不是“开放式自主规划”，而是正处在从 deterministic canonical-case routing 向 LLM-assisted governed case projection 过渡的阶段。

## 1. 文档目的

本文用于解释当前真实 InVEST `water_yield` 案例中的一个关键判断：

> 当前系统已经不是“纯规则直连”，但也还不是“开放式自主规划”。

更准确地说，当前系统处于：

> `LLM -> governed case contract projection -> real execution`

这一阶段。

这份文档要回答四个问题：

- 当前实现到底属于什么能力形态
- 为什么它现在表现为“受约束、偏投影式”
- 这是不是一个问题
- 后续如何从“受约束投影”演进到“受约束规划”

---

## 2. 当前实现的准确定义

### 2.1 它已经不再是纯规则链

当前真实案例链中，至少以下环节已经由 LLM 参与：

- `goal-route`
- `passb`
- `repair proposal`
- `final explanation`

在真实 `gura` 案例成功任务中，`goal-route` 和 `passb` 已经显示：

- `provider = glm`
- `model = glm-4.7`
- `fallback_used = false`

这说明系统已经具备“自然语言入口上的认知参与”，而不是完全依赖后端硬编码规则直接跳到运行时。

### 2.2 但它还不是开放式自主规划

当前真实案例链路更接近下面这个模式：

1. 用户用自然语言提出请求
2. LLM 识别意图和案例语义
3. 系统把结果投影到一个已经受治理的 `case contract`
4. 控制层完成验证、冻结、promotion
5. 执行层在真实 InVEST runtime 中运行

这意味着当前 LLM 的自由度主要在：

- 意图识别
- 案例匹配
- 参数语义映射
- 用户可读解释

它的自由度不在：

- 任意生成新的执行图
- 任意改变 workflow authority
- 任意跳过 Validation / Pass2 / Dispatcher
- 任意构造新的 provider/runtime 组合

所以当前阶段应定义为：

> 受治理的自然语言驱动真实案例执行

而不是：

> 开放域自主规划系统

### 2.3 当前阶段最稳妥的正式表述

若要用于阶段总结、验收结论、对外汇报或论文式描述，推荐统一采用下面这句：

> 当前系统已经证明，自然语言请求可以被映射到受治理的真实 InVEST 执行 contract，并稳定支撑真实执行与审计闭环；但当前成立的能力形态仍是 governed case-contract projection，而不是开放域自主规划，下一阶段扩展的重点应是 contract 内的候选规划自由度，而非 workflow authority。

---

## 3. 为什么当前会表现为“受约束投影”

### 3.1 因为当前目标不是追求最大自由度，而是先确保真实执行闭环成立

当前阶段最重要的，不是让模型“尽可能自由”，而是先确保下面这些东西成立：

- 真实 provider 可执行
- 真实输入 contract 可装配
- Validation 仍有效
- Pass2 图仍可冻结、可审计、可追踪
- artifact promotion 仍可归档
- cancel / resume / failure governance 仍不失控

如果在这一阶段就允许 LLM 进行开放式执行规划，最先破坏的通常不是“聪明程度”，而是：

- 输入绑定正确性
- 运行稳定性
- 错误可解释性
- 结果可信度
- authority 边界

### 3.2 因为当前系统仍坚持 control-layer authority

当前仓库的分层边界明确规定：

- `WAITING_USER / can_resume / required_user_actions` 由 control layer 决定
- `RECOVERABLE / FATAL` 由 Dispatcher 决定
- `task_state` 不由 LLM 决定
- LLM 不直接提交 job

这意味着 LLM 在当前阶段天然更适合做：

- route
- semantic binding
- proposal
- explanation

而不适合做：

- workflow authority
- execution authority
- uncontrolled graph synthesis

从架构上看，这不是限制过度，而是必要约束。

---

## 4. 当前阶段的价值判断

## 4.1 这不是缺陷，而是成熟度位置

“受约束投影”本身不是失败，而是一个合理阶段。

它至少证明了下面几件重要的事：

- LLM 已经进入主链，而不是外围装饰
- 自然语言请求可以被映射到真实 InVEST 案例
- 真实执行不是 demo，而是受治理的生产式闭环
- 认知层和执行层之间已经建立 contract

如果连这一层都没站稳，就直接追求开放式规划，系统很容易变成：

- 看起来很智能
- 但结果不可控
- 失败不可复现
- 审计无法成立

### 4.2 但也不能夸大成“开放式智能规划”

当前系统还不能声称自己已经具备以下能力：

- 面向未知案例自由合成真实执行方案
- 在多个真实 case 之间进行稳定选择和适配
- 在多个 capability / skill 之间进行开放式规划
- 在 contract 未定义区域内自主构造新执行链

因此，对外或对阶段能力的准确表述应是：

> 已实现受治理的真实案例智能路由与真实执行闭环

而不是：

> 已实现开放式自主规划

---

## 5. 当前阶段到底在哪一层

可以把系统能力粗分为三层：

### 第一层：纯规则直连

特点：

- 用户请求通过关键词或硬编码映射到固定分析
- 几乎没有语义理解
- 没有 LLM 主路径

### 第二层：受约束投影

特点：

- LLM 参与意图识别和语义映射
- 但输出必须投影到既有 contract / case / schema
- execution 和 authority 仍被严格治理

当前系统属于这一层。

### 第三层：受约束规划

特点：

- LLM 可以在“合法 contract 空间”内做候选规划
- 不局限于单一 canonical case
- 可以在多个真实 case / 配置 / 模板中受限选择
- 但 authority 仍然保留在 control layer

后续演进目标应该是第三层，而不是直接跳到无约束自由生成。

---

## 6. 什么叫“从受约束投影升级到受约束规划”

升级的关键，不是把 LLM 放得更自由，而是：

> 让 LLM 在 contract 空间内部获得更多选择权，而不是越过 authority 边界。

### 6.1 可以扩展的自由度

下一阶段可以逐步开放给 LLM 的能力包括：

- 在多个已登记真实 case 中做选择，而不是只匹配 `annual_water_yield_gura`
- 在多个合法输入配置之间做受限推断
- 在多个 template / runtime profile 中输出候选方案
- 对 case 不确定时生成 clarify 建议，而不是强行投影
- 生成 candidate plan，再由 control layer 验证和裁决

### 6.2 不能开放的自由度

以下边界在后续阶段也不应交给 LLM：

- `WAITING_USER` vs `FAILED`
- `can_resume`
- `required_user_actions`
- Validation 是否通过
- Pass2 前提是否满足
- 是否允许提交 job
- 真实状态真相写入

也就是说，后续演进应是：

> 扩展 LLM 的候选规划能力

而不是：

> 把 workflow authority 交给 LLM

---

## 7. 建议的演进路线

### 阶段 A：单 case 受约束投影稳定化

目标：

- 让 canonical case 路径稳定
- 让 `goal-route / passb / explanation` 对同一类 query 可重复
- 让真实执行闭环可靠通过验收

当前系统基本已经进入这个阶段的后段。

### 阶段 B：多 case 受约束投影

目标：

- registry 中登记多个真实 `water_yield` case
- LLM 在已知 case 集合内做匹配
- control layer 对 case 选择进行校验

这个阶段的核心不是“更自由”，而是“从单 case 投影扩展到多 case 投影”。

### 阶段 C：受约束 candidate planning

目标：

- LLM 输出多个候选 binding / template / case 方案
- backend 进行排序、校验、淘汰
- 若都不满足则进入 clarify 或 waiting

这一阶段才是真正意义上的“从投影走向规划”。

### 阶段 D：跨 capability 的受约束规划

目标：

- 不再只限于单一 `water_yield`
- 在多个 capability 中进行受治理 route
- 但仍坚持 authority 不下放

这个阶段不应在真实案例单链尚未完全稳定前推进。

---

## 8. 当前阶段的结论

当前系统的真实状态可以定义为：

> 执行闭环已成立，认知入口已成立，但能力形态仍属于受约束投影，而非开放式自主规划。

这是一个合理且健康的阶段位置。

它说明系统已经完成了最危险的一步：

- 不再是 demo 级“看起来智能”
- 而是把认知、治理、真实执行、工件归档接成了同一条链

同时它也提醒我们：

- 不能把当前成果误称为开放式智能规划
- 后续应该沿着“contract 内自由度扩展”的方向演进
- 不应该为了追求“更像智能体”而破坏现有 authority 边界

---

## 9. 推荐表述

在阶段总结、验收说明或论文式描述中，建议使用下面的表达：

### 推荐表达

当前系统已经实现：

- 受治理的自然语言驱动真实案例执行
- LLM 参与的意图识别、案例匹配与参数投影
- 基于真实 InVEST runtime 的端到端闭环

### 不建议表达

当前系统已经实现：

- 开放式自主规划
- 任意问题到任意分析链的自动合成
- 由 LLM 主导的 workflow authority

---

## 10. 后续文档衔接

本文建议与以下文档配套阅读：

- [Phase3-前置：真实InVEST案例验证计划.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-前置：真实InVEST案例验证计划.md)
- [Phase3-真实案例验收记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase3-真实案例验收记录.md)
- [真实案例-智能调用过程说明.md](/e:/paper_project/SAGE/Docs/架构设计/真实案例-智能调用过程说明.md)
- [认知面设计方案-latest.md](/e:/paper_project/SAGE/Docs/架构设计/认知面设计方案-latest.md)
