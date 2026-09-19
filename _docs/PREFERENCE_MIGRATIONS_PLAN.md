# Preference keys, migrations and settings import - plan

Status: **proposal, nothing implemented yet.** Written 2026-09-19, revised the same day after a
review (section 7). Related issue: #5140.

## 1. Why

**Import.** Since `dee57d5` a settings import applies in place instead of restarting the app. iOS
cannot restart itself, so in place is the only ending that works everywhere, and Android users are
the ones who test that code. But the running app keeps old state:

1. Cached `observe()` flows. `ImportExportPrefsImpl.executeImport()` writes below `Preferences`,
   so nothing emits.
2. Plugins that stay enabled. `ConfigBuilderImpl.applyConfiguration()` restarts only plugins whose
   enabled flag changed. Many plugins read settings only in `onStart` (for example
   `DanaRSPlugin.changePump()` reads the MAC address and pump name).
3. Objects that read a setting once, in a constructor, an `init` block or a property initializer,
   anywhere in the graph - not only singletons outside plugins. Examples: `ProfileRepositoryImpl`,
   `Objective.UITask`/`ExamTask` (they gate closed loop and SMB), `LImpl`, `ActiveSceneManager`.
4. Everything that runs only at startup, including the preference migrations.

**A weak check in today's in-place import.** `ImportViewModel.applySettings()` decides whether the
pump changed with `verifyPumpIdentification(type, pump.serialNumber())`, right after
`applyConfiguration()`. That sends `EventConfigBuilderChange`, and the Dana and Diaconn drivers
answer it with a `reset()`. The reset itself is fine: it clears the in-memory copy, and the next
connection reads it again. But until then the live serial is `""`, so at that moment the check
cannot tell an unchanged pump from a new one and may report "changed": `connectNewPump()` ends the
running temporary basal in the database, and `completeAllAsNoOp()` drops the queued commands. The
next loop cycle and the next connection heal both, so the effect is small. Dev only (`dee57d5` is
not released).

**Migrations.** There is no preference version anywhere. About 25 steps live in four places
(`MainApp.doMigrations()`/`dataMigrations()`, constructors, plugin `onStart`, lazy reads). Every
step decides by the shape of the data and most run on every start. They run on Android only; the
iOS and desktop shells run none. An import never runs them.

**Keys.** The registry (`PreferencesImpl.prefsList`) is 16 core enums seeded in code plus keys
added at runtime by `registerPreferences()` (plugins, `LocalAlertUtilsImpl`). Some code bypasses
`Preferences` and uses the raw store. Several keys that hold device or runtime state are exportable,
so an import restores them from the file (section 3.1.3).

## 2. Goals and non-goals

Goals:

- An import gives the same result as a restart used to, on Android, iOS and desktop, without
  restoring another phone's device state, and restores the pump only when the user asks for it.
- Migrations are deterministic: the result depends only on the input data and the code.
- Parallel development on different modules never collides (no shared counter).
- Removing a module in `settings.gradle` removes its keys, its migration steps and its snapshot.
- A forgotten migration fails the build.
- Every key the app stores is registered and classified.

Non-goals:

- Wear. It has its own store and its own registry. Same treatment later, separately.
- Room database migrations. They stay as they are.
- The format inside JSON values (automation, insulin, profiles, quick wizard). The owner of a
  document puts incoming data into its standard form; a document that changes format gets a
  version field inside.

## 3. Design

### 3.1 Every used key is registered and classified

1. **Registration at build time.** Key sets are contributed with a Metro multibinding
   (`@ContributesIntoSet(AppScope::class, binding = binding<PreferenceKeySet>())`, like
   `SearchableProvider`). `PreferencesImpl` builds its registry from the injected set.
   - `:core:keys` has no Metro, so its sets are contributed from `:implementation`.
   - `LocalAlertLongKey` moves into `:core:keys` (it is a global key), so `LocalAlertUtilsImpl` no
     longer registers it by hand.
   - `registerPreferences()` leaves the shared `Preferences` interface; wear keeps its own
     registration inside its own class.
   - A contributed key set has no dependencies, so it cannot create a DI cycle with
     `PreferencesImpl`.
   - `PluginBaseWithPreferences.ownPreferences` stays, with a new job: it declares which plugin owns
     which keys (3.1.7, completeness check in 3.3).
