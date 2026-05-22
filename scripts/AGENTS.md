<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# scripts

## Purpose
开发辅助脚本目录，包含性能追踪工具。使用 Perfetto 在设备上抓取应用 trace，并生成结构化报告。

## Key Files

| File | Description |
|------|-------------|
| `profile.sh` | Perfetto 性能追踪脚本：抓取 trace、帧统计、内存快照，生成 Markdown 报告 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 修改脚本后确保 `adb` 可用性检查和设备连接检测逻辑保持完整
- 脚本输出到 `logs/` 目录（trace、framestats、meminfo、report）

### Testing Requirements
- 需要已安装 `adb` 和连接的设备
- 应用需已安装：`./gradlew :app:installDebug`

## Dependencies

### Internal
- `logs/` — 脚本输出目录

### External
- Android SDK `adb`
- 设备上的 `perfetto` 工具

<!-- MANUAL: -->
