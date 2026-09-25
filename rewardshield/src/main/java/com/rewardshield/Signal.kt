package com.rewardshield

/** A single suspicious observation. [weight] is 0..100; higher is more suspicious. */
data class Signal(val id: String, val weight: Int, val detail: String)

enum class Verdict { CLEAN, SUSPICIOUS, BLOCK }

data class Assessment(val signals: List<Signal>) {
    val score: Int = signals.sumOf { it.weight }.coerceAtMost(100)
    val verdict: Verdict = when {
        score >= 70 -> Verdict.BLOCK
        score >= 30 -> Verdict.SUSPICIOUS
        else -> Verdict.CLEAN
    }
}