2. **The raw store is closed.** A source-scan test fails when production code outside an allowlist
   uses `SP`, `KeyValueStore`, `SharedPreferences` or `getSharedPreferences(`. Allowlist, each with
   its reason: `PreferencesImpl`, the import/export code, the migration runner, the DI wiring, both
   `FabricPrivacyImpl` classes (DI cycle), `LocaleHelper` and `SPBackupAgent` (they read before DI
   exists; a test pins the key strings `language` and `simple_mode` they use).
   - `Preferences` gets a write that commits synchronously, for the two places that need it:
     the ComboV2 nonce ("very important to not lose the nonce") and the vacuum flag in
     `MainApp.vacuumDatabaseIfDue()`.
3. **Classification.** Every key is one of three kinds. Phase 1 audits every key.
   - **Setting:** exportable, comes from the file.
   - **Pump configuration:** exported, and taken from the file only when the user ticks "Restore
     pump configuration" on import (3.4). It is every plugin key of the pump modules (`pump/*`,
     including pump families' shared modules and the virtual pump), the pump plugins' concrete
     enabled keys (`ConfigBuilder_Enabled_PUMP_*`) and `BooleanNonKey.AllowHardwarePump`. This
     covers the pump selection, its settings and its runtime state, for example
     `ErosStringNonPreferenceKey.PodState`/`ActiveBolus`, `DashStringNonPreferenceKey.PodState`,
     `MedtrumIntNonKey.PumpState`/`CurrentSequenceNumber`/`SyncedSequenceNumber`,
     `MedtrumLongNonKey.SessionToken`/`PatchId`, `EopatchStringNonKey.PatchState` and the 11
     ComboV2 keys. The contributed key sets carry their owner's plugin type, so the import can tell
     pump keys from the rest without a hand-kept list.
   - **Device state:** not exportable, never taken from a file; the phone keeps its value.
     Exportable today and must change:
     - pump identity of this device's database: `StringNonKey.ActivePumpType`,
       `ActivePumpSerialNumber`, `LongNonKey.ActivePumpChangeTimestamp` (they describe the local
       database, not the pump; 3.4 decides from them whether the pump changed)
     - sync cursors: `NsclientStringKey.V3LastModified`, the `NsclientLongKey` `*LastSyncedId`
       entries, `TidepoolLongNonKey.LastEnd`
     - `StringNonKey.ActiveScene` (holds local record ids)
     - also check `InstaraStringKey.DeviceMetaJson` (KDoc says not exportable, it is) and
       `GoogleDriveRefreshToken`.
     Already not exportable: the ActivePlugin mirror, client-control pairing and secrets, sync
     stamps, UI state.
   Pump state that is **not in the preference store** cannot be restored by any import: Insight
   keeps its pairing in its own SharedPreferences file (`PairingDataStorage`), and the Dana,
   Diaconn, Equil, Insight, Omnipod Dash and Eros drivers have their own history databases.
   Restoring an Insight setup on a new phone therefore still needs pairing again.
4. **Keys to move into enums:** cloud storage tokens (`CloudStorageManager`, `GoogleDriveProvider`
   and its three platform versions, `GoogleTokens`), CareLevo (`CarelevoPumpPlugin`,
   `CarelevoPatch`, `CarelevoAlarmNotifier`, four DAOs, `CarelevoPatchConnectViewModel`), and
   `VirtualStringNonPreferenceKey` (never registered). ComboV2's keys are fixed strings and are
   already registered; they only need `exportable = false` and the commit write.
5. **Runtime guard.** `PreferencesImpl` logs, and asserts in debug builds, when it reads or writes a
   key object that is not registered.
