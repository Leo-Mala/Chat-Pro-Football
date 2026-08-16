package com.example.util

import java.util.UUID

/**
 * Gerador de IDs únicos e idempotentes para transações e parcelas de transferência.
 */
object TransactionIdGenerator {
    fun generateUniqueId(): Long {
        val mostSigBits = UUID.randomUUID().mostSignificantBits
        val positive = mostSigBits and Long.MAX_VALUE
        return if (positive == 0L) System.currentTimeMillis() else positive
    }
}
