# Overview Graphs: Dynamic Y-Axis Scaling & Nice Numbers

## Motivation

Overview graphs load 24h of data but typically show a 6h sliding window (zoomable down to
30 minutes, up to 24h). Previously, each graph's Y-axis scale was computed by Vico from the
**entire loaded 24h**, not the currently visible portion. A single spike anywhere in the 24h
(e.g. a large IOB peak) would flatten every calm period visually, even when scrolled directly
onto it.

This change makes every graph's Y-axis recompute from the **currently visible (scrolled/zoomed)
window** instead, and additionally snaps axis bounds/ticks to round ("nice") numbers instead of
raw data-driven decimals.

Scope: `BgGraphCompose.kt`, `SecondaryGraphCompose.kt`, `GraphsSection.kt`, `GraphUtils.kt`.
No public API changes — all new composable parameters default to the previous behavior.

---

## 1. Visible-window axis scaling

### Mechanism

Vico (the charting library) does not expose the visible x-range at the Compose level
(`VicoScrollState`/`VicoZoomState` give scroll pixels and a zoom factor, not a resolved time
window). The only place this is reliably computable is at **draw time**, via
`CartesianDrawingContext` (`layerDimensions.xSpacing`, `ranges.minX`, `ranges.xStep`, `scroll`,
`layerBounds`) — the same context the existing "now" line decoration (`NowLine`) already reads.

- **`VisibleRangeReporter`** (`GraphUtils.kt`): a passive `Decoration` that computes the visible
  x-range on every draw pass and writes it into a plain `@Volatile` holder
  (`VisibleRangeHolder`) — deliberately **not** a Compose `State`. Writing Compose state
  synchronously from the draw phase was found to fight with Vico's own gesture-driven
  scroll/zoom mutations (observed: pinch-zoom became unresponsive — see Bug Fix #1 below).
- Each graph polls this holder from a `LaunchedEffect` (every 50ms), then debounces (30ms)
  before promoting it to real Compose state (`visibleRange`). This keeps all Compose-state
  writes off the draw path.
- The debounced visible range is included in the keys of the `LaunchedEffect` that calls
  `modelProducer.runTransaction`, and is also stashed in the transaction's `extras` (via an
  `ExtraStore.Key`). This is required because `CartesianChartModelProducer.update()` skips
  recomputing axis ranges when a transaction's series data **and** extras are both unchanged —
  scrolling/zooming re-submits identical series data (only the visible window changed), so
  without this the transaction would be silently dropped and the axis would never update.

### Applied to every axis-range case in `SecondaryGraphCompose`

All of the following now compute their min/max from the windowed (visible-only) data via two
shared helpers, `windowedY` and `windowedPrimaryY`, falling back to the full unwindowed data
only when the visible window currently contains no points (e.g. scrolled into a future gap):

- IOB/BAS combo (`primaryYMax`)
- Dual-axis zero-alignment (`dualAxisRanges`)
- Single-axis auto-range (`primaryAutoRange`)
- COB-alone nice scale (`primaryCobScale`)
- Zero-floor series alone: BGI/DEVIATIONS/ACTIVITY/STEPS (`primaryZeroFloorScale`)
- Free-range series alone: VAR_SENSITIVITY/HEART_RATE (`primaryFreeRangeScale`)
- Pivot-centered series alone: SENSITIVITY/DEV_SLOPE (`primaryPivotScale`)
- Basal overlay range (`basalMaxY`)

`BgGraphCompose`'s own axis is windowed the same way, but sourced differently — see
Bug Fix #1.

---

## 2. Nice-number scaling (`GraphUtils.kt`)

Implements Paul Heckbert's "Nice Numbers for Graph Labels" algorithm: axis bounds and tick
spacing are snapped to `1/2/5/10 × 10^n` (a `2.5` tier is added to avoid an overly coarse jump
between the `2` and `5` tiers).

