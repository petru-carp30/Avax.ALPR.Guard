package com.avax.alpr.guard.ui.guard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.avax.alpr.guard.ai.ocr.AutomaticPlateRecognition
import com.avax.alpr.guard.data.local.AccessLogSyncState
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.ui.camera.CameraPreviewCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GuardScreen(
    uiState: GuardUiState,
    onPlateChanged: (String) -> Unit,
    onPlateEditingChanged: (Boolean) -> Unit,
    onAreaSelected: (AccessArea) -> Unit,
    onVerify: () -> Unit,
    onEditPlate: () -> Unit,
    onContinue: () -> Unit,
    onSynchronize: () -> Unit,
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    developerViewAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    var showDeveloperView by rememberSaveable {
        mutableStateOf(false)
    }

    if (
        developerViewAvailable &&
        showDeveloperView
    ) {
        DeveloperGuardScreen(
            uiState = uiState,
            onPlateChanged = onPlateChanged,
            onPlateEditingChanged = onPlateEditingChanged,
            onAreaSelected = onAreaSelected,
            onVerify = onVerify,
            onSynchronize = onSynchronize,
            onAutomaticRecognition = onAutomaticRecognition,
            onAutomaticOcrFailure = onAutomaticOcrFailure,
            onBackToOperator = {
                showDeveloperView = false
            },
            modifier = modifier
        )
    } else {
        OperatorGuardScreen(
            uiState = uiState,
            onPlateChanged = onPlateChanged,
            onPlateEditingChanged = onPlateEditingChanged,
            onAreaSelected = onAreaSelected,
            onVerify = onVerify,
            onEditPlate = onEditPlate,
            onContinue = onContinue,
            onAutomaticRecognition = onAutomaticRecognition,
            onAutomaticOcrFailure = onAutomaticOcrFailure,
            developerViewAvailable = developerViewAvailable,
            onOpenDeveloper = {
                showDeveloperView = true
            },
            modifier = modifier
        )
    }
}

@Composable
private fun OperatorGuardScreen(
    uiState: GuardUiState,
    onPlateChanged: (String) -> Unit,
    onPlateEditingChanged: (Boolean) -> Unit,
    onAreaSelected: (AccessArea) -> Unit,
    onVerify: () -> Unit,
    onEditPlate: () -> Unit,
    onContinue: () -> Unit,
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    developerViewAvailable: Boolean,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember {
        FocusRequester()
    }

    LaunchedEffect(
        uiState.isPlateEditing,
        uiState.operatorResult
    ) {
        if (
            uiState.isPlateEditing &&
            uiState.operatorResult == null
        ) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        CameraPreviewCard(
            onAutomaticRecognition = onAutomaticRecognition,
            onAutomaticOcrFailure = onAutomaticOcrFailure,
            modifier = Modifier.fillMaxSize(),
            showHeader = false,
            showDiagnostics = false,
            contentPadding = 0.dp,
            fullscreenPreview = true,
            verticalZoomControls = uiState.operatorResult == null,
            showContainer = false
        )

        CompactAreaSelector(
            selectedArea = uiState.selectedArea,
            onAreaSelected = onAreaSelected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(
                    start = 14.dp,
                    top = 10.dp
                )
        )

        if (developerViewAvailable) {
            TextButton(
                onClick = onOpenDeveloper,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(
                        top = 10.dp,
                        end = 12.dp
                    )
            ) {
                Text("DEV")
            }
        }

        if (uiState.operatorResult == null) {
            OperatorPlateControl(
                uiState = uiState,
                focusRequester = focusRequester,
                onPlateChanged = onPlateChanged,
                onPlateEditingChanged = onPlateEditingChanged,
                onVerify = onVerify,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 12.dp
                    )
            )
        }

        uiState.operatorResult?.let { decision ->
            OperatorResultOverlay(
                decision = decision,
                onEditPlate = onEditPlate,
                onContinue = onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp
                    )
            )
        }
    }
}

