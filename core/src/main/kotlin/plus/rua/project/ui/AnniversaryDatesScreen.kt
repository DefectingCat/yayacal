package plus.rua.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import plus.rua.project.AnniversaryDates
import plus.rua.project.DateDetailInfo
import plus.rua.project.MilestoneProgressInfo
import plus.rua.project.UpcomingAnniversaryInfo
import plus.rua.project.daysTogether
import plus.rua.project.getAllMilestoneProgress
import plus.rua.project.getDateDetailInfo
import plus.rua.project.getUpcomingLunarAnniversaryInfo
import plus.rua.project.getUpcomingSolarAnniversaryInfo
import plus.rua.project.nextMilestone
import kotlin.time.Clock

/**
 * 纪念日期页面：只读汇总展示所有固定纪念日期与岁时档案。
 *
 * 采用 Material 3 Expressive 规范重塑：
 * 1. 【初遇相守 · 在一起】：起始公历、星期、干支农历、星座节气、相伴天数与周年预告
 * 2. 【专属生辰 · 岁岁年年】：鸭鸭生日（阳历固定）与小狗生日（农历岁岁换算）卡片
 * 3. 【浪漫岁时 · 爱的约定】：玫瑰日与七夕节的浪漫约定
 * 4. 【里程碑印记 · 岁月见证】：100天/365天/520天/1000天/1314天进度全览
 * 5. 【时光慢语】：岁月寄语底卡
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
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val togetherDetail = remember(today) { getDateDetailInfo(AnniversaryDates.TOGETHER) }
    val togetherDays = remember(today) { daysTogether(today) }
    val nextMilestoneInfo = remember(today) { nextMilestone(today) }
    val duckUpcoming =
        remember(today) {
            getUpcomingSolarAnniversaryInfo(
                today = today,
                month = AnniversaryDates.DUCK_BIRTHDAY_MONTH,
                day = AnniversaryDates.DUCK_BIRTHDAY_DAY,
            )
        }
    val dogUpcoming =
        remember(today) {
            getUpcomingLunarAnniversaryInfo(
                today = today,
                lunarMonth = AnniversaryDates.DOG_LUNAR_MONTH,
                lunarDay = AnniversaryDates.DOG_LUNAR_DAY,
            )
        }
    val roseUpcoming =
        remember(today) {
            getUpcomingSolarAnniversaryInfo(
                today = today,
                month = AnniversaryDates.ROSE_DAY_MONTH,
                day = AnniversaryDates.ROSE_DAY_DAY,
            )
        }
    val qixiUpcoming =
        remember(today) {
            getUpcomingLunarAnniversaryInfo(
                today = today,
                lunarMonth = AnniversaryDates.QIXI_LUNAR_MONTH,
                lunarDay = AnniversaryDates.QIXI_LUNAR_DAY,
            )
        }
    val milestones = remember(today) { getAllMilestoneProgress(today) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. 在一起起始主卡
            TogetherHeroCard(
                togetherDate = AnniversaryDates.TOGETHER.toString(),
                detailInfo = togetherDetail,
                daysCount = togetherDays,
                nextMilestoneLabel = nextMilestoneInfo.label,
                nextMilestoneDaysLeft = nextMilestoneInfo.daysLeft,
                modifier = Modifier.testTag("anniversary_date_together").entrance(0),
            )

            // 2. 专属生辰
            SectionHeader(
                title = "专属生辰",
                subtitle = "重要的日子，岁岁相伴",
                icon = Icons.Outlined.Celebration,
                modifier = Modifier.entrance(1),
            )

            DuckBirthdayCard(
                upcoming = duckUpcoming,
                modifier = Modifier.testTag("anniversary_date_duck").entrance(1),
            )

            DogBirthdayCard(
                upcoming = dogUpcoming,
                modifier = Modifier.testTag("anniversary_date_dog").entrance(2),
            )

            // 3. 浪漫岁时
            SectionHeader(
                title = "浪漫岁时",
                subtitle = "四季流转里的每一次心动",
                icon = Icons.Outlined.Favorite,
                modifier = Modifier.entrance(3),
            )

            RomanticDatesCard(
                roseUpcoming = roseUpcoming,
                qixiUpcoming = qixiUpcoming,
                modifier = Modifier.entrance(3),
            )

            // 4. 里程碑印记
            SectionHeader(
                title = "里程碑印记",
                subtitle = "每一个阶段，都是爱的见证",
                icon = Icons.Outlined.Stars,
                modifier = Modifier.entrance(4),
            )

            MilestonesProgressCard(
                milestones = milestones,
                modifier = Modifier.entrance(4),
            )

            // 5. 岁月慢语底卡
            RomanticFooterCard(
                modifier = Modifier.entrance(5),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 分区标题栏。
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 「在一起」起始日大号 Hero 卡片：
 * 包含初遇公历、星期、干支农历、星座节气、陪伴天数与动画。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TogetherHeroCard(
    togetherDate: String,
    detailInfo: DateDetailInfo,
    daysCount: Int,
    nextMilestoneLabel: String,
    nextMilestoneDaysLeft: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部标签与已相伴天数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "初见相恋",
                            style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "已相伴 $daysCount 天",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 中部大号日期展示与鸭子动画
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "在一起起始日",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                    Text(
                        text = togetherDate,
                        style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                AnimatedWebp(
                    contentDescription = null,
                    seed = togetherDate,
                    modifier = Modifier.size(86.dp),
                )
            }

            // 属性徽章标签池
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeroAttributeChip(text = detailInfo.weekdayText)
                HeroAttributeChip(text = detailInfo.lunarGanzhiText)
                HeroAttributeChip(text = "${detailInfo.constellationText} · ${detailInfo.solarTermText}")
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                thickness = 1.dp,
            )

            // 底部里程碑指引与寄语
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HourglassBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "距 $nextMilestoneLabel 还有 $nextMilestoneDaysLeft 天",
                        style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }

                Text(
                    text = "始于初见，陷于星光 ✨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/**
 * 主卡内属性小胶囊。
 */
