package com.example.data.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Relógio monotônico dos snapshots de listagem de saves.
 *
 * Reconciliações reservam gerações para permitir ordenação dos snapshots. Uma nova leitura, por si
 * só, não torna incorreto um snapshot que já terminou: apenas uma mutação externa invalida leituras
 * que começaram antes dela. O StateFlow especializado combina este piso de invalidação com a
 * geração do snapshot já publicado para rejeitar regressões sem criar interferência entre leituras
 * independentes ainda em voo.
 */
internal object SaveSlotsPublicationClock {
    private val generation = AtomicLong(0L)
    private val invalidationGeneration = AtomicLong(0L)

    fun reserve(): Long = generation.incrementAndGet()

    fun invalidate(): Long {
        val invalidatedAt = generation.incrementAndGet()
        invalidationGeneration.set(invalidatedAt)
        return invalidatedAt
    }

    fun current(): Long = generation.get()

    /**
     * Toda reconciliação reservada antes desta geração é inelegível para publicação. Reservas de
     * leitura posteriores não elevam este piso; somente mutações reais o fazem.
     */
    fun invalidationFloor(): Long = invalidationGeneration.get()
}

/** Lista imutável que transporta a geração da reconciliação até a fronteira do StateFlow. */
internal class SaveSlotsSnapshot(
    val publicationGeneration: Long,
    private val slots: List<SaveSlotMetadata>
) : AbstractList<SaveSlotMetadata>() {
    override val size: Int
        get() = slots.size

    override fun get(index: Int): SaveSlotMetadata = slots[index]
}
