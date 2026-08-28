# SHTF / Grid-Down Insulin Loop Resilience Plan

**Purpose:** Build a self-contained, internet-independent, vendor-independent automated-insulin-delivery (AID) setup that keeps working with no Apple, no Google, no App Store, no TestFlight, no Nightscout, and no cell/wifi connectivity. This document is both a **strategy** for the human owner and a **task list for the next Claude Code agent** who will make the code changes and walk the owner through the AAPS rebuild and xDrip+ install.

> **How to use this file**
> - **Human owner:** read Sections 1–4 for the strategy and decisions. Everything else is execution detail.
> - **Next agent:** your concrete work is in Sections 5 (AAPS eternal rebuild) and 6 (xDrip+ backup). Section 11 lists facts you must collect from the owner before starting. Anything I marked **[VERIFY IN SOURCE]** is from another model's recollection and MUST be confirmed against the actual code in this repo before you rely on it — do not trust a file path or constant name in this doc without grepping for it first.

---

## 1. Owner's current known-good baseline

Captured from the owner's running phone (do not assume, confirm if anything changed):

| Item | Value |
|---|---|
| AID app (Android) | **AAPS 3.2.0.4 + autoISF 3.0.1** |
| Build hash / date | `8a4db84731` — **2024.08.17** |
| Flavor | `fullrelease` |
| Nightscout version string | 15.0.2 |
| CGM | **Dexcom G7** via **BYODA** (Build Your Own Dexcom App), already wired into AAPS as the BG source |
| iOS AID app | **Trio** (on an iPhone that is being retired — deprioritize the iOS path) |
| Empirical note | This build is **~2 years old and still closed-looping**, which strongly implies the autoISF fork's version-expiration is not force-disabling the loop. The eternal rebuild (Section 5) turns "empirically fine" into "provably cannot expire." |

**Fork to build from:** `ga-zelle/autoISF` (autoISF extensions of AAPS). A clone-friendly mirror exists at `T-o-b-i-a-s/AndroidAPS-autoisf-clonable` if the main repo's clone-protection gets in the way. **Match the owner's current version** (AAPS 3.2 / autoISF 3.0.1) rather than jumping to a newer one — the goal is a frozen, known-good copy of what the owner already trusts, not an upgrade.

---

## 2. Threat model — what actually breaks in a grid-down scenario

Ranked by what fails first:

1. **Code-signing / app expiration** — the #1 killer. iOS apps expire (TestFlight 90 days; dev-signed ~1 year). Android store apps can be force-updated or pulled. This is what we engineer around.
2. **CGM cloud dependency** — the stock Dexcom app needs an account + Dexcom servers to activate a sensor. (BYODA and xDrip+ mitigate this — see Section 6.)
3. **Nightscout / remote-monitoring** — already optional for the loop; ignore for survival.
4. **Consumables** — pods, sensors, transmitter batteries, and refrigerated insulin run out long before software does. Software resilience is pointless without a consumables + MDI plan (Section 8).

**The loop itself is already offline-capable.** Phone ↔ pump ↔ CGM is all local Bluetooth, and the dosing algorithm ships inside the app. Connectivity is not the problem. **Signing and expiration are.**

---

## 3. The layered plan (defense in depth)

Each layer is independently useful; higher layers are the fallback for lower ones failing.

