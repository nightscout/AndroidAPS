# NFC plugin - migration to Kotlin Multiplatform

Written 2026-08-20, updated 2026-09-04. What the NFC Commands plugin needs in order to fit the
Kotlin Multiplatform refactoring going on in `Nightscout/kmp`, and to line up with the architecture
the other plugins already follow.

This note answers three questions:

1. What has to change in the NFC plugin to meet the KMP rules?
2. Is `Nightscout/kmp` far enough along to start, or would the work be thrown away?
3. Which parts of the plugin do not follow the project's own patterns, and should be fixed while we
   are in here anyway?

Companion documents: `_docs/NFC_COMMANDS.md` (what the plugin does) and, for the KMP plan itself,
`_docs/ios_blockers.md`.

**Note on the citations below.** Merge 9 deleted four upstream documents this note quotes -
`KMP_IOS_FEASIBILITY.md`, `METRO_MIGRATION_NEXT_STEPS.md`, `DI_FRAMEWORK_COMPARISON.md` and
`PLUGIN_SELECTION_KEY_SYNC.md`. The quotations are kept because they are what the decisions were
made on and they are still readable in git history, but the files are no longer in `_docs/`. What
survives there is `ios_blockers.md`, which is the one upstream keeps current.

---

## 0. Status

Working branch: `nfc/new-nfc-plugin_kmp`. It exists in parallel to `nfc/new-nfc-plugin` so this
preparation work can happen without disturbing the main NFC line.

| Step                                                       | State                                                                       |
|------------------------------------------------------------|-----------------------------------------------------------------------------|
| Merge `Nightscout/kmp` into the branch                     | **done eleven times** - latest `e5362142d0`, 42 commits, one conflict          |
| Tier 1, the changes needed to compile at all (section 3)    | **done** - `9cd204b8da` "NFC Fix build after kmp merge"                        |
| Architecture alignment (section 9)                         | **all done** - 9.1 to 9.8, the last being 9.5 in `57952b8690`                  |
| The command format and the store blobs (section 5)          | **done** - `28305e0af6` and `ff1916852f`. No `org.json` left in the plugin      |
| Metro DI and the plugin's own manifest (section 1a)         | **done** - `ba1cdbd518`, `009f4087ca`, `42ea9bbad2`. Metro owns the plugin now  |
| Tier 2, needed only for a multiplatform module (section 4)  | **not started** apart from the `org.json` row, which sections 5 and 9.7 did    |
| The `:plugins:sync` flip, step 6                            | **done** - upstream flipped it in `077aa2a6e4`, our files moved in `216e31867e` |
| Strings on `SyncStrings`, step 7                            | **done** - `3aa6e921e5`, and the duplicates it found are section 9b            |
| Build verification                                         | **green** - 93 NFC tests in `testAndroidHostTest`, 95 app tests, all pass       |
| `Nightscout/kmp` freshness                                 | merged 2026-09-04 at `b80996b606`. Still not in `dev`, and now much bigger      |

Sections 3.7 to 3.12 are six breaks that only a compiler found, after an import-derived list had
missed them. The lesson is recorded there because it will repeat on the next merge: **grepping imports
finds moved packages, never changed signatures.** It took two rounds of discovery to find them all -
the first list came from imports and was wrong - so budget for more than one pass.

### What the pull request looks like, measured at merge 11

Against `Nightscout/kmp` at `b80996b606`: **71 files, +8601, -3**. The three removed lines are edits,
not removals, so the change is **purely additive** - nothing upstream owns is deleted or rewritten.

48 of the 71 files are the plugin itself, under
`plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/nfcCommands/` and its tests. The other 23
are the touchpoints a new plugin needs, and they are worth listing because a reviewer will want to
see that the list is short and boring:

| Where | What |
|---|---|
| `core:interfaces` | one `ElementType.NFC`, one `LTag.NFC` |
| `core:data`, `database` | one `Sources.NfcCommands` and its converter and presentation entries |
| `core:keys` | two preference keys, `NfcAllowRemoteCommands` and `NfcForegroundPriority` |
| `core:ui` | the `IcPluginNfc` icon, an element colour, four `ElementTypeStyle` branches, two strings |
| `appshell` | one line: `ElementType.NFC` in the non-searchable navigation group |
| `app` | the six `ComposeMainActivity` hooks for foreground dispatch, and `380` in `ContributedPluginsTest` |
| `plugins/sync` | the manifest entry, the member injector, the strings, `nfc_tech_filter.xml`, and the serialization plugin in the build file |
| `_docs` | `NFC_COMMANDS.md` and this file |

