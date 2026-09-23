# Settings import — the plan

This is the operative plan. `PREFERENCE_MIGRATIONS_PLAN.md` is no longer one: it is the evidence
record — measurements, refuted theories, and why each decision went the way it did. Use it to check
that nothing here was forgotten, not to work from.

## What is wrong today

The import is `beforeImport()` → `sp.clear()` → rewrite every key raw → `afterImport()`, in two
hand-written copies (`ImportExportPrefsImpl.executeImport` on Android, `PrefsTransfer.applyImported`
for iOS/desktop/client; only the second has a test).

Because it writes **below** `Preferences`:

- every `preferences.observe(...)` flow keeps its pre-import value for the life of the process —
  143 call sites in 45 files — and `uiRestart` cannot fix it: `PreferencesImpl` is
  `@SingleIn(AppScope::class)`, so the flow cache survives an activity recreate
- the type of every value is guessed: `if (value == "true"\|"false") putBoolean else putString`, so a
  `StringKey` holding "true" is written as a Boolean
- wiping the store is what forces every "preserve the pump settings" mechanism to exist

And the confirm step is not one. `confirmImport` writes the store and *then* shows `ApplyConfirm`;
`onApplyConfirmed`'s own comment says "the settings are already written". `cancelImport()` exists, so
a user can decline after their settings have been replaced, with no rollback.

## The flow

Never clear the live store. Stage the file, then apply only what differs.

1. **Load the file into NEW**, a plain `Map<String, String>`.
2. **Run the key-moving migrations against NEW**, so an old file's old key names become current ones
   before anything touches the real store.
3. **Drop from NEW what must not be applied** (rules below).
4. **Apply NEW to the live store** — resolved, typed, diffed, in one batch, then publish once.
5. **Clean old trash out of the live store.** Separate, last, and see the warning.

### NEW is a map, not a store

It needs no `Preferences` and no `KeyValueStore`. The file is strings; typing happens once, at the
apply step, where `PreferenceKeyResolver` says what the type is. Composing a stored name needs only
`ComposedKey.composeKey()`, which is a default method on the key enum.

**This is a safety boundary, not just a simplification.** `doMigrations` makes 23 typed
`preferences.*` calls and 42 raw `sp.*` ones, and a few of them have live side effects — a database
write, a localized-resource read, and `onLocalSyncedWrite` stamping and publishing to clients. Wrap
NEW in a `Preferences` and those still compile and run against an unapplied file. Against a plain
map they cannot be expressed at all.

Cost: those 23 typed calls are rewritten as map operations rather than reused. `LegacyPreferenceValue`
already does the safe string→typed conversion. A few use `Preferences` *semantics* rather than
storage (`preferences.get(UnitDoubleKey.OverviewLowMark)` applies unit conversion and a calculated
default), so the migrations need reading one at a time, not mechanically translating.

## The apply rules

Every key in NEW is resolved with `PreferenceKeyResolver` → key + argument + `KeyCategory`, then
skipped if **any** of these says so:

- **`if (!resolved.key.exportable) skip`.** One line, and it is what actually enforces "device state
  never arrives from a file" — cheaper and more honest than a second axis on the table. Closes the
  old plan's bug 4.
- **Unresolvable name → leave it alone, log it.** A newer AAPS's keys, and on a client build every
  pump and APS key, because `MetroGraphs.allPlugins` never constructs those plugin buckets. Dropping
  them would silently discard a category that is legitimately in the file.
- **Value that will not parse → log, skip, do not guess.** Same answer as the start-up migrations:
  one odd value must not stop an import.
- **Unchanged → skip.** Most of a ~500-key file already matches the phone. Observers already conflate
  (a `StateFlow` does not emit an equal value), so what this saves is ~99 pointless
  `SyncedPrefModified` bumps — `onLocalSyncedWrite` fires regardless of whether the value changed —
  and ~500 pointless writes. The real prize is blast radius: an import becomes "these 12 settings
  changed", which can be logged, shown to the user, and reasoned about.
- **On a client, `sync?.direction == Bidirectional` → skip.** See below.
- **Pump-owned and the box is unticked → skip.** See below.

### Writing pump keys wakes the drivers — the write needs a stopped window

This is the one thing that makes Phase 1 more than a rewrite of the write loop. Four pump drivers
observe their own preference keys and act on a change:

