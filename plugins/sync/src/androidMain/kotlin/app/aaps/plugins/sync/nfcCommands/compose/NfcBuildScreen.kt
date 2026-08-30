package app.aaps.plugins.sync.nfcCommands.compose

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.QuickAddButtons
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.nfcCommands.ArgType
import app.aaps.plugins.sync.nfcCommands.NfcCategories
import app.aaps.plugins.sync.nfcCommands.NfcCommand
import app.aaps.plugins.sync.nfcCommands.NfcCommandCode
import app.aaps.plugins.sync.nfcCommands.NfcCommandsPlugin
import app.aaps.plugins.sync.nfcCommands.NfcCreatedTag
import app.aaps.plugins.sync.nfcCommands.NfcDefaults
import app.aaps.plugins.sync.nfcCommands.NfcLogEntry
import app.aaps.plugins.sync.nfcCommands.NfcParams
import app.aaps.plugins.sync.nfcCommands.NfcTagStore
import app.aaps.plugins.sync.nfcCommands.NfcUiCategory
import app.aaps.plugins.sync.nfcCommands.actions.NfcAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcBuildScreen(
    plugin: NfcCommandsPlugin,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onBack: () -> Unit,
    onTagWritten: () -> Unit = onBack,
    initialTagUid: String? = null,
    initialTag: NfcCreatedTag? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val categories = remember { NfcCategories.build(plugin.actionFactory) }
    
    val profileStore by plugin.profileRepository.profile.collectAsStateWithLifecycle()
    val profileNames = remember(profileStore) {
        profileStore?.getProfileList()?.map { it.toString() } ?: emptyList()
    }

    val scenesJson by plugin.sceneAutomationApi.scenesFlow.collectAsStateWithLifecycle()
    val sceneNames = remember(scenesJson) {
        plugin.sceneAutomationApi.getScenes().map { it.id to it.name }
    }

    val state = rememberNfcBuildState()

    val isEditMode = initialTag != null

    LaunchedEffect(initialTagUid, initialTag) {
        if (initialTag != null) {
            state.tagName = initialTag.name
            state.chain.clear()
            initialTag.commands.forEach { cmdJson ->
                NfcCommand.decode(cmdJson)?.let { decoded ->
                    val code = decoded.code
                    val params = decoded.params
                    run {
                        val action = createNfcUiAction(plugin, code, plugin.pumpBasalDurationStep())
                        // The command's own defaults first, then whatever the tag stored on top.
                        // applyParams leaves a field alone when the stored command has no value for it,
                        // so a missing value shows the default for that command rather than a number
                        // this screen made up - and the row is marked "value missing" either way.
                        action.applyParams(plugin.getAction(code).getDefaultParams())
                        action.applyParams(params)
                        state.chain.add(action)
                    }
                }
            }
            state.initialTagNameSnap = initialTag.name
            state.initialCommandsSnap = state.chain.map { action ->
                val p = action.getParams()
                NfcCommand(action.command, p).encode()
            }
            state.isInitialized = true
        } else if (initialTagUid != null) {
            val tag = plugin.nfcTagStore.findTagByUid(initialTagUid)
            if (tag != null) {
                state.tagName = tag.name
            }
            state.initialTagNameSnap = state.tagName
            state.initialCommandsSnap = emptyList()
            state.isInitialized = true
        } else {
            state.initialTagNameSnap = ""
            state.initialCommandsSnap = emptyList()
            state.isInitialized = true
        }
    }

    val title = if (isEditMode) stringResource(SyncStrings.nfccommands_rename_tag_title) else stringResource(SyncStrings.nfccommands_write_tag)
    val backDesc = stringResource(CoreUiStrings.back)
    val saveDesc = stringResource(CoreUiStrings.save)

    val onSave: () -> Unit = {
        if (initialTag != null) {
            val uid = initialTag.tagUid
            val commands = state.chain.map { 
                it.meta.params = it.getParams()
                it.meta.buildCommand(it.command)
            }
            val name = state.tagName
            plugin.nfcTagStore.saveCreatedTag(
                NfcCreatedTag(
                    tagUid = uid,
                    name = name,
                    commands = commands,
                    createdAtMillis = initialTag.createdAtMillis,
                    lastScannedAtMillis = initialTag.lastScannedAtMillis,
                ),
            )
            onTagWritten()
        }
    }

    val attemptClose: () -> Unit = {
        if (state.isDirty) state.showDiscardConfirm = true else onBack()
    }

    BackHandler { attemptClose() }

    LaunchedEffect(title, state.isDirty, state.chain.size) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = attemptClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSave()
                            },
                            enabled = state.isDirty && state.chain.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = saveDesc)
                        }
                    }
                },
            ),
        )
    }

    if (state.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { state.showDiscardConfirm = false },
            title = { Text(stringResource(SyncStrings.nfccommands_discard_title)) },
            text = { Text(stringResource(SyncStrings.nfccommands_discard_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        state.showDiscardConfirm = false
                        onBack()
                    }) { Text(stringResource(CoreUiStrings.confirm)) }
                    TextButton(onClick = {
                        state.showDiscardConfirm = false
                        onSave()
                    }) { Text(stringResource(CoreUiStrings.save)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showDiscardConfirm = false }) {
                    Text(stringResource(SyncStrings.cancel))
                }
            },
        )
    }

    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val nfcAdapter =
            NfcAdapter.getDefaultAdapter(context)
                ?: return@DisposableEffect onDispose {}

        val callback =
            NfcAdapter.ReaderCallback { tag ->
                if (!state.isWritingMode) {
                    plugin.aapsLogger.debug(LTag.NFC, "Inhibiting NFC scan in build screen (not in writing mode)")
                    return@ReaderCallback
                }

                val uid = NfcTagStore.tagUidHex(tag.id) ?: return@ReaderCallback
                val name = state.tagName
            val commands = state.chain.toList().map { action ->
                val p = action.getParams()
                NfcCommand(action.command, p).encode()
            }

                val existingTag = plugin.nfcTagStore.findTagByUid(uid)
                if (existingTag != null) {
                    coroutineScope.launch(Dispatchers.Main) {
                        state.isWritingMode = false
                        state.overwriteUid = uid
                        state.overwriteExistingName = existingTag.name
                        state.showOverwriteConfirm = true
                    }
                } else {
                    val ndefWritten = buildAndWriteNdef(tag, plugin)
                    val outcome = if (ndefWritten) WriteOutcome.NDEF_WRITTEN else WriteOutcome.GENERIC_ASSIGNED
                    val message = when (outcome) {
                        WriteOutcome.NDEF_WRITTEN -> plugin.rh.gs(SyncStrings.nfccommands_tag_written)
                        else -> plugin.rh.gs(SyncStrings.nfccommands_tag_assigned_generic)
                    }
                    plugin.nfcTagStore.appendLogEntry(
                        NfcLogEntry(
                            timestamp = System.currentTimeMillis(),
                            tagName = name,
                            action = "WRITE",
                            success = true,
                            message = message,
                        ),
                    )
                    // Always assign by UID — NDEF write is best-effort
                    plugin.nfcTagStore.saveCreatedTag(
                        NfcCreatedTag(
                            tagUid = uid,
                            name = name,
                            commands = commands,
                            createdAtMillis = System.currentTimeMillis(),
                        ),
                    )
                    plugin.nfcTagStore.markJustWritten(uid)
                    coroutineScope.launch(Dispatchers.Main) {
                        state.isWritingMode = false
                        if (outcome == WriteOutcome.GENERIC_ASSIGNED) {
                            plugin.showMessage(message)
                        }
                        state.chain.clear()
                        onTagWritten()
                    }
                }
            }
        nfcAdapter.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_F,
            null,
        )
        onDispose { nfcAdapter.disableReaderMode(activity) }
    }

    if (state.showBlankNameDialog) {
        AlertDialog(
            onDismissRequest = { state.showBlankNameDialog = false },
            title = { Text(stringResource(SyncStrings.nfccommands_blank_name_confirm_title)) },
            text = { Text(stringResource(SyncStrings.nfccommands_blank_name_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    state.showBlankNameDialog = false
                    state.isWritingMode = true
                }) { Text(stringResource(SyncStrings.nfccommands_blank_name_confirm_write_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { state.showBlankNameDialog = false }) {
                    Text(stringResource(SyncStrings.cancel))
                }
            },
        )
    }

    if (state.showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { state.showOverwriteConfirm = false },
            title = { Text(stringResource(SyncStrings.nfccommands_tag_already_registered_title)) },
            text = {
                val newName = state.tagName.ifBlank {
                    state.chain.firstOrNull()?.meta?.label?.let { stringResource(it) } ?: ""
                }
                Text(
                    stringResource(
                        SyncStrings.nfccommands_tag_already_registered_message,
                        state.overwriteExistingName,
                        newName
                    )
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        state.showOverwriteConfirm = false
                        onBack()
                    }) {
                        Text(stringResource(SyncStrings.nfccommands_discard_and_exit))
                    }
                    TextButton(onClick = {
                        state.showOverwriteConfirm = false
                        val name = state.tagName
                        val commands = state.chain.toList().map { action ->
                            val p = action.getParams()
                            NfcCommand(action.command, p).encode()
                        }
                        plugin.nfcTagStore.saveCreatedTag(
                            NfcCreatedTag(
                                tagUid = state.overwriteUid,
                                name = name,
                                commands = commands,
                                createdAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        plugin.nfcTagStore.markJustWritten(state.overwriteUid)
                        plugin.nfcTagStore.appendLogEntry(
                            NfcLogEntry(
                                timestamp = System.currentTimeMillis(),
                                tagName = name,
                                action = "WRITE",
                                success = true,
                                message = plugin.rh.gs(SyncStrings.nfccommands_tag_reassigned),
                            ),
                        )
                        state.chain.clear()
                        onTagWritten()
                    }) {
                        Text(stringResource(SyncStrings.nfccommands_overwrite))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { state.showOverwriteConfirm = false }) {
                    Text(stringResource(SyncStrings.nfccommands_cancel_scan))
                }
            },
        )
    }

    if (state.isWritingMode) {
        NfcWriteDialog(chain = state.chain.map { it.command.name }, onCancel = { state.isWritingMode = false })
    }

    if (state.showActionPicker) {
        ChooseActionSheet(
            plugin = plugin,
            categories = categories,
            onPick = { code ->
                coroutineScope.launch {
                    val action = createNfcUiAction(plugin, code, plugin.pumpBasalDurationStep())
                    action.applyParams(plugin.getAction(code).getDefaultParams())
                    state.chain.add(action)
                }
            },
            onDismiss = { state.showActionPicker = false }
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Section 1: Tag name
        OutlinedTextField(
            value = state.tagName,
            onValueChange = { 
                state.tagName = it
            },
            label = { Text(stringResource(SyncStrings.nfccommands_tag_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

        if (initialTag != null) {
            Text(
                text = stringResource(SyncStrings.nfccommands_tag_id_label, initialTag.tagUid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else if (initialTagUid != null) {
            Text(
                text = stringResource(SyncStrings.nfccommands_tag_id_label, initialTagUid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        SectionDivider(label = stringResource(SyncStrings.nfccommands_chain_title))

        // Section 2: Command state.chain (Editable list)
        if (state.chain.isEmpty()) {
            Text(
                text = stringResource(SyncStrings.nfccommands_cascade_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.chain.forEachIndexed { index, action ->
                    InlineActionCard(
                        plugin = plugin,
                        action = action,
                        profileNames = profileNames,
                        sceneNames = sceneNames,
                        onRemove = { state.chain.removeAt(index) }
                    )
                }
            }
        }

        // Section 3: Add Action Button
        OutlinedButton(
            onClick = { state.showActionPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(CoreUiStrings.add))
        }

        Spacer(Modifier.height(16.dp))

        // Section 4: Register button
        if (!isEditMode) {
            Button(
                onClick = {
                    if (state.tagName.isBlank()) state.showBlankNameDialog = true else state.isWritingMode = true
                },
                enabled = state.chain.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(SyncStrings.nfccommands_write_tag))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionDivider(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun InlineActionCard(
    plugin: NfcCommandsPlugin,
    action: NfcUiAction,
    profileNames: List<String>,
    sceneNames: List<Pair<String, String>>,
    onRemove: () -> Unit
) {
    val meta = action.meta

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CardDefaults.elevatedShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = meta.icon,
                    contentDescription = null,
                    tint = meta.customIconColor?.invoke() ?: meta.elementType.color(),
                    modifier = Modifier.size(20.dp)
                )
                meta.secondaryIcon?.let { secondaryIcon ->
                    Icon(
                        imageVector = secondaryIcon,
                        contentDescription = null,
                        tint = meta.secondaryIconColor?.invoke() ?: meta.elementType.color(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = stringResource(meta.label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                action.EditContent(profileNames, sceneNames) {
                    action.meta.params = action.getParams()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChooseActionSheet(
    plugin: NfcCommandsPlugin,
    categories: List<NfcUiCategory>,
    onPick: (NfcCommandCode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .consumeOverscroll()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(SyncStrings.nfccommands_add_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            categories.forEach { cat ->
                Text(
                    text = stringResource(cat.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cat.commands.forEach { code ->
                        val action = remember(code) { plugin.getAction(code) }
                        AssistChip(
                            onClick = {
                                onPick(code)
                                onDismiss()
                            },
                            label = { Text(stringResource(action.label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = action.customIconColor?.invoke() ?: action.elementType.color(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun NfcWriteDialog(
    chain: List<String>,
    onCancel: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 0), RepeatMode.Restart),
        label = "ring1",
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 200), RepeatMode.Restart),
        label = "ring2",
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 400), RepeatMode.Restart),
        label = "ring3",
    )

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(SyncStrings.nfccommands_write_ready),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val ringColor = MaterialTheme.colorScheme.primary
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier =
                            Modifier
                                .size(120.dp)
                                .scale(ring3Scale)
                                .alpha(1f - ring3Scale),
                    ) {}
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier =
                            Modifier
                                .size(80.dp)
                                .scale(ring2Scale)
                                .alpha(1f - ring2Scale),
                    ) {}
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier =
                            Modifier
                                .size(40.dp)
                                .scale(ring1Scale)
                                .alpha(1f - ring1Scale),
                    ) {}
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text =
                        chain
                            .mapIndexed { i, cmd ->
                                stringResource(SyncStrings.nfccommands_cascade_step_label, i + 1, cmd)
                            }.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(SyncStrings.cancel)) }
        },
    )
}

private fun buildAndWriteNdef(
    tag: Tag,
    plugin: NfcCommandsPlugin,
): Boolean {
    val record = NdefRecord.createMime(NfcTagStore.MIME_TYPE, ByteArray(0))
    val message = NdefMessage(arrayOf(record))
    return try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            ndef.use {
                it.writeNdefMessage(message)
                true
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: return false
            formatable.connect()
            formatable.use {
                it.format(message)
                true
            }
        }
    } catch (e: Exception) {
        plugin.aapsLogger.error(LTag.NFC, "Failed to write NDEF tag", e)
        false
    }
}

internal enum class WriteOutcome { REASSIGNED, NDEF_WRITTEN, GENERIC_ASSIGNED }

internal fun resolveWriteOutcome(alreadyAssigned: Boolean, ndefWritten: Boolean): WriteOutcome = when {
    alreadyAssigned -> WriteOutcome.REASSIGNED
    ndefWritten     -> WriteOutcome.NDEF_WRITTEN
    else            -> WriteOutcome.GENERIC_ASSIGNED
}

// ---------- NFC UI Action Models ----------

interface NfcUiAction {
    val command: NfcCommandCode
    val meta: NfcAction
    fun shortDescription(): String
    @Composable
    fun EditContent(profileNames: List<String>, sceneNames: List<Pair<String, String>>, onChange: () -> Unit)

    fun applyParams(params: NfcParams)

    fun getParams(): NfcParams
}

private fun createNfcUiAction(plugin: NfcCommandsPlugin, code: NfcCommandCode, durationStep: Int): NfcUiAction {
    val action = plugin.getAction(code)
    return GenericNfcUiAction(plugin, code, action.argType, durationStep)
}

/**
 * Generic implementation of [NfcUiAction] that dynamically builds the UI based on [argTypes].
 * It manages state for all possible atomic arguments (Insulin, Carbs, Duration, etc.).
 */
class GenericNfcUiAction(
    val plugin: NfcCommandsPlugin,
    override val command: NfcCommandCode,
    val argTypes: List<ArgType>,
    val durationStep: Int
) : NfcUiAction {
    override val meta = plugin.getAction(command)

    // UI state for all possible field types. These are only placeholders: both places that create a
    // row call applyParams straight afterwards, with the command's own defaults for a new command and
    // with the stored values for an existing one.
    var units by mutableDoubleStateOf(NfcDefaults.BOLUS_INSULIN)
    var glucose by mutableDoubleStateOf(NfcDefaults.TEMP_TARGET_MGDL_WITHOUT_PROFILE)
    var grams by mutableIntStateOf(NfcDefaults.CARBS_GRAMS)
    var duration by mutableIntStateOf(NfcDefaults.EXTENDED_BOLUS_DURATION_MINUTES)
    var percent by mutableIntStateOf(NfcDefaults.PROFILE_SWITCH_PERCENT)
    var rate by mutableDoubleStateOf(NfcDefaults.TEMP_BASAL_RATE)
    var meal by mutableStateOf(false)
    var profileName by mutableStateOf("")
    var sceneId by mutableStateOf("")

    // Bolus Wizard calculation toggles
    var useBg by mutableStateOf(true)
    var useTT by mutableStateOf(true)
    var useTrend by mutableStateOf(true)
    var useIOB by mutableStateOf(true)
    var useCOB by mutableStateOf(true)

    override fun shortDescription() = ""

    /** Current UI state as the parameters this command will carry. */
    override fun getParams(): NfcParams {
        var p = NfcParams()
        argTypes.forEach { type ->
            p = when (type) {
                ArgType.INSULIN              -> p.copy(insulin = units)
                ArgType.AMOUNT_GRAMS         -> p.copy(carbs = grams)
                ArgType.RATE                 -> p.copy(rate = rate)
                ArgType.PERCENT              -> p.copy(percent = percent)
                ArgType.DURATION             -> p.copy(duration = duration)
                ArgType.MEAL_CHECK           -> p.copy(isMeal = meal)
                ArgType.PROFILE_NAME         -> p.copy(profileName = profileName)
                ArgType.SCENE_ID             -> p.copy(sceneId = sceneId)
                ArgType.GLUCOSE_TARGET       -> p.copy(glucose = glucose)
                ArgType.BOLUS_WIZARD_OPTIONS -> p.copy(
                    useBg = useBg, useTt = useTT, useTrend = useTrend, useIob = useIOB, useCob = useCOB
                )

                else                         -> p
            }
        }
        return p
    }

    /**
     * Restores UI state from stored parameters.
     */
    override fun applyParams(params: NfcParams) {
        argTypes.forEach { type ->
            run {
                when (type) {
                    ArgType.INSULIN              -> units = params.insulin ?: units
                    ArgType.AMOUNT_GRAMS         -> grams = params.carbs ?: grams
                    ArgType.PERCENT              -> percent = params.percent ?: percent
                    ArgType.RATE                 -> rate = params.rate ?: rate
                    ArgType.DURATION             -> duration = params.duration ?: duration
                    ArgType.MEAL_CHECK           -> meal = params.isMeal
                    ArgType.PROFILE_NAME         -> profileName = params.profileName ?: profileName
                    ArgType.SCENE_ID             -> sceneId = params.sceneId ?: sceneId
                    ArgType.GLUCOSE_TARGET       -> glucose = params.glucose ?: glucose

                    ArgType.BOLUS_WIZARD_OPTIONS -> {
                        useBg = params.useBg
                        useTT = params.useTt
                        useTrend = params.useTrend
                        useIOB = params.useIob
                        useCOB = params.useCob
                    }

                    else                         -> {}
                }
            }
        }
        meta.params = params
    }

    /**
     * Dynamically renders the input fields required for this specific action.
     */
    @Composable
    override fun EditContent(profileNames: List<String>, sceneNames: List<Pair<String, String>>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (argTypes.isEmpty()) {
                Text(
                    text = stringResource(SyncStrings.nfccommands_no_args_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            argTypes.forEach { type ->
                when (type) {
                    ArgType.INSULIN -> InsulinInputRow(plugin, units) { units = it; onChange() }
                    ArgType.AMOUNT_GRAMS -> AmountGramsInputRow(plugin, grams) { grams = it; onChange() }
                    ArgType.DURATION -> {
                        val range = when(command) {
                            NfcCommandCode.PUMP_DISCONNECT -> 15.0..180.0
                            NfcCommandCode.LOOP_SUSPEND -> 30.0..480.0
                            else -> durationStep.toDouble()..480.0
                        }
                        val step = when(command) {
                            NfcCommandCode.PUMP_DISCONNECT -> 15.0
                            NfcCommandCode.LOOP_SUSPEND -> 30.0
                            else -> durationStep.toDouble()
                        }
                        DurationInputRow(duration.toDouble(), range, step) { duration = it.toInt(); onChange() }
                    }
                    ArgType.RATE -> RateInputRow(rate) { rate = it; onChange() }
                    ArgType.PERCENT -> {
                        val range = if (command == NfcCommandCode.PROFILE_SWITCH) 10.0..500.0 else 0.0..200.0
                        val step = 5.0
                        PercentInputRow(percent.toDouble(), range, step) { percent = it.toInt(); onChange() }
                    }
                    ArgType.MEAL_CHECK -> MealCheckRow(meal) { meal = it; onChange() }
                    ArgType.PROFILE_NAME -> NfcDropdown(
                        value = profileName.ifEmpty { profileNames.firstOrNull() ?: "" },
                        options = profileNames.map { it to it },
                        onValueChange = { profileName = it; onChange() },
                        label = stringResource(CoreUiStrings.profile)
                    )
                    ArgType.SCENE_ID -> NfcDropdown(
                        value = sceneId.ifEmpty { sceneNames.firstOrNull()?.first ?: "" },
                        options = sceneNames,
                        onValueChange = { sceneId = it; onChange() },
                        label = stringResource(CoreUiStrings.scenes)
                    )
                    ArgType.GLUCOSE_TARGET -> GlucoseInputRow(plugin, glucose) { glucose = it; onChange() }
                    ArgType.BOLUS_WIZARD_OPTIONS -> CalculatorOptions(useBg, useTT, useTrend, useIOB, useCOB,
                        onBgChange = { useBg = it; onChange() },
                        onTTChange = { useTT = it; onChange() },
                        onTrendChange = { useTrend = it; onChange() },
                        onIOBChange = { useIOB = it; onChange() },
                        onCOBChange = { useCOB = it; onChange() }
                    )
                    else -> {}
                }
            }
        }
    }
}

// ---------- Elementary Input Rows ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcDropdown(
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = options.find { it.first == value }?.second ?: value
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.second) },
                    onClick = { onValueChange(opt.first); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun InsulinInputRow(plugin: NfcCommandsPlugin, value: Double, onValueChange: (Double) -> Unit) {
    val bolusStep = plugin.activePlugin.activePump.pumpDescription.bolusStep
    NumberInputRow(
        labelRef = CoreUiStrings.overview_insulin_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.0..30.0,
        step = bolusStep,
        decimalPlaces = 2,
        unitLabel = CoreUiStrings.units_insulin
    )
    InsulinQuickAddButtons(
        increment1 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
        increment2 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
        increment3 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement3),
        onAddInsulin = { onValueChange((value + it).coerceIn(0.0, 30.0)) }
    )
}

@Composable
private fun AmountGramsInputRow(plugin: NfcCommandsPlugin, value: Int, onValueChange: (Int) -> Unit) {
    NumberInputRow(
        labelRef = InterfacesStrings.carbs,
        value = value.toDouble(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0.0..200.0,
        step = 1.0,
        valueFormat = NumberFormat.INTEGER,
        unitLabel = CoreUiStrings.units_grams
    )
    QuickAddButtons(
        increment1 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement1),
        increment2 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement2),
        increment3 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement3),
        onAddCarbs = { onValueChange((value + it).coerceIn(0, 200)) }
    )
}

@Composable
private fun DurationInputRow(value: Double, range: ClosedFloatingPointRange<Double>, step: Double, onValueChange: (Double) -> Unit) {
    NumberInputRow(
        labelRef = CoreUiStrings.duration_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        valueFormat = NumberFormat.INTEGER,
        unitLabel = CoreUiStrings.units_min
    )
}

@Composable
private fun RateInputRow(value: Double, onValueChange: (Double) -> Unit) {
    NumberInputRow(
        labelRef = SyncStrings.nfccommands_basal_rate_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.0..10.0,
        step = 0.05,
        decimalPlaces = 2,
        unitLabel = CoreUiStrings.units_insulin_rate
    )
}

@Composable
private fun PercentInputRow(value: Double, range: ClosedFloatingPointRange<Double> = 0.0..200.0, step: Double = 10.0, onValueChange: (Double) -> Unit) {
    NumberInputRow(
        labelRef = CoreUiStrings.percent,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        valueFormat = NumberFormat.INTEGER,
        unitLabel = CoreUiStrings.units_percent
    )
}

@Composable
private fun MealCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(SyncStrings.nfccommands_meal_bolus))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorOptions(
    useBg: Boolean,
    useTT: Boolean,
    useTrend: Boolean,
    useIOB: Boolean,
    useCOB: Boolean,
    onBgChange: (Boolean) -> Unit,
    onTTChange: (Boolean) -> Unit,
    onTrendChange: (Boolean) -> Unit,
    onIOBChange: (Boolean) -> Unit,
    onCOBChange: (Boolean) -> Unit
) {
    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            checked = useBg,
            onCheckedChange = {
                onBgChange(it)
                if (!it) onTTChange(false)
            },
            shape = SegmentedButtonDefaults.itemShape(0, 5),
            icon = {}
        ) {
            Icon(imageVector = ElementType.BG_CHECK.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        SegmentedButton(
            checked = useTT,
            onCheckedChange = {
                onTTChange(it)
                if (it) onBgChange(true)
            },
            shape = SegmentedButtonDefaults.itemShape(1, 5),
            icon = {}
        ) {
            Icon(imageVector = IcTtManual, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        SegmentedButton(
            checked = useTrend,
            onCheckedChange = onTrendChange,
            shape = SegmentedButtonDefaults.itemShape(2, 5),
            icon = {}
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        SegmentedButton(
            checked = useIOB,
            onCheckedChange = {
                onIOBChange(it)
                if (!it) onCOBChange(false)
            },
            shape = SegmentedButtonDefaults.itemShape(3, 5),
            icon = {}
        ) {
            Icon(imageVector = ElementType.INSULIN.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        SegmentedButton(
            checked = useCOB,
            onCheckedChange = {
                onCOBChange(it)
                if (it) onIOBChange(true)
            },
            shape = SegmentedButtonDefaults.itemShape(4, 5),
            icon = {}
        ) {
            Icon(imageVector = ElementType.COB.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun GlucoseInputRow(plugin: NfcCommandsPlugin, value: Double, onValueChange: (Double) -> Unit) {
    val units = plugin.profileUtil.units
    val isMmol = units == GlucoseUnit.MMOL
    val range = if (isMmol) 3.9..13.9 else 70.0..250.0
    val step = if (isMmol) 0.1 else 5.0
    val decimals = if (isMmol) 1 else 0
    val format = if (isMmol) NumberFormat.DECIMAL_1 else NumberFormat.INTEGER
    
    NumberInputRow(
        labelRef = CoreUiStrings.target_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        decimalPlaces = decimals,
        valueFormat = format,
        unitLabel = if (isMmol) CoreUiStrings.units_mmol else CoreUiStrings.units_mgdl
    )
}

@Composable
private fun InsulinQuickAddButtons(
    increment1: Double,
    increment2: Double,
    increment3: Double,
    onAddInsulin: (Double) -> Unit
) {
    val increments = listOf(increment1, increment2, increment3).filter { it != 0.0 }
    if (increments.isEmpty()) return

    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        increments.forEach { amount ->
            val label = if (amount > 0) "+$amount" else amount.toString()
            FilledTonalButton(onClick = {
                focusManager.clearFocus()
                onAddInsulin(amount)
            }) {
                Text(label)
            }
        }
    }
}