6. **Orphan log.** After the migration runner, log every stored key that matches no registered key.
7. **Global keys and plugin keys.**
   - **Global keys** are the enums in `core/*` (17 today). Any code may read them.
   - **Plugin keys** are the enums in a plugin module (85 today). Only the owning plugin reads and
     writes them, or its pump family's shared module (`pump/dana/common`, `pump/omnipod/common`,
     `pump/rileylink`). Measured on 2026-09-19: no plugin reads another plugin's keys. 20 plugin
     enums are used outside their module, all inside their pump family, plus `MainApp`'s migration
     code and a KDoc list in `IntentKey` (stale: `SmsIntentKey` is in `:plugins:sync` now).
   - **The rule that matters for the import:** a plugin may keep any key in memory from `onStart`,
     global or its own, because the import restarts every plugin (3.5). Code outside plugins must
     not keep any key in memory: it reads the value when needed, or observes it.
   - A source-scan test keeps plugin keys inside their module family (the same scan as the
     measurement). The "outside plugins" rule is covered by the audit in 3.5 and by review.

### 3.2 Migration steps with done markers

No version number. The store records **which steps have run**, like Rails, Flyway and Liquibase.

```kotlin
interface PrefMigration {
    /** "yyyy-mm-dd.<module>.<name>", unique. Steps run in id order. */
    val id: String

    /** Every key this step touches: old key (or pattern), old stored type, and what happens to it. */
    val handles: List<HandledKey>          // kind = Move(to) | Convert(newType) | Drop | FixValue

    /** Input and expected output maps, with values. Used by the history test (3.3). */
    val examples: List<MigrationExample>

    /** Pure: reads and changes only [map]. */
    fun migrate(map: MigrationMap)
}
```

**Rules for a step:**

- **Pure.** Only the raw key/value map: no `Preferences`, `Config`, database, logger or other
  services. This removes the `if (!config.AAPSCLIENT)` checks, which exist only because
  `Preferences.put()` stamps and publishes synced keys; a step writes below that layer.
- **Safe to run twice**, and tested so: the history test runs every step again on its own output
  and expects no change. A change of meaning always moves the value to a new key, never rewrites
  it in place.
- **Tolerant of types, per platform.** Android: Boolean, Int, Long, Float, String (an import stores
  everything except booleans as String; `SPImpl.putDouble` stores a Float, but `edit {}` stores a
  double as a String). Desktop: String only. iOS: raw `NSUserDefaults` values (normalized in
  `IosSp.getAll()`). `MigrationMap` has tolerant readers; a bad value is recorded and skipped.
- **Never move or drop a synced key** (`sync != null`). Sync messages carry the key string, and
  master and clients update at different times, so a rename breaks sync silently in both
  directions. If a synced key really must change, the old wire name stays as an alias for at least
  one release.

**Order.** Steps run in id order. A checked-in list of released ids exists; CI fails when a new id
sorts before the newest released id, so a branch merged late cannot slip in front of steps that
already shipped.

**Markers.** New composed key `LongComposedKey.PrefMigrationDone` = `"pref_migration_done_%s"`,
value = time applied, **exportable**, not synced. Export carries them, so a file says which steps
it has had. A file from before this change has none, so every step runs on it.

**Unknown markers.** At startup, markers for step ids this build does not know are removed (a
downgrade to a build with the runner; a removed module). On import they are ignored and logged:
the aapsclient flavors have no pump modules, so a master's file always has markers a client does
not know. A downgrade to a build **without** the runner is not covered: that build keeps the
markers and may write old key names.

**Runner** (`PrefMigrationRunner`, commonMain; needs only `KeyValueStore` and the contributed
steps, which have no dependencies):

1. `map = store.getAll()`.
2. Handle unknown markers (above).
3. For each step without a marker, in id order: run it on a copy of the map. If it throws, drop that
   step's changes, write no marker for it and stop; the steps before it still count.
4. Write everything in one `edit(commit = true)`, in this order: puts, removes, markers. On iOS and
   desktop the edit is not atomic, so the order matters after a crash. `DesktopSp.persist()` must
   first write a temp file and rename it (prerequisite).
