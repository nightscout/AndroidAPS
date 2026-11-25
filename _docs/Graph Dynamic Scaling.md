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
is the same subject, for the one curve that was missed because it has no axis of its own. It also
removes `ActivityGraphData.maxActivity` from `:core:interfaces` and `:workflow`, which that change
left with no readers.

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
shared helper, `windowedValues` (`SecondaryGraphCompose.kt`), falling back to the full unwindowed data
only when the visible window currently contains no points (e.g. scrolled into a future gap):

- Dual-axis zero-alignment (`dualAxisRanges`)
- Single-axis auto-range (`primaryAutoRange`)
- COB-alone nice scale (`primaryCobScale`)
- Zero-floor series alone: BGI/DEVIATIONS/ACTIVITY/STEPS (`primaryZeroFloorScale`)
- Free-range series alone: VAR_SENSITIVITY/HEART_RATE (`primaryFreeRangeScale`)
- Pivot-centered series alone: SENSITIVITY/DEV_SLOPE (`primaryPivotScale`)

`BgGraphCompose`'s own axis is windowed the same way, but sourced differently — see
Bug Fix #1.

---

## 2. Axis bounds and labels (`GraphUtils.kt`)

The bounds of an axis and its labels are worked out apart from each other.

**Bounds** are the data of the visible window, rounded out to a grid of about a tenth of the
range, as `1/2/5 × 10^n` (`boundGrid`). A bound lies at most a sixth of the range beyond the data,
so the curve fills its graph, and it still moves in steps instead of with every scroll tick.

| Function | Used for |
|---|---|
| `niceScale(min, max)` | Free-range scale, no zero anchor (BG, VAR_SENSITIVITY, HEART_RATE) |
| `zeroFloorNiceRange(min, max)` | Mostly-positive series (IOB, BGI, DEVIATIONS, ACTIVITY, STEPS, ABS_IOB) — floors at 0 by default, but disparity-aware: if the negative excursion is small relative to the positive range (ratio ≥ 10×), each side is rounded on its own grid, so the negative part stays a thin sliver |
| `niceScaleAroundPivot(min, max, pivot)` | SENSITIVITY (pivot=100%) / DEV_SLOPE (pivot=0) — pivot always lands exactly at the axis midpoint. Below a minimum deviation floor (`SENS_MIN_DEVIATION = 5.0`), a fixed pivot ± 5 instead of a range tight around a near-zero deviation |
| `niceUp(value)` | Rounds a single bound up on its own grid — used when the two sides of a range are rounded apart |

**Labels** come from `NiceVerticalAxisItemPlacer`. They are the multiples of the smallest
`1/2/5 × 10^n` step that keeps them `AXIS_LABEL_SPACING` (1.5) label heights apart on the real
pixel height of the axis, so a taller graph gets a finer step (0, 0.5, 1, 1.5 U instead of 0, 1 U).
When that spacing would leave fewer than two labels, they may come closer, down to just touching:
one label alone says nothing about the scale.
Being multiples of the step and not steps counted from a bound, they are round numbers whatever
the bounds are, and 0 is a label whenever it is on the axis (SENS' 100% too). A bound does not
need to carry a label.

Before, one Heckbert "nice numbers" scale did both jobs, so the bounds had to be label values.
A short graph has room for only a few labels — the IOB graph asked for 3 (`IOB_GRAPH_TICK_COUNT`)
to stay clear of Vico's own label thinning — and a bound could then land almost twice as far out
as the data: an IOB of 2.9 U got an axis up to 4 U, and up to 5 U with a hair of negative IOB in
view. The requested tick counts are gone with that.

The placer's `labelMin` / `labelMax` keep labels off a part of the axis the series does not
reach: in dual-axis combos the zero-floor side's axis is pushed below its own real floor purely
to align its zero with the other side's pivot, and labels must stop at its own real floor instead
of showing a fake negative tick for a series that can never go negative.

(There used to be a fixed IOB/BAS graph, with IOB in the lower 75% and basal hanging down from the
top. It is gone: basal is on the BG graph now, see section 5, and IOB is an ordinary series of a
graph below BG.)

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
  pivot's center row; its own real floor is preserved for tick labels via the `labelMin` of
  `NiceVerticalAxisItemPlacer`.
- **Neither pivot-centered** (e.g. COB + VAR_SENSITIVITY, BGI + STEPS): each series' own
  "negative fraction" (how much of its axis height sits below zero) is computed independently —
  primary from its nice bounds, secondary from its raw bounds — and the shared target fraction
  is their **average**, so neither curve's dynamic range dominates the compromise. Each axis is
  then widened just enough to hit that shared fraction, anchored at whichever of its own
  min/max avoids clipping (`fractionAlignedRange` / `fractionAlignedNiceRange`) — this
  construction guarantees neither curve is ever clipped, for any target fraction. Rounding the
  primary's stretched bound moves its zero a little off the shared fraction, so the secondary
  axis, which is not rounded, takes the primary's real zero fraction instead, and both zeros stay
  on one pixel row.

Verified by hand for the disparity case that motivated this design: a mostly-positive series
(e.g. IOB) combined with a series that has a real negative excursion (e.g. BGI) — in both
directions (which one is primary vs. secondary), neither curve loses its real max or min.

---

## 4. The insulin activity overlay

The yellow insulin-activity curve is the one curve that is never the primary series of a graph.
On the BG graph it is drawn on top of BG and its own axis is never shown. Vico only has a start and
an end vertical axis, so the activity curve cannot have a range of its own there: its points must
be converted into the BG axis' units before they reach the model. (On a graph below BG, Activity is
an ordinary series with its own axis. It used to be an overlay of the fixed IOB/BAS graph too; that
graph is gone, and an old config with that overlay on gets a graph of IOB and Activity instead.)

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

