package com.rewardshield

import android.content.Context

/** Combines the device checks with one ad session's timing checks. Call off the main thread. */
object RewardShield {
    fun assess(context: Context, session: Assessment): Assessment =
        Assessment(EnvironmentChecks(context).assess().signals + session.signals)
}
