package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.plugins.sync.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedClientsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthorizedClientsViewModel = hiltViewModel()
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val pairingOffer by viewModel.pairingOffer.collectAsStateWithLifecycle()
    val featureEnabled by viewModel.clientControlEnabled.collectAsStateWithLifecycle()

    // Re-prune expired pending entries every second while any are pending,
    // so the countdown ticks down and expired ones drop without a manual refresh.
    val anyPending = clients.any { it.state == ClientState.Pending }
    LaunchedEffect(anyPending) {
        while (anyPending) {
            delay(1_000L)
            viewModel.pruneExpired()
        }
    }

    when (val state = dialogState) {
        AuthorizedClientsViewModel.DialogState.EnterName        -> EnterNameDialog(
            onConfirm = { viewModel.confirmAdd(it) },
            onDismiss = viewModel::dismissDialog
        )

        is AuthorizedClientsViewModel.DialogState.ConfirmDelete -> ConfirmDeleteDialog(
            clientName = state.client.name,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog
        )

        null                                                    -> Unit
    }

    pairingOffer?.let { offer ->
        PairingPinDialog(
            offer = offer,
            onRetry = viewModel::retryPublish,
            onDismiss = viewModel::dismissPairing
        )
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.authorized_clients_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (featureEnabled) {
                FloatingActionButton(onClick = viewModel::requestAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.authorized_clients_add))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // The stop/allow-communication switch lives here (moved off the NSCv3 prefs screen). Off keeps the
            // paired clients listed but stops the master accepting anything (and blocks new pairing).
            ClientControlSwitchRow(enabled = featureEnabled, onToggle = viewModel::setClientControlEnabled)
            if (clients.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AapsSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    items(clients, key = { it.clientId }) { client ->
                        // Compute the time label here (viewModel in scope) and pass it down as a plain
                        // string — the card/badge stay state-down, events-up with no viewModel handle.
                        val timeLabel = when (client.state) {
                            ClientState.Active  -> viewModel.lastSeenLabel(client)
                            ClientState.Pending -> viewModel.pendingExpiresLabel(client)
                        }
                        AuthorizedClientCard(
                            client = client,
                            timeLabel = timeLabel,
                            onDelete = { viewModel.requestDelete(client) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorizedClientCard(
    client: AuthorizedClient,
    timeLabel: String,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = when (client.state) {
                    ClientState.Active  -> MaterialTheme.colorScheme.primary
                    ClientState.Pending -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StateBadgeAndTimeRow(client = client, timeLabel = timeLabel)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(app.aaps.core.ui.R.string.delete))
            }
        }
    }
}

@Composable
private fun StateBadgeAndTimeRow(
    client: AuthorizedClient,
    timeLabel: String
) {
    val stateLabel = when (client.state) {
        ClientState.Active  -> stringResource(R.string.authorized_clients_state_active)
        ClientState.Pending -> stringResource(R.string.authorized_clients_state_pending)
    }
    val stateColor = when (client.state) {
        ClientState.Active  -> MaterialTheme.colorScheme.primary
        ClientState.Pending -> MaterialTheme.colorScheme.tertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Text(text = stateLabel, style = MaterialTheme.typography.labelSmall, color = stateColor)
        Text(text = "•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ClientControlSwitchRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.authorized_clients_comm_switch_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.authorized_clients_comm_switch_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.authorized_clients_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.authorized_clients_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
    }
}

@Composable
private fun EnterNameDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.authorized_clients_name_dialog_title)) },
        text = {
            Column(modifier = Modifier.clearFocusOnTap(focusManager)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.authorized_clients_name_placeholder)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(app.aaps.core.ui.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    clientName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.authorized_clients_delete_dialog_title)) },
        text = { Text(stringResource(R.string.authorized_clients_delete_dialog_message, clientName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(app.aaps.core.ui.R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(app.aaps.core.ui.R.string.cancel)) }
        }
    )
}

@Composable
private fun PairingPinDialog(
    offer: AuthorizedClientsViewModel.PendingPairingOffer,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    // Sensitive content — block screenshots/recents previews while the PIN is on screen.
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Keyed on expiresAt so the initial msLeft snapshot tracks the (rarely-changing) offer; if a
    // future code path emits a new offer with a different expiresAt, the remember resets.
    var msLeft by remember(offer.expiresAt) { mutableLongStateOf(offer.expiresAt - System.currentTimeMillis()) }

    LaunchedEffect(offer.expiresAt) {
        while (true) {
            msLeft = offer.expiresAt - System.currentTimeMillis()
            if (msLeft <= 0L) {
                onDismiss()
                break
            }
            delay(1_000L)
        }
    }

    val formattedPin = remember(offer.pin) { offer.pin.chunked(4).joinToString("-") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(AapsSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                Text(
                    text = stringResource(R.string.authorized_clients_pin_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.authorized_clients_pin_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedPin,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = AapsSpacing.large)
                )
                PublishStatusBanner(status = offer.publishStatus, onRetry = onRetry)
                Text(
                    text = stringResource(
                        R.string.authorized_clients_pin_expires_in,
                        (msLeft / 1000L).coerceAtLeast(0L).toString()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.authorized_clients_pin_done))
                }
            }
        }
    }
}

@Composable
private fun PublishStatusBanner(
    status: AuthorizedClientsViewModel.PublishStatus,
    onRetry: () -> Unit
) {
    when (status) {
        AuthorizedClientsViewModel.PublishStatus.Loading   -> Text(
            text = stringResource(R.string.authorized_clients_pin_publishing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AuthorizedClientsViewModel.PublishStatus.Published -> Text(
            text = stringResource(R.string.authorized_clients_pin_published),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        AuthorizedClientsViewModel.PublishStatus.Failed    -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Text(
                text = stringResource(R.string.authorized_clients_pin_publish_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.authorized_clients_pin_retry))
            }
        }
    }
}
