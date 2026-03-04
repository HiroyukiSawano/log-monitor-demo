package com.example.demo.module.metrics.controller;

import com.example.demo.common.result.Result;
import com.example.demo.module.loghit.entity.LogHitRecord;
import com.example.demo.module.loghit.service.LogHitService;
import com.example.demo.module.metrics.entity.MetricsSnapshot;
import com.example.demo.module.metrics.service.MetricsService;
import com.example.demo.websocket.session.AgentSessionManager;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 仪表盘 API — 为前端 dashboard.html 提供数据
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MetricsService metricsService;
    private final AgentSessionManager sessionManager;
    private final LogHitService logHitService;

    public DashboardController(MetricsService metricsService,
            AgentSessionManager sessionManager,
            LogHitService logHitService) {
        this.metricsService = metricsService;
        this.sessionManager = sessionManager;
        this.logHitService = logHitService;
    }

    /**
     * 所有 Agent 概览（在线 + 曾上报过的）
     */
    @GetMapping("/agents")
    public Result<List<MetricsSnapshot>> allAgents() {
        // 1. 从 METRICS 数据获取所有曾上报过的 Agent 最新快照
        List<MetricsSnapshot> snapshots = metricsService.getAllLatest();
        Set<String> knownIds = new HashSet<>();
        for (MetricsSnapshot s : snapshots) {
            knownIds.add(s.getAgentId());
        }

        // 2. 检查是否有在线但还没上报过 METRICS 的 Agent
        Map<String, ?> onlineSessions = sessionManager.getAllSessions();
        for (String agentId : onlineSessions.keySet()) {
            if (!knownIds.contains(agentId)) {
                MetricsSnapshot stub = MetricsSnapshot.builder()
                        .agentId(agentId)
                        .online(true)
                        .build();
                snapshots.add(stub);
            }
        }

        // 3. 标记在线状态
        for (MetricsSnapshot s : snapshots) {
            s.setOnline(onlineSessions.containsKey(s.getAgentId()));
        }

        return Result.success(snapshots);
    }

    /**
     * 单个 Agent 详情
     */
    @GetMapping("/agents/{agentId}")
    public Result<MetricsSnapshot> agentDetail(@PathVariable String agentId) {
        MetricsSnapshot snapshot = metricsService.getLatestByAgent(agentId);
        if (snapshot == null) {
            // 可能在线但没上报过
            snapshot = MetricsSnapshot.builder().agentId(agentId).build();
        }
        snapshot.setOnline(sessionManager.getSession(agentId).isPresent());
        return Result.success(snapshot);
    }

    /**
     * 某 Agent 的错误日志（复用 LogHitService）
     */
    @GetMapping("/agents/{agentId}/logs")
    public Result<List<LogHitRecord>> agentLogs(
            @PathVariable String agentId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return Result.success(logHitService.listRecords(agentId, level, limit));
    }
}
