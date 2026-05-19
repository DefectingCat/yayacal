package plus.rua.project

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getGifUri(gifFile: String): String = "file:///android_asset/gifs/$gifFile"