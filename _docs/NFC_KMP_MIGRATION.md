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
| Merge `Nightscout/kmp` into the branch                     | **done four times** - latest `66c9f75ca1`, the first with no conflicts at all  |
| Tier 1, the changes needed to compile at all (section 3)    | **done** - `9cd204b8da` "NFC Fix build after kmp merge"                        |
| Architecture alignment (section 9)                         | **9.1 to 9.4, 9.6 and 9.7 done**. Only 9.5 remains, and it is not standalone   |
| The command format and the store blobs (section 5)          | **done** - `28305e0af6` and `ff1916852f`. No `org.json` left in the plugin      |
| Metro DI and the plugin's own manifest (section 1a)         | **done** - `ba1cdbd518`, `009f4087ca`, `42ea9bbad2`. Metro owns the plugin now  |
| Tier 2, needed only for a multiplatform module (section 4)  | **not started** apart from the `org.json` row, which sections 5 and 9.7 did    |
| Build verification                                         | **green** - compile clean, 93 NFC tests and 53 app DI tests pass                |
| `Nightscout/kmp` freshness                                 | merged 2026-08-27 at `db8b2413e8`. Still not in `dev` - more merges are due     |

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
| `6e3d819ffd`  | section 9.2 - the action factory, 33 files, +834 / -317      | one architectural change, **builds**     |
| `1457342316`  | section 9.3 - the last wildcard import, 1 file               | mechanical, **builds**                   |
| `36e0326ba1`  | section 9.4a - screens into `compose/`, 6 files              | pure move, recorded as renames, **builds** |
| `115eef6756`  | section 9.4b - the build screen state holder, 2 files        | one refactor, **builds**                 |
| `67fd617b85`  | section 9.6 - typed wizard state, 4 files                    | removes an unchecked cast, **builds**    |
| `28305e0af6`  | section 5 - the typed command format, 38 files, +348 / -372   | the format redesign, **builds**          |
| `ff1916852f`  | section 5 - the store blobs off `org.json`, 4 files           | one reader rewrite, **builds**           |
| `7bdab8c8b5`  | **merge 2**, 109 upstream commits, 3 conflicts               | landed **almost** building - one error   |
| `ec7f230935`  | fix build after merge 2 - 1 file, one word                    | mechanical, **builds**                   |
| `ba1cdbd518`  | section 1a - Metro member injection, 6 files                  | the DI change, **builds**                |
| `009f4087ca`  | section 1a - manifest and resource into the plugin, 3 files    | pure move, **builds**                    |
| `2fed15e79b`  | **merge 3**, 56 upstream commits, 4 conflicts                 | leaves the plugin registered nowhere     |
| `42ea9bbad2`  | fix build after merge 3 - the plugin registers itself, 7 files | one DI change, **builds**                |
| `66c9f75ca1`  | **merge 4**, 29 upstream commits, no conflicts                | nothing to fix afterwards                |
| `846802a5ca`  | section 5 - a missing value is refused, 6 files                | behaviour change, **builds**             |
| `a1b8a842bb`  | section 9.8 - every default in one place, 14 files             | removes 15 invented values, **builds**   |

Keeping these apart matters for review: the fix-build commit only has to answer "did the merge really
force this?", and the alignment commit carries its own rationale. `f9e51ec06c` is also the one that
is cleanly cherry-pickable - see section 10.

---

## 1. Where the two branches stand

| Branch                           | HEAD          | Date       | Note                                      |
|----------------------------------|---------------|------------|-------------------------------------------|
| `nfc/new-nfc-plugin_kmp` (ours)  | -             | -          | merged `kmp` at `4957c26eb8`               |
| `Nightscout/kmp`                 | `c9ca405f39`  | 2026-08-26 | **109 commits newer** than what we merged  |
| `Nightscout/dev`                 | `283a184f60`  | 2026-08-25 | `kmp` has **not** landed here yet          |

Shared base: `7fc8205e9a7` ("Fix scenes expiration", on `dev`).

Checked on 2026-08-26, `Nightscout/kmp` had **109 new commits** since the one we merged, **71 of them
Dagger or Metro** work. Three things, all verified:

- **No rewrite.** `4957c26eb8` is still an ancestor of `Nightscout/kmp`, so our merge stays valid as
  ancestry and the re-merge will be incremental rather than a repeat of the 8 conflicts.
- **`kmp` is not in `dev` yet**, so the wait in the plan is still a wait.
- **`nfcCommands/` has never been touched upstream**, which is expected - the plugin is not there - and
  is why the section 9 and section 5 work has been conflict free.

