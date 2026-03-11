package com.example.demo.module.rule.controller;

import com.example.demo.common.result.Result;
import com.example.demo.module.rule.entity.FilterRule;
import com.example.demo.module.rule.service.FilterRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 过滤规则管理 RESTful 接口
 */
@RestController
@RequestMapping("/api/rules")
public class FilterRuleController {

    private final FilterRuleService filterRuleService;

    public FilterRuleController(FilterRuleService filterRuleService) {
        this.filterRuleService = filterRuleService;
    }

    /** 查询所有规则，支持可选 agentId 过滤 */
    @GetMapping
    public Result<List<FilterRule>> list(@RequestParam(required = false) String agentId) {
        if (agentId != null && !agentId.isEmpty()) {
            return Result.success(filterRuleService.listByAgentId(agentId));
        }
        return Result.success(filterRuleService.listAll());
    }

    /** 按类型查询启用的规则 */
    @GetMapping("/type/{ruleType}")
    public Result<List<FilterRule>> listByType(@PathVariable String ruleType) {
        return Result.success(filterRuleService.listEnabledByType(ruleType));
    }

    @PostMapping
    public Result<FilterRule> create(@RequestBody FilterRule rule) {
        FilterRule created = filterRuleService.create(rule);
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<FilterRule> update(@PathVariable Long id, @RequestBody FilterRule rule) {
        rule.setId(id);
        FilterRule updated = filterRuleService.update(rule);
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        filterRuleService.delete(id);
        return Result.success(null);
    }

    /** 手动触发全部匹配器重建 */
    @PostMapping("/rebuild")
    public Result<String> rebuild() {
        filterRuleService.rebuildAllMatchers();
        return Result.success("全部匹配器重建完成");
    }

    /** 手动触发指定 Agent 匹配器重建 */
    @PostMapping("/rebuild/{agentId}")
    public Result<String> rebuildForAgent(@PathVariable String agentId) {
        filterRuleService.rebuildMatchersForAgent(agentId);
        return Result.success("匹配器重建完成: " + agentId);
    }
}
