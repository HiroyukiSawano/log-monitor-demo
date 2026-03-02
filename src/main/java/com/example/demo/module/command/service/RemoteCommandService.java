package com.example.demo.module.command.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.command.entity.CommandRecord;
import com.example.demo.module.command.mapper.CommandRecordMapper;
import com.example.demo.websocket.session.AgentSessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.*;
import java.util.concurrent.*;

/**
 * 远程指令服务 — 透传模式
 *
 * 接收前端传来的原始 JSON (content)，原封不动发给 Agent，
 * 从 content 中提取 cmdID 用于追踪和关联响应。
 */
@Slf4j
@Service
public class RemoteCommandService {

    private final AgentSessionManager sessionManager;
    private final CommandRecordMapper commandRecordMapper;
    private final ObjectMapper objectMapper;

    /** cmdId → Future，用于异步等待 Agent 响应 */
    private final Map<String, CompletableFuture<CommandRecord>> pendingFutures = new ConcurrentHashMap<>();

    /** 默认等待超时（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public RemoteCommandService(AgentSessionManager sessionManager,
            CommandRecordMapper commandRecordMapper,
            ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.commandRecordMapper = commandRecordMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送指令到 Agent（透传模式）
     *
     * @param agentId 目标 Agent
     * @param content 原始 JSON 内容（原样发送到 Agent，其中应包含 cmdID）
     * @return 指令记录（含执行结果）
     */
    public CommandRecord sendCommand(String agentId, String content) {
        // 1. 从 content 中提取 cmdID 和 func
        String cmdId = null;
        String func = null;
        try {
            JsonNode root = objectMapper.readTree(content);
            // 支持 cmdID 在顶层或在 cmd 子节点
            if (root.has("cmdID")) {
                cmdId = root.get("cmdID").asText();
            } else if (root.has("cmd") && root.get("cmd").has("cmdID")) {
                cmdId = root.get("cmd").get("cmdID").asText();
            }
            if (root.has("cmd") && root.get("cmd").has("func")) {
                func = root.get("cmd").get("func").asText();
            }
        } catch (Exception e) {
            log.warn("[Command] 无法解析 content JSON: {}", e.getMessage());
        }

        if (cmdId == null || cmdId.isEmpty()) {
            // 如果 content 里没有 cmdID，自动生成一个
            cmdId = UUID.randomUUID().toString();
            log.info("[Command] content 中未找到 cmdID，自动生成: {}", cmdId);
        }

        // 2. 生成指令记录
        CommandRecord record = CommandRecord.builder()
                .cmdId(cmdId)
                .agentId(agentId)
                .action(func)
                .params(content)
                .status("PENDING")
                .createTime(new Date())
                .build();
        commandRecordMapper.insert(record);

        // 3. 透传 content 到 Agent
        boolean sent = sendRawContent(agentId, content);
        if (!sent) {
            record.setStatus("FAILED");
            record.setResponse("Agent 不在线或发送失败");
            record.setFinishTime(new Date());
            commandRecordMapper.updateById(record);
            return record;
        }

        // 4. 注册 Future 并等待响应
        CompletableFuture<CommandRecord> future = new CompletableFuture<>();
        pendingFutures.put(cmdId, future);

        try {
            record = future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            record.setStatus("TIMEOUT");
            record.setResponse("等待 Agent 响应超时 (" + DEFAULT_TIMEOUT_SECONDS + "s)");
            record.setFinishTime(new Date());
            commandRecordMapper.updateById(record);
            log.warn("[Command] 指令超时: cmdId={}, agentId={}", cmdId, agentId);
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setResponse("等待响应异常: " + e.getMessage());
            record.setFinishTime(new Date());
            commandRecordMapper.updateById(record);
            log.error("[Command] 等待响应异常: cmdId={}", cmdId, e);
        } finally {
            pendingFutures.remove(cmdId);
        }

        return record;
    }

    /**
     * 处理 Agent 回传的指令执行结果（由 AgentWebSocketHandler 调用）
     */
    public void handleResponse(String agentId, String cmdId, boolean success, String output) {
        log.info("[Command] 收到指令响应: agentId={}, cmdId={}, success={}", agentId, cmdId, success);

        // 1. 更新 DB 记录
        LambdaQueryWrapper<CommandRecord> wrapper = new LambdaQueryWrapper<CommandRecord>()
                .eq(CommandRecord::getCmdId, cmdId);
        CommandRecord record = commandRecordMapper.selectOne(wrapper);

        if (record == null) {
            log.warn("[Command] 未找到指令记录: cmdId={}", cmdId);
            return;
        }

        record.setStatus(success ? "SUCCESS" : "FAILED");
        record.setResponse(output);
        record.setFinishTime(new Date());
        commandRecordMapper.updateById(record);

        // 2. 完成 Future，唤醒等待中的 HTTP 请求
        CompletableFuture<CommandRecord> future = pendingFutures.remove(cmdId);
        if (future != null) {
            future.complete(record);
        }
    }

    /**
     * 查询指令历史
     */
    public List<CommandRecord> listRecords(String agentId) {
        LambdaQueryWrapper<CommandRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(CommandRecord::getAgentId, agentId);
        }
        wrapper.orderByDesc(CommandRecord::getCreateTime);
        wrapper.last("LIMIT 100");
        return commandRecordMapper.selectList(wrapper);
    }

    /**
     * 查询单条指令详情
     */
    public CommandRecord getRecord(String cmdId) {
        LambdaQueryWrapper<CommandRecord> wrapper = new LambdaQueryWrapper<CommandRecord>()
                .eq(CommandRecord::getCmdId, cmdId);
        return commandRecordMapper.selectOne(wrapper);
    }

    /**
     * 透传原始 JSON 到 Agent
     */
    private boolean sendRawContent(String agentId, String content) {
        return sessionManager.getSession(agentId).map(session -> {
            try {
                session.sendMessage(new TextMessage(content));
                log.info("[Command] 指令已透传: agentId={}, length={}", agentId, content.length());
                return true;
            } catch (Exception e) {
                log.error("[Command] 指令透传失败: agentId={}", agentId, e);
                return false;
            }
        }).orElseGet(() -> {
            log.warn("[Command] Agent 不在线: agentId={}", agentId);
            return false;
        });
    }
}
