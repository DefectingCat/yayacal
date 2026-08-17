package plus.rua.project.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import plus.rua.project.AnniversaryDates
import plus.rua.project.LoveTimeBreakdown
import plus.rua.project.MilestoneProgressInfo
import plus.rua.project.MilestoneProgressSummary
import plus.rua.project.UpcomingAnniversaryInfo
import plus.rua.project.daysTogether
import plus.rua.project.getAllMilestoneProgress
import plus.rua.project.getLoveTimeBreakdown
import plus.rua.project.getMilestoneProgressSummary
import plus.rua.project.getUpcomingLunarAnniversaryInfo
import plus.rua.project.getUpcomingSolarAnniversaryInfo
import plus.rua.project.shared.R
import kotlin.time.Clock

/**
 * 纪念日页面：采用 2026 Material 3 Expressive 与 Bento 布局重塑设计。
 *
 * 核心模块：
 * 1. 【相伴时光 · 主力 Hero】：大字相恋天数、年月分解、阶段里程碑平滑进度条、鸭鸭/小狗萌宠动画
 * 2. 【生辰欢喜 · 专属时刻】：鸭鸭生日（阳历）与小狗生日（农历）Bento 卡片，含下一次公历日期与倒数
 * 3. 【浪漫岁时 · 爱的约定】：玫瑰之约（10.16）与七夕佳节（农历七月初七）并列卡片
 * 4. 【里程碑印记 · 岁月见证】：100天/365天/520天/1000天/1314天状态跑道
 * 5. 【岁时档案 · 纪念日历入口】：通往干支农历与星象档案内页的入口横幅
 * 6. 【岁月慢语】：温暖寄语底卡
 *
 * @param onBack 点击顶部返回按钮时触发
 * @param onNavigateToDates 点击右上角日历图标或档案入口卡片时触发，进入纪念日期内页
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
    val loveBreakdown = remember(today) { getLoveTimeBreakdown(today) }
    val milestoneSummary = remember(today) { getMilestoneProgressSummary(today) }
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. 在一起相伴时光主卡 (Hero)
            TogetherHeroCard(
                days = togetherDays,
                loveBreakdown = loveBreakdown,
                milestoneSummary = milestoneSummary,
                modifier = Modifier.testTag("anniversary_together").entrance(0),
            )

            // 2. 生辰欢喜
            SectionHeader(
                title = "生辰欢喜",
                subtitle = "重要的生辰，岁岁相伴",
                icon = Icons.Outlined.Celebration,
                modifier = Modifier.entrance(1),
            )

            BirthdayBentoCard(
                title = "鸭鸭生日",
                dateLabel = "阳历 ${AnniversaryDates.DUCK_BIRTHDAY_MONTH} 月 ${AnniversaryDates.DUCK_BIRTHDAY_DAY} 日 · 处女座 ♍",
                tagline = "✨ 闪闪发光的鸭鸭 · 愿所有美好与温柔如期而至",
                upcoming = duckUpcoming,
                icon = Icons.Outlined.Cake,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("anniversary_duck").entrance(1),
            )

            BirthdayBentoCard(
                title = "小狗生日",
                dateLabel = "${AnniversaryDates.DOG_LUNAR_LABEL} · 双鱼座 ♓",
                tagline = "🐾 活泼可爱的小狗 · 岁岁常欢愉，万事皆顺意",
                upcoming = dogUpcoming,
                icon = Icons.Outlined.Pets,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                badgeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                badgeTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("anniversary_dog").entrance(2),
            )

            // 3. 浪漫岁时
            SectionHeader(
                title = "浪漫岁时",
                subtitle = "四季流转里的每一次心动",
                icon = Icons.Outlined.Favorite,
                modifier = Modifier.entrance(3),
            )

            RomanticDatesBentoRow(
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

            MilestonesCarouselCard(
                milestones = milestones,
                modifier = Modifier.entrance(4),
            )

            // 5. 岁时档案内页入口
            ArchiveBannerCard(
                onClick = onNavigateToDates,
                modifier = Modifier.entrance(5),
            )

            // 6. 岁月慢语收尾
            RomanticFooterCard(
                modifier = Modifier.entrance(5),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 分区标题栏。
 *
 * @param title 分区标题
 * @param subtitle 分区副标题
 * @param icon 前置图标
 * @param modifier 布局修饰符
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
 * 「在一起」相伴时光主卡 (Hero Card)：
 * 采用 Material 3 Expressive primaryContainer 强调底色，
 * 包含初遇徽章、大字相伴天数、年月分解、里程碑阶段平滑进度条与萌宠动画。
 *
 * @param days 在一起经过天数
 * @param loveBreakdown 恋爱时长年月详细分解
 * @param milestoneSummary 里程碑进度摘要
 * @param modifier 布局修饰符
 */
