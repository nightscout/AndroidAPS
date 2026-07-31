# CWF Complication Slots — Status & Reference

Originally written as a task prompt for Claude Code. The core implementation is now **complete
and confirmed working on device**. This file has been restructured into a reference document:
what was built, why, the real root causes found along the way (worth keeping for future
maintainers — this feature had several silent-failure modes that took real investigation to
pin down), and the backlog of deferred items.

---

## Status

Complications work end-to-end on the Custom watch face: 3 slots, real system providers,
long-press picker, CWF-json-driven position/size/visibility and styling, correct z-order,
correct behavior across a fresh install. Confirmed on device.

**Not working / deferred** (not blocking, see full list at the end):
- AAPS Settings-menu entry point for the picker (platform limitation, not an app bug — see
  Root Cause 4 below).
- Provider catalog for CWF-driven defaults, lock, per-slot supported-type restriction,
  cross-CWF persistence semantics, provider-name summary in the menu — all designed but not
  yet implemented (see "Deferred design items" below).

## Final architecture (as built)

- **Slot declaration lives in `CustomWatchface.kt`**, not in `WatchFace.kt`. It overrides
  `createComplicationSlotsManager()` directly — `WatchFaceService.createComplicationSlotsManager()`
  is available on the grandparent class, so no hook/abstract method was needed on `WatchFace.kt`
  or `BaseWatchFace.kt` for this part. This was a deliberate correction during the session: an
  earlier plan to add a `getComplicationSlots()` hook to `WatchFace.kt` was reverted because it
  put Custom-specific logic into a file shared by every watch face.
- **`WatchFace.kt` only gained generic, no-op-by-default plumbing**: it stores the
  `ComplicationSlotsManager` reference handed to `createWatchFace()` (previously received and
  discarded), and the render loop draws whatever slots exist (`slot.render(...)` per enabled
  slot) plus the matching `renderHighlightLayer` pass for the system editor's selection
  highlight. For any watch face that declares zero slots (the unchanged default), this loop is
  a complete no-op — verified, not assumed.
- **Slot position/size/visibility come from the CWF json**, through the same `ViewMap`
  machinery used by every other view (`COMPLICATION1/2/3` are `ViewMap` entries, `FrameLayout`
  placeholders in `activity_custom.xml`). `ComplicationSlotBounds` are fractional
  (unit-square, canvas-relative) — converted from the CWF json's `WIDTH`/`HEIGHT`/`TOPMARGIN`/
  `LEFTMARGIN` (expressed in the fixed 400×400 `templeResolution` space) by dividing by
  `templeResolution`; this conversion is resolution-independent, no screen-size lookup needed.
- **Known accepted limitation**: `ComplicationSlot.bounds`/`.enabled` are not mutable at
  runtime in androidx.wear.watchface 1.2.1 — they're fixed at slot-creation time
  (`createComplicationSlotsManager()`, called once per `Engine` instance). Loading a different
  CWF updates colors/fonts live, but position/size/visibility only refresh on the next `Engine`
  restart (watch face reselection, reboot, app restart). Accepted tradeoff, not fixed further.
- **Styling** (`FONT`/`FONTTITLE`/`FONTCOLOR`/`FONTTITLECOLOR`/`COLOR` CWF json keys) is applied
  to each slot's `ComplicationDrawable` via its `...Active` setters
  (`setTextTypefaceActive`/`setTitleTypefaceActive`/`setTextColorActive`/`setTitleColorActive`/
  `setIconColorActive`), reusing the existing `FontMap` for typefaces. Applying styling is
  idempotent and safe to re-run whenever the CWF (re)loads.
- **Z-order**: `background` was pulled entirely out of the `activity_custom.xml` view hierarchy
  and is drawn manually on the canvas, first, before anything else — because it's the one
  fully-opaque, always-repainted layer needed to prevent visual ghosting (a real, previously-
  encountered defect on this watch face, unrelated to complications but load-bearing for their
  correct rendering too). Complications render next. The rest of `main_layout` (chart,
  `cover_chart`, freetexts, etc., including `cover_plate`/hands where present) draws last,
  unchanged internally — so `cover_chart`'s cutout holes correctly mask/finish complication
  edges exactly as they already did for the chart. No separate `cover_plate`/hands split was
  needed in the end for the specific test CWF used this session (it has no `cover_plate` and a
  transparent `cover_chart` over the complication slots) — **re-verify z-order on a CWF that
  does use an opaque `cover_plate` over complications**, that combination was never directly
  tested.
