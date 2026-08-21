# NFC plugin - migration to Kotlin Multiplatform

Written 2026-08-20. An architecture review of what the NFC Commands plugin needs in order to fit the
Kotlin Multiplatform refactoring that is going on in `Nightscout/kmp`.

This note answers two questions:

1. What has to change in the NFC plugin to meet the KMP rules?
2. Is `Nightscout/kmp` far enough along to start now, or would the NFC code have to be reworked
   again later?

Companion documents: `_docs/NFC_COMMANDS.md` (what the plugin does) and
`_docs/KMP_IOS_FEASIBILITY.md` on the `kmp` branch (the KMP plan itself, wave by wave).

**No migration work has been done yet.** Everything below is a description of what is still needed.

---

## 1. Where the two branches stand

| Branch                           | HEAD          | Date       | Commits ahead of the shared base |
|----------------------------------|---------------|------------|----------------------------------|
| `nfc/new-nfc-plugin_kmp` (ours)  | `3a902e974fc` | 2026-08-16 | 41                               |
| `Nightscout/kmp` (project owner) | `4957c26eb85` | 2026-08-20 | **153**                          |

Shared base: `7fc8205e9a7` ("Fix scenes expiration", on `dev`).

The two branches have **no KMP commit in common**. Our branch is built on `dev`; the KMP work all
sits on the other side.

`Nightscout/kmp` is moving fast - it was still receiving commits on the day this note was written.
Its own documentation is already behind it: `KMP_IOS_FEASIBILITY.md` stops at wave 18
(`:core:interfaces`), while the branch has since converted `:core:objects`, `:core:graph`,
`:core:ui`, `:core:utils`, `:pump:virtual`, `:plugins:smoothing`, `:plugins:sensitivity`,
`:plugins:calibration`, `:plugins:main`, `:plugins:aps`, and is part way through
`:plugins:automation`.

### Source set layout on `kmp`, per module

Measured from the branch, not assumed:

