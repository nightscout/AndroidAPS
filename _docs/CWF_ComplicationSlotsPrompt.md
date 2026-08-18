# CWF Complication Slots — Status & Reference

Originally written as a task prompt for Claude Code. Restructured into a living status/reference
document: what's done, what's in progress, what's deferred, and the real root causes found along
the way (worth keeping for future maintainers — this feature had several silent-failure modes
that took real investigation to pin down).

**Division of labor with `_docs/Complication_Libraries.md`**: that file holds library facts —
every claim sourced with a `file:line` reference against a specific androidx version. *This* file
holds project decisions, status, and backlog. When in doubt about a library behavior, check that
file first; when in doubt about what's done or why a decision was made, check this one.

---

## Status

Complications work end-to-end on the Custom watch face: N slots (currently 5, architecture
proven to scale from the original 3 with no code change beyond one `ViewMap`/`PrefMap` entry per
slot), real system providers, long-press picker, CWF-json-driven position/size/visibility and
styling (global + per-slot cascade), correct z-order, live per-tick geometry sync. Generic
complication plumbing has been extracted out of `CustomWatchface.kt` into
`watchfaces/utils/WatchFaceComplication.kt`, reusable in principle by other watch faces.

**Currently in progress**: generalizing the complication picker from a hardcoded 3 slots to N,
adding per-slot visibility preferences, and filtering the preference screen to what's actually
relevant for the loaded CWF — see "In Progress" below.

**Not working / structurally blocked** (not bugs, platform limits — see Root Causes and Closed
Investigations):
- AAPS Settings-menu entry point for the picker (Root Cause 4) — no app-side fix exists.
- Live heart-rate value on Samsung Health's Cardio complication — confirmed platform trust-gating,
  no app-side fix exists.
- Full-color `MonochromaticImage`, and wide (non-square) `SmallImage`/`PhotoImage` fill — both
  confirmed unconditional androidx renderer limitations. "Own the rendering" would fix both, and
  the ring/GOAL_PROGRESS cases below — still an undecided scope/cost call, not started.
- Fallback watch face for ~30–60 s after a reboot, a reinstall, or any watch-face process death — a
  Samsung binding-order defect (all three entry points confirmed), proven to affect an unrelated
  third-party watch face identically. No app-side fix; waking the watch ends it. See "Closed
  investigations".

---

## Checklist

### Done

**Core feature (original session)**
- [x] Real `ComplicationSlot`s declared, positioned/sized/visible from CWF json
- [x] Rendering wired into the render loop, correct z-order
- [x] Taps routed by the framework (no manual hit-testing)
- [x] Long-press "Customize" picker works end-to-end, providers persist correctly
- [x] Root Causes 1–5 fixed (legacy-API false start, Dagger injection race, `EditorSession`
  headless-instance crash, AAPS-menu picker cancellation — parked, black-screen-on-reinstall)
- [x] Provider name shown under "Complication N" in the long-press preferences menu

