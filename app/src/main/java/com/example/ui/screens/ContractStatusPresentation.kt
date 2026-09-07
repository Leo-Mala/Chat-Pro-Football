package com.example.ui.screens

internal enum class ContractAttention {
    REGULAR,
    LAST_YEAR,
    EXPIRING_THIS_WEEK
}

internal data class ContractPresentation(
    val attention: ContractAttention,
    val weeksText: String,
    val badgeText: String?
)

internal fun contractPresentation(weeksRemaining: Int): ContractPresentation {
    val normalizedWeeks = weeksRemaining.coerceAtLeast(0)
    val attention = when {
        normalizedWeeks <= 1 -> ContractAttention.EXPIRING_THIS_WEEK
        normalizedWeeks <= 52 -> ContractAttention.LAST_YEAR
        else -> ContractAttention.REGULAR
    }

    val weeksText = if (normalizedWeeks == 1) {
        "1 semana restante"
    } else {
        "$normalizedWeeks semanas restantes"
    }

    val badgeText = when (attention) {
        ContractAttention.REGULAR -> null
        ContractAttention.LAST_YEAR -> "ÚLTIMO ANO"
        ContractAttention.EXPIRING_THIS_WEEK -> "ENCERRA ESTA SEMANA"
    }

    return ContractPresentation(
        attention = attention,
        weeksText = weeksText,
        badgeText = badgeText
    )
}
