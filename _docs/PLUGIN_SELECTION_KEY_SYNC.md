# Plugin selection + settings on the key-sync path

**Status:** design spec (not yet implemented). Branch context: `ns_settings` (generic preference
sync).

**Outcome:** active-plugin selection and each plugin's settings sync as ordinary preference keys;
the
client can see/select the synced categories (gated on master reachability); consumers react to
precise
key changes instead of the coarse `EventConfigBuilderChange`. Deletes `ConfigExportImport`, the
bespoke
`RunningConfiguration` plugin blocks, and (eventually) the broadcast event. Adding a synced category
becomes a **single compile-forced decision**.

---

## 0. Verified foundations (what this rests on)

- Every `reloadInternalState()` in the tree is **empty** (APS×3, Safety, Sensitivity×3, Scenes) →
  those
  plugins read settings on-demand; nothing to reload.
- `ConfigExportImport.syncedKeys` / `reloadInternalState` is consumed **only** by
  `RunningConfigurationImpl`
  — *not* by settings export/import (which uses the `exportable` flag, `PreferencesImpl:438`). It is
  a pure
  device-sync hook despite its name.
- `sync` (`SyncSpec`) and `exportable` are **orthogonal** (`getSyncKeys()` filters on
  `sync != null`, never
  `exportable`); "synced but not exported" already exists (`LongComposedKey.SyncedPrefModified`).
- `setPluginEnabled` already performs **idempotent** `onStart()`/`onStop()` on real state
  transitions
  (`PluginBase:93`).
- `PluginType` is an enum → an **expression `when` over it (no `else`) is exhaustiveness-checked at
  compile
  time**.
- The sync wire is `Map<String, String>`; SharedPreferences stores only primitives + String. So
  every synced
  value is a string on the wire regardless of its Kotlin type.

---

## 1. Key model

### 1a. Selection — one flat key per single-select type, behind one exhaustive `when` (the SSOT)

Use a flat `StringNonKey` per **single-select** category. Generate keys for **all** single-select
types
(complete registry, future-proof); **sync is a per-key opt-in** via `SyncSpec`. Keys are *
*non-public**; the
sole entry point is an exhaustive `when`, so the `PluginType` enum is the only door in.

```kotlin
// StringNonKey — non-public; reached only via activePluginKey(). All exportable = false (synthetic mirrors).
internal ActivePluginAps        ("active_plugin_aps",         sync = SyncSpec(Cold, <dir>), exportable = false)
internal ActivePluginSensitivity("active_plugin_sensitivity", sync = SyncSpec(Cold, <dir>), exportable = false)
internal ActivePluginSmoothing  ("active_plugin_smoothing",   sync = SyncSpec(Cold, <dir>), exportable = false)
internal ActivePluginCalibration("active_plugin_calibration", sync = SyncSpec(Cold, <dir>), exportable = false)
internal ActivePluginPump       ("active_plugin_pump",        sync = null, exportable = false)  // generated, future
internal ActivePluginBgSource   ("active_plugin_bgsource",    sync = null, exportable = false)  // future
internal ActivePluginProfile    ("active_plugin_profile",     sync = null, exportable = false)  // future
internal ActivePluginInsulin    ("active_plugin_insulin",     sync = null, exportable = false)  // future

// SINGLE source of truth — exhaustive, NO else → adding a PluginType fails to compile here.
// Returns a key for every SINGLE-SELECT type; null for MULTI-SELECT (no single active plugin).
fun activePluginKey(type: PluginType): StringNonKey? = when (type) {
    PluginType.APS         -> StringNonKey.ActivePluginAps
    PluginType.SENSITIVITY -> StringNonKey.ActivePluginSensitivity
    PluginType.SMOOTHING   -> StringNonKey.ActivePluginSmoothing
    PluginType.CALIBRATION -> StringNonKey.ActivePluginCalibration
    PluginType.PUMP        -> StringNonKey.ActivePluginPump
    PluginType.BGSOURCE    -> StringNonKey.ActivePluginBgSource
    PluginType.PROFILE     -> StringNonKey.ActivePluginProfile
    PluginType.INSULIN     -> StringNonKey.ActivePluginInsulin
    PluginType.GENERAL, PluginType.CONSTRAINTS, PluginType.LOOP, PluginType.SYNC -> null  // multi-select
}

// Everything else derives from the one when:
val syncedSelectionTypes: List<PluginType> = PluginType.entries.filter { activePluginKey(it)?.sync != null }
```

Properties: **synthetic** (mirror of `ConfigBuilderEnabled`), **`exportable = false`** (regenerated
on
start; backups carry `ConfigBuilderEnabled`), value = the active plugin's identity (see §1c). One
key per
category ⇒ **selection is atomic**.

**Multi-select** (`GENERAL/CONSTRAINTS/LOOP/SYNC`): no single active plugin → `activePluginKey`
returns
`null`. Left out until/unless a multi-active representation is needed (which would be a `StringSet`
key — a
different shape). Keeping `ActivePlugin` single-valued avoids muddying it now.