`KMP_IOS_FEASIBILITY.md` is well behind its own branch: it stops at wave 18 (`:core:interfaces`),
while `kmp` has since converted `:core:objects`, `:core:graph`, `:core:ui`, `:core:utils`,
`:pump:virtual`, `:plugins:smoothing`, `:plugins:sensitivity`, `:plugins:calibration`,
`:plugins:main`, `:plugins:aps` and `:plugins:automation` - and has since started something bigger,
see section 1a.

---

## 1a. The Metro decision - this supersedes the Dagger guidance below

**71 of those 109 commits** are one thing: **Dagger is being removed from the whole project and
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

So the NFC target becomes: `NfcControlActivity` is Metro-injected and **stays in `:plugins:sync`**,
with its manifest entry moving out of `app/src/main/AndroidManifest.xml` into the plugin's own manifest -
the shape `plugins/automation/src/main/AndroidManifest.xml` now has.

**Done in `ba1cdbd518` and `009f4087ca`, and two of the predictions here were wrong.** Both are recorded
below because they are the kind of thing that repeats.

*It does not extend `MetroAppCompatActivity`.* The activity is declared with
`Theme.Translucent.NoTitleBar`, and an `AppCompatActivity` requires an AppCompat theme - the one-word
change would have compiled and then thrown at start up. It stays a `FragmentActivity` and calls
`injectMetroMembers(this)`, which is the whole body of those base classes anyway, and which
`MetroAndroidEntryPoints` already documents as the escape hatch. **The one-word rule holds only for an
activity whose theme allows AppCompat** - check the manifest before believing it.

**2. "No Dagger annotation anywhere, wiring moves to `app/di/`" is superseded.** The replacement is
per-module Metro wiring, and as of 2026-08-26 `:plugins:sync` has **the exact template `NfcControlActivity`
needs** - `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/di/SyncMemberInjectors.kt`:

```kotlin
@ContributesTo(AppScope::class)
@BindingContainer
object SyncMemberInjectors {
    @Provides @FeatureMemberInjectors @IntoMap @ClassKey(WearDataReceiver::class)
    fun bindWearDataReceiver(injector: MembersInjector<WearDataReceiver>): MembersInjector<*> = injector
}
```

Its own comment is the useful part: it *"contributes straight into the app root, so it needs no mention
in `:app` at all"*.

*"No `:app` change" was wrong too, and the reason is worth knowing before the next plugin is converted.*
The plugin list is **Dagger's**, and `NfcCommandsPlugin` is a `@Singleton` in it. The moment Metro
injected the activity, Metro built its **own second** `NfcCommandsPlugin`: the screen would have acted
on a different object than the plugin list holds, and the whole `NfcActionFactory` tree would have been
pulled into the root graph. The symptom was indirect - Metro asked for `SceneIconResolver`, a type only
`NfcActionFactory` needs and which only a Dagger `@Binds` in `:ui` provides.

The fix is to **hand the plugin over from Dagger** through `AapsLeaves`, exactly as `AuthFlowOut` and
`TidepoolUploader` already are for `AuthFlowIn`. One leaf entry, and Metro stops rebuilding the subtree -
the proof being that the `SceneIconResolver` request disappeared by itself. Note `CoreObjectsModule`
builds `AapsLeaves` **positionally**, so an inserted parameter silently shifts every later argument; the
compiler catches it as a wall of type mismatches. That leaf is the only `:app` mention the plugin has
left, and it goes away when Dagger does.

**The general rule: any Metro-injected class that reaches a Dagger-owned singleton must get it through
`AapsLeaves`, not let Metro construct it.** A missing binding error is the first sign, and it names a
type from deep inside the subtree rather than the singleton itself.

*The NFC leaf itself is gone again as of `42ea9bbad2`* - merge 3 deleted the Dagger module that made the
plugin Dagger-owned in the first place, so Metro owns it outright and there is nothing to hand over. The
rule above still holds wherever Dagger is still the owner; see the merge 3 notes in section 10 for what
replaced it, and for the scoping bug that came with it. Nine modules
now have a `*MemberInjectors.kt` of this shape, and `shared/tests` carries a `MemberInjectorCoverage`
helper that guards the maps with a test.

`OpenHumansMetroBridge.kt` / `OpenHumansMetroGraph.kt` in the same package are a *different* pattern -
a root graph of their own - and are not what NFC should copy.

