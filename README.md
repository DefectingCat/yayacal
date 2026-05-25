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

## 构建

```bash
# Debug
./gradlew :app:assembleDebug          # 构建 debug APK
./gradlew :app:installDebug           # 安装 debug APK 到设备

# Release
./gradlew :app:assembleRelease        # 构建 release APK
./gradlew :app:installBenchmark       # 安装 benchmark（release + 可调试）APK

# 测试
./gradlew :core:testDebugUnitTest                          # 运行全部测试
./gradlew :core:testDebugUnitTest --tests "plus.rua.project.ui.CalendarUtilsTest"  # 运行单个测试

# Baseline Profile（需要连接设备）
./gradlew :macrobenchmark:updateBaselineProfile                       # 一键生成 + 自动复制到 :core
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest               # 仅运行基准测试

# 性能 Profiling（需要连接设备）
./scripts/profile.sh                  # 默认 8 秒
./scripts/profile.sh 15               # 自定义时长
```

构建产物位于 `app/build/outputs/apk/<variant>/` 目录。

线条小狗表情包来自 https://www.douban.com/group/topic/264788645/?_i=9181692phrDzjR,9241256phrDzjR