### 1b. Settings — flag each plugin's existing `syncedKeys`

Tag the keys plugins already list in `syncedKeys` (e.g. `BooleanKey.ApsUseDynamicSensitivity`,
`IntKey.ApsDynIsfAdjustmentFactor`, `DoubleKey.AutosensMin`, …) with `SyncSpec(Cold, <dir>)`. They
then
ride `buildSyncedPrefs`/`applySyncedPrefs` automatically — no per-plugin block.

### 1c. Value identity — prefer a stable `pluginId` over `javaClass.simpleName`

The status-quo value is `javaClass.simpleName`, which is fragile (class rename / package move / R8
renaming silently breaks selection sync). **Recommendation:** add a stable `pluginId` to
`PluginDescription` and store that. If `simpleName` is kept for compatibility with the old field
during
migration, treat it as tech debt to retire. (Orthogonal to typing — this is robustness.)

### 1d. Direction (per key)

Each key's `SyncSpec` carries its direction. Start `MasterOnly` (master pushes; client view-only).
Flip the
synced ones to `Bidirectional` once §5's offline-gating is in (a client switching the master's
active
APS/sensitivity is dosing-relevant → gated on reachability).

---

## 2. Flat-sync extension: wire `Double` (and `Long` if used)

Sensitivity settings are plain `DoubleKey`; the flat path handles Boolean/String/Int/UnitDouble
only. Add a
`Double` branch (and `Long` if any synced setting needs it) in the ~6 spots:
`RunningConfigurationImpl.buildSyncedPrefs`/`applySyncedPrefs`, `PreferencesClientPublisher`
serialize,
`ClientControlReceiver.onVerifiedPreferencesUpdate`, `PreferencesImpl.put`/`putRemote`. Foundation
PR, no
behavior change.

---

## 3. ConfigBuilder ↔ keys bridge

Two representations, complementary:

|              | `ConfigBuilderEnabled` (existing)   | `ActivePlugin<…>` (new)     |
|--------------|-------------------------------------|-----------------------------|
| role         | local source of truth (`isEnabled`) | synced mirror               |
| `exportable` | true (selection in backups)         | **false**                   |
| `sync`       | null                                | per-key `SyncSpec` (subset) |
| filled by    | `storeSettings`                     | §3a / §3b below             |

### 3a. Generate on start (can't forget — driven by the SSOT)

After `loadSettings()`, regenerate by **looping `PluginType.entries` through `activePluginKey`** —
so any
single-select type is necessarily covered:

```kotlin
fun regenerateActivePluginKeys() {
    PluginType.entries.forEach { type ->
        val key = activePluginKey(type) ?: return@forEach   // null = multi-select, skip
        val active = activePlugin.getSpecificPluginsListByInterface(type).firstOrNull { it.isEnabled(type) }
        preferences.put(key, active?.pluginId ?: "")        // §1c
    }
}
```

This fills the **full single-select registry**, synced or not. What actually travels is the subset
with a
`SyncSpec` (the generic path filters on `sync != null`).

### 3b. Update on change

In `performPluginSwitch` / `processOnEnabledCategoryChanged`:
`activePluginKey(type)?.let { preferences.put(it, enabledPlugin.pluginId) }`. That `put` on a synced
key *is*
the publish trigger.

### 3c. Apply observer (the reconciler)

`ConfigBuilder` observes `syncedSelectionTypes.map { activePluginKey(it)!! }`; on change → resolve
id →
`performPluginSwitch(p, true, type)` (sets local `ConfigBuilderEnabled` + lifecycle). May reuse
`loadSettings()` (it already reconciles all plugins idempotently).

- **Echo-skip** own writes (`incoming == lastSelfWritten`).
- **Runs on master too** — echo-safe because it reads prefs / sets in-memory state, no pref
  write-back.

### 3d. Atomicity & ordering

- Selection is **one key/category** → no multi-key intermediate.
- Selection + settings ride the **same cold block** (`buildSyncedPrefs`), republished as a unit →
  travel
  together.
- Apply writes the **whole block first**, then the single reconcile runs → when plugin B starts (
  `onStart`),
  B's settings are already present. No stale-config start, no two-enabled flicker;
  `verifySelectionInCategories` is a no-op confirmation.

### 3e. LWW / echo

Per-key `SyncedPrefModified` stamps (existing); master-wins `putRemote(…, 0L)` on cold apply; client
edits
out-stamp via `max(stored + 1, now())`.

---

## 4. Delete `ConfigExportImport` + bespoke `RunningConfiguration`

- Remove `ConfigExportImport` from `APS`, `Sensitivity`, `Safety`, `Scenes` + their
  `syncedKeys`/`reloadInternalState` overrides (empty everywhere ⇒ nothing lost). Delete the
  interface once
  the implementers drop it.
- Delete `buildFromPlugin`/`applyToPlugin` and the fields `aps`, `sensitivity`, `smoothing`,
  `calibration`,
  `apsConfiguration`, `sensitivityConfiguration`, `safetyConfiguration` from
  `RunningConfigurationImpl`.
