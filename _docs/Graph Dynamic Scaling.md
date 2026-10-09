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

Section 4 (the insulin activity overlay) came later, on the `Todo/DynamicActivityCurve` branch: it
is the same subject, for the one curve that was missed because it has no axis of its own.

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

## 4. The insulin activity overlay

The yellow insulin-activity curve is the one curve that is never the primary series of a graph.
It is always drawn on top of another series — BG on the main graph, IOB on the IOB/BAS graph —
and its own axis is never shown. Vico only has a start and an end vertical axis, and on both of
those graphs they are already taken, so the activity curve cannot have a range of its own: its
points must be converted into the host axis' units before they reach the model.

That conversion was the one scale left out of the visible-window work above. It used to be:

```kotlin
// BG graph
scaleFactor = (maxBgY - minBgY) * 0.8 / activityData.maxActivity   // all three full-24h
y           = minBgY + value * scaleFactor
// IOB graph
scale = processedIob.max() * 0.8 / activityData.maxActivity        // both full-24h
y     = value * scale
```

Every factor came from the whole loaded 24 h while the host axis was already windowed, so the
curve no longer had any fixed relationship with the frame it was drawn in: scrolled onto a calm
window it shot far above the axis, and with the day's activity peak outside the window it
flattened onto the floor. On the BG graph the anchor was wrong as well — `minBgY` is a full-range
data value, not the axis floor, so zero activity could sit below the visible axis and push the
whole curve out of the frame.

### The rule

`activityOverlayScale` (`GraphUtils.kt`) computes a single linear map from the **visible-window**
activity range. One factor for both sides, so the curve stays linear:

- positive side: the nice-ified activity max plots at `ACTIVITY_HEIGHT_FRACTION` (0.8) of the room
  above the zero anchor, leaving a margin below the top of the graph.
- negative side: the nice-ified activity min plots at most at the room below the anchor. No margin
  — that room is already small and a negative excursion is the rare case.
- the smaller of the two limits wins, so a deep negative dip shrinks the whole curve instead of
  being clipped. A window holding only negative activity is scaled by its negative side alone.

The range is passed through `zeroFloorNiceRange` even though no label is ever drawn from it: the
bound then moves in discrete jumps, so the curve's height does not wobble on every scroll tick.

**The host scale is never widened to make the activity fit.** The caller passes the room the host
axis already leaves on each side of the anchor, and the curve is fitted inside it. This is a
deliberate constraint: the BG and IOB scales are what the user actually reads, and an overlay with
no axis of its own must not move them.

### The two anchors

| | BG graph | IOB/BAS graph |
|---|---|---|
| zero anchor | the **low mark** | IOB zero |
| room above | `bgNice.max - lowMark` | `iobDataMax` (stops below the reserved basal band) |
| room below | `lowMark - bgNice.min` | IOB's own negative sliver, usually none |

On the IOB/BAS graph this is the ordinary vertical zero alignment — activity zero on IOB zero.

On the BG graph the anchor is the low mark rather than the axis floor, because the low mark is the
bottom edge of the green in-range belt: a horizontal reference the eye already uses, with the band
between it and the axis floor left over for the rare negative excursion, which then reads as "below
the green".

### Where the negative tail is clipped

The BG axis floor is `floor(dataMin / step) * step`, and `dataMin` is itself floored at the low
mark, so the floor normally lands below the low mark and leaves 10–25% of the height underneath it.
But when the low mark is an exact multiple of the step and the visible window holds no BG below it,
the floor lands exactly **on** the low mark and there is no room at all:

| lowMark | highMark | BG axis | room below lowMark |
|---|---|---|---|
| 72 (default) | 180 | 50..200 | 22 mg/dL (14.7%) |
| 70 | 180 | 50..200 | 20 (13.3%) |
| 75 | 180 | 50..200 | 25 (16.7%) |
| **80** | **180** | **80..180** | **0** |
| **90** | **140** | **90..140** | **0** |
| **100** | any | **100..** | **0** |
| 3.9 mmol | 10.0 | 2.0..10.0 | 1.9 (23.8%) |
| **4.0 mmol** | **10.0** | **4.0..10.0** | **0** |
| **5.0 mmol** | **9.0** | **5.0..9.0** | **0** |

4.0 mmol/L is a common low mark, so this is not a corner case. In that situation the negative tail
is clipped at the axis floor and the positive side deliberately keeps its full scale — it is not
shrunk to compensate. The alternative would be to move the BG scale, which is exactly what the rule
above forbids.

`ActivityGraphData.maxActivity` (a `max(|activity|)` over the whole loaded day, computed in
`PrepareGraphDataRunner`) was the only input to the old formulas. Nothing reads it any more.

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

For the activity overlay (section 4), so far only unit tests: `GraphUtilsTest`'s
`ActivityOverlayScaleTest` pins each anchor and each room case, plus a shape/room sweep asserting
the curve never leaves the room it was given. Not yet verified on device.

## Known limitations / follow-ups

- Behavior of the shared-fraction dual-axis construction has not been exhaustively tested for
  every possible series-type combination — the general algorithm should handle any combination
  by construction (no clipping is a property of `fractionAlignedRange`/
  `fractionAlignedNiceRange`), but only a subset of combinations has been manually verified on
  device.
- The EPS (profile switch) layer on the BG graph has the same anchoring problem the activity
  overlay just had: it is anchored at `minBgY`, a full-24h data value, while the BG axis floor is
  windowed, so a low-percentage profile switch icon can fall below the visible axis. Not touched
  here.