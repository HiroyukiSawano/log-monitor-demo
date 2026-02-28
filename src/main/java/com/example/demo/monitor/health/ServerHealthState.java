package com.example.demo.monitor.health;

import com.example.demo.common.enums.HealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器健康状态快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerHealthState {
    /** Agent 标识 */
    private String agentId;
    /** 当前健康状态 */
    private HealthStatus status;
    /** 状态判定原因 */
    private String reason;
    /** 当前 CPU 使用率 */
    private double cpuUsage;
    /** 当前磁盘使用率 */
    private double diskUsage;
    /** CPU > 80% 的持续起始时间 (epoch ms, 0 = 未持续) */
    private long cpuHighSinceMs;
    /** 最近命中的强关注日志片段 */
    @Builder.Default
    private List<String> criticalLogs = new ArrayList<>();
}
