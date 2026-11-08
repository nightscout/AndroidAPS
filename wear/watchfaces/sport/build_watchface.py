"""Build AAPS Sport CWF resources and synthetic design previews, not Android renders."""

from __future__ import annotations

import argparse
from functools import lru_cache
import io
import json
import math
from pathlib import Path
import re
import zipfile

from PIL import Image, ImageDraw, ImageFont
from resvg_py import svg_to_bytes

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parents[2]
ASSET = ROOT / "plugins/configuration/src/androidMain/assets/AAPS Sport.zip"
FONTS = ROOT / "wear/src/main/res/font"
ART = json.loads((SOURCE / "SportArtwork.json").read_text(encoding="utf-8"))
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
GAUGE_VALUES = tuple(range(0, ART["reservoir"]["maximum"] + 1, ART["reservoir"]["increment"]))
GAUGE_NAMES = tuple(f"Reservoir{value:03d}" for value in GAUGE_VALUES)
ICON_NAMES = tuple(ART["icons"]["sources"])
RESOURCE_NAMES = ("Background", "Empty", *GAUGE_NAMES, *TREND_ARROWS, *ICON_NAMES)
# AAPS sends the complete ZIP in one Wear MessageClient call. Keep headroom
# below Google's general 100 kB message guidance; delivery still needs a watch test.
MAX_PACKAGE_BYTES = 95_000
VIEW_PREFS = {
    "sgv": "key_show_bg", "direction": "key_show_direction", "timestamp": "key_show_ago",
    "cob1": "key_show_cob", "cob2": "key_show_cob", "iob1": "key_show_iob", "iob2": "key_show_iob",
    "day": "key_show_date", "month": "key_show_date", "loop": "key_show_loop_status",
    "reservoir": "key_show_reservoir_level", "basalRate": "key_show_temp_basal",
}
PREF_DEFAULTS = {key: True for key in VIEW_PREFS.values()} | {"key_show_date": False, "key_show_detailed_iob": False, "key_units": True}
SAMPLES = {"time": "10:08", "timePeriod": "AM", "sgv": "106", "timestamp": "2'", "day": "16", "month": "OCT",
           "cob2": "18g", "iob1": "IOB", "iob2": "2.40", "loop": "3'", "basalRate": "0.85U/h"}
BASELINE_TEXT_VIEWS = frozenset(("cob1", "cob2", "freetext4", "iob1", "iob2"))


def load_face():
    return json.loads((SOURCE / "CustomWatchface.json").read_text(encoding="utf-8"))


def resolve_pref(face, key, prefs):
    result, visited = {}, set()
    while key:
        if key in visited:
            raise ValueError(f"Preference cycle: {key}")
        visited.add(key)
        rule = face["dynPref"][key]
        branch = rule[str(prefs.get(rule["prefKey"], PREF_DEFAULTS.get(rule["prefKey"], True))).lower()]
        result.update({k: v for k, v in branch.items() if k != "dynPref"})
        key = branch.get("dynPref", "")
    return result


def style_for(face, key, prefs):
    view = face[key]
    return resolve_pref(face, view["dynPref"], prefs) if "dynPref" in view else {}


def select_drawable(style, value):
    """Model DynProvider's image index, including its exclusive upper bound."""
    if style["valueKey"] != "reservoir":
        return style["image1"]
    if value is None or math.isnan(value):
        return style["invalidImage"]
    count = 0
    while f"image{count + 1}" in style:
        count += 1
    steps = count if "invalidImage" in style else count - 1
    minimum, maximum = style["minData"], style["maxData"]
    value = minimum if value < minimum else maximum * 0.9999 if value >= maximum else value
    index = int(1 + (value - minimum) * steps / (maximum - minimum))
    return style[f"image{index}"]


def font_for(view, size):
    return ImageFont.truetype(str(FONTS / f"{view['font']}.ttf"), size)


def point(center, radius, angle):
    angle = math.radians(angle)
    return center + radius * math.cos(angle), center + radius * math.sin(angle)


def arc(draw, center, radius, stroke, start, sweep, color, scale):
    outer = radius + stroke / 2
    box = tuple(n * scale for n in (center - outer, center - outer, center + outer, center + outer))
    draw.arc(box, start, start + sweep, fill=color, width=stroke * scale)
    for angle in (start, start + sweep):
        x, y = point(center, radius, angle)
        r = stroke / 2
        draw.ellipse(tuple(n * scale for n in (x - r, y - r, x + r, y + r)), fill=color)


