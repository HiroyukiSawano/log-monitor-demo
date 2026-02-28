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

    /** 查询所有规则 */
    @GetMapping
    public Result<List<FilterRule>> list() {
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
        filterRuleService.rebuildMatchers();
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<FilterRule> update(@PathVariable Long id, @RequestBody FilterRule rule) {
        rule.setId(id);
        FilterRule updated = filterRuleService.update(rule);
        filterRuleService.rebuildMatchers();
        return Result.success(updated);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        filterRuleService.delete(id);
        filterRuleService.rebuildMatchers();
        return Result.success(null);
    }

    /** 手动触发匹配器重建 */
    @PostMapping("/rebuild")
    public Result<String> rebuild() {
        filterRuleService.rebuildMatchers();
        return Result.success("匹配器重建完成");
    }
}
