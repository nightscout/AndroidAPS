# Preference keys, migrations and settings import - plan

Status: **proposal, nothing implemented yet.** Written 2026-09-19, revised the same day after a
review, again on 2026-09-20 after a second review (section 7), and then after an adversarial
review that changed the order of the work (sections 4 and 8). Related issue: #5140.

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
running temporary basal in the database, and `cancelAll()` drops the queued commands. The
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
   **Who uses it today** (measured 2026-09-20, production code only, store implementations and DI
   wiring left out):
   - Maintenance, and a fair use of the raw store - they move the whole store, not single settings:
     `ImportExportPrefsImpl`, `LocalImportExportPrefs`, `PrefsTransfer`, `CloudStorageManager`,
     `GoogleDriveProvider` with its Android, iOS and desktop versions, and `GoogleTokenStore`.
   - Before DI exists, as above: `FabricPrivacyImpl` (Android and iOS), `LocaleHelper`,
     `SPBackupAgent`.
   - **Two pump drivers, which is work to do, not an allowlist entry.** CareLevo reads raw in
     `CarelevoPumpPlugin`, `CarelevoPatch`, `CarelevoAlarmNotifier` and
     `CarelevoPatchConnectViewModel` - and at least some of those keys are enums already, read
     through `.key`, for example
     `sp.getInt(CarelevoIntPreferenceKey.CARELEVO_PATCH_EXPIRATION_REMINDER_HOURS.key, 0)`. ComboV2
     does the same through `AAPSPumpStateStore`, `Delegates` and `ComboV2Plugin`.
   Nothing else. `ActiveSceneManager`, `ProfileRepositoryImpl`, `UnscentedKalmanFilterPlugin` and
   `MainApp` only name the type in a comment.
   **Why the two drivers are a prerequisite and not a tidy-up.** A raw read never sees
   `reloadFromStore()` (3.5), so those values stay stale after an import however careful the engine
   is. A raw write never stamps a synced key, and never reaches the classification in 3.1.3, so the
   import cannot tell that pump state apart from a setting. Both drivers therefore have to move onto
   `Preferences` before the import (phase 3) and the restart (phase 4) mean anything for them. 3.1.4
   lists the same two for a related reason - keys that are still plain strings - but the change that
   matters here is the caller, not the key.
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
   Restoring an Insight setup on a new phone therefore still needs pairing again. Section 3.6 says
   what to do about that state.
   **The list above is a starting point, not the design.** A kind written down in this document is a
   hand-kept list under another name, and the first new pump key will not be in it. The kind belongs
   on the key itself, next to `exportable`, written by the person who adds the key. The snapshot
   check (3.3) then fails when a new key carries no kind, and removing a module removes its
   classifications with it. Only the rule stays in this document; the data lives with the keys.
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
     measurement).
   - **The "outside plugins" rule needs its own check.** Every other rule in this section has a
     build-time gate; this one had only an audit and review, and it is the rule the whole import
     rests on. A source-scan test flags a `preferences.get(...)` in a constructor, an `init` block
     or a property initializer of a class that is not a `PluginBase`, with an allowlist for the
     cases 3.5 step 2 still fixes by hand. An audit finds today's cases once; a test keeps new ones
     out.

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

**Considered and rejected: one importer per plugin.** A plugin knows its own keys, so it could import
them itself and leave the main import only the global ones. Rejected, for reasons that are worth
keeping:

- **The plugins are stopped at that moment** (3.5), which is what makes the write safe. A stopped
  plugin cannot take part. Letting it take part would mean running it during the write and giving
  back the observer problem the stop was there to remove.
- **One write or many.** The import is one `edit(commit = true)` (step 6). Each plugin writing its
  own part is a failure point per plugin, and a crash in the middle leaves a store nobody described.
  A plugin could instead change its slice of the map without writing - but a pure function over the
  map with no services is a migration step (3.2), which already exists.
- **Migrations run on the file's map before anything is written**, and must stay pure. There is no
  place there for a plugin either.
- **The aapsclient flavors have no pump modules.** A master's file imported on a client has pump keys
  with no owner present, so the engine needs a rule for "no owner" in any case.
- ComboV2 is the one plugin that implements `beforeImport`/`afterImport` today, and it does so to
  protect its keys from the central import. That is a symptom of missing classification, not the
  start of an architecture.

What the idea gets right is ownership, and 3.1.3 takes it: the **kind** of every key is declared with
the key, by its owner, and checked by the build. Knowledge is distributed, mechanism stays central.
The one thing a plugin really must do itself is in 3.6.

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
      (`cancelAll(comment, success = false)` - `completeAllAsNoOp()` is gone, see 4.1 decision 3),
      write the store (3.4 step 6), then `reloadFromStore()` (step 1 above).
   5. Start every enabled plugin, awaited, in order (`setPluginEnabledAwaiting`), then
      `AutomationRuntime.start()`, which re-reads the rules.
   6. Trigger a new calculation.
   No job sees a half-imported store, and no job survives from before the import.
   **This is also what makes the write safe, not only the memory fresh.** Some plugins observe their
   own keys and act on a change. Three act in a way an import must not trigger by accident:
   `AbstractDanaRPlugin` on `DanaStringNonKey.RName` (`danaPump.reset()`, `pumpSync.connectNewPump(true)`,
   `commandQueue.readStatus`), `MedtronicPumpPlugin` on `MedtronicStringPreferenceKey.Serial`
   (`pumpSync.connectNewPump()`, `readStatus`), and `DanaRPlugin`/`DanaRKoreanPlugin` on
   `DanaBooleanKey.UseExtended`, which calls `extendedBolusStop()` when an extended bolus is running.
   None of them can fire on an import today, because the import writes below `Preferences` - that is
   bug #5140. Any fix that makes the write visible turns them on. With the plugins stopped the
   collectors are gone, so the write fires nothing, and `onStart` reads the new values itself
   (`AbstractDanaRPlugin` already does: `danaPump.serialNumber = preferences.get(...RName)`). Every
   one of these observers uses `.drop(1)`, so a fresh subscription does not fire for the value it
   starts with. That leaves `connectNewPump()` reachable only through the deliberate decision in 3.4
   step 3, which is where it belongs.
   **`reloadFromStore()` belongs inside the stopped window** (step 1 above), not after the plugins
   start. A plugin that has already subscribed has spent its `.drop(1)` on the old value, so a later
   reload looks like a change and fires the observer.
   **Lifecycle contract:** `onStop` must undo everything `onStart` started. 41 plugins override
   `onStart`; 15 start work that a coroutine scope alone does not stop (`bindService`,
   `postDelayed`, receivers, `appScope`). Known leaks: `LoopPlugin` (two collectors in `appScope`),
   `OmnipodErosPumpPlugin` (a `postDelayed` loop). `PluginBase` gets a lifetime scope, created
   before `onStart` and cancelled and awaited after `onStop`; plugins launch their jobs there. A
   source-scan test flags `appScope` and `postDelayed` in `onStart`, and the 15 are audited.
   This needs device state to be kept (3.1.3, 3.4): a restart re-reads pod state and sync cursors,
   so it must not run on today's import, which takes those from the file.
   **Start the plugins only after the selection is known.** `ConfigBuilderImpl.loadSettings()`
   enables the plugins first and calls `activePlugin.verifySelectionInCategories()` afterwards, so
   every `onStart` job is already running while `activePumpStore` and `activeAPSStore` can still be
   null. `PluginStore.activePumpInternal` then throws "No pump selected" (bug 5.11). At startup a
   few plugins hide this behind `config.appInitialized`, but **that flag is already true during an
   import**, so those guards do nothing here.
   Reordering alone is not enough, and the first version of this paragraph was wrong to imply it is:
   `verifySelectionInCategories()` is itself a starter. For a single-select category with nothing
   enabled it calls `setPluginEnabled(type, true)` and throws the returned `Job` away, and
   `fallbackIfNotVisible` and `getTheOneEnabledInArray` do the same for stops. It has to return its
   jobs, or gain a variant that does, and the restart has to join them - otherwise "awaited" is not
   reachable however the calls are ordered. `activePumpInternal`'s fallback also has to stop
   enabling plugins from inside a getter. See 8.A.
   **When the sequence does not finish.** A plugin that never stops, or never starts again, must
   not leave the app in a state nobody described. `applyConfiguration()` waits only
   `PLUGIN_SETTLE_WAIT`, then logs and moves on. Say what the user sees and can do in that case:
   which plugins are down, that the settings are already written, and that the local backup from
   3.4 step 4 brings back the settings but not the plugin state. A pump driver that stays down on a
   phone with an active pump is the case that matters.
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

