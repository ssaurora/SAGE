# Phase2-B 冻结基线说明

更新日期：2026-03-27

## 1. 文档目的

本文件用于明确：

- `Phase2-B` 在当前工作树上的正式冻结边界
- 哪些能力已被视为冻结基线的一部分
- 哪些问题属于已知债务，但不影响 `Phase2-B` 关闭结论

本文件与 [Phase2-B-最终验收记录.md](/e:/paper_project/SAGE/Docs/开发计划/Phase2-B-最终验收记录.md) 配套使用。

---

## 2. 冻结结论

自本文件起，当前工作树上的 `Phase2-B` 视为：

- 已关闭
- 已冻结
- 不再继续以 `Phase2-B` 名义追加零散治理开发

后续如需继续推进治理增强，必须以新的阶段文档、任务单或正式开发计划重新启动。

---

## 3. 冻结基线包含内容

当前冻结基线明确包含以下能力：

### 3.1 控制面治理

- `task_state` 治理字段：
  - `planning_revision`
  - `checkpoint_version`
  - `inventory_version`
  - `resume_txn_json`
  - `corruption_reason`
  - `corrupted_since`
- 新状态：
  - `RESUMING`
  - `ARTIFACT_PROMOTING`
  - `STATE_CORRUPTED`
- `/resume` 最小事务语义：
  - `PREPARING`
  - `ACKED`
  - `COMMITTED`
  - `ROLLED_BACK`
  - `CORRUPTED`
- 严格 `MIN_READY`
- `STATE_CORRUPTED` 下 upload/resume 拒绝
- 管理员 `force-revert-checkpoint`

### 3.2 Manifest 与版本治理

- `analysis_manifest` 治理字段扩展
- candidate manifest 与 freeze-on-commit 分离
- `active_manifest` authority 指针只在 commit 更新
- checkpoint / planning revision / manifest version 语义分离

### 3.3 规划编译治理

- `graph_digest`
- `planning_summary`
- `canonicalization_summary`
- `rewrite_summary`
- `runtime_assertions`

### 3.4 失败治理与成功收口

- repairable assertion failure -> `WAITING_USER`
- fatal assertion failure -> `FAILED`
- `RUNNING -> ARTIFACT_PROMOTING -> SUCCEEDED`
- promotion failure -> `STATE_CORRUPTED`

### 3.5 读模型与前端边界

- backend authority facts 已投影到 detail / manifest / result
- frontend 只渲染 authority facts
- 管理员在 `STATE_CORRUPTED` 下可执行 checkpoint 回退

---

## 4. 冻结基线不包含内容

以下事项不属于本次冻结基线承诺范围：

- 新一轮治理增强需求
- 多 skill 编排扩展
- provider 扩散或新的能力生态扩展
- 新的前端业务推理
- 额外的 Phase3/后续阶段路线承诺

---

## 5. 已知但不阻塞的债务

以下问题已知存在，但不影响本次冻结结论：

- 本机默认 Python 环境缺少 `pytest`
- 本机默认 Python 环境缺少 `fastapi`
- Docker cleanup 阶段偶发 daemon 连接问题

这些问题属于环境与工程债务，不属于 `Phase2-B` 主链功能回退。

---

## 6. 变更规则

在新的阶段计划形成之前，针对当前冻结基线应遵守以下规则：

1. 允许修复明确 bug、回归和验收不一致问题
2. 不允许继续向 `Phase2-B` 主线追加新治理能力
3. 若需求明显超出 bugfix 范围，应先形成新的开发计划，再进入实现

---

## 7. 一句话结论

`Phase2-B` 的治理主线到此冻结；后续如继续推进，必须作为新阶段重新立项。