5. Return a report (steps run, values skipped, the failed step). The shell logs it after the write,
   because the logger itself reads settings. A failed step shows a notification.

**Where it runs:**

- **Android:** the first statement after `super.onCreate()` in `MainApp.onCreate`, before
  `resourceHelperImpl.start()` (it caches `GeneralSimpleMode`), `fabricPrivacyImpl.start()` and the
  rest. `MainApp` has no member injection; singletons are built lazily, so this is early enough.
- **iOS:** directly after `createGraphFactory<IosAppGraph.Factory>().create(...)` in
  `AapsAppHost.aapsAppViewController`, before the plugins map and `IosAppStartup` are built.
- **Desktop:** the first line of `startPlugins`, before `StringKey.GeneralLanguage` is read.
- **On import**, on the file's map, before anything is written (3.4).

**Porting today's migrations.** All of `MainApp.doMigrations()` becomes **one** step,
`2026-xx-xx.core.legacy`, with its sub-steps in their original order. They depend on each other:
step 8 produces the keys step 16 reads, and steps 9-10 feed the DIA step. Separate ids would let the
sort order break that.

| Today | Becomes |
|---|---|
| `doMigrations` steps 1-10, 12, 14, 17 | the one `legacy` step, same order |
| step 11 (DIA) and step 16 (insulin plugin) | inside `legacy`; they write hand-off keys instead of `MainApp` fields |
| step 13 (`aps_mode`) | inside `legacy`; writes a hand-off key; the RunningMode consumer runs at startup only, and an import drops this hand-off |
| `dataMigrations` (v33 insulin repair) | keeps running on every start, as today. It uses the hand-off keys when present and deletes them after its database write succeeded |
| Medtronic `migrateSettings` | own step in `:pump:medtronic` |
| `ProfileRepositoryImpl` legacy keys to JSON | a pure step if the conversion can be made pure; otherwise stays, and 3.5 calls `profileRepository.reset()` |
| step 2 SimpleMode, TT presets seed (15) | owner fills an empty value, **on the master only**; a client waits for the master (both keys are Bidirectional synced) |
| CareLevo cage defaults | not a migration: a value that depends on the active plugin; stays with the plugin |
| ActivePlugin mirror | not a migration (derived), stays in `ConfigBuilderImpl` |
| `InsulinImpl`, `AutomationRuntime`, QuickWizard GUIDs, graph config | stay with their owner (data from sync needs the same treatment) |
| `ExportPasswordDataStoreImpl` alias cleanup | not a preference; out of scope |

Hand-off keys are registered and not exportable. Each consumer has one fixed place and a build gate
in each shell, is idempotent, and on builds without a consumer the hand-off is deleted.

### 3.3 Schema snapshot and checks (build time, like Room's exported schema)

- **Snapshots split into global keys and plugin keys** (3.1.7), matching who owns them:
  - **Global:** one snapshot in `:core:keys`. All 17 global key enums are there already; the one
    non-plugin enum outside it, `LocalAlertLongKey` in `:implementation`, moves into `:core:keys`
    (it also drops the manual `registerPreferences` call in `LocalAlertUtilsImpl`).
  - **Plugin:** one snapshot in each plugin or pump module that defines key enums (19 modules
    today). A pump family's shared keys are in its shared module's snapshot (`pump/dana/common`,
    `pump/omnipod/common`, `pump/rileylink`).
  - A plugin's snapshot also lists its own concrete enabled key,
    `ConfigBuilder_Enabled_<TYPE>_<Class>`: the pattern is global, but each concrete key belongs to
    one plugin (check 3 below).
  - Removing a plugin module removes its snapshot, its keys and its steps together; the global
    snapshot never changes because of that.
- **Format:** `prefs-schema.txt`, checked in, sorted by key, one entry per line: key string or
  composed format, and storage type. Defaults, titles and `exportable` are not part of it.
  `IntentPreferenceKey` entries store nothing and are left out.
- **Collecting the keys:** a new helper that scans only the module's own compiled classes,
  including nested enums (the existing `aapsClassesOnClasspath` scans the whole classpath and skips
  nested classes). The per-module test only records and compares its own snapshot.
