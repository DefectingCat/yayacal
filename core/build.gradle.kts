import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "plus.rua.project.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("proguard-rules.pro")

        // 构建期扫描 assets/animations/ 生成 WebP 文件列表，避免运行期硬编码 (1..152)
        // 与 assets/ 目录耦合却不校验，导致增删文件后隐性 bug
        val webpFiles = layout.projectDirectory
            .dir("src/main/assets/animations")
            .asFile
            .listFiles { f -> f.extension.equals("webp", ignoreCase = true) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        require(webpFiles.isNotEmpty()) { "assets/animations/ 不应为空，请检查目录" }
        // 拼成 Java 数组字面量: new String[]{"001.webp","002.webp",...}
        val quoted = webpFiles.joinToString(",") { name -> "\"$name\"" }
        val arrayLiteral = "new String[]{$quoted}"
        buildConfigField("String[]", "WEBP_FILES", arrayLiteral)
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ENABLE_TRACE", "true")
        }
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
            buildConfigField("boolean", "ENABLE_TRACE", "false")
        }
        create("trace") {
            initWith(buildTypes.getByName("release"))
            buildConfigField("boolean", "ENABLE_TRACE", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "**/*.kotlin_metadata",
                "**/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.kotlinx.datetime)
    implementation(libs.tyme4kt)
    implementation(libs.sketch.compose)
    implementation(libs.sketch.animated.webp)
    implementation(libs.androidx.profileinstaller)

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
    testImplementation(libs.kotlinx.coroutines.test)
}
