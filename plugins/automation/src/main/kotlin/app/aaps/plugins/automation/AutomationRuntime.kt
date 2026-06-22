package app.aaps.plugins.automation

import android.Manifest
import android.content.Context
import androidx.annotation.VisibleForTesting
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PermissionProvider
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.utils.DeferredForegroundStart
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionDisableScene
import app.aaps.plugins.automation.actions.ActionEnableScene
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionRunScene
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.actions.ActionStopTempTarget
import app.aaps.plugins.automation.compose.AutomationComposeContent
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDelta
import app.aaps.plugins.automation.events.EventAutomationUpdateGui
import app.aaps.plugins.automation.events.EventLocationChange
import app.aaps.plugins.automation.services.LocationServiceHelper
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerAutosensValue
import app.aaps.plugins.automation.triggers.TriggerBTDevice
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerBolusAgo
import app.aaps.plugins.automation.triggers.TriggerCOB
import app.aaps.plugins.automation.triggers.TriggerCannulaAge
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDelta
import app.aaps.plugins.automation.triggers.TriggerHeartRate
import app.aaps.plugins.automation.triggers.TriggerInsulinAge
import app.aaps.plugins.automation.triggers.TriggerIob
import app.aaps.plugins.automation.triggers.TriggerLocation
import app.aaps.plugins.automation.triggers.TriggerPodChange
import app.aaps.plugins.automation.triggers.TriggerProfilePercent
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryAge
import app.aaps.plugins.automation.triggers.TriggerPumpBatteryLevel
import app.aaps.plugins.automation.triggers.TriggerPumpLastConnection
import app.aaps.plugins.automation.triggers.TriggerRecurringTime
import app.aaps.plugins.automation.triggers.TriggerReservoirLevel
import app.aaps.plugins.automation.triggers.TriggerSensorAge
import app.aaps.plugins.automation.triggers.TriggerStepsCount
import app.aaps.plugins.automation.triggers.TriggerTempTarget
import app.aaps.plugins.automation.triggers.TriggerTempTargetValue
import app.aaps.plugins.automation.triggers.TriggerTime
import app.aaps.plugins.automation.triggers.TriggerTimeRange
import app.aaps.plugins.automation.triggers.TriggerWifiSsid
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Standalone automation runtime — no longer a [PluginBase].
 *
 * Lifecycle: started once at app boot via [start] (from MainApp). Execution is master-only: the
 * processing loop, location service and event processing run only when [Config.APS]. On a client
 * (AAPSCLIENT) the runtime still loads/edits/persists/syncs definitions, but never executes.
 *
 * Permissions are surfaced through [PermissionProvider] (the non-plugin parallel to
 * `PluginBase.requiredPermissions`) and are reported dynamically — only while an enabled event
 * actually uses a location trigger.
 */
