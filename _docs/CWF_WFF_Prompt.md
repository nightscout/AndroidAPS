# Custom Watchface in Watch Face Format

How the Custom watch face reaches the screen on watches whose firmware no longer runs code-based
watch faces, why it is built this way, and what must not be broken.

This describes the solution. It is not a record of how it was reached: the measurements that decided
each choice are kept, the exploration that produced them is not.

| File | Holds |
|---|---|
| `Complication_Libraries.md` | **Library facts only.** Every claim carries `file:line` and a version. Read it *before* reading any androidx or Watch Face Format source. |
| `CWF_WFF_Test_Environment.md` | How to run and observe this, on an emulator and on a watch. |
| `cwf_bench_gw4.csv` | The per-view drawing costs measured on a Galaxy Watch 4, behind the numbers in section 3. |
| **this file** | The design, its reasons, and what is still open. |

---

## 1. The problem, and the shape of the answer

The Custom watch face lets a wearer design their own face in a zip and send it from the phone. It is
a code-based watch face: an Android service that draws itself. Newer Samsung firmware no longer
offers code-based faces in its picker, so on those watches it cannot be selected at all, and the
wearer's design becomes unreachable.

Watch Face Format is what that firmware does accept: a **declarative XML document**, with no code. It
cannot run our drawing code, and the Custom watch face is nothing but drawing code driven by a zip -
so the design cannot be translated into it.

The answer keeps our drawing and changes only how the result reaches the screen:

1. `CustomWatchface` draws itself into a **bitmap** instead of onto a watch face canvas.
2. That bitmap is published as an **image complication**.
3. A small Watch Face Format document, shipped with AAPS and installed by **Watch Face Push**, does
   nothing but show that complication full screen.

The wearer keeps their design, sends it the same way, and selects "AAPS" in the watch face list like
any other face.

### The one fact that shapes everything else

While the watch dozes, **our process is frozen** - measured, fifty-one seconds without a single log
line. Nothing of ours runs, so nothing of ours can update the picture. Anything that must stay
correct in always-on has to be drawn by the runtime, from the document.

---

## 2. How a frame is made

### 2.1 The render path

`CustomWatchface` gains entry points used only for drawing into a bitmap. They are **additive**: the
class still drives the real watch face on watches where it runs, and a wearer on that face must not
be able to tell this work happened (section 5).

- `prepareLayout` builds and measures the views once, before anything draws.
- `renderLayer` draws one layer. The face is cut where the **refresh rate** changes, not where the
  geometry suggests: the data, the clock text, the middle, the hands, the second hand.
- `setRenderInstant` draws for a chosen moment rather than for now.
- `setRenderAmbient` and `setRenderSeconds` say which mode, and whether the seconds appear. They are
  separate because the mode also chooses the simple always-on display.
- `styleId` changes when a new zip is applied, so cached layers can tell they belong to the old one.

`CwfRenderTarget` is the interface holding exactly these. It exists so the **order** of the calls can
be tested rather than remembered: `CwfFramePipelineTest` records what a real frame asks for and
asserts the sequence.

### 2.2 The pipeline

`CwfFramePipeline` draws the layers, keeps the slow ones, and blends them back in paint order. Only
the stale ones are redrawn - everything when the data changed or a new zip arrived, the minute layers
when the minute turned, the two clock layers on every frame.

`CwfCachePolicy` makes that decision and nothing else. It is pure and tested, because the decision
used to be spread across the pipeline, the updater and an event subscription, each right on its own
while the result was wrong. The rule that fixed it: **the cache carries what it was built from, and
is compared rather than notified.**

The frame is compressed with **WEBP lossless** and sent as bytes. Raw pixels across Binder are what
had the system killing the process.

### 2.3 Frames prepared in advance

The time is the one value known ahead. `CwfFrameQueue` draws the coming seconds while the watch is
idle, so a request is answered from stock.

Two rules hold it together, and each was paid for on a wrist:

- **A frame aims at the moment it will be shown, never at "now".** The producer continues the series
  after what is already prepared, and the window is capped at the horizon, so a full queue means
  nothing to do.
- **Time only moves forward.** A frame served for a later second must never be followed by one built
  for an earlier one. `take` returns the second it serves, because the caller cannot otherwise record
  what it is really showing.

`CwfMinuteFrame` holds the face drawn **without seconds**, refreshed when the minute turns. It
refuses to be shown in a different minute from the one it depicts: its seconds are absent, so nobody
sees those go stale, but the minute is drawn as a hand and as text.

### 2.4 Who asks for a new picture

`CwfComplicationUpdater` decides when, and lives outside the provider because a provider only exists
for as long as one request.

