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
import java.util.UUID
import kotlin.time.Clock

/**
 * 朋友圈页面 UI 状态。
 *
 * @param username 用户名
 * @param avatarPath 当前设置的头像文件路径（为 null 时使用默认占位符）
 * @param coverPath 当前设置的封面文件路径（为 null 时使用默认占位符）
 * @param posts 已发布的朋友圈动态列表
 */
data class MomentsUiState(
    val username: String = MomentsStorage.DEFAULT_USERNAME,
    val avatarPath: String? = null,
    val coverPath: String? = null,
    val posts: List<MomentPost> = emptyList(),
)

/**
 * 朋友圈页面 ViewModel，负责头像、相册封面以及动态列表的展示与持久化。
 *
 * @param storage 朋友圈配置存储仓库
 * @param filesDir 应用 filesDir 目录，用于安全存放用户头像与封面文件
 * @param ioDispatcher IO 协程调度器（支持测试注入）
 * @param clock 评论发送时间的时钟（支持测试注入）
 */
class MomentsViewModel(
    private val storage: MomentsStorage,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MomentsUiState(
                username = storage.getUsername(),
                avatarPath = storage.getAvatarPath(),
                coverPath = storage.getCoverPath(),
                posts = storage.getPosts(),
            ),
        )
    val uiState: StateFlow<MomentsUiState> = _uiState.asStateFlow()

    /**
     * 刷新已发布动态列表。
     */
    fun refreshPosts() {
        val posts = storage.getPosts()
        _uiState.update {
            it.copy(
                posts = posts,
                username = storage.getUsername(),
                avatarPath = storage.getAvatarPath(),
                coverPath = storage.getCoverPath(),
            )
        }
    }

    /** 切换当前本地用户的点赞状态；已删除的动态不会被重新写入。 */
    fun toggleLike(postId: String) {
        val post = storage.getPosts().find { it.id == postId } ?: return
        storage.savePost(post.copy(isLikedByMe = !post.isLikedByMe))
        refreshPosts()
    }

    /**
     * 保存文字或图片评论。空评论与不存在的动态返回 false。
     *
     * @param postId 被评论的动态 ID
     * @param text 评论正文，会去除首尾空白
     * @param replyToName 回复对象昵称，为 null 时评论整条动态
     * @param photoPath 已保存的评论图片路径
     * @return 是否保存成功
     */
    fun addComment(
        postId: String,
        text: String,
        replyToName: String? = null,
        photoPath: String? = null,
    ): Boolean {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty() && photoPath == null) return false
        val post = storage.getPosts().find { it.id == postId } ?: return false
        val comment = MomentComment(
            authorName = storage.getUsername(),
            text = trimmedText,
            timestamp = clock.now().toEpochMilliseconds(),
            replyToName = replyToName,
            photoPath = photoPath,
        )
        storage.savePost(post.copy(comments = post.comments + comment))
        refreshPosts()
        return true
    }

    /** 将系统相册图片复制到私有目录后发送评论；复制失败时保留编辑器草稿。 */
    suspend fun addPhotoComment(
        context: Context,
        postId: String,
        uri: Uri,
        text: String,
        replyToName: String? = null,
    ): Boolean {
        var savedPath: String? = null
        var saved = false
        try {
            withContext(ioDispatcher) {
                savedPath = copyUriToFile(context, uri, prefix = "comment")
            }
            val path = savedPath ?: return false
            saved = addComment(postId, text, replyToName, path)
            return saved
        } finally {
            if (!saved) savedPath?.let { File(it).delete() }
        }
    }

    /**
     * 删除指定的动态，并清理其关联的配图私有文件。
     */
    fun deletePost(postId: String) {
        val postToDelete = storage.getPosts().find { it.id == postId }
        storage.deletePost(postId)
        if (postToDelete != null) {
            viewModelScope.launch(ioDispatcher) {
                val referencedPaths = storage.getPosts().flatMap { post ->
                    post.photoPaths + post.comments.mapNotNull { it.photoPath }
                }.toSet()
                val ownedDir = File(filesDir, MOMENTS_DIR_NAME).canonicalFile
                val paths = postToDelete.photoPaths + postToDelete.comments.mapNotNull { it.photoPath }
                for (path in paths.distinct()) {
                    // 只清理本功能的私有文件，保留其他动态仍在使用的图片。
                    if (path !in referencedPaths) {
                        runCatching {
                            val file = File(path).canonicalFile
                            if (file.parentFile == ownedDir) file.delete()
                        }
                    }
                }
            }
        }
        refreshPosts()
    }

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
                    copyUriToFile(context, uri, prefix = "avatar")
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
     * 将用户从系统相册选取的封面 URI 保存到应用本地私有目录，并更新持久化设置与 UI 状态。
     *
     * @param context Android 上下文
     * @param uri 用户从系统相册选取的封面图片 content:// URI
     */
    fun setCoverFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val savedPath =
                withContext(ioDispatcher) {
                    copyUriToFile(context, uri, prefix = "cover")
                }

            if (savedPath != null) {
                // 删除旧封面文件以释放存储空间
                val oldPath = _uiState.value.coverPath
                if (oldPath != null && oldPath != savedPath) {
                    withContext(ioDispatcher) {
                        try {
                            File(oldPath).delete()
                        } catch (_: Exception) {}
                    }
                }

                storage.saveCoverPath(savedPath)
                _uiState.update { it.copy(coverPath = savedPath) }
            }
        }
    }

    /**
     * 设置自定义封面路径（主要用于测试与直接设置）。
     *
     * @param path 封面绝对路径
     */
    fun setCoverPath(path: String?) {
        storage.saveCoverPath(path)
        _uiState.update { it.copy(coverPath = path) }
    }

    /**
     * 将 URI 流复制到 filesDir/moments/ 目录下。
     */
    private fun copyUriToFile(
        context: Context,
        uri: Uri,
        prefix: String,
    ): String? {
        val momentsDir = File(filesDir, MOMENTS_DIR_NAME).apply { if (!exists()) mkdirs() }
        val targetFile = File(momentsDir, "${prefix}_${UUID.randomUUID()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            targetFile.absolutePath
        } catch (_: Exception) {
            targetFile.delete()
            null
        }
    }

    companion object {
        const val MOMENTS_DIR_NAME = "moments"
    }
}
