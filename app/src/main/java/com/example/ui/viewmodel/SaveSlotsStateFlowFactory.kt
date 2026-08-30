package com.example.ui.viewmodel

import com.example.data.model.SaveSlotMetadata
import com.example.data.model.SaveSlotsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow as KotlinMutableStateFlow

/**
 * Factory mais específica que a importação estrela de kotlinx.coroutines usada pelo GameViewModel.
 * Ela só participa de chamadas `MutableStateFlow<List<SaveSlotMetadata>>(...)`; os demais
 * MutableStateFlow continuam usando a factory padrão do kotlinx.coroutines.
 *
 * Nenhum slot desconhecido é anunciado como vazio no startup: o delegate começa exatamente com o
 * valor fornecido pelo ViewModel (normalmente lista vazia = ainda não carregado). Um snapshot que
 * começou antes de uma mutação real do seu próprio repositório continua inelegível. Reconciliações
 * de repositórios independentes usam domínios diferentes e não podem invalidar umas às outras.
 *
 * Publicações locais (não-SaveSlotsSnapshot) são aceitas apenas vazias ou com o conjunto completo
 * de cinco slots conhecidos. Isso mantém o fast-path de criação de carreira sem permitir que uma
 * corrida de startup transforme uma lista ainda não reconciliada em autorização implícita para
 * sobrescrever slots desconhecidos.
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

    private fun isStructurallySafeLocalPublication(candidate: T): Boolean {
        if (candidate is SaveSlotsSnapshot || candidate.isEmpty()) return true
        if (candidate.size != SAVE_SLOT_COUNT) return false
        val ids = candidate.mapTo(mutableSetOf()) { it.id }
        return ids == EXPECTED_SAVE_SLOT_IDS
    }

    private fun isCurrent(candidate: T): Boolean {
        if (!isStructurallySafeLocalPublication(candidate)) return false
        val snapshot = candidate as? SaveSlotsSnapshot ?: return true
        if (snapshot.publicationGeneration < snapshot.publicationDomain.invalidationFloor()) {
            return false
        }

        val published = delegate.value as? SaveSlotsSnapshot
        return published == null ||
            published.publicationDomain !== snapshot.publicationDomain ||
            snapshot.publicationGeneration > published.publicationGeneration
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

    private companion object {
        const val SAVE_SLOT_COUNT = 5
        val EXPECTED_SAVE_SLOT_IDS = (1..SAVE_SLOT_COUNT).map(Int::toString).toSet()
    }
}
