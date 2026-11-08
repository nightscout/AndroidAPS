"""Static CWF contract checks. These do not replace testing on a watch."""

import io
import itertools
import json
import math
import re
import unittest
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image

import build_watchface as build


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate key: {key}")
        result[key] = value
    return result


def contrast(first, second):
    def luminance(color):
        channels = [int(color[n:n + 2], 16) / 255 for n in (1, 3, 5)]
        channels = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
        return sum(a * b for a, b in zip(channels, (0.2126, 0.7152, 0.0722)))
    low, high = sorted((luminance(first), luminance(second)))
    return (high + 0.05) / (low + 0.05)


class MaterialWatchfaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.face = json.loads((build.SOURCE / "CustomWatchface.json").read_text(encoding="utf-8"), object_pairs_hook=strict_object)
        with zipfile.ZipFile(build.ROOT / "plugins/configuration/src/androidMain/assets/AAPS V2.zip") as archive:
            cls.reference = json.loads(archive.read("CustomWatchface.json"))

    def test_same_live_fields_as_aaps_v2(self):
        def visible(face):
            return {k for k, v in face.items() if isinstance(v, dict) and v.get("visibility") == "visible"
                    and k not in {"background", "freetext1", "freetext2", "freetext3", "freetext4", "timePeriod"}}
        self.assertEqual(self.reference["metadata"]["name"], "AAPS V2")
        self.assertEqual(self.reference["metadata"]["author_version"], "1.0")
        self.assertEqual(visible(self.reference), visible(self.face))
        self.assertEqual(self.reference["pointSize"], self.face["pointSize"])
        self.assertEqual(self.reference["enableSecond"], self.face["enableSecond"])

    def test_no_live_value_or_preference_override(self):
        metadata_keys = set(self.face["metadata"])
        self.assertFalse(any(k.startswith("key_") or k == "cwf_authorization" for k in metadata_keys))
        for key in build.VIEW_PREFS | {"time": "", "timePeriod": ""}:
            with self.subTest(key=key):
                view = self.face[key]
                self.assertNotIn("textvalue", view)
                self.assertNotIn("dynValue", view)
                self.assertNotIn("dynData", view)
                for dark, detailed in itertools.product((True, False), repeat=2):
                    style = build.resolve_pref(self.face, view["dynPref"], {"key_dark": dark, "key_show_detailed_iob": detailed}) if "dynPref" in view else {}
                    self.assertFalse(any(k.startswith("textvalue") for k in style))

    def test_metadata_version_passes_current_import_check(self):
        interface = build.ROOT / "core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/rx/weardata"
        supported = re.search(r'CUSTOM_VERSION = "([^"]+)"', (interface / "ResFormat.kt").read_text(encoding="utf-8")).group(1)
        app_version = [int(n) for n in supported.split(".")]
        face_version = [int(n) for n in self.face["metadata"]["cwf_version"].split(".")]
        self.assertGreaterEqual(len(app_version), 2)
        self.assertGreaterEqual(len(face_version), 2)
        self.assertGreaterEqual(app_version[0], face_version[0])

    def test_native_warning_hooks_not_masked(self):
        # CustomWatchface skips native age/battery/SGV warnings if stepFontColor > 0.
        for key in ("sgv", "direction", "timestamp", "uploader_battery"):
            self.assertNotIn("dynPref", self.face[key], key)
            self.assertNotIn("dynData", self.face[key], key)
        # Loop colors still come from the native grey/green/red background icons.
        loop = self.face["loop"]
        self.assertNotIn("background", loop)
        for dark in (True, False):
            style = build.resolve_pref(self.face, loop["dynPref"], {"key_dark": dark})
            self.assertFalse(any(k.startswith("image") for k in style))

    def test_all_names_and_preferences_exist_in_renderer(self):
        shared = build.ROOT / "shared/impl/src/commonMain/kotlin/app/aaps/shared/impl/weardata"
        symbols = {key: symbol for symbol, key in re.findall(r'\b([A-Z0-9_]+)\("([^"]+)"', (build.ROOT / "shared/impl/src/androidMain/kotlin/app/aaps/shared/impl/weardata/ViewKeys.kt").read_text(encoding="utf-8"))}
        renderer = (build.ROOT / "wear/src/main/kotlin/app/aaps/wear/watchfaces/CustomWatchface.kt").read_text(encoding="utf-8")
        pref_keys = set(re.findall(r'\("([^"]+)"', (shared / "JsonKeyValues.kt").read_text(encoding="utf-8")))
        metadata = (build.ROOT / "core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/rx/weardata/CwfMetaDataKey.kt").read_text(encoding="utf-8")
        pref_keys.update(re.findall(r'\("(key_[^"]+)"', metadata))
        for key, view in self.face.items():
            if isinstance(view, dict) and "width" in view:
                self.assertIn(key, symbols)
                self.assertIn(f"ViewKeys.{symbols[key]}.key", renderer)
                if "dynPref" in view:
                    self.assertIn(view["dynPref"], self.face["dynPref"])
                if "font" in view:
                    self.assertTrue((build.FONTS / f"{view['font']}.ttf").exists())
        for rule in self.face["dynPref"].values():
            self.assertIn(rule["prefKey"], pref_keys)

    def test_view_bounds_and_round_safe_chart(self):
        for key, view in self.face.items():
            if isinstance(view, dict) and "width" in view:
                self.assertGreater(view["width"], 0, key)
                self.assertGreater(view["height"], 0, key)
                self.assertGreaterEqual(view["leftmargin"], 0, key)
                self.assertGreaterEqual(view["topmargin"], 0, key)
                self.assertLessEqual(view["leftmargin"] + view["width"], 400, key)
                self.assertLessEqual(view["topmargin"] + view["height"], 400, key)
        chart = self.face["chart"]
        for x in (chart["leftmargin"], chart["leftmargin"] + chart["width"]):
            for y in (chart["topmargin"], chart["topmargin"] + chart["height"]):
                self.assertLess(math.hypot(x - 200, y - 200), build.RING_RADIUS - build.RING_STROKE / 2)

    def test_theme_steps_have_a_valid_nonmedical_value(self):
        # NONE has a zero-length range in DynProvider; a lone '*1' entry would not work.
        # Calendar day is always valid, independent of missing or stale glucose data.
        for dark, detailed in itertools.product((True, False), repeat=2):
            prefs = {"key_dark": dark, "key_show_detailed_iob": detailed}
            for key in self.face["dynPref"]:
                style = build.resolve_pref(self.face, key, prefs)
                if any(re.search(r"\d$", k) for k in style):
                    self.assertEqual(style.get("valueKey"), "day", key)

    def test_free_labels_follow_visibility_preferences(self):
        for label, pref in (("freetext1", "key_show_delta"), ("freetext2", "key_show_avg_delta"),
                            ("freetext3", "key_show_temp_basal"), ("freetext4", "key_show_bgi")):
            for dark in (True, False):
                rule = self.face[label]["dynPref"]
                hidden = build.resolve_pref(self.face, rule, {pref: False, "key_dark": dark})
                shown = build.resolve_pref(self.face, rule, {pref: True, "key_dark": dark})
                self.assertEqual(hidden["textvalue1"], "")
                self.assertNotIn("textvalue1", shown)

    def test_normal_and_warning_color_contrast(self):
        for dark, surface, hero in ((True, "#1B241F", "#14271F"), (False, "#E6EFE8", "#D7F5E6")):
            settings = {"key_dark": dark}
            for rule in ("primary", "secondary"):
                self.assertGreaterEqual(contrast(build.resolve_pref(self.face, rule, settings)["fontColor1"], surface), 4.5)
            palette = build.resolve_pref(self.face, "palette", settings)
            background = "#000000" if dark else "#FFFFFF"
            for key in ("midColor", "highColor", "lowColor"):
                self.assertGreaterEqual(contrast(palette[key], hero), 4.5)
                self.assertGreaterEqual(contrast(palette[key], background), 4.5)
            time_color = build.resolve_pref(self.face, self.face["time"]["dynPref"], settings)["fontColor1"]
            self.assertGreaterEqual(contrast(time_color, background), 4.5)
            self.assertGreaterEqual(contrast(time_color, hero), 4.5)
            self.assertGreaterEqual(contrast("#757575", background), 4.5)

    def test_header_glucose_and_central_clock_keep_their_helpers(self):
        time, sgv = self.face["time"], self.face["sgv"]
        day, month = self.face["day"], self.face["month"]
        # Date is stacked to the right of the clock, clear of the rig battery.
        for view in (day, month):
            self.assertGreaterEqual(view["leftmargin"], time["leftmargin"] + time["width"])
            self.assertGreaterEqual(view["topmargin"], time["topmargin"])
            self.assertLessEqual(view["topmargin"] + view["height"], time["topmargin"] + time["height"])
            self.assertGreaterEqual(view["topmargin"], self.face["rig_battery"]["topmargin"] + self.face["rig_battery"]["height"])
        self.assertEqual(day["leftmargin"], month["leftmargin"])
        self.assertLessEqual(day["topmargin"] + day["height"], month["topmargin"])
        self.assertLessEqual(sgv["topmargin"] + sgv["height"], time["topmargin"] + time["height"] / 2)
        self.assertEqual((sgv["leftmargin"], sgv["topmargin"], sgv["width"], sgv["height"]), (114, 18, 160, 74))
        self.assertEqual((time["leftmargin"], time["topmargin"], time["width"], time["height"]), (113, 83, 174, 80))
        self.assertGreater(time["textsize"], 42)
        self.assertGreater(time["textsize"], sgv["textsize"])
        self.assertEqual(time["textsize"], 64)
        self.assertEqual(sgv["textsize"], 54)
        age = self.face["timestamp"]
        self.assertGreaterEqual(age["topmargin"], sgv["topmargin"])
        self.assertLessEqual(age["topmargin"] + age["height"], sgv["topmargin"] + sgv["height"])
        self.assertGreaterEqual(age["leftmargin"], sgv["leftmargin"] + sgv["width"])
        period = self.face["timePeriod"]
        # AM/PM uses free space below the clock glyphs, left of the taller date.
        self.assertLessEqual(period["leftmargin"] + period["width"], day["leftmargin"])
        time_bounds = build.font_for(time, time["textsize"]).getbbox("23:59")
        period_bounds = build.font_for(period, period["textsize"]).getbbox("PM")
        time_bottom = time["topmargin"] + time["height"] / 2 + (time_bounds[3] - time_bounds[1]) / 2
        period_top = period["topmargin"] + period["height"] / 2 - (period_bounds[3] - period_bounds[1]) / 2
        self.assertGreater(period_top, time_bottom)
        self.assertLessEqual(period["topmargin"] + period["height"], self.face["delta"]["topmargin"])

    def test_date_helpers_allow_full_font_line_height_and_extra_space(self):
        # Glyph bounds alone missed the 1.10 clipping. Reserve a full font line
        # plus four template pixels for platform font padding. This remains a
        # design check, not a measurement of Android's actual TextView metrics.
        for key, textsize, height in (("day", 18, 26), ("month", 18, 26), ("timePeriod", 14, 22)):
            view = self.face[key]
            self.assertEqual(view["textsize"], textsize)
            self.assertEqual(view["height"], height)
            self.assertGreaterEqual(view["height"], sum(build.font_for(view, textsize).getmetrics()) + 4)
            for zoom in (0.9, 0.99, 1.0, 1.125, 1.2, 1.5):
                with self.subTest(key=key, zoom=zoom):
                    # Use high-resolution metrics to avoid whole-font-size rounding.
                    font = build.font_for(view, round(textsize * zoom * 100))
                    line_height = sum(font.getmetrics()) / 100
                    self.assertGreaterEqual(int(view["height"] * zoom), line_height + 4 * zoom)

    def test_representative_text_fits_without_auto_shrinking(self):
        cases = {"time": ("23:59", "10:08", "12:59"), "day": ("1", "14", "31"),
                 "month": ("JAN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEP", "SEPT", "OKT", "OCT", "NOV", "DEZ"),
                 "timePeriod": ("AM", "PM"), "sgv": ("106", "400", "22.2", "---"),
                 "delta": ("+12.3", "-12.3"), "avg_delta": ("+12.3", "-12.3"),
                 "timestamp": ("18'", "60'"), "uploader_battery": ("100%",),
                 "cob2": ("120g",), "basalRate": ("0.85U/h", "120%"),
                 "bgi": ("-12.3",), "iob1": ("IOB", "12.34"), "iob2": ("12.34", "(1.60|0.80)", "(-10.00|10.00)")}
        for detailed in (True, False):
            for key, samples in cases.items():
                view = self.face[key]
                style = build.resolve_pref(self.face, view["dynPref"], {"key_show_detailed_iob": detailed}) if "dynPref" in view else {}
                font = build.font_for(view, style.get("textsize1", view["textsize"]))
                for text in samples:
                    if key == "iob2" and text.startswith("(") and not detailed:
                        continue
                    with self.subTest(key=key, text=text, detailed=detailed):
                        self.assertLessEqual(font.getlength(text), view["width"])
                        bounds = font.getbbox(text)
                        self.assertLessEqual(bounds[3] - bounds[1], view["height"])
                        if key in ("time", "sgv", "timestamp", "day", "month", "timePeriod"):
                            center_x = view["leftmargin"] + view["width"] / 2
                            center_y = view["topmargin"] + view["height"] / 2
                            for x in (center_x - font.getlength(text) / 2, center_x + font.getlength(text) / 2):
                                for y in (center_y - (bounds[3] - bounds[1]) / 2, center_y + (bounds[3] - bounds[1]) / 2):
                                    self.assertLess(math.hypot(x - 200, y - 200), build.RING_RADIUS - build.RING_STROKE / 2)

    def test_archive_contract_and_resources(self):
        package = build.archive_bytes(self.face)
        self.assertEqual(package, build.archive_bytes(self.face))
        with zipfile.ZipFile(io.BytesIO(package)) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(set(archive.namelist()), {"CustomWatchface.json", "CustomWatchface.png", *build.RESOURCES})
            self.assertEqual(json.loads(archive.read("CustomWatchface.json")), self.face)
            preview = Image.open(io.BytesIO(archive.read("CustomWatchface.png")))
            self.assertEqual(preview.size, (400, 400))
            preview.verify()
            resources = {name.removesuffix(".svg") for name in build.RESOURCES}
            for rule in self.face["dynPref"].values():
                for branch in (rule["true"], rule["false"]):
                    if "image1" in branch:
                        self.assertIn(branch["image1"], resources)
            for view in self.face.values():
                if isinstance(view, dict) and "background" in view:
                    self.assertIn(view["background"], resources)
            for name in build.STATIC_RESOURCES:
                self.assertTrue(ET.fromstring(archive.read(name)).tag.endswith("svg"))
            res_map = (build.ROOT / "shared/impl/src/commonMain/kotlin/app/aaps/shared/impl/weardata/ResFileMap.kt").read_text(encoding="utf-8")
            for name in build.TREND_ARROWS:
                self.assertIn(f'("{name}")', res_map)
                self.assertEqual(archive.read(f"{name}.png"), build.trend_resource_bytes(name))

    def test_previews_render_missing_and_optional_states(self):
        for dark, detailed, hour12, matching in itertools.product((True, False), repeat=4):
            for state in ("normal", "mmol", "high", "low", "stale", "missing"):
                self.assertEqual(build.render(self.face, dark=dark, detailed=detailed, hour12=hour12, state=state,
                                              prefs={"key_match_divider": matching}).size, (400, 400))
        hidden = {key: False for key in build.VIEW_PREFS.values()}
        for matching in (True, False):
            self.assertEqual(build.render(self.face, prefs=hidden | {"key_match_divider": matching}).size, (400, 400))

    def test_matching_divider_selects_the_four_background_resources(self):
        renderer = (build.ROOT / "wear/src/main/kotlin/app/aaps/wear/watchfaces/CustomWatchface.kt").read_text(encoding="utf-8")
        self.assertIn("PREF_MATCH_DIVIDER(JsonKeyValues.PREF_MATCH_DIVIDER.key, R.string.key_match_divider, false, true, R.string.pref_matching_divider)", renderer)
        surface = self.face["background"]["dynPref"]
        self.assertEqual(self.face["dynPref"][surface]["prefKey"], "key_match_divider")
        for dark, matching in itertools.product((True, False), repeat=2):
            expected = f"Material{'Dark' if dark else 'Light'}{'Plain' if matching else ''}"
            rule = build.resolve_pref(self.face, surface, {"key_dark": dark, "key_match_divider": matching})
            self.assertEqual(rule, {"valueKey": "day", "image1": expected})
            self.assertIn(f"{expected}.svg", build.RESOURCES)
        self.assertFalse(build.PREF_DEFAULTS["key_match_divider"])
        self.assertEqual(build.resolve_pref(self.face, surface, {"key_dark": True})["image1"], "MaterialDark")
        self.assertEqual(build.resolve_pref(self.face, surface, {"key_dark": False})["image1"], "MaterialLight")

    def test_plain_surfaces_only_contain_the_full_screen_background(self):
        for name, color in (("MaterialDarkPlain.svg", "#000000"), ("MaterialLightPlain.svg", "#FFFFFF")):
            svg = ET.fromstring((build.SOURCE / name).read_bytes())
            self.assertEqual((svg.get("width"), svg.get("height")), ("400", "400"))
            self.assertEqual(len(svg), 1)
            self.assertTrue(svg[0].tag.endswith("rect"))
            self.assertEqual(svg[0].attrib, {"width": "400", "height": "400", "fill": color})

    def test_matching_divider_does_not_change_text_or_warning_styles(self):
        for dark, detailed in itertools.product((True, False), repeat=2):
            off = {"key_dark": dark, "key_show_detailed_iob": detailed, "key_match_divider": False}
            on = off | {"key_match_divider": True}
            for key, view in self.face.items():
                if key != "background" and isinstance(view, dict) and "dynPref" in view:
                    self.assertEqual(build.resolve_pref(self.face, view["dynPref"], off), build.resolve_pref(self.face, view["dynPref"], on), key)
            self.assertEqual(build.resolve_pref(self.face, "palette", off), build.resolve_pref(self.face, "palette", on))
            background = "#000000" if dark else "#FFFFFF"
            for rule in ("primary", "secondary"):
                self.assertGreaterEqual(contrast(build.resolve_pref(self.face, rule, on)["fontColor1"], background), 4.5)

    def test_matching_divider_removes_surfaces_but_keeps_header_and_ring(self):
        # Sample empty parts of each tonal surface, not the text or chart strokes.
        points = ((120, 100), (40, 182), (360, 182), (60, 211), (150, 211), (275, 211), (90, 303))
        for dark in (True, False):
            off = build.render(self.face, dark=dark, prefs={"key_match_divider": False})
            on = build.render(self.face, dark=dark, prefs={"key_match_divider": True})
            background = (0, 0, 0, 255) if dark else (255, 255, 255, 255)
            for point in points:
                self.assertEqual(on.getpixel(point), background, (dark, point))
                self.assertNotEqual(off.getpixel(point), background, (dark, point))
            for box in ((114, 18, 274, 80), (0, 0, 25, 400), (375, 0, 400, 400)):
                self.assertEqual(off.crop(box).tobytes(), on.crop(box).tobytes())

    def test_packaged_asset_is_current(self):
        self.assertEqual(build.ASSET.read_bytes(), build.archive_bytes(self.face))

    def test_ring_is_a_native_full_size_direction_view(self):
        view = self.face["direction"]
        self.assertEqual((view["width"], view["height"], view["leftmargin"], view["topmargin"]), (400, 400, 0, 0))
        # Native TrendArrowMap chooses the named PNG, and native colorFilter tints it.
        self.assertNotIn("rotation", view)
        self.assertNotIn("color", view)
        self.assertNotIn("dynData", view)
        self.assertNotIn("dynPref", view)

    def test_ring_visibility_is_controlled_by_the_native_direction_preference(self):
        renderer = (build.ROOT / "wear/src/main/kotlin/app/aaps/wear/watchfaces/CustomWatchface.kt").read_text(encoding="utf-8")
        self.assertIn("DIRECTION(ViewKeys.DIRECTION.key, R.id.direction, PrefMap.SHOW_DIRECTION)", renderer)
        self.assertIn("binding.direction.setImageDrawable(TrendArrowMap.drawable(singleBg[0]))", renderer)
        self.assertEqual(self.face["direction"]["visibility"], "visible")
        self.assertEqual(build.VIEW_PREFS["direction"], "key_show_direction")
        self.assertNotIn("key_show_direction", self.face["metadata"])

    def test_ring_and_arrowheads_have_display_edge_clearance(self):
        # Static template geometry only; this cannot prove a watch's runtime viewport.
        self.assertLess(build.RING_RADIUS + build.RING_STROKE / 2, 200)
        for name in build.TREND_ARROWS:
            for triangle in build.trend_triangles(name):
                for x, y in triangle:
                    self.assertLessEqual(math.hypot(x - 200, y - 200), 197 + 1e-9)

    def test_larger_ring_keeps_arrow_tip_radius_unchanged(self):
        self.assertEqual(build.RING_RADIUS * 2, 384)
        self.assertEqual(build.RING_STROKE, 5)
        self.assertEqual(build.RING_RADIUS + build.RING_STROKE * 0.2 + build.RING_ARROW_HEIGHT, 197)
        for name, (_, _, count) in build.TREND_ARROWS.items():
            tips = [triangle[0] for triangle in build.trend_triangles(name)]
            self.assertEqual(len(tips), count)
            for x, y in tips:
                self.assertAlmostEqual(math.hypot(x - 200, y - 200), 197)

    def test_ring_matches_aaps4_arc_positions_and_counts(self):
        source = (build.ROOT / "ui/src/commonMain/kotlin/app/aaps/ui/compose/overview/BgInfoSection.kt").read_text(encoding="utf-8")
        native = {key: (float(angle), int(count)) for key, angle, count in
                  re.findall(r'TrendArrow\.(\w+)\s*-> ArcIndicator\(centerAngle = (-?[\d.]+)f, sweepAngle = sweepAngle, triangleCount = (\d+)\)', source)}
        self.assertEqual(build.RING_SWEEP, float(re.search(r'val sweepAngle = ([\d.]+)f', source).group(1)))
        for name, (key, angle, count) in build.TREND_ARROWS.items():
            with self.subTest(name=name):
                if key == "NONE":
                    self.assertIsNone(angle)
                    self.assertEqual(count, 0)
                else:
                    self.assertEqual((angle, count), native[key])
                triangles = build.trend_triangles(name)
                self.assertEqual(len(triangles), count)
                for triangle in triangles:
                    for x, y in triangle:
                        self.assertLess(math.hypot(x - 200, y - 200), 200)
                        self.assertGreater(math.hypot(x - 200, y - 200), build.RING_RADIUS)

    def test_ring_pngs_are_transparent_white_and_have_correct_highlight(self):
        for name, (_, angle, _) in build.TREND_ARROWS.items():
            with self.subTest(name=name):
                image = Image.open(io.BytesIO(build.trend_resource_bytes(name)))
                self.assertEqual(image.mode, "RGBA")
                self.assertEqual(image.size, (400, 400))
                self.assertEqual(image.getpixel((200, 200))[3], 0)
                for point in ((0, 0), (399, 0), (0, 399), (399, 399)):
                    self.assertEqual(image.getpixel(point)[3], 0)
                self.assertTrue(all((r, g, b) == (255, 255, 255) for r, g, b, a in image.get_flattened_data() if a))
                if angle is None:
                    self.assertLess(image.getchannel("A").getextrema()[1], 100)
                else:
                    def point(degrees):
                        radians = math.radians(degrees)
                        return (round(200 + build.RING_RADIUS * math.cos(radians)),
                                round(200 + build.RING_RADIUS * math.sin(radians)))
                    self.assertGreater(image.getpixel(point(angle))[3], 200)
                    self.assertLess(image.getpixel(point(angle + 180))[3], 100)

    def test_ring_visibility_and_missing_trend_are_not_flat(self):
        for dark in (True, False):
            none = build.render(self.face, dark=dark, trend="ArrowNone")
            flat = build.render(self.face, dark=dark, trend="ArrowFlat")
            self.assertNotEqual(none.tobytes(), flat.tobytes())
            # A missing reading must not inherit the selected sample trend.
            self.assertEqual(build.render(self.face, dark=dark, state="missing", trend="ArrowFlat").tobytes(),
                             build.render(self.face, dark=dark, state="missing", trend="ArrowNone").tobytes())
            hidden = {"key_show_direction": False}
            for name in build.TREND_ARROWS:
                self.assertEqual(build.render(self.face, dark=dark, trend=name, prefs=hidden).tobytes(),
                                 build.render(self.face, dark=dark, trend="ArrowNone", prefs=hidden).tobytes())


if __name__ == "__main__":
    unittest.main()
