---
name: product-video
description: Turn app screenshots into a cinematic product video — a self-contained animated HTML showcase and an exported MP4. Use when the user has app screenshots (or the raw captures from deploy-ss) and wants a "product video", "promo video", "sizzle reel", "app trailer", "launch video", or a shareable motion showcase / App Store preview. Companion to deploy-ss (which makes the static store screenshots).
---

# product-video

Take app UI screenshots and produce a polished, auto-playing product video: a device that
crossfades through the app's screens with kinetic captions, a per-scene accent glow, and a
live ticker — delivered as a **self-contained HTML page** and an **exported MP4**.

## What this produces

| Deliverable | Format | Notes |
|---|---|---|
| Product video (interactive) | standalone `.html` | Auto-plays; scrub dots, play/pause, `#s=N` deep-links; zero external requests |
| Product video (file) | `.mp4` (1920×1080 or 1080×1920) | H.264/yuv420p, Ken-Burns + crossfades; plays anywhere |

The look is a fixed "trading-terminal, in motion" direction (dark cinematic stage, mono
utility type, per-scene accent). **Content is fully config-driven** — app name/wordmark,
scenes, copy, ticker, and colors all come from a JSON config, so the same renderer works
for any app.

## When to use

- The user has app screenshots and asks for a product/promo/launch video or an App Store preview.
- They just ran **deploy-ss** and want a motion piece from the same captures.
- They ask for a vertical (Reels/Shorts) cut of an app showcase.

## Prerequisites

- **Pillow** — `pip install --break-system-packages pillow`
- For the MP4: **ffmpeg** and **Chrome/Chromium** on the machine (both auto-detected).
- Screenshots: clean device captures work best (no marketing bezel/text — the renderer adds
  its own phone frame + captions). The `raw-screenshots/<platform>/` captures from deploy-ss
  are ideal.

## Workflow

### 1. Pick the screens and write the config

Choose 5–8 of the most distinctive screens and lead with the one that best sells the app.
Copy the structure of `config.example.json` (the UTXO config) and fill in:

- `app` — `name` (for the wordmark), `discIndex` + `discGlyph` (one letter rendered on the
  brand disc, e.g. UTXO's `O` → `₿`; omit for a plain wordmark), `kicker`, `tagline`,
  `brand` hex, and `screenAspect` (the source screenshot's `w/h`, e.g. `1206/2622` iPhone,
  `1080/2400` Android).
- `scenes[]` — one per screen: `tag` (ALL-CAPS pill), `accent` (hex — give each scene its
  own hue for rhythm), `img` (filename inside `--assets`), `headline` (array of 1–2 lines),
  `subtitle` (≤14 words).
- `ticker[]` — `[symbol, value, "u"|"d", "▲x%"]` rows for the ambient bottom strip.
- `outro.lede` — the closing line above the App Store / Google Play badges.

Write good copy: verb-first headlines ("Track live\ncrypto markets"), concrete subtitles
(name real features). See `references/config-schema.md` for every field.

### 2. Build the HTML

```bash
python3 <skill>/scripts/build.py \
  --config <config.json> --assets <screenshot-dir> --out <out>/product-video.html
```

Add `--mode artifact` to emit body-only content for publishing on claude.ai via the Artifact
tool (claude.ai supplies the `<head>`); the default `standalone` mode is a double-clickable file.

### 3. Export the MP4

```bash
python3 <skill>/scripts/encode.py \
  --html <out>/product-video.html --config <config.json> --out <out>/product-video.mp4
```

Add `--portrait` for a 1080×1920 vertical cut (Reels/Shorts/App Store previews). Timing flags:
`--intro`, `--hold`, `--outro`, `--xfade`, `--fps`.

### 4. Verify before delivering

- Open the `.html` (it auto-plays) or render a scene still: `chrome --headless … "file://…#shot&s=3"`.
- `ffprobe <mp4>` — confirm the duration and 1920×1080 (or 1080×1920).
- Extract a couple of frames (`ffmpeg -ss <t> -i <mp4> -frames:v 1 f.png`) and view them to
  confirm captions, phone, accent, and ticker render (no mojibake, no clipped text).

## How it works (so you can debug)

- `build.py` downscales each referenced screenshot and inlines it (plus the Inter display/body
  fonts) as data URIs into `template.html` at the `<!--ASSETS_SLOT-->` marker. It emits the
  config as `window.__PV__` with `ensure_ascii=True`, so every glyph/arrow/em-dash arrives as
  a `\u` escape — the page is charset-independent and never mojibakes.
- `template.html` is the whole design (CSS + a small scene engine). It exposes capture
  deep-links used by the encoder: `#shot&intro`, `#shot&s=N`, `#shot&outro` freeze a beat with
  entrance animation settled.
- `encode.py` screenshots each beat with headless Chrome (at 2× for crisp text), then ffmpeg
  builds one Ken-Burns clip per beat (`-frames:v` caps zoompan so it doesn't loop) and
  crossfades them with a fade from/to black.

## Common pitfalls

- **Use clean captures, not the framed marketing shots** — the renderer draws its own phone
  frame + headline; a pre-framed screenshot would double up.
- **Set `screenAspect`** to the real capture ratio or the phone screen crops oddly.
- **zoompan looping** → a 400s file: only happens if you feed a multi-frame input; `encode.py`
  already caps with `-frames:v`. Keep that if you edit it.
- Screenshots with lots of small text stay legible because frames render at 2× — don't drop
  `--scale 2` without reason.

## References

- `references/config-schema.md` — full config field reference.
- `config.example.json` — the working UTXO config.
