#!/usr/bin/env python3
"""
deploy-ss — App Store / Play Store asset generator.

Reads a JSON config describing the app, brand, and per-screen specs;
produces three sets of deliverables:

  - iPhone 6.7" screenshots (1284 × 2778) — App Store + Play Store compatible
  - iPad 12.9"/13" screenshots (2048 × 2732) — App Store iPad slot
  - Play Store feature graphic (1024 × 500)

Usage:
    python generate.py --config <config.json> --uploads <dir> --output <dir>

The config schema is documented in references/config-schema.md.
"""
import argparse
import json
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

# ──────────────────────────────────────────────────────────────────
# Setup — locate bundled fonts relative to this script
# ──────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
FONTS_DIR = SCRIPT_DIR / "fonts"

F_BLACK = str(FONTS_DIR / "Inter-Black.ttf")
F_BOLD = str(FONTS_DIR / "Inter-Bold.ttf")
F_SEMI = str(FONTS_DIR / "Inter-SemiBold.ttf")
F_MED = str(FONTS_DIR / "Inter-Medium.ttf")
F_REG = str(FONTS_DIR / "Inter-Regular.ttf")

# Canvas sizes
PHONE_W, PHONE_H = 1284, 2778       # iPhone 6.7" (App Store + Play Store)
IPAD_W, IPAD_H = 2048, 2732         # iPad 12.9"/13"
FEAT_W, FEAT_H = 1024, 500          # Play Store feature graphic


# ──────────────────────────────────────────────────────────────────
# Color helpers
# ──────────────────────────────────────────────────────────────────

def hex_to_rgb(h):
    """Accept '#RRGGBB' or 'RRGGBB' and return (r, g, b)."""
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def is_dark(rgb):
    return sum(rgb) < 384


# ──────────────────────────────────────────────────────────────────
# Drawing primitives
# ──────────────────────────────────────────────────────────────────

def rounded_mask(size, radius):
    w, h = size
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w, h), radius=radius, fill=255)
    return m


