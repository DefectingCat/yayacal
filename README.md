# YaYa

纯 Android + Jetpack Compose 日历应用，支持农历/节气/节日、个人班次排期，提供月/周/年三种视图。

<div>
    <img src="core/src/main/assets/app_icon.webp" width="128" height="128" />
</div>

## 特性

- **流畅的视图切换** —— 月视图、周视图、年视图三种模式，拖拽手势驱动月↔周折叠，弹簧动画自动吸附
- **无限滑动分页** —— 基于 `Int.MAX_VALUE` 的虚拟分页，前后无边界翻页
- **完整中式日历** —— 公历 + 农历 + 二十四节气 + 传统节日，ISO 8601 周起始（周一）
- **个人排班周期** —— 自定义工作/休息循环，与公共节假日独立
- **Material 3 设计** —— 动态配色，深色模式
- **双账号朋友圈** —— 小白与小鸡毛各自拥有主页、头像和封面，支持图文发布、点赞、评论回复、私密动态、搜索、相册与互动消息；通过独立后端同步

## 技术栈

- Kotlin 2.3 · Jetpack Compose · Material 3
- `kotlinx-datetime` 处理所有日期逻辑
- `tyme4kt` 提供农历、节气与传统节日
- `sketch` 渲染动画 WebP
- 三模块：`:core`（UI + 逻辑） · `:app`（薄壳） · `:macrobenchmark`（Baseline Profile / Startup Profile 生成）
- 朋友圈后端：`server/`，Rust Axum + PostgreSQL，独立于 Gradle 构建

## 朋友圈后端

启动、依赖版本、接口、备份和集成测试见 [server/README.md](server/README.md)。应用的账号选择页提供「连接设置」，填写后端地址后直接选择小白或小鸡毛，不使用密码。两个账号的主页和通知独立，公开动态可互相查看、点赞和评论。

日历功能保持离线可用。旧版本本机朋友圈数据保留原样，不会自动上传或猜测作者归属。朋友圈写入以服务器确认成功为准，失败保留草稿供重试。

## 构建

本机开发可以先在一个终端运行 `make server`，然后在另一个终端执行：

```bash
make emulator   # 启动或复用 Pixel_10：GPU/CPU 硬件加速、4 核、2 GiB 内存
make install    # 编译并安装 Debug APK
```

启动脚本需要 `adb`、`emulator` 和 `python3` 位于 PATH 中。`make emulator` 在独立会话中启动模拟器，等待系统启动完成，清除模拟器代理，并自动转发本机 8088 端口及检查后端健康状态。连接设置可使用 `http://127.0.0.1:8088`；默认的 `http://10.0.2.2:8088` 在 Android 17 上首次访问时会申请附近设备权限。可用 `AVD=名称`、`BACKEND_PORT=端口` 覆盖默认值，启动日志位于 `logs/emulator.log`。

公网后端在「朋友圈 → 连接设置」填写 HTTPS 根地址，不带 `/api/v1`。公网地址不申请本地网络权限，Release 只接受 HTTPS；本机的 HTTP 与 ADB 转发用于 Debug 调试。

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

# Baseline Profile / Startup Profile（需要连接设备）
./gradlew :macrobenchmark:updateBaselineProfile       # 生成并复制两份 Profile 到 :core
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest               # 仅运行基准测试

# 性能 Profiling（需要连接设备）
./scripts/profile.sh                  # 默认 8 秒
./scripts/profile.sh 15               # 自定义时长
```

构建产物位于 `app/build/outputs/apk/<variant>/` 目录。

线条小狗表情包来自 https://www.douban.com/group/topic/264788645/?_i=9181692phrDzjR,9241256phrDzjR
