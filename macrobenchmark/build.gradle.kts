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

// ===== Baseline Profile / Startup Profile 自动复制 =====
// 运行 ./gradlew :macrobenchmark:updateBaselineProfile 即可一键生成并复制

val updateBaselineProfile by tasks.registering {
    group = "benchmark"
    description = "运行 connectedBenchmarkAndroidTest 并将生成的 Baseline Profile 与 Startup Profile 复制到 :core 模块"

    // 依赖基准测试 task（需要先连接设备/模拟器）
    dependsOn("connectedBenchmarkAndroidTest")

    // 该 task 的输入是 benchmark 运行后的产物，因此不应被跳过
    outputs.upToDateWhen { false }

    // 预先计算目标路径，避免在 doLast 中引用 project 对象（configuration cache 兼容）
    val baselineTargetPath = rootProject.projectDir
        .resolve("core/src/main/baseline-prof.txt")
        .absolutePath
    val startupTargetDirPath = rootProject.projectDir
        .resolve("core/src/main/baselineProfiles")
        .absolutePath
    val buildDirPath = layout.buildDirectory.get().asFile.absolutePath

    doLast {
        val sourcePaths = listOf(
            "$buildDirPath/outputs/connected_android_test_additional_output/benchmark/",
            "$buildDirPath/outputs/connected_android_test_additional_output/",
        )

        // benchmark 1.4+ 文件名格式：{Class}_{Method}-baseline-prof.txt / {Class}_{Method}-startup-prof.txt
        // 排除带时间戳的历史文件（如 ...-2026-06-15-startup-prof.txt），只取当前 canonical 文件
        val dateStampRegex = Regex("-\\d{4}-\\d{2}-\\d{2}-")
        fun findNewestProfile(suffix: String): File? {
            val candidates = sourcePaths.flatMap { path ->
                val dir = File(path)
                if (!dir.exists()) emptyList()
                else dir.walkTopDown().filter {
                    it.isFile &&
                        it.name.endsWith(suffix) &&
                        !it.name.contains(dateStampRegex)
                }.toList()
            }
            return candidates
                .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.absolutePath })
                .firstOrNull()
        }

        fun requireProfile(suffix: String, label: String): File {
            val sourceFile = findNewestProfile(suffix)
                ?: throw GradleException(
                    "未找到生成的 *$suffix。\n" +
                        "请确认:\n" +
                        "  1. 设备/模拟器已连接 (adb devices)\n" +
                        "  2. 应用已安装在 benchmark 构建类型下\n" +
                        "  3. 检查 macrobenchmark/build/outputs/ 下是否有输出"
                )
            if (sourceFile.length() == 0L) {
                throw GradleException("生成的 $label 文件为空: ${sourceFile.absolutePath}")
            }
            return sourceFile
        }

        // 1) 先定位并校验两份源文件，确保都可用再开始复制，避免部分覆盖目标文件
        val baselineSource = requireProfile("-baseline-prof.txt", "Baseline Profile")
        val startupSource = requireProfile("-startup-prof.txt", "Startup Profile")

        // 2) Baseline Profile → AOT 编译优化
        val baselineTargetFile = File(baselineTargetPath)
        baselineSource.copyTo(baselineTargetFile, overwrite = true)
        logger.lifecycle("✅ Baseline Profile 已自动复制到: ${baselineTargetFile.absolutePath}")
        logger.lifecycle("   来源: ${baselineSource.absolutePath}")

        // 3) Startup Profile → DEX layout 优化（必须放在 baselineProfiles/startup-prof.txt）
        val startupTargetDir = File(startupTargetDirPath)
        startupTargetDir.mkdirs()
        val startupTargetFile = File(startupTargetDir, "startup-prof.txt")
        startupSource.copyTo(startupTargetFile, overwrite = true)
        logger.lifecycle("✅ Startup Profile 已自动复制到: ${startupTargetFile.absolutePath}")
        logger.lifecycle("   来源: ${startupSource.absolutePath}")
    }
}
