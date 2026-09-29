package plus.rua.project

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 朋友圈页面 UI 状态。
 *
 * @param username 用户名
 * @param avatarPath 当前设置的头像文件路径（为 null 时使用默认占位符）
 * @param coverPath 当前设置的封面文件路径（为 null 时使用默认占位符）
 */
data class MomentsUiState(
    val username: String = MomentsStorage.DEFAULT_USERNAME,
    val avatarPath: String? = null,
    val coverPath: String? = null,
)

/**
 * 朋友圈页面 ViewModel，负责头像设置与持久化。
 *
 * @param storage 朋友圈配置存储仓库
 * @param filesDir 应用 filesDir 目录，用于安全存放用户头像文件
 * @param ioDispatcher IO 协程调度器（支持测试注入）
 */
class MomentsViewModel(
    private val storage: MomentsStorage,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MomentsUiState(
                username = storage.getUsername(),
                avatarPath = storage.getAvatarPath(),
                coverPath = storage.getCoverPath(),
            ),
        )
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()

    /**
     * 将用户从系统相册选取的头像 URI 保存到应用本地私有目录，并更新持久化设置与 UI 状态。
     *
     * @param context Android 上下文（用于 openInputStream 读取图片）
     * @param uri 用户从系统相册选取的 content:// URI
     */
    fun setAvatarFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val savedPath =
                withContext(ioDispatcher) {
                    copyUriToAvatarFile(context, uri)
                }

            if (savedPath != null) {
                // 删除旧头像文件以释放存储空间
                val oldPath = _uiState.value.avatarPath
                if (oldPath != null && oldPath != savedPath) {
                    withContext(ioDispatcher) {
                        try {
                            File(oldPath).delete()
                        } catch (_: Exception) {}
                    }
                }

                storage.saveAvatarPath(savedPath)
                _uiState.update { it.copy(avatarPath = savedPath) }
            }
        }
    }

    /**
     * 设置自定义头像路径（主要用于测试与直接设置）。
     *
     * @param path 头像绝对路径
     */
    fun setAvatarPath(path: String?) {
        storage.saveAvatarPath(path)
        _uiState.update { it.copy(avatarPath = path) }
    }

    /**
     * 将 URI 流复制到 filesDir/moments/ 目录下。
     */
    private fun copyUriToAvatarFile(context: Context, uri: Uri): String? {
        return try {
            val momentsDir = File(filesDir, MOMENTS_DIR_NAME).apply { if (!exists()) mkdirs() }
            val avatarFile = File(momentsDir, "avatar_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(avatarFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            avatarFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val MOMENTS_DIR_NAME = "moments"
    }
}
