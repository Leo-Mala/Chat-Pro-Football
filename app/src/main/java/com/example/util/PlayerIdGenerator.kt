package com.example.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Gerador determinístico e thread-safe de IDs para jogadores de domínio.
 * Subsitui chamadas inseguras a System.currentTimeMillis() por uma sequência auditada.
 */
object PlayerIdGenerator {
    private val sequence = AtomicLong(100_000L)

    fun initMaxId(currentMaxId: Long) {
        val nextId = (currentMaxId + 1).coerceAtLeast(100_000L)
        sequence.updateAndGet { current -> maxOf(current, nextId) }
    }

    fun nextId(): Long {
        return sequence.incrementAndGet()
    }

    fun generateDeterministicId(teamId: Long, index: Int): Long {
        return if (teamId > 0L) {
            teamId * 1000L + (index + 1)
        } else {
            nextId()
        }
    }
}