### 3.6 State that is not in the preference store

Some state an import should care about is not in the key store at all, so no map-based import can
reach it:

- **Insight pairing.** `PairingDataStorage` keeps it in the driver's own SharedPreferences file
  (`<package>.PAIRING_DATA_STORAGE`). Measured on 2026-09-20: Insight is the **only** pump module
  that opens its own `SharedPreferences`; no other driver calls `getSharedPreferences` at all.
- **Driver history databases.** Dana, Diaconn, Equil, Insight, Omnipod Dash and Eros keep their own
  Room databases. Room is a non-goal (section 2) and stays out.

Two things follow.

**A narrow callback, not the old hooks.** When a file comes from another phone, a driver may need to
be told that what it holds outside the store is now meaningless - Insight's pairing is the case. That
is one callback with a written contract, called at a named point inside the stopped window of 3.5,
and nothing more: not a general "import your keys" hook, and not the `beforeImport`/`afterImport`
pair that 3.4 removes. A plugin with nothing outside the store does not implement it.

**Insight is a candidate for unification** (open question 2). Moving its pairing into registered keys
puts it under the same classification, export, snapshot and migration machinery as everything else,
and removes the permanent allowlist entry it would otherwise need in 3.1.2. The gain is real for the
common case, a reinstall on the **same** phone, where the Bluetooth bond still exists and only the
app's own data was lost. It does not help moving to a new phone: the bond lives in the operating
system, so pairing is needed again whatever the file holds. Weigh that against putting a pump secret
into the export file - which already carries other secrets and can be password protected.

## 4. Order of work

