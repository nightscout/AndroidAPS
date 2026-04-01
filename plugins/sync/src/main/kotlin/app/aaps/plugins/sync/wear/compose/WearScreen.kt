package app.aaps.plugins.sync.wear.compose

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.sync.R

@Composable
internal fun WearScreen(
    viewModel: WearViewModel,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onNavigateBack: () -> Unit,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle toast events
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            ToastUtils.okToast(context, message)
        }
    }

    // Back handler for sub-screens
    BackHandler(enabled = uiState.showInfos) { viewModel.hideCwfInfos() }
    BackHandler(enabled = uiState.showImportList) { viewModel.hideImportList() }

    // Only title needs pre-resolving (plain String used in LaunchedEffect suspend block)
    val wearTitle = stringResource(app.aaps.core.ui.R.string.wear)
    val importTitle = stringResource(R.string.wear_import_custom_watchface_title)

    // Determine current sub-screen
    val subScreen = when {
        uiState.showImportList -> SubScreen.IMPORT_LIST
        uiState.showInfos     -> SubScreen.INFOS
        else                   -> SubScreen.MAIN
    }

    // Toolbar config
    LaunchedEffect(subScreen, uiState.cwfInfosState?.title) {
        setToolbarConfig(
            ToolbarConfig(
                title = when (subScreen) {
                    SubScreen.IMPORT_LIST -> importTitle
                    SubScreen.INFOS       -> uiState.cwfInfosState?.title ?: ""
                    SubScreen.MAIN        -> wearTitle
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (subScreen) {
                            SubScreen.IMPORT_LIST -> viewModel.hideImportList()
                            SubScreen.INFOS       -> viewModel.hideCwfInfos()
                            SubScreen.MAIN        -> onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (subScreen == SubScreen.MAIN && onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                }
            )
        )
    }

    val moreWatchfacesUrl = stringResource(R.string.wear_link_to_more_cwf_doc)

    AnimatedContent(
        targetState = subScreen,
        label = "wear_screen"
    ) { screen ->
        when (screen) {
            SubScreen.IMPORT_LIST -> {
                CwfImportContent(
                    items = uiState.importItems,
                    onItemClick = { item -> viewModel.selectWatchface(item.cwfFile) },
                    modifier = modifier
                )
            }

            SubScreen.INFOS       -> {
                val infosState = uiState.cwfInfosState
                if (infosState != null) {
                    CwfInfosContent(
                        state = infosState,
                        modifier = modifier
                    )
                }
            }

            SubScreen.MAIN        -> {
                WearMainContent(
                    uiState = uiState,
                    onResendData = { viewModel.resendData() },
                    onOpenSettings = { viewModel.openSettingsOnWear() },
                    onLoadWatchface = { viewModel.loadWatchfaceFiles() },
                    onInfosWatchface = { viewModel.showCwfInfos() },
                    onExportTemplate = { viewModel.exportCustomWatchface() },
                    onMoreWatchfaces = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, moreWatchfacesUrl.toUri()))
                    },
                    modifier = modifier
                )
            }
        }
    }
}

private enum class SubScreen { MAIN, INFOS, IMPORT_LIST }

