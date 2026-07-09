# product-video config schema

A single JSON object. All content is here; the visual direction is fixed in `template.html`.

## `app` (object)

| Field | Type | Notes |
|---|---|---|
| `name` | string | The wordmark text (e.g. `"UTXO"`). |
| `discIndex` | int | 0-based index of the letter rendered on the brand disc. Omit for a plain wordmark. |
| `discGlyph` | string | Glyph shown on the disc in place of that letter (e.g. `"₿"`). Defaults to the letter itself. |
| `kicker` | string | Small mono label above the intro wordmark (e.g. `"Real-time crypto tracker"`). |
| `tagline` | string | One line under the intro wordmark. |
| `brand` | hex | Primary/brand color — the disc, intro accent, progress default. |
| `bg`, `bg2` | hex | Optional. Stage background + intro/outro radial inner color. Defaults are deep blue-black. |
| `screenAspect` | string | The source screenshot ratio as `"w/h"` (e.g. `"1206/2622"` iPhone, `"1080/2400"` Android). Sets the phone screen aspect so nothing crops. |

## `timing` (object, optional) — HTML autoplay only

| Field | Default (ms) | Notes |
|---|---|---|
| `intro` | 2600 | Intro card hold before scene 1. |
| `hold` | 3400 | Per-scene hold. |
| `outroHold` | 6000 | Last-scene hold before the outro card. |

(The MP4 has its own timing flags on `encode.py`: `--intro --hold --outro --xfade`.)

## `ticker` (array)

Rows for the ambient bottom strip. Each row: `[symbol, value, direction, delta]` where
`direction` is `"u"` (green) or `"d"` (red), and `delta` is display text (e.g. `"▲2.83%"`).

## `scenes` (array, 5–8 items)

One per screen, in play order.

| Field | Type | Notes |
|---|---|---|
| `tag` | string | ALL-CAPS pill label (4–10 chars): MARKETS, PORTFOLIO, CHARTS… |
| `accent` | hex | Scene accent — the pill, glow, progress, dots. Vary it per scene for rhythm. |
| `img` | string | Screenshot filename inside the `--assets` dir. **Use a clean capture**, not a marketing frame. |
| `headline` | string[] | 1–2 lines. Verb-first, concrete ("Track live", "crypto markets"). |
| `subtitle` | string | ≤14 words; name real features. |

## `outro` (object)

| Field | Type | Notes |
|---|---|---|
| `lede` | string | Closing line above the store badges. |
| `badges` | array | Optional override of the store badges. Each: `{ "sub": "...", "name": "...", "svg": "<path d>" }`. Defaults to App Store + Google Play. |

## Notes

- Non-ASCII (glyphs, `▲`/`▼`, `—`) is fine anywhere — `build.py` re-encodes the config to
  ASCII `\u` escapes, so the output page is charset-independent.
- Keep `scenes[].img` files in one directory and pass it as `--assets`.
