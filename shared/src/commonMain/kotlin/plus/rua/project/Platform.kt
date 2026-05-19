package plus.rua.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 获取 GIF 资源的 URI。
 *
 * @param gifFile GIF 文件名（如 "001.gif"）
 * @return 平台特定的资源 URI
 */
expect fun getGifUri(gifFile: String): String