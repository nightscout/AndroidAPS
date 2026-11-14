"""Static CWF/design tests. These do not run the Android renderer."""

import io
import itertools
import json
import math
import re
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image, ImageChops, ImageFilter

import build_watchface as build


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate key: {key}")
        result[key] = value
    return result


def contrast_on_black(color):
    channels = [int(color[n:n + 2], 16) / 255 for n in (1, 3, 5)]
    linear = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return (sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722))) + 0.05) / 0.05


class SportWatchfaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.face = json.loads((build.SOURCE / "CustomWatchface.json").read_text(encoding="utf-8"), object_pairs_hook=strict_object)
        cls.renderer = (build.ROOT / "wear/src/main/kotlin/app/aaps/wear/watchfaces/CustomWatchface.kt").read_text(encoding="utf-8")
        cls.shared = build.ROOT / "shared/impl/src/commonMain/kotlin/app/aaps/shared/impl/weardata"

    def test_metadata_and_version_are_accepted_by_the_checkout(self):
        self.assertEqual(self.face["metadata"]["name"], "AAPS Sport")
        self.assertEqual(self.face["metadata"]["author_version"], "1.14")
        supported = (build.ROOT / "core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/rx/weardata/ResFormat.kt").read_text(encoding="utf-8")
        app_version = re.search(r'CUSTOM_VERSION = "([^"]+)"', supported).group(1)
        self.assertLessEqual(int(self.face["metadata"]["cwf_version"].split(".")[0]), int(app_version.split(".")[0]))
        self.assertFalse(any(k.startswith("key_") or k == "cwf_authorization" for k in self.face["metadata"]))

    def test_only_supported_views_keys_preferences_and_fonts_are_used(self):
        view_keys = dict((key, symbol) for symbol, key in re.findall(r'\b([A-Z0-9_]+)\("([^"]+)"', (build.ROOT / "shared/impl/src/androidMain/kotlin/app/aaps/shared/impl/weardata/ViewKeys.kt").read_text(encoding="utf-8")))
        json_keys = set(re.findall(r'\("([^"]+)"', (self.shared / "JsonKeys.kt").read_text(encoding="utf-8")))
        pref_keys = set(re.findall(r'\("([^"]+)"', (self.shared / "JsonKeyValues.kt").read_text(encoding="utf-8")))
        meta = (build.ROOT / "core/interfaces/src/commonMain/kotlin/app/aaps/core/interfaces/rx/weardata/CwfMetaDataKey.kt").read_text(encoding="utf-8")
        pref_keys.update(re.findall(r'\("(key_[^"]+)"', meta))
        for key, view in self.face.items():
            if not isinstance(view, dict) or "width" not in view:
                continue
            self.assertIn(key, view_keys)
            self.assertIn(f"ViewKeys.{view_keys[key]}.key", self.renderer)
            self.assertTrue(set(view) <= json_keys, (key, set(view) - json_keys))
            if "font" in view:
                self.assertTrue((build.FONTS / f"{view['font']}.ttf").exists())
                self.assertIn(f'("{view["font"]}"', (self.shared / "JsonKeyValues.kt").read_text(encoding="utf-8"))
        for rule in self.face["dynPref"].values():
            self.assertIn(rule["prefKey"], pref_keys)
            for branch in (rule["true"], rule["false"]):
                self.assertTrue({re.sub(r"\d+$", "", k) for k in branch} <= json_keys)

    def test_no_chart_no_battery_no_extra_live_fields(self):
        live = {key for key, view in self.face.items() if isinstance(view, dict) and view.get("visibility") == "visible"}
        self.assertEqual(live, {"background", "cover_chart", "direction", "sgv", "timestamp", "time", "timePeriod",
                                "day", "month", "cob1", "cob2", "iob1", "iob2", "loop", "reservoir", "basalRate",
                                "freetext1", "freetext2", "freetext3", "freetext4"})
        self.assertEqual(self.face["chart"]["visibility"], "gone")
        self.assertFalse(any("battery" in key for key in self.face))
        self.assertFalse(self.face["enableSecond"])

    def test_medical_values_and_warnings_stay_native(self):
        for key in ("sgv", "timestamp", "direction", "loop", "reservoir"):
            for override in ("dynPref", "dynData", "dynValue", "textvalue", "color", "background"):
                self.assertNotIn(override, self.face[key], (key, override))
        for key in ("time", "timePeriod", "day", "month", "cob2", "iob1", "iob2", "basalRate"):
            self.assertNotIn("textvalue", self.face[key])
            self.assertNotIn("dynValue", self.face[key])
        self.assertIn("binding.direction.setImageDrawable(TrendArrowMap.drawable(singleBg[0]))", self.renderer)
        self.assertIn("binding.loop.setBackgroundResource(R.drawable.loop_green_25)", self.renderer)
        self.assertIn("binding.reservoir.setTextColor(reservoirColor(0))", self.renderer)
        self.assertNotIn("reservoirWarningColor", self.face)
        self.assertNotIn("reservoirUrgentColor", self.face)

    def test_reservoir_gauge_uses_native_numeric_data_and_a_200_u_scale(self):
        spec = build.ART["reservoir"]
        self.assertEqual((spec["maximum"], spec["increment"]), (200, 4))
        self.assertEqual((spec["start"], spec["sweep"]), (90, -90))
        self.assertEqual(spec["color"], "#65DEED")
        self.assertEqual(self.face["reservoirColor"], spec["color"])
        style = build.style_for(self.face, "cover_chart", {})
        self.assertEqual(style["valueKey"], "reservoir")
        self.assertEqual(style["minData"], 0)
        # Native bins have an exclusive upper end. 204 gives 51 bins of 4 U:
        # index 1 = 0 U and index 51 starts at exactly 200 U, not below it.
        self.assertEqual(style["maxData"], 204)
        self.assertEqual(style["invalidImage"], "Empty")
        self.assertEqual([style[f"image{n + 1}"] for n in range(51)], list(build.GAUGE_NAMES))
        self.assertEqual(len(build.GAUGE_NAMES), 51)
        self.assertIn("ValueMap.RESERVOIR        -> cwf.status[0].reservoir", self.renderer)
        self.assertIn("get() = dynDrawable.size - 1", self.renderer)
        self.assertIn("dataRange.maxData * 0.9999", self.renderer)
        for rule in self.face["dynPref"]:
            for prefs in ({}, {"key_show_reservoir_level": False}, {"key_show_detailed_iob": True}):
                style = build.resolve_pref(self.face, rule, prefs)
                self.assertIn(style.get("valueKey"), ("day", "reservoir"), rule)
        self.assertNotIn("dynData", self.face)

    def test_native_step_selection_handles_every_boundary_and_caps_at_200(self):
        style = build.style_for(self.face, "cover_chart", {})
        samples = [n / 4 for n in range(2001)] + [-1, -0.001, 3.9999, 199.9999, 200, 203.9999, 204, 1000]
        for value in samples:
            expected = max(0, min(200, math.floor(value / 4) * 4))
            self.assertEqual(build.select_drawable(style, value), f"Reservoir{expected:03d}", value)
        self.assertEqual(build.select_drawable(style, None), "Empty")
        self.assertEqual(build.select_drawable(style, float("nan")), "Empty")

    def test_reservoir_palette_matches_insulin_without_changing_cob(self):
        blue = "#65DEED"
        self.assertEqual(build.ART["reservoir"]["color"], blue)
        self.assertEqual(build.ART["reservoir"]["track"], "#173238")
        self.assertEqual(self.face["reservoirColor"], blue)
        for key in ("freetext3", "basalRate", "iob1", "iob2", "freetext4"):
            self.assertEqual(self.face[key]["fontColor"], blue, key)
        self.assertEqual(build.ART["icons"]["sources"]["IobIcon"]["color"], blue)
        for key in ("cob1", "cob2"):
            self.assertEqual(self.face[key]["fontColor"], "#FFAB5C", key)
        self.assertEqual(build.ART["icons"]["sources"]["CobIcon"]["color"], "#FFAB5C")
        # Let native warning/urgent colors override the normal reservoir amount.
        self.assertNotIn("fontColor", self.face["reservoir"])
        self.assertNotIn("reservoirWarningColor", self.face)
        self.assertNotIn("reservoirUrgentColor", self.face)

    def test_cropped_gauge_images_grow_and_fit_their_view(self):
        spec = build.ART["reservoir"]
        view = self.face["cover_chart"]
        self.assertEqual((view["leftmargin"], view["topmargin"], view["width"], view["height"]), (192, 192, 208, 208))
        self.assertEqual(spec["crop"], [192, 192, 400, 400])
        self.assertEqual(build.resource_image("Empty").getchannel("A").getextrema(), (0, 0))
        fill = tuple(int(spec["color"][n:n + 2], 16) for n in (1, 3, 5))
        track = tuple(int(spec["track"][n:n + 2], 16) for n in (1, 3, 5))
        channel = max(range(3), key=lambda index: fill[index] - track[index])
        threshold = (fill[channel] + track[channel]) / 2

        def filled_pixels(image):
            # Separate bright fill from the dim track without rejecting blended edges.
            return sum(1 for pixel in image.get_flattened_data() if pixel[3] > 200 and pixel[channel] > threshold)

        previous = -1
        for name in build.GAUGE_NAMES:
            image = build.resource_image(name)
            self.assertEqual(image.size, (208, 208))
            bright = filled_pixels(image)
            self.assertGreater(bright, previous)
            previous = bright
        self.assertGreater(previous, 2000)
        self.assertEqual(filled_pixels(build.resource_image("Reservoir000")), 0)
        for angle in (80, 60, 40, 20, 5):
            x, y = build.point(200, spec["radius"], angle)
            point = round(x - view["leftmargin"]), round(y - view["topmargin"])
            self.assertGreater(build.resource_image("Reservoir200").getpixel(point)[1], 200)
            self.assertLess(build.resource_image("Reservoir000").getpixel(point)[1], 100)

    def test_svg_gauges_preserve_crop_colors_caps_and_all_51_sweeps(self):
        spec = build.ART["reservoir"]
        ns = "{http://www.w3.org/2000/svg}"
        for value, name in zip(build.GAUGE_VALUES, build.GAUGE_NAMES):
            root = ET.fromstring(build.resource_bytes(name))
            self.assertEqual(root.tag, ns + "svg")
            self.assertEqual(root.attrib, {"width": "208", "height": "208", "viewBox": "192 192 208 208"})
            paths = list(root)
            self.assertEqual(len(paths), 1 if value == 0 else 2)
            for index, path in enumerate(paths):
                self.assertEqual(path.tag, ns + "path")
                self.assertEqual(set(path.attrib), {"fill", "stroke", "stroke-width", "stroke-linecap", "d"})
                self.assertEqual(path.attrib["fill"], "none")
                self.assertEqual(path.attrib["stroke-linecap"], "round")
                self.assertEqual(path.attrib["stroke-width"], str(spec["stroke"]))
                self.assertEqual(path.attrib["stroke"], spec["track"] if index == 0 else spec["color"])
                self.assertRegex(path.attrib["d"], r'^M [-\d.]+ [-\d.]+ A \d+ \d+ 0 0 0 [-\d.]+ [-\d.]+$')
                numbers = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", path.attrib["d"])]
                self.assertEqual(numbers[:2], [200, 384])
                self.assertEqual(numbers[2:7], [184, 184, 0, 0, 0])
                sweep = spec["sweep"] if index == 0 else spec["sweep"] * value / spec["maximum"]
                expected = build.point(200, spec["radius"], spec["start"] + sweep)
                for actual, coordinate in zip(numbers[-2:], expected):
                    self.assertAlmostEqual(actual, coordinate, places=5)

    def test_gauge_is_lower_right_and_fills_from_bottom_toward_right(self):
        spec = build.ART["reservoir"]
        view = self.face["cover_chart"]
        full = build.render(self.face, reservoir=200)
        self.assertEqual(full.getpixel((70, 330)), build.resource_image("Background").getpixel((70, 330)))
        self.assertGreater(full.getpixel((330, 330))[1], 200)
        for amount in (40, 100, 160):
            gauge = build.resource_image(f"Reservoir{amount:03d}")
            for fraction, filled in ((amount / 400, True), ((amount + 200) / 400, False)):
                angle = 90 - 90 * fraction
                x, y = build.point(200, spec["radius"], angle)
                pixel = gauge.getpixel((round(x - view["leftmargin"]), round(y - view["topmargin"])))
                self.assertEqual(pixel[1] > 150, filled, (amount, angle))

    def test_lower_right_gauge_clears_native_text_samples_with_one_pixel_reserve(self):
        gauge = Image.new("L", (400, 400))
        view = self.face["cover_chart"]
        mask = build.resource_image("Reservoir200").getchannel("A").point(lambda alpha: 255 if alpha > 128 else 0)
        gauge.paste(mask, (view["leftmargin"], view["topmargin"]))
        gauge = gauge.filter(ImageFilter.MaxFilter(3))
        samples = (("iob1", "-12.34", True), ("iob2", "-12.34", False), ("iob2", "(-10.00|10.00)", True),
                   ("iob2", "(-10,00|10,00)", True), ("reservoir", "250 U", False), ("freetext3", "", False))
        for key, sample, detailed in samples:
            for scale in (1, 3):
                text = Image.new("RGBA", (400 * scale, 400 * scale))
                build.draw_text(text, self.face, key, sample, {"key_show_detailed_iob": detailed}, scale)
                mask = text.getchannel("A").resize((400, 400), Image.Resampling.LANCZOS).point(lambda alpha: 255 if alpha > 128 else 0)
                self.assertIsNone(ImageChops.multiply(mask, gauge).getbbox(), (key, sample, scale))

    def test_compact_formats_have_native_loader_support_and_keep_png_trend_tinting(self):
        loader = (build.ROOT / "shared/impl/src/androidMain/kotlin/app/aaps/shared/impl/weardata/ZipWatchfaceFormat.kt").read_text(encoding="utf-8")
        extension = (build.ROOT / "shared/impl/src/androidMain/kotlin/app/aaps/shared/impl/weardata/ResDataExtension.kt").read_text(encoding="utf-8")
        self.assertIn('entryName.substringBeforeLast(".")', loader)
        self.assertIn("ResFormat.SVG", extension)
        self.assertIn("SVG.getFromString(String(value))", extension)
        self.assertIn("svg.renderToPicture()", extension)
        for name in build.GAUGE_NAMES:
            self.assertEqual(build.resource_filename(name), f"{name}.svg")
        for name in build.TREND_ARROWS:
            self.assertEqual(build.resource_filename(name), f"{name}.png")
            Image.open(io.BytesIO(build.resource_bytes(name))).verify()

    def test_complete_zip_fits_single_message_budget_and_oversize_builds_are_rejected(self):
        service = (build.ROOT / "plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/wear/wearintegration/DataLayerListenerServiceMobile.kt").read_text(encoding="utf-8")
        self.assertIn("sendMessage(rxWatchfacePath, it.payload)", service)
        self.assertIn(".sendMessage(nodeId, path, data)", service)
        self.assertLessEqual(len(build.archive_bytes(self.face)), build.MAX_PACKAGE_BYTES)
        self.assertLess(build.MAX_PACKAGE_BYTES, 100_000)
        with patch.object(build, "MAX_PACKAGE_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "transport budget"):
                build.archive_bytes(self.face)

    def test_gallery_thumbnail_is_a_valid_indexed_png_not_the_live_artwork(self):
        image = Image.open(io.BytesIO(build.preview_bytes(self.face)))
        self.assertEqual(image.format, "PNG")
        self.assertEqual(image.mode, "P")
        self.assertEqual(image.size, (400, 400))
        self.assertLessEqual(len(image.getcolors()), 256)
        self.assertEqual(build.resource_image("ArrowFlat").mode, "RGBA")

    def test_basal_is_live_native_text_not_a_fake_dynamic_arc(self):
        self.assertNotIn("cover_plate", self.face)
        self.assertNotIn("IobQuarter", build.RESOURCE_NAMES)
        self.assertNotIn("CobQuarter", build.RESOURCE_NAMES)
        self.assertNotIn("dynPref", self.face["basalRate"])
        self.assertNotIn("textvalue", self.face["basalRate"])
        self.assertNotIn("dynValue", self.face["basalRate"])
        self.assertEqual(self.face["basalRate"]["fontColor"], "#65DEED")

    def test_labels_frames_and_values_follow_visibility_preferences(self):
        for pref, frame, label in (("key_show_reservoir_level", "reservoirGauge", "freetext3"),
                                    ("key_show_temp_basal", None, "freetext2"), ("key_show_bg", None, "freetext1")):
            if frame:
                self.assertEqual(build.resolve_pref(self.face, frame, {pref: False})["image1"], "Empty")
                self.assertNotEqual(build.resolve_pref(self.face, frame, {pref: True})["image1"], "Empty")
            if label:
                self.assertEqual(build.style_for(self.face, label, {pref: False})["textvalue1"], "")
                self.assertNotEqual(build.style_for(self.face, label, {pref: True})["textvalue1"], "")
        for pref, sample in (("key_show_reservoir_level", (330, 330)),):
            visible = build.render(self.face)
            hidden = build.render(self.face, prefs={pref: False})
            self.assertNotEqual(visible.getpixel(sample), hidden.getpixel(sample))
            self.assertEqual(hidden.getpixel(sample), (0, 0, 0, 255))
        # COB/IOB switches must not hide the independent reservoir gauge.
        for pref in ("key_show_cob", "key_show_iob"):
            self.assertEqual(build.render(self.face).getpixel((330, 330)), build.render(self.face, prefs={pref: False}).getpixel((330, 330)))

    def test_iob_total_stays_native_in_both_detail_modes(self):
        for detailed in (False, True):
            prefs = {"key_show_detailed_iob": detailed}
            total = build.style_for(self.face, "iob1", prefs)
            value = build.style_for(self.face, "iob2", prefs)
            if detailed:
                self.assertNotIn("textvalue1", total)
                self.assertEqual(self.face["iob1"]["textsize"], 32)
                self.assertEqual(value["textsize1"], 12)
                self.assertEqual(value["topOffset1"], 38)
                self.assertEqual(value["leftOffset1"], -16)
            else:
                # The ordinary native iob1 is just the heading; total is in iob2.
                self.assertEqual(total["textvalue1"], "")
                self.assertEqual(value["textsize1"], 32)
                self.assertEqual(value["topOffset1"], 0)
                self.assertEqual(value["leftOffset1"], 0)
            self.assertNotIn("textvalue1", value)
            self.assertEqual(build.style_for(self.face, "freetext4", prefs)["image1"], "IobIcon")
            self.assertEqual(build.style_for(self.face, "freetext4", prefs)["textvalue1"], "")

    def test_units_follow_the_existing_units_preference(self):
        for mgdl, label in ((True, "mg/dL"), (False, "mmol/L")):
            self.assertEqual(build.style_for(self.face, "freetext1", {"key_units": mgdl})["textvalue1"], label)

    def test_view_bounds_and_font_line_reserve(self):
        for key, view in self.face.items():
            if not isinstance(view, dict) or "width" not in view:
                continue
            self.assertGreater(view["width"], 0, key)
            self.assertGreater(view["height"], 0, key)
            self.assertGreaterEqual(view["leftmargin"], 0, key)
            self.assertGreaterEqual(view["topmargin"], 0, key)
            self.assertLessEqual(view["leftmargin"] + view["width"], 400, key)
            for detailed in (False, True):
                style = build.style_for(self.face, key, {"key_show_detailed_iob": detailed})
                self.assertGreaterEqual(view["leftmargin"] + style.get("leftOffset1", 0), 0, key)
                self.assertLessEqual(view["leftmargin"] + style.get("leftOffset1", 0) + view["width"], 400, key)
                self.assertLessEqual(view["topmargin"] + style.get("topOffset1", 0) + view["height"], 400, key)
                if "textsize" in view:
                    size = style.get("textsize1", view["textsize"])
                    self.assertGreaterEqual(view["height"], sum(build.font_for(view, size).getmetrics()) + 4, key)
                    for zoom in (0.9, 0.99, 1, 1.125, 1.2, 1.5):
                        line = sum(build.font_for(view, round(size * zoom * 100)).getmetrics()) / 100
                        self.assertGreaterEqual(int(view["height"] * zoom), line + 4 * zoom, (key, zoom))

    def test_time_is_large_central_and_larger_than_glucose(self):
        time, glucose = self.face["time"], self.face["sgv"]
        self.assertEqual(time["leftmargin"] + time["width"] / 2, 200)
        self.assertLess(abs(time["topmargin"] + time["height"] / 2 - 200), 20)
        self.assertEqual(time["textsize"], 92)
        self.assertGreater(time["textsize"], glucose["textsize"] * 2)
        self.assertLess(glucose["topmargin"] + glucose["height"], time["topmargin"])

    def test_cob_and_iob_are_equal_larger_than_basal_and_reservoir(self):
        cob, reservoir = self.face["cob2"], self.face["reservoir"]
        self.assertEqual(cob["textsize"], 32)
        self.assertEqual(reservoir["textsize"], 20)
        self.assertGreater(cob["textsize"], reservoir["textsize"])
        self.assertEqual(self.face["basalRate"]["textsize"], reservoir["textsize"])
        self.assertEqual(self.face["freetext2"]["textsize"], self.face["freetext3"]["textsize"])
        self.assertEqual(self.face["freetext2"]["textsize"], 12)
        self.assertEqual((cob["leftmargin"], cob["topmargin"]), (100, 286))
        self.assertEqual(self.face["cob1"]["leftmargin"], 72)
        self.assertEqual((reservoir["leftmargin"] + reservoir["width"] / 2, reservoir["topmargin"] + reservoir["height"] / 2), (200, 361))

    def test_cob_iob_rows_have_equal_type_baselines_and_icon_gaps(self):
        geometry = ("height", "topmargin", "textsize")
        for first, second in (("cob1", "freetext4"), ("cob2", "iob2"), ("cob2", "iob1")):
            left, right = self.face[first], self.face[second]
            self.assertEqual([left[k] for k in geometry], [right[k] for k in geometry])
        for icon_key, value_key in (("cob1", "cob2"), ("freetext4", "iob1"), ("freetext4", "iob2")):
            icon, value = self.face[icon_key], self.face[value_key]
            self.assertEqual((icon["width"], icon["height"]), (24, 24))
            self.assertEqual(value["leftmargin"] - icon["leftmargin"] - icon["width"], 4)
            self.assertEqual(value["gravity"], "left")
            # LEFT is top-aligned natively. The three-pixel field shift keeps
            # the total baseline at its old centered-field position.
            font = build.font_for(value, value["textsize"])
            self.assertEqual(value["topmargin"] + build.text_baseline(value, font, value["height"]),
                             283 + build.baseline_y(font, value["height"]))
        self.assertEqual(self.face["freetext4"]["leftmargin"] - self.face["cob2"]["leftmargin"] - self.face["cob2"]["width"], 4)
        self.assertEqual(self.face["cob1"]["fontColor"], "#FFAB5C")
        self.assertEqual(self.face["cob2"]["fontColor"], "#FFAB5C")
        self.assertEqual(self.face["freetext4"]["fontColor"], "#65DEED")
        for detailed in (False, True):
            key = "iob1" if detailed else "iob2"
            style = build.style_for(self.face, key, {"key_show_detailed_iob": detailed})
            self.assertEqual(style.get("textsize1", self.face[key]["textsize"]), self.face["cob2"]["textsize"])
            self.assertEqual(style.get("topOffset1", 0), 0)

    def test_cob_iob_digit_tops_match_without_any_heading_text(self):
        for detailed in (False, True):
            prefs = {"key_show_detailed_iob": detailed}
            key = "iob1" if detailed else "iob2"
            for icon in ("cob1", "freetext4"):
                self.assertEqual(self.face[icon]["textvalue"], "")
                self.assertEqual(build.style_for(self.face, icon, prefs)["textvalue1"], "")
            for cob_text, iob_text in (("18g", "2.40"), ("120g", "-12.34"), ("0g", "0.00")):
                cob = build.text_bounds(self.face, "cob2", cob_text, prefs)
                iob = build.text_bounds(self.face, key, iob_text, prefs)
                self.assertEqual(cob[1], iob[1], (detailed, cob_text, iob_text))

    def test_short_and_long_totals_start_close_to_icons_in_both_iob_modes(self):
        self.assertIn("LEFT(JsonKeyValues.LEFT.key, Gravity.START)", self.renderer)
        for detailed in (False, True):
            prefs = {"key_show_detailed_iob": detailed}
            iob_key = "iob1" if detailed else "iob2"
            for icon_key, value_key, samples in (("cob1", "cob2", ("0g", "9g", "18g", "120g", "120(40)g", "---")),
                                                 ("freetext4", iob_key, ("0.00", "2.40", "12,34", "-12.34", "---"))):
                icon, value = self.face[icon_key], self.face[value_key]
                right = icon["leftmargin"] + icon["width"]
                for sample in samples:
                    bounds = build.text_bounds(self.face, value_key, sample, prefs)
                    self.assertEqual(bounds[0] - right, 4, (value_key, sample, detailed))
                    self.assertLessEqual(bounds[2], value["leftmargin"] + value["width"])
                    for scale in (1, 3):
                        picture = Image.new("RGBA", (400 * scale, 400 * scale))
                        build.draw_text(picture, self.face, value_key, sample, prefs, scale)
                        ink = picture.getchannel("A").getbbox()
                        gap = ink[0] / scale - right
                        self.assertGreaterEqual(gap, 4, (value_key, sample, scale))
                        # The digit "1" has extra blank space within its glyph.
                        self.assertLessEqual(gap, 7, (value_key, sample, scale))

    def test_preview_digits_do_not_move_when_cob_has_a_descender(self):
        for scale in (1, 3):
            for key, sample in (("cob2", "18g"), ("iob1", "18"), ("iob2", "18")):
                prefs = {"key_show_detailed_iob": key == "iob1"}
                picture = Image.new("RGBA", (400 * scale, 400 * scale))
                build.draw_text(picture, self.face, key, sample, prefs, scale)
                top = picture.getchannel("A").getbbox()[1]
                if key == "cob2":
                    cob_top = top
                else:
                    self.assertEqual(top, cob_top, (key, scale))

    def test_entire_cob_iob_row_clears_clock_field_at_native_zoom_factors(self):
        clock = self.face["time"]
        for zoom in (0.9, 0.99, 1, 1.125, 1.2, 1.5):
            clock_bottom = int(clock["topmargin"] * zoom) + int(clock["height"] * zoom)
            for key in ("cob1", "cob2", "freetext4", "iob1", "iob2"):
                self.assertGreaterEqual(int(self.face[key]["topmargin"] * zoom) - clock_bottom, 6, (key, zoom))

    def test_aaps_icons_reuse_phone_overview_paths_and_stay_inside_gauge(self):
        mappings = (build.ROOT / "core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/navigation/ElementTypeStyle.kt").read_text(encoding="utf-8")
        self.assertRegex(mappings, r"ElementType.COB\s*-> IcCarbs")
        self.assertRegex(mappings, r"ElementType.INSULIN\s*-> IcBolus")
        commands = {"moveTo": "M", "moveToRelative": "m", "lineTo": "L", "lineToRelative": "l", "curveTo": "C", "curveToRelative": "c", "close": "Z"}
        for name, key in (("CobIcon", "cob1"), ("IobIcon", "freetext4")):
            source = (build.ROOT / build.ART["icons"]["sources"][name]["file"]).read_text(encoding="utf-8")
            root = ET.fromstring(build.resource_bytes(name))
            self.assertEqual(root.attrib, {"width": "24", "height": "24", "viewBox": "0 0 24 24"})
            path = list(root)[0]
            self.assertEqual(path.attrib["fill"], self.face[key]["fontColor"])
            original = re.findall(r"^\s*(moveToRelative|lineToRelative|curveToRelative|moveTo|lineTo|curveTo|close)\(([^)]*)\)", source, re.M)
            packaged = re.findall(r"([MLCmlcZ])([^MLCmlcZ]*)", path.attrib["d"])
            self.assertEqual(len(original), len(packaged))
            for (operation, numbers), (svg_op, svg_numbers) in zip(original, packaged):
                self.assertEqual(svg_op, commands[operation])
                self.assertEqual([float(n.strip().removesuffix("f")) for n in numbers.split(",") if n.strip()],
                                 [float(n) for n in svg_numbers.split()])
            picture, view = build.resource_image(name), self.face[key]
            self.assertEqual(picture.size, (24, 24))
            for y in range(24):
                for x in range(24):
                    if picture.getpixel((x, y))[3] > 128:
                        limit = 178 if name == "CobIcon" else 189
                        self.assertLess(math.hypot(view["leftmargin"] + x - 200, view["topmargin"] + y - 200), limit)

    def test_icons_follow_native_cob_iob_visibility_independently(self):
        for detailed in (False, True):
            for key, pref, name in (("cob1", "key_show_cob", "CobIcon"), ("freetext4", "key_show_iob", "IobIcon")):
                shown = {"key_show_detailed_iob": detailed, pref: True}
                hidden = shown | {pref: False}
                self.assertEqual(build.style_for(self.face, key, shown)["image1"], name)
                self.assertEqual(build.style_for(self.face, key, hidden)["image1"], "Empty")
                view = self.face[key]
                box = (view["leftmargin"], view["topmargin"], view["leftmargin"] + view["width"], view["topmargin"] + view["height"])
                self.assertNotEqual(build.render(self.face, prefs=shown).crop(box).tobytes(), build.render(self.face, prefs=hidden).crop(box).tobytes())
        self.assertIn("view.background = it", self.renderer)

    def test_top_glucose_and_basal_loop_groups_share_mirrored_axes(self):
        ring = self.face["direction"]
        glucose_axis = ring["leftmargin"] + ring["width"] / 2
        self.assertEqual(glucose_axis, 132)
        for key in ("sgv", "timestamp", "freetext1"):
            view = self.face[key]
            self.assertEqual(view["leftmargin"] + view["width"] / 2, glucose_axis)
        for key in ("freetext2", "basalRate", "loop"):
            view = self.face[key]
            self.assertEqual(view["leftmargin"] + view["width"] / 2, 400 - glucose_axis)
            self.assertLessEqual(view["topmargin"] + view["height"], self.face["time"]["topmargin"])
        self.assertLessEqual(self.face["freetext2"]["topmargin"] + self.face["freetext2"]["height"], self.face["basalRate"]["topmargin"])
        self.assertLess(self.face["basalRate"]["topmargin"] + self.face["basalRate"]["height"], self.face["loop"]["topmargin"])
        # Test the whole ring envelope after moving it toward the round display edge.
        cx, cy = glucose_axis, ring["topmargin"] + ring["height"] / 2
        outer = build.ART["trend"]["radius"] + build.ART["trend"]["stroke"] / 2 - build.ART["trend"]["arrowInset"] + build.ART["trend"]["arrowHeight"]
        self.assertLess(math.hypot(cx - 200, cy - 200) + outer, build.ART["edge"]["radius"])

    def test_grouped_basal_and_loop_keep_independent_visibility_options(self):
        normal = build.render(self.face)
        no_basal = build.render(self.face, prefs={"key_show_temp_basal": False})
        no_loop = build.render(self.face, prefs={"key_show_loop_status": False})
        loop = self.face["loop"]
        loop_box = (loop["leftmargin"], loop["topmargin"], loop["leftmargin"] + loop["width"], loop["topmargin"] + loop["height"])
        basal_box = (198, 40, 338, 92)
        self.assertEqual(normal.crop(loop_box).tobytes(), no_basal.crop(loop_box).tobytes())
        self.assertNotEqual(normal.crop(loop_box).tobytes(), no_loop.crop(loop_box).tobytes())
        self.assertEqual(normal.crop(basal_box).tobytes(), no_loop.crop(basal_box).tobytes())
        self.assertNotEqual(normal.crop(basal_box).tobytes(), no_basal.crop(basal_box).tobytes())

    def test_background_ticks_do_not_clutter_the_glucose_group(self):
        background = build.resource_image("Background")
        ring = self.face["direction"]
        cx, cy = ring["leftmargin"] + ring["width"] / 2, ring["topmargin"] + ring["height"] / 2
        radius = build.ART["trend"]["radius"] - 5
        for x in range(int(cx - radius), int(cx + radius)):
            for y in range(int(cy - radius), int(cy + radius)):
                if math.hypot(x - cx, y - cy) < radius:
                    self.assertEqual(background.getpixel((x, y)), (0, 0, 0, 255))

    def test_date_is_stacked_left_of_clock_without_shrinking_text(self):
        day, month, time = (self.face[key] for key in ("day", "month", "time"))
        for view in (day, month):
            self.assertEqual(view["leftmargin"] + view["width"] / 2, 50)
            self.assertEqual(view["textsize"], 18)
            self.assertEqual(view["height"], 28)
        self.assertEqual(day["topmargin"] + day["height"], month["topmargin"])
        self.assertEqual((day["topmargin"] + month["topmargin"] + month["height"]) / 2,
                         time["topmargin"] + time["height"] / 2)
        for sample in ("00:00", "10:08", "12:59", "23:59", "11:11", "20:48"):
            clock = build.text_bounds(self.face, "time", sample, {})
            for key, text in (("day", "31"), ("month", "SEPT"), ("month", "MÄR")):
                date = build.text_bounds(self.face, key, text, {})
                self.assertGreaterEqual(clock[0] - date[2], 12, (sample, key, text))

    def test_reservoir_text_is_centered_without_moving_vertically_or_shrinking(self):
        label, amount = self.face["freetext3"], self.face["reservoir"]
        self.assertEqual((label["topmargin"], amount["topmargin"]), (323, 342))
        for view in (label, amount):
            self.assertEqual(view["leftmargin"] + view["width"] / 2, 200)
            self.assertEqual(view["gravity"], "center")
        self.assertEqual((label["textsize"], amount["textsize"]), (12, 20))
        self.assertEqual((label["width"], amount["width"]), (108, 136))
        for key, text in (("freetext3", ""), ("reservoir", "250 U")):
            bounds = build.text_bounds(self.face, key, text, {})
            for x, y in itertools.product((bounds[0], bounds[2]), (bounds[1], bounds[3])):
                self.assertLess(math.hypot(x - 200, y - 200), 178)

    def test_relocated_date_still_follows_its_visibility_setting(self):
        shown = build.render(self.face, prefs={"key_show_date": True})
        hidden = build.render(self.face, prefs={"key_show_date": False})
        self.assertFalse(build.PREF_DEFAULTS["key_show_date"])
        for key in ("day", "month"):
            view = self.face[key]
            box = (view["leftmargin"], view["topmargin"], view["leftmargin"] + view["width"], view["topmargin"] + view["height"])
            self.assertNotEqual(shown.crop(box).tobytes(), hidden.crop(box).tobytes())
        self.assertEqual(shown.crop((90, 159, 342, 275)).tobytes(), hidden.crop((90, 159, 342, 275)).tobytes())

    def test_text_samples_fit_fields_and_the_round_screen(self):
        cases = {"time": ("23:59", "10:08", "12:59"), "timePeriod": ("AM", "PM"),
                 "sgv": ("106", "400", "22.2", "22,2", "---"), "timestamp": ("18'", "60'", "--"),
                 "day": ("1", "16", "31"), "month": ("JAN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEPT", "OKT", "OCT", "NOV", "DEZ"),
                 "cob2": ("18g", "120g", "120(40)g", "---"), "iob1": ("-12.34", "12,34"),
                 "iob2": ("-12.34", "12,34"), "loop": ("3'", "19'", "60'", "-"),
                 "reservoir": ("40 U", "200 U", "250 U", "500U", ""),
                 "basalRate": ("0.85U/h", "0,85U/h", "12.34U/h", "120%", "1000%", "")}
        ring = self.face["direction"]
        center = (ring["leftmargin"] + ring["width"] / 2, ring["topmargin"] + ring["height"] / 2)
        for detailed in (False, True):
            prefs = {"key_show_detailed_iob": detailed}
            for key, samples in cases.items():
                if key == "iob2" and detailed:
                    samples = ("(1.60|0.80)", "(-10.00|10.00)", "(-10,00|10,00)")
                for sample in samples:
                    with self.subTest(key=key, sample=sample, detailed=detailed):
                        bounds = build.text_bounds(self.face, key, sample, prefs)
                        self.assertLessEqual(bounds[2] - bounds[0], self.face[key]["width"])
                        for x, y in itertools.product((bounds[0], bounds[2]), (bounds[1], bounds[3])):
                             # Keep the earlier conservative lower-left reserve.
                             # Actual right-side glyph/gauge clearance is tested separately.
                            limit = 178 if x < 200 and y > 200 else 189
                            self.assertLess(math.hypot(x - 200, y - 200), limit)
                            if key in ("sgv", "timestamp"):
                                self.assertLess(math.hypot(x - center[0], y - center[1]), build.ART["trend"]["radius"] - 5)

    def test_key_text_groups_leave_visible_space(self):
        for detailed in (False, True):
            prefs = {"key_show_detailed_iob": detailed}
            values = build.SAMPLES | ({"iob1": "-12.34", "iob2": "(-10.00|10.00)"} if detailed else {})
            for first, second in (("sgv", "timestamp"), ("freetext1", "sgv"), ("basalRate", "time"),
                                   ("time", "timePeriod"), ("time", "cob2"), ("day", "month"),
                                   ("cob2", "freetext3"), ("freetext3", "reservoir"),
                                   ("freetext2", "basalRate"), ("basalRate", "loop"), ("loop", "time"),
                                   ("time", "iob1" if detailed else "iob2")):
                a = build.text_bounds(self.face, first, values.get(first, ""), prefs)
                b = build.text_bounds(self.face, second, values.get(second, ""), prefs)
                self.assertGreaterEqual(b[1] - a[3], 5, (first, second, detailed))
            if detailed:
                total = build.text_bounds(self.face, "iob1", values["iob1"], prefs)
                detail = build.text_bounds(self.face, "iob2", values["iob2"], prefs)
                self.assertGreaterEqual(detail[1] - total[3], 5)

    def test_all_text_colors_have_contrast_on_black(self):
        for color in (self.face["midColor"], self.face["highColor"], self.face["lowColor"]):
            self.assertGreaterEqual(contrast_on_black(color), 4.5)
        for view in self.face.values():
            if isinstance(view, dict) and "fontColor" in view:
                self.assertGreaterEqual(contrast_on_black(view["fontColor"]), 4.5)

    def test_ring_is_selected_and_tinted_by_native_trend(self):
        self.assertEqual(self.face["direction"], {"width": 146, "height": 146, "leftmargin": 59, "topmargin": 24, "visibility": "visible"})
        ring = self.face["direction"]
        self.assertEqual((ring["leftmargin"] + ring["width"] / 2, ring["topmargin"] + ring["height"] / 2), (132, 97))
        res_map = (self.shared / "ResFileMap.kt").read_text(encoding="utf-8")
        for name in build.TREND_ARROWS:
            self.assertIn(f'("{name}")', res_map)
        phone = (build.ROOT / "ui/src/commonMain/kotlin/app/aaps/ui/compose/overview/BgInfoSection.kt").read_text(encoding="utf-8")
        native = {key: (float(angle), int(count)) for key, angle, count in re.findall(
            r'TrendArrow\.(\w+)\s*-> ArcIndicator\(centerAngle = (-?[\d.]+)f, sweepAngle = sweepAngle, triangleCount = (\d+)\)', phone)}
        self.assertEqual(build.ART["trend"]["sweep"], float(re.search(r'val sweepAngle = ([\d.]+)f', phone).group(1)))
        for _, (key, angle, count) in build.TREND_ARROWS.items():
            if key != "NONE":
                self.assertEqual((angle, count), native[key])

    def test_ring_resources_are_transparent_white_masks_with_correct_highlight(self):
        center = build.ART["trend"]["size"] / 2
        radius = build.ART["trend"]["radius"]
        for name, (_, angle, count) in build.TREND_ARROWS.items():
            image = build.resource_image(name)
            self.assertEqual(image.size, (146, 146))
            self.assertEqual(image.getpixel((73, 73))[3], 0)
            self.assertTrue(all((r, g, b) == (255, 255, 255) for r, g, b, a in image.get_flattened_data() if a))
            self.assertEqual(len(build.trend_triangles(name)), count)
            for triangle in build.trend_triangles(name):
                for x, y in triangle:
                    # The inset keeps the larger triangle inside its image canvas.
                    outer = radius + build.ART["trend"]["stroke"] / 2 - build.ART["trend"]["arrowInset"] + build.ART["trend"]["arrowHeight"]
                    self.assertLessEqual(math.hypot(x - center, y - center), outer + 1e-9)
                    self.assertLessEqual(outer, center - 2)
            if angle is None:
                self.assertLess(image.getchannel("A").getextrema()[1], 100)
            else:
                x, y = build.point(center, radius, angle)
                self.assertGreater(image.getpixel((round(x), round(y)))[3], 220)
                x, y = build.point(center, radius, angle + 180)
                self.assertLess(image.getpixel((round(x), round(y)))[3], 100)

    def test_glucose_ring_is_six_pixels_thick_without_changing_its_radius(self):
        spec = build.ART["trend"]
        self.assertEqual((spec["size"], spec["radius"], spec["stroke"]), (146, 61, 6))
        image = build.resource_image("ArrowNone")
        center = spec["size"] // 2
        width = sum(image.getpixel((x, center))[3] > 30 for x in range(center))
        self.assertEqual(width, 6)
        # Compare the rendered track against the previous four-pixel version,
        # bypassing the resource cache without editing any source file.
        with patch.dict(spec, {"stroke": 4}):
            previous = Image.open(io.BytesIO(build.resource_bytes.__wrapped__("ArrowNone"))).convert("RGBA")
        previous_width = sum(previous.getpixel((x, center))[3] > 30 for x in range(center))
        self.assertEqual(previous_width, 4)
        self.assertGreater(width, previous_width)

    def test_trend_markers_are_larger_without_clipping_or_changing_the_plain_ring(self):
        spec = build.ART["trend"]
        self.assertEqual((spec["arrowHeight"], spec["arrowHalfBase"], spec["arrowInset"]), (8, 11, 1))
        old_style = {"size": 140, "arrowHeight": 6, "arrowHalfBase": 9, "arrowInset": 1}
        for name, (_, angle, count) in build.TREND_ARROWS.items():
            current = build.resource_image(name)
            triangles = build.trend_triangles(name)
            with patch.dict(spec, old_style):
                previous = Image.open(io.BytesIO(build.resource_bytes.__wrapped__(name))).convert("RGBA")
            if angle is None:
                # Three pixels of extra transparent canvas on each side;
                # the visible circle is unchanged at its original screen center.
                self.assertEqual(current.crop((3, 3, 143, 143)).tobytes(), previous.tobytes())
                continue
            self.assertEqual(len(triangles), count)
            for a, b, c in triangles:
                area = abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])) / 2
                self.assertAlmostEqual(area, 88)
                self.assertGreater(area, 6 * 9)
            self.assertGreater(sum(alpha > 200 for alpha in current.getchannel("A").get_flattened_data()),
                               sum(alpha > 200 for alpha in previous.getchannel("A").get_flattened_data()), name)
            for border in ((0, 0, 146, 1), (0, 145, 146, 146), (0, 0, 1, 146), (145, 0, 146, 146)):
                self.assertLess(current.crop(border).getchannel("A").getextrema()[1], 128, name)

    def test_no_direction_is_distinct_from_flat_and_respects_toggle(self):
        self.assertNotEqual(build.render(self.face, trend="ArrowNone").tobytes(), build.render(self.face).tobytes())
        self.assertEqual(build.render(self.face, state="missing").tobytes(), build.render(self.face, state="missing", trend="ArrowNone").tobytes())
        for name in build.TREND_ARROWS:
            self.assertEqual(build.render(self.face, trend=name, prefs={"key_show_direction": False}).tobytes(),
                             build.render(self.face, trend="ArrowNone", prefs={"key_show_direction": False}).tobytes())

    def test_preview_variants_render_and_keep_gauge_independent_of_glucose(self):
        baseline = build.render(self.face)
        for detailed, hour12 in itertools.product((False, True), repeat=2):
            for state in ("normal", "mmol", "high", "low", "stale", "missing"):
                image = build.render(self.face, state=state, detailed=detailed, hour12=hour12)
                self.assertEqual(image.size, (400, 400))
                for point in ((330, 330), (330, 70)):
                    self.assertEqual(image.getpixel(point), baseline.getpixel(point))
        for pref in set(build.VIEW_PREFS.values()):
            self.assertEqual(build.render(self.face, prefs={pref: False}).size, (400, 400))
        self.assertEqual(build.render(self.face, prefs={pref: False for pref in build.VIEW_PREFS.values()}).size, (400, 400))

    def test_reservoir_previews_change_length_but_never_cap_the_native_number(self):
        pictures = [build.render(self.face, reservoir=value) for value in (0, 40, 100, 160, 200, 250)]
        self.assertEqual(len({picture.tobytes() for picture in pictures}), 6)
        # Full-screen arc crops are equal above 200, while the native amount differs.
        for box in ((370, 192, 400, 400), (192, 376, 300, 400)):
            self.assertEqual(pictures[-2].crop(box).tobytes(), pictures[-1].crop(box).tobytes())
        field = self.face["reservoir"]
        box = (field["leftmargin"], field["topmargin"], field["leftmargin"] + field["width"], field["topmargin"] + field["height"])
        self.assertNotEqual(pictures[-2].crop(box).tobytes(), pictures[-1].crop(box).tobytes())
        # The phone's unknown value is zero plus an empty native reservoir string.
        self.assertEqual(build.render(self.face, reservoir=None).tobytes(), pictures[0].tobytes())

    def test_zip_is_deterministic_complete_and_current(self):
        package = build.archive_bytes(self.face)
        self.assertEqual(package, build.archive_bytes(self.face))
        self.assertEqual(package, build.ASSET.read_bytes())
        with zipfile.ZipFile(io.BytesIO(package)) as archive:
            self.assertIsNone(archive.testzip())
            expected = {"CustomWatchface.json", "CustomWatchface.png", *(build.resource_filename(name) for name in build.RESOURCE_NAMES)}
            self.assertEqual(set(archive.namelist()), expected)
            self.assertEqual(len(archive.namelist()), len(expected))
            self.assertEqual(json.loads(archive.read("CustomWatchface.json")), self.face)
            self.assertEqual(archive.read("CustomWatchface.png"), (build.SOURCE / "CustomWatchface.png").read_bytes())
            for name in build.RESOURCE_NAMES:
                filename = build.resource_filename(name)
                self.assertEqual(archive.read(filename), build.resource_bytes(name))
                if filename.endswith(".svg"):
                    ET.fromstring(archive.read(filename))
                else:
                    Image.open(io.BytesIO(archive.read(filename))).verify()
            for rule in self.face["dynPref"]:
                for value in (False, True):
                    prefs = {self.face["dynPref"][rule]["prefKey"]: value}
                    style = build.resolve_pref(self.face, rule, prefs)
                    for key, name in style.items():
                        if re.fullmatch(r"image\d+|invalidImage", key):
                            self.assertIn(build.resource_filename(name), expected)


if __name__ == "__main__":
    unittest.main()
