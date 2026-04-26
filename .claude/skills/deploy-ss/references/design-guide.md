# Design guide

How to make decisions when filling in the config. The generator is the cheap part — picking the right copy and pairing is what makes the output good.

## Choosing the slot tag

The pill is a 4–10 character ALL-CAPS label that anchors the screen's purpose. It should be a noun, not a verb.

**Good:** MARKETS · CHARTS · INBOX · PROFILE · WALLET · NEWS · CALENDAR · DEPTH

**Avoid:** TRACK NOW · GET STARTED · NEW! · ⚡FAST · LOOK AT ME

The pill is supporting context, not the headline. If you find yourself trying to make the pill do a lot of work, the headline is probably weak — fix that instead.

## Writing the headline

Two lines, hand-broken with `\n`. Inter Black at 140 px (iPhone) or 168 px (iPad). The headline is what someone reads in the 1.5 seconds they're scrolling the App Store.

### Rules

1. **Verb + noun, present tense.** "Track live crypto markets" beats "Real-time cryptocurrency tracking solution".
2. **Concrete over abstract.** Name the thing — "BTC, ETH, SOL" lands harder than "your assets".
3. **Two-line break should feel like a natural breath.** Break on a phrase boundary, not a syllable.
4. **Avoid filler verbs.** "Get", "find", "discover" — usually replaceable with a stronger verb.
5. **The line break is a typographic decision.** If the second line is one syllable, you've broken in the wrong place.

### Examples

| Weak | Strong |
|---|---|
| Cryptocurrency tracking made easy | Track live\ncrypto markets |
| Get realtime stock prices | Real-time stocks,\npocket-sized |
| Manage your finances | Every dollar,\naccounted for |
| Find the best workout for you | Workouts that\nfit your week |

### Length

Each line should fit at the chosen font size with at most one auto-wrap. If a line wraps to a third visual line, either shorten the copy or accept the 3-line headline as a magazine-style display (which can look great when the words are short and bold).

## Writing the subtitle

One sentence, ≤14 words, sits below the headline at 52 px (iPhone) or 64 px (iPad). The job is to add concrete substance the headline couldn't carry.

### Patterns that work

- **Named features:** "Powered by TradingView. Pinch, scrub, zoom — full control."
- **Named integrations:** "Latest from BeInCrypto, CryptoSlate and trusted sources."
- **Concrete scope:** "BTC, ETH, SOL and 200+ coins — updated every second."
- **What's notably absent:** "No ads. No accounts. Just data."

### Anti-patterns

- Restating the headline in different words
- Generic claims ("powerful", "intuitive", "beautiful")
- Compound sentences with three commas — pick the strongest clause and cut the rest

## Choosing the theme (dark vs light)

Match the screenshot's actual background. A dark-mode app screenshot on a cream canvas creates an awkward contrast frame that draws the eye to the seam, not the content.

If the user's app supports both modes and they uploaded both, alternate themes for visual rhythm: dark-light-dark-light-dark across the 5 screens. If they only uploaded one mode, all 5 should match.

### Default palettes by theme

The generator picks these unless overridden. They're tuned so the headline reads cleanly on the background with no extra adjustment needed.

**Dark theme defaults**
- bg: `#0A0E1A` (deep midnight blue-black)
- fg_head: `#FFFFFF` (pure white)
- fg_sub: `#AAB4C8` (cool gray)

**Light theme defaults**
- bg: `#F5ECDE` (warm cream)
- fg_head: `#161618` (near-black)
- fg_sub: `#5F5F69` (warm gray)

### Per-screen background variants

For visual variety across the set, vary the dark backgrounds slightly screen-to-screen:

- Markets/list screen: `#0A0E1A` (midnight blue)
- Chart/depth screen: `#181224` (deep purple)
- Profile/favorites screen: `#080A0E` (near-black)
- News/content screen: `#0E1014` (charcoal)

For light:
- Cream: `#F5ECDE`
- Warm sand: `#F2E5D5`
- Soft pearl: `#F4EFE5`

The generator accepts any hex — don't feel locked to these.

## Picking the brand color

The brand color shows up in the pill and the feature graphic accent. Should be:

- **Saturated enough to read against any background** — pure pastels disappear on light themes; pure neon clashes with dark themes
- **Consistent with the app category** (see SKILL.md table for category defaults)
- **Distinct from the screenshot's dominant UI color** if possible — using BTC orange when the app's UI also uses BTC orange creates a same-color blob

If the user has no opinion, defaulting to the category color is safer than picking something unique.

## Handling sparse screens

A "sparse screen" is one where the app shows lots of empty space (e.g. a Favorites list with one item, an empty Inbox, a freshly-created profile).

### iPhone — use `collapse_middle`

The iPhone layout bleeds the phone off the bottom of the canvas. With a sparse screen, this means the user sees only the top content — and a lot of it might be empty space. The fix is to glue the top content to the bottom navbar, hiding the empty middle.

In the config:

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

- `top_keep`: pixels from the top of the screenshot to keep (header + the visible content)
- `bottom_keep`: pixels from the bottom to keep (typically the tab bar)
- `bg_color`: the screen's background — used for the seam, must match or the seam will be visible

### iPad — don't collapse

The iPad layout centers the phone in the right column. A collapsed phone there reads as a stubby card and looks inconsistent with the other phones in the set. Leave the iPad version using the original screenshot — the empty middle is visible but not jarring because the canvas around the phone gives breathing room.

If you really want to collapse on iPad too (rare), use the `preprocess_ipad` field instead — it overrides the iPad rendering specifically.

## The feature graphic

The Play Store feature graphic is a different design problem from the screenshots. It's a wide hero banner (1024 × 500), not a portrait card.

### What works

- **Bold app name on the left.** Inter Black at 132 px. This is the brand moment.
- **One-sentence tagline below.** ≤6 words is ideal.
- **A "feature row" of 3–4 items separated by middots.** "Live prices · Pro charts · No ads"
- **One phone screenshot on the right.** Pick the screen that most clearly says what the app does.
- **Optional accent_letter.** If the app name has a short, distinctive letter (the O in UTXO, the M in Monzo), rendering it on a colored disc gives the wordmark a memorable mark without needing a real logo. For category-themed apps you can substitute a unicode glyph inside the disc — set `glyph` to `"₿"` for Bitcoin apps, `"⚡"` for instant/fast apps, etc., with `glyph_scale: 0.62` to keep the disc visible around it (matching the canonical coin look).

### What doesn't work

- Multiple phones — visually busy at this size
- Long taglines that wrap — keeps the eye busy
- Dark text on a dark background — the Play Store dark-mode preview will look broken
- Edge-to-edge composition — Play Store overlays the install button on the bottom-right

### When to skip accent_letter

If the app name is generic (e.g. "Notes", "Tracker"), the disc treatment makes it look like a startup logo competition. Stick to plain bold text.

If the app name is too long (>5 chars), the disc throws off the proportion. UTXO works because it's 4 chars; "Productivity" wouldn't.

## When to override defaults

The generator's defaults are tuned for "good" — they should rarely need overriding. But when:

- **Text is too small/large for the wrap to feel right:** override `head_size` per screen
- **The brand color reads poorly on the chosen background:** specify `accent` per screen instead of inheriting `app.brand_color`
- **The phone bezel needs to match the screenshot's exact background:** override `bezel_color` per screen — most useful for light-mode screens where the default bezel matches the canvas

For everything else, trust the defaults and iterate on copy first.