@Composable
private fun TogetherHeroCard(
    days: Int,
    loveBreakdown: LoveTimeBreakdown,
    milestoneSummary: MilestoneProgressSummary,
    modifier: Modifier = Modifier,
) {
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(milestoneSummary.progress) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = milestoneSummary.progress,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }

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
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 顶部标签与第 N 天
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
                            text = "初见相恋 · ${AnniversaryDates.TOGETHER.year}.${AnniversaryDates.TOGETHER.month.number.toString().padStart(2, '0')}.${AnniversaryDates.TOGETHER.day.toString().padStart(2, '0')}",
                            style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
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
                        text = "已相伴第 ${days + 1} 天",
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

            // 中部大号天数与萌宠动画
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "在一起",
                        style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    // 年月与统计小胶囊
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    ) {
                        Text(
                            text = "${loveBreakdown.formattedText} · ${loveBreakdown.totalWeeks} 周",
                            style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                    modifier = Modifier.size(108.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedWebp(
                            contentDescription = "相伴萌宠",
                            seed = days,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                thickness = 1.dp,
            )

            // 底部阶段里程碑进度条
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "下一站：${milestoneSummary.nextMilestoneLabel}",
                            style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Text(
                        text =
                        if (milestoneSummary.isToday) {
                            "🎉 今日达成！"
                        } else {
                            "还有 ${milestoneSummary.daysLeft} 天"
                        },
                        style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                // 进度条
                LinearProgressIndicator(
                    progress = { progressAnim.value },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                )

                // 进度起止节点标注
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = milestoneSummary.prevMilestoneLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )

                    val targetFormatted =
                        "${milestoneSummary.targetDate.year}.${milestoneSummary.targetDate.month.number.toString().padStart(2, '0')}.${milestoneSummary.targetDate.day.toString().padStart(2, '0')}"
                    Text(
                        text = "目标：$targetFormatted",
                        style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * 生日 Bento 卡片：包含图标、标题、星座信息、下一次换算日期、倒数大数字与温馨寄语。
 *
 * @param title 卡片标题（如「鸭鸭生日」）
 * @param dateLabel 原始日期文案（如「阳历 9 月 4 日 · 处女座 ♍」）
 * @param tagline 底部温馨寄语
 * @param upcoming 下一次生日信息
 * @param icon 图标
 * @param iconContainerColor 图标底色
 * @param iconColor 图标色
 * @param badgeContainerColor 徽章底色
 * @param badgeTextColor 徽章文字色
 * @param modifier 布局修饰符
 */
@Composable
private fun BirthdayBentoCard(
    title: String,
    dateLabel: String,
    tagline: String,
    upcoming: UpcomingAnniversaryInfo,
    icon: ImageVector,
    iconContainerColor: Color,
    iconColor: Color,
    badgeContainerColor: Color,
    badgeTextColor: Color,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                }

                // 倒数或生日态
                if (upcoming.isToday) {
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
                                text = "${upcoming.daysLeft}",
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

            // 下一次发生日期预告框
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "下一次：${upcoming.targetSolarFormatted}",
                        style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeContainerColor,
                    ) {
                        Text(
                            text = if (upcoming.isToday) "🎉 今日" else "${upcoming.daysLeft} 天后",
                            style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = badgeTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                text = tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 浪漫岁时 Bento 卡片行：玫瑰之约与七夕佳节并列卡片。
 *
 * @param roseUpcoming 玫瑰日预告
 * @param qixiUpcoming 七夕节预告
 * @param modifier 布局修饰符
 */
@Composable
private fun RomanticDatesBentoRow(
    roseUpcoming: UpcomingAnniversaryInfo,
    qixiUpcoming: UpcomingAnniversaryInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RomanticSingleBentoCard(
            title = "玫瑰之约",
            dateLabel = "10 月 16 日",
            tagline = "明媚玫瑰 🌹",
            daysLeft = roseUpcoming.daysLeft,
            isToday = roseUpcoming.isToday,
            icon = Icons.Outlined.Favorite,
            themeColor = Color(0xFFE5576B),
            modifier = Modifier.weight(1f),
        )

        RomanticSingleBentoCard(
            title = "七夕佳节",
            dateLabel = "农历七月初七",
            tagline = "银汉迢迢 🌌",
            daysLeft = qixiUpcoming.daysLeft,
            isToday = qixiUpcoming.isToday,
            icon = Icons.Outlined.AutoAwesome,
            themeColor = Color(0xFF7C5CEB),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 浪漫岁时单个 Bento 卡片。
 */
@Composable
private fun RomanticSingleBentoCard(
    title: String,
    dateLabel: String,
    tagline: String,
    daysLeft: Int,
    isToday: Boolean,
    icon: ImageVector,
    themeColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = themeColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                    if (isToday) {
                        themeColor
                    } else {
                        themeColor.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = if (isToday) "🎉 今日" else "还有 $daysLeft 天",
                        style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (isToday) Color.White else themeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 里程碑印记横向滑动跑道卡片。
 *
 * @param milestones 里程碑进度列表
 * @param modifier 布局修饰符
 */
@Composable
private fun MilestonesCarouselCard(
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
                    MilestoneCarouselItem(milestone = milestone)
                }
            }
        }
    }
}

/**
 * 单个里程碑小卡片。
 */
@Composable
private fun MilestoneCarouselItem(
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
 * 岁时档案导航入口卡片。
 *
 * @param onClick 点击跳转回调
 * @param modifier 布局修饰符
 */
@Composable
private fun ArchiveBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
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
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "岁时档案与纪念日历",
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "查看干支农历、星座节气、历年岁时换算与详细纪念册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "查看详情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 岁月寄语与底部温暖收尾卡片。
 *
 * @param modifier 布局修饰符
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
