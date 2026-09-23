# Settings import

How AAPS applies a settings backup to a running app. This describes what the code does today. It is
documentation, not a plan.

## What an import is

An import reads an exported settings file and writes the values it carries into this phone's
preference store, without restarting the app.

It does **not** wipe the store first. A key that exists on this phone but is missing from the file
keeps its value. So an import is a **merge**, not a replace. See
[What an import does not do](#what-an-import-does-not-do).

The whole job is five steps:

1. Read the file into a plain `Map<String, String>`.
2. Work out, for every name in that map, which key it is and what type it holds.
3. Drop everything that must not be applied.
4. Write what is left, in one batch, and refresh the caches once.
5. Restart the plugins whose enabled state changed.

Nothing is written until the user has confirmed. Steps 3 and 4 are also run in a "do not write" mode
to produce the counts shown on the confirm screen.

## Reading the file

The file is a flat list of `name = value` pairs, all values stored as text. It may be encrypted with
the master password, and it may come from an older AAPS with older key names.

The map stays a plain map on purpose. It is never wrapped in a `Preferences` or a `KeyValueStore`.
That is a safety boundary: code that writes to a store, stamps a sync key, or touches the database
cannot even be expressed against a map, so it cannot accidentally run against a file that the user
has not accepted yet.

## Working out what a name means

`PreferenceKeyResolver` turns a stored name back into the key object that owns it. This is the part
that makes a typed, filtered import possible at all.

- A **plain key** matches by exact name: `use_autosens` is `BooleanKey.ApsUseAutosens`.
- A **composed key** matches by prefix plus an argument: `ConfigBuilder_Enabled_PUMP_VirtualPump`
  is `BooleanComposedKey.ConfigBuilderEnabled` with the argument `PUMP_VirtualPump`. The argument is
  checked against the key's format (`%d` must be a number).
- Matching is longest-prefix-first, and no prefix may swallow another. `ComposedKeyPrefixTest`
  fails the build if two composed keys can both claim the same stored name.

The resolver also gives each key a `KeyCategory`:

| Category | Meaning |
| --- | --- |
| `General` | not owned by any plugin |
| `PumpInternal` | owned by a plugin whose `mainType` is `PluginType.PUMP` |
| `OtherPluginInternal` | owned by some other plugin |
| `ConfigBuilderEnabled` | the "is this plugin turned on" flag |

Ownership comes from `PreferenceKeyResolverFactory`, which asks `ActivePlugin` for every constructed
plugin and splits them on `pluginDescription.mainType == PluginType.PUMP`. A plugin declares the keys
it owns with `ownPreferences`, and a plugin may claim a key that lives in a core enum — the virtual
pump does exactly that for `StringKey.VirtualPumpType` and `BooleanKey.VirtualPumpStatusUpload`,
because they are its configuration even though they are not in a driver enum.

## What gets dropped

`PreferenceImportApplier` walks the map and skips a key if **any** of these applies, in this order:

1. **The name does not resolve.** Left alone and logged — never deleted. A file from a newer AAPS
   carries keys this build has never heard of, and on a client build every pump and APS key is
   unresolvable because those plugins are never constructed. Dropping them would silently discard a
   category that is legitimately in the file.
2. **`key.exportable` is false.** This is the one line that enforces "device state never arrives
   from a file". 39 keys opt out: pump identity, the sync cursors for Nightscout, xDrip, Tidepool
   and OpenHumans, the local alarm deadlines, and the currently running scene. An old file can still
   carry such a key from before its flag changed, and a hand-edited file can carry anything.
3. **This is a client and the key is synced.** On a client every synced key belongs to the master.
   Writing one here would apply and then be reverted without a word by the master's next cold
   publish, because `applySyncedPrefs` adopts with `putRemote(key, value, 0L)` unconditionally. A
   value that takes effect and then changes back is worse than one that never arrives.
4. **"Also replace pump settings" is unticked and the key is pump configuration.** That means
   `PumpInternal`, plus the `ConfigBuilder_Enabled_PUMP_*` selection rows. These are counted even
   when skipped, so the confirm screen can say how many settings the tick would change.
5. **The value cannot be read as the key's type.** Logged and skipped. One odd value must not stop
   an import.
6. **The value is already what the phone has.** Most of a ~500-key file matches already. Skipping
   these keeps the change small enough to log, show and reason about, and avoids stamping a synced
   key that did not move.

Everything that survives is staged as a typed write — `putInt`, `putBoolean`, `putDouble` and so on,
chosen from the key, not guessed from the text.

## Writing

```kotlin
store.edit(commit = true) { edits.forEach { it() } }
preferences.reloadFromStore()
stampSyncedKeys(syncedWritten)
```

**One batch.** Not one write per key. Per-key writes are hundreds of separate commits, and they let a
live collector see a store that is half old and half new, in whatever order the keys happen to come.

**Then one refresh.** The batch goes in below `Preferences`, so no `observe()` flow would notice it.
`reloadFromStore()` re-reads every cached flow — boolean, string, double, unitDouble, int and long —
and pushes the new values. A `MutableStateFlow` conflates, so a flow whose value did not change emits
nothing and its observers stay quiet. The cache keeps the read lambda next to each flow, so composed
keys are refreshed too.

This is what makes an import visible to `AutomationRuntime`, `QuickWizard`, `ProfileRepositoryImpl`,
the scene list, and everything else that caches a preference-backed collection and refreshes through
`observe()`.

**Then the stamps.** Synced keys that were actually written get `SyncedPrefModified` advanced to
`max(previous + 1, now())`, the same formula a normal local write uses. Without it, an edit a client
made before the import would carry a newer timestamp, win the next sync, and silently replace the
imported value. Keys that were skipped as unchanged are not stamped.

## Turning plugins on and off

`ConfigBuilder_Enabled_*` is written like any other key. It is deliberately **not** routed through
`setPluginEnabled`, because that only flips an in-memory flag and stores nothing — the imported
selection would not survive, and the reload that follows would read the phone's old value and undo
the whole thing.

The lifecycle is left to `applyConfiguration()`, which runs after the write. Per plugin it compares
the stored enabled flag against the plugin's in-memory state and cycles only the ones that differ,
then elects one plugin per single-select category, then waits up to 30 s for the starts to finish
(a pump driver's `onStop` can block on disconnecting).

It reacts only to the enabled flag. Nothing else tells a plugin that its settings moved — that is
why the flow refresh above is load-bearing rather than a convenience.

## The order of the apply

```
withHold(pump command queue)
 ├ whileReconfiguring
 │   ├ executeImport(prefs, keepPumpSettings)   ← resolve, filter, write, refresh, stamp
 │   └ prepareImportedSettings()
 ├ applySettings()                              ← applyConfiguration + pump identity + caches
 ├ notifyIfPumpFellBackToVirtual(pumpBefore)
 └ configBuilder.storeSettings("import")
```

**Why the pump queue is held.** Several drivers watch their own keys and act on a change: Dana's
pump name and `UseExtended`, Medtronic's serial. They talk to the pump when those change — a stop for
a running temporary basal, or an extended bolus stop. Inside the hold, that command queues behind the
apply instead of racing it. The hold also stops anything new being picked up before it waits, so the
loop cannot slip a command in between the check and the write.

**Two reconfiguring windows, not one** — around the write and around the plugin rebuild — and they
are deliberately not nested. The first window must close before the caches refresh, or the overview
comes back showing "NO PROFILE SET".

**`storeSettings` runs last** so every plugin in this build has an explicit stored enabled value. A
file from a smaller build cannot name plugins it never had, and an absent value resolves from a
default that may differ on the next launch.

**The fallback notification** fires when the import leaves no pump selected and the category election
falls back to the virtual pump. It names what is active now, because "no pump selected" is not what
happened.

## What the user sees

1. **Pick a file.** The list shows what is on the phone and in cloud storage.
2. **Review.** The file is decrypted. If it was encrypted with a different password, the screen asks
   for that one. Under the password field, above the Import button, is a checkbox:
   *"Also replace pump settings"*, unchecked by default.
3. **Confirm.** A dialog restates the choice and the counts — how many settings change, how many
   pump settings are protected by the checkbox. Nothing has been written yet.
4. **Applying**, or **Waiting for pump** if the queue is busy.
5. **Done**, or **Failed** with a retry.

The checkbox is phrased as the destructive action, so unchecked is the safe state: restoring onto the
same phone and the same pump needs no thought, and replacing a pump's configuration takes one
deliberate tick. It sits with the other inputs rather than inside the confirm dialog, because a
destructive option in a modal that people dismiss reflexively is easy to mis-tick.

Cancelling at any point before step 4 changes nothing on disk.

## What an import does not do

- **It does not remove anything.** A key this phone has and the file does not keeps its old value,
  and keys left behind by old versions stay. There is no sound rule for "this key is not used any
  more": the plugin registry under-counts, because a plugin that is never constructed registers
  nothing, and prefix matching over-counts, because every dead composed argument still matches a live
  prefix. A rule that is wrong in either direction either destroys real settings or misses most of
  the rubbish.
- **It does not migrate the file.** Key-name migrations run at start-up, against the live store, in
  `MainApp.doMigrations`. A file with old key names therefore imports its old names, which then do
  not resolve and are left alone.
- **It does not reach preferences that bypass `Preferences`.** ComboV2 and Insight's
  `PairingDataStorage` keep some state in raw keys or their own file. `RawPreferenceStoreScanTest`
  lists what is left.

## Where the code is

| What | Where |
| --- | --- |
| Name → key + argument + category | `core/keys` `PreferenceKeyResolver` |
| Which plugin owns which key | `implementation` `PreferenceKeyResolverFactory` |
| Filter, diff, write, refresh, stamp | `implementation` `PreferenceImportApplier` |
| Flow cache and `reloadFromStore` | `implementation` `PreferencesImpl` |
| File handling, decryption, entry points | `implementation` `ImportExportPrefsImpl`, `LocalImportExportPrefs`, `PrefsTransfer` |
| Screen and state machine | `ui` `ImportSettingsScreen`, `ImportViewModel` |

## The tests that guard it

- **`ComposedKeyPrefixTest`** — no composed key prefix may swallow another. It found a real one:
  `appwidget_use_black_<id>` sat inside `appwidget_<id>`, so a Boolean could be read as an Int. The
  key is now `widget_use_black_` and `MainApp.doMigrations` moves the old values across.
- **`PreferenceKeyRoundTripTest`** — every key in the build composes to a stored name and resolves
  back to the same key. This is what catches a key added next month in a module nobody thought about.
- **`PreferenceKeySnapshotTest`** — a recorded snapshot of every key, its type, default and
  `exportable` flag. `exportable` defaults to `true`, which is right about 91% of the time; this test
  is what makes the minority visible, so a new key cannot quietly become exportable.
- **`RawPreferenceStoreScanTest`** — nothing may reach the store around `Preferences` without being
  on the allowlist.
- **`GenerateImportFixture`** — writes a real encrypted export file with a password of its own, for
  testing an import on a device whose master password is unknown. Set `AAPS_WRITE_IMPORT_FIXTURE` to
  a path.
