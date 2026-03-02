package com.example.demo.module.command.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.module.command.entity.CommandRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 远程指令记录 Mapper
 */
@Mapper
public interface CommandRecordMapper extends BaseMapper<CommandRecord> {
}
