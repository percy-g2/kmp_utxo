package ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import data.repository.AlertRepositoryImpl
import domain.model.PriceAlert
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import notification.NotificationService

sealed class AlertsUiState {
    data object Loading : AlertsUiState()

    data class Success(
        val grouped: Map<String, List<PriceAlert>>,
    ) : AlertsUiState()

    data object Empty : AlertsUiState()
}

class AlertsViewModel(
    private val alertRepository: AlertRepositoryImpl = AlertRepositoryImpl(),
    private val notificationService: NotificationService = NotificationService(),
) : ViewModel() {
    val uiState =
        alertRepository
            .getAlertsFlow()
            .map { list ->
                when {
                    list.isEmpty() -> AlertsUiState.Empty
                    else -> {
                        val bySymbol = list.groupBy { it.symbol }
                        val sortedKeys = bySymbol.keys.sorted()
                        AlertsUiState.Success(sortedKeys.associateWith { k -> bySymbol.getValue(k) })
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState.Loading)

    fun toggleAlert(
        id: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch { alertRepository.setEnabled(id, enabled) }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch {
            alertRepository.deleteAlert(id)
            notificationService.cancelAlert(id)
        }
    }

    fun areNotificationsEnabled(): Boolean = notificationService.areNotificationsEnabled()

    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        notificationService.requestPermission(onResult)
    }
}
