package com.example.demo.module.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * 告警规则实体 — 嵌套条件分组模式
 *
 * conditions JSON 格式:
 * {
 * "logic": "AND", // 组间逻辑
 * "groups": [
 * {
 * "logic": "OR", // 组内逻辑
 * "items": [
 * {"metricType":"CPU_USAGE","operator":"GTE","threshold":80},
 * {"metricType":"RAM_USAGE","operator":"GT","threshold":70}
 * ]
 * },
 * {
 * "logic": "OR",
 * "items": [{"metricType":"DISK_USAGE","operator":"GT","threshold":90}]
 * }
 * ]
 * }
 * 表达: (CPU ≥ 80% OR RAM > 70%) AND (Disk > 90%)
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

    /**
     * 条件表达式 JSON
     * 格式: {"logic":"AND/OR", "groups":[{"logic":"AND/OR",
     * "items":[{condition},...]},...]}
     */
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
     * 解析 conditions JSON 中的组间逻辑
     */
    public String parseTopLogic() {
        try {
            JsonNode root = MAPPER.readTree(conditions);
            return root.has("logic") ? root.get("logic").asText("OR") : "OR";
        } catch (Exception e) {
            return "OR";
        }
    }

    /**
     * 解析 conditions JSON 中的条件分组列表
     */
    public List<ConditionGroup> parseGroups() {
        if (conditions == null || conditions.isEmpty())
            return Collections.emptyList();
        try {
            JsonNode root = MAPPER.readTree(conditions);
            JsonNode groupsNode = root.get("groups");
            if (groupsNode == null || !groupsNode.isArray())
                return Collections.emptyList();

            List<ConditionGroup> groups = new ArrayList<>();
            for (JsonNode gn : groupsNode) {
                String logic = gn.has("logic") ? gn.get("logic").asText("OR") : "OR";
                JsonNode itemsNode = gn.get("items");
                List<AlertCondition> items = new ArrayList<>();
                if (itemsNode != null && itemsNode.isArray()) {
                    for (JsonNode in : itemsNode) {
                        AlertCondition c = new AlertCondition();
                        c.setMetricType(in.has("metricType") ? in.get("metricType").asText() : null);
                        c.setOperator(in.has("operator") ? in.get("operator").asText() : null);
                        c.setThreshold(in.has("threshold") ? in.get("threshold").asDouble() : null);
                        c.setTargetName(in.has("targetName") && !in.get("targetName").isNull()
                                ? in.get("targetName").asText()
                                : null);
                        c.setDurationSec(in.has("durationSec") ? in.get("durationSec").asInt(0) : null);
                        items.add(c);
                    }
                }
                groups.add(new ConditionGroup(logic, items));
            }
            return groups;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
