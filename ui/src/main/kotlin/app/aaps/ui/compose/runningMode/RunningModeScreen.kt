package app.aaps.ui.compose.runningMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.ui.R
import app.aaps.ui.compose.overview.chips.toColor
import app.aaps.ui.compose.overview.chips.toIcon
import kotlinx.coroutines.delay

/**
 * Running Mode management screen - replacement for LoopDialog.
 * Compact card-based design with single-row button layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningModeScreen(
    viewModel: RunningModeManagementViewModel,
    showOkCancel: Boolean,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var pendingAction by remember { mutableStateOf<PendingRunningModeAction?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            viewModel.loadState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = state.currentMode.toIcon(),
                            contentDescription = null,
                            tint = state.currentMode.toColor(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(state.currentModeText.ifEmpty { stringResource(app.aaps.core.ui.R.string.running_mode) })
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Reasons card (if any)
            if (!state.reasons.isNullOrEmpty()) {
                SectionCard(title = stringResource(app.aaps.core.ui.R.string.constraints)) {
                    Text(
                        text = state.reasons!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Loop Control Section
            val showLoopSection = state.allowedNextModes.any {
                it in listOf(RM.Mode.DISABLED_LOOP, RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS)
            }
            if (showLoopSection) {
                LoopControlSection(
                    allowedModes = state.allowedNextModes,
                    onAction = { action ->
                        if (showOkCancel) pendingAction = action
                        else {
                            executeAction(viewModel, action); onNavigateBack()
                        }
                    }
                )
            }

            // Suspend Section
            val showSuspendSection = state.allowedNextModes.contains(RM.Mode.SUSPENDED_BY_USER) ||
                (state.allowedNextModes.contains(RM.Mode.RESUME) && state.currentMode == RM.Mode.SUSPENDED_BY_USER)
            if (showSuspendSection) {
                SuspendSection(
                    currentMode = state.currentMode,
                    allowedModes = state.allowedNextModes,
                    onAction = { action ->
                        if (showOkCancel) pendingAction = action
                        else {
                            executeAction(viewModel, action); onNavigateBack()
                        }
                    }
                )
            }

            // Pump Disconnect Section (only in APS mode)
            val showPumpSection = state.isApsMode && (
                state.allowedNextModes.contains(RM.Mode.DISCONNECTED_PUMP) ||
                    (state.allowedNextModes.contains(RM.Mode.RESUME) && state.currentMode == RM.Mode.DISCONNECTED_PUMP)
                )
            if (showPumpSection) {
                PumpDisconnectSection(
                    currentMode = state.currentMode,
                    allowedModes = state.allowedNextModes,
                    tempDurationStep15mAllowed = state.tempDurationStep15mAllowed,
                    tempDurationStep30mAllowed = state.tempDurationStep30mAllowed,
                    onAction = { action ->
                        if (showOkCancel) pendingAction = action
                        else {
                            executeAction(viewModel, action); onNavigateBack()
                        }
                    }
                )
            }
        }
    }

    pendingAction?.let { action ->
        OkCancelDialog(
            title = stringResource(app.aaps.core.ui.R.string.confirmation),
            message = action.confirmationMessage,
            onConfirm = {
                executeAction(viewModel, action)
                pendingAction = null
                onNavigateBack()
            },
            onDismiss = { pendingAction = null }
        )
    }
}

private fun executeAction(viewModel: RunningModeManagementViewModel, action: PendingRunningModeAction) {
    viewModel.executeAction(action.targetMode, action.action, action.durationMinutes)
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LoopControlSection(
    allowedModes: List<RM.Mode>,
    onAction: (PendingRunningModeAction) -> Unit
) {
    val closedLoopText = stringResource(app.aaps.core.ui.R.string.closedloop)
    val lgsText = stringResource(app.aaps.core.ui.R.string.lowglucosesuspend)
    val openLoopText = stringResource(app.aaps.core.ui.R.string.openloop)
    val disableLoopText = stringResource(app.aaps.core.ui.R.string.disableloop)

    SectionCard(title = stringResource(app.aaps.core.ui.R.string.running_mode)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (allowedModes.contains(RM.Mode.CLOSED_LOOP)) {
                CompactButton(
                    closedLoopText, RM.Mode.CLOSED_LOOP,
                    { onAction(PendingRunningModeAction(RM.Mode.CLOSED_LOOP, Action.CLOSED_LOOP_MODE, 0, closedLoopText)) },
                    Modifier.weight(1f)
                )
            }
            if (allowedModes.contains(RM.Mode.CLOSED_LOOP_LGS)) {
                CompactButton(
                    lgsText, RM.Mode.CLOSED_LOOP_LGS,
                    { onAction(PendingRunningModeAction(RM.Mode.CLOSED_LOOP_LGS, Action.LGS_LOOP_MODE, 0, lgsText)) },
                    Modifier.weight(1f)
                )
            }
            if (allowedModes.contains(RM.Mode.OPEN_LOOP)) {
                CompactButton(
                    openLoopText, RM.Mode.OPEN_LOOP,
                    { onAction(PendingRunningModeAction(RM.Mode.OPEN_LOOP, Action.OPEN_LOOP_MODE, 0, openLoopText)) },
                    Modifier.weight(1f)
                )
            }
            if (allowedModes.contains(RM.Mode.DISABLED_LOOP)) {
                CompactButton(
                    disableLoopText, RM.Mode.DISABLED_LOOP,
                    { onAction(PendingRunningModeAction(RM.Mode.DISABLED_LOOP, Action.LOOP_DISABLED, Int.MAX_VALUE, disableLoopText)) },
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SuspendSection(
    currentMode: RM.Mode,
    allowedModes: List<RM.Mode>,
    onAction: (PendingRunningModeAction) -> Unit
) {
    val isSuspended = currentMode == RM.Mode.SUSPENDED_BY_USER
    val title = if (isSuspended) stringResource(app.aaps.core.ui.R.string.resumeloop) else stringResource(app.aaps.core.ui.R.string.suspendloop)

    val resumeText = stringResource(R.string.resume)
    val duration1hText = stringResource(R.string.duration1h)
    val duration2hText = stringResource(R.string.duration2h)
    val duration3hText = stringResource(R.string.duration3h)
    val duration10hText = stringResource(R.string.duration10h)
    val suspend1hText = stringResource(R.string.suspendloopfor1h)
    val suspend2hText = stringResource(R.string.suspendloopfor2h)
    val suspend3hText = stringResource(R.string.suspendloopfor3h)
    val suspend10hText = stringResource(R.string.suspendloopfor10h)

    SectionCard(title = title) {
        if (isSuspended && allowedModes.contains(RM.Mode.RESUME)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CompactButton(
                    resumeText, RM.Mode.RESUME,
                    { onAction(PendingRunningModeAction(RM.Mode.RESUME, Action.RESUME, 0, resumeText)) })
            }
        } else if (allowedModes.contains(RM.Mode.SUSPENDED_BY_USER)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactButton(
                    duration1hText, RM.Mode.SUSPENDED_BY_USER,
                    { onAction(PendingRunningModeAction(RM.Mode.SUSPENDED_BY_USER, Action.SUSPEND, 60, suspend1hText)) },
                    Modifier.weight(1f)
                )
                CompactButton(
                    duration2hText, RM.Mode.SUSPENDED_BY_USER,
                    { onAction(PendingRunningModeAction(RM.Mode.SUSPENDED_BY_USER, Action.SUSPEND, 120, suspend2hText)) },
                    Modifier.weight(1f)
                )
                CompactButton(
                    duration3hText, RM.Mode.SUSPENDED_BY_USER,
                    { onAction(PendingRunningModeAction(RM.Mode.SUSPENDED_BY_USER, Action.SUSPEND, 180, suspend3hText)) },
                    Modifier.weight(1f)
                )
                CompactButton(
                    duration10hText, RM.Mode.SUSPENDED_BY_USER,
                    { onAction(PendingRunningModeAction(RM.Mode.SUSPENDED_BY_USER, Action.SUSPEND, 600, suspend10hText)) },
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PumpDisconnectSection(
    currentMode: RM.Mode,
    allowedModes: List<RM.Mode>,
    tempDurationStep15mAllowed: Boolean,
    tempDurationStep30mAllowed: Boolean,
    onAction: (PendingRunningModeAction) -> Unit
) {
    val isDisconnected = currentMode == RM.Mode.DISCONNECTED_PUMP
    val title = if (isDisconnected) stringResource(R.string.reconnect) else stringResource(R.string.disconnectpump)

    val reconnectText = stringResource(R.string.reconnect)
    val duration15mText = stringResource(R.string.duration15m)
    val duration30mText = stringResource(R.string.duration30m)
    val duration1hText = stringResource(R.string.duration1h)
    val duration2hText = stringResource(R.string.duration2h)
    val duration3hText = stringResource(R.string.duration3h)
    val disconnect15mText = stringResource(R.string.disconnectpumpfor15m)
    val disconnect30mText = stringResource(R.string.disconnectpumpfor30m)
    val disconnect1hText = stringResource(R.string.disconnectpumpfor1h)
    val disconnect2hText = stringResource(R.string.disconnectpumpfor2h)
    val disconnect3hText = stringResource(R.string.disconnectpumpfor3h)

    SectionCard(title = title) {
        if (isDisconnected && allowedModes.contains(RM.Mode.RESUME)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CompactButton(
                    reconnectText, RM.Mode.RESUME,
                    { onAction(PendingRunningModeAction(RM.Mode.RESUME, Action.RECONNECT, 0, reconnectText)) })
            }
        } else if (allowedModes.contains(RM.Mode.DISCONNECTED_PUMP)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tempDurationStep15mAllowed) {
                    CompactButton(
                        duration15mText, RM.Mode.DISCONNECTED_PUMP,
                        { onAction(PendingRunningModeAction(RM.Mode.DISCONNECTED_PUMP, Action.DISCONNECT, 15, disconnect15mText)) },
                        Modifier.weight(1f)
                    )
                }
                if (tempDurationStep30mAllowed) {
                    CompactButton(
                        duration30mText, RM.Mode.DISCONNECTED_PUMP,
                        { onAction(PendingRunningModeAction(RM.Mode.DISCONNECTED_PUMP, Action.DISCONNECT, 30, disconnect30mText)) },
                        Modifier.weight(1f)
                    )
                }
                CompactButton(
                    duration1hText, RM.Mode.DISCONNECTED_PUMP,
                    { onAction(PendingRunningModeAction(RM.Mode.DISCONNECTED_PUMP, Action.DISCONNECT, 60, disconnect1hText)) },
                    Modifier.weight(1f)
                )
                CompactButton(
                    duration2hText, RM.Mode.DISCONNECTED_PUMP,
                    { onAction(PendingRunningModeAction(RM.Mode.DISCONNECTED_PUMP, Action.DISCONNECT, 120, disconnect2hText)) },
                    Modifier.weight(1f)
                )
                CompactButton(
                    duration3hText, RM.Mode.DISCONNECTED_PUMP,
                    { onAction(PendingRunningModeAction(RM.Mode.DISCONNECTED_PUMP, Action.DISCONNECT, 180, disconnect3hText)) },
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactButton(
    text: String,
    mode: RM.Mode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = mode.toColor()

    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = mode.toIcon(),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class PendingRunningModeAction(
    val targetMode: RM.Mode,
    val action: Action,
    val durationMinutes: Int = 0,
    val confirmationMessage: String
)
