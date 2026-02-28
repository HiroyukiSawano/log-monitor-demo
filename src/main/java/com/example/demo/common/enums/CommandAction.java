package com.example.demo.common.enums;

/**
 * Server → Agent 控制指令动作枚举
 */
public enum CommandAction {
    /** 立即获取系统指标 */
    GET_METRICS,
    /** 浏览指定目录 */
    LIST_DIR,
    /** 读取配置文件内容 */
    READ_FILE,
    /** 写入/修改配置文件 */
    WRITE_FILE,
    /** 重启应用进程 */
    RESTART_APP,
    /** 执行 Shell/CMD 命令 */
    EXEC_CMD,
    /** 开始监听指定日志文件 */
    START_LOG_WATCH,
    /** 停止监听指定日志文件 */
    STOP_LOG_WATCH
}
