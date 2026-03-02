-- =============================================================
-- 轻量级服务器与日志集中监控系统 — 数据库初始化脚本
-- H2 兼容 (MODE=MySQL)
-- =============================================================

-- 过滤规则表
CREATE TABLE IF NOT EXISTS `t_filter_rule` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_type`   VARCHAR(20)  NOT NULL COMMENT '规则类型: CRITICAL / EXCLUDE / BASIC',
    `rule_name`   VARCHAR(100) NOT NULL COMMENT '规则名称（人类可读）',
    `keyword`     VARCHAR(500) NOT NULL COMMENT '匹配关键字或正则表达式',
    `match_mode`  VARCHAR(20)  NOT NULL DEFAULT 'CONTAINS' COMMENT '匹配模式: CONTAINS / REGEX / STARTS_WITH / ENDS_WITH',
    `app_name`    VARCHAR(100) DEFAULT NULL COMMENT '所属应用名（NULL则全局生效）',
    `enabled`     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `priority`    INT          NOT NULL DEFAULT 0 COMMENT '优先级（同类型内排序）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

-- 初始示例数据
INSERT INTO `t_filter_rule` (`rule_type`, `rule_name`, `keyword`, `match_mode`, `enabled`, `priority`)
VALUES
    ('CRITICAL', '第三方接口超时',     '调用第三方接口超时',   'CONTAINS', 1, 10),
    ('CRITICAL', '数据库连接失败',     '数据库连接失败',       'CONTAINS', 1, 20),
    ('EXCLUDE',  '正常心跳日志',       'HealthCheck OK',       'CONTAINS', 1, 10),
    ('EXCLUDE',  '定时任务执行完成',   '定时任务执行完成',     'CONTAINS', 1, 20);

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
