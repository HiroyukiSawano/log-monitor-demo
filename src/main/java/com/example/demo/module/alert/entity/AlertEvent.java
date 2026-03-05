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
 * 告警事件实体 — 每次触发告警记录一条
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_alert_event")
public class AlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联规则 ID */
    private Long ruleId;

    /** 规则名称(冗余便于展示) */
    private String ruleName;

    /** 触发的采集端 ID */
    private String agentId;

    /** 指标类型 */
    private String metricType;

    /** 触发时的实际值 */
    private String metricValue;

    /** 告警消息 */
    private String message;

    /** 告警级别: WARNING / CRITICAL */
    private String alertLevel;

    /** 是否已确认 */
    private Boolean acknowledged;

    /** 触发时间 */
    private Date createTime;
}
