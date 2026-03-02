package com.example.demo.websocket.controller;

import com.example.demo.common.result.Result;
import com.example.demo.websocket.session.AgentSessionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Agent 会话查询接口
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentSessionManager sessionManager;

    public AgentController(AgentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * 获取所有在线 Agent 列表
     */
    @GetMapping("/online")
    public Result<List<Map<String, Object>>> onlineAgents() {
        List<Map<String, Object>> list = new ArrayList<>();
        sessionManager.getAllSessions().forEach((agentId, session) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("agentId", agentId);
            info.put("sessionId", session.getId());
            info.put("open", session.isOpen());
            list.add(info);
        });
        return Result.success(list);
    }
}
