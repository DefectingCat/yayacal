package plus.rua.project.ui

/**
 * 许可证条目数据。
 *
 * @param library 库名称
 * @param license 许可证名称
 */
data class LicenseItem(
    val library: String,
    val license: String
)

/**
 * 项目使用的第三方库及其许可证列表。
 */
val licenses = listOf(
    LicenseItem("AndroidX Activity Compose", "Apache-2.0"),
    LicenseItem("AndroidX CameraX", "Apache-2.0"),
    LicenseItem("AndroidX Lifecycle", "Apache-2.0"),
    LicenseItem("AndroidX Media3 (ExoPlayer)", "Apache-2.0"),
    LicenseItem("AndroidX Room", "Apache-2.0"),
    LicenseItem("Compose Material 3", "Apache-2.0"),
    LicenseItem("Kotlin", "Apache-2.0"),
    LicenseItem("kotlinx-datetime", "Apache-2.0"),
    LicenseItem("Sketch", "Apache-2.0"),
    LicenseItem("tyme4kt", "MIT"),
    LicenseItem("ZoomImage", "Apache-2.0"),
)
