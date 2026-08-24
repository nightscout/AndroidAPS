# NFC plugin - migration to Kotlin Multiplatform

Written 2026-08-20, updated 2026-08-23. What the NFC Commands plugin needs in order to fit the
Kotlin Multiplatform refactoring going on in `Nightscout/kmp`, and to line up with the architecture
the other plugins already follow.

This note answers three questions:

1. What has to change in the NFC plugin to meet the KMP rules?
2. Is `Nightscout/kmp` far enough along to start, or would the work be thrown away?
3. Which parts of the plugin do not follow the project's own patterns, and should be fixed while we
   are in here anyway?

Companion documents: `_docs/NFC_COMMANDS.md` (what the plugin does) and
`_docs/KMP_IOS_FEASIBILITY.md` on the `kmp` branch (the KMP plan itself, wave by wave).

---

## 0. Status

Working branch: `nfc/new-nfc-plugin_kmp`. It exists in parallel to `nfc/new-nfc-plugin` so this
preparation work can happen without disturbing the main NFC line.

| Step                                                       | State                                                                       |
|------------------------------------------------------------|-----------------------------------------------------------------------------|
| Merge `Nightscout/kmp` into the branch                     | **done** - `966e187d8b`                                                      |
| Tier 1, the changes needed to compile at all (section 3)    | **done** - `9cd204b8da` "NFC Fix build after kmp merge"                        |
| Architecture alignment (section 9)                         | **9.1 done** - `f9e51ec06c`. 9.2 to 9.7 not started, and are in the plan      |
| Tier 2, needed only for a multiplatform module (section 4)  | **not started**, and may not be needed - see section 6                       |
| Build verification                                         | **green** - compile clean, 84 NFC tests pass                                  |
| `Nightscout/kmp` freshness                                 | last read 2026-08-25 at `b0d677f3c5`, **94 commits** past our merge - see 1a   |

Sections 3.7 to 3.12 are six breaks that only a compiler found, after an import-derived list had
missed them. The lesson is recorded there because it will repeat on the next merge: **grepping imports
finds moved packages, never changed signatures.** It took two rounds of discovery to find them all -
the first list came from imports and was wrong - so budget for more than one pass.

### Commit shape on this branch

The convention here is that a merge is allowed to land broken, and is followed by a **fix build**
commit - the branch already had `fcff744bbb4 "NFC Fix build"` before this work. The merge commit was
left exactly as git produced it and never amended; the two commits after it were reshaped once, before
anything was pushed, so that each one builds on its own:

| Commit        | What                                                        | Nature                                  |
|---------------|-------------------------------------------------------------|-----------------------------------------|
| `966e187d8b`  | the merge, with the 8 conflict resolutions                  | does **not** compile, and that is fine  |
| `9cd204b8da`  | every call site fix the merge forced - 11 files, +40 / -30   | mechanical, no design decisions, **builds** |
| `f9e51ec06c`  | section 9.1 alignment - 5 files, +46 / -68                   | the finding, self contained, **builds**  |

Keeping these apart matters for review: the fix-build commit only has to answer "did the merge really
force this?", and the alignment commit carries its own rationale. `f9e51ec06c` is also the one that
is cleanly cherry-pickable - see section 10.

---

## 1. Where the two branches stand

| Branch                           | HEAD          | Date       | Note                                     |
|----------------------------------|---------------|------------|------------------------------------------|
| `nfc/new-nfc-plugin_kmp` (ours)  | `a448af5ab6`  | 2026-08-24 | merged `kmp` at `4957c26eb8`              |
| `Nightscout/kmp`                 | `b0d677f3c5`  | 2026-08-25 | **94 commits newer** than what we merged  |
| `Nightscout/dev`                 | `283a184f60`  | 2026-08-25 | `kmp` has **not** landed here yet         |

Shared base: `7fc8205e9a7` ("Fix scenes expiration", on `dev`).

Checked on 2026-08-25, `Nightscout/kmp` had **94 new commits** since the one we merged. Two things
about that, both verified:

- **No rewrite.** `4957c26eb8` is still an ancestor of `Nightscout/kmp`, so our merge stays valid as
  ancestry and the re-merge will be incremental rather than a repeat of the 8 conflicts.
- **`kmp` is not in `dev` yet**, so the wait in the plan is still a wait.

`KMP_IOS_FEASIBILITY.md` is well behind its own branch: it stops at wave 18 (`:core:interfaces`),
while `kmp` has since converted `:core:objects`, `:core:graph`, `:core:ui`, `:core:utils`,
`:pump:virtual`, `:plugins:smoothing`, `:plugins:sensitivity`, `:plugins:calibration`,
`:plugins:main`, `:plugins:aps` and `:plugins:automation` - and has since started something bigger,
see section 1a.

---

## 1a. The Metro decision - this supersedes the Dagger guidance below

About 60 of those 94 commits are one thing: **Dagger is being removed from the whole project and
replaced by Metro.** Recorded on `kmp` in `_docs/DI_FRAMEWORK_COMPARISON.md` (`cb96f4540b` compares
Koin, kotlin-inject and Metro; `4383225133` records the decision) and tracked in
`_docs/METRO_MIGRATION_NEXT_STEPS.md`.

The decision, quoted: **"Metro, and the target end state is Dagger fully removed - not Metro alongside
Dagger."** The reasoning is that the expensive part is not converting modules but the **bridge** -
every hazard that cost real debugging time came from the two frameworks coexisting, and "a permanently
half-migrated tree keeps every one of those costs forever, which is the worst outcome available".

