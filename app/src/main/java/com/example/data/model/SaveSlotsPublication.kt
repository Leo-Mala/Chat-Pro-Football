package com.example.data.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Domínio monotônico de publicação dos snapshots de listagem de saves.
 *
 * O domínio pertence ao repositório que produz os snapshots: reconciliações e mutações de uma
 * instância não podem invalidar publicações de outra instância independente. Isso também impede que
 * ViewModels antigos ou fixtures de teste ainda vivos contaminem o estado de um repositório novo.
 */
internal class SaveSlotsPublicationDomain {
    private val generation = AtomicLong(0L)
    private val invalidationGeneration = AtomicLong(0L)

    fun reserve(): Long = generation.incrementAndGet()

    fun invalidate(): Long {
        val invalidatedAt = generation.incrementAndGet()
        invalidationGeneration.updateAndGet { current -> maxOf(current, invalidatedAt) }
        return invalidatedAt
    }

    fun current(): Long = generation.get()

    /**
     * Toda reconciliação deste domínio reservada antes desta geração é inelegível para publicação.
     * Reservas de leitura posteriores não elevam o piso; somente mutações reais deste domínio.
     */
    fun invalidationFloor(): Long = invalidationGeneration.get()
}

/**
 * Domínio de compatibilidade usado pelos gates unitários que exercitam diretamente o contrato de
 * publicação. O runtime cria um domínio próprio por GamePreferencesRepository através de
 * [newDomain], evitando qualquer estado global entre repositórios independentes.
 */
internal object SaveSlotsPublicationClock {
    private val compatibilityDomain = SaveSlotsPublicationDomain()

    fun reserve(): Long = compatibilityDomain.reserve()

    fun invalidate(): Long = compatibilityDomain.invalidate()

    fun current(): Long = compatibilityDomain.current()

    fun invalidationFloor(): Long = compatibilityDomain.invalidationFloor()

    fun newDomain(): SaveSlotsPublicationDomain = SaveSlotsPublicationDomain()

    fun compatibilityDomain(): SaveSlotsPublicationDomain = compatibilityDomain
}

/** Lista imutável que transporta a geração e seu domínio até a fronteira do StateFlow. */
internal class SaveSlotsSnapshot(
    val publicationGeneration: Long,
    private val slots: List<SaveSlotMetadata>,
    val publicationDomain: SaveSlotsPublicationDomain = SaveSlotsPublicationClock.compatibilityDomain()
) : AbstractList<SaveSlotMetadata>() {
    override val size: Int
        get() = slots.size

    override fun get(index: Int): SaveSlotMetadata = slots[index]
}
