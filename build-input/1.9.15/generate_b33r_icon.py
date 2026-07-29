#!/usr/bin/env python3
from __future__ import annotations

import math
import pathlib
import random
import sys

from PIL import Image, ImageDraw, ImageFont


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    path = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    )
    return ImageFont.truetype(path, size)


def fit_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int) -> ImageFont.FreeTypeFont:
    size = start
    while size > 20:
        candidate = font(size, True)
        box = draw.textbbox((0, 0), text, font=candidate, stroke_width=max(1, size // 50))
        if box[2] - box[0] <= max_width:
            return candidate
        size -= 2
    return font(20, True)


def rounded_gradient(size: int = 1024) -> Image.Image:
    image = Image.new("RGBA", (size, size), (3, 3, 3, 255))
    pixels = image.load()
    center = size / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - center, y - center) / (size * 0.72)
            glow = max(0.0, 1.0 - d)
            value = int(5 + 24 * glow)
            pixels[x, y] = (value, value, value, 255)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=184, fill=255)
    image.putalpha(mask)
    return image


def draw_icon() -> Image.Image:
    size = 1024
    image = rounded_gradient(size)
    draw = ImageDraw.Draw(image)

    gold = "#FFB319"
    bright_gold = "#FFD45A"
    deep_gold = "#A95B00"
    cream = "#FFF1C8"
    dark = "#090909"
    screen_dark = "#111111"

    # Premium rounded launcher frame.
    draw.rounded_rectangle((20, 20, 1003, 1003), radius=176, outline=deep_gold, width=34)
    draw.rounded_rectangle((30, 28, 993, 993), radius=165, outline=gold, width=18)
    draw.rounded_rectangle((48, 45, 975, 975), radius=150, outline=bright_gold, width=5)

    # Antenna and crown.
    draw.line((410, 244, 326, 158), fill=deep_gold, width=24)
    draw.line((614, 244, 700, 158), fill=deep_gold, width=24)
    draw.line((410, 241, 326, 155), fill=bright_gold, width=12)
    draw.line((614, 241, 700, 155), fill=bright_gold, width=12)
    draw.ellipse((300, 128, 350, 178), fill=bright_gold, outline=deep_gold, width=7)
    draw.ellipse((676, 128, 726, 178), fill=bright_gold, outline=deep_gold, width=7)
    draw.pieslice((402, 208, 622, 310), 180, 360, fill=gold, outline=deep_gold, width=10)

    # Mug handle behind the screen.
    draw.rounded_rectangle((760, 374, 930, 680), radius=72, outline=deep_gold, width=42)
    draw.rounded_rectangle((765, 378, 922, 674), radius=66, outline=gold, width=22)

    # TV/mug body.
    body = (142, 255, 840, 788)
    draw.rounded_rectangle(body, radius=104, fill=dark, outline=deep_gold, width=34)
    draw.rounded_rectangle((157, 270, 825, 773), radius=91, outline=gold, width=16)
    draw.rounded_rectangle((178, 300, 805, 738), radius=76, fill=screen_dark, outline="#333333", width=7)

    # Amber beer field with a subtle vertical gradient.
    beer_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(beer_mask).rounded_rectangle((194, 344, 789, 716), radius=58, fill=255)
    beer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bp = beer.load()
    for y in range(344, 717):
        t = (y - 344) / 372
        r = int(153 - 55 * t)
        g = int(78 - 37 * t)
        b = int(4 + 3 * t)
        for x in range(194, 790):
            bp[x, y] = (r, g, b, 255)
    image.alpha_composite(Image.composite(beer, Image.new("RGBA", image.size), beer_mask))
    draw = ImageDraw.Draw(image)

    # Foam cap and drip.
    draw.rounded_rectangle((194, 317, 789, 421), radius=50, fill=cream)
    for cx, cy, radius in [(246, 383, 42), (340, 380, 56), (444, 391, 50), (548, 379, 62), (663, 386, 54), (748, 380, 38)]:
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=cream)
    draw.rounded_rectangle((706, 380, 759, 470), radius=25, fill=cream)

    # Deterministic bubbles for texture without visual clutter.
    random.seed(33)
    for _ in range(70):
        x = random.randint(225, 760)
        y = random.randint(445, 690)
        radius = random.randint(2, 7)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline="#D78A16", width=2)
    for _ in range(28):
        x = random.randint(220, 765)
        y = random.randint(332, 405)
        radius = random.randint(3, 9)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline="#E5CA91", width=2)

    # B33R wordmark.
    label = "B33R"
    label_font = fit_font(draw, label, 520, 165)
    box = draw.textbbox((0, 0), label, font=label_font, stroke_width=4)
    x = (size - (box[2] - box[0])) // 2 - 6
    y = 424
    draw.text((x + 7, y + 9), label, font=label_font, fill="#5A2600", stroke_width=5, stroke_fill="#5A2600")
    draw.text((x, y), label, font=label_font, fill=bright_gold, stroke_width=4, stroke_fill="#FFF2C6")

    # Play glyph and streaming arcs.
    triangle = [(486, 624), (486, 704), (564, 664)]
    draw.polygon(triangle, fill=bright_gold, outline=deep_gold)
    for offset in (0, 20):
        draw.arc((401 - offset, 622 - offset, 485 - offset, 706 + offset), 105, 255, fill=gold, width=9)
        draw.arc((565 + offset, 622 - offset, 649 + offset, 706 + offset), -75, 75, fill=gold, width=9)

    # Feet and secondary label.
    draw.polygon([(260, 780), (321, 780), (284, 860), (245, 860)], fill=deep_gold)
    draw.polygon([(706, 780), (767, 780), (779, 860), (740, 860)], fill=deep_gold)
    draw.polygon([(268, 780), (312, 780), (282, 842), (257, 842)], fill=gold)
    draw.polygon([(714, 780), (758, 780), (768, 842), (743, 842)], fill=gold)

    iptv_font = font(62, True)
    iptv = "IPTV"
    box = draw.textbbox((0, 0), iptv, font=iptv_font)
    ix = (size - (box[2] - box[0])) // 2
    draw.text((ix, 865), iptv, font=iptv_font, fill=gold)
    draw.rounded_rectangle((218, 902, ix - 28, 914), radius=6, fill=deep_gold)
    draw.rounded_rectangle((ix + (box[2] - box[0]) + 28, 902, 806, 914), radius=6, fill=deep_gold)

    return image


