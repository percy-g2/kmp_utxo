@file:OptIn(kotlin.time.ExperimentalTime::class)

package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.benasher44.uuid.uuid4
import data.repository.AlertRepositoryImpl
import domain.model.AlertCondition
import domain.model.PriceAlert
import kotlinx.coroutines.launch
import notification.NotificationService
import org.jetbrains.compose.resources.stringResource
import utxo.composeapp.generated.resources.Res
import utxo.composeapp.generated.resources.condition_pct_down
import utxo.composeapp.generated.resources.condition_pct_up
import utxo.composeapp.generated.resources.condition_price_above
import utxo.composeapp.generated.resources.condition_price_below
import utxo.composeapp.generated.resources.cooldown_minutes
import utxo.composeapp.generated.resources.current_price_label
import utxo.composeapp.generated.resources.set_alert
import utxo.composeapp.generated.resources.set_price_alert
import utxo.composeapp.generated.resources.target_value
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAlertBottomSheet(
    symbol: String,
    displaySymbol: String,
    currentPrice: Double?,
    onDismiss: () -> Unit,
    onAlertSaved: () -> Unit,
    alertRepository: AlertRepositoryImpl = remember { AlertRepositoryImpl() },
    notificationService: NotificationService = remember { NotificationService() },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var conditionKind by remember { mutableIntStateOf(0) }
    var targetText by remember { mutableStateOf("") }
    var cooldownMinutes by remember { mutableIntStateOf(60) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.set_price_alert), style = MaterialTheme.typography.titleLarge)
            currentPrice?.let { p ->
                Text(
                    stringResource(Res.string.current_price_label, p.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ConditionRow(0, conditionKind, stringResource(Res.string.condition_price_above)) { conditionKind = 0 }
            ConditionRow(1, conditionKind, stringResource(Res.string.condition_price_below)) { conditionKind = 1 }
            ConditionRow(2, conditionKind, stringResource(Res.string.condition_pct_up)) { conditionKind = 2 }
            ConditionRow(3, conditionKind, stringResource(Res.string.condition_pct_down)) { conditionKind = 3 }

            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text(stringResource(Res.string.target_value)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = cooldownMinutes.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let { cooldownMinutes = it.coerceAtLeast(1) } },
                label = { Text(stringResource(Res.string.cooldown_minutes)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val value = targetText.toDoubleOrNull() ?: return@Button
                    if (value <= 0) return@Button
                    val condition: AlertCondition =
                        when (conditionKind) {
                            0 -> AlertCondition.PriceAbove(value)
                            1 -> AlertCondition.PriceBelow(value)
                            2 -> AlertCondition.PercentChangeUp(value)
                            else -> AlertCondition.PercentChangeDown(value)
                        }
                    scope.launch {
                        notificationService.requestPermission { }
                        val alert =
                            PriceAlert(
                                id = uuid4().toString(),
                                symbol = symbol,
                                displayName = displaySymbol,
                                condition = condition,
                                isEnabled = true,
                                createdAt = Clock.System.now().toEpochMilliseconds(),
                                repeatAfterMinutes = cooldownMinutes,
                            )
                        alertRepository.addAlert(alert)
                        onAlertSaved()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.set_alert))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConditionRow(
    index: Int,
    selected: Int,
    label: String,
    onSelect: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = index == selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = index == selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
