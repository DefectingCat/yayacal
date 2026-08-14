package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import plus.rua.project.AnniversaryDates
import plus.rua.project.daysTogether
import plus.rua.project.nextLunarAnniversary
import plus.rua.project.nextMilestone
import plus.rua.project.nextSolarAnniversary
import plus.rua.project.shared.R
import plus.rua.project.toCountdown
import kotlin.time.Clock

/**
 * 纪念日页面：只读展示「在一起」天数与两个生日的倒数。
 *
 * 页面不提供任何编辑入口，数据固定来自 [plus.rua.project.AnniversaryDates]：
 * - 在一起：经过天数（差值口径）+ 下一个里程碑
 * - 鸭鸭生日：阳历 9 月 4 日，滚动到下一次
 * - 小狗生日：农历正月廿一，每年用 tyme 换算后滚动到下一次
 *
 * 具体日期不在此页展示，统一由纪念日期内页汇总（TopAppBar 右侧日历图标进入）。
 *
 * @param onBack 返回回调
 * @param onNavigateToDates 点击右上角日历图标时触发，进入纪念日期内页
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryScreen(
    onBack: () -> Unit,
    onNavigateToDates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val togetherDays = remember(today) { daysTogether(today) }
    val milestone = remember(today) { nextMilestone(today) }
    val duckBirthday = remember(today) {
        toCountdown(nextSolarAnniversary(today, AnniversaryDates.DUCK_BIRTHDAY_MONTH, AnniversaryDates.DUCK_BIRTHDAY_DAY), today)
    }
    val dogBirthday = remember(today) {
        toCountdown(nextLunarAnniversary(today, AnniversaryDates.DOG_LUNAR_MONTH, AnniversaryDates.DOG_LUNAR_DAY), today)
    }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "纪念日",
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
                actions = {
                    IconButton(onClick = onNavigateToDates) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "纪念日期",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TogetherCard(
                days = togetherDays,
                milestoneLabel = milestone.label,
                milestoneDaysLeft = milestone.daysLeft,
                milestoneIsToday = milestone.isToday,
                modifier = Modifier.testTag("anniversary_together"),
            )

            BirthdayCard(
                title = "鸭鸭生日",
                dateLabel = "阳历 ${AnniversaryDates.DUCK_BIRTHDAY_MONTH} 月 ${AnniversaryDates.DUCK_BIRTHDAY_DAY} 日",
                daysLeft = duckBirthday.daysLeft,
                isToday = duckBirthday.isToday,
                icon = Icons.Outlined.Cake,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("anniversary_duck"),
            )

            BirthdayCard(
                title = "小狗生日",
                dateLabel = AnniversaryDates.DOG_LUNAR_LABEL,
                daysLeft = dogBirthday.daysLeft,
                isToday = dogBirthday.isToday,
                icon = Icons.Outlined.Pets,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("anniversary_dog"),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 「在一起」主卡：primaryContainer 强调底色（M3 Reply 官方模式），
 * 大数字展示经过天数，右侧配随机鸭子动画。
 *
 * @param days 在一起经过天数
 * @param milestoneLabel 下一个里程碑名（如「365 天」）
 * @param milestoneDaysLeft 距下一个里程碑天数
 * @param milestoneIsToday 里程碑是否就是今天
 * @param modifier 布局修饰符
 */
@Composable
private fun TogetherCard(
    days: Int,
    milestoneLabel: String,
    milestoneDaysLeft: Int,
    milestoneIsToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "在一起",
                    style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "$days",
                        style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "天",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                }

                MilestonePill(
                    label = milestoneLabel,
                    daysLeft = milestoneDaysLeft,
                    isToday = milestoneIsToday,
                )
            }

            AnimatedWebp(
                contentDescription = null,
                seed = days,
                modifier = Modifier.size(104.dp),
            )
        }
    }
}

/**
 * 里程碑胶囊：日常态显示「距 365 天还有 81 天」，命中当天切换庆祝文案。
 *
 * @param label 里程碑名
 * @param daysLeft 剩余天数
 * @param isToday 是否就是今天
 * @param modifier 布局修饰符
 */
@Composable
private fun MilestonePill(
    label: String,
    daysLeft: Int,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
        modifier = modifier,
    ) {
        Text(
            text =
            if (isToday) {
                "今天是第 $label 纪念日"
            } else {
                "距 $label 还有 $daysLeft 天"
            },
            style =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * 生日卡片：倒数天数 + 原始日期文案，当天命中显示皇冠庆祝态。
 *
 * @param title 卡片标题（如「鸭鸭生日」）
 * @param dateLabel 原始日期文案（如「阳历 9 月 4 日」「农历正月廿一」）
 * @param daysLeft 距今天数
 * @param isToday 生日是否就是今天
 * @param icon 图标
 * @param iconContainerColor 图标底色
 * @param iconColor 图标色
 * @param modifier 布局修饰符
 */
@Composable
private fun BirthdayCard(
    title: String,
    dateLabel: String,
    daysLeft: Int,
    isToday: Boolean,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = iconContainerColor,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isToday) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_birthday_crown),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        text = "今天生日",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "$daysLeft",
                            style =
                            MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "天",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    Text(
                        text = "还有",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
