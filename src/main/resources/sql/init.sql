-- =============================================================
-- 轻量级服务器与日志集中监控系统 — 数据库初始化脚本
-- H2 兼容 (MODE=MySQL)
-- =============================================================

-- 过滤规则表
CREATE TABLE IF NOT EXISTS `t_filter_rule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`    VARCHAR(100) NOT NULL DEFAULT '*' COMMENT '绑定的采集端 ID (* = 全局)',
    `rule_type`   VARCHAR(20)  NOT NULL COMMENT '规则类型: CRITICAL / EXCLUDE / BASIC',
    `rule_name`   VARCHAR(100) NOT NULL COMMENT '规则名称（人类可读）',
    `keyword`     VARCHAR(500) NOT NULL COMMENT '匹配关键字或正则表达式',
    `match_mode`  VARCHAR(20)  NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配模式: CONTAINS / REGEX / STARTS_WITH / ENDS_WITH',
    `app_name`    VARCHAR(100) DEFAULT NULL COMMENT '所属应用名（NULL则全局生效）',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `priority`    INT          NOT NULL DEFAULT 0 COMMENT '优先级（同类型内排序）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_filter_agent` (`agent_id`)
);

-- 初始示例数据
INSERT INTO `t_filter_rule` (`agent_id`, `rule_type`, `rule_name`, `keyword`, `match_mode`, `enabled`, `priority`)
VALUES
    ('*', 'CRITICAL', '第三方接口超时',     '调用第三方接口超时',   'CONTAINS', 1, 10),
    ('*', 'CRITICAL', '数据库连接失败',     '数据库连接失败',       'CONTAINS', 1, 20),
    ('*', 'EXCLUDE',  '正常心跳日志',       'HealthCheck OK',       'CONTAINS', 1, 10),
    ('*', 'EXCLUDE',  '定时任务执行完成',   '定时任务执行完成',     'CONTAINS', 1, 20),
    ('*', 'BASIC',    'ERROR级别日志',       'ERROR',                'CONTAINS', 1, 10),
    ('*', 'BASIC',    '异常堆栈',           'Exception',            'CONTAINS', 1, 20),
    ('*', 'BASIC',    'FATAL级别日志',       'FATAL',                'CONTAINS', 1, 30),
    ('*', 'BASIC',    '堆栈跟踪',           'Stacktrace',           'CONTAINS', 1, 40),
    ('*', 'BASIC',    '空指针异常',         'NullPointerException', 'CONTAINS', 1, 50),
    ('*', 'BASIC',    '内存溢出',           'OutOfMemoryError',     'CONTAINS', 1, 60);

-- 远程指令记录表
CREATE TABLE IF NOT EXISTS `t_command_record` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `cmd_id`      VARCHAR(36)   NOT NULL COMMENT '指令唯一标识 (UUID)',
    `agent_id`    VARCHAR(100)  NOT NULL COMMENT '目标采集端 ID',
    `action`      VARCHAR(50)   NOT NULL COMMENT '指令动作 (CommandAction 枚举值)',
    `params`      VARCHAR(2000) DEFAULT NULL COMMENT '指令参数 (JSON 格式)',
    `status`      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '指令状态: PENDING / SUCCESS / FAILED / TIMEOUT',
    `response`    TEXT          DEFAULT NULL COMMENT 'Agent 执行反馈内容',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下发时间',
    `finish_time` DATETIME      DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cmd_id` (`cmd_id`)
);

-- 日志命中记录表
CREATE TABLE IF NOT EXISTS `t_log_hit_record` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`          VARCHAR(100)  NOT NULL COMMENT '来源采集端 ID',
    `app_name`          VARCHAR(100)  DEFAULT NULL COMMENT '应用名称',
    `log_path`          VARCHAR(500)  DEFAULT NULL COMMENT '日志文件路径',
    `level`             VARCHAR(30)   NOT NULL COMMENT '判定级别: CRITICAL / UNKNOWN_ERROR',
    `matched_rule_name` VARCHAR(200)  DEFAULT NULL COMMENT '命中的规则名称',
    `matched_keyword`   VARCHAR(500)  DEFAULT NULL COMMENT '命中的关键字',
    `log_content`       TEXT          DEFAULT NULL COMMENT '原始日志内容(最多2000字符)',
    `log_time`          DATETIME      DEFAULT NULL COMMENT 'Agent 上报时间',
    `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_level` (`agent_id`, `level`),
    KEY `idx_create_time` (`create_time`)
);

