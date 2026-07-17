package plus.rua.project

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 照片编辑器 UI 状态。
 *
 * @param loading 加载源图中
 * @param editorState 已加载的编辑状态；加载完成前为 null
 * @param error 加载失败信息
 * @param displayWidth 图片显示区域宽度（笔触坐标系基准，由 UI 回传）
 * @param displayHeight 图片显示区域高度
 * @param saving 保存中
 * @param savedPath 保存完成后的最终图片绝对路径；UI 据此触发跳转
 */
data class PhotoEditorUiState(
    val loading: Boolean = true,
    val editorState: PhotoEditorState? = null,
    val error: String? = null,
    val displayWidth: Float = 0f,
    val displayHeight: Float = 0f,
    val saving: Boolean = false,
    val savedPath: String? = null
)

/**
 * 照片编辑器 ViewModel，持有旋转/裁剪/手写状态并负责落盘。
 *
 * @param sourcePath 源图绝对路径
 */
class PhotoEditorViewModel(
    private val sourcePath: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoEditorUiState())
    val uiState: StateFlow<PhotoEditorUiState> = _uiState.asStateFlow()

    init { loadSource() }

    private fun loadSource() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bmp = PhotoProcessor.loadSampled(sourcePath)
                PhotoEditorState(
                    sourceBitmap = bmp,
                    sourceAbsolutePath = sourcePath
                )
            }.onSuccess { state ->
                _uiState.value = PhotoEditorUiState(loading = false, editorState = state)
            }.onFailure { e ->
                _uiState.value = PhotoEditorUiState(
                    loading = false,
                    error = "加载图片失败：${e.message}"
                )
            }
        }
    }

    /**
     * 更新显示区域尺寸，用于笔触坐标归一化与落盘缩放。
     */
    fun updateDisplaySize(w: Float, h: Float) {
        _uiState.update { it.copy(displayWidth = w, displayHeight = h) }
    }

    /** 顺时针/逆时针旋转 90° 的倍数。 */
    fun rotate(delta: Int) {
        update { it.copy(rotationDegrees = it.rotationDegrees + delta) }
    }

    /** 开启/关闭裁剪。开启时使用默认居中 4:3 裁剪框。 */
    fun toggleCrop() {
        update {
            if (it.cropEnabled) {
                it.copy(cropLeft = null, cropRight = null, cropTop = 0f, cropBottom = 1f)
            } else {
                it.copy(cropLeft = 0.1f, cropTop = 0.1f, cropRight = 0.9f, cropBottom = 0.9f)
            }
        }
    }

    /** 更新裁剪框比例。 */
    fun updateCrop(left: Float, top: Float, right: Float, bottom: Float) {
        update {
            it.copy(
                cropLeft = left.coerceIn(0f, 1f),
                cropTop = top.coerceIn(0f, 1f),
                cropRight = right.coerceIn(0f, 1f),
                cropBottom = bottom.coerceIn(0f, 1f)
            )
        }
    }

    /** 追加一个手写笔触采样点。 */
    fun addStrokePoint(offset: Offset) {
        update { it.withAddedPoint(offset) }
    }

    /** 结束当前笔触（抬起手指）。 */
    fun endStroke() {
        update { it.withEndedStroke() }
    }

    /** 撤销最近一条笔触。 */
    fun undoStroke() {
        update { it.copy(strokes = it.strokes.dropLast(1)) }
    }

    /** 设置笔触颜色。 */
    fun setStrokeColor(color: Color) {
        update { it.copy(strokeColor = color) }
    }

    /** 设置笔触线宽。 */
    fun setStrokeWidth(widthPx: Float) {
        update { it.copy(strokeWidthPx = widthPx) }
    }

    /** 保存编辑结果到新文件。 */
    fun save() {
        val state = _uiState.value
        val editor = state.editorState ?: return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val destFile = File(editor.sourceAbsolutePath).let { src ->
                    File(src.parentFile, "edited_${System.currentTimeMillis()}.jpg")
                }
                PhotoProcessor.render(
                    source = editor.sourceBitmap,
                    rotationDegrees = editor.rotationDegrees,
                    cropLeft = editor.cropLeft,
                    cropTop = editor.cropTop,
                    cropRight = editor.cropRight,
                    cropBottom = editor.cropBottom,
                    strokes = editor.strokes,
                    displayWidth = state.displayWidth,
                    displayHeight = state.displayHeight,
                    destFile = destFile
                )
            }.onSuccess { path ->
                _uiState.update { it.copy(saving = false, savedPath = path) }
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    private inline fun update(block: (PhotoEditorState) -> PhotoEditorState) {
        _uiState.update { current ->
            current.copy(editorState = current.editorState?.let(block))
        }
    }
}
