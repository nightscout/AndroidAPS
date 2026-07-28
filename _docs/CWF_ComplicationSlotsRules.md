# Task-specific rules — Wear CustomWatchface: complication slots

This file supplements the root `CLAUDE.md`. It does not override it — if anything here
conflicts with the root `CLAUDE.md`, the root file wins. This file exists only because the
`wear` module currently has no module-specific `CLAUDE.md` of its own.

## Non-negotiable

- Read the root `CLAUDE.md` in full before touching any code, and follow it strictly for the
  entire duration of this task. Do not ask later whether a rule from `CLAUDE.md` can be
  relaxed, skipped, or worked around for this feature — the answer is no. If a rule appears to
  block a reasonable implementation path, stop and explain the conflict instead of bypassing it.
- Work directly in the current worktree, on branch `wear/CWF_Complication`. Do not create a new
  branch or a new worktree.

## Context for this task

- Nightscout Foundation project (open-source, self-built/self-run by users — not a medical
  device manufacturer). This module is Wear OS code for AndroidAPS's `wear` app.
- We are re-introducing a feature (3 complication slots on the "Custom" watch face) that was
  attempted twice before, on two different **older** architectures, and abandoned both times.
- **Confirmed architecture fact (read `app/aaps/wear/watchfaces/utils/WatchFace.kt`, the base
  class of `BaseWatchFace`, before assuming anything else):** this codebase runs on the modern
  `androidx.wear.watchface` API (`WatchFaceService`, `ComplicationSlotsManager`,
  `Renderer.CanvasRenderer2`), not the legacy `android.support.wearable.watchface`/
  `android.support.wearable.complications` API that both earlier attempts targeted. The tap
  listener already installed in `WatchFace.kt` explicitly ignores taps that land on a
  complication slot with the comment "handled by the system" — confirming complications are
  meant to be declared as real `ComplicationSlot`s and left to the framework for tap handling
  and (very likely) provider selection.
- `WatchFace.kt` currently declares **no** complication slots at all (`createWatchFace()`
  receives a `ComplicationSlotsManager` parameter but never populates or stores it, and
  `createComplicationSlotsManager()` is not overridden). This is the actual gap — there was
  never a broken picker to fix, there were simply no real complication slots declared yet.
- The current layout (`activity_custom.xml`) already has the 3 `FrameLayout` placeholders
  `complication1`/`complication2`/`complication3` at fixed positions, coming from an earlier
  (pre-migration) pass — these positions are still useful as the source of truth for slot
  bounds, but they are plain `View`s with no framework meaning; they don't make the slots exist.

## Do not reuse the legacy-API code as-is

A hand-adapted `CustomWatchface.kt` draft was produced before `WatchFace.kt` was reviewed. It
uses `ComplicationDrawable` (from `android.support.wearable.complications.rendering`),
overrides `onComplicationDataUpdate`/`setActiveComplications`, and does complication hit-testing
manually in `onTapCommand`. **All of that targets the wrong, legacy API and should not be
carried over as-is** — it will not compile against the current `WatchFace`/`BaseWatchFace`, and
even if it did, taps are already meant to be routed by the framework, not hand-tested.

What is still useful from that draft (intent only, not code to copy):

- Which CWF JSON keys should drive complication styling (`FONT`, `FONTTITLE`, `FONTCOLOR`,
  `FONTTITLECOLOR`, `COLOR`) and where they'd be read from (`ViewMap`'s per-slot `viewJson`).
- The idea that slot position/size/visibility should come from the CWF json like any other
  view, via the existing `ViewMap`/`customizeViewCommon` machinery.
- `android.support.wearable.complications.rendering.ComplicationDrawable` may still be a valid
  rendering backend even under the new API — `androidx.wear.watchface.CanvasComplicationDrawable`
  is designed to wrap exactly this class. Verify this against current androidx docs before
  assuming either way.

## Known historical dead end — do not repeat it

The earlier (non-AndroidX) attempt tried to validate complication rendering with a hardcoded
fake `complicationId = 10` and a manually-built `ComplicationData`, bypassing the real
provider-selection flow entirely. This never worked, and under the current architecture the
equivalent mistake would be simulating complication data without ever registering a real
`ComplicationSlot` with the framework. Don't repeat either version of this shortcut.

## Documentation

If new CWF JSON keys are introduced or existing ones gain new meaning for complications, document
them wherever the CWF JSON format is already documented in this repo (check `_docs` and any
in-code KDoc on `JsonKeys`/`ViewKeys`). Mark any new documentation as a draft pending review, per
the Foundation's standing instructions for drafts.

## Data hygiene

No real user health data (CGM traces, logs, screenshots containing patient data) should end up
in code, comments, commit messages, or test fixtures. No hardcoded secrets. This should not come
up in a task this scoped, but flag it immediately if it would.