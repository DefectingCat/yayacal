plugins {
    id("com.android.test")
}

android {
    namespace = "plus.rua.project.macrobenchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 允许在模拟器 / debuggable target 上运行 macrobenchmark（仅用于开发验证）。
        // 正式发布 benchmark 请在物理设备、release 目标应用上执行。
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // benchmark 构建类型必须与 app 模块的 release 类型签名一致
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso)
    implementation(libs.androidx.test.uiautomator)
}

// ===== Baseline Profile 自动复制 =====
// 运行 ./gradlew :macrobenchmark:updateBaselineProfile 即可一键生成并复制

val updateBaselineProfile by tasks.registering {
    group = "benchmark"
    description = "运行 connectedBenchmarkAndroidTest 并将生成的 startup-prof.txt 复制到 :core 模块"

    // 依赖基准测试 task（需要先连接设备/模拟器）
    dependsOn("connectedBenchmarkAndroidTest")

    // 预先计算目标路径，避免在 doLast 中引用 project 对象（configuration cache 兼容）
    val targetPath = rootProject.projectDir.resolve("core/src/main/baseline-prof.txt").absolutePath
    val buildDirPath = layout.buildDirectory.get().asFile.absolutePath

    doLast {
        // 寻找生成的 profile 文件（benchmark 1.4+ 文件名格式：{Class}_{Method}-startup-prof.txt）
        val sourcePaths = listOf(
            "$buildDirPath/outputs/connected_android_test_additional_output/benchmark/",
            "$buildDirPath/outputs/connected_android_test_additional_output/",
        )

        val targetFile = File(targetPath)
        var copied = false
        for (path in sourcePaths) {
            val dir = File(path)
            if (!dir.exists()) continue

            // 优先匹配不带时间戳的 startup-prof.txt（benchmark 1.4+ 格式）
            val profileFile = dir.walkTopDown()
                .firstOrNull { it.name.endsWith("-startup-prof.txt") && !it.name.contains(Regex("-\\d{4}-\\d{2}-\\d{2}-")) }
                ?: continue

            profileFile.copyTo(targetFile, overwrite = true)
            println("✅ Baseline Profile 已自动复制到: ${targetFile.absolutePath}")
            println("   来源: ${profileFile.absolutePath}")
            copied = true
            break
        }

        if (!copied) {
            throw GradleException(
                "未找到生成的 *-startup-prof.txt。\n" +
                "请确认:\n" +
                "  1. 设备/模拟器已连接 (adb devices)\n" +
                "  2. 应用已安装在 benchmark 构建类型下\n" +
                "  3. 检查 macrobenchmark/build/outputs/ 下是否有输出"
            )
        }
    }
}
