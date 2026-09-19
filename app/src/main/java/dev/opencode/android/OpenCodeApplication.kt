package dev.opencode.android

import android.app.Application
import dev.opencode.android.data.OpenCodeRepository

class OpenCodeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenCodeRepository.init(this).reconnect()
    }
}