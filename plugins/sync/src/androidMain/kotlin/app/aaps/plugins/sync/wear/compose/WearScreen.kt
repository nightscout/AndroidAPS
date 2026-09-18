package app.aaps.plugins.sync.wear.compose

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.PushedWatchfaceId
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
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
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Back handler for sub-screens
    BackHandler(enabled = uiState.showInfos) { viewModel.hideCwfInfos() }
    BackHandler(enabled = uiState.showImportList) { viewModel.hideImportList() }

    // Only title needs pre-resolving (plain String used in LaunchedEffect suspend block)
    val wearTitle = stringResource(CoreUiStrings.wear)
    val importTitle = stringResource(SyncStrings.wear_import_custom_watchface_title)

    // Determine current sub-screen
    val subScreen = when {
        uiState.showImportList -> SubScreen.IMPORT_LIST
        uiState.showInfos      -> SubScreen.INFOS
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
                            contentDescription = stringResource(CoreUiStrings.back)
                        )
                    }
                },
                actions = {
                    if (subScreen == SubScreen.MAIN && onSettings != null) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(CoreUiStrings.nav_plugin_preferences)
                            )
                        }
                    }
                }
            )
        )
    }

    val moreWatchfacesUrl = stringResource(SyncStrings.wear_link_to_more_cwf_doc)

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
                    onSelectPushedWatchface = { viewModel.selectPushedWatchface(it) },
                    onDismissCustomWatchfaceNotShown = { viewModel.dismissCustomWatchfaceNotShown() },
                    modifier = modifier
                )
            }
        }
    }
}

private enum class SubScreen { MAIN, INFOS, IMPORT_LIST }

/**
 * @see WearMainContentPreview
 * @see WearMainContentDisconnectedPreview
 */
