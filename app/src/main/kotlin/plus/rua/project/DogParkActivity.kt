package plus.rua.project

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import plus.rua.project.ui.DogParkScreen
import plus.rua.project.ui.theme.YaYaTheme

private const val TAG = "DogParkActivity"

class DogParkActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.fade_in,
                R.anim.fade_out
            )
        }

        setContent {
            YaYaTheme {
                DogParkScreen(
                    onPlaybackError = {
                        Log.e(TAG, "Playback failed, finishing easter egg")
                        finishWithSlideBack()
                    }
                )
            }
        }
    }
}
