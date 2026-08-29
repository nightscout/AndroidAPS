# Spare Phone Setup — Galaxy S24+ (SM-S926U1, Android 16)

Provisioning the doomsday phone with the eternal AAPS build. Do this **while the internet still
works**; the phone must end up needing nothing.

## Phone fleet

| Phone | Role | Notes |
|---|---|---|
| Galaxy S24+ (SM-S926U1, Android 16) | Primary spare — this runbook | Update fully, then freeze (auto-updates off) |
| Galaxy A17 5G (SM-A176U) — to buy new, unlocked | Backup spare #2 | 6 yrs updates from Android 15; take it to Android 16 if offered, then freeze. Verify SM-A176U + "Unlocked" on arrival |
| Galaxy S10e (Android 12 — the minimum) | Test mule + tertiary spare | Battery was deep-discharged and revives slowly: **replace the battery (~$50)** before counting it as a real spare. Rehearse the full pipeline here first: sign, sideload, import, clock tests |

All three are Samsung/One UI: the battery-settings and sideload steps below apply to each.
Every phone in the bag joins the same ~3-month charge rotation.

## 1. Sideload the apps

On the S24+ (One UI):

1. Copy `AAPS-eternal-signed.apk` (and the xDrip+ and BYODA APKs) to the phone (USB cable or
   local transfer — no cloud needed).
2. Open each APK from **My Files**. When prompted "Install unknown apps", allow it for My Files.
3. Keep copies of all three APKs on the phone's storage AND in the archive — a future reinstall
   must not depend on any server.

## 2. Samsung battery settings (do not skip — this kills loops)

- Settings → Apps → AAPS → Battery → **Unrestricted**. Repeat for BYODA and xDrip+.
- Settings → Battery → Background usage limits → make sure none of the three apps are in
  "Sleeping" or "Deep sleeping" lists; add all three to **Never sleeping apps**.
- Settings → Battery → **Adaptive battery: off** (on a dedicated loop phone there is no reason
  to keep it on).
- When AAPS asks to ignore battery optimizations, accept.

## 3. Restore configuration

1. On the live phone: AAPS → Maintenance → **Export settings**. Note the master password
   (it is required at import — keep it in the password manager, never in this repo).
2. Move the export file to the spare phone (`AAPS/preferences` folder) and run
   Maintenance → **Import settings**.
3. This restores profiles (basal/ISF/IC/targets), autoISF settings, objectives progress, and
   skips the setup wizard.
4. **Verify every profile value against the live phone before trusting the spare.**

## 4. Config Builder

- BG Source: **BYODA** (primary). xDrip+ stays installed but idle (break-glass — see
  `RUNBOOK_BREAK_GLASS.md`).
- Pump: **Omnipod DASH** driver (primary). Pairing a pod binds it to the activating phone — the
  spare phone will activate a **new** pod when it takes over; a pod started by the old phone
  cannot be adopted.
- Loop: closed loop enabled.

## 5. Pre-storage test checklist (all in airplane mode)

- [ ] AAPS opens; spot-check basal/ISF/IC values match the live phone.
- [ ] Closed loop available — no objectives or version warnings anywhere.
- [ ] BYODA feeds BG into AAPS.
- [ ] xDrip+ can feed BG into AAPS (switch BG source to xDrip+, see a reading arrive, switch back).
- [ ] Activate a pod from the spare phone; loop closes (green loop).
- [ ] **Clock forward test:** set the date 12 months ahead → loop still closes.
- [ ] **Clock backward test:** set the date 12 months back → objectives/loop still intact.
      (Stock AAPS fails this one; the eternal build must not.) Reset the clock correctly afterward.
- [ ] Printed runbooks + MDI settings card in the bag.
- [ ] APKs, keystore, passwords, settings export archived off-device.

## 6. Storage protocol

- Store at ~50% charge; top up every ~3 months (recurring reminder).
- In the Faraday bag: phone, charging cable, small power bank, printed runbooks + settings card,
  spare pods; RileyLink/OrangeLink (charged, on the same 3-month rotation) if the Eros/Medtronic
  fallback is kept.
- On retrieval: set the clock correctly (dosing history timestamps), even though the loop no
  longer cares.
