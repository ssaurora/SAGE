# 第 5 周：做修复闭环

## 本周目标

让系统具备“知道为什么没跑通，并告诉用户怎么补”的能力。

---

## 前端团队

本周任务：

- 做 `WAITING_USER` 补数面板
    
- 展示：
    
    - `missing_slots`
        
    - `invalid_bindings`
        
    - `required_user_actions`
        
    - `resume_hint`
        
- 增加补传入口
    
- 增加 `/resume` 按钮
    

交付物：

- 结构化补数页面
    

技术栈：

- React / Next.js
    
- TypeScript
    
- 文件上传组件
    

---

## Java / Spring Boot 团队

本周任务：

- 实现 `WAITING_USER` 结构化对象
    
- 实现 Repair Dispatcher
    
- 实现 `/resume`
    
- 区分：
    
    - 参数/绑定错误
        
    - 图结构错误
        
    - 缺数
        
    - 系统等待
        
    - 致命失败
        

交付物：

- 等待态对象
    
- 修复分流逻辑
    
- 恢复入口
    

技术栈：

- Spring Boot
    
- PostgreSQL
    

---

## Python 团队

本周任务：

- 认知面产出 repair proposal / waiting reason
    
- 规划面返回图结构类错误摘要
    
- 配合 `/resume` 做最小重入逻辑
    

交付物：

- repair proposal 生成逻辑
    
- 图结构错误摘要逻辑
    

技术栈：

- Python
    
- LLM 调用层
    
- Pydantic
    

---

## 数据库与存储团队

本周任务：

- 设计等待态信息持久化结构
    
- 设计补传文件与任务的关联规则
    
- 确定 `/resume` 前的最小一致性检查数据项
    

交付物：

- 等待态存储方案
    
- 补传对象关联方案
    

技术栈：

- PostgreSQL
    
- 对象存储
    

---

## 第 5 周联合验收标准

周末必须看到：

1. 系统能区分“缺数”和“数据不兼容”
    
2. 前端能结构化展示缺口，而不是一行报错
    
3. 用户知道要补什么
    
4. 用户补传后能调用 `/resume`
    
5. 常见失败被分流，不再全都进入统一失败页
    
6. **Phase 1 的修复闭环基础成立**
    

---