- **Recording works like Room's schema export** (decided 2026-09-19): a local test run adds new
  keys to the snapshot by itself, and the change shows up in `git status` to be committed. CI only
  compares, and fails when a snapshot was not committed.
- **Recording only adds.** A key that disappears stays in the file as a tombstone that names the
  step handling it; recording refuses to remove a key no step handles.
- **Global checks run once in `:app`** (and the same in `:desktop:shell` tests), across all
  snapshots and all steps:
  1. A removed key or a changed type needs a step with a `Move`, `Convert` or `Drop` for it. A
     `FixValue` does not count. The same key string with the same type appearing in another
     snapshot is a move and needs no step. That is mostly a plugin key becoming global or the
     other way round; plugin keys do not move between plugins (no plugin reads another's keys).
  2. Keys match patterns by the whole format (`%d` = digits only), not by prefix; `appwidget_` and
     `appwidget_use_black_` overlap today.
  3. Plugin enabled keys are built from the class name (`ConfigBuilderImpl.composedKeyFor`). `:app`
     computes the concrete key of every plugin in the graph and compares it with the one in that
     plugin's snapshot, so renaming a plugin class is caught as a removed key (bug 5.1 is this kind
     of bug).
  4. Ids are unique, well formed, and new ids sort after the released list.
  5. History test: every step's examples, first with typed values and then with every value as a
     String; run all steps; compare values with the expected output; run all steps again and expect
     no change.
  6. Completeness: every key enum found by the scan is contributed through the multibinding. On
     iOS, compare the graph's keys with the snapshots (Mac only).

### 3.4 Import

One confirm screen, with a checkbox **"Restore pump configuration"** (pump selection, settings,
pairing and pod or patch state):

- Shown only when the file contains pump configuration and this build has pump modules (never on
  the aapsclient flavors). Disabled, with a note, when the file's pump plugin is not in this build.
- Default: ticked when the phone has no hardware pump set up (the virtual pump is active, as on a
  new phone); not ticked when it has one, so an active pump is never replaced by accident.
- The text says what ticking means: the current pump pairing and pod or patch state are replaced
  by the ones in the file. For Insight it also says that pairing is needed again (3.1.3).
- Not ticked: every pump-configuration key is dropped from the map, and the phone keeps its pump
  exactly as it is. This is what ComboV2's `beforeImport`/`afterImport` backup does today for
  ComboV2 only; the hooks go away.

Then one app-scoped job that the screen cannot cancel:

1. Decode the file into a map. Drop device-state keys, and pump-configuration keys when the box
   is not ticked (3.1.3).
2. Run the migration steps on the map; the file's markers decide which. Keys still unknown are
   dropped with a log line.
3. **Decide from data whether the pump changed**: only possible when the box is ticked. Compare
   the active pump plugin in the store with the one in the map, and that driver's identity keys
   (each pump driver declares them). Never from a serial read live after plugins were touched.
4. Save the current store as a local backup file, so a bad import can be undone.
5. `commandQueue.withHold { ... }`, and pause client-control processing. If the pump does not
   settle, nothing is written.
6. Write in one `edit(commit = true)`: settings from the map replace the old settings; device
   state stays. **No `sp.clear()`.** On a master, imported Bidirectional keys get a stamp of
   `max(stored + 1, now)` in the same write; a client writes no stamps and follows the master.
7. Log `IMPORT_SETTINGS`.
8. Reload and apply (3.5).

If the apply fails after the write, the screen offers a retry of the apply only. Queued commands
are finished as no-op on **every** import (decided 2026-09-19), at the quiet point in 3.5 step 3:
they were decided under the old settings, and whoever waits for one is told it did not happen.
Not at the start of the hold, because the loop, automation and SMS can still queue work until they
are stopped; not at the end, because the restarted plugins queue good commands under the new
settings (a pump driver's `readStatus` after `onStart`, the loop's first run).

