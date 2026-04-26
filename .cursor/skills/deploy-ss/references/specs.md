# Store specs reference

Quick reference for App Store Connect and Google Play Console submission requirements. The generator outputs at compliant dimensions by default — this file is for when something gets rejected and you need to know why.

## App Store Connect — iPhone screenshots

For a single submission, you only need to provide screenshots for ONE iPhone Display size. Apple uses scaling to derive what's shown on smaller devices.

| Display | Resolution (portrait) | Devices | Notes |
|---|---|---|---|
| 6.9" | 1320 × 2868 | iPhone 16 Pro Max, 17 Pro Max | Newest, not always accepted depending on submission flow |
| 6.7" | 1290 × 2796 OR 1284 × 2778 | iPhone 14/15/16 Plus, Pro Max | **Default target.** Widest compatibility. |
| 6.5" | 1242 × 2688 | iPhone XS Max, 11 Pro Max | Legacy fallback |
| 6.1" | 1170 × 2532 | iPhone 12, 13, 14, 15 | Optional |
| 5.5" | 1242 × 2208 | iPhone 6+, 7+, 8+ | Optional, deprecated for new apps |

**The skill outputs 1284 × 2778** (6.7"). This is accepted in the 6.5"/6.7" submission slot and is the lowest-friction default.

If App Store Connect specifically rejects with "should be 1242 × 2688": change the canvas constant in `generate.py` to `PHONE_W, PHONE_H = 1242, 2688` and re-run.

## App Store Connect — iPad screenshots

| Display | Resolution (portrait) | Devices |
|---|---|---|
| 13" | 2064 × 2752 | iPad Pro M4 13" |
| 12.9" | 2048 × 2732 | iPad Pro 12.9" (3rd–6th gen) |
| 11" | 1668 × 2388 | iPad Pro 11", iPad Air |

**The skill outputs 2048 × 2732** (12.9"). This is accepted in the 12.9"/13" Display slot.

## App Store — counts and limits

- **Minimum:** 3 screenshots per device type
- **Maximum:** 10 screenshots per device type
- **Format:** PNG or JPEG, RGB, no transparency on the canvas
- **File size:** Each screenshot up to 8 MB

## Google Play — phone screenshots

Google is more lenient than Apple on dimensions:
- **Min:** 320 px on the shortest side
- **Max:** 3840 px on the shortest side
- **Aspect ratio:** Between 9:16 and 16:9
- **Format:** PNG or JPEG (24-bit RGB)
- **Count:** 2–8 per device type

The skill's iPhone output (1284 × 2778) sits comfortably inside these bounds, so the same files can be uploaded to Play Console without a separate render pass.

## Google Play — feature graphic

| Property | Value |
|---|---|
| Dimensions | **1024 × 500** (exact, no flexibility) |
| Format | PNG or JPEG |
| File size | Up to 15 MB |
| Required | Yes — Play Console will not let you publish without one |

**Safe area:** Google overlays the install button and other store chrome over the bottom-right corner of the feature graphic. Keep critical text and logos in the **left two-thirds** and away from the bottom-right ~250 × 150 px region.

The generator handles this automatically — text is anchored to the left, the phone screenshot floats in the right third.

## Google Play — counts and limits

- **App icon:** 512 × 512 PNG (separate from this skill — needs the actual app icon file)
- **Phone screenshots:** 2–8
- **7" tablet screenshots:** Optional, 0–8
- **10" tablet screenshots:** Optional, 0–8
- **Short description:** 80 characters
- **Full description:** 4000 characters

## Common rejection reasons

**Apple — "Promotional artwork shown in screenshots"**
Apple sometimes rejects screenshots that look "too marketing." If this triggers, reduce the headline size or remove the pill — the screenshot itself should remain the dominant element. The skill's defaults are usually safe; large hero text alone has not historically been a rejection cause.

**Apple — "Status bar inconsistency"**
The simulator screenshots show a 9:41 status bar with mock signal/battery indicators. Apple accepts this. Don't try to "fix" it.

**Google — "Misrepresenting your app"**
Don't put text in the feature graphic that the app doesn't actually deliver. Generic taglines are fine; specific claims ("free forever", "1M users") risk rejection if false or unverifiable.

**Both stores — "Excessive promotional text"**
Both stores discourage screenshots that are 50%+ marketing copy with the app barely visible. The skill keeps the phone as the dominant element by default — don't override headline sizes upward without reason.
