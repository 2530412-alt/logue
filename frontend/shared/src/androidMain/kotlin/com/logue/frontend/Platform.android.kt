package com.logue.frontend

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// Emulador Android: 10.0.2.2 apunta a la computadora donde corre el backend
actual val BASE_URL: String = "http://10.0.2.2:8081"
