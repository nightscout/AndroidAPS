package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.QuickAddButtons
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.navigation.color
import app.aaps.plugins.main.R
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
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val categories = remember { NfcCategories.build() }
    
    val profileStore by plugin.profileRepository.profile.collectAsStateWithLifecycle()
    val profileNames = remember(profileStore) {
        profileStore?.getProfileList()?.map { it.toString() } ?: emptyList()
    }

    val chain = remember { mutableStateListOf<NfcUiAction>() }
    var tagName by remember { mutableStateOf("") }
    var isWritingMode by remember { mutableStateOf(false) }
    var showBlankNameDialog by remember { mutableStateOf(false) }
    var showActionPicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialTagUid) {
        if (initialTagUid != null) {
            val tag = plugin.nfcTagStore.findTagByUid(initialTagUid)
            if (tag != null) {
                tagName = tag.name
                chain.clear()
            }
        }
    }

    val title = stringResource(R.string.nfccommands_write_tag)
    val backDesc = stringResource(CoreUiR.string.back)
    LaunchedEffect(Unit) {
        setToolbarConfig(
            ToolbarConfig(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {},
            ),
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
                val commands = chain.mapNotNull { it.buildCommand() }
                val name = tagName.ifBlank { chain.firstOrNull()?.shortDescription() ?: "" }
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
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (isWritingMode) {
        NfcWriteDialog(chain = chain.map { it.command.template.code.name }, onCancel = { isWritingMode = false })
    }

    if (showActionPicker) {
        ChooseActionSheet(
            categories = categories,
            onPick = { cmd ->
                chain.add(createNfcUiAction(plugin, cmd, plugin.pumpBasalDurationStep()))
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
            onValueChange = { tagName = it },
            label = { Text(stringResource(R.string.nfccommands_tag_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )

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
                        action = action,
                        profileNames = profileNames,
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
        Button(
            onClick = {
                if (tagName.isBlank()) showBlankNameDialog = true else isWritingMode = true
            },
            enabled = chain.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.nfccommands_write_tag))
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
    action: NfcUiAction,
    profileNames: List<String>,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CardDefaults.elevatedShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = action.command.icon,
                    contentDescription = null,
                    tint = action.getIconColor(),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = stringResource(action.command.displayLabelResId),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                action.EditContent(profileNames) {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChooseActionSheet(
    categories: List<NfcUiCategory>,
    onPick: (NfcUiCommand) -> Unit,
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
                    cat.commands.forEach { cmd ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                onPick(cmd)
                                onDismiss()
                            },
                            label = { Text(stringResource(cmd.displayLabelResId)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = cmd.icon,
                                    contentDescription = null,
                                    tint = getNfcUiCommandColor(cmd),
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
private fun getNfcUiCommandColor(cmd: NfcUiCommand): Color = when (cmd.template.code) {
    NfcCommandCode.LOOP_STOP -> AapsTheme.elementColors.loopDisabled
    NfcCommandCode.LOOP_RESUME, NfcCommandCode.LOOP_CLOSED -> AapsTheme.elementColors.loopClosed
    NfcCommandCode.LOOP_LGS -> AapsTheme.elementColors.loopLgs
    NfcCommandCode.LOOP_SUSPEND -> AapsTheme.elementColors.loopSuspended
    NfcCommandCode.PUMP_DISCONNECT -> AapsTheme.elementColors.loopDisconnected
    NfcCommandCode.TARGET_MEAL -> AapsTheme.elementColors.tempTarget
    NfcCommandCode.TARGET_ACTIVITY -> AapsTheme.elementColors.exercise
    NfcCommandCode.TARGET_HYPO, NfcCommandCode.TARGET_STOP -> AapsTheme.elementColors.loopDisabled
    NfcCommandCode.PROFILE_SWITCH -> Color.White
    else -> cmd.elementType.color()
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
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
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
            try {
                ndef.writeNdefMessage(message)
                true
            } finally {
                ndef.close()
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: return false
            formatable.connect()
            try {
                formatable.format(message)
                true
            } finally {
                formatable.close()
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
    val command: NfcUiCommand
    fun buildCommand(): String?
    fun shortDescription(): String
    @Composable
    fun EditContent(profileNames: List<String>, onChange: () -> Unit)

    @Composable
    fun getIconColor(): Color = getNfcUiCommandColor(command)
}

private fun createNfcUiAction(plugin: NfcCommandsPlugin, cmd: NfcUiCommand, durationStep: Int): NfcUiAction = when (cmd.argType) {
    ArgType.NONE -> SimpleNfcUiAction(cmd)
    ArgType.SUSPEND -> SuspendNfcUiAction(cmd)
    ArgType.PUMP_DISCONNECT -> PumpDisconnectNfcUiAction(cmd)
    ArgType.BOLUS -> BolusNfcUiAction(plugin, cmd)
    ArgType.BASAL_ABS -> BasalAbsNfcUiAction(cmd, durationStep)
    ArgType.BASAL_PCT -> BasalPctNfcUiAction(cmd, durationStep)
    ArgType.EXTENDED -> ExtendedNfcUiAction(cmd)
    ArgType.CARBS -> CarbsNfcUiAction(plugin, cmd)
    ArgType.PROFILE -> ProfileNfcUiAction(cmd)
}

class SimpleNfcUiAction(override val command: NfcUiCommand) : NfcUiAction {
    override fun buildCommand() = NfcTagStore.buildCommand(command.template)
    override fun shortDescription() = "" 

    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Text(
            text = stringResource(R.string.nfccommands_no_args_needed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

class SuspendNfcUiAction(override val command: NfcUiCommand) : NfcUiAction {
    var minutes by mutableIntStateOf(60)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("duration", minutes))
    override fun shortDescription() = ""

    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        NumberInputRow(
            labelResId = CoreUiR.string.duration_label,
            value = minutes.toDouble(),
            onValueChange = { minutes = it.toInt(); onChange() },
            valueRange = 30.0..480.0,
            step = 30.0,
            valueFormat = DecimalFormat("0"),
            unitLabel = stringResource(KeysR.string.units_min)
        )
    }
}

class PumpDisconnectNfcUiAction(override val command: NfcUiCommand) : NfcUiAction {
    var minutes by mutableIntStateOf(30)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("duration", minutes))
    override fun shortDescription() = ""

    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        NumberInputRow(
            labelResId = CoreUiR.string.duration_label,
            value = minutes.toDouble(),
            onValueChange = { minutes = it.toInt(); onChange() },
            valueRange = 15.0..180.0,
            step = 15.0,
            valueFormat = DecimalFormat("0"),
            unitLabel = stringResource(KeysR.string.units_min)
        )
    }
}

class BolusNfcUiAction(val plugin: NfcCommandsPlugin, override val command: NfcUiCommand) : NfcUiAction {
    var units by mutableDoubleStateOf(1.0)
    var meal by mutableStateOf(false)
    override fun buildCommand(): String {
        return NfcTagStore.buildCommand(command.template, JSONObject().put("amount", units).put("isMeal", meal))
    }
    override fun shortDescription() = ""
    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val bolusStep = plugin.activePlugin.activePump.pumpDescription.bolusStep
            NumberInputRow(
                labelResId = CoreUiR.string.overview_insulin_label,
                value = units,
                onValueChange = { units = it; onChange() },
                valueRange = 0.0..30.0,
                step = bolusStep,
                decimalPlaces = 2,
                unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname)
            )
            InsulinQuickAddButtons(
                increment1 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement1),
                increment2 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement2),
                increment3 = plugin.preferences.get(DoubleKey.OverviewInsulinButtonIncrement3),
                onAddInsulin = { units = (units + it).coerceIn(0.0, 30.0); onChange() }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = meal, onCheckedChange = { meal = it; onChange() })
                Text(stringResource(R.string.nfccommands_meal_bolus))
            }
        }
    }
}

class BasalAbsNfcUiAction(override val command: NfcUiCommand, val step: Int) : NfcUiAction {
    var rate by mutableDoubleStateOf(1.0)
    var duration by mutableIntStateOf(30)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("rate", rate).put("duration", duration))
    override fun shortDescription() = ""
    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberInputRow(
                labelResId = R.string.nfccommands_basal_rate_label,
                value = rate,
                onValueChange = { rate = it; onChange() },
                valueRange = 0.0..10.0,
                step = 0.05,
                decimalPlaces = 2,
                unitLabel = stringResource(CoreUiR.string.profile_ins_units_per_hour)
            )
            NumberInputRow(
                labelResId = CoreUiR.string.duration_label,
                value = duration.toDouble(),
                onValueChange = { duration = it.toInt(); onChange() },
                valueRange = step.toDouble()..480.0,
                step = step.toDouble(),
                valueFormat = DecimalFormat("0"),
                unitLabel = stringResource(KeysR.string.units_min)
            )
        }
    }
}

class BasalPctNfcUiAction(override val command: NfcUiCommand, val step: Int) : NfcUiAction {
    var percent by mutableIntStateOf(100)
    var duration by mutableIntStateOf(30)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("percent", percent).put("duration", duration))
    override fun shortDescription() = ""
    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberInputRow(
                labelResId = CoreUiR.string.percent,
                value = percent.toDouble(),
                onValueChange = { percent = it.toInt(); onChange() },
                valueRange = 0.0..200.0,
                step = 10.0,
                valueFormat = DecimalFormat("0"),
                unitLabel = "%"
            )
            NumberInputRow(
                labelResId = CoreUiR.string.duration_label,
                value = duration.toDouble(),
                onValueChange = { duration = it.toInt(); onChange() },
                valueRange = step.toDouble()..480.0,
                step = step.toDouble(),
                valueFormat = DecimalFormat("0"),
                unitLabel = stringResource(KeysR.string.units_min)
            )
        }
    }
}