**3. `:plugins:sync` is still an Android module** (`main` / `test` / `androidTest`) and is now
*mid-DI-migration*: many files touch Metro while most still use `javax.inject`. **The NFC plugin is now
on the Metro side of that line** - see the two commits above. `javax.inject` on a constructor is not the
issue and stays: Metro reads it through `includeDagger()`, and `ActionFactory` in automation is still
`@Singleton @Inject constructor` on `kmp` today. What went is `dagger.android`.

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
- `dagger.android` was still alive on `kmp` (329 files use `HasAndroidInjector`), so
  `NfcControlActivity`'s `AndroidInjection.inject(this)` was not broken by the merge. It has since been
  converted anyway, by choice rather than by need - see 1a.

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
| ~~**`org.json` to `kotlinx.serialization`**~~                                | ~~18 files~~                              | **DONE** - `28305e0af6` for the commands and `ff1916852f` for the store blobs. `params` is a typed `NfcParams`, not a `JSONObject`, and `NfcTagStore.buildCommand()` is gone. See section 5. |
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

## 5. The command format, and what "robust" means here

### There is no deployed data, and that is freedom

**The plugin has never been released.** Two or three people are testing it. There are no tags in the
wild, so nothing has to stay readable and no migration is owed to anyone. If a change invalidates a
test tag, the tester writes it again.

An earlier version of this section said the opposite - that the NDEF payload and the two
`StringNonKey` blobs were "a stored format on physical hardware users already carry" - and concluded
that characterization tests had to come before any JSON change. That reasoning was imported from the
profile conversion on `kmp` (`d23b9be3f1a`, `74644c76675`, `419e949a718`), where it is correct because
profiles really do live on thousands of phones and go to Nightscout. It does not hold here, and the
step it implied has been dropped from the plan.

What follows from that is the useful part: the format can be **designed** rather than preserved.

### The format has already been redesigned once

| Version | Shape | Fixed |
|---------|-------|-------|
| v1, Jens | `"BASAL_ABS 0.75 30"`, split on spaces | - |
| v2, Philoul `02c25ccc5b` "NFC Improve Plugin Architecture", 2026-06-14 | `{"code":…,"params":{…}}` | values containing spaces - a profile or scene name broke the v1 parser |
| v3, proposed | `@Serializable` data classes | untyped reads that silently return a default |

Each step removes the failure mode of the one before. **Do not let anyone "simplify" v2 back to a
delimited string** - that reintroduces the space bug that motivated the change.

### What is still worth fixing, and why it is not about history

Write and read are separated in time: a tag written today is scanned next week, possibly after an app
update. So the format must round-trip correctly for **any** tag, not only for old ones. Two defects,
both independent of whether anything shipped:

**1. Untyped reads with silent coercion.** Every parameter is read positionally against a string key:

```kotlin
val tempBasal = params.optDouble(NfcJsonKeys.RATE, 0.0)
val rawDuration = params.optInt(NfcJsonKeys.DURATION, durationStep)
```

A missing key, a mistyped key, or a value of the wrong type does not fail - it returns the default. A
mismatch between what the build screen writes and what the action reads produces a **silently wrong
dose**, not an error. Same class of problem as the `as?` cast that section 9.6 removed, spread across
21 string constants in `NfcJsonKeys`.

This is not a design oversight. It is the only idiom hand-written `org.json` offers; the limitation
belongs to the library.

**2. Errors swallowed wholesale.** `NfcTagStore.loadLog()` and `loadCreatedTags()` wrap strict
`getLong` / `getString` / `getBoolean` reads in one blanket `catch (Exception)` returning an empty
list. A single bad field discards the whole list, with no error and no log line - the user's tag list
or history simply appears empty.

### v3, the typed command model - DONE

```kotlin
@Serializable
data class NfcCommand(val code: NfcCommandCode, val params: NfcParams = NfcParams())
```

`NfcParams` carries `insulin`, `carbs`, `glucose`, `percent`, `duration`, `rate`, `profileName`,
`sceneId`, `isMeal`, and the five wizard toggles `useBg` / `useTt` / `useTrend` / `useIob` / `useCob`.
Values are nullable where "not set" has to be distinguishable from zero.

Keeps everything v2 bought - structure, arbitrary strings, nesting - and removes the silent default:
decoding now fails as a whole rather than filling in a default. `decode()` returns null instead of
throwing, because a tag is outside the app's control and a bad one must not take a screen down.

**Two fields changed meaning, not just type.**

