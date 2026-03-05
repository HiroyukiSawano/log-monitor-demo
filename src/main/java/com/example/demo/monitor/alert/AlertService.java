package com.example.demo.monitor.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.alert.channel.AlertChannel;
import com.example.demo.module.alert.entity.AlertCondition;
import com.example.demo.module.alert.entity.AlertEvent;
import com.example.demo.module.alert.entity.AlertRule;
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
 * 告警服务 — 基于组合条件规则评估 METRICS 指标，触发告警并分发到通知渠道
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

    /**
     * 基于 METRICS 快照评估所有适用规则
     */
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

    /**
     * 获取未确认的告警事件
     */
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

    /**
     * 获取告警事件列表
     */
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

    /**
     * 确认(消除)告警
     */
    public void acknowledge(Long eventId) {
        AlertEvent event = eventMapper.selectById(eventId);
        if (event != null) {
            event.setAcknowledged(true);
            eventMapper.updateById(event);
        }
    }

    /**
     * 批量确认
     */
    public void acknowledgeAll(String agentId) {
        List<AlertEvent> events = getUnacknowledged(agentId);
        for (AlertEvent e : events) {
            e.setAcknowledged(true);
            eventMapper.updateById(e);
        }
    }

    // ==================== 私有方法 ====================

    private void checkRule(String agentId, AlertRule rule, MetricsSnapshot snapshot) {
        List<AlertCondition> conditions = rule.parseConditions();
        if (conditions.isEmpty())
            return;

        boolean isAnd = "AND".equalsIgnoreCase(rule.getLogic());

        // 评估每个条件，收集结果
        List<ConditionResult> results = new ArrayList<>();
        for (AlertCondition cond : conditions) {
            ConditionResult cr = evaluateCondition(cond, snapshot);
            results.add(cr);
        }

        // 根据 AND/OR 逻辑判断是否触发
        boolean shouldFire;
        if (isAnd) {
            // AND: 所有条件都满足
            shouldFire = results.stream().allMatch(r -> r.triggered);
        } else {
            // OR: 任一条件满足
            shouldFire = results.stream().anyMatch(r -> r.triggered);
        }

        if (shouldFire) {
            // 构建告警消息
            String logicLabel = isAnd ? " AND " : " OR ";
            List<String> triggeredMessages = results.stream()
                    .filter(r -> r.triggered)
                    .map(r -> r.message)
                    .collect(Collectors.toList());
            String combinedMessage = String.join(logicLabel, triggeredMessages);

            // 收集触发条件的指标类型摘要
            String metricTypes = results.stream()
                    .filter(r -> r.triggered)
                    .map(r -> r.metricType)
                    .collect(Collectors.joining(","));
            String metricValues = results.stream()
                    .filter(r -> r.triggered)
                    .map(r -> r.actualStr)
                    .collect(Collectors.joining(","));

            fireAlert(agentId, rule, metricTypes, metricValues, combinedMessage);
        }
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
                            String.format("CPU使用率 %s %s%.0f%%", actualStr, opLabel(op), threshold));
                }
                return new ConditionResult(false, metricType, actualStr, "");

            case "RAM_USAGE":
                actual = calcRamPct(snapshot);
                actualStr = String.format("%.0f%%", actual);
                if (compare(actual, op, threshold)) {
                    return new ConditionResult(true, metricType, actualStr,
                            String.format("内存使用率 %s %s%.0f%%", actualStr, opLabel(op), threshold));
                }
                return new ConditionResult(false, metricType, actualStr, "");

            case "DISK_USAGE":
                actual = calcDiskPct(snapshot);
                actualStr = String.format("%.0f%%", actual);
                if (compare(actual, op, threshold)) {
                    return new ConditionResult(true, metricType, actualStr,
                            String.format("总磁盘使用率 %s %s%.0f%%", actualStr, opLabel(op), threshold));
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
                                        String.format("分区 %s 可用 %s %s%.0fMB",
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
                                    String.format("进程 [%s] 状态异常: %s", ps.getProcessName(), ps.getStatus()));
                        }
                    }
                }
                return new ConditionResult(false, metricType, "正常", "");

            case "AGENT_OFFLINE":
                // Agent 离线由 WebSocket 断连检测触发，METRICS 阶段此处始终为 false
                return new ConditionResult(false, metricType, "在线", "");

            default:
                return new ConditionResult(false, metricType, "N/A", "");
        }
    }

    private void fireAlert(String agentId, AlertRule rule, String metricType,
            String metricValue, String message) {
        // 冷却检查
        String cooldownKey = rule.getId() + ":" + agentId;
        Long lastFired = cooldownMap.get(cooldownKey);
        long now = System.currentTimeMillis();
        int cooldownMs = (rule.getCooldownSec() != null ? rule.getCooldownSec() : 300) * 1000;

        if (lastFired != null && (now - lastFired) < cooldownMs) {
            return; // 冷却期内，跳过
        }

        cooldownMap.put(cooldownKey, now);

        // 写入事件表
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

        // 分发到所有通知渠道
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
            return Double.parseDouble(str.trim());
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

    /** 单个条件的评估结果 */
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
}
