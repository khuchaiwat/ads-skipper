package com.rewardshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulated ad-skipper attacks against [RewardSessionTracker]. Each test reproduces
 * what a real skipper does to the process (a hooked clock or a forged callback) using
 * controllable fake clocks, then checks that the defense catches it.
 */
class SkipperAttackTest {
    /** Fake device clocks. `speed` models a speed hack that makes hooked clocks run faster. */
    private class Clocks {
        var realMs = 0L
        var nanoSpeed = 1.0
        var elapsedSpeed = 1.0
        var wallSpeed = 1.0
        private var nano = 0.0; private var elapsed = 0.0; private var wall = 1_700_000_000_000.0
        fun advance(ms: Long) {
            realMs += ms
            nano += ms * 1_000_000.0 * nanoSpeed
            elapsed += ms * elapsedSpeed
            wall += ms * wallSpeed
        }
        fun tracker(min: Long = 5_000) = RewardSessionTracker(
            minAdDurationMs = min,
            nanoClock = { nano.toLong() },
            elapsedClock = { elapsed.toLong() },
            wallClock = { wall.toLong() },
        )
    }

    private fun Assessment.has(id: String) = signals.any { it.id == id }

    @Test fun legitimateViewIsClean() {
        val c = Clocks(); val t = c.tracker()
        t.onAdShown(); c.advance(30_000)
        assertEquals(Verdict.CLEAN, t.onRewardCallback().verdict)
    }

    @Test fun attack_forgedRewardWithoutAd() {
        val r = Clocks().tracker().onRewardCallback()
        assertEquals(Verdict.BLOCK, r.verdict)
        assertTrue(r.has("reward_without_show"))
    }

    @Test fun attack_rewardFiredImmediately() {
        val c = Clocks(); val t = c.tracker()
        t.onAdShown(); c.advance(300)
        assertTrue(t.onRewardCallback().has("reward_too_fast"))
    }

    @Test fun attack_replayedReward() {
        val c = Clocks(); val t = c.tracker()
        t.onAdShown(); c.advance(30_000)
        t.onRewardCallback()
        assertEquals(Verdict.BLOCK, t.onRewardCallback().verdict)
    }

    @Test fun attack_speedHackOnElapsedRealtimeOnly() {
        // 10x speed hack that hooks only elapsedRealtime: a 30s ad "finishes" in 3s real time.
        val c = Clocks().apply { elapsedSpeed = 10.0 }; val t = c.tracker()
        t.onAdShown(); c.advance(3_000)
        val r = t.onRewardCallback()
        assertTrue(r.has("clock_skew_nano"))
        assertTrue(r.verdict != Verdict.CLEAN)
    }

    @Test fun attack_speedHackOnNanoTimeOnly() {
        val c = Clocks().apply { nanoSpeed = 5.0 }; val t = c.tracker()
        t.onAdShown(); c.advance(6_000)
        assertTrue(t.onRewardCallback().has("clock_skew_nano"))
    }

    @Test fun attack_backgroundingAdToWaitItOut() {
        // Skipper hides the ad (e.g. switches app) and waits for the timer in the background.
        val c = Clocks(); val t = c.tracker()
        t.onAdShown(); c.advance(500); t.onAdHidden(); c.advance(30_000)
        assertTrue(t.onRewardCallback().has("reward_too_fast"))
    }

    /**
     * Known gap: a speed hack that hooks all three clocks consistently is not detected
     * on the device. Server-side verification (SSV) covers this case. The test documents it.
     */
    @Test fun knownGap_consistentSpeedHackOnAllClocks() {
        val c = Clocks().apply { nanoSpeed = 10.0; elapsedSpeed = 10.0; wallSpeed = 10.0 }
        val t = c.tracker()
        t.onAdShown(); c.advance(3_000)
        assertEquals(Verdict.CLEAN, t.onRewardCallback().verdict)
    }
}
