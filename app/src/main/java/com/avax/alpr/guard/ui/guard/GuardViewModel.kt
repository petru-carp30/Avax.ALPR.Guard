package com.avax.alpr.guard.ui.guard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.avax.alpr.guard.data.repository.SyncResult
import com.avax.alpr.guard.data.repository.VehicleAccessRepository
import com.avax.alpr.guard.data.repository.VehicleSyncRepository
import com.avax.alpr.guard.domain.model.AccessArea
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.avax.alpr.guard.data.local.AccessLogPersistenceStatus

class GuardViewModel(
    private val vehicleAccessRepository: VehicleAccessRepository,
    private val vehicleSyncRepository: VehicleSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardUiState())
    val uiState: StateFlow<GuardUiState> = _uiState.asStateFlow()

    init {
        observeSyncMetadata()
        observeRecentAccessLogs()
    }

    fun onPlateChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            plateInput = value,
            accessDecision = null,
            localLogMessage = null
        )
    }

    fun onAreaSelected(area: AccessArea) {
        _uiState.value = _uiState.value.copy(
            selectedArea = area,
            accessDecision = null,
            localLogMessage = null
        )
    }

    fun verifyLocally() {
        if (_uiState.value.isVerifying) return

        val plate = _uiState.value.plateInput
        val area = _uiState.value.selectedArea

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVerifying = true,
                localLogMessage = null
            )

            try {
                val verification =
                    vehicleAccessRepository.verify(
                        inputPlate = plate,
                        requestedArea = area
                    )

                val logMessage =
                    if (
                        verification.logPersistenceStatus ==
                        AccessLogPersistenceStatus.Failed
                    ) {
                        "Access decision completed, but the local access event could not be saved."
                    } else {
                        null
                    }

                _uiState.value = _uiState.value.copy(
                    accessDecision = verification.decision,
                    isVerifying = false,
                    localLogMessage = logMessage
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    accessDecision = null,
                    isVerifying = false,
                    syncMessage = "Local verification failed."
                )
            }
        }
    }

    fun synchronizeVehicles() {
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncMessage = "Synchronizing vehicle cache..."
            )

            val result = vehicleSyncRepository.synchronize()

            val message = when (result) {
                is SyncResult.Success -> "Synchronization completed: ${result.vehicleCount} vehicles."
                SyncResult.NoNetwork -> "No network connection. Existing local cache remains available."
                SyncResult.BackendUnavailable -> "Backend unavailable. Existing local cache remains available."
                SyncResult.Conflict -> "Synchronization rejected because the backend snapshot is not deterministic."
                is SyncResult.HttpError -> "Synchronization failed with HTTP ${result.statusCode}."
                is SyncResult.UnsupportedContractVersion -> "Unsupported synchronization contract version."
                SyncResult.MalformedSnapshot -> "Backend returned an invalid vehicle snapshot."
                SyncResult.DatabaseFailure -> "Local database update failed. Previous cache was preserved."
            }

            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncMessage = message
            )
        }
    }

    private fun observeSyncMetadata() {
        viewModelScope.launch {
            vehicleAccessRepository.observeSyncMetadata().collect { metadata ->
                _uiState.value = _uiState.value.copy(
                    hasLocalSnapshot = metadata != null,
                    cachedVehicleCount = metadata?.vehicleCount ?: 0,
                    snapshotGeneratedAtUtc = metadata?.snapshotGeneratedAtUtc,
                    importedAtUtc = metadata?.importedAtUtc
                )
            }
        }
    }

    private fun observeRecentAccessLogs() {
        viewModelScope.launch {
            vehicleAccessRepository
                .observeRecentAccessLogs()
                .collect { accessLogs ->

                    val recentLogs = accessLogs.map { accessLog ->
                        RecentAccessLogUiItem(
                            localLogId = accessLog.localLogId,
                            eventTimestampUtc = accessLog.eventTimestampUtc,
                            licensePlate =
                                accessLog.normalizedLicensePlate
                                    ?: accessLog.inputLicensePlate,
                            accessArea = accessLog.accessArea,
                            decisionStatus = accessLog.decisionStatus,
                            syncState = accessLog.syncState
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        recentAccessLogs = recentLogs
                    )
                }
        }
    }

    class Factory(
        private val vehicleAccessRepository: VehicleAccessRepository,
        private val vehicleSyncRepository: VehicleSyncRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GuardViewModel::class.java)) {
                return GuardViewModel(
                    vehicleAccessRepository = vehicleAccessRepository,
                    vehicleSyncRepository = vehicleSyncRepository
                ) as T
            }

            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}