- **Layer 0 — MDI fallback (always works, zero tech).** A printed card with the owner's basal rates, ISF, carb ratios, and correction targets, plus long-acting + rapid pens. This is the floor. If every device dies, the owner still doses safely. See Section 8.
- **Layer 1 — Android AAPS "eternal" build + BYODA + xDrip backup. THIS IS THE PRIMARY DOOMSDAY DEVICE.** A self-signed Android APK never expires. Rebuild AAPS with version-expiration removed, keep BYODA as primary CGM, add xDrip+ as an offline backup CGM source, load the owner's exact settings, test end-to-end, then cold-store it. Sections 5, 6, 9, 10.
- **Layer 2 — iOS Trio 1-year dev-signed build.** The owner has a paid Apple dev account. A locally Xcode-built, development-signed Trio install runs ~1 year per provisioning profile and needs Apple's portal online only once a year to refresh. Good near-term continuity; not "eternal." Section 7. (Deprioritized because the iPhone is being retired.)
- **Layer 3 — (optional, not pursued) TrollStore permanent iOS signing.** Requires a device on iOS 14.0–16.6.1 or exactly 17.0. Only worth documenting if the owner later acquires such a device. Not in scope here.

**Bottom line for the owner:** put the "forever" property on **Android (Layer 1)**, because a self-signed APK is the only piece in this whole system with *no* expiring component. Everything Apple stays annual at best.

---

## 4. Decisions the owner has already made

- iOS/iPhone is being retired → iOS path is secondary.
- Owner has a **paid Apple developer account** → Layer 1 iOS build is available when wanted (later).
- Owner already installed **BYODA** for the G7 → keep it as primary CGM; add xDrip+ only as offline backup.
- Owner wants the doomsday Android phone rebuilt with **expiration removed**, then stored in a **Faraday bag**.

---

## 5. AGENT TASK — Build the "eternal" AAPS (expiration removed)

**Goal:** produce a self-signed AAPS APK, functionally identical to the owner's current 3.2.0.4+autoISF3.0.1 build, that can **never** force itself to open-loop / LGS due to version age or lack of internet.

### 5.1 Set up the build

1. Confirm the owner's exact version target (Section 11). Check out the matching tag/branch in this repo (AAPS 3.2 / autoISF 3.0.1).
2. Follow the AAPS build environment setup (Android Studio, JDK, Gradle) from the AAPS wiki: <https://wiki.aaps.app/en/latest/Installing-AndroidAPS/Building-APK.html>. Prefer a **fully local** toolchain so a future rebuild needs no internet:
   - After one successful online build, the Gradle dependency cache (`~/.gradle/caches`) and the Android SDK are populated. Archive them alongside the source for offline rebuilds.
   - Note the exact Android Studio / Gradle / JDK versions that produced a working build and record them in this repo (create `BUILD_ENV_NOTES.md`).

### 5.2 Locate and neutralize the expiration / version-expiry logic

> **[VERIFY IN SOURCE — do not trust these names/paths blindly. Grep first.]**
>
> AAPS 3.x enforces version aging in roughly two places. Find them by searching the codebase for these terms and tracing what they gate:
> - `grep -ri "versionExpire\|VERSION_EXPIRE\|OLD_VERSION\|shouldWarnAboutOutdatedVersion\|keyLastTimeThisVersionDetected\|endDate\|daysToExpire\|outdated" --include=*.kt`
> - Likely files: a `VersionCheckerUtilsImpl.kt` (the checker that fetches the latest version and computes age), and a `SafetyPlugin` / constraints implementation whose `isClosedLoopAllowed()` (or similar constraint) returns false / forces open-loop when the version is judged expired. Also a `Config` implementation with expiry-related flags, and `Notification` IDs for the 60-day warning and 90-day expiry.
>
> The user-facing behavior to defeat: ~60 days after a newer release is detected → warning notification; ~90 days → **forced Open Loop / LGS**. Detection normally requires reaching GitHub, but there is also time-since-last-successful-check fallback logic that can trip an **offline** phone. That offline path is exactly what would kill a Faraday-bagged spare, so it must be neutralized too.

**Minimal, surgical change (preferred):** make the closed-loop constraint that checks version-expiry unconditionally treat the version as valid — i.e., the expiry check never contributes a reason to disable closed loop. Do **not** rip out unrelated safety constraints (max IOB, max bolus, objectives, etc.); only the *version-age* gate.

