package app.aaps.plugins.automation.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.actions.ActionOption
import app.aaps.plugins.automation.compose.actions.ChooseActionSheet
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.full.primaryConstructor

class AutomationComposeContent(
    private val plugin: AutomationPlugin,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val injector: HasAndroidInjector,
    private val uel: UserEntryLogger,
    @Suppress("unused") private val rh: ResourceHelper,
    private val localProfileManager: app.aaps.core.interfaces.profile.LocalProfileManager,
    private val sceneApi: SceneAutomationApi
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val holder = remember {
            AutomationStateHolder(plugin, rxBus, aapsSchedulers, fabricPrivacy, injector)
        }
        DisposableEffect(holder) {
            holder.start()
            onDispose { holder.stop() }
        }

        val route by holder.route.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val activity = context as? FragmentActivity
        val ioScope = rememberCoroutineScope()

        when (route) {
            is AutomationRoute.List        -> ListRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig,
                onNavigateBack = onNavigateBack,
                onSettings = onSettings,
                onRun = { ioScope.launch(Dispatchers.IO) { plugin.processActions() } },
                activity = activity
            )

            is AutomationRoute.Edit        -> EditRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig,
                activity = activity
            )

            is AutomationRoute.EditTrigger -> EditTriggerRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig
            )

            is AutomationRoute.MapPicker   -> MapPickerRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig,
                route = route as AutomationRoute.MapPicker
            )
        }
    }

    @Composable
    private fun ListRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?,
        onRun: () -> Unit,
        activity: FragmentActivity?
    ) {
        val state by holder.state.collectAsStateWithLifecycle()
        var showRemoveConfirm by remember { mutableStateOf(false) }

        val title = stringResource(R.string.automation)
        val backDesc = stringResource(app.aaps.core.ui.R.string.back)
        val settingsDesc = stringResource(app.aaps.core.ui.R.string.nav_plugin_preferences)
        val selectionMode = state.selectionMode
        val selectedCount = state.events.count { it.isSelected }

        LaunchedEffect(selectionMode, selectedCount) {
            setToolbarConfig(
                buildListToolbar(
                    title = title,
                    backDesc = backDesc,
                    settingsDesc = settingsDesc,
                    selectionMode = selectionMode,
                    selectedCount = selectedCount,
                    onBack = onNavigateBack,
                    onExitSelection = { holder.exitSelection() },
                    onRun = onRun,
                    onStartRemove = { holder.enterRemoveMode() },
                    onStartSort = { holder.enterSortMode() },
                    onConfirmRemove = { if (selectedCount > 0) showRemoveConfirm = true else holder.exitSelection() },
                    onSettings = onSettings
                )
            )
        }

        AutomationScreen(
            state = state,
            onToggleEnabled = holder::toggleEnabled,
            onClickEvent = { pos ->
                when (selectionMode) {
                    AutomationSelectionMode.None   -> holder.openEdit(pos)

                    AutomationSelectionMode.Remove -> {
                        val current = state.events.firstOrNull { it.position == pos }?.isSelected == true
                        holder.toggleSelection(pos, !current)
                    }

                    AutomationSelectionMode.Sort   -> Unit
                }
            },
            onLongClickEvent = {
                if (selectionMode == AutomationSelectionMode.None) holder.enterRemoveMode()
            },
            onToggleSelect = holder::toggleSelection,
            onMove = holder::move,
            onMoveFinished = holder::commitMove,
            onAddClick = { holder.openNew() }
        )

        if (showRemoveConfirm) {
            val targets = holder.selectedEvents()
            val message = if (targets.size == 1)
                stringResource(app.aaps.core.ui.R.string.removerecord) + " " + targets.first().title
            else
                stringResource(app.aaps.core.ui.R.string.confirm_remove_multiple_items, targets.size)
            AlertDialog(
                onDismissRequest = { showRemoveConfirm = false },
                title = { Text(stringResource(app.aaps.core.ui.R.string.removerecord)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        targets.forEach { uel.log(Action.AUTOMATION_REMOVED, Sources.Automation, it.title) }
                        holder.removeSelected()
                        showRemoveConfirm = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirm = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
        // silence unused activity
        @Suppress("UNUSED_EXPRESSION") activity
    }

    @Composable
    private fun EditTriggerRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit
    ) {
        val backDesc = stringResource(app.aaps.core.ui.R.string.back)
        val saveDesc = stringResource(app.aaps.core.ui.R.string.save)
        val title = stringResource(R.string.condition).trimEnd(':')
        val dirty by holder.triggerDirty.collectAsStateWithLifecycle()
        val resetTick by holder.triggerResetTick.collectAsStateWithLifecycle()
        var showDiscardConfirm by remember { mutableStateOf(false) }

        val attemptClose: () -> Unit = {
            if (dirty) showDiscardConfirm = true else holder.closeTriggerEditor()
        }

        androidx.activity.compose.BackHandler { attemptClose() }

        LaunchedEffect(dirty) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = attemptClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                        }
                    },
                    actions = {
                        IconButton(onClick = { holder.closeTriggerEditor() }, enabled = dirty) {
                            Icon(Icons.Default.Save, contentDescription = saveDesc)
                        }
                    }
                )
            )
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text(stringResource(R.string.automation_discard_title)) },
                text = { Text(stringResource(R.string.automation_discard_message)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.revertTrigger()
                            holder.closeTriggerEditor()
                        }) { Text(stringResource(R.string.automation_discard_confirm)) }
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.closeTriggerEditor()
                        }) { Text(stringResource(app.aaps.core.ui.R.string.save)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        key(resetTick) {
            AutomationEditTriggerScreen(
                root = holder.workingEvent().trigger,
                availableTriggers = plugin.getTriggerDummyObjects(),
                createTrigger = { cn -> instantiateTrigger(cn) },
                newConnector = { TriggerConnector(injector) },
                onChange = { holder.onTriggerChanged() },
                onPickLocationFromMap = { triggerLoc -> holder.openMapPicker(triggerLoc) }
            )
        }
    }

    @Composable
    private fun MapPickerRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit,
        route: AutomationRoute.MapPicker
    ) {
        val backDesc = stringResource(app.aaps.core.ui.R.string.back)
        val okDesc = stringResource(app.aaps.core.ui.R.string.ok)
        val title = stringResource(R.string.pick_from_map)
        var selected by remember { mutableStateOf<Pair<Double, Double>?>(null) }

        androidx.activity.compose.BackHandler { holder.closeMapPicker() }

        LaunchedEffect(selected) {
            val current = selected
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { holder.closeMapPicker() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { current?.let { (lat, lon) -> holder.submitMapPick(lat, lon) } },
                            enabled = current != null
                        ) {
                            Icon(Icons.Default.Check, contentDescription = okDesc)
                        }
                    }
                )
            )
        }

        MapPickerScreen(
            initialLat = route.initialLat,
            initialLon = route.initialLon,
            onLocationTapped = { lat, lon -> selected = lat to lon }
        )
    }

    private fun instantiateTrigger(className: String): Trigger? = runCatching {
        val k = Class.forName(className).kotlin
        k.primaryConstructor?.call(injector) as? Trigger
    }.getOrNull()

    @Composable
    private fun EditRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit,
        activity: FragmentActivity?
    ) {
        val editState by holder.editState.collectAsStateWithLifecycle()
        val route by holder.route.collectAsStateWithLifecycle()
        val isNew = (route as? AutomationRoute.Edit)?.position == -1

        val backDesc = stringResource(app.aaps.core.ui.R.string.back)
        val saveDesc = stringResource(app.aaps.core.ui.R.string.save)
        val title = if (isNew) stringResource(R.string.automation_new_rule) else stringResource(R.string.automation_edit_rule)
        val canSave = editState.canSave
        val dirty by holder.eventDirty.collectAsStateWithLifecycle()
        var showDiscardConfirm by remember { mutableStateOf(false) }

        val attemptClose: () -> Unit = {
            when {
                editState.readOnly -> holder.closeEdit()
                !dirty             -> holder.closeEdit()
                else               -> showDiscardConfirm = true
            }
        }

        androidx.activity.compose.BackHandler { attemptClose() }

        LaunchedEffect(canSave, editState.readOnly, isNew, dirty) {
            setToolbarConfig(
                ToolbarConfig(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = attemptClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
                        }
                    },
                    actions = {
                        if (!editState.readOnly) {
                            IconButton(
                                onClick = { holder.save() },
                                enabled = dirty && canSave
                            ) {
                                Icon(Icons.Default.Save, contentDescription = saveDesc)
                            }
                        }
                    }
                )
            )
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text(stringResource(R.string.automation_discard_title)) },
                text = { Text(stringResource(R.string.automation_discard_message)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.closeEdit()
                        }) { Text(stringResource(R.string.automation_discard_confirm)) }
                        if (canSave) {
                            TextButton(onClick = {
                                showDiscardConfirm = false
                                holder.save()
                            }) { Text(stringResource(app.aaps.core.ui.R.string.save)) }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        var showActionSheet by remember { mutableStateOf(false) }
        var actionTick by remember { mutableStateOf(0) }
        val profileNames = localProfileNames()
        val sceneOptions = sceneApi.getScenes()

        AutomationEditScreen(
            state = editState,
            liveActions = holder.workingEvent().actions.toList(),
            profileNames = profileNames,
            sceneOptions = sceneOptions,
            tick = actionTick,
            onTitleChange = holder::editTitleChanged,
            onUserActionChange = holder::editUserActionChanged,
            onEnabledChange = holder::editEnabledChanged,
            onEditTrigger = { holder.openTriggerEditor() },
            onAddAction = { showActionSheet = true },
            onRemoveAction = { index ->
                holder.removeAction(index)
                actionTick++
            },
            onActionChanged = {
                holder.onWorkingEventChanged()
                actionTick++
            }
        )

        if (showActionSheet) {
            val options = remember { plugin.getActionDummyObjects().map { ActionOption.from(it) } }
            ChooseActionSheet(
                options = options,
                onPick = { opt ->
                    instantiateAction(opt.className)?.let { newAction ->
                        holder.addAction(newAction)
                        actionTick++
                    }
                },
                onDismiss = { showActionSheet = false }
            )
        }

        // silence unused activity (kept for future use)
        @Suppress("UNUSED_EXPRESSION") activity
    }

    private fun instantiateAction(className: String): app.aaps.plugins.automation.actions.Action? = runCatching {
        val k = Class.forName(className).kotlin
        k.primaryConstructor?.call(injector) as? app.aaps.plugins.automation.actions.Action
    }.getOrNull()

    private fun localProfileNames(): List<String> =
        localProfileManager.profile?.getProfileList()?.map { it.toString() } ?: emptyList()
}

