package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import kotlinx.datetime.LocalDate

@Composable
fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.day.toString(),
            textAlign = TextAlign.Center,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
