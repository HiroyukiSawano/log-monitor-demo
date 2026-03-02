package com.example.demo.module.loghit.controller;

import com.example.demo.common.result.Result;
import com.example.demo.module.loghit.entity.LogHitRecord;
import com.example.demo.module.loghit.service.LogHitService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日志命中记录查询接口
 */
@RestController
@RequestMapping("/api/loghits")
public class LogHitController {

    private final LogHitService logHitService;

    public LogHitController(LogHitService logHitService) {
        this.logHitService = logHitService;
    }

    /**
     * 查询命中记录列表
     *
     * @param agentId 可选，按 Agent 过滤
     * @param level   可选，按级别过滤 (CRITICAL / UNKNOWN_ERROR)
     * @param limit   可选，返回数量上限 (默认100，最大500)
     */
    @GetMapping
    public Result<List<LogHitRecord>> list(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer limit) {
        return Result.success(logHitService.listRecords(agentId, level, limit));
    }

    /**
     * 查询单条详情
     */
    @GetMapping("/{id}")
    public Result<LogHitRecord> get(@PathVariable Long id) {
        LogHitRecord record = logHitService.getById(id);
        if (record == null) {
            return Result.error("记录不存在: " + id);
        }
        return Result.success(record);
    }

    /**
     * 统计概览
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("CRITICAL", logHitService.countByLevel("CRITICAL"));
        stats.put("UNKNOWN_ERROR", logHitService.countByLevel("UNKNOWN_ERROR"));
        return Result.success(stats);
    }
}
