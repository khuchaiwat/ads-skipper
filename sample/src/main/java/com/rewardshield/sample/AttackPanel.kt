package com.rewardshield.sample

import android.os.SystemClock
import com.rewardshield.RewardSessionTracker

/**
 * Debug-only attack modes that act on THIS app's own ad session, so you can check
 * RewardShield's verdicts on a real device. They reproduce what an ad skipper does from
 * outside (hooked clocks, forged callbacks) from inside the app. Remove from release builds.
 */
enum class Attack(val label: String) {
    NONE("Normal ad (6s)"),
    FORGED_REWARD("Forge reward, no ad shown"),
    INSTANT_REWARD("Reward 0.3s after show"),
    DOUBLE_REWARD("Fire reward twice"),
    SPEED_HACK_10X("10x speed hack on elapsedRealtime"),
    BACKGROUND_WAIT("Hide ad and wait in background"),
}

/** An elapsedRealtime clock that can be sped up, like a speed-hack hook would. */
class HookableClock {
    var speed = 1.0
    private var baseReal = SystemClock.elapsedRealtime()
    private var baseFake = baseReal.toDouble()
    fun now(): Long {
        val real = SystemClock.elapsedRealtime()
        baseFake += (real - baseReal) * speed
        baseReal = real
        return baseFake.toLong()
    }
}

fun hookedTracker(clock: HookableClock) =
    RewardSessionTracker(minAdDurationMs = 5_000, elapsedClock = clock::now)
