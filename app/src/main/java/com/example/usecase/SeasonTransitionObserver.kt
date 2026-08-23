package com.example.usecase

/**
 * Lightweight Phase 10.8 instrumentation hook for the real season-transition path.
 * Production uses [NONE], so profiling does not introduce a runtime dependency or persistence.
 */
interface SeasonTransitionObserver {
    fun onStageStarted(stage: String) = Unit
    fun onStageFinished(stage: String, durationNanos: Long) = Unit

    companion object {
        val NONE: SeasonTransitionObserver = object : SeasonTransitionObserver {}
    }
}