Rewritten on 2026-09-20 after the adversarial review (section 8). The old phase 0 is gone: it was
built on the belief that the fast track was self-contained, and it is not (8.B, finding "Phase 0's
reloadFromStore is unsafe on today's sp.clear()").

### 4.1 Decisions to settle before any code

Each of these changes what gets built, and none of them is a coding question.

1. **Does `kind` have a default?** `NonPreferenceKey.exportable` is `get() = true`, and that default
   is exactly how the pump-state keys in 8.B became exportable without anyone typing it. A `kind`
   with a default repeats that one level up, and the unsafe value (Setting) is again the natural
   default. A `kind` with no default breaks every key enum at once and has to be filled in one pass.
   Two very different pieces of work; phase 1 cannot start without the answer.
2. **What is the quiet point**, given that nothing can stop the loop between the temp basal and the
   SMB (8.A)? **HALF SETTLED AND BUILT; the other half is specified below and not built.**

   - **The queue half is done** (`21570ca902`). The recommendation below - do not stop the loop, stop a
     new enactment from starting - is implemented as `&& !commandQueue.isHeld()` in `LoopPlugin.invoke`
     and as an early return in `LoopPlugin.acceptChangeRequest` and
     `RunningModeReconciler.issueZeroTbrIfNeeded`. Two of the three have tests proven red without the
     guard; `invoke` does not, and that gap is deliberate rather than overlooked.
   - **The plugin-state half is NOT done, and it is the one users are hitting.** The hold stops commands;
     it does nothing about a plugin reading plugin state while the selection is being rebuilt. Live
     Crashlytics `PluginStore.getActivePumpInternal` - "No pump selected", 13 events / 6 users on
     `4.0.0-dev`..`dev-c`, newest 2026-09-16 - happens inside the window where `loadSettings` has
     disabled the old pump and not yet elected the new one. The `config.appInitialized` guards that
     several plugins carry were PRESENT in those builds and were passed, because `appInitialized` means
     "start up finished once", not "plugin state is valid now"; that is the part of the diagnosis that
     holds, and it is what decides the fix.

     **The caller that produced those events is `PersistentNotificationPlugin`, and its path is already
     closed** - by RxJava's undeliverable-exception behaviour, not by anything anyone designed, and by
     the Rx-to-Flow migration that removed it. Both are set out below. What is NOT established is
     whether any *other* reader is still uncontained in this window; that is the first task of the
     session that builds this. It does not block the design: the fix is the same wherever the read is,
     because it is about the window, not the call site.

     **The answer is not to soften the throw.** `PluginStore`'s "No pump selected" and its `checkNotNull`
     siblings are deliberate assertions - no nullable variant, no default fallback, no cached
     last-known value, no try/catch at a call site. See the comment block above the interface section
     of `PluginStore.kt`.

     **What to build instead:** a separate `reconfiguring` state on `Config`, with
     `appInitialized = initProgressFlow.value.done && !reconfiguring`. Every one of the ~15 existing
     `if (!config.appInitialized) return` guards then closes during an import, with no call-site changes.

     **Do NOT implement this by clearing `initProgressFlow.done`.** A six-dimension consequence analysis
     (2026-09-21, 30 findings, 16 refuted) returned seven independent blockers on that, all the same one:
     `done` is also the splash gate. `AapsAppRoot` renders
     `AnimatedVisibility(visible = !initProgress.done) { SplashScreen(...) }` over
     `AnimatedVisibility(visible = initProgress.done) { content(navController) }`, and `AnimatedVisibility`
     REMOVES the subtree from composition. Clearing `done` would (a) put the splash over a running app for
     up to 30 s with stale boot text, (b) take the import screen performing the apply off the display,
     (c) dispose and re-run every screen's `LaunchedEffect` on restore - including
     `LaunchedEffect(source) { importViewModel.startImport(source) }`, which writes `ImportStep.FilePicker`
     to the same `_importStep` that `onApplyConfirmed` writes `Applied`/`ApplyFailed` to, with no ordering
     - and (d) hide `ImportStep.ApplyFailed`, the only screen offering `retryApply()`, behind a splash with
     no Close button, after the settings are already on disk. It also re-introduces exactly what
     `ImportViewModel.finishApply` already defers `uiRestart.request()` to avoid.

     **A `CoroutineStart.UNDISPATCHED` mechanism was written here on 2026-09-21 and is REFUTED. There
     is no cheap local fix; do not build one.** The claim was that
     `PersistentNotificationPlugin.onStart` subscribes three collectors with
     `collectResilient(..., start = CoroutineStart.UNDISPATCHED)`, so the collector body runs on the
     caller's thread inside `onStart` and its throw escapes. The body does run on the caller's thread -
     that part of `ResilientCollect.kt`'s KDoc is accurate - but the throw cannot escape, because
     `collectResilient` is

         onEach { item -> try { block(item) } catch (CancellationException) { throw } catch (Throwable) { log } }
             .retryWhen { ... }
             .let { flow -> scope.launch(start = start) { flow.collect() } }

     The try/catch is INSIDE `onEach`, upstream of the `launch`. `start` selects which thread the
     launched coroutine runs on up to its first suspension; it does not move `block` outside that catch.
     So a throw from `triggerNotificationUpdate()` is logged and swallowed either way, and this plugin
     cannot be the crash source in any build that has `collectResilient` (`c42bde3538`, 2026-08-14).
     Its `config.appInitialized` guard came from `94a43687fa`, 2023-08-21, whose commit message is
     "fix crash" - the same bug, patched locally, three years earlier.

     **The real mechanism, from the code the crashing builds actually ran: RxJava.** At `88820c9811`
     this `onStart` was three `rxBus.toObservable(...).observeOn(aapsSchedulers.io)
     .subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)`. A throw from an
     RxJava `onNext` lambda is **not** routed to the `onError` handler next to it - it is wrapped as an
     `UndeliverableException` and rethrown on the scheduler thread, which on Android ends the process.
     So `fabricPrivacy::logException` never saw it, and the "No pump selected" assertion killed the app.

     **That path was closed on 2026-08-15 by `202ad40fba` ("plugins/main listens on Flow")**, which
     replaced those three subscriptions with `collectResilient` - incidentally, not deliberately, and
     the per-emission catch is what does it. Crash events continue to 2026-09-16, which is a month
     later; that is consistent with users still running `dev` builds older than that commit, since the
     affected versions are `4.0.0-dev`..`dev-c`. **Check the build ids in Crashlytics before calling
     this path fixed** - if any event is from a build at or after `202ad40fba`, the throw is coming from
     somewhere else and this whole entry is wrong again.

     The consequence for priority: this is no longer "six users are crashing today through a path that
     still exists". The `reconfiguring` flag remains the right general answer, because the window is
     real and other code reads the same accessors in it, but the urgency argument has to come from those
     readers, not from this one.

     Do not write another mechanism here without checking containment first: `PluginBase.runPhase`,
     `collectResilient`, and `pluginScope`'s `CoroutineExceptionHandler` each swallow a throw, so
     "reads `activePump` in the window" is not by itself a crash.

     **Audited 2026-09-21: all 90 production files that read the five throwing accessors, 11 surveyors
     and 102 adversarial verifiers. Zero confirmed crashes.** 101 of the 102 "this crashes" claims were
     refuted outright and one downgraded to a behaviour bug (below). Two structural facts did most of
     the refuting, and both are worth knowing before reading any future report:

     - **`ActivePlugin.activePump` never throws.** It is `get() = pumpWithConcentration()`, a provider
       that builds a `PumpWithConcentrationImpl`. Only *calling a member* on the result reaches
       `activePumpInternal` and its assertion. So `val pump = activePlugin.activePump` is safe
       everywhere, including in the import path, and a report that points at that line is pointing one
       line too early.
     - **`activePumpStore` is never deliberately set to null once elected.** The only assignments are
       the initial `null`, `= getTheOneEnabledInArray(...)` and `= getDefaultPlugin(...)`. So for most
       of an import `activePumpInternal` keeps answering with the previously elected pump.

     Read that result for what it is: the verifiers were told to default to "refuted" when they could
     not prove a crash, so it means **nothing survived a strict refutation**, not that the app cannot
     crash. It is consistent with the RxJava finding above - the readers that used to race are now
     contained, so the window is currently survivable rather than closed.

     **The window the audit missed, found by reading the election itself.** `getTheOneEnabledInArray`
     disables every enabled pump after the first *as it iterates*, and `verifySelectionInCategories`
     then does:

         activePumpStore = getTheOneEnabledInArray(pluginsInCategory, PluginType.PUMP, jobs) as Pump?
         if (activePumpStore == null) { activePumpStore = getDefaultPlugin(PluginType.PUMP) as Pump ... }

     The first line **can assign null** - that is what the `if` is for. Between the two lines both
     `activePumpStore` and `firstEnabledIn(PluginType.PUMP)` can be null at once (nothing is enabled
     yet, because the loop just disabled the runners-up and `loadPref` disabled the old pump), and a
     reader on another thread hits `error("No pump selected")`. The stores are not `@Volatile` either,
     so the visibility of these writes to other threads is unspecified. Two instructions wide, one
     thread in a hundred - which is exactly the shape of 13 events across 6 users.

     **This is not an argument for making that assignment atomic, and that fix is already rejected.**
     Computing the election into a local and assigning once would mean a concurrent reader gets the
     OLD, now-disabled pump - a stale pump driver, which is the silent-wrong-behaviour outcome the
     comment block in `PluginStore` exists to prevent. The answer stays the one Miloš gave: the code
     must not be reading plugin state in this window at all. This paragraph is evidence for the
     `reconfiguring` flag, not an alternative to it.

     **The one behaviour bug the audit did confirm:** `TriggerPumpBatteryLevel.shouldRun` (and its
     siblings in `plugins/automation`) reads the pump during an apply and silently evaluates against
     whichever pump is elected at that instant, so an automation rule can fire, or fail to fire, on the
     wrong pump's battery. No crash. The flag closes it, because `AutomationRuntime` already has an
     `if (!config.appInitialized) return`.

     **What the window really is: `sp.clear()`, and it is already bracketed.**
     `ImportExportPrefsImpl.executeImport` is `beforeImport()` / `sp.clear()` / rewrite every key /
     `afterImport()`; `LocalImportExportPrefs.executeImport` brackets `transfer.applyImported` the same
     way. Between the clear and the rewrite, every preference is gone - the plugin-selection keys
     included. Both implementations delegate to `PluginStore.beforeImport()` / `afterImport()`, so that
     pair is a single choke point for setting and clearing `reconfiguring`.

     **Three constraints on the build, each found in the code on 2026-09-21:**

     - **`reconfiguring` must be a `StateFlow`, not a `@Volatile Boolean`.** `Config.appInitialized` is
       not the only consumer. `Config.awaitInitialized(timeoutMs)` returns early on `appInitialized`
       (correct) and otherwise waits on `initProgressFlow.first { it.done }` - which is ALREADY true
       during an import, so it returns instantly and every caller walks straight through. The callers
       are `KeepAliveWorker`, which fires every five minutes and then calls `checkPump()`, and
       `SceneExpiryRunner`. Give `Config` one combined ready flow and have both members read it.
     - **The set/clear must be `try/finally`.** Neither `executeImport` has one today, so a throw from a
       `putString` would skip `afterImport()`. A `reconfiguring` flag left set means `appInitialized` is
       false for the rest of the process, and `WizardBolusExecutorImpl` refuses on it in two places: the
       user cannot bolus. That is worse than the bug being fixed, and it is the evidence for the scoped
       `config.whileApplyingSettings { }` + counter shape recommended below.
     - **It must be cleared before `ImportViewModel.applySettings` reads the pump.** That function does
       `val pump = activePlugin.activePump` immediately after `configBuilder.applyConfiguration()`.
       A flag still set there fails every import - into `ImportStep.ApplyFailed` with the settings
       already written to disk, which is the half-applied state the comment above it warns about.

     **The flag is cheap, and cheaper than it looked.** `Config.appInitialized` is a derived property
     defined in exactly one place (`Config.kt`, `get() = initProgressFlow.value.done`). Making it
     `done && !reconfiguring` closes about 35 existing `if (!config.appInitialized)` gates across `app`,
     `implementation/bolus`, `plugins/{automation,configuration,sync}`, `ui` and `ComposeMainActivity`,
     with no call-site changes - and without touching `initProgressFlow`, so none of the four splash
     blockers above apply. Ten of those gates are hand-written defences against this very bug; three of
     them (`TizenPlugin`, `DataHandlerMobile`, `XdripPlugin`) name `verifySelectionInCategories` in
     their comments.

     **Constraints on the build, from the same analysis:**
     - the restore must be a `finally` INSIDE `applySettings()`, ideally a scoped
       `config.whileApplyingSettings { }` so it cannot be forgotten, and a counter rather than a boolean;
     - set and clear it INSIDE the `withHold` block, after the wait - otherwise a watch "cancel bolus" is
       dropped while a bolus is actually delivering;
     - the wear handlers need a state that ANSWERS the watch ("the phone is reconfiguring, try again");
       reusing `appInitialized` inherits fifteen silent returns unchanged;
     - neither flag subsumes the other: all three `isHeld()` guards stay necessary.
3. ~~**What does "cancel the queued commands" mean**, given that `completeAllAsNoOp` reports
   `success = true` and the loop reads that as enacted (8.A)?~~ **SETTLED AND BUILT.**
   `completeAllAsNoOp` no longer exists. `16147121cc` replaced it with
   `CommandQueue.cancelAll(comment, success)` routed through `Command.cancel`, exactly as the
   recommendation below proposed, and the import passes `success = false`
   (`ImportViewModel.applySettings`). `clear()` deliberately keeps `cancelled = false`, because a
   connection timeout IS a delivery failure and must still raise its alarm.
   One correction to the recommendation below: it says the alarm cannot be suppressed because
   "`PumpEnactResult` holds a resolved `String` and every consumer branches on `success`". That was
   overtaken by `8f13138e29`, which added `PumpEnactResult.cancelled` - a dropped command is now told
   apart from a failed one, and `CommandQueueImplementation.postProfileWriteResult` returns early on
   it rather than posting `FAILED_UPDATE_PROFILE`.
4. **Replace or merge?** Dropping `sp.clear()` changes what an import means, and the removal rule is
   not written anywhere (8.B).
5. **ComboV2: device state or pump configuration?** 3.1.3 and 3.1.4 say different things, and the
   answer decides whether the restore checkbox can work at all (8.B).

**Recommendations.** Written 2026-09-20, then stressed by three reviewers each (does it work against
the code, does it make a pump user worse off, is the cost as stated) whose default answer was
"refuted". Four of the five came back changed; the scores are given so the weak ones are obvious.
They are still proposals - each needs a yes or a different answer.

**1. `kind` has no default anywhere.** (2 of 3 refuted: the answer stands, the reasoning did not.)

- The cause written here first was wrong, and 8.B repeated it. `NonPreferenceKey.exportable`
  `get() = true` is **not** how the pump-state keys reached the export: `StringNonKey` declares
  `override val exportable: Boolean = true` as a **constructor parameter with a default**, which
  shadows the interface getter, and the interface default was added later (`63f8deb434`). The
  mistake matters, because it means an abstract member cannot do the work on its own.
- An abstract `kind` on the interface obliges the enum **class** to supply it once, not each entry.
  The cheapest legal answer is then `override val kind: PrefKind = PrefKind.Setting` in the
  constructor, and every entry of that enum is silently a setting with the compiler content. About
  40 key enums already use exactly that idiom for `exportable`, `sync` and `preferenceType`.
- So the rule is: **abstract on the interface and no default value in any key enum's constructor**,
  with a source scan (or reflection over the primary constructor) failing the build when one appears.
  Apply the same scan to `exportable` in the same pass - it has the identical hole and it is the one
  that already did harm.
- The earlier "if a default is unavoidable, make it device state" is deleted. Device state means the
  key is not exported, so on a new phone it falls back to its own default - and the defaults are
  deliberately usable, not minimal: `SafetyMaxBolus` 3.0 U, `ApsSmbMaxIob` 3.0 U, `ApsMaxBasal`
  1.0 U, `SafetyMaxCarbs` 48 g. A user who set max bolus to 0.5 U would get 3.0 U back. On the
  safety keys, device state is the failing-**open** side. Neither default is safe, which is a
  stronger argument for none than the one written first.
- The snapshot must record `kind` per key (8.D), and it has to ship **with** the classification in
  4.2 step 2, not later. 516 classifications with no check behind them is worse than a known gap,
  because a guard that is believed removes the reason to build the real one.
- Two cases need an answer in the same pass: `IntentPreferenceKey` stores no value, so give it a
  fourth kind (`NotStored`) rather than a classification the checker throws away; and
  `BooleanComposedKey.ConfigBuilderEnabled` is one constant covering every plugin in the app, so it
  cannot carry a single kind - see decision 4.
- If the pass has to be staged, stage it with a shrinking allowlist of unclassified enums, never
  with a default.

**2. Do not stop the loop and do not wait for it under the hold.** (3 of 3 refuted. The goal stands;
the mechanism written first was a deadlock.)

- Why it deadlocks: `withHold` raises `held` **before** it waits, and `CommandExecutor` skips every
  pickup while `isHeld()`. The loop's enactment is two queue calls - `tempBasalAbsolute`, then
  `bolus` - each awaiting a deferred only the executor completes. `performing` clears the moment the
  temp basal finishes, which is exactly when the loop needs the queue for the SMB. Under the hold
  that SMB is never picked up, so an "enactment in progress" signal never clears and the import
  always times out - in the one window the signal exists for. Then `CommandSMBBolus` refuses the
  bolus as too old and `LoopPlugin` launches a `tempBasalFallback` run a second later, inside the
  restart window. The mechanism produces both harms it was meant to prevent.
- Build instead: **stop a new enactment from starting, do not observe a running one.** Extend the
  guard in `LoopPlugin.invoke` (`isChangeRequested() && !commandQueue.bolusInQueue()`) with
  `&& !commandQueue.isHeld()`; `isHeld()` is already on the interface. Add the same guard to
  `LoopPlugin.acceptChangeRequest`, which enacts a temp basal outside `invokeMutex` and is reachable
  from the phone and the watch, and to `RunningModeReconciler.issueZeroTbrIfNeeded` - that one is
  worse than the loop, because a hold granted between its `cancelExtended()` and its zero temp basal
  leaves full basal running while the app believes the pump is disconnected. A skipped loop run
  costs one cycle; a dose decided under the old settings and delivered under the new ones is the
  harm being avoided.
- **"Abort with nothing written" is not true today.** `ImportViewModel.confirmImport` runs
  `executeImport` - `sp.clear()` and the writes - *before* `onApplyConfirmed` takes the hold, so a
  timeout already leaves the store written and the plugins not restarted, which is #5140's own
  shape. Move `executeImport` and `prepareImportedSettings` inside the hold block (3.4 steps 5 and
  6). The local backup from step 4 is still on disk after an abort; say so.
- Closing the residual case - an enactment already in flight when the hold is granted - needs a
  delivery mutex taken **before** the hold by all four multi-command regions (`LoopPlugin.invoke`,
  `acceptChangeRequest`, `RunningModeReconciler.issueZeroTbrIfNeeded`, `WizardBolusExecutorImpl`'s
  batch). That is a change to the dosing path in three modules with its own device testing. It is a
  separate item, not a line inside the #5140 fix.
- The abort screen needs an exit: say that suspending the loop or disconnecting the pump first makes
  the import succeed, and that a hold which timed out has itself held the pump queue for
  `PUMP_WAIT`. Most of the machinery already exists (`withHold`, the `performing` wait, the timeout,
  the "pump busy" screen); only the guard and the move are new.
- 3.5 step 3 needs the same order: stopping `LoopPlugin` while `invoke()` is suspended on a command
  deferred hits the same wall from the other side. The guard comes before the stop.

**3. The import gets its own drain, with `success = false`.** (0 of 3 refuted - the only one that
survived unchanged. It needs neither phase 1 nor phase 2 and can be done now.)

- Use **one** method, not two: `Command.cancel(comment, success = true)` already exists and
  `PumpEnactResultObject.enacted` is false by default, so replace `completeAllAsNoOp` with
  `cancelAll(comment, success)` routed through `Command.cancel`. That fixes the bypass - only
  `CommandBolus` and `CommandSMBBolus` override `cancel`, both to clear `BolusProgressData` - and
  gives the import its `success = false` in the same change. Two near-identical drains differing by
  a boolean is how this bug arrived; do not add a third. `clear()` already cancels with
  `success = false` and its comment says why: "so a waiting bolus caller is not told a dose was
  delivered".
- Drop the earlier line about checking the alarm. A reason cannot carry that: `PumpEnactResult`
  holds a resolved `String` and every consumer branches on `success`. Keep the alarm and say so,
  or someone will thread a reason field through 92 files to silence it.
- The harm was stated too loosely. `LoopPlugin` writes no `TemporaryBasal` row, so "records a temp
  basal that never happened" invites dismissal. The load-bearing harms are: `applySMBRequest` runs
  behind a temp basal that never reached the pump, and the SMB is delivered after the import under
  the new settings; `postProfileWriteResult` writes an `EffectiveProfileSwitch` for a profile the
  pump never received; `CommandQueueImplementation.bolus` persists carbs for insulin never given.
- Decide the drain condition in the text: today `applySettings` drains only when
  `verifyPumpIdentification` fails. Either keep that, or drain on every import and say that an
  unchanged pump now loses its queued commands too.
- `CommandQueueImplementationTest` has no coverage of the drain and `ImportViewModelTest` only
  checks that it was called. Assert that a drained `CommandBolus` reports `success = false`,
  `enacted = false` and clears `BolusProgressData` for its generation.
- The `CommandExecutor` "pump not configured" drain has the same `success = true` problem and the
  same consequences. Raise it as its own item rather than folding it in.

**4. Replace is the intent; the removal rule written first must not ship.** (3 of 3 refuted.)

- The file cannot carry the rule. `PrefsTransfer.exportableValues()` is
  `store.getAll().filterKeys { isExportable(it) }` and nothing calls `setDefaultValues`, so an export
  holds only keys with a stored entry. "Absent from the file" therefore means three different
  things - left at the default, not present in that build, not present in that flavour - and the
  rule reads all three as "put it back to default".
- Removal is also not the same as writing the default. `simple_mode` defaults to **true**, so
  removing it puts an advanced phone into simple mode, after which `ApsMaxBasal` and `ApsSmbMaxIob`
  are computed and the stored values ignored. Removing `master_password` takes the bolus and
  application locks off. A carer importing a file from a second phone, pump box unticked, can lose a
  child's bolus lock and get 3.0 U max bolus in place of 0.5 U, silently.
- So: **export every registered exportable key with its effective value**, not only the stored ones.
  Then absence honestly means "that build did not have this key" and removal becomes sound. Until
  such a file exists, **merge**, gated on the file version, and say so in one line on the confirm
  screen. That is not a permanent merge - it is refusing to delete settings on evidence the file
  cannot carry.
- Never remove a lock or a mode (protection keys, PINs, `simple_mode`). Never remove a key that
  cannot be classified - unknown and unregistered keys are kept and logged as orphans, which makes
  3.1.6 load-bearing and moves it into phase 2. State the lookup order: exact key, then anchored
  `ComposedKey` prefix, else never remove (`Preferences.get(key: String)` is exact-match and
  `isExportableKey`'s prefix match is unanchored, so it answers a different question). Exempt the
  `PrefMigrationDone` markers and the legacy per-profile keys `LocalProfileData`'s KDoc keeps on
  purpose.
- Stop claiming this keeps `PrefsTransfer.applyImported`'s documented behaviour: that KDoc describes
  a full `store.clear()`, and this deliberately narrows it. The honest summary is "replace what we
  know, keep what we do not".
- `BooleanComposedKey.ConfigBuilderEnabled` cannot carry one kind. Marked setting, it deletes the
  phone's own pump selection whenever the file lacks that driver - an aapsclient export, or an older
  build - and `verifySelectionInCategories` then falls back to `VirtualPumpPlugin`, so the user
  wakes up looped onto a virtual pump. Marked pump configuration, no plugin selection is restorable
  with the box unticked. Compute these keys in `:app` from the live plugin list (8.D needs the same
  exposure), mark the `PUMP` ones pump configuration and the rest setting, and call `storeSettings`
  once after the import so every plugin in this build has an explicit value.
- Split by phase: "and every pump-configuration key when the box is ticked" belongs with the
  checkbox in 4.2 step 5. **Phase 3 must keep writing the file's pump keys exactly as today**, or a
  fresh-phone restore comes back with no pump configured for two whole phases. The plan has to state
  that.
- Removal is the only irreversible half of an import, and the step 4 backup holds exportable keys
  only. Make it a full store snapshot, show the user the list of keys that will be removed before
  the write - both the store and the map are already in hand - and log each removal next to
  `IMPORT_SETTINGS`. If a synced key is removed, bump `SyncedPrefModified` and emit on
  `syncedLocalChanges` in the same write, or master and client disagree with nothing saying why.

**5. ComboV2: split the keys. The 4 settings are pump configuration; the 11 state keys are device
state.** (3 of 3 refuted - the earlier "all pump configuration" was wrong, and 3.1.4 was right.)

- Pump configuration, exportable, checkbox-gated: `ComboIntKey.DiscoveryDuration`,
  `ComboBooleanKey.AutomaticReservoirEntry`, `AutomaticBatteryEntry`, `VerboseLogging`. That is what
  a Combo user wants back and it carries no risk.
- Device state, `exportable = false`: the 11 `AAPSPumpStateStore` keys (`ComboStringNonKey`,
  `ComboIntNonKey`, `ComboLongNonKey`). 3.1.4's sentence stays; 3.1.3 loses its ComboV2 clause.
- **The nonce is a per-packet counter.** `TransportLayer` increments it for every outgoing DATA and
  ACK, and the heartbeats send about once a second, so a connected pump burns hundreds per session.
  Recovery is bounded at `MAX_NUM_REGULAR_CONNECTION_ATTEMPTS` x `NONCE_INCREMENT` - about 1000 -
  after which the driver's own comment says to re-pair. A file more than a few hours old carries a
  nonce that cannot connect, so the restore a checkbox would promise is not deliverable for this
  pump.
- **Five keys are live session state.** `TbrTimestamp`, `TbrPercentage`, `TbrDuration`, `TbrType`
  and `UtcOffset` feed `getCurrentTbrState`, which `Pump.kt` reconciles against the pump's main
  screen on every connect: one case ends a real temp basal immediately, another emits `TbrEnded`,
  which `ComboV2Plugin` turns into `syncStopTemporaryBasalWithPumpId` at the **file's** timestamp -
  a treatment for something that never happened, so wrong IOB and wrong later dosing. Same rule 8.B
  applies to `PumpCommonBolusStorage`.
- It would also **remove a protection that exists today**: `AAPSPumpStateStore.createBackup` /
  `applyBackup`, driven by `beforeImport`/`afterImport`, always restore the live state over the
  file's. Ticking the box on a phone with a working Combo would replace a good nonce with a stale
  one. With the keys as device state there is no such window, and the hooks can then go on 8.B's
  condition ("once the import no longer clears the store") with nothing needed in their place.
- **The general rule to write into 3.1.3:** pairing state that contains a monotonic counter is
  device state, because restoring it does not restore a pairing - it installs a link that cannot
  authenticate. That answers open question 2 for Insight the same way: `PairingDataStorage`
  persists `lastNonceSent`/`lastNonceReceived` and has no retry loop at all. Drop the earlier
  "same treatment as Insight" line, which assumed the opposite.
- When the file's active pump is ComboV2, the confirm screen says the pump needs pairing again on
  this phone, and the four settings are still restored. Secondary: the ciphers are pump-control
  secrets and `PrefsFormatCodec` only encrypts when a master password is set, so
  `exportable = false` also keeps them out of every plaintext export and cloud upload.

**What gates what.** Decision 3 needs nothing and can be done now. Decision 1 gates the keys work
(4.2 step 2). Decisions 2 and 4 gate the import (4.2 step 3). Decision 5 is settled by 3.1.4 as it
already stands, and only removes a clause from 3.1.3.

### 4.2 The order

0. ~~**Re-derive bug 5.11 before spending anything on it.**~~ **DONE 2026-09-21, and the guess in this
   step was wrong.** The build ids were checked in the Firebase console. The issue is
   `PluginStore.getActivePumpInternal` - "No pump selected", **13 events / 6 users**, versions
   `4.0.0-dev`..`dev-c`, newest event 2026-09-16, tagged "Regressed issue". `4.0.0-dev-c` was created
   2026-09-08, **after** the 2026-08-19 gate, and 8 of the 13 events are on it - so the events do NOT
   predate the gate. Reading the crashing source at that tag, the `if (!config.appInitialized) return`
   guard was present and was PASSED, i.e. the flag was true: this is a plugin restarted during a
   settings import, not a start up. **So it is a change, not a test** - see 4.1 decision 2 for the
   design. A test was added anyway (`PluginStoreActivePumpTest`), but it pins only that the getter
   answers without writing; it does not fix this crash and says so.
1. **Make the plugin lifecycle honest.** `SupervisorJob` and a lifetime scope on `PluginBase`;
   `onStop` undoing `onStart` for the plugins that do not; `verifySelectionInCategories` returning
   its jobs; the foreground-service case; the scan test widened past `onStart` (all of 8.A). This
   was phase 4 and has to come first: the stop/write/start primitive, the import and the restart all
   stand on it, and today it cannot carry them.

   **Status 2026-09-21 - mostly built, one item left.** Done and CI-green on `dev`: the private
   `lifecycleScope` with a `SupervisorJob` and a handler, plus a handler on `pluginScope`
   (`3761a48629`, `430982af5f`); `verifySelectionInCategories` returning its jobs and no longer
   writing from a getter (`a37f6e81e1`); the foreground-service restart (`133e9f875b`); the scan test
   widened, including a `pluginScope.launch` inventory with a per-site decision and a launch count
   (`6951fb7a7e`, `1cc844c8e4`); transitions serialised under a lock (`9344233e5b`); plan bug 12
   (`ea560ad5e8`); the three `isHeld()` guards (`21570ca902`).

   **Left:** (a) the plugin-state quiet point - 4.1 decision 2, the one users are hitting; (b) four
   `survivesStop` entries in `PluginLifetimeWorkScanTest`, worst `LoopPlugin#invoke`'s
   `appScope.launch { delay(1000); invoke(...) }`, which reschedules a loop run into the restart
   window. Note (a) is a *blocker for 3.5 step 3*, so step 3 is still gated.

   **Settled while doing this, and not to be re-opened:** making `pluginScope` per-enable was proposed
   and rejected - cancelling it does not withdraw a queued command, so 12 of the 14 launch sites would
   lose their result without preventing anything, and `OmnipodDashPumpPlugin.handleCommandConfirmation`
   must not be cancelled at all. The reasoning is on the `pluginScope` declaration.
2. **Keys: registration and classification, with `kind` enforced by the build.** The `exportable`
   audit, the allowlist, the commit write (3.1); the three pump-state keys that live in `:core:keys`
   and that a module-owner classifier cannot see (8.B); CareLevo and ComboV2 off the raw store
   (3.1.2). The snapshot has to record `kind`, or none of this is checked (8.D).
3. **Fix #5140 for settings only.** Migrate the whole map, then classify, then drop, then one write,
   then reload - with the plugins stopped (3.4, 3.5). Only safe after 1 and 2. This is the part the
   reported data loss is about, and it is a small part of this document.
4. **Runner, markers, snapshots, checks** (3.2, 3.3). Independent of the import; a second person can
   take it in parallel with 3. `DesktopSp` fix first; `handles` measured rather than declared (8.C).
5. **Pump configuration checkbox and the full restart** (3.4, 3.5). Last, because it needs all of
   the above, and because the checkbox without the restart is a promise the app cannot keep.

**Scope, stated plainly.** One reviewer counted four projects here: key registration, migrations,
import, and plugin lifecycle. Only a small part of one of them is the #5140 fix. Splitting them is
allowed; shipping 3 before 1 and 2 is not.

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
7. **PARTLY FIXED.** `LoopPlugin` and `OmnipodErosPumpPlugin` leak background work on a stop and
   start. Done: `LoopPlugin`'s collectors are held in `collectors` and cancelled in `onStop`
   (`bec12877b2`); `OmnipodErosPumpPlugin.onStop` removes its `loopHandler` callbacks, deliberately
   without quitting the looper (`4d8d7e2ff3`); `XdripPlugin` removes callbacks before `quitSafely`
   (`334b25b628`). **Still leaking:** the four entries in
   `PluginLifetimeWorkScanTest.survivesStop` - worst `LoopPlugin#invoke`'s
   `appScope.launch { delay(1000); invoke(...) }`, which reschedules a loop run one second later,
   inside the restart window. That list is the worklist and should only ever get shorter.
8. `DesktopSp.persist()` rewrites the file in place; a crash can leave it truncated.
9. `InstaraStringKey.DeviceMetaJson` KDoc versus its `exportable`; `GoogleDriveRefreshToken` is
   exported (check whether intended).
10. Stale comments: `PluginBaseWithPreferences.beforeImport`/`afterImport` KDoc says the app
    restarts after an import; the `MainApp` comment about field injection before `doMigrations`;
    the `IntentKey` KDoc list (`SmsIntentKey` is in `:plugins:sync` now).
11. **STILL LIVE, and re-measured 2026-09-21 - the cause below was only half right.**
    `ConfigBuilderImpl.loadSettings()` launches every plugin's `onStart` before
    `activePlugin.verifySelectionInCategories()` picks the active plugins, so a plugin that reads
    the active pump in `onStart` can hit `PluginStore.activePumpInternal`'s "No pump selected".
    Crashlytics `PluginStore.getActivePumpInternal`: **13 events on 6 users** (not 19/9 - that
    figure was from a wider window), `4.0.0-dev`..`dev-c`, newest 2026-09-16, through
    `PersistentNotificationPlugin.onStart` -> `triggerNotificationUpdate` -> `ProcessedTbrEbData` ->
    `PumpWithConcentrationImpl.isFakingTempsByExtendedBoluses`.

    What the build ids showed: the `config.appInitialized` guard was PRESENT in the crashing builds
    and was **passed**, so this is **not** the start-up ordering above - it is the import window,
    where the flag is already true. The last sentence of the original entry was the right half.
    The fix is 4.1 decision 2, and it is **not** to soften `PluginStore`'s assertions (deliberate -
    see the comment block above the interface section there) nor to clear `initProgressFlow.done`
    (that is the splash gate; seven blockers). Blocker for 3.5 step 3.

    **Corrected 2026-09-21 (second pass).** How the throw became a crash: those builds subscribed with
    RxJava - `.subscribe({ triggerNotificationUpdate() }, fabricPrivacy::logException)` - and a throw
    from an RxJava `onNext` lambda does **not** reach the `onError` beside it. It is wrapped as an
    `UndeliverableException` and rethrown on the scheduler thread, ending the process. `202ad40fba`
    (2026-08-15) replaced those three subscriptions with `collectResilient`, whose per-emission
    `catch (Throwable)` swallows it, so **this particular path is closed on `dev` today** - by
    accident, as a side effect of the Rx-to-Flow migration. Verify against the Crashlytics build ids
    that no event comes from a build at or after `202ad40fba` before treating it as fixed. The window
    itself is untouched and decision 2 still has to be built; what changes is that the urgency must be
    argued from whichever readers are still uncontained, not from this one.

    A `CoroutineStart.UNDISPATCHED` explanation was written for this on 2026-09-21 and is wrong -
    `collectResilient` catches inside `onEach`, upstream of the `launch`, so `start` cannot let a
    throw escape. See 4.1 decision 2.
12. ~~The two start paths give different guarantees.~~ **FIXED, `ea560ad5e8`.** `initialize()` now
    returns its start jobs instead of dropping them, and `MainApp` waits on them bounded by 30 s -
    matching `applyConfiguration`'s `PLUGIN_SETTLE_WAIT`. It could not wait internally: it is not
    `suspend`, and several instrumented tests call it, so the contract is that it hands the jobs back.
    The wait sits immediately before `runningModeReconciler.start()`, whose startup-drift check reads
    the active pump - previously against plugins that were enabled but not started. Pinned by
    `ConfigBuilderImplTest.initialize hands back the jobs its plugins are starting on`, beside the
    existing `applying the configuration waits for the plugins to start`, so the pair reads as one
    contract. Note this does **not** fix 11: `loadSettings` still schedules `onStart` before electing,
    so the ordering is unchanged - what changed is that "plugins have started" is now true when
    `initialize()` returns.

## 6. Open questions

1. Wear: when, and with the same design?
2. Insight pairing: move it into registered keys (3.6), so "restore pump configuration" also works
   after a reinstall on the same phone and the raw-store allowlist loses an entry? The counter-weight
   is a pump secret in the export file.

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

### Second review, 2026-09-20

Read against `dev` after #5140 was filed. Five changes:

- **The plugin start order is a live bug, not only an import concern.** `loadSettings()` starts the
  plugins before `verifySelectionInCategories()` selects them (3.5 step 3, bug 5.11). The
  `config.appInitialized` guards that hide it at startup do nothing during an import, because the
  flag is already true by then.
- **The two start paths differ** - `initialize()` does not wait for the start jobs,
  `applyConfiguration()` does (bug 5.12). The lifecycle contract should hold for both; otherwise
  the import is fixed and startup keeps the race.
- **The "outside plugins" rule gets a source-scan test** (3.1.7). It was the only rule in that
  section without a build-time gate, and it is the one the import depends on.
- **Phase 0 gets a gate on device-state keys** (section 4). Without it, `reloadFromStore()` would
  publish the file's pod state, pump identity and sync cursors into live flows, making bug 5.5
  worse while fixing #5140.
- **The failure end state of the whole-app restart is named** (3.5 step 3): a plugin that does not
  come back, on a phone with an active pump, is the case to describe before building it.

Added later the same day, after working through the import with fresh measurements:

- **Stopping the plugins is what makes the write safe**, not only what refreshes memory (3.5 step 3).
  Three pump observers act on a change - `RName` and `Serial` call `connectNewPump()`, `UseExtended`
  stops a running extended bolus - and they are dormant only because today's import writes below
  `Preferences`. Any fix for #5140 wakes them. This also rewrites phase 0, which is no longer
  self-contained (section 4).
- **`reloadFromStore()` must run while the plugins are stopped.** After they start, `.drop(1)` is
  already spent and a reload looks like a change.
- **The classification of a key belongs on the key**, not in a list in this document (3.1.3). The
  list here rots the first time someone adds a pump key.
- **One importer per plugin was considered and rejected** (3.4), with the reasons written down so it
  is not proposed again. The plugins are stopped at that moment, which is the point.
- **Out-of-store state gets its own short section** (3.6). Insight is the only pump module with its
  own SharedPreferences file; everything else outside the store is Room history, which is a
  non-goal. Whether to fold Insight pairing into registered keys is open question 2.
- **The raw-store users are measured, not assumed** (3.1.2). Outside maintenance and the three
  bootstrap cases there are exactly two: CareLevo and ComboV2, both pump drivers, both reading and
  writing below `Preferences`. They are a prerequisite for phases 3 and 4, because a raw read never
  sees `reloadFromStore()` and a raw write never reaches the classification.
## 8. Adversarial review, 2026-09-20

Seven lenses read the plan against `dev`, then two skeptics judged each finding with "refuted" as the
default answer. 88 findings, 87 after removing duplicates, **all 87 judged: 28 survived, 59 were
refuted.** A refusal here means one of two things: the finding was wrong about the code, or the plan
already answers it - several of the later ones were refused because an earlier revision of this
document had already taken them.

Every item below survived two skeptics who were told to refute it. Each names the section it hits.

### 8.A The plugin lifecycle cannot carry the restart yet

This is the group that reorders the work (4.2 step 1). All of it is about 3.5 step 3.

- ~~**A throwing `onStart` can make every later restart a no-op.**~~ **FIXED, `3761a48629`.**
  `onStart`/`onStop` no longer run on `pluginScope` at all - they run on a private `lifecycleScope`
  with its own `SupervisorJob` and exception handler, and `runPhase` catches everything a phase can
  throw, records it in `PluginBase.lastStartFailed` and raises an URGENT notification. `pluginScope`
  also carries a `SupervisorJob` now, plus a handler (`430982af5f`) - without one a throwing plugin
  launch reached the thread's default handler and ended the process. A failed pump driver reports
  `isInitialized() == false` through `PumpWithConcentrationImpl`, so the dosing gates refuse it.
- ~~**Nothing stops the running loop.**~~ **ADDRESSED, `21570ca902` - by not trying to stop it.**
  `CalculationExecutor.waitForPrepare` still covers only the prepare phase, so waiting for a running
  enactment remains impossible and waiting for it under the hold still deadlocks (`withHold` raises the
  flag before it waits, so the loop's second queue call is never picked up). The answer was to stop a
  NEW enactment from starting instead: `&& !commandQueue.isHeld()` in `LoopPlugin.invoke`, and early
  returns in `LoopPlugin.acceptChangeRequest` (which enacts outside `invokeMutex` and is reachable from
  the watch) and `RunningModeReconciler.issueZeroTbrIfNeeded` (worst of the three - a hold granted
  between its `cancelExtended()` and its zero TBR leaves full basal running while the app believes the
  pump is suspended). Sub-steps 3 and 4 are buildable once reworded this way.
- **Plugin state is not quiet during an import, and nothing says so.** The hold covers the command
  queue; it does not stop a plugin restarted by `loadSettings` from reading an active plugin that is
  mid-election. Live in Crashlytics. See 4.1 decision 2 for the mechanism, the evidence, and why the
  fix must NOT be to clear `initProgressFlow.done` or to soften `PluginStore`'s assertions. **Blocker
  for 3.5 step 3.**
- ~~**`completeAllAsNoOp` tells the loop the cancelled command succeeded.**~~ **FIXED, `16147121cc`.**
  Replaced by `cancelAll(comment, success)` routed through `Command.cancel`, with the import passing
  `success = false`. The last caller that still passed `true` - `CommandExecutor`, when the pump is
  selected but not configured - was flipped in `a837bb1f86`; it had been persisting the accompanying
  carbs for insulin that never left the pump, because `bolus()` writes them on a successful result.
  `PumpEnactResult.cancelled` (`8f13138e29`) keeps all of this silent: a dropped command is told apart
  from a failed one, so nothing raises the delivery alarm. `clear()` deliberately keeps
  `cancelled = false`, because a connection timeout IS a delivery failure and must still alarm.
- **`completeAllAsNoOp` also bypasses `Command.cancel`.** It calls `callback?.result(...)?.run()`
  directly, so `CommandBolus.cancel`/`CommandSMBBolus.cancel` never run and `BolusProgressData`
  stays started. `CommandQueueImplementation.clear()` does go through `cancel`. One line to fix, and
  a bug today.
- **Stopping `PersistentNotificationPlugin` stops the foreground service.** `onStop()` calls
  `dummyServiceHelper.stopService(context)`, and `DummyService` is what keeps AAPS out of the
  background execution limits. The plugin is `alwaysEnabled`, so "stop every enabled plugin"
  includes it, and `onStart` does not start the service again. **Done**: `onStart` now starts it,
  deferred and idempotent - the missing half of this plugin's own `onStop`. Exempting `alwaysEnabled`
  plugins from the sweep was the alternative and was rejected: it would leave the plugin running while
  everything it reports on is restarting, and the asymmetry would still be there for anything else
  that stops a service.
- **`verifySelectionInCategories` starts plugins itself and drops the jobs**, for six categories. So
  "start every plugin, awaited" is unreachable however the calls are ordered, and the note added
  earlier to 3.5 was wrong to suggest that reordering alone fixes it. It has to return its jobs, and
  `activePumpInternal`'s fallback has to stop **disabling** plugins from inside a getter. (Corrected
  2026-09-21: this bullet said "enabling". The fallback calls `getTheOneEnabledInArray`, which keeps
  the first enabled pump and disables every later one - a write, and dropped `onStop` jobs, during a
  property read.) **Both done**: `verifySelectionInCategories` returns `List<Job>`, `loadSettings`
  adds them to the list `applyConfiguration` already waits on, and the getter is a pure read.
- **No scan test exists.** 3.5 step 3 and an earlier version of this bullet read as though one did.
  When it is written it must scan the whole plugin class, not `onStart`: the work that survives a stop
  is launched from ordinary methods, and `LoopPlugin.invoke` ends its SMB branch with
  `appScope.launch { delay(1000); invoke(...) }`, which lands inside or just after the restart window
  and queues pump commands. **Done**: `PluginLifetimeWorkScanTest` in `:app/src/testFull`. It looks for
  `appScope`, `GlobalScope`, `postDelayed`, `Handler(`/`Thread(` across the whole class and makes every
  hit declare itself either reviewed-safe or survives-stop. Two limits worth knowing: it reads source
  text, so it cannot see work a helper class schedules on the plugin's behalf, and it does not look at
  `WorkManager` or `AlarmManager`, which outlive the process and are a separate problem. Raw
  `CoroutineScope(` is deliberately not matched - created in `onStart` and cancelled in `onStop` is the
  correct idiom that 25 plugins already use, and flagging it buried the real hits in noise.
- **Two pump-safety singletons outside plugins are missing from the reload list** (3.5 step 2):
  `PumpSyncStorage`, guarded by a one-shot `storageInitialized` flag, and
  `DetailedBolusInfoStorageImpl`.

### 8.B The import: classification and semantics

- **Pump runtime state sits in `:core:keys`, where a module-owner classifier cannot see it.**
  `StringNonKey.PumpCommonBolusStorage`, `PumpCommonTbrStorage` and `BolusInfoStorage` are not in a
  pump module, are exportable, and are absent from the device-state list. (They are exportable
  because `StringNonKey` gives its `exportable` constructor parameter a default of `true`, which
  shadows the interface getter - not because of the interface default, which came later. See 4.1
  decision 1.) They
  hold `temporaryId` values pointing at rows in *this* phone's database, so they mean nothing on any
  other phone. The reviewers corrected the damage: a foreign temporary id matches no row, so nothing
  is inserted; the harm is that `MedtronicHistoryData.findDbEntry` matches on a two minute window
  with no amount check, takes the `temporaryId != null` branch, and a bolus that really was delivered
  is then never written. Classify all three as **device state**, and say in 3.1.3 that global enums
  holding pump or device runtime state have to be classified by hand.
- **Dropping keys before the migrations run lets legacy pump keys escape the drop.** Step 1 filters
  on today's names; a file from before the 2024 rename holds
  `ConfigBuilder_PUMP_DanaRSPlugin_Enabled`, which survives the filter, is renamed by the legacy step
  and is then written - with the checkbox unticked. **Swap steps 1 and 2: migrate the whole map
  first, then classify and drop.**
- **Dropping `sp.clear()` turns the import from replace into merge.** `PrefsTransfer.applyImported`
  documents why the clear exists: "A setting the old configuration had and the new one does not would
  otherwise survive an import that was meant to replace it." The plan removes it and never says what
  happens to a setting missing from the file. This needs a removal rule in step 6, not the blanket
  clear back.
- **Phase 0 was unsafe for a reason the gate did not cover.** Today's import calls `sp.clear()`,
  which deletes every key *not* in the file - device state included - so `reloadFromStore()` would
  publish those deletions into live flows. Both branches of the gate written earlier missed this.
  **Blocker**, and the reason the old phase 0 is gone (4.2).
- **The local backup is not restorable as specified.** `PrefsFormatCodec.encode` encrypts when a
  master password is given, and step 6 then replaces `ProtectionMasterPassword` with the file's, so
  the backup can no longer be opened; it holds exportable keys only; and it is written into the
  directory that the same import replaces. State the password rule and make it a full store snapshot
  in a named undo slot.
- **On a client, every synced setting in the file is silently reverted by the master.** About 100
  keys declare `SyncSpec(Cold, Bidirectional)`, including simple mode, the APS SMB set,
  `SafetyMaxBolus` and the document keys. A client import writes below `Preferences`, so nothing is
  stamped and the master's older copy wins the next cold sync. Either warn on the import screen or
  stamp and push.
- **The plan contradicts itself on ComboV2.** 3.1.3 calls its keys pump configuration ("exported, and
  taken from the file only when the user ticks"), 3.1.4 says they "only need `exportable = false`".
  Both cannot hold: with `exportable = false` the checkbox can never restore a ComboV2 pairing.
  Pump-configuration keys stay exportable and are gated on the import side only.
- **The legacy step on an imported map adds a bogus insulin.** 3.4 step 2 runs unmarked steps on the
  file's map and drops only the `aps_mode` hand-off, so the DIA and insulin hand-offs reach the live
  store and `dataMigrations` builds an entry from the file's legacy values.

- **ComboV2's import hooks are gated on the wrong phase.** 3.4 says they go away "only after its
  keys are device state", which is phase 1. But `sp.clear()` lives in `executeImport` and only goes
  in 3.4 step 6, which is phase 3. Between the two the ComboV2 keys are neither in the file nor
  protected from the clear. Change the condition to "once the import no longer clears the store".
- **The pause on client-control processing has no resume point.** Step 5 pauses it with the queue
  hold, and nothing says where it resumes, or what happens to it across the "retry the apply only"
  path. The queue hold has an owner and a timeout; the pause has neither.
- **The new sequence silently drops `prepareImportedSettings()`.** `ImportViewModel.confirmImport`
  calls it today: it force-sets `BooleanNonKey.GeneralSetupWizardProcessed` and sends
  `EventDiaconnG8PumpLogReset`. Losing the first sends the user back through the setup wizard after
  an import. Put the write inside the single `edit(commit = true)` of step 6.

### 8.C The runner and the migration steps

- **`handles` is declared, never measured.** Nothing compares it with what `migrate()` does, so "a
  forgotten migration fails the build" is satisfied by writing the declaration alone - and a step
  that touches a key it did not declare is equally invisible. Run `migrate()` over an instrumented
  `MigrationMap` that records every key read, written and removed, and fail when it differs from
  `handles`.
- **No rollback for a bad step.** Puts, removes and the marker land in one `edit(commit = true)`, so
  a wrong step destroys the old value on every install and marks itself done. The user's own export
  does not rescue them. For at least one release a `Move` should copy and not delete, with the delete
  as a separate, later-dated step.
- **"Owner fills an empty value" cannot work for the temp-target seed.** It needs six raw legacy keys
  that are registered nowhere, and 3.1.2 forbids the owner from reading the raw store. Keep the
  reading inside the pure step and hand off neutral values.
- **`IosSp.getAll()` does no normalization**, contrary to 3.2. It returns raw `NSUserDefaults` values,
  so numbers and booleans arrive as `NSNumber`. Either write the normalizer or say that the steps
  have to handle it.

- **Deleting unknown markers breaks idempotence on a downgrade.** Build N+1 runs a step that moves
  `old` to `new`; the user downgrades to build N, which deletes that marker and still writes
  `old`; on the next upgrade the step runs again over a map that now holds both. Downgrade is a
  normal recovery action here. Quarantine unknown markers instead of deleting them, or require a
  `Move` to do nothing when the destination already exists.
- **The `ProfileRepository` row would delete a documented safety net.** `StringNonKey.LocalProfileData`'s
  KDoc records a deliberate decision: the per-profile legacy keys are still read as a fallback and
  are **not** removed, so a downgrade still finds them. `Move` and `Drop` both remove. That row may
  only convert and keep, and the reason should be written next to it.
- **An imported marker can mark a step this phone failed.** Markers are exportable and step 6 writes
  the file's map, so a phone whose run of a step threw can receive that step's marker from a file
  where it succeeded, and will never run it again. The store's own marker set should be
  authoritative; the file's markers should only decide what runs on the map.

### 8.D The machinery that is supposed to keep it true

The completeness pass found that every other finding was about runtime, and that nobody had asked
whether 3.3 enforces the design. It does not.

- **Classification is the one property with no test.** 3.1.3 promises "the snapshot check fails when
  a new key carries no kind", but 3.3's format leaves `exportable` out on purpose and check 1 fires
  only on a removed key or a changed type. So a new key with no kind passes CI, and flipping a key
  from device state back to setting passes CI, with no diff in any checked-in file. Type changes,
  which cannot hurt anyone, are gated; classification, which decides whether another phone's pod
  state lands in this database, is not.
- **A per-module class scan cannot produce the concrete plugin enabled key.** It is built from
  `p.getType()`, instance state assembled in the plugin's constructor. Only `:app`, which has the
  graph, can compute it.
- **`composedKeyFor` is private**, so the check would restate the stored key format - a second
  definition of the thing it is checking. Expose the composition from production code instead.
- **Snapshots found by walking the repository survive a module leaving the build.** The goal "removing
  a module removes its snapshot" only holds if the global check reads each snapshot as a resource of
  its owning module, from the classpath. Walking the repo for `prefs-schema.txt` - the obvious way
  for an `:app` test to find 19 files - keeps the snapshot of a module that is no longer built.
- **Per-module recording cannot enforce "refuses to remove a key no step handles".** The recorder for
  a plugin module cannot see a step that lives elsewhere, and that is the normal case, since all of
  today's migrations become one `legacy` step. The recorder should only add and mark a vanished key
  as an unexplained tombstone; the "a tombstone must name a step" decision belongs in the `:app`
  check, which is the only place that sees both.
