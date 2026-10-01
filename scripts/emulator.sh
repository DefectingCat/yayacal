#!/bin/bash
# 启动一台硬件加速模拟器，保留用户数据，并为本地朋友圈配置 ADB 转发。
set -euo pipefail

avd="${AVD:-Pixel_10}"
backend_port="${BACKEND_PORT:-8088}"
adb="${ADB:-adb}"
emulator="${EMULATOR:-emulator}"
project_root="$(cd "$(dirname "$0")/.." && pwd)"
log_file="$project_root/logs/emulator.log"

command -v "$adb" >/dev/null || { echo "找不到 adb，请将 Android SDK platform-tools 加入 PATH。" >&2; exit 1; }
command -v "$emulator" >/dev/null || { echo "找不到 emulator，请将 Android SDK emulator 加入 PATH。" >&2; exit 1; }
if [[ ! "$backend_port" =~ ^[1-9][0-9]{0,4}$ ]] || (( backend_port > 65535 )); then
    echo "BACKEND_PORT 必须是 1 到 65535 的端口号。" >&2
    exit 1
fi
if ! "$emulator" -list-avds | tr -d '\r' | grep -Fxq "$avd"; then
    echo "找不到 AVD：${avd}。请先创建它，或使用 make emulator AVD=已有名称。" >&2
    exit 1
fi

"$adb" start-server
serial=""
launch_pid=""
running="$("$adb" devices | awk '$1 ~ /^emulator-/ { print $1 }')"
while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    name="$("$adb" -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -n 1 || true)"
    if [[ "$name" == "$avd" ]]; then
        serial="$candidate"
        break
    fi
done <<< "$running"

if [[ -n "$serial" ]]; then
    echo "复用 ${avd}（${serial}）。"
else
    if [[ -n "$running" ]]; then
        echo "已有其他模拟器运行或正在启动，请先关闭它后再启动 ${avd}。" >&2
        exit 1
    fi
    serial="emulator-5554"
    mkdir -p "$project_root/logs"
    echo "启动 ${avd}：硬件加速、4 核、2 GiB 内存、冷启动。日志：${log_file}"
    # 防止终端遗留的 HTTP 代理接管模拟器所有 TCP 流量。
    nohup env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY \
        "$emulator" -avd "$avd" -port 5554 -no-snapshot -no-boot-anim \
        -gpu host -accel on -cores 4 -memory 2048 -partition-size 2048 \
        </dev/null >"$log_file" 2>&1 &
    launch_pid=$!
fi

deadline=$((SECONDS + 120))
until [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    if [[ -n "$launch_pid" ]] && ! kill -0 "$launch_pid" 2>/dev/null; then
        echo "模拟器启动失败，请检查 ${log_file}。" >&2
        tail -n 15 "$log_file" >&2
        exit 1
    fi
    if (( SECONDS >= deadline )); then
        echo "模拟器 120 秒内未启动完成，请检查 ${log_file}。" >&2
        exit 1
    fi
    sleep 1
done

"$adb" -s "$serial" emu proxy clear >/dev/null
"$adb" -s "$serial" reverse "tcp:$backend_port" "tcp:$backend_port"
echo "模拟器已就绪；本机后端地址：http://127.0.0.1:${backend_port}（ADB 转发）。"

# 保持输入短暂打开，避免 Android netcat 在收到 HTTP 响应前因 stdin EOF 退出。
response="$("$adb" -s "$serial" shell "{ printf 'GET /health HTTP/1.1\r\nHost: 127.0.0.1:$backend_port\r\nConnection: close\r\n\r\n'; sleep 1; } | toybox nc -w 3 -W 3 127.0.0.1 $backend_port" 2>/dev/null || true)"
if [[ "$response" == HTTP/*' 200 '* ]]; then
    echo "后端健康检查通过（模拟器 → ADB → 本机 /health，HTTP 200）。"
else
    echo "转发已配置，后端尚未就绪；在另一终端运行 make server 后即可连接。"
fi
