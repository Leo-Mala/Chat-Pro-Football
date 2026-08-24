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
 * valor fornecido pelo ViewModel (normalmente lista vazia = ainda não carregado). Toda publicação
 * concretamente reconciliada, inclusive a primeira, precisa corresponder à geração atualmente
 * reservada. Assim uma mutação de metadata ocorrida enquanto o primeiro load estava em voo nunca
 * permite que um snapshot já invalidado se torne visível.
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
