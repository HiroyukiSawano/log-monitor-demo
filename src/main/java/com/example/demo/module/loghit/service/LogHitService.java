package com.example.demo.module.loghit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.common.enums.LogLevel;
import com.example.demo.engine.model.FilterResult;
import com.example.demo.engine.model.LogContext;
import com.example.demo.module.loghit.entity.LogHitRecord;
import com.example.demo.module.loghit.mapper.LogHitRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 日志命中记录服务
 */
@Slf4j
@Service
public class LogHitService {

    private final LogHitRecordMapper logHitRecordMapper;

    public LogHitService(LogHitRecordMapper logHitRecordMapper) {
        this.logHitRecordMapper = logHitRecordMapper;
    }

    /**
     * 保存命中记录（仅 CRITICAL / UNKNOWN_ERROR 入库，EXCLUDED 和 NORMAL 不入库）
     */
    public void saveIfMatched(LogContext context, FilterResult result) {
        if (result.getLevel() == LogLevel.NORMAL || result.getLevel() == LogLevel.EXCLUDED) {
            return;
        }

        String logContent = context.getRawLine();
        if (logContent != null && logContent.length() > 2000) {
            logContent = logContent.substring(0, 2000);
        }

        LogHitRecord record = LogHitRecord.builder()
                .agentId(context.getAgentId())
                .appName(context.getAppName())
                .logPath(context.getLogPath())
                .level(result.getLevel().name())
                .matchedRuleName(result.getMatchedRuleName())
                .matchedKeyword(result.getMatchedKeyword())
                .logContent(logContent)
                .logTime(new Date(context.getTimestamp()))
                .createTime(new Date())
                .build();

        try {
            logHitRecordMapper.insert(record);
            log.debug("[LogHit] 命中记录已入库: agentId={}, level={}, rule={}",
                    context.getAgentId(), result.getLevel(), result.getMatchedRuleName());
        } catch (Exception e) {
            log.error("[LogHit] 命中记录入库失败", e);
        }
    }

    /**
     * 查询命中记录（支持 agentId、level 过滤）
     */
    public List<LogHitRecord> listRecords(String agentId, String level, Integer limit) {
        LambdaQueryWrapper<LogHitRecord> wrapper = new LambdaQueryWrapper<>();
        if (agentId != null && !agentId.isEmpty()) {
            wrapper.eq(LogHitRecord::getAgentId, agentId);
        }
        if (level != null && !level.isEmpty()) {
            wrapper.eq(LogHitRecord::getLevel, level);
        }
        wrapper.orderByDesc(LogHitRecord::getCreateTime);
        int maxRows = (limit != null && limit > 0 && limit <= 500) ? limit : 100;
        wrapper.last("LIMIT " + maxRows);
        return logHitRecordMapper.selectList(wrapper);
    }

    /**
     * 查询单条详情
     */
    public LogHitRecord getById(Long id) {
        return logHitRecordMapper.selectById(id);
    }

    /**
     * 统计各级别命中数量
     */
    public long countByLevel(String level) {
        LambdaQueryWrapper<LogHitRecord> wrapper = new LambdaQueryWrapper<LogHitRecord>()
                .eq(LogHitRecord::getLevel, level);
        return logHitRecordMapper.selectCount(wrapper);
    }

    /**
     * 统计最近 sinceSec 秒内指定 Agent 的命中数量
     *
     * @param agentId  采集端 ID
     * @param level    可选，按级别过滤 (CRITICAL / UNKNOWN_ERROR)，null 表示所有级别
     * @param sinceSec 时间窗口（秒），0 或负数表示不限时间
     */
    public long countRecentByAgent(String agentId, String level, int sinceSec) {
        LambdaQueryWrapper<LogHitRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogHitRecord::getAgentId, agentId);
        if (level != null && !level.isEmpty()) {
            wrapper.eq(LogHitRecord::getLevel, level);
        }
        if (sinceSec > 0) {
            Date since = new Date(System.currentTimeMillis() - sinceSec * 1000L);
            wrapper.ge(LogHitRecord::getCreateTime, since);
        }
        return logHitRecordMapper.selectCount(wrapper);
    }
}
