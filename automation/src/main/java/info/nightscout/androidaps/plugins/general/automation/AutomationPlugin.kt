package info.nightscout.androidaps.plugins.general.automation

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.events.EventBTChange
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventLocationChange
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.services.LocationServiceHelper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
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
    private val constraintChecker: ConstraintChecker,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val config: Config,
    private val locationServiceHelper: LocationServiceHelper,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutomationFragment::class.qualifiedName)
        .pluginIcon(R.drawable.ic_automation)
        .pluginName(R.string.automation)
        .shortName(R.string.automation_short)
        .showInList(config.APS)
        .neverVisible(!config.APS)
        .preferencesId(R.xml.pref_automation)
        .description(R.string.automation_description),
    aapsLogger, rh, injector
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val keyAutomationEvents = "AUTOMATION_EVENTS"

    private val automationEvents = ArrayList<AutomationEvent>()
    var executionLog: MutableList<String> = ArrayList()
    var btConnects: MutableList<EventBTChange> = ArrayList()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {

        const val event =
            "{\"title\":\"Low\",\"enabled\":true,\"trigger\":\"{\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBg\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"bg\\\\\\\":4,\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\",\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\"}}\\\",\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDelta\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"value\\\\\\\":-0.1,\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\",\\\\\\\"deltaType\\\\\\\":\\\\\\\"DELTA\\\\\\\",\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\"}}\\\"]}}\",\"actions\":[\"{\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStartTempTarget\\\",\\\"data\\\":{\\\"value\\\":8,\\\"units\\\":\\\"mmol\\\",\\\"durationInMinutes\\\":60}}\"]}"
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
                           if (e.isChanged(rh, R.string.key_location)) {
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
                    val event = AutomationEvent(injector).fromJSON(o.toString(), i)
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        else
            automationEvents.add(AutomationEvent(injector).fromJSON(event, 0))
    }

    internal fun processActions() {
        var commonEventsEnabled = true
        if (loop.isSuspended || !(loop as PluginBase).isEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop deactivated")
            executionLog.add(rh.gs(R.string.loopisdisabled))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        if (loop.isDisconnected || !(loop as PluginBase).isEnabled()) {
            aapsLogger.debug(LTag.AUTOMATION, "Loop disconnected")
            executionLog.add(rh.gs(R.string.disconnected))
            rxBus.send(EventAutomationUpdateGui())
            commonEventsEnabled = false
        }
        if (activePlugin.activePump.isSuspended()) {
            aapsLogger.debug(LTag.AUTOMATION, "Pump suspended")
            executionLog.add(rh.gs(R.string.waitingforpump))
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

    fun processEvent(event: AutomationEvent) {
        if (event.trigger.shouldRun() && event.getPreconditions().shouldRun()) {
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
    fun add(event: AutomationEvent) {
        automationEvents.add(event)
        event.position = automationEvents.size - 1
        rxBus.send(EventAutomationDataChanged())
    }

    @Synchronized
    fun addIfNotExists(event: AutomationEvent) {
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
    fun set(event: AutomationEvent, index: Int) {
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

    fun userEvents(): List<AutomationEvent> {
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
}
