# 监控大盘后端任务规划 (TODO)

## Stage 1: 基础设施搭建 (当前阶段)
- [x] 增加依赖项：Lombok, MyBatis-Plus, 新版 ES Client (`elasticsearch-java`) 等。
- [x] 配置基础类的通用返回实体（`Result<T>`）。
- [x] 全局异常处理器 (`@RestControllerAdvice`) 的配置。
- [x] 编写全局的 ES SSL 跳过配置，并实例化 ES 客户端。

## Stage 2: 核心组件开发
- [x] 封装针对 ES 日志查询的 Service 层（携带 `@timestamp` 过滤、`size` 和 `timeout` 的防御性校验）。
- [ ] MySQL 数据库结构设计与对应 Entity 类的编写。
- [ ] 编写告警通知人与告警规则管理的 CRUD 接口。

## Stage 3: 定时告警与串联
- [ ] 引入 Spring Task 的 `@Scheduled` 注解体系开启定时任务。
- [ ] 编写定时扫描任务：基于规则查询 ES -> 聚合日志内容 -> 触发/发送告警通知。
