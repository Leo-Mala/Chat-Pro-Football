package com.example

import android.app.Application
import android.database.CursorWindow
import com.example.data.EuropeanAuditedFactualBaselinesA3Materializer2026_27
import com.example.data.EuropeanAuditedLowerTierClubTargetMaterializer2026_27
import com.example.data.EuropeanFactualAssetRuntime
import com.example.data.EuropeanFactualClubTargetMaterializer2026_27
import com.example.data.Fc26FactualAssetRuntime
import com.example.data.ProductionCareerSeedPrewarm
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

        // Faz em background não só a leitura/validação do FC26, mas também o matching de clubes,
        // mapeamento de jogadores, resolução de empréstimos e fallbacks do universo de produção.
        // Assim o clique em INICIAR CARREIRA reutiliza um plano já pronto em memória.
        seedPrewarmScope.launch {
            runCatching { ProductionCareerSeedPrewarm.prewarm() }
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