private fun buildListToolbar(
    title: String,
    backDesc: String,
    settingsDesc: String,
    selectionMode: AutomationSelectionMode,
    selectedCount: Int,
    onBack: () -> Unit,
    onExitSelection: () -> Unit,
    onRun: () -> Unit,
    onStartRemove: () -> Unit,
    onStartSort: () -> Unit,
    onConfirmRemove: () -> Unit,
    onSettings: (() -> Unit)?
): ToolbarConfig = when (selectionMode) {
    AutomationSelectionMode.Remove -> ToolbarConfig(
        title = "$selectedCount",
        navigationIcon = {
            IconButton(onClick = onExitSelection) {
                Icon(Icons.Default.Close, contentDescription = backDesc)
            }
        },
        actions = {
            IconButton(onClick = onConfirmRemove) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }
    )

    AutomationSelectionMode.Sort   -> ToolbarConfig(
        title = title,
        navigationIcon = {
            IconButton(onClick = onExitSelection) {
                Icon(Icons.Default.Close, contentDescription = backDesc)
            }
        },
        actions = {
            Icon(Icons.Default.DragHandle, contentDescription = null)
        }
    )

    AutomationSelectionMode.None   -> ToolbarConfig(
        title = title,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backDesc)
            }
        },
        actions = {
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = settingsDesc)
                }
            }
            AutomationOverflow(onRun, onStartRemove, onStartSort)
        }
    )
}

@Composable
private fun AutomationOverflow(
    onRun: () -> Unit,
    onStartRemove: () -> Unit,
    onStartSort: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.run_automations)) },
            onClick = { expanded = false; onRun() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(app.aaps.core.ui.R.string.remove_items)) },
            onClick = { expanded = false; onStartRemove() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(app.aaps.core.ui.R.string.sort_items)) },
            onClick = { expanded = false; onStartSort() }
        )
    }
}