- **`amount` was two things.** Insulin units (`Double`) for `BOLUS` and `EXTENDED_SET`, carbs grams
  (`Int`) for `CARBS` and `BOLUS_WIZARD` - read as `optDouble` in one pair and `optInt` in the other.
  `GenericNfcUiAction` already kept them apart as `units` and `grams`, so the shared key was only an
  artefact of flattening the UI state onto JSON. Now `insulin` and `carbs`.
- **The tag's name left the params.** It was copied into **every** command of a chain and read back at
  **25 sites**, purely to fill the user entry note, while `NfcCreatedTag.name` was the real source of
  truth. It is now a parameter: `execute(tagName)`.

`formatParams(tagName)` needed it too, which only the compiler found: the wizard bakes `notes` into
`WizardInputs` at *prepare* time inside `formatParams`, and `execute` commits by `bolusId` afterwards.
Leaving the name out would have silently emptied the note on every wizard bolus.

**Also removed:** `NfcJsonKeys` and its 21 constants, `ArgType`'s `jsonKey` indirection, and five hand
rolled `JSONObject(cmd) -> code + params` parses that are now one `NfcCommand.decode`. This resolves
section 9.7 - `NfcAction.params` was `org.json` held in Compose state - and the `org.json` row of
Tier 2.

**Build change:** `kotlin("plugin.serialization")` on `:plugins:sync`, applied the way `wear` does.
Note this diverges from the automation conversion on `kmp` (`9eb22e76a17`), which used the JSON DOM
API and kept lenient reads with defaults. The divergence is deliberate: the lenient reads are the
defect being removed.

### The store blobs - DONE, `ff1916852f`

`NfcTagStore` kept `org.json` for the **tag list** and **log** blobs, which are a different format from
commands. Both wrapped strict `getLong` / `getString` / `getBoolean` reads in one blanket
`catch (Exception)` returning an empty list, so a single bad field discarded the whole list with no
error and no log line - a user's tag list or history simply appearing empty.

`NfcCreatedTag` and `NfcLogEntry` are now `@Serializable`, which was one annotation each since both were
already data classes. **The reader is the actual fix.** Decoding a `List<T>` in one call would keep the
old all-or-nothing behaviour, so `loadList` parses the blob to a `JsonArray` and decodes **each element
on its own**: a bad entry is dropped and named through `LTag.NFC`, and every other entry survives. Only
a blob that is not an array at all yields an empty list, and that is logged too. `NfcTagStore` takes an
`AAPSLogger` for this, which is the only reason its construction sites changed.

Four tests cover it, including the case that matters in practice: a tag written before the typed format
is **still listed with its name and uid**, and only its command fails to decode. Two behaviours were
deliberately kept - entries with a blank uid or no non-blank command are still skipped, and the list is
still sorted newest first.

After this there is no `org.json` left anywhere in the plugin.

### A missing value is refused, not invented - DONE, `846802a5ca`

Every value a command must supply is nullable in `NfcParams`, where null means "not set". Nothing
checked that. Each action carried its own fallback - `params.insulin ?: 0.0`, `params.carbs ?: 0` - so a
command that had lost a field ran anyway, on a number nobody chose, with no error and nothing in the log.

**This is not only a development story, which is the part worth keeping.** The rename from the hand
written `org.json` keys to the `@Serializable` field names is indeed a one-off that users will never
meet. But a command can arrive without a value in ordinary use:

- **A preferences export restored into a build whose parameter names differ.** The tag list is
  exportable since `3a902e974f`, and restoring a backup or moving to a new phone is routine.
- **A tag written by another phone on another version.** A tag is a physical object that outlives an
  install, and `NDEF_DISCOVERED` reads the command from the tag itself.

In both, the unknown names are dropped when the command is decoded and what is left has no value. A
truncated or corrupt tag is different and was already safe: the whole command fails to decode.

Each action already declares what it needs in `argType`, so the check only had to read that list.
`NfcArgs.kt` holds the one mapping from `ArgType` to `NfcParams` field - a blank profile or scene name
counts as missing, and booleans never do, `false` and `true` both being real answers.
`NfcAction.executeIfComplete` runs an action only when nothing is missing, and `routeAction` calls it
instead of `execute`, so nothing can be run past the check by accident. The tag list marks such a command
**"value missing"** so it is visible before the tag is scanned.

**How this showed up in practice.** A tester's three wizard tags, written before the typed format,
carried their carb amount under `amount`. The field is `carbs` now, so the value was dropped, and the
build screen displayed **20 g** - its own invented default - while a scan would have calculated with
**0 g**. Neither number was ever recorded by anyone. That is what made the case for refusing rather
than filling in.

