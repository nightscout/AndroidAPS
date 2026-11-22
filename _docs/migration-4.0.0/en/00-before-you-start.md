# Step 0 · Before you start

4.0.0 was released on 2026-10-31. It is the biggest change in years: the phone and watch apps were rewritten in Jetpack Compose, the tab-based navigation is gone and several functions moved or were renamed. Plan it calmly.

## Requirements

- **Android 12 or newer** on the phone. That has been the minimum since AAPS 3.4 and the 4.0.0 code keeps it (`minSdk = 31` in `buildSrc/src/main/kotlin/Versions.kt`).
- **Wear OS 3.0 or newer** (API 30) if you use a watch. Wear OS 2 is not supported.
- **Nightscout 15 or newer with API v3** and an **access token** (not the old API secret). NSClient v1 has been removed; only NSClientV3 remains.
- **The same keystore** you used to sign the version currently installed. Without it you cannot install over it and you lose the database.
- Your AAPS **master password** (Settings → Protection). An export cannot be imported without it.

## Required reading according to the wiki

1. The 4.0.0 release notes.
2. The "What changed in AAPS v4" page, which lists what moved, was renamed or was removed.

If anything is unclear, the wiki recommends asking the community (Discord or Facebook) before updating.

## Practical recommendations (not official steps)

- Pick a quiet moment with time to review settings, not right before sleeping or going out.
- Keep spare supplies and a way to give insulin manually in case something goes wrong.
- With Omnipod or another tubeless pump, doing it right after a pod change keeps things simple: the step 1 export will contain the active pod's details.
- Write down your current loop mode, active profile and completed objectives so you can check the result.

Next: [Step 1 · Export your settings](01-export-settings.md)
