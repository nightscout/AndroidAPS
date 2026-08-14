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

## Checklist

Core feature (this session):

- [x] 3 real `ComplicationSlot`s declared, positioned/sized/visible from CWF json
- [x] Rendering wired into the render loop, correct z-order for the tested CWF
- [x] Taps routed by the framework (no manual hit-testing)
- [x] Long-press "Customize" picker works end-to-end, providers persist correctly
- [x] Legacy-API false start replaced with real androidx implementation (Root Cause 1)
- [x] Dagger injection race in `createComplicationSlotsManager()` fixed (Root Cause 2)
- [x] `EditorSession` headless-instance crash-on-close fixed (Root Cause 3)
- [x] Black-screen-after-reinstall bug fixed (`loadData()` sync override) (Root Cause 5)
- [x] Temporary debug instrumentation stripped, permanent fix documented
- [x] Show currently-assigned provider name under "Complication N" in the preferences menu
  *(works via the long-press path; AAPS-menu path blocked on the picker fix below, same
  root cause — not a separate bug, see Root Cause 4)*
- [ ] AAPS Settings-menu picker doesn't open — investigate real fix vs confirm no app-side fix
  exists (Root Cause 4; a first fix attempt was tried, disproven, and reverted) —
  **next up, with Opus**
- [ ] Restrict Settings-menu complication entries to CustomWatchface + visible slots only
- [ ] **Complications aren't integrated into the CWF's live per-tick refresh flow** — no
  bounds/position/background update on CWF reload, and no path at all for live `DynProvider`-
  style changes (e.g. BG-level-driven resize/reposition) within the same CWF (see "Open bugs"
  below) — right after the picker fix
- [ ] **Complication visible content (icon/text) renders far smaller than the declared CWF
  bounds box, by a large margin** — confirmed via device testing (see "Open bugs" below)

Visual/UX polish (from screenshot comparison against an OEM watch face):

- [ ] Heart rate complication shows static label instead of live value
- [ ] Complication bounds sizing guidance (square should contain the cutout circle, not the
  other way around) — CWF-authoring guidance, not code
- [ ] Sane default text style (bold/size) for complications without CWF-specified styling
- [ ] Icon position (above/below text) — investigate whether framework- or provider-driven
- [ ] Fewer providers in our picker than in OEM native settings — likely expected, confirm

Design work, not yet started:

- [ ] Provider catalog (`ComplicationProviderKeyValues` + `ComplicationProviderMap`) for
  `DEFAULTPROVIDER`
- [ ] `LOCKPROVIDER` (fixed complication data source)
- [ ] `SUPPORTEDTYPES` per CWF, replacing the hardcoded list
- [ ] Cross-CWF provider-assignment persistence semantics (design decision needed first)
- [ ] `DynProvider` integration for numeric/rangeable complication values
- [ ] Investigate whether any SysUI entry point can trigger the customize flow (only relevant
  if the AAPS Settings-menu picker turns out unfixable)

Not yet tested:

- [ ] Z-order with a CWF that has an opaque `cover_plate` layered over visible complications
  (only tested with a CWF that has none / a transparent `cover_chart` there)
- [ ] Ambient-mode rendering, burn-in protection for complications specifically

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
- **Known accepted limitation, now reclassified as an active bug — see "Open bugs" below**:
  `ComplicationSlot.bounds`/`.enabled` are not mutable at runtime in androidx.wear.watchface
  1.2.1 — they're fixed at slot-creation time (`createComplicationSlotsManager()`, called once
  per `Engine` instance). This was originally treated as an acceptable tradeoff (colors/fonts
  update live, position/size/visibility only on next `Engine` restart), but direct testing
  showed this breaks a core CWF design principle — see "Open bugs" item A, now the top
  architecture priority.
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

**Background regression (introduced by the manual-canvas move, fixed)**
- [x] Symptom: a CWF driving `background` through a `dynPref` colour step (e.g. `key_dark` →
  `color1` black/white) stayed permanently black, while every other view in the same CWF flipped.
