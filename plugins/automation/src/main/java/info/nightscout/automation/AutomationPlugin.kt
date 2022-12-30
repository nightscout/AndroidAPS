package info.nightscout.automation

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.automation.actions.Action
import info.nightscout.automation.actions.ActionAlarm
import info.nightscout.automation.actions.ActionCarePortalEvent
import info.nightscout.automation.actions.ActionNotification
import info.nightscout.automation.actions.ActionProfileSwitch
import info.nightscout.automation.actions.ActionProfileSwitchPercent
import info.nightscout.automation.actions.ActionRunAutotune
import info.nightscout.automation.actions.ActionSendSMS
import info.nightscout.automation.actions.ActionStartTempTarget
import info.nightscout.automation.actions.ActionStopProcessing
import info.nightscout.automation.actions.ActionStopTempTarget
import info.nightscout.automation.elements.Comparator
import info.nightscout.automation.elements.InputDelta
import info.nightscout.automation.events.EventAutomationDataChanged
import info.nightscout.automation.events.EventAutomationUpdateGui
import info.nightscout.automation.events.EventLocationChange
import info.nightscout.automation.services.LocationServiceHelper
import info.nightscout.automation.triggers.Trigger
import info.nightscout.automation.triggers.TriggerAutosensValue
import info.nightscout.automation.triggers.TriggerBTDevice
import info.nightscout.automation.triggers.TriggerBg
import info.nightscout.automation.triggers.TriggerBolusAgo
import info.nightscout.automation.triggers.TriggerCOB
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerDelta
import info.nightscout.automation.triggers.TriggerIob
import info.nightscout.automation.triggers.TriggerLocation
import info.nightscout.automation.triggers.TriggerProfilePercent
import info.nightscout.automation.triggers.TriggerPumpLastConnection
import info.nightscout.automation.triggers.TriggerRecurringTime
import info.nightscout.automation.triggers.TriggerTempTarget
import info.nightscout.automation.triggers.TriggerTempTargetValue
import info.nightscout.automation.triggers.TriggerTime
import info.nightscout.automation.triggers.TriggerTimeRange
import info.nightscout.automation.triggers.TriggerWifiSsid
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.Loop
import info.nightscout.interfaces.automation.Automation
import info.nightscout.interfaces.automation.AutomationEvent
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.queue.Callback
import info.nightscout.automation.ui.TimerUtil
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventBTChange
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class AutomationPlugin @Inject constructor(
    injector: HasAndroidInjector,
    rh: ResourceHelper,
    private val context: Context,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val rxBus: RxBus,
    private val constraintChecker: Constraints,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val config: Config,
    private val locationServiceHelper: LocationServiceHelper,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val timerUtil: TimerUtil
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutomationFragment::class.qualifiedName)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_automation)
        .pluginName(R.string.automation)
        .shortName(R.string.automation_short)
        .showInList(config.APS)
        .neverVisible(!config.APS)
        .preferencesId(R.xml.pref_automation)
        .description(R.string.automation_description),
    aapsLogger, rh, injector
), Automation {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val keyAutomationEvents = "AUTOMATION_EVENTS"

    private val automationEvents = ArrayList<AutomationEventObject>()
    var executionLog: MutableList<String> = ArrayList()
    var btConnects: MutableList<EventBTChange> = ArrayList()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {

        const val event =
            "{\"title\":\"Low\",\"enabled\":true,\"trigger\":\"{\\\"type\\\":\\\"TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerBg\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"bg\\\\\\\":4,\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\",\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\"}}\\\",\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"TriggerDelta\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"value\\\\\\\":-0.1,\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\",\\\\\\\"deltaType\\\\\\\":\\\\\\\"DELTA\\\\\\\",\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\"}}\\\"]}}\",\"actions\":[\"{\\\"type\\\":\\\"ActionStartTempTarget\\\",\\\"data\\\":{\\\"value\\\":8,\\\"units\\\":\\\"mmol\\\",\\\"durationInMinutes\\\":60}}\"]}"
    }

    init {
        refreshLoop = Runnable {
            processActions()
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun specialEnableCondition(): Boolean = !config.NSCLIENT

    override fun onStart() {
        locationServiceHelper.startService(context)

        super.onStart()
        loadFromSP()
        handler.postDelayed(refreshLoop, T.mins(1).msecs())

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ e ->
                           if (e.isChanged(rh.gs(R.string.key_location))) {
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
        handler.removeCallbacks(refreshLoop)
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

        sp.putString(keyAutomationEvents, array.toString())
    }

    @Synchronized
    private fun loadFromSP() {
        automationEvents.clear()
        val data = sp.getString(keyAutomationEvents, "")
        if (data != "")
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val event = AutomationEventObject(injector).fromJSON(o.toString(), i)
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        else
            automationEvents.add(AutomationEventObject(injector).fromJSON(event, 0))
    }

    internal fun processActions() {
        var commonEventsEnabled = true
        if (loop.isSuspended || !(loop as PluginBase).isEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop deactivated")
            executionLog.add(rh.gs(info.nightscout.core.ui.R.string.loopisdisabled))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        if (loop.isDisconnected || !(loop as PluginBase).isEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop disconnected")
            executionLog.add(rh.gs(info.nightscout.core.ui.R.string.disconnected))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        if (activePlugin.activePump.isSuspended()) {
            aapsLogger.debug(LTag.AUTOMATION, "Pump suspended")
            executionLog.add(rh.gs(info.nightscout.core.ui.R.string.waitingforpump))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        val enabled = constraintChecker.isAutomationEnabled()
        if (!enabled.value()) {
            executionLog.add(enabled.getMostLimitedReasons(aapsLogger))
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

        // we cannot detect connected BT devices
        // so let's collect all connection/disconnections between 2 runs of processActions()
        // TriggerBTDevice can pick up and process these events
        // after processing clear events to prevent repeated actions
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
        event.position = automationEvents.size - 1
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
    fun removeAt(index: Int) {
        if (index >= 0 && index < automationEvents.size) {
            automationEvents.removeAt(index)
            rxBus.send(EventAutomationDataChanged())
        }
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
        return listOf(
            //ActionLoopDisable(injector),
            //ActionLoopEnable(injector),
            //ActionLoopResume(injector),
            //ActionLoopSuspend(injector),
            ActionStopProcessing(injector),
            ActionStartTempTarget(injector),
            ActionStopTempTarget(injector),
            ActionNotification(injector),
            ActionAlarm(injector),
            ActionCarePortalEvent(injector),
            ActionProfileSwitchPercent(injector),
            ActionProfileSwitch(injector),
            ActionRunAutotune(injector),
            ActionSendSMS(injector)
        )
    }

    fun getTriggerDummyObjects(): List<Trigger> {
        return listOf(
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
        )
    }

    /**
     * Generate reminder via [info.nightscout.interfaces.utils.TimerUtil]
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
            title = rh.gs(info.nightscout.core.ui.R.string.bolus_advisor)
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
            title = rh.gs(info.nightscout.core.ui.R.string.bolus_advisor)
        }
        removeIfExists(event)
    }

    override fun scheduleAutomationEventBolusReminder() {
        val event = AutomationEventObject(injector).apply {
            title = rh.gs(info.nightscout.core.ui.R.string.bolus_reminder)
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
            title = rh.gs(info.nightscout.core.ui.R.string.bolus_reminder)
        }
        removeIfExists(event)
    }
}