def wrap_text(draw, text, font, max_width):
    """Word-wrap to a max pixel width."""
    words = text.split()
    lines, cur = [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=font) <= max_width:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def draw_pill(draw, x, y, label, accent_rgb, font_size=38):
    """Draw a rounded-pill label and return its (width, height)."""
    f = ImageFont.truetype(F_SEMI, font_size)
    tw = draw.textlength(label, font=f)
    ph = int(font_size * 1.6)
    pw = int(tw + font_size * 1.45)
    draw.rounded_rectangle((x, y, x + pw, y + ph),
                           radius=ph // 2, fill=accent_rgb)
    draw.text((x + font_size * 0.72, y + (ph - font_size) // 2 - 2),
              label, font=f, fill=(255, 255, 255))
    return pw, ph


def collapse_empty_middle(top_keep, bottom_keep, bg_rgb):
    """Preprocess fn: keep top_keep + bottom_keep of the image, drop the middle."""
    def fn(img):
        w, h = img.size
        out = Image.new("RGB", (w, top_keep + bottom_keep), bg_rgb)
        out.paste(img.crop((0, 0, w, top_keep)), (0, 0))
        out.paste(img.crop((0, h - bottom_keep, w, h)), (0, top_keep))
        return out
    return fn


PREPROCESS_REGISTRY = {
    "collapse_middle": collapse_empty_middle,
    "none": None,
}


def resolve_preprocess(spec):
    """Resolve preprocess shorthand into a callable."""
    p = spec.get("preprocess")
    if not p or p == "none":
        return None
    if isinstance(p, dict) and p.get("type") == "collapse_middle":
        return collapse_empty_middle(
            top_keep=p.get("top_keep", 900),
            bottom_keep=p.get("bottom_keep", 520),
            bg_rgb=hex_to_rgb(p.get("bg_color", "#0A0A0A")),
        )
    return None


# ──────────────────────────────────────────────────────────────────
# Phone (iPhone) screenshot — text on top, phone bleeds off bottom
# ──────────────────────────────────────────────────────────────────

def render_phone(spec, app, uploads_dir, out_dir):
    bg = hex_to_rgb(spec["bg_color"])
    fg_head = hex_to_rgb(spec["fg_head"])
    fg_sub = hex_to_rgb(spec["fg_sub"])
    accent = hex_to_rgb(spec.get("accent", app["brand_color"]))
    bezel = hex_to_rgb(spec.get("bezel_color", "#181A20")
                       if is_dark(bg) else spec.get("bezel_color",
                                                    spec["bg_color"]))

    canvas = Image.new("RGBA", (PHONE_W, PHONE_H), bg + (255,))

    # Subtle radial highlight (top-left)
    grad = Image.new("RGBA", (PHONE_W, PHONE_H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for r in range(900, 0, -40):
        a = int(8 * (r / 900))
        col = (255, 255, 255, a) if is_dark(bg) else (255, 255, 255, a // 2)
        gd.ellipse((-200 - r, -300 - r, 600 + r, 400 + r), fill=col)
    canvas.alpha_composite(grad)

    # ── Text block
    d = ImageDraw.Draw(canvas)
    pad_x = 90
    max_w = PHONE_W - pad_x * 2
    y = 160

    # Pill
    if spec.get("tag"):
        _, ph = draw_pill(d, pad_x, y, spec["tag"], accent, font_size=38)
        y += ph + 36

    # Headline (honor explicit \n; wrap any line still too wide)
    head_size = spec.get("head_size", 140)
    head_font = ImageFont.truetype(F_BLACK, head_size)
    head_lines = []
    for raw in spec["headline"].split("\n"):
        head_lines.extend(wrap_text(d, raw, head_font, max_w) or [""])
    line_h = int(head_size * 1.02)
    for ln in head_lines:
        d.text((pad_x, y), ln, font=head_font, fill=fg_head)
        y += line_h
    y += 28

    # Subtitle
    sub_size = spec.get("sub_size", 52)
    sub_font = ImageFont.truetype(F_MED, sub_size)
    for ln in wrap_text(d, spec["subtitle"], sub_font, max_w):
        d.text((pad_x, y), ln, font=sub_font, fill=fg_sub)
        y += int(sub_size * 1.28)

    # ── Phone mockup
    phone_top = max(y + 80, 880)
    src_path = uploads_dir / spec["screenshot"]
    src = Image.open(src_path).convert("RGB")
    pre = resolve_preprocess(spec)
    if pre:
        src = pre(src)

    sw, sh = src.size
    target_w = int(PHONE_W * 0.84)
    target_h = int(target_w * sh / sw)
    src = src.resize((target_w, target_h), Image.LANCZOS)

    bezel_px = 8
    framed_w = target_w + bezel_px * 2
    framed_h = target_h + bezel_px * 2
    framed = Image.new("RGB", (framed_w, framed_h), bezel)
    framed.paste(src, (bezel_px, bezel_px))

    x = (PHONE_W - framed_w) // 2
    radius = 92

    # Shadow
    shadow_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    sd.rounded_rectangle(
        (x + 12, phone_top + 28, x + framed_w + 12, phone_top + framed_h + 28),
        radius=radius, fill=(0, 0, 0, 110),
    )
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(40))
    canvas.alpha_composite(shadow_layer)

    framed_rgba = framed.convert("RGBA")
    framed_rgba.putalpha(rounded_mask((framed_w, framed_h), radius))
    canvas.alpha_composite(framed_rgba, (x, phone_top))

    out_path = out_dir / f"phone_{spec['index']:02d}_{spec['slug']}.png"
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)
    return out_path


# ──────────────────────────────────────────────────────────────────
# iPad screenshot — side-by-side, text left, phone right
# ──────────────────────────────────────────────────────────────────

def render_ipad(spec, app, uploads_dir, out_dir):
    bg = hex_to_rgb(spec["bg_color"])
    fg_head = hex_to_rgb(spec["fg_head"])
    fg_sub = hex_to_rgb(spec["fg_sub"])
    accent = hex_to_rgb(spec.get("accent", app["brand_color"]))
    bezel = hex_to_rgb(spec.get("bezel_color", "#181A20")
                       if is_dark(bg) else spec.get("bezel_color",
                                                    spec["bg_color"]))

    canvas = Image.new("RGBA", (IPAD_W, IPAD_H), bg + (255,))

    # Radial highlight
    grad = Image.new("RGBA", (IPAD_W, IPAD_H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for r in range(1200, 0, -50):
        a = int(10 * (r / 1200))
        col = (255, 255, 255, a) if is_dark(bg) else (255, 255, 255, a // 2)
        gd.ellipse((-300 - r, -400 - r, 800 + r, 600 + r), fill=col)
    canvas.alpha_composite(grad)

    pad = 130
    gutter = 80
    col_w = (IPAD_W - pad * 2 - gutter) // 2

    # ── Phone (right column, vertically centered)
    src_path = uploads_dir / spec["screenshot"]
    src = Image.open(src_path).convert("RGB")
    # iPad uses the full screenshot by default — no inheriting the iPhone
    # collapse preprocess. Opt in via "preprocess_ipad" if needed.
    if spec.get("preprocess_ipad"):
        pre = resolve_preprocess({"preprocess": spec["preprocess_ipad"]})
        if pre:
            src = pre(src)
    sw, sh = src.size

    phone_render_w = int(col_w * 0.95)
    phone_render_h = int(phone_render_w * sh / sw)
    max_phone_h = IPAD_H - pad * 2
    if phone_render_h > max_phone_h:
        phone_render_h = max_phone_h
        phone_render_w = int(phone_render_h * sw / sh)

    phone_x = pad + col_w + gutter + (col_w - phone_render_w) // 2
    phone_y = (IPAD_H - phone_render_h) // 2

    src_resized = src.resize((phone_render_w, phone_render_h), Image.LANCZOS)
    bezel_px = 10
    framed_w = phone_render_w + bezel_px * 2
    framed_h = phone_render_h + bezel_px * 2
    framed = Image.new("RGB", (framed_w, framed_h), bezel)
    framed.paste(src_resized, (bezel_px, bezel_px))
    fx = phone_x - bezel_px
    fy = phone_y - bezel_px

    # Shadow
    shadow_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    sd.rounded_rectangle(
        (fx + 16, fy + 32, fx + framed_w + 16, fy + framed_h + 32),
        radius=110, fill=(0, 0, 0, 130),
    )
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(50))
    canvas.alpha_composite(shadow_layer)

    framed_rgba = framed.convert("RGBA")
    framed_rgba.putalpha(rounded_mask((framed_w, framed_h), 110))
    canvas.alpha_composite(framed_rgba, (fx, fy))

    # ── Text (left column, vertically centered)
    head_size = spec.get("ipad_head_size", 168)
    sub_size = spec.get("ipad_sub_size", 64)

    # Measure text block height for vertical centering
    dummy = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    head_font = ImageFont.truetype(F_BLACK, head_size)
    sub_font = ImageFont.truetype(F_MED, sub_size)
    block_h = 0
    if spec.get("tag"):
        block_h += int(44 * 1.6) + 36
    head_lines = []
    for raw in spec["headline"].split("\n"):
        head_lines.extend(wrap_text(dummy, raw, head_font, col_w) or [""])
    block_h += int(head_size * 1.02) * len(head_lines) + 36
    sub_lines = wrap_text(dummy, spec["subtitle"], sub_font, col_w)
    block_h += int(sub_size * 1.28) * len(sub_lines)

    text_y = (IPAD_H - block_h) // 2
    text_x = pad
    d = ImageDraw.Draw(canvas)

    if spec.get("tag"):
        _, ph = draw_pill(d, text_x, text_y, spec["tag"], accent,
                          font_size=44)
        text_y += ph + 36

    line_h = int(head_size * 1.02)
    for ln in head_lines:
        d.text((text_x, text_y), ln, font=head_font, fill=fg_head)
        text_y += line_h
    text_y += 36

    sub_line_h = int(sub_size * 1.28)
    for ln in sub_lines:
        d.text((text_x, text_y), ln, font=sub_font, fill=fg_sub)
        text_y += sub_line_h

    out_path = out_dir / f"ipad_{spec['index']:02d}_{spec['slug']}.png"
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)
    return out_path


# ──────────────────────────────────────────────────────────────────
# Play Store feature graphic — 1024 × 500
# ──────────────────────────────────────────────────────────────────

def render_feature_graphic(app, uploads_dir, out_dir):
    bg = hex_to_rgb(app.get("feature_bg", "#0A0E1A"))
    accent = hex_to_rgb(app["brand_color"])
    canvas = Image.new("RGBA", (FEAT_W, FEAT_H), bg + (255,))

    # Radial glow
    grad = Image.new("RGBA", (FEAT_W, FEAT_H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for r in range(700, 0, -25):
        a = int(14 * (r / 700))
        col = (255, 255, 255, a) if is_dark(bg) else (0, 0, 0, a // 4)
        gd.ellipse((-200 - r, -300 - r, 350 + r, 250 + r), fill=col)
    canvas.alpha_composite(grad)

    # ── Phone (right side)
    src_path = uploads_dir / app["feature_graphic_screenshot"]
    src = Image.open(src_path).convert("RGB")
    sw, sh = src.size
    phone_h = FEAT_H - 70
    phone_w = int(phone_h * sw / sh)
    src_resized = src.resize((phone_w, phone_h), Image.LANCZOS)

    bezel_px = 4
    framed_w = phone_w + bezel_px * 2
    framed_h = phone_h + bezel_px * 2
    bezel_col = (24, 26, 32) if is_dark(bg) else (220, 220, 224)
    framed = Image.new("RGB", (framed_w, framed_h), bezel_col)
    framed.paste(src_resized, (bezel_px, bezel_px))

    phone_x = FEAT_W - framed_w - 50
    phone_y = (FEAT_H - framed_h) // 2

    shadow_layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow_layer)
    sd.rounded_rectangle(
        (phone_x + 6, phone_y + 12,
         phone_x + framed_w + 6, phone_y + framed_h + 12),
        radius=42, fill=(0, 0, 0, 160),
    )
    shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(20))
    canvas.alpha_composite(shadow_layer)

    framed_rgba = framed.convert("RGBA")
    framed_rgba.putalpha(rounded_mask((framed_w, framed_h), 42))
    canvas.alpha_composite(framed_rgba, (phone_x, phone_y))

    # ── Text (left side)
    d = ImageDraw.Draw(canvas)
    text_x = 64
    fg = (255, 255, 255) if is_dark(bg) else (20, 20, 24)
    sub_fg = (180, 190, 210) if is_dark(bg) else (90, 95, 105)
    feat_fg = (130, 140, 160) if is_dark(bg) else (130, 130, 140)

    # Pill
    pill_label = app.get("feature_pill", "REAL-TIME")
    pill_font = ImageFont.truetype(F_SEMI, 18)
    tw = d.textlength(pill_label, font=pill_font)
    ph = 36
    pw = int(tw + 28)
    pill_y = 130
    d.rounded_rectangle((text_x, pill_y, text_x + pw, pill_y + ph),
                        radius=ph // 2, fill=accent)
    d.text((text_x + 14, pill_y + 7), pill_label,
           font=pill_font, fill=(255, 255, 255))

    # Wordmark — supports an "accent_letter" treatment (e.g. "UTXO" with
    # the "O" rendered as an accent-colored disc). The disc is sized based
    # on the original letter (so wordmark spacing stays correct), but an
    # optional `glyph` can be rendered inside the disc instead of the letter.
    # Useful for: Bitcoin-themed apps (letter="O", glyph="₿"), brand
    # symbols, or any unicode mark. `glyph_scale` controls relative size
    # inside the disc (default 1.0 = same size as the letter).
    word_font = ImageFont.truetype(F_BLACK, 132)
    word_y = pill_y + ph + 24
    name = app["app_name"]
    accent_letter = app.get("accent_letter")  # e.g. {"index": 3, "letter": "O"}

    if accent_letter:
        idx = accent_letter["index"]
        prefix = name[:idx]
        ltr = name[idx]
        suffix = name[idx + 1:]
        glyph = accent_letter.get("glyph", ltr)
        glyph_scale = accent_letter.get("glyph_scale", 1.0)

        d.text((text_x, word_y), prefix, font=word_font, fill=fg)
        prefix_w = d.textlength(prefix, font=word_font)
        ltr_w = d.textlength(ltr, font=word_font)
        disc_pad = 18
        disc_x0 = text_x + prefix_w - 6
        disc_y0 = word_y + 22
        disc_x1 = disc_x0 + ltr_w + disc_pad
        disc_y1 = disc_y0 + ltr_w + disc_pad
        d.ellipse((disc_x0, disc_y0, disc_x1, disc_y1), fill=accent)

        # Render the glyph centered on the disc.
        if glyph_scale == 1.0 and glyph == ltr:
            # Fast path: same size + letter, align like before
            ltr_x = disc_x0 + (disc_x1 - disc_x0 - ltr_w) // 2 - 2
            d.text((ltr_x, word_y), ltr, font=word_font, fill=(255, 255, 255))
        else:
            glyph_font = ImageFont.truetype(F_BLACK, int(132 * glyph_scale))
            glyph_bbox = d.textbbox((0, 0), glyph, font=glyph_font)
            glyph_w = glyph_bbox[2] - glyph_bbox[0]
            glyph_h = glyph_bbox[3] - glyph_bbox[1]
            disc_cx = (disc_x0 + disc_x1) // 2
            disc_cy = (disc_y0 + disc_y1) // 2
            glyph_x = disc_cx - glyph_w // 2 - glyph_bbox[0]
            glyph_y = disc_cy - glyph_h // 2 - glyph_bbox[1]
            d.text((glyph_x, glyph_y), glyph, font=glyph_font,
                   fill=(255, 255, 255))

        if suffix:
            suffix_x = disc_x1 + 4
            d.text((suffix_x, word_y), suffix, font=word_font, fill=fg)
    else:
        d.text((text_x, word_y), name, font=word_font, fill=fg)

    # Tagline
    tag_font = ImageFont.truetype(F_MED, 30)
    tag_y = word_y + 150
    d.text((text_x, tag_y), app["tagline"], font=tag_font, fill=sub_fg)

    # Feature row
    if app.get("feature_row"):
        feat_font = ImageFont.truetype(F_SEMI, 18)
        feat_y = tag_y + 50
        d.text((text_x, feat_y), app["feature_row"],
               font=feat_font, fill=feat_fg)

    out_path = out_dir / "feature_graphic_1024x500.png"
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)
    return out_path


# ──────────────────────────────────────────────────────────────────
# Spec normalization
# ──────────────────────────────────────────────────────────────────

THEME_DEFAULTS = {
    "dark": {
        "bg_color": "#0A0E1A",
        "fg_head": "#FFFFFF",
        "fg_sub": "#AAB4C8",
        "bezel_color": "#181A20",
    },
    "light": {
        "bg_color": "#F5ECDE",
        "fg_head": "#161618",
        "fg_sub": "#5F5F69",
        "bezel_color": "#F5ECDE",
    },
}


def slugify(s):
    return "".join(c.lower() if c.isalnum() else "_" for c in s).strip("_")


def normalize_screen(i, raw, app):
    """Apply theme defaults and derive missing fields."""
    theme = raw.get("theme", "dark")
    defaults = THEME_DEFAULTS[theme].copy()
    # Spec values override theme defaults
    spec = {**defaults, **raw}
    spec["theme"] = theme
    spec["index"] = i + 1
    spec["slug"] = raw.get("slug") or slugify(raw.get("tag", f"slot_{i + 1}"))
    if "accent" not in spec:
        spec["accent"] = app["brand_color"]
    return spec


# ──────────────────────────────────────────────────────────────────
# Entrypoint
# ──────────────────────────────────────────────────────────────────

def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--config", required=True, help="Path to config JSON")
    p.add_argument("--uploads", required=True, help="Directory with screenshots")
    p.add_argument("--output", required=True, help="Output directory")
    p.add_argument("--skip-phone", action="store_true")
    p.add_argument("--skip-ipad", action="store_true")
    p.add_argument("--skip-feature", action="store_true")
    args = p.parse_args()

    config_path = Path(args.config)
    uploads_dir = Path(args.uploads)
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    config = json.loads(config_path.read_text())
    app = config["app"]
    screens = [normalize_screen(i, s, app)
               for i, s in enumerate(config["screens"])]

    produced = []

    if not args.skip_phone:
        print(f"iPhone 6.7\" ({PHONE_W} × {PHONE_H}):")
        for s in screens:
            path = render_phone(s, app, uploads_dir, out_dir)
            sz = os.path.getsize(path) / 1024
            print(f"  {path}  ({sz:.0f} KB)")
            produced.append(path)

    if not args.skip_ipad:
        print(f"\niPad 12.9\"/13\" ({IPAD_W} × {IPAD_H}):")
        for s in screens:
            path = render_ipad(s, app, uploads_dir, out_dir)
            sz = os.path.getsize(path) / 1024
            print(f"  {path}  ({sz:.0f} KB)")
            produced.append(path)

    if not args.skip_feature:
        print(f"\nPlay Store feature graphic ({FEAT_W} × {FEAT_H}):")
        path = render_feature_graphic(app, uploads_dir, out_dir)
        sz = os.path.getsize(path) / 1024
        print(f"  {path}  ({sz:.0f} KB)")
        produced.append(path)

    print(f"\nDone. {len(produced)} files in {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