@Composable
internal fun WearMainContent(
    uiState: WearUiState,
    onResendData: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoadWatchface: () -> Unit,
    onInfosWatchface: () -> Unit,
    onExportTemplate: () -> Unit,
    onMoreWatchfaces: () -> Unit,
    onSelectPushedWatchface: (String) -> Unit,
    onDismissCustomWatchfaceNotShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The face the wearer tapped, waiting for their confirmation; null while no dialog is open.
    // Confirmed first because the watch replaces its installed face at once.
    var pendingWatchface by remember { mutableStateOf<String?>(null) }
    pendingWatchface?.let { face ->
        val current = if (uiState.customWatchfaceSelected) PushedWatchfaceId.CWF else PushedWatchfaceId.WFS
        // Leaving the complications face loses what was edited on it in the watch face editor:
        // the runtime drops a face's user configuration when a face of another package name takes
        // the slot. Leaving the custom face loses nothing worth a warning, so only one way says so.
        val message =
            if (current == PushedWatchfaceId.WFS) SyncStrings.wear_pushed_watchface_confirm_message_from_complications
            else SyncStrings.wear_pushed_watchface_confirm_message
        OkCancelDialog(
            title = stringResource(SyncStrings.wear_pushed_watchface_confirm_title),
            message = stringResource(message, pushedWatchfaceLabel(face), pushedWatchfaceLabel(current)),
            onConfirm = {
                onSelectPushedWatchface(face)
                pendingWatchface = null
            },
            onDismiss = { pendingWatchface = null }
        )
    }

    // A zip was just sent while the complications face is the one on the wrist: it is stored on
    // the watch, but nothing shows it until the custom face is selected. Said once, at that moment.
    uiState.customWatchfaceNotShown?.let { name ->
        OkDialog(
            title = pushedWatchfaceLabel(PushedWatchfaceId.CWF),
            message = stringResource(SyncStrings.wear_custom_watchface_not_shown, name),
            onDismiss = onDismissCustomWatchfaceNotShown
        )
    }

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
                    button1 = ButtonDef(Icons.Default.Refresh, stringResource(SyncStrings.resend_all_data), onResendData),
                    button2 = ButtonDef(Icons.Default.Settings, stringResource(SyncStrings.open_settings_on_wear), onOpenSettings)
                )
            }
        }

        // Pushed Watchface Card: only on a watch that reported Watch Face Push (Wear OS 6+). Below
        // that the pushed faces cannot exist, and a choice that does nothing would only mislead.
        if (uiState.isDeviceConnected && uiState.watchFacePushSupported) {
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
                        text = stringResource(StringKey.WearPushedWatchface.title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = AapsSpacing.small)
                    )

                    // Watch Face Push gives the app one slot, so this is a choice between the two
                    // embedded faces, not two switches. The tap asks first, see pendingWatchface.
                    // While the watch still holds the other face - the seconds an install takes,
                    // or longer after a reinstall until the preferences reach it - the chosen row
                    // says so, quietly: it is progress, not a fault.
                    val selectedId = if (uiState.customWatchfaceSelected) PushedWatchfaceId.CWF else PushedWatchfaceId.WFS
                    val installing = uiState.installedWatchface != null && uiState.installedWatchface != selectedId
                    Column(modifier = Modifier.selectableGroup()) {
                        listOf(PushedWatchfaceId.CWF, PushedWatchfaceId.WFS).forEach { face ->
                            val selected = face == selectedId
                            WatchfaceChoiceRow(
                                label = pushedWatchfaceLabel(face),
                                selected = selected,
                                hint = if (selected && installing) stringResource(SyncStrings.wear_pushed_watchface_installing) else null,
                                onSelect = { if (!selected) pendingWatchface = face }
                            )
                        }
                    }

                    // What the wrist shows with the complications face chosen. The custom face
                    // needs no picture here: its own preview is in its own card below.
                    if (!uiState.customWatchfaceSelected) {
                        Spacer(modifier = Modifier.height(AapsSpacing.small))
                        Image(
                            painter = painterResource(R.drawable.wfs_watchface_preview),
                            contentDescription = pushedWatchfaceLabel(PushedWatchfaceId.WFS),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AapsSpacing.extraLarge),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }

        // Custom Watchface Card (visible only when connected), whatever face is chosen above: a
        // watch below Wear OS 6 runs the code-based face, a watch like the Galaxy Watch 5 runs it
        // beside the pushed faces, and a zip can be loaded before switching
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
                        text = stringResource(SyncStrings.wear_custom_watchface, uiState.watchfaceName),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = AapsSpacing.small)
                    )

                    // Row 1: Load + Info
                    ButtonRow(
                        button1 = ButtonDef(Icons.Default.Upload, stringResource(SyncStrings.wear_load_watchface), onLoadWatchface),
                        button2 = if (uiState.hasCustomWatchface)
                            ButtonDef(Icons.Default.Info, stringResource(SyncStrings.wear_infos_watchface), onInfosWatchface)
                        else null
                    )

                    // Row 2: More Watchfaces + Export
                    ButtonRow(
                        button1 = ButtonDef(Icons.Default.Public, stringResource(InterfacesStrings.wear_more_watchfaces), onMoreWatchfaces),
                        button2 = ButtonDef(Icons.Default.Download, stringResource(SyncStrings.wear_export_watchface), onExportTemplate)
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

/** The label of an embedded face, from the same strings the key's list entries use */
@Composable
private fun pushedWatchfaceLabel(face: String): String =
    stringResource(if (face == PushedWatchfaceId.CWF) KeysStrings.wear_pushed_watchface_cwf else KeysStrings.wear_pushed_watchface_wfs)

/** One radio row; [hint] is a quiet note after the label, for a state that will pass by itself */
@Composable
private fun WatchfaceChoiceRow(
    label: String,
    selected: Boolean,
    hint: String?,
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = AapsSpacing.small, horizontal = AapsSpacing.small)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = AapsSpacing.medium)
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AapsSpacing.small)
            )
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
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Icon(button1.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = button1.text, textAlign = TextAlign.Center)
        }
        if (button2 != null) {
            OutlinedButton(
                onClick = button2.onClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Icon(button2.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = button2.text, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * @see CwfInfosContentPreview
 */
@Composable
internal fun CwfInfosContent(
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
            // Plain rows rather than ListItem: ListItem enforces its own 56dp minimum height, which
            // left these lines spread far wider apart than the metadata lines just above them.
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                state.preferences.forEach { pref ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pref.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (pref.isEnabled) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = stringResource(if (pref.isEnabled) SyncStrings.enabled else SyncStrings.disabled),
                            tint = if (pref.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // View elements section
        if (state.viewElements.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
            Text(
                text = stringResource(SyncStrings.cwf_infos_view_title),
                style = MaterialTheme.typography.titleSmall
            )
            // Same reason as the preference rows above - see there.
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                state.viewElements.forEach { viewItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewItem.key,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = viewItem.comment, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