@Composable
private fun HeroAttributeChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style =
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * 鸭鸭生日卡片（阳历固定：每年 9 月 4 日）。
 */
@Composable
private fun DuckBirthdayCard(
    upcoming: UpcomingAnniversaryInfo,
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 头部：图标 + 标题 + 阳历固定徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Cake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "鸭鸭生日",
                            style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "阳历固定 · 处女座 ♍ · 白露",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "9 月 4 日",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 大号核心日期
            Text(
                text = "阳历 9 月 4 日",
                style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 下一次生日预告框
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "下一次：${upcoming.targetSolarFormatted}",
                            style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "对应 ${upcoming.targetLunarFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                        if (upcoming.isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        },
                    ) {
                        Text(
                            text = if (upcoming.isToday) "🎉 今天生日！" else "还有 ${upcoming.daysLeft} 天",
                            style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color =
                            if (upcoming.isToday) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Text(
                text = "✨ 闪闪发光的鸭鸭 · 愿所有美好与温柔如期而至",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 小狗生日卡片（农历换算：每年农历正月廿一）。
 */
@Composable
private fun DogBirthdayCard(
    upcoming: UpcomingAnniversaryInfo,
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 头部：图标 + 标题 + 农历换算徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Pets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "小狗生日",
                            style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "农历岁岁换算 · 双鱼座 ♓ · 雨水",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "正月廿一",
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            // 大号核心日期
            Text(
                text = AnniversaryDates.DOG_LUNAR_LABEL,
                style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 下一次公历生日换算预告框
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "下一次：${upcoming.targetSolarFormatted}",
                            style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "对应 ${upcoming.targetLunarFormatted}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color =
                        if (upcoming.isToday) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        },
                    ) {
                        Text(
                            text = if (upcoming.isToday) "🐾 今天生日！" else "还有 ${upcoming.daysLeft} 天",
                            style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color =
                            if (upcoming.isToday) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Text(
                text = "🐾 活泼开朗的小狗 · 岁岁常欢愉，万事皆顺意",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * 浪漫岁时卡片：包含玫瑰之约与七夕佳节。
 */
@Composable
private fun RomanticDatesCard(
    roseUpcoming: UpcomingAnniversaryInfo,
    qixiUpcoming: UpcomingAnniversaryInfo,
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 玫瑰日项
            val roseDateStr =
                if (roseUpcoming.isToday) {
                    "🎉 今天是玫瑰日！"
                } else {
                    "下一次：${roseUpcoming.targetSolarDate.year} 年 ${roseUpcoming.targetSolarDate.month.number} 月 ${roseUpcoming.targetSolarDate.day} 日 · 还有 ${roseUpcoming.daysLeft} 天"
                }
            RomanticItemRow(
                title = "玫瑰之约",
                dateLabel = "每年 10 月 16 日",
                tagline = "天秤座 ♎ · 送你一整束明媚玫瑰 🌹",
                nextOccurrenceText = roseDateStr,
                icon = Icons.Outlined.Favorite,
                iconTint = Color(0xFFE5576B),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.8.dp,
            )

            // 七夕节项
            val qixiDateStr =
                if (qixiUpcoming.isToday) {
                    "🌌 今日七夕，佳期如梦！"
                } else {
                    "下一次：${qixiUpcoming.targetSolarDate.year} 年 ${qixiUpcoming.targetSolarDate.month.number} 月 ${qixiUpcoming.targetSolarDate.day} 日 · 还有 ${qixiUpcoming.daysLeft} 天"
                }
            RomanticItemRow(
                title = "七夕佳节",
                dateLabel = "农历七月初七",
                tagline = "乞巧佳节 · 银汉迢迢，所爱惟你 🌌",
                nextOccurrenceText = qixiDateStr,
                icon = Icons.Outlined.AutoAwesome,
                iconTint = Color(0xFF7C5CEB),
            )
        }
    }
}

/**
 * 浪漫岁时单行展示。
 */
@Composable
private fun RomanticItemRow(
    title: String,
    dateLabel: String,
    tagline: String,
    nextOccurrenceText: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = iconTint.copy(alpha = 0.16f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dateLabel,
                    style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Text(
                text = tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = nextOccurrenceText,
                style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 里程碑进度卡片：横向可滑动卡片列表展示各个重要阶段天数。
 */
@Composable
private fun MilestonesProgressCard(
    milestones: List<MilestoneProgressInfo>,
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
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                milestones.forEach { milestone ->
                    MilestonePillCard(milestone = milestone)
                }
            }
        }
    }
}

/**
 * 单个里程碑状态胶囊卡片。
 */
@Composable
private fun MilestonePillCard(
    milestone: MilestoneProgressInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color =
        if (milestone.isPassed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else if (milestone.isToday) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
        },
        border =
        if (!milestone.isPassed && !milestone.isToday) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        } else {
            null
        },
        modifier = modifier.width(142.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = milestone.label,
                    style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color =
                    if (milestone.isToday) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )

                if (milestone.isPassed) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已达成",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = milestone.tagline,
                style = MaterialTheme.typography.bodySmall,
                color =
                if (milestone.isToday) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            val formattedTarget =
                "${milestone.targetDate.year}.${milestone.targetDate.month.number.toString().padStart(2, '0')}.${milestone.targetDate.day.toString().padStart(2, '0')}"
            Text(
                text =
                if (milestone.isPassed) {
                    "已于 $formattedTarget 达成"
                } else if (milestone.isToday) {
                    "🎉 今日达成！"
                } else {
                    "$formattedTarget\n还有 ${milestone.daysLeft} 天"
                },
                style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color =
                if (milestone.isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (milestone.isPassed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                },
            )
        }
    }
}

/**
 * 岁月寄语与底部温暖收尾卡片。
 */
@Composable
private fun RomanticFooterCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "日子慢慢过，我们慢慢爱",
                    style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }

            Text(
                text = "YaYa Calendar · 记录所有温暖与爱 ✨",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
