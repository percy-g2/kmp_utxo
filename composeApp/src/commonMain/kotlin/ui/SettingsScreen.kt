package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import model.RssProvider
import openLink
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import theme.ThemeManager
import theme.ThemeManager.store
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.cancel
import utxo.composeapp.generated.resources.github_icon
import utxo.composeapp.generated.resources.settings_about
import utxo.composeapp.generated.resources.settings_all_sources_enabled
import utxo.composeapp.generated.resources.settings_appearance
import utxo.composeapp.generated.resources.settings_dark_mode
import utxo.composeapp.generated.resources.settings_deselect_all
import utxo.composeapp.generated.resources.settings_done
import utxo.composeapp.generated.resources.settings_github
import utxo.composeapp.generated.resources.settings_news_sources
import utxo.composeapp.generated.resources.settings_no_sources_enabled
import utxo.composeapp.generated.resources.settings_privacy_policy
import utxo.composeapp.generated.resources.settings_select_all
import utxo.composeapp.generated.resources.settings_select_news_sources
import utxo.composeapp.generated.resources.settings_select_theme
import utxo.composeapp.generated.resources.settings_sources_count
import utxo.composeapp.generated.resources.settings_theme
import utxo.composeapp.generated.resources.settings_title
import utxo.composeapp.generated.resources.settings_use_dark_theme
import utxo.composeapp.generated.resources.settings_version
import utxo.composeapp.generated.resources.settings_version_number
import utxo.composeapp.generated.resources.settings_website
import utxo.composeapp.generated.resources.theme_dark
import utxo.composeapp.generated.resources.theme_light
import utxo.composeapp.generated.resources.theme_system

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPress: () -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRssProvidersDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val settings: Flow<Settings?> = store.updates
    val settingsState by settings.collectAsState(initial = Settings(appTheme = AppTheme.System))
    val isDarkTheme = settingsState?.appTheme == AppTheme.Dark || (settingsState?.appTheme == AppTheme.System && isSystemInDarkTheme())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.shadow(
                    elevation = 4.dp,
                    ambientColor = if (isDarkTheme) Color.White else Color.LightGray,
                    spotColor = if (isDarkTheme) Color.White else Color.LightGray
                ),
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                windowInsets = WindowInsets(
                    top = 0.dp,
                    bottom = 0.dp
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn {
                item {
                    SettingsHeader(title = stringResource(Res.string.settings_appearance))
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = stringResource(Res.string.settings_theme),
                        subtitle = getThemeDisplayName(settingsState?.appTheme ?: AppTheme.System),
                        onClick = { showThemeDialog = true }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(Res.string.settings_dark_mode),
                        subtitle = stringResource(Res.string.settings_use_dark_theme),
                        checked = isDarkTheme,
                        onCheckedChange = { isDark ->
                            coroutineScope.launch {
                                val theme = if (isDark) AppTheme.Dark else AppTheme.Light
                                ThemeManager.updateTheme(theme)
                            }
                        }
                    )
                }

                item {
                    SettingsHeader(title = stringResource(Res.string.settings_news_sources))
                    val currentEnabled = settingsState?.enabledRssProviders ?: RssProvider.DEFAULT_ENABLED_PROVIDERS
                    val enabledCount = currentEnabled.size
                    val totalCount = RssProvider.ALL_PROVIDERS.size
                    val subtitle = when (enabledCount) {
                        0 -> stringResource(Res.string.settings_no_sources_enabled)
                        totalCount -> stringResource(Res.string.settings_all_sources_enabled)
                        else -> stringResource(Res.string.settings_sources_count, enabledCount, totalCount)
                    }
                    SettingsItem(
                        icon = Icons.Default.Article,
                        title = stringResource(Res.string.settings_news_sources),
                        subtitle = subtitle,
                        onClick = { showRssProvidersDialog = true }
                    )
                }

                item {
                    SettingsHeader(title = stringResource(Res.string.settings_about))
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = stringResource(Res.string.settings_version),
                        subtitle = stringResource(Res.string.settings_version_number),
                        onClick = null
                    )
                    SettingsItem(
                        icon = Icons.Default.Policy,
                        title = stringResource(Res.string.settings_privacy_policy),
                        onClick = { openLink("https://raw.githubusercontent.com/percy-g2/kmp_utxo/refs/heads/main/PrivacyPolicy.md") }
                    )
                    SettingsItem(
                        icon = Icons.Default.Web,
                        title = stringResource(Res.string.settings_website),
                        onClick = { openLink("https://androdevlinux.com/") }
                    )
                    SettingsItem(
                        icon = vectorResource(Res.drawable.github_icon),
                        title = stringResource(Res.string.settings_github),
                        onClick = { openLink("https://github.com/percy-g2/kmp_utxo") }
                    )
                }
            }

            if (showThemeDialog) {
                ThemeSelectionDialog(
                    currentTheme = settingsState?.appTheme ?: AppTheme.System,
                    onThemeSelected = {
                        coroutineScope.launch {
                            ThemeManager.updateTheme(it)
                        }
                    },
                    onDismiss = { showThemeDialog = false }
                )
            }
            
            if (showRssProvidersDialog) {
                RssProvidersSelectionDialog(
                    enabledProviders = settingsState?.enabledRssProviders ?: RssProvider.DEFAULT_ENABLED_PROVIDERS,
                    onProvidersChanged = { newProviders ->
                        coroutineScope.launch {
                            store.update { currentSettings ->
                                currentSettings?.copy(enabledRssProviders = newProviders)
                                    ?: Settings(enabledRssProviders = newProviders)
                            }
                        }
                    },
                    onDismiss = { showRssProvidersDialog = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}


@Composable
private fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_select_theme)) },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    RadioButtonItem(
                        text = getThemeDisplayName(theme),
                        selected = theme == currentTheme,
                        onClick = {
                            onThemeSelected(theme)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun getThemeDisplayName(theme: AppTheme): String {
    return when (theme) {
        AppTheme.System -> stringResource(Res.string.theme_system)
        AppTheme.Light -> stringResource(Res.string.theme_light)
        AppTheme.Dark -> stringResource(Res.string.theme_dark)
    }
}

@Composable
private fun RadioButtonItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun RssProvidersSelectionDialog(
    enabledProviders: Set<String>,
    onProvidersChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var localEnabledProviders by remember(enabledProviders) { mutableStateOf(enabledProviders) }
    val allSelected = localEnabledProviders.size == RssProvider.ALL_PROVIDERS.size
    val noneSelected = localEnabledProviders.isEmpty()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(Res.string.settings_select_news_sources),
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Select All / Deselect All buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            localEnabledProviders = if (allSelected) {
                                emptySet()
                            } else {
                                RssProvider.ALL_PROVIDERS.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(
                            text = if (allSelected) {
                                stringResource(Res.string.settings_deselect_all)
                            } else {
                                stringResource(Res.string.settings_select_all)
                            }
                        )
                    }
                }
                
                // Providers list
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    RssProvider.ALL_PROVIDERS.forEach { provider ->
                        val isEnabled = localEnabledProviders.contains(provider.id)
                        CheckboxItem(
                            text = provider.name,
                            checked = isEnabled,
                            onClick = {
                                localEnabledProviders = if (isEnabled) {
                                    localEnabledProviders - provider.id
                                } else {
                                    localEnabledProviders + provider.id
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onProvidersChanged(localEnabledProviders)
                    onDismiss()
                }
            ) {
                Text(stringResource(Res.string.settings_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

@Composable
private fun CheckboxItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() }
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Serializable
data class Settings(
    val appTheme: AppTheme = AppTheme.System,
    val favPairs: List<String> = listOf(""),
    val selectedTradingPair: String = "BTC",
    val enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS
)

enum class AppTheme {
    System, Light, Dark
}