package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VisibilityGreen = Color(0xFF07C160)

private data class VisibilityPalette(
    val background: Color,
    val optionBackground: Color,
    val text: Color,
    val secondaryText: Color,
    val divider: Color,
    val radioOutline: Color,
)

private val LightVisibilityPalette = VisibilityPalette(
    background = Color(0xFFEDEDED),
    optionBackground = Color.White,
    text = Color(0xFF181818),
    secondaryText = Color(0xFF737373),
    divider = Color(0xFFEDEDED),
    radioOutline = Color(0xFFC6C6C6),
)

private val DarkVisibilityPalette = VisibilityPalette(
    background = Color(0xFF111111),
    optionBackground = Color(0xFF1F1F1F),
    text = Color(0xFFEEEEEE),
    secondaryText = Color(0xFFAAAAAA),
    divider = Color(0xFF333333),
    radioOutline = Color(0xFF777777),
)

/**
 * 微信式双账号可见范围选择页：公开给两个账号，私密仅发布账号可见。
 *
 * 选项仅在本页暂存，配置变化后保留；点击完成才回传，取消时由调用方关闭页面。
 *
 * @param currentVisibility 已保存的公开/私密选项
 * @param onDone 点击完成时回传所选范围；不使用联系人标签，第二个参数始终为空
 * @param onCancel 点击取消时触发，不保存本页的选择
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsVisibilityScreen(
    currentVisibility: String,
    onDone: (String, List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibility by rememberSaveable(currentVisibility) { mutableStateOf(currentVisibility) }
    val palette = if (isSystemInDarkTheme()) DarkVisibilityPalette else LightVisibilityPalette

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("moments_visibility_screen"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
        ) {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.text),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(min = 64.dp)
                    .testTag("visibility_cancel_button"),
            ) {
                Text("取消", fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1)
            }
            Text(
                text = "谁可以看",
                color = palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
            )
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.align(Alignment.CenterEnd).heightIn(min = 48.dp),
            ) {
                Button(
                    onClick = { onDone(visibility, emptyList()) },
                    shape = RoundedCornerShape(5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisibilityGreen,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .widthIn(min = 64.dp)
                        .testTag("visibility_done_button"),
                ) {
                    Text("完成", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            VisibilityOption(
                label = "公开",
                description = "小白和小鸡毛都能看到",
                isSelected = visibility == "公开",
                palette = palette,
                onClick = { visibility = "公开" },
                modifier = Modifier.testTag("visibility_option_public"),
            )
            HorizontalDivider(
                color = palette.divider,
                thickness = 0.5.dp,
                modifier = Modifier.background(palette.optionBackground).padding(start = 60.dp),
            )
            VisibilityOption(
                label = "私密",
                description = "仅当前发布账号可见",
                isSelected = visibility == "私密",
                palette = palette,
                onClick = { visibility = "私密" },
                modifier = Modifier.testTag("visibility_option_private"),
            )
        }
    }
}

/** 整行可点击的单选项，选择状态统一由行语义提供给无障碍服务。 */
@Composable
private fun VisibilityOption(
    label: String,
    description: String,
    isSelected: Boolean,
    palette: VisibilityPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = palette.optionBackground,
            contentColor = palette.text,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            role = Role.RadioButton
            selected = isSelected
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .background(if (isSelected) VisibilityGreen else Color.Transparent, CircleShape)
                    .border(1.5.dp, if (isSelected) VisibilityGreen else palette.radioOutline, CircleShape),
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(label, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
                Text(
                    text = description,
                    color = palette.secondaryText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
