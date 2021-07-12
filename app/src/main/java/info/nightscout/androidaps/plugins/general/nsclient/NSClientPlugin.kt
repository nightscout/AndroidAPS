package info.nightscout.androidaps.plugins.general.nsclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventAppExit
import info.nightscout.androidaps.events.EventChargingState
import info.nightscout.androidaps.events.EventNetworkChange
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.Config
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.data.AlarmAck
import info.nightscout.androidaps.plugins.general.nsclient.data.NSAlarm
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientNewLog
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientResend
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientStatus
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.plugins.general.nsclient.services.NSClientService
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper.fromHtml
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val sp: SP,
    private val nsClientReceiverDelegate: NsClientReceiverDelegate,
    private val config: Config,
    private val buildHelper: BuildHelper
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(NSClientFragment::class.java.name)
    .pluginIcon(R.drawable.ic_nightscout_syncs)
    .pluginName(R.string.nsclientinternal)
    .shortName(R.string.nsclientinternal_shortname)
    .preferencesId(R.xml.pref_nsclientinternal)
    .description(R.string.description_ns_client),
    aapsLogger, resourceHelper, injector
) {

    private val disposable = CompositeDisposable()
    var handler: Handler? = null
    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    var textLog = fromHtml("")
    var paused = false
    var autoscroll = false
    var status = ""
    var nsClientService: NSClientService? = null
    val isAllowed: Boolean
        get() = nsClientReceiverDelegate.allowed

    init {
        if (config.NSCLIENT) {
            pluginDescription.alwaysEnabled(true).visibleByDefault(true)
        }
        if (handler == null) {
            val handlerThread = HandlerThread(NSClientPlugin::class.java.simpleName + "Handler")
            handlerThread.start()
            handler = Handler(handlerThread.looper)
        }
    }

    override fun onStart() {
        paused = sp.getBoolean(R.string.key_nsclientinternal_paused, false)
        autoscroll = sp.getBoolean(R.string.key_nsclientinternal_autoscroll, true)
        val intent = Intent(context, NSClientService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()
        nsClientReceiverDelegate.grabReceiversState()
        disposable.add(rxBus
            .toObservable(EventNSClientStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventNSClientStatus ->
                status = event.getStatus(resourceHelper)
                rxBus.send(EventNSClientUpdateGUI())
            }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ if (nsClientService != null) context.unbindService(mConnection) }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventNSClientNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventNSClientNewLog ->
                addToLog(event)
                aapsLogger.debug(LTag.NSCLIENT, event.action + " " + event.logText)
            }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventNSClientResend::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resend(event.reason) }, fabricPrivacy::logException)
        )
    }

    override fun onStop() {
        context.applicationContext.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(resourceHelper.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_sync_use_absolute))?.isVisible = false
        } else {
            // APS or pumpControl mode
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_receive_profile_switch))?.isVisible = buildHelper.isEngineeringMode()
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_receive_insulin))?.isVisible = buildHelper.isEngineeringMode()
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_receive_carbs))?.isVisible = buildHelper.isEngineeringMode()
            preferenceFragment.findPreference<SwitchPreference>(resourceHelper.gs(R.string.key_ns_receive_temp_target))?.isVisible = buildHelper.isEngineeringMode()
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is disconnected")
            nsClientService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected")
            val mLocalBinder = service as NSClientService.LocalBinder
            @Suppress("UNNECESSARY_SAFE_CALL")
            nsClientService = mLocalBinder?.serviceInstance // is null when running in roboelectric
        }
    }

    @Synchronized fun clearLog() {
        handler?.post {
            synchronized(listLog) { listLog.clear() }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    @Synchronized private fun addToLog(ev: EventNSClientNewLog) {
        handler?.post {
            synchronized(listLog) {
                listLog.add(ev)
                // remove the first line if log is too large
                if (listLog.size >= Constants.MAX_LOG_LINES) {
                    listLog.removeAt(0)
                }
            }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    @Synchronized fun updateLog() {
        try {
            val newTextLog = StringBuilder()
            synchronized(listLog) {
                for (log in listLog) {
                    newTextLog.append(log.toPreparedHtml())
                }
            }
            textLog = fromHtml(newTextLog.toString())
        } catch (e: OutOfMemoryError) {
            ToastUtils.showToastInUiThread(context, rxBus, "Out of memory!\nStop using this phone !!!", R.raw.error)
        }
    }

    fun resend(reason: String) {
        nsClientService?.resend(reason)
    }

    fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_nsclientinternal_paused, newState)
        paused = newState
        rxBus.send(EventPreferenceChange(resourceHelper, R.string.key_nsclientinternal_paused))
    }

    fun url(): String = nsClientService?.nsURL ?: ""
    fun hasWritePermission(): Boolean = nsClientService?.hasWriteAuth ?: false

    fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled(PluginType.GENERAL)) return
        if (!sp.getBoolean(R.string.key_ns_upload, false)) {
            aapsLogger.debug(LTag.NSCLIENT, "Upload disabled. Message dropped")
            return
        }
        nsClientService?.sendAlarmAck(
            AlarmAck().also { ack ->
                ack.level = originalAlarm.level()
                ack.group = originalAlarm.group()
                ack.silenceTime = silenceTimeInMilliseconds
            })
    }

    fun updateLatestDateReceivedIfNewer(latestReceived: Long) {
        nsClientService?.let { if (latestReceived > it.latestDateInReceivedData) it.latestDateInReceivedData = latestReceived }
    }
}