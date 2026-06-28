package ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bottom content padding that lets a scrolling list flow UNDER the iOS 26 native Liquid Glass tab
 * bar — so the bar frosts the list content (Contacts-style) — while keeping the last item scrollable
 * fully clear of the bar.
 *
 * On the iOS 26 native-TabView path each tab's Compose view extends edge-to-edge under the floating
 * bar (via `.ignoresSafeArea(.container, edges: .bottom)` in LiquidGlassRootView.swift), and each
 * screen applies only the TOP window inset to its body so the scroll viewport reaches the screen
 * bottom. The bar's footprint is then reserved here as list `contentPadding.bottom` (NOT as parent
 * padding, which would keep the viewport above the bar).
 *
 * [clearance] is that footprint, passed ONLY by the iOS-26 ComposeUIViewController hosts
 * (MainViewController.kt). It defaults to 0.dp everywhere else (the shared App() path on
 * Android / Desktop / Web / iOS < 26), making this a no-op so those platforms are unchanged.
 */
fun bottomBarClearancePadding(clearance: Dp): Dp = clearance.coerceAtLeast(0.dp)
