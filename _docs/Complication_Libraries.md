# androidx.wear.watchface complications — library reference

Factual notes on how the AndroidX watch-face complication libraries actually behave, taken by
reading the real published sources, not the online documentation. Written to be useful with **no
context from any particular feature**: nothing here is specific to AndroidAPS, the Custom watch
face (CWF), or its JSON format. Design decisions and CWF JSON key mappings belong in
`CWF_ComplicationSlotsPrompt.md`, not here.

## Versions checked

| Artifact | Version | Source of truth |
|---|---|---|
| `androidx.wear.watchface:watchface` | **1.2.1** | `watchface-1.2.1-sources.jar` |
| `androidx.wear.watchface:watchface-complications` | **1.3.0** | `watchface-complications-1.3.0-sources.jar` |
| `androidx.wear.watchface:watchface-complications-data` | **1.3.0** | `watchface-complications-data-1.3.0-sources.jar` |
| `androidx.wear.watchface:watchface-complications-rendering` | **1.2.1** | `watchface-complications-rendering-1.2.1-sources.jar` |
| `androidx.wear.watchface:watchface-complications-data-source` | **1.3.0** | `watchface-complications-data-source-1.3.0-sources.jar` |
| `androidx.wear.watchface:watchface-style` | **1.2.1** | `watchface-style-1.2.1-sources.jar` |
| `androidx.wear.watchface:watchface-editor` | **1.2.1** | `watchface-editor-1.2.1-sources.jar` |
| `androidx.wear.watchface:watchface-client` | **1.2.1** | `watchface-client-1.2.1-sources.jar` |

All sources jars are already in the Gradle cache; extract with:

```
unzip -o <sources.jar> -d <dir>
# e.g. ~/.gradle/caches/modules-2/files-2.1/androidx.wear.watchface/
#        watchface-complications-rendering/1.2.1/<hash>/watchface-complications-rendering-1.2.1-sources.jar
```

Note the rendering sources jar contains the **internal** classes too (`ComplicationRenderer.java`,
`TextRenderer.java`, `utils/*LayoutHelper.java`) — no decompiling needed for any of the findings
below.

> **API details move between versions.** Every entry below records the version it was checked
> against. Line numbers are for the sources jar of that exact version.

## How to maintain this file

**Living document, append-and-correct.** Any session that reads these libraries' sources for any
reason — even incidentally, even a single property — adds or corrects an entry here before moving
on. Two rules:

- Every claim carries a `file:line` reference and the version checked. No paraphrasing from memory
  and no claims sourced from a previous conversation's summary.
- If a later check contradicts an earlier entry, **flag the discrepancy and keep both**, tagged
  with their versions. Do not silently overwrite — a difference between versions is itself the
  finding worth recording.

---

## `ComplicationSlot` (watchface 1.2.1, `androidx/wear/watchface/ComplicationSlot.kt`)

### Runtime-mutable vs fixed at creation

The distinction matters because `createComplicationSlotsManager()` runs **once per `Engine`
instance**, so anything fixed at creation stays fixed for the life of that engine.

Declared `public var` but with an **`internal` setter** — readable by app code, *not* writable:

| Property | Line | Notes |
|---|---|---|
| `complicationSlotBounds` | 887–897 | `@UiThread get` / `@UiThread internal set`; setter `require`s `boundsType != BACKGROUND`, and sets `complicationBoundsDirty` (879) |
| `enabled` | 902–911 | getter is `@JvmName("isEnabled")`; sets `enabledDirty` (899) |
| `defaultDataSourcePolicy` | 919–928 | |
| `defaultDataSourceType` | 936–945 | `@Deprecated` in favour of `DefaultComplicationDataSourcePolicy.systemDataSourceFallbackDefaultType` |
| `accessibilityTraversalIndex` | 954–964 | setter `require`s `>= 0` |
| `nameResourceId` | 974–984 | |
| `screenReaderNameResourceId` | 994–1003 | |

The **only writers of these setters anywhere in the library** are in `ComplicationSlotsManager`:

- `ComplicationSlotsManager.kt:155-156` — `init`, restoring each slot's initial config.
- `ComplicationSlotsManager.kt:225-232` — `applyComplicationSlotsStyleCategoryOption`, applying a
  `ComplicationSlotsUserStyleSetting` option's overlays.

So **the one supported way to change a slot's bounds or enabled state at runtime is a user-style
change** driven by a `ComplicationSlotsUserStyleSetting` (see that section below for its
constraint).

Genuinely app-writable at runtime: `configExtras` (412–417) — plain `public set`, and its setter
notifies `complicationSlotsManager.configExtrasChangeCallback`.

Immutable `val`s fixed by the builder: `id` (384), `boundsType` (386), `canvasComplicationFactory`
(388), `supportedTypes` (389), `initiallyEnabled` (392), `fixedComplicationDataSource` (394),
`tapFilter` (395).

### Data

- `complicationData: StateFlow<ComplicationData>` — 1043, public read-only flow. Written only by
  internal `setComplicationData` (1057) / `init` (1280).
- `isActiveAt(instant)` — 1194–1201. Returns false for `NO_DATA`, `NO_PERMISSION`, `EMPTY`, else
  checks `validTimeRange`.

### Builder / factory methods

| Factory | Line | `boundsType` | Tap filter |
|---|---|---|---|
| `createRoundRectComplicationSlotBuilder` | 519–535 | `ROUND_RECT` | **hard-coded** `RoundRectComplicationTapFilter()` (533) |
| `createBackgroundComplicationSlotBuilder` | 556–571 | `BACKGROUND` | hard-coded `BackgroundComplicationTapFilter()` (569); bounds forced to the full unit square (568) |
| `createEdgeComplicationSlotBuilder` | 603–620 | `EDGE` | **caller-supplied** `ComplicationTapFilter` (609) |
| `createEdgeComplicationSlotBuilder` (+`BoundingArc`) | 643–675 | `EDGE` | defaults to a `boundingArc.hitTest` filter (650–664); `@ComplicationExperimental` |

There is **no `Builder.setTapFilter`** — the filter is a private `Builder` constructor parameter
(705) that only these factories supply. Choosing a custom tap filter therefore means choosing
`EDGE`, which costs two things, both documented in the library itself:

- `ComplicationSlot.kt:582-583` — *"Note hit detection in an editor for `ComplicationSlot`s created
  with this method is not supported."*
- `CanvasComplicationDrawable.drawHighlight` only draws for `ROUND_RECT`
  (`CanvasComplicationDrawable.kt:140-143`), so an `EDGE` slot gets no selection highlight unless
  the renderer overrides it (`ComplicationSlot.kt:579-580` says as much).

`Builder.initiallyEnabled` defaults to `true` (710); `Builder.setEnabled` at 779.

### Rendering entry points

```kotlin
// ComplicationSlot.kt:1211-1218
public fun render(canvas: Canvas, zonedDateTime: ZonedDateTime, renderParameters: RenderParameters) {
    val bounds = computeBounds(Rect(0, 0, canvas.width, canvas.height))
    renderer.render(canvas, bounds, zonedDateTime, renderParameters, id)
}
```

- `renderer: CanvasComplication` — **`public val`**, 423–434, created lazily from
  `canvasComplicationFactory`. KDoc: *"can't be used until after `WatchFaceService.createWatchFace`
  has completed"* (420–422).
- `renderHighlightLayer` — 1230–1272. Returns early for `fixedComplicationDataSource` (1237–1239),
  then dispatches on `renderParameters.highlightLayer?.highlightedElement`:
  `AllComplicationSlots` → always draw, `ComplicationSlot` → draw if `id` matches, `UserStyle` /
  `null` → nothing.
- `computeBounds(screen, complicationType, applyMargins)` — 1295–1321,
  `@RestrictTo(LIBRARY_GROUP)`. Converts unit-square bounds to pixels, optionally expanding by the
  per-type margins, then intersects with the unit square. Adds `0.5f` before `toInt()` to round
  rather than truncate (1314–1320).

### Tap filters

- `ComplicationTapFilter.hitTest(slot, screenBounds, x, y, includeMargins)` — 176–182; the
  4-argument overload is `@Deprecated` (193–202).
- `RoundRectComplicationTapFilter` — 208–216. Body is exactly
  `complicationSlot.computeBounds(screenBounds, includeMargins).contains(x, y)`.
- `BackgroundComplicationTapFilter` — 221–229. Always returns `false` (background complications are
  not tappable).

---

### Supported-type negotiation — order matters, but on only one side

Answers a question neither this doc nor the client-side `watchface` sources alone settle: does the
*order* of `supportedTypes` passed to the slot builder affect which `ComplicationType` gets used
when a provider offers several? Checked directly against both sides of the negotiation, current
versions, not inferred from the legacy `android.support.wearable.complications` API generation.

**Watch face side — order is meaningful, first match wins.** `ComplicationSlot.kt:369-373`
(constructor KDoc for `supportedTypes`):

> *"The list of `ComplicationType`s accepted by this complication slot, must be non-empty. During
> complication data source selection, **each item in this list is compared in turn** with entries
> from a data source's supported types. **The first matching entry from `supportedTypes` is
> chosen.** If there are no matches then that data source is not eligible to be selected in this
> slot."*

