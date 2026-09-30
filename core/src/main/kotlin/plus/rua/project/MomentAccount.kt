package plus.rua.project

import android.content.Context
import androidx.annotation.DrawableRes
import plus.rua.project.shared.R
import java.io.File

/**
 * 朋友圈账号实体定义。
 *
 * @param id 账号全局唯一标识（如 "xiaobai", "xiaojimao"）
 * @param name 账号显示昵称（如 "小白"、"小鸡毛"）
 * @param avatarResId 内置头像资源 ID
 * @param filename 导出到应用私有目录时的文件名
 * @param description 账号简介或角色说明
 */
data class MomentAccount(
    val id: String,
    val name: String,
    @field:DrawableRes val avatarResId: Int,
    val filename: String,
    val description: String = "",
) {
    companion object {
        const val ID_XIAOBAI = "xiaobai"
        const val ID_XIAOJIMAO = "xiaojimao"

        val ACCOUNT_XIAOBAI =
            MomentAccount(
                id = ID_XIAOBAI,
                name = "小白",
                avatarResId = R.drawable.avatar_xiaobai,
                filename = "avatar_xiaobai.jpg",
                description = "可爱小白狗",
            )

        val ACCOUNT_XIAOJIMAO =
            MomentAccount(
                id = ID_XIAOJIMAO,
                name = "小鸡毛",
                avatarResId = R.drawable.avatar_xiaojimao,
                filename = "avatar_xiaojimao.jpg",
                description = "活泼线条小狗",
            )

        val ALL_ACCOUNTS = listOf(ACCOUNT_XIAOBAI, ACCOUNT_XIAOJIMAO)

        /**
         * 根据账号 ID 获取账号信息，未匹配时默认返回小白。
         */
        fun findById(id: String?): MomentAccount = ALL_ACCOUNTS.find { it.id == id } ?: ACCOUNT_XIAOBAI

        /**
         * 根据账号名称获取账号信息，未匹配时默认返回小白。
         */
        fun findByName(name: String?): MomentAccount = ALL_ACCOUNTS.find { it.name == name } ?: ACCOUNT_XIAOBAI

        /**
         * 将内置头像资源复制到应用私有 filesDir/moments 目录下，以便生成标准的绝对路径供图片加载和全局持久化。
         *
         * @param context Android 上下文
         * @param account 选中的账号
         * @return 写入本地磁盘后的头像文件绝对路径
         */
        fun ensureAvatarFile(context: Context, account: MomentAccount): String {
            val dir = File(context.filesDir, MomentsViewModel.MOMENTS_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(dir, account.filename)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    context.resources.openRawResource(account.avatarResId).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                    // 异常时保留后续重试
                }
            }
            return targetFile.absolutePath
        }
    }
}
