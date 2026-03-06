package com.example.demo.monitor.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.alert.channel.AlertChannel;
import com.example.demo.module.alert.entity.AlertCondition;
import com.example.demo.module.alert.entity.AlertEvent;
import com.example.demo.module.alert.entity.AlertRule;
import com.example.demo.module.alert.entity.ConditionGroup;
import com.example.demo.module.alert.mapper.AlertEventMapper;
import com.example.demo.module.alert.service.AlertRuleService;
import com.example.demo.module.metrics.entity.DiskPartition;
import com.example.demo.module.metrics.entity.MetricsSnapshot;
import com.example.demo.module.metrics.entity.ProcessStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 告警服务 — 基于嵌套条件分组规则评估 METRICS 指标
 *
 * 表达式格式: (A OR B) AND (C) — 组间用顶层 logic，组内用各组 logic
 */
@Slf4j
@Service
public class AlertService {

    private final AlertRuleService ruleService;
    private final AlertEventMapper eventMapper;
    private final List<AlertChannel> channels;

    /** 冷却记录: key = "ruleId:agentId", value = 上次触发时间 */
    private final Map<String, Long> cooldownMap = new ConcurrentHashMap<>();

    public AlertService(AlertRuleService ruleService,
            AlertEventMapper eventMapper,
            List<AlertChannel> channels) {
        this.ruleService = ruleService;
        this.eventMapper = eventMapper;
        this.channels = channels != null ? channels : Collections.emptyList();
        log.info("[AlertService] 已注册 {} 个告警通知渠道: {}",
                this.channels.size(),
                this.channels.stream().map(AlertChannel::channelName)
                        .reduce((a, b) -> a + ", " + b).orElse("无"));
    }

    // ==================== 公开方法 ====================

    public void evaluateMetrics(String agentId, MetricsSnapshot snapshot) {
        List<AlertRule> rules = ruleService.listByAgent(agentId);
        if (rules.isEmpty())
            return;

        for (AlertRule rule : rules) {
            try {
                checkRule(agentId, rule, snapshot);
            } catch (Exception e) {
                log.error("[AlertService] 规则检查异常: ruleId={}, agentId={}", rule.getId(), agentId, e);
            }
        }
    }

