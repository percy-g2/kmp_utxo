#!/usr/bin/env python3
"""Compose Play Store screenshots for UTXO from raw emulator captures."""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "raw-screenshots"
OUT = ROOT / "play-store"
OUT.mkdir(parents=True, exist_ok=True)

CANVAS_W, CANVAS_H = 1242, 2208

FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REG = "/System/Library/Fonts/Supplemental/Arial.ttf"

ICON_PATH = (
    ROOT / "androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp"
)


@dataclass
class Shot:
    index: int
    title: str
    tagline: str
    gradient: tuple[tuple[int, int, int], tuple[int, int, int]]


def _tone(rgb, factor):
    """Return a subtle lighter/darker variant of a base color."""
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


# Single base color per shot; background is a subtle same-hue gradient
# (base *1.08 at top → base *0.88 at bottom) so it reads as a solid fill
# with a gentle lift rather than two-tone.
BASES = {
    1: (226, 80, 80),    # Hero — warm coral red (matches original style)
    2: (226, 80, 80),    # Live Market — coral red
    3: (76, 110, 180),   # Pro Charts — denim blue
    4: (62, 136, 110),   # Depth & Signals — forest teal
    5: (158, 90, 180),   # Your Watchlist — soft violet
    6: (200, 148, 70),   # Make it Yours — warm amber
    7: (86, 104, 156),   # Glanceable — slate blue
    8: (90, 90, 115),    # Day or Night — neutral slate (under the split)
}

SHOTS = [
    Shot(1, "UTXO", "Real Time Insights",
         (_tone(BASES[1], 1.08), _tone(BASES[1], 0.88))),
    Shot(2, "Live Market", "Real-time prices, straight from Binance.",
         (_tone(BASES[2], 1.08), _tone(BASES[2], 0.88))),
    Shot(3, "Pro Charts", "Candlesticks from 1m to 1M.",
         (_tone(BASES[3], 1.08), _tone(BASES[3], 0.88))),
    Shot(4, "Depth & Signals", "Order book heatmap + 8 news sources.",
         (_tone(BASES[4], 1.08), _tone(BASES[4], 0.88))),
    Shot(5, "Your Watchlist", "Your Favourite, In Single Place",
         (_tone(BASES[5], 1.08), _tone(BASES[5], 0.88))),
    Shot(6, "Make it Yours", "Theme, sources, widgets — all tunable.",
         (_tone(BASES[6], 1.08), _tone(BASES[6], 0.88))),
    Shot(7, "Glanceable", "Home-screen widget with live sparklines.",
         (_tone(BASES[7], 1.08), _tone(BASES[7], 0.88))),
    Shot(8, "Day or Night", "System, light, and dark themes.",
         (_tone(BASES[8], 1.08), _tone(BASES[8], 0.88))),
]


def gradient_bg(c1, c2) -> Image.Image:
    img = Image.new("RGB", (CANVAS_W, CANVAS_H), c1)
    px = img.load()
    for y in range(CANVAS_H):
        t = y / (CANVAS_H - 1)
        r = int(c1[0] + (c2[0] - c1[0]) * t)
        g = int(c1[1] + (c2[1] - c1[1]) * t)
        b = int(c1[2] + (c2[2] - c1[2]) * t)
        for x in range(CANVAS_W):
            px[x, y] = (r, g, b)
    return img


def split_gradient_bg(c_left, c_right) -> Image.Image:
    """Horizontal two-tone gradient used for the light/dark comparison shot."""
    img = Image.new("RGB", (CANVAS_W, CANVAS_H))
    px = img.load()
    mid = CANVAS_W // 2
    band = 60
    for x in range(CANVAS_W):
        if x < mid - band:
            c = c_left
        elif x > mid + band:
            c = c_right
        else:
            t = (x - (mid - band)) / (2 * band)
            c = tuple(int(c_left[i] + (c_right[i] - c_left[i]) * t) for i in range(3))
        for y in range(CANVAS_H):
            px[x, y] = c
    return img


def rounded_mask(size, radius) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255
    )
    return mask


