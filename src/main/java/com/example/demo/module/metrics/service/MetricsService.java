package com.example.demo.module.metrics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.module.metrics.entity.DiskPartition;
import com.example.demo.module.metrics.entity.MetricsSnapshot;
import com.example.demo.module.metrics.entity.ProcessStatus;
import com.example.demo.module.metrics.mapper.DiskPartitionMapper;
import com.example.demo.module.metrics.mapper.MetricsSnapshotMapper;
import com.example.demo.module.metrics.mapper.ProcessStatusMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 指标数据服务 — 解析 METRICS payload 并持久化
 */
@Slf4j
@Service
public class MetricsService {

    private final MetricsSnapshotMapper snapshotMapper;
    private final DiskPartitionMapper diskPartitionMapper;
    private final ProcessStatusMapper processStatusMapper;

    public MetricsService(MetricsSnapshotMapper snapshotMapper,
            DiskPartitionMapper diskPartitionMapper,
            ProcessStatusMapper processStatusMapper) {
        this.snapshotMapper = snapshotMapper;
        this.diskPartitionMapper = diskPartitionMapper;
        this.processStatusMapper = processStatusMapper;
    }

    /**
     * 解析 METRICS payload 并入库
     *
     * @return 保存后的 MetricsSnapshot (含 id)
     */
    public MetricsSnapshot saveMetrics(String agentId, JsonNode payload, Long timestamp) {
        MetricsSnapshot snapshot = MetricsSnapshot.builder()
                .agentId(agentId)
                .mainframeName(textVal(payload, "mainframeName"))
                .osName(textVal(payload, "osName"))
                .osVersion(textVal(payload, "osVersion"))
                .osType(textVal(payload, "osType"))
                .cpuType(textVal(payload, "cpuType"))
                .cpuSpeed(textVal(payload, "cpuSpeed"))
                .cpuUsage(textVal(payload, "cpuUsage"))
                .cpuCores(textVal(payload, "cpuCores"))
                .ramCapacity(textVal(payload, "ramCapacity"))
                .ramUsage(textVal(payload, "ramUsage"))
                .ramAvailable(textVal(payload, "ramAvailable"))
                .ramSpeed(textVal(payload, "ramSpeed"))
                .ramType(textVal(payload, "ramType"))
                .ramManufacturer(textVal(payload, "ramManufacturer"))
                .mainBoard(textVal(payload, "mainBoard"))
                .disk(textVal(payload, "disk"))
                .gpu(textVal(payload, "gpu"))
                .totalDiskCapacity(textVal(payload, "totalDiskCapacity"))
                .totalAvailableCapacityDisk(textVal(payload, "totalAvailableCapacityDisk"))
                .processCount(payload.path("processCount").asInt(0))
                .feedbackTime(textVal(payload, "terminalMainframeInfoFeedbackTime"))
                .reportTimestamp(timestamp)
                .createTime(new Date())
                .build();

        try {
            snapshotMapper.insert(snapshot);
            Long snapshotId = snapshot.getId();

            // 磁盘分区
            JsonNode parts = payload.path("parts");
            if (parts.isArray()) {
                for (JsonNode p : parts) {
                    DiskPartition dp = DiskPartition.builder()
                            .snapshotId(snapshotId)
                            .mountPoint(textVal(p, "mountPoint"))
                            .capacity(textVal(p, "capacity"))
                            .availableCapacity(textVal(p, "availableCapacity"))
                            .build();
                    diskPartitionMapper.insert(dp);
                }
            }

            // 进程状态
            JsonNode psList = payload.path("processStatus");
            if (psList.isArray()) {
                for (JsonNode ps : psList) {
                    ProcessStatus status = ProcessStatus.builder()
                            .snapshotId(snapshotId)
                            .processName(textVal(ps, "processName"))
                            .status(textVal(ps, "status"))
                            .build();
                    processStatusMapper.insert(status);
                }
            }

            log.info("[Metrics] 指标快照已入库: agentId={}, snapshotId={}", agentId, snapshotId);
        } catch (Exception e) {
            log.error("[Metrics] 指标入库失败: agentId={}", agentId, e);
        }

        return snapshot;
    }

    /**
     * 获取某 Agent 最新一条快照（含分区与进程）
     */
    public MetricsSnapshot getLatestByAgent(String agentId) {
        LambdaQueryWrapper<MetricsSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricsSnapshot::getAgentId, agentId)
                .orderByDesc(MetricsSnapshot::getId)
                .last("LIMIT 1");
        MetricsSnapshot snapshot = snapshotMapper.selectOne(wrapper);
        if (snapshot != null) {
            fillChildren(snapshot);
        }
        return snapshot;
    }

    /**
     * 获取所有 Agent 的最新快照
     * (按 agentId 分组取最新的一条)
     */
    public List<MetricsSnapshot> getAllLatest() {
        // 取全部快照按 id 倒序
        LambdaQueryWrapper<MetricsSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MetricsSnapshot::getId);
        List<MetricsSnapshot> all = snapshotMapper.selectList(wrapper);

        // 按 agentId 去重保留最新
        Map<String, MetricsSnapshot> map = new LinkedHashMap<>();
        for (MetricsSnapshot s : all) {
            if (!map.containsKey(s.getAgentId())) {
                map.put(s.getAgentId(), s);
            }
        }

        List<MetricsSnapshot> result = new ArrayList<>(map.values());
        for (MetricsSnapshot s : result) {
            fillChildren(s);
        }
        return result;
    }

    /**
     * 获取指定 Agent 的所有不同 agentId（用于发现所有曾上报过的 Agent）
     */
    public List<String> getAllKnownAgentIds() {
        LambdaQueryWrapper<MetricsSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(MetricsSnapshot::getAgentId)
                .groupBy(MetricsSnapshot::getAgentId);
        List<MetricsSnapshot> list = snapshotMapper.selectList(wrapper);
        List<String> ids = new ArrayList<>();
        for (MetricsSnapshot s : list) {
            ids.add(s.getAgentId());
        }
        return ids;
    }

    private void fillChildren(MetricsSnapshot snapshot) {
        Long sid = snapshot.getId();
        snapshot.setParts(diskPartitionMapper.selectList(
                new LambdaQueryWrapper<DiskPartition>().eq(DiskPartition::getSnapshotId, sid)));
        snapshot.setProcessStatusList(processStatusMapper.selectList(
                new LambdaQueryWrapper<ProcessStatus>().eq(ProcessStatus::getSnapshotId, sid)));
    }

    private String textVal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