@Singleton
class AutomationRuntime @Inject constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val rxBus: RxBus,
    private val constraintChecker: ConstraintsChecker,
    private val aapsSchedulers: AapsSchedulers,
    private val config: Config,
    private val locationServiceHelper: LocationServiceHelper,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val timerUtil: TimerUtil,
    private val receiverStatusStore: ReceiverStatusStore,
    // UI-only dependencies, forwarded to the Compose screen via [composeContent].
    private val uel: UserEntryLogger,
    private val profileRepository: ProfileRepository,
    private val sceneApi: SceneAutomationApi
) : Automation, PermissionProvider, BtConnectionSource {

    override val executionEnabled: Boolean get() = config.APS

    /**
     * Build the Compose screen content. Replaces the old plugin-descriptor `composeContent` lambda;
     * called from the standalone `AutomationList` nav route.
     */
    fun composeContent(): ComposablePluginContent =
        AutomationComposeContent(
            plugin = this,
            rxBus = rxBus,
            aapsSchedulers = aapsSchedulers,
            fabricPrivacy = fabricPrivacy,
            injector = injector,
            uel = uel,
            profileRepository = profileRepository,
            sceneApi = sceneApi
        )

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var scope: CoroutineScope? = null
    private val deferredStart = DeferredForegroundStart()

    /**
     * Whether the location foreground service has actually been started (driven by
     * [updateLocationService]). Volatile because it's written from the deferred main-thread start
     * callback and read from the IO-dispatched reconcilers.
     */
    @Volatile private var locationServiceRunning = false

    private val automationEvents = ArrayList<AutomationEventObject>()
    var executionLog: MutableList<String> = ArrayList()

    /** BT connect/disconnect events accumulated between processActions() runs (master only). The
     *  single external reader is TriggerBTDevice, via [recentBtConnects]. */
    private val btConnects: MutableList<EventBTChange> = ArrayList()

    override fun recentBtConnects(): List<EventBTChange> = ArrayList(btConnects)

    /**
     * Snapshot stream of [automationEvents]. Replaces the old `EventAutomationDataChanged` RxBus
     * broadcast — collectors get the latest list, and the internal storeToSP subscribe reads this
     * so persistence stays driven by the same source of truth.
     *
     * Mutations route through [notifyChanged] (or one of the wrapped add/remove/set/swap methods).
     * Each emission wraps the snapshot in [IdentityList], whose `equals` is identity-only so
     * `MutableStateFlow.value =` *always* publishes — without this, in-place mutations to event
     * fields (e.g. `event.isEnabled = ...`) would silently dedupe because the underlying list
     * contains the same `AutomationEventObject` references and `AutomationEventObject` has no
     * equals override.
     */
    private val _events = MutableStateFlow<List<AutomationEventObject>>(IdentityList(emptyList()))
    override val events: StateFlow<List<AutomationEvent>> = _events.asStateFlow()

    // Persistence is driven by EDITS only ([requestPersist]), never by [loadFromSP] — so applying a
    // master push is a pure verbatim re-parse with no store, hence no echo. Deliberately decoupled from
    // [_events] (the UI snapshot stream) so a load can refresh the UI/wear without persisting.
    private val _persist = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emit a fresh snapshot for UI/wear collectors. Does NOT persist (see [requestPersist]). */
    @Synchronized
    fun notifyChanged() {
        _events.value = IdentityList(automationEvents.toList())
    }

    /** Request a debounced persist. Only genuine edits / last-run call this — never a load. */
    private fun requestPersist() {
        _persist.tryEmit(Unit)
    }

    /**
     * Definition edit from an in-place external editor (e.g. toggleEnabled): refresh the UI and request
     * a persist. The client→master sync version is stamped automatically by PreferencesImpl when
     * storeToSP writes the bidirectionally-synced AutomationEvents key.
     */
    @Synchronized
    fun markEdited() {
        notifyChanged()
        requestPersist()
    }

    /**
     * List wrapper whose equals/hashCode use object identity. Bypasses [MutableStateFlow]'s default
     * structural dedup so emissions fire for every mutation — including in-place edits to mutable
     * `AutomationEventObject` fields that don't change list structure (toggleEnabled is the common
     * case). The List API itself is delegated, so consumers using `events.value.filter { ... }`
     * see no difference. Private — leaks only via `events` which exposes the `List<AutomationEvent>`
     * interface.
     */
    private class IdentityList<T>(private val delegate: List<T>) : List<T> by delegate {

        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    companion object {

        const val EMPTY_EVENT =
            "{\"title\":\"Low\",\"enabled\":true,\"trigger\":\"{\\\"type\\\":\\\"TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerBg\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"bg\\\\\\\":4,\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\",\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\"}}\\\",\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerDelta\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"value\\\\\\\":-0.1,\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\",\\\\\\\"deltaType\\\\\\\":\\\\\\\"DELTA\\\\\\\",\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\"}}\\\"]}}\",\"actions\":[\"{\\\"type\\\":\\\"ActionStartTempTarget\\\",\\\"data\\\":{\\\"value\\\":8,\\\"units\\\":\\\"mmol\\\",\\\"durationInMinutes\\\":60}}\"]}"
    }

    /**
     * Location permission is required only on a master device that has at least one enabled event
     * using a [TriggerLocation]. Queried by [ActivePlugin.collectMissingPermissions] on every
     * collection pass, so the permission appears/disappears as the event set changes.
     */
    override fun requiredPermissions(): List<PermissionGroup> =
        if (config.APS && usesLocationTrigger()) listOf(
            PermissionGroup(
                permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                rationaleTitle = R.string.permission_location_title,
                rationaleDescription = R.string.permission_location_description,
            ),
            PermissionGroup(
                permissions = listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                rationaleTitle = R.string.permission_location_title,
                rationaleDescription = R.string.permission_background_location_description,
            ),
        ) else emptyList()

    /** True when any enabled event's trigger tree contains a [TriggerLocation]. */
    private fun usesLocationTrigger(): Boolean =
        synchronized(this) { automationEvents.toList() }
            .any { it.isEnabled && connectorHasLocation(it.trigger) }

    private fun connectorHasLocation(connector: TriggerConnector): Boolean =
        connector.list.any { t ->
            when (t) {
                is TriggerLocation  -> true
                is TriggerConnector -> connectorHasLocation(t)
                else                -> false
            }
        }

    /**
     * Start the runtime. Called once from MainApp at boot. Definition load + persistence + tile
     * refresh run on every flavor; the processing loop and location service are master-only.
     */
    fun start(externalScope: CoroutineScope? = null) {
        if (scope != null) return // idempotent — already started; avoid leaking a second scope + duplicate subscriptions
        bootstrap()

        // externalScope is injected by tests to drive the debounced persistence deterministically;
        // production passes nothing and gets the long-lived IO scope.
        val newScope = externalScope ?: CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        // Persist on EDITS only (never on a load — that's the whole point of the verbatim model).
        // debounce coalesces rapid edit-flow mutations (toggle/move/save back-to-back) into a single write.
        @Suppress("OPT_IN_USAGE")
        _persist.debounce(300).onEach { storeToSP() }.launchIn(newScope)
        // Refresh the wear UserAction tile on any cache change — fires for NS-synced edits too,
        // not only when the in-app Automation screen is open. debounce(300) prevents per-drag-tick
        // tile storms during reorder.
        @Suppress("OPT_IN_USAGE")
        _events.drop(1).debounce(300).onEach { rxBus.send(EventWearUpdateTiles()) }.launchIn(newScope)

        // Adopt definitions written behind our back via putRemote — BOTH directions:
        //  • master→client cold-sync push (on the client),
        //  • client→master push applied by ClientControlReceiver (on the master) — this restores the
        //    master reload the old bespoke `onVerifiedAutomationUpdate`/`reloadInternalState()` did,
        //    which the move to the generic pref channel dropped.
        // Skip OUR OWN writes (storeToSP, incl. master last-run persists) via the value echo-check so
        // the master doesn't re-parse on its own edits; loadFromSP preserves lastRun by id, so a real
        // remote change doesn't reset run-timers for unchanged events.
        preferences.observe(StringNonKey.AutomationEvents).drop(1).onEach { json ->
            if (json != lastSelfWritten) loadFromSP()
        }.launchIn(newScope)

        // Execution is master-only. On a client we only edit + sync definitions — no loop, no
        // location service, no event processing.
        if (!config.APS) return

        newScope.launch {
            delay(T.mins(1).msecs())
            while (isActive) {
                processActions()
                delay(T.secs(150).msecs())
            }
        }

        receiverStatusStore.chargingStatusFlow
            .filterNotNull()
            .onEach { processActions() }
            .launchIn(newScope)
        receiverStatusStore.networkStatusFlow
            .filterNotNull()
            .onEach { processActions() }
            .launchIn(newScope)

        // Start/stop the location service reactively: run it only while an enabled event actually
        // uses a location trigger (avoids a permanent foreground-service notification otherwise).
        // No drop(1) — evaluate the freshly loaded state immediately.
        _events.onEach { updateLocationService() }.launchIn(newScope)
        // Re-create the service when the location provider mode changes — restart through the same
        // deferred/flag-aware path (avoids a background startForegroundService crash on Android 12+).
        preferences.observe(StringKey.AutomationLocation).drop(1).onEach {
            if (locationServiceRunning) {
                deferredStart.cancel()
                locationServiceHelper.stopService(context)
                locationServiceRunning = false
            }
            updateLocationService()
        }.launchIn(newScope)

        disposable += rxBus
            .toObservable(EventLocationChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.AUTOMATION, "Grabbed location: ${it.location.latitude} ${it.location.longitude} Provider: ${it.location.provider}")
                           scope?.launch { processActions() }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBTChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.AUTOMATION, "Grabbed new BT event: $it")
                           btConnects.add(it)
                           scope?.launch { processActions() }
                       }, fabricPrivacy::logException)
    }

    /** Tear down the runtime. Not called in production (always-on singleton); used by tests. */
    fun stop() {
        scope?.cancel()
        scope = null
        disposable.clear()
        deferredStart.cancel()
        if (locationServiceRunning) {
            locationServiceHelper.stopService(context)
            locationServiceRunning = false
        }
    }

    @Synchronized
    private fun updateLocationService() {
        val need = usesLocationTrigger()
        if (need && !locationServiceRunning) {
            // startService() returns false when the location permission isn't granted yet; only
            // latch the flag once it actually started, so a later permission grant — reconciled on
            // the next processActions tick — retries instead of leaving the service stuck off.
            deferredStart.start { if (locationServiceHelper.startService(context)) locationServiceRunning = true }
        } else if (!need && locationServiceRunning) {
            deferredStart.cancel()
            locationServiceHelper.stopService(context)
            locationServiceRunning = false
        }
    }

    /**
     * The exact JSON this runtime last wrote itself (edit or last-run persist). The self-observe below
     * compares against it to skip OUR OWN writes — so the master doesn't re-parse (and clobber the
     * runtime list) on its own edits / per-tick last-run persists, only on a genuine remote push.
     */
    @Volatile private var lastSelfWritten: String? = null

    // Pure compose — only ever reached from the EDIT-driven [_persist] trigger (never from a load), so
    // no band-aid is needed: a verbatim load doesn't persist, and an edit always changes content.
    private fun storeToSP() {
        val json = eventsToJson()
        lastSelfWritten = json
        preferences.put(StringNonKey.AutomationEvents, json)
    }

    /** Serialize the in-memory event list to the persisted JSON shape (what [storeToSP] writes). */
    private fun eventsToJson(): String {
        val array = JSONArray()
        synchronized(this) { automationEvents.toMutableList() }.forEach { event ->
            try {
                array.put(JSONObject(event.toJSON()))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return array.toString()
    }

    // Verbatim mirror of the persisted definitions — parse only, NO store, NO seed, NO id-backfill-store.
    // Id-backfill + the EMPTY_EVENT starter happen once in [bootstrap] (master); a master push is already
    // canonical, so applying it is a pure re-parse → no store → no echo. @VisibleForTesting + internal:
    // production reaches this only via start()/self-observe; tests drive it to assert the load behavior.
    @VisibleForTesting
    @Synchronized
    internal fun loadFromSP() {
        // Carry run-timers across the re-parse: lastRun isn't serialized, so a naive reload would reset
        // every event's timer and (on a master that executes) risk re-firing automations right after a
        // remote push. Preserve it by id for events that still exist; new/changed ids start fresh.
        val previousLastRun = automationEvents.associate { it.id to it.lastRun }
        automationEvents.clear()
        val data = preferences.get(StringNonKey.AutomationEvents)
        if (data != "")
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val event = AutomationEventObject(injector).fromJSON(array.getJSONObject(i).toString())
                    previousLastRun[event.id]?.let { event.lastRun = it }
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        notifyChanged() // fan out to UI/wear collectors; does NOT persist
    }

    // One-time at init (from [start]).
    // CLIENT: pure verbatim mirror — the master owns the canonical definitions; never re-serialize or
    // persist (no echo, no mixed-version cosmetic divergence).
    // MASTER: id-backfill legacy id-less events (fromJSON assigns ids in-memory) and seed the EMPTY_EVENT
    // starter on a fresh install (pref never initialized = ""), then persist once via putRemote if the
    // canonical form changed. Idempotent for already-canonical data.
    @Synchronized
    private fun bootstrap() {
        if (config.AAPSCLIENT) {
            loadFromSP()
            return
        }
        val before = preferences.get(StringNonKey.AutomationEvents)
        loadFromSP()
        if (before == "") automationEvents.add(AutomationEventObject(injector).fromJSON(EMPTY_EVENT))
        notifyChanged()
        val after = eventsToJson()
        if (after != before)
            preferences.putRemote(
                StringNonKey.AutomationEvents, after,
                preferences.get(LongComposedKey.SyncedPrefModified, StringNonKey.AutomationEvents.key)
            )
    }

    internal suspend fun processActions() {
        if (!config.appInitialized) return
        if (!config.APS) return // execution is master-only — clients never run automation
        // Reconcile the location service each tick: retries a start that earlier no-op'd because the
        // location permission wasn't granted yet, and stops it if the last location event was removed.
        updateLocationService()
        /**
         * Changed to false if some condition prevents automation from running.
         * In this case only system automations are enabled.
         */
        var commonEventsEnabled = true
        /*
         * Running mode must report running to process automation events.
         */
        val runningMode = loop.runningMode()
        if (runningMode.pausesLoopExecution() || !runningMode.isLoopRunning()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop suspended")
            executionLog.add(rh.gs(app.aaps.core.ui.R.string.loopsuspended))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        /*
         * Loop must be enabled to process automation events.
         */
        if (!(loop as PluginBase).isEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop not enabled")
            executionLog.add(rh.gs(app.aaps.core.ui.R.string.disconnected))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        /*
         * Constraints must not block automation
         */
        val enabled = constraintChecker.isAutomationEnabled()
        if (!enabled.value()) {
            val reason = enabled.getMostLimitedReasons()
            if (executionLog.lastOrNull() != reason) executionLog.add(reason)
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }

        aapsLogger.debug(LTag.AUTOMATION, "processActions")
        val iterator = synchronized(this) { automationEvents.toMutableList().iterator() }
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (event.isEnabled && !event.userAction && event.shouldRun())
                if (event.systemAction || commonEventsEnabled) {
                    processEvent(event)
                    if (event.hasStopProcessing()) break
                }
        }

        /*
         * We cannot detect connected BT devices
         * So, let's collect all connection/disconnections between 2 runs of processActions()
         * TriggerBTDevice can pick up and process these events
         * after processing clear events to prevent repeated actions
         */
        btConnects.clear()

        requestPersist() // persist last-run time (edit-driven trigger; master only)
    }

    override suspend fun processEvent(someEvent: AutomationEvent) {
        if (!config.APS) return // execution is master-only — guards every UI entry point (wear, quick launch, scenes, run-now)
        val event = someEvent as AutomationEventObject
        if (event.canRun() && event.preconditionCanRun()) {
            val actions = event.actions
            for (action in actions) {
                action.title = event.title
                if (action.isValid()) {
                    val result = action.doAction()
                    val sb = StringBuilder()
                        .append(dateUtil.timeString(dateUtil.now()))
                        .append(" ")
                        .append(if (result.success) "☺" else "▼")
                        .append(" <b>")
                        .append(event.title)
                        .append(":</b> ")
                        .append(action.shortDescription())
                        .append(": ")
                        .append(result.comment)
                    executionLog.add(sb.toString())
                    aapsLogger.debug(LTag.AUTOMATION, "Executed: $sb")
                    rxBus.send(EventAutomationUpdateGui())
                } else {
                    executionLog.add("Invalid action: ${action.shortDescription()}")
                    aapsLogger.debug(LTag.AUTOMATION, "Invalid action: ${action.shortDescription()}")
                    rxBus.send(EventAutomationUpdateGui())
                }
            }
            event.lastRun = dateUtil.now()
            if (event.autoRemove) remove(event)
        }
    }

    @Synchronized
    fun add(event: AutomationEventObject) {
        automationEvents.add(event)
        markEdited()
    }

    @Synchronized
    fun addIfNotExists(event: AutomationEventObject) {
        for (e in automationEvents) {
            if (event.title == e.title) return
        }
        automationEvents.add(event)
        markEdited()
    }

    @Synchronized
    fun removeIfExists(event: AutomationEvent) {
        for (e in automationEvents.reversed()) {
            if (event.title == e.title) {
                automationEvents.remove(e)
                markEdited()
            }
        }
    }

    @Synchronized
    fun set(event: AutomationEventObject, index: Int) {
        automationEvents[index] = event
        markEdited()
    }

    @Synchronized
    fun remove(event: AutomationEvent) {
        if (automationEvents.remove(event)) markEdited()
    }

    fun at(index: Int) = automationEvents[index]

    fun size() = automationEvents.size

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        Collections.swap(automationEvents, fromPosition, toPosition)
        // Reorder is a config change — persisted ordering decides processing order in
        // processActions, so collectors and storeToSP both need to see it.
        markEdited()
    }

    override fun findEventById(id: String): AutomationEvent? {
        return synchronized(this) { automationEvents.find { it.id == id } }
    }

    fun getActionDummyObjects(): List<Action> {
        val actions = mutableListOf(
            ActionStopProcessing(injector),
            ActionStartTempTarget(injector),
            ActionStopTempTarget(injector),
            ActionNotification(injector),
            ActionAlarm(injector),
            ActionSettingsExport(injector),
            ActionCarePortalEvent(injector),
            ActionProfileSwitchPercent(injector),
            ActionProfileSwitch(injector),
            ActionSendSMS(injector),
            ActionSMBChange(injector),
            ActionRunScene(injector),
            ActionEnableScene(injector),
            ActionDisableScene(injector)
        )
        if (config.isEngineeringMode() && config.isDev())
            actions.add(ActionRunAutotune(injector))

        return actions.toList()
    }

    fun getTriggerDummyObjects(): List<Trigger> {
        val triggers = mutableListOf(
            TriggerConnector(injector),
            TriggerTime(injector),
            TriggerRecurringTime(injector),
            TriggerTimeRange(injector),
            TriggerBg(injector),
            TriggerDelta(injector),
            TriggerIob(injector),
            TriggerCOB(injector),
            TriggerProfilePercent(injector),
            TriggerTempTarget(injector),
            TriggerTempTargetValue(injector),
            TriggerWifiSsid(injector),
            TriggerLocation(injector),
            TriggerAutosensValue(injector),
            TriggerBolusAgo(injector),
            TriggerPumpLastConnection(injector),
            TriggerBTDevice(injector),
            TriggerHeartRate(injector),
            TriggerSensorAge(injector),
            TriggerCannulaAge(injector),
            TriggerReservoirLevel(injector),
            TriggerStepsCount(injector)
        )

        val pump = activePlugin.activePump

        if (pump.pumpDescription.isPatchPump) {
            triggers.add(TriggerPodChange(injector))
        } else {
            triggers.add(TriggerInsulinAge(injector))
        }
        if (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()) {
            triggers.add(TriggerPumpBatteryAge(injector))
        }
        val erosBatteryLinkAvailable = pump.model() == PumpType.OMNIPOD_EROS && pump.isUseRileyLinkBatteryLevel()
        if (pump.model().supportBatteryLevel || erosBatteryLinkAvailable) {
            triggers.add(TriggerPumpBatteryLevel(injector))
        }

        return triggers.toList()
    }

    /**
     * Generate reminder via [TimerUtil]
     *
     * @param seconds seconds to the future
     */
    override fun scheduleTimeToEatReminder(seconds: Int) =
        timerUtil.scheduleReminder(seconds, rh.gs(R.string.time_to_eat))

    /**
     * Create new Automation event to alarm when is time to eat
     */
    override fun scheduleAutomationEventEatReminder() {
        val event = AutomationEventObject(injector).apply {
            title = rh.gs(app.aaps.core.ui.R.string.bolus_advisor)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.OR).apply {

                // Bg under 180 mgdl and dropping by 15 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 180.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, -15.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, -8.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
                // Bg under 160 mgdl and dropping by 9 mgdl
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 160.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, -9.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, -5.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
                // Bg under 145 mgdl and dropping
                list.add(TriggerConnector(injector, TriggerConnector.Type.AND).apply {
                    list.add(TriggerBg(injector, 145.0, GlucoseUnit.MGDL, Comparator.Compare.IS_LESSER))
                    list.add(TriggerDelta(injector, InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_LESSER))
                    list.add(
                        TriggerDelta(
                            injector,
                            InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.SHORT_AVERAGE),
                            GlucoseUnit.MGDL,
                            Comparator.Compare.IS_EQUAL_OR_LESSER
                        )
                    )
                })
            }
            actions.add(ActionAlarm(injector, rh.gs(R.string.time_to_eat)))
        }

        addIfNotExists(event)
    }

    /**
     * Remove Automation event
     */
    override fun removeAutomationEventEatReminder() {
        val event = AutomationEventObject(injector).apply {
            title = rh.gs(app.aaps.core.ui.R.string.bolus_advisor)
        }
        removeIfExists(event)
    }

    override fun scheduleAutomationEventBolusReminder() {
        val event = AutomationEventObject(injector).apply {
            title = rh.gs(app.aaps.core.ui.R.string.bolus_reminder)
            readOnly = true
            systemAction = true
            autoRemove = true
            trigger = TriggerConnector(injector, TriggerConnector.Type.AND).apply {

                // Bg above 70 mgdl and delta positive mgdl
                list.add(TriggerBg(injector, 70.0, GlucoseUnit.MGDL, Comparator.Compare.IS_EQUAL_OR_GREATER))
                list.add(
                    TriggerDelta(
                        injector, InputDelta(rh, 0.0, -360.0, 360.0, 1.0, DecimalFormat("0"), InputDelta.DeltaType.DELTA), GlucoseUnit.MGDL, Comparator.Compare
                            .IS_GREATER
                    )
                )
            }
            actions.add(ActionAlarm(injector, rh.gs(R.string.time_to_bolus)))
        }

        addIfNotExists(event)
    }

    override fun removeAutomationEventBolusReminder() {
        val event = AutomationEventObject(injector).apply {
            title = rh.gs(app.aaps.core.ui.R.string.bolus_reminder)
        }
        removeIfExists(event)
    }
}
