package plus.rua.project.ui

/**
 * 许可证条目数据。
 *
 * @param library 库名称
 * @param license 许可证名称
 */
data class LicenseItem(
    val library: String,
    val license: String,
    val description: String,
    val category: String,
    val url: String? = null
)

/**
 * 项目使用的第三方库及其许可证列表。
 */
val licenses = listOf(
    LicenseItem(
        library = "tyme4kt",
        license = "MIT",
        description = "中国传统历法推演引擎，支持公历、农历、干支、节气及法定节假日推算。",
        category = "农历与节气",
        url = "https://github.com/6tail/tyme4kt"
    ),
    LicenseItem(
        library = "kotlinx-datetime",
        license = "Apache-2.0",
        description = "Kotlin 官方 DateTime 日期时间计算库，取代传统 Java Calendar。",
        category = "日期时间",
        url = "https://github.com/Kotlin/kotlinx-datetime"
    ),
    LicenseItem(
        library = "Sketch",
        license = "Apache-2.0",
        description = "高效强大的图片加载与解码引擎，支持 WebP 动图扫描与帧动画播放。",
        category = "图片处理",
        url = "https://github.com/panpf/sketch"
    ),
    LicenseItem(
        library = "ZoomImage",
        license = "Apache-2.0",
        description = "日期记录器照片手势缩放、平移与双击查看手势缩放组件。",
        category = "图片处理",
        url = "https://github.com/panpf/zoomimage"
    ),
    LicenseItem(
        library = "AndroidX Room",
        license = "Apache-2.0",
        description = "日期记录器 (DateRecorder) 本地 SQLite 结构化数据持久化数据库。",
        category = "数据存储",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    LicenseItem(
        library = "AndroidX CameraX",
        license = "Apache-2.0",
        description = "日期记录器内置相机拍照、镜头切换与图片捕捉与预览能力。",
        category = "媒体与相机",
        url = "https://developer.android.com/training/camerax"
    ),
    LicenseItem(
        library = "AndroidX Media3 (ExoPlayer)",
        license = "Apache-2.0",
        description = "小狗乐园彩蛋页面全屏视频循环播放的高性能媒体引擎。",
        category = "媒体与相机",
        url = "https://developer.android.com/media/media3"
    ),
    LicenseItem(
        library = "Compose Material 3",
        license = "Apache-2.0",
        description = "Material Design 3 现代化 UI 交互组件、动态配色与主题系统。",
        category = "UI 框架",
        url = "https://developer.android.com/jetpack/compose/design/material3"
    ),
    LicenseItem(
        library = "AndroidX Activity Compose",
        license = "Apache-2.0",
        description = "Jetpack Compose 与 Android Activity 生命周期及 Intent 桥接支持。",
        category = "UI 框架",
        url = "https://developer.android.com/jetpack/androidx/releases/activity"
    ),
    LicenseItem(
        library = "AndroidX Lifecycle",
        license = "Apache-2.0",
        description = "ViewModel 与 UI State 状态绑定及生命周期感知组合能力。",
        category = "架构组件",
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"
    ),
    LicenseItem(
        library = "Kotlin",
        license = "Apache-2.0",
        description = "Kotlin 编程语言标准库与 Flow / Coroutine 异步协程框架。",
        category = "核心语言",
        url = "https://kotlinlang.org"
    )
)
