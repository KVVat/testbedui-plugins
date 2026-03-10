#!/usr/bin/env bash

# testbed-core の起動スクリプト
# 二重起動防止のためにポート11452を監視し、使用中ならキルしてから起動します。

PORT=11452
TARGET_DIR="../testbed-core"

echo "Checking for existing MCP server on port $PORT..."
PID=$(lsof -t -i :$PORT)

if [ ! -z "$PID" ]; then
    echo "Found existing process (PID: $PID). Killing it to prevent conflicts..."
    kill -9 $PID
    sleep 1
    echo "Process killed."
else
    echo "No existing process found on port $PORT."
fi

echo "Starting testbed-core in background..."
cd "$TARGET_DIR" || { echo "Failed to navigate to $TARGET_DIR. Please check directory structure."; exit 1; }

# バックグラウンドで起動し、ログは標準出力ではなくファイルに逃がすか破棄する
# ここでは開発時の確認のために ./gradlew :composeApp:run を使用
./gradlew :composeApp:run > /dev/null 2>&1 &

echo "testbed-core setup initialized. It may take a few seconds for the MCP server to start responding."