| Module                           | Source sets                                                | Converted? |
|----------------------------------|------------------------------------------------------------|------------|
| `:core:data`                     | `commonMain` `commonTest` `iosMain` `jvmMain` `nativeMain`  | yes        |
| `:core:interfaces`               | `commonMain` `androidMain` `iosMain` `androidHostTest`      | yes        |
| `:core:keys`                     | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:core:ui`                       | `commonMain` `androidMain` `iosMain` `androidHostTest`      | yes        |
| `:core:objects`                  | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:core:utils`                    | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:plugins:main`                  | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:plugins:aps`                   | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:plugins:sensitivity`           | `commonMain` `androidMain` `androidHostTest`                | yes        |
| `:plugins:smoothing`             | `commonMain` `androidMain` `androidHostTest`                | yes        |
| **`:plugins:sync`**              | **`main` `test` `androidTest`**                             | **no**     |
| `:plugins:automation`            | `main` `test`                                               | no         |
| `:plugins:configuration`         | `main` `test`                                               | no         |
| `:database:*`, `:implementation` | `main` `test`                                               | no         |

**`:plugins:sync`, where the NFC plugin lives, is not converted and is not next in line.** It is one
of the largest plugin modules and has not been touched by the module flips.

---

## 2. What is already fine

The NFC plugin makes 90 distinct `app.aaps.core.*` imports. Every one of them exists on `kmp`, and
**all of them are already in `commonMain`**. Nothing the plugin depends on was deleted or left
behind on Android, including the newest interfaces added by our own recent work:

`SceneAutomationApi`, `SceneIconResolver`, `ClientControlActionDispatcher`, `WizardBolusExecutor`,
`BolusProgressData`, `ElementType`, `PluginBase`, `PluginBaseWithPreferences`, `PluginDescription`,
`PreferenceSubScreenDef`, `PumpCommunicationStatus`, `PumpActivityDialog`, `NumberInputRow`,
`QuickAddButtons`, `AapsTheme`, `LocalDateUtil`, `LocalConfig`, `LocalPreferences`,
`GlobalSnackbarHost`, `consumeOverscroll`, `DecimalFormatter`, `OrgJsonCompat`, `JsonLenientRead`.

The plugin also already has a clean internal split. Of its 34 main files (4413 lines):

- **30 files have zero `android.*` imports.**
- All 28 Android imports sit in **4 files**: `NfcBuildScreen`, `NfcCommandsPlugin`,
  `NfcControlActivity`, `NfcForegroundDispatch`.
- The 25 action files, `NfcCommandCode`, `NfcJsonKeys`, `NfcTagStore`, `NfcCommandsScreen` and
  `NfcCommonUi` are Android free apart from `androidx.annotation.StringRes` and `org.json`.

The Android entry points are **already in the right module**. The manifest activity declaration,
`app/src/main/res/xml/nfc_tech_filter.xml` and the `ComposeMainActivity` hooks all live in `:app`,
which is exactly where `kmp` commit `4957c26eb85` (":plugins:automation move Android entry points to
:app") puts this kind of code. No change needed there.

Three compatibility seams also survive the merge unchanged:

- `PreferenceSubScreenDef` kept a `titleResId: Int` constructor next to the new `title: TextRef`
  one, so the NFC preference screen still compiles.
- `ResourceHelper` still declares `gs(id: Int)` in `androidMain`, so all 82 `rh.gs(R.string.x)`
  calls still compile inside an Android module.
- `dagger.android` is still alive on `kmp` (329 files use `HasAndroidInjector`), so
  `NfcControlActivity`'s `AndroidInjection.inject(this)` is not broken either.

---

## 3. Tier 1 - what breaks the moment `kmp` is merged

Six items. These are hard compile errors, and all of them are small. Rough cost: one day.

### 3.1 `PluginDescription` takes `TextRef` only

`pluginName`, `shortName` and `description` no longer have an `Int` overload. Three call sites, in
`NfcCommandsPlugin.kt:101-103`.

Fix: `TextRef.AndroidRes(R.string.x)` - the same thing the other 8 plugins in `:plugins:sync`
already do on `kmp` (`NSClientV3Plugin.kt:159`, `SmsCommunicatorPlugin.kt:148`, and so on).

### 3.2 `import app.aaps.core.keys.R` is gone

Wave 12 removed **all 32** importers of that class across the whole repository; zero remain on
`kmp`. One call site for us: `NfcBuildScreen.kt:108` and `:1083`.

Fix: `KeysStrings.units_min`, the generated `TextRef` name.

Note the asymmetry: `app.aaps.core.ui.R` **is** still read across module borders on `kmp`, so this is
an enforced rule for `:core:keys` specifically, not a general one.

### 3.3 `ElementType` label and description are `TextRef` now

`labelResId(): Int` became `label(): TextRef?`, and `descriptionResId(): Int` became
`description(): TextRef?`. Our two added branches in `ElementTypeStyle.kt` have to become
`UiStrings.nfccommands` and `UiStrings.description_nfc_communicator`.

### 3.4 Preference keys need a `TextRef` title

`BooleanKey` and `StringNonKey` now declare `override val title: TextRef` instead of
`titleResId: Int`. Our two `BooleanKey` entries become `title = KeysStrings.x`, and the strings have
to live in `core/keys/src/androidMain/res/values/strings.xml` so the generator emits the names.

### 3.5 `PreferenceActionItem` is ours, and it is Android shaped

This is a **new file we added** to `:core:ui`. It does not exist on `kmp` at all, and it carries
`@StringRes val titleResId: Int`, which means nothing off Android.

Fix: re-create it in `core/ui/src/commonMain/...` with `TextRef`, **plus a `titleResId` compat
constructor**, copying how `PreferenceSubScreenDef` solved the same problem on `kmp`. Two call sites
follow (`AdaptivePreferenceList.kt`, `PreferenceContentExtensions.kt`).

### 3.6 Source set paths moved under us

`core/data`, `core/interfaces`, `core/keys`, `core/ui`, `core/objects` and `plugins/main` all moved
`src/main` to `src/commonMain`, with resources going to `src/androidMain/res`. Our edits in those
modules come through as add/add or delete/modify merge conflicts, not clean merges.

The 20 files we touched outside the plugin, with their new home:

| Our file                                                          | Change            | Path on `kmp`                                                                    |
|-------------------------------------------------------------------|-------------------|----------------------------------------------------------------------------------|
| `core/data/.../ue/Sources.kt`                                     | +1 enum value     | `core/data/src/commonMain/...`                                                   |
| `core/interfaces/.../logging/LTag.kt`                             | +1 tag            | `core/interfaces/src/commonMain/...`                                             |
| `core/interfaces/.../navigation/ElementType.kt`                   | +1 enum value     | `core/interfaces/src/commonMain/...`                                             |
| `core/keys/.../BooleanKey.kt`                                     | +2 keys           | `core/keys/src/commonMain/...`                                                   |
| `core/keys/.../StringNonKey.kt`                                   | +2 keys           | `core/keys/src/commonMain/...`                                                   |
| `core/keys/src/main/res/values/strings.xml`                       | +2 strings        | `core/keys/src/androidMain/res/...`                                              |
| `core/objects/src/main/res/drawable/ic_nfc.xml`                   | new               | `core/objects/src/androidMain/res/...`                                           |
| `core/ui/.../icons/IcPluginNfc.kt`                                | new               | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../navigation/ElementColors.kt`                         | +1 color          | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../navigation/ElementTypeStyle.kt`                      | +4 branches       | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../preference/AdaptivePreferenceList.kt`                | +action item case | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../preference/PreferenceActionItem.kt`                  | new               | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../preference/PreferenceContentExtensions.kt`           | +action item case | `core/ui/src/commonMain/...`                                                     |
| `core/ui/src/main/res/values/strings.xml`                         | +3 strings        | `core/ui/src/androidMain/res/...`                                                |
| `plugins/main/src/main/res/*`                                     | drawables, colors | `plugins/main/src/androidMain/res/...`                                           |
| `plugins/main/.../di/PluginsModule.kt`                            | 1 line            | `app/src/main/kotlin/app/aaps/di/MainPluginsModule.kt` (moved to `:app` on `kmp`) |
| `database/impl/.../UserEntry.kt`                                  | +1 source         | unchanged (`src/main`)                                                           |
| `database/persistence/.../SourcesExtension.kt`                    | +2 lines          | unchanged (`src/main`)                                                           |
| `implementation/.../UserEntryPresentationHelperImpl.kt`            | +3 lines          | unchanged (`src/main`)                                                           |
| `app/**` (manifest, `nfc_tech_filter.xml`, `ComposeMainActivity`)  | NFC wiring        | unchanged - already correct                                                      |