- **Taps** are routed entirely by the framework (`TapListener` in `WatchFace.kt` explicitly
  ignores taps landing on a complication slot) — no manual hit-testing needed or implemented.
- **Provider selection**: the system's native long-press → Customize flow works end-to-end
  (`ConfigurationActivity`, `EditorSession.createOnWatchEditorSession`,
  `openComplicationDataSourceChooser`). The AAPS Settings-menu path does not — see Root Cause 4.

For the exact current file-by-file diff, cross-reference Claude Code's end-of-session summary
(requested separately) rather than treating the above as a line-level spec.

## Root causes found this session (keep for future maintainers)

This feature had **five independent, unrelated bugs**, each producing symptoms that looked like
— or masked — the others. Listed in the order they were found, because later ones were only
reachable once earlier ones were fixed.

**1. Legacy-API false start.** The first implementation draft targeted the pre-AndroidX
complications API (`android.support.wearable.complications.*`, manual `ComplicationDrawable`
+ `onComplicationDataUpdate`/`onTapCommand`/`setActiveComplications` overrides) because it was
  written before `WatchFace.kt` had been reviewed. The real base class already runs on
  `androidx.wear.watchface` (`WatchFaceService`, `ComplicationSlotsManager`,
  `Renderer.CanvasRenderer2`) — confirmed by reading `WatchFace.kt` directly. None of that legacy
  code was usable; the whole complications-specific implementation was rewritten against the real
  API. Lesson: for this codebase, always verify the actual base-class API before writing
  framework-integration code — don't infer it from an older sibling implementation.

**2. Dagger injection race in `createComplicationSlotsManager()`.** This method can run before
`BaseWatchFace.onCreate()`'s `AndroidInjection.inject(this)` has completed, under an engine
(re)creation path unrelated to normal app startup — it threw
`UninitializedPropertyAccessException` on `complicationDataRepository`, silently caught by
`WatchFaceService`'s own background-thread handler (no visible crash, just no complications).
Fixed with an explicit `daggerInjectionComplete` flag on `BaseWatchFace`, set right after
injection completes, checked before touching `complicationDataRepository` — deliberately **not**
a `catch (UninitializedPropertyAccessException)`, which would also mask unrelated lateinit bugs
elsewhere in the same call chain.

**3. `EditorSession` binding to a disconnected headless engine, crashing on close.** The
long-press picker's `EditorSession` does not always attach to the live, on-screen engine
instance — `WatchFace.getOrCreateEditorDelegate()` falls back to spinning up its own reflection-
constructed headless instance when no delegate is registered yet at that exact moment. That
headless instance never runs `Service.onCreate()` (built via
`getConstructor().newInstance()` + `setContext()`, which only calls `attachBaseContext()`), so
`AndroidInjection.inject(this)` never ran on it either. When the editor session closed,
`BaseWatchFace.onDestroy()` called `simpleUi.onDestroy()` on that never-injected instance →
`UninitializedPropertyAccessException` → the exception killed the binder connection → the system
dropped the editing session and recovered the watch face as a fresh engine instance, **discarding
the provider assignments it had already successfully persisted moments earlier**. This produced
the "picker succeeds, watch face still shows nothing configured" symptom, reproducible
identically across two separate test sessions. Fixed with `BaseWatchFace.ensureInjected()`
(manually triggers Dagger injection via `(applicationContext as? HasAndroidInjector)?.
androidInjector()?.inject(this)` when `daggerInjectionComplete` is false), called at the top of
both `createComplicationSlotsManager()` and `onDestroy()` — not a narrow guard on `onDestroy()`
alone, which would have left the headless instance's wrong fallback bounds and a
preview-render crash unaddressed.

