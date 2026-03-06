package com.example.demo.module.alert.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警条件 POJO — 存储在 AlertRule.conditions JSON 数组中的单个条件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertCondition {

    /**
     * 指标类型: CPU_USAGE / RAM_USAGE / DISK_USAGE / DISK_PARTITION / PROCESS_ABNORMAL
     * / AGENT_OFFLINE
     */
    private String metricType;

    /** 比较符: GT / GTE / LT / LTE / EQ (非数值指标可为空) */
    private String operator;

    /** 阈值 (非数值指标可为空) */
    private Double threshold;

    /** 目标名称 (盘符如C 或进程名，可选) */
    private String targetName;

    /** 持续时间（秒），为 null 或 0 表示瞬时判定 */
    private Integer durationSec;
}
