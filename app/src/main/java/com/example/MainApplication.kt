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
    private val fc26WarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()
        Fc26FactualAssetRuntime.initialize(assets)
        EuropeanFactualAssetRuntime.initialize(assets)

        // Antecipamos somente a parte imutável/factual do FC26: leitura, validação e templates dos
        // 18.405 jogadores. Diferente do prewarm antigo, não gera os ~40k fallbacks procedurais,
        // não calcula calendário e não segura o lock global do plano de ~60k jogadores. Se o
        // usuário iniciar a carreira cedo, o caminho síncrono reutiliza tudo que já ficou pronto.
        fc26WarmupScope.launch {
            runCatching { Fc26FactualAssetRuntime.prewarmRuntimeTemplates() }
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
