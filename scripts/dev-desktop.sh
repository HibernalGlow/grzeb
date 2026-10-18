#!/usr/bin/env bash
# 桌面端开发脚本：改 Kotlin 源文件就自动重启 :desktopApp:run。
#
# 不用 Compose Hot Reload 是因为它要动 4 个构建文件、还得把运行时换成 JetBrains Runtime；
# 这里只做「保存 → 杀掉 → 重新跑」，Gradle 守护进程保持热着，一轮约 10~30s。
#
# 为什么轮询而不是 fswatch：fswatch 往管道里写事件时是块缓冲（4KB）的，
# 单条改动根本传不到 while read，实测过；1s 一次的 find 开销可以忽略。
#
# 用法：./scripts/dev-desktop.sh    （Ctrl-C 退出，会连带关掉应用窗口）
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

# 应用 JVM 由 Gradle 守护进程拉起，命令行里带这个主类名；
# 只按这两个特征 pkill，绝不打到 Gradle 守护进程本身（它的命令行里没有任务名）。
APP_MARKER='dev.hibernalglow.grzeb.desktop.MainKt'
RUN_MARKER=':desktopApp:run'
WATCH_DIRS=(core/src shared/src desktopApp/src)
GRADLE_PID=""

# 只结束可执行文件是 java 的匹配进程：按命令行匹配会误伤任何恰好提到这两个字符串的
# shell（实测把跑验证命令的 zsh 一起杀了），所以先验 comm 再 kill。
kill_java_matching() {
  local pattern="$1" pid exe
  for pid in $(pgrep -f "$pattern" 2>/dev/null); do
    exe=$(ps -o comm= -p "$pid" 2>/dev/null)
    case "$exe" in
      *java*) kill "$pid" 2>/dev/null ;;
    esac
  done
}

stop_app() {
  [[ -n "$GRADLE_PID" ]] && kill "$GRADLE_PID" 2>/dev/null
  sleep 2
  kill_java_matching "$APP_MARKER"
  kill_java_matching "$RUN_MARKER"
  GRADLE_PID=""
  # 等 JVM 退干净，否则新一轮 run 会和旧进程抢同一个 jar
  sleep 1
}

start_app() {
  echo ">>> $(date +%T) 启动 :desktopApp:run"
  ./gradlew :desktopApp:run --console=plain &
  GRADLE_PID=$!
}

cleanup() {
  echo
  echo ">>> 停止开发服务"
  stop_app
}
trap cleanup INT TERM EXIT

STAMP=$(mktemp -t dev-desktop)
start_app
echo ">>> 监听 ${WATCH_DIRS[*]}（Ctrl-C 退出）"

while :; do
  sleep 1
  changed=$(find "${WATCH_DIRS[@]}" -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.sq' \) \
    -newer "$STAMP" -print -quit 2>/dev/null)
  [[ -z "$changed" ]] && continue
  touch "$STAMP"
  echo ">>> $(date +%T) 改动 $changed"
  stop_app
  start_app
done
