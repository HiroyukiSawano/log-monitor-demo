package com.example.demo.module.metrics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 磁盘分区子实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_disk_partition")
public class DiskPartition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联快照 ID */
    private Long snapshotId;

    /** 挂载点/盘符 */
    private String mountPoint;

    /** 分区总容量(MB) */
    private String capacity;

    /** 分区可用容量(MB) */
    private String availableCapacity;
}
