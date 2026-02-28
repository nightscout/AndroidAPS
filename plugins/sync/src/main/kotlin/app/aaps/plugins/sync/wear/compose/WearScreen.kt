package app.aaps.plugins.sync.wear.compose

import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.sync.R

@Composable
fun WearScreen(
    viewModel: WearViewModel,
    rh: ResourceHelper,
    importExportPrefs: ImportExportPrefs,
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

    // Back handler for infos sub-screen
    BackHandler(enabled = uiState.showInfos) { viewModel.hideCwfInfos() }

    // Toolbar config
    LaunchedEffect(uiState.showInfos, uiState.cwfInfosState?.title) {
        setToolbarConfig(
            ToolbarConfig(
                title = if (uiState.showInfos) uiState.cwfInfosState?.title ?: "" else rh.gs(app.aaps.core.ui.R.string.wear),
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.showInfos) viewModel.hideCwfInfos()
                        else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = rh.gs(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (!uiState.showInfos && onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = rh.gs(app.aaps.core.ui.R.string.nav_plugin_preferences)
                            )
                        }
                    }
                }
            )
        )
    }

    val moreWatchfacesUrl = stringResource(R.string.wear_link_to_more_cwf_doc)

    AnimatedContent(
        targetState = uiState.showInfos,
        label = "wear_screen"
    ) { showInfos ->
        val infosState = uiState.cwfInfosState
        if (showInfos && infosState != null) {
            CwfInfosContent(
                state = infosState,
                modifier = modifier
            )
        } else {
            WearMainContent(
                uiState = uiState,
                onResendData = { viewModel.resendData() },
                onOpenSettings = { viewModel.openSettingsOnWear() },
                onLoadWatchface = { (context as? FragmentActivity)?.let { importExportPrefs.importCustomWatchface(it) } },
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Connection Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wear_custom_watchface, uiState.watchfaceName),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 4.dp)
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Image(
                            bitmap = image,
                            contentDescription = uiState.watchfaceName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Spacer(modifier = Modifier.height(8.dp))
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = state.prefTitle,
                style = MaterialTheme.typography.titleSmall
            )
            state.preferences.forEach { pref ->
                ListItem(
                    headlineContent = {
                        Text(text = pref.label, style = MaterialTheme.typography.bodyMedium)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (pref.isEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (pref.isEnabled) "On" else "Off",
                            tint = if (pref.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        // View elements section
        if (state.viewElements.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.cwf_infos_view_title),
                style = MaterialTheme.typography.titleSmall
            )
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
