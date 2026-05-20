<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# iosApp

## Purpose
iOS 应用的 Swift 源码和资源目录，包含 SwiftUI 入口和 Compose 嵌入视图。

## Key Files

| File | Description |
|------|-------------|
| `iOSApp.swift` | iOS 应用入口，`@main struct iOSApp: App` |
| `ContentView.swift` | SwiftUI 视图，通过 `UIViewControllerRepresentable` 嵌入 Compose |
| `Info.plist` | iOS 应用配置 |
| `Assets.xcassets/` | iOS 应用图标和颜色资源 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `Assets.xcassets/` | iOS 图标和颜色资源 |
| `Preview Content/` | Xcode 预览资源 |

## For AI Agents

### Working In This Directory
- 不要在此添加业务逻辑
- `ContentView.swift` 中的 `MainViewController()` 来自 `:shared` 模块的 iOS 工厂

## Dependencies

### Internal
- `:shared` 模块 — 通过 `MainViewController()` 提供 Compose UI

<!-- MANUAL: -->