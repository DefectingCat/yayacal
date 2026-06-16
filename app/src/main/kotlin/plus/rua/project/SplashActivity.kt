package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.rua.project.ui.SplashScreen
import plus.rua.project.ui.theme.YaYaTheme

private const val SPLASH_DELAY_MS = 400L

/**
 * 启动页 Activity。
 *
 * 显示品牌启动图 400ms 后跳转到 [MainActivity]，并 finish 自身。
 */
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                SplashScreen(
                    iconPainter = painterResource(R.mipmap.ic_launcher),
                )
            }
        }

        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        if (isFinishing) return
        startActivityWithSlide(Intent(this, MainActivity::class.java))
        finish()
    }
}
