#!/bin/bash
# startup.sh

APP_NAME="demo-0.0.1-SNAPSHOT.jar"
APP_PATH="./$APP_NAME"
LOG_FILE="./app.log"
PID_FILE="./app.pid"

# 默认不指定端口 (使用 application.yml 中的配置)
PORT=""

# 解析命令行参数 (例如: ./startup.sh -p 8081)
while getopts "p:" opt; do
  case $opt in
    p)
      PORT=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      echo "Usage: $0 [-p port]"
      exit 1
      ;;
  esac
done

# 检查可执行 jar 是否存在
if [ ! -f "$APP_PATH" ]; then
    echo "Error: $APP_PATH not found in the current directory!"
    exit 1
fi

# 检查应用是否已在运行
if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE")
    if ps -p "$pid" > /dev/null 2>&1; then
        echo "Error: Application is already running (PID=$pid). Please stop it first."
        exit 1
    else
        echo "Warning: Stale PID file found. Removing it..."
        rm -f "$PID_FILE"
    fi
fi

# 构造启动命令参数
JVM_OPTS="-Dfile.encoding=UTF-8"
APP_ARGS=""
if [ -n "$PORT" ]; then
    APP_ARGS="--server.port=$PORT"
    echo "Starting application $APP_NAME on port $PORT..."
else
    echo "Starting application $APP_NAME on default port..."
fi

# 使用 nohup 后台运行应用，并将 PID 保存到 app.pid 文件中
nohup java $JVM_OPTS -jar "$APP_PATH" $APP_ARGS > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "Application started!"
echo "PID: $(cat "$PID_FILE")"
echo "Logs are available in $LOG_FILE"
