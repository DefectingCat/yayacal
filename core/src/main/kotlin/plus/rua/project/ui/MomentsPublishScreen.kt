package plus.rua.project.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import plus.rua.project.MomentsPublishUiState
import plus.rua.project.MomentsPublishViewModel
import plus.rua.project.MomentsStorage
import java.io.File

/**
 * 朋友圈发布页面子页面导航状态枚举。
 */
private enum class MomentsPublishSubScreen {
    MAIN,
    LOCATION,
    VISIBILITY,
}

/**
 * 朋友圈发布页面有状态组件，管理配图选取、自动定位、发布持久化及所在位置选择弹层。
 *
 * @param onCancel 点击左上角“取消”时触发
 * @param onPublishedSuccess 发布成功并存入数据库后触发
 * @param initialVisibility 初始可见范围（默认 "公开"）
 * @param viewModel 发布页面 ViewModel
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsPublishScreen(
    onCancel: () -> Unit,
    onPublishedSuccess: () -> Unit,
    initialVisibility: String = "公开",
    viewModel: MomentsPublishViewModel = run {
        val context = LocalContext.current.applicationContext
        viewModel(
            factory =
            viewModelFactory {
                initializer {
                    MomentsPublishViewModel(
                        storage = MomentsStorage.fromContext(context),
                        filesDir = context.filesDir,
                        initialVisibility = initialVisibility,
                        repository = plus.rua.project.MomentsConnection.repository(context),
                        draftNamespace = plus.rua.project.MomentsConnection.url(context),
                    )
                }
            },
        )
    },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 页面加载时自动执行一次位置探测
    LaunchedEffect(Unit) {
        viewModel.autoDetectLocation(context)
    }

    // 多图选择器（上限 9 张）
    val pickMultipleMediaLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(
                maxItems = MomentsPublishUiState.MAX_PHOTOS,
            ),
        ) { uris ->
            viewModel.addPhotosFromUris(context, uris)
        }

    val currentSubScreen = when {
        uiState.isLocationPickerVisible -> MomentsPublishSubScreen.LOCATION
        uiState.isVisibilityPickerVisible -> MomentsPublishSubScreen.VISIBILITY
        else -> MomentsPublishSubScreen.MAIN
    }

    BackHandler(enabled = currentSubScreen != MomentsPublishSubScreen.MAIN) {
        when (currentSubScreen) {
            MomentsPublishSubScreen.LOCATION -> viewModel.closeLocationPicker()
            MomentsPublishSubScreen.VISIBILITY -> viewModel.closeVisibilityPicker()
            MomentsPublishSubScreen.MAIN -> Unit
        }
    }

    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (targetState != MomentsPublishSubScreen.MAIN && initialState == MomentsPublishSubScreen.MAIN) {
                // 前进 Push：子页面从右侧滑入并淡入，主页面向左微移并微弱淡出
                (
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(240))
                    ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(200)),
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (targetState == MomentsPublishSubScreen.MAIN && initialState != MomentsPublishSubScreen.MAIN) {
                // 返回 Pop：子页面向右滑出退场，主页面自左微移恢复原位并淡入
                (
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    ) + fadeIn(animationSpec = tween(240))
                    ).togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    ) + fadeOut(animationSpec = tween(200)),
                ).apply {
                    targetContentZIndex = 0f
                }
            } else {
                fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
            }
        },
        label = "moments_publish_screen_transition",
        modifier = modifier.fillMaxSize(),
    ) { subScreen ->
        when (subScreen) {
            MomentsPublishSubScreen.LOCATION -> {
                MomentsLocationScreen(
                    locations = uiState.displayedLocations,
                    selectedLocation = uiState.selectedLocation,
                    searchQuery = uiState.locationSearchQuery,
                    onSearchQueryChange = viewModel::onLocationSearchQueryChanged,
                    onSelectLocation = viewModel::selectLocation,
                    onCancel = viewModel::closeLocationPicker,
                )
            }

            MomentsPublishSubScreen.VISIBILITY -> {
                MomentsVisibilityScreen(
                    currentVisibility = uiState.visibility,
                    onDone = { visibility, tags ->
                        viewModel.selectVisibility(visibility, tags)
                    },
                    onCancel = viewModel::closeVisibilityPicker,
                )
            }

            MomentsPublishSubScreen.MAIN -> {
                MomentsPublishScreen(
                    uiState = uiState,
                    onCancel = onCancel,
                    onPublish = {
                        viewModel.publish(onSuccess = onPublishedSuccess)
                    },
                    onTextChange = viewModel::onTextChanged,
                    onAddPhotosClick = {
                        pickMultipleMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRemovePhoto = viewModel::removePhotoAt,
                    onLocationClick = viewModel::openLocationPicker,
                    onVisibilityClick = viewModel::openVisibilityPicker,
                )
            }
        }
    }
}

/**
 * 朋友圈发布页面无状态渲染组件。
 *
 * @param uiState 发布页面 UI 状态
 * @param onCancel 点击“取消”时触发
 * @param onPublish 点击“发表”时触发
 * @param onTextChange 正文输入文本改变时触发
 * @param onAddPhotosClick 点击“+”添加照片方块时触发
 * @param onRemovePhoto 点击某张配图右上角删除按钮时触发
 * @param onLocationClick 点击“所在位置”选项时触发
 * @param onVisibilityClick 点击“谁可以看”选项时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsPublishScreen(
    uiState: MomentsPublishUiState,
    onCancel: () -> Unit,
    onPublish: () -> Unit,
    onTextChange: (String) -> Unit,
    onAddPhotosClick: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onLocationClick: () -> Unit,
    onVisibilityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("moments_publish_screen"),
    ) {
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (uiState.isPreparingPhotos) Text("正在准备图片…", modifier = Modifier.padding(16.dp))
        // 1. 顶部操作栏：取消 | 占位 | 微信绿“发表”按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("publish_cancel_button"),
            ) {
                Text(
                    text = "取消",
                    color = Color(0xFF191919),
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onPublish,
                enabled = uiState.canPublish && !uiState.isPublishing,
                shape = RoundedCornerShape(4.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07C160),
                    disabledContainerColor = Color(0xFFA3E7C2),
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("publish_submit_button"),
            ) {
                Text(
                    text = if (uiState.isPublishing) "发表中..." else "发表",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 2. 页面主体（可滚动）：正文输入框、9 宫格配图、位置与可见性选项
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            // 文字输入区
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (uiState.text.isEmpty()) {
                    Text(
                        text = "这一刻的想法...",
                        color = Color(0xFFB2B2B2),
                        fontSize = 16.sp,
                    )
                }
                BasicTextField(
                    value = uiState.text,
                    onValueChange = onTextChange,
                    textStyle =
                    TextStyle(
                        fontSize = 16.sp,
                        color = Color(0xFF191919),
                        lineHeight = 22.sp,
                    ),
                    cursorBrush = SolidColor(Color(0xFF07C160)),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("publish_text_input"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 9 格配图网格
            MomentsPhotoGrid(
                photos = uiState.photos,
                maxPhotos = MomentsPublishUiState.MAX_PHOTOS,
                onAddPhotosClick = onAddPhotosClick,
                onRemovePhoto = onRemovePhoto,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 底部选项列表：所在位置、提醒谁看、谁可以看
            HorizontalDivider(color = Color(0xFFF2F2F2), thickness = 0.6.dp)

            // 所在位置
            PublishOptionRow(
                icon = Icons.Outlined.LocationOn,
                title = "所在位置",
                subtitle = uiState.selectedLocation?.name,
                isHighlight = uiState.selectedLocation != null,
                onClick = onLocationClick,
                testTag = "publish_option_location",
            )
            HorizontalDivider(
                color = Color(0xFFF2F2F2),
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 56.dp),
            )

            // 谁可以看
            PublishOptionRow(
                icon = Icons.Outlined.Person,
                title = "谁可以看",
                subtitle = uiState.formattedVisibility,
                onClick = onVisibilityClick,
                testTag = "publish_option_visibility",
            )
            HorizontalDivider(color = Color(0xFFF2F2F2), thickness = 0.6.dp)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 微信 3 列等比正方形配图网格组件。
 */
