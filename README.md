# YaYa

基于 Kotlin Multiplatform 与 Compose Multiplatform 的日历应用，Android 和 iOS 共享 UI。

月视图与周视图之间支持流畅的折叠/展开过渡——拖拽切换，弹簧动画自动吸附。无限分页，ISO 8601 周起始，Material 3。

## 构建

```bash
# Android
./gradlew :androidApp:assembleDebug

# iOS — 在 Xcode 中打开 iosApp/ 运行
```

## 技术栈

- Kotlin 2.3 · Compose Multiplatform 1.10 · Material 3
- `kotlinx-datetime` 处理所有日期逻辑
- 双模块：`:shared`（UI + 逻辑）· `:androidApp`（薄壳）
