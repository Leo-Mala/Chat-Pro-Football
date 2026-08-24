package com.example.ui.viewmodel

import com.example.data.model.SaveSlotMetadata
import com.example.data.model.SaveSlotsPublicationClock
import com.example.data.model.SaveSlotsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow as KotlinMutableStateFlow

/**
 * Factory mais específica que a importação estrela de kotlinx.coroutines usada pelo GameViewModel.
 * Ela só participa de chamadas `MutableStateFlow<List<SaveSlotMetadata>>(...)`; os demais
 * MutableStateFlow continuam usando a factory padrão do kotlinx.coroutines.
 *
 * Nenhum slot desconhecido é anunciado como vazio no startup: o delegate começa exatamente com o
 * valor fornecido pelo ViewModel (normalmente lista vazia = ainda não carregado). O primeiro
 * snapshot concretamente reconciliado pode ser publicado mesmo se outra reconciliação tiver
 * reservado a geração logo depois, porque ainda não existe estado concreto anterior para a UI.
 * Depois da primeira publicação concreta, somente a geração atualmente reservada pode substituir o
 * estado, mantendo o fail-closed contra snapshots antigos após mutações/reconciliações posteriores.
 */
@Suppress("FunctionName")
internal fun <T : List<SaveSlotMetadata>> MutableStateFlow(initialValue: T): KotlinMutableStateFlow<T> {
    val delegate = KotlinMutableStateFlow(initialValue)
    return SaveSlotsPublicationStateFlow(delegate)
}

private class SaveSlotsPublicationStateFlow<T : List<SaveSlotMetadata>>(
    private val delegate: KotlinMutableStateFlow<T>
) : KotlinMutableStateFlow<T> by delegate {

    private fun isCurrent(candidate: T): Boolean {
        val snapshot = candidate as? SaveSlotsSnapshot ?: return true
        val currentValue = delegate.value
        // Estado vazio inicial significa "ainda não carregado", não "slots vazios". Aceitar o
        // primeiro resultado reconciliado evita que duas leituras concorrentes deixem a UI sem
        // qualquer slot concreto; as publicações seguintes continuam estritamente geracionais.
        if (currentValue.isEmpty() && currentValue !is SaveSlotsSnapshot) return true
        return snapshot.publicationGeneration == SaveSlotsPublicationClock.current()
    }

    override var value: T
        get() = delegate.value
        set(value) {
            if (isCurrent(value)) delegate.value = value
        }

    override suspend fun emit(value: T) {
        if (isCurrent(value)) delegate.emit(value)
    }

    override fun tryEmit(value: T): Boolean {
        if (!isCurrent(value)) return false
        return delegate.tryEmit(value)
    }

    override fun compareAndSet(expect: T, update: T): Boolean {
        if (!isCurrent(update)) return false
        return delegate.compareAndSet(expect, update)
    }
}
