package com.example.data.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Relógio monotônico dos snapshots de listagem de saves.
 *
 * Toda nova reconciliação reserva uma geração. Uma mutação externa de metadata também invalida
 * snapshots em voo antes de alterar o estado. Assim uma leitura antiga nunca pode ser publicada
 * depois que uma operação mais nova começou.
 */
internal object SaveSlotsPublicationClock {
    private val generation = AtomicLong(0L)

    fun reserve(): Long = generation.incrementAndGet()

    fun invalidate(): Long = generation.incrementAndGet()

    fun current(): Long = generation.get()
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
