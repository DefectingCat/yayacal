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
 * 发布朋友圈页面 UI 状态。
 *
 * @param text 正文文本
 * @param photos 已选取的配图路径列表（最多 9 张）
 * @param selectedLocation 当前选中的位置（为 null 表示不显示位置）
 * @param availableLocations 可选位置列表
 * @param locationSearchQuery 位置搜索框文本
 * @param visibility 可见性范围（"公开" / "私密"）
 * @param isLocationPickerVisible 是否正在展示选择位置子页面
 * @param isPublishing 是否正在保存发布中
 */
data class MomentsPublishUiState(
    val text: String = "",
    val photos: List<String> = emptyList(),
    val selectedLocation: MomentLocationItem? = null,
    val availableLocations: List<MomentLocationItem> = MomentsLocationProvider.getDefaultLocations(),
    val locationSearchQuery: String = "",
    val visibility: String = "公开",
    val isLocationPickerVisible: Boolean = false,
    val isPublishing: Boolean = false,
) {
    val canPublish: Boolean get() = text.isNotBlank() || photos.isNotEmpty()
    val remainingPhotoSlots: Int get() = (MAX_PHOTOS - photos.size).coerceAtLeast(0)

    val displayedLocations: List<MomentLocationItem>
        get() = MomentsLocationProvider.filterLocations(availableLocations, locationSearchQuery)

    companion object {
        const val MAX_PHOTOS = 9
    }
}

/**
 * 朋友圈发布页面 ViewModel。
 *
 * @param storage 朋友圈数据存储仓库
 * @param filesDir 应用私有 filesDir 目录
 * @param initialVisibility 初始可见性（默认 "公开"）
 * @param ioDispatcher IO 调度器（支持测试注入）
 */
class MomentsPublishViewModel(
    private val storage: MomentsStorage,
    private val filesDir: File,
    initialVisibility: String = "公开",
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MomentsPublishUiState(visibility = initialVisibility),
    )
    val uiState: StateFlow<MomentsPublishUiState> = _uiState.asStateFlow()

    fun onTextChanged(newText: String) {
        _uiState.update { it.copy(text = newText) }
    }

    /**
     * 将用户从系统多图选择器中选择的 URI 复制到本地私有目录，并添加到图片列表。
     */
    fun addPhotosFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentCount = _uiState.value.photos.size
        val allowedUris = uris.take(MomentsPublishUiState.MAX_PHOTOS - currentCount)
        if (allowedUris.isEmpty()) return

        viewModelScope.launch {
            val copiedPaths = withContext(ioDispatcher) {
                val momentsDir = File(filesDir, MomentsViewModel.MOMENTS_DIR_NAME).apply {
                    if (!exists()) mkdirs()
                }
                allowedUris.mapNotNull { uri ->
                    try {
                        val targetFile = File(
                            momentsDir,
                            "moment_photo_${System.currentTimeMillis()}_${System.nanoTime() % 1000}.jpg",
                        )
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: return@mapNotNull null
                        targetFile.absolutePath
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            _uiState.update { state ->
                val combined = (state.photos + copiedPaths).take(MomentsPublishUiState.MAX_PHOTOS)
                state.copy(photos = combined)
            }
        }
    }

    /**
     * 直接添加图片路径（用于测试）。
     */
    fun addPhotoPaths(paths: List<String>) {
        _uiState.update { state ->
            val combined = (state.photos + paths).take(MomentsPublishUiState.MAX_PHOTOS)
            state.copy(photos = combined)
        }
    }

    /**
     * 移除指定索引处的配图。
     */
    fun removePhotoAt(index: Int) {
        _uiState.update { state ->
            if (index in state.photos.indices) {
                val removedPath = state.photos[index]
                viewModelScope.launch(ioDispatcher) {
                    try {
                        File(removedPath).delete()
                    } catch (_: Exception) {}
                }
                val updated = state.photos.toMutableList().apply { removeAt(index) }
                state.copy(photos = updated)
            } else {
                state
            }
        }
    }

    fun openLocationPicker() {
        _uiState.update { it.copy(isLocationPickerVisible = true, locationSearchQuery = "") }
    }

    fun closeLocationPicker() {
        _uiState.update { it.copy(isLocationPickerVisible = false, locationSearchQuery = "") }
    }

    fun onLocationSearchQueryChanged(query: String) {
        _uiState.update { it.copy(locationSearchQuery = query) }
    }

    fun selectLocation(item: MomentLocationItem?) {
        val selected = if (item == null || item.isNone) null else item
        _uiState.update {
            it.copy(
                selectedLocation = selected,
                isLocationPickerVisible = false,
                locationSearchQuery = "",
            )
        }
    }

    /**
     * 自动通过系统 GPS/网络定位并刷新可选位置列表。
     */
    fun autoDetectLocation(context: Context) {
        viewModelScope.launch {
            val resolved = MomentsLocationProvider.resolveLocation(context)
            _uiState.update { it.copy(availableLocations = resolved) }
        }
    }

    /**
     * 点击“发表”按钮，将动态写入存储，并通知外部完成。
     */
    fun publish(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (!state.canPublish || state.isPublishing) return

        _uiState.update { it.copy(isPublishing = true) }

        viewModelScope.launch {
            val post = MomentPost(
                text = state.text.trim(),
                photoPaths = state.photos,
                location = state.selectedLocation?.name,
                locationAddress = state.selectedLocation?.address,
                visibility = state.visibility,
                timestamp = System.currentTimeMillis(),
            )
            withContext(ioDispatcher) {
                storage.savePost(post)
            }
            _uiState.update { it.copy(isPublishing = false) }
            onSuccess()
        }
    }
}
