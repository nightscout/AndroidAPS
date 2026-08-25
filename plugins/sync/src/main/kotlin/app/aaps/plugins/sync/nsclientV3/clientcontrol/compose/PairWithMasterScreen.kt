package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.plugins.sync.R

private const val PIN_LENGTH = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairWithMasterScreen(
    onNavigateBack: () -> Unit,
    viewModel: PairWithMasterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // While the hello upload is in flight, swallow Back to keep the ViewModel (and therefore
    // viewModelScope) alive until publishHello() completes — otherwise the master never sees
    // the hello and the client is stuck Pending on the master side.
    BackHandler(enabled = state is PairWithMasterViewModel.UiState.Sending) { /* swallow */ }

    // After successful pair, drop back to the previous screen so the user lands wherever they came
    // from. Latched so a recomposition while state is still Success cannot pop twice — the
    // ViewModel's Success state is sticky, but onNavigateBack is not idempotent.
    var navigated by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is PairWithMasterViewModel.UiState.Success && !navigated) {
            navigated = true
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.pair_with_master_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = state) {
                is PairWithMasterViewModel.UiState.AlreadyPaired -> AlreadyPairedContent(
                    masterInstallId = s.pairing.masterInstallId,
                    onUnpair = viewModel::unpair,
                    onCancel = onNavigateBack
                )

                PairWithMasterViewModel.UiState.PinEntry         -> PinEntryContent(onSubmit = viewModel::onPinEntered)

                PairWithMasterViewModel.UiState.Fetching         -> FetchingContent()

                is PairWithMasterViewModel.UiState.Confirming    -> ConfirmPairingDialog(
                    masterInstallId = s.payload.masterInstallId,
                    onConfirm = viewModel::confirmPair,
                    onCancel = viewModel::cancelConfirmation
                )

                PairWithMasterViewModel.UiState.Sending          -> SendingContent()

                is PairWithMasterViewModel.UiState.Error         -> ErrorContent(
                    reason = s.reason,
                    onRetry = viewModel::resumePinEntry,
                    onCancel = onNavigateBack
                )

                PairWithMasterViewModel.UiState.Success          -> Unit // LaunchedEffect handles back-nav
            }
        }
    }
}

@Composable
private fun PinEntryContent(onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val complete = pin.length == PIN_LENGTH
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clearFocusOnTap(focusManager)
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.pair_with_master_pin_entry_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.pair_with_master_pin_entry_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
        OutlinedTextField(
            value = pin,
            // Filter to digits + cap at PIN_LENGTH up-front so the IME shows a clean count and the
            // submit gate stays simple — paste of "1234-5678" still produces 8 digits.
            onValueChange = { raw -> pin = raw.filter(Char::isDigit).take(PIN_LENGTH) },
            label = { Text(stringResource(R.string.pair_with_master_pin_entry_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (complete) onSubmit(pin) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AapsSpacing.large)
        )
        Button(
            onClick = { onSubmit(pin) },
            enabled = complete,
            modifier = Modifier
                .padding(top = AapsSpacing.large)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.pair_with_master_pin_entry_submit))
        }
    }
}

@Composable
private fun FetchingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.pair_with_master_fetching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = AapsSpacing.large)
        )
    }
}

@Composable
private fun SendingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.pair_with_master_sending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = AapsSpacing.large)
        )
    }
}

@Composable
private fun ConfirmPairingDialog(
    masterInstallId: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.pair_with_master_confirm_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pair_with_master_confirm_master, masterInstallId),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.pair_with_master_confirm_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = AapsSpacing.medium)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.pair_with_master_confirm_pair))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(app.aaps.core.ui.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ErrorContent(
    reason: PairWithMasterViewModel.ErrorReason,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val message = when (reason) {
        PairWithMasterViewModel.ErrorReason.WrongPin           -> stringResource(R.string.pair_with_master_error_wrong_pin)
        PairWithMasterViewModel.ErrorReason.AmbiguousPin       -> stringResource(R.string.pair_with_master_error_ambiguous_pin)
        PairWithMasterViewModel.ErrorReason.OfferExpired       -> stringResource(R.string.pair_with_master_error_expired)
        PairWithMasterViewModel.ErrorReason.NetworkUnavailable -> stringResource(R.string.pair_with_master_error_network)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.pair_with_master_error_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .padding(top = AapsSpacing.large)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.pair_with_master_retry))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.cancel))
        }
    }
}

@Composable
private fun AlreadyPairedContent(
    masterInstallId: String,
    onUnpair: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.pair_with_master_already_paired_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.pair_with_master_already_paired_master, masterInstallId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
        Button(
            onClick = onUnpair,
            modifier = Modifier
                .padding(top = AapsSpacing.large)
                .fillMaxWidth()
        ) {
            Text(stringResource(R.string.pair_with_master_unpair))
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.close))
        }
    }
}
