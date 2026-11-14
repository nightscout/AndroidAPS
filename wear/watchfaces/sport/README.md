# AAPS Sport (1.14)

Integrated into this AndroidAPS 4 checkout; see [integration and current verification](../README.md). The source-checkout notes below document the original design and its history.

A separate, importable **Custom Watchface (CWF)** for [BernhardRo/AndroidAPS, branch `dev`](https://github.com/BernhardRo/AndroidAPS/tree/dev). The installed phone/Wear version is still unknown.

No application code, treatment logic, alerts, data transport, AAPS V2 or AAPS Material files were changed. This is not a standalone WFF APK.

## Design

Black background, large bold digital time, light-blue reservoir/basal/IOB, orange COB and small instrument-style edge marks. The 400 × 400 template has:

Version 1.14 enlarges the **trend triangles again: eight-pixel height and 22-pixel base**, from six and 18 pixels. A larger transparent 146 × 146 canvas gives the tips room, without changing the visible ring's radius, six-pixel stroke or screen center. Its view moves three pixels up/left only to compensate for the three-pixel transparent border added on each side. Glucose text and all other fields remain unchanged from 1.13. The package remains compact at **81,819 bytes**. Loading of 1.6 was user-confirmed; the larger markers still need watch confirmation.

- **Time:** central, 92-pixel text, native 12/24-hour format; optional AM/PM below it. No seconds.
- **Glucose:** upper left, inside a 146 × 146 transparent trend view at x=59, y=24. The visible ring stays centered at x=132, y=97 with 61-pixel radius. Subdued circle and highlighted segment retain a six-pixel stroke, native direction selection and glucose-color tinting. Glucose text remains 40 pixels. Trend triangles have eight-pixel height and 22-pixel base. A one-pixel base inset puts tips at radius 71, two pixels inside the larger canvas; double-marker spacing follows the wider base. Direction center angles, counts and 40-degree highlight sweep are unchanged. Units follow the existing units setting. Reading age sits inside the ring below the value. Background ticks inside the relocated ring were removed.
- **Basal:** upper right, turquoise, with the loop icon directly below it on the same center line. Basal uses AAPS's native current-basal text: a rate with units or a temporary-basal percentage. It updates as text, not as an arc; no parsing or new basal calculation is used. Basal and loop retain separate visibility settings.
- **COB/IOB:** matching 32-pixel, left-aligned values with 24 × 24 icons to their left and an equal four-pixel icon-to-value-field gap. Value fields start at x=100/252, y=286, fully below the clock field ending at y=275. Icons sit at x=72/224, y=293. The COB field is wider (120 pixels versus 88 for IOB) to allow native strings such as `120(40)g` while keeping both groups inside the round display and clear of the reservoir arc. Four pixels separate the full COB field from the IOB icon. The leading gap no longer grows for short strings; glyph side bearings add a little blank space. Native `left` gravity also top-aligns text, so the fields sit three pixels lower to preserve the total baseline in the local font model. COB stays orange and IOB turquoise. There is no `COB` or `IOB / U` heading. Both totals remain native, in both IOB modes. Detailed IOB keeps its native 12-pixel breakdown below the total, left-aligned at x=236, y=324. Preview baselines keep the `g` descender from shifting the digits upward; Android font padding can differ.
- **Reservoir:** light-blue quarter-circle gauge at the lower right, growing from bottom to right. The native remaining-amount string and `RES / U` heading share the watch's horizontal center at x=200. Their vertical placement and 20/12-pixel fonts are unchanged. Native reservoir warning/urgent text colors still take priority over the normal light-blue text.
- **Date:** day above abbreviated month, stacked to the left of the clock. Both retain 18-pixel text and 28-pixel field height to allow line-height space. The stack is vertically centered on the clock, without shrinking or moving the time.
- **Loop:** native green/red/grey loop icon and loop age, now grouped under basal at the upper right. This is not a separate claim about loop mode, connectivity or delivery state. There is no extra fixed loop label.

### AAPS icon sources

The builder reads the actual phone overview icon geometry from `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/icons/IcCarbs.kt` and `IcBolus.kt`. `ElementTypeStyle.kt` maps COB to `IcCarbs` and insulin/IOB to `IcBolus`. Paths are converted to `CobIcon.svg`/`IobIcon.svg`; only size and fill color change. No app source is edited. These are static field identifiers, not new alerts, buttons or copies of the phone chip's blinking behavior.

Existing text views `cob1` and `freetext4` act as blank icon containers, using native preference-controlled backgrounds. Each icon follows its own COB/IOB visibility setting; native total values are not replaced or parsed.

### Reservoir scale: full at 200 U

The user selected a **0–200 U display scale**. The light-blue arc grows from the bottom toward the right edge. It is half full at 100 U, full from exactly 200 U, and stays full above 200 U. The native amount text is **not capped**: for example, 250 U still reads 250 U.

The existing CWF image-step mechanism selects **51 arc images in 4 U increments**, not a smooth animation. Intermediate values round down for the arc only; the native numeric string keeps its own format. This is a display scale, not a new treatment target, alarm threshold or assertion about pump capacity.

`cover_chart` carries the gauge image, despite its native view name; the actual history `chart` is gone. The gauge follows `key_show_reservoir_level`, independently of COB/IOB. Each gauge SVG has a 208 × 208 viewport over the lower-right crop (`viewBox="192 192 208 208"`), placed at x=192, y=192. A negative 90-degree sweep and SVG sweep flag 0 mirror the arc, without mirroring text or icons. The checked native loader already supports SVG resources and resolves dynamic image references by resource name without the extension. No app extension is needed.

Implementation detail: the renderer uses an exclusive upper bound for its image bins. With `invalidImage` plus 51 numbered images, `minData: 0` and `maxData: 204` give 4 U bins. The last bin starts at exactly 200 U. **204 is a lookup bound, not the full-scale value.** Tests cover every boundary and values above the scale.

The checked phone code sends `reservoir=0` and an empty native string when the amount is unavailable; a real zero also has no positive-amount string. The face preserves that ambiguity: only the dim track is shown, with no fabricated `0 U` value. A blank amount/dim track is not proof that the pump is empty. This ZIP cannot add a separate unknown/stale reservoir state that is absent from the source data.

### Agreed limits

There is **no history chart and no battery or step display**. The checked renderer cannot use COB, IOB or basal rate as numeric gauge sources, and exposes no watch-battery or step-count source. The static COB/IOB quarters from version 1.0 were removed rather than implying that those values control their lengths. Phone battery was not substituted.

The checked phone-to-Wear status also has no dedicated pump-battery value. Its existing battery fields refer to the phone and Nightscout rig/uploader, not the pump. A real pump-battery bar would require app/transport/renderer changes; the user chose to leave this out rather than display a different device's battery.

The background is always black, including with the existing light-mode option. `Querbalken farbgleich` has no separate effect: this face has no contrasting status-bar surfaces to match. User settings are not overridden.

### Trend ring and warnings

Enable **Trendpfeil anzeigen** on the watch to show the ring. It belongs to the native `direction` view, not the loop-status icon. The seven ordinary directions use the phone's arc positions and triangle counts. An unknown/NONE direction has only the subdued ring, not a flat-trend marker.

The branch transports both triple-up and triple-down with the same `X` symbol, which the existing renderer resolves to double-up. This package does not fix that ambiguity or claim complete phone/Wear trend parity. Native stale-reading, glucose-color, reservoir-warning and loop-warning hooks remain in use; no thresholds or warning logic were changed. The reservoir arc itself stays light blue; warning colors come from the native reservoir text.

### Compact transfer and resolved loading report

The user reported that **1.5 appears in the phone list, but selecting it leaves the old watchface on the watch, with no visible error**. The local ZIP CRC, JSON and PNGs are valid. The checked phone code sends the entire ZIP through one `MessageClient.sendMessage` call; it does not use an Asset or chunk the file. Google describes message payloads as [generally not larger than 100k](https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient). This guidance is not proof of a hard cutoff or the user's exact failure cause; no phone/Wear failure log is available.

Version 1.6 reduces the package by **85.3%**, without reducing the number of gauge steps or changing transport code. The builder rejects a ZIP above a conservative **95,000-byte budget**, leaving margin below that guidance. The 51 SVGs retain the crop, colors, round caps and arc geometry. Their local previews render the packaged SVG through resvg; native AndroidSVG rasterization may differ slightly. Only the gallery thumbnail is palette-quantized, not the live text colors or PNG trend masks.

After trying 1.6, the user replied **"funktioniert!"** on 2026-10-16. This confirms successful loading/activation for the user's setup and closes the reported loading problem. It does not prove that message size was the cause, nor verify all reservoir/trend states, exact spacing, warnings or ambient behavior.

## Import

Package: [`AAPS Sport.zip`](../../../plugins/configuration/src/androidMain/assets/AAPS%20Sport.zip).

1. Keep the ZIP zipped and copy it into `<selected AAPS directory>/exports/AAPS Sport.zip` on the phone.
2. With the watch connected, use **AAPS → Wear → Watchface laden → AAPS Sport (1.14)**. Selecting it sends the face to the watch. To update an earlier version, replace its ZIP in `exports`, close/reopen the load list, and confirm that it shows version 1.14 before selecting it.
3. Select **AAPS(Custom)** on the watch if needed.
4. In its existing settings, enable the fields you want: glucose, trend arrow, reading age, COB, IOB, loop status, **reservoir**, **basal/temporary basal** and **date**. Date is off by default in the checked native code; the preview deliberately shows it on. Saved settings are respected, not changed by the ZIP.

No APK rebuild or installation is needed when the installed app supports this CWF format. As with Material, a future phone build may include the ZIP as an asset, but adding an asset does not refresh an already populated `exports` list. Copy this ZIP explicitly.

## Sources and local checks

- `CustomWatchface.json`: live-field layout and existing preference chains.
- `SportArtwork.json`: editable colors, reservoir scale/geometry, crop, edge marks, trend-ring geometry and icon source paths/colors. Keep its gauge steps consistent with the explicit native image list in `CustomWatchface.json`; tests check this.
- `build_watchface.py`: SVG/PNG resource generation, deterministic size-limited ZIP and synthetic previews. Pillow and resvg-py versions are pinned in `requirements.txt`. This is an asset-building helper, not application code.
- `test_watchface.py`: static CWF contract and design checks.
- `CustomWatchface.png`, `preview-states.png`, `preview-trends.png`, `preview-reservoir.png`: generated previews.

From `AndroidAPS/`:

```powershell
python -m pip install -r wear/watchfaces/sport/requirements.txt
python wear/watchfaces/sport/build_watchface.py
python wear/watchfaces/sport/build_watchface.py --check
python -m unittest discover -s wear/watchfaces/sport -p "test_*.py" -v
```

The ZIP contains 65 root entries: `CustomWatchface.json`, the indexed `CustomWatchface.png` thumbnail, 51 gauge SVGs, two icon SVGs, and ten other PNGs (background, empty image and eight trend masks). No fonts are downloaded or added; the face uses fonts already in Wear.

**All previews use synthetic data. They are not Android screenshots or evidence of live watch behavior.** They use the packaged artwork and trend masks, but approximate text placement and native loop icons. Their sample readings must not be used for treatment decisions.

## Watch verification still needed

- Basic loading/activation of **1.6** is user-confirmed on 2026-10-16. Later requests refined icons, spacing, reservoir color/position, ring thickness and marker size. Confirm loading, the larger trend markers and retained layout for **1.14** at normal viewing size. If transfer fails, collect phone/Wear logs around selection; the checked phone code logs `sendMessage` with the payload size and a `failure` line on send failure.
- Check `OKT`/`OCT`, longer month abbreviations, 12/24-hour time and optional AM/PM for clipping. Check the date-to-clock gap and the lower reservoir text's clearance from the arc on the actual display.
- Check glucose units, reading age, the six-pixel ring and trend-ring visibility against the phone, including text clearance and all larger direction markers, especially the double markers and image-edge clearance. Missing data must not be mistaken for a flat trend.
- Check the orange wheat and turquoise syringe icons, their closer spacing to short and long 32-pixel COB/IOB totals, spacing from the clock, and normal/detailed IOB. The native breakdown remains below the IOB total. The local font model is not proof of exact Android placement. Check negative values, larger COB strings and rate/percentage basal strings. Arbitrarily long native strings and all locales cannot be guaranteed by sample-based tests.
- Compare the light-blue reservoir heading, normal amount text and lower-right SVG arc with IOB/basal color and with the phone at naturally occurring levels, including visibility and clipping. Check the arc's clearance from long/negative IOB and detailed breakdown text: the local sample model has only a small gap for the longest negative total. Full scale is 200 U; only the arc is limited. COB must remain orange. Check native warning/urgent text appearance without changing treatment or pump settings for a visual test.
- Toggle optional fields, including reservoir and basal: their headings/arc should follow them. Each COB/IOB switch must hide both its icon and its value, without hiding the other group or reservoir arc. Basal and loop must still hide independently, despite being grouped together.
- Check native stale-data and loop-warning appearance, ambient mode, wake/touch behavior and battery use. Multiple gauge images are loaded by the native renderer; memory use and runtime behavior still need watch verification. No device testing or installation has been done by the agent.
