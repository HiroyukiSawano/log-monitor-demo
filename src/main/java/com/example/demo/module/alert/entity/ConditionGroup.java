package com.example.demo.module.alert.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 条件分组 — 组内条件用 logic 连接
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionGroup {

    /** 组内逻辑: AND / OR */
    private String logic;

    /** 组内条件列表 */
    private List<AlertCondition> items;
}