- It paces the clock to what the watch can actually draw.
- A **mode change** is refreshed at once, outside the coalescing queue: the process is being frozen
  as the watch dozes, so a refresh left pending often never went out.
- The always-on slot is asked on its own slower schedule - once a minute, which is as often as its
  picture can change.
- Both slots are refreshed on **every** mode change, both directions. A slot's tap action and its
  seconds are decided when its data is built, and that data outlives the mode it was built in.

---

## 3. The measurements that decided the design

From a Galaxy Watch 4 unless stated. Each one closed a question that would otherwise be reopened.

**Area governs the cost of drawing, not complexity.** A 400x400 bitmap costs about 30 ms whether
rotated or not; the same image at 120x120 costs 3.5 ms. Rotation is nearly free, SVG is cheap, text
is free.

**Weight and cadence are unrelated.** The most elaborate zip measured has the *cheapest* per-second
layer: its hands are small sprites, while a plain face drew them full screen.

**Blending is nearly free** - about 1 ms per full-screen layer, linear up to six. So cut the face
wherever the refresh rate changes, and stop counting layers.

**The compression is the largest single term**, not the drawing. WEBP lossless beats PNG on both time
and size: 82 ms and 45 kB against 126 ms and 68 kB at full screen. WEBP *lossy* is slower than
lossless on this hardware.

**Re-reading the values costs 77 ms**, and a clock tick has no reason to pay it.

Together these take a frame from about 340 ms to about 100 ms.

**Nothing of ours runs while the watch dozes** - fifty-one seconds of silence.
`setExactAndAllowWhileIdle` is throttled to about nine minutes in doze, so a local alarm cannot help.

**The runtime repaints once as the screen dims, then not again until the minute turns.** That one
repaint takes the picture it already holds. Ours arrives about 150 ms later, which is 150 ms too
late: our detection is only 27 ms behind the display state, so being quicker is not the answer.

**In always-on the picture only refreshes when new data arrives**, about every five minutes, because
that is what thaws our process. Confirmed over twenty minutes on a watch: the refreshes tracked the
glucose readings, ten seconds behind each, and never the minute.

**`UPDATE_PERIOD_SECONDS` is a wish.** A request of 60 s produced intervals of 1m35 to 6m19.

---

## 4. The document, and why it holds two pictures

The document is in `wear/watchfacepush/template/watchface.xml`. It draws almost nothing of its own -
it shows our complication - with three exceptions, each forced by the freeze.

**The always-on clock is drawn by the runtime.** A picture of ours would sit there with a stale
minute and a stopped second hand. The AM/PM marker comes from the platform's own data source, because
`TimeText` accepts hours, minutes and seconds and nothing else: a 12-hour watch showed "05:01" at
17:01 with no way to tell morning from evening.

**The always-on readouts** - glucose with its age, and the status line - are drawn by the runtime from
two small complications, for the same reason. The glucose value goes stale while we sleep, but its
"minutes ago" keeps counting, which is the half that matters.

**Two slots carry the same face, one with seconds and one without**, and the runtime swaps them at the
mode change. This is the only thing that removes the second hand at the instant the screen dims: no
delivery of ours can win that race (section 3).

Splitting the *seconds* into their own slot was considered and rejected, for two reasons that come
from the format rather than from taste. The `SECOND` view sits in the middle of the paint order, with
cover plates, the date and the hands drawn over it - and a slot draws wholly above or wholly below
another, never interleaved. And the digital seconds are characters inside the TIME view's own text,
so isolating them would mean moving the whole clock and making its glyphs line up between two
separately compressed images. Each slot therefore carries a **whole face**.

It costs one compression a minute, not a second face: everything expensive is shared, and a picture
without seconds only changes when the minute does.

### The one setting

Long-pressing the face offers one switch: whether always-on shows the runtime's plain clock, or the
wearer's own design, dimmed. **On by default**, so the dimmed view only appears when it is asked for.

The trade it offers is real and is stated in Appendix A: the wearer's design in always-on refreshes
only when a glucose reading arrives.

The **ambient alpha is a ceiling on the whole picture**. At 130 only 1.9% of the screen was above a
luminance of 100 and the design was unreadable in a dark room; at 240 it was indistinguishable from
awake. It sits at **200**. A screenshot understates all of these, because the panel is itself dimmed
to 21 of 255 while dozing. A side effect worth keeping: the closer this is to 255, the harder it
becomes to notice if the runtime ever fails to dim at all.

### Rules the format imposes

Learned by breaking something each time:

- a `ComplicationSlot` may not sit inside a `Condition` - put the condition around the content it
  draws instead;
