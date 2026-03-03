#!/bin/bash
# shutdown.sh

PID_FILE="./app.pid"

# 检查 PID 文件是否存在
if [ ! -f "$PID_FILE" ]; then
    echo "Error: $PID_FILE not found. Is the application running?"
    exit 1
fi

# 读取 PID
pid=$(cat "$PID_FILE")

# 检查该 PID 是否对应一个正在运行的进程
if ! ps -p "$pid" > /dev/null 2>&1; then
    echo "Warning: Process $pid is not running. Cleaning up stale PID file."
    rm -f "$PID_FILE"
    exit 0
fi

echo "Stopping application (PID=$pid)..."
# 发送 SIGTERM 优雅退出
kill "$pid"

# 轮询等待进程真正退出 (最多等待 30 秒)
count=0
while ps -p "$pid" > /dev/null 2>&1; do
    echo -n "."
    sleep 1
    count=$((count+1))
    if [ "$count" -ge 30 ]; then
        echo -e "\nApplication (PID=$pid) did not stop within 30 seconds."
        echo "Forcing termination with SIGKILL..."
        kill -9 "$pid"
        break
    fi
done

echo -e "\nApplication stopped."
# 清理 PID 文件
rm -f "$PID_FILE"
