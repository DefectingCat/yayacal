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

线条小狗表情包来自 https://www.douban.com/group/topic/264788645/?_i=9181692phrDzjR,9241256phrDzjR

## 快速开始

```bash
# 编译 Android debug APK
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug

# 编译 release APK（含 Baseline Profiles）
./gradlew :app:assembleRelease
```

## Baseline Profiles 维护指南

本项目已集成 [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles)，用于消除冷启动时的 JIT 编译开销。Release APK 构建时会自动将 `core/src/main/baseline-prof.txt` 打包进 `assets/dexopt/baseline.prof`。

### 何时需要更新 `baseline-prof.txt`

每次发版前，检查以下清单。任一条件命中，就需要更新 profile：

| 检查项 | 是否需要更新 | 说明 |
|--------|-------------|------|
| 新增/修改了首帧渲染的 composable | 必须 | `MainActivity` → `CalendarMonthView` 启动路径上的任何 composable |
| 修改了 `DayCell.kt` 的方法签名 | 必须 | DayCell 是启动最热点（35 次调用） |
| 修改了 `CalendarMonthPage.kt` 的方法签名 | 必须 | 月度网格页面在首帧渲染 |
| 修改了 `CalendarMonthView.kt` 的方法签名 | 必须 | 根 composable |
| 修改了 `LunarCache.kt` 的计算逻辑 | 建议 | 缓存 miss 时会走 compute() 路径 |
| 新增/删除了 `tyme4kt` 的调用 | 建议 | 农历计算是 CPU 密集型 |
| 仅修改 UI 颜色、文字、布局 | 不需要 | 不涉及方法签名变化 |
| 新增设置页、关于页等非首屏页面 | 不需要 | 不在冷启动路径 |

### 如何更新

1. **定位新增/变更的热点方法**

   通过 logcat 抓取启动时的 JIT 编译日志：
   ```bash
   adb shell setprop dalvik.vm.extra-opts -verbose:compiler
   adb logcat -s "JIT" | grep "plus/rua/project"
   ```

   或抓取带 ART 详细日志的启动 trace：
   ```bash
   adb shell am start -W -n plus.rua.project/.MainActivity
   adb logcat -d | grep -E "JIT|dex2oat|BaselineProfile"
   ```

2. **编辑 `core/src/main/baseline-prof.txt`**

   新增规则格式：
   ```
   HSPL<类全路径;-><方法名>(<参数类型签名>)<返回类型签名>
   ```

   例如新增一个 composable `NewWidget`：
   ```
   HSPLplus/rua/project/ui/NewWidgetKt;->NewWidget(Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
   ```

   常用签名速查：

   | Kotlin 类型 | Profile 签名 |
   |------------|-------------|
   | `Int` | `I` |
   | `Float` | `F` |
   | `Boolean` | `Z` |
   | `String` | `Ljava/lang/String;` |
   | `LocalDate` | `Lkotlinx/datetime/LocalDate;` |
   | `Modifier` | `Landroidx/compose/ui/Modifier;` |
   | `Composer` | `Landroidx/compose/runtime/Composer;` |
   | `Function0<Unit>` | `Lkotlin/jvm/functions/Function0;` |
   | `Function1<T, R>` | `Lkotlin/jvm/functions/Function1;` |
   | `ShiftKind?` | `Lplus/rua/project/ShiftKind;` |
   | `Unit` / 无返回值 | `V` |

3. **验证方法名不被混淆**

   如果新增的方法在 `core/proguard-rules.pro` 中没有保留规则，添加：
   ```proguard
   -keepclassmembers class plus.rua.project.ui.NewWidgetKt {
       public static void NewWidget(...);
   }
   ```

4. **编译验证**
   ```bash
   ./gradlew :core:compileDebugKotlin
   ./gradlew :app:assembleRelease
   ```

5. **确认 profile 打包成功**
   ```bash
   unzip -l app/build/outputs/apk/release/app-release-unsigned.apk | grep baseline
   # 应看到 assets/dexopt/baseline.prof
   ```

### 自动化替代方案（推荐后期迁移）

手动维护容易遗漏，长期建议迁移到 **Macrobenchmark 自动生成**：

1. 创建 `:macrobenchmark` 模块
2. 编写启动基准测试（自动遍历冷启动路径）
3. `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
4. 自动输出 `baseline-prof.txt`，直接替换即可

参考：[Android Baseline Profiles 官方文档](https://developer.android.com/topic/performance/baselineprofiles/create-profile)

## 性能监控

项目内置了 `LunarCache`（农历/节气/节假日缓存）和性能追踪（`ComposeTrace.kt`）。查看 `DEVELOPMENT.md` 了解如何使用 Perfetto/Systrace 进行深度性能分析。