### What this changes for the NFC plugin

**1. Android entry points no longer have to move to `:app`.** This note used to say
`NfcControlActivity` and `NfcForegroundDispatch` belong in `:app`, following `4957c26eb85`. That is
**reversed**: `f41d1b223a` moved automation's entry points *back home* into `plugins/automation` and
deleted `app/src/main/kotlin/app/aaps/di/AutomationAndroidModule.kt`.

The reason matters, because this note had repeated the wrong half of it. Two supposed blockers, only
one real:

| Claim | Verdict |
|---|---|
| Dagger cannot wire a multiplatform module - it answers `@HiltWorker` and `@AssistedInject` with generated **Java**, and an AGP multiplatform library has no Java compile step | **the real blocker** |
| "Android classes cannot live in a multiplatform module" | **never true** - `androidMain` compiles against the Android SDK like any other source set |

Metro is a Kotlin compiler plugin and generates no Java, so blocker 1 is gone. Proven on `kmp` rather
than assumed: `RunningModeExpiryWorker` moved into `plugins/aps/src/androidMain`,
`:plugins:aps:compileKotlinIosArm64` still compiles, and the APK dex carries the class from the
multiplatform module.

Base classes make converting a one-word change, covering 84 sites (34 activities, 31 services, 19
receivers):

| dagger.android | Metro | where |
|---|---|---|
| `DaggerBroadcastReceiver` | `MetroBroadcastReceiver` | `:core:objects` androidMain |
| `DaggerService` | `MetroService` | `:core:objects` androidMain |
| `DaggerAppCompatActivity` | `MetroAppCompatActivity` | `:core:ui` androidMain |

So the NFC target becomes: `NfcControlActivity` extends `MetroAppCompatActivity` and **stays in
`:plugins:sync`**, with its manifest entry moving out of `app/src/main/AndroidManifest.xml` into the
plugin's own manifest - the shape `plugins/automation/src/main/AndroidManifest.xml` now has.

**2. "No Dagger annotation anywhere, wiring moves to `app/di/`" is superseded.** The replacement is
per-module Metro wiring, and `:plugins:sync` already has an example -
`plugins/sync/src/main/kotlin/app/aaps/plugins/sync/di/OpenHumansMetroBridge.kt` and
`OpenHumansMetroGraph.kt`.

**3. `:plugins:sync` is still an Android module** (`main` / `test` / `androidTest`) and is now
*mid-DI-migration*: 14 files touch Metro while 58 still use `javax.inject` and 28 still use Dagger. New
NFC code should keep Dagger for now, consistent with the rest of the module, and be converted with it.
`ActionFactory` in automation is still `@Singleton @Inject constructor` on `kmp` today.

**4. The old cost rule is dead.** `KMP_IOS_FEASIBILITY.md` said "a module's conversion cost is roughly
its Dagger count", because Dagger had to go before a module could be multiplatform. With Metro that is
no longer true - DI works in `commonMain`. The `:plugins:sync` Dagger count is a *migration* cost now,
not a *blocker*.

**5. A precondition gates the volume work**, which is why this has not reached `:plugins:sync` fully.
Removing Dagger means removing Hilt, and Hilt answers 294 `@ContributesAndroidInjector`, 42
`@HiltWorker`, 3 `@AndroidEntryPoint` and 77 `@HiltViewModel`. The note's instruction: "Prove the entry
points first."

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

**`:plugins:sync`, where the NFC plugin lives, is not converted and is not next in line.**

---

## 2. What is already fine

The NFC plugin makes 90 distinct `app.aaps.core.*` imports. Every one exists on `kmp`, and **all of
them are already in `commonMain`**, including the newest interfaces from our own recent work:

`SceneAutomationApi`, `SceneIconResolver`, `ClientControlActionDispatcher`, `WizardBolusExecutor`,
`BolusProgressData`, `ElementType`, `PluginBase`, `PluginBaseWithPreferences`, `PluginDescription`,
`PreferenceSubScreenDef`, `PumpCommunicationStatus`, `PumpActivityDialog`, `NumberInputRow`,
`QuickAddButtons`, `AapsTheme`, `LocalDateUtil`, `LocalConfig`, `LocalPreferences`,
`GlobalSnackbarHost`, `consumeOverscroll`, `DecimalFormatter`, `OrgJsonCompat`, `JsonLenientRead`.

The plugin has a clean split by file: of its 35 main files, **31 have zero `android.*` imports**. All
28 Android imports sit in **4 files** - `NfcBuildScreen`, `NfcCommandsPlugin`, `NfcControlActivity`,
`NfcForegroundDispatch`.

The Android entry points currently sit in `:app` - the manifest activity declaration,
`app/src/main/res/xml/nfc_tech_filter.xml` and the `ComposeMainActivity` hooks. That matched
`4957c26eb85` when it was written, and **section 1a reverses it**: under Metro they belong in
`:plugins:sync`, with the plugin owning its own manifest entry. So this is a small piece of work now
rather than something already correct.

Three compatibility seams survive the merge unchanged:

- `PreferenceSubScreenDef` kept a `titleResId: Int` constructor next to the new `title: TextRef` one.
- `ResourceHelper` still declares `gs(id: Int)` in `androidMain`, so all 82 `rh.gs(R.string.x)` calls
  still compile inside an Android module.
- `dagger.android` is still alive on `kmp` (329 files use `HasAndroidInjector`), so
  `NfcControlActivity`'s `AndroidInjection.inject(this)` is not broken.

---

