package org.androdevlinux.utxo

import App
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
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
    private var backPressedTime = 0L
    private var backToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

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

    private fun handleBackPress() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - backPressedTime < 2000) {
            // Pressed back twice within 2 seconds, exit the app
            backToast?.cancel()
            finish()
        } else {
            // First back press, show toast
            backPressedTime = currentTime
            backToast?.cancel()
            backToast = Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT)
            backToast?.show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backToast?.cancel()
    }
}

@Preview
@Composable
fun Preview() {
    ContextProvider.setContext(LocalContext.current)
    App()
}

class AppInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ContextProvider.setContext(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

object ContextProvider {
    private lateinit var appContext: Context

    fun setContext(context: Context) {
        appContext = context
    }

    fun getContext(): Context {
        return appContext
    }
}