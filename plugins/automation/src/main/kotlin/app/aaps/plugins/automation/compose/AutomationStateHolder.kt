package app.aaps.plugins.automation.compose

import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.compose.icons.IcUserOptions
import app.aaps.plugins.automation.AutomationEventObject
import app.aaps.plugins.automation.AutomationRuntime
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.events.EventAutomationUpdateGui
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerLocation
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AutomationStateHolder(
    private val plugin: AutomationRuntime,
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

    private var disposable: CompositeDisposable? = null
    private var scope: CoroutineScope? = null

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
        disposable = d
        // drop(1) skips the seed empty snapshot the plugin emits before loadFromSP runs — the
        // refresh() below covers the cold start, and the plugin's first real emission after load
        // re-triggers it. EventWearUpdateTiles is now broadcast from the plugin's own scope so it
        // also fires for NS-synced edits while the user is on a different screen — no longer
        // gated on this holder being alive.
        val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope = newScope
        plugin.events.drop(1).onEach { refresh() }.launchIn(newScope)
        refresh()
    }

    fun stop() {
        disposable?.clear()
        disposable = null
        scope?.cancel()
        scope = null
    }

    // ---- List ----
    fun toggleEnabled(position: Int, checked: Boolean) {
        plugin.at(position).isEnabled = checked
        // In-place mutation — list reference unchanged, so we have to kick the flow manually.
        // markEdited bumps the sync version so the toggle propagates client→master.
        plugin.markEdited()
    }

    fun move(from: Int, to: Int) {
        plugin.swap(from, to)
        // swap() emits, refresh() runs from the flow collector. Local refresh() removed to avoid
        // a redundant rebuild on every drag tick — the collector already fires on the same thread.
    }

    fun commitMove() {
        // swap() already emitted on every drag step, so this is a no-op in the new model.
        // Kept as a no-op so the existing caller in the drag handler doesn't break.
    }

    fun eventAt(position: Int): AutomationEventObject? =
        runCatching { plugin.at(position) }.getOrNull()

    fun remove(position: Int) {
        eventAt(position)?.let { plugin.remove(it) }
        // plugin.remove() emits; the collector picks it up and rebuilds the list.
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

    fun addAction(action: Action) {
        workingEvent.addAction(action)
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
        // add/set both emit via notifyChanged() — collector triggers refresh + EventWearUpdateTiles.
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
                // Identity of the persistent event object — stable across in-place swaps so the
                // reorder key doesn't change mid-drag (position does).
                key = System.identityHashCode(a).toLong(),
                position = i,
                title = a.title,
                isEnabled = a.isEnabled,
                readOnly = a.readOnly,
                userAction = a.userAction,
                systemAction = a.systemAction,
                actionsValid = a.areActionsValid(),
                triggerIcons = triggerIcons.distinct(),
                actionIcons = actionIcons.distinct()
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