For reference, the `org.json` versus `kotlinx` differences that made the profile conversion delicate
still matter for that work, as things the new code must get right rather than stay compatible with:
`optInt` truncates a JSON number but saturates a numeric string; `org.json` writes `1.0` as `1`,
rejects non-finite doubles that kotlinx emits as invalid JSON, and accepts malformed input kotlinx
rejects.

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
| 1    | ~~The store blobs (section 5)~~ - **done**, `ff1916852f`.                                                                                                          | -                    |
| 2    | ~~Convert the NFC plugin's DI to Metro and move the activity's manifest entry into the plugin~~ - **done**, `ba1cdbd518` and `009f4087ca`. See 1a for the two predictions that were wrong. | -                    |
| 3    | Wait for `kmp` to reach `dev`, then merge again. Tier 1 is applied and two merges are behind us; expect a small conflict set in the same shared files.              | follow-up 1 on `kmp` |
| 4    | Only if step 0 says "shared": Tier 2, in the owner's own order - `TimeUnit` / `DateFormat` / `Clock` / hex first, then `R.string` to `TextRef`. The `org.json` row is already done. | step 0               |
| 5    | Only when `:plugins:sync` is flipped: decide the NFC hardware seam.                                                                                                | step 4               |

Step 1 is worth doing whatever the answer to step 0, and nothing on `kmp` can invalidate it.

Where the rest of section 9 lands: **9.1 to 9.4, 9.6 and 9.7 are done**. Only **9.5** remains and it is
not standalone - Toast, vibration and the `Handler` post are the only reason `NfcCommandsPlugin` still
takes a `Context`, so they go when it does, in step 4.

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

### 9.2 Actions received the whole plugin - 261 reach-through accesses - FIXED

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

**Fixed in `6e3d819ffd`** with an `NfcActionFactory` mirroring
`plugins/automation/.../actions/ActionFactory.kt` - it holds the dependencies and passes each action
exactly what it asks for. Verified: `:plugins:sync` compiles and all NFC tests pass. Afterwards
there are **zero** `plugin.x` accesses left in the actions package.

Note the justification shifted with section 1a. The reason recorded here first was "Dagger cannot work
in a multiplatform module", which Metro removes. The factory is still right for the reason
`ActionFactory`'s own KDoc gives: actions are built from **stored JSON**, so the set of them is only
known when a tag is read and they can never come out of a DI graph. It is written with Dagger
(`@Singleton @Inject constructor`, as `ActionFactory` still is) and converts to Metro with the module.

| Before | After |
|---|---|
| `NfcAction(plugin: NfcCommandsPlugin)` | `NfcAction(aapsLogger, rh, uel)` - the three every action uses |
| 261 `plugin.x` accesses in 24 action files | each action's own constructor, e.g. `LoopStopAction(loop, profileFunction)`; `BolusWizardAction` names nine |
| `NfcCommandCode` held `createAction: (NfcCommandsPlugin) -> NfcAction` | the enum names no action class at all |
| plugin held `lastRemoteBolusTime` and the action state map | `NfcRuntimeState` |
| plugin held `pumpBasalDurationStep()` / `roundUpToStep()` | `actions/NfcBasalStep.kt`, internal functions |
| tests reached through a mocked plugin | tests build a **real** `NfcActionFactory` over the same mocks |

Three of the reach-throughs were not dependencies at all but plugin state or behaviour, which is why
they needed homes of their own rather than a constructor parameter. The plugin keeps a thin
`pumpBasalDurationStep()` because `NfcBuildScreen` calls it.

Two things were deliberately left for their own sections: the action state map is still `Any` typed
(9.6) and `NfcCommandsPlugin` still wildcard-imports the actions package (9.3).

### 9.3 Two wildcard imports - FIXED

`CLAUDE.md`: *"Always use explicit imports (no exceptions)."* There were two, both
`import app.aaps.plugins.sync.nfcCommands.actions.*`. 9.2 removed the one in `NfcCommandCode.kt` by
taking the action mapping away from the enum; **`1457342316`** replaced the one in
`NfcCommandsPlugin.kt` with the two symbols it actually uses - `NfcAction` and
`pumpBasalDurationStep`. That it shrank to two is itself a check on 9.2: before the factory, the
plugin constructed all 24 action classes itself.

No wildcard imports remain in the plugin, in `src/main` or `src/test`.

### 9.4 UI files are not in a `compose/` package, and screen state is not hoisted