def inset(source: Image.Image, size: int, ratio: float) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * (1 - 2 * ratio))
    art = source.resize((inner, inner), Image.Resampling.LANCZOS)
    offset = (size - inner) // 2
    canvas.alpha_composite(art, (offset, offset))
    return canvas


def write_resources(project_root: pathlib.Path) -> None:
    source = draw_icon()
    res = project_root / "app/src/main/res"

    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = res / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        inset(source, size, 0.035).save(folder / "ic_launcher.png", optimize=True)
        round_icon = inset(source, size, 0.06)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        round_icon.putalpha(Image.composite(round_icon.getchannel("A"), Image.new("L", (size, size), 0), mask))
        round_icon.save(folder / "ic_launcher_round.png", optimize=True)

    nodpi = res / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    inset(source, 320, 0.02).save(nodpi / "b33r_login_logo.png", optimize=True)
    adaptive = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    art = source.resize((318, 318), Image.Resampling.LANCZOS)
    adaptive.alpha_composite(art, ((432 - 318) // 2, (432 - 318) // 2))
    adaptive.save(nodpi / "ic_launcher_foreground_art.png", optimize=True)

    banner = Image.new("RGB", (320, 180), "#070707")
    banner_draw = ImageDraw.Draw(banner)
    for y in range(180):
        value = int(7 + 14 * (1 - y / 179))
        banner_draw.line((0, y, 319, y), fill=(value, value, value))
    banner.paste(source.resize((146, 146), Image.Resampling.LANCZOS).convert("RGB"), (9, 17))
    banner_draw.text((164, 53), "B33R", font=font(37, True), fill="#FFB31A", stroke_width=1, stroke_fill="#6B3C00")
    banner_draw.text((166, 98), "IPTV", font=font(24, True), fill="#F6F6F6")
    banner_draw.rounded_rectangle((164, 132, 300, 139), radius=3, fill="#FFB31A")
    banner_paths = [res / "drawable/tv_banner.png", *sorted(res.glob("drawable-*/tv_banner.png"))]
    for path in banner_paths:
        path.parent.mkdir(parents=True, exist_ok=True)
        banner.save(path, optimize=True)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: generate_b33r_icon.py <android-project-root>")
    write_resources(pathlib.Path(sys.argv[1]))
