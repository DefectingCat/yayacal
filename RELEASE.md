# 发布流程

## 1. 更新 CHANGELOG.md

按倒序（新版在前）在 `[Unreleased]` 下方添加新版本条目，格式遵循 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)。

底部添加版本链接：

```
[x.y.z]: https://github.com/DefectingCat/yayacal/releases/tag/vx.y.z
```

## 2. 更新版本号

编辑 `gradle.properties`：

- `app.version.base` 改为新版本号（如 `1.2.0`）

编辑 `app/build.gradle.kts`：

- `versionCode` 递增 `+1`

> `app.version.base` 优先于 `build.gradle.kts` 中的默认值，因此以 `gradle.properties` 为准。

## 3. 构建 Release APK

```bash
./gradlew :app:assembleRelease
```

产物路径：`app/build/outputs/apk/release/app-release.apk`

## 4. 提交、打 Tag、推送

```bash
git add CHANGELOG.md gradle.properties app/build.gradle.kts
git commit -m "release: vx.y.z"
git tag vx.y.z
git push origin main --tags
```

## 5. 创建 GitHub Release

```bash
gh release create vx.y.z \
  app/build/outputs/apk/release/app-release.apk \
  --title "YaYa vx.y.z" \
  --notes-file CHANGELOG.md
```

`--notes-file` 会读取 CHANGELOG.md 全文作为 Release body。

## 检查清单

- [ ] CHANGELOG.md 新版本条目已添加（倒序，新版在前）
- [ ] CHANGELOG.md 底部链接已添加
- [ ] `gradle.properties` 中 `app.version.base` 和 `app/build.gradle.kts` 中 `versionCode` 已更新
- [ ] Release APK 构建成功
- [ ] Git tag 已推送
- [ ] GitHub Release 已创建且包含 APK
