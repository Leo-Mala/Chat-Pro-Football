package com.example

import android.app.Application
import android.database.CursorWindow
import com.example.data.EuropeanFactualAssetRuntime
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EuropeanFactualAssetRuntime.initialize(assets)
        fixCursorWindowSize()
    }

    companion object {
        fun fixCursorWindowSize() {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                try {
                    val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                    field.isAccessible = true
                    field.set(null, 100 * 1024 * 1024) // 100 MB
                } catch (_: Throwable) {
                    // Safely ignored
                }
            }
        }
    }
}

