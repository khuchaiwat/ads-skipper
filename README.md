# RewardShield

An Android library that helps game developers **detect and prevent rewarded-ad
skipping**, the tools that speed through or bypass ads while still collecting the reward.

## How ad skippers work, and what catches each one

| Attack | How it works | Defense here |
|---|---|---|
| Speed hack | Hooks the clock APIs so the ad timer races ahead | `RewardSessionTracker` compares three independent clocks (`nanoTime`, `elapsedRealtime`, wall clock) and flags drift |
| Early or fake reward callback | Hooks the ad SDK and calls `onUserEarnedReward` directly | Minimum visible-duration check, show→reward ordering, duplicate-reward check, and **server-side verification** (SSV) |
| Auto-clicker or skip bot | Accessibility service taps "close" or "skip" | `EnvironmentChecks` lists accessibility services that aren't on a trusted list |
| Hooking frameworks | Frida, Xposed/LSPosed, Substrate | Scans `/proc/self/maps` for known libraries, looks for the Xposed class, probes Frida's default port |
| Root, debugger or emulator | Needed by most of the above | Checks for `su`/Magisk/KernelSU paths, test-keys builds, an attached debugger and emulator fingerprints |

Each signal adds a weight to a score from 0 to 100. The score maps to a verdict of
`CLEAN`, `SUSPICIOUS` or `BLOCK`.

## The important part: grant rewards on the server

Everything on the device can be bypassed by a determined attacker. The one control a
skipper can't fake is **AdMob server-side verification**. Google's servers call your
backend with a signed callback only after an ad has actually completed.
`server/verify_ssv.py` checks the ECDSA signature against Google's published keys,
rejects stale callbacks and rejects duplicate transaction IDs.

Recommended flow:

1. The client shows the ad and records the session with `RewardSessionTracker`.
2. The client sends the `Assessment` (score and signals) to your backend. Treat it as a hint.
3. The backend receives the SSV callback, verifies it, and joins it with the client report
   using `custom_data` or the user ID.
4. The backend grants the reward only when the SSV check is valid. Use the client score
   to throttle, flag or review accounts, not as the only gate.

For stronger device checks, also call the **Play Integrity API** on the server.

## Usage

```kotlin
val tracker = RewardSessionTracker(minAdDurationMs = 5_000)

// Ad SDK callbacks:
tracker.onAdShown()                 // onAdShowedFullScreenContent
tracker.onAdHidden() / onAdVisible() // from onPause / onResume
val session = tracker.onRewardCallback() // onUserEarnedReward

// On a background thread:
val result = RewardShield.assess(context, session)
```

`sample/` contains a demo app with a button that simulates a normal ad and one that
simulates a skipped ad.

## Build

Open the project in Android Studio (AGP 8.5, Kotlin 1.9, JDK 17) and run `:sample`.
