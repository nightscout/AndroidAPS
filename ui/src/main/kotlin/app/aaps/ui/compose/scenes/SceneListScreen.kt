package app.aaps.ui.compose.scenes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TT
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.labelResId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneListScreen(
    onNavigateToWizard: () -> Unit,
    onNavigateToEditor: (sceneId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SceneListViewModel = hiltViewModel()
) {
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val activeState by viewModel.activeSceneState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val invalidSceneIds by viewModel.invalidSceneIds.collectAsStateWithLifecycle()

    // Dialog handling
    when (val state = dialogState) {
        is SceneListViewModel.DialogState.ConfirmActivation   -> {
            SceneActivationDialog(
                state = state,
                onConfirm = viewModel::confirmActivation,
                onDismiss = viewModel::dismissDialog
            )
        }

        is SceneListViewModel.DialogState.ConfirmDeactivation -> {
            SceneDeactivationDialog(
                state = state,
                onConfirm = viewModel::confirmDeactivation,
                onDismiss = viewModel::dismissDialog
            )
        }

        is SceneListViewModel.DialogState.ValidationError     -> {
            OkDialog(
                title = stringResource(R.string.error),
                message = state.message,
                onDismiss = viewModel::dismissDialog
            )
        }

        null                                                  -> Unit
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(ElementType.SCENE_MANAGEMENT.labelResId())) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToWizard) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.scene))
            }
        }
    ) { paddingValues ->
        if (scenes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(AapsSpacing.xxLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.scenes),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.scene_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AapsSpacing.medium)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(AapsSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                items(scenes, key = { it.id }) { scene ->
                    val isActive = activeState?.scene?.id == scene.id
                    val isInvalid = scene.id in invalidSceneIds
                    val subtitle = stringResource(
                        R.string.scene_summary,
                        scene.actions.size,
                        viewModel.formatMinutes(scene.defaultDurationMinutes)
                    )
                    val chainTargetId = (scene.endAction as? SceneEndAction.ChainScene)?.sceneId
                    val chainTargetName = chainTargetId?.let { id -> scenes.firstOrNull { it.id == id }?.name }
                    SceneCard(
                        scene = scene,
                        subtitle = subtitle,
                        isActive = isActive,
                        isInvalid = isInvalid,
                        chainTargetName = chainTargetName,
                        chainMissing = chainTargetId != null && chainTargetName == null,
                        onActivate = { viewModel.requestActivation(scene) },
                        onDeactivate = { viewModel.requestDeactivation() },
                        onEdit = { onNavigateToEditor(scene.id) },
                        onDelete = { viewModel.deleteScene(scene.id) },
                        onToggleEnabled = { viewModel.toggleEnabled(scene.id) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SceneCard(
    scene: Scene,
    subtitle: String,
    isActive: Boolean,
    isInvalid: Boolean = false,
    chainTargetName: String? = null,
    chainMissing: Boolean = false,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit = {}
) {
    val nameColor = when {
        isInvalid -> MaterialTheme.colorScheme.error
        isActive  -> AapsTheme.elementColors.scene
        else      -> MaterialTheme.colorScheme.onSurface
    }
    val iconTint = when {
        isInvalid -> MaterialTheme.colorScheme.error
        isActive  -> AapsTheme.elementColors.scene
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = scene.isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
            Icon(
                imageVector = SceneIcons.fromKey(scene.icon).icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scene.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = nameColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInvalid) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    chainMissing            -> Text(
                        text = stringResource(R.string.scene_chain_target_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    chainTargetName != null -> Text(
                        text = stringResource(R.string.scene_chain_indicator, chainTargetName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                if (isActive) {
                    IconButton(onClick = onDeactivate) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.scene_deactivate))
                    }
                } else {
                    IconButton(onClick = onActivate, enabled = scene.isEnabled) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.scene_activate))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
                if (scene.isDeletable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }
    }
}

// --- Dialogs ---

@Composable
private fun SceneActivationDialog(
    state: SceneListViewModel.DialogState.ConfirmActivation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.scene_confirm_activate, state.scene.name))
        },
        text = {
            Column {
                // Action summaries
                state.actionSummaries.forEach { summary ->
                    Text(
                        text = "\u2022 $summary",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = AapsSpacing.extraSmall)
                    )
                }

                // Conflicts in accent color
                if (state.conflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AapsSpacing.large))
                    state.conflicts.forEach { conflict ->
                        Text(
                            text = "\u26A0 $conflict",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = AapsSpacing.extraSmall)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.scene_activate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

@Composable
private fun SceneDeactivationDialog(
    state: SceneListViewModel.DialogState.ConfirmDeactivation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.scene_confirm_deactivate, state.sceneName))
        },
        text = {
            Column {
                state.revertSummaries.forEach { summary ->
                    Text(
                        text = "\u2022 $summary",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = AapsSpacing.extraSmall)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.scene_deactivate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun SceneCardNormalPreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "1",
                    name = "Exercise",
                    icon = "exercise",
                    defaultDurationMinutes = 60,
                    actions = listOf(
                        SceneAction.TempTarget(reason = TT.Reason.ACTIVITY, targetMgdl = 140.0),
                        SceneAction.SmbToggle(enabled = false)
                    )
                ),
                subtitle = "2 actions, 1 hour",
                isActive = false,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SceneCardActivePreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "2",
                    name = "Sick Day",
                    icon = "sick",
                    defaultDurationMinutes = 480,
                    actions = listOf(
                        SceneAction.TempTarget(reason = TT.Reason.CUSTOM, targetMgdl = 120.0)
                    )
                ),
                subtitle = "1 actions, 8 hours",
                isActive = true,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SceneCardInvalidPreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "3",
                    name = "Broken Scene",
                    icon = "star",
                    defaultDurationMinutes = 60,
                    actions = listOf(
                        SceneAction.ProfileSwitch(profileName = "Deleted Profile")
                    )
                ),
                subtitle = "1 actions, 1 hour",
                isActive = false,
                isInvalid = true,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}
