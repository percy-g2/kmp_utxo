package org.androdevlinux.utxo

import App
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current
            val settingsState by store.updates.collectAsState(initial = Settings(appTheme = AppTheme.System))
            val view = LocalView.current

            if (!view.isInEditMode) {
                // Determine color scheme based on themeState and system theme
                val colorScheme = when (settingsState?.appTheme) {
                    AppTheme.Light -> LightColorScheme
                    AppTheme.System -> DarkColorScheme
                    else -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
                }

                // Set the system UI bar colors based on the app and system theme
                val scrimColor = colorScheme.background.toArgb()
                val darkScrimColor = colorScheme.onBackground.toArgb()

                DisposableEffect(lifecycleOwner, settingsState?.appTheme) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            when (settingsState?.appTheme) {
                                AppTheme.Light -> {
                                    enableEdgeToEdge(
                                        statusBarStyle = SystemBarStyle.light(scrimColor, scrimColor),
                                        navigationBarStyle = SystemBarStyle.light(scrimColor, scrimColor)
                                    )
                                }

                                AppTheme.Dark -> {
                                    enableEdgeToEdge(
                                        statusBarStyle = SystemBarStyle.dark(darkScrimColor),
                                        navigationBarStyle = SystemBarStyle.dark(darkScrimColor)
                                    )
                                }

                                else -> {
                                    enableEdgeToEdge()
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