```kotlin
// AbstractDanaRPlugin.onStart
preferences.observe(DanaStringNonKey.RName).drop(1).onEach {
    danaPump.reset(); pumpSync.connectNewPump(true); commandQueue.readStatus(...)
}
// DanaRPlugin / DanaRKoreanPlugin.onStart
preferences.observe(DanaBooleanKey.UseExtended).drop(1).onEach {
    if (pumpSync.expectedPumpState().extendedBolus != null) executionService?.extendedBolusStop()
}
// MedtronicPumpPlugin.onStart — the same on MedtronicStringPreferenceKey.Serial
```

`connectNewPump(true)` writes a stop for the running temporary basal and extended bolus;
`extendedBolusStop()` is a real command sent to the pump. These are **driver-owned keys** — genuine,
legitimately exportable pump configuration — so the `exportable` work in Phase 0 does not touch them.

Today's import cannot reach them, because it writes below `Preferences` and no flow ever updates.
That is bug **#5140**, and making the write visible is precisely what turns it on.

Keep-pump-settings mode is safe: those keys are `PumpInternal` and skipped. **Full import is not**,
and full import is the fresh-phone restore — the main case.

So the write happens with the plugins stopped: hold the command queue, stop them, write, then let
`applyConfiguration()` start them again. A driver that is not running has no collector, so the
observers fire against the new values on the way up rather than mid-write. This is what the old plan
called "what makes the write safe, not only the memory fresh".

### Replace or merge: this flow changes what "import" means

Today `sp.clear()` makes an import a **replace** — there is a test for it,
`an import removes what the file does not have`. Not clearing makes it a **merge**: a key set on this
phone but absent from the file survives.

That is deliberate and it is what keeps the pump working, but it has to be said out loud, because the
replace semantics do not disappear — they move to the removal step, which is Phase 3 and ships as a
log first. **Between Phase 1 and Phase 3 an import merges and nothing removes stale keys.** A user
importing a file that lacks a key they once set keeps the old value. That is the safer direction and
the visible cost of not deleting on a classification that does not exist yet.

`PrefsTransfer.applyImported`'s KDoc and that test change in the same commit as the clear.

### Synced keys on a client: never write

`RunningConfigurationImpl.applySyncedPrefs` applies the master's cold doc with
`putRemote(key, value, 0L)` — **unconditionally, no stamp comparison**. So a value written on a
client survives only until the master's next cold publish and is then silently reverted, with
nothing in the UI or the log. A transient divergence that quietly corrects itself is worse than
never applying it: the user sees their setting take effect and then change back.

On a client those 99 keys belong to the master, and the app already treats them that way —
`PreferenceState` disables editing them while the master is reachable, and a freshly paired client
receives the correct values anyway.

So: **skip. No write, no stamp, no publish.** This also removes the "what version does an imported
synced key get" question entirely, rather than answering it.

Removals are covered by the same rule — a removal on a client must not publish either. Note the old
plan contains a recommendation to bump the stamp and emit on removal *with no role gate*; on a
client that publishes the key's **default** to the master.

### Synced keys on a master: stamp what you imported

The mirror image of the rule above, and it is not symmetric. The applier writes the batch through
`KeyValueStore`, which sits **below** `Preferences`, so `onLocalSyncedWrite` never runs and
`SyncedPrefModified` keeps its old time. That old time is the master's side of the staleness check
in `ClientControlReceiver.onVerifiedPreferencesUpdate`, which drops a pushed value only
`if (pushed.lastModified <= ours)`.

So without a stamp an import loses to any client edit made *before* it:

1. 09:00 — master and client agree on a synced key; both stamps read 09:00.
2. 10:00 — the client goes offline and the user edits that key there. The client stamps it locally
   and queues the push.
3. 10:05 — the user imports a backup on the master. The imported value lands, but the stamp stays
   at 09:00.
4. 10:10 — the client reconnects and pushes with `lastModified = 10:00`.
5. The master compares `10:00 > 09:00`, accepts, and the imported value is silently replaced.

`PreferenceImportApplier.stampSyncedKeys` closes it: every synced key the applier actually **wrote**
gets `max(previous + 1, now())`, the same formula `onLocalSyncedWrite` uses. Keys it skipped as
unchanged are not stamped — an import that changed nothing must not win an argument it was not in.
The stamp is written through `Preferences`, after `reloadFromStore()`, so the flows are already
correct when the publisher observes it.