-- 指标快照主表 (每次 METRICS 上报存一行)
CREATE TABLE IF NOT EXISTS `t_metrics_snapshot` (
    `id`                         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`                   VARCHAR(100)  NOT NULL COMMENT '采集端 ID',
    `mainframe_name`             VARCHAR(200)  DEFAULT NULL COMMENT '主机名',
    `os_name`                    VARCHAR(200)  DEFAULT NULL COMMENT '操作系统名称',
    `os_version`                 VARCHAR(100)  DEFAULT NULL COMMENT '操作系统版本',
    `os_type`                    VARCHAR(50)   DEFAULT NULL COMMENT '操作系统类型',
    `cpu_type`                   VARCHAR(200)  DEFAULT NULL COMMENT 'CPU 型号',
    `cpu_speed`                  VARCHAR(50)   DEFAULT NULL COMMENT 'CPU 主频',
    `cpu_usage`                  VARCHAR(20)   DEFAULT NULL COMMENT 'CPU 使用率',
    `cpu_cores`                  VARCHAR(10)   DEFAULT NULL COMMENT 'CPU 核心数',
    `ram_capacity`               VARCHAR(20)   DEFAULT NULL COMMENT '内存总量',
    `ram_usage`                  VARCHAR(20)   DEFAULT NULL COMMENT '已用内存',
    `ram_available`              VARCHAR(20)   DEFAULT NULL COMMENT '可用内存',
    `ram_speed`                  VARCHAR(20)   DEFAULT NULL COMMENT '内存频率',
    `ram_type`                   VARCHAR(50)   DEFAULT NULL COMMENT '内存类型',
    `ram_manufacturer`           VARCHAR(100)  DEFAULT NULL COMMENT '内存厂商',
    `main_board`                 VARCHAR(200)  DEFAULT NULL COMMENT '主板型号',
    `disk`                       VARCHAR(500)  DEFAULT NULL COMMENT '硬盘型号',
    `gpu`                        VARCHAR(500)  DEFAULT NULL COMMENT 'GPU 列表',
    `total_disk_capacity`        VARCHAR(20)   DEFAULT NULL COMMENT '磁盘总容量(MB)',
    `total_available_capacity_disk` VARCHAR(20) DEFAULT NULL COMMENT '磁盘可用容量(MB)',
    `process_count`              INT           DEFAULT NULL COMMENT '进程数',
    `feedback_time`              VARCHAR(50)   DEFAULT NULL COMMENT 'Agent 上报时间字符串',
    `report_timestamp`           BIGINT        DEFAULT NULL COMMENT 'Agent 上报时间戳(ms)',
    `create_time`                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_create_time_ms` (`create_time`)
);

-- 磁盘分区子表
CREATE TABLE IF NOT EXISTS `t_disk_partition` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `snapshot_id`        BIGINT       NOT NULL COMMENT '关联快照 ID',
    `mount_point`        VARCHAR(20)  DEFAULT NULL COMMENT '挂载点/盘符',
    `capacity`           VARCHAR(20)  DEFAULT NULL COMMENT '分区总容量(MB)',
    `available_capacity` VARCHAR(20)  DEFAULT NULL COMMENT '分区可用容量(MB)',
    PRIMARY KEY (`id`),
    KEY `idx_snapshot_id` (`snapshot_id`)
);

-- 进程状态子表
CREATE TABLE IF NOT EXISTS `t_process_status` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `snapshot_id`   BIGINT       NOT NULL COMMENT '关联快照 ID',
    `process_name`  VARCHAR(200) DEFAULT NULL COMMENT '进程/应用名称',
    `status`        VARCHAR(50)  DEFAULT NULL COMMENT '状态: 正常 / 异常',
    PRIMARY KEY (`id`),
    KEY `idx_snapshot_id_ps` (`snapshot_id`)
);

-- 告警规则表 (嵌套条件分组模式)
CREATE TABLE IF NOT EXISTS `t_alert_rule` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id`      VARCHAR(100) NOT NULL DEFAULT '*' COMMENT '绑定的采集端 ID (* = 全局)',
    `rule_name`     VARCHAR(200) NOT NULL COMMENT '规则名称',
    `conditions`    TEXT         NOT NULL COMMENT '条件表达式 JSON: {logic,groups:[{logic,items:[{metricType,operator,threshold,targetName},...]},...]}',
    `alert_level`   VARCHAR(20)  NOT NULL DEFAULT 'WARNING' COMMENT '告警级别: WARNING / CRITICAL',
    `cooldown_sec`  INT          NOT NULL DEFAULT 300 COMMENT '冷却秒数',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_alert_agent` (`agent_id`)
);

-- 告警事件记录表
CREATE TABLE IF NOT EXISTS `t_alert_event` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_id`       BIGINT        NOT NULL COMMENT '关联规则 ID',
    `rule_name`     VARCHAR(200)  DEFAULT NULL COMMENT '规则名称(冗余)',
    `agent_id`      VARCHAR(100)  NOT NULL COMMENT '触发的采集端 ID',
    `metric_type`   VARCHAR(30)   DEFAULT NULL COMMENT '指标类型',
    `metric_value`  VARCHAR(100)  DEFAULT NULL COMMENT '触发时的实际值',
    `message`       VARCHAR(500)  DEFAULT NULL COMMENT '告警消息',
    `alert_level`   VARCHAR(20)   NOT NULL DEFAULT 'WARNING' COMMENT '告警级别',
    `acknowledged`  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已确认',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    PRIMARY KEY (`id`),
    KEY `idx_alert_event_agent` (`agent_id`),
    KEY `idx_alert_event_ack` (`acknowledged`)
);