@Composable
private fun OperatorPlateControl(
    uiState: GuardUiState,
    focusRequester: FocusRequester,
    onPlateChanged: (String) -> Unit,
    onPlateEditingChanged: (Boolean) -> Unit,
    onVerify: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = 0.92f
            )
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uiState.automaticRecognition.operatorHint()?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.plateInput,
                    onValueChange = onPlateChanged,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            onPlateEditingChanged(
                                it.isFocused
                            )
                        },
                    placeholder = {
                        Text("License plate")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onVerify()
                        }
                    )
                )

                Button(
                    onClick = onVerify,
                    enabled = !uiState.isVerifying,
                    modifier = Modifier.height(56.dp)
                ) {
                    if (uiState.isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Search")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactAreaSelector(
    selectedArea: AccessArea,
    onAreaSelected: (AccessArea) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box(modifier = modifier) {
        Button(
            onClick = {
                expanded = true
            }
        ) {
            Text(
                "${selectedArea.displayName().uppercase(Locale.getDefault())} ▼"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            AccessArea.entries.forEach { area ->
                DropdownMenuItem(
                    text = {
                        Text(area.displayName())
                    },
                    onClick = {
                        expanded = false
                        onAreaSelected(area)
                    }
                )
            }
        }
    }
}

@Composable
private fun OperatorResultOverlay(
    decision: AccessDecision,
    onEditPlate: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = decision.status.statusColor()
    val statusTextColor = decision.status.statusContentColor()

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 12.dp
        )
    ) {
        Column {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = statusColor
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp,
                        vertical = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = decision.status.displayName(),
                        color = statusTextColor,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = decision.normalizedLicensePlate,
                        color = statusTextColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                decision.vehicle?.let { vehicle ->
                    val vehicleName = listOfNotNull(
                        vehicle.brand,
                        vehicle.model
                    )
                        .filter {
                            it.isNotBlank()
                        }
                        .joinToString(" ")

                    if (vehicleName.isNotBlank()) {
                        Text(
                            text = vehicleName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    vehicle.color
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            Text("Color: $it")
                        }

                    vehicle.accessNotes
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            Text(
                                text = "Notes: $it",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                }

                if (
                    decision.status ==
                    AccessDecisionStatus.VehicleNotFound
                ) {
                    Text(
                        "No local vehicle record was found for this plate."
                    )
                }

                Text(
                    text = "Area: ${decision.requestedArea.displayName()}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onEditPlate,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("EDIT PLATE")
                    }

                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CONTINUE")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperGuardScreen(
    uiState: GuardUiState,
    onPlateChanged: (String) -> Unit,
    onPlateEditingChanged: (Boolean) -> Unit,
    onAreaSelected: (AccessArea) -> Unit,
    onVerify: () -> Unit,
    onSynchronize: () -> Unit,
    onAutomaticRecognition: (AutomaticPlateRecognition) -> Unit,
    onAutomaticOcrFailure: (String) -> Unit,
    onBackToOperator: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AVAX ALPR Guard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = onBackToOperator
                ) {
                    Text("OPERATOR")
                }
            }

            CameraPreviewCard(
                onAutomaticRecognition = onAutomaticRecognition,
                onAutomaticOcrFailure = onAutomaticOcrFailure
            )

            AutomaticRecognitionCard(
                uiState.automaticRecognition
            )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        onPlateEditingChanged(
                            it.isFocused
                        )
                    },
                label = {
                    Text("License plate")
                },
                placeholder = {
                    Text("CJ 12 ABC")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )

            DeveloperAccessAreaSelector(
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
                DeveloperAccessResultCard(
                    decision
                )
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

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                Text(
                    "Cached vehicles: ${uiState.cachedVehicleCount}"
                )

                uiState.importedAtUtc?.let {
                    Text(
                        "Last imported: $it"
                    )
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = accessLog.licensePlate,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Text(
                            text = "Time: ${formatUtcTimestampForLocalDisplay(accessLog.eventTimestampUtc)}"
                        )

                        Text(
                            text = "Area: ${accessLog.accessArea.displayName()}"
                        )

                        Text(
                            text = "Result: ${accessLog.decisionStatus.displayName()}",
                            color = accessLog.decisionStatus.statusColor(),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Sync: ${accessLog.syncState.displayName()}"
                        )
                    }

                    if (
                        index <
                        accessLogs.lastIndex
                    ) {
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
private fun DeveloperAccessAreaSelector(
    selectedArea: AccessArea,
    onAreaSelected: (AccessArea) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccessArea.entries.forEach { area ->
            OutlinedButton(
                onClick = {
                    onAreaSelected(area)
                }
            ) {
                Text(
                    area.displayName()
                )
            }
        }
    }
}

@Composable
private fun DeveloperAccessResultCard(
    decision: AccessDecision
) {
    val statusColor =
        decision.status.statusColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(
                alpha = 0.14f
            )
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

            Text(
                "Area: ${decision.requestedArea.displayName()}"
            )

            decision.vehicle?.let { vehicle ->
                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    "Plate: ${vehicle.displayLicensePlate}"
                )

                val vehicleName = listOfNotNull(
                    vehicle.brand,
                    vehicle.model
                )
                    .filter {
                        it.isNotBlank()
                    }
                    .joinToString(" ")

                if (vehicleName.isNotBlank()) {
                    Text(
                        "Vehicle: $vehicleName"
                    )
                }

                vehicle.color
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        Text(
                            "Color: $it"
                        )
                    }

                vehicle.accessNotes
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        Text(
                            "Notes: $it"
                        )
                    }
            }
        }
    }
}

