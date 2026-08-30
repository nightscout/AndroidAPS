package app.aaps.plugins.automation.compose

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.objects.extensions.profileNames
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.masterEditingEnabled
import app.aaps.plugins.automation.AutomationEventFactory
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.PairedBtDevices
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.compose.actions.ActionOption
import app.aaps.plugins.automation.compose.actions.ChooseActionSheet
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.plugins.automation.triggers.TriggerLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutomationComposeContent(
    private val plugin: AutomationRuntime,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val actionFactory: ActionFactory,
    private val automationEventFactory: AutomationEventFactory,
    private val triggerFactory: TriggerFactory,
    private val uel: UserEntryLogger,
    private val profileRepository: ProfileRepository,
    private val sceneApi: SceneAutomationApi,
    private val pairedBtDevices: PairedBtDevices
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val holder = remember {
            AutomationStateHolder(plugin, rxBus, aapsLogger, automationEventFactory)
        }
        DisposableEffect(holder) {
            holder.start()
            onDispose { holder.stop() }
        }

        val route by holder.route.collectAsStateWithLifecycle()
        val ioScope = rememberCoroutineScope()

        when (route) {
            is AutomationRoute.List -> ListRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig,
                onNavigateBack = onNavigateBack,
                onSettings = onSettings,
                onRun = { ioScope.launch(Dispatchers.IO) { plugin.processActions() } }
            )

            is AutomationRoute.Edit -> EditRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig
            )

            is AutomationRoute.EditTrigger -> EditTriggerRoute(
                holder = holder,
                setToolbarConfig = setToolbarConfig
            )

            is AutomationRoute.MapPicker -> MapPickerRoute(
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
        onRun: () -> Unit
    ) {
        val state by holder.state.collectAsStateWithLifecycle()
        var deleteTarget by remember { mutableStateOf<Int?>(null) }

        val title = stringResource(AutomationStrings.automation)
        val backDesc = stringResource(CoreUiStrings.back)
        val settingsDesc = stringResource(CoreUiStrings.nav_plugin_preferences)

        LaunchedEffect(Unit) {
            setToolbarConfig(
                buildListToolbar(
                    title = title,
                    backDesc = backDesc,
                    settingsDesc = settingsDesc,
                    onBack = onNavigateBack,
                    onRun = onRun,
                    // Run stays master-only (automation executes only on the master). Settings is shown on
                    // the client too: its sole pref (location-provider mode) is now a Bidirectional synced
                    // setting the client can change, and the screen resolves on a client (see BuiltInSearchables).
                    onSettings = onSettings,
                    showRun = plugin.executionEnabled
                )
            )
        }

        // On a client whose master is unreachable, edits can't sync right now — grey out the
        // whole list and hide the add FAB (mirrors the offline-gating of synced preference rows).
        // Master short-circuits to true, so it is never gated.
        val editingEnabled = masterEditingEnabled()

        AutomationScreen(
            state = state,
            onToggleEnabled = holder::toggleEnabled,
            onEditEvent = { pos -> holder.openEdit(pos) },
            onDeleteEvent = { pos -> deleteTarget = pos },
            onMove = holder::move,
            onMoveFinished = holder::commitMove,
            onAddClick = { holder.openNew() },
            editingEnabled = editingEnabled
        )

        deleteTarget?.let { pos ->
            val target = holder.eventAt(pos)
            val message = stringResource(CoreUiStrings.removerecord) +
                (target?.let { " " + it.title } ?: "")
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(CoreUiStrings.removerecord)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        target?.let { uel.log(Action.AUTOMATION_REMOVED, Sources.Automation, it.title) }
                        holder.remove(pos)
                        deleteTarget = null
                    }) { Text(stringResource(CoreUiStrings.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(CoreUiStrings.cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun EditTriggerRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit
    ) {
        val focusManager = LocalFocusManager.current
        val backDesc = stringResource(CoreUiStrings.back)
        val saveDesc = stringResource(CoreUiStrings.save)
        val title = stringResource(AutomationStrings.condition).trimEnd(':')
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
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                holder.closeTriggerEditor()
                            },
                            enabled = dirty
                        ) {
                            Icon(Icons.Default.Save, contentDescription = saveDesc)
                        }
                    }
                )
            )
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirm = false },
                title = { Text(stringResource(AutomationStrings.automation_discard_title)) },
                text = { Text(stringResource(AutomationStrings.automation_discard_message)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.revertTrigger()
                            holder.closeTriggerEditor()
                        }) { Text(stringResource(AutomationStrings.automation_discard_confirm)) }
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.closeTriggerEditor()
                        }) { Text(stringResource(CoreUiStrings.save)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text(stringResource(CoreUiStrings.cancel))
                    }
                }
            )
        }

        // null means the Bluetooth permission is missing, which the user can fix, so it gets a
        // message. An empty list just means no device is paired and says nothing.
        val pairedNames = remember { pairedBtDevices.names() }
        val noPermission = stringResource(CoreUiStrings.need_connect_permission)
        LaunchedEffect(pairedNames) {
            if (pairedNames == null) rxBus.send(EventShowSnackbar(noPermission, EventShowSnackbar.Type.Error))
        }

        // Offered only when a position is known, which is what the old dialog did with
        // `maybeAdd(..., lastLocation != null)`. Null means no button rather than a dead one.
        val lastKnownLocation = triggerFactory.deps.lastKnownLocation
        val useCurrentLocation: ((TriggerLocation) -> Unit)? =
            lastKnownLocation.position()?.let { position ->
                { trigger: TriggerLocation ->
                    trigger.latitude.setValue(position.latitude)
                    trigger.longitude.setValue(position.longitude)
                    holder.onTriggerChanged()
                }
            }

        key(resetTick) {
            AutomationEditTriggerScreen(
                root = holder.workingEvent().trigger,
                bondedDevices = pairedNames.orEmpty(),
                onUseCurrentLocation = useCurrentLocation,
                availableTriggers = plugin.getTriggerDummyObjects(),
                createTrigger = { cn -> instantiateTrigger(cn) },
                newConnector = { TriggerConnector(triggerFactory.deps) },
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
        val backDesc = stringResource(CoreUiStrings.back)
        val okDesc = stringResource(CoreUiStrings.ok)
        val title = stringResource(AutomationStrings.pick_from_map)
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

    private fun instantiateTrigger(className: String): Trigger? = triggerFactory.instantiate(className)

    @Composable
    private fun EditRoute(
        holder: AutomationStateHolder,
        setToolbarConfig: (ToolbarConfig) -> Unit
    ) {
        val focusManager = LocalFocusManager.current
        val editState by holder.editState.collectAsStateWithLifecycle()
        val route by holder.route.collectAsStateWithLifecycle()
        val isNew = (route as? AutomationRoute.Edit)?.position == -1

        val backDesc = stringResource(CoreUiStrings.back)
        val saveDesc = stringResource(CoreUiStrings.save)
        val title = if (isNew) stringResource(AutomationStrings.automation_new_rule) else stringResource(AutomationStrings.automation_edit_rule)
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
                                onClick = {
                                    focusManager.clearFocus()
                                    holder.save()
                                },
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
                title = { Text(stringResource(AutomationStrings.automation_discard_title)) },
                text = { Text(stringResource(AutomationStrings.automation_discard_message)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            holder.closeEdit()
                        }) { Text(stringResource(AutomationStrings.automation_discard_confirm)) }
                        if (canSave) {
                            TextButton(onClick = {
                                focusManager.clearFocus()
                                showDiscardConfirm = false
                                holder.save()
                            }) { Text(stringResource(CoreUiStrings.save)) }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirm = false }) {
                        Text(stringResource(CoreUiStrings.cancel))
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

    }

    // Fully qualified: `Action` here is app.aaps.core.data.ue.Action, imported for the UserEntry logging.
    private fun instantiateAction(className: String): app.aaps.plugins.automation.actions.Action? = actionFactory.instantiate(className)

    private fun localProfileNames(): List<String> = profileRepository.profileNames()
}

private fun buildListToolbar(
    title: String,
    backDesc: String,
    settingsDesc: String,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onSettings: (() -> Unit)?,
    showRun: Boolean
): ToolbarConfig = ToolbarConfig(
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
        // "Run now" is master-only — automation does not execute on a client.
        if (showRun) AutomationOverflow(onRun)
    }
)

@Composable
private fun AutomationOverflow(onRun: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(AutomationStrings.run_automations)) },
            onClick = { expanded = false; onRun() }
        )
    }
}
