"""Build a CWF asset and layout previews with synthetic data, not a Wear renderer."""

from __future__ import annotations

import argparse
from functools import lru_cache
import io
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image, ImageDraw, ImageFont

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parents[2]
ASSET = ROOT / "plugins/configuration/src/androidMain/assets/AAPS Material.zip"
FONTS = ROOT / "wear/src/main/res/font"
STATIC_RESOURCES = ("MaterialDark.svg", "MaterialLight.svg", "MaterialDarkPlain.svg", "MaterialLightPlain.svg", "Phone.svg", "Rig.svg")
# Match the native default without changing a user's saved watch preference.
PREF_DEFAULTS = {"key_match_divider": False}
# Same positions and triangle counts as BgInfoSection.toArcIndicator for the
# seven directions that the existing CWF renderer can distinguish.
TREND_ARROWS = {
    "ArrowNone": ("NONE", None, 0),
    "ArrowFlat": ("FLAT", 0, 1),
    "Arrow45Up": ("FORTY_FIVE_UP", -45, 1),
    "ArrowSingleUp": ("SINGLE_UP", -90, 1),
    "ArrowDoubleUp": ("DOUBLE_UP", -90, 2),
    "Arrow45Down": ("FORTY_FIVE_DOWN", 45, 1),
    "ArrowSingleDown": ("SINGLE_DOWN", 90, 1),
    "ArrowDoubleDown": ("DOUBLE_DOWN", 90, 2),
}
RING_RADIUS = 192
RING_STROKE = 5
RING_ARROW_HEIGHT = 4  # Shorter tips keep their outer radius at 197 on the larger ring.
RING_SWEEP = 40
RING_TRACK_ALPHA = round(255 * 0.3)
RESOURCES = (*STATIC_RESOURCES, *(f"{name}.png" for name in TREND_ARROWS))
VIEW_PREFS = {
    "day": "key_show_date", "month": "key_show_date",
    "sgv": "key_show_bg", "direction": "key_show_direction",
    "timestamp": "key_show_ago", "loop": "key_show_loop_status",
    "delta": "key_show_delta", "avg_delta": "key_show_avg_delta",
    "uploader_battery": "key_show_uploader_battery", "rig_battery": "key_show_rig_battery",
    "cob1": "key_show_cob", "cob2": "key_show_cob",
    "iob1": "key_show_iob", "iob2": "key_show_iob",
    "basalRate": "key_show_temp_basal", "bgi": "key_show_bgi",
}
SAMPLES = {
    "time": "10:08", "day": "14", "month": "OCT", "sgv": "106",
    "loop": "3'", "timestamp": "2'", "uploader_battery": "82%", "rig_battery": "67%",
    "delta": "+3", "avg_delta": "+1.8", "cob1": "COB", "cob2": "18g",
    "basalRate": "0.85U/h", "bgi": "-1.2", "iob1": "IOB", "iob2": "2.40",
    "timePeriod": "AM",
}


def load_face() -> dict:
    return json.loads((SOURCE / "CustomWatchface.json").read_text(encoding="utf-8"))


def resolve_pref(face: dict, key: str, prefs: dict) -> dict:
    """Resolve the selected branches like CustomWatchface.buildDynPrefs."""
    result = {}
    visited = set()
    while key:
        if key in visited:
            raise ValueError(f"Preference cycle: {key}")
        visited.add(key)
        rule = face["dynPref"][key]
        branch = rule[str(prefs.get(rule["prefKey"], PREF_DEFAULTS.get(rule["prefKey"], True))).lower()]
        result.update({k: v for k, v in branch.items() if k != "dynPref"})
        key = branch.get("dynPref", "")
    return result


def font_for(view: dict, size: int):
    name = view.get("font", "roboto_condensed_regular")
    return ImageFont.truetype(str(FONTS / f"{name}.ttf"), size)


def trend_triangles(name: str):
    """Adapt AAPS 4's outward triangles to leave space at the display edge."""
    _, angle, count = TREND_ARROWS[name]
    triangles = []
    half_base = RING_STROKE * 1.6
    height = RING_ARROW_HEIGHT
    base_distance = RING_RADIUS + RING_STROKE * 0.2
    spacing = math.degrees(half_base * 1.6 / RING_RADIUS)
    for index in range(count):
        radians = math.radians(angle + (index - (count - 1) / 2) * spacing)
        dx, dy = math.cos(radians), math.sin(radians)
        base_x, base_y = 200 + base_distance * dx, 200 + base_distance * dy
        triangles.append(((base_x + height * dx, base_y + height * dy),
                          (base_x - half_base * dy, base_y + half_base * dx),
                          (base_x + half_base * dy, base_y - half_base * dx)))
    return triangles