---

## 4. Tier 2 - only needed if the module itself becomes multiplatform

| Item                                                                                                               | Sites                                                            | Notes                                                                                                                                                                                                                                                                                                                                                                                                          |
|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **All Dagger annotations must go**: `@Singleton`, `@Inject`, `NfcCommandsModule`, the `SyncPluginsListModule` entry | 2 classes plus 2 DI files                                        | Locked decision in `KMP_IOS_FEASIBILITY.md`: a module can be multiplatform only if it carries no Dagger annotation anywhere - not in `commonMain`, and **not in `androidMain` either**. Dagger emits Java, a KMP module has no `javac`, so the build passes while the factories silently do not exist. Wiring moves to `app/src/main/kotlin/app/aaps/di/`. **Needed either way - see section 6.** |
| **`org.json` to `kotlinx.serialization`**                                                                          | **18 files**                                                     | `params: JSONObject` sits in `NfcAction`'s public API *and* in `NfcTagStore.buildCommand()`, so it is viral, the same shape as the profile conversion. Everything needed is already in `commonMain`: `buildJsonObject`, `app.aaps.core.utils.lenient*` (`JsonLenientRead.kt`), and `OrgJsonCompat` in `:core:data`. Template commit: `9eb22e76a17`. **See section 5 for the risk.**                    |
| **`R.string` to `TextRef`**                                                                                        | **189 references in 31 files** (82 `rh.gs`, 63 `stringResource`) | Needs a `GenerateKeyStringsTask` in the module build file plus string owner registration, the pattern used by `:plugins:smoothing` and `:core:ui`                                                                                                                                                                                                                                                                |
| `androidx.annotation.StringRes` on `NfcAction.labelResId: Int`                                                      | **26 files**                                                     | becomes `TextRef`                                                                                                                                                                                                                                                                                                                                                                                              |
| `java.util.concurrent.TimeUnit.MINUTES.toMillis`                                                                    | 5                                                                | `T.mins(x).msecs()`. Wave 2 removed `TimeUnit` repo wide; the NFC branch put 5 back                                                                                                                                                                                                                                                                                                                             |
| `java.text.DateFormat`                                                                                             | 2, `NfcCommandsScreen.kt:246` and `:409`                         | `DateUtil` or kotlinx-datetime                                                                                                                                                                                                                                                                                                                                                                                 |
| `System.currentTimeMillis()`                                                                                       | 9                                                                | `kotlin.time.Clock.System.now().toEpochMilliseconds()`, the pattern in commit `b0922302841`                                                                                                                                                                                                                                                                                                                     |
| `"%02x".format(it)` in `NfcTagStore.tagUidHex`                                                                     | 1                                                                | `ByteArray.toHex()`, already in `:core:utils` `commonMain` (`HexByteArrayConversion.kt`)                                                                                                                                                                                                                                                                                                                        |
| `java.nio.charset.StandardCharsets`                                                                                | 1                                                                | `decodeToString()`                                                                                                                                                                                                                                                                                                                                                                                             |
| **`Context` injected into `NfcCommandsPlugin`**                                                                    | 1                                                                | It is used only for vibration and Toast. Drop both and the `Context` goes with them. Matches commit `b3bf3633524`, ":core:interfaces eliminate redundant Context parameters"                                                                                                                                                                                                                                     |
| `Toast` (3), `VibratorManager` / `VibrationEffect`, `Handler(Looper.getMainLooper())`                               | 5                                                                | Android only. Toast to Snackbar or notification per `CLAUDE.md`; vibration needs a small interface with an Android implementation                                                                                                                                                                                                                                                                               |
| **NFC hardware: `NfcAdapter`, `Tag`, `Ndef`, `NdefFormatable`, `NdefMessage`, `NdefRecord`**                        | 14 imports in 3 files                                            | The one item that is more than porting cost. See section 6                                                                                                                                                                                                                                                                                                                                                     |
| `NfcControlActivity` (`FragmentActivity` + `AndroidInjection`), `NfcForegroundDispatch` (`Activity`, `PendingIntent`) | 2 files, 300 lines                                             | Android entry points. They belong in `:app` or `androidMain`, the same move as commit `4957c26eb85`                                                                                                                                                                                                                                                                                                             |
| Tests                                                                                                              | 6 files, 1458 lines                                              | Plain JUnit 5 plus Mockito, no Robolectric and no Compose rule, so they move easily to `androidHostTest`. Note that `test-module-dependencies` applies `com.android.library` and therefore **cannot** be applied to a KMP module - the dependencies have to be written out by hand, as `:plugins:smoothing` does                                                                                                  |

