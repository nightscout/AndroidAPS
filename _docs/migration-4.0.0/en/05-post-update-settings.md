# Step 5 · Required settings after updating

These are the post-update steps listed on the official "What changed in AAPS v4" page. Several affect loop safety directly.

## 1. Nightscout (NSClientV3)

NSClient v1 is gone. Go to **Configuration → Communication → NSClientV3**:

- Enter your Nightscout URL and an **access token**. The old API secret no longer works.
- Confirm your Nightscout is version 15 or newer, with API v3.
- **Websockets** must be set the same way on the main phone and on followers. For AAPSClient remote control it is mandatory on both.

## 2. Insulin

Older records that had an insulin but no DIA receive a default DIA (for example 9.0 hours for Lyumjev), and records already migrated are not updated again. If you see the migration message about a default DIA, check **Manage → Insulin settings**. To change DIA or peak, create a new insulin and activate it (the active one cannot be edited).

## 3. AAPSClient (caregiver phones)

In 4.0.0, clients send signed commands, can deliver a bolus and each one has a role (read-only, meals, caregiver, admin). Pair every client with the main phone: with an admin token on the main phone, a QR code sets up and pairs the client in one scan. With client control on (hardened mode), treatments typed directly into the Nightscout website **are no longer applied**; enter them on a paired client.

## 4. Duplicate BG readings

Previously the loop stayed in closed loop without correcting. Now it switches to **Low Glucose Suspend (LGS)** and the loop dialog shows why. Remove the duplicates (see the BG quality warning sign) and switch back manually: press and hold the loop icon and choose **Closed Loop**.

## 5. Tidepool

Usually nothing is needed: new data appears under the device name **AAPS** and a new data set starts. **Do not run "Full synchronization"** while old data remains in Tidepool, or it will be duplicated. To move everything under the new name, first **Purge all data** on the Tidepool screen, then run "Full synchronization"; only data still on the phone can be re-uploaded.

## 6. Protection

To edit profiles, insulin, temp targets or QuickWizards while protection is on, tap the ✏️ pencil. To remove the lock entirely, use **Settings → Protection**.

## Also, depending on your setup

- **Pump:** if an imported file named a pump this version lacks, AAPS falls back to the Virtual Pump. Reselect your pump in **Configuration** and check its settings.
- **Omnipod DASH:** drift compensation is always on in 4.0.0.
- **Watch:** on Wear OS 6 or newer, the watch face installs itself (Watch Face Push).

Next: [Step 6 · Final check](06-final-checklist.md)
