package plus.rua.project

/** 将网络或操作异常转换为可恢复的中文说明，避免把地址、响应体和堆栈直接展示给用户。 */
internal fun momentsErrorDescription(error: String): String {
    val text = error.lowercase()
    return when {
        "标记为已读" in text -> "消息已加载，已读状态暂未同步"
        "服务地址" in text || "https" in text -> "请在工具页的服务器设置中检查地址"
        "超时" in text || "timeout" in text || "timed out" in text -> "连接等待有些久，请稍后再试"
        "空间" in text || "enospc" in text -> "存储空间不足，请清理一些空间后再试"
        "草稿保存" in text -> "草稿暂时无法保存，请检查存储空间"
        "图片不能超过" in text || "413" in text -> "图片过大，请选择不超过 50 MiB 的图片"
        "动态图" in text -> "暂不支持动态图，请选择静态图片"
        "仅支持静态" in text || "仅支持 jpeg" in text -> "请选择 JPEG、PNG 或 WebP 格式的静态图片"
        "图片损坏" in text || "图片尺寸" in text || "图片方向" in text -> "这张图片无法处理，请重新选择"
        "图片压缩" in text -> "图片处理失败，请重试或重新选择"
        "读取图片" in text || "无法读取图片" in text -> "这张图片暂时无法读取，请重新选择"
        "图片不存在" in text -> "图片已失效，请重新选择后再试"
        "账号已切换" in text -> "账号已切换，请返回后重新操作"
        "权限" in text || "403" in text || "forbidden" in text -> "当前无法访问此内容，请检查权限或账号"
        "404" in text || "不存在" in text || "不可见" in text -> "内容已被删除或当前账号无法查看"
        "429" in text || "频繁" in text -> "操作有些频繁，请稍等一下再试"
        "500" in text || "502" in text || "503" in text || "服务暂" in text -> "服务暂时不可用，请稍后再试"
        "网络" in text || "连接" in text || "离线" in text || "offline" in text || "connect" in text || "host" in text || "socket" in text -> "请检查网络连接，或稍后再试"
        else -> "暂时未能完成，请稍后再试"
    }
}
