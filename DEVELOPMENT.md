# 开发指南

## Baseline Profile

```bash
# 编译 Android debug APK
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug

# 编译 release APK（含 Baseline Profiles）
./gradlew :app:assembleRelease

./gradlew :app:installBenchmark
```

Baseline Profile 自动生成器。

运行方式（一键生成 + 自动复制到 :core）：

```
./gradlew :macrobenchmark:updateBaselineProfile
```

仅运行基准测试（不自动复制）：

```
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

手动复制路径：
`macrobenchmark/build/outputs/connected_android_test_additional_output/`

测试覆盖全部用户交互路径，实现全量 AOT：

1. 冷启动 → 首帧渲染
2. FAB 展开 → 年视图 → 月视图
3. 日期选择 → 周视图折叠/展开
4. 关于页 → 开源许可页
5. 返回主界面

## 模拟器

```
emulator -avd Pixel_10 \
  -no-snapshot \
  -no-boot-anim \
  -gpu host \
  -accel on \
  -cores 4 \
  -memory 4096 \
  -partition-size 2048
```