| Function | Used for |
|---|---|
| `niceScale(min, max)` | Free-range scale, no zero anchor (VAR_SENSITIVITY, HEART_RATE) |
| `zeroFloorNiceRange(min, max)` | Mostly-positive series (IOB, BGI, DEVIATIONS, ACTIVITY, STEPS, ABS_IOB) — floors at 0 by default, but disparity-aware: if the negative excursion is small relative to the positive range (ratio ≥ 10×), the negative side gets its own small "nice" sliver instead of forcing one shared (coarser) tick step across the whole range |
| `niceScaleAroundPivot(min, max, pivot)` | SENSITIVITY (pivot=100%) / DEV_SLOPE (pivot=0) — pivot always lands exactly at the axis midpoint. Below a minimum deviation floor (`SENS_MIN_DEVIATION = 5.0`), snaps to a fixed 3-tick scale instead of nice-ifying a near-zero range |
| `niceUp(value)` / `niceNegativeSliver(value)` | Round a single bound up (or further negative) independently — used when the two sides of a range need decoupled scales |

`ClampedVerticalAxisItemPlacer` wraps the standard step-based item placer to filter out
labels/gridlines outside a given `[visibleMin, visibleMax]` — used in two situations:
1. IOB/BAS: the axis extends above the real IOB data to reserve headroom for the basal overlay;
   labels must stop at the real data max, not continue into the reserved band.
2. Dual-axis combos where the zero-floor side's axis is pushed below its own real floor purely
   to align its zero with the other side's pivot — labels must stop at its own real floor
   instead of showing a fake negative tick for a series that can never go negative.

---

## 3. Dual-axis (combined primary + secondary curve) alignment

Vico computes each vertical axis independently, so y=0 on the left axis does not naturally land
at the same pixel row as y=0 on the right axis. `dualAxisRanges` (in `SecondaryGraphCompose`)
computes a shared axis construction depending on which series are combined:

- **Both pivot-centered** (SENS + DEV_SLOPE): existing symmetric-around-pivot alignment,
  adequate since both are already symmetric around their own pivot.
- **One pivot, one zero-floor** (e.g. SENS + COB): the pivot side gets a full nice-scaled
  symmetric range around its own pivot, unconditionally. The zero-floor side gets a symmetric
  `(-half, +half)` container sized from its own zero-floor nice range, so its zero lands at the
  pivot's center row; its own real floor is preserved for tick labels via
  `ClampedVerticalAxisItemPlacer`.
- **Neither pivot-centered** (e.g. COB + VAR_SENSITIVITY, BGI + STEPS): each series' own
  "negative fraction" (how much of its axis height sits below zero) is computed independently —
  primary from its nice bounds, secondary from its raw bounds — and the shared target fraction
  is their **average**, so neither curve's dynamic range dominates the compromise. Each axis is
  then widened just enough to hit that shared fraction, anchored at whichever of its own
  min/max avoids clipping (`fractionAlignedRange` / `fractionAlignedNiceRange`) — this
  construction guarantees neither curve is ever clipped, for any target fraction.

Verified by hand for the disparity case that motivated this design: a mostly-positive series
(e.g. IOB) combined with a series that has a real negative excursion (e.g. BGI) — in both
directions (which one is primary vs. secondary), neither curve loses its real max or min.

---

## Bug Fixes

### 1. BG pinch-zoom became unresponsive / snapped during live gestures

**Root cause:** `CartesianLayerRangeProvider.fixed(...)` returns a new instance every time
bounds change. Recreating it forces `rememberLineCartesianLayer`/`rememberCartesianChart` to
mint a new `CartesianChart.id`, which triggers Vico's internal chart re-registration — this
destabilized BG's live pinch-zoom/scroll gesture handling (BG is the only graph with an
interactive chart; secondary graphs are non-interactive and were never affected by this).

**Fix:** `MutableYRangeProvider` (`BgGraphCompose.kt`) — a `CartesianLayerRangeProvider`
implementation backed by plain `@Volatile var` fields instead of an immutable value object. Its
identity never changes across scroll/zoom; only the field values are mutated in place, read
fresh whenever Vico next processes a `modelProducer.runTransaction`. BG's chart object is never
rebuilt mid-gesture.