def trend_triangles(name):
    _, angle, count = TREND_ARROWS[name]
    spec = ART["trend"]
    center, radius = spec["size"] / 2, spec["radius"]
    half_base = spec["arrowHalfBase"]
    spacing = math.degrees(half_base * 2.5 / radius)
    result = []
    for index in range(count):
        radians = math.radians(angle + (index - (count - 1) / 2) * spacing)
        dx, dy = math.cos(radians), math.sin(radians)
        # Set the larger base slightly inside the ring so the tip stays on canvas.
        x, y = point(center, radius + spec["stroke"] / 2 - spec["arrowInset"], math.degrees(radians))
        result.append(((x + spec["arrowHeight"] * dx, y + spec["arrowHeight"] * dy),
                       (x - half_base * dy, y + half_base * dx), (x + half_base * dy, y - half_base * dx)))
    return result


def resource_filename(name):
    # Keep white trend masks as PNG: native bitmap tinting must remain unchanged.
    return f"{name}.svg" if name in GAUGE_NAMES or name in ICON_NAMES else f"{name}.png"


def gauge_svg(name):
    spec = ART["reservoir"]
    value = int(name.removeprefix("Reservoir"))
    left, top, right, bottom = spec["crop"]
    width, height = right - left, bottom - top
    center, radius = ART["size"] / 2, spec["radius"]

    def path(sweep, color):
        start = point(center, radius, spec["start"])
        end = point(center, radius, spec["start"] + sweep)
        clockwise = int(sweep > 0)
        return (f'<path fill="none" stroke="{color}" stroke-width="{spec["stroke"]}" stroke-linecap="round" '
                f'd="M {start[0]:.6f} {start[1]:.6f} A {radius} {radius} 0 0 {clockwise} {end[0]:.6f} {end[1]:.6f}"/>')

    body = path(spec["sweep"], spec["track"])
    if value > 0:
        body += path(spec["sweep"] * value / spec["maximum"], spec["color"])
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
            f'viewBox="{left} {top} {width} {height}">{body}</svg>').encode("utf-8")


def icon_svg(name):
    """Reuse the phone overview's actual IcCarbs/IcBolus paths, without app edits."""
    spec = ART["icons"]["sources"][name]
    source = (ROOT / spec["file"]).read_text(encoding="utf-8")
    blocks = re.findall(r"path\([\s\S]*?\)\s*\{([^{}]*)\}", source)
    if len(blocks) != 1 or "viewportWidth = 24f" not in source or "viewportHeight = 24f" not in source:
        raise ValueError(f"Unsupported AAPS icon structure: {spec['file']}")
    operations = {"moveTo": ("M", 2), "moveToRelative": ("m", 2), "lineTo": ("L", 2),
                  "lineToRelative": ("l", 2), "curveTo": ("C", 6), "curveToRelative": ("c", 6), "close": ("Z", 0)}
    commands = []
    for line in blocks[0].strip().splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r"\s*(\w+)\(([^)]*)\)\s*", line)
        if not match or match[1] not in operations:
            raise ValueError(f"Unsupported AAPS icon command: {line}")
        command, count = operations[match[1]]
        numbers = [float(n.strip().removesuffix("f")) for n in match[2].split(",") if n.strip()]
        if len(numbers) != count:
            raise ValueError(f"Wrong AAPS icon argument count: {line}")
        commands.append(command + " ".join(f"{n:g}" for n in numbers))
    size = ART["icons"]["size"]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 24 24">'
            f'<path fill="{spec["color"]}" d="{" ".join(commands)}"/></svg>').encode("utf-8")


