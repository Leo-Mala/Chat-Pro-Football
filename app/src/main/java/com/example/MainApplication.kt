package com.example

import android.app.Application
import android.database.CursorWindow
import android.util.Log
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
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()
        Fc26FactualAssetRuntime.initialize(assets)
        EuropeanFactualAssetRuntime.initialize(assets)
        fixCursorWindowSize()

        // O snapshot FC26 é grande e imutável. Antecipar sua primeira validação/parse em IO tira
        // esse custo do caminho crítico de Editor/Novo Jogo sem bloquear o startup da Activity.
        // Se houver corrupção, o erro é apenas registrado aqui; a próxima leitura continua
        // fail-closed e volta a validar, em vez de transformar falha em dataset ausente.
        applicationScope.launch {
            try {
                Fc26FactualAssetRuntime.loadValidatedOrNull()
            } catch (e: Exception) {
                Log.e("MainApplication", "Falha ao pré-aquecer snapshot FC26 validado", e)
            }
        }
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