- [x] Root cause, confirmed from source, not guessed: when `background` was pulled out of the View
  hierarchy for z-order (commit `e5e8cceeceb`, `id = R.id.background` → `View.NO_ID`), it stopped
  going through `ViewMap.customizeImageView` and got a bespoke path that only ever consulted
  `ViewMap.BACKGROUND.drawable(cwf)` = `dynData?.getDrawable()` (the *image* steps `image1…`,
  `invalidImage`) falling back to `rangeCustom`/`highCustom`/`lowCustom`. It never consulted
  `getColorStep()` (`color1…`, `invalidColor`), never applied a `COLOR`/colour-step tint to the
  image it did resolve, and never fell back to `defaultDrawable`. `customizeImageView` does all
  three for every other image-backed view — background alone lost them.
- [x] Fix keeps the manual canvas draw (still required: `deferMainLayoutDraw()` is true, so
  `mainLayout` paints in `onDrawOverlay` *after* complications — putting background back in it
  would paint it over them). `resolveBackgroundDrawable()` now mirrors `customizeImageView`'s
  three steps, and `onDraw()` paints a flat colour when no drawable resolves.

**Styling parameter audit — Phase 2 (real CWF json keys, not placeholders)**
- [x] `JsonKeys` added: `ICONCOLOR`, `TITLESIZE`, `TITLESTYLE`, `FONTTITLE`, `FONTTITLECOLOR`,
  `BORDERRADIUS`, `RINGWIDTH` (percentage of slot width), `RINGPRIMARYCOLOR`,
  `RINGSECONDARYCOLOR`, `COMPLICATIONSTYLE` (the global section key). `FONT`/`FONTSTYLE`/
  `FONTCOLOR`/`COLOR`/`TEXTSIZE` reused from the shared view vocabulary.
- [x] Three-level cascade wired for every key: per-slot json → CWF-wide `complicationStyle` →
  built-in default.
- [x] **`BORDERRADIUS` confirmed to shape the background fill itself**, not just the content
  inset — `width/2` on a square slot with an opaque `COLOR` background renders a visible
  circle (device-confirmed); with the default transparent background, only the content shrinks,
  no visible shape (nothing to fill). `getBorderRadius` clamps to `min(w,h)/2`, so anything
  ≥ that is harmlessly capped at a full circle.
- [x] **`TITLESTYLE`/`FONTSTYLE` combine with their typeface key** via
  `Typeface.create(base, style)` — works for system font families (real bold/italic variants).
  **Documented limitation**: no synthetic bold for a single-weight custom font resource
  (`ComplicationStyle` exposes no `Paint` to fake it, unlike `TextView`) — affects
  `FontMap`'s `roboto_condensed_*` entries specifically.