The two lines a reviewer should look at hardest are the ones that are not additive in spirit even
though they are in form: `ElementType.NFC` and `Sources.NfcCommands` are enum values in shared code,
so every exhaustive `when` over them in the tree has to name NFC. That is by design - it is what
caught the lost branch at merge 10 - but it means this PR touches files in five modules that have
nothing to do with NFC.

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
| `6fa678e46b`  | **merge 5**, 90 upstream commits, 3 conflicts                 | does **not** build - see 10              |
| `88d804a052`  | fix build after merge 5 - 1 file, the UiStrings rename         | mechanical, **builds**                   |
| `0ef1155b65`  | **merge 6**, 34 upstream commits, no conflicts                | nothing to fix afterwards                |
| `57952b8690`  | section 9.5 - snackbar events, no more Context, 7 files        | last alignment item, **builds**          |
| `31889dde7a`  | **merge 7**, 30 upstream commits, no conflicts                | nothing to fix afterwards                |
| `f1b0a38d39`  | **merge 8**, 64 upstream commits, 1 conflict                  | leaves nfcCommands outside every source set |
| `216e31867e`  | fix build after merge 8 - into androidMain, 49 files          | pure move, recorded as renames, **builds** |
| `65653e43ea`  | the note after merge 8 and the move                           | documentation only                       |
| `3aa6e921e5`  | step 7 - the strings on SyncStrings, 40 files                 | one change of kind, **builds**            |
| `a410a957c9`  | five repeated strings dropped, section 9b written             | behaviour neutral, **builds**             |
| `ea86643c0a`  | **merge 9**, 64 upstream commits, no conflicts                | does **not** build - javax.inject is gone |
| `860ab34fd9`  | fix build after merge 9 - Metro's Inject, 6 files              | mechanical, **builds**                    |
| `41cf73925b`  | the note after merge 9                                        | documentation only                       |
| `662dff1a58`  | **merge 10**, 123 upstream commits, 1 conflict                | does **not** build - one lost branch      |
| `dcf479defc`  | fix build after merge 10 - one line in appshell                | mechanical, **builds**                    |
| `e5362142d0`  | **merge 11**, 42 upstream commits, 1 conflict                 | nothing to fix afterwards                 |

Keeping these apart matters for review: the fix-build commit only has to answer "did the merge really
force this?", and the alignment commit carries its own rationale. `f9e51ec06c` is also the one that
is cleanly cherry-pickable - see section 10.

---

## 1. Where the two branches stand

| Branch                           | HEAD          | Date       | Note                                      |
|----------------------------------|---------------|------------|-------------------------------------------|
| `nfc/new-nfc-plugin_kmp` (ours)  | `e5362142d0`  | 2026-09-04 | merged `kmp` at `b80996b606`               |
| `Nightscout/kmp`                 | `b80996b606`  | 2026-09-03 | fully merged as of merge 11                |
| `Nightscout/dev`                 | `283a184f60`  | 2026-08-25 | `kmp` has **not** landed here yet          |

The rest of this section is the picture as it was on 2026-08-26, kept for the reasoning it records.

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

**Out of date as of merge 8.** That table was measured before merges 5 to 8, and every "no" row in it
has since been converted. `:plugins:sync` now has `commonMain` `androidMain` `iosMain`
`androidHostTest` `androidDeviceTest` - see section 6 for what is in each.

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

## 4. Tier 2 - only needed if the NFC code itself goes to `commonMain`

**The title used to say "if the module itself becomes multiplatform". The module became
multiplatform at merge 8 and none of this became due**, because the NFC code went to `androidMain`.
Read every row below as "needed only if someone decides NFC should be shared", which nothing
suggests - see section 6.

Two rows have since moved on their own, for reasons that are not about Tier 2:

- **`R.string` to `TextRef`** is now worth doing anyway. Upstream moved almost all of `:plugins:sync`
  onto a generated `SyncStrings`, and only one upstream file still uses `R.string.`, which makes the
  NFC code the largest `R.string` user left in the module. This is agreed as the next step - step 7
  in section 8. It is alignment, not a build need: `R` exists in `androidMain` and the current code
  compiles.
- **Tests** landed exactly as the last row predicted. They are in `androidHostTest`, and the
  dependencies really are written out by hand in the module build file, because
  `test-module-dependencies` applies `com.android.library`.

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

**Settled at merge 8, and half of the reasoning below was wrong.** Upstream did flip
`:plugins:sync` to multiplatform - `077aa2a6e4`, in merge 8 - so the "sync is Android only by
nature" quote further down no longer describes the module. It now has a real `commonMain` with the
NS client in it, and an `iosMain`:

| source set        | files | what is in it                                        |
|-------------------|-------|------------------------------------------------------|
| `commonMain`      | 60    | only `nsclientV3` - the client core and the client-control screens |
| `androidMain`     | 233   | everything else, including `R` and all of `res/`      |
| `iosMain`         | 3     | the iOS half of the NS client                         |
| `androidHostTest` | 114   | was `src/test`                                        |
| `androidDeviceTest` | 1   | was `src/androidTest`                                 |

What survived is the **answer**, not the reason for it: NFC belongs in `androidMain`, the first row
of the table below. The module being multiplatform does not pull NFC into `commonMain`, because the
argument for keeping it on Android was never about the module - it was about NDEF intent dispatch not
existing on iOS. Tier 2 is still not needed.

The original reasoning follows, kept because it is what the decision was made on at the time.

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

**Upstream has effectively answered this, as of merge 5.** `_docs/KMP_IOS_FEASIBILITY.md` on `kmp` says
of the profile and device-status JSON:

> Both live in `:plugins:sync`, which is **Android only by nature** (WorkManager, a socket.io
> `Service`), so the `org.json` quirks stay out of the shared modules.

And the module conversions bear it out. In the 90 commits of merge 5, upstream made `:plugins:automation`,
`:plugins:constraints`, `:plugins:configuration`, `:plugins:source` and `:shared:impl` multiplatform,
each with the same commit title - "move off Dagger to Metro **and make it multiplatform**".
`:plugins:sync` got only the first half: `e7d693ac23` "Move `:plugins:sync` off Dagger to Metro and flip
its leaves to delegates", 63 files, no multiplatform step.

So the first row of the table is the one to plan for: **NFC stays Android only.** Two things follow, and
they are the reason this matters.

1. **Tier 2 is not needed.** The 185 string references, the 26 `@StringRes` files, `TimeUnit`,
   `DateFormat` and the hex helper are all allowed in `androidMain`. That is the ten-times cost, and it
   is off the table unless someone decides NFC should be shared.
2. **The flip, when it comes, is a path move.** The other modules became multiplatform *shells* with an
   `androidMain` for their Android-only code - `:plugins:automation` did exactly that. When
   `:plugins:sync` follows, the plugin's files move from `src/main` to `src/androidMain` and nothing
   else has to change, because Tier 1 is already applied.

This is upstream's documented reasoning and its commit record, not a decision from the owner of this
work, so it is still worth confirming. But it is no longer an open question with a ten-times cost hanging
on it, and no expensive work should be started on the assumption that NFC goes to `commonMain`.

Note that the section 9 alignment work is worth doing under either answer, and 9.1 was a prerequisite for
the Dagger work in both.

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
2. ~~**The `:plugins:sync` flip**~~ - **done upstream at merge 8**, `077aa2a6e4`. The NFC hardware
   `expect` / `actual` question did not need answering: the plugin is in `androidMain` and the
   module's `commonMain` holds only the NS client. See section 6.

One thing may still move: follow-up 3 in the KMP note is `PluginDescription.description: Int`, the
`-1` sentinel set by 57 files. That is one line in `NfcCommandsPlugin.kt`.

---

## 8. Working plan

| Step | Work                                                                                                                                                             | Depends on           |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| 0    | ~~Ask the owner the section 6 question~~ - upstream has answered it in its own doc and commits: `:plugins:sync` stays Android. Worth confirming, no longer worth waiting for.                                                                                    | -                    |
| 1    | ~~The store blobs (section 5)~~ - **done**, `ff1916852f`.                                                                                                          | -                    |
| 2    | ~~Convert the NFC plugin's DI to Metro and move the activity's manifest entry into the plugin~~ - **done**, `ba1cdbd518` and `009f4087ca`. See 1a for the two predictions that were wrong. | -                    |
| 3    | **Keep merging `kmp` as it moves**, roughly daily while it is this active. Eleven merges in, `nfcCommands/` has never been touched by one and the conflicts are always in the same two or three shared files. Waiting costs more than merging - see the merge notes in section 10. | -                    |
| 4    | ~~Tier 2~~ - **not needed.** Upstream's own doc and its conversion record say `:plugins:sync` stays Android, so the plugin lives in `androidMain`. See section 6. | -                    |
| 5    | ~~Decide the NFC hardware seam~~ - **not needed** for the same reason. There is no iOS half to design. | -                    |
| 6    | ~~When upstream makes `:plugins:sync` multiplatform, the plugin's files move from `src/main` to `src/androidMain`~~ - **done**, `216e31867e`. It happened at merge 8, and it *was* our task after all - see below. | -                    |
| 7    | ~~Move the NFC strings onto the generated `SyncStrings` names~~ - **done**, `3aa6e921e5`. No `R.string` is left in the plugin. | -                    |
| 8    | Propose one generic key for each of the twelve strings NFC and the SMS communicator both own, and retire the two feature-named ones. Better done once the plugin is merged - see 9b. | the plugin landing |
| 9    | Open the pull request against `kmp`. Its shape is measured in section 0. | -                    |