**This is about a folder name and about state hoisting - not about Compose itself.** An earlier
heading here read "No `compose/` package", which is easy to misread as "no Compose". The plugin is
thoroughly Compose: **19 of its 38 files** import `androidx.compose` and it declares **42
`@Composable` functions**. Nothing in the project is moving away from Compose - the V3 to V4 Compose
migration is precisely what makes a shared iOS UI plausible at all, because Compose Multiplatform
publishes `androidx.compose.*` under the same package names.

Two organisational gaps against Automation:

**A `compose/` sub-package.** Automation groups its UI in
`plugins/automation/.../automation/compose/` - `AutomationScreen.kt`, `AutomationEditScreen.kt`,
`AutomationComposeContent.kt`, `AutomationColors.kt`, plus `compose/{actions,triggers,elements}/` for
the per-element editors. NFC keeps `NfcBuildScreen.kt`, `NfcCommandsScreen.kt` and `NfcCommonUi.kt`
flat in the plugin root, beside `NfcTagStore.kt` and the plugin class. Tidiness only.

**A state holder.** Automation has `AutomationState.kt` and `AutomationStateHolder.kt`, so screen
state lives in a plain class the UI observes. NFC declares its state inline:

```kotlin
val chain = remember { mutableStateListOf<NfcUiAction>() }
var tagName by remember { mutableStateOf("") }
var isWritingMode by remember { mutableStateOf(false) }
```

That is ordinary, idiomatic Compose. The objection is scale, not technique: `NfcBuildScreen.kt` is
**1239 lines** - 2.5x the next largest file in the plugin - holding a dozen pieces of state plus the
write, read and edit flows in one composable. Hoisting makes it testable without a UI.

**Fixed in two commits.**

`36e0326ba1` moved `NfcBuildScreen.kt`, `NfcCommandsScreen.kt` and `NfcCommonUi.kt` into
`nfcCommands/compose/`, so the plugin now reads `actions/`, `compose/`, `keys/` with the plumbing at
the root. Git recorded all three as renames, which is the reason it was kept apart from the state
work. The boundary is narrow both ways: only four symbols leave `compose/` -
`NfcCommandsComposeContent`, `NfcExecutionConfirmationDialog`, and `WriteOutcome` plus
`resolveWriteOutcome` for a test.

`115eef6756` added `compose/NfcBuildStateHolder.kt` holding all twelve values plus `currentCommands`
and `isDirty`, so twenty lines of declarations became `val state = rememberNfcBuildState()` and 65
references now go through it. `isEditMode` stayed in the composable because it derives from a
parameter, not from remembered state.

Worth knowing if this is ever repeated elsewhere: three **named arguments** shared a name with hoisted
state - `tagName` in the two `NfcLogEntry` calls and `chain` in the `NfcWriteDialog` call - so a plain
rename would have produced `state.tagName = name` *inside a constructor call*. `NfcWriteDialog` also
declares its own `chain` parameter outside the composable. The rename had to be scoped to the function
body and those three sites left bare.

### 9.5 Toast, vibration and `Handler(Looper.getMainLooper())`

`CLAUDE.md` wants Snackbar or Android notifications rather than Toast. Also 3 Toast sites,
`VibratorManager`, and a `Handler` post - together these are the only reason `NfcCommandsPlugin`
needs a `Context` at all (Tier 2).

### 9.6 `Any`-typed action state - FIXED

```kotlin
- fun setActionState(key: String, state: Any)
- fun getActionState(key: String): Any?
+ fun setWizardPreview(key: String, preview: WizardBolusExecutor.PrepareResult.Preview)
+ fun getWizardPreview(key: String): WizardBolusExecutor.PrepareResult.Preview?
```

**Fixed in `67fd617b85`.** `CLAUDE.md` asks for specific types over `Any`, but the **cast mattered more
than the type**. The single reader did
`getActionState(...) as? WizardBolusExecutor.PrepareResult.Preview`, and `as?` yields `null` on a
mismatch rather than failing. `BolusWizardAction` reads `null` as "state not found" and aborts with
*"Remote command is not possible"* - so a wrongly typed value parked under that key would have reached
the user as a **silently refused bolus**, with nothing naming the cause. The map cannot hold anything
else now, so that failure mode is gone.

**Deliberately not a sealed type**, which is what this note first suggested. There is one writer, one
reader and one stored type, so a single-case hierarchy would be speculative generality. Naming the
member `wizardPreviews` also says what it holds, where "action state" said nothing; a second action
that needs to park something gets its own typed member.