**4. AAPS Settings-menu picker cancelled by Wear Services.** Confirmed via logs
(`WFUnderEditingResolver: No active editing session` / `ComplicationsManager$ComplicationsException`
/ `ProviderChooserControl: Cancelling ... as there is no editing session in progress`): the
system's own long-press flow supplies the real running watch-face instance ID
(`WatchFaceId(id='wfId-14')`) when it builds its `EditorRequest`; our self-built
`EditorRequest` (used by the Settings-menu redirect) had no way to supply that and resolved to
an empty `WatchFaceId('')`. Wear Services' `WFUnderEditingResolver` requires its own
system-level editing-session bookkeeping to find an active session before it will let the
provider chooser proceed — an app-initiated launch cannot satisfy that on its own. **An attempted
fix (sourcing `watchState.watchFaceInstanceId` via a new `onInteractiveWatchFaceInstanceId` hook)
was implemented, tested, and disproven** — the log showed the correct `wfId-14` being supplied and
the chooser was still cancelled — and was fully reverted (all three parts: `ComplicationPickerSupport.kt`,
`CustomWatchface.kt`, `WatchFace.kt`) rather than left in as dead/misleading code. **No app-side
fix is currently known.** Parked as a platform limitation; only worth revisiting if a genuine
SysUI-level entry point for triggering the customize flow is ever found (see deferred item below).

