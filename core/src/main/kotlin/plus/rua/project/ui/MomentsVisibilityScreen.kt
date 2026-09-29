package plus.rua.project.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 微信朋友圈“谁可以看”可见性类型常量。
 */
object MomentsVisibilityConstants {
    const val PUBLIC = "公开"
    const val PRIVATE = "私密"
    const val PARTIAL = "部分可见"
    const val EXCLUDE = "不给谁看"

    val DEFAULT_TAGS_PARTIAL = listOf("家人", "朋友", "同事", "同学")
    val DEFAULT_TAGS_EXCLUDE = listOf("同事", "客户", "领导", "工作")
}

/**
 * 微信风格“谁可以看”权限设置全屏组件，完整复刻微信朋友圈可见性设置：
 * - 顶部居中标题、返回键与微信绿“完成”操作按钮；
 * - 公开（所有朋友可见）、私密（仅自己可见）、部分可见、不给谁看 4 档隐私权限；
 * - 微信同款单选按钮（选中为高亮绿外圈加绿色中心实心圆点，未选为浅灰圆环）；
 * - “部分可见”与“不给谁看”支持平滑展开常用标签勾选与通讯录/群聊选择入口。
 *
 * @param currentVisibility 当前选择的可见性类型（"公开" / "私密" / "部分可见" / "不给谁看"）
 * @param currentTags 当前已选取的标签/联系人列表
 * @param onDone 点击右上角“完成”按钮确认选择时触发，提供选中的可见性类型与标签列表
 * @param onCancel 点击左上角“<”返回按钮时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsVisibilityScreen(
    currentVisibility: String,
    currentTags: List<String> = emptyList(),
    onDone: (visibility: String, tags: List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedVisibility by remember(currentVisibility) {
        mutableStateOf(
            if (currentVisibility.startsWith(MomentsVisibilityConstants.PARTIAL)) {
                MomentsVisibilityConstants.PARTIAL
            } else if (currentVisibility.startsWith(MomentsVisibilityConstants.EXCLUDE)) {
                MomentsVisibilityConstants.EXCLUDE
            } else if (currentVisibility == MomentsVisibilityConstants.PRIVATE) {
                MomentsVisibilityConstants.PRIVATE
            } else {
                MomentsVisibilityConstants.PUBLIC
            },
        )
    }

    val selectedTags = remember(currentTags) {
        mutableStateListOf<String>().apply { addAll(currentTags) }
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(Color(0xFFEDEDED))
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics { testTagsAsResourceId = true }
            .testTag("moments_visibility_screen"),
    ) {
        // 1. 顶部操作栏：返回 | 谁可以看 (居中) | 完成
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(
                onClick = onCancel,
                modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .testTag("visibility_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = Color(0xFF191919),
                    modifier = Modifier.size(28.dp),
                )
            }

            Text(
                text = "谁可以看",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF191919),
                modifier = Modifier.align(Alignment.Center),
            )

            Button(
                onClick = {
                    val finalTags = if (selectedVisibility == MomentsVisibilityConstants.PARTIAL ||
                        selectedVisibility == MomentsVisibilityConstants.EXCLUDE
                    ) {
                        selectedTags.toList()
                    } else {
                        emptyList()
                    }
                    onDone(selectedVisibility, finalTags)
                },
                shape = RoundedCornerShape(4.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07C160),
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .height(32.dp)
                    .testTag("visibility_done_button"),
            ) {
                Text(
                    text = "完成",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 2. 权限选项区域（白色卡片容器）
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White),
            ) {
                // (1) 公开
                VisibilityOptionItem(
                    title = MomentsVisibilityConstants.PUBLIC,
                    subtitle = "所有朋友可见",
                    isSelected = selectedVisibility == MomentsVisibilityConstants.PUBLIC,
                    onSelect = {
                        selectedVisibility = MomentsVisibilityConstants.PUBLIC
                        selectedTags.clear()
                    },
                    testTag = "visibility_option_public",
                )

                HorizontalDivider(
                    color = Color(0xFFEFEFEF),
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(start = 52.dp),
                )

                // (2) 私密
                VisibilityOptionItem(
                    title = MomentsVisibilityConstants.PRIVATE,
                    subtitle = "仅自己可见",
                    isSelected = selectedVisibility == MomentsVisibilityConstants.PRIVATE,
                    onSelect = {
                        selectedVisibility = MomentsVisibilityConstants.PRIVATE
                        selectedTags.clear()
                    },
                    testTag = "visibility_option_private",
                )

                HorizontalDivider(
                    color = Color(0xFFEFEFEF),
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(start = 52.dp),
                )

                // (3) 部分可见
                VisibilityOptionItem(
                    title = MomentsVisibilityConstants.PARTIAL,
                    subtitle = if (selectedVisibility == MomentsVisibilityConstants.PARTIAL && selectedTags.isNotEmpty()) {
                        "已选：${selectedTags.joinToString("、")}"
                    } else {
                        null
                    },
                    isSelected = selectedVisibility == MomentsVisibilityConstants.PARTIAL,
                    onSelect = {
                        selectedVisibility = MomentsVisibilityConstants.PARTIAL
                    },
                    testTag = "visibility_option_partial",
                )

                // 部分可见展开子选项
                AnimatedVisibility(
                    visible = selectedVisibility == MomentsVisibilityConstants.PARTIAL,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    VisibilitySubSection(
                        availableTags = MomentsVisibilityConstants.DEFAULT_TAGS_PARTIAL,
                        selectedTags = selectedTags,
                        onTagToggle = { tag ->
                            if (selectedTags.contains(tag)) {
                                selectedTags.remove(tag)
                            } else {
                                selectedTags.add(tag)
                            }
                        },
                        onSelectFromContacts = {
                            Toast.makeText(context, "从通讯录选择功能正在开发中", Toast.LENGTH_SHORT).show()
                        },
                        onSelectFromGroup = {
                            Toast.makeText(context, "从群聊选择功能正在开发中", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                HorizontalDivider(
                    color = Color(0xFFEFEFEF),
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(start = 52.dp),
                )

                // (4) 不给谁看
                VisibilityOptionItem(
                    title = MomentsVisibilityConstants.EXCLUDE,
                    subtitle = if (selectedVisibility == MomentsVisibilityConstants.EXCLUDE && selectedTags.isNotEmpty()) {
                        "已选：${selectedTags.joinToString("、")}"
                    } else {
                        null
                    },
                    isSelected = selectedVisibility == MomentsVisibilityConstants.EXCLUDE,
                    onSelect = {
                        selectedVisibility = MomentsVisibilityConstants.EXCLUDE
                    },
                    testTag = "visibility_option_exclude",
                )

                // 不给谁看展开子选项
                AnimatedVisibility(
                    visible = selectedVisibility == MomentsVisibilityConstants.EXCLUDE,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    VisibilitySubSection(
                        availableTags = MomentsVisibilityConstants.DEFAULT_TAGS_EXCLUDE,
                        selectedTags = selectedTags,
                        onTagToggle = { tag ->
                            if (selectedTags.contains(tag)) {
                                selectedTags.remove(tag)
                            } else {
                                selectedTags.add(tag)
                            }
                        },
                        onSelectFromContacts = {
                            Toast.makeText(context, "从通讯录选择功能正在开发中", Toast.LENGTH_SHORT).show()
                        },
                        onSelectFromGroup = {
                            Toast.makeText(context, "从群聊选择功能正在开发中", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            HorizontalDivider(
                color = Color(0xFFE5E5E5),
                thickness = 0.6.dp,
            )
        }
    }
}

/**
 * 微信可见性单选项条目。
 *
 * @param title 主标题（如“公开”、“私密”）
 * @param subtitle 副标题（如“所有朋友可见”），为空时不显示
 * @param isSelected 是否处于被选中状态
 * @param onSelect 点击整行时触发
 * @param testTag UI 测试标签
 * @param modifier 布局修饰符
 */
