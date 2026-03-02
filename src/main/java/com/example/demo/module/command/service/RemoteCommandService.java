package com.example.demo.module.command.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.command.entity.CommandRecord;
import com.example.demo.module.command.mapper.CommandRecordMapper;
import com.example.demo.websocket.session.AgentSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.*;
import java.util.concurrent.*;

/**
 * 远程指令服务 — 下发指令到 Agent 并异步等待反馈
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
     * 发送指令到 Agent 并同步等待响应（最多 30 秒）
     *
     * @param agentId 目标 Agent
     * @param action  指令动作
     * @param params  指令参数（JSON 字符串）
     * @return 指令记录（含执行结果）
     */
    public CommandRecord sendCommand(String agentId, String action, String params) {
        // 1. 生成指令记录
        String cmdId = UUID.randomUUID().toString();
        CommandRecord record = CommandRecord.builder()
                .cmdId(cmdId)
                .agentId(agentId)
                .action(action)
                .params(params)
                .status("PENDING")
                .createTime(new Date())
                .build();
        commandRecordMapper.insert(record);

        // 2. 构建并发送 CMD_REQUEST 消息
        boolean sent = sendCmdRequest(agentId, cmdId, action, params);
        if (!sent) {
            record.setStatus("FAILED");
            record.setResponse("Agent 不在线或发送失败");
            record.setFinishTime(new Date());
            commandRecordMapper.updateById(record);
            return record;
        }

        // 3. 注册 Future 并等待响应
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
     * 处理 Agent 回传的指令执行结果（由 MessageDispatcher 调用）
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
     * 通过 WebSocket 发送 CMD_REQUEST 到 Agent
     */
    private boolean sendCmdRequest(String agentId, String cmdId, String action, String params) {
        return sessionManager.getSession(agentId).map(session -> {
            try {
                Map<String, Object> message = new LinkedHashMap<>();
                message.put("type", "CMD_REQUEST");
                message.put("agentId", agentId);
                message.put("timestamp", System.currentTimeMillis());

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("cmdId", cmdId);
                payload.put("action", action);
                if (params != null && !params.isEmpty()) {
                    // 尝试将 params 作为 JSON 对象解析，失败则作为字符串
                    try {
                        payload.put("params", objectMapper.readTree(params));
                    } catch (Exception e) {
                        payload.put("params", params);
                    }
                }
                message.put("payload", payload);

                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                log.info("[Command] 指令已下发: cmdId={}, agentId={}, action={}", cmdId, agentId, action);
                return true;
            } catch (Exception e) {
                log.error("[Command] 指令下发失败: cmdId={}, agentId={}", cmdId, agentId, e);
                return false;
            }
        }).orElseGet(() -> {
            log.warn("[Command] Agent 不在线: agentId={}", agentId);
            return false;
        });
    }
}