This is verified by unit test and by falsification (the tests fail with `stampSyncedKeys` disabled),
**not on a device** — reproducing it needs a master, a paired client, and an offline edit window.

### The master publishes by observation — nothing to build

`RunningConfigurationPublisher` merges `preferences.observeChange(key)` over the running-config keys,
debounces 5 s, and calls `publishCold()`. `observeChange` is built on `observe()`, a `StateFlow`, so
only keys that actually changed fire. Combined with the diff, the master publishes exactly the real
delta, once.

The only requirement is timing, and moving the write (below) supplies it: the publish must land
after the user has confirmed, not while they are still deciding.

### `ConfigBuilderEnabled` is NOT a special row

An early draft had it calling `setPluginEnabled`. That does not work: `PluginBase.setPluginEnabled`
only flips the in-memory `state` and schedules start/stop — **it writes nothing to the store**. The
key is written by `ConfigBuilderImpl.savePref`. So the imported selection would never persist, and
then `applySettings` → `applyConfiguration()` → `loadPref()` would read the phone's *old* stored
value and revert the entire imported plugin selection inside the same import.

**Write `ConfigBuilder_Enabled_*` with the others, and let `applyConfiguration()` do the lifecycle.**

How it decides what to restart — it does not diff settings. Per plugin it compares the **stored
enabled flag** against the plugin's **in-memory `state`**:

```
applyConfiguration()
 └ loadSettings()
    ├ loadPref(p, type)  for every plugin
    │    existing = preferences.getIfExists(ConfigBuilderEnabled, composedKeyFor(p, type))
    │    p.setPluginEnabled(type, existing)       → Job, or null if unchanged
    └ verifySelectionInCategories()               → elects one per single-select category
 └ setAlwaysEnabledPluginsEnabled()
 └ regenerateActivePluginKeys()                   (skipped on client)
 └ startActivePluginObservers()
 └ withTimeoutOrNull(30s) { started.joinAll() }
 └ EventConfigBuilderChange + activeSelectionChanges
```

`setPluginEnabled` acts only `if (state != ENABLED)` / `if (state == ENABLED)`, else returns null and
nothing happens. So exactly the plugins whose enabled-ness differs get cycled, exclusivity and empty
categories are handled by the election, and the 30 s bound exists because a pump driver's `onStop`
can block on disconnecting.

It reacts **only** to the enabled flag, never to any other setting — which is why "update by
observing" is load-bearing: nothing else will tell a plugin its settings moved.

In the keep-pump-settings mode, skip `ConfigBuilder_Enabled_*` for plugins whose
`mainType == PluginType.PUMP`. The settled rule is that losing the pump *selection* is acceptable and
losing the pump *configuration* is not — and selection-from-file with configuration-from-phone is the
one combination explicitly ruled out.

### Write in one batch, refresh once

Not ~500 individual `preferences.put` calls. On desktop each one rewrites the whole properties file
with an `fsync`; on Android each is its own `edit().apply()`. Per-key writes also let every live
collector see a partially-imported store in iteration order.

Use the resolver for the *type*, write into a single `edit(commit = true)` — `KeyValueStore.edit {}`
exists and neither importer uses it — then refresh the flow cache once. That is atomic on Android,
cheap on desktop, and closes `remove()`'s hole for free: `PreferencesImpl.remove()` is
`sp.remove(key.key)` and nothing else, so it updates no flow and signals no sync change.

Needs one new thing: `Preferences.reloadFromStore()` to re-read cached flows. Zero occurrences today;
same shape as the existing `refreshUnitDoubleFlows()`.

## The UI

**Move the write out of `confirmImport` and into `onApplyConfirmed`.** One move, four results: the
checkbox can actually gate what is written, confirm means something, `cancelImport()` becomes safe
instead of misleading, and the master's publish lands after the apply so no suppression machinery is
needed.

**The checkbox lives in `ImportReviewContent`, under the decryption password field, above the Import
button** — not in a dialog. It is an input, and it belongs with the other inputs; a destructive
option inside a modal that people dismiss reflexively is easy to mis-tick. By the time the Import
button is live the file is decrypted, so the real counts can sit beside it and update as it is
toggled: *"6 pump settings would be replaced"*.

