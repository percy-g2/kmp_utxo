package org.androdevlinux.utxo

import App
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.startup.Initializer
import theme.DarkColorScheme
import theme.LightColorScheme
import theme.ThemeManager.store
import ui.AppTheme
import ui.Settings

class MainActivity : ComponentActivity() {
    private var backToast: Toast? = null
    
    companion object {
        const val EXTRA_COIN_SYMBOL = "coin_symbol"
        const val EXTRA_COIN_DISPLAY_SYMBOL = "coin_display_symbol"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle intent extras for coin detail navigation
        handleCoinDetailIntent(intent)

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val settingsState by store.updates.collectAsState(initial = Settings(appTheme = AppTheme.System))
            val view = LocalView.current

            if (!view.isInEditMode) {
                // Determine color scheme based on themeState and system theme
                val isSystemDark = isSystemInDarkTheme()

                DisposableEffect(lifecycleOwner, settingsState?.appTheme, isSystemDark) {
                    // Update status bar immediately when theme changes
                    val currentTheme = settingsState?.appTheme
                    val currentColorScheme = when (currentTheme) {
                        AppTheme.Light -> LightColorScheme
                        AppTheme.Dark -> DarkColorScheme
                        else -> if (isSystemDark) DarkColorScheme else LightColorScheme
                    }

                    val scrimColor = currentColorScheme.background.toArgb()

                    when (currentTheme) {
                        AppTheme.Light -> {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.light(scrimColor, scrimColor),
                                navigationBarStyle = SystemBarStyle.light(scrimColor, scrimColor)
                            )
                        }

                        AppTheme.Dark -> {
                            enableEdgeToEdge(
                                statusBarStyle = SystemBarStyle.dark(scrimColor),
                                navigationBarStyle = SystemBarStyle.dark(scrimColor)
                            )
                        }

                        else -> {
                            // System theme - adapt to current system theme
                            if (isSystemDark) {
                                enableEdgeToEdge(
                                    statusBarStyle = SystemBarStyle.dark(scrimColor),
                                    navigationBarStyle = SystemBarStyle.dark(scrimColor)
                                )
                            } else {
                                enableEdgeToEdge(
                                    statusBarStyle = SystemBarStyle.light(scrimColor, scrimColor),
                                    navigationBarStyle = SystemBarStyle.light(scrimColor, scrimColor)
                                )
                            }
                        }
                    }

                    // Capture values for the observer callback
                    val capturedTheme = currentTheme

                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            // Re-apply status bar style on resume using captured values
                            // Note: We use captured values since we can't call composable functions here
                            when (capturedTheme) {
                                AppTheme.Light -> {
                                    enableEdgeToEdge(
                                        statusBarStyle = SystemBarStyle.light(
                                            scrimColor,
                                            scrimColor
                                        ),
                                        navigationBarStyle = SystemBarStyle.light(
                                            scrimColor,
                                            scrimColor
                                        )
                                    )
                                }

                                AppTheme.Dark -> {
                                    enableEdgeToEdge(
                                        statusBarStyle = SystemBarStyle.dark(scrimColor),
                                        navigationBarStyle = SystemBarStyle.dark(scrimColor)
                                    )
                                }

                                else -> {
                                    // System theme - use captured system dark value
                                    if (isSystemDark) {
                                        enableEdgeToEdge(
                                            statusBarStyle = SystemBarStyle.dark(scrimColor),
                                            navigationBarStyle = SystemBarStyle.dark(scrimColor)
                                        )
                                    } else {
                                        enableEdgeToEdge(
                                            statusBarStyle = SystemBarStyle.light(
                                                scrimColor,
                                                scrimColor
                                            ),
                                            navigationBarStyle = SystemBarStyle.light(
                                                scrimColor,
                                                scrimColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
            }
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCoinDetailIntent(intent)
    }
    
    private fun handleCoinDetailIntent(intent: Intent?) {
        val symbol = intent?.getStringExtra(EXTRA_COIN_SYMBOL)
        val displaySymbol = intent?.getStringExtra(EXTRA_COIN_DISPLAY_SYMBOL)
        if (symbol != null && displaySymbol != null) {
            // Store in a way that App composable can access
            CoinDetailIntentHandler.setPendingCoinDetail(symbol, displaySymbol)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backToast?.cancel()
    }
}

class AppInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ContextProvider.setContext(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

// CoinDetailIntentHandler and ContextProvider are now in composeApp library
