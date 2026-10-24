package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nsclientV3.clientcontrol.clientRole
import kotlinx.coroutines.delay
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizedClientsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthorizedClientsViewModel = metroViewModel()
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val pairingOffer by viewModel.pairingOffer.collectAsStateWithLifecycle()
    val featureEnabled by viewModel.clientControlEnabled.collectAsStateWithLifecycle()
    val simpleMode by viewModel.simpleMode.collectAsStateWithLifecycle()
    val tokens by viewModel.tokens.collectAsStateWithLifecycle()
    val unpairedTokens by viewModel.unpairedTokens.collectAsStateWithLifecycle()
    val tokenKinds by viewModel.tokenKinds.collectAsStateWithLifecycle()
    val qrDialog by viewModel.qrDialog.collectAsStateWithLifecycle()

    // The newest client is first in the list, but a LazyColumn stays on the card that was first in view, so a
    // client added while the list is open would be above the screen. Scroll to a card when it is added.
    val listState = rememberLazyListState()
    val clientIds = remember(clients) { clients.map { it.clientId } }
    var shownIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(clientIds) {
        val before = shownIds
        shownIds = clientIds
        if (before == null) return@LaunchedEffect
        val added = clientIds.indexOfFirst { it !in before }
        if (added >= 0) listState.animateScrollToItem(added)
    }

    // Re-prune expired pending entries every second while any are pending,
    // so the countdown ticks down and expired ones drop without a manual refresh.
    // The tick is read where the countdown text is made: without it the card kept the seconds of its first drawing,
    // because nothing else changes while the client is pending.
    val anyPending = clients.any { it.state == ClientState.Pending }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(anyPending) {
        while (anyPending) {
            delay(1_000L)
            tick++
            viewModel.pruneExpired()
        }
    }

    when (val state = dialogState) {
        AuthorizedClientsViewModel.DialogState.EnterName             -> EnterNameDialog(
            // Known before the user picks a QR code option: better said here than after the name is typed.
            qrProblem = (tokens as? AuthorizedClientsViewModel.TokensState.Unavailable)?.problem
                ?.takeIf { it == AuthorizedClientsViewModel.TokenProblem.NotAllowed || it == AuthorizedClientsViewModel.TokenProblem.NotConnected },
            onConfirm = viewModel::confirmAdd,
            onDismiss = viewModel::dismissDialog
        )

        is AuthorizedClientsViewModel.DialogState.ConfirmDelete      -> ConfirmDeleteDialog(
            clientName = state.client.name,
            deletesToken = tokenKinds[state.client.clientId] == AuthorizedClientsViewModel.TokenKind.ClientToken,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog
        )

        is AuthorizedClientsViewModel.DialogState.ConfirmDeleteToken -> ConfirmDeleteTokenDialog(
            clientName = viewModel.displayName(state.subject),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialog
        )

        null                                                         -> Unit
    }

    pairingOffer?.let { offer ->
        PairingPinDialog(
            offer = offer,
            onRetry = viewModel::retryPublish,
            onDismiss = viewModel::dismissPairing
        )
    }

    qrDialog?.let { dialog -> ClientQrDialog(dialog = dialog, onDismiss = viewModel::dismissQr) }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(SyncStrings.authorized_clients_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                },
                // One explanation of the four roles for the whole list, instead of an (i) on every card.
                actions = { ClientRolesInfoButton() }
            )
        },
        floatingActionButton = {
            if (featureEnabled) {
                FloatingActionButton(onClick = viewModel::requestAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(SyncStrings.authorized_clients_add))
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
            // Simple mode forces it on, so it is not offered there.
            if (!simpleMode) ClientControlSwitchRow(enabled = featureEnabled, onToggle = viewModel::setClientControlEnabled)
            // On the master, client control on is hardened mode on. Said here in every mode, because in simple
            // mode the NSClient settings that would explain it are hidden.
            if (featureEnabled) HardenedModeInfoRow(modifier = Modifier.padding(horizontal = AapsSpacing.large))
            if (clients.isEmpty() && unpairedTokens.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = AapsSpacing.medium, top = AapsSpacing.medium, end = AapsSpacing.medium, bottom = AapsSpacing.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    items(clients, key = { it.clientId }) { client ->
                        // Compute the time label here (viewModel in scope) and pass it down as a plain
                        // string — the card/badge stay state-down, events-up with no viewModel handle.
                        val timeLabel = when (client.state) {
                            ClientState.Active  -> viewModel.lastSeenLabel(client)
                            ClientState.Pending -> tick.let { viewModel.pendingExpiresLabel(client) }
                        }
                        AuthorizedClientCard(
                            client = client,
                            timeLabel = timeLabel,
                            tokenKind = tokenKinds[client.clientId] ?: AuthorizedClientsViewModel.TokenKind.Unknown,
                            onRoleChange = { viewModel.setRole(client, it) },
                            onDelete = { viewModel.requestDelete(client) }
                        )
                    }
                    // Below the clients, so the indexes the scroll above uses stay the clients' own.
                    if (unpairedTokens.isNotEmpty()) {
                        item(key = "unpaired_tokens_header") {
                            Text(
                                text = stringResource(SyncStrings.authorized_clients_tokens_header),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = AapsSpacing.medium)
                            )
                        }
                        items(unpairedTokens, key = { "token_" + (it.id ?: it.name) }) { subject ->
                            UnpairedTokenCard(
                                clientName = viewModel.displayName(subject),
                                showQr = qrCodeAvailable,
                                onShowQr = { viewModel.showTokenQr(subject) },
                                onDelete = { viewModel.requestDeleteToken(subject) }
                            )
                        }
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
    tokenKind: AuthorizedClientsViewModel.TokenKind,
    onRoleChange: (ClientRole) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AapsSpacing.large, top = AapsSpacing.large, end = AapsSpacing.large),
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
                // Which Nightscout token the phone writes with: its own client token, or one with more rights.
                when (tokenKind) {
                    AuthorizedClientsViewModel.TokenKind.ClientToken -> Text(
                        text = stringResource(SyncStrings.authorized_clients_uses_client_token),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AuthorizedClientsViewModel.TokenKind.OtherToken  -> Text(
                        text = stringResource(SyncStrings.authorized_clients_uses_other_token, client.nsSubject.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    AuthorizedClientsViewModel.TokenKind.NotItsOwn   -> Text(
                        text = stringResource(SyncStrings.authorized_clients_uses_not_own_token, client.nsSubject.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    AuthorizedClientsViewModel.TokenKind.Unknown     -> Unit
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(CoreUiStrings.delete))
            }
        }
        // One tap changes the role: the screen is behind settings protection, and the client follows on the next roster.
        ClientRoleSelector(
            selected = client.clientRole,
            onSelect = onRoleChange,
            modifier = Modifier.padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
        )
    }
}

@Composable
private fun StateBadgeAndTimeRow(
    client: AuthorizedClient,
    timeLabel: String
) {
    val stateLabel = when (client.state) {
        ClientState.Active  -> stringResource(SyncStrings.authorized_clients_state_active)
        ClientState.Pending -> stringResource(SyncStrings.authorized_clients_state_pending)
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
    // This switch allows or blocks remote control from paired phones; a screen reader read it as
    // "Switch, on" with no name. The whole row is the switch now.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(SyncStrings.authorized_clients_comm_switch_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(SyncStrings.authorized_clients_comm_switch_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
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
            text = stringResource(SyncStrings.authorized_clients_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(SyncStrings.authorized_clients_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
    }
}

/** A phone with its own client token and no pairing: it reads Nightscout. */
@Composable
private fun UnpairedTokenCard(
    clientName: String,
    showQr: Boolean,
    onShowQr: () -> Unit,
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
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = clientName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = stringResource(SyncStrings.authorized_clients_token_unpaired),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showQr) IconButton(onClick = onShowQr) {
                Icon(Icons.Default.QrCode2, contentDescription = stringResource(SyncStrings.authorized_clients_token_show_qr))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(CoreUiStrings.delete))
            }
        }
    }
}

/**
 * Name, way of adding and role of a new client. The QR code options are offered only where the platform can draw a
 * code; [qrProblem] tells why they cannot work now (no admin token, no Nightscout), so they are shown but cannot be
 * chosen.
 */
@Composable
private fun EnterNameDialog(
    qrProblem: AuthorizedClientsViewModel.TokenProblem?,
    onConfirm: (String, ClientRole?, AuthorizedClientsViewModel.AddMethod) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val qrPossible = qrCodeAvailable && qrProblem == null
    var method by remember {
        mutableStateOf(if (qrPossible) AuthorizedClientsViewModel.AddMethod.QrWithPairing else AuthorizedClientsViewModel.AddMethod.Pin)
    }
    // Nothing preselected: any default is wrong for someone (an own second phone wants Admin, a school phone Meals).
    var role by remember { mutableStateOf<ClientRole?>(null) }
    val needsRole = method != AuthorizedClientsViewModel.AddMethod.QrNightscoutOnly
    val focusManager = LocalFocusManager.current
    val canConfirm = name.isNotBlank() && (role != null || !needsRole)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(SyncStrings.authorized_clients_name_dialog_title)) },
        text = {
            // A long dialog with three ways and the roles: it scrolls on a small screen or with a large font.
            Column(modifier = Modifier
                .clearFocusOnTap(focusManager)
                .verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    singleLine = true,
                    placeholder = { Text(stringResource(SyncStrings.authorized_clients_name_placeholder)) }
                )
                if (qrCodeAvailable) {
                    Text(
                        text = stringResource(SyncStrings.authorized_clients_method_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = AapsSpacing.large, bottom = AapsSpacing.small)
                    )
                    AddMethodOption(
                        title = stringResource(SyncStrings.authorized_clients_method_qr_pair),
                        summary = stringResource(SyncStrings.authorized_clients_method_qr_pair_summary),
                        selected = method == AuthorizedClientsViewModel.AddMethod.QrWithPairing,
                        enabled = qrPossible,
                        onSelect = { method = AuthorizedClientsViewModel.AddMethod.QrWithPairing }
                    )
                    AddMethodOption(
                        title = stringResource(SyncStrings.authorized_clients_method_qr_ns),
                        summary = stringResource(SyncStrings.authorized_clients_method_qr_ns_summary),
                        selected = method == AuthorizedClientsViewModel.AddMethod.QrNightscoutOnly,
                        enabled = qrPossible,
                        onSelect = { method = AuthorizedClientsViewModel.AddMethod.QrNightscoutOnly }
                    )
                    AddMethodOption(
                        title = stringResource(SyncStrings.authorized_clients_method_pin),
                        summary = stringResource(SyncStrings.authorized_clients_method_pin_summary),
                        selected = method == AuthorizedClientsViewModel.AddMethod.Pin,
                        enabled = true,
                        onSelect = { method = AuthorizedClientsViewModel.AddMethod.Pin }
                    )
                    qrProblem?.let {
                        Text(
                            text = tokenProblemText(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = AapsSpacing.small)
                        )
                    }
                }
                if (needsRole) {
                    Row(modifier = Modifier.padding(top = AapsSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(CoreUiStrings.client_role_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        ClientRolesInfoButton()
                    }
                    ClientRoleSelector(selected = role, onSelect = { role = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(name, if (needsRole) role else null, method) },
                enabled = canConfirm
            ) {
                Text(stringResource(CoreUiStrings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) }
        }
    )
}

@Composable
private fun AddMethodOption(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = AapsSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = AapsSpacing.medium)) {
            val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = color)
            Text(text = summary, style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else color)
        }
    }
}