Phrased as the destructive action so that unchecked is the safe state:

> ☐ Also replace pump settings

**Unchecked by default.** Restoring onto the same phone and the same pump then needs no thought at
all, and replacing a pump's configuration takes one deliberate tick. A checkbox meaning "don't do
something" reads badly under stress.

`ApplyConfirm` stays an `OkDialog`, but restates the choice and the counts, so the last thing the
user sees names the consequence.

## Order of work

**Phase 0 — fix the `exportable` flags. DONE 2026-09-22.** It was estimated at "about seven keys"
and turned out to be **39**.

Not cosmetic. `verifyPumpIdentification` reads `ActivePumpType` and `ActivePumpSerialNumber`, and
those are core-enum keys owned by no plugin — so they classify as `General` and would be written **in
the keep-pump-settings mode too**. The live pump then fails the comparison and `applySettings` calls
`connectNewPump()`, which writes a stop for the running temporary basal **and** the running extended
bolus and wipes the identity keys — on a healthy, unchanged pump, in the mode whose entire promise is
that the pump is untouched.

- **Pump identity (3)** — `StringNonKey.ActivePumpType`, `ActivePumpSerialNumber`,
  `LongNonKey.ActivePumpChangeTimestamp`. The timestamp is the "accept nothing older than this" line
  for pump history, so another phone's value silently drops or admits a stretch of records.
- **`StringNonKey.ActiveScene` (1)** — which scene is running now, as opposed to `SceneDefinitions`
  beside it, which is the configured list and stays exportable. It rides the hot channel.
- **Sync cursors (31)** — `NsclientLongKey` (15), `XdripLongKey` (14), `NsclientStringKey.V3LastModified`,
  `TidepoolLongNonKey.LastEnd`. Every one of these enums holds nothing but cursors, so the flag was
  set once on each enum's constructor rather than 31 times, with a note to mark any future
  non-cursor key `exportable = true` explicitly. A cursor describes this phone's conversation with a
  server: too high and the install skips records that were never uploaded, too low and it re-sends.

- **Local alarm deadlines (2)** — `LocalAlertLongKey.NextPumpDisconnectedAlarm` and
  `NextMissedReadingsAlarm`. Wall-clock deadlines this phone moves forward as it goes. A time from
  another phone is wrong in both directions: in the future it suppresses an alarm this install should
  raise, in the past it fires one at once.
- **OpenHumans cursors (2)** — `OhLongKey.Counter` and `UploadOffset`. Same category as the sync
  cursors above, on a plugin the first sweep did not look at. Set per entry, not on the constructor,
  because `ExpiresAt` in the same enum is **not** a cursor: it belongs with the OAuth token beside it
  and stays exportable.

Tidepool's OAuth state beside the cursor stays exportable, matching the decision already taken for
Google Drive and OpenHumans — a token is issued to the account, survives a transfer, and
re-authorising on a new phone is the friction a backup exists to remove.

The last four were found by later review passes, not by the first sweep. The lesson is in the
guard tests, not in the list: `RawPreferenceStoreScanTest` and `PreferenceKeySnapshotTest` now make
any new key state its `exportable` value out loud.

**Phase 1 — the write half. DONE 2026-09-22.** `sp.clear()` is gone from both implementations.

The apply is now: hold the queue → `whileReconfiguring { executeImport; prepareImportedSettings }` →
`applySettings()` → fallback notification → `storeSettings`. Inside `executeImport`,
`PreferenceImportApplier` resolves each name, filters, diffs, writes once and publishes once.

- **The write moved into `onApplyConfirmed`.** It used to run in `confirmImport`, before the user was
  asked - which made `cancelImport` a lie and would have published the file to every paired client
  five seconds later. `ApplyConfirm` is now a real `OkCancelDialog` carrying the preview counts.
- **The checkbox is in `ImportReviewContent`**, under the decryption password and above the Import
  button: "Also replace pump settings", unchecked by default.
- **No stopped window was needed after all.** The reactors only fire on a genuine change, and Phase 0
  removed the false trigger (`ActivePumpType` / `ActivePumpSerialNumber` were exportable, so every
  import looked like a pump change). What remains fires only when the file really does name a
  different pump or really does turn extended boluses off, and the reaction is then correct. The
  write sits inside `withHold`, so a driver's `commandQueue.readStatus` queues behind the apply.
