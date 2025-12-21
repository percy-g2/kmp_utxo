# iOS Widget Extension Documentation

Complete guide for the UTXO iOS Widget Extension implementation.

## Table of Contents

1. [Overview](#overview)
2. [Setup Instructions](#setup-instructions)
3. [App Groups Configuration](#app-groups-configuration)
4. [Features](#features)
5. [Data Flow](#data-flow)
6. [Widget Refresh Behavior](#widget-refresh-behavior)
7. [Debugging](#debugging)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The iOS Widget Extension displays favorite cryptocurrencies with live prices, change percentages, volumes, and sparkline charts. It matches the Android widget functionality and design.

### Files Structure

- `UTXOWidget.swift` - Main widget configuration and timeline provider
- `FavoritesWidgetEntryView.swift` - SwiftUI views for widget display (medium and large sizes)
- `WidgetDataHelper.swift` - Helper functions for data fetching, formatting, and settings reading
- `UTXOWidgetBundle.swift` - Widget bundle entry point
- `RefreshWidgetIntent.swift` - AppIntent for manual refresh
- `Info.plist` - Widget extension configuration

### Widget Sizes

- **Medium Widget**: Shows 2 favorite coins
- **Large Widget**: Shows 4 favorite coins

---

## Setup Instructions

### Step 1: Add Widget Extension to Xcode Project

1. **Open the project in Xcode:**
   ```bash
   open iosApp/iosApp.xcodeproj
   ```

2. **Add Widget Extension Target:**
   - In Xcode, go to **File → New → Target**
   - Select **"Widget Extension"**
   - Product Name: `UTXOWidget`
   - Organization Identifier: `org.androdevlinux.utxo`
   - Language: **Swift**
   - Include Configuration Intent: **No**
   - Click **Finish**

3. **Replace Generated Files:**
   - Delete the auto-generated widget files in the new target
   - Copy all files from `iosApp/UTXOWidget/` to the widget extension target folder
   - Add them to the widget extension target in Xcode

4. **Configure Signing:**
   - Select the widget extension target
   - Go to **Signing & Capabilities**
   - Configure signing (use same team as main app)
   - Bundle identifier should be: `org.androdevlinux.utxo.UTXOWidget`

5. **Build Settings:**
   - Set iOS Deployment Target to **15.0 or higher** (matching main app)
   - Ensure Swift version is **5.0 or higher**

### Step 2: Configure Deep Linking (Optional)

To enable deep linking from widget to app:

- In `iOSApp.swift` or `ContentView.swift`, handle URLs:
  - `utxo://favorites` - Opens favorites screen
  - `utxo://coin/{SYMBOL}` - Opens coin detail screen

The URL scheme (`utxo://`) is already configured in the main app's `Info.plist`.

---

## App Groups Configuration

**Important:** Widget extensions run in a separate process and **cannot access the main app's cache directory**. You must configure App Groups to share data between the app and widget.

### Why App Groups Are Needed

Without App Groups, the widget will show "No favorites added" even when you have favorites, because it cannot access the main app's cache directory.

### Step-by-Step Setup

1. **Main App Target:**
   - Select the main app target in Xcode
   - Go to **Signing & Capabilities**
   - Click **+ Capability**
   - Add **App Groups**
   - Click **+** and add: `group.org.androdevlinux.utxo`
   - Ensure it's checked/enabled

2. **Widget Extension Target:**
   - Select the widget extension target (`UTXOWidget`)
   - Go to **Signing & Capabilities**
   - Click **+ Capability**
   - Add **App Groups**
   - Click **+** and add: `group.org.androdevlinux.utxo`
   - Ensure it's checked/enabled

3. **Verify Both Targets:**
   - Both targets must be signed with the **same team**
   - Both must have the same App Group ID: `group.org.androdevlinux.utxo`

### Data Sharing Implementation

The app syncs settings to App Group UserDefaults when favorites change:

- **Kotlin side** (`App.ios.kt`): `syncSettingsToWidget()` writes to App Group UserDefaults
- **Swift side** (`WidgetDataHelper.swift`): Reads from App Group UserDefaults

The widget reads settings from:
1. **App Group UserDefaults**: `group.org.androdevlinux.utxo` (primary)
2. **App Group file container**: `group.org.androdevlinux.utxo/settings.json` (fallback)
3. **Cache directory**: `{CachesDirectory}/settings.json` (last resort)

---

## Features

✅ **Displays up to 4 favorites** (2 in medium widget, 4 in large widget)  
✅ **Live prices and change percentages** with color coding (green/red)  
✅ **Volume display** with smart formatting (B, M, K)  
✅ **Sparkline charts** for each coin  
✅ **Automatic refresh** every 5 minutes (managed by iOS WidgetKit)  
✅ **Manual refresh button** (↻ icon in header)  
✅ **Theme-aware** (respects system light/dark mode)  
✅ **Deep linking** - Tap widget to open app, tap coin to open coin details  
✅ **Empty state** - Shows message when no favorites are added  

---

## Data Flow

### 1. Settings Storage

The widget reads settings from (in priority order):

1. **App Group UserDefaults**: `group.org.androdevlinux.utxo`
   - Key: `favPairs` (JSON string array)
   - Key: `selectedTradingPair` (string)
   - Key: `appTheme` (string)

2. **App Group file container**: `group.org.androdevlinux.utxo/settings.json`

3. **Cache directory**: `{CachesDirectory}/settings.json` (fallback)

### 2. Data Fetching

Widget fetches data from Binance API:

- **Ticker**: `https://api.binance.com/api/v3/ticker/24hr?symbol={SYMBOL}`
- **Chart**: `https://api.binance.com/api/v3/uiKlines?symbol={SYMBOL}&interval=1s&limit=1000`

### 3. Deep Linking

- **Widget → App**: `utxo://favorites` (opens app)
- **Widget → Coin Detail**: `utxo://coin/{SYMBOL}` (opens coin detail screen)

The app handles these URLs in `iOSApp.swift` and stores pending navigation in UserDefaults for Kotlin code to read.

---

## Widget Refresh Behavior

### Automatic Refresh

iOS widgets refresh automatically on a schedule managed by the system. The UTXO widget is configured to refresh every **5 minutes**.

**When Widget Shows Updated Data:**

1. **Automatic Refresh**: Every 5 minutes (managed by iOS)
2. **After Adding Widget**: Widget loads immediately when first added
3. **After App Launch**: Widget may refresh when app is opened
4. **Manual Refresh**: Tap the refresh button (↻) in the widget header

### Manual Refresh

The widget includes a refresh button (↻ icon) in the header that triggers an immediate refresh via `RefreshWidgetIntent`.

### Force Immediate Refresh

**Option 1: Use Refresh Button (Recommended)**
- Tap the refresh icon (↻) in the widget header
- Widget will reload immediately

**Option 2: Remove and Re-add Widget**
1. Long press widget → Remove Widget
2. Long press home screen → + → UTXO Favorites
3. Widget will load with latest favorites

**Option 3: Wait for Auto-Refresh**
- Widget automatically refreshes every 5 minutes
- No action needed

### Why Widget Might Show Old Data

If you:
1. Add the widget **before** adding favorites → Widget shows "No favorites"
2. Then add favorites in the app → Widget still shows "No favorites" until next refresh

**Solution**: Tap the refresh button, wait up to 5 minutes for automatic refresh, or remove and re-add the widget.

### Current Implementation

The widget:
- ✅ Reads favorites from App Group UserDefaults (shared with main app)
- ✅ Auto-refreshes every 5 minutes
- ✅ Shows latest data when first added
- ✅ Supports manual refresh via button
- ⚠️ Does NOT immediately refresh when favorites change in app (by design - iOS limitation)

This is normal iOS widget behavior. Widgets are designed to refresh on a schedule, not instantly when app data changes, to preserve battery life.

---

## Debugging

### Quick Checklist

1. **App Groups Configured?**
   - [ ] Main app target has App Groups capability with `group.org.androdevlinux.utxo`
   - [ ] Widget extension target has App Groups capability with `group.org.androdevlinux.utxo`
   - [ ] Both are signed with the same team

2. **Check Console Logs**
   When the widget loads, look for these logs:
   - `WidgetDataHelper: App Group UserDefaults available` - Means App Groups is configured
   - `WidgetDataHelper: Found favPairs JSON string:` - Means data was found
   - `WidgetDataHelper: Successfully decoded favorites` - Means favorites were read successfully
   - `UTXOWidget: Loaded X favorites from settings` - Final count

3. **Check App Logs**
   When you add/remove favorites, look for:
   - `Syncing to App Group: favPairs=[...]` - Means sync is being called
   - `Successfully synced settings to App Group UserDefaults` - Means sync succeeded

### How to See Widget Extension Logs

Widget extensions run in a **separate process** from your main app, so their logs appear separately in Xcode.

**Method 1: Run Widget Extension Target**
1. In Xcode, select the **UTXOWidget** scheme (not the main app)
2. Run the widget extension target
3. When prompted, choose a simulator/device
4. The widget will appear and you'll see its logs in the console

**Method 2: Filter Console Logs**
1. Run your main app normally
2. In Xcode Console, use the filter/search box
3. Type: `UTXOWidget` or `WidgetDataHelper`
4. This will show only widget-related logs

**Method 3: Check System Console (macOS)**
1. Open Console.app (Applications → Utilities → Console)
2. Filter by your app name or "UTXOWidget"
3. Widget logs will appear here

### What to Look For

When the widget loads, you should see logs like:

```
WidgetDataHelper: Attempting to read from App Group UserDefaults...
WidgetDataHelper: ✅ App Group UserDefaults available
WidgetDataHelper: All UserDefaults keys: [...]
WidgetDataHelper: ✅ Found favPairs JSON string: ["BTCUSDT"]
WidgetDataHelper: ✅ Successfully decoded favorites: ["BTCUSDT"]
UTXOWidget: ✅ Found 1 favorites
```

### Common Issues

**Issue: "App Group UserDefaults not available"**
- **Solution:** App Groups capability is not configured. Add it to both targets in Xcode.

**Issue: "No favPairs key found in UserDefaults"**
- **Solution:** The app isn't syncing. Check if `syncSettingsToWidget` is being called when favorites change.

**Issue: "Failed to decode favPairs JSON"**
- **Solution:** JSON format mismatch. Check the console for the actual JSON string.

**Issue: Widget shows "No favorites added"**
- **Solution:** 
  - Make sure you have added favorites in the app
  - Check that settings.json exists in cache directory or app group
  - Verify App Groups are configured correctly

**Issue: Widget doesn't update**
- **Solution:** 
  - iOS manages widget updates automatically
  - Updates occur every 5 minutes or when system determines appropriate
  - You can force refresh by tapping the refresh button or removing and re-adding the widget

**Issue: Deep linking doesn't work**
- **Solution:** 
  - Ensure URL scheme is configured in Info.plist (already done)
  - Check that the app handles URLs in `iOSApp.swift` (already implemented)

### Test UserDefaults Access

To verify the widget can read from App Group UserDefaults, add this test code temporarily to your widget:

Add this to `UTXOWidget.swift` in the `loadFavoritesData()` function, right at the start:

```swift
// TEST: Direct UserDefaults access
if let testDefaults = UserDefaults(suiteName: "group.org.androdevlinux.utxo") {
    print("🔵 TEST: App Group UserDefaults accessible")
    let allKeys = testDefaults.dictionaryRepresentation().keys
    print("🔵 TEST: All keys: \(Array(allKeys))")
    
    if let testFavPairs = testDefaults.string(forKey: "favPairs") {
        print("🔵 TEST: ✅ Found favPairs: \(testFavPairs)")
    } else {
        print("🔵 TEST: ❌ favPairs not found as string")
        if let testObject = testDefaults.object(forKey: "favPairs") {
            print("🔵 TEST: Found as object: \(testObject), type: \(type(of: testObject))")
        } else {
            print("🔵 TEST: ❌ favPairs key doesn't exist")
        }
    }
} else {
    print("🔵 TEST: ❌ Cannot access App Group UserDefaults")
}
```

### Verify App Groups Setup

Run this in Xcode Console while debugging the widget:

```swift
if let defaults = UserDefaults(suiteName: "group.org.androdevlinux.utxo") {
    print("App Group available!")
    print("Keys: \(defaults.dictionaryRepresentation().keys)")
    if let favPairs = defaults.string(forKey: "favPairs") {
        print("favPairs: \(favPairs)")
    }
} else {
    print("App Group NOT available - check configuration")
}
```

---

## Testing

### Build and Test

1. **Build the widget extension target**
2. **Run the app on a device or simulator**
3. **Add the widget to home screen:**
   - Long press on home screen
   - Tap the "+" button
   - Search for "Favorites" or "UTXO"
   - Select widget size (Medium or Large)
   - Add to home screen

### Manual Test Flow

1. Open the app
2. Add a favorite (e.g., BTCUSDT)
3. Check Xcode Console - you should see sync logs:
   ```
   Syncing to App Group: favPairs=[BTCUSDT]
   Successfully synced settings to App Group UserDefaults
   ```
4. Force refresh the widget:
   - Tap the refresh button (↻) in the widget header, OR
   - Remove and re-add the widget
5. Check widget logs - should see favorites:
   ```
   UTXOWidget: ✅ Found 1 favorites
   ```

---

## Troubleshooting

### Build Errors

- **Issue:** Build fails with missing symbols
  - **Solution:** Ensure all files are added to the widget extension target in Xcode

- **Issue:** Swift version errors
  - **Solution:** Ensure Swift version is 5.0 or higher

- **Issue:** iOS Deployment Target errors
  - **Solution:** Set iOS Deployment Target to 15.0 or higher (matching main app)

### Runtime Issues

- **Issue:** Widget crashes on launch
  - **Solution:** Check that all required files are included in the widget target
  - **Solution:** Verify Info.plist is correctly configured

- **Issue:** Widget shows blank screen
  - **Solution:** Check console logs for errors
  - **Solution:** Verify data fetching is working (check network logs)

### Data Issues

- **Issue:** Widget shows "No favorites added" but you have favorites
  - **Solution:** Verify App Groups are configured correctly
  - **Solution:** Check that `syncSettingsToWidget` is being called
  - **Solution:** Check console logs for data sync errors

- **Issue:** Widget shows old data
  - **Solution:** Tap the refresh button
  - **Solution:** Wait for automatic refresh (up to 5 minutes)
  - **Solution:** Remove and re-add the widget

---

## Notes

- Widget updates are managed by iOS WidgetKit (automatic refresh every 5 minutes)
- Widget has limited network access and execution time
- Charts are rendered using SwiftUI Path (Charts framework not available in widgets)
- Widget respects system appearance (light/dark mode)
- The widget matches the Android widget functionality and design
- Widget extensions run in a separate process from the main app
- App Groups are required for data sharing between app and widget

---

## Additional Resources

- [Apple WidgetKit Documentation](https://developer.apple.com/documentation/widgetkit)
- [App Groups Documentation](https://developer.apple.com/documentation/xcode/configuring-app-groups)
- [Widget Extension Guide](https://developer.apple.com/documentation/widgetkit/creating-a-widget-extension)

