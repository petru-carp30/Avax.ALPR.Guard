package com.avax.alpr.guard.ui.guard

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.avax.alpr.guard.ai.ocr.AutomaticPlateRecognition
import com.avax.alpr.guard.data.local.AccessLogPersistenceStatus
import com.avax.alpr.guard.data.repository.SyncResult
import com.avax.alpr.guard.data.repository.VehicleAccessRepository
import com.avax.alpr.guard.data.repository.VehicleSyncRepository
import com.avax.alpr.guard.domain.PlateNormalizer
import com.avax.alpr.guard.domain.model.AccessArea
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GuardViewModel(
    private val vehicleAccessRepository: VehicleAccessRepository,
    private val vehicleSyncRepository: VehicleSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardUiState())
    val uiState: StateFlow<GuardUiState> = _uiState.asStateFlow()

    private val automaticVerificationCooldown = AutomaticVerificationCooldown()

    init {
        observeSyncMetadata()
        observeRecentAccessLogs()
    }

    fun onPlateChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            plateInput = value,
            isPlateEditing = true,
            accessDecision = null,
            operatorResult = null,
            localLogMessage = null
        )
    }

    fun onPlateEditingChanged(isEditing: Boolean) {
        _uiState.value = _uiState.value.copy(
            isPlateEditing = isEditing
        )
    }

    fun onAreaSelected(area: AccessArea) {
        val state = _uiState.value

        val currentPlate = state.plateInput.ifBlank {
            state.operatorResult?.normalizedLicensePlate
                ?: state.automaticRecognition.normalizedPlate
                ?: state.automaticRecognition.ocrText
                ?: ""
        }

        _uiState.value = state.copy(
            plateInput = currentPlate,
            isPlateEditing = false,
            selectedArea = area,
            accessDecision = null,
            operatorResult = null,
            automaticRecognition = state.automaticRecognition.copy(
                accessDecision = null,
                message = null
            ),
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
                isPlateEditing = false,
                operatorResult = null,
                localLogMessage = null
            )

            try {
                val verification = vehicleAccessRepository.verify(
                    inputPlate = plate,
                    requestedArea = area
                )

                val logMessage = if (
                    verification.logPersistenceStatus == AccessLogPersistenceStatus.Failed
                ) {
                    "Access decision completed, but the local access event could not be saved."
                } else {
                    null
                }

                _uiState.value = _uiState.value.copy(
                    accessDecision = verification.decision,
                    operatorResult = verification.decision,
                    isVerifying = false,
                    localLogMessage = logMessage
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    accessDecision = null,
                    operatorResult = null,
                    isVerifying = false,
                    syncMessage = "Local verification failed."
                )
            }
        }
    }

    fun editPlateFromResult() {
        val state = _uiState.value
        val fallbackPlate = state.operatorResult?.normalizedLicensePlate.orEmpty()

        _uiState.value = state.copy(
            plateInput = state.plateInput.ifBlank { fallbackPlate },
            isPlateEditing = true,
            operatorResult = null,
            accessDecision = null,
            localLogMessage = null
        )
    }

    fun continueScanning() {
        _uiState.value = _uiState.value.copy(
            plateInput = "",
            isPlateEditing = false,
            accessDecision = null,
            operatorResult = null,
            localLogMessage = null
        )
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
            vehicleAccessRepository.observeRecentAccessLogs().collect { accessLogs ->
                val recentLogs = accessLogs.map { accessLog ->
                    RecentAccessLogUiItem(
                        localLogId = accessLog.localLogId,
                        eventTimestampUtc = accessLog.eventTimestampUtc,
                        licensePlate = accessLog.normalizedLicensePlate ?: accessLog.inputLicensePlate,
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

    fun onAutomaticPlateRecognized(recognition: AutomaticPlateRecognition) {
        viewModelScope.launch {
            val ocrResult = recognition.ocrResult
            val normalizedPlate = PlateNormalizer.normalize(ocrResult.text)

            if (normalizedPlate.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    automaticRecognition = AutomaticRecognitionUiState(
                        ocrText = ocrResult.text,
                        detectorConfidence = recognition.detectorConfidence,
                        ocrConfidence = ocrResult.confidence,
                        ocrLatencyMs = ocrResult.latencyMs,
                        message = "OCR result is not usable for local verification."
                    )
                )
                return@launch
            }

            if (_uiState.value.automaticRecognition.isVerifying) return@launch

            val nowMs = SystemClock.elapsedRealtime()

            if (automaticVerificationCooldown.shouldSuppress(normalizedPlate, nowMs)) {
                val state = _uiState.value

                _uiState.value = state.copy(
                    plateInput = if (state.isPlateEditing) state.plateInput else ocrResult.text,
                    automaticRecognition = state.automaticRecognition.copy(
                        ocrText = ocrResult.text,
                        normalizedPlate = normalizedPlate,
                        detectorConfidence = recognition.detectorConfidence,
                        ocrConfidence = ocrResult.confidence,
                        ocrLatencyMs = ocrResult.latencyMs,
                        message = "Duplicate automatic verification suppressed."
                    )
                )
                return@launch
            }

            val state = _uiState.value
            val area = state.selectedArea

            _uiState.value = state.copy(
                plateInput = if (state.isPlateEditing) state.plateInput else ocrResult.text,
                automaticRecognition = AutomaticRecognitionUiState(
                    ocrText = ocrResult.text,
                    normalizedPlate = normalizedPlate,
                    detectorConfidence = recognition.detectorConfidence,
                    ocrConfidence = ocrResult.confidence,
                    ocrLatencyMs = ocrResult.latencyMs,
                    isVerifying = true
                )
            )

            try {
                val verification = vehicleAccessRepository.verify(
                    inputPlate = ocrResult.text,
                    requestedArea = area
                )

                if (verification.logPersistenceStatus == AccessLogPersistenceStatus.Persisted) {
                    automaticVerificationCooldown.mark(
                        normalizedPlate = normalizedPlate,
                        nowMs = SystemClock.elapsedRealtime()
                    )
                }

                val message = when (verification.logPersistenceStatus) {
                    AccessLogPersistenceStatus.Failed ->
                        "Access result completed, but the local access event could not be saved."

                    else -> null
                }

                _uiState.value = _uiState.value.copy(
                    operatorResult = verification.decision,
                    automaticRecognition = AutomaticRecognitionUiState(
                        ocrText = ocrResult.text,
                        normalizedPlate = normalizedPlate,
                        detectorConfidence = recognition.detectorConfidence,
                        ocrConfidence = ocrResult.confidence,
                        ocrLatencyMs = ocrResult.latencyMs,
                        accessDecision = verification.decision,
                        isVerifying = false,
                        message = message
                    )
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    automaticRecognition = AutomaticRecognitionUiState(
                        ocrText = ocrResult.text,
                        normalizedPlate = normalizedPlate,
                        detectorConfidence = recognition.detectorConfidence,
                        ocrConfidence = ocrResult.confidence,
                        ocrLatencyMs = ocrResult.latencyMs,
                        message = "Automatic local verification failed."
                    )
                )
            }
        }
    }

    fun onAutomaticOcrFailure(message: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                automaticRecognition = _uiState.value.automaticRecognition.copy(
                    accessDecision = null,
                    isVerifying = false,
                    message = message
                )
            )
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