---
name: deploy-ss
description: Generate App Store and Play Store deployment assets — iPhone screenshots, iPad screenshots, and a Play Store feature graphic — from raw app UI screenshots. Use this skill whenever the user has app screenshots and wants to publish to the App Store or Google Play, mentions "store screenshots", "marketing screenshots", "feature graphic", "app store assets", "App Store Connect", "Play Console", "Google Play listing", or asks how to prepare their app's listing for submission. Trigger this even when the user just uploads phone screenshots and says something vague like "make these look good for the store" or "I'm submitting my app, help me with the visuals" — those are still this skill.
---

# deploy-ss

Take raw app UI screenshots and produce a polished, store-ready set of marketing visuals at the exact dimensions Apple and Google require.

## What this produces

Three deliverables per run, all in the user's chosen output directory:

| Deliverable | Dimensions | Purpose | Count |
|---|---|---|---|
| iPhone screenshots | 1284 × 2778 | App Store iPhone 6.5"/6.7" Display slot; also accepted by Play Store | 5 |
| iPad screenshots | 2048 × 2732 | App Store iPad 12.9"/13" Display slot | 5 |
| Feature graphic | 1024 × 500 | Play Store hero banner | 1 |

Files are named `phone_NN_<slug>.png`, `ipad_NN_<slug>.png`, and `feature_graphic_1024x500.png` — stable and self-describing so the user can re-run and replace cleanly.

## When to use this skill

Trigger on any of these:
- The user uploads 3–8 phone screenshots and asks for store-ready visuals
- The user mentions submitting to App Store Connect or Google Play Console
- The user asks for a "feature graphic" or "store banner"
- The user shows a marketing template (e.g. UI8/Mobbin) and says "make screenshots like this for my app"

If the user only wants ONE of the three deliverables (e.g. just the feature graphic), still use this skill — pass `--skip-phone --skip-ipad` to the generator.

## Workflow

Follow these steps in order. Don't skip the inventory step — picking the right screenshot for each slot is what makes the output good.

### 1. Inventory the user's screenshots

List the uploaded files. View each image with the `view` tool to see what feature it shows. Categorize each one — typical buckets: home/list, detail/chart, settings, profile, onboarding, empty state. Do NOT ask the user "what does this screenshot show" — figure it out by looking.

### 2. Propose a 5-screen plan

Pick the 5 most distinctive screenshots. For each, decide:

- **slot tag** — short ALL-CAPS label (4–10 chars): MARKETS, CHARTS, INBOX, SETTINGS, etc. This is the pill above the headline.
- **headline** — 2 lines, hand-broken with `\n`. Inter Black, very large. Punchy: "Track live\ncrypto markets" not "A complete real-time market tracking solution".
- **subtitle** — 1 sentence, ≤14 words. Reinforces the headline with concrete detail (named features, named integrations).
- **theme** — `"dark"` or `"light"`. Match the screenshot's actual background — placing a dark-mode screenshot on a cream canvas looks wrong.

Aim for a mix of dark/light themes (e.g. 3 dark + 2 light) so the set has visual rhythm when the user scrolls through the App Store listing.

Present the plan as a table to the user and get one round of feedback before generating. Don't push back hard if the user wants different copy — they know their app.

### 3. Pick a brand color

Default to a hue that fits the app category:

| Category | Default brand color |
|---|---|
| Crypto / Bitcoin-leaning | `#F27C2F` (Bitcoin orange) |
| Finance / banking | `#2454C8` (deep blue) |
| Productivity / dev tools | `#7C3AED` (violet) |
| Health / fitness | `#16A34A` (green) |
| Social / lifestyle | `#E11D48` (rose) |
| Photo / creative | `#0EA5E9` (sky) |

Ask the user: "Brand color — go with `<default>` or do you have a specific hex?" One question, not a five-question interview.

### 4. Pick the feature-graphic screenshot

Pick the ONE screenshot that most clearly says "this is what the app does" at a glance. For a list-based app, that's the home/list screen. For a chart-based app, the chart. Avoid screens that need context to understand (settings, empty states, modals).

### 5. Build the config and generate

Write the config JSON to a temp file (e.g. `/tmp/deploy-ss-config.json`). See `references/config-schema.md` for the full schema, or copy and modify `examples/config.example.json`.

Run from the skill's `scripts/` directory:

```bash
python3 <skill-path>/scripts/generate.py \
  --config /tmp/deploy-ss-config.json \
  --uploads <path-to-user-uploads> \
  --output <path-to-output>
```

The script needs `Pillow`. Install with `pip install --break-system-packages pillow` if it's missing.

### 6. Present the results

Show the user the produced files. Give them the file paths and a short summary of what's in each (e.g. "phone_01_markets is your home-screen pitch, dark theme"). Mention dimensions for confidence.

Offer to regenerate any individual screen with different copy — re-running the generator with a tweaked config is fast.

## Special cases

**Empty-looking screens.** If a screenshot has lots of empty space (e.g. a Favorites screen with one item), the iPhone version can use a `collapse_middle` preprocess to glue the top content to the bottom navbar. See `references/design-guide.md` § "Handling sparse screens". The iPad version should use the original screenshot — collapsing makes the centered phone look stubby.

**App name with a logomark letter.** The feature graphic supports an `accent_letter` treatment — one letter of the app name is rendered on top of an accent-colored disc (e.g. UTXO with the "O" on a Bitcoin-orange disc). Use this when the app name has a short, distinctive letter that can carry brand weight. Skip it for long or generic names.

**Non-standard screenshot sizes.** The script accepts any aspect ratio — it just resizes proportionally. If the user uploads Android screenshots (e.g. 1080 × 2400), they'll work fine.

**Fewer than 5 screenshots.** Generate as many as the user has. App Store accepts 3 minimum; Play Store accepts 2.

## References

For deeper detail, read these as needed:

- `references/specs.md` — Apple/Google dimension requirements and submission gotchas
- `references/design-guide.md` — Choosing copy, color, theme; common mistakes
- `references/config-schema.md` — Full config JSON schema with all options
- `examples/config.example.json` — Working example (the UTXO crypto tracker)
