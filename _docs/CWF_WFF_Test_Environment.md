# CWF / WFF - test environment and operating notes

Everything here was learned the hard way during 2026-09-01 to 09-03 and exists nowhere else. It is
about **how to test**, not what to build; the plan lives in `CWF_WFF_Prompt.md`.

## The one rule that matters

**The emulator is wrong by roughly ten times on anything timing-related.** Per-frame: 34 ms on the
emulator against 576 to 2179 ms on the Galaxy Watch 4. Application start: about 6 s against 27 s. A
change whose value depends on speed is **unproven until it has run on the watch** - that is how the
startup clock (§7z-bis) shipped and had to be withdrawn the same morning.

## Devices

| device | how to reach it |
|---|---|
| **Galaxy Watch 4** (SM-R890), the reference | Wireless debugging. The **port changes on every reboot**, and often on its own. The mDNS serial usually survives: `adb devices` may list `adb-RFAT90H10TM-...._adb-tls-connect._tcp` - use that as `-s` and no port is needed. |
| **Wear6_API36** emulator | Wear OS 6, `system-images;android-36;android-wear-signed;x86_64`. Created with the **modern** `cmdline-tools/latest/bin/avdmanager` and `JAVA_HOME` pointed at Android Studio's JBR; the legacy `tools/bin/avdmanager` crashes on this tag. |
| **Phone_API34_Play** emulator | Android 14, Play image, Google account signed in, AAPS installed with the user's Nightscout settings. `hw.keyboard=yes` so credentials can be typed. |

## Pairing the two emulators

Studio's **"Pair Wearable" is greyed out** for this Wear image, so use the in-app route:

1. Both emulators running.
2. `adb -s <phone> forward tcp:5601 tcp:5601` - the phone **listens** on its own 5601 and the watch
   emulator dials the host. Getting this backwards, or leaving a stale forward pointing at the phone,
   breaks it silently.
3. Wear OS app on the phone -> **overflow menu -> "Pair with emulator"**.
4. Consent screens (Google ToS, Fit) must be tapped by the **user**, not automated.

Watch for `WearableSRegistry: onConnectedNodes: [Node{...}]` on the phone. AAPS then finds the watch;
the watch itself adopts the sender's node from the first message it receives, so `Selected node: null`
before any traffic is normal, not a fault.

**Norton's SSL/TLS scanning must stay off.** With it on, Google device check-in fails
(`CheckinService result: 3`, `AUTHENTICATION_FAILED`), so no sign-in, no companion app, no pairing -
and `git fetch` fails too. Verify by reading the certificate issuer for `android.googleapis.com`; it
must not be `Norton Web/Mail Shield Root`. The interception is **selective**, so testing one host
proves nothing.

## Emulator quirks that waste time

- **Hardware key injection is ignored.** Studio's toolbar buttons and `adb emu event send
  EV_KEY:KEY_HOME` report success and do nothing. Navigate with `adb shell input keyevent` or an edge
  swipe (`input swipe 20 227 420 227 200` = back). Side buttons map to STEM keys: launcher and
  recents, never the watch face.
- **`screencap` returns 0 bytes during boot** - it only works once the UI is up, so the window before
  the first frame cannot be captured by racing it. Force the state deterministically instead.
- **No watch face editor** on the Google image: `sysui` declares no customisation activity and the
  runtime offers only `Bootstrap`/`Empty`. Samsung's watch has
  `com.samsung.wear.watchface.runtime/.editor.EditorActivity`.
- `adb root`, `pm disable-user` and `setprop persist.sys.timezone` are all refused on these
  production builds. Set the timezone with the emulator's `-timezone` flag instead.
- Watch face picker: `am start -n com.google.android.wearable.sysui/com.google.android.clockwork.sysui.mainui.module.watchfacepicker2.WatchFacePickerAllFacesActivity`,
  then tap the **preview thumbnail** at about `120,230` - the label below it is not clickable and a
  tap there only scrolls.

## Diagnosing before blaming the render path

**Check what is on top first.** A face that looks frozen is often an activity covering it:

```
adb shell dumpsys window | grep mCurrentFocus     # SysUiActivity = the face really is showing
adb shell dumpsys wallpaper | grep mWallpaperComponent
```

`CwfRenderPreviewActivity` (debug, adb-launched) draws a **still** bitmap and looks exactly like a
broken watch face - it once sat on screen for three hours and was reported as a freeze. Close it.

Never `am force-stop com.google.wear.watchface.runtime` to force a reload: the system falls back to
its own default face and ours must be re-selected by hand.

## Logging

`log_WEAR` gates every AAPS wear log line. To turn it on without the UI (debug builds):

```
adb shell am force-stop info.nightscout.androidaps
adb shell "run-as info.nightscout.androidaps sed -i 's|name=\"log_WEAR\" value=\"false\"|name=\"log_WEAR\" value=\"true\"|' //data/data/info.nightscout.androidaps/shared_prefs/info.nightscout.androidaps_preferences.xml"
```

Note the `//data/...` double slash - Git Bash rewrites a single leading slash into a Windows path.

**Currently left ON** on both emulators and on the GW4. Turn it off when this topic closes: it is
about 77 lines a minute, and on the phone it logs whole payloads including patient data (§7y, S16).

## Installing

- **Never install on the physical watch. Only the owner does that.** It runs a *signed* APK, so a
  debug build is refused - `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match` - and the
  usual way round that refusal is to uninstall first, which **wipes the setup on a watch somebody is
  wearing and depends on**. Build it, say it is ready, and stop there. Attempted once from here; it
  failed of its own accord, which was luck rather than care.

  | device | who installs |
  |---|---|
  | Wear 6 emulator | anyone, debug builds are fine |
  | phone emulator | anyone |
  | **the Galaxy Watch 4** | **the owner, with their signed APK** |

  On the watch, read only: logs, package state, battery, screenshots.

- **Only ever install the wear APK.** The face travels inside it as
  `assets/watchfacepush/aapsv4.apk` plus its token, and the app pushes it at runtime. Installing
  `watchfacepush-full-release.apk` by hand takes over the same package name and displaces the pushed
  face - it cost several hours on 09-01.
- The face pushes itself when the **embedded face's token changes** (`c3ecd486f0`). The "Install
  watchface" menu entry appears **only while the face is absent**, so it cannot be used to force a
  re-push.
- **After a push to the face that is currently active, re-select it in the picker.** The runtime
  otherwise shows the old and new scenes' alphas together - the ambient layer ghosting over the normal
  face - which reads as a bug and is not one.

## Build rituals

- `[ksp] ComponentProcessingStep ... WearActivitiesModule_... could not be resolved` - a flake. Just
  build again; it has never needed anything else.
- `Failed to clean up output files` or a missing `mergeDexFullRelease/classes.dex` - stale outputs.
  `./gradlew.bat --stop`, then `rm -rf wear/build/intermediates/dex wear/build/outputs/apk`, then
  rebuild. A 6.7 MB "release APK" is the broken artefact of such a build; a real one is about 55 MB.
- Validate a document change offline before building:
  `java -cp <validator-push-cli jar> ValidateWff.java <path to watchface.xml> 1`. It takes a **file
  path**, not XML text.