**Rebase onto `Nightscout/dev`**
- [x] Branch rebased onto upstream (`f8f99e50` + `555ed133`, PR #5042 "Watchface Activation for
  Samsung"/"...cleaning"). Adopted upstream's `key_selected_watchface` gating in
  `PreferenceMenuActivity`/`WatchFaceCatalog`/`SamsungWatchFaceEditor` — this **resolved the
  CustomWatchface half** of "restrict Settings-menu entries" (see below): the complication
  entries can now only be reached via CWF's own preference screen, after CWF is actually
  active. Also fixed a real defect this replaced: `watchFaceComponentFor()`'s always-non-null
  `else` branch meant *every* Settings screen previously activated CWF as a side effect.
- [x] All complication-specific local commits (picker plumbing, headless-instance Dagger fix,
  `EditorSession` double-close fix) preserved through the rebase — no upstream equivalent.
- [x] A leftover SysUI-editor-mode block, mistakenly living in `ComplicationPickerSupport.kt`,
  identified and removed in favor of upstream's `SamsungWatchFaceEditor`.

**Live per-tick refresh (Open bug A) — architecturally resolved**
- [x] Complications now participate in the same refresh trigger as every other view.
  `WatchFaceComplications.syncGeometry()` runs every `onDraw()`, pushing current
  bounds/enabled via the `ComplicationSlotsUserStyleSetting` mechanism (the only supported way
  to mutate a slot after creation) — so position/size/visibility now follow CWF reload *and*
  live `DynProvider` offsets, not just Engine restart.
- [x] Style similarly reapplies every refresh (`ViewMap.customizeComplicationView`) — but **only
  3 of 12 style properties can currently vary dynamically** (font color, background color,
  text size), because `DynProvider` only exposes per-step getters for those three. Title
  color, icon color, ring width, border radius, title size stay static until `DynProvider`
  itself gains the missing getters. Recorded as a real, scoped limitation, not a bug.

**Architecture refactor**
- [x] Complication-specific plumbing fully externalized out of `CustomWatchface.kt` into a
  dedicated file (`WatchFaceComplications`, `ComplicationSlotState`, `ComplicationStyleValues`,
  `SyncLoadingCanvasComplication`) — reusable in principle by other watch faces. Only
  CWF-specific json decoding stays in `CustomWatchface`/`ViewMap`; live per-slot state
  deliberately stays off `ViewMap` (a live `ComplicationDrawable` can't safely be enum state —
  the editor runs a second, concurrent headless instance).
- [x] Complications now follow the same customize/refresh pattern as every other view type:
  geometry and style both decoded in `ViewMap` (mirroring `customizeTextView`, not a separate
  cascade class), applied every refresh tick, with preference-gated visibility
  (`SHOW_COMPLICATION_N`) mirroring `SHOW_IOB`/`SHOW_COB` — confirmed genuinely inert when off
  (no render, no taps, no data served), not just invisible.
- [x] 3→5 slot manual stress test confirms the architecture scales with one new `ViewMap` + one
  new `PrefMap` entry per slot, no other `CustomWatchface`-side change — **except** the
  picker, still hardcoded to 3 (see "In Progress").

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
  `BORDERRADIUS`, `RINGWIDTH` (see the units change below), `RINGPRIMARYCOLOR`,
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
- [x] `RINGWIDTH` replaces the previous ratio-only fallback; `2dp` (the library's own default)
  confirmed rejected — mixes two incompatible resolution-independence systems (`dp` vs. this
  format's `zoomFactor`), and would be the one CWF dimension that doesn't scale with its slot.

**`RINGWIDTH` units changed — breaking, and deliberately taken now**
- It was implemented earlier this session as a **percentage of the slot width**. It is now an
  **absolute int in the shared 400×400 space**, scaled by `zoomFactor`, exactly like `WIDTH`,
  `HEIGHT` and `BORDERRADIUS`.
- Why: consistency with the rest of the format was judged worth more than the proportionality a
  percentage gives. Free to change today — the key has never shipped to a user, and the one real CWF
  using complications has a 106-wide slot where both conventions land within about a pixel of each
  other. It will not be free later, hence doing it before more CWFs adopt the key.
- **The absent-key default moved to the same convention**, which is the point of the change:
  `PROVISIONAL_RING_WIDTH_RATIO = 12` (meaning *slot width ÷ 12*) became
  `PROVISIONAL_RING_WIDTH = 9` (meaning *9 in 400×400 space*, scaled by `zoomFactor` like the key).
  Keeping a proportional default alongside an absolute key was tried first and rejected: it gives
  "what does `ringWidth` mean" two different answers depending on whether the key is present, which
  cannot be documented cleanly. `9` is what the old ratio produced on the 106-wide slot of the one
  real CWF using complications (106/12 ≈ 8.8), so that dial keeps its appearance.
- Consequence, accepted deliberately: an unusually large or small slot no longer gets a
  proportionally scaled ring by default. A CWF that wants one sets `RINGWIDTH` itself.
- Upper clamp re-expressed in the new units: capped at the slot width (was 100%). Same intent — the
  ring is stroked centred on its arc, so anything wider paints outside the complication.

**Border support added — `BORDERCOLOR` + `BORDERWIDTH`, no `BORDERSTYLE` ever**
- `borderStyle` is pinned to `SOLID` in code and is not exposed as a json key. It would be a second
  knob contradicting the first: `PaintSet` implements `BORDER_STYLE_NONE` as
  `mBorderPaint.setAlpha(0)`, so a transparent `BORDERCOLOR` already *is* "no border".
- `BORDERCOLOR` — string colour like every other colour key, **fully transparent when absent**, so an
  unset border looks exactly like today's `NONE`.
- `BORDERWIDTH` — int in the 400×400 space (not a percentage, per the alignment above), default
  `CustomWatchface.DEFAULT_BORDER_WIDTH = 2` ⇒ ~2.3px on a 450px screen. Named constant, easy to
  tune after testing.
- **Verified before choosing that default, and it overturned the premise of the question:**
  `borderWidth` takes part in **no** layout decision. It reaches only
  `mBorderPaint.setStrokeWidth` (`ComplicationRenderer.java:1383`); `calculateBounds` insets content
  from `borderRadius` alone. So unlike the original `borderRadius` defect, nothing has been silently
  eating content space — and there was nothing to eat it either way, since our `borderStyle` default
  was `NONE`, which skips `drawBorders` outright.
- `BORDERRADIUS`'s existing default was re-checked at the same time and is **correct as-is** (`0`,
  written unconditionally), so it stays settled rather than re-opened.
- Documented limit: the stroke is centred on the slot edge and the renderer sets no clip, so half of
  `BORDERWIDTH` paints *outside* the declared bounds. An argument for small values.
- [ ] Device-confirm: `BORDERCOLOR` alone draws a ~2.3px border; `BORDERWIDTH` thickens it; neither
  key set looks exactly as before; `RINGWIDTH: 5` gives a ~5.6px ring rather than 5% of the slot.
- [x] **Closes the original "complications render thinner/less readable than OEM" observation.**
  Root cause was the `borderRadius`-driven ~41% content-area inset (already fixed earlier by
  forcing `borderRadius = 0` unless a CWF sets one), not text weight. Once content stopped
  being shrunk, readability at the same declared size matched third-party watch faces — no
  separate bold-by-default styling needed.

### In Progress

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

**Subject 1 — Step 2, Phase 1: dedicated, code-built settings screen** — *device-confirmed*
- [x] `CustomWatchfaceConfigurationFragment` builds CWF's screen in code from
  `CustomWatchface.settingRows`, a list of neutral `WatchFaceSettingRow`s (Toggle / Choice /
  Action) declared in `watchfaces/utils/WatchFaceSettings.kt`. The watch face decides *which* rows
  exist and in what order; the fragment only knows how to turn a row into a `Preference`. That is
  what makes Phase 2 a change inside `CustomWatchface` alone.
- [x] A **fragment**, not a dedicated activity: `ConfigurationActivity` is the only activity with
  the `WATCH_FACE_EDITOR` intent-filter, and `EditorSession.createOnWatchEditorSession()` only
  works in the activity the system launched with that intent. A separate activity could only be
  reached by trampolining out of it, which loses the session — exactly the dead Settings-menu path
  (Root Cause 4). Both entry points host the new fragment; it branches on which one, since only
  `ConfigurationActivity` can open the picker directly.
- [x] Side effect worth having: Digital/Circle's shared fragments no longer contain any
  complication code, and `ComplicationPickerSupport.hasComplicationPreferences()` is gone —
  "is this the right screen" is now answered by which fragment is running, not by looking for
  known keys in it. That closes the shared-Activity scoping constraint permanently.
- [x] `PrefMap` gained `title`, taken from `metadataKey.label` wherever a `CwfMetadataKey` names the
  preference, so a row is labelled by the very string the phone already shows. Only `PREF_DARK` and
  `PREF_MATCH_DIVIDER` carry their own label (no metadata key); `PREF_UNITS` has none at all — it is
  pushed from the phone and has no row. Verified redundancy first: 16 of those labels were the same
  text declared once in `core:interfaces` and again in `wear`.
- [x] Device-confirmed: the code-built screen matches the xml one row for row, from both entry
  points. First attempt crashed on entry - `Preference.setDependency()` resolves its key through
  `PreferenceManager`, which does not know the screen until `preferenceScreen` has been assigned,
  so dependencies must be applied in a second pass after that. A settings xml hides this: the
  framework inflates the whole screen first and only then resolves dependencies.
- [ ] Delete `watch_face_configuration_custom.xml` - dead since this phase, deliberately kept until
  the end of the subject.
- Noted for Phase 2: "include external views" and "simplify UI" are not about what the dial shows —
  they drive template content and data presentation — so they are listed literally, not derived from
  a `PrefMap`/`ViewMap` pair, and must never be filtered out.

**Subject 1 — Step 2, Phase 2: filter preferences by view visibility** — *device-confirmed*
- [x] `settingRows` takes the stored CWF (opaque text, fetched by the host activity, parsed only
  inside `CustomWatchface`) and leaves out rows the loaded zip gives nothing to act on.
- [x] **Two arms, both needed.** A preference is kept when some `ViewMap` entry it gates is declared
  *visible* in the json, **or** when any `dynPref` block keys on it. The second arm is not an edge
  case: the AAPS V2 zip drives every colour from `key_dark` through one dynPref block that no view
  names, so a view-only rule would hide the very preference that CWF depends on most.
- [x] Visibility is read from the json alone, never from the preference itself — a row that vanished
  the moment the user switched it off could never be switched back on.
- [x] No json (nothing stored, or unparseable) keeps every row: a setting missing because a read
  failed is worse than one row too many.
- [x] "Switch external data" is filtered too, but keyed on the **views** rather than on a `PrefMap`:
  kept only while some `ViewMap` entry with `external > 0` is visible, since no single preference
  gates those views.
- [x] Exactly two rows are never filtered, and they sit last by design — "Simplify UI", then
  "Include external views in template". One decides how data is presented, the other what a template
  exported from here contains, so no CWF can make either irrelevant.
- [x] Device-confirmed with AAPS V2: `Show seconds` and `Show Week number` gone (both `gone` in that
  zip), `Dark` and `Matching divider` kept (dynPref), `Show Date` kept (day/month visible), all 5
  complication pairs gone (no complication block).
- Known, undecided: the screen is built in `onCreatePreferences`, so it reflects the CWF loaded when
  it opened. Loading a new zip while the screen is open does not re-filter until it is reopened — a
  one-line move to `onResume` if that ever matters in practice.

**Subject 1 — Step 2, Phase 2 (original scoping notes, both now resolved)**
- [x] Original goal: a per-view `PrefMap` entry's row is shown only when at least one `ViewMap`
  entry using it is currently visible in the loaded CWF json, never by the preference screen parsing
  json itself (hard rule) — met, and widened by the dynPref arm above.
- [x] **The shared Activity/Fragment constraint is gone rather than worked around**: CustomWatchface
  has its own fragment, so no filtering code can ever run for Digital/Circle. Worth remembering why
  the once-planned "gate on which keys the screen contains" would have been wrong:
  `watch_face_configuration_digitalstyle.xml` also declares `key_show_date` and
  `key_show_week_number`, so key-presence duck typing would have hidden Digital's own rows.
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

### Backlog / Future

**Provider assignment — re-scoped after this session's investigation, don't re-litigate the
re-scoping without new evidence**
- Exhaustively confirmed: no androidx API lets an app assign or reassign a slot's data source
  without the user picking it via the system chooser (`EditorSession.openComplicationDataSourceChooser`
  is the *only* mutation path in the whole artifact). The negotiated `ComplicationType` is fixed at
  pick time and persists across reinstall/reload/Engine recreation — device-confirmed by
  re-picking a Steps provider and watching its negotiated type actually change (`SHORT_TEXT` →
  `GOAL_PROGRESS`), which nothing else (reordering our type list, a CWF reload, a watch-face
  switch) achieved on its own.
- `DEFAULTPROVIDER`: narrowed to **true first-run-only** semantics — it can only ever apply to a
  slot the system has never bound. Must not be documented to CWF authors as "this zip will show
  provider X," since after any prior binding it won't. One-line addition via
  `DefaultComplicationDataSourcePolicy` when implemented.
- `LOCKPROVIDER`: becomes **app-side policy**, not a pure library feature — refuse to open the
  picker for a marked-locked slot in `ConfigurationActivity`/`ComplicationPickerSupport`.
  `setFixedComplicationDataSource` stays a best-effort creation-time extra alongside it. Needs a
  way for that (non-`CustomWatchface`) code to read "is this slot locked" without violating the
  CWF-json hard rule — likely the same exposed-state pattern being built for Subject 1.
- `SUPPORTEDTYPES` **mechanism already implemented and proven** — the ordered, data-bearing-first
  type list (`WatchFaceComplications.supportedTypes`) demonstrably changes negotiation outcomes.
  Remaining backlog item is narrower than originally scoped: expose it as a CWF json key, not
  build the mechanism. **Now has a concrete forcing case — see "Complication type negotiation".**

**Complication type negotiation — why an icon-only provider shows text, proven from its manifest**
- Symptom: a Samsung Health exercise complication showed a white icon days earlier, then only text,
  while the *same* provider showed its icon on a Samsung watch face throughout.
- Cause, from `ExerciseOtherWorkoutComplicationProviderService`'s own manifest (pulled from the
  device APK and decoded with `aapt2 dump xmltree`):
  `SUPPORTED_TYPES = "ICON,SMALL_IMAGE,SHORT_TEXT,LONG_TEXT"` — identical for every exercise
  provider, and containing **no** `RANGED_VALUE`/`GOAL_PROGRESS`/`WEIGHTED_ELEMENTS`.
- The system walks *our* ordered list and takes the first type the provider also offers. Ours puts
  the data-bearing types 1–3, `SHORT_TEXT` 4th, and `MONOCHROMATIC_IMAGE`/`SMALL_IMAGE` 6th–7th. So
  `SHORT_TEXT` always wins for this provider — and its `SHORT_TEXT` payload carries no image, which
  is exactly what the device log showed. The image types can never be reached.
- Samsung's watch face shows the icon because its slots ask for `ICON`/`SMALL_IMAGE` first. Not trust
  gating — plain negotiation order.
- Why it worked before: the negotiated type is fixed **at pick time** and stored by the *system* per
  (watch face, slot). An older binding had negotiated an image type; re-picking renegotiated it
  against the current list and landed on `SHORT_TEXT`. This also explains why reinstalling an older
  APK did not restore it — the binding is not in our app.
- **A global reorder cannot fix it**: putting image types before text would give this provider its
  icon but cost the heart-rate provider its value (it offers `ICON,SMALL_IMAGE,SHORT_TEXT`, so it
  would negotiate `ICON` and lose the number). Per-slot is genuinely required.
- Design agreed for when this is built, but **not implemented**: a per-slot json key with an *intent*
  vocabulary rather than raw type names, since neither a CWF author nor a user should need to know
  what `WEIGHTED_ELEMENTS` is — `value` (default, today's order), `icon`, `text`, each reordering the
  same full list. It is a preference order, not a filter, so a "wrong" choice degrades gracefully
  instead of breaking a slot.
- Constraint to document for CWF authors when it lands: `supportedTypes` is a `ComplicationSlot`
  builder argument, i.e. **creation-time only**, and the negotiated type is system-stored. So the key
  affects only *future* picks — seeing a change requires reloading the CWF, recreating the engine
  (watch-face switch or reboot) **and** re-picking the provider.
- Cross-CWF persistence semantics for provider assignments — still needs a design decision
  (dormant / prompt reselection / clear) before any code; unchanged from before.

**Payload-rewrite regressions found on device, fixed and now unit-tested**
- Symptom chain, all on one slot (an exercise complication, SHORT_TEXT): icon disappeared, then the
  **border** disappeared too as soon as `iconColor` was set on that slot — while complication1
  (heart rate, same type, same border keys) was fine throughout.
- Device experiment that isolated it: removing `iconColor` from that slot brought the border back.
  Since `iconColor` does only two things — set a tint, and flip `iconColorRequested` — the tint
  cannot reach a border, so the payload rewrite was the culprit.
- **Two guards added**, each covering one branch of the failure:
  1. `reapplyIconColorRequest()` now pushes into the drawable **only when the rewrite actually
     changes the payload** (identity comparison). Re-sending unchanged data is a write the system
     never asked for, outside its normal flow, and buys nothing.
  2. The rewrite never trades a working image for one that cannot draw: `hasIcon()` only says the
     field is *set*, so the icon is now resolved (`loadDrawable`) before the small image is dropped.
- **The missing icon has a separate cause, now proven from the provider's manifest** — see
  "Complication type negotiation" below. It is *not* trust gating: an earlier revision of this entry
  blamed `isForSafeWatchFace`, which was wrong and is superseded. The device log
  (`hasIcon=false hasSmallImage=false`) is accurate but is a *consequence* of the negotiated type,
  not of what the provider is willing to tell us.
- Honest gap: the earlier log showing that slot with *no* images contradicts `iconColor` having had
  any effect on it at all (the guards return early with no small image). Either the payload varies by
  selected activity, or something in the reload path is harmful even for unchanged data. Guard 1
  covers the second case regardless, which is why both were added rather than picking one.

**Unit tests for the complication rendering decisions — new, 16 tests**
- `ComplicationPayloadPolicyTest` (10) is a truth table for the library-vs-workaround decision, now
  extracted into a named pure function `shouldDropSmallImage(...)` so the rules are stated once and
  testable without Android. Each case names the real device behaviour it protects — the ranged-value
  both-images bug, heart rate's tintable icon, the exercise complication's untinted one, and the two
  never-downgrade guards above.
- `ComplicationImageFitTest` (6, Robolectric for `Rect`) pins `ComplicationImageFit.destination` —
  the arithmetic whose first version shipped cropped *and* distorted, and which only device
  comparison against a WFF face caught.
- Deliberately not covered: anything needing a live `ComplicationDrawable`/`WatchState`. The value is
  in the decisions, not in re-testing androidx's renderer.

**`iconColor` now works for every complication type — implemented, device test pending**
- Symptom: on `SHORT_TEXT` (Samsung heart rate) the icon stayed red whatever `iconColor` said, while
  it worked on `RANGED_VALUE`. Not a tint failure — the icon was never drawn:
  `ShortTextLayoutHelper.getIconBounds` empties the rect when the provider also sends a small image,
  and `drawSmallImage` clears the colour filter for `IMAGE_STYLE_ICON`. `iconColor` only ever feeds
  `drawIcon`. Mechanism with line references in `_docs/Complication_Libraries.md`.
- Fix: the payload edit that already existed for the RANGED_VALUE layout bug now also runs when the
  CWF **explicitly** asks for an `iconColor` — per slot or in the CWF-wide `complicationStyle`.
  Dropping the small image lets the library's own `drawIcon` run and tint with the requested colour,
  for any complication type. We choose the image; the library still does the colouring.
- Asking for nothing keeps the provider's own colours (unchanged behaviour, and the better default).
  `fontColor`, which `iconColor` falls back to for its *value*, deliberately does **not** count as
  asking — a CWF that only set a text colour never asked to lose the provider's icon.
- Reversible without new data from the system: `ComplicationSlotState.iconColorRequested` re-applies
  the decision to the payload already received, since `loadData` only runs when the system has
  something new and a CWF change is not that.
- Hard limit: a provider sending *only* a small image has no tintable image to switch to, so
  `iconColor` still does nothing there. Samsung's heart rate sends both, so that case is covered.
- [ ] Device-confirm: `iconColor` changes the heart's colour on SHORT_TEXT, transparent hides it,
  and removing the key restores the provider's red heart.

**Image fit for `SMALL_IMAGE`/`PHOTO_IMAGE` — implemented, device test pending**
- New per-slot key `imageFit`, also accepted in the CWF-wide `complicationStyle` block, with the
  usual cascade (slot → global → library behaviour). Values are named after
  `ImageView.ScaleType`: `fit_center` (default, unchanged), `center_crop`, `fit_xy`.
- Values mean what `ImageView.ScaleType` means, computed from the image's **own intrinsic size** —
  `fit_center` is a true letterbox showing the whole image.
- **First attempt (canvas transform around the library) was implemented, tested on device and
  discarded** — keep this, it is the reason the current code exists. It handed the drawable the
  central square it would have computed anyway and scaled the canvas around that call. All three
  values came out wrong, exactly as the arithmetic predicts for a 450×225 provider image in a
  300×115 px slot: `fit_center` a small square, `center_crop` with most of the curve outside the
  slot, `fit_xy` cropped *and* 2.6:1 distorted. The cause is that `RoundedDrawable` centre-crops the
  image into that square **before** any caller-side transform, so the missing sides can never be
  recovered afterwards.
- Now `SyncLoadingCanvasComplication.render` draws the image itself for `SMALL_IMAGE`/`PHOTO_IMAGE`:
  it takes the `Icon` from the complication data (cached until the icon changes), computes the
  destination rect from the image's intrinsic size, and paints background plus rounded corners from
  the same `ComplicationStyle` the library would have used, so `borderRadius` still shapes the slot
  and clips the image. Ambient uses `SmallImage.ambientImage` when the provider supplies one. Every
  other complication type is still drawn entirely by the library — those carry text, which is what
  makes taking over only these two self-contained.
- Mechanism and line references in `_docs/Complication_Libraries.md`, "How an image is scaled inside
  its bounds".
- [ ] Device-confirm on the 267×102 slot with `BgGraphComplication` (450×225 bitmap): `fit_center`
  (the default) should now show the **whole** graph undistorted, ~230×115 px centred — the same
  picture as the WFF face and as tapping through to the graph activity; `center_crop` should fill
  the slot with top and bottom trimmed; `fit_xy` should fill it stretched.
- **Behaviour change to watch for**: the default is no longer the library's centre-cropped square,
  so a near-square slot that previously showed a cropped image now shows the whole one, letterboxed.
  `center_crop` reproduces the old look for any CWF that wanted it.

**Styling — remaining audit items**
- ~~Image fit mode for `SMALL_IMAGE`/`PHOTO_IMAGE`~~ — done, see above. Original finding kept for
  context: **confirmed bug, design proposed, implementation paused**. `SmallImageLayoutHelper`/`LargeImageLayoutHelper` unconditionally crop to the slot's
  central square (`getCentralSquare`, identical code for both types) — no `ComplicationStyle`
  property or type switch escapes it (device-confirmed: a 267×102 slot renders 102×102). Contrast
  with this project's own WFF-pushed face (`watchfacepush/template/watchface.xml`), which fills a
  non-square `PartImage` rect by author declaration — WFF has no layout helper at all, so nothing
  computes bounds on the author's behalf there. Proposed: a new key + `JsonKeyValues` for
  stretch / fit-min-letterbox / fill-max-crop (today's square-crop kept as an explicit fourth
  named option). Paused pending Subject 1.
- `DynProvider` integration for complications, Direction 1 (CWF data → complication style):
  **partially done** — font color, background color, text size already flow through the exact
  same per-step getters every other view uses, zero complication-specific code. Extending to the
  other 9 style properties needs new `DynProvider` getters, not attempted this session.
- `DynProvider` integration, Direction 2 (complication value → drives other views): **explicitly
  out of scope**, deferred, not designed.
- Confirm whether fewer providers appear in our picker than an OEM's native settings is expected
  platform behavior (a proprietary catalog outside the standard chooser) — still just a
  confirmation pass, unstarted, low priority.

**"Own the rendering" — a real, live, undecided architecture option**
Raised and deferred multiple times this session, now with four concrete motivating cases rather
than one:
1. Ring geometry (radius/inset/gap/start-angle) — currently only tunable via a proportional
   `RINGWIDTH`, not owned.
2. `GOAL_PROGRESS`'s non-stylable visuals — a hardcoded `Color.RED` "over-achievement" arc, a dot
   instead of a filled arc, progress scaled against `target × 1.1` — none of it reachable via
   `ComplicationStyle`.
3. `MonochromaticImage` always flattened to one color — confirmed unconditional, no bounds shape
   or type escapes it (`ComplicationRenderer.drawIcon`'s filter application has no null branch).
4. Wide-slot image cropping (above).
   The interface (`CanvasComplication`) is small; the rendering contract it replaces is not
   (ambient/burn-in variants, async image loading, text-fitting/ellipsis/emoji, placeholder payloads,
   tap highlight). A scoped version (e.g. only the `RANGED_VALUE` family, or only image-bearing
   types, delegating everything else to `ComplicationDrawable`) was discussed as the realistic shape
   if this is ever pursued — not started.

**Settings-menu / SysUI (Root Cause 4)**
- [ ] AAPS Settings-menu picker — confirmed no app-side fix exists (exhaustive investigation:
  `WFUnderEditingResolver` requires system-level editing-session bookkeeping an app-initiated
  launch can't satisfy). Parked permanently unless a genuine SysUI entry point is found.
- [ ] "Potential major finding" about Wear OS 5+ Samsung "Factory Built" watches possibly needing
  the `ACTION_EDIT_WATCH_FACE` broadcast as the *only* way to select any AAPS watch face —
  **still unconfirmed**, needs a report from an affected user on that hardware. If confirmed,
  revisit the larger menu-restructuring item (three always-visible watch-face entries, "Select
  and Configure" framing) described in the original prompt.

**Taps stopped working after loading a complication zip — hardened, root cause not fully proven**
- Reported once: select CustomWatchface while a zip *without* complications is loaded, then load a
  zip *with* them — they render, but no tap does anything. **Not reproducible afterwards**, so the
  chain below is a mechanism proven from library source, not an observed sequence.
- Slots are built once, at engine creation, from the zip loaded *at that moment*. Since the slot
  identity consolidation their bounds fall back to `RectF(0,0,0,0)` when the zip declares no
  complication (the removed per-slot `defaultBounds` had non-zero values, with a comment saying
  "so slots never start zero-sized").
- `ComplicationSlotsManager.applyComplicationSlotsStyleCategoryOption` (`:225-239`) restores each
  slot's **initial** bounds and enabled flag whenever an applied `ComplicationSlotsOption` carries
  no overlay for it — and this schema's own default option carries none. Rendering does not notice,
  because it reads the placeholder views; only tap hit-testing reads the slot
  (`getComplicationSlotAt` `:409-419` → `ComplicationSlot.computeBounds` `:1295-1320`), so the
  symptom is exactly "draws fine, taps dead".
- The old `syncGeometry` compared its own last-pushed signature, i.e. intent against intent, so an
  external reset was invisible to it and never re-pushed. **Now it compares against the slots
  themselves** (`slot.enabled` / `slot.complicationSlotBounds.perComplicationTypeBounds`) and
  re-pushes whenever one is not where it should be — self-healing whatever caused the reset.
- Deliberately *not* done: reintroducing per-slot non-zero initial bounds. With the self-heal the
  first `onDraw` corrects a zero-sized slot anyway, and non-zero defaults would only move taps to
  the wrong place instead of nowhere.
- **What remains unproven**: which event applied an overlay-less option in the reported sequence
  (candidates: an editor/headless instance from the settings screen or the preview request, a style
  re-read on watch face selection). The reset behaviour itself is source-proven; the trigger is not.

**Stale long-press preview after loading a new zip — mechanism found, fix implemented, device test
pending**
- Symptom: load a new CWF, long-press the watch face, and the preview still shows the *previous*
  zip. Entering the settings screen and coming back refreshes it.
- The system renders and caches that thumbnail itself, and only knows to redo it when the
  `UserStyleSchema` changes — which says nothing about which CWF is loaded. Full mechanism, with
  `file:line` refs, in `_docs/Complication_Libraries.md`, "The system's cached preview image".
- **An app-side path does exist**, contrary to the first hypothesis in this session (that only an
  editor session could refresh it): `Renderer.sendPreviewImageNeedsUpdateRequest()` is public, is
  documented for exactly this case ("configuration outside of the `UserStyleSchema`"), and is
  latched by the library if nothing is listening yet. Why the settings-screen round trip works is
  a *different*, session-only mechanism: `EditorSession.close()` renders a bitmap and broadcasts it.
- Implemented as `WatchFace.requestPreviewImageUpdate()`, called from `setWatchfaceStyle()` inside
  the block that only runs when the json or resources actually changed — the system is documented
  to rate limit these requests, so firing per refresh tick would be wrong.
- [ ] **Device-confirm**: load a different zip, do *not* open settings, long-press, and check
  whether the preview shows the new dial. The library explicitly allows a system to ignore the
  request ("if the system is incompatible this does nothing"), so this is not proven until seen on
  hardware. Watch `WatchFaceService` in logcat for "Ignoring sendPreviewImageNeedsUpdateRequest
  because interactiveInstanceId not initialized", which would mean it was asked from a headless
  instance instead of the interactive one.

**Subject 2 — graphical picker (deliberately not started)**
A future direction, kept in mind while designing Subject 1 Step 2 so nothing there forecloses it:
a CWF-thumbnail-based screen where visibility toggles and provider-picker access happen by
tapping regions of an actual rendering of the loaded zip, instead of a flat, growing preference
list. Motivation: the preference list is getting long (2 rows per complication slot); exact
spatial selection beats numbered slots; a live preview of what a `twinView` would show instead,
if a complication is toggled off, would be a real usability win. Feasibility without
over-engineering is genuinely unknown and **not to be investigated yet** — Subject 1's
"is this slot/pref visible" query mechanism should be built so a future thumbnail UI could reuse
it, not as preference-screen-specific plumbing.

**Phone-side "Watch face info" summary — resolved, was never a code defect**
- [x] The earlier entry here said complications were "simply absent from that summary, not
  auto-detected", and wondered whether the screen reads the json itself or a watch-exported summary.
  Both halves are now answered. It *does* parse the json phone-side
  (`WearViewModel.listVisibleView`, `plugins/sync`), it *does* handle complications, and the
  observation came from a **phone app older than the branch**: `ViewKeys` gained `COMPLICATION1..3`
  in the branch's first commit and `4..5` on 13 Aug, while only the wear module was being rebuilt.
- Proven rather than argued: `WearViewModelTest.showCwfInfos lists visible complication views and
  hides the others` feeds a json with one visible complication, one `gone` and one wide one, and
  asserts what comes back. It passes, and stays as a regression guard.
- Note for the json-access rule: the phone parsing the CWF json here is **not** a violation. That
  rule is about the *watch* side, where `CustomWatchface` owns the format; the phone has always read
  the zip it imports (it builds the metadata and preference lists the same way).
- [x] Two real defects found on that screen while checking, both fixed: the file name rendered as
  `AAPSzip` (`CWF_EXTENSION` is `"zip"` with no separator, and one of the two places building the
  name forgot the dot), and every preference/view row was far taller than the metadata lines above
  it (`ListItem` enforces a 56dp minimum height — replaced with plain rows on the same spacing).

**Not yet tested**
- [ ] Z-order with a CWF using an opaque `cover_plate` over visible complications (only tested
  with a transparent `cover_chart`/no `cover_plate`).
- [ ] Ambient-mode rendering, burn-in protection, specifically for complications.
- [ ] The reverse pref-transition (re-enable `SHOW_COMPLICATION_N` → slot resumes receiving data)
  — follows from the same proven code path as disabling, but never isolated and device-tested
  as its own direction.

---

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

---

## Closed investigations (kept for future maintainers — real root causes, not guesses)

**Fallback watch face for ~30–60 s whenever the watch face has to be (re)bound — Samsung platform
defect, no app-side fix.** Reported as "the watch face takes 1–2 minutes to be ready after a reboot
or a reinstall, and the Big Digital Hour fallback shows first". Confirmed for **all three entry
points**, each with its own `watchFaceSetReason` and failure message:

| entry point | `watchFaceSetReason` | failure message |
|---|---|---|
| reinstall / process death | `WF_RECOVERY` | *"failed to recover … within 15000 milliseconds"* |
| cold reboot | `REBOOT` | *"failed to connect after user unlock"* |
| recovery retries after either | `WF_RECOVERY` | as above |

Reproducible on demand with `adb shell am force-stop info.nightscout.androidaps`, which is equivalent
to the reinstall case and needs no reboot — worth remembering, it made this diagnosable in minutes.

*The mechanism, from device logs:* the system asks WearServices for a watch-face client via
`AndroidXWatchFaceEngine.blockAndCreateWatchFaceWearServicesClient` **before** binding the wallpaper
— `setWallpaperComponent` is either absent or called with `name=null`. That waits on a future for
**10 s** for an engine that cannot exist, because nothing is bound. Attempts time out, then
*"Forcing to fallback watch face"*. Only a later `USER_INITIATED` switch calls
`setWallpaperComponent` with a real component **first**, and that one succeeds in **0.35 s**. So the
delay is entirely the platform's retry schedule (10 s timeouts, 20 s backoffs, a 15 s deadline), not
slowness in the watch face.

*Reboot capture, 19 Aug (boot at 15:31:44):* `switchTo=…, watchFaceSetReason=REBOOT` at 15:32:36.08
with **no preceding `setWallpaperComponent`** → `TimeoutException` after 10 s at 15:32:47.55 →
fallback at 15:32:47.65 → `USER_INITIATED` at 15:33:12.55 → first real
`setWallpaperComponent name=…CustomWatchface` at 15:33:13.99. Two *further* `WF_RECOVERY` attempts
followed at 15:34:09 and 15:34:21, with another 10 s timeout and another
`setWallpaperComponent name=null`, i.e. the system kept cycling even after the watch face was up.
The same log is full of unrelated `TimeoutException`s from Samsung's own components
(`StorageManagerService`, `FontLog`, `GmsPipelineLogger`, `CNotificationSetting`, `WCS`) — the device
is broadly contended after boot, which is context worth having before blaming any one app.

*Why the delay feels inconsistent rather than constant — this also answers "it is worse than
before":* it is not a fixed cost. It lasts **until something triggers a `USER_INITIATED` switch**.
That reboot took ~27 s to bind because the watch was being handled (wifi setup) right then; leaving
the watch alone lets the full retry schedule run, which is where 1–2 minutes comes from. Nothing
about this lives in AAPS, so a real regression on our side is not a plausible explanation for a
change over time.

*Proof it is not ours, and the reason this is closed rather than parked:* the identical sequence —
`name=null`, two 10 s timeouts, forced fallback, then instant success on `USER_INITIATED` — was
captured for **`com.space.galaxy.galaxy3d.watchface.wear`**, a third-party Watch Face Format face
hosted in Samsung's own `com.samsung.wear.watchface.runtime`. It shares **no code** with AAPS and is
not even code-based. Ours took ~50 s, it took ~56 s. Nothing about complications, the 3→5 slot
change, `syncGeometry`, the preview request or the DataStore read is involved.

*A wrong hypothesis worth recording, so nobody spends the build cycles again:* the prime suspect was
the `runBlocking { getCustomWatchface() }` in `createComplicationSlotsManager` — a cold DataStore
read blocking creation past the 10 s allowance. A temporary timing log **measured it at 41 ms** (7 ms
warm), and the log also showed `createComplicationSlotsManager` running *after*
`onInteractiveWatchFaceCreated`, so it cannot block creation at all. A `withTimeoutOrNull` guard was
added, proven useless by that measurement, and reverted. Lesson: measure the blocking call before
bounding it.

*User-facing note:* waking the watch or opening the watch-face picker triggers a `USER_INITIATED`
switch, which short-circuits the whole retry cycle and brings the watch face back immediately. That
is the whole workaround, and it explains why the delay seems variable.

*Superseded note, kept because the reasoning was sound at the time:* this entry originally excluded
cold reboot, on the argument that a reboot has no prior crash and should therefore bind the wallpaper
normally. The reboot capture above disproved that — the system skips the binding on the `REBOOT` path
too. The prediction was wrong; the discriminator set up to test it (is there a
`setWallpaperComponent` before `blockAndCreate…`?) is what settled it, and is the check to reuse if
this resurfaces on a future Wear OS version.

**Heart-rate/Cardio static label — platform limitation, confirmed, no app-side fix.** Samsung
Health's `HeartrateComplicationProviderService` manifest declares only `ICON`/`SMALL_IMAGE`/
`SHORT_TEXT` — no data-bearing type exists for it, at any trust level. Separately, but compounding
it: `ComplicationRequest.isForSafeWatchFace` (`SAFE`/`UNSAFE`, device-confirmed via provider logs)
lets Samsung Health serve materially different content — a static label under `UNSAFE`, a live
value under `SAFE` — for an *identical* provider and negotiated type. Ruled out as the deciding
factor: consumer-side permissions (structurally irrelevant — data flows provider→system→watch
face), request cadence (we're polled *more* often than a "SAFE" face, still get static content),
every other `ComplicationRequest`/builder field (only `isForSafeWatchFace` differs). What actually
decides `SAFE`/`UNSAFE` is undocumented, closed platform logic inside Samsung's `WearServices` —
install provenance correlates (sideloaded vs. Play-Store) but is unproven causal on a sample of
two. Cross-checked against a genuine third-party (non-Samsung) watch face confirmed running the
*same* androidx `ComplicationSlotsManager`/`CanvasRenderer` framework as us, which nonetheless
shows the live value with a correctly colored icon and title — proving by direct contradiction
that it does **not** use `ComplicationDrawable`; it owns its own `CanvasComplication`
implementation. **This is exactly the "own the rendering" case above, demonstrated in the wild**,
not evidence that our renderer has an unused capability. Separately noted, not an AAPS fix: a
third-party complication-provider app reading the brand-agnostic Wear Health Services API
(not Samsung Health) may sidestep this trust gate entirely — a user-facing workaround to document,
not code to write.

**Icon color — confirmed unconditional, proven from source.** `ComplicationRenderer.drawIcon`
applies its color filter (`PorterDuffColorFilter`/`ColorMatrixColorFilter` per ambient mode) with
no null branch, for every bounds shape and every complication type that carries a
`MonochromaticImage`. `iconColor` selects *which* single color, never whether one is applied. The
only native path to a multi-color image is `SmallImage` with `IMAGE_STYLE_ICON`, which is exactly
the image the both-images layout bug (`withoutRedundantSmallImage`) has to strip on a non-wide
slot to avoid a worse bug (both icon and small image rendering empty). The real tradeoff on a
square slot is single-tint icon vs. no icon at all — never single-tint vs. full color.

**Wide-image cropping — see backlog above** (kept open, not closed, since a design is proposed
but not built).

**Watch Face Format (WFF) — considered and rejected as a wholesale replacement, not as a
comparison tool.** Google made WFF mandatory for Play Store distribution/updates of legacy
code-based watch faces as of January 2026 — confirmed a Play-Store-specific restriction, not a
device-level block on sideloaded code-based watch faces. AAPS is not Play-Store-distributed, so
this is believed not to affect it; re-confirm if AAPS's distribution model ever changes. WFF
itself was ruled out as a target format for CWF: it is purely declarative (no executable code),
so it cannot host `DynProvider`/`ValueMap`'s arbitrary AAPS-data-driven logic. It remains useful
*only* as a comparison tool for what a different rendering approach can achieve (see "own the
rendering" above) — explicitly **not** as evidence that our own renderer has some undiscovered
capability, since the two share no rendering code.

*(Root Causes 1–5 above were recovered verbatim from `6a0d903de26` after a rebase left the
section structurally broken — heading emptied, 1 and 2 orphaned inside the checklist, 3–5 dropped
entirely. The original architecture-as-built notes and the "not yet tested" list from the original
session are unchanged except where superseded explicitly.)*

---

## Data hygiene note

Real BPM/step-count/other health values observed during device testing are never recorded in this
file or in `_docs/Complication_Libraries.md` — only the mechanism/finding they illustrate. This is
a standing practice for this project, not a one-off decision; keep it up in any future update.