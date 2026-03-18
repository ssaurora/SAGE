# 第 2 周：建输入链

## 本周目标

让系统围绕 Pass 1 的角色和槽位，真实地产生绑定和参数草案，而不是人工拼参数。

---

## 前端团队

本周任务：

- 在详情页增加：
    
    - `slot_bindings` 摘要区
        
    - `args_draft` 摘要区
        
    - 验证结果摘要区
        
- 优化状态展示，把 `COGNIZING / PLANNING / VALIDATING` 区分开
    

交付物：

- 一个能展示“当前绑定内容”和“参数草案”的任务详情页
    

技术栈：

- React / Next.js
    
- TypeScript
    

---

## Java / Spring Boot 团队

本周任务：

- 串起控制面阶段编排：
    
    - Planning Pass 1
        
    - Cognition Pass B
        
    - Validation Gate
        
- 统一 Validation Gate 放行权
    
- 保存 `slot_bindings` 和 `args_draft` 摘要
    
- 失败时进入 `WAITING_USER` 或 `FAILED` 的最小逻辑
    

交付物：

- 控制面阶段编排服务
    
- Validation Gate 雏形
    

技术栈：

- Spring Boot
    
- PostgreSQL
    

---

## Python 团队

本周任务：

- 实现 Cognition Pass B
    
- 基于 `logical_input_roles` 生成：
    
    - `slot_bindings`
        
    - `args_draft`
        
    - `decision_summary`
        
- 提供基础 validate primitive
    
- 输出结构化 schema，而不是自由文本
    

交付物：

- Pass B 服务
    
- 绑定与参数草案 schema
    

技术栈：

- Python
    
- LLM 调用层
    
- Pydantic
    

---

## 数据库与存储团队

本周任务：

- 为 `task_state` 增补阶段摘要字段或关联表
    
- 设计 `slot_bindings` / `args_draft` 摘要存储方案
    
- 确定状态推进与事件追加的数据库事务边界
    

交付物：

- 绑定与草案摘要存储设计
    
- 控制面状态推进事务规则
    

技术栈：

- PostgreSQL
    
- SQL migration
    

---

## 第 2 周联合验收标准

周末必须看到：

1. Pass 1 输出被真实送入 Pass B
    
2. 系统能生成结构化 `slot_bindings`
    
3. 系统能生成结构化 `args_draft`
    
4. Validation Gate 在控制面统一执行
    
5. 前端可以看到绑定摘要、参数草案和验证摘要
    
6. 失败不只是异常堆栈，而是结构化失败说明
    

---
