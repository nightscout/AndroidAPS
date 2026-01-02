package app.aaps.plugins.automation

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.automation.AutomationEvent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.interfaces.rx.events.EventChargingState
import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionAlarm
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionNotification
import app.aaps.plugins.automation.actions.ActionProfileSwitch
import app.aaps.plugins.automation.actions.ActionProfileSwitchPercent
import app.aaps.plugins.automation.actions.ActionRunAutotune
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionSendSMS
import app.aaps.plugins.automation.actions.ActionSettingsExport
import app.aaps.plugins.automation.actions.ActionStartTempTarget
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.actions.ActionStopTempTarget
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputDelta
import app.aaps.plugins.automation.events.EventAutomationDataChanged
import app.aaps.plugins.automation.events.EventAutomationUpdateGui
import app.aaps.plugins.automation.events.EventLocationChange
import app.aaps.plugins.automation.keys.AutomationStringKey
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
import app.aaps.plugins.automation.ui.TimerUtil
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
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
    private val timerUtil: TimerUtil
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutomationFragment::class.qualifiedName)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_automation)
        .pluginName(R.string.automation)
        .shortName(R.string.automation_short)
        .showInList { config.APS }
        .neverVisible(!config.APS)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.automation_description),
    ownPreferences = listOf(AutomationStringKey::class.java),
    aapsLogger, rh, preferences
), Automation {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val automationEvents = ArrayList<AutomationEventObject>()
    var executionLog: MutableList<String> = ArrayList()
    var btConnects: MutableList<EventBTChange> = ArrayList()

    private var handler: Handler? = null
    private var refreshLoop: Runnable

    companion object {

        const val EMPTY_EVENT =
            "{\"title\":\"Low\",\"enabled\":true,\"trigger\":\"{\\\"type\\\":\\\"TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerBg\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"bg\\\\\\\":4,\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\",\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\"}}\\\",\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerDelta\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"value\\\\\\\":-0.1,\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\",\\\\\\\"deltaType\\\\\\\":\\\\\\\"DELTA\\\\\\\",\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\"}}\\\"]}}\",\"actions\":[\"{\\\"type\\\":\\\"ActionStartTempTarget\\\",\\\"data\\\":{\\\"value\\\":8,\\\"units\\\":\\\"mmol\\\",\\\"durationInMinutes\\\":60}}\"]}"
    }

    init {
        refreshLoop = Runnable {
            processActions()
            handler?.postDelayed(refreshLoop, T.secs(150).msecs())
        }
    }

    override fun specialEnableCondition(): Boolean = !config.AAPSCLIENT

    override fun onStart() {
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
        locationServiceHelper.startService(context)

        super.onStart()
        loadFromSP()
        handler?.postDelayed(refreshLoop, T.mins(1).msecs())

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ e ->
                           if (e.isChanged(StringKey.AutomationLocation.key)) {
                               locationServiceHelper.stopService(context)
                               locationServiceHelper.startService(context)
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationDataChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ storeToSP() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventLocationChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.AUTOMATION, "Grabbed location: ${it.location.latitude} ${it.location.longitude} Provider: ${it.location.provider}")
                           processActions()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ processActions() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ processActions() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBTChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.AUTOMATION, "Grabbed new BT event: $it")
                           btConnects.add(it)
                           processActions()
                       }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
        locationServiceHelper.stopService(context)
        super.onStop()
    }

    private fun storeToSP() {
        val array = JSONArray()
        val iterator = synchronized(this) { automationEvents.toMutableList().iterator() }
        try {
            while (iterator.hasNext()) {
                val event = iterator.next()
                array.put(JSONObject(event.toJSON()))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        preferences.put(AutomationStringKey.AutomationEvents, array.toString())
    }

    @Synchronized
    private fun loadFromSP() {
        automationEvents.clear()
        val data = preferences.get(AutomationStringKey.AutomationEvents)
        if (data != "")
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val event = AutomationEventObject(injector).fromJSON(o.toString())
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        else
            automationEvents.add(AutomationEventObject(injector).fromJSON(EMPTY_EVENT))
    }

    internal fun processActions() {
        if (!config.appInitialized) return
        /**
         * Changed to false if some condition prevents automation from running.
         * In this case only system automations are enabled.
         */
        var commonEventsEnabled = true
        /*
         * Running mode must report running to process automation events.
         */
        if (loop.runningMode.isSuspended() || !loop.runningMode.isLoopRunning()) {
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

        storeToSP() // save last run time
    }

    override fun processEvent(someEvent: AutomationEvent) {
        val event = someEvent as AutomationEventObject
        if (event.canRun() && event.preconditionCanRun()) {
            val actions = event.actions
            for (action in actions) {
                action.title = event.title
                if (action.isValid()) {
                    action.doAction(object : Callback() {
                        override fun run() {
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
                        }
                    })
                    SystemClock.sleep(3000)
                } else {
                    executionLog.add("Invalid action: ${action.shortDescription()}")
                    aapsLogger.debug(LTag.AUTOMATION, "Invalid action: ${action.shortDescription()}")
                    rxBus.send(EventAutomationUpdateGui())
                }
            }
            SystemClock.sleep(1100)
            event.lastRun = dateUtil.now()
            if (event.autoRemove) remove(event)
        }
    }

    @Synchronized
    fun add(event: AutomationEventObject) {
        automationEvents.add(event)
        rxBus.send(EventAutomationDataChanged())
    }

    @Synchronized
    fun addIfNotExists(event: AutomationEventObject) {
        for (e in automationEvents) {
            if (event.title == e.title) return
        }
        automationEvents.add(event)
        rxBus.send(EventAutomationDataChanged())
    }

    @Synchronized
    fun removeIfExists(event: AutomationEvent) {
        for (e in automationEvents.reversed()) {
            if (event.title == e.title) {
                automationEvents.remove(e)
                rxBus.send(EventAutomationDataChanged())
            }
        }
    }

    @Synchronized
    fun set(event: AutomationEventObject, index: Int) {
        automationEvents[index] = event
        rxBus.send(EventAutomationDataChanged())
    }

    @Synchronized
    fun remove(event: AutomationEvent) {
        automationEvents.remove(event)
    }

    fun at(index: Int) = automationEvents[index]

    fun size() = automationEvents.size

    @Synchronized
    fun swap(fromPosition: Int, toPosition: Int) {
        Collections.swap(automationEvents, fromPosition, toPosition)
    }

    override fun userEvents(): List<AutomationEvent> {
        val list = mutableListOf<AutomationEvent>()
        val iterator = synchronized(this) { automationEvents.toMutableList().iterator() }
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (event.userAction && event.isEnabled) list.add(event)
        }
        return list
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
            ActionSMBChange(injector)
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
     * Generate reminder via [app.aaps.plugins.automation.ui.TimerUtil]
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

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val entries = arrayOf<CharSequence>(
            rh.gs(R.string.use_passive_location),
            rh.gs(R.string.use_network_location),
            rh.gs(R.string.use_gps_location),
        )

        val entryValues = arrayOf<CharSequence>(
            "PASSIVE",
            "NETWORK",
            "GPS",
        )
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "automation_settings"
            title = rh.gs(app.aaps.core.ui.R.string.automation)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveListPreference(ctx = context, stringKey = StringKey.AutomationLocation, title = R.string.locationservice, entries = entries, entryValues = entryValues))
        }
    }
}
