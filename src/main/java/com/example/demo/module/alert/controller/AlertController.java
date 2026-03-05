package com.example.demo.module.alert.controller;

import com.example.demo.common.result.Result;
import com.example.demo.module.alert.entity.AlertEvent;
import com.example.demo.module.alert.entity.AlertRule;
import com.example.demo.module.alert.service.AlertRuleService;
import com.example.demo.monitor.alert.AlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警规则 + 告警事件 REST API
 */
@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final AlertRuleService ruleService;
    private final AlertService alertService;

    public AlertController(AlertRuleService ruleService, AlertService alertService) {
        this.ruleService = ruleService;
        this.alertService = alertService;
    }

    // ==================== 规则管理 ====================

    /** 获取规则列表（可按 agentId 过滤） */
    @GetMapping("/rules")
    public Result<List<AlertRule>> listRules(@RequestParam(required = false) String agentId) {
        if (agentId != null && !agentId.isEmpty()) {
            return Result.success(ruleService.listByAgentOnly(agentId));
        }
        return Result.success(ruleService.listAll());
    }

    /** 获取某 Agent 适用的全部规则（含全局） */
    @GetMapping("/rules/applicable")
    public Result<List<AlertRule>> applicableRules(@RequestParam String agentId) {
        return Result.success(ruleService.listByAgent(agentId));
    }

    /** 创建规则 */
    @PostMapping("/rules")
    public Result<AlertRule> createRule(@RequestBody AlertRule rule) {
        return Result.success(ruleService.create(rule));
    }

    /** 更新规则 */
    @PutMapping("/rules/{id}")
    public Result<AlertRule> updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        AlertRule updated = ruleService.update(id, rule);
        if (updated == null)
            return Result.error("规则不存在: " + id);
        return Result.success(updated);
    }

    /** 删除规则 */
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleService.delete(id);
        return Result.success(null);
    }

    // ==================== 告警事件 ====================

    /** 获取告警事件列表 */
    @GetMapping("/events")
    public Result<List<AlertEvent>> listEvents(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        return Result.success(alertService.listEvents(agentId, acknowledged, limit));
    }

    /** 获取未确认的告警（Dashboard 公告栏用） */
    @GetMapping("/events/unacknowledged")
    public Result<List<AlertEvent>> unacknowledged(
            @RequestParam(required = false) String agentId) {
        return Result.success(alertService.getUnacknowledged(agentId));
    }

    /** 确认(消除)单条告警 */
    @PostMapping("/events/{id}/ack")
    public Result<Void> acknowledgeEvent(@PathVariable Long id) {
        alertService.acknowledge(id);
        return Result.success(null);
    }

    /** 批量确认某 Agent 的全部告警 */
    @PostMapping("/events/ack-all")
    public Result<Void> acknowledgeAll(@RequestParam(required = false) String agentId) {
        alertService.acknowledgeAll(agentId);
        return Result.success(null);
    }
}