- **JSON-doc keys** (`SceneDefinitions`, and already-migrated `InsulinConfiguration` /
  `AutomationEvents` /
  `QuickWizard`): flag the `StringNonKey` `SyncSpec(Cold, …)`, keep their **self-observer** (that's
  why
  `reloadInternalState` is empty), drop `scenesConfiguration`.
- `observableKeys()`: the new keys flow in via `coldSyncKeys()`; drop the per-plugin `syncedKeys`
  additions.

---

## 5. Configuration screen (client) — `ConfigurationViewModel`

1. **Un-hide** synced categories: in `buildCategories()`, show a category for `AAPSCLIENT` iff
   `type in syncedSelectionTypes` (replaces the hardcoded `if (!config.AAPSCLIENT)` /
   `if (config.APS)` gates
   for these). Keep the rest hidden — derived from the same SSOT, so they can't drift.
2. **Offline-gate** edits (when `Bidirectional`): `canToggle = … && masterEditingEnabled()`, render
   `MasterOfflineBanner` — reuse the offline-gating already built. A client may switch the master's
   plugin
   only when reachable.
3. **Observe changes:** the VM builds once + refreshes only on local actions today. Observe the
   `activePluginKey(it)` keys for `syncedSelectionTypes` (origin-agnostic — fires on local *and*
   sync) →
   `refreshCategories()`.

---

## 6. Replace `EventConfigBuilderChange` with precise observation

Sender today: only `performPluginSwitch`. Migrate consumers to observe the specific keys (which fire
on
`putRemote` too, so sync-driven changes notify automatically — no event to thread through the
reconcile):

| Consumer                        | Observe instead                                                                                                                                                                    |
|---------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `IobCobCalculatorPlugin`        | `activePluginKey(SENSITIVITY)`, `activePluginKey(APS)` **+ the settings keys its calc reads** — enumerate **exhaustively, test** (loop-critical: a missed key = silent stale calc) |
| Dana / Diaconn pumps            | own `ConfigBuilderEnabled[PUMP_<id>]` (or `ActivePluginPump`)                                                                                                                      |
| `RunningConfigurationPublisher` | drop subscription (covered by `observableKeys()`)                                                                                                                                  |
| Configuration screen            | the `activePluginKey` keys (§5.3)                                                                                                                                                  |
| Setup wizard                    | keys, or keep the event (setup-time, low stakes)                                                                                                                                   |

Then shrink `EventConfigBuilderChange` to a compat shim and delete.

---

## 7. PR ordering

1. **Foundation:** wire `Double`/`Long` in the flat sync (+ tests). No behavior change.
2. **Selection:** the `ActivePlugin*` keys + `activePluginKey` SSOT + `pluginId` (§1c) +
   ConfigBuilder
   bridge (generate/update/observe, echo-skip, master-too), `MasterOnly`. Delete the bespoke
   selection
   fields. Keep `*Configuration` blocks for now.
3. **Settings:** flag plugin `syncedKeys`; delete `ConfigExportImport` + the `*Configuration`
   blocks +
   `buildFromPlugin`/`applyToPlugin`.
4. **Client UX:** un-hide + offline-gate + observe in the Configuration screen; flip synced
   directions to
   `Bidirectional`.
5. **Cleanup:** migrate consumers off `EventConfigBuilderChange`; delete the event.

(Within 2–4, **dual-write** old fields + new keys for one release and prefer new on apply, to
survive
master/client version skew; drop the old fields next release.)

---

## 8. Test matrix

- Selection round-trip master→client and client→master (`Bidirectional`); LWW on simultaneous edits.
- **Settings travel with selection:** switch active plugin → new plugin's settings present *before*
  it
  starts.
- **Atomicity:** no two-enabled / zero-enabled intermediate.
- **Compile-forced SSOT:** add a dummy `PluginType` → `activePluginKey` fails to compile (regression
  guard).
- Offline-gating: Configuration toggles disabled + banner on a client when master unreachable;
  master
  unaffected.
- **IobCob precision:** recomputes on sensitivity/APS selection and its settings; **not** on
  unrelated
  toggles (BG source, sync).
- Export/import: `ActivePlugin*` absent from backups; `ConfigBuilderEnabled` restores selection;
  `ActivePlugin*` regenerated on start.
- Value identity: rename a plugin class → selection sync still works (proves `pluginId`, not
  `simpleName`).

---

## 9. Non-goals / explicitly deferred

- **Generic `Key<T>` / composed-key sync.** Not needed: flat per-type keys + the exhaustive `when`
  give the
  type-coupling and atomicity without it. The single `String` value is fine — SharedPreferences and
  the wire
  are string-based anyway. Revisit only if a broad pattern of complex-valued keys appears.
- **Multi-select active sets** (`GENERAL/CONSTRAINTS/LOOP/SYNC`): left as `null` until a
  multi-active
  representation is genuinely needed (a `StringSet` key).
- **Symmetric per-key LWW** (cold wire carries no `lastModified`) — same status as the rest of the
  cold path;
  fix holistically, not here.
