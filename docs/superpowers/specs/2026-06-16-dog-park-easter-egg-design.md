# 关于页面「小狗乐园」彩蛋设计

## 背景

在「关于鸭鸭日历」页面中，版本号目前是一个无实际功能的 `TextButton`。本设计为其增加一个隐藏彩蛋：连续点击版本号 7 次后进入「小狗乐园」页面，全屏循环播放一段彩蛋视频。

## 目标

- 提升应用趣味性，给用户一个可发现的隐藏彩蛋。
- 保持现有架构：所有 UI 与业务逻辑留在 `:core`，`:app` 仅作为 Activity 壳。
- 不引入过度复杂的依赖或状态管理。

## 设计决策摘要

| 决策项 | 选择 | 说明 |
|--------|------|------|
| 总点击次数 | 7 次 | 足够隐藏，又不会太难触发 |
| 提示开始时机 | 第 4 次点击 | 前 3 次静默，避免普通用户误触时被打扰 |
| 提示文案 | 「再点击 N 下进入小狗乐园」 | N 为剩余次数，简洁明确 |
| 超时重置 | 1.5 秒 | 与 Android 开发者选项等经典彩蛋保持一致节奏 |
| 进度持久化 | 不持久化 | 离开页面或超时即重置 |
| 提示组件 | 系统 Toast | 最符合「小气泡」语义，轻量 |
| 视频播放 | Media3 ExoPlayer | 功能强、与 Compose 集成成熟 |
| 视频位置 | `core/src/main/assets/video/enter_screen_bg1.mp4` | 与现有 `app_icon.webp` 等资源保持一致 |
| 视频显示 | 等比裁剪铺满（`RESIZE_MODE_ZOOM`） | 填满屏幕，视觉沉浸 |
| 声音 | 静音 | 不打扰用户 |
| 屏幕方向 | 跟随系统 | 不强制横竖屏 |
| 退出方式 | 系统返回键 | 支持预测性返回手势，Activity 自然 finish |
| 进入过渡动画 | 淡入 400ms | 营造进入彩蛋的仪式感 |
| 退出过渡动画 | 默认 slide | 保持现有返回风格 |

## 触发机制（关于页面）

### 状态

- 在 `AboutScreen` 内使用 `remember { mutableIntStateOf(0) }` 保存当前连续点击次数。
- 计数为局部状态，不提升到 ViewModel，也不持久化。
- `AboutScreen` 离开 Composition 时计数自然消失。

### 点击行为

| 当前点击次数 | 行为 |
|--------------|------|
| 1 ~ 3 | 计数 +1，无 Toast |
| 4 | Toast「再点击 3 下进入小狗乐园」 |
| 5 | Toast「再点击 2 下进入小狗乐园」 |
| 6 | Toast「再点击 1 下进入小狗乐园」 |
| 7 | 调用 `onNavigateToDogPark()`，进入彩蛋页面 |

### 超时重置

每次点击启动/重启一个 `LaunchedEffect`：

- 在 1.5 秒内收到下一次点击：取消旧 Job，计数 +1。
- 1.5 秒内无新点击：Job 执行，计数重置为 0。

## 导航链路

```
MainActivity
  └── startActivityWithSlide → AboutActivity
        └── onNavigateToDogPark → startActivityWithSlide → DogParkActivity
```

- `AboutActivity` 给 `AboutScreen` 新增回调 `onNavigateToDogPark: () -> Unit`。
- `DogParkActivity` 继承 `BaseActivity`，自动获得 edge-to-edge 和 slide 转场支持。

## 小狗乐园页面

### 组件

- `DogParkScreen`：位于 `:core`，无业务参数，只负责全屏视频播放。
- `DogParkActivity`：位于 `:app`，继承 `BaseActivity`，壳逻辑。

### 视频播放

- 使用 Media3 ExoPlayer + `PlayerView`。
- 通过 Compose `AndroidView` 嵌入 `PlayerView`。
- 配置：
  - `resizeMode = RESIZE_MODE_ZOOM`：等比裁剪铺满全屏。
  - `useController = false`：不显示播放控件。
  - `player.volume = 0f`：静音。
  - `repeatMode = Player.REPEAT_MODE_ONE`：循环播放。
- 媒体源 URI：`asset:///video/enter_screen_bg1.mp4`。

### 生命周期

- `onStart` → `player.play()`
- `onStop` → `player.pause()`
- `onDestroy` → `player.release()`
- 使用 `DisposableEffect` 绑定释放逻辑。

### 退出

- 用户按系统返回键或执行返回手势时 Activity finish。
- 预测性返回手势由 Manifest 中的 `android:enableOnBackInvokedCallback="true"` 支持。
- 退出动画保留 `BaseActivity` 默认 slide。

## 过渡动画

### 进入动画（淡入）

- 新增 `app/src/main/res/anim/fade_in.xml`。
- `DogParkActivity.onCreate` 中：
  - Android 14+：`overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in, R.anim.fade_out)`
  - 低版本：`overridePendingTransition(R.anim.fade_in, R.anim.fade_out)`（在 `super.onCreate` 之后、`setContent` 之前调用）
- 淡入时长约 400ms。

### 退出动画

- 保留 `BaseActivity` 默认的 slide 返回动画，不覆盖。

## 资源

- 视频文件：
  - 来源：`~/Pictures/enter_screen_bg1.mp4`
  - 目标：`core/src/main/assets/video/enter_screen_bg1.mp4`
- 动画资源：
  - 新增 `app/src/main/res/anim/fade_in.xml`

## 依赖变更

### `gradle/libs.versions.toml`

新增：

```toml
[versions]
androidx-media3 = "1.6.1"

[libraries]
androidx-media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "androidx-media3" }
androidx-media3-ui = { module = "androidx.media3:media3-ui", version.ref = "androidx-media3" }
```

### `core/build.gradle.kts`

新增：

```kotlin
implementation(libs.androidx.media3.exoplayer)
implementation(libs.androidx.media3.ui)
```

## 错误处理

- 视频加载/准备失败时，直接 `finishWithSlideBack()` 静默返回关于页面。
- 不显示弹窗或 Snackbar，避免破坏彩蛋体验。

## 测试计划

### 单元测试

- 抽离纯函数 `getToastMessage(clickCount: Int): String?` 并测试：
  - 1 ~ 3 返回 `null`
  - 4 返回「再点击 3 下进入小狗乐园」
  - 5 返回「再点击 2 下进入小狗乐园」
  - 6 返回「再点击 1 下进入小狗乐园」
  - 7 返回 `null`（此时已跳转）

### 手动测试

- 连续点击版本号 7 次，确认进入 `DogParkActivity`。
- 点击过程中停顿 1.5 秒，确认计数重置。
- 确认进入动画为淡入。
- 确认视频全屏、静音、无控件、循环播放。
- 确认系统返回键正常退出，并回到关于页面。
- 确认低版本（< Android 14）和 Android 14+ 的淡入动画都生效。

## 未包含在本期

- 多次进入彩蛋后的不同内容。
- 视频下载/动态更新。
- 屏幕常亮保持。
- 分享彩蛋入口。