All of section 9 is done as of `57952b8690`, and step 6 is done as of `216e31867e`. The plugin
compiles and tests inside a multiplatform `:plugins:sync`.

**Step 6 was our task after all. This is the prediction that failed, and why.** The note used to say
that whoever converts the module moves all of `src/main` and our files travel with it, so there would
be nothing for us to do. The first half held - `077aa2a6e4` moved 233 files - but the second half did
not.

What actually happened at merge 8:

- The files we had **edited** followed the rename by themselves, and our changes merged into them:
  the manifest entry, the `NfcControlActivity` member injector, and the NFC strings in
  `res/values/strings.xml`. Git treats those as content changes to files it can follow.
- The files we **added** did not move. All 49 stayed in `src/main` and `src/test`, which are no
  longer source sets, so nothing in `nfcCommands/` was compiled at all. Git did not even report the
  `CONFLICT (file location)` the note expected - it said nothing, and the merge looked clean.

**The lesson, and it is the same one as section 3 and as the merge 7 trap: a clean merge is not a
correct merge.** After any merge that renames a directory we have files in, check where our files
actually are, not whether git complained. `git ls-files plugins/sync/src/main` answers it in one
line.

The move itself was as cheap as predicted - 49 files, every one a rename, zero lines changed - once
two things were checked first:

1. `R` still exists in `androidMain`, so the roughly 40 NFC files on `R.string` and `rh.gs(Int)`
   compile unchanged.
2. The hand written `androidHostTest` source set already declares `:shared:tests`,
   `:implementation`, `:plugins:aps`, Robolectric and the Compose test artifacts, which used to come
   from the test convention plugins a multiplatform module cannot apply.

Where section 9 lands: **all of it is done** - 9.1 to 9.4, 9.6, 9.7 and 9.8, and 9.5 last in
`57952b8690`.

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

### 9.5 Toast, vibration and `Handler(Looper.getMainLooper())` - FIXED, `57952b8690`

Three Toast sites, a `VibratorManager` and a `Handler(Looper.getMainLooper())` post. Together they were
the only reason `NfcCommandsPlugin` took a `Context`.

`CLAUDE.md` prefers a snackbar to a Toast, but also warns that a background plugin has no Compose tree to
show one in and should use a notification instead. **That warning does not apply here**, and the reason is
worth writing down because it applies to any plugin: the project already has the mechanism, and
`GlobalSnackbarHost` documents it itself -

> all downstream code can `rxBus.send(EventShowSnackbar(...))` from any thread without needing a Context,
> a `LocalSnackbarHostState`, or a direct reference to the host.

and when no screen is up, an application-scoped collector in `MainApp` turns the event into a system
notification. So the event covers both cases at once, which is more than the Toast did - a Toast posted
from the plugin with the app in the background was simply lost.

All three Toasts became `EventShowSnackbar`, typed `Success` or `Error` from the result rather than every
message looking alike. The `Handler` post went with them: `rxBus` is safe from any thread, so there was
nothing left to hop to the main thread for. No new `LocalSnackbarHostState` consumer was added, which
`CLAUDE.md` also asks for.

The vibration stays, because a tag is often scanned without looking at the phone and it is the only
feedback at the moment of the scan. It needs a `Context`, so it moved to `NfcVibration.kt` and is called
by the two screens that run a chain - same effects, same swallowing of failures, since a busy vibrator
must not turn into a failed command.

Result: `NfcCommandsPlugin` no longer imports anything from `android.*`, and its constructor has no
`Context`. Two test construction sites lost that argument.

Note this was never a Tier 2 prerequisite in the end - see section 6. It is worth having on its own
terms: a message that survives the app being in the background, and a plugin that does not reach for the
screen.

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

## 9b. Strings the plugin repeats, and what was done about each

