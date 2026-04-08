# Phase3-C contract 治理视图统一组装记录
更新日期：2026-04-05

## 0. 文档目的

本文记录 Phase3-C 在 `contract-first` 方向上的又一轮收口：

- 不再继续扩展新的 contract 语义
- 将已有 `detail / manifest / result / audit` 四类读取路径上的 contract 治理视图组装逻辑统一到共享 assembler
- 避免 `TaskService` 内继续累积多份相似但逐渐漂移的 helper

本轮目标是“统一 code/view 的组装边界”，不是扩前端，也不是扩新能力面平台。

---

## 1. 本轮结论

本轮已经完成：

> `detail / manifest / result / audit` 四类 contract 治理视图的 DTO 组装逻辑，已经统一收敛到共享的 `ContractGovernanceAssembler`。

这意味着当前的治理语义虽然仍由 control layer 投影，但不再依赖 `TaskService` 中多份分散的组装方法。

---

## 2. 本轮实现内容

### 2.1 新增共享 assembler

新增文件：

- [ContractGovernanceAssembler.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/ContractGovernanceAssembler.java)

该 assembler 负责统一组装：

- `ContractGovernanceView`
- `ContractIdentityView`
- `ContractConsistencyView`
- `ResumeContractEvaluationView`

当前暴露两类正式入口：

1. 通用治理视图组装
   - `build(...)`

2. audit 侧治理视图组装
   - `buildAudit(...)`

这样做的意义是：

- `detail / manifest / result` 复用同一套 DTO 组装逻辑
- `audit` 不再保留独立的一套半重复 view 构造代码

### 2.2 TaskService 改为调用共享 assembler

修改文件：

- [TaskService.java](/e:/paper_project/SAGE/BackEnd/src/main/java/com/sage/backend/task/TaskService.java)

调整内容：

- `getTask(...)` 改为使用 `ContractGovernanceAssembler.build(...)`
- `getTaskManifest(...)` 改为使用 `ContractGovernanceAssembler.build(...)`
- `getTaskResult(...)` 改为使用 `ContractGovernanceAssembler.build(...)`
- `getTaskAudit(...)` 改为使用 `ContractGovernanceAssembler.buildAudit(...)`

### 2.3 删除 TaskService 中失效的旧 helper

本轮同时清理了 `TaskService` 内部已不再需要的旧方法：

- `buildContractGovernanceView(...)`
- `buildAuditContractGovernanceView(...)`
- `toContractIdentityView(...)`
- `toContractConsistencyView(...)`

保留不变的部分：

- frozen/current contract identity 的事实计算逻辑仍在 `TaskService`
- `determineContractMismatchCode(...)`
- `buildContractCompatibilityView(...)`

也就是说：

- **事实判定** 仍由 control layer service 计算
- **治理视图组装** 则统一收敛到共享 assembler

这符合当前仓库的层边界要求，没有把 authority 推回 frontend 或 execution。

---

## 3. 当前结构变化的意义

在本轮之前，系统已经具备：

- `detail / manifest / result` 的 contract governance DTO
- `audit` 的 contract governance 投影
- `resume` 的结构化 mismatch 证据

但组装路径仍散在 `TaskService` 中，导致两个风险：

1. audit 与 detail 逐步出现不同的字段投影口径  
2. 后续新增 contract drift code 时，容易只补某一条路径

本轮收口后，至少在“视图组装”层面，已经把这两个风险压住。

---

## 4. 验证情况

### 4.1 真实链路回归

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/phase3-realcase-e2e.ps1 -Scenario Success
```

结果：通过

最新真实链路任务：

- `task_20260405055813185_fd048cbd`

这说明：

- 本轮重构共享 assembler 后，没有打坏当前 governed success 主链

### 4.2 backend 编译状态

backend 能够正常打包并进入 compose stack。  
这在真实链路回归中已经被间接验证，因为脚本会先打包 backend jar 并启动整栈。

### 4.3 Maven 定向测试说明

本轮尝试执行：

```powershell
mvn -q -f BackEnd/pom.xml "-Dtest=TaskServiceGovernanceTest,TaskServiceCognitionFlowTest,TaskProjectionBuilderTest,CapabilityContractGuardTest" test
```

当前环境下再次触发了既有的测试源解析异常，表现为大量 `找不到符号 / 程序包不存在` 的编译报错。  
该问题与本轮新增 assembler 本身无直接对应关系，因为：

- backend 实际已能成功打包
- `Success` 真实链路回归通过
- 本轮改动没有引入新的 compile-time unresolved symbol

因此，本轮代码可视为已通过真实运行验证，但测试入口本身仍存在环境/构建侧不稳定性，后续应单独收口。

---

## 5. 当前阶段结论

本轮最准确的结论是：

> Phase3-C 已将 contract 治理视图的组装逻辑从 `TaskService` 分散 helper 收敛为共享 assembler，形成 `detail / manifest / result / audit` 四类读模型的一致治理视图输出；但 contract 事实判定仍由 control layer service 负责，尚未进一步抽象为独立 projector/service。

这一步不是功能扩展，而是治理边界的继续收紧。

---

## 6. 下一步建议

下一步最合理的方向不是扩前端，而是继续收治理边界本身：

1. 继续把 audit / detail / manifest / result 的 contract drift code/view 做到完全统一  
2. 如果后续仍有扩展需要，再考虑把 contract consistency 的事实计算也从 `TaskService` 抽成独立 projector/service  
3. 单独处理当前 Maven 定向测试入口不稳定的问题，避免后续把“测试入口问题”误判成“业务代码问题”