def render_trend(name: str, scale=3):
    """White/alpha bitmap: the native Wear renderer applies the live BG color."""
    _, angle, _ = TREND_ARROWS[name]
    image = Image.new("RGBA", (400 * scale, 400 * scale))
    draw = ImageDraw.Draw(image)
    # Pillow's stroke is inside the box; expand it by half a stroke to center it.
    outer_radius = RING_RADIUS + RING_STROKE / 2
    box = tuple(n * scale for n in (200 - outer_radius, 200 - outer_radius,
                                    200 + outer_radius, 200 + outer_radius))
    draw.ellipse(box, outline=(255, 255, 255, RING_TRACK_ALPHA), width=RING_STROKE * scale)
    if angle is not None:
        start = (angle - RING_SWEEP / 2) % 360
        draw.arc(box, start, start + RING_SWEEP, fill="white", width=RING_STROKE * scale)
        for end in (angle - RING_SWEEP / 2, angle + RING_SWEEP / 2):
            radians = math.radians(end)
            x = (200 + RING_RADIUS * math.cos(radians)) * scale
            y = (200 + RING_RADIUS * math.sin(radians)) * scale
            radius = RING_STROKE / 2 * scale
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill="white")
        for triangle in trend_triangles(name):
            draw.polygon([(x * scale, y * scale) for x, y in triangle], fill="white")
    return image


@lru_cache(maxsize=len(TREND_ARROWS))
def trend_resource_bytes(name: str) -> bytes:
    output = io.BytesIO()
    render_trend(name).resize((400, 400), Image.Resampling.LANCZOS).save(output, format="PNG")
    return output.getvalue()


def draw_svg(image, name: str, scale: int, origin=(0, 0)):
    """Render our rectangle-only SVGs; reject additions this preview cannot show."""
    svg = ET.fromstring((SOURCE / name).read_bytes())
    draw = ImageDraw.Draw(image)
    for node in svg:
        if node.tag.split("}")[-1] != "rect":
            raise ValueError("The preview supports only SVG rectangles")
        x = (float(node.get("x", 0)) + origin[0]) * scale
        y = (float(node.get("y", 0)) + origin[1]) * scale
        box = (x, y, x + float(node.get("width")) * scale, y + float(node.get("height")) * scale)
        fill = node.get("fill", "#000000")
        draw.rounded_rectangle(
            box, radius=float(node.get("rx", 0)) * scale,
            fill=None if fill == "none" else fill, outline=node.get("stroke"),
            width=max(1, round(float(node.get("stroke-width", 1)) * scale)),
        )


def draw_text(image, face: dict, key: str, text: str, prefs: dict, scale: int, color=None):
    view = face[key]
    style = resolve_pref(face, view["dynPref"], prefs) if "dynPref" in view else {}
    text = style.get("textvalue1", view.get("textvalue", text))
    if not text:
        return
    size = int(style.get("textsize1", view.get("textsize", 22))) * scale
    font = font_for(view, size)
    color = color or style.get("fontColor1", view.get("fontColor", "#757575"))
    x, y = view["leftmargin"] * scale, view["topmargin"] * scale
    width, height = view["width"] * scale, view["height"] * scale
    layer = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(layer)
    bounds = draw.textbbox((0, 0), text, font=font)
    # Clip within the configured view. Do not shrink text to hide a sizing error.
    tx = 0 if view.get("gravity") == "left" else (width - (bounds[2] - bounds[0])) / 2
    ty = (height - (bounds[3] - bounds[1])) / 2 - bounds[1]
    draw.text((tx, ty), text, font=font, fill=color)
    image.alpha_composite(layer, (x, y))


