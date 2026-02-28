package com.example.demo.monitor.health;

import com.example.demo.common.enums.HealthStatus;
import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.model.FilterResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 健康状态评估器 — 根据指标和日志过滤结果计算 Agent 的健康状态
 *
 * <pre>
 * 🔴 RED 条件（任一满足）：
 *   - CPU 持续 > 80% 超过 10 分钟
 *   - 出现 CRITICAL 级别日志
 *
 * 🟡 YELLOW 条件：
 *   - CPU 在 30% ~ 80%
 *   - 出现 UNKNOWN_ERROR 级别日志
 *
 * 🟢 GREEN 条件（全部满足）：
 *   - 无 CRITICAL / UNKNOWN_ERROR 日志
 *   - CPU < 30%
 *   - 磁盘 < 70%
 * </pre>
 */
@Slf4j
@Component
public class HealthEvaluator {

    private static final long CPU_HIGH_THRESHOLD_MS = 10 * 60 * 1000L; // 10 分钟
    private static final double CPU_RED_THRESHOLD = 80.0;
    private static final double CPU_YELLOW_THRESHOLD = 30.0;
    private static final double DISK_GREEN_THRESHOLD = 70.0;
    private static final int MAX_CRITICAL_LOGS = 20;

    private final HealthStateStore stateStore;

    public HealthEvaluator(HealthStateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * 更新 CPU/Disk 指标并重新评估状态
     */
    public ServerHealthState updateMetrics(String agentId, double cpuUsage, double diskUsage) {
        ServerHealthState state = stateStore.getOrCreate(agentId);
        state.setCpuUsage(cpuUsage);
        state.setDiskUsage(diskUsage);

        // 追踪 CPU 持续高位
        if (cpuUsage > CPU_RED_THRESHOLD) {
            if (state.getCpuHighSinceMs() == 0) {
                state.setCpuHighSinceMs(System.currentTimeMillis());
            }
        } else {
            state.setCpuHighSinceMs(0);
        }

        evaluate(state);
        stateStore.put(agentId, state);
        return state;
    }

    /**
     * 处理日志过滤结果并更新健康状态
     */
    public ServerHealthState processLogResult(String agentId, FilterResult filterResult, String logSnippet) {
        ServerHealthState state = stateStore.getOrCreate(agentId);

        if (filterResult.getLevel() == LogLevel.CRITICAL) {
            // 保留最近的强关注日志
            if (state.getCriticalLogs().size() >= MAX_CRITICAL_LOGS) {
                state.getCriticalLogs().remove(0);
            }
            state.getCriticalLogs().add(logSnippet);
        }

        evaluate(state);
        stateStore.put(agentId, state);
        return state;
    }

    /**
     * 综合评估
     */
    private void evaluate(ServerHealthState state) {
        // 🔴 RED 检查
        if (!state.getCriticalLogs().isEmpty()) {
            state.setStatus(HealthStatus.RED);
            state.setReason("存在强关注日志命中");
            return;
        }
        if (state.getCpuHighSinceMs() > 0) {
            long duration = System.currentTimeMillis() - state.getCpuHighSinceMs();
            if (duration >= CPU_HIGH_THRESHOLD_MS) {
                state.setStatus(HealthStatus.RED);
                state.setReason(String.format("CPU 持续 > 80%% 已超过 %d 分钟", duration / 60000));
                return;
            }
        }

        // 🟡 YELLOW 检查
        if (state.getCpuUsage() >= CPU_YELLOW_THRESHOLD) {
            state.setStatus(HealthStatus.YELLOW);
            state.setReason(String.format("CPU 使用率偏高: %.1f%%", state.getCpuUsage()));
            return;
        }

        // 🟢 GREEN — 全部正常
        if (state.getDiskUsage() < DISK_GREEN_THRESHOLD) {
            state.setStatus(HealthStatus.GREEN);
            state.setReason("一切正常");
        } else {
            state.setStatus(HealthStatus.YELLOW);
            state.setReason(String.format("磁盘使用率偏高: %.1f%%", state.getDiskUsage()));
        }
    }
}