def wrap_in_device_frame(shot_img: Image.Image) -> Image.Image:
    """Wrap a raw 1080x2424 capture in a minimal Android-style phone frame."""
    bezel = 26
    corner = 110
    camera_w, camera_h = 260, 60

    w, h = shot_img.size
    frame_w, frame_h = w + bezel * 2, h + bezel * 2

    frame = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
    body_mask = rounded_mask((frame_w, frame_h), corner)
    body = Image.new("RGBA", (frame_w, frame_h), (10, 10, 10, 255))
    frame.paste(body, (0, 0), body_mask)

    inner_mask = rounded_mask((w, h), corner - bezel)
    rgba = shot_img.convert("RGBA")
    frame.paste(rgba, (bezel, bezel), inner_mask)

    # Front camera pill
    cam = Image.new("RGBA", (camera_w, camera_h), (0, 0, 0, 0))
    ImageDraw.Draw(cam).rounded_rectangle(
        (0, 0, camera_w - 1, camera_h - 1), radius=camera_h // 2, fill=(0, 0, 0, 255)
    )
    frame.paste(cam, ((frame_w - camera_w) // 2, bezel + 14), cam)

    # Shadow
    shadow = Image.new("RGBA", (frame_w + 40, frame_h + 40), (0, 0, 0, 0))
    shadow_mask = rounded_mask((frame_w, frame_h), corner)
    shadow.paste(Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 120)),
                 (20, 20), shadow_mask)
    shadow = shadow.filter(ImageFilter.GaussianBlur(25))

    composed = Image.new("RGBA", shadow.size, (0, 0, 0, 0))
    composed.alpha_composite(shadow)
    composed.alpha_composite(frame, (20, 20))
    return composed


def draw_text_block(canvas: Image.Image, title: str, tagline: str) -> None:
    title_font = ImageFont.truetype(FONT_BOLD, 108)
    tag_font = ImageFont.truetype(FONT_REG, 48)

    # Shadow helper
    def draw_shadowed(text, font, y, fill):
        # Multi-layer soft shadow
        shadow_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(shadow_layer)
        bbox = sdraw.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        x = (CANVAS_W - tw) // 2
        sdraw.text((x, y + 4), text, font=font, fill=(0, 0, 0, 160))
        shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(6))
        canvas.alpha_composite(shadow_layer)
        ImageDraw.Draw(canvas).text((x, y), text, font=font, fill=fill)

    draw_shadowed(title, title_font, 120, (255, 255, 255, 255))
    draw_shadowed(tagline, tag_font, 260, (255, 255, 255, 235))


def place_framed_device(canvas: Image.Image, framed: Image.Image,
                        top_y: int = 420) -> None:
    max_h = CANVAS_H - top_y - 80
    scale = max_h / framed.size[1]
    new_w = int(framed.size[0] * scale)
    new_h = int(framed.size[1] * scale)
    scaled = framed.resize((new_w, new_h), Image.LANCZOS)
    x = (CANVAS_W - new_w) // 2
    canvas.alpha_composite(scaled, (x, top_y))


def compose_standard(shot: Shot, raw: Image.Image) -> Image.Image:
    bg = gradient_bg(*shot.gradient).convert("RGBA")
    draw_text_block(bg, shot.title, shot.tagline)
    framed = wrap_in_device_frame(raw)
    place_framed_device(bg, framed, top_y=420)
    return bg.convert("RGB")