---

## 5. A safety item that is not on anybody's list

The NDEF payload and the two `StringNonKey` preference blobs are a **stored format on physical
hardware that users already carry in their pocket**. Converting `org.json` to `kotlinx` changes
bytes.

This is not a theoretical worry. Wave 18 of the KMP work measured exactly this class of bug:

- `org.json.optInt` reads a numeric **string** through `Double.intValue()`, which saturates, but a
  real JSON **number** through `Long.intValue()`, which truncates. Reading both through
  `Long.toInt()` looks obviously right and turns `"1785992181588"` into `-714213548`.
- `org.json` writes a whole numbered double without its fraction (`1.0` becomes `1`), rejects
  non-finite doubles that kotlinx happily emits as invalid JSON, and accepts malformed input that
  kotlinx rejects.
- Android's `org.json` and Maven's `org.json:json` are **different implementations**: `optString` of
  a JSON null gives `"null"` on Android and `""` in the Crockford version. The right oracle for
  tests is `com.vaadin.external.google:android-json`, which is the AOSP implementation repackaged
  for the JVM.

Two concrete consequences for NFC:

1. **A tag written by today's build must still parse after the conversion.** That needs
   characterization tests over real tag payloads written *before* the JSON is touched, the same
   discipline the owner used for profiles (commits `d23b9be3f1a`, `74644c76675`, `419e949a718`).
