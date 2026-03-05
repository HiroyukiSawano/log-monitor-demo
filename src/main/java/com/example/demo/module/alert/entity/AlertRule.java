package com.example.demo.module.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 告警规则实体 — 组合条件模式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_alert_rule")
public class AlertRule {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 绑定的采集端 ID（* = 全局） */
    private String agentId;

    /** 规则名称 */
    private String ruleName;

    /** 条件组合逻辑: AND / OR */
    private String logic;

    /** 条件 JSON 数组 */
    private String conditions;

    /** 告警级别: WARNING / CRITICAL */
    private String alertLevel;

    /** 冷却秒数 */
    private Integer cooldownSec;

    /** 是否启用 */
    private Boolean enabled;

    private Date createTime;
    private Date updateTime;

    // ==================== JSON 辅助方法 ====================

    /**
     * 解析 conditions JSON 为 List
     */
    public List<AlertCondition> parseConditions() {
        if (conditions == null || conditions.isEmpty())
            return Collections.emptyList();
        try {
            return MAPPER.readValue(conditions, new TypeReference<List<AlertCondition>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 将条件列表序列化为 JSON 字符串
     */
    public void setConditionList(List<AlertCondition> list) {
        try {
            this.conditions = MAPPER.writeValueAsString(list != null ? list : Collections.emptyList());
        } catch (Exception e) {
            this.conditions = "[]";
        }
    }
}
