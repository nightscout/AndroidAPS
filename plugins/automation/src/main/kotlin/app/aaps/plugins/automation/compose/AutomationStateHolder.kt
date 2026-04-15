package app.aaps.plugins.automation.compose

import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.compose.icons.IcUserOptions
import app.aaps.plugins.automation.AutomationEventObject
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.core.interfaces.rx.events.EventAutomationDataChanged
import app.aaps.plugins.automation.events.EventAutomationUpdateGui
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerLocation
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutomationStateHolder(
    private val plugin: AutomationPlugin,
    private val rxBus: RxBus,
    private val aapsSchedulers: AapsSchedulers,
    private val fabricPrivacy: FabricPrivacy,
    private val injector: HasAndroidInjector
) {

    private val _state = MutableStateFlow(AutomationUiState())
    val state: StateFlow<AutomationUiState> = _state.asStateFlow()

    private val _route = MutableStateFlow<AutomationRoute>(AutomationRoute.List)
    val route: StateFlow<AutomationRoute> = _route.asStateFlow()

    private val _editState = MutableStateFlow(AutomationEditUiState())
    val editState: StateFlow<AutomationEditUiState> = _editState.asStateFlow()

    private val selectedPositions = mutableSetOf<Int>()
    private var disposable: CompositeDisposable? = null

    // Working copy for edit
    private var workingEvent: AutomationEventObject = AutomationEventObject(injector)
    private var workingPosition: Int = -1

    fun start() {
        if (disposable != null) return
        val d = CompositeDisposable()
        d += rxBus.toObservable(EventAutomationUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           refresh()
                           refreshEditState()
                       }, fabricPrivacy::logException)
        d += rxBus.toObservable(EventAutomationDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           refresh()
                           rxBus.send(EventWearUpdateTiles())
                       }, fabricPrivacy::logException)
        disposable = d
        refresh()
    }

    fun stop() {
        disposable?.clear()
        disposable = null
    }

    // ---- List ----
    fun toggleEnabled(position: Int, checked: Boolean) {
        plugin.at(position).isEnabled = checked
        rxBus.send(EventAutomationDataChanged())
    }

    fun move(from: Int, to: Int) {
        plugin.swap(from, to)
        refresh()
    }

    fun commitMove() {
        rxBus.send(EventAutomationDataChanged())
    }

    fun enterRemoveMode() {
        selectedPositions.clear()
        _state.value = _state.value.copy(selectionMode = AutomationSelectionMode.Remove)
        refresh()
    }

    fun enterSortMode() {
        _state.value = _state.value.copy(selectionMode = AutomationSelectionMode.Sort)
        refresh()
    }

    fun exitSelection() {
        selectedPositions.clear()
        _state.value = _state.value.copy(selectionMode = AutomationSelectionMode.None)
        refresh()
    }

    fun toggleSelection(position: Int, checked: Boolean) {
        if (checked) selectedPositions.add(position) else selectedPositions.remove(position)
        refresh()
    }

    fun selectedEvents(): List<AutomationEventObject> =
        selectedPositions.sorted().mapNotNull { runCatching { plugin.at(it) }.getOrNull() }

    fun removeSelected() {
        selectedEvents().forEach { plugin.remove(it) }
        selectedPositions.clear()
        _state.value = _state.value.copy(selectionMode = AutomationSelectionMode.None)
        rxBus.send(EventAutomationDataChanged())
    }

    private var eventSnapshotJson: String? = null
    private val _eventDirty = MutableStateFlow(false)
    val eventDirty: StateFlow<Boolean> = _eventDirty.asStateFlow()

    private fun snapshotEvent() {
        eventSnapshotJson = workingEvent.toJSON()
        _eventDirty.value = false
    }

    private fun recomputeEventDirty() {
        val snap = eventSnapshotJson ?: return
        _eventDirty.value = workingEvent.toJSON() != snap
    }

    fun onWorkingEventChanged() = recomputeEventDirty()

    // ---- Navigation / Edit ----
    fun openNew() {
        workingEvent = AutomationEventObject(injector)
        workingPosition = -1
        snapshotEvent()
        _route.value = AutomationRoute.Edit(-1)
        refreshEditState()
    }

    fun openEdit(position: Int) {
        val source = plugin.at(position)
        workingEvent = AutomationEventObject(injector).fromJSON(source.toJSON())
        workingPosition = position
        snapshotEvent()
        _route.value = AutomationRoute.Edit(position)
        refreshEditState()
    }

    fun closeEdit() {
        _route.value = AutomationRoute.List
    }

    private var triggerSnapshotJson: String? = null
    private val _triggerDirty = MutableStateFlow(false)
    val triggerDirty: StateFlow<Boolean> = _triggerDirty.asStateFlow()
    private val _triggerResetTick = MutableStateFlow(0)
    val triggerResetTick: StateFlow<Int> = _triggerResetTick.asStateFlow()

    fun openTriggerEditor() {
        triggerSnapshotJson = workingEvent.trigger.dataJSON().toString()
        _triggerDirty.value = false
        _route.value = AutomationRoute.EditTrigger
    }

    fun onTriggerChanged() {
        val snap = triggerSnapshotJson ?: return
        _triggerDirty.value = workingEvent.trigger.dataJSON().toString() != snap
        recomputeEventDirty()
    }

    fun revertTrigger() {
        val snap = triggerSnapshotJson ?: return
        workingEvent.trigger.fromJSON(snap)
        _triggerDirty.value = false
        recomputeEventDirty()
        _triggerResetTick.value = _triggerResetTick.value + 1
    }

    private var locationPickWriter: ((Double, Double) -> Unit)? = null

    fun openMapPicker(trigger: TriggerLocation) {
        locationPickWriter = { lat, lon ->
            trigger.latitude.value = lat
            trigger.longitude.value = lon
        }
        val lastLocation = trigger.locationDataContainer.lastLocation
        _route.value = AutomationRoute.MapPicker(
            initialLat = trigger.latitude.value.takeIf { it != 0.0 } ?: lastLocation?.latitude,
            initialLon = trigger.longitude.value.takeIf { it != 0.0 } ?: lastLocation?.longitude
        )
    }

    fun submitMapPick(lat: Double, lon: Double) {
        locationPickWriter?.invoke(lat, lon)
        locationPickWriter = null
        _route.value = AutomationRoute.EditTrigger
    }

    fun closeMapPicker() {
        locationPickWriter = null
        _route.value = AutomationRoute.EditTrigger
    }

    fun closeTriggerEditor() {
        triggerSnapshotJson = null
        _triggerDirty.value = false
        _route.value = AutomationRoute.Edit(workingPosition)
        refreshEditState()
    }

    fun editTitleChanged(title: String) {
        workingEvent.title = title
        recomputeEventDirty()
        refreshEditState()
    }

    fun editUserActionChanged(checked: Boolean) {
        workingEvent.userAction = checked
        recomputeEventDirty()
        refreshEditState()
    }

    fun editEnabledChanged(checked: Boolean) {
        workingEvent.isEnabled = checked
        recomputeEventDirty()
        refreshEditState()
    }

    fun removeAction(index: Int) {
        if (index in workingEvent.actions.indices) {
            workingEvent.actions.removeAt(index)
            recomputeEventDirty()
            refreshEditState()
        }
    }

    fun workingEvent(): AutomationEventObject = workingEvent

    fun save(): Boolean {
        val e = workingEvent
        if (e.title.isBlank()) return false
        if (e.trigger.size() == 0 && !e.userAction) return false
        if (e.actions.isEmpty()) return false
        if (workingPosition == -1) plugin.add(e) else plugin.set(e, workingPosition)
        rxBus.send(EventAutomationDataChanged())
        rxBus.send(EventWearUpdateTiles())
        _route.value = AutomationRoute.List
        return true
    }

    private fun refreshEditState() {
        val e = workingEvent
        val preconditions = e.getPreconditions()
        _editState.value = AutomationEditUiState(
            title = e.title,
            userAction = e.userAction,
            enabled = e.isEnabled,
            readOnly = e.readOnly,
            triggerDescription = e.trigger.friendlyDescription(),
            hasTrigger = e.trigger.size() > 0,
            preconditionsDescription = if (preconditions.size() > 0) preconditions.friendlyDescription() else "",
            actions = e.actions.mapIndexed { i, a ->
                AutomationActionUi(
                    index = i,
                    title = a.shortDescription(),
                    icon = a.composeIcon(),
                    valid = a.isValid()
                )
            },
            titleError = false
        )
    }

    private fun refresh() {
        val events = (0 until plugin.size()).map { i ->
            val a = plugin.at(i)
            val triggerIcons = mutableListOf<AutomationIcon>()
            if (a.userAction) triggerIcons.add(AutomationIcon(IcUserOptions))
            collectTriggerIcons(a.trigger, triggerIcons)
            val actionIcons = mutableListOf<AutomationIcon>()
            for (act in a.actions) act.composeIcon()?.let { actionIcons.add(AutomationIcon(it, act.composeIconTint())) }
            AutomationEventUi(
                position = i,
                title = a.title,
                isEnabled = a.isEnabled,
                readOnly = a.readOnly,
                userAction = a.userAction,
                systemAction = a.systemAction,
                actionsValid = a.areActionsValid(),
                triggerIcons = triggerIcons.distinct(),
                actionIcons = actionIcons.distinct(),
                isSelected = i in selectedPositions
            )
        }
        val sb = StringBuilder()
        for (l in plugin.executionLog.reversed()) sb.append(l).append("<br>")
        _state.value = _state.value.copy(
            events = events,
            logHtml = sb.toString()
        )
    }

    private fun collectTriggerIcons(connector: TriggerConnector, list: MutableList<AutomationIcon>) {
        for (t in connector.list) {
            if (t is TriggerConnector) collectTriggerIcons(t, list)
            else t.composeIcon()?.let { list.add(AutomationIcon(it, t.composeIconTint())) }
        }
    }
}