@Composable
private fun VisibilityOptionItem(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeChatRadioButton(
            selected = isSelected,
            modifier = Modifier.padding(end = 16.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF191919),
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF888888),
                )
            }
        }
    }
}

/**
 * 微信“部分可见”或“不给谁看”选中时展开的快捷标签与通讯录选择面板。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisibilitySubSection(
    availableTags: List<String>,
    selectedTags: List<String>,
    onTagToggle: (String) -> Unit,
    onSelectFromContacts: () -> Unit,
    onSelectFromGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Text(
            text = "快速选择标签：",
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            availableTags.forEach { tag ->
                val isTagSelected = selectedTags.contains(tag)
                Box(
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isTagSelected) Color(0xFFE8F7ED) else Color(0xFFEDEDED),
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (isTagSelected) Color(0xFF07C160) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .clickable { onTagToggle(tag) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("visibility_tag_$tag"),
                ) {
                    Text(
                        text = tag,
                        fontSize = 13.sp,
                        color = if (isTagSelected) Color(0xFF07C160) else Color(0xFF333333),
                        fontWeight = if (isTagSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 从通讯录选择入口
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectFromContacts)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "从通讯录选择",
                fontSize = 14.sp,
                color = Color(0xFF576B95),
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFB2B2B2),
                modifier = Modifier.size(18.dp),
            )
        }

        // 从群聊选择入口
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectFromGroup)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "从群聊选择",
                fontSize = 14.sp,
                color = Color(0xFF576B95),
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFB2B2B2),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 微信风格单选圆圈组件：
 * 选中态：绿色外圈（1.5dp）+ 绿色实心内圆点（10dp）+ 留白间隙；
 * 未选中态：浅灰色细外圈（1.5dp）。
 *
 * @param selected 是否选中
 * @param modifier 布局修饰符
 */
@Composable
fun WeChatRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(
                width = 1.5.dp,
                color = if (selected) Color(0xFF07C160) else Color(0xFFC7C7CC),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07C160)),
            )
        }
    }
}
