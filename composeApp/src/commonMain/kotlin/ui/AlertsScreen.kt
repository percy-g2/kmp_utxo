package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import domain.model.PriceAlert
import notification.conditionSummary
import org.jetbrains.compose.resources.stringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.alerts_dismiss
import utxo.composeapp.generated.resources.alerts_empty
import utxo.composeapp.generated.resources.alerts_enable
import utxo.composeapp.generated.resources.alerts_notification_banner_body
import utxo.composeapp.generated.resources.alerts_notification_banner_title
import utxo.composeapp.generated.resources.alerts_title
import utxo.composeapp.generated.resources.back
import utxo.composeapp.generated.resources.delete
import utxo.composeapp.generated.resources.please_wait_fetching

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    showUpNavigation: Boolean,
    onNavigateUp: () -> Unit,
    viewModel: AlertsViewModel = viewModel { AlertsViewModel() },
) {
    val uiState by viewModel.uiState.collectAsState(AlertsUiState.Loading)
    var bannerDismissed by remember { mutableStateOf(false) }
    val showBanner = !viewModel.areNotificationsEnabled() && !bannerDismissed

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.alerts_title)) },
                navigationIcon = {
                    if (showUpNavigation) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showBanner) {
                NotificationPermissionBanner(
                    onEnable = { viewModel.requestNotificationPermission { } },
                    onDismiss = { bannerDismissed = true },
                )
            }
            when (val s = uiState) {
                AlertsUiState.Empty -> {
                    Text(
                        stringResource(Res.string.alerts_empty),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                AlertsUiState.Loading -> {
                    Text(stringResource(Res.string.please_wait_fetching), modifier = Modifier.padding(24.dp))
                }
                is AlertsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        for ((sym, alerts) in s.grouped.entries) {
                            item(key = "h_$sym") {
                                Text(
                                    sym,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                            items(alerts, key = { it.id }) { alert ->
                                AlertRowCard(alert, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRowCard(
    alert: PriceAlert,
    viewModel: AlertsViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(alert.conditionSummary(), style = MaterialTheme.typography.bodyLarge)
                Text(alert.displayName, style = MaterialTheme.typography.labelSmall)
            }
            Switch(
                checked = alert.isEnabled,
                onCheckedChange = { viewModel.toggleAlert(alert.id, it) },
            )
            IconButton(onClick = { viewModel.deleteAlert(alert.id) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(Res.string.alerts_notification_banner_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.alerts_notification_banner_body), style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.alerts_dismiss))
                }
                TextButton(onClick = onEnable) {
                    Text(stringResource(Res.string.alerts_enable))
                }
            }
        }
    }
}
