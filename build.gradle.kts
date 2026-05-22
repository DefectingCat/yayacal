plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.versions)
    alias(libs.plugins.catalogUpdate)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("${layout.buildDirectory}/**/*.kt")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// versionCatalogUpdate 配置见 https://github.com/littlerobots/version-catalog-update-plugin
// 注意：运行该任务会重写 gradle/libs.versions.toml，非依赖版本键需手动保留
