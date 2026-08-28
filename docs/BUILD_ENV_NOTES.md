# Build Environment Notes (Eternal Build)

Toolchain that builds this tree (AAPS 3.4.2.6 + eternal-build changes). Record kept so a future
rebuild can be done fully offline from archived pieces.

## Versions

| Component | Version |
|---|---|
| Base source | AAPS `3.4.2.6`, commit `598e2eb3` (= upstream `nightscout/AndroidAPS` tag `3.4.2.6`) + eternal-build changes (see `ETERNAL_BUILD_CHANGES.md`) |
| JDK | OpenJDK 21 (repo requires Java 21 — `Versions.kt`) |
| Gradle | 9.0.0 (wrapper: `gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin | 8.13.2 (`gradle/libs.versions.toml`) |
| compileSdk / targetSdk / minSdk | 36 / 32 / 31 (Android 12 is the install floor) |
| Android SDK components | `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`, cmdline-tools `11076708` |
| Flavor / variant | `full` / `release` → task `:app:assembleFullRelease` |

Android Studio is optional — everything builds from the command line:

```bash
export ANDROID_HOME=~/android-sdk        # or wherever the SDK lives
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleFullRelease
# unsigned output: app/build/outputs/apk/full/release/
```

## Signing (owner's machine only — never commit or share the keystore)

Generate once, archive forever (offline copy + password manager):

```bash
keytool -genkeypair -v -keystore aaps-eternal.jks -alias aaps \
  -keyalg RSA -keysize 4096 -validity 36500
```

Sign an unsigned release APK (apksigner ships in `build-tools/36.0.0/`):

```bash
apksigner sign --ks aaps-eternal.jks --ks-key-alias aaps \
  --out AAPS-eternal-signed.apk app-full-release-unsigned.apk
apksigner verify --print-certs AAPS-eternal-signed.apk
```

A self-signed APK never expires. Losing the keystore means a future update can only be installed
by uninstalling first (losing on-phone data), so treat the keystore + passwords as part of the kit.

Alternatively, build **and** sign in Android Studio: Build → Generate Signed App Bundle / APK →
APK → select the keystore → variant `fullRelease`.

## Offline rebuild ark

After one successful **online** build, archive together:

1. This source tree (full git clone).
2. The Gradle dependency cache `~/.gradle/caches` **and** the wrapper dist `~/.gradle/wrapper`.
3. The Android SDK directory (`$ANDROID_HOME`).
4. A JDK 21 installer/tarball for the build machine's OS.
5. The keystore + passwords (separately, securely).

With those five pieces a rebuild needs zero network: restore `~/.gradle` and the SDK, then run
`./gradlew --offline :app:assembleFullRelease`.

## Notes

- `app/google-services.json` is committed in this repo, so no Firebase setup is needed to build.
  On a phone with no internet the Firebase calls fail harmlessly.
- Do not install this build over an existing AAPS signed with a different key — Android requires
  an uninstall first (data loss). Dedicated spare phone only.