- a `BooleanConfiguration` yields the **string** `TRUE`. Comparing it to 0 and 1 made both branches
  false and blanked a watch;
- `!=`, `<` and `<=` are not among the operators the format documents - use `!(a == b)`;
- a `<Template>` needs at least one `<Parameter>`; a literal goes directly inside `<Font>`;
- `displayName` must be a string resource. A literal is silently ignored;
- **`DefaultProviderPolicy` applies only to a slot the runtime has never bound.** Changing the
  provider of an existing `slotId` does nothing at all, silently. A new slot needs a fresh id;
- a slot's `BoundingBox` governs both what it draws and where it answers taps. A one-pixel box makes
  its content vanish.

**So a change to this document must be additive**: add the conditional element on top, never make the
existing rendering depend on a new expression. Then a wrong expression can only fail to add
something - it can never blank a watch somebody is wearing.

---

## 5. Rules that hold for every change here

Both were paid for in regressions.

### Rule 1 - a frame aims at the moment it will be shown, never at "now"

A frame takes 100 ms to over a second to produce, so one drawn for the present instant is already
late when it appears - every time. Every calculation that decides *which* second a frame depicts must
aim forward: the producer continues the series after the last second prepared, the request path aims
at `now + measured production time`, clamped so it can never go below the last second drawn, and the
watch face is then asked to draw *for that instant*.

### Rule 2 - the code-behind Custom watch face must not change behaviour

`CustomWatchface` still drives the real watch face people wear today. This port reuses it as a
renderer, and that reuse must be **additive**: new entry points are allowed, changes to what the live
face does are not.

A fix to shared behaviour is allowed only when the result is provably identical in the live path, and
only with the proof written down. Two changes so far qualified, and both are recorded in the code
beside them:

- **`updateSecondVisibility()`** read its own output, which made it a one-way latch: once always-on
  hid the seconds, waking could never bring them back. In the live face the fault was masked and
  never observable, because the style pass always follows it in the same call and rewrites the
  visibility from the zip's declaration. The render path calls it alone, which is why the fault only
  appeared there.
- **`updatePref()`** wrote the preference on every style application, feeding an event back into the
  refresh that had just written it. It now writes only on a real change: fewer redundant refreshes,
  not less information.

---

## 6. Tried and rejected

Kept so nobody spends the time again.

**Translating the zip into a Watch Face Format document.** The format is declarative and the Custom
watch face is code driven by a zip - dynamic colours, dynamic positions, conditional views. There is
no mapping.

**Generating a document per zip, on the watch.** Watch Face Push requires a *validation token* that
only Google's offline validator produces, and the validator is a desktop tool. A document built on
the watch cannot be signed for installation.

**Writing our own watch face runtime.** It would have to be a code-based watch face, which is exactly
what the firmware no longer offers.

**Two complications, an upper and a lower half**, each refreshing at its own rate. Blending measured
at about 1 ms a layer, so splitting across two slots bought nothing - and it cost two data reads, two
compressions, and the need to keep the halves agreeing with each other from outside. Done privately
inside one provider instead.

**The seconds in their own slot.** See section 4: the paint order and the digital seconds being
characters inside the time text both rule it out.

**A setting for the charging deck.** Built, verified three ways on an emulator, and removed: on a
Galaxy Watch 4 the wearer tried every combination and the simplified display never appeared on the
deck at all, because Wear OS puts its own charging screen there. A setting that does nothing is worse
than no setting, and it cost a second page in an editor where every page carries a preview.

**Handing off to one manufacturer's system UI** to open an editing session. It activated the watch
face as a side effect, so opening the AAPS settings menu silently switched the wearer from the Watch
Face Format face to the code-based one. Removed, along with every other trace of that manufacturer:
this port is for watches of every make.

**Showing the wearer's zip as the picture the watch face picker displays.** The system asks for that
picture **once** and keeps the answer - measured, it was not asked again after an app restart, a data
wipe, a re-push or a reboot. A personal picture there is guaranteed to become wrong for anyone who
changes design, with no way to correct it, so the app's own artwork is used in both places that show
a picture before the choice is made: the complication's preview, and the face's own image in the
picker. Their design is still shown where we control the drawing: the watch face itself, and the
Custom row in the settings menu.

---

## 7. Where this branch is going

`wear/cwf_wff` starts from `wear/CWF_Complication`, which is pull request #5047 against `dev`.

The order that avoids doing the migration twice:

1. **`CWF_Complication` catches up with `dev`**, including the Dagger to Metro migration that came
   with the kmp branch. That refreshes #5047 and is independent of everything below.
