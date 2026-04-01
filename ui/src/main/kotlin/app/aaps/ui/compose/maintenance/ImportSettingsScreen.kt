package app.aaps.ui.compose.maintenance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportDecryptResult
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ImportSummaryItem
import app.aaps.core.ui.compose.SnackbarMessage
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.AapsSnackbarHost
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.R as CoreUiR

@Composable
fun ImportSettingsScreen(
    viewModel: ImportViewModel,
    prefFileList: FileListProvider,
    onClose: () -> Unit
) {
    val step by viewModel.importStep.collectAsStateWithLifecycle()

    when (val currentStep = step) {
        is ImportStep.Idle,
        is ImportStep.Loading        -> {
            ImportLoadingContent()
        }

        is ImportStep.FilePicker     -> {
            ImportFilePickerContent(
                state = currentStep,
                prefFileList = prefFileList,
                onFileClick = { viewModel.selectFile(it) },
                onLoadMore = { viewModel.loadMoreCloud() },
                onClose = {
                    viewModel.cancelImport()
                    onClose()
                }
            )
        }

        is ImportStep.Review         -> {
            ImportReviewContent(
                state = currentStep,
                onMasterPasswordChanged = { viewModel.onMasterPasswordChanged(it) },
                onDecryptionPasswordChanged = { viewModel.onDecryptionPasswordChanged(it) },
                onDecrypt = { viewModel.decrypt() },
                onImport = { viewModel.confirmImport() },
                onBack = { viewModel.goBackToFilePicker() }
            )
        }

        is ImportStep.RestartConfirm -> {
            OkDialog(
                title = stringResource(CoreUiR.string.import_restart_title),
                message = stringResource(CoreUiR.string.import_restart_message),
                onDismiss = { viewModel.onRestartConfirmed() }
            )
        }

        is ImportStep.Error          -> {
            OkDialog(
                title = stringResource(CoreUiR.string.error),
                message = currentStep.message,
                onDismiss = {
                    viewModel.dismissError()
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun ImportLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImportFilePickerContent(
    state: ImportStep.FilePicker,
    prefFileList: FileListProvider,
    onFileClick: (ImportFileItem) -> Unit,
    onLoadMore: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiR.string.import_setting)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.files.isEmpty() && !state.isLoadingCloud) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(CoreUiR.string.import_no_files),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(state.files, key = { "${it.source}_${it.prefsFile.name}" }) { item ->
                    ImportFileCard(
                        item = item,
                        showSourceBadge = state.source == ImportSource.BOTH,
                        prefFileList = prefFileList,
                        onClick = { onFileClick(item) }
                    )
                }

                // Cloud loading progress indicator
                if (state.isLoadingCloud) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(24.dp)
                            )
                            if (state.cloudLoadingProgress != null) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = state.cloudLoadingProgress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Load more button for cloud pagination
                if (state.hasMoreCloud && !state.isLoadingCloud) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator()
                            } else {
                                OutlinedButton(onClick = onLoadMore) {
                                    Text(stringResource(CoreUiR.string.import_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFileCard(
    item: ImportFileItem,
    showSourceBadge: Boolean,
    prefFileList: FileListProvider,
    onClick: () -> Unit
) {
    val metadata = item.prefsFile.metadata
    val infoColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = 16.dp
    val iconStartPadding = 30.dp

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {

            // Row 1: format icon + file name + optional source badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Use format icon if available, otherwise first metadata icon
                val formatEntry = metadata.entries.find { it.key.key == "format" }
                if (formatEntry != null) {
                    Icon(
                        painter = painterResource(id = formatEntry.key.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 5.dp, end = 6.dp)
                            .height(18.dp)
                            .width(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = item.prefsFile.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                if (showSourceBadge) {
                    SourceBadge(source = item.source)
                }
            }

            // Row 2: device name icon + device name
            metadata.entries.find { it.key.key == "device_name" }?.let { (metaKey, metaEntry) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = metaKey.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = iconStartPadding, end = 8.dp)
                            .height(iconSize)
                            .width(iconSize),
                        tint = infoColor
                    )
                    Text(
                        text = metaEntry.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = infoColor
                    )
                }
            }

            // Row 3: date icon + "exported ago" | version (right) | flavour (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                metadata.entries.find { it.key.key == "created_at" }?.let { (metaKey, metaEntry) ->
                    Icon(
                        painter = painterResource(id = metaKey.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = iconStartPadding, end = 8.dp)
                            .height(iconSize)
                            .width(iconSize),
                        tint = infoColor
                    )
                    Text(
                        text = prefFileList.formatExportedAgo(metaEntry.value),
                        style = MaterialTheme.typography.bodySmall,
                        color = infoColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                metadata.entries.find { it.key.key == "aaps_version" }?.let { (_, metaEntry) ->
                    Text(
                        text = metaEntry.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            metaEntry.status.isOk      -> AapsTheme.generalColors.statusNormal
                            metaEntry.status.isWarning -> AapsTheme.generalColors.statusWarning
                            else                       -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }

                metadata.entries.find { it.key.key == "aaps_flavour" }?.let { (_, metaEntry) ->
                    Text(
                        text = metaEntry.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            metaEntry.status.isOk      -> AapsTheme.generalColors.statusNormal
                            metaEntry.status.isWarning -> AapsTheme.generalColors.statusWarning
                            else                       -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: ImportSource) {
    val (icon, label) = when (source) {
        ImportSource.LOCAL -> Icons.Default.Smartphone to stringResource(CoreUiR.string.import_source_local)
        ImportSource.CLOUD -> Icons.Default.Cloud to stringResource(CoreUiR.string.import_source_cloud)
        ImportSource.BOTH  -> Icons.Default.Cloud to stringResource(CoreUiR.string.import_source_cloud)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportReviewContent(
    state: ImportStep.Review,
    onMasterPasswordChanged: (String) -> Unit,
    onDecryptionPasswordChanged: (String) -> Unit,
    onDecrypt: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val successResult = state.decryptResult as? ImportDecryptResult.Success
    val canImport = successResult != null && successResult.importPossible
    var snackbarMessage by remember { mutableStateOf<SnackbarMessage?>(null) }

    Scaffold(
        snackbarHost = {
            AapsSnackbarHost(
                message = snackbarMessage,
                onDismiss = { snackbarMessage = null }
            )
        },
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(CoreUiR.string.import_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            if (canImport) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val importOk = successResult.importOk
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing,
                        colors = if (importOk) ButtonDefaults.buttonColors()
                        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            if (importOk) stringResource(CoreUiR.string.import_btn)
                            else stringResource(CoreUiR.string.import_anyway_btn)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .clearFocusOnTap(focusManager)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // File details card
            FileDetailsCard(file = state.file, source = state.fileSource, onSnackbarMessage = { snackbarMessage = it })

            // Master password
            OutlinedTextField(
                value = state.masterPassword,
                onValueChange = onMasterPasswordChanged,
                label = { Text(stringResource(CoreUiR.string.import_master_password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = state.masterPasswordError,
                supportingText = if (state.masterPasswordError) {
                    { Text(stringResource(CoreUiR.string.import_wrong_password)) }
                } else null,
                singleLine = true,
                enabled = !state.isProcessing && state.decryptResult == null
            )

            // Decrypt & Review button
            if (state.decryptResult == null && !state.needsDecryptionPassword) {
                Button(
                    onClick = onDecrypt,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isProcessing && state.masterPassword.isNotBlank()
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(20.dp)
                                .width(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(CoreUiR.string.import_decrypt_review))
                }
            }

            // Decryption password (progressive disclosure)
            AnimatedVisibility(
                visible = state.needsDecryptionPassword,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(CoreUiR.string.import_different_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = AapsTheme.generalColors.statusWarning
                    )

                    OutlinedTextField(
                        value = state.decryptionPassword,
                        onValueChange = onDecryptionPasswordChanged,
                        label = { Text(stringResource(CoreUiR.string.import_decryption_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = state.decryptResult is ImportDecryptResult.WrongPassword,
                        supportingText = if (state.decryptResult is ImportDecryptResult.WrongPassword) {
                            { Text(stringResource(CoreUiR.string.import_wrong_password)) }
                        } else null,
                        singleLine = true,
                        enabled = !state.isProcessing
                    )

                    Button(
                        onClick = onDecrypt,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isProcessing && state.decryptionPassword.isNotBlank()
                    ) {
                        if (state.isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(20.dp)
                                    .width(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(CoreUiR.string.import_decrypt))
                    }
                }
            }

            // Decrypt error
            if (state.decryptResult is ImportDecryptResult.Error) {
                Text(
                    text = state.decryptResult.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Summary section (shown after successful decrypt)
            AnimatedVisibility(
                visible = successResult != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                successResult?.let { result ->
                    val problemEntries = result.prefs.metadata.entries.filter {
                        it.value.status.isWarning || it.value.status.isError
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()

                        // Status row with icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            val (icon, iconTint, messageText) = when {
                                result.importOk       -> Triple(
                                    Icons.Default.CheckCircle,
                                    AapsTheme.generalColors.statusNormal,
                                    stringResource(CoreUiR.string.import_decrypt_ok)
                                )

                                result.importPossible -> Triple(
                                    Icons.Default.Warning,
                                    AapsTheme.generalColors.statusWarning,
                                    stringResource(CoreUiR.string.import_decrypt_warn)
                                )

                                else                  -> Triple(
                                    Icons.Default.Error,
                                    MaterialTheme.colorScheme.error,
                                    stringResource(CoreUiR.string.import_decrypt_error)
                                )
                            }

                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = messageText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = iconTint
                            )
                        }

                        // Only show entries with warnings or errors
                        if (problemEntries.isNotEmpty()) {
                            problemEntries.forEach { (metaKey, metaEntry) ->
                                ImportSummaryItem(metaKey = metaKey, metaEntry = metaEntry, onSnackbarMessage = { snackbarMessage = it })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileDetailsCard(
    file: PrefsFile,
    source: ImportSource,
    onSnackbarMessage: (SnackbarMessage) -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                SourceBadge(source = source)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Show all metadata
            file.metadata.entries.forEach { (metaKey, metaEntry) ->
                ImportSummaryItem(metaKey = metaKey, metaEntry = metaEntry, onSnackbarMessage = onSnackbarMessage)
            }
        }
    }
}
