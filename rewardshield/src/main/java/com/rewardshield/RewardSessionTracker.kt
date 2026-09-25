package com.rewardshield

import android.os.SystemClock

/**
 * Tracks one rewarded-ad session and detects the two main ways skippers work:
 *
 *  1. Time acceleration (speed hacks hook the clock APIs so the ad's timer races ahead).
 *     We sample three independent clocks; a hooked process rarely patches all of them
 *     consistently, so their elapsed values drift apart.
 *  2. Fake or early reward callbacks (hooked SDK methods invoke onUserEarnedReward
 *     directly). We require the ad to have been visible for a minimum duration and
 *     the callback to arrive in the expected order.
 *
 * Usage: call [onAdShown] from the SDK's show callback, [onAdVisible]/[onAdHidden] from your
 * lifecycle, and [onRewardCallback] from the reward callback. Treat the returned
 * [Assessment] as a hint; the authoritative grant must come from server-side verification.
 */
class RewardSessionTracker(
    private val minAdDurationMs: Long = 5_000,
    private val maxClockSkewRatio: Double = 0.15,
    private val nanoClock: () -> Long = System::nanoTime,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private var shownNano = 0L
    private var shownElapsed = 0L
    private var shownWall = 0L
    private var visibleSinceElapsed = -1L
    private var visibleTotalMs = 0L
    private var shown = false
    private var rewarded = false

    fun onAdShown() {
        shownNano = nanoClock()
        shownElapsed = elapsedClock()
        shownWall = wallClock()
        visibleSinceElapsed = shownElapsed
        visibleTotalMs = 0
        shown = true
        rewarded = false
    }

    fun onAdHidden() {
        if (visibleSinceElapsed >= 0) {
            visibleTotalMs += elapsedClock() - visibleSinceElapsed
            visibleSinceElapsed = -1
        }
    }

    fun onAdVisible() {
        if (shown && visibleSinceElapsed < 0) visibleSinceElapsed = elapsedClock()
    }

    fun onRewardCallback(): Assessment {
        val signals = mutableListOf<Signal>()
        if (!shown) {
            return Assessment(listOf(Signal("reward_without_show", 100, "Reward fired with no ad shown")))
        }
        if (rewarded) signals += Signal("duplicate_reward", 100, "Reward fired twice for one ad")
        rewarded = true

        onAdHidden()
        val nanoMs = (nanoClock() - shownNano) / 1_000_000.0
        val elapsedMs = (elapsedClock() - shownElapsed).toDouble()
        val wallMs = (wallClock() - shownWall).toDouble()

        if (visibleTotalMs < minAdDurationMs) {
            signals += Signal(
                "reward_too_fast", 60,
                "Ad visible ${visibleTotalMs}ms, minimum is ${minAdDurationMs}ms",
            )
        }
        skew(nanoMs, elapsedMs)?.let { signals += Signal("clock_skew_nano", 50, it) }
        // The wall clock can jump legitimately (NTP sync), so it counts for less.
        skew(wallMs, elapsedMs)?.let { signals += Signal("clock_skew_wall", 25, it) }
        return Assessment(signals)
    }

    private fun skew(a: Double, b: Double): String? {
        val base = maxOf(a, b)
        if (base < 1_000) return null
        val ratio = kotlin.math.abs(a - b) / base
        return if (ratio > maxClockSkewRatio) "Clocks disagree: %.0fms vs %.0fms".format(a, b) else null
    }
}
