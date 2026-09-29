package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import plus.rua.project.MomentLocationItem

/**
 * 微信风格“所在位置”选择全屏组件，支持自动定位列表、实时搜索和自定义位置。
 *
 * @param locations 可选位置列表
 * @param selectedLocation 当前选中的位置（为 null 表示不显示位置）
 * @param searchQuery 搜索框当前输入内容
 * @param onSearchQueryChange 搜索框输入变化时触发
 * @param onSelectLocation 确认选择位置时触发
 * @param onCancel 点击左上角“取消”时触发
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsLocationScreen(
    locations: List<MomentLocationItem>,
    selectedLocation: MomentLocationItem?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectLocation: (MomentLocationItem?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var temporarySelected by remember(selectedLocation) {
        mutableStateOf(selectedLocation)
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("moments_location_screen"),
    ) {
        // 1. 顶部操作栏：取消 | 所在位置 | 完成
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
                modifier = Modifier.testTag("location_cancel_button"),
            ) {
                Text(
                    text = "取消",
                    color = Color(0xFF191919),
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "所在位置",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF191919),
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onSelectLocation(temporarySelected) },
                shape = RoundedCornerShape(4.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF07C160),
                    contentColor = Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("location_done_button"),
            ) {
                Text(
                    text = "完成",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // 2. 搜索框：搜寻附近位置
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFF5F5F5),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(40.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "搜寻附近位置",
                            color = Color(0xFF999999),
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle =
                        TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xFF191919),
                        ),
                        cursorBrush = SolidColor(Color(0xFF07C160)),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("location_search_input"),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "清空",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // 3. 位置列表（不显示位置、城市、附近 POI）
        LazyColumn(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(locations, key = { it.name + (it.address ?: "") }) { item ->
                val isSelected =
                    if (item.isNone) {
                        temporarySelected == null
                    } else {
                        temporarySelected?.name == item.name
                    }

                LocationListItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        temporarySelected = if (item.isNone) null else item
                    },
                )
                HorizontalDivider(
                    color = Color(0xFFF0F0F0),
                    thickness = 0.6.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * 单个位置列表项组件。
 */
@Composable
private fun LocationListItem(
    item: MomentLocationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("location_item_${item.name}"),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = if (item.isNone) FontWeight.Normal else FontWeight.Medium,
                color = if (isSelected) Color(0xFF07C160) else Color(0xFF191919),
            )
            if (!item.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.address,
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    maxLines = 1,
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选择",
                tint = Color(0xFF07C160),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
