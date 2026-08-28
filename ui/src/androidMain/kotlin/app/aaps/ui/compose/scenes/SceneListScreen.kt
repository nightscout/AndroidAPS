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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.MasterOfflineBanner
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.core.ui.compose.navigation.label
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneListScreen(
    onNavigateToWizard: () -> Unit,
    onNavigateToEditor: (sceneId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SceneListViewModel = metroViewModel()
) {
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val activeState by viewModel.activeSceneState.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val invalidSceneIds by viewModel.invalidSceneIds.collectAsStateWithLifecycle()
    val activationReasons by viewModel.activationReasons.collectAsStateWithLifecycle()
    val editLockReasons by viewModel.editLockReasons.collectAsStateWithLifecycle()
    val masterOfflineBanner by viewModel.masterOfflineBanner.collectAsStateWithLifecycle()

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
            if (state.chainTargetName != null) {
                // Same 3-button affordance as MainViewModel.requestSceneDeactivation: primary
                // "Skip to <X>" fast-forwards to the chained scene; secondary just stops; cancel
                // dismisses. Body summarises the revert actions, identical to the 2-button path.
                ThreeButtonDialog(
                    title = stringResource(CoreUiStrings.scene_confirm_deactivate, state.sceneName),
                    message = state.revertSummaries.joinToString("\n") { "• $it" },
                    primaryLabel = stringResource(CoreUiStrings.scene_skip_to_format, state.chainTargetName),
                    onPrimary = viewModel::confirmDeactivationAndChain,
                    secondaryLabel = stringResource(CoreUiStrings.scene_deactivate),
                    onSecondary = viewModel::confirmDeactivation,
                    onDismiss = viewModel::dismissDialog
                )
            } else {
                SceneDeactivationDialog(
                    state = state,
                    onConfirm = viewModel::confirmDeactivation,
                    onDismiss = viewModel::dismissDialog
                )
            }
        }

        is SceneListViewModel.DialogState.ValidationError     -> {
            OkDialog(
                title = stringResource(CoreUiStrings.error),
                message = state.message,
                onDismiss = viewModel::dismissDialog
            )
        }

        null                                                  -> Unit
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text((stringResourceOrNull(ElementType.SCENE_MANAGEMENT.label()) ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        },
        floatingActionButton = {
            // FAB is hidden when the master is unreachable on AAPSCLIENT — same reasoning as
            // the per-card lock: a new scene definition would need to sync to master to be
            // useful, so don't surface the affordance when sync can't happen.
            if (masterOfflineBanner == null) {
                FloatingActionButton(onClick = onNavigateToWizard) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(CoreUiStrings.scene))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MasterOfflineBanner(
                editingEnabled = masterOfflineBanner == null,
                text = masterOfflineBanner ?: ""
            )
            if (scenes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AapsSpacing.xxLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(CoreUiStrings.scenes),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(CoreUiStrings.scene_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AapsSpacing.medium)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(AapsSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    items(scenes, key = { it.id }) { scene ->
                        val isActive = activeState?.scene?.id == scene.id
                        val isInvalid = scene.id in invalidSceneIds
                        val subtitle = stringResource(
                            CoreUiStrings.scene_summary,
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
                            activationReason = activationReasons[scene.id],
                            editLockReason = editLockReasons[scene.id],
                            masterReachable = masterOfflineBanner == null,
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
}

/**
 * @see SceneCardNormalPreview
 * @see SceneCardActivePreview
 * @see SceneCardInvalidPreview
 */
@Composable
internal fun SceneCard(
    scene: Scene,
    subtitle: String,
    isActive: Boolean,
    isInvalid: Boolean = false,
    chainTargetName: String? = null,
    chainMissing: Boolean = false,
    activationReason: String? = null,
    editLockReason: String? = null,
    masterReachable: Boolean = true,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit = {}
) {
    val editEnabled = editLockReason == null
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
                onCheckedChange = { onToggleEnabled() },
                enabled = editEnabled
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
                        text = stringResource(CoreUiStrings.scene_chain_target_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    chainTargetName != null -> Text(
                        text = stringResource(CoreUiStrings.scene_chain_indicator, chainTargetName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Surface the activation gate reason (pump disconnected / no profile / etc.)
                // so the user understands why the play button is disabled.
                if (activationReason != null && !isActive) {
                    Text(
                        text = activationReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Surface the edit-lock reason (running scene / master offline) so the user understands why the
                // edit/delete/checkbox actions are disabled — unless it just repeats the activation reason already
                // shown above (a global master block sets both gates to the same text → would otherwise double up).
                val editReasonDuplicated = activationReason != null && !isActive && editLockReason == activationReason
                if (editLockReason != null && !editReasonDuplicated) {
                    Text(
                        text = editLockReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                if (isActive) {
                    IconButton(onClick = onDeactivate, enabled = masterReachable) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(CoreUiStrings.scene_deactivate))
                    }
                } else {
                    IconButton(
                        onClick = onActivate,
                        enabled = scene.isEnabled && activationReason == null
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(CoreUiStrings.scene_activate))
                    }
                }
                IconButton(onClick = onEdit, enabled = editEnabled) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
                if (scene.isDeletable) {
                    IconButton(onClick = onDelete, enabled = editEnabled) {
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
            Text(stringResource(CoreUiStrings.scene_confirm_activate, state.scene.name))
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
                Text(stringResource(CoreUiStrings.scene_activate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.cancel))
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
            Text(stringResource(CoreUiStrings.scene_confirm_deactivate, state.sceneName))
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
                Text(stringResource(CoreUiStrings.scene_deactivate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiStrings.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    )
}

// --- Previews ---
