# 第 6 周：做稳定性与可追溯

## 本周目标

补齐 workspace、结果、工件和运行态协调，让系统具备可重复使用的稳定性。

---

## 前端团队

本周任务：

- 做工件页
    
- 展示：
    
    - `primary_outputs`
        
    - `intermediate_outputs`
        
    - `logs`
        
    - `audit_artifacts`
        
- 展示恢复后的运行历史摘要
    

交付物：

- 工件视图页
    
- 运行历史视图
    

技术栈：

- React / Next.js
    
- TypeScript
    

---

## Java / Spring Boot 团队

本周任务：

- 接入 Capability / Provider Registry 最小版
    
- 查询 registry 并路由能力调用
    
- 接入 Redis 运行态协调
    
- 统一结果与工件查询接口
    

交付物：

- registry 路由雏形
    
- 运行态协调接入
    
- 工件查询 API
    

技术栈：

- Spring Boot
    
- PostgreSQL
    
- Redis
    

---

## Python 团队

本周任务：

- 实现 workspace 生命周期：
    
    - create
        
    - cleanup
        
    - archive
        
    - demolish
        
- 完善 `result_bundle`
    
- 生成工件索引信息
    
- 对接 Redis heartbeat / cancel token
    

交付物：

- workspace 生命周期逻辑
    
- 完整一点的 result bundle
    
- artifact 元信息输出
    

技术栈：

- Python Worker
    
- Docker
    
- Redis
    
- 对象存储
    

---

## 数据库与存储团队

本周任务：

- 建：
    
    - `workspace_registry`
        
    - `result_bundle_record`
        
    - `artifact_index`
        
    - `capability_registry`
        
    - `provider_registry`
        
- 设计工件索引和 workspace 追溯关系
    
- 约束 Redis 只做运行态协调，不做主事实源
    

交付物：

- 第二阶段核心表
    
- 索引与约束
    
- Redis 使用边界说明
    

技术栈：

- PostgreSQL
    
- Redis
    
- 对象存储
    

---

## 第 6 周联合验收标准

周末必须看到：

1. 任一 workspace 都能追溯到 task/job/run revision
    
2. 运行结束后临时目录可清理
    
3. `result_bundle` 有稳定记录，不只是临时内存对象
    
4. 工件能通过 `artifact_index` 被追踪
    
5. Redis 只承担 heartbeat / lease / cancel token，不承担主事实
    
6. registry 开始接管能力调用路由，减少硬编码
    
7. **Phase 1 到本周结束正式完成**
    

---
