# ANR: runBlocking in ProfileFunctionImpl.getProfile() blocks main thread

## Issue

The app freezes (ANR) when the user opens preferences or navigates UI while the pump is
communicating. The main thread blocks for 5+ seconds waiting on a database query.

## Root Cause

`ProfileFunctionImpl.getProfile()` uses `runBlocking` to call a suspend function (Room DB query)
synchronously. When called from the main thread during Compose recomposition, it blocks the UI.

**File:** `implementation/src/main/kotlin/app/aaps/implementation/profile/ProfileFunctionImpl.kt`

```kotlin
// Line 93-105
override fun getProfile(time: Long): EffectiveProfile? {
    val rounded = time - time % 1000
    synchronized(cache) {
        if (cache.containsKey(rounded)) {
            return cache[rounded]  // Fast path - cache hit
        }
    }
    // SLOW PATH - blocks calling thread on DB query
    val ps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(time) }
    ...
}

// Line 75-86 (same issue)
private fun getProfileName(time: Long, ...): String {
    val profileSwitch = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(time) }
    ...
}
```

## Call Chain (from ANR stack trace)

```
ComposeMainActivity (main thread, Compose recomposition)
  → AllPreferencesScreen:101
    → AllPreferencesScreen$getPreferenceContentIfEnabled:90
      → PluginBase.isEnabled:67 → isEnabled:72
        → OpenAPSSMBPlugin.specialEnableCondition:196
          → PumpWithConcentrationImpl.getPumpDescription:136
            → PumpWithConcentrationImpl.getConcentration:43
              → InsulinImpl.getICfg:49
                → ProfileFunctionImpl.getProfile:91 → getProfile:105
                  → runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt() }
                    → Unsafe.park  ← BLOCKED waiting for DB
```

## Why It Happens

1. Compose recomposition triggers preference screen rendering
2. Preference visibility/enable checks call `specialEnableCondition` on pump plugins
3. `PumpWithConcentrationImpl` needs concentration, which needs insulin config, which needs profile
4. `getProfile()` hits a cache miss and calls `runBlocking` on the main thread
5. Room DB query competes with other threads (pump communication writing to DB)
6. Main thread blocks → ANR after 5 seconds

## Why runBlocking Is There

Room throws `IllegalStateException: Cannot access database on the main thread` without it.
`runBlocking` bridges the sync `getProfile()` API to the async Room query.

## Possible Fixes

### Option A: Cache concentration (quick fix)

Cache the insulin concentration in `PumpWithConcentrationImpl` instead of recomputing from profile
on every `getPumpDescription` call. Update cache when profile changes via Flow.

### Option B: Make getProfile() return cached-only for main thread

```kotlin
override fun getProfile(time: Long): EffectiveProfile? {
    val rounded = time - time % 1000
    synchronized(cache) {
        if (cache.containsKey(rounded)) return cache[rounded]
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        // Don't block main thread - return null on cache miss
        // Schedule async cache fill
        appScope.launch { fillCache(rounded) }
        return null
    }
    val ps = runBlocking { persistenceLayer.getEffectiveProfileSwitchActiveAt(time) }
    ...
}
```

### Option C: Make specialEnableCondition not need profile

Break the chain so preference screen rendering doesn't need DB access. The `getPumpDescription` →
`getConcentration` → `getProfile` chain seems excessive for a UI visibility check.

### Option D: StateFlow-based profile

Replace `getProfile()` with a `StateFlow<EffectiveProfile?>` that's updated reactively. UI observes
the flow, never blocks. Biggest refactor but cleanest long-term.

## Reproduction

1. Select Dana RS pump with emulator enabled (or any pump that's actively communicating)
2. Open Preferences / AllPreferencesScreen
3. If pump communication is writing to DB at that moment → ANR

## Additional Blocked Thread: AutomationPluginHandler

A second thread is also blocked on the same pattern at the same time:

```
"AutomationPluginHandler" (background HandlerThread): WAIT
  → AutomationPlugin.processActions$automation:285
    → LoopPlugin.getRunningMode:220
      → LoopPlugin.getRunningModeRecord:224
        → LoopPlugin.runningModePreCheck:349
          → Unsafe.park  ← BLOCKED waiting for DB
```

**File:** `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/loop/LoopPlugin.kt`

This confirms the issue is systemic — multiple threads use `runBlocking` to access the DB. When
Room's query executor is saturated (e.g. pump communication writing history events), all
`runBlocking` callers stall simultaneously.

## Scope of runBlocking Usage

The problem is not limited to `ProfileFunctionImpl`. Any `runBlocking { persistenceLayer.xxx() }`
call is a potential ANR if called from the main thread, or a potential deadlock if multiple
background threads compete for Room's executor. A codebase-wide audit of `runBlocking` usage against
Room queries is recommended.

## Not Related To

- Dana RS emulator (happens with any pump)
- Any specific pump driver
- The `activePump` vs `activePumpInternal` issue (separate bug)