The plugin's `private fun clearActionStates()` wrapper went too - it only forwarded.

### 9.7 `org.json` as Compose UI state - FIXED

`NfcAction.params` was `JSONObject by mutableStateOf(JSONObject())` - a JSON document used as Compose
state. It is now `NfcParams by mutableStateOf(NfcParams())`, fixed as part of the typed command model
in section 5, which is the same work rather than a separate job. The plugin now matches Automation,
which keeps typed editor state and serialises only at the storage boundary.

---

### 9.8 Default values were written as literals in four places - FIXED, `a1b8a842bb`

The project rule is that a value used in more than one place gets a name. The plugin had **four** sets of
default values, written as literals wherever they were needed, and they did not agree:

| Where | What it was for |
|---|---|
| each action's `getDefaultParams()` | what a new command starts with - the deliberate set |
| the build screen's `applyParams()` | generic numbers of its own, used when restoring a stored command |
| the build screen's field initialisers | generic again, and different again |
| each action's own `?:` fallback | what to use when the value was missing |

A missing duration fell back to **0, 30 or 60** depending which action you read, and to two different
values inside the same action. A missing carb value showed **20 g** on screen and executed as **0 g**. A
bolus was **1.0 U** on screen, **0.0 U** in the action, and `getDefaultParams` for the same command said
**0.0 U**.

`NfcDefaults` holds all of them now, one name each. It sits in the plugin rather than the shared
`Constants`, because nothing outside reads them and numbers that look therapy-wide do not belong there.
The two duplicated ranges (`10..500`, `1..180`) moved with them.

**The fallbacks inside `execute()` were deleted, not renamed.** The commit before made them unreachable,
and a dose must never come from a number nobody chose, so each is now
`params.x ?: return invalidFormat()` - fifteen invented values gone. Only display paths keep a fallback,
because they have to show something, and they use the same constant as the command's own default.
`applyParams` no longer invents either: a value the stored command does not carry leaves the field alone,
and the restore path lays the command's own defaults down first.

Two restatements went too: `BolusWizardAction` repeated `useBg`, `useTt` and `useIob` as `true` although
`NfcParams` already declares them so, and `BolusAction` repeated `isMeal = false`. Stating a default
twice is how the others drifted apart.

Left alone on purpose: the fallback for a missing BG reading, and the pump basal duration step, which
comes from the pump and names a constant only for the case where the pump does not say.

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
when the main branch merges `dev` after `kmp` lands.

### Rename detection is the hazard to watch on the next merge

There are **1474 renames** between the shared base and `kmp`, because whole modules moved from
`src/main` to `src/commonMain`. If git's rename detection is truncated, renames degrade into
add/delete pairs and **edits to a moved file are silently dropped** - the merge reports no conflict and
the change is simply gone.

The merge recorded here completed detection fully and produced 8 conflicts. On any future merge, check
the output for `inexact rename detection was skipped`; if it appears, the result cannot be trusted.

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

### Merge 2 outcome, for reference

Commit `7bdab8c8b5`, 109 upstream commits, taken **before** the Metro work because
`MetroAppCompatActivity`, `FeatureMemberInjectors` and `SyncMemberInjectors.kt` exist only upstream.
Rename detection completed again. **Three** conflicts, all in shared files, and none under
`nfcCommands/` - no file of the plugin was touched by the merge at all:

| File | Resolution |
|---|---|
| `plugins/sync build.gradle.kts` | both sides added a plugin - kept both, Metro from upstream and kotlin serialization from here |
| `app ComposeMainActivity.kt` | our NFC field sits where upstream added the `defaultViewModelProviderFactory` override - kept both, dropped our now-stale "Hilt-provided" comment |
| `plugins/sync di/SyncModule.kt` | upstream removed `SMSCommunicatorModule` from the includes, we had added `NfcCommandsModule` - kept both changes |

Four of the seven files predicted to conflict merged cleanly. **`rerere` recorded all three
resolutions**, so a re-merge replays them.

The merge landed with exactly **one** compile error, fixed in `ec7f230935`: `:plugins:sync` now carries
the Metro compiler plugin, and Metro refuses a **non-final** class with `@Inject` fields unless it is
annotated `@HasMemberInjections`, because the injector is looked up by runtime class. `NfcControlActivity`
was `open` for no reason - nothing subclasses it - so it became final, which is also its end state after
the Metro conversion.

### Merge 3 outcome, for reference