/** The Material 3 alpha of disabled content. */
private const val DISABLED_ALPHA = 0.38f

@Composable
private fun tokenProblemText(problem: AuthorizedClientsViewModel.TokenProblem): String = when (problem) {
    AuthorizedClientsViewModel.TokenProblem.NotConnected -> stringResource(SyncStrings.authorized_clients_token_problem_not_connected)
    AuthorizedClientsViewModel.TokenProblem.NotAllowed   -> stringResource(SyncStrings.authorized_clients_token_problem_not_allowed)
    AuthorizedClientsViewModel.TokenProblem.NameTaken    -> stringResource(SyncStrings.authorized_clients_token_problem_name_taken)
    AuthorizedClientsViewModel.TokenProblem.NameInUse    -> stringResource(SyncStrings.authorized_clients_token_problem_name_in_use)
    AuthorizedClientsViewModel.TokenProblem.Failed       -> stringResource(SyncStrings.authorized_clients_token_problem_failed)
}

@Composable
private fun ConfirmDeleteDialog(
    clientName: String,
    deletesToken: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(SyncStrings.authorized_clients_delete_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(SyncStrings.authorized_clients_delete_dialog_message, clientName))
                if (deletesToken) Text(stringResource(SyncStrings.authorized_clients_delete_dialog_token))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(CoreUiStrings.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) }
        }
    )
}

