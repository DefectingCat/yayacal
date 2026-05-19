# YaYa

基于 Kotlin Multiplatform 与 Compose Multiplatform 的跨平台日历应用,Android 与 iOS 共享同一套 UI 与业务逻辑。

## 特性

- **流畅的视图切换** —— 月视图、周视图、年视图三种模式,拖拽手势驱动月↔周折叠,弹簧动画自动吸附
- **无限滑动分页** —— 基于 `Int.MAX_VALUE` 的虚拟分页,前后无边界翻页
- **完整中式日历** —— 公历 + 农历 + 二十四节气 + 传统节日,ISO 8601 周起始(周一)
- **个人排班周期** —— 自定义工作/休息循环,与公共节假日独立
- **Material 3 设计** —— 动态配色,深色模式

## 技术栈

- Kotlin 2.3 · Compose Multiplatform 1.11 · Material 3
- `kotlinx-datetime` 处理所有日期逻辑
- `tyme4kt` 提供农历、节气与传统节日
- `sketch` 渲染 GIF 动画
- 双模块:`:shared`(UI + 逻辑) · `:androidApp`(薄壳)
- iOS 入口为 `MainViewController.kt`,Xcode 工程位于 `iosApp/`
