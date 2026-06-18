---
name: yayacal-release
description: Use when the user asks to release a new version of the yayacal Android calendar app
---

# YaYa Release

## Overview

One-shot local release workflow for the yayacal Android app. All steps run on the developer machine using `gh` CLI.

## When to Use

- User says something like "发布 1.2.0", "release v1.3.0", "发一个新版本".
- Working in `/Users/issuser/Developer/xfy/yayacal`.

## Release Steps

1. **Confirm version number** with the user if not provided.
2. **Update version numbers**:
   - `gradle.properties`: `app.version.base=x.y.z`
   - `app/build.gradle.kts`: increment `versionCode` by 1
3. **Update `CHANGELOG.md`**:
   - Compare commits since the last tag: `git log <last-tag>..HEAD --pretty=format:"%s"`
   - Group into Added / Changed / Fixed
   - Insert `## [x.y.z] - YYYY-MM-DD` below `## [Unreleased]`
   - Append `[x.y.z]: https://github.com/xfy/yayacal/releases/tag/vx.y.z` at the bottom
4. **Commit** with the exact message:
   ```bash
   git add CHANGELOG.md gradle.properties app/build.gradle.kts
   git commit -m "release: vx.y.z"
   ```
5. **Tag and push** (lightweight tag, matching previous releases):
   ```bash
   git tag vx.y.z
   git push origin main --tags
   ```
6. **Build release APK**:
   ```bash
   ./gradlew :app:assembleRelease
   ```
7. **Create GitHub Release** with the current version's CHANGELOG as body:
   ```bash
   python3 - <<'PY'
   import re
   content = open('CHANGELOG.md').read()
   m = re.search(r'## \[x.y.z\] - .*?\n(.*?)\n## \[', content, re.DOTALL)
   open('/tmp/vx.y.z-notes.md', 'w').write(m.group(1).strip())
   PY
   gh release create vx.y.z \
     app/build/outputs/apk/release/app-release.apk \
     --title "YaYa vx.y.z" \
     --notes-file /tmp/vx.y.z-notes.md
   ```

## Quick Reference

| Item | Value |
|---|---|
| Version base | `gradle.properties` → `app.version.base` |
| Version code | `app/build.gradle.kts` → `versionCode` |
| APK path | `app/build/outputs/apk/release/app-release.apk` |
| Commit message | `release: vx.y.z` |
| Tag format | lightweight `vx.y.z` |
| Release title | `YaYa vx.y.z` |
| Signing | Keep existing debug signing; do **not** add release keystore logic |

## Common Mistakes

- **Wrong commit message**: Do not use `chore(release): ...` or `build: ...`. Use exactly `release: vx.y.z`.
- **Annotated tag**: Previous releases use lightweight tags; do not add `-a`.
- **Empty release body**: Always extract the current version's CHANGELOG section and pass it to `--notes-file`.
- **Releasing from a dirty tree**: Run `git status` first; only release from a clean `main` branch.
- **Forgetting to push the tag**: `git push origin main --tags` is required.
