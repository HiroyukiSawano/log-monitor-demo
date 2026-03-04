package com.example.demo.module.metrics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 指标快照主实体 — 每次 METRICS 上报存储一条完整记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_metrics_snapshot")
public class MetricsSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采集端 ID */
    private String agentId;

    /** 主机名 */
    private String mainframeName;

    /** 操作系统名称 */
    private String osName;

    /** 操作系统版本 */
    private String osVersion;

    /** 操作系统类型 */
    private String osType;

    /** CPU 型号 */
    private String cpuType;

    /** CPU 主频 */
    private String cpuSpeed;

    /** CPU 使用率 (如 "6%") */
    private String cpuUsage;

    /** CPU 核心数 */
    private String cpuCores;

    /** 内存总量 */
    private String ramCapacity;

    /** 已用内存 */
    private String ramUsage;

    /** 可用内存 */
    private String ramAvailable;

    /** 内存频率 */
    private String ramSpeed;

    /** 内存类型 */
    private String ramType;

    /** 内存厂商 */
    private String ramManufacturer;

    /** 主板型号 */
    private String mainBoard;

    /** 硬盘型号 */
    private String disk;

    /** GPU 列表 (|分隔) */
    private String gpu;

    /** 磁盘总容量(MB) */
    private String totalDiskCapacity;

    /** 磁盘可用容量(MB) */
    private String totalAvailableCapacityDisk;

    /** 进程总数 */
    private Integer processCount;

    /** Agent 上报时间字符串 */
    private String feedbackTime;

    /** Agent 上报时间戳(ms) */
    private Long reportTimestamp;

    /** 入库时间 */
    private Date createTime;

    // ---- 非持久化字段，用于 Service 层组装返回 ----
    @TableField(exist = false)
    private List<DiskPartition> parts;

    @TableField(exist = false)
    private List<ProcessStatus> processStatusList;

    @TableField(exist = false)
    private Boolean online;
}
