# Prompt for Claude Code — Wear CustomWatchface complication slots

Paste everything below to Claude Code, working in the current worktree (branch
`wear/CWF_Complication`).

---

## 0. Before anything else

1. Read the root `CLAUDE.md` in full and follow it strictly for this entire task, with no
   exceptions. If any instruction below conflicts with it, `CLAUDE.md` wins — stop and tell me
   about the conflict rather than working around it.
2. Read `_docs/CWF_ComplicationSlotsRules.md` (task-specific supplementary context — the `wear`
   module has no module-level `CLAUDE.md` of its own, this file fills that gap for this task).
3. Do not create a new branch or worktree. You are already on `wear/CWF_Complication` in the
   worktree I'm running you from — work directly here.
4. Read `app/aaps/wear/watchfaces/utils/WatchFace.kt` (or wherever it actually lives — search
   for the class `WatchFace` that `BaseWatchFace` extends) before writing any code. This is the
   real base engine class and it confirms the codebase already runs on the modern
   `androidx.wear.watchface` API (`WatchFaceService`, `ComplicationSlotsManager`,
   `Renderer.CanvasRenderer2`) — not the legacy complications API. Everything below assumes
   this; if you find it's not actually the case, stop and tell me before proceeding.
5. Before writing implementation code, produce a short written plan (see "Deliverables" below)
   covering section 4's open design points. This feature was attempted twice before on older
   architectures and abandoned both times — don't repeat that by rushing past the design phase.

## 1. Goal

Add **3 working complication slots** (`COMPLICATION1`/`2`/`3`) to the "Custom" watch face
(`CustomWatchface.kt` in the `wear` module), properly integrated with the AndroidX Wear
Watchface complications framework already in place in `WatchFace.kt`/`BaseWatchFace.kt`.

The watch face itself (background image, text/image fields driven by a user-supplied CWF zip)
already works and is unrelated — don't touch anything outside what's needed for complications.

## 2. Context and prior art (read before coding — mind the caveat)

Two earlier, incomplete attempts exist, **both targeting a legacy pre-AndroidX complications
API that this codebase no longer uses**:

- A first attempt with `FrameLayout` complication placeholders in the layout but no real
  rendering.
- A second, more complete attempt using `android.support.wearable.complications.rendering.
  ComplicationDrawable`, overriding `onComplicationDataUpdate`/`onTapCommand`/
  `setActiveComplications`, and driven by CWF JSON keys `FONT`/`FONTTITLE`/`FONTCOLOR`/
  `FONTTITLECOLOR`/`COLOR` for styling. **This attempt also included a hardcoded test hack
  (fake `complicationId = 10`) that never worked** — see `_docs/CWF_ComplicationSlotsRules.md`
  for why.
- A hand-adapted draft `CustomWatchface.kt` (attached, see file with `.reference` suffix or
  similar — rename/inspect as needed) tried to port the second attempt onto the current
  architecture **before `WatchFace.kt` had been reviewed**. It targets the wrong (legacy) API
  for the complications-specific parts and will not compile as-is. **Use it only for the
  non-complication-API intent** described in `_docs/CWF_ComplicationSlotsRules.md` (JSON key
  naming, slot position/size driven by CWF json via `ViewMap`) — do not port its
  `ComplicationDrawable`/`onComplicationDataUpdate`/`onTapCommand`/`setActiveComplications`
  code.

Current state, confirmed from the real `WatchFace.kt`:

- `WatchFaceService.createWatchFace(...)` receives a `ComplicationSlotsManager` parameter but
  it is currently unused and unstored — no complication slots are declared anywhere.
  `createComplicationSlotsManager()` is not overridden, so the framework almost certainly
  builds an empty manager.
- The `TapListener` installed in `createWatchFace()` already explicitly ignores taps on a
  complication slot with the comment "handled by the system" — i.e. the framework is expected
  to own tap routing for complications once real slots exist. Manual hit-testing (as the old
  attempts did) should not be needed.
- The current layout (`activity_custom.xml`) already has 3 `FrameLayout` placeholders
  (`complication1`/`2`/`3`) with fixed pixel positions — useful as the source of truth for slot
  bounds, but they carry no framework meaning by themselves.
- `BaseWatchFace.kt`/`CustomWatchface.kt` (current, no complications) — the production "Custom"
  watch face you're extending.

## 3. What "done" looks like

- 3 real `ComplicationSlot`s exist for the Custom watch face, positioned/sized from the CWF
  json exactly like every other view in `CustomWatchface` (respecting visibility too — a slot
  hidden by the CWF json shouldn't be selectable/shown).
- The system's native complication configuration UI (long-press watch face → customize, or
  equivalent) lets the user pick a provider for each visible slot — confirm this actually works
  end-to-end; if the modern API still requires something explicit from us for this to appear
  (e.g. correct `ComplicationSlot` configuration, `supportedTypes`, default provider), implement
  it rather than assuming it's fully automatic.
- Complications render correctly in interactive and ambient mode, and respect the CWF json's
  styling keys (`FONT`, `FONTTITLE`, `FONTCOLOR`, `FONTTITLECOLOR`, `COLOR`) where the chosen
  rendering approach allows it — confirm whether `CanvasComplicationDrawable` (wrapping the
  legacy `ComplicationDrawable` for styling) or a fully custom `CanvasComplication` is the
  right vehicle for this under the current androidx watchface version in use; check the
  version pinned in the Gradle files and cross-check against current androidx docs before
  deciding, API details have moved around across versions.
