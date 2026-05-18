package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.main.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcBuildScreen(
    plugin: NfcCommandsPlugin,
    setToolbarConfig: (ToolbarConfig) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val categories = remember { NfcCategories.build() }

    var selectedCategoryIndex by remember { mutableIntStateOf(-1) }
    var selectedCommandIndex by remember { mutableIntStateOf(-1) }
    val chain = remember { mutableStateListOf<String>() }
    var tagName by remember { mutableStateOf("") }
    var isWritingMode by remember { mutableStateOf(false) }
    var writeResult by remember { mutableStateOf<String?>(null) }
    var showBlankNameDialog by remember { mutableStateOf(false) }

    var suspendMinutes by remember { mutableIntStateOf(60) }
    var pumpDisconnectMinutes by remember { mutableIntStateOf(30) }
    var bolusUnits by remember { mutableDoubleStateOf(1.0) }
    var mealBolus by remember { mutableStateOf(false) }
    var basalAbsRate by remember { mutableDoubleStateOf(1.0) }
    var basalAbsDuration by remember { mutableIntStateOf(30) }
    var basalPct by remember { mutableIntStateOf(100) }
    var basalPctDuration by remember { mutableIntStateOf(30) }
    var extendedUnits by remember { mutableDoubleStateOf(1.0) }
    var extendedDuration by remember { mutableIntStateOf(30) }
    var carbsGrams by remember { mutableIntStateOf(20) }
    var profileIndex by remember { mutableIntStateOf(1) }
    var profileWithPct by remember { mutableStateOf(false) }
    var profilePct by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        val step = plugin.pumpBasalDurationStep()
        basalAbsDuration = snapToStep(basalAbsDuration, step)
        basalPctDuration = snapToStep(basalPctDuration, step)
    }

    val title = stringResource(R.string.nfccommands_write_tag)
    val backDesc = stringResource(app.aaps.core.ui.R.string.back)
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
            )
        )
    }

    val selectedCommand = if (selectedCategoryIndex >= 0 && selectedCommandIndex >= 0)
        categories[selectedCategoryIndex].commands.getOrNull(selectedCommandIndex) else null

    val previewText = selectedCommand?.let {
        buildCurrentCommand(
            it, suspendMinutes, pumpDisconnectMinutes, bolusUnits, mealBolus,
            basalAbsRate, basalAbsDuration, basalPct, basalPctDuration,
            extendedUnits, extendedDuration, carbsGrams, profileIndex, profileWithPct, profilePct,
        )
    }

    // NFC reader mode for writing
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(isWritingMode) {
        if (!isWritingMode) return@DisposableEffect onDispose {}
        val activity = context as? Activity ?: return@DisposableEffect onDispose {}
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            ?: return@DisposableEffect onDispose {}

        val callback = NfcAdapter.ReaderCallback { tag ->
            val uid = NfcTagStore.tagUidHex(tag.id) ?: return@ReaderCallback
            val commands = chain.toList()
            val name = tagName.ifBlank { commands.firstOrNull() ?: "" }
            val alreadyAssigned = NfcTagStore.findTagByUid(context, uid) != null
            val success = if (alreadyAssigned) true else buildAndWriteNdef(tag, plugin)
            val message = when {
                !success        -> context.getString(R.string.nfccommands_tag_write_error)
                alreadyAssigned -> context.getString(R.string.nfccommands_tag_reassigned)
                else            -> context.getString(R.string.nfccommands_tag_written)
            }

            NfcTagStore.appendLogEntry(
                context,
                NfcLogEntry(
                    timestamp = System.currentTimeMillis(),
                    tagName = name,
                    action = "WRITE",
                    success = success,
                    message = message,
                ),
            )
            if (success) {
                NfcTagStore.saveCreatedTag(
                    context,
                    NfcCreatedTag(
                        tagUid = uid,
                        name = name,
                        commands = commands,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                NfcTagStore.markJustWritten(uid)
            }
            coroutineScope.launch(Dispatchers.Main) {
                writeResult = message
                isWritingMode = false
                if (success) chain.clear()
            }
        }
        nfcAdapter.enableReaderMode(
            activity, callback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null,
        )
        onDispose { nfcAdapter.disableReaderMode(activity) }
    }

    // Write result toast replacement: show as a dialog
    writeResult?.let { result ->
        AlertDialog(
            onDismissRequest = { writeResult = null },
            title = { Text(result) },
            confirmButton = {
                TextButton(onClick = { writeResult = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }

    // Blank tag name confirmation
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

    // NFC write dialog with animated rings
    if (isWritingMode) {
        NfcWriteDialog(
            chain = chain,
            onCancel = { isWritingMode = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (chain.isNotEmpty()) 80.dp else 0.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Tag name
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text(stringResource(R.string.nfccommands_tag_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            // Category chips
            Text(
                text = stringResource(R.string.nfccommands_select_category),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categories.forEachIndexed { index, category ->
                        FilterChip(
                            selected = selectedCategoryIndex == index,
                            onClick = {
                                if (selectedCategoryIndex != index) {
                                    selectedCategoryIndex = index
                                    selectedCommandIndex = -1
                                }
                            },
                            label = { Text(stringResource(category.labelResId)) },
                        )
                    }
                    if (selectedCategoryIndex >= 0) {
                        val category = categories[selectedCategoryIndex]
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = stringResource(R.string.nfccommands_doc_info),
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(20.dp)
                                .clickable {
                                    val url = context.getString(R.string.nfccommands_doc_base_url) +
                                        context.getString(category.docAnchorResId)
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                                    )
                                },
                        )
                    }
                }
            }

            // Command list
            if (selectedCategoryIndex >= 0) {
                val category = categories[selectedCategoryIndex]
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    category.commands.forEachIndexed { index, command ->
                        val isSelected = selectedCommandIndex == index
                        Surface(
                            onClick = {
                                selectedCommandIndex = index
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(command.displayLabelResId),
                                style = if (isSelected) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                else MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // Arg panel
            if (selectedCommand != null) {
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        when (selectedCommand.argType) {
                            ArgType.NONE -> {
                                Text(
                                    text = stringResource(R.string.nfccommands_no_args_needed),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }

                            ArgType.SUSPEND -> {
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_suspend_minutes, suspendMinutes),
                                    onMinus = { suspendMinutes = maxOf(30, suspendMinutes - 30) },
                                    onPlus = { suspendMinutes = minOf(480, suspendMinutes + 30) },
                                )
                                PresetRow(
                                    presets = listOf(30 to R.string.nfccommands_duration_30m, 60 to R.string.nfccommands_duration_1h,
                                        120 to R.string.nfccommands_duration_2h, 240 to R.string.nfccommands_duration_4h,
                                        480 to R.string.nfccommands_duration_8h),
                                    onSelect = { suspendMinutes = it },
                                )
                            }

                            ArgType.PUMP_DISCONNECT -> {
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_pump_disconnect_minutes, pumpDisconnectMinutes),
                                    onMinus = { pumpDisconnectMinutes = maxOf(15, pumpDisconnectMinutes - 15) },
                                    onPlus = { pumpDisconnectMinutes = minOf(180, pumpDisconnectMinutes + 15) },
                                )
                                PresetRow(
                                    presets = listOf(15 to R.string.nfccommands_duration_15m, 30 to R.string.nfccommands_duration_30m,
                                        60 to R.string.nfccommands_duration_1h, 120 to R.string.nfccommands_duration_2h,
                                        180 to R.string.nfccommands_duration_3h),
                                    onSelect = { pumpDisconnectMinutes = it },
                                )
                            }

                            ArgType.BOLUS -> {
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_bolus_units, bolusUnits),
                                    onMinus = { bolusUnits = maxOf(0.05, bolusUnits - 0.05).roundTo2() },
                                    onPlus = { bolusUnits = minOf(30.0, bolusUnits + 0.05).roundTo2() },
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = mealBolus, onCheckedChange = { mealBolus = it })
                                    Text(stringResource(R.string.nfccommands_meal_bolus))
                                }
                            }

                            ArgType.BASAL_ABS -> {
                                val step = plugin.pumpBasalDurationStep()
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_basal_abs_value).format(basalAbsRate),
                                    onMinus = { basalAbsRate = maxOf(0.05, basalAbsRate - 0.05).roundTo2() },
                                    onPlus = { basalAbsRate = minOf(30.0, basalAbsRate + 0.05).roundTo2() },
                                )
                                StepperRow(
                                    label = basalAbsDuration.toString(),
                                    onMinus = { basalAbsDuration = maxOf(step, basalAbsDuration - step) },
                                    onPlus = { basalAbsDuration = minOf(480, basalAbsDuration + step) },
                                )
                            }

                            ArgType.BASAL_PCT -> {
                                val step = plugin.pumpBasalDurationStep()
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_percent_value, basalPct),
                                    onMinus = { basalPct = maxOf(0, basalPct - 10) },
                                    onPlus = { basalPct = minOf(200, basalPct + 10) },
                                )
                                StepperRow(
                                    label = basalPctDuration.toString(),
                                    onMinus = { basalPctDuration = maxOf(step, basalPctDuration - step) },
                                    onPlus = { basalPctDuration = minOf(480, basalPctDuration + step) },
                                )
                            }

                            ArgType.EXTENDED -> {
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_bolus_units, extendedUnits),
                                    onMinus = { extendedUnits = maxOf(0.05, extendedUnits - 0.05).roundTo2() },
                                    onPlus = { extendedUnits = minOf(30.0, extendedUnits + 0.05).roundTo2() },
                                )
                                StepperRow(
                                    label = extendedDuration.toString(),
                                    onMinus = { extendedDuration = maxOf(15, extendedDuration - 15) },
                                    onPlus = { extendedDuration = minOf(480, extendedDuration + 15) },
                                )
                            }

                            ArgType.CARBS -> {
                                StepperRow(
                                    label = stringResource(R.string.nfccommands_carbs_value, carbsGrams),
                                    onMinus = { carbsGrams = maxOf(5, carbsGrams - 5) },
                                    onPlus = { carbsGrams = minOf(500, carbsGrams + 5) },
                                )
                            }

                            ArgType.PROFILE -> {
                                StepperRow(
                                    label = profileIndex.toString(),
                                    onMinus = { profileIndex = maxOf(1, profileIndex - 1) },
                                    onPlus = { profileIndex = minOf(20, profileIndex + 1) },
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = profileWithPct, onCheckedChange = { profileWithPct = it })
                                    Text(stringResource(R.string.nfccommands_profile_percent))
                                }
                                if (profileWithPct) {
                                    StepperRow(
                                        label = stringResource(R.string.nfccommands_percent_value, profilePct),
                                        onMinus = { profilePct = maxOf(70, profilePct - 5) },
                                        onPlus = { profilePct = minOf(200, profilePct + 5) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Preview + Add to chain
            if (previewText != null) {
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.nfccommands_preview_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                        Button(
                            onClick = { chain.add(previewText) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(R.string.nfccommands_add_to_chain))
                        }
                    }
                }
            }

            // Chain list (non-scrollable — inside a verticalScroll parent)
            if (chain.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.nfccommands_chain_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                ChainList(
                    chain = chain,
                    onMove = { from, to -> chain.add(to, chain.removeAt(from)) },
                    onRemove = { chain.removeAt(it) },
                )
            }
        }

        // Write FAB
        if (chain.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (tagName.isBlank()) showBlankNameDialog = true else isWritingMode = true
                },
                text = { Text(stringResource(R.string.nfccommands_register_tag)) },
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ChainList(
    chain: List<String>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }
    val context = LocalContext.current
    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxWidth()
            .height((chain.size * 56).dp.coerceAtMost(280.dp)),
    ) {
        itemsIndexed(chain, key = { index, _ -> index }) { index, item ->
            ReorderableItem(reorderableState, key = index) { isDragging ->
                val elevation = if (isDragging) 4.dp else 0.dp
                Surface(shadowElevation = elevation, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = null,
                            modifier = Modifier
                                .draggableHandle()
                                .padding(horizontal = 8.dp),
                        )
                        Text(
                            text = context.getString(R.string.nfccommands_cascade_step_label, index + 1, item),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                        }
                    }
                }
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
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 0), RepeatMode.Restart),
        label = "ring1",
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 200), RepeatMode.Restart),
        label = "ring2",
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = InfiniteRepeatableSpec(tween(900, delayMillis = 400), RepeatMode.Restart),
        label = "ring3",
    )

    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.nfccommands_write_ready), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val ringColor = MaterialTheme.colorScheme.primary
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(120.dp)
                            .scale(ring3Scale)
                            .alpha(1f - ring3Scale),
                    ) {}
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(ring2Scale)
                            .alpha(1f - ring2Scale),
                    ) {}
                    Surface(
                        shape = CircleShape,
                        border = BorderStroke(2.dp, ringColor),
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(ring1Scale)
                            .alpha(1f - ring1Scale),
                    ) {}
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = chain.mapIndexed { i, cmd ->
                        context.getString(R.string.nfccommands_cascade_step_label, i + 1, cmd)
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

@Composable
private fun StepperRow(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−")
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+")
        }
    }
}

