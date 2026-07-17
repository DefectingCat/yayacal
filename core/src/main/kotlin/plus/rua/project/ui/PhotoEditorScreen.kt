package plus.rua.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import plus.rua.project.PhotoEditorState
import plus.rua.project.PhotoEditorViewModel
import plus.rua.project.toPath

/**
 * 照片编辑页面，提供旋转 / 裁剪 / 手写三种编辑能力。
 *
 * 编辑完成后通过 [onSaved] 回调返回最终照片绝对路径，由 Activity 跳转记录编辑页。
 *
 * @param onBack 取消编辑返回回调
 * @param onSaved 保存成功回调，参数为最终照片绝对路径
 * @param sourcePath 源照片绝对路径（来自相机或详情页）
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    sourcePath: String,
    modifier: Modifier = Modifier
) {
    val viewModel: PhotoEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PhotoEditorViewModel(sourcePath) }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(EditTab.ROTATE) }
    val savedPath = state.savedPath

    if (savedPath != null) {
        LaunchedEffect(savedPath) { onSaved(savedPath) }
    }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "编辑照片",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::save,
                        enabled = state.editorState != null && !state.saving,
                        modifier = Modifier.testTag("editor_save")
                    ) {
                        Icon(Icons.Filled.Done, contentDescription = "保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error!!,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                state.editorState != null -> {
                    EditorBody(
                        state = state.editorState!!,
                        saving = state.saving,
                        activeTab = activeTab,
                        onTabChange = { activeTab = it },
                        onRotate = viewModel::rotate,
                        onCropToggle = viewModel::toggleCrop,
                        onCropChange = viewModel::updateCrop,
                        onAddPoint = viewModel::addStrokePoint,
                        onEndStroke = viewModel::endStroke,
                        onUndoStroke = viewModel::undoStroke,
                        onStrokeColorChange = viewModel::setStrokeColor,
                        onDisplaySizeChange = viewModel::updateDisplaySize,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private enum class EditTab { ROTATE, CROP, HANDWRITE }

@Composable
private fun EditorBody(
    state: PhotoEditorState,
    saving: Boolean,
    activeTab: EditTab,
    onTabChange: (EditTab) -> Unit,
    onRotate: (Int) -> Unit,
    onCropToggle: () -> Unit,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    onAddPoint: (Offset) -> Unit,
    onEndStroke: () -> Unit,
    onUndoStroke: () -> Unit,
    onStrokeColorChange: (Color) -> Unit,
    onDisplaySizeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            EditTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = activeTab == tab,
                    onClick = { onTabChange(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index, EditTab.entries.size),
                    icon = {
                        Icon(
                            when (tab) {
                                EditTab.ROTATE -> Icons.Filled.RotateRight
                                EditTab.CROP -> Icons.Filled.Crop
                                EditTab.HANDWRITE -> Icons.Filled.Brush
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            when (tab) {
                                EditTab.ROTATE -> "旋转"
                                EditTab.CROP -> "裁剪"
                                EditTab.HANDWRITE -> "手写"
                            }
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            EditableImage(
                state = state,
                mode = activeTab,
                onCropChange = onCropChange,
                onAddPoint = onAddPoint,
                onEndStroke = onEndStroke,
                onDisplaySizeChange = onDisplaySizeChange
            )
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        ToolBar(
            mode = activeTab,
            state = state,
            onRotate = onRotate,
            onCropToggle = onCropToggle,
            onUndoStroke = onUndoStroke,
            onStrokeColorChange = onStrokeColorChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
    }
}

@Composable
private fun EditableImage(
    state: PhotoEditorState,
    mode: EditTab,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    onAddPoint: (Offset) -> Unit,
    onEndStroke: () -> Unit,
    onDisplaySizeChange: (Float, Float) -> Unit
) {
    val rotatedBmp = remember(state.rotationDegrees, state.sourceBitmap) { state.rotatedBitmap }
    // 裁剪框拖动手柄（CROP 模式下使用）
    var dragHandle by remember { mutableStateOf<CropHandle?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(rotatedBmp.width.toFloat() / rotatedBmp.height.toFloat())
            .background(Color.Black)
    ) {
        Image(
            bitmap = rotatedBmp.asImageBitmap(),
            contentDescription = "编辑中的照片",
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    if (mode == EditTab.HANDWRITE) {
                        detectDragGestures(
                            onDragStart = { offset -> onAddPoint(offset) },
                            onDrag = { change, _ ->
                                change.consume()
                                onAddPoint(change.position)
                            },
                            onDragEnd = onEndStroke,
                            onDragCancel = onEndStroke
                        )
                    }
                },
            contentScale = ContentScale.Fit
        )

        // 裁剪框覆盖层
        if (mode == EditTab.CROP && state.cropEnabled) {
            CropOverlay(
                state = state,
                dragHandle = dragHandle,
                onHandleChange = { dragHandle = it },
                onCropChange = onCropChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 手写笔触覆盖层 + 同步显示尺寸
        Canvas(modifier = Modifier.fillMaxSize()) {
            onDisplaySizeChange(size.width, size.height)
            state.strokes.forEach { stroke ->
                drawPath(
                    path = stroke.toPath(),
                    color = stroke.color,
                    style = Stroke(
                        width = stroke.widthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, NONE }

/** 裁剪框四元组，支持解构以简化 onDrag 回传。 */
private data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Composable
private fun CropOverlay(
    state: PhotoEditorState,
    dragHandle: CropHandle?,
    onHandleChange: (CropHandle?) -> Unit,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val left = state.cropLeft ?: 0.1f
    val right = state.cropRight ?: 0.9f
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val size = this.size
                    val ox = offset.x / size.width
                    val oy = offset.y / size.height
                    onHandleChange(
                        when {
                            ox < 0.2f && oy < 0.2f -> CropHandle.TOP_LEFT
                            ox > 0.8f && oy < 0.2f -> CropHandle.TOP_RIGHT
                            ox < 0.2f && oy > 0.8f -> CropHandle.BOTTOM_LEFT
                            ox > 0.8f && oy > 0.8f -> CropHandle.BOTTOM_RIGHT
                            else -> CropHandle.NONE
                        }
                    )
                },
                onDrag = { change, drag ->
                    change.consume()
                    val size = this.size
                    val dx = drag.x / size.width
                    val dy = drag.y / size.height
                    val (nL, nT, nR, nB) = when (dragHandle) {
                        CropHandle.TOP_LEFT ->
                            CropRect(left + dx, state.cropTop + dy, right, state.cropBottom)
                        CropHandle.TOP_RIGHT ->
                            CropRect(left, state.cropTop + dy, right + dx, state.cropBottom)
                        CropHandle.BOTTOM_LEFT ->
                            CropRect(left + dx, state.cropTop, right, state.cropBottom + dy)
                        CropHandle.BOTTOM_RIGHT ->
                            CropRect(left, state.cropTop, right + dx, state.cropBottom + dy)
                        else -> return@detectDragGestures
                    }
                    onCropChange(nL, nT, nR, nB)
                },
                onDragEnd = { onHandleChange(null) },
                onDragCancel = { onHandleChange(null) }
            )
        }
    ) {
        val w = size.width
        val h = size.height
        val l = left * w
        val t = state.cropTop * h
        val r = right * w
        val b = state.cropBottom * h
        // 四周遮罩
        drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, 0f), size = Size(l, h))
        drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(r, 0f), size = Size(w - r, h))
        drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(l, 0f), size = Size(r - l, t))
        drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(l, b), size = Size(r - l, h - b))
        // 裁剪框白线
        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun ToolBar(
    mode: EditTab,
    state: PhotoEditorState,
    onRotate: (Int) -> Unit,
    onCropToggle: () -> Unit,
    onUndoStroke: () -> Unit,
    onStrokeColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (mode) {
            EditTab.ROTATE -> {
                IconButton(onClick = { onRotate(-90) }) {
                    Icon(Icons.Filled.RotateLeft, contentDescription = "左转 90°")
                }
                IconButton(onClick = { onRotate(90) }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = "右转 90°")
                }
            }
            EditTab.CROP -> {
                FilledIconButton(onClick = onCropToggle) {
                    Text(if (state.cropEnabled) "关闭裁剪" else "开启裁剪")
                }
            }
            EditTab.HANDWRITE -> {
                IconButton(onClick = onUndoStroke, enabled = state.strokes.isNotEmpty()) {
                    Icon(Icons.Filled.Undo, contentDescription = "撤销笔触")
                }
                listOf(Color.Red, Color.Yellow, Color.White, Color.Black).forEach { c ->
                    val selected = state.strokeColor == c
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(c, RoundedCornerShape(14.dp))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .pointerInput(c) {
                                detectTapGestures { onStrokeColorChange(c) }
                            }
                    )
                }
            }
        }
    }
}