Commit `2fed15e79b`, 56 upstream commits, taken early on purpose: upstream was reworking the very DI the
two commits before it had touched, and waiting only makes the same conflicts bigger. Rename detection
completed. `nfcCommands/` was untouched again. **Four** conflicts, all DI:

| File | Resolution |
|---|---|
| `plugins/sync di/SyncMemberInjectors.kt` | both sides added entries - kept both |
| `app di/metro/AapsLeaves.kt` and `app di/CoreObjectsModule.kt` | upstream deleted the lines **around** ours, having moved `dateUtil`, `loggerUtils` and `alarmSoundPlayer` into Metro proper. Our leaf kept here so the merge drops nothing of ours, then removed in `42ea9bbad2` for a better reason |
| `plugins/sync di/SyncPluginsListModule.kt` (modify/delete) | **deleted upstream**, and our `@IntKey(380)` entry was the only thing we had in it. Deletion accepted, so the plugin was in no list until the next commit |

**The plugin now registers itself.** Upstream has two shapes: a plugin with a Dagger-built consumer is
registered from `SyncPluginsBindings` in `:app` so Dagger keeps ownership, and one without carries its
own annotations. NFC has no Dagger-built consumer left - the only field injection was
`NfcControlActivity`, converted in `ba1cdbd518` - so it follows Tidepool, Xdrip, Tizen and Garmin:
`@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())`, `@NotNSClient`, `@IntKey(380)`,
`@SingleIn(AppScope::class)`. **The `AapsLeaves` leaf from `ba1cdbd518` is therefore gone**: it existed to
stop Metro building a second plugin beside Dagger's, and Dagger no longer builds one.

**`SplitBrainTest` caught a real bug this introduced, and it is the lesson to carry forward.** Metro does
**not** treat javax `@Singleton` as a scope. The moment Metro built the plugin it also built its
dependencies, and `NfcActionFactory`, `NfcRuntimeState` and `NfcTagStore` were rebuilt **on every read** -
breaking exactly the things that only work when there is one object: the remote bolus cooldown and the
parked wizard preview in `NfcRuntimeState`, the just-written cooldown and the log update flow in
`NfcTagStore`. Nothing outside the NFC subtree injects those three, so `@SingleIn(AppScope::class)`
replaces `@Singleton` and no leaf is needed. **Rule: when Metro starts building a class, every javax
`@Singleton` below it has to be re-scoped or handed over - the compiler says nothing.** `SplitBrainTest`,
`ContributedPluginsTest` and `LeafOwnershipTest` in `app/src/test/.../di/metro/` are the guards; run them
after any DI change.

One build-state trap, not a source problem: a stale Dagger factory for `PersistenceLayerImpl` survives in
`database/persistence`'s KSP output from before that class moved to Metro. Nothing regenerates or removes
it and `javac` still compiles it, so the build fails there until
`database/persistence/build/generated/ksp/fullDebug/java` is deleted. Recompiling does not help.

### Merge 4 outcome, for reference

Commit `66c9f75ca1`, 29 upstream commits, 576 files - and **no conflicts at all**, the first such merge
on this branch. Rename detection completed and `nfcCommands/` was untouched a fourth time.

A clean merge is not a reason to skip the checks, and upstream had edited four files we have also
edited, so each was verified rather than trusted: our `@IntKey(380)` entry survived upstream's own edit
to `ContributedPluginsTest`, nothing of ours remains in `AapsLeaves` or `CoreObjectsModule` since
`42ea9bbad2`, and `NfcCommandsPlugin` still carries its four Metro annotations. The `commonMain` source
sets were grepped for `android` imports as well. All clean.

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
| Files with `org.json` (before section 5)   | 18    |
| `R.string.` references                     | 185   |
| Files with `R.string.`                     | 31    |
| `rh.gs(` calls                             | 82    |
| `stringResource(` calls                    | 59    |
| Files with `androidx.annotation.StringRes` | 26    |
| `System.currentTimeMillis()` sites         | 9     |
| `TimeUnit.` sites                          | 5     |
| `plugin.` reach-through accesses in actions | **0** (was 261, see 9.2) |

Largest files: `NfcBuildScreen.kt` 1239, `NfcCommandsScreen.kt` 489, `NfcCommandsPlugin.kt` ~315,
`NfcControlActivity.kt` 220, `NfcTagStore.kt` 171, `NfcCommonUi.kt` 142, `NfcCommandCode.kt` 118.

### Reading build output

Two traps that make a failing build look green. Both were hit in this work and produced wrong
"verified" claims before being caught. Check them against whatever Gradle the project is on:

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
