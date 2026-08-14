package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import plus.rua.project.AnniversaryDates

/**
 * 纪念日期页面：只读汇总展示所有固定纪念日期，不提供任何编辑入口。
 *
 * 数据固定来自 [AnniversaryDates]：
 * - 在一起：起始日，ISO 格式（如 2025-11-04）
 * - 鸭鸭生日：阳历 9 月 4 日
 * - 小狗生日：农历正月廿一
 *
 * @param onBack 返回回调
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryDatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "纪念日期",
                        style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "返回",
                        )
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateRow(
                title = "在一起",
                date = AnniversaryDates.TOGETHER.toString(),
                modifier = Modifier.testTag("anniversary_date_together"),
            )
            DateRow(
                title = "鸭鸭生日",
                date = "阳历 ${AnniversaryDates.DUCK_BIRTHDAY_MONTH} 月 ${AnniversaryDates.DUCK_BIRTHDAY_DAY} 日",
                modifier = Modifier.testTag("anniversary_date_duck"),
            )
            DateRow(
                title = "小狗生日",
                date = AnniversaryDates.DOG_LUNAR_LABEL,
                modifier = Modifier.testTag("anniversary_date_dog"),
            )
        }
    }
}

/**
 * 单行纪念日期卡片：左侧名称，右侧日期，只读不可点击。
 *
 * @param title 纪念日名称（如「在一起」）
 * @param date 日期文案（如「2025-11-04」「阳历 9 月 4 日」）
 * @param modifier 布局修饰符
 */
@Composable
private fun DateRow(
    title: String,
    date: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = date,
                style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
