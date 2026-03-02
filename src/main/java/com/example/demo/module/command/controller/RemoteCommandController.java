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
     * 发送指令（透传模式）
     * content 原样下发到 Agent，Server 从中提取 cmdID 用于追踪
     */
    @PostMapping("/send")
    public Result<CommandRecord> send(@RequestBody Map<String, String> body) {
        String agentId = body.get("agentId");
        String content = body.get("content");
        if (agentId == null || agentId.isEmpty()) {
            return Result.error("agentId 不能为空");
        }
        if (content == null || content.isEmpty()) {
            return Result.error("content 不能为空");
        }
        CommandRecord record = remoteCommandService.sendCommand(agentId, content);
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
