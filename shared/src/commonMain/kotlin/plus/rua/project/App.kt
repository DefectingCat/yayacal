package plus.rua.project

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import plus.rua.project.ui.CalendarMonthView

/**
 * 应用入口 Composable，根据系统主题切换明暗 ColorScheme 并包裹 CalendarMonthView。
 */
@Composable
@Preview(name = "Calendar App")
fun App() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        CalendarMonthView(modifier = Modifier)
    }
}
