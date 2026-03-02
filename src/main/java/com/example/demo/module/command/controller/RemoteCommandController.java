package com.example.demo.module.command.controller;

import com.example.demo.common.result.Result;
import com.example.demo.module.command.entity.CommandRecord;
import com.example.demo.module.command.service.RemoteCommandService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 远程指令 RESTful 接口
 */
@RestController
@RequestMapping("/api/commands")
public class RemoteCommandController {

    private final RemoteCommandService remoteCommandService;

    public RemoteCommandController(RemoteCommandService remoteCommandService) {
        this.remoteCommandService = remoteCommandService;
    }

    /**
     * 发送指令到 Agent 并等待响应
     * <p>
     * 请求体示例：
     * { "agentId": "server-01", "action": "EXEC_CMD", "params": "{\"cmd\": \"df
     * -h\"}" }
     */
    @PostMapping("/send")
    public Result<CommandRecord> send(@RequestBody Map<String, String> body) {
        String agentId = body.get("agentId");
        String action = body.get("action");
        String params = body.get("params");

        if (agentId == null || agentId.isEmpty()) {
            return Result.error("agentId 不能为空");
        }
        if (action == null || action.isEmpty()) {
            return Result.error("action 不能为空");
        }

        CommandRecord record = remoteCommandService.sendCommand(agentId, action, params);
        return Result.success(record);
    }

    /**
     * 查询指令历史（可选 agentId 过滤）
     */
    @GetMapping
    public Result<List<CommandRecord>> list(@RequestParam(required = false) String agentId) {
        return Result.success(remoteCommandService.listRecords(agentId));
    }

    /**
     * 查询单条指令详情
     */
    @GetMapping("/{cmdId}")
    public Result<CommandRecord> get(@PathVariable String cmdId) {
        CommandRecord record = remoteCommandService.getRecord(cmdId);
        if (record == null) {
            return Result.error("指令记录不存在: " + cmdId);
        }
        return Result.success(record);
    }
}