@Composable
private fun PresetRow(
    presets: List<Pair<Int, Int>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        presets.forEach { (value, labelResId) ->
            FilterChip(
                selected = false,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelResId)) },
            )
        }
    }
}

private fun buildCurrentCommand(
    command: NfcUiCommand,
    suspendMinutes: Int,
    pumpDisconnectMinutes: Int,
    bolusUnits: Double,
    mealBolus: Boolean,
    basalAbsRate: Double,
    basalAbsDuration: Int,
    basalPct: Int,
    basalPctDuration: Int,
    extendedUnits: Double,
    extendedDuration: Int,
    carbsGrams: Int,
    profileIndex: Int,
    profileWithPct: Boolean,
    profilePct: Int,
): String? = when (command.argType) {
    ArgType.NONE -> NfcTagStore.buildCommand(command.template, "")
    ArgType.SUSPEND -> NfcTagStore.buildCommand(command.template, suspendMinutes.toString())
    ArgType.PUMP_DISCONNECT -> NfcTagStore.buildCommand(command.template, pumpDisconnectMinutes.toString())
    ArgType.BOLUS -> {
        val args = if (mealBolus) "%.2f MEAL".format(bolusUnits) else "%.2f".format(bolusUnits)
        NfcTagStore.buildCommand(command.template, args)
    }
    ArgType.BASAL_ABS -> NfcTagStore.buildCommand(command.template, "%.2f %d".format(basalAbsRate, basalAbsDuration))
    ArgType.BASAL_PCT -> NfcTagStore.buildCommand(command.template, "$basalPct% $basalPctDuration")
    ArgType.EXTENDED -> NfcTagStore.buildCommand(command.template, "%.2f %d".format(extendedUnits, extendedDuration))
    ArgType.CARBS -> NfcTagStore.buildCommand(command.template, carbsGrams.toString())
    ArgType.PROFILE -> {
        val args = if (profileWithPct) "$profileIndex $profilePct" else profileIndex.toString()
        NfcTagStore.buildCommand(command.template, args)
    }
}

private fun buildAndWriteNdef(tag: Tag, plugin: NfcCommandsPlugin): Boolean {
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

private fun snapToStep(value: Int, step: Int): Int =
    if (value % step == 0) maxOf(step, value) else maxOf(step, ((value / step) + 1) * step)

private fun Double.roundTo2() = Math.round(this * 100) / 100.0