    public List<AlertEvent> getUnacknowledged(String agentId) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertEvent::getAcknowledged, false);
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(AlertEvent::getAgentId, agentId);
        }
        wrapper.orderByDesc(AlertEvent::getId);
        wrapper.last("LIMIT 100");
        return eventMapper.selectList(wrapper);
    }

    public List<AlertEvent> listEvents(String agentId, Boolean acknowledged, Integer limit) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(AlertEvent::getAgentId, agentId);
        }
        if (acknowledged != null) {
            wrapper.eq(AlertEvent::getAcknowledged, acknowledged);
        }
        wrapper.orderByDesc(AlertEvent::getId);
        wrapper.last("LIMIT " + (limit != null ? limit : 100));
        return eventMapper.selectList(wrapper);
    }

    public void acknowledge(Long eventId) {
        AlertEvent event = eventMapper.selectById(eventId);
        if (event != null) {
            event.setAcknowledged(true);
            eventMapper.updateById(event);
        }
    }

    public void acknowledgeAll(String agentId) {
        List<AlertEvent> events = getUnacknowledged(agentId);
        for (AlertEvent e : events) {
            e.setAcknowledged(true);
            eventMapper.updateById(e);
        }
    }

    // ==================== 核心评估逻辑 ====================

    private void checkRule(String agentId, AlertRule rule, MetricsSnapshot snapshot) {
        List<ConditionGroup> groups = rule.parseGroups();
        if (groups.isEmpty())
            return;

        String topLogic = rule.parseTopLogic();
        boolean isTopAnd = "AND".equalsIgnoreCase(topLogic);

        // 评估每个分组
        List<GroupResult> groupResults = new ArrayList<>();
        for (ConditionGroup group : groups) {
            GroupResult gr = evaluateGroup(group, snapshot);
            groupResults.add(gr);
        }

        // 顶层逻辑组合
        boolean shouldFire;
        if (isTopAnd) {
            shouldFire = groupResults.stream().allMatch(g -> g.triggered);
        } else {
            shouldFire = groupResults.stream().anyMatch(g -> g.triggered);
        }

        if (shouldFire) {
            // 构建告警消息
            List<String> groupMessages = new ArrayList<>();
            for (GroupResult gr : groupResults) {
                if (gr.triggered && !gr.messages.isEmpty()) {
                    String groupLogicLabel = "AND".equalsIgnoreCase(gr.logic) ? " 且 " : " 或 ";
                    String joined = gr.messages.size() == 1
                            ? gr.messages.get(0)
                            : "(" + String.join(groupLogicLabel, gr.messages) + ")";
                    groupMessages.add(joined);
                }
            }
            String topLogicLabel = isTopAnd ? " 且 " : " 或 ";
            String combinedMessage = String.join(topLogicLabel, groupMessages);

            String metricTypes = groupResults.stream()
                    .filter(g -> g.triggered)
                    .flatMap(g -> g.metricTypes.stream())
                    .collect(Collectors.joining(","));
            String metricValues = groupResults.stream()
                    .filter(g -> g.triggered)
                    .flatMap(g -> g.actualValues.stream())
                    .collect(Collectors.joining(","));

            fireAlert(agentId, rule, metricTypes, metricValues, combinedMessage);
        }
    }

    /**
     * 评估一个条件分组
     */
    private GroupResult evaluateGroup(ConditionGroup group, MetricsSnapshot snapshot) {
        List<AlertCondition> items = group.getItems();
        if (items == null || items.isEmpty()) {
            return new GroupResult(false, group.getLogic(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList());
        }

        boolean isAnd = "AND".equalsIgnoreCase(group.getLogic());
        List<ConditionResult> results = new ArrayList<>();
        for (AlertCondition cond : items) {
            results.add(evaluateCondition(cond, snapshot));
        }

        boolean triggered;
        if (isAnd) {
            triggered = results.stream().allMatch(r -> r.triggered);
        } else {
            triggered = results.stream().anyMatch(r -> r.triggered);
        }

        List<String> messages = results.stream().filter(r -> r.triggered)
                .map(r -> r.message).collect(Collectors.toList());
        List<String> types = results.stream().filter(r -> r.triggered)
                .map(r -> r.metricType).collect(Collectors.toList());
        List<String> values = results.stream().filter(r -> r.triggered)
                .map(r -> r.actualStr).collect(Collectors.toList());

        return new GroupResult(triggered, group.getLogic(), messages, types, values);
    }

    /**
     * 评估单个条件
     */
    private ConditionResult evaluateCondition(AlertCondition cond, MetricsSnapshot snapshot) {
        String metricType = cond.getMetricType();
        double actual;
        String actualStr;
        String op = cond.getOperator() != null ? cond.getOperator() : "GT";
        double threshold = cond.getThreshold() != null ? cond.getThreshold() : 0;

        switch (metricType) {
            case "CPU_USAGE":
                actual = parsePct(snapshot.getCpuUsage());
                actualStr = snapshot.getCpuUsage();
                if (compare(actual, op, threshold)) {
                    return new ConditionResult(true, metricType, actualStr,
                            String.format("CPU %s %s%.0f%%", actualStr, opLabel(op), threshold));
                }
                return new ConditionResult(false, metricType, actualStr, "");

            case "RAM_USAGE":
                actual = calcRamPct(snapshot);
                actualStr = String.format("%.0f%%", actual);
                if (compare(actual, op, threshold)) {
                    return new ConditionResult(true, metricType, actualStr,
                            String.format("RAM %s %s%.0f%%", actualStr, opLabel(op), threshold));
                }
                return new ConditionResult(false, metricType, actualStr, "");

            case "DISK_USAGE":
                actual = calcDiskPct(snapshot);
                actualStr = String.format("%.0f%%", actual);
                if (compare(actual, op, threshold)) {
                    return new ConditionResult(true, metricType, actualStr,
                            String.format("磁盘 %s %s%.0f%%", actualStr, opLabel(op), threshold));
                }
                return new ConditionResult(false, metricType, actualStr, "");

            case "DISK_PARTITION":
                if (snapshot.getParts() != null && cond.getTargetName() != null) {
                    for (DiskPartition part : snapshot.getParts()) {
                        if (cond.getTargetName().equalsIgnoreCase(part.getMountPoint())) {
                            double avail = parseDouble(part.getAvailableCapacity());
                            actualStr = String.format("%.0fMB", avail);
                            if (compare(avail, op, threshold)) {
                                return new ConditionResult(true, metricType, actualStr,
                                        String.format("分区%s %s %s%.0fMB",
                                                part.getMountPoint(), actualStr, opLabel(op), threshold));
                            }
                            return new ConditionResult(false, metricType, actualStr, "");
                        }
                    }
                }
                return new ConditionResult(false, metricType, "N/A", "");

            case "PROCESS_ABNORMAL":
                if (snapshot.getProcessStatusList() != null) {
                    for (ProcessStatus ps : snapshot.getProcessStatusList()) {
                        if (!"正常".equals(ps.getStatus())) {
                            if (cond.getTargetName() != null && !cond.getTargetName().isEmpty()) {
                                if (!cond.getTargetName().equals(ps.getProcessName()))
                                    continue;
                            }
                            return new ConditionResult(true, metricType, ps.getStatus(),
                                    String.format("进程[%s]异常:%s", ps.getProcessName(), ps.getStatus()));
                        }
                    }
                }
                return new ConditionResult(false, metricType, "正常", "");

            case "AGENT_OFFLINE":
                return new ConditionResult(false, metricType, "在线", "");

            default:
                return new ConditionResult(false, metricType, "N/A", "");
        }
    }

    private void fireAlert(String agentId, AlertRule rule, String metricType,
            String metricValue, String message) {
        String cooldownKey = rule.getId() + ":" + agentId;
        Long lastFired = cooldownMap.get(cooldownKey);
        long now = System.currentTimeMillis();
        int cooldownMs = (rule.getCooldownSec() != null ? rule.getCooldownSec() : 300) * 1000;

        if (lastFired != null && (now - lastFired) < cooldownMs) {
            return;
        }
        cooldownMap.put(cooldownKey, now);

        AlertEvent event = AlertEvent.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getRuleName())
                .agentId(agentId)
                .metricType(metricType)
                .metricValue(metricValue)
                .message(message)
                .alertLevel(rule.getAlertLevel())
                .acknowledged(false)
                .createTime(new Date())
                .build();
        eventMapper.insert(event);

        for (AlertChannel channel : channels) {
            try {
                channel.send(event);
            } catch (Exception e) {
                log.error("[AlertService] 通知渠道 {} 发送失败", channel.channelName(), e);
            }
        }
    }

    // ==================== 工具方法 ====================

    private boolean compare(double actual, String operator, double threshold) {
        switch (operator) {
            case "GT":
                return actual > threshold;
            case "GTE":
                return actual >= threshold;
            case "LT":
                return actual < threshold;
            case "LTE":
                return actual <= threshold;
            case "EQ":
                return Math.abs(actual - threshold) < 0.01;
            default:
                return actual > threshold;
        }
    }

    private String opLabel(String operator) {
        switch (operator) {
            case "GT":
                return "> ";
            case "GTE":
                return "≥ ";
            case "LT":
                return "< ";
            case "LTE":
                return "≤ ";
            case "EQ":
                return "= ";
            default:
                return "> ";
        }
    }

    private double parsePct(String str) {
        if (str == null)
            return 0;
        try {
            return Double.parseDouble(str.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(String str) {
        if (str == null)
            return 0;
        try {
            // Remove any characters that are not digits or a decimal point
            String numericPart = str.replaceAll("[^0-9.]", "");
            if (numericPart.isEmpty()) {
                return 0;
            }
            return Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double calcRamPct(MetricsSnapshot s) {
        double total = parseDouble(s.getRamCapacity());
        double used = parseDouble(s.getRamUsage());
        return total > 0 ? (used / total) * 100 : 0;
    }

    private double calcDiskPct(MetricsSnapshot s) {
        double total = parseDouble(s.getTotalDiskCapacity());
        double avail = parseDouble(s.getTotalAvailableCapacityDisk());
        return total > 0 ? ((total - avail) / total) * 100 : 0;
    }

    // ==================== 内部类 ====================

    private static class ConditionResult {
        final boolean triggered;
        final String metricType;
        final String actualStr;
        final String message;

        ConditionResult(boolean triggered, String metricType, String actualStr, String message) {
            this.triggered = triggered;
            this.metricType = metricType;
            this.actualStr = actualStr;
            this.message = message;
        }
    }

    private static class GroupResult {
        final boolean triggered;
        final String logic;
        final List<String> messages;
        final List<String> metricTypes;
        final List<String> actualValues;

        GroupResult(boolean triggered, String logic, List<String> messages,
                List<String> metricTypes, List<String> actualValues) {
            this.triggered = triggered;
            this.logic = logic;
            this.messages = messages;
            this.metricTypes = metricTypes;
            this.actualValues = actualValues;
        }
    }
}