- Taps on complications are handled by the framework (no manual hit-testing needed) — verify
  this holds once slots are real, and only add manual handling if you find a concrete gap.
- No hardcoded/fake complication IDs anywhere in the shipped code path.
- Existing (non-complication) behavior of `CustomWatchface` and of every other watch face
  extending `BaseWatchFace`/`WatchFace` is unaffected (in particular: other watch faces should
  keep having zero complication slots, unaffected by whatever mechanism you add to let
  `CustomWatchface` declare its 3).

## 4. Open design points — resolve before implementing

1. **Where do complication slots get declared?** `createComplicationSlotsManager()` is a
   `WatchFaceService` override point. Since only `CustomWatchface` needs slots, the cleanest
   pattern is probably an `open`/`protected` hook on `WatchFace.kt` (e.g.
   `protected open fun getComplicationSlots(): List<ComplicationSlot> = emptyList()`) that
   `WatchFace.kt` uses in its own `createComplicationSlotsManager()` override, with
   `CustomWatchface` being the only subclass that overrides it with real content. Verify this
   pattern against the actual androidx API surface available in this project (exact method
   name/signature may differ by version) and adjust as needed — don't guess a signature and
   ship it unverified.
2. **Dynamic bounds from the CWF json.** Slot position/size can change whenever the user loads
   a different CWF zip — this is not a fixed, compile-time layout. Determine whether
   `ComplicationSlot` bounds can be updated at runtime (there is API surface for this in
   androidx watchface, exact shape needs checking) or whether slots need to be torn down and
   recreated when a new CWF is loaded. Whatever you pick, it must stay in sync with the
   existing `ViewMap`/`customizeViewCommon` flow that already repositions the placeholder
   `FrameLayout`s from the json on every CWF (re)load.
3. **Rendering integration.** `WatchFaceRenderer.render()` in `WatchFace.kt` currently only
   calls `onDraw(canvas)`. Complications will need their own render call
   (`ComplicationSlot.render(...)` or equivalent) integrated into that same render loop, using
   the stored `complicationSlotsManager`. Store whatever reference is needed from
   `createWatchFace()`'s parameters to make this possible, following the existing pattern used
   for `renderer` in that file.
4. **Provider selection UI — confirm before assuming.** With the legacy API this required a
   custom picker Activity, which is why it was never finished twice before. With
   `androidx.wear.watchface`, Wear OS is generally expected to provide this natively once real
   slots exist. Confirm this is actually true for this app's target Wear OS versions/manifest
   setup (check `minSdkVersion`/Wear OS version support, and whether the watch face service's
   manifest declaration needs anything specific — e.g. `android.support.wearable.watchface.
   companionConfigurationAction` or the modern equivalent — for the system complication chooser
   to be reachable). If a gap exists, report it back before building a custom picker as a
   fallback — don't silently build one if the native path just needs a small manifest/config
   fix.

## 5. Concrete steps

1. Resolve the 4 open design points above and write up findings (see Deliverables).
2. Implement complication slot declaration per point 4.1, wired through `WatchFace.kt` and
   overridden in `CustomWatchface`.
3. Implement dynamic bounds/visibility syncing with the CWF json per point 4.2, reusing
   `ViewMap`'s existing per-slot `viewJson` for `COMPLICATION1`/`2`/`3` (these `ViewMap` entries
   already exist conceptually in the reference draft — keep that part, drop the
   `ComplicationDrawable`/`onComplicationDataUpdate` parts).
4. Wire rendering per point 4.3.
5. Confirm/implement provider selection per point 4.4.
6. Apply CWF json styling keys (`FONT`/`FONTTITLE`/`FONTCOLOR`/`FONTTITLECOLOR`/`COLOR`) to
   each slot's rendering, via whichever `CanvasComplication` implementation you land on.
7. Verify (and add if missing) in the shared module: `ViewKeys.COMPLICATION1/2/3`,
   `JsonKeys.FONTTITLE`, `JsonKeys.FONTTITLECOLOR`.
8. `defaultWatchface()` in `CustomWatchface.kt` should include the 3 slots'
   position/size/visibility in the default CWF json, like any other view — no provider-related
   data belongs in a CWF zip (that's a per-user, per-slot preference, not shipped config).
9. Test end-to-end on an emulator or device with at least one real system complication provider
   (date, battery, step count, etc.) before worrying about polish or edge cases.
10. If any CWF JSON key's meaning/usage is new or changed, document it wherever the CWF JSON
    format is already documented in this repo. Mark new docs as drafts pending review.

## 6. Deliverables

- A short written plan/summary of findings for the 4 open design points in section 4 **before**
  the bulk of the implementation — I want to sanity-check the design, especially points 1, 2
  and 4, before you build against them.
- Working code for the 3 complication slots on `CustomWatchface`, integrated with the real
  `androidx.wear.watchface` complications framework (no legacy-API leftovers).
- Any doc updates per section 5.10.
- A summary at the end of what was changed and, importantly, what remains fragile or untested
  (e.g. ambient-mode rendering of complications, burn-in protection, behavior across CWF
  switches, behavior on watch face re-selection) — this feature has a history of silent
  failure, so err on the side of over-flagging rather than under-flagging remaining risk.