package app.aaps.plugins.sync.nfcCommands

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.QuickAddButtons
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nfcCommands.actions.NfcAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DecimalFormat
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

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
    val categories = remember { NfcCategories.build(plugin) }
    
    val profileStore by plugin.profileRepository.profile.collectAsStateWithLifecycle()
    val profileNames = remember(profileStore) {
        profileStore?.getProfileList()?.map { it.toString() } ?: emptyList()
    }

    val scenesJson by plugin.sceneAutomationApi.scenesFlow.collectAsStateWithLifecycle()
    val sceneNames = remember(scenesJson) {
        plugin.sceneAutomationApi.getScenes().map { it.id to it.name }
    }

    val chain = remember { mutableStateListOf<NfcUiAction>() }
    var tagName by remember { mutableStateOf("") }
    var isWritingMode by remember { mutableStateOf(false) }
    var showBlankNameDialog by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Track initial state for "dirty" check
    var initialCommandsSnap by remember { mutableStateOf<List<String>>(emptyList()) }
    var initialTagNameSnap by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }

    val currentCommands = chain.map { 
        it.meta.params = it.getParams()
        it.meta.buildCommand(it.command, tagName)
    }
    val isDirty = isInitialized && (tagName != initialTagNameSnap || currentCommands != initialCommandsSnap)

    val isEditMode = initialTag != null

    LaunchedEffect(initialTagUid, initialTag) {
        if (initialTag != null) {
            tagName = initialTag.name
            chain.clear()
            initialTag.commands.forEach { cmdJson ->
                runCatching { JSONObject(cmdJson) }.onSuccess { json ->
                    val codeName = json.optString(NfcJsonKeys.CODE)
                    val code = runCatching { NfcCommandCode.valueOf(codeName) }.getOrNull()
                    val params = json.optJSONObject(NfcJsonKeys.PARAMS) ?: JSONObject()
                    if (code != null) {
                        val action = createNfcUiAction(plugin, code, plugin.pumpBasalDurationStep())
                        action.applyParams(params)
                        chain.add(action)
                    }
                }
            }
            initialTagNameSnap = initialTag.name
            initialCommandsSnap = chain.map { 
                it.meta.params = it.getParams()
                it.meta.buildCommand(it.command, initialTag.name)
            }
            isInitialized = true
        } else if (initialTagUid != null) {
            val tag = plugin.nfcTagStore.findTagByUid(initialTagUid)
            if (tag != null) {
                tagName = tag.name
            }
            initialTagNameSnap = tagName
            initialCommandsSnap = emptyList()
            isInitialized = true
        } else {
            initialTagNameSnap = ""
            initialCommandsSnap = emptyList()
            isInitialized = true
        }
    }

    val title = if (isEditMode) stringResource(R.string.nfccommands_rename_tag_title) else stringResource(R.string.nfccommands_write_tag)
    val backDesc = stringResource(CoreUiR.string.back)
    val saveDesc = stringResource(CoreUiR.string.save)

    val onSave: () -> Unit = {
        if (initialTag != null) {
            val uid = initialTag.tagUid
            val commands = chain.map { 
                it.meta.params = it.getParams()
                it.meta.buildCommand(it.command, tagName)
            }
            val name = tagName
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
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler { attemptClose() }

    LaunchedEffect(title, isDirty, chain.size) {
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
                            enabled = isDirty && chain.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = saveDesc)
                        }
                    }
                },
            ),
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.nfccommands_discard_title)) },
            text = { Text(stringResource(R.string.nfccommands_discard_message)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        onBack()
                    }) { Text(stringResource(CoreUiR.string.confirm)) }
                    TextButton(onClick = {
                        showDiscardConfirm = false
                        onSave()
                    }) { Text(stringResource(CoreUiR.string.save)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(isWritingMode) {
        if (!isWritingMode) return@DisposableEffect onDispose {}
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val nfcAdapter =
            NfcAdapter.getDefaultAdapter(context)
                ?: return@DisposableEffect onDispose {}

        val callback =
            NfcAdapter.ReaderCallback { tag ->
                val uid = NfcTagStore.tagUidHex(tag.id) ?: return@ReaderCallback
                val name = tagName
                val commands = chain.map { 
                    it.meta.params = it.getParams()
                    it.meta.buildCommand(it.command, name)
                }
                val alreadyAssigned = plugin.nfcTagStore.findTagByUid(uid) != null
                val ndefWritten = !alreadyAssigned && buildAndWriteNdef(tag, plugin)
                val outcome = resolveWriteOutcome(alreadyAssigned, ndefWritten)
                val message =
                    when (outcome) {
                        WriteOutcome.REASSIGNED -> plugin.rh.gs(R.string.nfccommands_tag_reassigned)
                        WriteOutcome.NDEF_WRITTEN -> plugin.rh.gs(R.string.nfccommands_tag_written)
                        WriteOutcome.GENERIC_ASSIGNED -> plugin.rh.gs(R.string.nfccommands_tag_assigned_generic)
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
                    isWritingMode = false
                    if (outcome == WriteOutcome.GENERIC_ASSIGNED) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    chain.clear()
                    onTagWritten()
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

    if (showBlankNameDialog) {
        AlertDialog(
            onDismissRequest = { showBlankNameDialog = false },
            title = { Text(stringResource(R.string.nfccommands_blank_name_confirm_title)) },
            text = { Text(stringResource(R.string.nfccommands_blank_name_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showBlankNameDialog = false
                    isWritingMode = true
                }) { Text(stringResource(R.string.nfccommands_blank_name_confirm_write_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showBlankNameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (isWritingMode) {
        NfcWriteDialog(chain = chain.map { it.command.name }, onCancel = { isWritingMode = false })
    }

    if (showActionPicker) {
        ChooseActionSheet(
            plugin = plugin,
            categories = categories,
            onPick = { code ->
                coroutineScope.launch {
                    val action = createNfcUiAction(plugin, code, plugin.pumpBasalDurationStep())
                    action.applyParams(plugin.getAction(code).getDefaultParams())
                    chain.add(action)
                }
            },
            onDismiss = { showActionPicker = false }
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
            value = tagName,
            onValueChange = { 
                tagName = it
            },
            label = { Text(stringResource(R.string.nfccommands_tag_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

        if (initialTag != null) {
            Text(
                text = "${stringResource(R.string.nfccommands_tag_id_label)} ${initialTag.tagUid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        } else if (initialTagUid != null) {
            Text(
                text = "${stringResource(R.string.nfccommands_tag_id_label)} $initialTagUid",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        SectionDivider(label = stringResource(R.string.nfccommands_chain_title))

        // Section 2: Command chain (Editable list)
        if (chain.isEmpty()) {
            Text(
                text = stringResource(R.string.nfccommands_cascade_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chain.forEachIndexed { index, action ->
                    InlineActionCard(
                        plugin = plugin,
                        action = action,
                        profileNames = profileNames,
                        sceneNames = sceneNames,
                        onRemove = { chain.removeAt(index) }
                    )
                }
            }
        }

        // Section 3: Add Action Button
        OutlinedButton(
            onClick = { showActionPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(CoreUiR.string.add))
        }

        Spacer(Modifier.height(16.dp))

        // Section 4: Register button
        if (!isEditMode) {
            Button(
                onClick = {
                    if (tagName.isBlank()) showBlankNameDialog = true else isWritingMode = true
                },
                enabled = chain.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.nfccommands_write_tag))
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
    meta.params = action.getParams()

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
                    text = stringResource(meta.labelResId),
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
                    // Implicitly updates meta.params via getParams() inside EditContent
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
                text = stringResource(CoreUiR.string.add) + " Action",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            categories.forEach { cat ->
                Text(
                    text = stringResource(cat.labelResId),
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
                            label = { Text(stringResource(action.labelResId)) },
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
                stringResource(R.string.nfccommands_write_ready),
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
                                stringResource(R.string.nfccommands_cascade_step_label, i + 1, cmd)
                            }.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
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

    fun applyParams(params: JSONObject)

    fun getParams(): JSONObject
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

    // UI State for all possible field types
    var units by mutableDoubleStateOf(1.0)
    var glucose by mutableDoubleStateOf(100.0)
    var grams by mutableIntStateOf(20)
    var duration by mutableIntStateOf(30)
    var percent by mutableIntStateOf(100)
    var rate by mutableDoubleStateOf(1.0)
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

    /**
     * Serializes current UI state into a JSONObject using keys defined in [ArgType].
     */
    override fun getParams(): JSONObject {
        val json = JSONObject()
        argTypes.forEach { type ->
            type.jsonKey?.let { key ->
                when (type) {
                    ArgType.INSULIN              -> json.put(key, units)
                    ArgType.AMOUNT_GRAMS         -> json.put(key, grams)
                    ArgType.RATE                 -> json.put(key, rate)
                    ArgType.PERCENT              -> json.put(key, percent)
                    ArgType.DURATION             -> json.put(key, duration)
                    ArgType.MEAL_CHECK           -> json.put(key, meal)
                    ArgType.PROFILE_NAME         -> json.put(key, profileName)
                    ArgType.SCENE_ID             -> json.put(key, sceneId)
                    ArgType.GLUCOSE_TARGET       -> json.put(key, glucose)

                    ArgType.BOLUS_WIZARD_OPTIONS -> {
                        json.put(NfcJsonKeys.USE_BG, useBg)
                        json.put(NfcJsonKeys.USE_TT, useTT)
                        json.put(NfcJsonKeys.USE_TREND, useTrend)
                        json.put(NfcJsonKeys.USE_IOB, useIOB)
                        json.put(NfcJsonKeys.USE_COB, useCOB)
                    }

                    else                         -> {}
                }
            }
        }
        meta.params = json
        return json
    }

    /**
     * Restores UI state from a JSONObject.
     */
    override fun applyParams(params: JSONObject) {
        argTypes.forEach { type ->
            type.jsonKey?.let { key ->
                when (type) {
                    ArgType.INSULIN              -> units = params.optDouble(key, 1.0)
                    ArgType.AMOUNT_GRAMS         -> grams = params.optInt(key, 20)
                    ArgType.PERCENT              -> percent = params.optInt(key, 100)
                    ArgType.RATE                 -> rate = params.optDouble(key, 1.0)
                    ArgType.DURATION             -> duration = params.optInt(key, 30)
                    ArgType.MEAL_CHECK           -> meal = params.optBoolean(key, false)
                    ArgType.PROFILE_NAME         -> profileName = params.optString(key, "")
                    ArgType.SCENE_ID             -> sceneId = params.optString(key, "")
                    ArgType.GLUCOSE_TARGET       -> glucose = params.optDouble(key, 100.0)

                    ArgType.BOLUS_WIZARD_OPTIONS -> {
                        useBg = params.optBoolean(NfcJsonKeys.USE_BG, true)
                        useTT = params.optBoolean(NfcJsonKeys.USE_TT, true)
                        useTrend = params.optBoolean(NfcJsonKeys.USE_TREND, true)
                        useIOB = params.optBoolean(NfcJsonKeys.USE_IOB, true)
                        useCOB = params.optBoolean(NfcJsonKeys.USE_COB, true)
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
                    text = stringResource(R.string.nfccommands_no_args_needed),
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
                        val step = if (command == NfcCommandCode.PROFILE_SWITCH) 5.0 else 10.0
                        PercentInputRow(percent.toDouble(), range, step) { percent = it.toInt(); onChange() }
                    }
                    ArgType.MEAL_CHECK -> MealCheckRow(meal) { meal = it; onChange() }
                    ArgType.PROFILE_NAME -> NfcDropdown(
                        value = profileName.ifEmpty { profileNames.firstOrNull() ?: "" },
                        options = profileNames.map { it to it },
                        onValueChange = { profileName = it; onChange() },
                        label = stringResource(CoreUiR.string.profile)
                    )
                    ArgType.SCENE_ID -> NfcDropdown(
                        value = sceneId.ifEmpty { sceneNames.firstOrNull()?.first ?: "" },
                        options = sceneNames,
                        onValueChange = { sceneId = it; onChange() },
                        label = stringResource(CoreUiR.string.scenes)
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
        labelResId = CoreUiR.string.overview_insulin_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.0..30.0,
        step = bolusStep,
        decimalPlaces = 2,
        unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname)
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
        labelResId = CoreUiR.string.carbs,
        value = value.toDouble(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = 0.0..200.0,
        step = 1.0,
        valueFormat = DecimalFormat("0"),
        unitLabel = stringResource(CoreUiR.string.shortgramm)
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
        labelResId = CoreUiR.string.duration_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        valueFormat = DecimalFormat("0"),
        unitLabel = stringResource(KeysR.string.units_min)
    )
}

@Composable
private fun RateInputRow(value: Double, onValueChange: (Double) -> Unit) {
    NumberInputRow(
        labelResId = R.string.nfccommands_basal_rate_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.0..10.0,
        step = 0.05,
        decimalPlaces = 2,
        unitLabel = stringResource(CoreUiR.string.profile_ins_units_per_hour)
    )
}

@Composable
private fun PercentInputRow(value: Double, range: ClosedFloatingPointRange<Double> = 0.0..200.0, step: Double = 10.0, onValueChange: (Double) -> Unit) {
    NumberInputRow(
        labelResId = CoreUiR.string.percent,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        valueFormat = DecimalFormat("0"),
        unitLabel = "%"
    )
}

@Composable
private fun MealCheckRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(stringResource(R.string.nfccommands_meal_bolus))
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
    val format = if (isMmol) DecimalFormat("0.0") else DecimalFormat("0")
    
    NumberInputRow(
        labelResId = CoreUiR.string.target_label,
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        step = step,
        decimalPlaces = decimals,
        valueFormat = format,
        unitLabel = if (isMmol) "mmol/l" else "mg/dl"
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