2. **This branch's history is simplified** - done: seventy-four commits became seven.
3. **When step 1 has landed**, `CWF_Complication` is merged *down* into this branch. The migration
   comes with it, and only this branch's own service registrations need re-expressing.
4. **When #5047 is merged**, this branch's pull request targets `dev` directly.

The one thing that would double the work is migrating this branch to Metro independently, instead of
letting step 3 carry it.

Beta testers do not need any of this: they need a signed APK, which is independent of the pull
request timing.

---

## 8. Open points

Everything still to do, in one place.

The spike numbers (S1 to S55) come from the working history that this file used to carry. They are
kept because they are referred to in commit messages and in conversation; the sections they pointed
at are gone, and what they concluded is in sections 1 to 6.

| | what | where the detail is | why it is still open |
|---|---|---|---|
| **S41** | The very slow frame when entering ambient, 2.3 to 3.9 s | §3 | Measured repeatedly, never investigated. Worth **re-measuring before investigating**: always-on now builds one picture a minute with no queue to fill, so it may already be gone |
| **S55** | Decide what to do about the always-on clock only refreshing at the pace of glucose readings, once beta testers have said whether it is acceptable | §9 | Two answers, both real: drop the dimmed view of the wearer's own design, which removes a good deal of code, or refresh every minute from the phone. Section 9 records the second one - what exists, what blocks it, and what has to be measured first. Nothing to build until there is feedback |
| **S52** | In always-on the picture is sometimes drawn at **full brightness for a significant time**, not dimmed, always without the second hand | §4 | The wearer has ruled out two explanations of mine. Not the light sensor: the two states alternate under unchanged lighting. And not the one-second flash at the moment of waking, which is a different thing they have also seen and which is explained. What is left is that the runtime sometimes does not apply the ambient variant at all, so the awake slot is drawn at alpha 255 - and in always-on that slot carries the secondless picture, which explains the missing second hand exactly. Not yet measured. The ambient alpha of 200 makes it visible again if it happens, which 240 did not |
| **S50** | Try `icon` on the setting and see whether the Samsung editor changes its presentation | §4 | The schema calls it *"an item of option list"*, which suggests it changes the presentation. Worth less now that there is only one setting left, so only one page - but the preview filling the middle of that page is still the complaint. Needs a Galaxy Watch 4, so only the owner can judge |
| **S11** | Can AAPS hold `GET_IS_FOR_SAFE_WATCH_FACE`, and is an allow-list honoured at all? | — | Never pursued. Cosmetic, and the platform may ignore it |
| **S12** | Any other documented way to keep a data source out of other watch faces' pickers | — | Same |
| — | **User-facing documentation** - a first draft is in Appendix A, at the end of this file. It has to be read by someone other than its author before it goes into the pull request or the wiki | Appendix A | Drafted, not reviewed |

### Not open, but not finished either

- **This document and `Complication_Libraries.md`** are updated as findings arrive. That item is
  never "done".
- **The `kmp` migration** is planned work rather than an open question - the order is in section 7.

### Everything else

Every other numbered point is closed - by an answer, by being built, by being removed after
measurement, or as overtaken. What they concluded is in sections 1 to 6; the reasoning that produced
them is in the git history of this file.

---

## 9. Keeping the dimmed design up to date every minute - a path, not a decision

Only worth building if beta testers say that a clock refreshing at the pace of glucose readings is
not acceptable. The other answer to that feedback is to drop the dimmed view of the wearer's own
design altogether, which removes a good deal of code. Both are live until there is feedback.

### What makes a refresh happen today

Verified end to end:

```
phone   EventAutosensCalculationFinished (≈ one per reading, 5 min)
      → dataHandlerMobile.resendData() → EventMobileToWear
      → DataLayerListenerServiceMobile.sendMessage(rxPath, …)

watch   the system starts DataLayerListenerServiceWear   ← this is what thaws our process
      → rxBus → DataHandlerWear → writes the repository
      → the updater sees the flow change → refresh after DATA_COALESCE_MS (5 s)
```

**The content of the message is irrelevant.** What wakes us is the system starting our listener
service, so any message would do - including one carrying nothing. That is why a tick from the phone
would work at all.

### What already exists on the phone, and what does not

- **A periodic loop exists**, in `AutomationRuntime`: `delay(1.minutes)` once, then `processActions()`
  every **150 seconds**. Two things to know before counting on it: the period is 150 s, not 60, and it
  returns early on a client (`if (!config.APS) return`), so it does not run at all on a phone that is
  not the master. A one-minute tick would need its own timer.
