# Bug: the IOB of the past changes when the time zone changes

Status: **open**. Found on 2026-10-04 while testing whether the IOB cache could be kept between
glucose values. Only a workaround is in place (see "What is done now").

## Summary

Insulin that was delivered does not change when the phone moves to another time zone. The IOB that
AAPS calculates for the past does change. The basal part of IOB is calculated against the basal
profile, and the profile is looked up with the **current** time zone, not with the zone that was in
force at that moment.

## How it happens

All timestamps are UTC milliseconds, so they do not change. The value calculated for them does:

1. A temporary basal counts as the **difference from the profile basal** ("net basal"). For every
   5 minute part of a TBR, `TB.iobCalc` (in `TemporaryBasalExtension.kt`) asks for
   `profile.getBasal(calcDate)`:
   - absolute TBR: `netBasalRate = rate - basalRate`
   - percent TBR: `netBasalRate = (rate - 100) / 100 * basalRate`
2. `ProfileSealed.getBasal(timestamp)` uses `MidnightUtils.secondsFromMidnight(timestamp)`, which
   converts the timestamp to a time of day with `TimeZone.currentSystemDefault()`.
3. After a zone change, the same past moment falls on another hour of the basal profile. Example:
   17:00 UTC was 19:00 in Prague (UTC+2) and is 18:00 in London (UTC+1).
4. So the profile basal of the past moves by the zone difference. `basal`, `tempBasalAbsolute` of a
   percent TBR (`TB.convertedToAbsolute`), `netbasalinsulin`, `basaliob` and `iob` all change. Bolus
   IOB does not change; it does not use the profile.

The same lookup is used by autosens (deviations are built from the basal IOB) and by
`IobCobCalculatorPlugin.getBasalData`, so those values change too.

## Evidence

Measured on a Pixel with a diagnostic "shadow" cache that kept every calculated value and compared
it with a fresh calculation of the same timestamp (local branch `iob-cache-shadow`, not merged).

Phone switched from Europe/Prague to Europe/London and back:

```
10/3 11:02  iob 0.922 -> 0.895, basaliob 0.194 -> 0.167, netbasalinsulin 2.43 -> 1.46
10/3 19:07  iob 1.456 -> 1.546, basaliob 0.735 -> 0.825
10/3 19:00  basal 0.9 -> 1.2 (getBasalData)
```

503 values differed after the two zone changes. In the same session about 62,000 other values were
compared after boluses and carbs dated in the past, deleted treatments, TBRs, extended boluses,
profile switches (also dated in the past) and different BG intervals: none of them differed. The
time zone is the only case found where the same timestamp gives another IOB.

## Why it matters

- After travelling, the IOB, COB and autosens of the last hours are calculated against a basal
  profile shifted by the zone difference. The loop doses from these values.
- The pump delivered the basal by its own clock, which was still in the old zone at that time. The
  value calculated before the change is the true history; the recalculated one is not.
- The size of the error grows with the zone difference and with how different the profile blocks
  are around the shifted hours.

## What is done now (workaround)

- Since 2021 the IOB cache (`iobTable`, `basalDataTable`) is cleared on every BG reload
  (`PrepareGraphDataRunner`, the `clearCacheAfterBgReload` call). This was probably not added for
  this bug, but it means every value is recalculated in the current zone within a minute. All
  values are then consistent, and all equally wrong for the past.
- `EventTimeZoneChanged` is sent by `TimeDateOrTZChangeReceiver` on a zone change, and
  `IobCobCalculatorPlugin` answers it with the same full reset as a configuration change
  (`resetDataAndRunCalculation`). Without this, keeping the IOB cache between glucose values would
  leave old values next to new ones.

Neither of them fixes the bug: after a zone change the past is calculated in the wrong zone.

## How it could be fixed

- Look the profile up with the UTC offset that was in force **at that moment**, not with the current
  zone. Most records already store it (`utcOffset` on `BS`, `TB`, `CA`, `EB`, ...). A time zone
  history (from the records, or from the `TIME_CHANGE`/time zone events the app sees) would give the
  offset for any past moment, including moments without a record.
- `MidnightUtils.secondsFromMidnight(timestamp)` is the one place all profile lookups go through, so
  an offset-aware variant there would cover the IOB, the basal data and autosens together.
- With that fixed, the same timestamp always gives the same value, and the reset on a zone change is
  no longer needed.

## Not affected

- Daylight saving time. The zone stays the same and its rules know the offset of every moment, so a
  past timestamp converts to the same time of day before and after the switch. (Expected, not yet
  tested on a device.)
- A manual clock change. Past timestamps do not move.

## Second finding: `getBasalData` caches a value that depends on the caller's profile

`IobCobCalculatorPlugin.getBasalData(profile, time)` calculates `basal = profile.getBasal(time)` and,
for a percent TBR, `tempBasalAbsolute` from the **profile the caller passes**, and stores the result in
`basalDataTable` under the time alone. Two callers passing different profiles for the same time get
the first caller's answer.

Seen once in the same test: after two overlapping profile switches (120 % started at 11:30, and an
80 % switch inserted later but dated 10:33 with 2 h duration), the stored value for 11:35 was
`basal 0.96` (80 %), the fresh one `1.44` (120 %, the switch active at that time).

Callers today:
- `TddCalculatorImpl.sumInterval` passes `profileFunction.getProfile(t)` for every step (correct).
- `OverviewDataCacheImpl` (basal graph) updates its `profile` only when it crosses a profile switch
  boundary. Most likely the source of the wrong value, not proven yet.
- `OverviewDataCacheImpl.updateTbrFromDatabase` and the widget ask for "now"; `roundUpTime` puts that
  key in the future, so it is never cached.

Today the clear on every BG reload limits the damage to one minute. If the cache is kept, either
`getBasalData` must look the profile up itself (`profileFunction.getProfile(time)`), or the key must
include the profile.

**Solved by removing the cache.** The overview basal graph and `TddCalculatorImpl` now read the
temporary basals of their whole range at once (`ProcessedTbrEbData.getTempBasalsIncludingConvertedExtended`)
and use the profile of each step. The callers of `getBasalData` left ask for "now", which was never
stored, so `basalDataTable` was removed.

## Related

- The IOB cache question: whether `iobTable` (and `basalDataTable`, now removed) can be kept between glucose values
  instead of being cleared on every BG reload (cleared since the "IobCobCalculator refactor" in
  April 2021, commit `36040b7ed2`). This bug is the one thing the shadow test found that the clear
  protects against.
