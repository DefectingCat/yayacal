package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.math.absoluteValue
import plus.rua.project.PhotoEditorState
import plus.rua.project.PhotoEditorViewModel
import plus.rua.project.RotationGeometry
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
                        text = "照片图层编辑",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.editorState != null && !state.saving,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("editor_save")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("完成", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        onApplyCrop = viewModel::applyCrop,
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
    onApplyCrop: () -> Unit,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    onAddPoint: (Offset) -> Unit,
    onEndStroke: () -> Unit,
    onUndoStroke: () -> Unit,
    onStrokeColorChange: (Color) -> Unit,
    onDisplaySizeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Mode Selector Tab Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EditTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick = { onTabChange(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, EditTab.entries.size),
                        icon = {
                            Icon(
                                when (tab) {
                                    EditTab.ROTATE -> Icons.AutoMirrored.Outlined.RotateRight
                                    EditTab.CROP -> Icons.Outlined.Crop
                                    EditTab.HANDWRITE -> Icons.Outlined.Brush
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
                                    EditTab.HANDWRITE -> "涂鸦"
                                },
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // Photo Canvas viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val srcAspect = state.sourceBitmap.width.toFloat() / state.sourceBitmap.height.toFloat()
            val containerAspect by animateFloatAsState(
                targetValue = RotationGeometry.stableAspect(srcAspect, state.rotationDegrees),
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "rotateAspect"
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.aspectRatio(containerAspect)
            ) {
                EditableImage(
                    state = state,
                    mode = activeTab,
                    containerAspect = containerAspect,
                    onCropChange = onCropChange,
                    onAddPoint = onAddPoint,
                    onEndStroke = onEndStroke,
                    onDisplaySizeChange = onDisplaySizeChange
                )
            }

            if (saving) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                        Text(
                            text = "正在处理照片...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Tool Control Panel with Smooth Animated Transition
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                (slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut(tween(180)))
            },
            label = "tabTransition",
            modifier = Modifier.fillMaxWidth()
        ) { targetMode ->
            ToolPanel(
                mode = targetMode,
                state = state,
                onRotate = onRotate,
                onCropToggle = onCropToggle,
                onApplyCrop = onApplyCrop,
                onUndoStroke = onUndoStroke,
                onStrokeColorChange = onStrokeColorChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Composable
private fun EditableImage(
    state: PhotoEditorState,
    mode: EditTab,
    containerAspect: Float,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    onAddPoint: (Offset) -> Unit,
    onEndStroke: () -> Unit,
    onDisplaySizeChange: (Float, Float) -> Unit
) {
    val displayRotation = remember(state.sourceBitmap) {
        Animatable(state.rotationDegrees.toFloat())
    }
    LaunchedEffect(state.rotationDegrees) {
        displayRotation.animateTo(
            targetValue = state.rotationDegrees.toFloat(),
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    onDisplaySizeChange(size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        val angle = displayRotation.value
        val offset = angle - state.rotationDegrees
        val scale = RotationGeometry.coverScale(offset, containerAspect)
        Image(
            bitmap = state.sourceBitmap.asImageBitmap(),
            contentDescription = "编辑中的照片",
            modifier = Modifier
                .layout { measurable, constraints ->
                    val isSwapped = (state.rotationDegrees % 180 != 0)
                    val targetWidth = if (isSwapped) constraints.maxHeight else constraints.maxWidth
                    val targetHeight = if (isSwapped) constraints.maxWidth else constraints.maxHeight
                    val placeable = measurable.measure(
                        Constraints.fixed(targetWidth, targetHeight)
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x = (constraints.maxWidth - targetWidth) / 2
                        val y = (constraints.maxHeight - targetHeight) / 2
                        placeable.placeRelative(x, y)
                    }
                }
                .graphicsLayer {
                    rotationZ = angle
                    scaleX = scale
                    scaleY = scale
                }
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

        if (mode == EditTab.CROP && state.cropEnabled) {
            CropOverlay(
                state = state,
                onCropChange = onCropChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
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

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY, NONE }

private data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun hitHandle(
    ox: Float,
    oy: Float,
    l: Float,
    t: Float,
    r: Float,
    b: Float
): CropHandle {
    val corner = 0.1f
    val atTopLeft = (ox - l).absoluteValue < corner && (oy - t).absoluteValue < corner
    val atTopRight = (ox - r).absoluteValue < corner && (oy - t).absoluteValue < corner
    val atBottomLeft = (ox - l).absoluteValue < corner && (oy - b).absoluteValue < corner
    val atBottomRight = (ox - r).absoluteValue < corner && (oy - b).absoluteValue < corner
    return when {
        atTopLeft -> CropHandle.TOP_LEFT
        atTopRight -> CropHandle.TOP_RIGHT
        atBottomLeft -> CropHandle.BOTTOM_LEFT
        atBottomRight -> CropHandle.BOTTOM_RIGHT
        ox in l..r && oy in t..b -> CropHandle.BODY
        else -> CropHandle.NONE
    }
}

private fun moveCrop(
    dx: Float,
    dy: Float,
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    handle: CropHandle,
    minSize: Float = 0.1f
): CropRect {
    val left = l.coerceIn(0f, 1f)
    val top = t.coerceIn(0f, 1f)
    val right = r.coerceIn(0f, 1f)
    val bottom = b.coerceIn(0f, 1f)
    return when (handle) {
        CropHandle.TOP_LEFT -> {
            val nl = (left + dx).coerceIn(0f, right - minSize)
            val nt = (top + dy).coerceIn(0f, bottom - minSize)
            CropRect(nl, nt, right, bottom)
        }
        CropHandle.TOP_RIGHT -> {
            val nr = (right + dx).coerceIn(left + minSize, 1f)
            val nt = (top + dy).coerceIn(0f, bottom - minSize)
            CropRect(left, nt, nr, bottom)
        }
        CropHandle.BOTTOM_LEFT -> {
            val nl = (left + dx).coerceIn(0f, right - minSize)
            val nb = (bottom + dy).coerceIn(top + minSize, 1f)
            CropRect(nl, top, right, nb)
        }
        CropHandle.BOTTOM_RIGHT -> {
            val nr = (right + dx).coerceIn(left + minSize, 1f)
            val nb = (bottom + dy).coerceIn(top + minSize, 1f)
            CropRect(left, top, nr, nb)
        }
        CropHandle.BODY -> {
            val w = right - left
            val h = bottom - top
            val nl = (left + dx).coerceIn(0f, 1f - w)
            val nt = (top + dy).coerceIn(0f, 1f - h)
            CropRect(nl, nt, nl + w, nt + h)
        }
        CropHandle.NONE -> CropRect(left, top, right, bottom)
    }
}

@Composable
private fun CropOverlay(
    state: PhotoEditorState,
    onCropChange: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragHandle by remember { mutableStateOf(CropHandle.NONE) }
    var dragLeft by remember { mutableFloatStateOf(0f) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragRight by remember { mutableFloatStateOf(0f) }
    var dragBottom by remember { mutableFloatStateOf(0f) }
    val currentState by rememberUpdatedState(state)

    val left = if (dragHandle != CropHandle.NONE) dragLeft else (state.cropLeft ?: 0.1f)
    val top = if (dragHandle != CropHandle.NONE) dragTop else state.cropTop
    val right = if (dragHandle != CropHandle.NONE) dragRight else (state.cropRight ?: 0.9f)
    val bottom = if (dragHandle != CropHandle.NONE) dragBottom else state.cropBottom

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val size = this.size
                    val ox = offset.x / size.width
                    val oy = offset.y / size.height
                    val baseL = currentState.cropLeft ?: 0.1f
                    val baseT = currentState.cropTop
                    val baseR = currentState.cropRight ?: 0.9f
                    val baseB = currentState.cropBottom
                    dragLeft = baseL
                    dragTop = baseT
                    dragRight = baseR
                    dragBottom = baseB
                    dragHandle = hitHandle(ox, oy, baseL, baseT, baseR, baseB)
                },
                onDrag = { change, drag ->
                    if (dragHandle == CropHandle.NONE) return@detectDragGestures
                    change.consume()
                    val size = this.size
                    val dx = drag.x / size.width
                    val dy = drag.y / size.height
                    val cur = moveCrop(dx, dy, dragLeft, dragTop, dragRight, dragBottom, dragHandle)
                    dragLeft = cur.left
                    dragTop = cur.top
                    dragRight = cur.right
                    dragBottom = cur.bottom
                },
                onDragEnd = {
                    if (dragHandle != CropHandle.NONE) {
                        onCropChange(dragLeft, dragTop, dragRight, dragBottom)
                    }
                    dragHandle = CropHandle.NONE
                },
                onDragCancel = { dragHandle = CropHandle.NONE }
            )
        }
    ) {
        val w = size.width
        val h = size.height
        val l = left * w
        val t = top * h
        val r = right * w
        val b = bottom * h
        drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(0f, 0f), size = Size(l, h))
        drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(r, 0f), size = Size(w - r, h))
        drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(l, 0f), size = Size(r - l, t))
        drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(l, b), size = Size(r - l, h - b))

        drawRect(
            color = Color.White,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = 2.5f)
        )
    }
}

/**
 * 底部照片编辑面板，根据当前 EditTab 提供特定的操作工具。
 */
@Composable
private fun ToolPanel(
    mode: EditTab,
    state: PhotoEditorState,
    onRotate: (Int) -> Unit,
    onCropToggle: () -> Unit,
    onApplyCrop: () -> Unit,
    onUndoStroke: () -> Unit,
    onStrokeColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (mode) {
                EditTab.ROTATE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { onRotate(-90) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.RotateLeft,
                                contentDescription = "左转 90°",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("左转 90°")
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "${(state.rotationDegrees % 360 + 360) % 360}°",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = { onRotate(90) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.RotateRight,
                                contentDescription = "右转 90°",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("右转 90°")
                        }
                    }
                }

                EditTab.CROP -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (state.cropEnabled) "拖动四角或框架调整裁剪区域" else "点击开启自由裁剪",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.cropEnabled) {
                                Button(
                                    onClick = onApplyCrop,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("确认裁剪区域")
                                }
                            } else {
                                Button(
                                    onClick = onCropToggle,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Crop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("开启裁剪")
                                }
                            }
                        }
                    }
                }

                EditTab.HANDWRITE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "涂鸦画笔颜色",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 使用 OutlinedButton 避免数字/汉字在宽高度受限的 IconButtons 里错位换行
                            OutlinedButton(
                                onClick = onUndoStroke,
                                enabled = state.strokes.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.strokes.isNotEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Undo,
                                    contentDescription = "撤销笔触",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "撤销",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        val palette = listOf(
                            Color(0xFFFF453A), // Red
                            Color(0xFFFF9F0A), // Orange
                            Color(0xFFFFD60A), // Yellow
                            Color(0xFF30D158), // Green
                            Color(0xFF64D2FF), // Cyan
                            Color(0xFF007AFF), // Blue
                            Color(0xFF1D1B20)  // Dark Gray / Black
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            palette.forEach { c ->
                                val selected = state.strokeColor == c
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(c, CircleShape)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { onStrokeColorChange(c) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = if (c == Color(0xFFFFD60A) || c == Color(0xFF64D2FF)) Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