@Composable
private fun ConfirmDeleteTokenDialog(
    clientName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(SyncStrings.authorized_clients_token_delete_title)) },
        text = { Text(stringResource(SyncStrings.authorized_clients_token_delete_message, clientName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(CoreUiStrings.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) }
        }
    )
}

/** Making the token, why it could not be made, or the QR code itself. */
@Composable
private fun ClientQrDialog(
    dialog: AuthorizedClientsViewModel.QrDialog,
    onDismiss: () -> Unit
) {
    when (dialog) {
        AuthorizedClientsViewModel.QrDialog.Working    -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(SyncStrings.authorized_clients_qr_working))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
        )

        is AuthorizedClientsViewModel.QrDialog.Problem -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(SyncStrings.authorized_clients_token_problem_title)) },
            text = { Text(tokenProblemText(dialog.problem)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.ok)) } }
        )

        is AuthorizedClientsViewModel.QrDialog.Shown   -> ClientQrCodeDialog(dialog = dialog, onDismiss = onDismiss)
    }
}

@Composable
private fun ClientQrCodeDialog(
    dialog: AuthorizedClientsViewModel.QrDialog.Shown,
    onDismiss: () -> Unit
) {
    // The code is a working Nightscout token, and with the pairing the right to send commands: no screenshots.
    @Suppress("UNUSED_VARIABLE")
    val screenshotsBlocked = blockScreenshotsWhileVisible()
    val modules = remember(dialog.text) { qrModules(dialog.text) }

    var msLeft by remember(dialog.expiresAt) { mutableLongStateOf(dialog.expiresAt?.let { it - Clock.System.now().toEpochMilliseconds() } ?: 0L) }
    if (dialog.expiresAt != null) LaunchedEffect(dialog.expiresAt) {
        while (true) {
            msLeft = dialog.expiresAt - Clock.System.now().toEpochMilliseconds()
            if (msLeft <= 0L) {
                onDismiss()
                break
            }
            delay(1_000L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AapsSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                Text(
                    text = stringResource(SyncStrings.authorized_clients_qr_title, dialog.clientName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(if (dialog.pairs) SyncStrings.authorized_clients_qr_pair_instructions else SyncStrings.authorized_clients_qr_ns_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                modules?.let {
                    QrCodeImage(
                        modules = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = QR_MAX_SIZE)
                    )
                }
                Text(
                    text = stringResource(SyncStrings.authorized_clients_qr_keep_private),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (dialog.expiresAt != null) Text(
                    text = stringResource(SyncStrings.authorized_clients_pin_expires_in, (msLeft / 1000L).coerceAtLeast(0L).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(SyncStrings.authorized_clients_pin_done))
                }
            }
        }
    }
}

/** Large enough to scan from a hand's length, small enough to fit a phone in landscape with the text around it. */
private val QR_MAX_SIZE = 360.dp

@Composable
private fun PairingPinDialog(
    offer: AuthorizedClientsViewModel.PendingPairingOffer,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    // Sensitive content — block screenshots/recents previews while the PIN is on screen.
    // Returns false where the platform cannot do it - Apple has no FLAG_SECURE. The PIN is still
    // shown, but the caller can tell the difference between "protected" and "not protected".
    @Suppress("UNUSED_VARIABLE")
    val screenshotsBlocked = blockScreenshotsWhileVisible()

    // Keyed on expiresAt so the initial msLeft snapshot tracks the (rarely-changing) offer; if a
    // future code path emits a new offer with a different expiresAt, the remember resets.
    var msLeft by remember(offer.expiresAt) { mutableLongStateOf(offer.expiresAt - Clock.System.now().toEpochMilliseconds()) }

    LaunchedEffect(offer.expiresAt) {
        while (true) {
            msLeft = offer.expiresAt - Clock.System.now().toEpochMilliseconds()
            if (msLeft <= 0L) {
                onDismiss()
                break
            }
            delay(1_000L)
        }
    }

    // A space, not a dash: a dash read as part of the PIN. The client keeps only the digits anyway.
    val formattedPin = remember(offer.pin) { offer.pin.chunked(4).joinToString(" ") }

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
                    text = stringResource(SyncStrings.authorized_clients_pin_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(SyncStrings.authorized_clients_pin_instructions),
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
                        SyncStrings.authorized_clients_pin_expires_in,
                        (msLeft / 1000L).coerceAtLeast(0L).toString()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(SyncStrings.authorized_clients_pin_done))
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
            text = stringResource(SyncStrings.authorized_clients_pin_publishing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AuthorizedClientsViewModel.PublishStatus.Published -> Text(
            text = stringResource(SyncStrings.authorized_clients_pin_published),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        AuthorizedClientsViewModel.PublishStatus.Failed    -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            Text(
                text = stringResource(SyncStrings.authorized_clients_pin_publish_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(SyncStrings.authorized_clients_pin_retry))
            }
        }
    }
}
