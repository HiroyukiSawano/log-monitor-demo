#!/bin/bash
# startup.sh

APP_NAME="demo-0.0.1-SNAPSHOT.jar"
APP_PATH="./target/$APP_NAME"
LOG_FILE="./app.log"
PID_FILE="./app.pid"

# 检查可执行 jar 是否存在
if [ ! -f "$APP_PATH" ]; then
    echo "Error: $APP_PATH not found! Please run 'mvn clean package -DskipTests' first."
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

echo "Starting application $APP_NAME..."
# 使用 nohup 后台运行应用，并将 PID 保存到 app.pid 文件中
nohup java -jar "$APP_PATH" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "Application started!"
echo "PID: $(cat "$PID_FILE")"
echo "Logs are available in $LOG_FILE"