@Composable
private fun AutomaticRecognitionCard(
    state: AutomaticRecognitionUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Automatic Recognition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (
                state.ocrText == null &&
                state.accessDecision == null &&
                !state.isVerifying
            ) {
                Text(
                    "Waiting for a license plate..."
                )
            }

            state.ocrText?.let {
                Text(
                    "OCR: $it"
                )
            }

            state.normalizedPlate?.let {
                Text(
                    text = "Normalized: $it",
                    fontWeight = FontWeight.SemiBold
                )
            }

            state.detectorConfidence?.let {
                Text(
                    "Detector confidence: ${String.format(Locale.US, "%.3f", it)}"
                )
            }

            state.ocrConfidence?.let {
                Text(
                    "OCR confidence: ${String.format(Locale.US, "%.3f", it)}"
                )
            }

            state.ocrLatencyMs?.let {
                Text(
                    "OCR latency: ${String.format(Locale.US, "%.1f", it)} ms"
                )
            }

            if (state.isVerifying) {
                CircularProgressIndicator()
                Text(
                    "Checking local access..."
                )
            }

            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.accessDecision?.let {
                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                DeveloperAccessResultCard(
                    it
                )
            }
        }
    }
}

private fun AutomaticRecognitionUiState.operatorHint(): String? {
    return message?.takeIf {
        it.contains(
            "Plate too far",
            ignoreCase = true
        )
    }
}

private fun formatUtcTimestampForLocalDisplay(
    timestampUtc: String
): String {
    return runCatching {
        val instant =
            Instant.parse(timestampUtc)

        LOCAL_DATE_TIME_FORMATTER
            .withZone(
                ZoneId.systemDefault()
            )
            .format(
                instant
            )
    }.getOrElse {
        timestampUtc
    }
}

private val LOCAL_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        "dd.MM.yyyy HH:mm:ss",
        Locale.getDefault()
    )

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
        AccessDecisionStatus.Granted ->
            Color(0xFF2E7D32)

        AccessDecisionStatus.Denied ->
            Color(0xFFC62828)

        AccessDecisionStatus.NotYetValid,
        AccessDecisionStatus.Expired,
        AccessDecisionStatus.VehicleNotFound,
        AccessDecisionStatus.InvalidInput,
        AccessDecisionStatus.DataUnavailable ->
            Color(0xFFF9A825)
    }
}

private fun AccessDecisionStatus.statusContentColor(): Color {
    return when (this) {
        AccessDecisionStatus.Granted,
        AccessDecisionStatus.Denied ->
            Color.White

        AccessDecisionStatus.NotYetValid,
        AccessDecisionStatus.Expired,
        AccessDecisionStatus.VehicleNotFound,
        AccessDecisionStatus.InvalidInput,
        AccessDecisionStatus.DataUnavailable ->
            Color.Black
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