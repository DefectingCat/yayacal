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
    }

    buildFeatures {
        compose = true
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