A related, smaller instance of the same class of bug: attaching a `VisibleRangeReporter`
decoration directly to BG's own chart (to window its own axis) was also found to disturb its
gesture handling, even though decorations are normally passive draw-time overlays. Fix: BG does
not observe its own visible window at all — it sources it from the fixed IOB graph's own
(already debounced) visible range instead, since IOB's scroll/zoom is synced to BG's anyway
(`GraphsSection.kt`, `iobVisibleRange` → `iobVisibleRangeSettled`, debounced an additional 400ms
specifically before feeding BG — secondary graphs are non-interactive and tolerate the shorter
30ms debounce fine, but updating BG's axis geometry on every ~80ms tick during an active gesture
kept changing its Y-axis label width mid-gesture and broke pinch-zoom).

### 2. BG viewport snapped to the wrong zoom/scroll after an auto-reset

**Root cause:** driving `VicoZoomState.zoom(Zoom)` to reset to a fixed default (6h) is
pinch-gesture-oriented — it applies a ratio anchored on the current canvas center via an async
`pendingScroll` flow — and produced a wrong end state when used to reset to an absolute target
rather than in response to an actual gesture.

**Fix:** `bgViewportResetTrigger` (`GraphsSection.kt`) — bumping this recreates the BG
scroll/zoom state objects from scratch via `key(bgViewportResetTrigger) { ... }`, snapping them
back to their initial values exactly as Vico itself positions them on first composition,
instead of driving the existing objects to a target. Only triggered on real inactivity (a new
BG reading arriving with no recent user interaction), never mid-gesture.

Every effect reading `bgScrollState`/`bgZoomState` had to be re-keyed on those objects (not
`Unit`) so it restarts against the fresh instances after a reset — otherwise it keeps comparing
secondary-graph state against a stale, abandoned reference forever and fires wrong corrections
(this caused a visible "dancing"/flashing regression in the secondary graphs on the first
attempt at this fix).

### 3. COB Y-axis forced into a fake negative range

**Root cause:** COB's Y-axis could get force-symmetrized around zero (e.g. `-42..+42`) whenever
it shared a dual-axis graph with a series that crosses zero, even though COB itself is never
negative — the generic zero-crossing alignment logic (`alignZerosAtOrigin`) was being applied
uniformly regardless of whether a series is actually zero-floor (never negative) vs.
genuinely bipolar.

**Fix:** zero-floor series (COB and the `ZERO_FLOOR_SERIES_TYPES` set: BGI, DEVIATIONS,
ACTIVITY, STEPS, ABS_IOB) now get their own dedicated floor-at-0 axis construction
(`fractionAlignedRange`/`fractionAlignedNiceRange`, see the dual-axis section above) instead of
reusing the generic symmetric-zero-crossing logic — their real floor is preserved and only the
shared zero-alignment fraction stretches the axis, never mirroring a fake negative range that
doesn't exist in the data.

---

## Testing performed

- Manual scroll/zoom testing on device across the full zoom range (30min–24h) on BG and all
  secondary graph types, including dual-axis combinations.
- Verified no data clipping on combined curves for mismatched-disparity cases (mostly-positive
  series + series with a real negative excursion, both orderings).
- Verified pinch-zoom remains responsive throughout a sustained gesture and that BG's axis
  updates ~400ms after gesture end rather than mid-gesture.
- Verified BG viewport reset (new BG reading, no recent interaction) returns to the correct
  default 6h window/scroll position.

## Known limitations / follow-ups

- Behavior of the shared-fraction dual-axis construction has not been exhaustively tested for
  every possible series-type combination — the general algorithm should handle any combination
  by construction (no clipping is a property of `fractionAlignedRange`/
  `fractionAlignedNiceRange`), but only a subset of combinations has been manually verified on
  device.