@lru_cache(maxsize=len(RESOURCE_NAMES))
def resource_bytes(name):
    if name in GAUGE_NAMES:
        return gauge_svg(name)
    if name in ICON_NAMES:
        return icon_svg(name)
    scale = 4
    size = ART["trend"]["size"] if name in TREND_ARROWS else ART["size"]
    image = Image.new("RGBA", (size * scale, size * scale), ART["background"] if name == "Background" else (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    center = size / 2
    if name in TREND_ARROWS:
        spec = ART["trend"]
        radius, stroke = spec["radius"], spec["stroke"]
        outer = radius + stroke / 2
        box = tuple(n * scale for n in (center - outer, center - outer, center + outer, center + outer))
        draw.ellipse(box, outline=(255, 255, 255, spec["trackAlpha"]), width=stroke * scale)
        angle = TREND_ARROWS[name][1]
        if angle is not None:
            arc(draw, center, radius, stroke, angle - spec["sweep"] / 2, spec["sweep"], "white", scale)
            for triangle in trend_triangles(name):
                draw.polygon([(x * scale, y * scale) for x, y in triangle], fill="white")
    elif name == "Background":
        edge, ticks = ART["edge"], ART["ticks"]
        draw.ellipse(tuple(n * scale for n in (center - edge["radius"], center - edge["radius"], center + edge["radius"], center + edge["radius"])),
                     outline=edge["color"], width=edge["stroke"] * scale)
        for angle in ticks["angles"]:
            ends = [point(center, ticks["radius"] - offset, angle) for offset in (0, ticks["length"])]
            draw.line([(x * scale, y * scale) for x, y in ends], fill=ticks["color"], width=ticks["stroke"] * scale)
    elif name != "Empty":
        raise ValueError(f"Unknown resource: {name}")
    if name == "Empty":
        image = image.crop(tuple(n * scale for n in ART["reservoir"]["crop"]))
        size = image.width // scale
    output = io.BytesIO()
    image.resize((size, size), Image.Resampling.LANCZOS).save(output, format="PNG")
    return output.getvalue()


@lru_cache(maxsize=len(RESOURCE_NAMES))
def resource_image(name):
    data = resource_bytes(name)
    if resource_filename(name).endswith(".svg"):
        # Render the actual packaged SVG for local previews, not a separate artwork model.
        # resvg is not AndroidSVG; on-watch rendering remains unverified.
        data = svg_to_bytes(svg_string=data.decode("utf-8"))
    return Image.open(io.BytesIO(data)).convert("RGBA")


def baseline_y(font, height):
    """Center a font line, not each string's ink; descenders keep the same baseline."""
    ascent, descent = font.getmetrics()
    return (height + ascent - descent) / 2


def text_left(view, width, text_width):
    gravity = view.get("gravity", "center")
    return 0 if gravity == "left" else width - text_width if gravity == "right" else (width - text_width) / 2


def text_baseline(view, font, height):
    # Native LEFT/RIGHT map to START/END without CENTER_VERTICAL, so text is top-aligned.
    # Pillow metrics approximate Android's font padding; verify the result on the watch.
    return baseline_y(font, height) if view.get("gravity", "center") == "center" else font.getmetrics()[0]


def text_bounds(face, key, text, prefs):
    view, style = face[key], style_for(face, key, prefs)
    text = style.get("textvalue1", view.get("textvalue", text))
    if view.get("allCaps"):
        text = text.upper()
    font = font_for(view, style.get("textsize1", view["textsize"]))
    bounds = font.getbbox(text)
    width, height = font.getlength(text), bounds[3] - bounds[1]
    left = view["leftmargin"] + style.get("leftOffset1", 0) + text_left(view, view["width"], width)
    cy = view["topmargin"] + style.get("topOffset1", 0) + view["height"] / 2
    if key in BASELINE_TEXT_VIEWS:
        baseline = cy - view["height"] / 2 + text_baseline(view, font, view["height"])
        bounds = font.getbbox(text, anchor="ls")
        return left, baseline + bounds[1], left + width, baseline + bounds[3]
    top = cy - height / 2 if view.get("gravity", "center") == "center" else cy - view["height"] / 2
    return left, top, left + width, top + height


def draw_text(image, face, key, value, prefs, scale, color=None):
    view, style = face[key], style_for(face, key, prefs)
    text = style.get("textvalue1", view.get("textvalue", value))
    if view.get("allCaps"):
        text = text.upper()
    if not text:
        return
    font = font_for(view, style.get("textsize1", view["textsize"]) * scale)
    layer = Image.new("RGBA", (view["width"] * scale, view["height"] * scale))
    draw = ImageDraw.Draw(layer)
    bounds = font.getbbox(text)
    x = text_left(view, layer.width, font.getlength(text))
    if key in BASELINE_TEXT_VIEWS:
        y, anchor = text_baseline(view, font, layer.height), "ls"
    else:
        top = (layer.height - (bounds[3] - bounds[1])) / 2 if view.get("gravity", "center") == "center" else 0
        y, anchor = top - bounds[1], "la"
    draw.text((x, y), text, font=font, anchor=anchor, fill=color or style.get("fontColor1", view.get("fontColor", "#F3F7FA")))
    image.alpha_composite(layer, ((view["leftmargin"] + style.get("leftOffset1", 0)) * scale,
                                  (view["topmargin"] + style.get("topOffset1", 0)) * scale))


def render(face, *, state="normal", trend="ArrowFlat", detailed=False, hour12=False, prefs=None, values=None, reservoir=120):
    """Synthetic preview. Native loop icons, font metrics and stale styling may differ."""
    scale = 3
    settings = PREF_DEFAULTS | {"key_show_date": True, "key_show_detailed_iob": detailed, "key_units": state != "mmol"} | (prefs or {})
    samples = SAMPLES | ({"iob1": "2.40", "iob2": "(1.60|0.80)"} if settings["key_show_detailed_iob"] else {})
    # The checked phone code sends 0 and an empty string for unavailable reservoir.
    samples["reservoir"] = f"{reservoir:.0f} U" if reservoir is not None and reservoir > 0 else ""
    if state == "mmol":
        samples["sgv"] = "5.9"
    elif state == "high":
        samples["sgv"] = "248"
    elif state == "low":
        samples["sgv"] = "58"
    elif state == "stale":
        samples.update(timestamp="18'", loop="19'")
    elif state == "missing":
        samples.update(sgv="---", timestamp="--", loop="-")
        trend = "ArrowNone"
    samples.update(values or {})
    color = face[{"high": "highColor", "low": "lowColor"}.get(state, "midColor")]
    image = resource_image("Background").resize((400 * scale, 400 * scale), Image.Resampling.LANCZOS)
    # Match the native image lookup, then place the cropped gauge at its view origin.
    view = face["cover_chart"]
    name = select_drawable(style_for(face, "cover_chart", settings), 0 if reservoir is None else reservoir)
    gauge = resource_image(name).resize((view["width"] * scale, view["height"] * scale), Image.Resampling.LANCZOS)
    image.alpha_composite(gauge, (view["leftmargin"] * scale, view["topmargin"] * scale))
    if settings["key_show_direction"]:
        view = face["direction"]
        ring = resource_image(trend).resize((view["width"] * scale, view["height"] * scale), Image.Resampling.LANCZOS)
        tinted = Image.new("RGBA", ring.size, color)
        tinted.putalpha(ring.getchannel("A"))
        image.alpha_composite(tinted, (view["leftmargin"] * scale, view["topmargin"] * scale))
    for key, view in face.items():
        if not isinstance(view, dict) or "textsize" not in view or view.get("visibility") != "visible":
            continue
        if key in VIEW_PREFS and not settings[VIEW_PREFS[key]]:
            continue
        if key == "timePeriod" and not hour12:
            continue
        style = style_for(face, key, settings)
        if "image1" in style:
            # CWF supports a preference-controlled drawable as TextView background.
            drawable = resource_image(style["image1"]).resize((view["width"] * scale, view["height"] * scale), Image.Resampling.LANCZOS)
            image.alpha_composite(drawable, ((view["leftmargin"] + style.get("leftOffset1", 0)) * scale,
                                             (view["topmargin"] + style.get("topOffset1", 0)) * scale))
        if key == "loop":
            # Approximation only; the ZIP keeps the native grey/green/red loop drawable.
            draw = ImageDraw.Draw(image)
            x, y, w, h = (view[k] * scale for k in ("leftmargin", "topmargin", "width", "height"))
            loop_color = "#888888" if state == "missing" else "#CF0000" if state == "stale" else "#00CF00"
            for start in (25, 205):
                draw.arc((x + scale, y + scale, x + w - scale, y + h - scale), start, start + 130, fill=loop_color, width=3 * scale)
        text_color = color if key == "sgv" else face["reservoirColor"] if key == "reservoir" else "#FF0000" if key == "timestamp" and state in ("stale", "missing") else None
        draw_text(image, face, key, samples.get(key, ""), settings, scale, text_color)
    if state == "stale" and settings["key_show_bg"]:
        bounds = text_bounds(face, "sgv", samples["sgv"], settings)
        y = (bounds[1] + bounds[3]) / 2
        ImageDraw.Draw(image).line([(bounds[0] * scale, y * scale), (bounds[2] * scale, y * scale)], fill=color, width=3 * scale)
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
    image.putalpha(mask)
    return image.resize((400, 400), Image.Resampling.LANCZOS)


def preview_bytes(face):
    # Only the gallery thumbnail uses an indexed palette. Live field colors and
    # trend masks are not quantized. Full-color state sheets remain available.
    preview = io.BytesIO()
    render(face).quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(preview, format="PNG", optimize=True)
    return preview.getvalue()


def archive_bytes(face):
    files = {"CustomWatchface.json": (SOURCE / "CustomWatchface.json").read_bytes(), "CustomWatchface.png": preview_bytes(face)}
    files.update({resource_filename(name): resource_bytes(name) for name in RESOURCE_NAMES})
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 16, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    package = output.getvalue()
    if len(package) > MAX_PACKAGE_BYTES:
        raise ValueError(f"Watchface ZIP is {len(package)} bytes, over the {MAX_PACKAGE_BYTES}-byte transport budget.")
    return package


def write_sheet(face, path, title, states, columns):
    rows = math.ceil(len(states) / columns)
    sheet = Image.new("RGB", (24 + columns * 420, 64 + rows * 452), "#0B1014")
    draw = ImageDraw.Draw(sheet)
    font = font_for({"font": "roboto_condensed_regular"}, 22)
    draw.text((24, 14), f"AAPS SPORT / {title} / SYNTHETIC DATA", font=font, fill="#D8E1E7")
    for index, (label, kwargs) in enumerate(states):
        x, y = 24 + index % columns * 420, 64 + index // columns * 452
        draw.text((x + 8, y), label, font=font, fill="#ABB8C2")
        preview = render(face, **kwargs)
        sheet.paste(preview, (x, y + 32), preview)
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check the ZIP against the sources, without writing files")
    args = parser.parse_args()
    face = load_face()
    package = archive_bytes(face)
    if args.check:
        if not ASSET.exists() or ASSET.read_bytes() != package:
            raise SystemExit("AAPS Sport.zip is missing or stale. Run the builder.")
        print("AAPS Sport.zip matches its sources.")
        return
    ASSET.write_bytes(package)
    (SOURCE / "CustomWatchface.png").write_bytes(preview_bytes(face))
    write_sheet(face, SOURCE / "preview-states.png", "DESIGN PREVIEW", (
        ("Normal / mg/dL", {}), ("mmol/L / rising", {"state": "mmol", "trend": "Arrow45Up"}),
        ("12-hour / detailed IOB", {"detailed": True, "hour12": True}),
        ("High / double up", {"state": "high", "trend": "ArrowDoubleUp"}),
        ("Low / falling", {"state": "low", "trend": "ArrowSingleDown"}),
        ("Old reading / old loop", {"state": "stale"}),
        ("Missing reading", {"state": "missing"}),
        ("Larger / negative / temp basal", {"values": {"cob2": "120(40)g", "iob2": "-12.34", "timestamp": "60'", "basalRate": "120%"}}),
        ("Optional fields off", {"prefs": {key: False for key in VIEW_PREFS.values()}}),
    ), 3)
    write_sheet(face, SOURCE / "preview-trends.png", "TREND RING", tuple(
        (data[0].replace("_", " "), {"trend": name}) for name, data in TREND_ARROWS.items()), 4)
    write_sheet(face, SOURCE / "preview-reservoir.png", "RESERVOIR / FULL AT 200 U", (
        ("No amount / native zero", {"reservoir": None}),
        ("40 U / 20%", {"reservoir": 40}), ("100 U / 50%", {"reservoir": 100}),
        ("160 U / 80%", {"reservoir": 160}), ("200 U / full", {"reservoir": 200}),
        ("250 U / capped arc", {"reservoir": 250}),
    ), 3)
    print(f"Created {ASSET}")
    print(f"ZIP size: {len(package):,} bytes; budget: {MAX_PACKAGE_BYTES:,} bytes")
    print(f"Synthetic previews: {SOURCE / 'preview-states.png'}")


if __name__ == "__main__":
    main()