@Composable
private fun WearMainContent(
    uiState: WearUiState,
    onResendData: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadWatchface: () -> Unit,
    onInfosWatchface: () -> Unit,
    onExportTemplate: () -> Unit,
    onMoreWatchfaces: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        // Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AapsSpacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
            ) {
                Text(
                    text = uiState.connectedDevice,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                ButtonRow(
                    button1 = ButtonDef(Icons.Default.Refresh, stringResource(R.string.resend_all_data), onResendData),
                    button2 = ButtonDef(Icons.Default.Settings, stringResource(R.string.open_settings_on_wear), onOpenSettings)
                )
            }
        }

        // Custom Watchface Card (visible only when connected)
        if (uiState.isDeviceConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AapsSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    Text(
                        text = stringResource(R.string.wear_custom_watchface, uiState.watchfaceName),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = AapsSpacing.small)
                    )

                    // Row 1: Load + Info
                    ButtonRow(
                        button1 = ButtonDef(Icons.Default.Upload, stringResource(R.string.wear_load_watchface), onLoadWatchface),
                        button2 = if (uiState.hasCustomWatchface)
                            ButtonDef(Icons.Default.Info, stringResource(R.string.wear_infos_watchface), onInfosWatchface)
                        else null
                    )

                    // Row 2: More Watchfaces + Export
                    ButtonRow(
                        button1 = ButtonDef(Icons.Default.Public, stringResource(app.aaps.core.interfaces.R.string.wear_more_watchfaces), onMoreWatchfaces),
                        button2 = ButtonDef(Icons.Default.Download, stringResource(R.string.wear_export_watchface), onExportTemplate)
                    )

                    // Watchface preview image
                    uiState.watchfaceImage?.let { image ->
                        Spacer(modifier = Modifier.height(AapsSpacing.small))
                        Image(
                            bitmap = image,
                            contentDescription = uiState.watchfaceName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AapsSpacing.extraLarge),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

private data class ButtonDef(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@Composable
private fun ButtonRow(
    button1: ButtonDef,
    button2: ButtonDef?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        OutlinedButton(
            onClick = button1.onClick,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Icon(button1.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = button1.text, textAlign = TextAlign.Center)
        }
        if (button2 != null) {
            OutlinedButton(
                onClick = button2.onClick,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Icon(button2.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = button2.text, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CwfInfosContent(
    state: CwfInfosState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        // Watchface image
        state.watchfaceImage?.let { image ->
            Image(
                bitmap = image,
                contentDescription = state.title,
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(AapsSpacing.medium))
        }

        // Metadata
        Text(text = state.fileName, style = MaterialTheme.typography.bodyMedium)
        Text(text = state.author, style = MaterialTheme.typography.bodyMedium)
        Text(text = state.createdAt, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = state.version,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.isVersionOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        if (state.comment.isNotBlank()) {
            Text(text = state.comment, style = MaterialTheme.typography.bodyMedium)
        }

        // Preferences section
        if (state.preferences.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
            Text(
                text = state.prefTitle,
                style = MaterialTheme.typography.titleSmall
            )
            Column {
                state.preferences.forEach { pref ->
                    ListItem(
                        headlineContent = {
                            Text(text = pref.label, style = MaterialTheme.typography.bodyMedium)
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (pref.isEnabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = stringResource(if (pref.isEnabled) R.string.enabled else R.string.disabled),
                                tint = if (pref.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }

        // View elements section
        if (state.viewElements.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
            Text(
                text = stringResource(R.string.cwf_infos_view_title),
                style = MaterialTheme.typography.titleSmall
            )
            Column {
                state.viewElements.forEach { viewItem ->
                    ListItem(
                        headlineContent = {
                            Text(text = viewItem.comment, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Text(
                                text = viewItem.key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WearMainContentPreview() {
    MaterialTheme {
        WearMainContent(
            uiState = WearUiState(
                connectedDevice = "Galaxy Watch 5 (a1b2)",
                isDeviceConnected = true,
                hasCustomWatchface = true,
                watchfaceName = "AAPS V2"
            ),
            onResendData = {},
            onOpenSettings = {},
            onLoadWatchface = {},
            onInfosWatchface = {},
            onExportTemplate = {},
            onMoreWatchfaces = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WearMainContentDisconnectedPreview() {
    MaterialTheme {
        WearMainContent(
            uiState = WearUiState(
                connectedDevice = "No watch connected",
                isDeviceConnected = false
            ),
            onResendData = {},
            onOpenSettings = {},
            onLoadWatchface = {},
            onInfosWatchface = {},
            onExportTemplate = {},
            onMoreWatchfaces = {}
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CwfInfosContentPreview() {
    MaterialTheme {
        CwfInfosContent(
            state = CwfInfosState(
                title = "AAPS V2 (1.0)",
                fileName = "Filename: AAPS_V2.zip",
                author = "Author: Someone",
                createdAt = "Created: 2025-01-15",
                version = "Version: 1.0",
                isVersionOk = true,
                comment = "Comment: Custom watchface for AAPS",
                prefTitle = "Required preferences (locked by CWF)",
                preferences = listOf(
                    CwfPrefItem("Show IOB", true),
                    CwfPrefItem("Show COB", true),
                    CwfPrefItem("Show Delta", false)
                ),
                viewElements = listOf(
                    CwfViewItem("\"status\":", "Loop status"),
                    CwfViewItem("\"iob1\":", "IOB value"),
                    CwfViewItem("\"cob1\":", "COB value")
                )
            )
        )
    }
}