- **Two reconfiguring windows, not one** - around the write, and around the plugin rebuild. They are
  deliberately not nested: `applySettings`'s window must close before the cache refreshes or the
  overview comes back showing "NO PROFILE SET".
- **`PrefsTransfer.applyImported` is deleted**, and with it `an import removes what the file does not
  have`. That test asserted the replace semantics this change drops on purpose.
- **The fallback notification** fires when the import leaves no pump selected and
  `verifySelectionInCategories` falls back to the virtual pump - it names what is active now, since
  "no pump selected" is not what happened.
- **`storeSettings` runs afterwards**, so every plugin in THIS build has an explicit stored value
  rather than an absent one the next start would fill from a default.

**Phase 2 — migrations.** Extract `doMigrations` from `MainApp` (it is Android-only and wired to the
live singletons), add NEW as a map, port the key-moving steps onto it. `dataMigrations()` stays out.

Two traps found before starting:

- **The DIA hand-off breaks if it is ported naively.** `legacyProfileKeysToRemove` collects raw key
  NAMES while migrating and deletes them from the live store after `dataMigrations` has used the
  values. Run the migrations against NEW and the names come from NEW while the deletion still targets
  CURRENT. Worse, the DIA a hand-off would carry is the IMPORTED file's profile DIA, and
  `dataMigrations` uses it to stamp THIS phone's historical boluses and profile switches — one-way,
  and right only if the database came from the same source as the file.
- **Port order is a correctness requirement, not tidiness.** The steps must run over ONE mutable map
  in source order, because later steps read what earlier ones wrote. The insulin label is the case
  that proves it: `insulinLabel` reads only the `ConfigBuilder_Enabled_INSULIN_*` spelling, and a
  store carrying only the legacy `ConfigBuilder_INSULIN_<Plugin>_Enabled` form gets the right answer
  today purely because the ConfigBuilder loop rewrote the spelling first. The history test needs a
  legacy-spelling fixture asserting the Lyumjev label comes out.

**Phase 3 — cleanup.** Step 5, **as a log only**.

## Why cleanup does not ship yet

There is no sound definition of "not used", and the two candidates fail in opposite directions:

- **The runtime registry under-counts.** Registration is a side effect of constructing a plugin, so
  on `aapsclient`/`pumpcontrol` whole buckets are never built and their live keys look unused.
  `virtual_pump_serial_number` is registered nowhere on any flavour.
- **Prefix matching over-counts.** Every dead composed argument — a removed plugin's
  `ConfigBuilder_Enabled_*`, a deleted profile's `LocalProfile_*_3` — matches a live prefix. That is
  the largest category of real trash, and a name-based rule can never see it.

So the sweep as specified would destroy real data and miss most of what it was added for. It also
needs a raw enumeration (`store.getAll()`, the stored *names*) that nothing in the import has today,
plus its own `RawPreferenceStoreScanTest` entry.

Ship the log for one release. It turns every question above into data from real installs.

**And the old plan contradicts itself here, so this is a live conflict rather than a settled rule.**
Decision 4 calls an unregistered key "the trash, and the reason for the whole decision" — remove it.
Recommendation 4 says the opposite: "never remove a key that cannot be classified — unknown and
unregistered keys are kept and logged as orphans." Log-first picks the second. If the first is what
is wanted, it cannot ship before `kind` exists and is enforced, and before ComboV2 is off the raw
store — both named as preconditions by decision 4 itself.

Still on the raw store and invisible to any registry rule: **ComboV2** (3 files) and **Insight's
`PairingDataStorage`** (its own SharedPreferences file, which `sp.clear()` never touched, so Insight
was never an import problem). CareLevo is already done — `CarelevoStringNonKey` exists, all eight
keys `exportable = false`, registered.

## Already built

`PreferenceKeyResolver` (name → key + argument + category, prefix-unambiguous),
`PreferenceKeyResolverFactory` (ownership via `mainType == PluginType.PUMP`), `KeyCategory`,
`Preferences.getAllKeys()`.

Guards: `ComposedKeyPrefixTest`, `PreferenceKeyRoundTripTest`, `PreferenceKeySnapshotTest`,
`RawPreferenceStoreScanTest`, `PluginLifetimeWorkScanTest`.