Found while doing step 7. Comparing the English text of every NFC string against every other string
in `:plugins:sync`, `:core:ui` and `:core:interfaces` turned up **16 exact matches**, plus one pair
that repeated itself inside NFC. This section records all of them, because most were deliberately
left alone.

**Compare case sensitively.** A first pass that lowercased both sides reported four more matches
that are not matches at all: `:core:ui` writes its user-entry-log labels in capitals, so
`uel_cancel_extended_bolus` is `CANCEL EXTENDED BOLUS`, `uel_loop_resumed` is `LOOP RESUMED` and
`carbs_g` is `CARBS %1$d g`. Reusing one of those would put shouting text in the action picker.

### Removed - the string already existed with the same text

| Our string | Now uses | Text |
|---|---|---|
| `nfccommands` | `CoreUiStrings.nfccommands` | `NFC Commands` |
| `nfccommands_cmd_loop_lgs` | `CoreUiStrings.lowglucosesuspend` | `Low Glucose Suspend` |
| `nfccommands_log_action_manual` | `CoreUiStrings.manual` | `Manual` |
| `nfccommands_clear_log` | `SyncStrings.clear_log` | `Clear log` |
| `nfccommands_extended_canceled` | `SyncStrings.nfccommands_cmd_extended_stop` | `Cancel extended bolus` |

The first three are worth taking from `:core:ui`: the text is identical, so reusing it also brings
its translations. `nfccommands` was ours twice over - this branch added the `:core:ui` copy in
`25fb743cd4` for `ElementType.NFC` in `ElementTypeStyle.kt`, while the plugin kept its own. One is
enough, and it has to be the `:core:ui` one, because `commonMain` code there cannot see the plugin.

The last row is the pair that repeated itself: `nfccommands_cmd_extended_stop` labelled the command
in the picker and `nfccommands_extended_canceled` was the message shown after it ran, both saying
`Cancel extended bolus`. The label is the more visible of the two and is the one kept. If the result
message should read differently - `Extended bolus cancelled` would be the natural wording - that is a
wording change, not a duplicate, and needs its own string.

### Kept on purpose - decide after the plugin lands

Twelve NFC strings repeat a string that `:plugins:sync` already owns, all of them from the SMS
communicator:

| Our string | Same text as | Text |
|---|---|---|
| `nfccommands_another_bolus_in_queue` | `smscommunicator_another_bolus_in_queue` | `There is another bolus in queue. Try again later.` |
| `nfccommands_loop_has_been_disabled` | `smscommunicator_loop_has_been_disabled` | `Loop has been disabled` |
| `nfccommands_loop_resumed` | `smscommunicator_loop_resumed` | `Loop resumed` |
| `nfccommands_profile_switch_created` | `sms_profile_switch_created` | `Profile switch created` |
| `nfccommands_reconnect` | `smscommunicator_reconnect` | `Pump reconnected` |
| `nfccommands_remote_bolus_not_allowed` | `smscommunicator_remote_bolus_not_allowed` | `Remote bolus not available. Try again later.` |
| `nfccommands_remote_command_not_allowed` | `smscommunicator_remote_command_not_allowed` | `Remote command is not allowed` |
| `nfccommands_remote_command_not_possible` | `smscommunicator_remote_command_not_possible` | `Remote command is not possible` |
| `nfccommands_restarting` | `smscommunicator_restarting` | `AAPS is restarting` |
| `nfccommands_tt_set` | `smscommunicator_meal_bolus_delivered_tt` | `Target %1$s for %2$d minutes` |
| `nfccommands_unknown_command` | `smscommunicator_unknown_command` | `Unknown command or wrong reply` |
| `nfccommands_wrong_duration` | `smscommunicator_wrong_duration` | `Wrong duration` |

**These stay as they are, and the reason is the key name, not the text.** Each of these is named
after the feature that owns it - `smscommunicator_`, `sms_` - so pointing NFC at one would leave NFC
reading a string named after SMS. The right answer is probably a third, generic key that both
features use, with the SMS and NFC names retired. That is a change to code this branch does not own,
so it is better proposed once the NFC plugin is merged, when the two callers are visible side by
side. Until then the repetition is deliberate.

The cost of leaving it is small and worth stating plainly: a translator is asked for the same
sentence twice, and the two copies can drift apart in a language where only one of them is
retranslated.

### The near matches, for whoever runs this comparison again

Not duplicates, listed so they are not reported as such a second time:

| Our string | Looks like | Difference |
|---|---|---|
| `nfccommands_carbs_set` `Carbs %1$d g` | `carbs_g` `CARBS %1$d g` | case |
| `nfccommands_cmd_extended_stop` `Cancel extended bolus` | `uel_cancel_extended_bolus` `CANCEL EXTENDED BOLUS` | case |
| `nfccommands_loop_resumed` `Loop resumed` | `uel_loop_resumed` `LOOP RESUMED` | case |

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

### Merge 5 outcome, for reference

Commit `6fa678e46b`, 90 upstream commits including a merge of `origin/ios` into `kmp`. Rename detection
completed, `nfcCommands/` untouched a fifth time. Three conflicts:

| File | Resolution |
|---|---|
| `plugins/sync build.gradle.kts`, `app build.gradle.kts` | each conflicted as **one hunk over the whole file**, because upstream committed both with CRLF and stray CR terminators while ours are LF - so every line differs. The real change is small: upstream dropped the `dagger.android` KSP processor from both. Took upstream's **bytes**, keeping their line endings so these two stop conflicting wholesale every time, and put our `kotlin("plugin.serialization")` line back |
| `core/ui ElementTypeStyle.kt` | upstream renamed `UiStrings` to `CoreUiStrings`. Took upstream's version and put our four NFC branches back - the same resolution as merge 1 |

Taking upstream's `app/build.gradle.kts` also **restores the `gitAvailable()` and `allCommitted()` build
checks**, which `5aa040f7c0` "import NFC Plugin" had commented out. That has nothing to do with NFC and
should not travel with this work. It cannot fire on this branch anyway, the uncommitted-changes check
applying only on `master`.

**The trap fired for real this time.** The `UiStrings` rename reaches `NfcBuildScreen.kt` too, and that
file merged with **no conflict**, because upstream never touched those lines in our copy. So merge 5 did
not build, and `88d804a052` fixed it - one import and seven unit labels. This is the second time the
"clean merge is not a correct merge" note has paid for itself; keep grepping for renamed symbols after
every merge, not only for `android` imports in `commonMain`.

**A build-state trap that will hit anyone building this merge.** Ninety commits moved many classes from
Dagger to Metro, and each leaves an orphan Dagger `_Factory.java` in its module's KSP output. Nothing
regenerates or deletes those, `javac` still compiles them, and recompiling does not help - the build
fails in whichever module it reaches first, `:database:persistence` and `:ui` here. Deleting the stale
`build/generated/ksp/*/java` directories clears it, and they are all regenerated. There were 93 of them
after this merge.

### Merge 6 outcome, for reference

Commit `0ef1155b65`, 34 upstream commits, **no conflicts**, and `nfcCommands/` untouched a sixth time.
The checks were run anyway: the `@IntKey(380)` registration and its `ContributedPluginsTest` entry, the
`NfcControlActivity` member injector, `CoreUiStrings` still being the name `NfcBuildScreen` imports, and
no `android` import in a `commonMain` source set. All clean, and it compiled first time.

**The stale-generated-output problem has a third hiding place**, worth listing together since each merge
has found a new one:

| Where | Found after |
|---|---|
| `database/persistence/build/generated/ksp/*/java` | merge 3 |
| `ui/build/generated/ksp/*/java`, and 93 more | merge 5 |
| `app/build/generated/hilt` | merge 6 |

The pattern is always the same: upstream moves a class from Dagger to Metro, or deletes it, and a
generated Java file that names it stays behind and is still compiled. Merge 6's was
`NotificationManagerImpl`, a class that no longer exists anywhere in the tree - only a stale comment in
`UiInteractionImpl` still mentions it - failing `:app:hiltJavaCompileFullDebug`. Recompiling never
clears these. Deleting the generated directory does, and it is regenerated.

### Merge 7 outcome, for reference

Commit `31889dde7a`, 30 upstream commits, **no conflicts**, `nfcCommands/` untouched a seventh time, and
nothing to fix afterwards - the first merge that needed no follow-up commit at all. The usual checks were
run and all passed.

The one thing it changed for us is not code: **library modules lost their product flavours**, so the
build commands are different from here on. That is written up in the appendix under "Build commands
changed at merge 7", because the error Gradle gives looks alarming and has nothing to do with the code.

One trap worth remembering: **`PreferenceContentExtensions.kt` merged with no conflict while carrying
our Android-only `androidx.compose.ui.res.stringResource` import into a `commonMain` file.** Git had
no reason to flag it because kmp never touched those lines. A clean merge is not proof of a correct
merge - grep the `commonMain` source sets for `android` imports after any future merge.

### Merge 8 outcome, for reference