- Leave the version *checker* itself able to run harmlessly (or stub its network call so it never concludes "outdated"), but ensure **no code path forces open-loop or LGS based on version age or staleness**.
- Add a clearly-labeled comment at each edit: `// SHTF eternal build: version-expiry disabled intentionally. See SHTF_LOOP_RESILIENCE_PLAN.md`.

**After editing, prove it:**
- Build succeeds.
- Search again to confirm no remaining code path can flip `isClosedLoopAllowed` (or equivalent) false due to version/staleness.
- If feasible, set the device clock far into the future in a test install and confirm the loop still closes.

### 5.3 Sign and install

- Build a **release APK signed with a keystore the owner controls** (generate one and **archive the keystore + passwords securely** — losing it means you can never build an update-compatible APK again). A self-signed APK has no expiry.
- Sideload to the doomsday phone (enable "install unknown apps").
- **Do not** install over the owner's daily-driver AAPS with a different signing key — different key = uninstall required = data loss. Use a dedicated spare phone.

### 5.4 Restore the owner's exact configuration

- On the owner's live phone: AAPS → **Maintenance → Export settings** (produces an encrypted preferences file in the `AAPS/preferences` folder). Note the **master password** used.
- Move that file to the spare phone and **Import settings**. This restores basal/ISF/IC profiles, targets, algorithm settings (including autoISF), and objective progress — so the spare doesn't have to re-run objectives.
- Verify every profile value on the spare matches the live phone before trusting it.

### 5.5 Deliverables for this section

- The signed APK (archived).
- The keystore + passwords (archived securely by the owner).
- `BUILD_ENV_NOTES.md` (toolchain versions) committed to this repo.
- A record of exactly which files/lines were changed to disable expiry (so a future agent can re-apply after a version bump).

---

## 6. AGENT TASK — Install xDrip+ as an offline backup CGM

**Why:** BYODA stays the **primary** CGM (real Dexcom G7 algorithm, self-built, no store expiry, already working). xDrip+ is added **only as backup** for the one grid-down gap: xDrip+ can pair and **start a fresh G7 sensor session fully offline with no Dexcom account**, whereas BYODA (being the Dexcom app) may need Dexcom's servers to activate a brand-new sensor.

### 6.1 Install xDrip+

- Get xDrip+ from the official source: <https://github.com/NightscoutFoundation/xDrip> (releases). It is self-signed and does not expire. For maximum resilience, also archive the APK (and optionally the source for a future self-build).

### 6.2 Configure for Dexcom G7

- xDrip+ → **Settings → Hardware Data Source → Dexcom G7 / ONE / ONE+** (native G7 mode).
- Pair to the sensor. **BLE caveat:** the G7 supports multiple simultaneous connections, but plan/test how many collectors you run at once (Dexcom-app/BYODA + xDrip + AAPS). Decide whether xDrip runs continuously as a second collector or is a *break-glass* swap-in if BYODA fails.

### 6.3 Wire into AAPS as a fallback BG source

- In AAPS, the primary BG source stays **BYODA**. Document the exact steps to switch AAPS's BG source to **xDrip+** (Config Builder → BG Source) so the owner can flip it under stress without guessing.
- Test that a reading flows xDrip+ → AAPS end-to-end at least once.

### 6.4 Deliverable

- A short, printed **"if BYODA fails, do this"** runbook: how to switch AAPS to xDrip+ and start a G7 session offline.

---

## 7. iOS Trio 1-year path (secondary — do later, only if keeping an iPhone)

The owner has a paid Apple dev account. For continuity while an iPhone is still in use:

- Build Trio locally in Xcode, **development-signed** against the paid account → ~1 year per provisioning profile (vs 90 days for TestFlight).
- Build the **offline "ark"**: a Mac with Xcode archived, a full `git clone --recurse-submodules` of Trio, the SPM package cache mirrored locally, and the signing cert (`.p12`) + provisioning profile exported. Within the profile's validity you can rebuild/reinstall with zero network; once a year, while Apple exists, refresh the profile.
- **Reality:** stock iOS cannot be made *eternal* without TrollStore-class hardware. Treat iOS as bridge continuity, not the doomsday layer. The Android build (Section 5) is the "forever" layer.