def compose_hero(shot: Shot) -> Image.Image:
    bg = gradient_bg(*shot.gradient).convert("RGBA")

    # App icon huge in center
    icon = Image.open(ICON_PATH).convert("RGBA")
    target = 720
    icon = icon.resize((target, target), Image.LANCZOS)

    # Soft white glow behind icon
    glow = Image.new("RGBA", (target + 240, target + 240), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse((0, 0, glow.size[0] - 1, glow.size[1] - 1),
                  fill=(255, 255, 255, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(60))

    # Squircle-masked icon on white card
    card_size = target + 60
    card = Image.new("RGBA", (card_size, card_size), (0, 0, 0, 0))
    card_mask = rounded_mask((card_size, card_size), 160)
    card_bg = Image.new("RGBA", (card_size, card_size), (255, 255, 255, 255))
    card.paste(card_bg, (0, 0), card_mask)
    card.paste(icon, ((card_size - target) // 2, (card_size - target) // 2), icon)

    icon_shadow = Image.new("RGBA", (card_size + 60, card_size + 60), (0, 0, 0, 0))
    sm = rounded_mask((card_size, card_size), 160)
    icon_shadow.paste(Image.new("RGBA", (card_size, card_size), (0, 0, 0, 160)),
                      (30, 40), sm)
    icon_shadow = icon_shadow.filter(ImageFilter.GaussianBlur(30))

    cx = CANVAS_W // 2
    cy = CANVAS_H // 2 + 40

    bg.alpha_composite(glow, (cx - glow.size[0] // 2, cy - glow.size[1] // 2))
    bg.alpha_composite(icon_shadow,
                       (cx - icon_shadow.size[0] // 2, cy - icon_shadow.size[1] // 2))
    bg.alpha_composite(card, (cx - card_size // 2, cy - card_size // 2))

    # Title above icon, tagline below
    title_font = ImageFont.truetype(FONT_BOLD, 220)
    tag_font = ImageFont.truetype(FONT_REG, 56)

    def draw_centered(text, font, y, fill, shadow_blur=8):
        shadow_layer = Image.new("RGBA", bg.size, (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(shadow_layer)
        bbox = sdraw.textbbox((0, 0), text, font=font)
        tw = bbox[2] - bbox[0]
        x = (CANVAS_W - tw) // 2
        sdraw.text((x, y + 6), text, font=font, fill=(0, 0, 0, 180))
        shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(shadow_blur))
        bg.alpha_composite(shadow_layer)
        ImageDraw.Draw(bg).text((x, y), text, font=font, fill=fill)

    draw_centered(shot.title, title_font, 260, (255, 255, 255, 255), 14)
    draw_centered(shot.tagline, tag_font,
                  cy + card_size // 2 + 80, (255, 255, 255, 235))

    return bg.convert("RGB")


def draw_sparkline(size: tuple[int, int], color: tuple[int, int, int],
                   seed: int) -> Image.Image:
    rnd = random.Random(seed)
    w, h = size
    points = 24
    values = [rnd.random() for _ in range(points)]
    # Smooth with a moving average
    smoothed = []
    for i in range(points):
        window = values[max(0, i - 2): min(points, i + 3)]
        smoothed.append(sum(window) / len(window))
    vmin, vmax = min(smoothed), max(smoothed)
    rng = max(vmax - vmin, 0.001)

    img = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    step = w / (points - 1)
    xs = [i * step for i in range(points)]
    ys = [h - 6 - ((v - vmin) / rng) * (h - 14) for v in smoothed]

    # Filled area
    fill_pts = list(zip(xs, ys)) + [(w, h), (0, h)]
    draw.polygon(fill_pts, fill=(*color, 70))

    # Line
    for i in range(points - 1):
        draw.line((xs[i], ys[i], xs[i + 1], ys[i + 1]), fill=(*color, 255), width=4)
    return img


def render_synthetic_widget(width: int, height: int,
                            rows) -> Image.Image:
    """Render a 4x2 style Android widget with up to 3-4 favorite rows."""
    widget = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    mask = rounded_mask((width, height), 56)
    bg = Image.new("RGBA", (width, height), (24, 24, 26, 245))
    widget.paste(bg, (0, 0), mask)
    draw = ImageDraw.Draw(widget)

    title_font = ImageFont.truetype(FONT_BOLD, 40)
    sym_font = ImageFont.truetype(FONT_BOLD, 48)
    quote_font = ImageFont.truetype(FONT_REG, 30)
    vol_font = ImageFont.truetype(FONT_REG, 28)
    price_font = ImageFont.truetype(FONT_BOLD, 36)
    pct_font = ImageFont.truetype(FONT_BOLD, 36)

    # Header
    draw.text((36, 28), "UTXO Favorites", font=title_font, fill=(245, 245, 245))
    draw.rounded_rectangle((width - 96, 28, width - 36, 88),
                           radius=30, outline=(140, 140, 150), width=3)
    draw.text((width - 82, 40), "↻", font=title_font, fill=(200, 200, 210))

    # Rows
    row_top = 110
    row_gap = 8
    row_h = (height - row_top - 24) // max(len(rows), 1) - row_gap
    for i, r in enumerate(rows):
        y0 = row_top + i * (row_h + row_gap)
        y1 = y0 + row_h
        draw.rounded_rectangle((16, y0, width - 16, y1),
                               radius=28, fill=(34, 34, 36))
        # Left column: symbol + volume
        base, quote, vol, price, chg_pct, color = r
        draw.text((44, y0 + 18), base, font=sym_font, fill=(245, 245, 245))
        # Compute base text width to place quote
        bbox = draw.textbbox((0, 0), base, font=sym_font)
        base_w = bbox[2] - bbox[0]
        draw.text((44 + base_w + 6, y0 + 28), f"/{quote}",
                  font=quote_font, fill=(170, 170, 175))
        draw.text((44, y0 + 76), vol, font=vol_font, fill=(150, 150, 155))

        # Center: sparkline
        spark_w, spark_h = 260, 90
        sx = (width - spark_w) // 2
        sy = y0 + (row_h - spark_h) // 2
        sparkline = draw_sparkline((spark_w, spark_h), color, seed=i * 31 + 7)
        widget.alpha_composite(sparkline, (sx, sy))

        # Right: change % + price
        pct_text = chg_pct
        price_text = price
        pct_col = (82, 200, 120) if chg_pct.startswith("+") else (230, 80, 95)
        pct_bbox = draw.textbbox((0, 0), pct_text, font=pct_font)
        pct_w = pct_bbox[2] - pct_bbox[0]
        draw.text((width - 44 - pct_w, y0 + 18), pct_text,
                  font=pct_font, fill=pct_col)
        price_bbox = draw.textbbox((0, 0), price_text, font=price_font)
        price_w = price_bbox[2] - price_bbox[0]
        draw.text((width - 44 - price_w, y0 + 66), price_text,
                  font=price_font, fill=(245, 245, 245))

    return widget


def compose_widget(shot: Shot, home_bg: Image.Image) -> Image.Image:
    """Home screen with synthetic UTXO widget pasted on top, then framed."""
    # Start from clean home capture, paste widget onto it at upper-middle area
    home = home_bg.copy().convert("RGBA")
    home_w, home_h = home.size

    widget_w = int(home_w * 0.90)
    widget_h = int(home_h * 0.28)
    rows = [
        ("ETH", "BTC", "971.09 vol", "0.03039000 ₿", "-0.72%", (230, 80, 95)),
        ("WBTC", "BTC", "209.09 vol", "0.99710000 ₿", "-0.05%", (230, 80, 95)),
        ("BNB", "BTC", "103.14 vol", "0.00816700 ₿", "-2.20%", (230, 80, 95)),
    ]
    widget = render_synthetic_widget(widget_w, widget_h, rows)

    wx = (home_w - widget_w) // 2
    wy = int(home_h * 0.20)

    # Drop shadow for widget
    shadow_pad = 40
    wshadow = Image.new("RGBA",
                        (widget_w + shadow_pad * 2, widget_h + shadow_pad * 2),
                        (0, 0, 0, 0))
    sm = rounded_mask((widget_w, widget_h), 56)
    wshadow.paste(Image.new("RGBA", (widget_w, widget_h), (0, 0, 0, 150)),
                  (shadow_pad, shadow_pad + 8), sm)
    wshadow = wshadow.filter(ImageFilter.GaussianBlur(28))
    home.alpha_composite(wshadow, (wx - shadow_pad, wy - shadow_pad))
    home.alpha_composite(widget, (wx, wy))

    bg = gradient_bg(*shot.gradient).convert("RGBA")
    draw_text_block(bg, shot.title, shot.tagline)
    framed = wrap_in_device_frame(home.convert("RGB"))
    place_framed_device(bg, framed, top_y=420)
    return bg.convert("RGB")


def compose_comparison(shot: Shot, light: Image.Image,
                       dark: Image.Image) -> Image.Image:
    """Split one device frame vertically: left half light, right half dark."""
    bg = gradient_bg(*shot.gradient).convert("RGBA")
    draw_text_block(bg, shot.title, shot.tagline)

    w, h = light.size
    combined = Image.new("RGB", (w, h))
    combined.paste(light.crop((0, 0, w // 2, h)), (0, 0))
    combined.paste(dark.crop((w // 2, 0, w, h)), (w // 2, 0))

    # Thin divider
    divider = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ddraw = ImageDraw.Draw(divider)
    ddraw.line((w // 2, 40, w // 2, h - 40), fill=(255, 255, 255, 150), width=6)
    combined_rgba = combined.convert("RGBA")
    combined_rgba.alpha_composite(divider)

    framed = wrap_in_device_frame(combined_rgba.convert("RGB"))
    place_framed_device(bg, framed, top_y=420)
    return bg.convert("RGB")


def main():
    raw_files = {
        2: RAW / "02_market.png",
        3: RAW / "03_chart.png",
        4: RAW / "04_orderbook_news.png",
        5: RAW / "05_favorites.png",
        6: RAW / "06_settings.png",
    }

    for idx, path in raw_files.items():
        shot = SHOTS[idx - 1]
        raw = Image.open(path)
        out = compose_standard(shot, raw)
        out.save(OUT / f"{idx}.png", optimize=True)
        print(f"Wrote {OUT / f'{idx}.png'}")

    # Hero
    hero = compose_hero(SHOTS[0])
    hero.save(OUT / "1.png", optimize=True)
    print(f"Wrote {OUT / '1.png'}")

    # Widget
    home_clean = Image.open(RAW / "home_clean.png")
    widget_img = compose_widget(SHOTS[6], home_clean)
    widget_img.save(OUT / "7.png", optimize=True)
    print(f"Wrote {OUT / '7.png'}")

    # Comparison
    light = Image.open(RAW / "market_light.png")
    dark = Image.open(RAW / "02_market.png")
    cmp_img = compose_comparison(SHOTS[7], light, dark)
    cmp_img.save(OUT / "8.png", optimize=True)
    print(f"Wrote {OUT / '8.png'}")


if __name__ == "__main__":
    main()
