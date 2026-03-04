package com.example.demo.module.metrics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进程状态子实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_process_status")
public class ProcessStatus {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联快照 ID */
    private Long snapshotId;

    /** 进程/应用名称 */
    private String processName;

    /** 状态: 正常 / 异常 */
    private String status;
}
