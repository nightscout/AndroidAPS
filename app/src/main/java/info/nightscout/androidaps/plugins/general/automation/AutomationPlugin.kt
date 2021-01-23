package info.nightscout.androidaps.plugins.general.automation

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventBTChange
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventLocationChange
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.services.LocationServiceHelper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

@Singleton
class AutomationPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    private val context: Context,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val loopPlugin: LoopPlugin,
    private val rxBus: RxBusWrapper,
    private val constraintChecker: ConstraintChecker,
    aapsLogger: AAPSLogger,
    private val config: Config,
    private val locationServiceHelper: LocationServiceHelper,
    private val dateUtil: DateUtil
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(AutomationFragment::class.qualifiedName)
    .pluginIcon(R.drawable.ic_automation)
    .pluginName(R.string.automation)
    .shortName(R.string.automation_short)
    .showInList(config.APS)
    .neverVisible(!config.APS)
    .alwaysEnabled(!config.APS)
    .preferencesId(R.xml.pref_automation)
    .description(R.string.automation_description),
    aapsLogger, resourceHelper, injector
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val keyAutomationEvents = "AUTOMATION_EVENTS"

    private val automationEvents = ArrayList<AutomationEvent>()
    var executionLog: MutableList<String> = ArrayList()
    var btConnects: MutableList<EventBTChange> = ArrayList()

    private val loopHandler: Handler = Handler(HandlerThread(AutomationPlugin::class.java.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    companion object {

        const val event = "{\"title\":\"Low\",\"enabled\":true,\"trigger\":\"{\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerBg\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"bg\\\\\\\":4,\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\",\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\"}}\\\",\\\"{\\\\\\\"type\\\\\\\":\\\\\\\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDelta\\\\\\\",\\\\\\\"data\\\\\\\":{\\\\\\\"value\\\\\\\":-0.1,\\\\\\\"units\\\\\\\":\\\\\\\"mmol\\\\\\\",\\\\\\\"deltaType\\\\\\\":\\\\\\\"DELTA\\\\\\\",\\\\\\\"comparator\\\\\\\":\\\\\\\"IS_LESSER\\\\\\\"}}\\\"]}}\",\"actions\":[\"{\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStartTempTarget\\\",\\\"data\\\":{\\\"value\\\":8,\\\"units\\\":\\\"mmol\\\",\\\"durationInMinutes\\\":60}}\"]}"
    }

    init {
        refreshLoop = Runnable {
            processActions()
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onStart() {
        locationServiceHelper.startService(context)

        super.onStart()
        loadFromSP()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ e ->
                if (e.isChanged(resourceHelper, R.string.key_location)) {
                    locationServiceHelper.stopService(context)
                    locationServiceHelper.startService(context)
                }
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutomationDataChanged::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ storeToSP() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventLocationChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ e ->
                e?.let {
                    aapsLogger.debug(LTag.AUTOMATION, "Grabbed location: $it.location.latitude $it.location.longitude Provider: $it.location.provider")
                    processActions()
                }
            }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ processActions() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ processActions() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ processActions() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventBTChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                aapsLogger.debug(LTag.AUTOMATION, "Grabbed new BT event: $it")
                btConnects.add(it)
                processActions()
            }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
        locationServiceHelper.stopService(context)
        super.onStop()
    }

    private fun storeToSP() {
        val array = JSONArray()
        try {
            for (event in automationEvents) {
                array.put(JSONObject(event.toJSON()))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        sp.putString(keyAutomationEvents, array.toString())
    }

    private fun loadFromSP() {
        automationEvents.clear()
        val data = sp.getString(keyAutomationEvents, "")
        if (data != "")
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val event = AutomationEvent(injector).fromJSON(o.toString())
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        else
            automationEvents.add(AutomationEvent(injector).fromJSON(event))
    }

    @Synchronized
    private fun processActions() {
        var userEventsEnabled = config.APS
        if (config.APS) {
            if (loopPlugin.isSuspended || !loopPlugin.isEnabled()) {
                aapsLogger.debug(LTag.AUTOMATION, "Loop deactivated")
                executionLog.add(resourceHelper.gs(R.string.smscommunicator_loopisdisabled))
                userEventsEnabled = false
            }
            val enabled = constraintChecker.isAutomationEnabled()
            if (!enabled.value()) {
                executionLog.add(enabled.getMostLimitedReasons(aapsLogger))
                userEventsEnabled = false
            }
        }

        aapsLogger.debug(LTag.AUTOMATION, "processActions")
        val iterator = automationEvents.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (event.isEnabled && event.shouldRun() && event.trigger.shouldRun() && event.getPreconditions().shouldRun()) {
                if (event.systemAction || userEventsEnabled) {
                    val actions = event.actions
                    for (action in actions) {
                        action.doAction(object : Callback() {
                            override fun run() {
                                val sb = StringBuilder()
                                sb.append(dateUtil.timeString(DateUtil.now()))
                                sb.append(" ")
                                sb.append(if (result.success) "☺" else "▼")
                                sb.append(" <b>")
                                sb.append(event.title)
                                sb.append(":</b> ")
                                sb.append(action.shortDescription())
                                sb.append(": ")
                                sb.append(result.comment)
                                executionLog.add(sb.toString())
                                aapsLogger.debug(LTag.AUTOMATION, "Executed: $sb")
                                rxBus.send(EventAutomationUpdateGui())
                            }
                        })
                    }
                    SystemClock.sleep(1100)
                    event.lastRun = DateUtil.now()
                    if (event.autoRemove) automationEvents.remove(event)
                }
            }
        }
        // we cannot detect connected BT devices
        // so let's collect all connection/disconnections between 2 runs of processActions()
        // TriggerBTDevice can pick up and process these events
        // after processing clear events to prevent repeated actions
        btConnects.clear()

        storeToSP() // save last run time
    }

    fun add(event: AutomationEvent) {
        automationEvents.add(event)
        rxBus.send(EventAutomationDataChanged())
    }

    fun addIfNotExists(event: AutomationEvent) {
        for (e in automationEvents) {
            if (event.title == e.title) return
        }
        automationEvents.add(event)
        rxBus.send(EventAutomationDataChanged())
    }

    fun set(event: AutomationEvent, index: Int) {
        automationEvents[index] = event
        rxBus.send(EventAutomationDataChanged())
    }

    fun removeAt(index: Int) {
        automationEvents.removeAt(index)
        rxBus.send(EventAutomationDataChanged())
    }

    fun at(index: Int) = automationEvents[index]

    fun size() = automationEvents.size

    fun swap(fromPosition: Int, toPosition: Int) {
        Collections.swap(automationEvents, fromPosition, toPosition)
        rxBus.send(EventAutomationDataChanged())
    }

    fun getActionDummyObjects(): List<Action> {
        return listOf(
            //ActionLoopDisable(injector),
            //ActionLoopEnable(injector),
            //ActionLoopResume(injector),
            //ActionLoopSuspend(injector),
            ActionStartTempTarget(injector),
            ActionStopTempTarget(injector),
            ActionNotification(injector),
            ActionAlarm(injector),
            ActionProfileSwitchPercent(injector),
            ActionProfileSwitch(injector),
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
            TriggerWifiSsid(injector),
            TriggerLocation(injector),
            TriggerAutosensValue(injector),
            TriggerBolusAgo(injector),
            TriggerPumpLastConnection(injector),
            TriggerBTDevice(injector)
        )
    }
}
