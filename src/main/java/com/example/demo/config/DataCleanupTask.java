package com.example.demo.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.alert.entity.AlertEvent;
import com.example.demo.module.alert.mapper.AlertEventMapper;
import com.example.demo.module.loghit.entity.LogHitRecord;
import com.example.demo.module.loghit.mapper.LogHitRecordMapper;
import com.example.demo.module.metrics.entity.DiskPartition;
import com.example.demo.module.metrics.entity.MetricsSnapshot;
import com.example.demo.module.metrics.entity.ProcessStatus;
import com.example.demo.module.metrics.mapper.DiskPartitionMapper;
import com.example.demo.module.metrics.mapper.MetricsSnapshotMapper;
import com.example.demo.module.metrics.mapper.ProcessStatusMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据清理定时任务 — 防止时序表无限膨胀
 * <p>
 * 按可配置的保留天数定期删除过期数据:
 * <ul>
 *   <li>t_metrics_snapshot + 子表 (t_disk_partition, t_process_status)</li>
 *   <li>t_log_hit_record</li>
 *   <li>t_alert_event (仅已确认的)</li>
 * </ul>
 */
@Slf4j
@Component
public class DataCleanupTask {

    private final MetricsSnapshotMapper snapshotMapper;
    private final DiskPartitionMapper diskPartitionMapper;
    private final ProcessStatusMapper processStatusMapper;
    private final LogHitRecordMapper logHitRecordMapper;
    private final AlertEventMapper alertEventMapper;

    @Value("${cleanup.retention.metrics-days:7}")
    private int metricsDays;

    @Value("${cleanup.retention.loghit-days:30}")
    private int loghitDays;

    @Value("${cleanup.retention.alert-days:90}")
    private int alertDays;

    public DataCleanupTask(MetricsSnapshotMapper snapshotMapper,
                           DiskPartitionMapper diskPartitionMapper,
                           ProcessStatusMapper processStatusMapper,
                           LogHitRecordMapper logHitRecordMapper,
                           AlertEventMapper alertEventMapper) {
        this.snapshotMapper = snapshotMapper;
        this.diskPartitionMapper = diskPartitionMapper;
        this.processStatusMapper = processStatusMapper;
        this.logHitRecordMapper = logHitRecordMapper;
        this.alertEventMapper = alertEventMapper;
    }

    /**
     * 每小时执行一次清理（可通过 cleanup.cron 覆盖）
     */
    @Scheduled(cron = "${cleanup.cron:0 0 * * * ?}")
    public void cleanupExpiredData() {
        log.info("[Cleanup] 开始清理过期数据...");

        int metricsCount = cleanupMetrics();
        int loghitCount = cleanupLogHits();
        int alertCount = cleanupAlertEvents();

        log.info("[Cleanup] 清理完成: 指标快照={}, 命中记录={}, 告警事件={}",
                metricsCount, loghitCount, alertCount);
    }

    /**
     * 清理过期指标快照及其子表
     */
    private int cleanupMetrics() {
        Date cutoff = daysAgo(metricsDays);

        // 1. 找出过期的快照 ID
        LambdaQueryWrapper<MetricsSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(MetricsSnapshot::getCreateTime, cutoff)
               .select(MetricsSnapshot::getId);
        List<MetricsSnapshot> expired = snapshotMapper.selectList(wrapper);

        if (expired.isEmpty()) return 0;

        List<Long> ids = expired.stream().map(MetricsSnapshot::getId).collect(Collectors.toList());

        // 2. 批量删除子表 (分批防止 IN 子句过长)
        int batchSize = 500;
        for (int i = 0; i < ids.size(); i += batchSize) {
            List<Long> batch = ids.subList(i, Math.min(i + batchSize, ids.size()));
            diskPartitionMapper.delete(
                    new LambdaQueryWrapper<DiskPartition>().in(DiskPartition::getSnapshotId, batch));
            processStatusMapper.delete(
                    new LambdaQueryWrapper<ProcessStatus>().in(ProcessStatus::getSnapshotId, batch));
        }

        // 3. 删除主表
        snapshotMapper.delete(
                new LambdaQueryWrapper<MetricsSnapshot>().lt(MetricsSnapshot::getCreateTime, cutoff));

        return ids.size();
    }

    /**
     * 清理过期命中记录
     */
    private int cleanupLogHits() {
        Date cutoff = daysAgo(loghitDays);
        return logHitRecordMapper.delete(
                new LambdaQueryWrapper<LogHitRecord>().lt(LogHitRecord::getCreateTime, cutoff));
    }

    /**
     * 清理过期且已确认的告警事件
     */
    private int cleanupAlertEvents() {
        Date cutoff = daysAgo(alertDays);
        return alertEventMapper.delete(
                new LambdaQueryWrapper<AlertEvent>()
                        .lt(AlertEvent::getCreateTime, cutoff)
                        .eq(AlertEvent::getAcknowledged, true));
    }

    private Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * 24L * 3600 * 1000);
    }
}
