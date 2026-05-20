<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# iosMain

## Purpose
iOS 平台特定实现 source set，包含 `ComposeUIViewController` 工厂和 `expect/actual` 的 iOS 端实现。

## Key Files

| File | Description |
|------|-------------|
| `kotlin/plus/rua/project/MainViewController.kt` | `ComposeUIViewController` 工厂函数，iOS 应用通过此入口加载 Compose UI |
| `kotlin/plus/rua/project/ComposeTrace.ios.kt` | Trace 标记的 iOS 实现（no-op） |
| `kotlin/plus/rua/project/Platform.ios.kt` | `Platform` 接口的 iOS 实现 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | iOS 平台实现源码（见 `kotlin/plus/rua/project/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 仅放置 `commonMain` 中 `expect` 声明的 iOS `actual` 实现
- `MainViewController.kt` 是 iOS 侧的 Compose 入口，保持简洁

## Dependencies

### Internal
- `shared/src/commonMain/kotlin/plus/rua/project/App.kt` — Compose 根界面
- `shared/src/commonMain/kotlin/plus/rua/project/ComposeTrace.kt` — expect 声明
- `shared/src/commonMain/kotlin/plus/rua/project/Platform.kt` — expect 声明

### External
- `platform.UIKit`

<!-- MANUAL: -->
