<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# androidMain

## Purpose
Android 平台特定实现 source set，包含 `expect/actual` 的 Android 端实现和预览工具。

## Key Files

| File | Description |
|------|-------------|
| `kotlin/plus/rua/project/ComposeTrace.android.kt` | `composeTraceBeginSection` / `composeTraceEndSection` 的 Android 实现（路由到 `android.os.Trace`） |
| `kotlin/plus/rua/project/Platform.android.kt` | `Platform` 接口的 Android 实现 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | Android 平台实现源码（见 `kotlin/plus/rua/project/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 仅放置 `commonMain` 中 `expect` 声明的 Android `actual` 实现
- 不要在此添加业务逻辑

## Dependencies

### Internal
- `shared/src/commonMain/kotlin/plus/rua/project/ComposeTrace.kt` — expect 声明
- `shared/src/commonMain/kotlin/plus/rua/project/Platform.kt` — expect 声明

### External
- `android.os.Trace`

<!-- MANUAL: -->
