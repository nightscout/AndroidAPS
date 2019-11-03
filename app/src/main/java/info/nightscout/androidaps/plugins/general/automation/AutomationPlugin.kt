package info.nightscout.androidaps.plugins.general.automation

import android.content.Intent
import android.os.Build
import android.os.Handler
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventLocationChange
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.actions.*
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationDataChanged
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui
import info.nightscout.androidaps.plugins.general.automation.triggers.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.services.LocationService
import info.nightscout.androidaps.utils.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*

object AutomationPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutomationFragment::class.qualifiedName)
        .pluginName(R.string.automation)
        .shortName(R.string.automation_short)
        .preferencesId(R.xml.pref_automation)
        .description(R.string.automation_description)) {

    private val log = LoggerFactory.getLogger(L.AUTOMATION)
    private var disposable: CompositeDisposable = CompositeDisposable()

    private const val key_AUTOMATION_EVENTS = "AUTOMATION_EVENTS"

    val automationEvents = ArrayList<AutomationEvent>()
    var executionLog: MutableList<String> = ArrayList()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            processActions()
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onStart() {
        val context = MainApp.instance().applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(Intent(context, LocationService::class.java))
        else
            context.startService(Intent(context, LocationService::class.java))

        super.onStart()
        loadFromSP()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())

        disposable += RxBus
                .toObservable(EventPreferenceChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ e ->
                    if (e.isChanged(R.string.key_location)) {
                        context.stopService(Intent(context, LocationService::class.java))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            context.startForegroundService(Intent(context, LocationService::class.java))
                        else
                            context.startService(Intent(context, LocationService::class.java))
                    }
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventAutomationDataChanged::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ storeToSP() }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventLocationChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ e ->
                    e?.let {
                        log.debug("Grabbed location: $it.location.latitude $it.location.longitude Provider: $it.location.provider")
                        processActions()
                    }
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventChargingState::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ processActions() }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventNetworkChange::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ processActions() }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventAutosensCalculationFinished::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ processActions() }, {
                    FabricPrivacy.logException(it)
                })
    }

    override fun onStop() {
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
        val context = MainApp.instance().applicationContext
        context.stopService(Intent(context, LocationService::class.java))
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

        SP.putString(key_AUTOMATION_EVENTS, array.toString())
    }

    private fun loadFromSP() {
        automationEvents.clear()
        val data = SP.getString(key_AUTOMATION_EVENTS, "")
        if (data != "") {
            try {
                val array = JSONArray(data)
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val event = AutomationEvent().fromJSON(o.toString())
                    automationEvents.add(event)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    private fun processActions() {
        if (!isEnabled(PluginType.GENERAL))
            return
        if (LoopPlugin.getPlugin().isSuspended) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Loop deactivated")
            return
        }

        if (L.isEnabled(L.AUTOMATION))
            log.debug("processActions")
        for (event in automationEvents) {
            if (event.isEnabled && event.trigger.shouldRun() && event.preconditions.shouldRun()) {
                val actions = event.actions
                for (action in actions) {
                    action.doAction(object : Callback() {
                        override fun run() {
                            val sb = StringBuilder()
                            sb.append(DateUtil.timeString(DateUtil.now()))
                            sb.append(" ")
                            sb.append(if (result.success) "☺" else "X")
                            sb.append(" ")
                            sb.append(event.title)
                            sb.append(": ")
                            sb.append(action.shortDescription())
                            sb.append(": ")
                            sb.append(result.comment)
                            executionLog.add(sb.toString())
                            if (L.isEnabled(L.AUTOMATION))
                                log.debug("Executed: $sb")
                            RxBus.send(EventAutomationUpdateGui())
                        }
                    })
                }
                event.trigger.executed(DateUtil.now())
            }
        }
        storeToSP() // save last run time
    }

    fun getActionDummyObjects(): List<Action> {
        return listOf(
                //ActionLoopDisable(),
                //ActionLoopEnable(),
                //ActionLoopResume(),
                //ActionLoopSuspend(),
                ActionStartTempTarget(),
                ActionStopTempTarget(),
                ActionNotification(),
                ActionProfileSwitchPercent(),
                ActionProfileSwitch(),
                ActionSendSMS()
        )
    }

    fun getTriggerDummyObjects(): List<Trigger> {
        return listOf(
                TriggerTime(),
                TriggerRecurringTime(),
                TriggerTimeRange(),
                TriggerBg(),
                TriggerDelta(),
                TriggerIob(),
                TriggerCOB(),
                TriggerProfilePercent(),
                TriggerTempTarget(),
                TriggerWifiSsid(),
                TriggerLocation(),
                TriggerAutosensValue(),
                TriggerBolusAgo(),
                TriggerPumpLastConnection()
        )
    }

}
