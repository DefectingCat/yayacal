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

启动、依赖版本、接口、备份和集成测试见 [server/README.md](server/README.md)。应用使用当前构建的默认后端地址，也可在账号选择页的「连接设置」修改，然后直接选择小白或小鸡毛，不使用密码。两个账号的主页和通知独立，公开动态可互相查看、点赞和评论。

日历功能保持离线可用。旧版本本机朋友圈数据保留原样，不会自动上传或猜测作者归属。朋友圈写入以服务器确认成功为准，失败保留草稿供重试。

朋友圈动态、评论、头像和封面统一支持最多 50 MiB 的静态 JPEG、PNG、WebP 图片。选图时自动生成上传副本：普通照片最长边 2560 像素、目标约 2 MiB，长截图优先保留文字清晰度，透明图片保留透明通道；较小图片可直接上传。相册原图保持原样，草稿保存处理结果及已上传图片供失败重试。处理过程中显示进度，动态图与无法处理的图片提示重新选择。

## 构建

本机开发可以先在一个终端运行 `make server`，然后在另一个终端执行：

```bash
make emulator   # 启动或复用 Pixel_10：GPU/CPU 硬件加速、4 核、2 GiB 内存
make install    # 编译并安装 Debug APK
```

启动脚本需要 `adb`、`emulator` 和 `python3` 位于 PATH 中。`make emulator` 在独立会话中启动模拟器，等待系统启动完成，清除模拟器代理，并自动转发本机 8088 端口及检查后端健康状态。连接设置可使用 `http://127.0.0.1:8088`；默认的 `http://10.0.2.2:8088` 在 Android 17 上首次访问时会申请附近设备权限。可用 `AVD=名称`、`BACKEND_PORT=端口` 覆盖默认值，启动日志位于 `logs/emulator.log`。

朋友圈默认地址由 `core/build.gradle.kts` 的 `BuildConfig.MOMENTS_DEFAULT_URL` 在构建时注入：

| 构建类型 | 默认服务地址 |
| --- | --- |
| Debug（`make build` / `make install`） | `http://10.0.2.2:8088` |
| Release（`make` / `make release`） | `https://yaya.rua.plus` |
| trace / benchmark | `https://yaya.rua.plus`，沿用 Release 配置 |

手动修改时填写服务根地址，不带 `/api/v1`。Debug 与 Release 分别保存手动地址，trace / benchmark 共用 Release 设置；覆盖安装时会读取当前环境的设置，没有保存值时使用构建默认地址。首次升级会将旧 HTTP 地址迁入 Debug 设置；旧 HTTPS 地址不再覆盖默认值，Release 首次使用线上地址，之后仍可在「朋友圈 → 连接设置」修改。公网地址不申请本地网络权限，Release 只接受 HTTPS；本机的 HTTP 与 ADB 转发用于 Debug 调试。

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

## 独立发布

Android 和朋友圈后端分别维护版本与 changelog：

| 组件 | 版本文件 | 更新记录 | 发布 tag | 产物 |
| --- | --- | --- | --- | --- |
| Android | `gradle.properties` 的 `app.version.base` | `CHANGELOG.md` | `vX.Y.Z` | APK |
| 后端 | `server/Cargo.toml` 的 `package.version` | `server/CHANGELOG.md` | `server-vX.Y.Z` | GHCR amd64/arm64 镜像 |

Android 发布前更新基础版本、递增 `app/build.gradle.kts` 中的 `versionCode`，并在根目录 changelog 整理对应版本的更新记录。执行 `python3 scripts/release.py android vX.Y.Z` 校验，提交并推送代码，然后仅推送对应 tag：

```sh
git push origin main
git tag vX.Y.Z
git push origin vX.Y.Z
```

Android CI 校验 tag、版本与 changelog 后运行格式检查、单元测试和 APK 构建，自动创建 `YaYa vX.Y.Z` Release，并使用该版本的 changelog 作为正文。日常 Android CI 跳过仅涉及 `server/` 或后端工作流的修改。

后端发布流程见 [server/README.md](server/README.md#准备后端发布)，后端 tag 只触发后端发布。日常 Server CI 检查后端、发布脚本和相关构建配置；任一组件发版都无需同步提升另一个组件的版本号。

线条小狗表情包来自 https://www.douban.com/group/topic/264788645/?_i=9181692phrDzjR,9241256phrDzjR
