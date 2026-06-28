package ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import model.HyperliquidWallet
import model.MAX_WALLETS
import model.RssProvider
import model.SCOPE_ALL
import model.displayName
import model.isValidHyperliquidAddress
import model.shortenAddress
import network.HlAccountCheck
import network.HyperliquidService
import openLink
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import theme.ThemeManager
import theme.ThemeManager.store
import ui.utils.bottomBarClearancePadding
import ui.utils.debouncedClickable
import ui.utils.isDarkTheme
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.cancel
import utxo.composeapp.generated.resources.github_icon
import utxo.composeapp.generated.resources.settings_about
import utxo.composeapp.generated.resources.settings_all_sources_enabled
import utxo.composeapp.generated.resources.settings_appearance
import utxo.composeapp.generated.resources.settings_clear
import utxo.composeapp.generated.resources.settings_dark_mode
import utxo.composeapp.generated.resources.settings_deselect_all
import utxo.composeapp.generated.resources.settings_add_all
import utxo.composeapp.generated.resources.settings_add_to_list
import utxo.composeapp.generated.resources.settings_add_wallet
import utxo.composeapp.generated.resources.settings_add_wallets_title
import utxo.composeapp.generated.resources.settings_bulk_done
import utxo.composeapp.generated.resources.settings_bulk_hint
import utxo.composeapp.generated.resources.settings_bulk_paste_label
import utxo.composeapp.generated.resources.settings_bulk_result
import utxo.composeapp.generated.resources.settings_bulk_status_added
import utxo.composeapp.generated.resources.settings_bulk_status_failed
import utxo.composeapp.generated.resources.settings_bulk_status_no_activity
import utxo.composeapp.generated.resources.settings_bulk_verify_add
import utxo.composeapp.generated.resources.settings_cart_empty
import utxo.composeapp.generated.resources.settings_cart_title
import utxo.composeapp.generated.resources.settings_paste_several
import utxo.composeapp.generated.resources.settings_remove
import utxo.composeapp.generated.resources.settings_wallets_count
import utxo.composeapp.generated.resources.settings_wallets_empty_body
import utxo.composeapp.generated.resources.settings_wallets_empty_title
import utxo.composeapp.generated.resources.settings_delete
import utxo.composeapp.generated.resources.settings_done
import utxo.composeapp.generated.resources.settings_duplicate_address
import utxo.composeapp.generated.resources.settings_edit_wallet
import utxo.composeapp.generated.resources.settings_github
import utxo.composeapp.generated.resources.settings_hyperliquid_address
import utxo.composeapp.generated.resources.settings_hyperliquid_address_hint
import utxo.composeapp.generated.resources.settings_hyperliquid_invalid
import utxo.composeapp.generated.resources.settings_news_sources
import utxo.composeapp.generated.resources.settings_no_sources_enabled
import utxo.composeapp.generated.resources.settings_portfolio
import utxo.composeapp.generated.resources.settings_privacy_policy
import utxo.composeapp.generated.resources.settings_save
import utxo.composeapp.generated.resources.settings_wallet_ens_badge
import utxo.composeapp.generated.resources.settings_wallet_limit
import utxo.composeapp.generated.resources.settings_wallet_label_hint
import utxo.composeapp.generated.resources.settings_wallet_label_optional
import utxo.composeapp.generated.resources.settings_wallet_name
import utxo.composeapp.generated.resources.settings_wallet_name_hint
import utxo.composeapp.generated.resources.settings_wallet_not_found
import utxo.composeapp.generated.resources.settings_wallet_verify_failed
import utxo.composeapp.generated.resources.settings_wallet_verifying
import utxo.composeapp.generated.resources.settings_select_all
import utxo.composeapp.generated.resources.settings_select_news_sources
import utxo.composeapp.generated.resources.settings_select_theme
import utxo.composeapp.generated.resources.settings_sources_count
import utxo.composeapp.generated.resources.settings_theme
import utxo.composeapp.generated.resources.settings_title
import utxo.composeapp.generated.resources.settings_use_dark_theme
import utxo.composeapp.generated.resources.settings_version
import utxo.composeapp.generated.resources.settings_website
import buildinfo.APP_VERSION_NAME
import utxo.composeapp.generated.resources.theme_dark
import utxo.composeapp.generated.resources.theme_light
import utxo.composeapp.generated.resources.theme_system

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackPress: () -> Unit,
    // Bottom clearance for the iOS 26 native glass tab bar; 0.dp (no-op) on every other path. See
    // ui.utils.bottomBarClearancePadding.
    bottomBarClearance: Dp = 0.dp,
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showRssProvidersDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val settings: Flow<Settings?> = store.updates
    val settingsState by settings.collectAsState(initial = Settings(appTheme = AppTheme.System))
    val isDarkTheme = isDarkTheme(settingsState)

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
        // On the iOS 26 path apply only the top inset so the list reaches the screen bottom and flows
        // UNDER the glass bar (the LazyColumn's bottom contentPadding keeps the last row scrollable
        // clear); off that path reserve the whole innerPadding exactly as before.
        val boxModifier = if (bottomBarClearance > 0.dp) {
            Modifier.padding(top = innerPadding.calculateTopPadding())
        } else {
            Modifier.padding(innerPadding)
        }
        Box(
            modifier = boxModifier
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    bottom = bottomBarClearancePadding(bottomBarClearance),
                ),
            ) {
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
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(Res.string.settings_news_sources),
                        subtitle = subtitle,
                        onClick = { showRssProvidersDialog = true }
                    )
                }

                item {
                    SettingsHeader(title = stringResource(Res.string.settings_portfolio))
                    PortfolioWalletsManager(
                        wallets = settingsState?.hyperliquidWallets ?: emptyList(),
                    )
                }

                item {
                    SettingsHeader(title = stringResource(Res.string.settings_about))
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = stringResource(Res.string.settings_version),
                        subtitle = APP_VERSION_NAME,
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
private fun PortfolioWalletsManager(
    wallets: List<HyperliquidWallet>,
) {
    val scope = rememberCoroutineScope()
    val service = remember { HyperliquidService() }
    DisposableEffect(Unit) {
        onDispose { service.close() }
    }

    var editTarget by remember { mutableStateOf<HyperliquidWallet?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    // Verify the address on Hyperliquid (only when new or actually changed), then persist.
    // Progress, a localized error, and completion are reported back to the calling surface.
    fun submit(
        old: String?,
        rawAddr: String,
        label: String?,
        setVerifying: (Boolean) -> Unit,
        onError: (StringResource) -> Unit,
        onDone: () -> Unit,
    ) {
        scope.launch {
            setVerifying(true)
            val addr = rawAddr.trim().lowercase()
            val check = if (old == null || addr != old) {
                withContext(Dispatchers.Default) { service.verifyAccount(addr) }
            } else {
                HlAccountCheck.Active
            }
            setVerifying(false)
            when (check) {
                HlAccountCheck.Active -> {
                    if (old == null) addHyperliquidWallet(addr, label) else updateHyperliquidWallet(old, addr, label)
                    onDone()
                }
                HlAccountCheck.Empty -> onError(Res.string.settings_wallet_not_found)
                HlAccountCheck.Unreachable -> onError(Res.string.settings_wallet_verify_failed)
            }
        }
    }

    val atCap = wallets.size >= MAX_WALLETS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (wallets.isEmpty()) {
            WalletsEmptyState(onAdd = { showAdd = true })
        } else {
            Text(
                text = stringResource(Res.string.settings_wallets_count, wallets.size, MAX_WALLETS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wallets.forEach { wallet ->
                WalletCard(
                    wallet = wallet,
                    onEdit = { editTarget = wallet },
                    onDelete = { scope.launch { deleteHyperliquidWallet(wallet.address) } },
                )
            }
            AddWalletButton(atCap = atCap, onClick = { showAdd = true })
        }
    }

    editTarget?.let { target ->
        EditWalletDialog(
            wallet = target,
            existing = wallets,
            onSave = { addr, label, setVerifying, onError, onDone ->
                submit(target.address, addr, label, setVerifying, onError, onDone)
            },
            onDismiss = { editTarget = null },
        )
    }

    if (showAdd) {
        AddWalletsDialog(
            existing = wallets,
            service = service,
            scope = scope,
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun WalletCard(
    wallet: HyperliquidWallet,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // The display name is the address itself when there is no label/ENS — so only show the
    // address as a subtitle when it adds new information (i.e. there is a name above it).
    val hasName = wallet.customLabel?.isNotBlank() == true || wallet.ensName?.isNotBlank() == true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WalletAvatar(wallet.address)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = wallet.displayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (wallet.customLabel == null && wallet.ensName != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = stringResource(Res.string.settings_wallet_ens_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (hasName) {
                    Text(
                        text = shortenAddress(wallet.address),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.settings_edit_wallet))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.settings_delete))
            }
        }
    }
}

@Composable
private fun WalletsEmptyState(onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(Res.string.settings_wallets_empty_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.settings_wallets_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            AddWalletButton(atCap = false, onClick = onAdd)
        }
    }
}

@Composable
private fun AddWalletButton(atCap: Boolean, onClick: () -> Unit) {
    if (atCap) {
        Text(
            text = stringResource(Res.string.settings_wallet_limit, MAX_WALLETS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(Res.string.settings_add_wallet),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * A 36dp circular monogram (first two hex nibbles) tinted with a colour derived from the
 * address, so each tracked wallet has a stable visual identity — especially in the aggregate view.
 */
@Composable
private fun WalletAvatar(address: String) {
    val hue = (((address.hashCode() % 360) + 360) % 360).toFloat()
    val background = Color.hsv(hue, 0.5f, 0.6f)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = address.removePrefix("0x").take(2).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun EditWalletDialog(
    wallet: HyperliquidWallet,
    existing: List<HyperliquidWallet>,
    onSave: (String, String?, (Boolean) -> Unit, (StringResource) -> Unit, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf(wallet.address) }
    var name by remember { mutableStateOf(wallet.customLabel.orEmpty()) }
    var verifying by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<StringResource?>(null) }

    val normalized = address.trim().lowercase()
    val isValid = isValidHyperliquidAddress(normalized)
    // A collision only counts against a *different* tracked wallet (keeping the same address is fine).
    val isDuplicate = isValid && normalized != wallet.address && existing.any { it.address == normalized }
    val showFormatError = address.isNotEmpty() && (!isValid || isDuplicate)
    val canSave = isValid && !isDuplicate && !verifying

    AlertDialog(
        onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text(stringResource(Res.string.settings_edit_wallet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it.trim()
                        verifyError = null
                    },
                    label = { Text(stringResource(Res.string.settings_hyperliquid_address)) },
                    placeholder = { Text(stringResource(Res.string.settings_hyperliquid_address_hint)) },
                    singleLine = true,
                    enabled = !verifying,
                    isError = showFormatError || verifyError != null,
                    trailingIcon = if (address.isNotEmpty() && !verifying) {
                        {
                            IconButton(onClick = {
                                address = ""
                                verifyError = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.settings_clear))
                            }
                        }
                    } else {
                        null
                    },
                    supportingText = when {
                        verifying -> {
                            { Text(stringResource(Res.string.settings_wallet_verifying)) }
                        }
                        isDuplicate -> {
                            { Text(stringResource(Res.string.settings_duplicate_address)) }
                        }
                        showFormatError -> {
                            { Text(stringResource(Res.string.settings_hyperliquid_invalid)) }
                        }
                        verifyError != null -> {
                            { Text(stringResource(verifyError!!)) }
                        }
                        else -> null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.settings_wallet_name)) },
                    placeholder = { Text(stringResource(Res.string.settings_wallet_name_hint)) },
                    singleLine = true,
                    enabled = !verifying,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (verifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = {
                        verifyError = null
                        onSave(
                            normalized,
                            name.trim().takeIf { it.isNotEmpty() },
                            { verifying = it },
                            { verifyError = it },
                            { onDismiss() },
                        )
                    },
                    enabled = canSave,
                ) {
                    Text(stringResource(Res.string.settings_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !verifying) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

private val HEX_ADDRESS_REGEX = Regex("0x[0-9a-fA-F]{40}")

private enum class BulkStatus { Queued, Verifying, Added, NotFound, Unreachable, Duplicate, OverLimit }

private data class BulkEntry(val address: String, val label: String?, val status: BulkStatus)

/**
 * Parse a free-form paste into wallets, line by line. A line may carry an optional label after the
 * address ("0x… Main" or "0x…, Main"); a line with several addresses contributes each with no label.
 * A tracked duplicate or a within-paste repeat is [BulkStatus.Duplicate], anything beyond the
 * remaining capacity is [BulkStatus.OverLimit], the rest are [BulkStatus.Queued] for verification.
 */
private fun parseBulkAddresses(
    raw: String,
    existingAddresses: Set<String>,
    remainingSlots: Int,
): List<BulkEntry> {
    val seen = mutableSetOf<String>()
    var slots = remainingSlots

    fun classify(address: String, label: String?): BulkEntry {
        val status = when {
            address in existingAddresses || !seen.add(address) -> BulkStatus.Duplicate
            slots <= 0 -> BulkStatus.OverLimit
            else -> {
                slots--
                BulkStatus.Queued
            }
        }
        return BulkEntry(address, label, status)
    }

    val result = mutableListOf<BulkEntry>()
    raw.lineSequence().forEach { line ->
        val matches = HEX_ADDRESS_REGEX.findAll(line).toList()
        when (matches.size) {
            0 -> Unit
            1 -> {
                val match = matches.first()
                val label = line.removeRange(match.range.first, match.range.last + 1)
                    .trim().trim(',', ';', '|', '"', '\'').trim()
                    .takeIf { it.isNotEmpty() }
                result.add(classify(match.value.lowercase(), label))
            }
            else -> matches.forEach { result.add(classify(it.value.lowercase(), null)) }
        }
    }
    return result
}

/**
 * Add one or many wallets in a single sitting. Build a cart (type an address + optional label and
 * "Add to list", or paste several at once), then "Verify & add" checks each on Hyperliquid in
 * parallel and commits the ones with real activity.
 */
@Composable
private fun AddWalletsDialog(
    existing: List<HyperliquidWallet>,
    service: HyperliquidService,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val cart = remember { mutableStateListOf<BulkEntry>() }
    var addr by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf("") }
    var committing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val existingAddrs = remember(existing) { existing.map { it.address }.toSet() }
    val cartAddrs = cart.map { it.address }.toSet()
    val used = existing.size + cart.size
    val full = used >= MAX_WALLETS

    val normalized = addr.trim().lowercase()
    val isValid = isValidHyperliquidAddress(normalized)
    val isDuplicate = isValid && (normalized in existingAddrs || normalized in cartAddrs)
    val showFormatError = addr.isNotEmpty() && (!isValid || isDuplicate)
    val canStage = isValid && !isDuplicate && !full

    val pasteToAdd = if (showPaste && paste.isNotBlank()) {
        parseBulkAddresses(paste, existingAddrs + cartAddrs, MAX_WALLETS - used)
            .count { it.status == BulkStatus.Queued }
    } else {
        0
    }

    fun stage() {
        cart.add(BulkEntry(normalized, label.trim().takeIf { it.isNotEmpty() }, BulkStatus.Queued))
        addr = ""
        label = ""
    }

    fun stagePaste() {
        val parsed = parseBulkAddresses(paste, existingAddrs + cartAddrs, MAX_WALLETS - used)
        cart.addAll(parsed.filter { it.status == BulkStatus.Queued })
        paste = ""
        showPaste = false
    }

    fun commit() {
        committing = true
        scope.launch {
            coroutineScope {
                cart.indices.forEach { i ->
                    launch {
                        cart[i] = cart[i].copy(status = BulkStatus.Verifying)
                        val check = withContext(Dispatchers.Default) { service.verifyAccount(cart[i].address) }
                        when (check) {
                            HlAccountCheck.Active -> {
                                addHyperliquidWallet(cart[i].address, cart[i].label)
                                cart[i] = cart[i].copy(status = BulkStatus.Added)
                            }
                            HlAccountCheck.Empty -> cart[i] = cart[i].copy(status = BulkStatus.NotFound)
                            HlAccountCheck.Unreachable -> cart[i] = cart[i].copy(status = BulkStatus.Unreachable)
                        }
                    }
                }
            }
            finished = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_add_wallets_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!committing) {
                    Text(
                        text = stringResource(Res.string.settings_wallets_count, used, MAX_WALLETS),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = addr,
                        onValueChange = { addr = it.trim() },
                        label = { Text(stringResource(Res.string.settings_hyperliquid_address)) },
                        placeholder = { Text(stringResource(Res.string.settings_hyperliquid_address_hint)) },
                        singleLine = true,
                        enabled = !full,
                        isError = showFormatError,
                        trailingIcon = if (addr.isNotEmpty()) {
                            {
                                IconButton(onClick = { addr = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.settings_clear))
                                }
                            }
                        } else {
                            null
                        },
                        supportingText = when {
                            full -> {
                                { Text(stringResource(Res.string.settings_wallet_limit, MAX_WALLETS)) }
                            }
                            isDuplicate -> {
                                { Text(stringResource(Res.string.settings_duplicate_address)) }
                            }
                            showFormatError -> {
                                { Text(stringResource(Res.string.settings_hyperliquid_invalid)) }
                            }
                            else -> null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(Res.string.settings_wallet_label_optional)) },
                        placeholder = { Text(stringResource(Res.string.settings_wallet_label_hint)) },
                        singleLine = true,
                        enabled = !full,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showPaste = !showPaste }, enabled = !full) {
                            Text(stringResource(Res.string.settings_paste_several))
                        }
                        TextButton(onClick = { stage() }, enabled = canStage) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(Res.string.settings_add_to_list),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    if (showPaste) {
                        OutlinedTextField(
                            value = paste,
                            onValueChange = { paste = it },
                            label = { Text(stringResource(Res.string.settings_bulk_paste_label)) },
                            placeholder = { Text(stringResource(Res.string.settings_bulk_hint)) },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { stagePaste() }, enabled = pasteToAdd > 0) {
                                Text(stringResource(Res.string.settings_add_all, pasteToAdd))
                            }
                        }
                    }
                    if (cart.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_cart_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.settings_cart_title, cart.size),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        cart.forEachIndexed { index, entry ->
                            CartRow(entry = entry, committing = false, onRemove = { cart.removeAt(index) })
                        }
                    }
                } else {
                    cart.forEach { entry -> CartRow(entry = entry, committing = true, onRemove = {}) }
                    if (finished) {
                        Text(
                            text = stringResource(
                                Res.string.settings_bulk_result,
                                cart.count { it.status == BulkStatus.Added },
                                cart.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!committing) {
                TextButton(enabled = cart.isNotEmpty(), onClick = { commit() }) {
                    Text(stringResource(Res.string.settings_bulk_verify_add, cart.size))
                }
            } else {
                TextButton(enabled = finished, onClick = onDismiss) {
                    Text(stringResource(Res.string.settings_bulk_done))
                }
            }
        },
        dismissButton = {
            if (!finished) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

@Composable
private fun CartRow(
    entry: BulkEntry,
    committing: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletAvatar(entry.address)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label ?: shortenAddress(entry.address),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.label != null) {
                Text(
                    text = shortenAddress(entry.address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (committing) {
            BulkStatusIndicator(entry.status)
        } else {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.settings_remove))
            }
        }
    }
}

@Composable
private fun BulkStatusIndicator(status: BulkStatus) {
    when (status) {
        BulkStatus.Verifying -> {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        BulkStatus.Added -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(Res.string.settings_bulk_status_added),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        BulkStatus.NotFound -> {
            Text(
                text = stringResource(Res.string.settings_bulk_status_no_activity),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BulkStatus.Unreachable -> {
            Text(
                text = stringResource(Res.string.settings_bulk_status_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        else -> Unit
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
            .debouncedClickable(onClick = onClick)
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
                    .wrapContentHeight()
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
            .debouncedClickable(onClick = onClick)
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
    val enabledRssProviders: Set<String> = RssProvider.DEFAULT_ENABLED_PROVIDERS,
    @Deprecated("Migrated into hyperliquidWallets; kept one release for migration read.")
    val hyperliquidWalletAddress: String = "",
    val hyperliquidWallets: List<HyperliquidWallet> = emptyList(),
    /** [SCOPE_ALL] (aggregate) or a single lowercased wallet address. */
    val portfolioScope: String = SCOPE_ALL,
)

enum class AppTheme {
    System, Light, Dark
}