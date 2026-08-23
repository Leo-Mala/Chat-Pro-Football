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
 * A fronteira de publicação consulta o relógio global imediatamente no setter. Isso fecha a janela
 * que não pode ser fechada apenas dentro de `loadSaveSlots()`: uma reconciliação antiga pode ter
 * retornado ao caller e perder a CPU antes de escrever no StateFlow. Se uma reconciliação ou
 * mutação mais nova já reservou/invalida a geração, o snapshot antigo é descartado aqui.
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
            if (isCurrent(value)) {
                delegate.value = value
            }
        }

    override suspend fun emit(value: T) {
        if (isCurrent(value)) {
            delegate.emit(value)
        }
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
