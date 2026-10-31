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
reach. Two situations:
1. IOB/BAS: the axis goes on above the real IOB data, to leave room above IOB (see below);
   labels must stop at the real data max.
2. Dual-axis combos where the zero-floor side's axis is pushed below its own real floor purely
   to align its zero with the other side's pivot — labels must stop at its own real floor
   instead of showing a fake negative tick for a series that can never go negative.

**IOB/BAS layout.** IOB and the bolus markers fill the lower 75% of the height
(`IOB_HEIGHT_FRACTION`); basal hangs down from the top over 50% (`BASAL_HEIGHT_FRACTION`). The two
overlap between 50% and 75%. They meet there only when a high temp basal runs with a high IOB;
the loop sets a low or zero temp most of the time, and basal then stays at the top. Before, the
two did not overlap, and IOB had only the lower half. The free top quarter holds the label of a
bolus given at the IOB peak.

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

### The two anchors

| | BG graph | IOB/BAS graph |
|---|---|---|
| zero anchor | the **low mark** | IOB zero |
| room above | `bgNice.max - lowMark` | `primaryDataNiceRange.max` (the IOB top, below the free top quarter) |
| room below | `lowMark - bgNice.min` | IOB's own negative sliver, usually none |

On the IOB/BAS graph this is the ordinary vertical zero alignment — activity zero on IOB zero.

The IOB room comes from `primaryDataNiceRange`, the windowed nice range of the primary layer, and
not from the IOB+basal axis itself. That axis only exists when basal is drawn (single-axis IOB),
so reading the room from it would make the overlay disappear with no other symptom if IOB were
ever paired with a second series on its own axis. Today `GraphsSection` always builds the IOB
graph from `listOf(SeriesType.IOB)` alone, so the paired case is unreachable — but it is one
config change away, and a silently missing curve is the wrong failure. In the paired case the real
axis is the zero-aligned dual-axis range, which always contains this nice range (no clipping is a
property of `fractionAlignedNiceRange`), so the curve is sized a little conservatively and still
sits in frame with its zero on the axis' zero.

On the BG graph the anchor is the low mark rather than the axis floor, because the low mark is the
bottom edge of the green in-range belt: a horizontal reference the eye already uses, with the band
between it and the axis floor left over for the rare negative excursion, which then reads as "below
the green".

### Where the negative tail is clipped

The BG axis floor is `dataMin` rounded down to the bound grid (about a tenth of the range), and
`dataMin` is itself floored at the low mark. While the visible window holds no BG below the low
mark, the floor therefore lands on the low mark or just under it, and there is little or no room:

| lowMark | highMark | BG axis | room below lowMark |
|---|---|---|---|
| 72 (default) | 180 | 70..180 | 2 mg/dL (1.8%) |
| **70** | **180** | **70..180** | **0** |
| 75 | 180 | 70..180 | 5 (4.5%) |
| **80** | **180** | **80..180** | **0** |
| **90** | **140** | **90..140** | **0** |
| **100** | any | **100..** | **0** |
| 3.9 mmol | 10.0 | 3.5..10.0 | 0.4 (6.2%) |
| **4.0 mmol** | **10.0** | **4.0..10.0** | **0** |
| **5.0 mmol** | **9.0** | **5.0..9.0** | **0** |

So the negative tail is clipped at the axis floor in most setups, and the positive side
deliberately keeps its full scale — it is not shrunk to compensate. The alternative would be to
move the BG scale, which is exactly what the rule above forbids. (With the coarser rounding the BG
axis used before, most setups left 10–25% of the height under the low mark.)

`ActivityGraphData.maxActivity` (a `max(|activity|)` over the whole loaded day, computed in
`PrepareGraphDataRunner`) was the only input to the old formulas and was removed with them.

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