package com.example.demo.module.rule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.module.rule.entity.FilterRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 过滤规则 Mapper
 */
@Mapper
public interface FilterRuleMapper extends BaseMapper<FilterRule> {
}
