<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# iosApp

## Purpose
iOS 应用入口，Xcode 项目结构。使用 SwiftUI 的 `ComposeUIViewController` 承载共享模块中的 Compose UI，共享模块通过 CocoaPods/SPM 集成为 framework。

## Key Files

| File | Description |
|------|-------------|
| `iosApp.xcodeproj/project.pbxproj` | Xcode 项目配置 |
| `iosApp/iOSApp.swift` | iOS 应用入口（`@main App`） |
| `iosApp/ContentView.swift` | SwiftUI 视图，嵌入 `ComposeUIViewController` |
| `iosApp/Info.plist` | iOS 应用信息配置 |
| `Configuration/Config.xcconfig` | Xcode 构建设置 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `iosApp/` | Swift 源码和资源（见 `iosApp/iosApp/AGENTS.md`） |
| `Configuration/` | Xcode 构建设置文件 |

## For AI Agents

### Working In This Directory
- 不要在此目录添加业务逻辑；所有逻辑在 `:shared` 模块
- 首次打开 Xcode 前需运行 `./gradlew :shared:generateDummyFramework`
- 修改 `shared` 后需重新生成 framework

### Testing Requirements
- 打开 `iosApp/iosApp.xcworkspace` 在 Xcode 中运行
- iOS 测试通过 Xcode 执行

### Common Patterns
- `ContentView` 使用 `UIViewControllerRepresentable` 包装 `ComposeUIViewController`
- `MainViewController` 工厂在 `shared/src/iosMain/` 中定义

## Dependencies

### Internal
- `:shared` 模块 — 作为 iOS framework 集成

### External
- SwiftUI, UIKit

<!-- MANUAL: -->