@Composable
private fun MomentsPhotoGrid(
    photos: List<String>,
    maxPhotos: Int,
    onAddPhotosClick: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items: List<String?> = photos + if (photos.size < maxPhotos) listOf(null) else emptyList()
    val rows = items.chunked(3)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (colIndex in 0 until 3) {
                    if (colIndex < rowItems.size) {
                        val item = rowItems[colIndex]
                        val itemIndex = rowIndex * 3 + colIndex
                        Box(
                            modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        ) {
                            if (item != null) {
                                PhotoThumbnail(
                                    path = item,
                                    onDelete = { onRemovePhoto(itemIndex) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                AddPhotoPlaceholder(
                                    onClick = onAddPhotosClick,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    } else {
                        // 空白补齐占位
                        Spacer(
                            modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单张配图缩略图（带右上角微小删除按键）。
 */
@Composable
private fun PhotoThumbnail(
    path: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF2F2F2)),
    ) {
        val fileUri = if (path.startsWith("content://") || path.startsWith("file://")) {
            path
        } else {
            "file://${File(path).absolutePath}"
        }

        AsyncImage(
            uri = fileUri,
            contentDescription = "已选配图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 右上角半透明圆形删除按键
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clickable(onClick = onDelete)
                .testTag("remove_photo_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "移除照片",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * 微信经典的“+”号添加相片占位方块。
 */
@Composable
private fun AddPhotoPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF7F7F7),
        modifier =
        modifier
            .clickable(onClick = onClick)
            .testTag("add_photo_button"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加照片",
                tint = Color(0xFF999999),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/**
 * 微信朋友圈发布选项条目组件（所在位置、提醒谁看、谁可以看）。
 */
@Composable
private fun PublishOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    isHighlight: Boolean = false,
    testTag: String = "",
    modifier: Modifier = Modifier,
) {
    val iconTint by animateColorAsState(
        targetValue = if (isHighlight) Color(0xFF07C160) else Color(0xFF191919),
        animationSpec = tween(durationMillis = 250),
        label = "option_icon_tint",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isHighlight) Color(0xFF07C160) else Color(0xFF888888),
        animationSpec = tween(durationMillis = 250),
        label = "option_subtitle_color",
    )

    Card(
        onClick = onClick,
        shape = RectangleShape,
        colors =
        CardDefaults.cardColors(
            containerColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier =
        modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 15.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color(0xFF191919),
                modifier = Modifier.weight(1f),
            )
            this@Row.AnimatedVisibility(
                visible = !subtitle.isNullOrBlank(),
                enter = fadeIn(tween(250)) + expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(250)),
                exit = fadeOut(tween(200)) + shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(200)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle.orEmpty(),
                        fontSize = 15.sp,
                        color = subtitleColor,
                        fontWeight = if (isHighlight) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入选项",
                tint = Color(0xFFB2B2B2),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
