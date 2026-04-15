package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.compose.actions.ActionEditor

@Composable
fun AutomationEditScreen(
    state: AutomationEditUiState,
    liveActions: List<Action>,
    profileNames: List<String>,
    tick: Int,
    onTitleChange: (String) -> Unit,
    onUserActionChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEditTrigger: () -> Unit,
    onAddAction: () -> Unit,
    onRemoveAction: (index: Int) -> Unit,
    onActionChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_EXPRESSION") tick
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (state.readOnly) ReadOnlyChip()
            BasicsSection(state, onTitleChange, onUserActionChange, onEnabledChange)
            ConditionSection(state, onEditTrigger)
            ActionsSection(
                liveActions = liveActions,
                readOnly = state.readOnly,
                profileNames = profileNames,
                onRemoveAction = onRemoveAction,
                onAddAction = onAddAction,
                onActionChanged = onActionChanged
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReadOnlyChip() {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(R.string.automation_read_only)) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun BasicsSection(
    state: AutomationEditUiState,
    onTitleChange: (String) -> Unit,
    onUserActionChange: (Boolean) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.taskname)) },
            singleLine = true,
            isError = state.titleError,
            enabled = !state.readOnly,
            supportingText = if (state.titleError) {
                { Text(stringResource(R.string.automation_missing_task_name)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.user_action)) },
                trailingContent = {
                    Switch(
                        checked = state.userAction,
                        onCheckedChange = onUserActionChange,
                        enabled = !state.readOnly
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.automation_enabled)) },
                trailingContent = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onEnabledChange,
                        enabled = !state.readOnly
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun ConditionSection(
    state: AutomationEditUiState,
    onEditTrigger: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.condition).trimEnd(':'))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.hasTrigger) {
                    Text(
                        text = state.triggerDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.automation_missing_trigger),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!state.readOnly) {
                    OutlinedButton(onClick = onEditTrigger) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.edit_short))
                    }
                }
            }
        }
        if (state.preconditionsDescription.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CardDefaults.elevatedShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.preconditions).trimEnd(':'),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = state.preconditionsDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsSection(
    liveActions: List<Action>,
    readOnly: Boolean,
    profileNames: List<String>,
    onRemoveAction: (Int) -> Unit,
    onAddAction: () -> Unit,
    onActionChanged: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.action).trimEnd(':'))
        if (liveActions.isEmpty()) {
            Text(
                text = stringResource(R.string.automation_missing_action),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        } else {
            liveActions.forEachIndexed { index, action ->
                val full = action.shortDescription().ifEmpty { action.javaClass.simpleName }
                val headerTitle = full.substringBefore(':').trim().ifEmpty { full }
                InlineActionCard(
                    action = action,
                    valid = action.isValid(),
                    shortDescription = headerTitle,
                    readOnly = readOnly,
                    profileNames = profileNames,
                    onRemove = { onRemoveAction(index) },
                    onChange = onActionChanged
                )
            }
        }
        if (!readOnly) {
            OutlinedButton(
                onClick = onAddAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.add_short))
            }
        }
    }
}

@Composable
private fun InlineActionCard(
    action: Action,
    valid: Boolean,
    shortDescription: String,
    readOnly: Boolean,
    profileNames: List<String>,
    onRemove: () -> Unit,
    onChange: () -> Unit
) {
    val containerColor =
        if (valid) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.errorContainer
    Surface(
        color = containerColor,
        shape = CardDefaults.elevatedShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                val icon = action.composeIcon()
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = action.composeIconTint() ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                androidx.compose.foundation.layout.Box(modifier = Modifier.size(6.dp))
                Text(
                    text = shortDescription,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (!readOnly) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (!readOnly) {
                ActionEditor(
                    action = action,
                    profileNames = profileNames,
                    onChange = onChange
                )
            }
        }
    }
}

// ---------- Previews ----------

private fun sampleEditState(
    readOnly: Boolean = false,
    hasTrigger: Boolean = true,
    actions: Int = 2,
    userAction: Boolean = false
) = AutomationEditUiState(
    title = if (readOnly) "System wakeup" else "Morning TT",
    userAction = userAction,
    enabled = true,
    readOnly = readOnly,
    triggerDescription = if (hasTrigger) "BG < 4 mmol/L AND Delta < -0.1 mmol/L" else "",
    hasTrigger = hasTrigger,
    preconditionsDescription = if (hasTrigger) "Loop running AND profile active" else "",
    actions = (0 until actions).map { i ->
        AutomationActionUi(
            index = i,
            title = if (i == 0) "Start temp target 8 mmol/L for 60 min" else "Send notification",
            icon = IcAutomation,
            valid = i != actions - 1 || actions == 1
        )
    },
    titleError = false
)

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun PreviewAutomationEditScreenNew() {
    MaterialTheme {
        AutomationEditScreen(
            state = AutomationEditUiState(),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun PreviewAutomationEditScreenEdit() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun PreviewAutomationEditScreenReadOnly() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(readOnly = true, actions = 1),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun PreviewAutomationEditScreenUserAction() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(hasTrigger = false, userAction = true, actions = 1),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            tick = 0
        )
    }
}