- [x] `RINGWIDTH` (percentage-based) replaces the previous ratio-only fallback; `2dp` (the
  library's own default) confirmed rejected — mixes two incompatible resolution-independence
  systems (`dp` vs. this format's `zoomFactor`), and would be the one CWF dimension that
  doesn't scale with its slot.
- [x] **Closes the original "complications render thinner/less readable than OEM" observation.**
  Root cause was the `borderRadius`-driven ~41% content-area inset (already fixed earlier by
  forcing `borderRadius = 0` unless a CWF sets one), not text weight. Once content stopped
  being shrunk, readability at the same declared size matched third-party watch faces — no
  separate bold-by-default styling needed.
## Root causes found this session (keep for future maintainers)

This feature had **five independent, unrelated bugs**, each producing symptoms that looked like
— or masked — the others. Listed in the order they were found, because later ones were only
reachable once earlier ones were fixed.

**Subject 1 — Step 1: generalize picker to N slots** — *code done, device test pending*
- [x] `PrefMap.SHOW_COMPLICATION_1..5` verified end to end. Found and fixed a real defect:
  `CwfMetadataKey.CWF_PREF_WATCH_SHOW_COMPLICATION4`/`5` both carried the json key
  `key_show_complication1`. It compiled, but `CustomWatchface`'s metadata→SharedPreferences loop
  means a zip setting `key_show_complication1=false` turned off slots **1, 4 and 5** together, and
  slots 4/5 could never be driven from a zip at all.
- [x] Slot identity moved into its own enum, `CustomWatchface.ComplicationMap(id, preferenceKey)`,
  replacing the five loose `COMPLICATION_SLOT_ID_N` constants. `ViewMap` now points at an entry
  (`complication`) instead of repeating a raw id, so one enum entry carries the slot's whole
  identity: system id + the settings row that opens its picker.
- [x] `ComplicationPickerSupport` is now watch-face-agnostic: it names no watch face, hardcodes no
  preference keys and no slot count. It asks the active watch face for the slots it hosts through
  two neutral interfaces in `WatchFaceComplication.kt` — `ComplicationSlotInfo` (`id`,
  `preferenceKey`) and `WatchFaceComplicationSlots` (`complicationSlots`) — reached via
  `WatchFaceCatalog`, which stays the single place allowed to name a watch face. A watch face with
  a fixed layout can answer with a constant list; CWF answers with a list derived from
  `ComplicationMap`. Deliberately nothing about visibility or user settings is in that contract:
  which slots exist is a property of the watch face, whether the user wants one shown is not.
- [x] "Show Complication i" toggle added before each picker row in
  `watch_face_configuration_custom.xml` for all 5 slots, with `android:dependency` graying the
  picker row when its toggle is off. Cosmetic only — `dependency` disables, never hides — so it
  complements Step 2's filtering rather than replacing it. First device test showed the disabled
  row looking identical to an enabled one: `androidx.preference` only calls `setEnabled(false)` on
  the row, and `preference_material_multiline.xml` (applied to *every* preference in the watch app)
  hardcoded `android:textColor`. Now a colour state list — so disabled state renders on every
  preference screen, not just these rows.
- [ ] Device-confirm slots 4 and 5 (added by hand, never build-tested through the generic path)
  actually render, tap, and persist a provider correctly. Build is green; not yet run on device.

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
- Design reported, not yet approved or implemented. Scoping must gate on **watch face identity**,
  not on which keys a screen contains: `watch_face_configuration_digitalstyle.xml` also declares
  `key_show_date` and `key_show_week_number`, so key-presence duck typing would wrongly hide
  Digital's rows. Both activities already resolve that identity before creating the fragment
  (`ConfigurationActivity` from the editor intent's component, `WatchfaceConfigurationActivity`
  from `key_selected_watchface` via `WatchFaceCatalog`), so it only has to be passed down.
  `WatchFaceCatalog.complicationWatchFace` is the placeholder constant that becomes that per-screen
  lookup. For the visibility query itself: `CustomWatchface` publishes the set of preference keys
  used by at least one currently-visible view to SharedPreferences on CWF load — same pattern as
  the metadata→preferences loop and as `cacheAssignedDataSourceNames` — and the screen filters on
  it. Taken at load time from the json, never per frame: a `DynProvider`-driven row that appears
  and vanishes as BG moves would be worse than no filtering.

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
   sending. **Confirmed visually** via a side-by-side screenshot comparison (same heart-rate
   provider: our watch face shows "Cardio", a Samsung watch face shows "83") — see also item 1c
   below, from the same comparison.

1b. **Complication content fills the available space poorly compared to an OEM watch face,
for both slot sizing and default text style.** From the same screenshot comparison:
- Our complication square is currently inscribed *inside* `cover_chart`'s circular cutout
(smaller than the circle), while the OEM reference has it the other way around — the
circle inscribed inside a larger square, so content fills the visible circle much better.
This is a per-CWF `WIDTH`/`HEIGHT`/margin tuning issue, not a code bug — worth writing up
as sizing guidance for CWF authors (make the complication's declared bounds larger than
the cutout circle, not smaller) rather than something to fix in code.
- Our complication text renders visibly thinner/less readable than the OEM reference at the
same physical size. Same root cause as the already-fixed default-background issue
(library defaults apply whenever the CWF json doesn't specify `FONT`/`FONTSTYLE`) — the
library's own default text weight/size is too thin for a complication's small area. Give
complications a sane default style (e.g. bold, a size tuned for typical slot dimensions)
independent of CWF-provided styling, symmetric to what was already done for the default
background color.

1c. **Icon position relative to the text/value differs between our rendering and an OEM watch
face, and inconsistently so** — same screenshot comparison shows the OEM face placing the
icon above the text for heart rate but below it for step count, while our rendering places
it in the same position for both. Genuinely unresolved, don't guess: check whether
`ComplicationDrawable`/`ComplicationStyle`'s icon layout is framework-decided based on
bounds shape/aspect ratio (in which case matching it may just require different bounds), or
whether it's provider-driven, before assuming there's something to configure on our side.

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
   min/max/step model. See also "Open bugs" item A above for the converse direction (other live
   values driving a complication's own bounds/appearance) — the two together mean complications
   need full two-way integration with the existing `DynProvider` mechanism, not just one-way.

## Open bugs — active, not yet fixed (found after initial "complete" status)

**Priority order for the next work session, as explicitly set by the project owner:**
1. Provider name shown under "Complication N" in the preferences menu — **done** for the
   long-press path; AAPS-menu path blocked on item 2 below, not a separate bug
2. AAPS Settings-menu picker fix (Root Cause 4 follow-up) — next up, with Opus
3. **Architecture item A below** — right after the picker fix
4. Item B below (content-fills-bounds sizing bug)

**A. Core architecture principle violated: complications are not integrated into the CWF's
live rendering flow, unlike every other view.** This is the central design rule of the whole CWF
format: the user fixes *all* display parameters in the json — position, size, font (typeface +
size), style, background (transparent/color/image) — and for every existing view type
(`TextView`, `ImageView`, the chart, etc.) these are re-evaluated live, on **every refresh
tick** — not just on CWF (re)load. This already happens today via `ViewMap`/`DynProvider`/
`ValueMap`: a view's background image, size, position, or rotation can already change from one
refresh to the next based on a live data value — the clearest example being BG level: a view's
background image swaps (e.g. high/mid/low background) the moment glycemia crosses into
hyperglycemia, with no CWF reload involved at all, just the normal per-tick refresh. Position/
size offsets driven by `DynProvider` (`getTopOffset`/`getLeftOffset`/`getRotationOffset` etc.)
work the same way — continuously re-evaluated, not fixed at load time.

Complications currently violate this on two counts, not one:
1. They don't refresh even on CWF *reload* (the originally-reported symptom: switching to a
   CWF with a different complication layout shows no change until some unrelated event forces
   an `Engine` restart).
2. More fundamentally, they can't participate in the same **per-tick** dynamic adjustments that
   other views already have via `DynProvider` — e.g. a complication's size, position, or
   background could reasonably need to change live when BG crosses a threshold, exactly like an
   `ImageView`'s background already does, and there's currently no path for that at all, not
   even in principle. This connects directly to "Deferred observations" item 3 below
   (complications driving `DynProvider`) — that item was framed as "complication value drives
   other visual effects"; this is the converse and equally necessary: other live values (BG
   level, etc.) must be able to drive a complication's own bounds/appearance, the same as they
   already drive every other view type.

The goal is not "find a way to occasionally refresh bounds on CWF switch" — it's
**complications behaving like every other CWF-driven element, on every refresh tick**: read
from `DynProvider`/the current CWF json state each time, exactly like background image
swapping or position offsets already do for text and image views.

Investigation needed (don't assume an answer, this session already confirmed one specific
constraint that must be re-verified, not re-guessed):
- Is there a supported androidx API to update an existing `ComplicationSlot`'s bounds/enabled
  state at runtime? (Already checked once this session and found no such API in
  androidx.wear.watchface 1.2.1 at the `ComplicationSlot`/`ComplicationSlotsManager` level —
  re-verify this conclusion specifically, since it's the crux of everything below.)
- **Also check a lower-level path before concluding engine recreation is required**: is there a
  `CanvasComplication`-level rendering call that accepts a `Rect`/bounds parameter directly per
  invocation (as opposed to `ComplicationSlot.render()`, which uses the slot's own cached
  bounds internally)? If the underlying drawable/renderer can be driven with per-frame bounds
  supplied by us — the same way `ViewMap.customizeViewCommon()` already computes fresh
  layout params every tick — that would let complications join the existing per-tick refresh
  flow directly, with no engine recreation needed at all. This is the outcome to aim for if it
  exists, since it's strictly better regardless of refresh cadence.
- **Only if that's genuinely not possible**, the remaining path is making the watch face
  `Engine` recreate itself. Refresh cadence here is real CGM data (BG readings), roughly every
  5 minutes — possibly a bit off-cadence for `EXT1`/`EXT2` follower data on dual/multi-user
  screens, which isn't perfectly synced with the primary reading — **not** a per-second UI tick.
  This makes periodic engine recreation considerably more plausible as an acceptable fallback
  than it would be at second-level frequency, though still worth confirming it doesn't
  re-trigger the Dagger-injection-race or `EditorSession`-crash bugs already fixed this session,
  and doesn't produce a visibly jarring restart on every BG update.
- Whichever path is real, the end state should be: a complication's position/size/visibility/
  background can update with the same immediacy already true for every other CWF view, on CWF
  reload *and* on live data changes within the same CWF.
- **The refresh trigger itself is not something to design** — the CWF refresh loop is already
  deterministic and driven by an external event (new data received from the phone), the same
  event that already refreshes every other view's position/size/background. Whatever mechanism
  is chosen for complications (per-frame bounds passed directly, or — only if unavoidable —
  engine recreation) should hook into that same existing trigger point, not invent a separate
  timer/polling mechanism of its own.

**B. Complication visible content (icon/text) renders far smaller than the declared CWF bounds
box.** Separate issue from A — this is about content-fits-within-bounds, not
bounds-not-refreshing. Found via device testing, comparing against a Samsung native watch
face's complications at equivalent visual size:
- A box matching `cover_chart`'s cutout circle diameter (~103px in the 400×400 CWF coordinate
  space) produces a "ridiculously small" result.
- Even a 140px box — well larger than the 103px circle — still doesn't fill comparably to the
  same provider on the Samsung watch face.
- This means CWF authors would need to declare boxes far larger than the intended visual area to
  compensate, which defeats the CWF format's core design goal: precise, reproducible pixel
  positioning in a 400×400 space, independent of screen density/resolution.

Hypothesis to verify against real source, don't assume it's correct: `ComplicationDrawable`/
`ComplicationStyle` likely reserves internal padding/inset around the icon+text by default —
possibly space for a ranged-value progress ring, applied even when the complication type isn't
`RANGED_VALUE` — and if that inset is a fixed size (e.g. dp-based) rather than proportional to
the bounds, it would explain the observation exactly: a small box is dominated by the fixed
margin, a bigger box is less dominated but still short by the same fixed amount. Also re-check
whether our bounds-to-fraction conversion (dividing CWF px by `templeResolution`=400 — see
architecture section above) is itself correct, or whether there's a separate dp/px unit mismatch
somewhere between what we pass to the drawable and what the CWF format's contract expects.

Report the actual mechanism (ring/inset defaults found in source, and/or a confirmed unit
mismatch) before proposing a fix (e.g. disabling the ring reservation, exposing a configurable
inset via a new CWF json key, or correcting a unit conversion).

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

## Potential major finding — watch face selection, not just complications (needs confirmation)

While fixing Root Cause 4, discovered that Samsung SysUI's broadcast handler
(`setActiveWatchfaceAndStartEditor`, triggered via `ACTION_EDIT_WATCH_FACE`) has a side effect:
it makes the target watch face **active**, not just editable. This may matter far beyond
complications: on newer "Factory Built" Wear OS 5+ Samsung watches, the legacy code-based watch
face picker library was removed entirely, so users on that hardware likely cannot select/
activate `CustomWatchface` (or `Digital`/`Circle`) via the normal on-watch picker at all — this
broadcast mechanism could be **the only way** for them to select any of AAPS's watch faces, not
just a UX nicety for the complications picker.

**Not yet confirmed** — the test device used throughout this session still has the legacy
library (long-press already worked there before any of this work), so nothing tested so far
proves this solves the newer-watch problem. Needs confirmation on an actual Wear OS 5+ Samsung
watch with the legacy library removed. Flag for community testing from an affected user before
acting on the item below.

**If confirmed, a larger follow-up architecture item** (not urgent, not started, only relevant
once the above is confirmed): today there's a single AAPS Settings-menu entry that shows
whichever watch face's preferences XML is currently active (Digital/Circle/CWF) — this
implicitly assumes the user can already select any watch face via the normal picker, which is
exactly what's broken on affected hardware. If the broadcast mechanism genuinely enables
selection, the menu would need restructuring:
- Three distinct, always-visible menu entries (one per watch face: Digital, Circle, Custom),
  not one entry conditional on whichever is currently active.
- Each entry should be able to both *select+activate* its watch face (via the same broadcast
  mechanism, generalized beyond `CustomWatchface`-specific complication slot handling — this
  points to extracting a small reusable "ask SysUI to activate+edit watch face X" helper rather
  than keeping the logic complication-specific) and show/edit its preferences, even for watch
  faces with no complications at all (Digital, Circle) which don't currently need any of this
  machinery.
- Likely needs a renamed/reworded menu entry point (something like "Select and Configure" rather
  than the current framing) to reflect the new dual purpose.