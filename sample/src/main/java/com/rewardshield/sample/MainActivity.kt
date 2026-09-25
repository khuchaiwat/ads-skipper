package com.rewardshield.sample

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rewardshield.RewardSessionTracker
import com.rewardshield.RewardShield
import kotlin.concurrent.thread

/**
 * Simulates a rewarded ad so you can see the checks work. Replace the simulated
 * callbacks with your ad SDK's (e.g. AdMob's FullScreenContentCallback and
 * OnUserEarnedRewardListener).
 */
class MainActivity : Activity() {
    private val tracker = RewardSessionTracker(minAdDurationMs = 5_000)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this)
        val normal = Button(this).apply { text = "Watch full ad (6s)"; setOnClickListener { play(6_000) } }
        val skipped = Button(this).apply { text = "Simulate skipped ad (1s)"; setOnClickListener { play(1_000) } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(normal); addView(skipped); addView(log)
        })
    }

    private fun play(durationMs: Long) {
        log.text = "Ad playing…"
        tracker.onAdShown()
        main.postDelayed({
            val session = tracker.onRewardCallback()
            thread {
                val result = RewardShield.assess(applicationContext, session)
                val text = buildString {
                    appendLine("Verdict: ${result.verdict} (score ${result.score})")
                    result.signals.forEach { appendLine("• ${it.id}: ${it.detail}") }
                    appendLine()
                    appendLine("Send this with the SSV transaction id to your server; grant only there.")
                }
                runOnUiThread { log.text = text }
            }
        }, durationMs)
    }
}