## 3. Tier 1 - what breaks the moment `kmp` is merged (done, `9cd204b8da`)

All applied. **Six items were predicted from imports; three more only showed up when the merge
actually happened.** That last part is the lesson: an import-derived list cannot see signature
changes.

### 3.1 `PluginDescription` takes `TextRef` only

`pluginName`, `shortName` and `description` have no `Int` overload. Fixed with
`TextRef.AndroidRes(R.string.x)`, the same form the other 8 plugins in `:plugins:sync` use on `kmp`.

### 3.2 `import app.aaps.core.keys.R` is gone

Wave 12 removed all 32 importers repo wide; zero remain on `kmp`. Note the asymmetry:
`app.aaps.core.ui.R` **is** still read across module borders, so this is an enforced rule for
`:core:keys` specifically.

### 3.3 `ElementType` label and description are `TextRef`

`labelResId(): Int` became `label(): TextRef?`, `descriptionResId(): Int` became
`description(): TextRef?`. Our NFC branches now use `UiStrings.nfccommands` and
`UiStrings.description_nfc_communicator`.

### 3.4 Preference keys need a `TextRef` title

`BooleanKey` declares `override val title: TextRef`. Our two NFC keys use
`KeysStrings.pref_title_nfc_*`, with the strings in `core/keys/src/androidMain/res/values/strings.xml`
so the generator emits the names.

### 3.5 `PreferenceActionItem` - deleted, not migrated

Our own type, created by Jens (`2b139fd97c`, 2026-05-18). It duplicated a feature `:core:keys`
already had. The merge forced a choice - port it to `TextRef` or drop it - so `9cd204b8da` ported it to keep that
commit mechanical, and `f9e51ec06c` then removed it. See section 9.1.

### 3.6 Source set paths moved

`core/data`, `core/interfaces`, `core/keys`, `core/ui`, `core/objects` and `plugins/main` moved
`src/main` to `src/commonMain`, resources to `src/androidMain/res`.

### 3.7 NOT PREDICTED - `NumberInputRow.unitLabel` is now `TextRef?`, not `String`

**Six** call sites in `NfcBuildScreen`, not the one the import scan suggested. Two passed raw string
literals (`"%"`, `"mmol/l"` / `"mg/dl"`), which a `TextRef` cannot hold. All six now use
`UiStrings.units_*`. Values were checked before substituting, so the only visible change is
`mg/dl` -> `mg/dL` and `mmol/l` -> `mmol/L`, which now match the rest of the app.

### 3.8 NOT PREDICTED - `units_min` left `:core:keys`

Wave 11 moved it to `:core:ui`, so the fix is `UiStrings.units_min`, **not** `KeysStrings.units_min`
as first written here.

### 3.9 NOT PREDICTED - `profile_ins_units_per_hour` moved to `:core:interfaces`

So `CoreUiR.string.profile_ins_units_per_hour` would not have compiled either. Replaced with
`UiStrings.units_insulin_rate` ("U/h", identical text).

### 3.10 NOT PREDICTED - `PluginBase.rh` is a `TextResolver`, so `rh.gs(Int)` is gone

The single biggest one, and it was invisible to every check made before the first real build.
`PluginBase` declares `open val rh: TextResolver`, and `TextResolver.gs()` only takes a [TextRef]. The
resource id overloads live on `ResourceHelper` in `androidMain`. So inside the plugin - and through
`plugin.rh` in all 25 action files and the screens - every one of the 82 `rh.gs(R.string.x)` calls
stopped compiling.

The fix is one line, and it is what `SmsCommunicatorPlugin.kt:117` already does:

```kotlin
override val rh: ResourceHelper,   // narrows PluginBase.rh, which is only a TextResolver
```

A covariant override is legal because `ResourceHelper : TextResolver`. One line restored all 82 call
sites.

### 3.11 NOT PREDICTED - six shared strings moved to `:core:interfaces`

`bolus`, `carbs`, `format_carbs`, `loopsuspended`, `pump_disconnected` and `pumpsuspended` left
`:core:ui` in kmp commit `7b4adad4b42` ("Move 33 shared strings to :core:interfaces"). Nine call sites
in six files. Fixed with an `InterfacesR` alias import rather than switching to `InterfacesStrings`,
to keep the fix-build commit mechanical - Tier 2 converts all of it to `TextRef` anyway.

Worth doing on the next merge rather than waiting for the compiler: check every `CoreUiR.string.*`
name against `:core:ui`'s actual `strings.xml`, which finds all of them at once. **Scan `src/test` as
well as `src/main`** - checking only main is what made this take an extra build round.

### 3.12 NOT PREDICTED - three more signature changes

- `PumpCommunicationStatus`'s third parameter is now `rh: TextResolver`, not the activity, so
  `NfcControlActivity` passed `this` where `rh` belonged.
- `PumpActivityDialog.queueStatus` is `AnnotatedString?`. The call site had `queueStatus ?: ""`, which
  widened it to `CharSequence`. The parameter is already nullable, so the elvis was simply wrong.
- `ActivePlugin.getSpecificPluginsListByInterface` takes `KClass<*>`, so `NsClient::class.java` became
  `NsClient::class`.

The `NfcForegroundDispatch` uses of `::class.java` are Android framework APIs - `getSystemService` and
`Intent(Context, Class)` - and correctly stay as they are.

### The 20 files we touched outside the plugin