- **A round trip with the watch face already exists**: `ActionGetCustomWatchface` /
  `ActionSetCustomWatchface`, and the phone already keeps watch-face state of its own in preferences
  (`WearCwfWatchfaceName`, `WearCwfAuthorVersion`, `WearCwfFileName`). So holding a watch face setting
  on the phone is not a new idea here - the shape is in place.

### The obstacle that decides the design

The "simplified always-on" setting is a `UserConfiguration` **inside the watch face document**, read
by the runtime and never by us. The runtime is what switches between its own plain layer and our
dimmed picture, and it does that with a `Condition` on `[CONFIGURATION.simplifyAmbient]`.

Two consequences, and they are the whole difficulty:

- **We cannot read that setting.** So the phone cannot be told "the wearer wants the dimmed view"
  until the setting stops living in the document. The filter described below is impossible before
  that move, not after it.
- **If it moves to the phone, the document loses its switch.** A document can only test its own
  configurations and the platform's data sources. The switching would have to become ours: the
  always-on slot delivers nothing when the wearer wants the simple display, and the runtime's own
  layer stops being conditional and is simply drawn underneath, covered by our picture when there is
  one. That is a larger change than moving a setting, and it should be designed before it is started.

### The shape, if it is built

1. Move the setting from the document to the phone, beside the other Custom watch face state, and
   make the always-on slot deliver an empty complication when the simple display is wanted.
2. Tell the phone what the watch is doing: always-on on or off. The watch knows this from the display
   state, and the message channel already runs both ways.
3. Send an empty tick once a minute, only while **always-on is on and the simple display is off**.
   Both conditions matter: without them every wearer pays for a feature most of them have switched
   off, and in always-on with the simple display on our picture is not even drawn.

### What has to be measured before adopting it

The cost is not one wake. Each tick starts a service on the watch, thaws the process, rebuilds a
picture (200 to 800 ms of CPU measured on a Galaxy Watch 4), compresses it, and carries it over
Bluetooth - **five times more often than today, all night, on both devices**. The CPU part can be
estimated; the radio and the repeated thaw cannot be, from here. A night of battery on a real watch,
with and without the tick, is what should decide it.

### The alternative that costs nothing

The runtime keeps drawing while our process is frozen - that is exactly why the simple display stays
on time. So it could draw a small clock of its own **over** the dimmed design. The time would then be
right permanently, with no tick, no wake and no setting to move. The price is visual: it puts text on
the design the wearer chose in order to see it whole.

---

## Appendix A. For the wearer - draft text for the pull request or the wiki

Plain language, for someone managing diabetes rather than reading code. Kept here so it travels with
the branch; it belongs in the wiki or the pull request text, not in this file.

### What changed

Newer Samsung watches no longer run the Custom watch face the way older ones did. Your own design -
the zip you send from the phone - is now drawn by AAPS into a picture, and a small watch face
supplied with AAPS shows that picture. You keep your design. You set it up the same way, from the
phone, as before.

After updating AAPS, choose the **AAPS** watch face once from your watch's watch face list. It
appears there like any other.

### The always-on display

When you lower your wrist the screen dims but stays on. In that state **AAPS is not running** - the
watch freezes it to save the battery - so a picture from AAPS cannot be kept up to date. The watch
face therefore shows a simple clock of its own, with your glucose value and how old it is, plus your
basal, carbs and insulin on board.

That is the default, and it is the honest one: the reading may be old, and the "minutes ago" line
tells you how old.

**If you prefer to see your own design** in the always-on display, long-press the watch face, tap the
pencil, and turn **Simple always-on** off. You then see your design, dimmed. Two things to know
before you choose it:

- the clock and the values only refresh **when a new glucose reading arrives - about every five
  minutes**, and longer if readings stop. While the watch is asleep AAPS is frozen by the watch to
  save battery, so nothing of ours can update the picture in between. The simple display does not
  have this problem: its clock is drawn by the watch itself;
- there is no second hand in always-on, in either choice.

### What a tap does

Tapping the watch face opens the AAPS menu. On a sleeping watch the first tap only wakes the screen,
as it should - the menu is one tap away after that.

### What has not changed

Everything about how you build and send a watch face zip from the phone. The settings for the Custom
watch face are still in the AAPS menu on the watch, under **AAPS(Custom)**.

### If something looks wrong

- **The picture in the watch face list is not your design.** Expected. The watch asks for that
  picture once and keeps it, so it shows a fixed AAPS image rather than something that could become
  wrong. Your design is on the watch face itself.
- **The clock seems stuck in always-on.** Raise your wrist. While the watch is asleep AAPS is frozen,
  which is the trade described above.
- **This is not medical advice.** The values shown come from your existing setup; if a reading looks
  wrong, check it the way you normally would and speak to your care team.
