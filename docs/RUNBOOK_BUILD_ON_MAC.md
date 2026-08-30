# Build, Sign & Install the Eternal AAPS — Mac, Beginner Guide

For a non-developer on a Mac. You will build the eternal AAPS from your own branch in Android
Studio (the official AAPS method), sign it with a keystore only you hold, and install it on the
phone. Do the whole thing first on the **S10e (test mule)**; the steps are identical for the
S24+ and A17.

Time: ~1–2 hours the first time (mostly downloads waiting). Later phones: ~15 min each.

---

## Part A — One-time setup on the Mac

### A1. Install Android Studio
1. Go to <https://developer.android.com/studio>, click **Download Android Studio**, accept terms.
2. It auto-detects Apple Silicon (M-series) vs Intel — either is fine.
3. Open the downloaded `.dmg`, drag **Android Studio** into **Applications**, launch it.
4. On first launch pick **Standard** setup and let it download the SDK components (several GB).
   Accept all licenses. Wait for "Finish."

Android Studio bundles its own Java, so you do NOT need to install Java separately.

### A2. Get the code (your branch with the eternal changes)
1. In the Android Studio welcome window choose **Get from VCS** (or **Clone Repository**).
2. URL: `https://github.com/zgoettsc/AndroidAPS`
3. Pick a folder (e.g. `Documents/AndroidAPS`), click **Clone**, sign in to GitHub if asked.
4. When it opens, bottom-right shows the current branch. Click it → **Remote** →
   **origin/claude/docs-handoff-review-ihrc9w** → **Checkout**. Confirm the branch name shows at
   bottom-right.
5. Let Gradle finish syncing (progress bar at the bottom). First sync downloads more components —
   wait for "Gradle sync finished."

---

## Part B — Create your keystore and signed APK (once; reuse the keystore forever)

> The keystore is your app's identity. The **same** keystore lets you install updates over the top
> later without wiping data. Lose it and you must uninstall (data loss) to install a future build.
> Back it up: password manager + one offline copy (USB stick in the kit). Never email it or commit it.

1. Menu bar: **Build → Generate Signed App Bundle / APK…**
2. Choose **APK**, click **Next**.
3. Click **Create new…** under Key store path. Fill in:
   - **Key store path:** save as `aaps-eternal.jks` somewhere safe (NOT inside the project folder).
   - **Password / Confirm:** a strong keystore password (save it).
   - **Alias:** `aaps`
   - **Key password / Confirm:** can match the keystore password (save it).
   - **Validity (years):** `100`
   - **Certificate:** first + last name is enough; the rest optional.
   - Click **OK**.
4. Back on the signing screen the fields are filled in. Check **Remember passwords**, click **Next**.
5. **Build Variant:** select **fullRelease**. (If checkboxes for **V1** and **V2** signature
   versions appear, tick both.) Click **Create** / **Finish**.
6. Wait for "Generate Signed APK" success (bottom-right). Click **locate** in that popup, or find it
   at:
   `app/build/outputs/apk/full/release/app-full-release.apk`
   That signed file is what you install. Copy it somewhere obvious like the Desktop and rename it
   `AAPS-eternal-signed.apk` so you can find it.

You will reuse `aaps-eternal.jks` (and its passwords) for the other two phones and all future
updates — do not create a new one per phone.

---

## Part C — Put the APK on the phone and install it

Two ways; USB is most reliable.

### C1. Move the file (USB)
1. Connect the phone to the Mac with a USB cable.
2. On a Mac, Samsung phones need **Android File Transfer** (<https://www.android.com/filetransfer/>)
   to show up — install it, open it.
3. On the phone, pull down the notification shade → tap the USB notification → choose
   **File transfer / Android Auto** (not "Charging only").
4. In Android File Transfer, drag `AAPS-eternal-signed.apk` into the phone's **Download** folder.

(No cable? Email the APK to yourself or use Google Drive, and open it from the phone — same result.)

### C2. Install on the phone
1. On the phone open **My Files** (Samsung) → **Downloads** → tap `AAPS-eternal-signed.apk`.
2. First time, Android says installing unknown apps isn't allowed → tap **Settings** →
   toggle **Allow from this source** → back → **Install**.
3. If Play Protect warns about an unknown app, choose **Install anyway** (it's your own build).
4. Open AAPS once it installs. It will ask for permissions — allow them.

---

## Part D — Samsung battery settings (do not skip — this keeps the loop alive)

Settings app on the phone:
1. **Apps → AAPS → Battery → Unrestricted.** (Repeat for BYODA and xDrip+ after you install them.)
2. **Battery → Background usage limits →** make sure AAPS/BYODA/xDrip are NOT in "Sleeping"/"Deep
   sleeping"; add all three to **Never sleeping apps**.
3. **Battery → Adaptive battery: OFF.**
4. When AAPS asks to ignore battery optimization, accept.

---

## Part E — Load your settings and configure

1. **Live phone:** AAPS → **Maintenance → Export settings**. Remember the master password you set.
2. Move the exported file (in the `AAPS/preferences` folder) to the new phone the same way you moved
   the APK, into the matching `AAPS/preferences` folder.
3. **New phone:** AAPS → **Maintenance → Import settings** → enter the master password.
4. **Verify every profile value** (basal, ISF, carb ratio, targets) against the live phone. This
   matters because your export came from an older AAPS version.
5. **Config Builder:** BG Source = **BYODA** (primary); Pump = **Omnipod DASH**; enable closed loop.
6. Install **BYODA** and **xDrip+** APKs the same sideload way (Part C), then redo Part D for them.

---

## Part F — Test before trusting (airplane mode)

- [ ] AAPS opens; profile values match the live phone.
- [ ] Closed loop available — no objectives or version warnings.
- [ ] BYODA feeds BG into AAPS.
- [ ] xDrip+ can feed BG into AAPS (switch source, confirm, switch back).
- [ ] Activate a pod; loop closes (green loop).
- [ ] Set the date 12 months FORWARD → loop still closes.
- [ ] Set the date 12 months BACK → objectives/loop still fine. Reset the clock afterward.

Then: turn OFF auto-updates (Settings → Software update → auto download off; also in Galaxy/Play
Store), charge to ~50%, and bag it. See `RUNBOOK_SPARE_PHONE_SETUP.md` for storage.

---

## If something breaks

- **Gradle sync/build fails on memory** (Mac with 8 GB RAM): open `gradle.properties`, change
  `org.gradle.jvmargs=-Xmx8g …` to `-Xmx4g`, sync again.
- **"App not installed"** on the phone: an existing AAPS signed with a different key is present —
  uninstall it first (this wipes its data; only do it on a spare, never your live phone by accident).
- **Wrong branch:** bottom-right must read `claude/docs-handoff-review-ihrc9w` before you build.
