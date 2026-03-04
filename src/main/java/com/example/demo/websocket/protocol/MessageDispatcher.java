package com.example.demo.websocket.protocol;

import com.example.demo.common.enums.LogLevel;
import com.example.demo.common.enums.MessageType;
import com.example.demo.engine.filter.LogFilterChain;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;
import com.example.demo.module.command.service.RemoteCommandService;
import com.example.demo.module.loghit.service.LogHitService;
import com.example.demo.module.metrics.service.MetricsService;
import com.example.demo.monitor.alert.AlertService;
import com.example.demo.monitor.health.HealthEvaluator;
import com.example.demo.monitor.health.ServerHealthState;
import com.example.demo.websocket.session.AgentSessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息分发器 — 根据信封中的 type 字段将消息路由到对应的处理逻辑
 */
@Slf4j
@Component
public class MessageDispatcher {

    private final AgentSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final LogFilterChain logFilterChain;
    private final HealthEvaluator healthEvaluator;
    private final AlertService alertService;
    private final RemoteCommandService remoteCommandService;
    private final LogHitService logHitService;
    private final MetricsService metricsService;

    public MessageDispatcher(AgentSessionManager sessionManager,
            ObjectMapper objectMapper,
            LogFilterChain logFilterChain,
            HealthEvaluator healthEvaluator,
            AlertService alertService,
            RemoteCommandService remoteCommandService,
            LogHitService logHitService,
            MetricsService metricsService) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.logFilterChain = logFilterChain;
        this.healthEvaluator = healthEvaluator;
        this.alertService = alertService;
        this.remoteCommandService = remoteCommandService;
        this.logHitService = logHitService;
        this.metricsService = metricsService;
    }

    /**
     * 分发 WebSocket 文本消息
     */
    public void dispatch(WsMessage message) {
        String type = message.getType();
        String agentId = message.getAgentId();
        JsonNode payload = message.getPayload();

        MessageType messageType;
        try {
            messageType = MessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            log.warn("[Dispatcher] 未知消息类型: type={}, agentId={}", type, agentId);
            return;
        }

        switch (messageType) {
            case HEARTBEAT:
                handleHeartbeat(agentId);
                break;
            case METRICS:
                handleMetrics(agentId, payload, message.getTimestamp());
                break;
            case LOG_LINE:
                handleLogLine(agentId, payload, message.getTimestamp());
                break;
            case CMD_RESPONSE:
                handleCmdResponse(agentId, payload);
                break;
            default:
                log.warn("[Dispatcher] 不支持的 Agent 上报消息类型: {}", type);
        }
    }

    private void handleHeartbeat(String agentId) {
        sessionManager.refreshHeartbeat(agentId);
        log.debug("[Dispatcher] 收到心跳: agentId={}", agentId);
    }

    private void handleMetrics(String agentId, JsonNode payload, Long timestamp) {
        // 1. 持久化完整指标快照
        metricsService.saveMetrics(agentId, payload, timestamp);

        // 2. 提取 CPU / Disk 使用率用于健康评估
        double cpuUsage = parseCpuPercent(payload.path("cpuUsage").asText("0"));
        double totalDisk = payload.path("totalDiskCapacity").asDouble(1);
        double availDisk = payload.path("totalAvailableCapacityDisk").asDouble(0);
        double diskUsage = totalDisk > 0 ? ((totalDisk - availDisk) / totalDisk) * 100.0 : 0;

        ServerHealthState state = healthEvaluator.updateMetrics(agentId, cpuUsage, diskUsage);
        log.info("[Dispatcher] 指标已更新: agentId={}, cpu={}%, disk={}%, status={}",
                agentId, cpuUsage, diskUsage, state.getStatus());
    }

    /**
     * 解析 CPU 使用率字符串 (如 "6%") 为 double
     */
    private double parseCpuPercent(String cpuStr) {
        try {
            return Double.parseDouble(cpuStr.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void handleLogLine(String agentId, JsonNode payload, Long timestamp) {
        String appName = payload.path("appName").asText();
        String logPath = payload.path("logPath").asText();
        String line = payload.path("line").asText();

        // 1. 构建日志上下文
        LogContext context = LogContext.builder()
                .agentId(agentId)
                .appName(appName)
                .logPath(logPath)
                .rawLine(line)
                .timestamp(timestamp != null ? timestamp : System.currentTimeMillis())
                .build();

        // 2. 送入过滤链
        FilterResult result = logFilterChain.execute(context);

        // 3. 回推过滤结果到 Agent（调试用）
        sendFilterResult(agentId, result, line);

        // 3.5 命中的日志入库
        logHitService.saveIfMatched(context, result);

        // 4. 根据结果处理
        if (result.getLevel() == LogLevel.CRITICAL) {
            String snippet = line.length() > 200 ? line.substring(0, 200) : line;
            ServerHealthState state = healthEvaluator.processLogResult(agentId, result, snippet);
            alertService.triggerAlert(state, result, snippet);
        } else if (result.getLevel() == LogLevel.UNKNOWN_ERROR) {
            String snippet = line.length() > 200 ? line.substring(0, 200) : line;
            healthEvaluator.processLogResult(agentId, result, snippet);
            log.warn("[Dispatcher] 未知错误日志: agentId={}, app={}, keyword={}",
                    agentId, appName, result.getMatchedKeyword());
        }
    }

    /**
     * 向 Agent 回推日志过滤结果
     */
    private void sendFilterResult(String agentId, FilterResult result, String logLine) {
        sessionManager.getSession(agentId).ifPresent(session -> {
            try {
                java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("type", "FILTER_RESULT");
                response.put("agentId", agentId);
                response.put("timestamp", System.currentTimeMillis());
                java.util.Map<String, Object> p = new java.util.LinkedHashMap<>();
                p.put("level", result.getLevel().name());
                p.put("matched", result.isMatched());
                p.put("matchedRuleName", result.getMatchedRuleName());
                p.put("matchedKeyword", result.getMatchedKeyword());
                p.put("logSnippet", logLine.length() > 120 ? logLine.substring(0, 120) + "..." : logLine);
                response.put("payload", p);
                String json = objectMapper.writeValueAsString(response);
                session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            } catch (Exception e) {
                log.warn("[Dispatcher] 回推过滤结果失败: agentId={}", agentId, e);
            }
        });
    }

    private void handleCmdResponse(String agentId, JsonNode payload) {
        String cmdId = payload.path("cmdId").asText();
        boolean success = payload.path("success").asBoolean();
        String output = payload.path("output").asText("");
        log.info("[Dispatcher] 收到指令响应: agentId={}, cmdId={}, success={}", agentId, cmdId, success);
        remoteCommandService.handleResponse(agentId, cmdId, success, output);
    }
}
