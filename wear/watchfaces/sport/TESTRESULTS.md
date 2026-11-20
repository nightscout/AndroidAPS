# AAPS Sport 1.14 verification

Historical verification date: 2026-10-16. Local environment: Windows, Python, Pillow 12.1.1 and resvg-py 0.5.0. Current project: [BernhardRo/AndroidAPS, branch `dev`](https://github.com/BernhardRo/AndroidAPS/tree/dev). See [integration verification](../README.md) for the later import checks.

## Executed checks

Run from the AndroidAPS checkout root:

| Check | Command / method | Result |
| --- | --- | --- |
| Build ZIP and previews | `python wear/watchfaces/sport/build_watchface.py` | Passed |
| Rebuild consistency | `python wear/watchfaces/sport/build_watchface.py --check` | Passed; ZIP matches sources |
| Static/design tests | `python -m unittest discover -s wear/watchfaces/sport -p "test_*.py" -v` | 45 tests passed |
| Complete ZIP transport budget | Compare final bytes with the 95,000-byte builder budget; test oversized-package rejection | Passed; 81,819 bytes |
| Tracked files unchanged | `git diff --exit-code HEAD -- .` | Passed; no tracked-file differences |
| Preserve existing Material sources, previews and existing ZIP assets | SHA-256 comparison with the pre-edit baseline of 37 existing files | Passed; none changed or removed |

The final suite passed after rebuilding. Packaged SVG artwork is rendered locally by resvg for the static/design checks. This is not an AndroidSVG runtime test.

## Change from 1.13: larger arrowheads with transparent edge room

- Increased triangle height from six to eight pixels and base from 18 to 22 pixels, retaining the one-pixel base inset. The tip now reaches radius 71 instead of 69; double-marker spacing follows the wider base. Ring radius/stroke, native direction center angles/counts, tinting, highlighted sweep and all live data remain unchanged.
- Expanded the transparent image/view from 140 × 140 to 146 × 146 and shifted its origin from x=62/y=27 to x=59/y=24. The visible ring remains centered at x=132/y=97 with radius 61 and stroke 6; the extra three pixels per side provide two pixels of geometric tip margin. Glucose text and all other field positions are unchanged.
- Existing tests now compare with 1.13-style triangles: area 88 instead of 54 square pixels, more rendered opaque area in all seven marker states, no opaque border clipping, full round-screen envelope clearance, unchanged six-pixel track width and native mapping. Cropping the new NONE resource by three pixels per side gives exactly the previous image, proving the plain ring pixels are unchanged apart from transparent padding.
- ZIP changes are limited to metadata, the transparent direction frame, thumbnail and eight resized trend PNGs. All 55 other artwork resources are byte-identical to 1.13, including every reservoir SVG and both icons.
- **1.14 has not been tested on the watch.** Verify larger single/double arrowheads and image placement on the actual display.

## History: 1.13 changes from 1.12

- Increased triangle height from four to six template pixels and base from 15.6 to 18 pixels. Added explicit `arrowHalfBase: 9` and `arrowInset: 1` artwork parameters; the builder places the base one pixel inward to keep the tip at radius 69, one pixel inside the existing 140 × 140 canvas. Double-marker spacing follows the new base width. Native direction center angles/counts and segment sweep remain unchanged.
- Ring stroke remains six pixels, radius 61, position/view size unchanged. The NONE/plain-circle PNG is byte-identical; no data source, preference, glucose font, other field or warning hook changed.
- Added geometric area, rendered visible-area and edge-clipping checks for all seven marker states, compared with locally regenerated 1.12-style markers. Each triangle's geometric area is 54 square pixels instead of 31.2. Tests also retain the six-pixel track-width check, native mapping, tintable white masks and full round-screen envelope clearance.
- ZIP comparison confirms changes only to version metadata, thumbnail and seven marker PNGs. All 56 other artwork resources are byte-identical, including NONE, every reservoir SVG, icons, background and empty image.
- **1.13 has not been tested on the watch.** Confirm the larger single/double markers and their edge clearance on the actual display.

## History: 1.12 changes from 1.11

- Increased trend stroke from four to six template pixels, for both the subdued circle and highlighted segment in all eight PNG masks. View size 140 × 140, centerline radius 61, position, 40-pixel glucose font, native tinting/selection, 40-degree segment sweep and triangle counts/height remain unchanged. Existing builder rules scale triangle bases and double-marker spacing with stroke width; the outer tip reaches radius 68, still two pixels inside the resource canvas.
- Added a raster width check: the NONE mask has a six-pixel midline cross-section, compared with four in a locally regenerated previous-stroke mask. Tests retain geometry, text clearance, round-screen envelope, white-mask tinting and native direction mapping checks.
- ZIP differences are limited to version metadata, thumbnail and the eight trend PNGs. All 55 other artwork resources are byte-identical, including every reservoir SVG, icons, background and empty image. All live-field JSON geometry and preferences remain unchanged.
- Pump-battery display was investigated but deferred by the user. Checked `EventData.Status`/`DataHandlerMobile.sendStatus` expose phone battery and rig/uploader status, not a dedicated pump battery; no app changes or substitute battery display were added.
- **1.12 has not been tested on the watch.** Local previews and prior ring visibility/loading confirmations do not verify this new stroke or all trend states.

## History: 1.11 changes from 1.10

- Moved only `RES / U` and the native reservoir amount 20 pixels right, from shared x=180 to x=200, matching the watch's horizontal center. Their left margins become 146/132; widths 108/136, top positions 323/342, fonts 12/20 and centered gravity are unchanged.
- Lower-right gauge, colors, native warning hooks, COB/IOB and all other fields remain unchanged. Tests check the shared screen center, unchanged vertical positions, fonts, widths, text/gauge clearance and visibility behavior.
- ZIP comparison confirms only JSON and thumbnail changed; all 63 artwork resources are byte-identical to 1.10. JSON differences are limited to metadata and the two reservoir text views.
- **1.11 has not been tested on the watch.** The local preview and tests are not proof of exact Android placement.

## History: 1.10 changes from 1.9

- Mirrored only the reservoir arc/track from lower left to lower right. The gauge view moves to x=192, y=192 with the same 208 × 208 size; SVG crop becomes `viewBox="192 192 208 208"`. Start remains 90 degrees; sweep becomes -90 degrees, with SVG sweep flag 0. Fill grows from bottom toward right rather than left.
- Radius 184, stroke 8, round caps, light-blue fill/dim track, 51 states, 4 U bins, 200 U full scale and uncapped native amount are unchanged. No reservoir heading, amount, COB, IOB or other text/icon position changed. JSON comparison with 1.9 is limited to metadata and `cover_chart`.
- ZIP comparison confirms only JSON, thumbnail and the 51 gauge SVGs changed. All 12 other artwork resources are byte-identical. The transparent empty PNG retains identical bytes despite the changed crop origin.
- Added tests for the correct side, fill direction, no old left-side arc, and rasterized clearance between the full right-side gauge and representative negative/detailed IOB and reservoir text. At 1x/3x, the tested glyphs clear the opaque gauge plus a one-pixel mask expansion. The long `-12.34` sample does not clear a two-pixel expansion at 3x; no wider margin or exact Android alignment is claimed. Other fields were not moved to hide this limitation.
- **1.10 has not been tested on the watch.** Verify actual IOB/gauge clearance and native SVG placement; the 1.6 loading confirmation does not verify this revision.

## History: 1.9 changes from 1.8

- User requested the reservoir bar and its text in the same light-blue insulin color as IOB/basal. Changed all 51 gauge SVG fills to `#65DEED`, the dim track to `#173238`, the `RES / U` heading to `#65DEED`, and the normal native reservoir amount via `reservoirColor` to `#65DEED`.
- COB text and wheat remain orange. Native warning/urgent amount colors still take priority; the reservoir text view has no fixed `fontColor`, and warning/urgent colors and thresholds are not overridden. The arc itself remains light blue at all levels.
- The user also requested COB farther right toward the center. COB icon/value move together eight pixels, from x=64/92 to x=72/100. Their four-pixel internal gap, fonts and vertical placement are retained. The full COB field still clears the IOB icon by four pixels; long native strings fit. IOB and all other field positions are unchanged.
- Fonts, native values, visibility preferences, 200 U scale, all step boundaries and gauge geometry are unchanged. Only metadata, `reservoirColor`, the reservoir heading and COB icon/value positions differ in JSON. ZIP comparison confirms only JSON, thumbnail and the 51 reservoir SVGs changed; all 12 other artwork resources are byte-identical to 1.8.
- Added a palette-consistency test and replaced the orange-specific pixel counter with a fill/track color-based counter. Every gauge image must still increase in visible filled area; exact SVG geometry/color checks remain in place.
- **1.9 has not been tested on the watch.** Previous loading confirmation is not confirmation of this color update, COB position or native warning behavior.

## History: 1.8 changes from 1.7

- User requested icons closer to the numbers. The large visible gap came from centering short numbers inside wide value fields. All three native total views (`cob2`, `iob1`, `iob2`) now use `gravity: left`; normal and detailed IOB still use the native total values.
- Icon positions and 24-pixel sizes are unchanged. Value fields move four pixels left to x=92/252, reducing the icon-to-field gap from eight to four pixels. Their widths and 32-pixel fonts are retained. Tests cover zero, short, long, negative and placeholder strings in both IOB modes; the modeled leading gap stays four pixels rather than growing with field space. Raster checks at 1x/3x allow up to three extra pixels of glyph side bearing.
- Native LEFT maps to `Gravity.START`, without vertical centering. Total fields move three pixels down to y=286 to preserve their previous total baseline in the Pillow model. The preview helper now respects horizontal gravity and top alignment rather than assuming all text is centered. Android font padding is still not verified by this model.
- Detailed IOB's offset changes to down 38/left 16, placing the 12-pixel breakdown at x=236, y=324 below the total under top alignment. Sample strings fit and retain vertical separation. Icons and the entire total row remain below the clock.
- Only JSON and thumbnail differ inside the ZIP. All 63 artwork resources, including the icons, are byte-identical to 1.7; all other fields, preferences, data, warning hooks and scale are unchanged. Archive remains 65 entries and below the transport budget.
- **1.8 has not been tested on the watch.** User confirmation of 1.6 loading does not confirm this spacing change.

## History: 1.7 changes from 1.6

- New user report: on the actual watch, the COB/IOB headings sit very close to the large time, almost overlapping. The user requested AAPS symbols before larger numbers instead of those headings. Earlier equal-coordinate/ink-gap checks did not verify the watch's full clock-to-heading layout.
- Replaced both headings with 24 × 24 SVG icons: orange wheat/carbs from AAPS `IcCarbs`, and turquoise syringe/insulin from `IcBolus`. The builder reads the existing Compose source path commands; tests compare every SVG command and operand with the source, and check the phone overview's `ElementTypeStyle` mapping. No app/icon source was edited.
- Blank `cob1` and `freetext4` text views carry existing preference-controlled drawable backgrounds. Icons follow the COB/IOB switches independently; no native numeric value is replaced with icon text. Icons are static identifiers, not new warning/blink logic.
- Both total fonts increase from 26 to 32 pixels. Native total fields begin at y=283 with height 44; the entire view is below the clock field ending at y=275. Tests require at least six physical pixels of view separation at six native zoom factors, not only separation of sample glyphs.
- Icons sit at x=64/224, y=293; each has an eight-pixel gap to its value field. Value centers are x=156/300, y=305. The COB value field is 120 pixels wide for long native strings, versus 88 for IOB. Tests check sample strings, matching numeral tops/baselines, icon/value centering, inter-group separation and icon clearance from the round reservoir arc. An initial design preview put the wheat too near the arc; final icons are smaller and inset.
- Detailed IOB retains the same 32-pixel total row; its native 12-pixel breakdown is moved down 26 and left 20 pixels from the base IOB2 field, centering it at x=280. The local preview helper now handles native step-based left offsets and TextView image backgrounds.
- Clock, date, basal/loop, reservoir text, 200 U scale, preferences and warning hooks are unchanged. All 61 original artwork resources are byte-identical to 1.6; only JSON and thumbnail changed, and two icon SVGs were added. Archive has 65 entries and remains below the transport budget.
- **1.7 has not been tested on the watch.** The 1.6 loading confirmation does not confirm this new layout, spacing, icon rendering or transfer.

## History: reported failure and user-confirmed 1.6 loading

- User report for 1.5: the watchface is present in the AAPS list, but selecting it shows no error and leaves the old watchface on the watch. This confirms list visibility, not successful delivery or activation.
- The 1.5 ZIP was locally readable with valid CRC, JSON and decodable PNGs. Investigation found that `WearViewModel.selectWatchface` sends its ZIP bytes via `EventMobileToWearWatchface`; `DataLayerListenerServiceMobile` passes the entire array to one `MessageClient.sendMessage` call. No chunking/Asset transport is used on that path.
- [Google's MessageClient reference](https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient) describes payloads as generally not larger than 100k. The old 519,551-byte package exceeded that guidance. This is a plausible transfer risk, not proof of the user's exact cause or a documented hard cutoff. No device log was provided.
- Replaced only the 51 reservoir PNGs with small SVGs. The checked `ZipWatchfaceFormat` recognizes SVG and stores arbitrary image resources under their extension-free name. `ResDataExtension` already parses them with AndroidSVG and renders them to a PictureDrawable. Native app/transport code is unchanged.
- Retained all 51 states in 4 U steps, the 0–200 U scale, 208 × 208 crop, 184-pixel arc radius, 8-pixel stroke, round caps and track/orange colors. Local tests parse every SVG path, verify endpoints and render the packaged SVGs through resvg for the existing pixel/design checks. Antialiasing may differ from the earlier raster artwork and from AndroidSVG.
- Thumbnail changed to a valid 400 × 400 indexed PNG with up to 256 colors. Full-color preview sheets remain available. All eight PNG direction masks, background and empty PNG are byte-identical to 1.5, preserving native bitmap tinting.
- JSON differs from 1.5 only in `metadata.author_version` (1.6). All field positions, fonts, colors, preferences and warning hooks are unchanged.
- Final ZIP is 76,356 bytes, **85.3% smaller**. Builder and tests enforce a conservative 95,000-byte budget and reject an oversized result. New local dependency resvg-py 0.5.0 is pinned with Pillow in `requirements.txt`.
- **User confirmation, 2026-10-16:** after trying the compact 1.6 package, the user replied **"funktioniert!"**. Basic loading/activation on the user's watch is therefore confirmed, and the reported loading failure is closed for this setup. The exact earlier failure cause remains unproven without logs. This broad confirmation does not verify all SVG arc levels, trend directions, live updates, precise spacing, warnings or ambient behavior.

## History: 1.5 changes from 1.4

- Corrected COB/IOB preview text placement to use a fixed font-line baseline rather than centering each string's ink. The `g` descender no longer shifts the COB digits above the IOB digits. This changes the Python preview helper, not the Android renderer or live data.
- Moved only the IOB heading (`freetext4`) from top 252 to 251 to account for the slash extending one template pixel below capital letters in the bundled font. The total fields keep their native coordinates and font sizes in both detail modes; labels and units are retained.
- At template scale, the local font model measures both visible heading-to-number gaps as 14.5 pixels for `18g`/`2.40`. Tests also cover `120g`/negative IOB and zero strings in both IOB modes. Raster checks at 1x and 3x confirm equal numeral tops and equal lower heading edges. These checks do not establish exact Android pixel alignment.
- Other layout, font sizes, colors, native values, preferences, gauge and trend masks are unchanged. ZIP comparison with 1.4 found changes only to JSON and the preview. JSON changes are limited to `metadata` and `freetext4`. All 61 artwork resources remain byte-identical.

## History: 1.4 changes from 1.3

- Day/month moved from the bottom strip to a vertical stack left of the clock, centered at x=50. Day top is 189 and month top is 217; the stack's vertical center matches the clock at y=217. Both retain 18-pixel text and 28-pixel field height. Date still follows the saved date preference, whose native default is off.
- Reservoir heading/amount moved 18 pixels down and 40 pixels inward to clear the round arc. Both share x=180; heading top is 323 and amount top is 342. Fonts remain 12/20 pixels.
- Clock size and position, all other fields, colors, preference chains, 200 U gauge and trend masks are unchanged.
- ZIP comparison with 1.3 found changes only to `CustomWatchface.json` and `CustomWatchface.png`. Changed JSON sections are limited to `metadata`, `day`, `month`, `freetext3` and `reservoir`. All 61 artwork resources remain byte-identical.

## History: 1.3 changes from 1.2

- Glucose/ring/unit/age cluster moved left to x=132 center, without changing sizes or masks. Basal heading/value/loop share the mirrored x=268 axis. Loop is directly below basal, with the native icon and warning hook unchanged.
- Basal heading reduced to 12 pixels like `RES / U`; native basal value reduced to 20 like the reservoir amount. Basal and loop remain separately controlled by existing preferences.
- COB/IOB columns mirror each other at x=120/280. Headings are 14 pixels; total values are 26, with matching view dimensions and row positions. COB heading/value are orange; IOB heading/total are turquoise.
- A separate preference-controlled `freetext4` keeps the IOB heading stable in both native detail modes. Normal IOB uses native `iob2` for the total. Detailed IOB uses native `iob1` for the same-size total on the same row, and native `iob2` for the 12-pixel breakdown below. No live number is replaced by a decorative string.
- Day/month strip is centered on the canvas at the bottom. Reservoir number, gauge geometry and 200 U scale, main clock, palettes and no-chart/no-battery scope are unchanged.
- Removed background tick marks that would otherwise appear inside the relocated glucose ring.
- Read-only ZIP comparison with 1.2 found changes only to `CustomWatchface.json`, `CustomWatchface.png` and `Background.png`. All 51 gauge images, eight trend masks and the empty image remain byte-identical. The archive still has 63 entries.
- No application code, API, treatment, alert, warning threshold or data transport was changed.

## History: 1.2 changes from 1.1

- Reservoir amount font reduced from 26 to 20 template pixels.
- COB value font increased from 22 to 26. Its field height increases from 32 to 38, with the top moved from 277 to 274 to keep the same center at (112, 293).
- Reservoir value center remains (140, 343). Labels, all other fields, colors, preference chains and the 200 U gauge are unchanged.
- Read-only comparison with the pre-edit 1.1 ZIP confirms changes only to JSON and the package preview. JSON changes are confined to `metadata`, `cob2` and `reservoir`; all 61 artwork resources are byte-identical.

## History: 1.1 changes from 1.0

- Removed both static COB/IOB quarters. Added one orange lower-left reservoir gauge, full at the user-selected 200 U, using 51 images in 4 U steps.
- Added the native reservoir amount with no text/value cap. Native reservoir warning/urgent text hooks remain enabled; only the normal reservoir text color is set to orange.
- Upper right now shows native current-basal text in turquoise, including temporary-basal percentages. It is not a numeric arc source.
- COB/IOB remain as compact columns below the large clock, including native detailed IOB. Date and loop were repositioned below them; the small brand and fixed loop label were removed for space.
- Time, glucose/trend/age geometry, native glucose palette and no-chart/no-battery scope are unchanged.
- No application code, API, treatment, alert, warning threshold or data transport was changed.

## Test coverage

- Strict JSON parsing, no duplicate keys, separate face identity and compatible CWF major version in the checked source.
- Existing native view keys, JSON fields, font names, resource names and preference keys; no new app fields or API extensions.
- No preference metadata overrides, seconds, history chart or substituted battery display.
- Native live values, direction selection and glucose/stale/reservoir/loop-warning hooks are not replaced with sample text or custom warning logic.
- Reservoir uses the existing numeric `ValueMap.RESERVOIR` source. Its lower-right light-blue quarter-circle has a 200 U display maximum. SVG viewport dimensions/origin, negative sweep/direction flag, all 51 paths, colors, caps and increasing locally rendered visible arc area were checked. Heading/normal amount/gauge color must match IOB/basal, while COB remains orange and native warning colors are not overridden.
- Native image-index formula modeled locally, including its `maxData * 0.9999` boundary clamp and `stepDraw = dynDrawable.size - 1`. With one invalid entry and 51 images, `maxData=204` produces 4 U bins and selects the full image only from 200 U. Tests cover 0–500 U in quarter-unit samples, fractional boundaries, negative values, values above full scale and invalid selection. This is source-based modeling, not an executed Kotlin test.
- Above-full-scale previews keep identical arc pixels while changing the uncapped amount string. The native unknown value (zero plus empty amount string) is not replaced by a fabricated `0 U` reading.
- Gauge/amount/label follow reservoir visibility, not COB or IOB. Basal heading follows native basal visibility. COB and IOB icon backgrounds follow their native visibility settings independently, in both IOB modes. Units and normal/detailed IOB remain supported.
- Bounds, full bundled-font line height plus four template pixels of reserve, and checks at six zoom factors. This includes date fields rather than testing visible glyph bounds alone.
- Representative time, German/English month, decimal-comma/point glucose, negative IOB, detailed IOB, larger COB, native reservoir and basal rate/percentage strings. Text fits its field. The earlier conservative radius-178 lower-left bounds remain; other areas have a radius-189 check. The new right-side gauge clearance is checked separately with rasterized glyphs and a one-pixel expanded gauge mask. Glucose/age samples also stay within their own trend ring.
- Space between main text groups; no preview auto-shrinking to conceal field-size issues.
- Matching 32-pixel COB/IOB fonts and numeric rows, equal icon sizes/four-pixel field gaps and colors; smaller equal-sized basal/reservoir values and headings. Native IOB total remains 32 pixels in both modes. Fixed preview baselines, left/top alignment and rasterized numeral edges are checked. Full native clock/row view separation is checked at six zoom factors. Icons are compared with original AAPS paths and checked for visibility, arc clearance and icon/value-field separation. Short/long/negative/placeholder totals retain a close leading gap at 1x/3x instead of being centered within the fields.
- Mirrored glucose and basal/loop axes, basal/loop vertical order, independent visibility settings and full trend-ring envelope clearance from the round display edge. Background pixels inside the glucose ring are black, not decorative tick marks.
- Date stack left of the clock, unchanged date fonts/line reserve, shared date axis, vertical centering and at least 12 pixels of visible separation from representative clock strings. Date visibility does not change clock pixels. Lower reservoir text is centered at x=200 with unchanged size/height; clearance within radius 178 is checked explicitly.
- All configured text and glucose colors have at least 4.5:1 contrast against black. Decorative ticks and subdued trend tracks are not text and are not covered by this claim.
- Six-pixel ring stroke with unchanged centerline radius/screen center and a larger transparent image frame; raster comparison with the old four-pixel track, full marker envelope and text clearance. Eight-by-22-pixel triangles are checked for geometric/rendered area, two-pixel canvas margin and no opaque edge clipping. The cropped NONE image must match the previous plain-ring pixels exactly. Seven ordinary native directions and NONE, native resource names, transparent white masks, highlight positions and arrow counts remain checked. Full triple-up/down parity is not claimed.
- 24 preview combinations of six data states × normal/detailed IOB × 12/24-hour display, individual visibility toggles and all optional fields off. Additional previews include negative IOB, a long COB string and a temporary-basal percentage. Reservoir sheet covers unavailable/zero, 40, 100, 160, 200 and 250 U.
- Deterministic ZIP bytes, exact root entries, CRC, decodable PNGs, parsable/renderable SVGs, extension-free preference-resource references and source/asset consistency. Complete-package size, oversized-package rejection, native source support and indexed-thumbnail format are checked.

## Visual review

Final indexed `CustomWatchface.png` and full-color `preview-trends.png` were read as images for 1.14. Review checked the further enlarged single/double arrowheads in all seven marker states, the retained NONE circle/ring position, text space, centered reservoir text and unchanged lower-right arc/COB/IOB layout. State/reservoir sheets were rebuilt but not separately visually reviewed for this revision; tests cover optional visibility, other data states and text clearance.

The images are **synthetic design previews**, not device screenshots. They use the packaged arc images and locally model native selection. Basic loading/activation of 1.6 is user-confirmed, not independently tested by the agent. Runtime text placement, exact native loop icons, warning colors/threshold choices, stale styling, tinting, memory use and refresh behavior have not been checked individually on Android. The normal preview shows date enabled; the ZIP does not change the user's saved date setting.

The checked phone source uses a numeric zero and an empty reservoir string for unavailable data. The existing CWF numeric source cannot tell this apart from a real zero. The face preserves the native amount and shows no bright fill for that input; a dim track/blank amount is not a confirmed empty-pump diagnosis. Basal strings are taken as-is, not parsed or converted.

## Scope and artifact

Only `wear/watchfaces/sport/` JSON version/direction frame, artwork marker geometry/canvas size, tests, docs and generated previews, plus `plugins/configuration/src/main/assets/AAPS Sport.zip`, were updated for 1.14. Builder and dependencies are unchanged. All existing Material files and the user's other renamed/copied packages were preserved, verified against a new pre-edit hash baseline of 37 files. The previous 1.13 Sport ZIP was backed up outside the checkout in the approved OpenCode temp directory; earlier backups were not changed. No app code, original AAPS icon source, transport, glucose interpretation, treatment or alarm logic was changed. Nothing was committed, pushed or installed on a device.

- Path: `plugins/configuration/src/main/assets/AAPS Sport.zip`
- Size: 81,819 bytes; 65 ZIP entries (53 SVG, 11 PNG, one JSON)
- SHA-256: `0896647474cdd8dc83232646e6c50cf343b9c6e703e52fe42e4a21d93eec8ed7`

No Android APK build, emulator/device run, instrumented test, installation, ambient test or battery measurement was performed. The installed phone/Wear commit remains unknown. Previous Material ring visibility reports are not runtime verification of this new face. User verification on the watch remains required.
