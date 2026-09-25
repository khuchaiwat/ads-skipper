package com.rewardshield.sample

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rewardshield.Assessment
import com.rewardshield.RewardShield
import kotlin.concurrent.thread

/**
 * Simulates a rewarded ad and lets you run attacks against it. Replace the simulated
 * callbacks with your ad SDK's (e.g. AdMob's FullScreenContentCallback and
 * OnUserEarnedRewardListener).
 */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        Attack.values().forEach { a ->
            root.addView(Button(this).apply { text = a.label; setOnClickListener { run(a) } })
        }
        root.addView(log)
        setContentView(root)
    }

    private fun run(attack: Attack) {
        val clock = HookableClock()
        val tracker = hookedTracker(clock)
        log.text = "Running: ${attack.label}…"
        when (attack) {
            Attack.NONE -> { tracker.onAdShown(); after(6_000) { tracker.onRewardCallback() } }
            Attack.FORGED_REWARD -> report(attack, tracker.onRewardCallback())
            Attack.INSTANT_REWARD -> { tracker.onAdShown(); after(300) { tracker.onRewardCallback() } }
            Attack.DOUBLE_REWARD -> {
                tracker.onAdShown()
                after(6_000) { tracker.onRewardCallback(); tracker.onRewardCallback() }
            }
            Attack.SPEED_HACK_10X -> {
                clock.speed = 10.0
                tracker.onAdShown(); after(1_000) { tracker.onRewardCallback() }
            }
            Attack.BACKGROUND_WAIT -> {
                tracker.onAdShown()
                main.postDelayed({ tracker.onAdHidden() }, 500)
                after(6_000) { tracker.onRewardCallback() }
            }
        }
        if (attack != Attack.FORGED_REWARD) pending = attack
    }

    private var pending = Attack.NONE

    private fun after(ms: Long, reward: () -> Assessment) =
        main.postDelayed({ report(pending, reward()) }, ms)

    private fun report(attack: Attack, session: Assessment) = thread {
        val result = RewardShield.assess(applicationContext, session)
        val text = buildString {
            appendLine("${attack.label}")
            appendLine("Verdict: ${result.verdict} (score ${result.score})")
            result.signals.forEach { appendLine("• ${it.id}: ${it.detail}") }
        }
        runOnUiThread { log.text = text }
    }
}