Commit `f1b0a38d39`, **64 upstream commits**, one conflict. The big one: upstream flipped
`:plugins:sync` to multiplatform (`077aa2a6e4`), which is the event step 6 was waiting for. Merge 7's
trap repeated in a new shape - the merge was almost silent, and still left the plugin uncompiled.

- **The one conflict** was `plugins/sync/build.gradle.kts`, and only because upstream rewrote the
  whole file. Our single line in it was `kotlin("plugin.serialization")`, kept as
  `id("kotlinx-serialization")`, the form the converted modules use. It is genuinely needed:
  `NfcCommand.kt` and `NfcTagStore.kt` use `@Serializable`.
- **`nfcCommands/` was touched by a merge for the first time** - not in content, in placement. See
  section 8 for what git did and did not do, and `216e31867e` for the move.
- Nothing else broke. No `core/` module changed in these 64 commits, so none of the signature churn
  of sections 3.7 to 3.12 repeated.

Also in this merge, and worth knowing even though none of it touches NFC: the `ios` branch is now
merged into `kmp` regularly, the NS client core and the client-control screens moved to `commonMain`
with an `iosMain` half, `:workflow` and `:shared:tests` were flipped, and the sync strings moved onto
a generated `SyncStrings` - see the next step in section 8.

### Merge 9 outcome, for reference

Commit `ea86643c0a`, **64 upstream commits, no conflicts**, and the first merge where the two files
that conflicted the time before came through on their own: our `kotlinx-serialization` line in
`plugins/sync/build.gradle.kts` and the `NfcControlActivity` entry in the manifest both auto-merged.
`nfcCommands/` stayed in `androidMain` and `androidHostTest`, so the placement check that merge 8
taught us to run passed without any work.

It still did not build, for one reason. **Dagger is now completely gone** - `95fe432ba8` removed the
last of it and `abd6515f64` dropped `javax.inject` together with Metro's Dagger interop.
`:plugins:sync` no longer applies that interop, so `javax.inject.Inject` stopped resolving in the
five NFC files that used it. `860ab34fd9` swaps it for `dev.zacsweers.metro.Inject` in the same
places, and changes the seven `pumpEnactResultProvider.get()` calls in the test to
`pumpEnactResultProvider()`, because Metro's `Provider` is invoked rather than asked. Both are
copies of what upstream did to its own files.