| Our file                                                          | Change            | Path on `kmp`                                                                    |
|-------------------------------------------------------------------|-------------------|----------------------------------------------------------------------------------|
| `core/data/.../ue/Sources.kt`                                     | +1 enum value     | `core/data/src/commonMain/...`                                                   |
| `core/interfaces/.../logging/LTag.kt`                             | +1 tag            | `core/interfaces/src/commonMain/...`                                             |
| `core/interfaces/.../navigation/ElementType.kt`                   | +1 enum value     | `core/interfaces/src/commonMain/...`                                             |
| `core/keys/.../BooleanKey.kt`                                     | +2 keys           | `core/keys/src/commonMain/...`                                                   |
| `core/keys/.../StringNonKey.kt`                                   | +2 keys           | `core/keys/src/commonMain/...`                                                   |
| `core/keys/res/values/strings.xml`                                | +2 strings        | `core/keys/src/androidMain/res/...`                                              |
| `core/ui/.../icons/IcPluginNfc.kt`                                | new               | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../navigation/ElementColors.kt`                         | +1 color          | `core/ui/src/commonMain/...`                                                     |
| `core/ui/.../navigation/ElementTypeStyle.kt`                      | +4 branches       | `core/ui/src/commonMain/...`                                                     |
| `core/ui/res/values/strings.xml`                                  | +3 strings        | `core/ui/src/androidMain/res/...`                                                |
| `database/impl/.../UserEntry.kt`                                  | +1 source         | unchanged (`src/main`)                                                           |
| `database/persistence/.../SourcesExtension.kt`                    | +2 lines          | unchanged (`src/main`)                                                           |
| `implementation/.../UserEntryPresentationHelperImpl.kt`            | +3 lines          | unchanged (`src/main`)                                                           |
| `app/**` (manifest, `nfc_tech_filter.xml`, `ComposeMainActivity`)  | NFC wiring        | unchanged - already correct                                                      |

Removed during the merge, all created by Jens at the start of the NFC work, absent from `dev` and
`kmp`, and referenced nowhere: `plugins/main/res/values/colors.xml` (an empty file), and the
drawables `ic_nfc.xml`, `ic_add_box.xml`, `ic_info_outline.xml`. Also dropped: an unused
`sh.calvin.reorderable` dependency in `plugins/main/build.gradle.kts` and a
`plugins/main/.../di/PluginsModule.kt` edit that was only a missing newline.

### After section 9.1, our whole `:core:ui` footprint is

4 files, +81 lines, all of it `ElementType.NFC` registration:
`IcPluginNfc.kt`, `ElementColors.kt`, `ElementTypeStyle.kt`, `androidMain/res/values/strings.xml`.
This cannot move: registering an `ElementType` means touching the registry, and the registry
(`color()`, `icon()`, `label()`, `description()`) lives in `:core:ui`.

---

## 4. Tier 2 - only needed if the module itself becomes multiplatform

| Item                                                                        | Sites                                     | Notes                                                                                                                                                                                                                                              |
|-----------------------------------------------------------------------------|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **All Dagger annotations must go**                                          | 2 classes, 2 DI files, **plus section 9.1** | **SUPERSEDED - see section 1a.** The old rule was "no Dagger annotation anywhere, wiring moves to `app/di/`". The project is moving to **Metro**, which generates no Java and works in `commonMain`, so wiring stays in the module. What remains is a Dagger-to-Metro conversion, done with the rest of `:plugins:sync`. **Needed either way - see section 6.** |
| **`org.json` to `kotlinx.serialization`**                                   | **18 files**                              | `params: JSONObject` is in `NfcAction`'s public API and in `NfcTagStore.buildCommand()`, so it is viral. Helpers already in `commonMain`: `buildJsonObject`, `app.aaps.core.utils.lenient*` (`JsonLenientRead.kt`), `OrgJsonCompat` in `:core:data`. Template commit: `9eb22e76a17`. **See section 5 for the risk.** |
| **`R.string` to `TextRef`**                                                 | **185 refs in 31 files** (82 `rh.gs`, 59 `stringResource`) | Needs a `GenerateKeyStringsTask` plus string owner registration in the module build file, as `:plugins:smoothing` does                                                                                                                        |
| `androidx.annotation.StringRes` on `NfcAction.labelResId: Int`              | **26 files**                              | becomes `TextRef`                                                                                                                                                                                                                                  |
| `java.util.concurrent.TimeUnit.MINUTES.toMillis`                            | 5                                         | `T.mins(x).msecs()`. Wave 2 removed `TimeUnit` repo wide; the NFC branch put 5 back                                                                                                                                                                 |
| `java.text.DateFormat`                                                      | 2 in `NfcCommandsScreen`                  | `DateUtil` or kotlinx-datetime                                                                                                                                                                                                                     |
| `System.currentTimeMillis()`                                                | 9                                         | `kotlin.time.Clock.System.now().toEpochMilliseconds()`, pattern in commit `b0922302841`                                                                                                                                                             |
| `"%02x".format(it)` in `NfcTagStore.tagUidHex`                              | 1                                         | `ByteArray.toHex()`, already in `:core:utils` `commonMain`                                                                                                                                                                                          |
| `java.nio.charset.StandardCharsets`                                         | 1                                         | `decodeToString()`                                                                                                                                                                                                                                 |
| **`Context` injected into `NfcCommandsPlugin`**                             | 1                                         | Used only for vibration and Toast. Drop both and the `Context` goes with them. Matches commit `b3bf3633524`                                                                                                                                          |
| `Toast` (3), `VibratorManager` / `VibrationEffect`, `Handler(Looper...)`     | 5                                         | Android only. See section 9.5                                                                                                                                                                                                                      |
| **NFC hardware: `NfcAdapter`, `Tag`, `Ndef`, `NdefFormatable`, `NdefMessage`, `NdefRecord`** | 14 imports in 3 files     | The one item that is more than porting cost. See section 6                                                                                                                                                                                         |
| `NfcControlActivity`, `NfcForegroundDispatch`                                | 2 files, 300 lines                        | **REVERSED - see section 1a.** They stay in `:plugins:sync`; `NfcControlActivity` extends `MetroAppCompatActivity` and the plugin owns its manifest entry                                                                                            |
| Tests                                                                       | 6 files, 1458 lines                       | Plain JUnit 5 plus Mockito, no Robolectric, so they move easily to `androidHostTest`. Note `test-module-dependencies` applies `com.android.library` and **cannot** be applied to a KMP module - write the dependencies out by hand, as `:plugins:smoothing` does |

---

## 5. A safety item that is not on anybody's list

The NDEF payload and the two `StringNonKey` preference blobs are a **stored format on physical
hardware users already carry**. Converting `org.json` to `kotlinx` changes bytes. Wave 18 measured
exactly this class of bug:

- `org.json.optInt` reads a numeric **string** through `Double.intValue()` (saturates) but a real
  JSON **number** through `Long.intValue()` (truncates). Reading both through `Long.toInt()` looks
  right and turns `"1785992181588"` into `-714213548`.
- `org.json` writes a whole numbered double without its fraction (`1.0` -> `1`), rejects non-finite
  doubles that kotlinx emits as invalid JSON, and accepts malformed input kotlinx rejects.
- Android's `org.json` and Maven's `org.json:json` are **different implementations**: `optString` of
  a JSON null gives `"null"` on Android and `""` in the Crockford version. The right test oracle is
  `com.vaadin.external.google:android-json`, the AOSP implementation repackaged for the JVM.

Two consequences for NFC:

1. **A tag written by today's build must still parse after the conversion.** Write characterization
   tests over real tag payloads *before* touching the JSON, as was done for profiles (`d23b9be3f1a`,
   `74644c76675`, `419e949a718`).
2. **`NfcTagStore.loadLog()` reads with strict `getLong` / `getString` inside a blanket
   `catch (Exception)`.** Under kotlinx one changed field turns the whole log into an empty list with
   no error and no log line. Use the lenient readers field by field, not a wrapper around the loop.

---

## 6. The open question: does NFC belong in shared code at all?

This changes the cost by roughly ten times and has not been decided.

**The case for Android only:**

- iOS has Core NFC (`NFCNDEFReaderSession`, `NFCTagReaderSession`) but **no equivalent of Android's
  NDEF intent dispatch**. A tag scan cannot launch or wake the app. The "tap a tag and AAPS acts"
  model does not exist on iOS.
- The stated iOS goal is a **follower client, not a master**. A follower does not deliver boluses,
  set temp basals or switch profiles - which is what every NFC action does.

| Decision                                                                              | Work needed                                                                                                                                                                                    |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **NFC stays Android only** - lives in `androidMain` when `:plugins:sync` is flipped   | Tier 1 (done) plus the Dagger work, and nothing else. `org.json`, `R.string`, `@StringRes`, `TimeUnit`, `DateFormat` are all allowed in `androidMain`. **1 to 2 days.**                          |
| **NFC goes to `commonMain`**                                                          | All of Tier 2: 185 string refs, 18 JSON files, 26 `@StringRes` files, plus an `expect` / `actual` NFC seam whose iOS half cannot do the main job. **Weeks, with the wire format risk of section 5.** |

**Ask the project owner before doing anything expensive.** Note that the section 9 alignment work is
worth doing under *either* answer, and 9.1 is a prerequisite for the Dagger work in both.

---

## 7. Verdict - is `kmp` ready enough?

**Yes for preparation, and the work is not thrown away. Do not flip the module.**

- Every interface the plugin touches is already in `commonMain` **in its final KMP shape**: `TextRef`,
  `TextResolver`, the Flow only `RxBus`, `PluginDescription`, `PluginBase`, `PreferenceSubScreenDef`,
  the Compose kit.
- `KMP_IOS_FEASIBILITY.md` has a section "Decisions taken, so they are not re-opened by the next
  analysis" which locks the ones that matter here - the Dagger rule, the `TextRef` string strategy,
  the rejection of compose-resources.
- The owner's method is **prepare in place, then flip**. `:plugins:automation`'s three newest commits
  (`a8ea386028f` drop `HasAndroidInjector`, `9eb22e76a17` org.json to kotlinx, `4957c26eb85` move
  Android entry points to `:app`) were **all made while automation was still a plain `src/main`
  Android module**.

Still to wait for:

1. **The `kmp` to `dev` merge** - open decision 11 in the KMP note, and its follow-up 1.
2. **The `:plugins:sync` flip** - not started, not next. That is where the NFC hardware
   `expect` / `actual` question belongs, and section 6 suggests it may never need answering.

One thing may still move: follow-up 3 in the KMP note is `PluginDescription.description: Int`, the
`-1` sentinel set by 57 files. That is one line in `NfcCommandsPlugin.kt`.

---

## 8. Working plan

| Step | Work                                                                                                                                                             | Depends on           |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| 0    | **Ask the owner the section 6 question** - is NFC ever meant to be shared code?                                                                                    | -                    |
| 1    | **Section 9.2** - replace the plugin god object with an `NfcActionFactory`. Biggest item, and a prerequisite for the Dagger work under either answer to step 0.     | -                    |
| 2    | **Section 9.3, 9.4, 9.6** - explicit imports, a `compose/` package with a state holder, typed action state.                                                        | -                    |
| 3    | Write characterization tests over real tag payloads and stored blobs (section 5), against the current `org.json` code.                                              | -                    |
| 4    | Convert the NFC plugin's DI from Dagger to **Metro**, with the rest of `:plugins:sync`, and move `NfcControlActivity` plus its manifest entry back into the plugin. See section 1a. | step 1, and `kmp` reaching `:plugins:sync` |
| 5    | Wait for `kmp` to reach `dev`, then re-merge. Tier 1 is already applied; expect a smaller conflict set.                                                             | follow-up 1 on `kmp` |
| 6    | Only if step 0 says "shared": Tier 2, in the owner's own order - `TimeUnit` / `DateFormat` / `Clock` / hex first, then `org.json`, then `R.string` to `TextRef`, then move the Android entry points. | steps 0, 3     |
| 7    | Only when `:plugins:sync` is flipped: decide the NFC hardware seam.                                                                                                | step 6               |

Steps 1, 2 and 3 are worth doing whatever the answer to step 0, and nothing on `kmp` can invalidate
them.

Where the rest of section 9 lands: **9.1 is done** (`f9e51ec06c`). **9.5** (Toast, vibration,
`Handler`) rides with dropping the injected `Context` in step 6, since those three are the only reason
the plugin needs one. **9.7** (`org.json` as Compose state) is the same work as the `org.json`
conversion in step 6, so it is not a separate job - but it stays listed because it is also a pattern
difference from Automation, which keeps typed editor state and serialises only at the boundary.

---

## 9. Architecture alignment - where the plugin does not follow the project's own patterns

Reference plugins: **`:plugins:automation`** (closest in shape - actions, JSON, an editor UI) and
**`smsCommunicator`** / **`xdrip`** in `:plugins:sync` (preference screens). The NFC UI was already
reworked once to be closer to Automation; these are what is left.

### 9.1 `PreferenceActionItem` duplicated `PreferenceType.CLICK` - FIXED

`:core:keys` already had this, and it was on `dev` at our merge base, so it was available all along:

```kotlin
enum class PreferenceType { …, CLICK, … }   // "clickable preference that invokes onClick"

interface IntentPreferenceKey : PreferenceKey {
    val onClick: (() -> Unit)?          get() = null
    val confirmationMessage: TextRef?   get() = null   // built-in OK/Cancel dialog
}
fun IntentPreferenceKey.withClick(onClick: () -> Unit): IntentPreferenceKey
```

The plugin-local pattern was already used **twice inside `:plugins:sync`** - `SmsIntentKey` and
`XdripIntentKey`, both plain enums in the plugin module. They work because `IntentPreferenceKey`
extends `PreferenceKey`, a type `:core:ui` already renders; dispatch is on `preferenceType`.

**Why the old design forced a `:core:ui` change:** `PreferenceItem` is a bare marker interface in
`:core:keys`, so the renderers in `:core:ui` dispatch by type test. `:core:ui` depends on
`:core:data`, `:core:interfaces`, `:core:keys` and **no `:plugins:*` module**, so a new top-level
`PreferenceItem` type can never live in a plugin. Implementing an existing interface avoids the whole
problem.

**Fixed in `f9e51ec06c`** by deleting `PreferenceActionItem`, reverting `AdaptivePreferenceList.kt`
and `PreferenceContentExtensions.kt` to byte-identical with `kmp`, and adding
`nfcCommands/keys/NfcIntentKey.kt` mirroring `XdripIntentKey`. The preference key string
`"nfccommunicator_clear_log"` was kept, and `confirmationMessage` deliberately left off so behaviour
is unchanged - though a destructive action arguably should confirm, which is a separate decision.

Two side effects worth recording: 2 of the 4 conflicts the next `kmp` merge would raise in `:core:ui`
are gone, and our whole `:core:ui` footprint is down to the 4 files in section 3 - all of it
`ElementType.NFC` registration, none of it removable.

### 9.2 Actions receive the whole plugin - 261 reach-through accesses

`NfcAction(protected val plugin: NfcCommandsPlugin)`, and the 25 actions reach through it:

```
71x plugin.rh          44x plugin.profileUtil    18x plugin.preferences
17x plugin.decimalFormatter  16x plugin.loop     15x plugin.aapsLogger
14x plugin.profileFunction   13x plugin.dateUtil  9x plugin.commandQueue
 7x plugin.persistenceLayer   6x plugin.pumpBasalDurationStep  5x plugin.sceneAutomationApi
 5x plugin.constraintChecker  4x plugin.roundUpToStep
```

This is the pattern Milos **just removed** from Automation. `ActionFactory`'s own KDoc says why:

> They used to be handed a `HasAndroidInjector` and inject themselves, which needed a generated
> members injector - Java, and therefore impossible in a multiplatform module - and hid each action's
> real dependencies behind `@Inject lateinit`. This holds the dependencies instead and passes each
> action exactly what it asks for.

The NFC variant is worse in one respect: `NfcCommandsPlugin` is a Dagger `@Singleton`, so **every
action transitively depends on the DI graph**. No NFC action can reach `commonMain` until this is
undone, which puts it **on the critical path**, not in the cosmetic pile.

Fix: an `NfcActionFactory` mirroring `plugins/automation/.../actions/ActionFactory.kt` - hold the
dependencies, pass each action exactly what it asks for.

Note the justification shifted with section 1a. The original reason was "Dagger cannot work in a
multiplatform module", which Metro removes. The factory is still right for the reason Automation's
own KDoc gives: actions are built from **stored JSON**, so they can never be constructed by a DI
graph, and reaching back through a god object hides what each action actually depends on. It should
be written with Dagger today - `@Singleton @Inject constructor`, as `ActionFactory` still is - and
converted to Metro with the module.

### 9.3 Two wildcard imports

```
NfcCommandCode.kt:4      import app.aaps.plugins.sync.nfcCommands.actions.*
NfcCommandsPlugin.kt:46  import app.aaps.plugins.sync.nfcCommands.actions.*
```

`CLAUDE.md`: *"Always use explicit imports (no exceptions)."* Will need doing anyway with 9.2.

### 9.4 No `compose/` package and no state holder

Automation has `compose/` containing `AutomationState.kt`, `AutomationStateHolder.kt`,
`AutomationComposeContent.kt`, the screens, and `compose/{actions,triggers,elements}/`. NFC keeps
**1728 lines** of Compose (`NfcBuildScreen` 1239, `NfcCommandsScreen` 489) flat in the plugin root,
with state inline in the composables. `NfcBuildScreen` is the largest file in the plugin by 2.5x.

Fix: mirror Automation's layout - a `compose/` package, state hoisted into a state holder.

### 9.5 Toast, vibration and `Handler(Looper.getMainLooper())`

`CLAUDE.md` wants Snackbar or Android notifications rather than Toast. Also 3 Toast sites,
`VibratorManager`, and a `Handler` post - together these are the only reason `NfcCommandsPlugin`
needs a `Context` at all (Tier 2).

### 9.6 `Any`-typed action state

```kotlin
fun setActionState(key: String, state: Any) { actionStates[key] = state }
fun getActionState(key: String): Any? = actionStates[key]
```

`CLAUDE.md`: prefer specific types over `Any?`. This is the Bolus Wizard calculation hand-off between
phases; a small sealed type would express it and remove the unchecked casts at the read sites.

### 9.7 `org.json` as Compose UI state

`NfcAction.params: JSONObject by mutableStateOf(JSONObject())`. Automation keeps typed `elements/`
for editor state and serialises only at the storage boundary. Overlaps with Tier 2 but is also a
pattern difference in its own right.

---

## 10. Git history and authorship

**Jens Heuschkel has 16 commits on this branch, not 4.** He committed under two different author
names with the same email, so a case-sensitive search silently misses 12 of them:

| Author name      | Email                       | Commits         |
|------------------|-----------------------------|-----------------|
| `Jens Heuschkel` | `jens.heuschkel@h-da.de`    | 4 (April 2026)  |
| `jens.heuschkel` | `jens.heuschkel@h-da.de`    | 12 (May 2026)   |

Correct way to list them:

```
git log -i --author=heuschkel --format="%h %an <%ae> %s" nfc/new-nfc-plugin_kmp
```

Expect **16**. The email is the same either way, so forge attribution is already correct; only the
displayed name differs. A `.mailmap` at the repo root would unify the display without rewriting
history.

**Rules that follow from this:**

- **Never squash across Jens's commits.** `git rebase -i` with `squash` / `fixup` keeps only the first
  commit's author and silently drops the rest. His 16 span April to May and are interleaved with
  Philoul's work, so a squash-rebase would erase a lot of attribution.
- If any collapsing is ever unavoidable, add
  `Co-authored-by: Jens Heuschkel <jens.heuschkel@h-da.de>` trailers.

### Merge, not rebase

Decided and acted on. Reasons, in order of weight:

1. **1474 renames** between the shared base and `kmp` (2453 files changed). A rebase replays each of
   our commits against the relocated tree, so the same rename conflict is resolved once per commit; a
   merge resolves it once.
2. `kmp` is a moving target - a rebase is a one-shot alignment to a tip that has already moved.
3. `origin/nfc/new-nfc-plugin` is published, so a rebase means force-pushing.
4. It matches the project's convention: the branch already carries five
   `Merge remote-tracking branch 'Nightscout/dev'` commits, and Jens's own history has
   `Merge remote-tracking branch 'upstream/dev'`.
5. Rebase preserves the **author** but rewrites the **committer** and mints new SHAs. Jens's 4 April
   commits already exist in **three** copies from earlier rebases (`a0668b94d5` original on
   `juehv/patch-new-plugin-nfc_commands`, `6c33a97bdd`, `5aa040f7c0`). Merging stops that
   proliferation.

**Cherry-picking, corrected:** the `:plugins:sync` work *is* cleanly cherry-pickable. The `:core:*`
work is **not** - those are merge resolutions, not a portable diff, so the same conflicts reappear
when the main branch merges `dev` after `kmp` lands. The tool for that is `git rerere`, which records
resolutions and replays them on an identical conflict. It must be enabled **before** the merge to
record, so it did not capture this one:

```
git config rerere.enabled true
```

### Required git setup for this merge

| Setting              | Value      | Why                                                                                      |
|----------------------|------------|------------------------------------------------------------------------------------------|
| git version          | **>= 2.34** | `merge-ort` becomes the default engine; far better and faster on large rename sets        |
| `merge.renameLimit`  | 4000       | 1474 renames exceed the default (~1000). If truncated, renames degrade to add/delete pairs and **edits to a moved file are silently dropped** |
| `diff.renameLimit`   | 4000       | same                                                                                     |

With git 2.55 and those limits the merge produced **8 conflicts and no truncation warning**. Watch for
`inexact rename detection was skipped` on any future merge.

### Merge outcome, for reference

Commit `966e187d8b`, parents `52956a4654b` + `4957c26eb85`. Eight conflicts:

| File | Resolution |
|---|---|
| `core/keys BooleanKey.kt` (modify/delete) | our 2 NFC keys ported into the `commonMain` enum with `KeysStrings` titles; old path deleted |
| `core/ui ElementTypeStyle.kt` | took kmp's version, re-added the 4 NFC branches |
| `core/ui PreferenceActionItem.kt` (file location) | rewritten for `TextRef` here, then **deleted entirely** in `f9e51ec06c` - see 9.1 |
| `core/ui AdaptivePreferenceList.kt` | our render branch carried across here, reverted to byte-identical with kmp in `f9e51ec06c` - see 9.1 |
| `core/ui IcPluginNfc.kt` (file location) | accepted at the `commonMain` path |
| `plugins/main build.gradle.kts` | took kmp's version; our edit was an unused dependency |
| `plugins/main di/PluginsModule.kt` (modify/delete) | accepted the deletion; our edit was a missing newline |
| `plugins/main res/values/colors.xml` (file location) | deleted; the file was empty |

One trap worth remembering: **`PreferenceContentExtensions.kt` merged with no conflict while carrying
our Android-only `androidx.compose.ui.res.stringResource` import into a `commonMain` file.** Git had
no reason to flag it because kmp never touched those lines. A clean merge is not proof of a correct
merge - grep the `commonMain` source sets for `android` imports after any future merge.

---

## Appendix - measured numbers

Working tree after the merge and Tier 1, build folders excluded.

| What                                       | Count |
|--------------------------------------------|-------|
| NFC main files                             | 35    |
| NFC main lines                             | 4450  |
| NFC test files / lines                     | 6 / 1458 |
| Files touched outside `nfcCommands/`       | 20    |
| Our `:core:ui` footprint vs kmp            | 4 files, +81 lines |
| Files with `android.*` imports             | 4     |
| `android.*` import statements              | 28    |
| Files with `org.json`                      | 18    |
| `R.string.` references                     | 185   |
| Files with `R.string.`                     | 31    |
| `rh.gs(` calls                             | 82    |
| `stringResource(` calls                    | 59    |
| Files with `androidx.annotation.StringRes` | 26    |
| `System.currentTimeMillis()` sites         | 9     |
| `TimeUnit.` sites                          | 5     |
| `plugin.` reach-through accesses in actions | 261  |

Largest files: `NfcBuildScreen.kt` 1239, `NfcCommandsScreen.kt` 489, `NfcCommandsPlugin.kt` ~315,
`NfcControlActivity.kt` 220, `NfcTagStore.kt` 171, `NfcCommonUi.kt` 142, `NfcCommandCode.kt` 118.

### Reading build output on this Gradle version

Two traps that make a failing build look green. Both were hit in this work and produced wrong
"verified" claims before being caught:

- **Kotlin errors are not `e:` lines.** They come as `Problem found: Kotlin compiler error` blocks with
  the message and `Location:` on following lines. Grepping only `^e: ` returns 0 on a build with a
  dozen errors. Grep for both.
- **Test result XMLs survive a failed compile.** If `compileFullDebugUnitTestKotlin` fails,
  `build/test-results/` still holds the *previous* run, so the counts read like a pass. Check the file
  timestamps, or delete
  `plugins/sync/build/test-results/testFullDebugUnitTest/TEST-*.nfcCommands.*.xml` first.

Also use redirect, not pipe, for gradle output - a pipe reports the *pipe's* exit code, so a failed
build looks like it passed.

### Useful `kmp` commits to copy from

| Commit        | Why it matters for NFC                                                                                       |
|---------------|--------------------------------------------------------------------------------------------------------------|
| `a8ea386028f` | `:plugins:automation` drop `HasAndroidInjector`, and the `ActionFactory` pattern that replaced it - **the template for 9.2** |
| `4957c26eb85` | `:plugins:automation` move Android entry points to `:app`                                                     |
| `9eb22e76a17` | `:plugins:automation` `org.json` to kotlinx.serialization                                                    |
| `981e21a6b0f` | `:plugins:smoothing` is fully multiplatform - smallest complete example, including its `build.gradle.kts`      |
| `450a3e6f7a0` | `PluginDescription` names plugins with `TextRef`                                                              |
| `a9c537a2652` | `TextResolver`, the part of `ResourceHelper` shared code can use                                              |
| `b0922302841` | `Clock` instead of `System.currentTimeMillis`                                                                 |
| `b3bf3633524` | eliminate redundant `Context` parameters                                                                      |
| `8e928f30367` | shared `org.json` compat shim with parity tests                                                              |
| `6fdb924e6b1` | `:core:keys` String migration - removed the 32 `keys.R` importers                                             |

### Reference files for the alignment work

| Pattern | File |
|---|---|
| Action factory instead of a god object | `plugins/automation/src/main/kotlin/app/aaps/plugins/automation/actions/ActionFactory.kt` |
| Compose package and state holder | `plugins/automation/src/main/kotlin/app/aaps/plugins/automation/compose/` |
| Plugin-local clickable preference | `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/xdrip/keys/XdripIntentKey.kt` |
| Preference screen with a click row | `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/smsCommunicator/SmsCommunicatorPlugin.kt` (line ~1112) |
| KMP module build file | `plugins/smoothing/build.gradle.kts` |
