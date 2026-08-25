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
 * valor fornecido pelo ViewModel (normalmente lista vazia = ainda não carregado). Um snapshot que
 * começou antes de uma mutação real continua inelegível. Entre reconciliações somente de leitura,
 * porém, a ordenação é local ao StateFlow: uma leitura concluída não é descartada apenas porque
 * outra instância reservou uma geração global depois dela.
 */
@Suppress("FunctionName")
internal fun <T : List<SaveSlotMetadata>> MutableStateFlow(initialValue: T): KotlinMutableStateFlow<T> {
    val delegate = KotlinMutableStateFlow(initialValue)
    return SaveSlotsPublicationStateFlow(delegate)
}

private class SaveSlotsPublicationStateFlow<T : List<SaveSlotMetadata>>(
    private val delegate: KotlinMutableStateFlow<T>
) : KotlinMutableStateFlow<T> by delegate {
    private val publicationLock = Any()

    private fun isCurrent(candidate: T): Boolean {
        val snapshot = candidate as? SaveSlotsSnapshot ?: return true
        if (snapshot.publicationGeneration < SaveSlotsPublicationClock.invalidationFloor()) {
            return false
        }

        val published = delegate.value as? SaveSlotsSnapshot
        return published == null || snapshot.publicationGeneration > published.publicationGeneration
    }

    override var value: T
        get() = delegate.value
        set(value) {
            synchronized(publicationLock) {
                if (isCurrent(value)) delegate.value = value
            }
        }

    override suspend fun emit(value: T) {
        this.value = value
    }

    override fun tryEmit(value: T): Boolean = synchronized(publicationLock) {
        if (!isCurrent(value)) return@synchronized false
        delegate.tryEmit(value)
    }

    override fun compareAndSet(expect: T, update: T): Boolean = synchronized(publicationLock) {
        if (!isCurrent(update)) return@synchronized false
        delegate.compareAndSet(expect, update)
    }
}
