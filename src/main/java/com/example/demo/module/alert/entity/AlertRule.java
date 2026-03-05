package com.example.demo.module.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 告警规则实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_alert_rule")
public class AlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定的采集端 ID（* = 全局） */
    private String agentId;

    /** 规则名称 */
    private String ruleName;

    /**
     * 指标类型: CPU_USAGE / RAM_USAGE / DISK_USAGE / DISK_PARTITION / PROCESS_ABNORMAL
     * / AGENT_OFFLINE
     */
    private String metricType;

    /** 比较符: GT / GTE / LT / LTE / EQ */
    private String operator;

    /** 阈值 */
    private Double threshold;

    /** 目标名称 (盘符如C 或进程名，可选) */
    private String targetName;

    /** 告警级别: WARNING / CRITICAL */
    private String alertLevel;

    /** 冷却秒数 */
    private Integer cooldownSec;

    /** 是否启用 */
    private Boolean enabled;

    private Date createTime;
    private Date updateTime;
}