`ImportExportPrefsImpl.executeImport` (Android) and `PrefsTransfer.applyImported` (iOS, desktop)
become one commonMain path. ComboV2's `beforeImport`/`afterImport` backup goes away only after its
keys are device state (3.1.3).

### 3.5 Reload and apply after import (#5140)

1. `Preferences.reloadFromStore()`: every cached flow reads the store again. `FlowCache` keeps a
   reader next to each flow (also composed keys and unit doubles; replaces
   `refreshUnitDoubleFlows()`). A `StateFlow` emits only for values that changed. Runs **before**
   any plugin starts, because a starting plugin gets the cached flow.
2. Read-once objects outside plugins (section 1, item 3) follow the rule in 3.1.7: each reads
   through a getter or observes its key (like `InsulinImpl` since `36e4d2d1f3`). Until each is
   fixed, it is reloaded here explicitly (`profileRepository.reset()` unless 3.2 made it pure). The
   objective tasks are built once and `ObjectivesPlugin` has no `onStart`, so they must read through
   getters. Device state is not reloaded.
3. **Every enabled plugin restarts, around one quiet point** (agreed 2026-09-19). A restart re-reads
   everything a plugin keeps in memory, including global keys it does not own, which a targeted
   restart based on changed keys would miss. The whole sequence runs inside the pump hold:
   1. Stop every enabled plugin, awaited, in reverse order.
   2. Stop `AutomationRuntime` and wait for a run in progress (its run mutex).
   3. Cancel or wait for the running calculation.
   4. Nothing runs now and nothing can queue a command: cancel every queued command
      (`completeAllAsNoOp()`), write the store (3.4 step 6), then `reloadFromStore()` (step 1
      above).
   5. Start every enabled plugin, awaited, in order (`setPluginEnabledAwaiting`), then
      `AutomationRuntime.start()`, which re-reads the rules.
   6. Trigger a new calculation.
   No job sees a half-imported store, and no job survives from before the import.
   **Lifecycle contract:** `onStop` must undo everything `onStart` started. 41 plugins override
   `onStart`; 15 start work that a coroutine scope alone does not stop (`bindService`,
   `postDelayed`, receivers, `appScope`). Known leaks: `LoopPlugin` (two collectors in `appScope`),
   `OmnipodErosPumpPlugin` (a `postDelayed` loop). `PluginBase` gets a lifetime scope, created
   before `onStart` and cancelled and awaited after `onStop`; plugins launch their jobs there. A
   source-scan test flags `appScope` and `postDelayed` in `onStart`, and the 15 are audited.
   This needs device state to be kept (3.1.3, 3.4): a restart re-reads pod state and sync cursors,
   so it must not run on today's import, which takes those from the file.
4. Pump: `connectNewPump()` only when 3.4 step 3 found that the pump really changed (the queued
   commands were already cancelled at the quiet point). The live serial cannot be used: after the restart, a Dana or Diaconn
   driver has none until it reconnects.
