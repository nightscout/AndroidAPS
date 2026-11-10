# AAPS Material (1.11)

Integrated into this AndroidAPS 4 checkout; see [integration and current verification](../README.md). The source-checkout notes below document the original design and its history.

A separate **Custom Watchface** for the Wear target of [BernhardRo/AndroidAPS, branch `dev`](https://github.com/BernhardRo/AndroidAPS/tree/dev).

The layout takes its information set from the bundled **AAPS V2 (1.0)** by Andrew Warrington, adapted to CWF by Philoul. The original ZIP is not changed.

## Design

Material 3 Expressive-inspired styling: a black OLED background, mint accent, rounded tonal surfaces, a large central clock, and clear type hierarchy. A light variant follows the existing dark-mode setting. The existing Wear fonts are reused; no font download or new Android dependency is required.

- Top: glucose with reading age to its right, and phone/rig batteries with distinct icons.
- Outer edge: live glucose trend as an AAPS-4-style full circle, bright arc and outward arrowheads.
- Center: time, optional day/month stacked to its right, optional AM/PM below the clock, and the separate existing colored loop icon with loop age.
- Chips: delta and average delta.
- Cards: COB, basal rate/BGI, and IOB, including the detailed IOB option.
- Bottom: the existing live chart, including its configured history, target lines, treatments and basal overlay.

The chart is smaller than in AAPS V2; its complete rectangle stays inside a round screen. No chart data or history settings are changed. The template uses the same 400 × 400 coordinate space as AAPS V2 and the renderer scales it to the display width.

Version 1.11 gives day and month taller text fields: 26 instead of 18 template pixels, while keeping their 18-pixel font. The day/month spacing is adjusted, and optional AM/PM has a 22-pixel-tall field below the clock, to the left of the date. This addresses the user's report of a clipped month on the Galaxy Watch 4. The new test reserves a full font line plus extra space instead of checking visible glyphs alone; actual Android rendering still needs verification.

Glucose remains at the top with 54-pixel text and time in the center with 64-pixel text. The version 1.9 ring (diameter 384, stroke 5, tips at radius 197), all other fields, warning colors and the version 1.10 matching-divider support are unchanged.

### Matching divider / Querbalken farbgleich

In the watch's **AAPS(Custom)** settings:

- **Off:** keep the existing colored Material surfaces behind time, delta/average delta, COB/basal/IOB and the chart.
- **On:** match those surfaces to the overall background: black in dark mode, white in light mode.

This changes only the decorative background image. Text, live values, warning colors, trend ring, loop icons and chart data remain unchanged. Chart curves, grid, treatments and basal overlays keep their existing colors. Your saved setting is used; the ZIP does not turn the setting on or off. With the native default of off, the earlier Material look is preserved.

The face uses the native `key_match_divider` preference to select one of four SVG backgrounds through its existing dynamic-preference support. No APK or native renderer change is needed. `preview-surfaces.png` compares both settings in dark and light modes. Its images use synthetic data, not watch screenshots. On-watch switching still needs verification.

### Trend ring

The geometry follows `ui/.../compose/overview/BgInfoSection.kt`: a 30%-opacity full circle, a bright 40° arc, and outward triangles at the actual trend position. Flat is right/3 o'clock; rising is upper-right or top; falling is lower-right or bottom. Double-up/down use two triangles. The ring follows the existing glucose range color, **not** the loop status. Reading age and stale-value strike-through remain visible independently.

The eight generated `Arrow*.png` files use white pixels and transparency. The existing CWF `TrendArrowMap` selects the appropriate file and applies its native glucose-color filter. This preserves normal Wear updates without an APK rebuild, protocol change or new native watchface code. Disabling the Wear direction-arrow setting hides the whole ring. Unknown/NONE uses a dim circle without a bright arc or arrowhead, not a fabricated flat arrow.

**User confirmation, 2026-10-15:** the ring became visible on the user's **Samsung Galaxy Watch 4** after enabling **Trendpfeil anzeigen**. That setting was the cause of the reported missing ring, not a demonstrated size or resource-loading fault. This confirms visibility of the earlier ring only; version 1.9's larger ring and all individual trend states still need on-watch verification.

### If the ring is missing on the watch

In the 400-pixel template, the version 1.9 circle is 384 pixels across, the stroke outer radius is 194.5, and the arrowhead vertices stay inside radius 197. The PNGs are recognized by this checkout's importer. Static checks are not a substitute for checking the installed phone/Wear version and the watch display.

1. In the watch's AAPS display settings, enable **Show Direction Arrow** (**Trendpfeil anzeigen**). The ring is the native direction view; this setting hides or shows the whole ring. This face does not override your setting.
2. Check with the watch awake, not charging, and with simplified UI off for the test. `SimpleUi` draws its own glucose/time display instead of the custom layout and does not use the ring PNGs.
3. Replace the ZIP in `exports` and explicitly load **AAPS Material (1.11)** again; select **AAPS(Custom)** on the watch. The selection image is synthetic and is not proof that its resources rendered on the watch. The user also reported that the ring is visible on the watch even when absent from the Samsung phone overview image; that image alone does not establish a missing ring.
4. If still absent, provide the watch model, phone/Wear AAPS versions, a photo of the active face, and whether any small trend arrow appears. A small arrow would suggest the native fallback icon rather than the custom ring resource. No native renderer change is included in this package.

**Existing Wear limitation:** this branch transmits both triple-up and triple-down with the same `X` symbol. The existing CWF renderer maps that symbol to its double-up resource. An asset-only face cannot distinguish those triple directions or match the phone's three-triangle display; this existing ambiguity is not fixed here. Use the phone display to check triple trends. The ring matches the seven distinct ordinary directions supported by the renderer, not all phone trend states.

All live text comes from the existing AAPS fields. The face does not set units, thresholds, treatment values or user preferences. Optional fields still follow the Wear display settings. Rig battery, BGI and date are off by default in the existing renderer; enable them there if wanted. Empty tonal surfaces may remain when their fields are hidden.

The native stale-value strike-through, reading-age warning, low-phone-battery warning and grey/green/red loop icons remain under the existing renderer's control. The glucose range colors change visually, not semantically or numerically. Static theme steps use the current calendar day as `valueKey`: unlike the renderer's zero-range `NONE` value, it selects the single style step even when glucose is missing.

## Files and build

Editable sources are `CustomWatchface.json`, the six SVGs and the trend-ring geometry in `build_watchface.py`. The generated package is:

`plugins/configuration/src/androidMain/assets/AAPS Material.zip`

Run from the AndroidAPS checkout root with Python and Pillow installed:

```powershell
python -m pip install -r wear/watchfaces/material/requirements.txt
python wear/watchfaces/material/build_watchface.py
python wear/watchfaces/material/build_watchface.py --check
python -m unittest discover -s wear/watchfaces/material -p "test_*.py" -v
```

The builder creates a deterministic ZIP with the importer-required root entries `CustomWatchface.json` and `CustomWatchface.png`, plus the six SVGs and eight generated trend PNGs. It also writes `CustomWatchface.png`, `preview-states.png`, `preview-trends.png` and `preview-surfaces.png` here. The test suite uses the unchanged bundled AAPS V2 ZIP and the checkout's view/preference definitions to check the asset contract, compares ring positions/counts with the phone's AAPS 4 implementation, and checks all four matching-divider/theme combinations.

**The previews use synthetic data and are not screenshots from an Android watch.** They use the source layout, bundled fonts and packaged trend-ring masks, but Android text metrics, loop icons and chart drawing are approximations. Do not use their sample readings for treatment decisions.

## Use on the watch

1. Keep `AAPS Material.zip` zipped and copy it into the `exports` folder of the AAPS directory selected on the phone.
2. In the phone's AAPS Wear screen, tap **Load Watchface** (**Watchface laden** in German) and select **AAPS Material (1.11)**. The selection sends it to the watch. To update an earlier copy, replace its ZIP in `exports` and import this version again. The button is shown only while AAPS reports a connected watch.
3. Select the existing **AAPS(Custom)** watchface on the watch if needed, and adjust its display settings.

No APK rebuild is needed for this import path when the installed version supports the same CWF format. The ZIP is also included as a phone-app asset for future builds. Note: `FileListProviderImpl` loads bundled ZIPs only when no valid ZIPs exist in `exports`; adding an asset alone does not add it to an already populated import list. Copy the new ZIP to `exports` explicitly.

This package uses the same legacy custom-watchface service as AAPS V2. It is **not** a new standalone Watch Face Format (WFF) APK and does not change `wear:watchfacepush`. Compatibility on watches that reject legacy faces is not established by these asset checks.

## Verification before daily use

The automated checks cover field parity, resource names, preference chains, text samples, round-screen chart bounds, palette contrast and archive consistency. They are static/design checks, not Android execution tests.

On the actual watch, verify:

- Round/square layout and readable text at its actual size; localized month and decimal formats. Check that the full day/month is visible, especially `OKT`/`OCT` and longer abbreviations, and that optional AM/PM stays clear of clock/date/delta text.
- mg/dL or mmol/L as configured, 12/24-hour time, date and every optional field toggle.
- Toggle **Querbalken farbgleich** in both dark and light modes: only the decorative surfaces should change; values, warnings and ring must remain visible. Check that the choice survives changing watch mode or reselecting the face.
- Normal/detailed IOB, larger and negative IOB values, long temporary-basal/rig-battery strings.
- High/low glucose, missing data, old readings, low phone battery and all loop states.
- Ring direction against the phone for flat, diagonal, up/down and double up/down; no highlighted direction for NONE; visibility toggle and light/dark tinting. Check the triple-trend limitation above.
- Chart history/treatments/basal settings, taps and the existing chart/action shortcuts.
- Ambient mode and the existing simplified-UI option; this package adds no new ambient-mode policy.

No device installation, instrumented test or treatment-logic change is part of this work. See `TESTRESULTS.md` for the local verification record.

Design reference: [Wear OS Material 3 Expressive color guidance](https://developer.android.com/design/ui/wear/guides/styles/color).