A window whose activity is zero everywhere — no insulin on board at all — is caught before that
nice-ification and returns a zero scale, so nothing is drawn. It has to be caught first, because
`zeroFloorNiceRange(0.0, 0.0)` returns `0..1` (`niceScale` widens a zero-width range by 1 so it
always has a usable tick step), which would otherwise give a positive scale and draw a flat line
on the anchor. On the BG graph that is the low mark, where it reads as a second target line. A
window where activity is constant but *not* zero is a real curve and still draws, flat.

**The host scale is never widened to make the activity fit.** The caller passes the room the host
axis already leaves on each side of the anchor, and the curve is fitted inside it. This is a
deliberate constraint: the BG and IOB scales are what the user actually reads, and an overlay with
no axis of its own must not move them.

### The anchor

| | BG graph |
|---|---|
| zero anchor | the **low mark** |
| room above | `bgNice.max - lowMark` |
| room below | `lowMark - bgNice.min` |

On the BG graph the anchor is the low mark rather than the axis floor, because the low mark is the
LOW band: a horizontal reference the eye already uses, with the room
between it and the axis floor left over for the rare negative excursion, which then reads as "below
the green".

### The negative tail

The BG axis starts at 0 (see section 5). The room below the low mark goes down to the top of the
basal, `lowMark × (1 − BASAL_TOP_OF_LOW_MARK)`, so a negative activity tail stops above the basal
instead of being drawn over it. (Before the axis went back to 0, its floor sat on or just
under the low mark in most setups, and the tail was clipped there.)

`ActivityGraphData.maxActivity` (a `max(|activity|)` over the whole loaded day, computed in
`PrepareGraphDataRunner`) was the only input to the old formulas and was removed with them.

---

## 5. Basal and treatments on the BG graph

The BG graph shows what AAPS 3 showed under the curve, so no graph below BG is needed by default:

- **The BG axis starts at 0.** The top is still windowed (`niceScale(0.0, dataMax)`).
- **Basal** is drawn in BG units: 0 U/h at BG 0, and the highest basal of the visible window
  (`basalWindowMax`, `GraphUtils.kt`) at `BASAL_TOP_OF_LOW_MARK` (0.6) of the low mark, so it stays
  under the LOW band. It is the first layer, so a low reading is drawn over it. The step
  series reach the right edge of the axis (`processStepPoints`).
- **Manual boluses and carbs** are markers on the curve (`TreatmentMarkers.kt`). A bolus has its
  amount above it, carbs below. A label that would not fit inside the graph is left out.
- **SMBs** are small triangles standing on the bottom edge, as on the zero line of the old IOB
  graph, sized against the largest SMB, with no label. On the curve, one every few minutes hid the
  readings.
- **COB failover** is a thin bar in the carbs colour along the bottom of the treatment belt, one
  segment per stretch (`cobFailoverStretches`). On the bottom edge of BG it covered the SMBs.

The default BG height is 200 dp (`DEFAULT_BG_GRAPH_HEIGHT_DP`), because the axis from 0 needs the
room. The stored graph config has a version: version 2 moved a BG height still at the old default
of 100 to 200, version 3 turned the fixed IOB graph into the first graph below BG.

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
not observe its own visible window at all — it sources it from the treatment belt's visible range
instead. The belt is always shown and its scroll/zoom is synced to BG's anyway
(`GraphsSection.kt`, `beltVisibleRange` → `beltVisibleRangeSettled`, debounced an additional 400ms
specifically before feeding BG — the other graphs are non-interactive and tolerate the shorter
debounce fine, but updating BG's axis geometry on every ~80ms tick during an active gesture
kept changing its Y-axis label width mid-gesture and broke pinch-zoom). It used to come from the
fixed IOB graph, which is gone; with no graph below BG at all there would have been no source.

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