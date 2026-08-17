package com.avax.alpr.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
                val container = (application as GuardApplication).container

                val factory = remember {
                    GuardViewModel.Factory(
                        vehicleAccessRepository = container.vehicleAccessRepository,
                        vehicleSyncRepository = container.vehicleSyncRepository
                    )
                }

                val guardViewModel: GuardViewModel = viewModel(factory = factory)
                val uiState by guardViewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GuardScreen(
                        uiState = uiState,
                        onPlateChanged = guardViewModel::onPlateChanged,
                        onAreaSelected = guardViewModel::onAreaSelected,
                        onVerify = guardViewModel::verifyLocally,
                        onSynchronize = guardViewModel::synchronizeVehicles,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}