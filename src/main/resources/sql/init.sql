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
