package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.triggers.TriggerEditor
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerLocation

/**
 * Compose trigger-tree editor. Mutates [root] in place; callers bump [onChange]
 * at the screen level when they need to re-sync a description (not required for
 * correctness since mutations take effect immediately).
 *
 * @param availableTriggers dummy trigger prototypes used by the chooser sheet.
 * @param createTrigger builds a new [Trigger] for a picked prototype's class name.
 * @param newConnector creates an empty nested group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditTriggerScreen(
    root: TriggerConnector,
    availableTriggers: List<Trigger>,
    createTrigger: (className: String) -> Trigger?,
    newConnector: () -> TriggerConnector,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    bondedDevices: List<String> = emptyList(),
    onPickLocationFromMap: (TriggerLocation) -> Unit = {}
) {
    var tick by remember { mutableIntStateOf(0) }
    fun bump() {
        tick++; onChange()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectorCard(
                connector = root,
                depth = 0,
                tick = tick,
                availableTriggers = availableTriggers,
                createTrigger = createTrigger,
                newConnector = newConnector,
                bondedDevices = bondedDevices,
                onPickLocationFromMap = onPickLocationFromMap,
                onChange = ::bump,
                isRoot = true,
                onRemoveSelf = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConnectorCard(
    connector: TriggerConnector,
    depth: Int,
    tick: Int,
    availableTriggers: List<Trigger>,
    createTrigger: (String) -> Trigger?,
    newConnector: () -> TriggerConnector,
    bondedDevices: List<String>,
    onPickLocationFromMap: (TriggerLocation) -> Unit,
    onChange: () -> Unit,
    isRoot: Boolean,
    onRemoveSelf: () -> Unit
) {
    @Suppress("UNUSED_EXPRESSION") tick // force recomposition on mutations
    var showSheet by remember { mutableStateOf(false) }
    val railColor = when (depth % 4) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    val indentStart = if (isRoot) 0.dp else 20.dp
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentStart)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left rail indicating depth
            Surface(
                color = railColor,
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TriggerConnector.Type.entries.forEach { type ->
                            FilterChip(
                                selected = connector.currentType() == type,
                                onClick = { connector.setType(type); onChange() },
                                label = { Text(stringResource(type.stringRes)) }
                            )
                        }
                    }
                    if (!isRoot) {
                        IconButton(onClick = onRemoveSelf) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
                connector.list.toList().forEachIndexed { index, child ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    if (child is TriggerConnector) {
                        ConnectorCard(
                            connector = child,
                            depth = depth + 1,
                            tick = tick,
                            availableTriggers = availableTriggers,
                            createTrigger = createTrigger,
                            newConnector = newConnector,
                            bondedDevices = bondedDevices,
                            onPickLocationFromMap = onPickLocationFromMap,
                            onChange = onChange,
                            isRoot = false,
                            onRemoveSelf = {
                                val i = connector.list.indexOf(child)
                                if (i >= 0) connector.list.removeAt(i)
                                onChange()
                            }
                        )
                    } else {
                        LeafCard(
                            trigger = child,
                            tick = tick,
                            bondedDevices = bondedDevices,
                            onPickLocationFromMap = onPickLocationFromMap,
                            onRemove = {
                                val i = connector.list.indexOf(child)
                                if (i >= 0) connector.list.removeAt(i)
                                onChange()
                            },
                            onChange = onChange
                        )
                    }
                    @Suppress("UNUSED_EXPRESSION") index
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showSheet = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.automation_choose_trigger), modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = {
                        connector.list.add(newConnector())
                        onChange()
                    }) {
                        Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.automation_add_group),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        val options = remember(availableTriggers) { availableTriggers.map { TriggerOption.from(it) } }
        ChooseTriggerSheet(
            options = options,
            onPick = { opt ->
                createTrigger(opt.className)?.let {
                    connector.list.add(it)
                    onChange()
                }
            },
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
private fun LeafCard(
    trigger: Trigger,
    tick: Int,
    bondedDevices: List<String>,
    onPickLocationFromMap: (TriggerLocation) -> Unit,
    onRemove: () -> Unit,
    onChange: () -> Unit
) {
    @Suppress("UNUSED_EXPRESSION") tick // force re-execution when a sibling field mutates
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = CardDefaults.elevatedShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = trigger.composeIcon()
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = trigger.composeIconTint() ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(trigger.friendlyName()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            TriggerEditor(
                trigger = trigger,
                onChange = onChange,
                tick = tick,
                bondedDevices = bondedDevices,
                onPickLocationFromMap = onPickLocationFromMap
            )
        }
    }
}

/**
 * [TriggerConnector] exposes setType but no getter. Map friendlyName (== type.stringRes)
 * back to the enum value.
 */
private fun TriggerConnector.currentType(): TriggerConnector.Type {
    val nameRes = friendlyName()
    return TriggerConnector.Type.entries.firstOrNull { it.stringRes == nameRes } ?: TriggerConnector.Type.AND
}
