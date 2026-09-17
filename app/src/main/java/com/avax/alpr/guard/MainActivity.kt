package com.avax.alpr.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.avax.alpr.guard.ui.guard.GuardScreen
import com.avax.alpr.guard.ui.guard.GuardViewModel
import com.avax.alpr.guard.ui.theme.AvaxALPRGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AvaxALPRGuardTheme {
                val container =
                    (application as GuardApplication).container

                val factory = remember {
                    GuardViewModel.Factory(
                        vehicleAccessRepository =
                            container.vehicleAccessRepository,
                        vehicleSyncRepository =
                            container.vehicleSyncRepository
                    )
                }

                val guardViewModel: GuardViewModel =
                    viewModel(
                        factory = factory
                    )

                val uiState by
                guardViewModel.uiState.collectAsStateWithLifecycle()

                GuardScreen(
                    uiState = uiState,
                    onPlateChanged =
                        guardViewModel::onPlateChanged,
                    onPlateEditingChanged =
                        guardViewModel::onPlateEditingChanged,
                    onAreaSelected =
                        guardViewModel::onAreaSelected,
                    onVerify =
                        guardViewModel::verifyLocally,
                    onEditPlate =
                        guardViewModel::editPlateFromResult,
                    onContinue =
                        guardViewModel::continueScanning,
                    onSynchronize =
                        guardViewModel::synchronizeVehicles,
                    onAutomaticRecognition =
                        guardViewModel::onAutomaticPlateRecognized,
                    onAutomaticOcrFailure =
                        guardViewModel::onAutomaticOcrFailure,
                    developerViewAvailable =
                        BuildConfig.DEBUG,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}