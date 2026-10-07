package com.logue.frontend

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform