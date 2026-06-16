package plus.rua.project

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.AnimRes

/**
 * 提供 edge-to-edge 和 slide 转场动画的 Activity 基类。
 */
abstract class BaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
    }

    /**
     * 带 slide 返回动画的 finish。
     */
    protected fun finishWithSlideBack(
        @AnimRes enterAnim: Int = R.anim.slide_in_left,
        @AnimRes exitAnim: Int = R.anim.slide_out_right
    ) {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    /**
     * 带 slide 进入动画的 startActivity。
     */
    protected fun startActivityWithSlide(
        intent: android.content.Intent,
        @AnimRes enterAnim: Int = R.anim.slide_in_right,
        @AnimRes exitAnim: Int = R.anim.slide_out_left
    ) {
        startActivity(intent)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    /**
     * 带 fade 进入动画的 startActivity。
     */
    protected fun startActivityWithFade(
        intent: android.content.Intent,
        @AnimRes enterAnim: Int = R.anim.fade_in,
        @AnimRes exitAnim: Int = R.anim.fade_out
    ) {
        startActivity(intent)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION") // API 34+ 使用 overrideActivityTransition，低版本仍用 overridePendingTransition 实现 fade 转场
            overridePendingTransition(enterAnim, exitAnim)
        }
    }
}