*(A separate agent working in the Trio repo can flesh out the exact manual build steps; keep that work in the Trio repo, not here.)*

---

## 8. Consumables & MDI fallback (Layer 0 — the real floor)

Software outlasts supplies. Plan the physical layer:

- **Printed settings card** (Layer 0): total daily dose, basal schedule, ISF, carb ratios, correction targets, and simple correction/carb math — enough to dose by pen with no device. Laminate; keep copies with the kit and off-site.
- **Insulin:** rotate a stock; know unrefrigerated shelf life for the specific insulin. Plan cool storage.
- **Pens/syringes:** long-acting + rapid, as the ultimate fallback.
- **Pump consumables:** pods/reservoirs/infusion sets — stock and rotate by expiry.
- **CGM:** sensor stock + (if applicable) transmitter batteries; know the manual-BG-meter fallback (test strips) for when sensors run out.
- **Pump controller handoff:** **[VERIFY for the owner's pump]** — e.g., Omnipod binds a running pod to the phone that activated it; moving the loop to the spare phone generally means activating a **new** pod. Document the owner's pump's handoff reality so the spare phone can actually take over when needed.

---

## 9. Cold-storage / Faraday-bag protocol

- Store the spare phone at **~50% charge** (lithium degrades fastest at full or empty).
- **Top up every ~3 months** — set a recurring reminder. A dead-battery spare is as useless as an expired app.
- Keep the spare in the Faraday bag with: printed runbooks (Sections 6.4, 8), a charging cable, and ideally a small offline power source.
- Because the eternal build has expiry disabled, the phone's clock drifting or being wrong will **not** disable the loop — but still set the clock correctly when you pull it out, for correct dosing timestamps.

---

## 10. Pre-storage test checklist (do this BEFORE bagging the spare)

Do not trust an untested doomsday device. Confirm, on the spare phone, fully offline (airplane mode):

- [ ] AAPS opens and shows the owner's imported profile values (spot-check basal/ISF/IC).
- [ ] Objectives are satisfied / closed loop is available.
- [ ] BYODA feeds BG into AAPS.
- [ ] xDrip+ can independently feed BG into AAPS (switch source, confirm, switch back).
- [ ] Pump pairs and the loop closes (green loop) — ideally test-enact against a real pod/pump.
- [ ] Set device clock 6–12 months forward → loop STILL closes (proves expiry is defeated). Reset clock afterward.
- [ ] Printed runbooks and settings card are in the bag.
- [ ] Keystore + passwords + settings-export file are archived off-device.

---

## 11. Info the next agent must collect from the owner before starting

1. **Exact pump model** (Omnipod DASH / Eros / Medtronic / Dana / Medtrum…) — determines driver, controller-handoff behavior, and pairing steps.
2. **Spare phone model + Android version** — confirm it can run this AAPS build and BLE-pair the pump and G7.
3. **Confirm target version** — rebuild the same AAPS 3.2.0.4 + autoISF 3.0.1 the owner runs today (recommended), or a specific other version?
4. **Master password** used for the AAPS settings export (needed for import).
5. Whether the owner wants xDrip+ running **continuously** as a second collector, or only as a **break-glass** swap-in.
6. Where the owner will **securely archive** the keystore/passwords (losing them forecloses future compatible updates).

---

*This plan was drafted as a strategy + agent-handoff document. Sections marked **[VERIFY IN SOURCE]** or **[VERIFY for the owner's pump]** contain recollections that MUST be confirmed against the actual code and the owner's actual hardware before being relied upon. Nothing here overrides clinical judgment; the MDI/Layer-0 fallback exists precisely because automated dosing can fail.*