2. **`NfcTagStore.loadLog()` currently reads with strict `getLong` / `getString` inside a blanket
   `catch (Exception)`.** Under kotlinx, one changed field turns the whole log into an empty list
   with no error and no log line. The lenient readers in `JsonLenientRead.kt` are the right
   replacement, field by field, not a wrapper around the whole loop.

---

## 6. The open question nobody has decided: does NFC belong in shared code at all?

This decision changes the cost of the migration by roughly ten times, and it has not been asked yet.

**The case for Android only:**

- iOS has Core NFC (`NFCNDEFReaderSession`, `NFCTagReaderSession`), but **there is no equivalent of
  Android's NDEF intent dispatch.** A tag scan cannot launch or wake the app. The whole "tap a tag
  and AAPS acts" model does not exist on iOS.
- The stated iOS goal in `KMP_IOS_FEASIBILITY.md` is a **follower client, not a master**. A follower
  does not deliver boluses, set temp basals or switch profiles, which is what every NFC action does.
- The scope note in that document says that pump drivers, automation, SMS, Garmin and wear never need
  to be KMP - although automation is now being converted anyway, so that list is not binding.

**What each answer costs:**

| Decision                                                                               | Work needed                                                                                                                                                                                              |
|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **NFC stays Android only** - it lives in `androidMain` when `:plugins:sync` is flipped  | Tier 1 plus Dagger removal, and nothing else. `org.json`, `R.string`, `@StringRes`, `TimeUnit` and `DateFormat` are all allowed in `androidMain`. **About 1 to 2 days.**                                   |
| **NFC goes to `commonMain`**                                                           | All of Tier 2: 186 string references, 18 JSON files, 26 `@StringRes` files, plus an `expect` / `actual` NFC seam whose iOS half cannot do the main job. **Weeks, with the wire format risk of section 5.** |

**Recommendation: ask the project owner before doing anything expensive.** The likely answer is
Android only, in which case the effort goes into Tier 1 plus Dagger, which is cheap and is useful
under either answer.

---

## 7. Verdict - is `kmp` ready enough to start?

**Yes for the preparation work, and it will not have to be redone. No for flipping the module.**

Why the foundation is stable:

- Every interface the NFC plugin touches is already in `commonMain` **in its final KMP shape**:
  `TextRef`, `TextResolver`, the Flow only `RxBus`, `PluginDescription`, `PluginBase`,
  `PreferenceSubScreenDef`, and the whole Compose kit. These are finished waves.
- `KMP_IOS_FEASIBILITY.md` has a section called "Decisions taken, so they are not re-opened by the
  next analysis", which deliberately locks the ones that matter here - the Dagger rule, the `TextRef`
  string strategy, and the rejection of compose-resources.
- The owner's method is **prepare in place, then flip**. `:plugins:automation`'s three newest commits
  - drop `HasAndroidInjector` (`a8ea386028f`), `org.json` to kotlinx (`9eb22e76a17`), move Android
  entry points to `:app` (`4957c26eb85`) - were **all made while automation was still a plain
  `src/main` Android module**. That is exactly the work the NFC plugin can do today, inside
  `:plugins:sync`, with no waiting at all.

What to wait for:

1. **The `kmp` to `dev` merge.** This is open decision 11 in the KMP note and its own follow-up
   number 1, described there as no longer a fast-forward and getting less free every day. Merging NFC
   into `kmp` before that happens means merging twice.
2. **The `:plugins:sync` flip.** Not started, not next. That is where the NFC hardware
   `expect` / `actual` question belongs, and section 6 suggests it may never need answering.

