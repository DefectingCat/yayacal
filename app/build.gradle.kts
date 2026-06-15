import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val baseVersion = findProperty("app.version.base") as? String ?: "1.1.0"

val gitHash = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short=5", "HEAD")
    }.standardOutput.asText.get().trim()
} catch (_: Exception) {
    "unknown"
}

val buildDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy"))
val appVersionName = "${baseVersion}_${gitHash}_${buildDate}"

android {
    namespace = "plus.rua.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "plus.rua.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = appVersionName

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        // trace 构建类型：release 优化 + trace 标记保留，用于性能分析
        create("trace") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // benchmark 构建类型供 macrobenchmark 模块使用
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            // isDebuggable=false 使 macrobenchmark 在模拟器上稳定运行，且 Partial 编译模式可用。
            // 代价是生成的 baseline-prof.txt 会包含 R8 混淆后的类名；功能上仍然有效。
            // 若需要可人工维护的可读 profile，请在真机上使用 debuggable build 或引入 Baseline Profile Gradle plugin。
            isDebuggable = false
        }
    }


    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "**/*.kotlin_metadata",
                "**/*.kotlin_module",
            )
        }
    }

    bundle {
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}
