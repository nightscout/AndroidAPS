# CWF / WFF - test environment and operating notes

How to test this work rather than what to build; the design lives in `CWF_WFF_Prompt.md`. Each note
below cost time to learn and is written down so it costs nobody else the same.

## The one rule that matters

**An emulator is wrong by roughly ten times on anything timing-related.** Measured per frame: 34 ms
on a Wear OS emulator against 576 to 2179 ms on a Galaxy Watch 4. Application start: about 6 s
against 27 s. So a change whose value depends on speed is **unproven until it has run on a real
watch** - a startup clock was shipped on emulator evidence once and had to be withdrawn the same
morning.

## Pairing a watch emulator with a phone emulator

Android Studio's **"Pair Wearable" is greyed out** for the Wear OS 6 image, so use the in-app route:

1. Both emulators running.
2. `adb -s <phone> forward tcp:5601 tcp:5601` - the phone **listens** on its own 5601 and the watch
   emulator dials the host. Getting this backwards, or leaving a stale forward pointing at the phone,
   breaks it silently.
3. Wear OS app on the phone -> **overflow menu -> "Pair with emulator"**.
4. Consent screens (Google terms, Fit) must be tapped by a **person**; they cannot be automated.

Watch for `WearableSRegistry: onConnectedNodes: [Node{...}]` on the phone. AAPS then finds the watch;
the watch itself adopts the sender's node from the first message it receives, so `Selected node: null`
before any traffic is normal, not a fault.

A watch emulator needs the Wear OS 6 image `system-images;android-36;android-wear-signed;x86_64`,
created with the **modern** `cmdline-tools/latest/bin/avdmanager` and `JAVA_HOME` pointed at Android
Studio's JBR; the legacy `tools/bin/avdmanager` crashes on this tag.

**If sign-in or pairing fails, suspect TLS interception before anything else.** An antivirus that
scans SSL breaks Google device check-in (`CheckinService result: 3`, `AUTHENTICATION_FAILED`): no
sign-in, no companion app, no pairing - and `git fetch` fails too. Verify by reading the certificate
issuer for `android.googleapis.com`. The interception is often **selective**, so testing one host
proves nothing.

## Emulator quirks that waste time

- **Hardware key injection is ignored.** Studio's toolbar buttons and `adb emu event send
  EV_KEY:KEY_HOME` report success and do nothing. Navigate with `adb shell input keyevent` or an edge
  swipe (`input swipe 20 227 420 227 200` = back). Side buttons map to STEM keys: launcher and
  recents, never the watch face.
- **`screencap` returns 0 bytes during boot** - it only works once the UI is up, so the window before
  the first frame cannot be captured by racing it. Force the state deterministically instead.
- **No watch face editor** on the Google image: `sysui` declares no customisation activity and the
  runtime offers only `Bootstrap`/`Empty`. A Samsung watch has
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

Turn it off again when done: it is about 77 lines a minute, and on the phone it logs whole payloads
including patient data.

## Installing

- **A real watch runs a signed APK, so a debug build is refused** -
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`. The usual way round that refusal is
  to uninstall first, which **wipes the setup on a watch somebody is wearing and depends on**. On a
  watch in daily use, build the APK and let its owner sign and install it.
- **Only ever install the wear APK.** Both faces travel inside it as
  `assets/watchfacepush/wfs.apk` and `assets/watchfacepush/cwf.apk`, each with its token, and the app
  pushes the one chosen in the phone's wear settings. Installing a `watchfacepush-*.apk` by hand takes
  over the same package name and displaces the pushed face.
- The face pushes itself when the **embedded face's token changes**. The "Install watchface" menu
  entry appears **only while the face is absent**, so it cannot be used to force a re-push.
- **After a push to the face that is currently active, re-select it in the picker.** The runtime
  otherwise shows the old and new scenes' alphas together - the ambient layer ghosting over the normal
  face - which reads as a bug and is not one.

## Build rituals

- A KSP error naming a generated class that "could not be resolved" is a flake. Just build again; it
  has never needed anything else.
- `Failed to clean up output files` or a missing `mergeDexFullRelease/classes.dex` - stale outputs.
  `./gradlew.bat --stop`, then `rm -rf wear/build/intermediates/dex wear/build/outputs/apk`, then
  rebuild. A 6.7 MB "release APK" is the broken artefact of such a build; a real one is about 55 MB.
- Validate a document change offline before building:
  `java -cp <validator-push-cli jar> ValidateWff.java <path to watchface.xml> 1`. It takes a **file
  path**, not XML text.