One small thing could still move under us: follow-up 3 in the KMP note is
`PluginDescription.description: Int` - the `-1` sentinel, set by 57 files - so `.description()` may
change shape once more. That is one line in `NfcCommandsPlugin.kt`.

---

## 8. Suggested order of work

| Step | Work                                                                                                                                                                                                            | Depends on           |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| 0    | **Ask the owner the section 6 question** - is NFC ever meant to be shared code?                                                                                                                                  | -                    |
| 1    | Write characterization tests over real tag payloads and stored blobs (section 5), against the current `org.json` code.                                                                                            | -                    |
| 2    | Move Dagger wiring out of `:plugins:sync` into `app/src/main/kotlin/app/aaps/di/NfcModule.kt`, plain constructors.                                                                                                | -                    |
| 3    | Wait for `kmp` to reach `dev`, then merge and apply Tier 1 (section 3).                                                                                                                                          | follow-up 1 on `kmp` |
| 4    | Only if the answer to step 0 is "shared": Tier 2, in the owner's own order - `TimeUnit` / `DateFormat` / `Clock` / hex first, then `org.json`, then `R.string` to `TextRef`, then move the Android entry points.   | step 0, step 1       |
| 5    | Only when `:plugins:sync` is flipped: decide the NFC hardware seam.                                                                                                                                              | step 4               |

Steps 1 and 2 can start today, and nothing on `kmp` can invalidate them.

---

## Appendix - measured numbers

All counts are from the working tree at `3a902e974fc` and from `Nightscout/kmp` at `4957c26eb85`,
build folders excluded.

| What                                       | Count                                                                           |
|--------------------------------------------|---------------------------------------------------------------------------------|
| NFC main files                             | 34                                                                              |
| NFC main lines                             | 4413                                                                            |
| NFC test files / lines                     | 6 / 1458                                                                        |
| Files we touched outside `nfcCommands/`    | 20                                                                              |
| Files with `android.*` imports             | 4                                                                               |
| `android.*` import statements              | 28                                                                              |
| Files with `org.json`                      | 18                                                                              |
| `R.string.` references                     | 189                                                                             |
| Files with `R.string.`                     | 31                                                                              |
| `rh.gs(` calls                             | 82                                                                              |
| `stringResource(` calls                    | 63                                                                              |
| Files with `androidx.annotation.StringRes` | 26                                                                              |
| `System.currentTimeMillis()` sites         | 9                                                                               |
| `TimeUnit.` sites                          | 5                                                                               |
| Non-Compose `androidx` imports             | 8 (3 `lifecycle.compose`, 2 `activity.compose`, 3 `lifecycle` / `fragment.app`) |

Largest files: `NfcBuildScreen.kt` 1239, `NfcCommandsScreen.kt` 489, `NfcCommandsPlugin.kt` 321,
`NfcControlActivity.kt` 220, `NfcTagStore.kt` 171, `NfcCommonUi.kt` 142, `NfcCommandCode.kt` 118.

Useful `kmp` commits to copy from:

| Commit        | Why it matters for NFC                                                                                       |
|---------------|--------------------------------------------------------------------------------------------------------------|
| `4957c26eb85` | `:plugins:automation` move Android entry points to `:app`                                                     |
| `9eb22e76a17` | `:plugins:automation` `org.json` to kotlinx.serialization                                                    |
| `a8ea386028f` | `:plugins:automation` drop `HasAndroidInjector`, and the `ActionFactory` pattern that replaced the injector   |
| `981e21a6b0f` | `:plugins:smoothing` is fully multiplatform - the smallest complete example, including its `build.gradle.kts` |
| `450a3e6f7a0` | `PluginDescription` names plugins with `TextRef`                                                              |
| `a9c537a2652` | `TextResolver`, the part of `ResourceHelper` that shared code can use                                        |
| `b0922302841` | `Clock` instead of `System.currentTimeMillis`                                                                 |
| `b3bf3633524` | eliminate redundant `Context` parameters                                                                      |
| `8e928f30367` | shared `org.json` compat shim with parity tests                                                              |
| `6fdb924e6b1` | `:core:keys` String migration - the commit that removed the 32 `keys.R` importers                             |
