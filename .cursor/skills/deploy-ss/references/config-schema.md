# Config schema

The generator reads a JSON file with two top-level keys: `app` (global brand) and `screens` (one entry per screenshot).

## Top-level structure

```json
{
  "app": { /* see "App" below */ },
  "screens": [ /* see "Screen" below — array of 3–8 entries */ ]
}
```

## App

Settings that apply to the feature graphic and inherited as defaults by all screens.

| Field | Type | Required | Description |
|---|---|---|---|
| `app_name` | string | Yes | Renders large in the feature graphic. ≤6 chars works best. |
| `tagline` | string | Yes | One sentence under the wordmark in the feature graphic. ≤8 words. |
| `feature_row` | string | No | 3–4 features separated by middots, e.g. `"Live prices · Pro charts · No ads"`. |
| `feature_pill` | string | No | Pill label above the wordmark in the feature graphic. Default: `"REAL-TIME"`. |
| `brand_color` | hex string | Yes | The accent color used in pills, the feature-graphic disc, etc. e.g. `"#F27C2F"`. |
| `feature_graphic_screenshot` | filename | Yes | Which uploaded screenshot to feature in the right side of the 1024×500 graphic. |
| `feature_bg` | hex string | No | Background color of the feature graphic. Default: `"#0A0E1A"` (dark midnight). |
| `accent_letter` | object | No | Logomark treatment. See below. |

### `accent_letter` — logomark treatment

Renders one letter of `app_name` as an accent-colored disc, with an optional unicode glyph rendered inside the disc instead of the original letter.

**Plain treatment** — letter rendered on disc (e.g. "UTXO" with white "O" on orange):

```json
{
  "accent_letter": {
    "index": 3,
    "letter": "O"
  }
}
```

**Glyph substitution** — disc is sized for the original letter (so wordmark spacing stays correct), but a different glyph is rendered inside. Useful for branded marks like the Bitcoin coin symbol:

```json
{
  "accent_letter": {
    "index": 3,
    "letter": "O",
    "glyph": "₿",
    "glyph_scale": 0.62
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `index` | int | Yes | 0-based position of the letter to replace in `app_name`. |
| `letter` | string | Yes | The letter being replaced. Must match `app_name[index]`. Used for sizing the disc. |
| `glyph` | string | No | Unicode character rendered inside the disc. Default: same as `letter`. |
| `glyph_scale` | float | No | Glyph size relative to the letter (1.0 = same size). Use 0.5–0.7 to leave more accent color visible around the glyph, matching a coin/badge look. Default: 1.0. |

**Common glyph choices:**
- `"₿"` (U+20BF) — Bitcoin sign. Inter has a clean native rendering.
- `"⚡"` — Lightning bolt, for fast/instant apps.
- `"◈"` — Rotated square, generic crypto.
- `"●"` — Solid circle, a minimal mark.

Skip this entire field for a plain wordmark with no disc treatment.

## Screen

One entry per screenshot. The skill produces one iPhone PNG and one iPad PNG per screen entry.

| Field | Type | Required | Description |
|---|---|---|---|
| `screenshot` | filename | Yes | Filename inside the `--uploads` directory. e.g. `"market_list_dark.png"`. |
| `tag` | string | Yes | ALL-CAPS pill label. 4–10 chars. e.g. `"MARKETS"`. |
| `headline` | string | Yes | 2-line headline. Use `\n` for hard breaks. e.g. `"Track live\ncrypto markets"`. |
| `subtitle` | string | Yes | One sentence, ≤14 words. |
| `theme` | `"dark"` or `"light"` | Yes | Determines default bg/fg colors. |
| `slug` | string | No | Filename slug. Default: derived from `tag`. |
| `bg_color` | hex | No | Override theme default. |
| `fg_head` | hex | No | Headline color. Override theme default. |
| `fg_sub` | hex | No | Subtitle color. Override theme default. |
| `accent` | hex | No | Pill color. Default: `app.brand_color`. |
| `bezel_color` | hex | No | Phone frame color. Default: matches theme. |
| `head_size` | int | No | iPhone headline size in px. Default: 140. |
| `sub_size` | int | No | iPhone subtitle size in px. Default: 52. |
| `ipad_head_size` | int | No | iPad headline size. Default: 168. |
| `ipad_sub_size` | int | No | iPad subtitle size. Default: 64. |
| `preprocess` | object | No | iPhone-only screenshot transform. See below. |
| `preprocess_ipad` | object | No | iPad-specific screenshot transform. Defaults to none (uses original). |

### `preprocess` — iPhone-only screenshot transforms

Used when a screenshot has lots of empty space and the iPhone bleed-off layout would show mostly emptiness. Currently only one type is supported:

```json
{
  "preprocess": {
    "type": "collapse_middle",
    "top_keep": 900,
    "bottom_keep": 520,
    "bg_color": "#0A0A0A"
  }
}
```

Crops out the middle of the screenshot, keeping `top_keep` pixels from the top and `bottom_keep` pixels from the bottom. The seam is filled with `bg_color` so the join is invisible.

To use the same trick on iPad (rare), put it in `preprocess_ipad` instead — the iPad render does NOT inherit `preprocess` because the centered iPad phone needs the full screenshot height to look proportional.

## Theme defaults

If `bg_color`, `fg_head`, `fg_sub`, or `bezel_color` are omitted, these defaults apply based on `theme`:

```json
"dark": {
  "bg_color": "#0A0E1A",
  "fg_head": "#FFFFFF",
  "fg_sub": "#AAB4C8",
  "bezel_color": "#181A20"
},
"light": {
  "bg_color": "#F5ECDE",
  "fg_head": "#161618",
  "fg_sub": "#5F5F69",
  "bezel_color": "#F5ECDE"
}
```

For visual variety across the set, vary `bg_color` per screen — see `design-guide.md` § "Per-screen background variants" for suggestions.

## Minimal example

```json
{
  "app": {
    "app_name": "Notepad",
    "tagline": "Notes that stay out of your way.",
    "brand_color": "#7C3AED",
    "feature_graphic_screenshot": "home.png"
  },
  "screens": [
    {
      "screenshot": "home.png",
      "tag": "NOTES",
      "headline": "Capture\nanything",
      "subtitle": "Plain text, fast search, zero friction.",
      "theme": "dark"
    }
  ]
}
```

This produces 1 iPhone screenshot, 1 iPad screenshot, and 1 feature graphic. For a full submission, use 5 screen entries.
