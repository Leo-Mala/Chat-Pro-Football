package com.example

import android.app.Application
import android.database.CursorWindow
import com.example.data.EuropeanAuditedFactualBaselinesA3Materializer2026_27
import com.example.data.EuropeanAuditedLowerTierClubTargetMaterializer2026_27
import com.example.data.EuropeanFactualAssetRuntime
import com.example.data.EuropeanFactualClubTargetMaterializer2026_27
import com.example.data.Fc26FactualAssetRuntime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MainApplication : Application() {
    private val seedPrewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()
        Fc26FactualAssetRuntime.initialize(assets)
        EuropeanFactualAssetRuntime.initialize(assets)

        // O snapshot FC26 é imutável e cacheado. Validar/descompactar o asset em background durante
        // o uso normal do menu tira esse custo do clique em INICIAR CARREIRA sem alterar nenhum
        // dado esportivo nem bloquear a thread principal. Se a validação falhar aqui, o fluxo
        // canônico tentará novamente e continuará falhando fechado como antes.
        seedPrewarmScope.launch {
            runCatching { Fc26FactualAssetRuntime.loadValidatedOrNull() }
        }

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