class ExtendedNfcUiAction(override val command: NfcUiCommand) : NfcUiAction {
    var units by mutableDoubleStateOf(1.0)
    var duration by mutableIntStateOf(30)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("amount", units).put("duration", duration))
    override fun shortDescription() = ""
    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberInputRow(
                labelResId = R.string.nfccommands_extended_units,
                value = units,
                onValueChange = { units = it; onChange() },
                valueRange = 0.0..30.0,
                step = 0.1,
                decimalPlaces = 2,
                unitLabel = stringResource(CoreUiR.string.insulin_unit_shortname)
            )
            NumberInputRow(
                labelResId = CoreUiR.string.duration_label,
                value = duration.toDouble(),
                onValueChange = { duration = it.toInt(); onChange() },
                valueRange = 15.0..480.0,
                step = 15.0,
                valueFormat = DecimalFormat("0"),
                unitLabel = stringResource(KeysR.string.units_min)
            )
        }
    }
}

class CarbsNfcUiAction(val plugin: NfcCommandsPlugin, override val command: NfcUiCommand) : NfcUiAction {
    var grams by mutableIntStateOf(20)
    override fun buildCommand() = NfcTagStore.buildCommand(command.template, JSONObject().put("amount", grams))
    override fun shortDescription() = ""
    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            NumberInputRow(
                labelResId = CoreUiR.string.carbs,
                value = grams.toDouble(),
                onValueChange = { grams = it.toInt(); onChange() },
                valueRange = 0.0..200.0,
                step = 1.0,
                valueFormat = DecimalFormat("0"),
                unitLabel = stringResource(CoreUiR.string.shortgramm)
            )
            QuickAddButtons(
                increment1 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement1),
                increment2 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement2),
                increment3 = plugin.preferences.get(IntKey.OverviewCarbsButtonIncrement3),
                onAddCarbs = { grams = (grams + it).coerceIn(0, 200); onChange() }
            )
        }
    }
}

class ProfileNfcUiAction(override val command: NfcUiCommand) : NfcUiAction {
    var profileName by mutableStateOf("")
    var percent by mutableIntStateOf(100)
    override fun buildCommand(): String? {
        if (profileName.isBlank()) return null
        return NfcTagStore.buildCommand(command.template, JSONObject().put("profileName", profileName).put("percentage", percent))
    }
    override fun shortDescription() = ""

    @Composable
    override fun EditContent(profileNames: List<String>, onChange: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NfcProfileDropdown(
                value = profileName.ifEmpty { profileNames.firstOrNull() ?: "" },
                options = profileNames,
                onValueChange = { profileName = it; onChange() },
                label = stringResource(R.string.nfccommands_cat_profile)
            )
            NumberInputRow(
                labelResId = CoreUiR.string.percent,
                value = percent.toDouble(),
                onValueChange = { percent = it.toInt(); onChange() },
                valueRange = 10.0..500.0,
                step = 5.0,
                unitLabel = "%"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcProfileDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
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
                    text = { Text(opt) },
                    onClick = { onValueChange(opt); expanded = false }
                )
            }
        }
    }
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