5. Client: after `applyConfiguration()`, reconcile the ActivePlugin mirror with the enabled
   plugins (the kept mirror plus a StateFlow that skips equal values would otherwise leave the
   client on the file's plugin).
6. Master: request a cold republish once the pump is initialized.
7. Overview and IOB caches, as today.
8. End-to-end host test: import a file with changed automation, quick wizard, profiles, scenes,
   temp-target presets, insulin and objectives progress; check that every owner sees the new values,
   that every plugin restarted once, and that device state is untouched.

## 4. Phases

0. **Fast track for #5140, on today's import.** Decide the pump change from data
   (3.4 step 3), `reloadFromStore()` before `applyConfiguration()`, `profileRepository.reset()`,
   queued commands cancelled on every import at the start of the hold (there is no quiet point
   yet, and today's import writes before the hold).
   No additional plugin restarts. Check first that no device-state key is observed, because today's
   import still restores those from the file. Small and self-contained.
1. **Keys.** Registration, classification (the `exportable` audit), allowlist, commit write, move
   the raw keys (3.1).
2. **Runner.** Steps, markers, runner in all three shells, the `legacy` step, hand-offs, owners
   filling defaults on the master, snapshots and checks (3.2, 3.3). `DesktopSp` fix first. Golden
   tests: old `doMigrations` and the new `legacy` step give the same store for the same input.
3. **Import.** Map-based import with steps, device state kept, the "Restore pump configuration"
   checkbox, pump decision, local backup, one held app-scoped job, stamps, log, queued commands
   cancelled (3.4).
4. **Apply.** Read-once audit, the lifecycle contract (lifetime scope, scan test, audit of the 15
   plugins, the two known leaks), the quiet-point restart of every plugin, client reconcile,
   republish, end-to-end test (3.5).

## 5. Bugs to fix on the way

1. `doMigrations` step 3 writes `ConfigBuilder_APS_OpenAPSSMB_Enabled`; step 8 turns it into
   `ConfigBuilder_Enabled_APS_OpenAPSSMB`, but `ConfigBuilderImpl.composedKeyFor` reads
   `APS_OpenAPSSMBPlugin`. The key is never read (since January 2024).
2. Step 11 deletes the DIA keys before `dataMigrations` uses them; a crash in between loses them.
3. The key-moving loops use `value as Long` / `as Boolean`; one odd value aborts the startup.
4. The import writes every key in the file, including unknown and not exportable ones.
5. Pump runtime state, pump identity and sync cursors are exportable, so every import restores
   another time's or another phone's state (the old import that restarted the app did the same).
   After this plan: pump state only through the checkbox, identity and cursors never.
6. Today's import decides "pump changed" from a live serial that Dana and Diaconn keep empty until
   the next connection, so it may take that path for an unchanged pump (section 1; small effect).
7. `LoopPlugin` and `OmnipodErosPumpPlugin` leak background work on a stop and start.
8. `DesktopSp.persist()` rewrites the file in place; a crash can leave it truncated.
9. `InstaraStringKey.DeviceMetaJson` KDoc versus its `exportable`; `GoogleDriveRefreshToken` is
   exported (check whether intended).
10. Stale comments: `PluginBaseWithPreferences.beforeImport`/`afterImport` KDoc says the app
    restarts after an import; the `MainApp` comment about field injection before `doMigrations`.

## 6. Open questions

1. Wear: when, and with the same design?

Decided 2026-09-19: pump configuration is restored only through the checkbox (3.4); queued
commands are cancelled on every import, at the quiet point (3.4, 3.5); snapshots are recorded
automatically by local test runs and only compared in CI (3.3).

## 7. Review log

The first version was reviewed from six angles (facts, runner, import and sync, startup and DI,
schema checks, risk); a verifier checked every finding against the code. 46 findings, 33 confirmed,
13 partly, none refuted. Main changes:

- "Restart every enabled plugin" stays (agreed 2026-09-19, after a detour through a targeted
  restart), with conditions: one quiet point, the lifecycle contract, and only once device state is
  kept.
- Pump configuration (selection, settings, pairing, pod or patch state) restored only through a
  checkbox on the import screen; queued commands cancelled after every import (both decided
  2026-09-19).
- Keys split into global and plugin keys (agreed 2026-09-19): one global snapshot in
  `:core:keys`, one per plugin module, and the rule on who may keep a value in memory.
- Device state is classified and kept across an import; the pump change is decided from data.
- The runner moved to the true first line of each shell; failure handling and write order defined.
- Legacy steps are one ordered step; `dataMigrations` keeps running every start.
- Synced keys may not be moved; owners fill synced defaults on the master only.
- Schema checks split into per-module record/compare and global checks in `:app`; recording only
  adds, automatically on local runs and compare-only in CI; concrete plugin keys recorded; examples
  with values; exact pattern matching.
- Registration details: core keys from `:implementation`, `ownPreferences` keeps a job, allowlist
  with reasons, a commit write.
- A phase 0 fast track for #5140.
- Corrected after the review: the Dana and Diaconn `reset()` is fine; the weak point is the import's
  check on a live serial, and its effect heals on the next loop cycle.
