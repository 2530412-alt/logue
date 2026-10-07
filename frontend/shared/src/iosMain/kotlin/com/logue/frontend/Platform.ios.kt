package com.logue.frontend

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

// Simulador de iOS: comparte la red de la Mac
actual val BASE_URL: String = "http://localhost:8081"