def render(face: dict, *, dark=True, state="normal", detailed=False, hour12=False, prefs=None, trend="ArrowFlat"):
    """Design preview only. Android text metrics, graph and icons may differ."""
    scale = 3
    values = dict(SAMPLES)
    settings = {key: True for key in VIEW_PREFS.values()}
    settings.update(PREF_DEFAULTS)
    settings.update(key_dark=dark, key_show_detailed_iob=detailed)
    settings.update(prefs or {})
    if detailed:
        values.update(iob1="2.40", iob2="(1.60|0.80)")
    if state == "mmol":
        values.update(sgv="5.9", delta="+0.2", avg_delta="+0.1", bgi="-0.1")
    elif state == "high":
        values.update(sgv="248", delta="+8", avg_delta="+6.2")
    elif state == "low":
        values.update(sgv="58", delta="-4", avg_delta="-3.1")
    elif state == "stale":
        values.update(timestamp="18'", loop="19'", uploader_battery="12%")
    elif state == "missing":
        values.update(sgv="---", timestamp="--", loop="-")
        trend = "ArrowNone"
    palette = resolve_pref(face, face["dynPrefColor"], settings)
    bg_color = palette[{"high": "highColor", "low": "lowColor"}.get(state, "midColor")]
    image = Image.new("RGBA", (400 * scale, 400 * scale))
    background = resolve_pref(face, face["background"]["dynPref"], settings)["image1"]
    draw_svg(image, f"{background}.svg", scale)

    # Synthetic chart: show history, target grid, carbs and basal overlay.
    chart = face["chart"]
    x, y, w, h = (chart[k] for k in ("leftmargin", "topmargin", "width", "height"))
    draw = ImageDraw.Draw(image)
    for offset in (20, 45, 70):
        draw.line([(x * scale, (y + offset) * scale), ((x + w) * scale, (y + offset) * scale)],
                  fill=palette["gridColor"], width=scale)
    draw.rectangle((x * scale, (y + h - 13) * scale, (x + w) * scale, (y + h - 5) * scale),
                   fill=palette["basalBackgroundColor"])
    draw.line([(x * scale, (y + h - 10) * scale), ((x + w) * scale, (y + h - 10) * scale)],
              fill=palette["basalCenterColor"], width=scale)
    trace = (50, 54, 49, 39, 35, 38, 32, 29, 34, 37, 35, 32, 30)
    points = [((x + i * (w - 12) / (len(trace) - 1) + 6) * scale, (y + n) * scale) for i, n in enumerate(trace)]
    draw.line(points, fill=palette["midColor"], width=2 * scale)
    for px, py in points:
        draw.ellipse((px - 2 * scale, py - 2 * scale, px + 2 * scale, py + 2 * scale), fill=palette["midColor"])
    px, py = points[4]
    draw.ellipse((px - 3 * scale, py - 9 * scale, px + 3 * scale, py - 3 * scale), fill=palette["carbColor"])
    graph_font = font_for({"font": "roboto_condensed_regular"}, 12 * scale)
    for label, offset in (("−2h", 6), ("−1h", 94), ("now", w - 27)):
        draw.text(((x + offset) * scale, (y + h - 1) * scale), label, font=graph_font, anchor="ls", fill=palette["gridColor"])

    for key, view in face.items():
        if not isinstance(view, dict) or view.get("visibility") != "visible":
            continue
        if key in VIEW_PREFS and not settings[VIEW_PREFS[key]]:
            continue
        if key == "timePeriod" and not hour12:
            continue
        if "background" in view:
            draw_svg(image, f"{view['background']}.svg", scale, (view["leftmargin"], view["topmargin"]))
        if "textsize" in view:
            color = bg_color if key == "sgv" else None
            if key == "timestamp" and state in ("stale", "missing"):
                color = "#E53935"
            if key == "uploader_battery" and state == "stale":
                color = palette["lowBatColor"]
            if key == "loop":
                bx, by = view["leftmargin"] * scale, view["topmargin"] * scale
                bw, bh = view["width"] * scale, view["height"] * scale
                loop_color = "#888888" if state == "missing" else "#CF0000" if state == "stale" else "#00CF00"
                draw.arc((bx + scale, by + scale, bx + bw - scale, by + bh - scale), 25, 155, fill=loop_color, width=3 * scale)
                draw.arc((bx + scale, by + scale, bx + bw - scale, by + bh - scale), 205, 335, fill=loop_color, width=3 * scale)
            draw_text(image, face, key, values.get(key, ""), settings, scale, color)
    if settings["key_show_direction"]:
        # Preview the same generated bitmap/alpha mask that is packaged for Wear.
        ring = Image.open(io.BytesIO(trend_resource_bytes(trend))).resize(image.size, Image.Resampling.LANCZOS)
        tinted = Image.new("RGBA", ring.size, bg_color)
        tinted.putalpha(ring.getchannel("A"))
        image.alpha_composite(tinted)
    if state == "stale" and settings["key_show_bg"]:
        sgv = face["sgv"]
        text_width = font_for(sgv, sgv["textsize"]).getlength(values["sgv"])
        center_x = sgv["leftmargin"] + sgv["width"] / 2
        center_y = sgv["topmargin"] + sgv["height"] / 2
        draw.line([((center_x - text_width / 2) * scale, center_y * scale),
                   ((center_x + text_width / 2) * scale, center_y * scale)],
                  fill=bg_color, width=3 * scale)
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).ellipse((0, 0, 400 * scale - 1, 400 * scale - 1), fill=255)
    image.putalpha(mask)
    return image.resize((400, 400), Image.Resampling.LANCZOS)