**Data source side — order is explicitly meaningless.**
`ComplicationDataSourceService.kt:206-208` (watchface-complications-data-source 1.3.0, describing
the provider's own manifest-declared type list):

> *"The order in which types are listed has no significance. In the case where a watch face
> supports multiple types in a single complication slot, **the watch face will determine which
> types it prefers.**"*

**Conclusion:** the two statements describe the same negotiation from each side and agree — our
`supportedTypes` list is read as a **preference order** (most preferred first) and the system
picks the first entry of ours that the chosen provider also supports; the provider's own declared
order carries no weight. This is the *current* androidx Kotlin API's documented behaviour, not a
holdover from the legacy manifest-metadata generation — the two are independently consistent.

---

## `ComplicationDataSourceService` (watchface-complications-data-source 1.3.0, `androidx/wear/watchface/complications/datasource/ComplicationDataSourceService.kt`)

The base class for writing a complication **data source** (provider) — the other side of the
negotiation from everything else in this file, which is all watch-face-side. Documented here
because understanding what a provider receives and must return is necessary to reason about why a
given slot shows what it shows. Not used by this project as a provider implementation; read only to
understand the protocol.

### Manifest-declared type vocabulary

KDoc table, lines 195–204 — the tag names a provider's manifest lists in
`android.support.wearable.complications.SUPPORTED_TYPES` map onto the same `ComplicationType`
values used on the watch-face side, but with **different string tags**, not matching the Kotlin
enum names 1:1:

| Androidx class | Tag name |
|---|---|
| `GoalProgressComplicationData` | `GOAL_PROGRESS` |
| `LongTextComplicationData` | `LONG_TEXT` |
| `MonochromaticImageComplicationData` | `ICON` |
| `PhotoImageComplicationData` | `LARGE_IMAGE` |
| `RangedValueComplicationData` | `RANGED_TEXT` |
| `ShortTextComplicationData` | `SHORT_TEXT` |
| `SmallImageComplicationData` | `SMALL_IMAGE` |
| `WeightedElementsComplicationData` | `WEIGHTED_ELEMENTS` |

Note `MONOCHROMATIC_IMAGE`/`ICON` and `PHOTO_IMAGE`/`LARGE_IMAGE` — the same enum-vs-wire-name
mismatch already recorded for `ComplicationType` in "Other reusable facts" below, now confirmed to
extend to the manifest tag vocabulary too.

### `ComplicationRequest` — the system asks for exactly one type

`ComplicationRequest` (75–137), `@RequiresApi(TIRAMISU)` on its full constructor (76) with a
lower-API 3-arg overload (83–91, defaulting `isForSafeWatchFace`): `complicationInstanceId: Int`
(*"a unique value for the tuple [Watch face ComponentName, complication slot ID]"*, 61–62 —
independent confirmation of the binding key already recorded under `EditorSession`/engine
lifecycle), `complicationType: ComplicationType` — **singular**, not a list —
`immediateResponseRequired: Boolean`, `isForSafeWatchFace`.

`onComplicationRequest(request, listener)` (374–377, `@MainThread abstract`) is the method a
provider implements. Its KDoc (352-364) states the normal deadline is ~20s before the system
unbinds, tightened to <100ms when `immediateResponseRequired` is true (which itself requires a
manifest meta-data key **and** the privileged permission
`com.google.android.wearable.permission.USE_IMMEDIATE_COMPLICATION_UPDATE`). `getPreviewData(type)`
(388-397, abstract) is the editor-preview counterpart — explicitly static per type, not live data
(*"rather than returning the real time it should return a fixed date and time"*).

**This confirms the negotiation is a closed decision, not a live conversation:** the system picks
one `ComplicationType` (per the order-of-preference rule above) and hands the provider exactly that
type in `request.complicationType`; the provider cannot offer a different type back. So a provider
capable of `RANGED_VALUE` but asked for `SHORT_TEXT` (because `SHORT_TEXT` was first in our list)
has no path to send the ranged-value form for that request. Confirms our earlier working
inference — this section makes it a checked fact.

### "Safe watch face" trust gating — a provider may serve *different content* for an identical (provider, type) pair

This is the mechanism behind "two watch faces bind the same provider, negotiate the same
`ComplicationType`, and get different content." It is easy to miss because it is invisible in the
negotiated type — the type is identical; only the payload differs.

**`TargetWatchFaceSafety`** (`ComplicationDataSourceService.kt:148-178`), the values of
`ComplicationRequest.isForSafeWatchFace`:

| Constant | Value | Line |
|---|---|---|
| `UNKNOWN` | **0** | 156 |
| `SAFE` | **1** | 162 |
| `UNSAFE` | **2** | 168 |

The `ComplicationRequest.isForSafeWatchFace` KDoc (66–73, 118–131) states the contract plainly:

> *"Whether this request is on behalf of a 'safe' watch face as defined by the
> `METADATA_KEY_SAFE_WATCH_FACES` meta data in the data source's manifest. **The data source may
> choose to serve different results for a 'safe' watch face.** If the data source does not have the
> privileged permission `com.google.wear.permission.GET_IS_FOR_SAFE_WATCH_FACE`, then this must be
> null."*

Two related manifest keys:

- **`METADATA_KEY_SAFE_WATCH_FACES`** = `android.support.wearable.complications.SAFE_WATCH_FACES`
  (887–888). A provider's own comma-separated allow-list of trusted watch faces — flattened
  `ComponentName`s or bare package names (876–881). KDoc: *"Only trusted watch faces that will set
  this complication data source as a default should be included"* (870–871), and *"if a watch face
  is in the same app package as the complication data source, it does not need to be added to this
  list"* (873–874). Documented to yield `UNKNOWN` when absent (122–123).
- **`METADATA_KEY_SAFE_WATCH_FACE_SUPPORTED_TYPES`** =
  `androidx.wear.watchface.complications.datasource.SAFE_WATCH_FACE_SUPPORTED_TYPES` (818–819).
  Gated behind the same privileged permission; **overrides** `METADATA_KEY_SUPPORTED_TYPES` for safe
  watch faces (809–812). KDoc's own example of intended use: *"trusted watch faces could receive
  `SHORT_TEXT` and untrusted ones `MONOCHROMATIC_IMAGE`"* (814–816).

So there are **two independent gates**, and they compose: the *type list* a watch face may negotiate
from can differ by trust level, **and** the provider can vary the *content* it puts in whichever type
is negotiated, keyed off `request.isForSafeWatchFace`.

#### Reading complication logs on device — a trap worth knowing

**Absence of `onComplicationRequest` / `watchFaceSlotId=N` log lines does NOT mean no provider is
bound to slot N.** Providers emit only when they have a reason to send an update, and
`UPDATE_PERIOD_SECONDS=0` (common — e.g. every Samsung Health provider checked here) means no
periodic polling at all. A step-count provider observed while the wearer is stationary produces
*zero* log lines despite being correctly bound and rendering a live value on screen. A heart-rate
provider on the same device logs roughly once per second, purely because its value keeps changing.

So log silence is evidence about **update activity**, never about **binding**. To establish what is
actually bound to a slot, either create real activity in the underlying data source and re-capture,
or read the binding from a source that enumerates configuration rather than events. This exact
mistake was made during this investigation: a correct "absence of logs isn't proof" caveat was noted
and then, a few steps later, restated as a settled "no provider bound" conclusion — which was wrong.

#### Device-observed on Samsung Wear OS 5 (SM-R890, 2026-08-07)

Recorded because it demonstrates the mechanism in the wild and documents one platform's deviation
from the androidx contract. Two watch faces, same provider
(`com.samsung.android.wear.shealth/...HeartrateComplicationProviderService`), both negotiating
`SHORT_TEXT`, provider's own log line:

```
[onComplicationRequest]48, type:SHORT_TEXT, safe:2     <- sideloaded watch face  -> static label, no live value
[onComplicationRequest]57, type:SHORT_TEXT, safe:1     <- Play-Store watch face  -> live value
```

`safe:2` = `UNSAFE`, `safe:1` = `SAFE`. Identical type, different `isForSafeWatchFace`, different
content — confirming the "may choose to serve different results" clause is exercised in practice for
health data.

That provider's manifest (pulled from the device, `aapt2 dump xmltree`) declares:

- `SUPPORTED_TYPES` = `ICON,SMALL_IMAGE,SHORT_TEXT`
- `SAFE_WATCH_FACE_SUPPORTED_TYPES` = `ICON,SHORT_TEXT,LONG_TEXT`
- `uses-permission com.google.wear.permission.GET_IS_FOR_SAFE_WATCH_FACE` (manifest line 126)
- **no** `SAFE_WATCH_FACES` key anywhere in the manifest (verified by case-insensitive search of the
  full dump)

> ⚠ **Platform deviation from the androidx contract.** The androidx KDoc states
> `isForSafeWatchFace` is `UNKNOWN` when `METADATA_KEY_SAFE_WATCH_FACES` is undefined (122–123). This
> provider defines no such list, yet the system supplies `SAFE`/`UNSAFE` (1/2), never `UNKNOWN`. So
> on this platform the SAFE/UNSAFE decision is made by the **system** (Samsung's WearServices) by its
> own criteria, not from the provider's manifest allow-list. The deciding criteria are **not
> established** — see below for what was ruled in and out.

**Ruled OUT as the deciding factor — the consuming app's own permissions.** Complication data flows
**provider → system → watch face**, so the consumer never needs the source's data permission; its
permission set is structurally irrelevant to what a provider serves it. Confirmed on device, and the
observation inverts the naive expectation: the app receiving *live* health data holds **no** health or
body-sensor permissions at all, while the app receiving the static placeholder holds `BODY_SENSORS`
and `android.permission.health.READ_HEART_RATE`, both granted.

⚠ Do not read that as "holding health permissions is somehow counter-productive," and do not treat
those grants as complication-related at all: in the case measured, the sideloaded app holds them for
an **unrelated feature of its own** — that app independently samples heart rate and step count to
forward to a paired phone, which is why it requests `BODY_SENSORS`/`ACTIVITY_RECOGNITION` at all.
Those permissions were never a candidate mechanism for complication content; they are noise in this
comparison, recorded only so a future reader doesn't re-investigate them.

**Correlates, but not proven causal — install provenance.** `installerPackageName` is `null` for the
sideloaded app (adb install, no `SYSTEM` flag) versus `com.android.vending` for the Play-Store app.
Consistent with a system-side trust heuristic, but correlation on a sample of two; the actual rule
inside WearServices has not been read.

**Ruled OUT as the deciding factor — request cadence / starvation.** Measured with a cleared log
buffer while the sideloaded watch face was active: its complication was re-requested at roughly
**one request per second**, sustained (WearServices' `scheduleImmediateUpdateForAllConfigsWithProvider`
→ `requestUpdateFromProvider` → provider `onComplicationRequest` loop). The `UNSAFE` watch face is
therefore being polled *more* frequently than any freshness argument would require, and still receives
static content on every single one of those requests. Under-requesting is not the mechanism.

**Ruled OUT as the deciding factor — a watch-face-side request/builder flag.**
`ComplicationRequest`'s complete field set is `complicationInstanceId`, `complicationType`,
`immediateResponseRequired`, `isForSafeWatchFace` (constructor, 75–103). Of these only
`isForSafeWatchFace` differed; `complicationType` was identical, `complicationInstanceId` is just the
per-(watch face, slot) key, and `immediateResponseRequired` governs response *latency* (<100ms vs 20s
deadline, 352–364), not payload content. Nothing a watch face passes to
`ComplicationSlot.Builder` — `supportedTypes`, `defaultDataSourcePolicy`, bounds — is transmitted to
the provider at all, so no builder-side option can influence this.

**Net conclusion:** the trust classification is made system-side from the *watch face's package
identity*, and is not reachable or influenceable from watch-face application code. For a sideloaded
watch face, a first-party health provider serving reduced/static content is expected behaviour, not a
defect in the watch face.

#### Can a watch face obtain SAFE status? Documented answer: only the provider can grant it

Searched for any mechanism by which a *watch face* could be classified safe/trusted. Findings:

- **The only documented mechanism is provider-side.** `METADATA_KEY_SAFE_WATCH_FACES` is a list in
  the **data source's own manifest** naming watch face components or packages it trusts
  (`ComplicationDataSourceService.kt:876-888`). Trust is granted *by the provider, to named watch
  faces*. There is no watch-face-side manifest key, permission, or API to declare, request, or
  negotiate it — the watch face is a passive subject of someone else's list.
- **The value is computed entirely off-device-app.** androidx merely reads an int the system passes
  over AIDL: `bundle.getInt(IComplicationProvider.BUNDLE_KEY_IS_SAFE_FOR_WATCHFACE, UNKNOWN)`
  (484–488, and again 682–686). Nothing in the watch face's process participates in deciding it.
- **No signing, certification, Play-distribution or partner program is documented as conferring it.**
  Official docs for complications and for exposing complication data do not mention safe watch faces
  at all; the concept surfaces only in the androidx `wear-watchface` release notes and API reference,
  framed exclusively as an OEM/provider-side capability.
- **Same-package watch faces are automatically trusted:** *"if a watch face is in the same app
  package as the complication data source, it does not need to be added to this list"* (873–874).

**Practical consequences.** (a) For a *third-party* provider, a watch face gains SAFE status only if
that provider's developer adds it to their manifest and ships an update — not achievable unilaterally
by any app-side change. (b) For an app's **own** complication data sources, the same-package rule
means its own watch faces are automatically trusted, so this gate never applies to first-party
complications.

⚠ Note the observed platform behaviour still isn't explained by the documented mechanism: the
provider measured here declares **no** `SAFE_WATCH_FACES` list, so by the documented contract every
caller should see `UNKNOWN`, yet the system supplies `SAFE`/`UNSAFE`. The install-provenance
correlation (`installerPackageName` null vs `com.android.vending`) remains the closest thing to an
observed rule, but it is an **unexplained correlation on a sample of two**, not a documented or
verified mechanism. The deciding logic lives in the vendor's closed WearServices implementation.

#### The negotiated type is persisted per (watch face, slot) and does NOT re-negotiate

⚠ **Refines an earlier reading in this file.** The steps observation was first noted here as "richer
types are available to a watch face the system trusts", implying the trust gate governed it. Further
measurement shows that was wrong for steps — trust is *not* the mechanism there. Both readings are of
the same device/versions; the earlier sentence is superseded.

Measured on the same device, same Samsung Health suite, steps provider
(`...complications.steps.StepsComplicationProviderService`):

| Watch face | Bound type | Content served |
|---|---|---|
| Play-Store (`SAFE`) | `type=5` → `RANGED_VALUE` | live value + goal → ring rendering |
| sideloaded (`UNSAFE`) | `type=3` → `SHORT_TEXT` | **live, updating value + goal** → plain text |

The sideloaded watch face receives **fully live data** here — the step count was observed changing
across successive requests during real walking, and the provider's own log line carries both current
value and goal even when answering as `SHORT_TEXT`. **There is no content reduction for this
provider.** Its manifest confirms why: it declares

```
SUPPORTED_TYPES                 = ICON,SHORT_TEXT,LONG_TEXT,RANGED_VALUE,SMALL_IMAGE,GOAL_PROGRESS
SAFE_WATCH_FACE_SUPPORTED_TYPES = ICON,SHORT_TEXT,LONG_TEXT,RANGED_VALUE,SMALL_IMAGE,GOAL_PROGRESS
```

— **identical lists for both trust levels**, so `isForSafeWatchFace` cannot change what types this
provider offers. Contrast the heart-rate provider in the same app, whose two lists *differ* and whose
content *is* trust-reduced. **Trust gating is per-provider, and per-aspect (type list vs payload
content); do not generalise one provider's behaviour to another, or from type list to content.**

So the type difference must have been decided at binding time. The binding — including its negotiated
`type=` — is stored by the system per `(watch face component, slot id)` and reported back verbatim in
every subsequent `ComplicationConfig{...}` log line. Device evidence that it is **not** re-negotiated:
after the watch face's `supportedTypes` list was reordered to put data-bearing types first, rebuilt
and reinstalled, the bound type stayed `SHORT_TEXT` across (a) a fresh install, (b) a CWF reload, and
(c) a full watch-face switch away and back — the confirmed engine-recreation trigger, which does re-run
`createComplicationSlotsManager()` with the new list.

#### PROVEN: an explicit re-pick is what forces re-negotiation

The prediction above was stated in advance and then tested on device. Both halves held:

**Steps slot, re-picked** — bound type changed `type=3` → **`type=13`**, and the provider switched to
answering `dataType : GOAL_PROGRESS`. Exactly the first-match result predicted from
`GOAL_PROGRESS, WEIGHTED_ELEMENTS, RANGED_VALUE, …` ∩ provider's list — **not** `RANGED_VALUE` (which
would have indicated a different rule) and not the previously stuck `SHORT_TEXT`. Wire type `13`
corresponds to `GOAL_PROGRESS`, observed by the two appearing together in the same request.

**Heart-rate slot, re-picked** — bound type stayed `type=3` / `SHORT_TEXT` and the payload stayed
static, `safe:2`. This is the correct falsification control: that provider's manifest offers no
data-bearing type at all, so a re-pick *should* change nothing, and it didn't.

Together these establish: the negotiated type is fixed at pick time, persists indefinitely across
reinstall / CWF reload / engine recreation, and is re-derived **only** by re-picking the data source
through the chooser. A `supportedTypes` change therefore reaches existing installations only when the
user re-picks — it is not retroactive.

**Practical takeaway:** changing a watch face's `supportedTypes` has **no effect on already-bound
slots**. Existing users would keep whatever type was negotiated when they first chose the provider.
Any change to that list needs a re-pick story, not just a code change.

(Health values are deliberately omitted throughout: they are the device owner's data.)

**Practical consequence for any watch face:** a sideloaded/self-signed watch face may be
structurally unable to obtain live health-metric content from a first-party health provider, no
matter what it declares in `supportedTypes` — because the gate is trust-based and applied
provider-side, not negotiation-based. Verify `isForSafeWatchFace` in the provider's logs before
concluding a static/placeholder payload is a bug in the watch face.

### Other files in this artifact — not yet read in depth

`ComplicationDataSourceUpdateRequester.kt` — already covered under "Other reusable facts" below
(refreshes an already-bound provider's data; does not bind). `ComplicationDataTimeline.kt` and
`WearSdkComplicationsApi.kt` exist in this artifact but have not been examined; noted here so a
future session knows they're unread rather than assuming this file's silence means "nothing there."

---

## `ComplicationSlotBounds` (watchface-complications 1.3.0, `androidx/wear/watchface/complications/ComplicationSlotBounds.kt`)

- Primary constructor takes **two** maps: `perComplicationTypeBounds` (53) and
  `perComplicationTypeMargins` (54), both keyed by `ComplicationType`.
- Convenience constructor: `@JvmOverloads constructor(bounds: RectF, margins: RectF = RectF())`
  (100–107) — applies the same rect to every `ComplicationType`.
- **Margins are a tap-area concept only.** KDoc, line 50: *"complication margins for tap detection
  (doesn't affect rendering)."* This is the supported way to make a slot's tap target larger than
  its drawn area.
- `init` validates that both maps contain an entry for every `ComplicationType` (109–119). Bounds
  are fractional/unit-square, canvas-relative — there is no validation forbidding an empty rect.

---

## `ComplicationSlotsManager` (watchface 1.2.1, `androidx/wear/watchface/ComplicationSlotsManager.kt`)

- `complicationSlots: Map<Int, ComplicationSlot>` — 95, public.
- `init` — 197; restores each slot's `InitialComplicationConfig` (104) then applies the current
  style option (173).
- `applyComplicationSlotsStyleCategoryOption(styleOption)` — 225–232; for each slot, bounds and
  `enabled` become `override?.… ?: initialConfig.…`, i.e. an option that omits a slot resets it to
  its creation-time value.

### Tap routing — reads cached bounds only

```kotlin
// ComplicationSlotsManager.kt:409-429
public fun getComplicationSlotAt(@Px x: Int, @Px y: Int): ComplicationSlot? =
    findLowestIdMatchingComplicationOrNull { it.enabled && it.tapFilter.hitTest(…, includeMargins = false) }
        ?: findLowestIdMatchingComplicationOrNull { it.enabled && it.tapFilter.hitTest(…, includeMargins = true) }
```

Two passes: without margins first (KDoc, 402–403: *"should be no overlaps"*), then with margins
(*"overlaps are possible"*), lowest slot id winning (435–443).

**Consequence worth knowing:** the chain `getComplicationSlotAt` → `tapFilter.hitTest` →
`RoundRectComplicationTapFilter` → `computeBounds` reads the slot's **cached
`complicationSlotBounds`**. It never sees bounds passed to a render call. So a watch face that
paints a complication somewhere other than its declared bounds (see the per-invocation render path
below) still has it *tapped* at the declared bounds, and none of that chain is overridable for
`ROUND_RECT`.

- `onComplicationSlotSingleTapped(id)` — 466–493. If the data type is `NO_PERMISSION` it launches
  `ComplicationHelperActivity.createPermissionRequestHelperIntent` (470–481); otherwise it does
  `data.tapAction?.send()` inside a `try/catch (CanceledException)` (484–490), then notifies
  `complicationListeners`. The data it reads is `slot.renderer.getData()` (468), not the slot's
  `complicationData` flow.
- `onComplicationsUpdated()` — 246–299. Builds `activeKeys` from **only the slots where
  `complication.enabled` is true** (261–262) and, when `enabledDirty`, calls
  `watchFaceHostApi.setActiveComplicationSlots(activeKeys)` (297–299).
  **A disabled slot therefore receives no complication data from the system at all** — relevant
  because `enabled` cannot be raised later from app code.
- `getComplicationsState(screenBounds)` — 501–522; reports each slot's `computeBounds` with and
  without margins (509–510) plus its `enabled` flag (522) — this is what an editor sees.

---

## `CanvasComplication` (watchface 1.2.1, `androidx/wear/watchface/ComplicationSlot.kt:67-160`)

The rendering interface. **Bounds are a per-call parameter, not state:**

```kotlin
// ComplicationSlot.kt:95-102
@UiThread
public fun render(
    canvas: Canvas,
    bounds: Rect,
    zonedDateTime: ZonedDateTime,
    renderParameters: RenderParameters,
    slotId: Int
)
```

Its own KDoc (83–87) is explicit that the caller chooses the geometry: *"Draws the complication …
into the canvas **with the specified bounds** … The width and height will be the same as that
computed by computeBounds but the translation and canvas size may differ."*

Other members: `onRendererCreated(renderer)` `@WorkerThread`, default no-op (81); `drawHighlight`
(116–122, plus an `@ComplicationExperimental` `BoundingArc` overload at 136–145 that delegates to
it); `getData()` (148); `loadData(complicationData, loadDrawablesAsynchronous)` (159).

**`ComplicationSlot.render()` is only a thin wrapper** that fills `bounds` in from the slot's own
cached value (1216–1217, quoted above). Calling `slot.renderer.render(canvas, myBounds, …)`
directly is the intended way to render at caller-chosen geometry, not a workaround — the library
does exactly that in its own editor-preview path: `WatchFace.kt:1207` `renderComplicationToBitmap`
calls `slot.renderer.render(...)` at 1240 and 1262.

### Where the framework dispatches taps

`WatchFace.kt:1127-1157` (`WatchFaceImpl.onTapCommand`, internal):

1. `tappedComplication = complicationSlotsManager.getComplicationSlotAt(x, y)` (1128–1129).
2. `tapListener?.onTapEvent(tapType, tapEvent, tappedComplication)` (1130) — the watch face's
   listener is informed **but cannot veto**; the framework proceeds regardless.
3. On `TapType.UP`, `complicationSlotsManager.onComplicationSlotSingleTapped(id)` (1147); on
   `TapType.DOWN`, `onTapDown` (1152).

A `TapListener` is installed via `WatchFace.setTapListener` (514) and stored at 121.

---

## `CanvasComplicationDrawable` (rendering 1.2.1, `androidx/wear/watchface/complications/rendering/CanvasComplicationDrawable.kt`)

The `CanvasComplication` implementation that draws via `ComplicationDrawable`.

- `render` — 109–131. Returns immediately unless
  `renderParameters.watchFaceLayers.contains(WatchFaceLayer.COMPLICATIONS)` (116–118), then sets
  `drawable.isInAmbientMode`, **`drawable.bounds = bounds`** (121), `drawable.currentTime`,
  `drawable.isHighlighted`, and calls `drawable.draw(canvas)`. Bounds are re-applied on **every**
  invocation, so there is no cached-geometry state to fight.
- `isHighlighted` is derived from `renderParameters.lastComplicationTapDownEvents[slotId]` against
  `COMPLICATION_HIGHLIGHT_DURATION_MS = 300L` (58, 123–129).
- `drawHighlight` — 133–143. Draws **only** when `boundsType == ROUND_RECT` (140), via
  `ComplicationHighlightRenderer` built with `EXPANSION_DP = 6.0f` and `STROKE_WIDTH_DP = 3.0f`
  (60–61, 64–77).
- `drawable` is a `public var` (95–107); assigning a new one re-points the `Drawable.Callback`,
  copies the old `complicationData` across (*"otherwise the complication will be blank until the
  next update"*, 101–103) and re-applies the low-bit/burn-in flags.
- `loadData` — 162–169, `@CallSuper`; stores `_data` and calls
  `drawable.setComplicationData(complicationData, loadDrawablesAsynchronous)`.

---

## `ComplicationStyle` (rendering 1.2.1, `androidx/wear/watchface/complications/rendering/ComplicationStyle.kt`)

Flat property bag. Every setter sets an internal `isDirty` flag (105–107) which
`ComplicationDrawable.updateStyleIfRequired` (696–703) uses to push changes into the renderer.
Full property surface, re-derived property by property:

| Property | Line | Default | Default source |
|---|---|---|---|
| `backgroundColor: Int` | 38 | `Color.BLACK` — **opaque** | `BACKGROUND_COLOR_DEFAULT`, 319 |
| `backgroundDrawable: Drawable?` | 45 | `null` | inline |
| `textColor: Int` | 52 | `Color.WHITE` | `PRIMARY_COLOR_DEFAULT`, 313 |
| `titleColor: Int` | 63 | `Color.LTGRAY` | `SECONDARY_COLOR_DEFAULT`, 316 |
| `textTypeface: Typeface` | 70 | `Typeface.create("sans-serif-condensed", NORMAL)` | `TYPEFACE_DEFAULT`, 331 |
| `titleTypeface: Typeface` | 74 | same as above | `TYPEFACE_DEFAULT`, 331 |
| `textSize: Int` `@Px` | 159 (field 77) | `Int.MAX_VALUE` | `TEXT_SIZE_DEFAULT`, 328 |
| `titleSize: Int` `@Px` | 167 (field 79) | `Int.MAX_VALUE` | `TEXT_SIZE_DEFAULT`, 328 |
| `imageColorFilter: ColorFilter?` | 143 (field 81) | `null` | inline |
| `iconColor: Int` | 151 (field 83) | `Color.WHITE` | `PRIMARY_COLOR_DEFAULT`, 313 |
| `borderColor: Int` | 175 (field 85) | `Color.WHITE` | `BORDER_COLOR_DEFAULT`, 325 |
| `borderStyle: Int` | 183 (field 87) | **`BORDER_STYLE_SOLID`** | inline at 87 |
| `borderDashWidth: Int` `@Px` | 196 (field 89) | `3` | `DASH_WIDTH_DEFAULT`, 334 |
| `borderDashGap: Int` `@Px` | 204 (field 91) | `3` | `DASH_GAP_DEFAULT`, 337 |
| `borderRadius: Int` `@Px` | 217 (field 93) | **`Int.MAX_VALUE`** | `BORDER_RADIUS_DEFAULT`, 346 (public) |
| `borderWidth: Int` `@Px` | 225 (field 95) | `1` | `BORDER_WIDTH_DEFAULT`, 340 |
| `rangedValueRingWidth: Int` `@Px` | 233 (field 97) | `2` | `RING_WIDTH_DEFAULT`, 343 |
| `rangedValuePrimaryColor: Int` | 241 (field 99) | `Color.WHITE` | `PRIMARY_COLOR_DEFAULT`, 313 |
| `rangedValueSecondaryColor: Int` | 249 (field 101) | `Color.LTGRAY` | `SECONDARY_COLOR_DEFAULT`, 316 |
| `highlightColor: Int` | 257 (field 103) | `Color.LTGRAY` | `HIGHLIGHT_COLOR_DEFAULT`, 322 |

That is the complete `public var` surface — 20 properties, verified by scanning every member
declaration in the file, not just the ones grepped for by name.

Also: `setTextTypeface(Typeface)` (269) and `setTitleTypeface(Typeface)` (279) — plain setter
aliases for the corresponding `var`s. `borderStyle`'s setter sanitises anything that isn't
`SOLID`/`DASHED` to `BORDER_STYLE_NONE` (186–194). Constants: `BORDER_STYLE_NONE = 0` (300),
`BORDER_STYLE_SOLID = 1` (303), `BORDER_STYLE_DASHED = 2` (310). Copy constructor at 111–133.

Not app-callable: `asTinted(tintColor)` (283–296) is `@RestrictTo(RestrictTo.Scope.LIBRARY)`. It
returns a copy with `backgroundColor`, `borderColor`, `highlightColor`, `iconColor`, both
ranged-value colours, `textColor` and `titleColor` all passed through the internal `tint()` helper
(349) — i.e. the library's own low-bit/tinted-ambient transformation. Listed here only so a future
reader doesn't mistake it for available API.

> ### ⚠ The table above lists FIELD INITIALIZERS, which are NOT the effective defaults
>
> **`ComplicationDrawable.setContext()` overwrites almost all of them from resources**
> (`ComplicationDrawable.kt:288-292` → `setStyleToDefaultValues(style, resources)`, defined at
> 745–778). Since `ComplicationDrawable(context)` calls `setContext`, **any drawable constructed with
> a context uses the resource values, not the initializers above.** Only a drawable built with the
> no-arg constructor and never given a context keeps them.
>
> Resource values, read from `watchface-complications-rendering-1.2.1.aar` (`res/values/values.xml`):
>
> | Property | Field initializer | **Effective default (resource)** |
> |---|---|---|
> | `backgroundColor` | `Color.BLACK` (opaque) | **`#00000000` — fully transparent** |
> | `textColor` | `Color.WHITE` | `#FFFFFFFF` |
> | `titleColor` | `Color.LTGRAY` | `#80FFFFFF` (50 % white) |
> | `iconColor` | `Color.WHITE` | `#FFFFFFFF` |
> | `borderColor` | `Color.WHITE` | **`#80000000` (50 % black)** |
> | `borderStyle` | `BORDER_STYLE_SOLID` | `1` = `BORDER_STYLE_SOLID` (same) |
> | `borderWidth` | `1` (px) | `1dp` |
> | `borderRadius` | `Int.MAX_VALUE` | `65536dp` (same practical effect) |
> | `borderDashWidth` / `borderDashGap` | `3` / `3` (px) | `1dp` / `1dp` |
> | `rangedValueRingWidth` | `2` (px) | **`2dp`** — density-scaled |
> | `rangedValuePrimaryColor` | `Color.WHITE` | `#FFFFFFFF` |
> | `rangedValueSecondaryColor` | `Color.LTGRAY` | `#80FFFFFF` |
> | `highlightColor` | `Color.LTGRAY` | `#4DFFFFFF` |
> | `textSize` / `titleSize` | `Int.MAX_VALUE` | **`14sp` / `14sp`** |
>
> Three of these materially change earlier conclusions in this file:
> **(a) the background default is transparent, not opaque black**, for any context-attached drawable;
> **(b) `rangedValueRingWidth` is `2dp`, not 2px** — on a 2.125-density watch that is ~4.25px, over
> twice what the initializer suggests; **(c) `textSize` defaults to a real `14sp`**, so the
> "`Int.MAX_VALUE` means auto-fit-to-box" reading below applies only to context-less drawables — with
> a context the cap is 14sp, then shrunk to fit by `TextRenderer`.
>
> `highlightDuration` and `noDataText` are set from resources on the same path (294–304), which is why
> the `highlightDuration = 0` initializer never takes effect in practice.

**Defaults that surprise people:** a 1 px (1dp) solid border is drawn unless `borderStyle` is set to
`NONE` — it is merely 50 % black rather than white, so it reads as a subtle dark edge. The
frequently-cited "opaque black background" is only the *initializer*; context-attached drawables
default to transparent (see the box above). Nothing here is inherited from the watch face.

**`textSize` / `titleSize` are upper bounds, not exact sizes.** `ComplicationRenderer` feeds them
straight to `Paint.setTextSize` (1342, 1356), but `TextRenderer` then clamps to
`min(height / maxLines, paint.textSize)` and shrinks in `TEXT_SIZE_STEP_SIZE` decrements until the
text fits (`TextRenderer.java:365-380`; class KDoc at 53: *"shrinking the text size if necessary to
make the characters fit"*). The `Int.MAX_VALUE` default is therefore "as large as the box allows".

---

## `ComplicationDrawable` (rendering 1.2.1, `androidx/wear/watchface/complications/rendering/ComplicationDrawable.kt`)

Extends `android.graphics.drawable.Drawable`, so `bounds` comes from `Drawable`.

| Member | Line | Default / notes |
|---|---|---|
| `activeStyle: ComplicationStyle` | 188 | `val` — **two independent style slots**, one for interactive, one for ambient |
| `ambientStyle: ComplicationStyle` | 191 | `val` |
| `context: Context?` | 178 | `null`; most operations throw without it — `assertInitialized()` (712–717) |
| `currentTime: Instant` | 204 | `Instant.EPOCH` |
| `isInAmbientMode: Boolean` | 207 | `false` — selects which style slot is used |
| `isLowBitAmbient: Boolean` | 213 | `false` |
| `isBurnInProtectionOn: Boolean` | 219 | `false` |
| `isHighlighted: Boolean` | 228 | `false` |
| `isRangedValueProgressHidden: Boolean` | 559 | `false`; suppresses the ring for `RANGED_VALUE` data |
| `complicationData: ComplicationData` | 605 | `NoDataComplicationData()` |
| `highlightDuration: Long` | 689 | field initialiser is `0`, but `setContext` overwrites it from the `complicationDrawable_highlightDurationMs` resource (294–296), so the effective default is the documented 300 ms. `0` disables highlighting (676). **Field initialiser and KDoc disagree — the resource wins whenever a context is attached.** |
| `noDataText: CharSequence?` | 719 | `null` → renders empty text; setter copies the sequence |
| `setComplicationData(data, loadDrawablesAsync)` | 572 | see below |
| `onTap(x, y)` | 629 | |
| `setContext(context)` | 284 | |
| `getDrawable(context, id)` | 737 | companion; inflates from XML |

Constructors: no-arg (234, creates two fresh styles), `(Context)` (245), and a copy constructor
(249) that deep-copies both styles and most flags but forces `isHighlighted = false`.

### Inherited `Drawable` members it overrides — two of them throw

Easy to miss when treating this as a style bag; found by scanning every declaration in the file.

| Override | Line | Behaviour |
|---|---|---|
| `draw(canvas)` | 511–522 | `assertInitialized()`, then `updateStyleIfRequired()`, then delegates to the internal `ComplicationRenderer` with `currentTime` / `isInAmbientMode` / `isLowBitAmbient` / `isBurnInProtectionOn` / `isHighlighted` |
| `setAlpha(alpha)` | 529–531 | **throws `UnsupportedOperationException`** — *"setAlpha is not supported in ComplicationDrawable"* |
| `setColorFilter(colorFilter)` | 539–543 | **throws `UnsupportedOperationException`**; KDoc directs you to `ComplicationStyle.imageColorFilter` instead (534–537). This is why that style property exists |
| `getOpacity()` | 545–546 | returns `PixelFormat.OPAQUE`, but is `@Deprecated("This method is no longer used in graphics optimizations")` — so it does *not* mean a transparent `backgroundColor` fails to render |
| `onBoundsChange(bounds)` | 548–550 | `protected`; forwards the new bounds to the internal renderer |
| `inflate(...)` | 466 | XML inflation path; `inflateAttributes` (310) / `inflateStyle` (317) read `R.styleable.ComplicationDrawable` |

`bounds` itself is plain inherited `Drawable` state, which is what `CanvasComplicationDrawable.render`
assigns on every call.

### `setComplicationData`'s async branch replaces the renderer object

`ComplicationDrawable.kt:572-599`. This is a real trap:

```kotlin
if (loadDrawablesAsync) {
    // Calling nextRenderer.setComplicationData() causes it to render as blank until the
    // async load has completed. To mask this we delay applying the update until the load
    // has completed.
    val nextRenderer = ComplicationRenderer(this.context, activeStyle, ambientStyle)
    …
    nextRenderer.setOnInvalidateListener { complicationRenderer = nextRenderer; … }
    nextRenderer.setComplicationData(complicationData.asWireComplicationData(), true)
} else {
    complicationRenderer?.setComplicationData(complicationData.asWireComplicationData(), false)
}
```

The public `complicationData` field is updated immediately (585) but on the async path a **second**
`ComplicationRenderer` is built and only swapped into `complicationRenderer` from the
`setOnInvalidateListener` callback (591–596). Until that callback fires, the renderer actually
being drawn still holds its previous (possibly empty) data — so every externally observable signal
can report correct data while nothing is painted. Passing `loadDrawablesAsync = false` puts the
data into the live renderer synchronously.

---

### A `MonochromaticImage` is ALWAYS flattened to a single colour — there is no "untinted" option

`PaintSet` builds the icon filter unconditionally from `style.iconColor`
(`ComplicationRenderer.java:1347-1350`):

```java
mIconColorFilter = antiAlias
        ? new PorterDuffColorFilter(style.getIconColor(), PorterDuff.Mode.SRC_IN)
        : new ColorMatrixColorFilter(createSingleColorMatrix(style.getIconColor()));
```

and `drawIcon` (885–903) always applies it: `icon.setColorFilter(paintSet.mIconColorFilter)`. There is
no null/pass-through branch. `SRC_IN` keeps only the source alpha and replaces every colour channel,
so a monochromatic image is reduced to exactly one colour whatever the asset contained.

**Consequence:** *not* setting `iconColor` does not yield an untinted icon — it yields the default
tint (`#FFFFFFFF` white, per the resource table above). Choosing the colour is the only control
available; "leave the provider's colours alone" is not achievable for this field.

**Small images are different**, and are the only route to a multi-colour icon: `drawSmallImage`
(905–939) applies a colour filter only for `IMAGE_STYLE_PHOTO`, and explicitly clears it for
`IMAGE_STYLE_ICON` — *"Don't apply radius or color filter on icon style images"* (929–932):

```java
if (mComplicationData.getSmallImageStyle() == ComplicationData.IMAGE_STYLE_ICON) {
    mRoundedSmallImage.setColorFilter(null);
    mRoundedSmallImage.setRadius(0);
}
```

**Therefore `iconColor` is unreachable whenever the provider sends both images.** Every layout helper
prefers the small image: `ShortTextLayoutHelper.getIconBounds` (45–48) empties the icon rect on
`!hasIcon() || hasSmallImage()`, so `drawIcon` returns before its filter is ever applied, and the
small image it draws instead has that filter explicitly cleared for `IMAGE_STYLE_ICON`. Device-
confirmed with Samsung's heart rate provider on `SHORT_TEXT`: the heart stays red for every value of
`iconColor`, including transparent. This is the same `hasSmallImage()` preference behind the
both-images bug below, seen from a different angle — there it costs the image entirely, here it costs
only the ability to tint it.

**The only lever is the payload.** Dropping `smallImage` makes `hasSmallImage()` false, the icon rect
resolves normally and the library tints it with `style.iconColor`. Worth doing only when a colour was
actually requested: leaving the payload alone is what preserves the provider's own colours, which is
the better default and what the library does unaided.

**Say this without a shape caveat.** An earlier revision of this section scoped the consequence to
"non-wide" slots. That was wrong — the scoping belonged to the *small image* discussion below, not to
this one. Re-verified against 1.2.1 source: `draw()` calls `drawIcon` at **line 450**, a single call
site in the unconditional content block, before any per-type dispatch; `drawIcon` (885–903) branches
only on empty bounds and placeholder-or-not, and its `setColorFilter` call has **no null branch**;
all four `PaintSet`s (active, ambient, and the two `LostTapAction` variants) build `mIconColorFilter`
from `style.iconColor` in the same constructor. Nothing in that chain reads bounds, aspect ratio or
slot shape, and `mIcon` is only ever `mComplicationData.getIcon()` (1262, and 1153 on the async
branch) — it never receives the small image. **On any bounds shape and any complication type, a
`MonochromaticImage` renders in exactly one colour.**

⚠ This interacts with the both-images layout bug documented above, but the escape route is *not* the
one previously written here. On a **square ranged-value slot that also has short text**, dropping the
small image forces the monochromatic one — **a single-tint icon, forfeiting the provider's colours**.
Dropping the monochromatic image instead does nothing: `getSmallImageBounds` still delegates to
`ShortTextLayoutHelper.getIconBounds`, whose `hasSmallImage()` test short-circuits *before*
`hasIcon()` is consulted, so it returns empty either way.

But the delegation is gated on `hasShortText() && !isWideRectangle()` — **both** conditions
(`RangedValueLayoutHelper` 114 and 131). `!hasShortText()` alone avoids it, *including on a square
slot*, routing to `scaledAroundCenter(outRect, mRangedValueInnerSquare, 1 - 2 × ICON_PADDING_FRACTION)`
— a genuinely non-empty rect (`mRangedValueInnerSquare` is computed unconditionally at 66–73). All four
predicates are plain null checks on the wire data (`LayoutHelper` 200–230), so they respond to whatever
the watch face hands the drawable. Reachable outcomes on a square ranged-value slot:

| payload handed to the drawable | ring | value text | image |
|---|---|---|---|
| small image stripped | ✅ | ✅ | mono icon, forced single tint |
| untouched (both images) | ✅ | ✅ | **none** — the self-cancelling bug |
| short text (and title) stripped | ✅ | ❌ | small image, **provider's own colours** |
| *(alternative: slot made wider than 2:1)* | ✅ | ✅ | small image, provider's own colours |

*Device-confirmed (Samsung Wear OS 5, SM-R890):* the Samsung Health steps provider sends
`smallImage=SmallImage(image=Icon(typ=RESOURCE …), type=ICON, ambientImage=null)` — i.e.
`IMAGE_STYLE_ICON`, the style `drawSmallImage` renders **without** a colour filter. So the provider's
image genuinely would appear in its own colours; the reason it does not is purely that its bounds
resolve empty. The tradeoff for a square slot is therefore **single-tint icon vs. no image at all**,
never "single tint vs. correct colour".

## `ComplicationRenderer` (rendering 1.2.1, `androidx/wear/watchface/complications/rendering/ComplicationRenderer.java`) — internal

### Content is inset from the declared bounds, proportional to `borderRadius`

This is why complication content can render far smaller than the bounds given to it.

```java
// ComplicationRenderer.java:972-979
private int getBorderRadius(ComplicationStyle currentStyle) {
    if (mBounds.isEmpty()) return 0;
    return Math.min(Math.min(mBounds.height(), mBounds.width()) / 2, currentStyle.getBorderRadius());
}
```

```java
// LayoutUtils.java:128-132
public static void getInnerBounds(@NonNull Rect outRect, @NonNull Rect inRect, float radius) {
    outRect.set(inRect);
    int padding = (int) Math.ceil((Math.sqrt(2.0f) - 1.0f) * radius);
    outRect.inset(padding, padding);
}
```

`getInnerBounds` is called with `Math.max(getBorderRadius(mActiveStyle), getBorderRadius(mAmbientStyle))`
at `ComplicationRenderer.java:1076-1079` and again at `1475-1478`; text bounds are then
intersected with the result (1081–1086).

Doing the arithmetic with the **default** `borderRadius` of `Int.MAX_VALUE`
(`ComplicationStyle.kt:346`): the radius saturates at half the shorter edge, so
`padding = ceil(0.4142 × edge/2) ≈ 0.207 × edge` **per side**, leaving an inner box of roughly
**58.6 % of the declared edge**. For square `SHORT_TEXT` bounds with an icon, the text then gets
the bottom half of the central square of *that* — on the order of 29 % of the declared height.

Both style slots matter: because of the `max(...)`, setting `borderRadius` on `activeStyle` alone
has no effect on the inset while `ambientStyle` keeps the default.

### `borderWidth` does NOT inset content — it is purely a stroke width

Worth stating explicitly, because `borderRadius`'s behaviour above invites the assumption that the
other border property does something similar. It does not. `borderWidth` occurs **exactly once** in
the whole renderer:

```java
// ComplicationRenderer.java:1383, in PaintSet's constructor
mBorderPaint.setStrokeWidth(style.getBorderWidth());
```

`calculateBounds()` (998–1090) derives its inner box from `getBorderRadius(...)` alone (1076–1079);
no `borderWidth` term appears in any layout path. So a border neither reserves nor consumes content
space at any width, and the question "does a *transparent* border skip the inset?" has no subject —
there is no inset to skip.

Three related facts from the same code:

- **`BORDER_STYLE_NONE` and a zero-alpha `borderColor` are the same thing.** `drawBorders`
  (529–534) skips entirely on `NONE`, and `PaintSet` additionally does `mBorderPaint.setAlpha(0)`
  for it (1380–1382). A solid border with a transparent colour therefore renders identically, which
  makes `borderStyle` redundant for a watch face that controls the colour.
- **The stroke is centred on the slot edge and nothing clips it.** `drawBorders` strokes
  `mBackgroundBoundsF` — the full bounds — and `draw()` (445–446) only does `save()` + `translate()`,
  no `clipRect`. Half of `borderWidth` therefore paints *outside* the declared bounds.
- Effective default is **`1dp`**, not the `1` px field initializer, for any context-attached
  drawable — see the resource-defaults box in the `ComplicationStyle` section.

Other confirmed details: `ComplicationStyle.textSize`/`titleSize` are applied via
`mPrimaryTextPaint.setTextSize(style.getTextSize())` (1342) and
`mSecondaryTextPaint.setTextSize(style.getTitleSize())` (1356). `setBounds(Rect)` at 356.

---

### `GOAL_PROGRESS` renders very differently from `RANGED_VALUE` — including a hardcoded red arc

Both types share the *same* content layout (see the helper table below), but their **ring drawing is
completely different code**, and `GOAL_PROGRESS`'s has non-configurable visuals. Anyone choosing
between the two for a progress-style complication should know this before picking.

`ComplicationRenderer.drawRangedValue` branches at line 617: `TYPE_GOAL_PROGRESS` is diverted to
`drawGoalProgress` (704–770) and returns; every other type uses the normal ranged-value path.

Constants: `OVER_ACHIEVEMENT_FRACTION = 1.1f` (128), `OVER_ACHIEVEMENT_ARC_LENGTH = 36.0f` degrees
(125), `RANGED_VALUE_START_ANGLE = -90` i.e. 12 o'clock (98), `STROKE_GAP_IN_DEGREES_FOR_SCORE = 15`
(92), `STROKE_GAP_IN_DEGREES = 4` (89).

What `drawGoalProgress` does that `RANGED_VALUE` does not:

| Behaviour | `GOAL_PROGRESS` (704–770) | `RANGED_VALUE` (622+) |
|---|---|---|
| Scale | `targetValue * 1.1` — progress is scaled against **110 % of target**, so the indicator always reads ~9 % lower than `value/target` | `(value − min) / (max − min)` |
| Track | full-circle arc **always drawn** at fixed length | in-progress + remainder arcs sized to actual progress |
| Progress mark | a **dot** (`canvas.drawCircle`, 754–769), not a filled arc | filled arc |
| Final 36° | **`paintSet.mInProgressPaint.setColor(Color.RED)`** (742) — a hardcoded red "over-achievement" arc, drawn unconditionally regardless of progress | none |
| Gap | always 15° (`STROKE_GAP_IN_DEGREES_FOR_SCORE`) | 4°, or 0° at 0 %/100 % |

**The red arc is not stylable.** `Color.RED` is written directly into the paint and restored
afterwards (741–750); no `ComplicationStyle` property influences it — not
`rangedValuePrimaryColor`, not `rangedValueSecondaryColor`. A watch face that wants no red segment
must avoid `GOAL_PROGRESS`, not restyle it.

**Content layout is identical between the two**, so icon/title differences between a `GOAL_PROGRESS`
and a `RANGED_VALUE` rendering of the same provider are **payload** differences, not layout ones:
`TYPE_GOAL_PROGRESS`, `TYPE_RANGED_VALUE` and `TYPE_WEIGHTED_ELEMENTS` all resolve to the same
helper (1023–1035) — `RangedValueLayoutHelper` when the ring is shown, or
`IconLayoutHelper`/`ShortTextLayoutHelper` when `isRangedValueProgressHidden` is set. And
`RangedValueLayoutHelper` does render icon and title when present (`getIconBounds` 109–120,
`getShortTitleBounds` 202–213, the latter requiring **both** title and short text to be non-null).

*Device-confirmed (SM-R890, 2026-08-08):* full white track, progress dot short of the true fraction,
and a red arc at the end of the circle — all three matching the source above.

> ⚠ **Superseded reading.** An earlier version of this entry attributed a missing icon/title under
> `GOAL_PROGRESS` to "the provider sending a leaner payload". That was **wrong** — see the next
> section, which establishes from a live payload dump that the icon and title are present and are
> dropped by the *layout helpers*, not by the provider.

### A payload carrying BOTH `monochromaticImage` and `smallImage` renders NEITHER (square slots)

A genuine sharp edge in the layout helpers, worth knowing because the symptom ("provider sends no
icon") is misleading and the real cause is a self-cancelling delegation inside androidx.

Both helpers gate the icon on the *small image's* presence:

```java
// ShortTextLayoutHelper.getIconBounds (45-60) and RangedValueLayoutHelper.getIconBounds (109-120)
if (!hasIcon() || hasSmallImage()) { outRect.setEmpty(); }
```

So when a payload has a small image, the **monochromatic icon is deliberately suppressed** — the
small image is meant to render instead. But for a **non-wide** (square/round) slot that also has
short text, `RangedValueLayoutHelper.getSmallImageBounds` (123–139) resolves the small image's
position by delegating to *the icon method*:

```java
} else {
    mShortTextLayoutHelper.getIconBounds(outRect);          // <- returns EMPTY when hasSmallImage()
    outRect.offset(mRangedValueInnerSquare.left, mRangedValueInnerSquare.top);
}
```

`mShortTextLayoutHelper` holds the same `ComplicationData`, so `hasSmallImage()` is true there too and
that call returns an empty rect; offsetting an empty rect leaves it empty. **Net result: icon bounds
empty (because a small image exists) and small image bounds empty (because the icon method
self-nullifies) — nothing is drawn.** The wide-rectangle branch is unaffected: it uses
`scaledAroundCenter(...)` directly and renders fine.

**And the title disappears too, for an independent reason — but only via the same delegation.**
`RangedValueLayoutHelper.getShortTitleBounds` (202–213) has its *own* gate first
(`!hasShortTitle() || !hasShortText()` → empty, "as title is meaningless without text"), and for a
**wide** slot it positions the title itself. For a **non-wide** slot it delegates to
`ShortTextLayoutHelper.getShortTitleBounds` (135–143), which is `setEmpty()` when `hasIcon()` — and
`hasIcon()` is true (the monochromatic image *is* in the payload) even though that icon may not be
drawn. So on square slots the title is suppressed by an icon that isn't visible; on wide slots it is
not.

Be precise about this when quoting it: the `hasIcon()` rule belongs to `ShortTextLayoutHelper` and
reaches `RANGED_VALUE` **only** through the non-wide delegation — it is not a rule of
`RangedValueLayoutHelper` itself. *Payload-gap alternatives are ruled out for the case measured
here:* the live `ComplicationSlot.data` dump showed `title` and `text` both non-null. And a Watch
Face Format (declarative) watch face showing a title on the same provider says nothing about these
helpers — WFF does not use `ComplicationDrawable` at all (now proven, not assumed: see the
`DeclarativeWatchFaceRuntime` section below).

*Device-confirmed:* a Samsung Health steps provider sends `monochromaticImage`, `smallImage`, `title`
and `text` together (verified by dumping the live `ComplicationSlot.data`, which prints the API-level
object un-redacted unlike the wire-format logs). On a square slot, `RANGED_VALUE` rendered ring +
value only — no icon, no title — exactly as the code above predicts. The same payload under
`SHORT_TEXT` *did* show an image, because that path never enters the `RangedValueLayoutHelper`
delegation.

**Consequences for a watch face using `ComplicationDrawable`:** with such a payload on a square slot
you effectively choose between *ring without icon* (`RANGED_VALUE`/`GOAL_PROGRESS`) and *icon without
ring* (`SHORT_TEXT`, or setting `isRangedValueProgressHidden`). Levers that do exist: making the slot
wide (aspect > 2:1) takes the unaffected branch, and a watch face that overrides
`CanvasComplication.loadData` can rewrite the incoming `ComplicationData` before it reaches the
drawable — e.g. dropping `smallImage` so `hasSmallImage()` becomes false and the monochromatic icon
renders. Neither is a library fix; both are workarounds for this delegation.

#### Editing a `ComplicationData`: the wire round-trip is lossless, the Builder is not

If a watch face needs to alter incoming complication data before rendering (e.g. to work around the
bug above), note that the **API-level builders cannot round-trip faithfully**.
`RangedValueComplicationData.Builder` (Data.kt, builder at 1266 onward) exposes setters only for
`tapAction`, `validTimeRange`, `monochromaticImage`, `smallImage`, `title`, `text`, `colorRamp` and
`valueType` — while its `build()` (1403–1428) also passes `dataSource`, `persistencePolicy`,
`displayPolicy`, `dynamicValueInvalidationFallback`, `extras` and `cachedWireComplicationData`, none
of which have public setters. Rebuilding through it therefore **silently drops those fields**.

The wire format round-trips completely:

```kotlin
val wire = apiData.asWireComplicationData()                 // @RestrictTo(LIBRARY_GROUP), Data.kt:180-181
WireComplicationData.Builder(wire)                          // copy ctor, ComplicationData.kt:1438
    .setSmallImage(null)                                    // 1690-1692, null => putOrRemoveField removes it
    .setBurnInProtectionSmallImage(null)                    // 1708
    .build()
    .toApiComplicationData()                                // @RestrictTo(LIBRARY_GROUP), Data.kt:2731-2733
```

`WireComplicationData.Builder(data)` copies `type` and the entire `fields` map, so every field not
explicitly changed survives. `hasSmallImage()` / `hasIcon()` are plain `fields.containsKey` checks
(895, 842), so removing a field genuinely flips them. Both conversion functions are
`@RestrictTo(LIBRARY_GROUP)` — lint-only, so callable with a suppression, same status as
`CurrentUserStyleRepository.updateUserStyle`.

Wire type constants are plain ints and carry no `@RequiresApi`, unlike the `ComplicationType` enum
constants: `TYPE_RANGED_VALUE = 5` (2044), `TYPE_GOAL_PROGRESS = 13` (2116),
`TYPE_WEIGHTED_ELEMENTS = 14` (2129) — handy for type checks on a pre-Tiramisu-capable code path.
(`TYPE_GOAL_PROGRESS = 13` also matches the `type=13` seen in WearServices logs after negotiating
that type.)

**Useful diagnostic:** `adb shell dumpsys activity service <pkg>/<WatchFaceService>` prints, per slot,
`supportedTypes`, `enabled`, `complicationSlotBounds`, the current `UserStyle`, and the full
`data=` / `timelineComplicationData=` objects with image and text fields visible. Values themselves
are redacted, but *field presence* — which is what layout decisions turn on — is not. This is far more
informative than WearServices logcat, where `mFields=REDACTED` hides everything.

**Practical guidance:** for a "progress toward a goal" complication, `RANGED_VALUE` is usually the
better negotiation target than `GOAL_PROGRESS` despite the name, unless the over-achievement
semantics and its fixed red arc are actually wanted. Ordering `RANGED_VALUE` ahead of
`GOAL_PROGRESS` in a slot's `supportedTypes` is what selects it (first-match rule).

## `LayoutUtils` (rendering 1.2.1, `androidx/wear/watchface/complications/rendering/utils/LayoutUtils.java`) — internal

| Helper | Line | Behaviour |
|---|---|---|
| `WIDE_RECTANGLE_MINIMUM_ASPECT_RATIO` | 34 | `2.0f` |
| `isWideRectangle(bounds)` | 40–42 | `width > height * 2.0f` |
| `getLeftPart` / `getRightPart` | 49–64 | splits off a leading square / the remainder |
| `getTopHalf` / `getBottomHalf` | 67–74 | exact halves |
| `getCentralSquare` | 80–87 | largest centred square that fits |
| `scaledAroundCenter` | 93–100 | scales edges by a fraction, centre fixed |
| `fitSquareToBounds` | 103–122 | |
| `getInnerBounds` | 128–132 | the `(√2 − 1) × radius` inset, above |

## `ShortTextLayoutHelper` (rendering 1.2.1, `.../utils/ShortTextLayoutHelper.java`) — internal

Layout for `SHORT_TEXT`. **All of it is framework-decided from the data present and the bounds'
aspect ratio — none of it is provider-driven or configurable.**

- **Icon placement** (`getIconBounds`, 45–60): if `isWideRectangle` → the left square part of the
  bounds; otherwise → the central square, then its **top half**, then the central square of that.
  `getSmallImageBounds` (63–78) is identical. So icon-beside-text vs icon-above-text is purely a
  function of whether the bounds are wider than 2:1.
- **An icon suppresses the title entirely** (`getShortTitleBounds`, 135–143): returns an *empty*
  rect when `hasIcon() || !hasShortTitle()`; otherwise the title gets the bottom half of the bounds.
- **Text placement** (`getShortTextBounds`, 105–121): with an icon → right part (wide) or bottom
  half of the central square (not wide); with a title and no icon → top half; text only → the full
  bounds.
- Gravity/alignment: `getShortTextGravity` (94–102) is `BOTTOM` when a title is shown without an
  icon, else `CENTER_VERTICAL`; `getShortTitleGravity` is always `TOP` (130–132);
  `getShortTextAlignment` (82–91) is `ALIGN_NORMAL` only for a wide rectangle with an icon, else
  `ALIGN_CENTER`.

Sibling helpers exist per type and were not read in detail: `LongTextLayoutHelper`,
`RangedValueLayoutHelper`, `IconLayoutHelper`, `SmallImageLayoutHelper`,
`LargeImageLayoutHelper`, plus the base `LayoutHelper`.

---

## `ComplicationSlotsUserStyleSetting` (watchface-style 1.2.1, `androidx/wear/watchface/style/UserStyleSetting.kt`)

**This is the only supported way to change a `ComplicationSlot`'s bounds or enabled state at
runtime**, because both setters are `internal` (see the `ComplicationSlot` section). A new
`ComplicationSlotsOption` is applied by
`ComplicationSlotsManager.applyComplicationSlotsStyleCategoryOption`
(`ComplicationSlotsManager.kt:225-232`) whenever the selected option changes (173, 188).

### Constructors — which are usable

Several overloads per class, and the *supported* one is not always the one that looks most
convenient. Annotations checked individually.

`ComplicationSlotsUserStyleSetting` (class at 927):

| Ctor | Line | Status |
|---|---|---|
| `(id, displayNameInternal: DisplayText, descriptionInternal: DisplayText, icon, …)` | 928–951 | internal base ctor, not callable |
| `(id, displayName: CharSequence, description: CharSequence, icon, complicationConfig, affectsWatchFaceLayers, defaultOption, watchFaceEditorData)` | 1262 | `@JvmOverloads` **`@RestrictTo(LIBRARY_GROUP)`** |
| `(id, resources, @StringRes displayNameResourceId, @StringRes descriptionResourceId, icon, complicationConfig: List<ComplicationSlotsOption>, affectsWatchFaceLayers: Collection<WatchFaceLayer>, defaultOption = complicationConfig.first(), watchFaceEditorData = null)` | 1307–1326 | `@JvmOverloads`, **plain public — the supported one** |

Its `init` block `require`s that `affectedWatchFaceLayers` contains
`WatchFaceLayer.COMPLICATIONS` (947–950), so that layer must always be passed.

`ComplicationSlotsOption` (class at 1452, extends `Option`):

| Ctor | Line | Status |
|---|---|---|
| `(id, displayName: CharSequence, screenReaderName: CharSequence, icon, complicationSlotOverlays, watchFaceEditorData)` | 1507–1521 | `@JvmOverloads` **`@RestrictTo(LIBRARY_GROUP)`** |
| `(id, resources, @StringRes displayNameResourceId, icon, complicationSlotOverlays, watchFaceEditorData)` | 1544–1558 | **`@Deprecated`** — *"Use a constructor that sets the screenReaderNameResourceId"* |
| `(id, resources, @StringRes displayNameResourceId, @StringRes screenReaderNameResourceId, icon, complicationSlotOverlays: Collection<ComplicationSlotOverlay>, watchFaceEditorData = null)` | 1588–1604 | `@JvmOverloads`, **plain public — the supported one** |

Both public ctors take `Resources` + `@StringRes` ids rather than `CharSequence`, so using this API
requires string resources even when the text is never user-visible.

An option whose `complicationSlotOverlays` is **empty** is documented as meaning *"the net result is
the initial complication configuration"* (KDoc ~1500) — i.e. a harmless no-op option, useful as the
schema's mandatory default.

`ComplicationSlotOverlay` (class at 974):

| Ctor | Line | Status |
|---|---|---|
| `(complicationSlotId: Int, enabled: Boolean? = null, complicationSlotBounds: ComplicationSlotBounds? = null, accessibilityTraversalIndex: Int? = null, nameResourceId: Int? = null, screenReaderNameResourceId: Int? = null)` | 974–991 | primary, **plain public — the supported one** |
| `(complicationSlotId, enabled, complicationSlotBounds, accessibilityTraversalIndex)` | 1002–1020 | **`@Deprecated`** (`DeprecationLevel.WARNING`) in favour of the 6-arg primary |
| `Builder(complicationSlotId)` | 1037 | public builder alternative |

Every override field is nullable and *"If null then no changes are made"* (KDoc 956–973), so one
overlay can change bounds only, `enabled` only, or both. `init` `require`s
`nameResourceId != 0` and `screenReaderNameResourceId != 0` (992–995) — note it rejects `0`, not
`null`.

### How a style change reaches the slots

`ComplicationSlotsManager.listenForStyleChanges(coroutineScope)` — 165–193,
`@RestrictTo(LIBRARY_GROUP)` `@WorkerThread`, called by the library at
`WatchFaceService.kt:2211` with the **UI-thread** scope. It resolves the current option via
`UserStyleSchema.findComplicationSlotsOptionForUserStyle(userStyle)` (public,
`CurrentUserStyleRepository.kt:693-699`), applies it once up front (173), then `collect`s
`currentUserStyleRepository.userStyle` and re-applies **only when
`previousOption != newlySelectedOption`** (183–190). A `null` new option triggers
`applyInitialComplicationConfig()` (187).

> ⚠ **Trap:** `UserStyleSetting.Option.equals` compares **`id` and nothing else**
> (`UserStyleSetting.kt:679-686`; `Option.Id` wraps a `ByteArray` compared with `contentEquals`,
> 609–638). Two options carrying *different* overlays but the same `Id` are equal, so the
> `previousOption != newlySelectedOption` guard skips them and the new bounds are **silently never
> applied**. Any code pushing successive options must give each one a distinct `Id`.

### The two `Id` types have different shapes and limits

| Type | Line | Backing value | `MAX_LENGTH` | Equality |
|---|---|---|---|---|
| `UserStyleSetting.Id` | 284–308 | `String` | **40**, enforced by `init` `require` (290–295) | `value == other.value` (297–304) |
| `UserStyleSetting.Option.Id` | 609–648 | `ByteArray`, plus a secondary ctor taking `String` and calling `encodeToByteArray()` (614) | **1024** (628), *not* enforced by an `init` block in this class | `value.contentEquals` (631–635), `hashCode` = `contentHashCode` (637–638) |

`Option.Id`'s KDoc (618–627) asks for ids *"ideally under 10 bytes"* because the `UserStyleSchema`
and its settings are sent over Bluetooth to the companion phone during editing. Note the same KDoc
refers to `UserStyle.MAXIMUM_SIZE_BYTES`, but **no such declaration exists anywhere in
watchface-style 1.2.1** — the only occurrence in the whole artifact is that KDoc reference
(`UserStyleSetting.kt:624`). Treat the "maximum UserStyle size" as undocumented in this version
rather than assuming a specific limit.

`Option.toString()` decodes the byte array back to a string, falling back to `ByteArray.toString()`
on failure (642–648) — handy when logging.

### `UserStyle` / `MutableUserStyle` — construction and validation

`UserStyle` (`CurrentUserStyleRepository.kt`) constructors: public `(Map<UserStyleSetting, Option>)`
(74–77, copies the map), public copy ctor `(UserStyle)` (65), public
`(UserStyleData, UserStyleSchema)` (~745 onward — *"Unrecognized style settings will be ignored.
Unlisted style settings will be initialized with that setting's default option"*), and an internal
`(ByteArray, UserStyleSchema)`. Conversions: `toWireFormat()` (132), `toUserStyleData()` (135),
`toMutableUserStyle()` (138); `MutableUserStyle.toUserStyle()` (338).

`MutableUserStyle.set(setting, option)` — 276–284:

```kotlin
public operator fun set(setting: UserStyleSetting, option: UserStyleSetting.Option) {
    require(selectedOptions.containsKey(setting)) { "Unknown setting $setting" }
    require(option.getUserStyleSettingClass() == setting::class.java) { … }
    selectedOptions[setting] = option
}
```

There is also a `set(settingId: UserStyleSetting.Id, option)` overload (297) that resolves the
setting by id first.

> ⚠ **KDoc contradicts the implementation.** `set`'s KDoc (267–275) says the option *"Must be a
> valid option for [setting]"* and declares `@throws IllegalArgumentException … if [option] is
> invalid for [setting]`. The body performs **no membership check** — only "setting is present" and
> "option class matches the setting class". A runtime-built option therefore passes. Same class of
> doc/code mismatch as `ComplicationDrawable.highlightDuration` above; trust the body.

`UserStyleSchema(userStyleSettings: List<UserStyleSetting>)` is a plain public constructor (428).
Useful members: `getDefaultUserStyle()` (617) — every setting mapped to its default option, which is
what `CurrentUserStyleRepository`'s backing `MutableStateFlow` starts from (710) — and
`findComplicationSlotsOptionForUserStyle(userStyle)` (693–699).

### Correction — options do *not* have to be enumerated in the schema

> **Supersedes an earlier entry in this file.** Both the old and new readings are of the **same**
> version (watchface-style 1.2.1), so this is a correction of a wrong reading, not a version
> difference. The superseded claim is quoted below rather than deleted, per this file's policy.

**An earlier entry in this file claimed** that only geometries enumerated in the `UserStyleSchema`
up front can ever be selected, i.e. that this mechanism can only express "switch between N
predefined layouts". **That is wrong**, checked against the same versions (watchface-style 1.2.1):

`CurrentUserStyleRepository.updateUserStyle(newUserStyle)` (718–722) calls `validateUserStyle`
(733–748), and that function `require`s only two things per entry:

1. the **setting** is `==` one in `schema.userStyleSettings` (736–741), and
2. the option's class matches the setting's class, via `value.getUserStyleSettingClass()` (742–747).

There is **no check that the option is a member of the setting's own option list.** A
`ComplicationSlotsOption` constructed at runtime with arbitrary `ComplicationSlotBounds` therefore
passes validation, as long as a `ComplicationSlotsUserStyleSetting` exists in the schema at all.
What must be enumerated up front is only the set of options a *system editor* can offer the user.

**Caveat on reachability:** `updateUserStyle` is `@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)`
(717). `@RestrictTo` is enforced by lint, not by the compiler or the JVM, so the call compiles and
runs — but it is not public API and lint will flag it (`RestrictedApi`).
`updateUserStyleForScreenshot` (725–731) is similarly restricted and returns an `AutoCloseable`
that restores the previous style via `compareAndSet`.

Also note the round-trip behaviour of a synthetic option: `UserStyle.toWireFormat` serialises
option **ids**, and the `UserStyle(UserStyleData, UserStyleSchema)` constructor documents that
*"Unrecognized style settings will be ignored. Unlisted style settings will be initialized with
that setting's default option"* (`CurrentUserStyleRepository.kt:745-752`). An id that isn't in the
schema therefore does not survive a restart — it falls back to the setting's default.

---

## `EditorSession` (watchface-editor 1.2.1, `androidx/wear/watchface/editor/EditorSession.kt`)

The whole artifact is two files — `EditorSession.kt` and `WatchFaceEditorContract.kt` — so the
surface below is exhaustive for this version.

### There is no programmatic way to assign a complication data source

Complete public surface of `interface EditorSession : AutoCloseable` (105):

| Member | Line | Mutability |
|---|---|---|
| `watchFaceComponentName: ComponentName` | 107 | read-only |
| `watchFaceId: WatchFaceId` | 113 | read-only |
| `userStyle: MutableStateFlow<UserStyle>` | 122 | **writable** |
| `previewReferenceInstant: Instant` | 125 | read-only |
| `userStyleSchema: UserStyleSchema` | 128 | read-only |
| `complicationSlotsState: StateFlow<Map<Int, ComplicationSlotState>>` | 137 | read-only |
| `commitChangesOnClose: Boolean` | 155 | writable |
| `complicationsPreviewData: StateFlow<Map<Int, ComplicationData>>` | 171 | read-only |
| `complicationsDataSourceInfo: StateFlow<Map<Int, ComplicationDataSourceInfo?>>` | 185 | read-only |
| `backgroundComplicationSlotId: Int?` | 188 | read-only |
| `getComplicationSlotIdAt(x, y): Int?` | 197 | query |
| `renderWatchFaceToBitmap(renderParameters, instant, slotIdToComplicationData): Bitmap` | 209 | query |
| `openComplicationDataSourceChooser(complicationSlotId): ChosenComplicationDataSource?` | 228 | **the only mutation path** |

**The contrast is deliberate and is the load-bearing fact:** `userStyle` is exposed as a
`MutableStateFlow` an app can write, while `complicationsDataSourceInfo`, `complicationSlotsState`
and `complicationsPreviewData` are all exposed as plain read-only `StateFlow` on the interface (the
impl's backing fields are `MutableStateFlow` at 463, 466 and 483, but that is not visible through
the interface). Style is app-writable by design; **which data source occupies a slot is not**.

`openComplicationDataSourceChooser` (215–230) is `@UiThread suspend`, opens the system chooser UI,
and returns `null` *"if the operation was cancelled"*, otherwise a `ChosenComplicationDataSource`
(404–408: `complicationSlotId`, `complicationDataSourceInfo`, `extras`). It throws
`IllegalStateException` if a previous invocation is still running. It is therefore **user-mediated
by construction** — there is no non-interactive variant, no "set" overload, and no writable flow
anywhere in the artifact.

Session construction: `createOnWatchEditorSession(activity: ComponentActivity)` (254) — must be
called on an Activity/Fragment initialisation path because it registers an activity result handler,
and is lifecycle-aware (auto-closes on `onDestroy`); `createHeadlessEditorSession(...)` (360);
`EDITING_SESSION_TIMEOUT = Duration.ofSeconds(4)` (390), thrown as
`TimeoutCancellationException`. `DEFAULT_PREVIEW_INSTANT = Instant.ofEpochMilli(-1L)` (236).

`WatchFaceEditorContract.kt`: `EditorRequest` (100) carries `watchFaceComponentName`,
`editorPackageName`, `initialUserStyle: UserStyleData?`, `watchFaceId`, `headlessDeviceConfig`,
`previewScreenshotParams` — again style only, no complication assignment; and
`PreviewScreenshotParams` (58).

> **Conclusion, checked exhaustively for these versions:** no API in standard AndroidX
> (`watchface`, `watchface-complications*`, `watchface-editor`) lets an app assign or reassign a
> complication slot's data source without the user picking it in the system chooser. The three
> plausible-looking candidates are all ruled out: `ComplicationSlot.defaultDataSourcePolicy` has an
> `internal` setter and is absent from `ComplicationSlotOverlay`;
> `WatchFaceHostApi.setDefaultComplicationDataSourceWithFallbacks` is called from
> `onComplicationsUpdated` but carries the library's own comment `// Note this is a NOP in the
> androidx flow.`; and `ComplicationDataSourceUpdateRequester` only asks an already-bound provider
> to refresh its data. `DefaultComplicationDataSourcePolicy` is a creation-time snapshot, surfaced
> to the system via `ComplicationSlotsManager.getDefaultProviderPolicies()` (:576).

### How an image is scaled inside its bounds — always centre-crop, never letterbox

Completes the picture for `SMALL_IMAGE`/`PHOTO_IMAGE`, where two separate steps decide what is seen.

**Step 1, the rect.** `SmallImageLayoutHelper.getSmallImageBounds` (35–38) and
`LargeImageLayoutHelper.getLargeImageBounds` (35–38) are the same two lines: `getBounds` then
`getCentralSquare`. `LayoutUtils.getCentralSquare` (80–87) takes `edge = min(width, height)` centred
on the slot. Unconditional — no style property, no complication type, no aspect-ratio branch reaches
it. A 267×102 slot therefore gets a 102×102 image area with the remainder unused.

**Step 2, the image inside that rect.** `ComplicationRenderer.drawSmallImage` (905–939) hands the
rect to `RoundedDrawable.setBounds` and draws. `RoundedDrawable.updateBitmapShader` (124–135) builds
a bitmap of exactly `bounds.width() × bounds.height()`, and `drawableToBitmap` (138–160)
**centre-crops** into it: it compares intrinsic width and height, scales the longer axis to cover, and
offsets by half the overflow. So the image always fills its square completely and loses its edges —
there is no letterboxing anywhere in this path, and no setting that changes it.

**Consequence for a watch face: the image must be drawn by the watch face, or it cannot be fixed.**
Both steps run inside `ComplicationDrawable` before anything a caller can influence, so the sides a
wide image loses in step 2 are gone before any bounds choice or canvas transform applies. Measured
case — a 450×225 provider bitmap in a 300×115 px slot:

| approach | horizontal scale | vertical scale | content |
|---|---|---|---|
| library alone | 0.51 | 0.51 | middle **half** of the image, in a 115×115 square |
| library + canvas stretch back to the slot | 1.33 | 0.51 | same missing half, now 2.6:1 distorted |
| watch face draws the image | 0.67 | 0.67 | whole image, undistorted |

The transform approach was implemented and rejected on device — it produced exactly the middle table
row (fat dots, skewed shapes, most of the graph absent). Drawing the image from the complication
data (`SmallImageComplicationData.smallImage.image` / `PhotoImageComplicationData.photoImage`, both
`Icon`) against its own `intrinsicWidth`/`intrinsicHeight` is the only way to get a correct fit. It
costs re-implementing just the image step: background and corner radius are easy to reproduce from
the same `ComplicationStyle` (`backgroundColor`, `borderRadius`), the ambient variant is
`SmallImage.ambientImage`, and every other complication type can still be left entirely to the
library — `SMALL_IMAGE`/`PHOTO_IMAGE` carry no text fields, which is what makes taking over their
drawing self-contained.

For comparison, a Watch Face Format face does not hit any of this: it declares the image rect itself
(`PartImage width="450" height="225"` for the same provider in this repo's own pushed template) and
scales the whole bitmap into it — no layout helper, no central square, no crop.

## The system's cached preview image — how it is refreshed (watchface 1.2.1, watchface-editor 1.2.1, watchface-client 1.2.1)

The thumbnail the system shows for a watch face (long-press "Customize", the watch face picker) is
**rendered and cached by the system**, not owned by the watch face. A watch face whose appearance
depends on state outside the `UserStyleSchema` — a loaded template, a chosen background image —
therefore keeps showing a stale thumbnail until something tells the system to re-render. Two
distinct mechanisms exist, and only one of them is usable outside an editing session.

### 1. `Renderer.sendPreviewImageNeedsUpdateRequest()` — public, callable by the watch face itself

`Renderer.kt:526`, `public`, **no `@RestrictTo`**. Its KDoc (516–525) states the intended use case
verbatim: *"Sends a request to the system asking it to update the preview image. This is useful for
watch faces with configuration outside of the `UserStyleSchema` E.g. a watchface with a selectable
background."* It also states two limits: *"The system may choose to rate limit this method … and the
system is free to schedule when the update occurs"*, and *"Requires a compatible system to work (if
the system is incompatible this does nothing)."* Callable from any thread.

Path through the library:

- `Renderer.kt:526-534` — forwards to `watchFaceHostApi.sendPreviewImageNeedsUpdateRequest()`, or,
  when the renderer is not yet attached to a host, sets `pendingSendPreviewImageNeedsUpdateRequest`
  (187). The pending flag is replayed in the `watchFaceHostApi` setter (190–205), so **a request
  issued before the host API exists is queued, not lost**.
- `WatchFaceHostApi.kt:151` — the interface method, default empty body.
- `WatchFaceService.kt:2575-2590` — the real implementation. Requires `interactiveInstanceId` to be
  initialised; otherwise it logs *"Ignoring sendPreviewImageNeedsUpdateRequest because
  interactiveInstanceId not initialized"* and does nothing. That is the case for a **headless**
  instance (the one the editor spins up), so only the interactive instance can ask. On success it
  records `lastPreviewImageNeedsUpdateRequest` and calls `IWatchfaceListener
  .onPreviewImageUpdateRequested(interactiveInstanceId)` on every registered listener.
- `WatchFaceService.kt:2790-2804` — `addWatchFaceListener` replays
  `lastPreviewImageNeedsUpdateRequest` to a listener that registers *after* the request. The request
  is therefore **latched**, not dropped, if nothing was listening at the time.
- `WatchFaceControlClient.kt:291-315` (watchface-client) — the consumer side.
  `getOrCreateInteractiveWatchFaceClient` takes a `previewImageUpdateRequestedListener`, documented
  as: the consumer *"should schedule creation of a headless instance to render a new preview
  image"*. Re-rendering is the system's job; the watch face only signals.

**So this is a hint, not a push: the app cannot supply pixels, only ask to be re-rendered.**

### 2. `EditorSession.close()` — pushes actual pixels, but only from a live editing session

`EditorSession.kt:703-750` (watchface-editor 1.2.1): on close, **if** `commitChangesOnClose &&
previewScreenshotParams != null && SDK_INT >= O_MR1` (718–730), it renders the watch face with
`renderWatchFaceToBitmap(...)`, wraps it via `SharedMemoryImage.ashmemWriteImageBundle`, and calls
`EditorService.globalEditorService.broadcastEditorState(EditorStateWireFormat(..., previewImage))`
(734–749). That is what makes "open the settings screen and leave it again" refresh a stale
thumbnail.

Not reachable outside a session, for two independent reasons:

- `renderWatchFaceToBitmap` is a member of `EditorSession` and needs the session's
  `WatchFaceEditorDelegate`.
- `EditorService` is `@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)` (`EditorService.kt:27-28`), and
  `broadcastEditorState` (117) delivers only to observers registered through
  `IEditorService.registerObserver` — the singleton is handed to the system by
  `WatchFaceControlService.getEditorService()` (238–239) and, per its own class doc, is *"intended
  for use by EditorSession only"*.

### Practical consequence

A watch face configured from outside the style schema has exactly one supported way to keep the
system's thumbnail current on its own: call `Renderer.sendPreviewImageNeedsUpdateRequest()` when its
appearance actually changes. Whether any given OEM system acts on it is not knowable from these
sources — the KDoc explicitly permits doing nothing — so it needs confirming on device per platform.

## Engine lifecycle — when slots and schema are built (watchface 1.2.1, `WatchFaceService.kt`)

Searched for every call site, because "can the app make its slots be rebuilt?" comes up whenever
something is fixed at creation time.

- `createComplicationSlotsManager` (declared 509) and `createUserStyleSchema` (declared 487) are
  invoked only through their `…Internal` wrappers (520/523 and 492/494).
- Those wrappers are called from: `createWatchFaceInternal` (2173, the real construction path —
  schema at 2195–2199, then slot manager at 2202–2207), the metadata/headless query helpers at
  1951, 1974 and 1987, `createHeadlessInstance` (2100) and `createInteractiveInstance` (2153).
- `createWatchFaceInternal` itself is reached only from `maybeCreateWatchFace` (1160),
  `createHeadlessInstance` (2100) and `createInteractiveInstance` (2153) — all driven by the system
  over IPC, and 1145–1152 explicitly guards with `!engineWrapper.watchFaceCreatedOrPending()`.
- `onCreateEngine()` is `final override` (638): a `WatchFaceService` subclass cannot even
  participate in engine creation.

**Conclusion: nothing in the public API lets an app force its own engine to be destroyed and
recreated.** Every entry point is system-initiated. Anything fixed at
`createComplicationSlotsManager` time stays fixed until the system independently recreates the
engine (watch face reselected, reboot, process death, editor session).

`ComplicationSlotsManager.onComplicationsUpdated()` is called by the library at
`WatchFaceService.kt:1679`, once per batch, every time complication data arrives from the system
(`listenForComplicationChanges`, 1665–1680). So dirty flags set on a slot are consumed and pushed
to the system on the next data update without anyone calling it explicitly.

## Compiled surface of `internal` members (watchface 1.2.1 `classes.jar`, via `javap`)

Kotlin `internal` members compile to **JVM-`public` methods with a `$<module>` name suffix**, so
they are reachable by reflection *without* `setAccessible` and without tripping hidden-API
restrictions. Recorded because it changes what is technically possible, not because it is advisable
— the suffix encodes the library's Gradle module name and can change between releases.

`androidx.wear.watchface.ComplicationSlot`:

```
public final androidx.wear.watchface.complications.ComplicationSlotBounds getComplicationSlotBounds();
public final void setComplicationSlotBounds$watchface_release(ComplicationSlotBounds);
public final boolean isEnabled();
public final void setEnabled$watchface_release(boolean);
public final void setComplicationBoundsDirty$watchface_release(boolean);
public final void setEnabledDirty$watchface_release(boolean);
public final android.graphics.Rect computeBounds(android.graphics.Rect, boolean);
```

`androidx.wear.watchface.ComplicationSlotsManager`: `onComplicationsUpdated$watchface_release()`,
`applyComplicationSlotsStyleCategoryOption$watchface_release(ComplicationSlotsOption)`,
`init$watchface_release(...)`, `onComplicationSlotSingleTapped$watchface_release(int)`,
plus `setWatchFaceHostApi$watchface_release` / `setRenderer$watchface_release`.

Note `computeBounds` is JVM-public with no suffix (it is `public` + `@RestrictTo(LIBRARY_GROUP)`,
`ComplicationSlot.kt:1294-1295`), so it is callable directly, subject only to lint.

## `watchface-client` (watchface-client 1.2.1) — not usable by an ordinary app

`WatchFaceControlClient` (`androidx/wear/watchface/client/WatchFaceControlClient.kt:75`,
`createWatchFaceControlClient`) and `InteractiveWatchFaceClient` look like a way to drive a running
watch face from outside, including `updateWatchFaceInstance(newInstanceId, userStyle)`
(`InteractiveWatchFaceClient.kt:218`, and a `UserStyleData` overload at 226).

It is gated by permission: the AAR manifest declares

```xml
<service android:name="androidx.wear.watchface.control.WatchFaceControlService"
    android:exported="true"
    android:permission="com.google.android.wearable.permission.BIND_WATCH_FACE_CONTROL" />
```

(`watchface-1.2.1.aar`, `AndroidManifest.xml:43-47`). Binding requires that Google-namespace
permission, so this surface is for the system's own picker/editor processes. *Not verified on
device* — worth a `dumpsys package` protection-level check before ruling it out completely, since a
comparable Samsung permission turned out to be `normal`.

Useful semantics recorded from its KDoc anyway (221–226): `updateWatchFaceInstance` *"Sets the
current UserStyle … and clears any complication data. Setting the new UserStyle may have a side
effect of enabling or disabling complicationSlots"* — independent confirmation that user style is
the sanctioned lever for slot enable/disable.

## Watch Face Format / `DeclarativeWatchFaceRuntime` — an androidx *host* with a non-androidx complication renderer

Elsewhere this document warns that a WFF watch face "does not use `ComplicationDrawable` at all".
That was originally an inference from package names. It is now **proven**, so it can be cited as fact —
and the proof also pins down what WFF *does* share with us, which is more than the original wording
implied.

Package `com.samsung.wear.watchface.runtime`, `codePath=/system/priv-app/DeclarativeWatchFaceRuntime`,
`versionName=4.0.00.16`. It declares `com.google.wear.watchface.runtime.RuntimeControlService` behind
`com.google.android.wearable.permission.BIND_WATCH_FACE_CONTROL`, and a `WallpaperService` under
category `com.google.android.wearable.watchface.category.RUNTIME_WATCH_FACE_SERVICE`.

**It *is* an androidx watch face.** `dumpsys activity service com.samsung.wear.watchface.runtime/com.google.wear.watchface.runtime.DeclarativeWatchFaceRuntime0`
prints androidx's own `ComplicationSlotsManager:` / `ComplicationSlot NN:` / `complicationSlotBounds=ComplicationSlotBounds(…)`
blocks and a `CanvasRenderer:` section (`canvasType=1`, `RenderParameters`, `watchFaceLayers`). So the
framework layer — `WatchFaceService`, `ComplicationSlotsManager`, `CanvasRenderer` — is the same library
this project uses. Framework usage on its own therefore proves **nothing** about the complication
renderer: supplying a custom `CanvasComplication` is a supported extension point, and this project uses
it too.

**But it does not render complications through `ComplicationDrawable`** — proof by contradiction from
live state on both watch faces, same device, same provider:

- `ComplicationSlot.dump` prints `data=${renderer.getData()}` (`ComplicationSlot.kt:1369`) — the
  **`CanvasComplication`'s own payload**, i.e. what the renderer actually holds, downstream of any
  `loadData` rewriting. (Confirmed by this project's own dump: the slot whose small image we strip
  prints `smallImage=null`, while an untouched `SHORT_TEXT` slot prints it present.)
- A WFF face's steps slot was square (`RectF(0.622, 0.400, 0.844, 0.622)` → 1:1), type `RANGED_VALUE`,
  fed by the same `StepsComplicationProviderService`, and its `data=` retained **both**
  `monochromaticImage` **and** `smallImage(type=ICON)`, plus `title` and `text`.
- That exact payload, on that exact shape, is the self-cancelling case proven above: `getIconBounds`
  empty because `hasSmallImage()`, `getSmallImageBounds` empty because it delegates to the same test.
  `ComplicationDrawable` would draw **no image at all**.
- The face visibly draws an icon.

Hence its complication drawing is done by its own renderer (the dump's trailing
`########### DWF renderer 4.0.16 (40016) ###########` block), not by `ComplicationRenderer`. The same
block reports the loaded document's metadata: `com.google.wear.watchface.format.publisher` (e.g.
`WatchFaceStudio-1.9.5`), `com.google.wear.watchface.format.version`, declared vs. max supported format
version, `clipShape`, `resolution` and `clock type`.

*Residual unknown, stated rather than glossed:* which field a WFF face's coloured icon comes from — the
provider's untinted `IMAGE_STYLE_ICON` small image or an asset bundled in the watch face document — is
not determinable from the dump. It does not affect the conclusion, since `ComplicationDrawable` would
render neither provider image here.

**What WFF does instead — a concrete, in-repo example.** `wear/watchfacepush/template/watchface.xml`
(this project's own pushed AAPS V4 face) drives `BgGraphComplication` through a `SMALL_IMAGE` slot of
420x210 and lays the image out explicitly:

```xml
<ComplicationSlot slotId="3" supportedTypes="SMALL_IMAGE EMPTY" x="15" y="189" width="420" height="210">
  <Complication type="SMALL_IMAGE">
    <PartImage x="-15" y="-8" width="450" height="225"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage>
  </Complication>
</ComplicationSlot>
```

The author states the image rectangle — here even larger than the slot and offset negatively. There is
no layout helper: WFF binds `[COMPLICATION.SMALL_IMAGE]` to a `PartImage` whose geometry the document
owns. The same file draws `RANGED_VALUE` as a hand-built `PartDraw` with `Line`/`Stroke` and a
`Transform` on `endX`, which is why a WFF ring never matches `ComplicationRenderer`'s arc geometry.

**This is the sharpest illustration of the split:** given the identical `SmallImage` from the
identical provider, `ComplicationDrawable` renders it as the slot's *central square*
(`SmallImageLayoutHelper.getSmallImageBounds` -> `getCentralSquare`, unconditional; and
`LargeImageLayoutHelper` for `PHOTO_IMAGE` is character-for-character the same), while WFF fills the
declared rectangle. **No `ComplicationStyle` property or complication type changes this** - a wide
image in a wide slot is unreachable through `ComplicationDrawable`, and reachable by default in WFF,
purely because one renderer computes image bounds and the other is told them.

**How to use this comparison correctly.** A WFF face is a valid source of evidence about *providers*
(what a given data source sends, which types it serves, whether content is trust-gated) because that
half is the same system service on both sides. It is **not** evidence about layout, sizing, tinting,
title suppression or image selection — those are its renderer's decisions, and its renderer is not the
one in `watchface-complications-rendering`. Do not use "the WFF face shows X, so androidx can show X"
as an argument; that inference has already been made and retracted more than once.

## Other reusable facts

- `ComplicationData.tapAction: PendingIntent?` is public — `watchface-complications-data`
  1.3.0, `androidx/wear/watchface/complications/data/Data.kt:151`. This is what
  `onComplicationSlotSingleTapped` sends, so a watch face can dispatch a complication's own tap
  action itself if it needs to.
- `ComplicationHelperActivity.createComplicationDataSourceChooserHelperIntent` — its Javadoc states
  that from Android R onwards *"this API can only be called during an editing session"*. An
  app-initiated launch with no system-side editing session is cancelled by Wear Services.
  ⚠ **Reference unverified — now confirmed absent from the artifact that would be the obvious
  place for it.** The quote is verbatim from a source read, but the class is **confirmed absent**
  from `watchface-complications-data-source` 1.3.0 — that artifact's sources jar contains exactly
  four files (`ComplicationDataSourceService.kt`, `ComplicationDataSourceUpdateRequester.kt`,
  `ComplicationDataTimeline.kt`, `WearSdkComplicationsApi.kt`, verified by listing the jar), none of
  them this class — and it is likewise not in `androidx.wear:wear` 1.3.0/1.4.0. It is the legacy
  `android.support.wearable.complications` class, whose sources are not in this project's
  Gradle cache. Re-establish the `file:line` when a copy is available before relying on it.
- `WatchFaceService` public surface is small and contains **no way to force engine recreation**:
  `getXmlWatchFaceResourceId` (449), `getUiThreadHandler` (646), `getBackgroundThreadHandler`
  (684), `getSystemTimeProvider` (699), `getWallpaperSurfaceHolderOverride` (779). Everything that
  drives the engine lifecycle is `internal` (`WatchFace.kt`/`WatchFaceService.kt`).
- `ComplicationSlotBoundsType` values are `ROUND_RECT`, `BACKGROUND`, `EDGE`
  (`ComplicationSlot.kt:231-239`); the `@IntDef` annotation itself is `@RestrictTo(LIBRARY_GROUP)`
  while `ComplicationSlot.boundsType` is public.
- `ComplicationType` — **12** values, declared in `Type.kt` (not `ComplicationType.kt`),
  watchface-complications-data 1.3.0, lines 31–45, each wrapping a `WireComplicationData.TYPE_*`
  constant: `NO_DATA` (32), `EMPTY` (33), `NOT_CONFIGURED` (34), `SHORT_TEXT` (35), `LONG_TEXT`
  (36), `RANGED_VALUE` (37), `MONOCHROMATIC_IMAGE` (38, wire type `TYPE_ICON`), `SMALL_IMAGE` (39),
  `PHOTO_IMAGE` (40, wire type `TYPE_LARGE_IMAGE`), `NO_PERMISSION` (41), `GOAL_PROGRESS` (43) and
  `WEIGHTED_ELEMENTS` (45). The last two are `@RequiresApi(Build.VERSION_CODES.TIRAMISU)` (42, 44).
  Note the enum name does not always match the wire name.

---

*Draft — pending review. Contents are observations from published library sources at the versions
listed above; verify against the sources for the version you are building against before relying
on any specific line reference.*
