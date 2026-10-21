package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientSetupCode
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings

/** Reads the master's set-up QR code with the camera. Reached from the setup wizard and from Pair with master. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanMasterQrScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanMasterQrViewModel = metroViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(SyncStrings.client_setup_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
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
                // One branch for both, so the camera stays on behind the dialog instead of starting again. Its codes
                // are ignored until the answer.
                ScanMasterQrViewModel.UiState.Scanning,
                is ScanMasterQrViewModel.UiState.Confirming -> ScanningContent(onScanned = viewModel::onScanned)

                ScanMasterQrViewModel.UiState.Applying      -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is ScanMasterQrViewModel.UiState.Done       -> MessageContent(
                    title = stringResource(SyncStrings.client_setup_done_title),
                    message = stringResource(if (s.paired) SyncStrings.client_setup_done_paired else SyncStrings.client_setup_done_ns_only),
                    isError = false,
                    primaryLabel = stringResource(CoreUiStrings.ok),
                    onPrimary = onNavigateBack,
                    onSecondary = null
                )

                is ScanMasterQrViewModel.UiState.Error      -> MessageContent(
                    title = stringResource(SyncStrings.pair_with_master_error_title),
                    message = errorText(s.reason),
                    isError = true,
                    primaryLabel = stringResource(SyncStrings.pair_with_master_retry),
                    onPrimary = viewModel::scanAgain,
                    onSecondary = onNavigateBack
                )
            }
            (state as? ScanMasterQrViewModel.UiState.Confirming)?.let { s ->
                ConfirmCodeDialog(code = s.code, onConfirm = viewModel::confirm, onCancel = viewModel::scanAgain)
            }
        }
    }
}

@Composable
private fun ScanningContent(onScanned: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Text(
            text = stringResource(SyncStrings.client_setup_scan_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        QrScanner(
            onScanned = onScanned,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = CAMERA_MAX_SIZE)
                .aspectRatio(1f)
                .clipToBounds()
        )
    }
}

/** Square and not too large: the code fills a good part of it from a hand's length away. */
private val CAMERA_MAX_SIZE = 480.dp

@Composable
private fun ConfirmCodeDialog(
    code: ClientSetupCode,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(SyncStrings.client_setup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Text(stringResource(SyncStrings.client_setup_confirm_nightscout, code.url), style = MaterialTheme.typography.bodyMedium)
                code.pairing?.let {
                    Text(stringResource(SyncStrings.client_setup_confirm_pairs, it.masterInstallId), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(SyncStrings.pair_with_master_confirm_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = stringResource(SyncStrings.client_setup_confirm_replaces),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(SyncStrings.client_setup_confirm_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(CoreUiStrings.cancel)) }
        }
    )
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    isError: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .padding(top = AapsSpacing.large)
                .fillMaxWidth()
        ) {
            Text(primaryLabel)
        }
        onSecondary?.let {
            TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreUiStrings.cancel)) }
        }
    }
}

@Composable
private fun errorText(reason: ScanMasterQrViewModel.ErrorReason): String = when (reason) {
    ScanMasterQrViewModel.ErrorReason.NotACode          -> stringResource(SyncStrings.client_setup_scan_not_a_code)
    ScanMasterQrViewModel.ErrorReason.OtherVersion      -> stringResource(SyncStrings.client_setup_scan_other_version)
    ScanMasterQrViewModel.ErrorReason.Expired           -> stringResource(SyncStrings.client_setup_scan_expired)
    ScanMasterQrViewModel.ErrorReason.AlreadyPaired     -> stringResource(SyncStrings.client_setup_scan_already_paired)
    ScanMasterQrViewModel.ErrorReason.SecretStoreFailed -> stringResource(SyncStrings.pair_with_master_error_secret_store)
}