def archive_bytes(face: dict) -> bytes:
    preview = io.BytesIO()
    render(face).save(preview, format="PNG")
    files = {"CustomWatchface.json": (SOURCE / "CustomWatchface.json").read_bytes(),
             "CustomWatchface.png": preview.getvalue()}
    files.update({name: (SOURCE / name).read_bytes() for name in STATIC_RESOURCES})
    files.update({f"{name}.png": trend_resource_bytes(name) for name in TREND_ARROWS})
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 14, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    return output.getvalue()


def write_previews(face: dict):
    render(face).save(SOURCE / "CustomWatchface.png")
    sheet = Image.new("RGB", (1280, 948), "#101713")
    draw = ImageDraw.Draw(sheet)
    heading = font_for({}, 24)
    draw.text((28, 14), "AAPS MATERIAL / DESIGN PREVIEW / SYNTHETIC DATA", font=heading, fill="#F0F5F1")
    states = (("Dark · flat", {}), ("Light · mmol/L · rising", {"dark": False, "state": "mmol", "trend": "Arrow45Up"}),
              ("Detailed IOB · double up", {"detailed": True, "hour12": True, "trend": "ArrowDoubleUp"}),
              ("High · rising", {"state": "high", "trend": "ArrowSingleUp"}), ("Low · falling", {"state": "low", "trend": "ArrowSingleDown"}),
              ("Stale · low phone battery", {"state": "stale"}))
    for index, (label, kwargs) in enumerate(states):
        x, y = 24 + (index % 3) * 420, 56 + (index // 3) * 444
        draw.text((x + 8, y), label, font=heading, fill="#BAC8BF")
        preview = render(face, **kwargs)
        sheet.paste(preview, (x, y + 32), preview)
    sheet.save(SOURCE / "preview-states.png")
    trend_sheet = Image.new("RGB", (1704, 948), "#101713")
    draw = ImageDraw.Draw(trend_sheet)
    draw.text((28, 14), "AAPS MATERIAL / TREND RING / SYNTHETIC DATA", font=heading, fill="#F0F5F1")
    for index, name in enumerate(TREND_ARROWS):
        x, y = 24 + (index % 4) * 420, 56 + (index // 4) * 444
        label = TREND_ARROWS[name][0].replace("_", " ")
        draw.text((x + 8, y), label, font=heading, fill="#BAC8BF")
        preview = render(face, trend=name)
        trend_sheet.paste(preview, (x, y + 32), preview)
    trend_sheet.save(SOURCE / "preview-trends.png")
    surface_sheet = Image.new("RGB", (864, 948), "#101713")
    draw = ImageDraw.Draw(surface_sheet)
    draw.text((28, 14), "MATCHING DIVIDER / SYNTHETIC DATA", font=heading, fill="#F0F5F1")
    states = (("Dark / OFF / Material", True, False), ("Dark / ON / plain", True, True),
              ("Light / OFF / Material", False, False), ("Light / ON / plain", False, True))
    for index, (label, dark, matching) in enumerate(states):
        x, y = 24 + (index % 2) * 420, 56 + (index // 2) * 444
        draw.text((x + 8, y), label, font=heading, fill="#BAC8BF")
        preview = render(face, dark=dark, prefs={"key_match_divider": matching})
        surface_sheet.paste(preview, (x, y + 32), preview)
    surface_sheet.save(SOURCE / "preview-surfaces.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check the committed asset without writing files")
    args = parser.parse_args()
    face = load_face()
    package = archive_bytes(face)
    if args.check:
        if not ASSET.exists() or ASSET.read_bytes() != package:
            raise SystemExit("AAPS Material.zip is missing or stale. Run the builder.")
        print("Asset matches its source files.")
        return
    ASSET.write_bytes(package)
    write_previews(face)
    print(f"Created {ASSET}")
    print(f"Design previews: {SOURCE / 'preview-states.png'}")


if __name__ == "__main__":
    main()
