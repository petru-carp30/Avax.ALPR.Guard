package com.avax.alpr.guard.ui.guard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.ui.camera.CameraPreviewCard


@Composable
fun GuardScreen(
    uiState: GuardUiState,
    onPlateChanged: (String) -> Unit,
    onAreaSelected: (AccessArea) -> Unit,
    onVerify: () -> Unit,
    onSynchronize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AVAX ALPR Guard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            CameraPreviewCard()

            CacheCard(
                uiState = uiState,
                onSynchronize = onSynchronize
            )

            Text(
                text = "Manual Access Verification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = uiState.plateInput,
                onValueChange = onPlateChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("License plate") },
                placeholder = { Text("CJ 12 ABC") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )

            AccessAreaSelector(
                selectedArea = uiState.selectedArea,
                onAreaSelected = onAreaSelected
            )

            Button(
                onClick = onVerify,
                enabled = !uiState.isVerifying,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator()
                } else {
                    Text("Verify locally")
                }
            }

            uiState.accessDecision?.let { decision ->
                AccessResultCard(decision)
            }

            uiState.localLogMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RecentAccessLogsCard(
                accessLogs = uiState.recentAccessLogs
            )
        }
    }
}

@Composable
private fun CacheCard(
    uiState: GuardUiState,
    onSynchronize: () -> Unit
) {
    val context = LocalContext.current

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSynchronize()
        } else {
            Toast.makeText(
                context,
                "Local network access is required to synchronize vehicles.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun synchronizeWithLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < 37) {
            onSynchronize()
            return
        }

        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            onSynchronize()
        } else {
            localNetworkPermissionLauncher.launch(
                Manifest.permission.ACCESS_LOCAL_NETWORK
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Local Vehicle Cache",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (uiState.hasLocalSnapshot) {
                    "Local snapshot available"
                } else {
                    "No local snapshot available"
                }
            )

            if (uiState.hasLocalSnapshot) {
                Text("Cached vehicles: ${uiState.cachedVehicleCount}")

                uiState.importedAtUtc?.let {
                    Text("Last imported: $it")
                }
            }

            Button(
                onClick = {
                    synchronizeWithLocalNetworkPermission()
                },
                enabled = !uiState.isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator()
                } else {
                    Text("Synchronize vehicles")
                }
            }

            uiState.syncMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RecentAccessLogsCard(
    accessLogs: List<RecentAccessLogUiItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent Local Access Events",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (accessLogs.isEmpty()) {
                Text(
                    text = "No local access events yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                accessLogs.forEachIndexed { index, accessLog ->

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = accessLog.licensePlate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Text(
                            "Time (UTC): ${accessLog.eventTimestampUtc}"
                        )

                        Text(
                            "Area: ${accessLog.accessArea.displayName()}"
                        )

                        Text(
                            text =
                                "Result: ${accessLog.decisionStatus.displayName()}",
                            color =
                                accessLog.decisionStatus.statusColor(),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Sync: ${accessLog.syncState.displayName()}"
                        )
                    }

                    if (index < accessLogs.lastIndex) {
                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessAreaSelector(
    selectedArea: AccessArea,
    onAreaSelected: (AccessArea) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Access area",
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccessArea.entries.forEach { area ->
                FilterChip(
                    selected = selectedArea == area,
                    onClick = { onAreaSelected(area) },
                    label = { Text(area.displayName()) }
                )
            }
        }
    }
}

@Composable
private fun AccessResultCard(decision: AccessDecision) {
    val statusColor = decision.status.statusColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.14f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = decision.status.displayName(),
                color = statusColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = decision.normalizedLicensePlate,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text("Area: ${decision.requestedArea.displayName()}")

            decision.vehicle?.let { vehicle ->
                Spacer(modifier = Modifier.height(4.dp))

                Text("Plate: ${vehicle.displayLicensePlate}")

                val vehicleName = listOfNotNull(
                    vehicle.brand,
                    vehicle.model
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")

                if (vehicleName.isNotBlank()) {
                    Text("Vehicle: $vehicleName")
                }

                vehicle.color?.takeIf { it.isNotBlank() }?.let {
                    Text("Color: $it")
                }

                vehicle.accessNotes?.takeIf { it.isNotBlank() }?.let {
                    Text("Notes: $it")
                }
            }
        }
    }
}

private fun AccessArea.displayName(): String {
    return when (this) {
        AccessArea.ParkingLot -> "Parking Lot"
        AccessArea.Site -> "Site"
        AccessArea.Camp -> "Camp"
    }
}

private fun AccessDecisionStatus.displayName(): String {
    return when (this) {
        AccessDecisionStatus.Granted -> "GRANTED"
        AccessDecisionStatus.Denied -> "DENIED"
        AccessDecisionStatus.NotYetValid -> "NOT YET VALID"
        AccessDecisionStatus.Expired -> "EXPIRED"
        AccessDecisionStatus.VehicleNotFound -> "UNKNOWN VEHICLE"
        AccessDecisionStatus.InvalidInput -> "INVALID PLATE"
        AccessDecisionStatus.DataUnavailable -> "LOCAL DATA UNAVAILABLE"
    }
}

private fun AccessDecisionStatus.statusColor(): Color {
    return when (this) {
        AccessDecisionStatus.Granted -> Color(0xFF2E7D32)
        AccessDecisionStatus.Denied -> Color(0xFFC62828)

        AccessDecisionStatus.NotYetValid,
        AccessDecisionStatus.Expired,
        AccessDecisionStatus.VehicleNotFound,
        AccessDecisionStatus.InvalidInput,
        AccessDecisionStatus.DataUnavailable -> Color(0xFFF9A825)
    }
}

private fun AccessLogSyncState.displayName(): String {
    return when (this) {
        AccessLogSyncState.Pending -> "PENDING"
        AccessLogSyncState.Synced -> "SYNCED"
        AccessLogSyncState.Conflict -> "CONFLICT"
        AccessLogSyncState.Rejected -> "REJECTED"
    }
}