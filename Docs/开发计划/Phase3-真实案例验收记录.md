# Phase3 真实案例验收记录

记录日期：2026-03-31

## 1. 验收对象

本次验收针对当前工作树中的单一真实案例闭环收口，范围限定为：

- 单 skill：`water_yield`
- 单 case：`annual_water_yield_gura`
- 单真实 provider：`planning-pass1-invest-local`
- 单真实 runtime profile：`docker-invest-real`

## 2. 本次验收使用的入口

代码与脚本入口：

- [scripts/phase3-realcase-e2e.ps1](/e:/paper_project/SAGE/scripts/phase3-realcase-e2e.ps1)
- [scripts/phase1-gate.ps1](/e:/paper_project/SAGE/scripts/phase1-gate.ps1)
- [Service/planning-pass1/Dockerfile.invest-real](/e:/paper_project/SAGE/Service/planning-pass1/Dockerfile.invest-real)

本地验证命令：

```powershell
python -m compileall Service/planning-pass1/app
mvn -q -DskipTests package
npm run build
powershell -ExecutionPolicy Bypass -File .\scripts\phase3-realcase-e2e.ps1 -Scenario All
```

## 3. 当前验收结果

### 3.1 已通过

- `python -m compileall Service/planning-pass1/app`
  - 结果：通过
- `mvn -q -DskipTests package`
  - 结果：通过
- `npm run build`
  - 结果：通过

### 3.2 被环境阻断

真实案例 E2E 执行时间：

- 2026-03-31

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase3-realcase-e2e.ps1 -Scenario All
```

实际结果：

- compose 启动流程进入执行
- 后续被本机 Docker daemon 不可用阻断

阻断信息摘要：

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
The system cannot find the file specified.
service health did not become ready
```

结论：

- 当前代码、脚本、文档资产已经落地到可执行状态
- 但“真实 provider 实际运行成功”本次尚未在本机完成最终验证

## 4. 场景矩阵

| 场景 | 目标 | 当前状态 | 说明 |
|---|---|---|---|
| real-case success | create -> SUCCEEDED -> result/artifact/explanation 完整 | 脚本已实现，待 Docker 环境恢复后重跑 | `phase3-realcase-e2e.ps1 -Scenario Success` |
| real-case repair/resume | missing input -> WAITING_USER -> upload -> /resume -> SUCCEEDED | 脚本已实现，待 Docker 环境恢复后重跑 | `phase3-realcase-e2e.ps1 -Scenario RepairResume` |
| real-case cancel | RUNNING -> cancel -> CANCELLED/FAILED with logs/artifacts | 脚本已实现，待 Docker 环境恢复后重跑 | `phase3-realcase-e2e.ps1 -Scenario Cancel` |

## 5. 当前可确认的工程收口项

当前工作树已经完成的收口项：

- `water_yield` capability contract 已前移到真实输入角色
- 真实 provider 独立 Dockerfile 已落地
- real-case 脚本已覆盖 `success / repair-resume / cancel`
- unified gate 已接入 real-case 模块
- 输入说明与 artifact 映射说明已形成独立文档

## 6. 仍待最终验证的项目

以下项目必须在 Docker daemon 可用后重新执行并回填结果：

- 真实 provider image 构建是否稳定
- success 场景是否稳定 `SUCCEEDED`
- repair/resume 场景是否稳定回到 `SUCCEEDED`
- cancel 场景是否稳定进入非成功终态且保留日志/工件
- `run_manifest / runtime_request / docker_runtime_evidence` 反作弊对照是否全部通过

## 7. 本记录结论

当前验收记录状态为：

- `部分完成，待环境恢复后补全真实运行证据`

它不是最终 `Go` 记录，但已经足以作为本阶段代码与文档收口的中间验收基线。
