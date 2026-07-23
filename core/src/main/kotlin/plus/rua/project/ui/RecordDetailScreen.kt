package plus.rua.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.DateRecordDetailUiState
import plus.rua.project.DateRecordDetailViewModel
import plus.rua.project.DateRecorderRepository

/**
 * 记录详情页面，展示单条记录的大图与全部信息，并提供编辑/删除入口。
 *
 * @param onBack 返回回调
 * @param recordId 记录 ID
 * @param onEditInfo 编辑记录信息回调（跳转记录编辑页）
 * @param onEditPhoto 编辑照片回调（跳转照片编辑页，携带当前照片路径）
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onBack: () -> Unit,
    recordId: Long,
    onEditInfo: (Long) -> Unit,
    onEditPhoto: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: DateRecordDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DateRecordDetailViewModel(
                    DateRecorderRepository.fromContext(context),
                    recordId
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLightbox by remember { mutableStateOf(false) }

    // 删除完成后自动返回
    if (state.deleted) {
        LaunchedEffect(Unit) { onBack() }
    }

    val record = state.record

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "记录详情",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (record != null) {
                DetailBottomBar(
                    onEditInfo = { onEditInfo(record.id) },
                    onEditPhoto = { viewModel.currentPhotoPath()?.let(onEditPhoto) },
                    onDelete = { showDeleteDialog = true }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            record == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("记录不存在", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> DetailContent(
                state = state,
                onPhotoClick = { showLightbox = true },
                onEditInfo = { onEditInfo(record.id) },
                onEditPhoto = { viewModel.currentPhotoPath()?.let(onEditPhoto) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("确定删除「${state.record?.title}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showLightbox) {
        ImageLightbox(
            photoUri = state.photoUri,
            contentDescription = state.record?.title,
            onDismiss = { showLightbox = false }
        )
    }
}

/**
 * 详情页核心内容布局，包含 Hero 大图、标题 header、元信息网格与笔记卡片。
 *
 * @param state UI 状态
 * @param onPhotoClick 点击图片回调（打开灯箱）
 * @param onEditInfo 编辑信息回调
 * @param onEditPhoto 编辑照片回调
 * @param modifier 布局修饰符
 */
@Composable
private fun DetailContent(
    state: DateRecordDetailUiState,
    onPhotoClick: () -> Unit,
    onEditInfo: () -> Unit,
    onEditPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val record = state.record ?: return
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeroPhotoCard(
            photoUri = state.photoUri,
            title = record.title,
            onPhotoClick = onPhotoClick,
            onEditPhotoClick = onEditPhoto
        )

        TitleHeaderSection(title = record.title)

        MetadataSection(
            shootDate = record.shootDate,
            linkedDate = record.linkedDate
        )

        JournalNoteSection(
            note = record.note,
            onEditNoteClick = onEditInfo
        )

        Spacer(modifier = Modifier.padding(bottom = 12.dp))
    }
}

/**
 * Hero 大图卡片，提供大图容器、全屏放大提示与直接进入涂鸦编辑的快捷入口。
 *
 * @param photoUri 照片 URI
 * @param title 标题说明
 * @param onPhotoClick 点击照片回调
 * @param onEditPhotoClick 点击涂鸦编辑回调
 * @param modifier 布局修饰符
 */
@Composable
private fun HeroPhotoCard(
    photoUri: String?,
    title: String,
    onPhotoClick: () -> Unit,
    onEditPhotoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onPhotoClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 380.dp)
        ) {
            AsyncImage(
                uri = photoUri,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
            )

            // 右上角全屏提示胶囊
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ZoomIn,
                        contentDescription = "全屏预览",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "全屏查看",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // 右下角涂鸦编辑快捷 Badge
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp),
                onClick = onEditPhotoClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Brush,
                        contentDescription = "涂鸦编辑",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "涂鸦编辑",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 标题头部区域，展示分类 Badge 与大字号标题。
 *
 * @param title 记录标题
 * @param modifier 布局修饰符
 */
@Composable
private fun TitleHeaderSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "日期记忆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 元信息卡片组，双列卡片展示拍摄日期与关联日历信息。
 *
 * @param shootDate 拍摄日期
 * @param linkedDate 关联日历日期
 * @param modifier 布局修饰符
 */
@Composable
private fun MetadataSection(
    shootDate: LocalDate,
    linkedDate: LocalDate?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetadataCard(
            icon = Icons.Outlined.CalendarToday,
            iconTint = MaterialTheme.colorScheme.primary,
            label = "拍摄日期",
            primaryValue = formatChineseDate(shootDate),
            secondaryValue = getWeekdayName(shootDate),
            secondaryColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        val hasLinked = linkedDate != null
        MetadataCard(
            icon = if (hasLinked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            iconTint = if (hasLinked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "关联日历",
            primaryValue = if (hasLinked) formatChineseDate(linkedDate) else "未关联",
            secondaryValue = if (hasLinked) getWeekdayName(linkedDate) else "独立记录",
            secondaryColor = if (hasLinked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 单条元信息卡片。
 *
 * @param icon 图标
 * @param iconTint 图标颜色
 * @param label 标签名称
 * @param primaryValue 主显示文本
 * @param secondaryValue 副文本/星期
 * @param secondaryColor 副文本颜色
 * @param modifier 布局修饰符
 */
@Composable
private fun MetadataCard(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    primaryValue: String,
    secondaryValue: String,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = primaryValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = secondaryValue,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor
                )
            }
        }
    }
}

/**
 * 笔记记录正文卡片。
 *
 * @param note 笔记内容
 * @param onEditNoteClick 点击卡片/编辑图标回调
 * @param modifier 布局修饰符
 */
@Composable
private fun JournalNoteSection(
    note: String,
    onEditNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onEditNoteClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "记录笔记",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "编辑笔记",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (note.isNotBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "暂无笔记内容，点击可编辑添加...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * 详情页底部固定操作栏。
 *
 * @param onEditInfo 点击编辑信息回调
 * @param onEditPhoto 点击涂鸦编辑回调
 * @param onDelete 点击删除回调
 * @param modifier 布局修饰符
 */
@Composable
private fun DetailBottomBar(
    onEditInfo: () -> Unit,
    onEditPhoto: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onEditInfo,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("编辑信息")
            }

            OutlinedButton(
                onClick = onEditPhoto,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Brush,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("涂鸦编辑")
            }

            OutlinedIconButton(
                onClick = onDelete,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除记录"
                )
            }
        }
    }
}

private fun formatChineseDate(date: LocalDate): String {
    return "${date.year}年${date.month.number}月${date.day}日"
}

private fun getWeekdayName(date: LocalDate): String {
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }
}
