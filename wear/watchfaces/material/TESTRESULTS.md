# AAPS Material 1.11 verification

Historical verification date: 2026-10-15. Local environment: Windows, Python with Pillow 12.1.1. Current project: [BernhardRo/AndroidAPS, branch `dev`](https://github.com/BernhardRo/AndroidAPS/tree/dev). See [integration verification](../README.md) for the later import checks.

## Executed checks

Commands ran from the AndroidAPS checkout root:

| Check | Command | Result |
| --- | --- | --- |
| Build importable CWF asset and previews | `python wear/watchfaces/material/build_watchface.py` | Passed |
| Rebuild consistency | `python wear/watchfaces/material/build_watchface.py --check` | Passed; asset bytes match the sources |
| Static/design regression suite | `python -m unittest discover -s wear/watchfaces/material -p "test_*.py" -v` | 26 tests passed |
| Original face, Wear runtime and phone UI unchanged | `git diff --exit-code -- "plugins/configuration/src/main/assets/AAPS V2.zip" wear/src/main shared/impl/src/main plugins/sync/src/main ui/src/main` | Passed; no differences |

The suite checks:

- Live-field parity with the bundled AAPS V2 author version 1.0, excluding decorative free text and the added AM/PM indicator.
- CWF metadata version acceptance, ZIP structure, CRC, PNG preview decoding, deterministic packaging and asset/source equality.
- View/preference/font names against the checkout, preference-chain resolution, and label hiding.
- No live-value or preference overrides; no dynamic font steps masking native glucose/age/phone-battery warnings; no custom images masking native loop icons.
- 400 × 400 view bounds and the chart's four corners inside the trend ring's inner edge.
- Glucose at the top and the larger-font clock in the center; date to the right of the clock, below the rig battery; optional AM/PM in free space below the clock glyphs and left of the date; reading age beside glucose in the header.
- At least 4.5:1 contrast for primary/secondary card text, glucose range colors and clock text on both the hero surface and black/white background, and neutral battery/age text on black/white.
- Representative large, decimal, negative and detailed-IOB text widths and glyph heights using the bundled fonts, without shrinking the preview to conceal clipping; glucose, reading age, clock, day, German/English month and AM/PM sample glyph bounds inside the trend ring's inner edge.
- Day/month/AM-PM fields reserve full font line height plus four template pixels, including design checks at six zoom factors. This supplements visible-glyph tests; it is not an Android TextView test.
- Preview rendering for 96 combinations of dark/light, matching-divider on/off, normal/detailed IOB, 12/24-hour display and normal/mmol/high/low/stale/missing data, plus hidden-optional-fields cases with matching-divider on and off.
- Full-size native direction view, recognized `Arrow*.png` names, transparent white pixels for native tinting, highlighted segment at the correct angle, dim-only NONE resource and outward triangle geometry inside the display.
- Arc position, 40° sweep and triangle counts checked against the phone's `BgInfoSection.kt` for the seven distinguishable ordinary directions.
- Missing-reading previews cannot inherit a flat indicator; hiding the direction setting removes the ring for every sample trend in both themes.
- Direction-view preference mapping and native trend-resource selection checked against the checkout; circle stroke and arrowhead geometry stay inside the template's display boundary.
- Enlarged centerline diameter of 384 pixels with unchanged 5-pixel stroke and arrow tips at radius 197.
- Native `key_match_divider` mapping and its false default, all four theme/background combinations, and flat background SVG structure.
- Pixel samples from all seven decorative surfaces match black/white with the option on; glucose-header and outer-ring preview regions are unchanged between settings. Non-background text and palette rules stay identical and primary/secondary text retains at least 4.5:1 contrast on plain backgrounds.

All preview sheets were rebuilt. `preview-states.png` and `preview-surfaces.png` were visually reviewed for version 1.11, including the optional AM/PM sample and matching-divider off/on in both themes. `preview-trends.png` was regenerated but not separately visually reviewed for this version; ring resources are byte-identical to version 1.10. These are explicitly labeled as synthetic-data design previews. Android drawing/tinting and its exact font metrics are not covered by these checks.

Version 1.11 increases both date fields from 18 to 26 pixels tall, with the font unchanged at 18. Day starts at y=106 and month at y=132. Optional AM/PM moves below the clock and left of the date (x=250, y=144, width=34, height=22), keeping its 14-pixel font. Clock, glucose, ring, chart, remaining fields, palette, warning hooks and matching-divider behavior are unchanged. No native code or APK change is included. The clipping correction still needs user verification on the watch.

The user's earlier packages (`AAPS Material_1_10.zip`, `AAPS Material 1_9.zip`, and `AAPS Material - Kopie.zip`) were preserved. Read-only comparison against the user's 1.10 ZIP confirmed changes only to JSON and the package preview. JSON changes are confined to `metadata`, `day`, `month` and `timePeriod`. All six SVGs and eight ring PNGs remain byte-identical. The new package is written to the standard `AAPS Material.zip` path.

## User-reported clipped month

The user supplied `Screenshot_20261015-114617.png`, a Samsung phone overview image, and reported that the month was clipped on the watch. The image shows the date area; the user separately confirmed that the ring is present on the watch despite being absent from that phone image. No treatment or glucose interpretation was made from the screenshot.

Local measurements with the bundled font at size 18 give ascent 17 and descent 5, hence a 22-pixel line height. The earlier fields were only 18 pixels tall. The native renderer sets TextView size/font but does not disable its font padding. This supports insufficient field height as the likely cause; it is not a runtime measurement of the user's installed build. Earlier tests measured only visible glyph bounds and therefore did not catch this risk. The new regression test requires line-height reserve without reducing the date font.

History: version 1.1 swapped clock/glucose positions and used a 64-pixel central clock. Version 1.2 restored their original positions and made the header clock larger than version 1.0. Version 1.3 added the AAPS-4-style outer trend ring and inset clock/date and chart bounds for it. Version 1.4 used 58-pixel clock and 68-pixel glucose text. Version 1.5 increased clock text to 64 pixels and reduced glucose to 60. Version 1.6 stacked the date to the right of the clock, slightly moved the clock and placed optional AM/PM below it. Version 1.7 reduced glucose text to 54 pixels. Version 1.8 put glucose at the top and time in the center, with their helper fields moved accordingly. Version 1.9 enlarged the ring diameter from 380 to 384 and shortened arrowheads from 6 to 4 pixels, keeping tip radius 197. Version 1.10 connected matching-divider to decorative surfaces, preserving text and palette colors. The chart remains 216 × 86 template pixels. Eight transparent arrow resources and the separate native loop-status icon are retained, with no Wear/app code or transport change. Synthetic strike-through still follows the glucose view's center.

## User-reported missing ring: cause confirmed

On 2026-10-15 the user identified their watch as a **Samsung Galaxy Watch 4**, then confirmed: the ring is now visible, and **Trendpfeil anzeigen had been switched off**. This resolves the reported visibility problem for that earlier ring. No code fix or diagnosis ZIP was needed. Installed phone/Wear builds remain unknown, and this report does not establish all trend states or version 1.9's larger ring as verified.

Read-only code inspection confirmed:

- `CustomWatchface.kt`: `zoomFactor` uses display width / 400; the native direction view follows `PrefMap.SHOW_DIRECTION`, selects the named arrow resources and uses native BG tinting.
- `ResFileMap.kt` / `ZipWatchfaceFormat.kt`: all eight packaged PNG names are recognized by this checkout's importer.
- `BaseWatchFace.kt` / `SimpleUi.kt`: simplified UI draws a separate glucose/time display rather than the custom layout and its ring resources.
- Version 1.9 geometry: circle diameter 384 in a 400-pixel template, stroke outer radius 194.5, arrowhead vertex radius at most 197. Template geometry is not runtime evidence.

After confirming visibility, the user requested a slightly larger ring, added in version 1.9 and retained thereafter. In the later screenshot discussion, the user again confirmed that the ring is visible on the watch. No ring change was made for version 1.11. Native code and user settings remain untouched. The `1e-9` tolerance in geometry tests accounts for floating-point rounding; all 26 current tests passed.

## Package

- File: `plugins/configuration/src/main/assets/AAPS Material.zip`
- Size: 180,312 bytes
- SHA-256: `2aa57c3f42252433d5f7a08b9e99d846affbfddc59ea2b43416f39aeb5bae21c`

## Not verified

The agent performed no Android APK build, emulator/device rendering, on-device import, ambient-mode run, real data synchronization, touch interactions or battery-use measurement. No application was installed and no instrumented tests were run. The user's ring visibility confirmation is recorded above; it does not verify version 1.11, the corrected date fields, matching-divider switching or the complete on-watch checklist. The previews approximate Android text/icon/chart rendering and are not runtime evidence.

Before daily use, carry out the actual-watch checklist in `README.md`. Device-specific legacy-watchface compatibility and unusually long basal/rig-battery strings remain open checks. The package is not a standalone WFF APK and does not modify the Wear OS Watch Face Push module.

The baseline's triple-up/down transport ambiguity remains: both use `X`, which the existing CWF renderer resolves to the double-up resource. Therefore this asset does not establish parity with the phone's three-triangle indications. Check triple trends on the phone; see the limitation in `README.md`.
