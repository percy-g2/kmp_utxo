#!/usr/bin/env python3
"""Custom Play Store feature graphic (1024x500):
   dual hero phones (AI Insights + Portfolio) over a faded collage of the other screens."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance

ROOT = Path(__file__).resolve().parents[4]
FONTS = ROOT / ".claude/skills/deploy-ss/scripts/fonts"
IOS = ROOT / "raw-screenshots/ios"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/feature.png")

F_BLACK = str(FONTS / "Inter-Black.ttf")
F_SEMI = str(FONTS / "Inter-SemiBold.ttf")
F_MED = str(FONTS / "Inter-Medium.ttf")

W, H = 1024, 500
BRAND = (242, 124, 47)        # bitcoin orange
BG_TOP = (12, 16, 30)
BG_BOT = (6, 9, 18)


def hex_rgb(h):
    h = h.lstrip("#"); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0], size[1]), radius=radius, fill=255)
    return m


def frame_phone(path, target_h, bezel=7, radius=54, bezel_color=(18, 20, 26)):
    src = Image.open(path).convert("RGB")
    sw, sh = src.size
    tw = int(target_h * sw / sh)
    src = src.resize((tw, target_h), Image.LANCZOS)
    fw, fh = tw + bezel * 2, target_h + bezel * 2
    framed = Image.new("RGB", (fw, fh), bezel_color)
    framed.paste(src, (bezel, bezel))
    framed = framed.convert("RGBA")
    framed.putalpha(rounded_mask((fw, fh), radius))
    return framed


def paste_rotated_shadow(canvas, phone, center, angle, shadow_alpha=170, blur=26, dx=10, dy=18):
    rot = phone.rotate(angle, expand=True, resample=Image.BICUBIC)
    rw, rh = rot.size
    x = int(center[0] - rw / 2); y = int(center[1] - rh / 2)
    # shadow from rotated alpha
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    solid = Image.new("RGBA", rot.size, (0, 0, 0, shadow_alpha))
    solid.putalpha(rot.split()[3].point(lambda a: int(a * shadow_alpha / 255)))
    sh.alpha_composite(solid, (x + dx, y + dy))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(sh)
    canvas.alpha_composite(rot, (x, y))


# ---- base gradient ----
canvas = Image.new("RGBA", (W, H), BG_BOT + (255,))
grad = Image.new("RGBA", (W, H))
gd = grad.load()
for y in range(H):
    t = y / H
    r = int(BG_TOP[0] * (1 - t) + BG_BOT[0] * t)
    g = int(BG_TOP[1] * (1 - t) + BG_BOT[1] * t)
    b = int(BG_TOP[2] * (1 - t) + BG_BOT[2] * t)
    for x in range(W):
        gd[x, y] = (r, g, b, 255)
canvas.alpha_composite(grad)

# ---- faded collage of the "rest" screens ----
collage = ["markets_dark", "allocation_dark", "charts_light",
           "depth_dark", "news_light", "favorites_dark"]
# (center_x, center_y, angle, height, alpha)
spots = [
    (560, 250, -9, 330, 95),
    (720, 150, 9, 300, 80),
    (415, 360, -7, 300, 70),
    (860, 330, 11, 320, 78),
    (330, 150, 12, 270, 36),
    (960, 150, -10, 280, 62),
]
coll_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
for name, (cx, cy, ang, ht, al) in zip(collage, spots):
    ph = frame_phone(IOS / f"{name}.png", ht, bezel=5, radius=40, bezel_color=(34, 37, 46))
    ph = ImageEnhance.Brightness(ph).enhance(0.62)
    ph = ph.filter(ImageFilter.GaussianBlur(1.7))
    a = ph.split()[3].point(lambda v: int(v * al / 255))
    ph.putalpha(a)
    rot = ph.rotate(ang, expand=True, resample=Image.BICUBIC)
    coll_layer.alpha_composite(rot, (int(cx - rot.width / 2), int(cy - rot.height / 2)))
coll_layer = coll_layer.filter(ImageFilter.GaussianBlur(0.6))
canvas.alpha_composite(coll_layer)

# ---- dark scrim (stronger on left for text legibility) ----
scrim = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = scrim.load()
for x in range(W):
    # left 55% darker, fading to lighter on the right
    t = max(0.0, 1 - x / 620)
    a = int(150 * t + 40)
    for y in range(H):
        sd[x, y] = (5, 8, 16, a)
canvas.alpha_composite(scrim)

# subtle top glow
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gdd = ImageDraw.Draw(glow)
for r in range(520, 0, -18):
    a = int(9 * (r / 520))
    gdd.ellipse((-160 - r, -240 - r, 360 + r, 200 + r), fill=(255, 255, 255, a))
canvas.alpha_composite(glow)

# ---- hero phones: AI Insights (behind) + Portfolio (front) ----
ai = frame_phone(IOS / "ai_insights_dark.png", 492, bezel=7, radius=56, bezel_color=(20, 23, 30))
pf = frame_phone(IOS / "portfolio_dark.png", 500, bezel=7, radius=58, bezel_color=(16, 20, 26))
paste_rotated_shadow(canvas, ai, center=(612, 250), angle=-7, shadow_alpha=155, blur=32, dx=-8, dy=20)
paste_rotated_shadow(canvas, pf, center=(860, 252), angle=7, shadow_alpha=185, blur=34, dx=14, dy=22)

# ---- brand text (left) ----
d = ImageDraw.Draw(canvas)
tx = 64
# pill
pill = "AI-POWERED"
pf18 = ImageFont.truetype(F_SEMI, 19)
pw = int(d.textlength(pill, font=pf18) + 30); ph = 38; py = 96
d.rounded_rectangle((tx, py, tx + pw, py + ph), radius=ph // 2, fill=BRAND)
d.text((tx + 15, py + 8), pill, font=pf18, fill=(255, 255, 255))

# wordmark UTX + orange disc with ₿
word_font = ImageFont.truetype(F_BLACK, 128)
wy = py + ph + 20
d.text((tx, wy), "UTX", font=word_font, fill=(255, 255, 255))
prefix_w = d.textlength("UTX", font=word_font)
ltr_w = d.textlength("O", font=word_font)
disc_pad = 18
dx0 = tx + prefix_w - 6; dy0 = wy + 22
dx1 = dx0 + ltr_w + disc_pad; dy1 = dy0 + ltr_w + disc_pad
d.ellipse((dx0, dy0, dx1, dy1), fill=BRAND)
gf = ImageFont.truetype(F_BLACK, int(128 * 0.62))
gb = d.textbbox((0, 0), "₿", font=gf)
gw, gh = gb[2] - gb[0], gb[3] - gb[1]
d.text(((dx0 + dx1) // 2 - gw // 2 - gb[0], (dy0 + dy1) // 2 - gh // 2 - gb[1]),
       "₿", font=gf, fill=(255, 255, 255))

# tagline
tag_font = ImageFont.truetype(F_MED, 26)
ty = wy + 154
d.text((tx, ty), "Markets, portfolio & AI insights.", font=tag_font, fill=(196, 205, 222))
# feature row
feat_font = ImageFont.truetype(F_SEMI, 18)
d.text((tx, ty + 48), "Live prices  ·  AI market reads  ·  Pro charts",
       font=feat_font, fill=(140, 150, 170))

canvas.convert("RGB").save(OUT, "PNG", optimize=True)
print("wrote", OUT, canvas.size)