**The trap in this merge is not in the code at all: stale KSP output.** `:app` failed with
*"package dagger.internal does not exist"* in generated Java under three pump modules. Fourteen
modules still had `build/generated/ksp` full of the Dagger factories generated before the merge, and
those import a package that no longer exists. Deleting those directories is the whole fix - no clean
build, and nothing in the source tree changes:

    for d in app plugins/sync pump/* shared/tests; do rm -rf $d/build/generated/ksp; done

Anyone merging across this Dagger removal on an existing checkout will hit the same thing, and the
error names Dagger, which makes it look like a code problem on our side. It is not.

Also in this merge, and none of it touching NFC: automation and `LoopPlugin` moved to `commonMain`,
and **upstream deleted several of the documents this note cites** - see the note under section 1.

### Merge 10 outcome, for reference

Commit `662dff1a58`, **123 upstream commits**, one conflict. This is the merge where the other two
clients became real: `:plugins:sync` gained `jvmMain`, `jvmSharedMain`, `commonTest` and `iosTest`
beside `androidMain`, the whole AAPS UI runs on desktop, and the Nightscout websocket is shared with
desktop polling as its fallback.

**The conflict was a move, not a disagreement, and it is the shape to expect again.** Upstream took
614 lines out of `ComposeMainActivity` - `navigate(ElementType)` and `handlePluginClick` went to
`appshell/src/commonMain/.../navigation/ElementNavigation.kt` so a desktop tap does something. Our
side of those lines was a single entry, `ElementType.NFC` in the non-searchable group. Resolved by
taking upstream's deletion whole and keeping the one thing of ours that lives outside it, the
`NfcForegroundDispatch` import. The other five NFC hooks in that activity - the lazy field,
`onResume`, `onPause`, `onNewIntent` and `observeWarning` - are outside the moved block and merged
themselves.

That left exactly one error, and it is worth understanding rather than just fixing: the shared switch
has **no `else`**, which is what makes the compiler name a new `ElementType`, and `ElementType.NFC` is
**ours**. So a switch that moves house takes our branch with it and forgets to bring it. `dcf479defc`
puts the one line back where the switch now lives.

**Lesson for the next one:** after a merge, grep for our own enum values in whatever file now owns the
`when`, not only in the file that used to. `git grep -n "ElementType.AUTOMATION"` finds the new home in
one line, because automation sits next to us in every one of those lists.

### Merge 11 outcome, for reference

Commit `e5362142d0`, **42 upstream commits**, one conflict and **nothing to fix afterwards** -
`:app:compileFullDebugKotlin` was green straight off the merge. The iOS client now builds and uploads
to TestFlight from CI, the app shows in the user's language on iOS and desktop, imported settings
apply without a restart, and the websocket frame handling is shared.

`ComposeMainActivity` conflicted again, this time as two additions on one line rather than a move:
upstream added a third observer to `observePreferences()` so an import can ask the activity to
recreate itself, and our `nfcForegroundDispatch.observeWarning()` call sits in the same place. Both
kept - upstream's block with the other preference observers, ours after it, because it is a different
kind of thing.

`nfcCommands/` has now been untouched by upstream **eleven merges running**.

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

### Build commands changed at merge 7

Upstream **removed the product flavours from library modules** - `flavorDimensions` and the five
`productFlavors` are gone from `buildSrc/.../android-module-dependencies.gradle.kts`, and a new
`:appshell` module appeared. Library modules now build plain `debug` / `release` variants, so the task
names changed:

| Before merge 7 | From merge 7 |
|---|---|
| `:plugins:sync:compileFullDebugKotlin` | `:plugins:sync:compileDebugKotlin` |
| `:plugins:sync:testFullDebugUnitTest` | `:plugins:sync:testDebugUnitTest` |
| test results under `build/test-results/testFullDebugUnitTest/` | `.../testDebugUnitTest/` |

`:app` keeps its flavours, so `:app:compileFullDebugKotlin` and `:app:testFullDebugUnitTest` are
unchanged. The failure this produces is not a compile error and says nothing about the code - Gradle
reports *"Cannot locate tasks that match ... task 'compileFullDebugKotlin' not found in project
':plugins:sync'"*.

### Build commands changed at merge 8

`:plugins:sync` is a multiplatform module now, so its variant tasks are gone in turn. `:app` is
unaffected again.

| From merge 7 | From merge 8 |
|---|---|
| `:plugins:sync:compileDebugKotlin` | built by `:plugins:sync:allTests` |
| `:plugins:sync:testDebugUnitTest` | `:plugins:sync:allTests` |
| test results under `build/test-results/testDebugUnitTest/` | `.../testAndroidHostTest/` |

`runtests.sh` already covers this - it runs `testFullDebugUnitTest testDebugUnitTest allTests`, and
`allTests` is the multiplatform one.

### TLS failure on this machine, and the flag that fixes it

Gradle cannot download anything new here: every repository fails with
*"Got SSL handshake exception ... PKIX path building failed"*. It is not the network - `curl` fetches
the same URL with a 200. The JDK truststore does not have the certificate the local TLS interception
presents, and `git fetch` fails for the same reason.

Adding `-Djavax.net.ssl.trustStoreType=Windows-ROOT` to the Gradle command makes it read the Windows
certificate store instead, and the build downloads normally. Nothing in the repository is changed by
it. Merge 8 needed it because upstream added a new dependency,
`dev.whyoleg.cryptography:cryptography-provider-optimal`, which was not in the local cache yet.

    ./gradlew.bat :plugins:sync:allTests --no-daemon -Djavax.net.ssl.trustStoreType=Windows-ROOT

### Stale KSP output after the Dagger removal

Not a build-command change, but it belongs next to them. On an existing checkout, a merge that
crosses `95fe432ba8` leaves every module's `build/generated/ksp` holding Dagger factories that
`import dagger.internal`, a package the merge deletes. `javac` then fails on generated code with
errors that name Dagger and point at files nobody wrote. Delete the directories, do not clean build:

    for d in app plugins/sync pump/* shared/tests; do rm -rf $d/build/generated/ksp; done

### Reading build output

Two traps that make a failing build look green. Both were hit in this work and produced wrong
"verified" claims before being caught. Check them against whatever Gradle the project is on:

- **Kotlin errors are not `e:` lines.** They come as `Problem found: Kotlin compiler error` blocks with
  the message and `Location:` on following lines. Grepping only `^e: ` returns 0 on a build with a
  dozen errors. Grep for both.
- **Test result XMLs survive a failed compile.** If the test compile fails, `build/test-results/`
  still holds the *previous* run, so the counts read like a pass. Check the file timestamps, or
  delete `plugins/sync/build/test-results/` first. The NFC files are now
  `.../testAndroidHostTest/TEST-app.aaps.plugins.sync.nfcCommands.*.xml`.

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