**5. Black complication squares despite everything upstream being correct.** The hardest one —
data was present (`slot.complicationData` correct), styling was correct (transparent
background, white text, non-zero bounds — confirmed by a temporary magenta debug outline drawn
around each slot's exact render bounds, which *was* visible even though the complication
content wasn't), the render call itself ran with the right `RenderParameters`
(`WatchFaceLayer.COMPLICATIONS` present) — yet nothing was drawn, on a genuine fresh-install
cold start specifically (a watch-face-switch-and-back test does *not* reproduce it — that keeps
the app process alive, only recreating the `Engine`, which is not equivalent to a true process
cold start). Root cause, found by reading `ComplicationDrawable.setComplicationData()`'s actual
source: when `loadDrawablesAsync = true` (the normal path — every real observed call used
`async=true`; only the system's preview/screenshot path used `async=false`), the drawable's
*public* `complicationData` field is updated immediately (which is what all prior logging
observed and correctly reported), but the actual internal renderer object
(`complicationRenderer`, private) is only swapped in via a `setOnInvalidateListener` callback
once an async resource-load completes — and on the cold-start path, that callback never fired.
So the live renderer kept stale/empty state and painted nothing, while every externally-observable
signal looked correct. Fixed by overriding `loadData()` to force the synchronous branch
(`super.loadData(complicationData, false)`), so data lands in the live renderer immediately.
Cost (loading a few small icons on the calling thread instead of off it) is negligible for this
watch face, which already invalidates every second. This fix explained, retroactively and
precisely, every previous observation in this investigation (tap-works-but-black, reselecting
one slot fixing both, byte-identical instrumentation before/after) — worth remembering as a
model of what "everything upstream measured correct, nothing renders" can mean: the defect can
be *inside* a leaf rendering class's own internal caching, not in the surrounding pipeline.

**Process lessons worth keeping, independent of the code:** several rounds in the middle of
this investigation involved inferences presented with more confidence than the evidence
supported (e.g. "the original engine was torn down" — never actually observed, just inferred;
"render() was never called" — actually just suppressed by the logging's own change-only
deduplication). Both were caught and retracted once challenged, not defended. When a
diagnostic claim in this codebase says something didn't happen, double check whether the
logging could simply have failed to observe it, before trusting silence as evidence.

## Deferred observations — not blocking, revisit later

1. **Heart rate complication shows a static "Cardio" label instead of live BPM**, unlike the
   same provider on at least one OEM watch face (observed on what was likely a Samsung face),
   which shows the live rate under the heart icon. Possible causes to check when picked up:
   whether our declared `complicationSupportedTypes` (in `CustomWatchface.kt`) matches what
   this provider prefers to send, or whether our default styling always favors the title text
   over the data value for this complication type. The dataType logging used earlier this
   session should reveal directly which `ComplicationType`/fields the provider is actually
   sending.

2. **Fewer providers appear in our in-app complication picker than in the OEM's native watch
   face settings.** Likely not a bug: OEM settings screens often surface a proprietary catalog
   (including providers that don't register through the standard androidx
   `openComplicationDataSourceChooser` API our picker uses), which a standards-based picker
   can't see. Worth a brief confirmation pass later, but treat as expected platform behavior
   unless proven otherwise.

3. **Complications with a numeric/rangeable value should be able to drive `DynProvider`**
   (sliders, rotation, or other dynamic visual effects keyed off the complication's value),
   the same way BG/IOB/battery-level etc. already do. Needs design: how a complication's
   `RangedValueComplicationData` (or similar) maps onto `ValueMap`/`DynProvider`'s existing
   min/max/step model.

## Deferred design items — complication providers & persistence

1. **System + AAPS provider catalog, for default-provider assignment via CWF json.**
   Architecture already decided (don't re-litigate):
    - A new small, stable, shareable vocabulary `ComplicationProviderKeyValues` (enum, in the
      `shared` module next to `JsonKeyValues`) — the public dictionary of provider keywords a
      CWF author can write (e.g. `"step_count"`, `"aaps_iob"`).
    - A private enum `ComplicationProviderMap` in `CustomWatchface.kt` (like
      `FontMap`/`GravityMap`) that maps each `ComplicationProviderKeyValues` key to either a
      `SystemDataSources.DATA_SOURCE_*` constant or an AAPS `ComponentName`, plus its supported
      `ComplicationType`.
    - Get the exact, current `SystemDataSources` constant list by inspecting the real installed
      SDK jar (same technique used all session for androidx internals) — don't trust a
      hand-typed list.
    - New CWF json key: `DEFAULTPROVIDER` (uses
      `androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy`, set at
      slot-creation time in `buildComplicationSlot()`).

2. **Lock a slot's provider (prevent user from changing it).** Confirmed native API:
   `ComplicationSlot.Builder.setFixedComplicationDataSource(Boolean)`. New CWF json key:
   `LOCKPROVIDER` (boolean), read alongside `DEFAULTPROVIDER` at slot-creation time.

3. **Restrict which `ComplicationType`s a slot accepts, per CWF.** `ComplicationType` list
   (confirmed small & stable — ~11 values: `SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`,
   `MONOCHROMATIC_IMAGE`, `SMALL_IMAGE`, `PHOTO_IMAGE`, `GOAL_PROGRESS`, `WEIGHTED_ELEMENTS`,
   `EMPTY`, `NO_DATA`, `NOT_CONFIGURED`) belongs directly in the shared `JsonKeyValues` (unlike
   the provider list — small+stable+reusable, not single-purpose). New CWF json key:
   `SUPPORTEDTYPES` (list), replacing the current hardcoded `complicationSupportedTypes` in
   `CustomWatchface.kt`.

4. **Persistence of provider assignments across different CWF zips.** Needs design
   clarification before implementation, not just coding. Known constraint (see architecture
   section above): bounds/enabled aren't mutable at runtime, "fixed at creation, refreshed on
   next Engine restart" is the accepted tradeoff. Open question to resolve: when a user loads a
   *different* CWF with fewer/relocated visible slots, what should happen to a provider already
   assigned to a slot ID that's no longer visible, or whose meaning changed? Decide the intended
   behavior (keep assignment dormant vs prompt reselection vs clear it) before touching code.

5. **Restrict Settings-menu complication entries to CustomWatchface + visible slots only.**
   Menu should (a) only offer "Complication N" entries when the active watch face is
   CustomWatchface, not Digital/Circle, and (b) only list slots that are
   `visibility:"visible"` in the currently loaded CWF json.

6. **Show the currently-assigned provider's name under each "Complication N" entry**, in both
   the long-press preferences screen and the AAPS Settings-menu screen — same `setSummary()`
   pattern already used elsewhere on that screen. Refresh on screen open (all 3 slots) and
   immediately after a successful pick (that one slot). Needs the real `EditorSession` API for
   a human-readable provider name per slot (don't assume a field name, check the extracted
   androidx source first).

7. **AAPS Settings-menu picker still doesn't open at all.** See Root Cause 4 above — parked as
   a platform limitation, no app-side fix identified. Only revisit if a genuine SysUI-level
   entry point for the customize flow is found.