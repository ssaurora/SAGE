
# 第 1 周：立骨架

## 本周目标

把系统最小骨架立起来，让“任务—状态—事件—模板—运行镜像”这几根主梁先站住。

---

## 前端团队

本周任务：

- 做任务创建页
    
- 做任务详情页骨架
    
- 展示任务主状态
    
- 展示事件列表
    
- 预留结果区和错误区占位
    

交付物：

- 一个可输入自然语言并提交任务的页面
    
- 一个能展示任务状态和事件流的页面
    

技术栈：

- React / Next.js
    
- TypeScript
    
- 基础 UI 组件
    

---

## Java / Spring Boot 团队

本周任务：

- 建立 `task_state`
    
- 建立 `event_log`
    
- 建立 `audit_record`
    
- 实现最小状态机
    
- 开放：
    
    - `POST /tasks`
        
    - `GET /tasks/{taskId}`
        
    - `GET /tasks/{taskId}/events`
        
- 实现任务创建后的状态推进骨架
    

交付物：

- 最小控制面主服务
    
- 最小 API
    
- 最小状态流转逻辑
    

技术栈：

- Spring Boot
    
- JPA / MyBatis
    
- PostgreSQL
    

---

## Python 团队

本周任务：

- 实现 Planning Pass 1 雏形
    
- 固定 `water_yield` 单模板
    
- 输出：
    
    - `selected_template`
        
    - `logical_input_roles`
        
    - `slot_schema_view`
        
    - `graph_skeleton`
        
- 产出固定 Docker 运行镜像
    
- 固定容器内输入/输出/日志目录规范
    

交付物：

- Pass 1 原型服务
    
- Docker 镜像
    

技术栈：

- Python
    
- Pydantic
    
- FastAPI 或内部 Python 服务
    
- Docker
    
- InVEST / GIS 依赖环境
    

---

## 数据库与存储团队

本周任务：

- 建表：
    
    - `task_state`
        
    - `event_log`
        
    - `audit_record`
        
- 设计最小索引
    
- 设计 `task_id`、`state_version` 等关键字段约束
    
- 约定对象存储和本地目录命名规范雏形
    

交付物：

- 核心表 schema
    
- 基础索引和约束
    
- 初版命名规范
    

技术栈：

- PostgreSQL
    
- SQL migration 工具
    
- 对象存储命名约定文档
    

---

## 第 1 周联合验收标准

周末必须看到：

1. 前端能创建任务
    
2. 后端能生成 `task_id` 并落库
    
3. 状态和事件能展示
    
4. Pass 1 能真实返回角色和槽位视图
    
5. Docker 镜像能独立启动
    
6. 正式链路不再依赖“口头约定模板结构”
    

---




