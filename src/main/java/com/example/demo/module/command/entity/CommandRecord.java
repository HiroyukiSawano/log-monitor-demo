package com.example.demo.module.command.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 远程指令记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_command_record")
public class CommandRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指令唯一标识 (UUID) */
    private String cmdId;

    /** 目标采集端 ID */
    private String agentId;

    /** 指令动作 (CommandAction 枚举值) */
    private String action;

    /** 指令参数 (JSON 格式) */
    private String params;

    /** 指令状态：PENDING / SUCCESS / FAILED / TIMEOUT */
    private String status;

    /** Agent 执行反馈内容 */
    private String response;

    /** 下发时间 */
    private Date createTime;

    /** 完成时间 */
    private Date finishTime;
}
