package info.nightscout.plugins.sync.nsclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.text.Spanned
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.nsclient.NSAlarm
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.source.DoingOwnUploadSource
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.interfaces.sync.Sync
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.HtmlHelper.fromHtml
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.NSClientFragment
import info.nightscout.plugins.sync.nsShared.events.EventNSClientResend
import info.nightscout.plugins.sync.nsShared.events.EventNSClientStatus
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGUI
import info.nightscout.plugins.sync.nsclient.data.AlarmAck
import info.nightscout.plugins.sync.nsclient.services.NSClientService
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventAppExit
import info.nightscout.rx.events.EventChargingState
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.events.EventNetworkChange
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.events.EventSWSyncStatus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NSClientPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val sp: SP,
    private val nsClientReceiverDelegate: NsClientReceiverDelegate,
    private val config: Config,
    private val dataSyncSelector: DataSyncSelector,
    private val uiInteraction: UiInteraction,
    private val activePlugin: ActivePlugin
) : NsClient, Sync, PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(NSClientFragment::class.java.name)
        .pluginIcon(R.drawable.ic_nightscout_syncs)
        .pluginName(R.string.ns_client)
        .shortName(R.string.ns_client_short_name)
        .preferencesId(R.xml.pref_ns_client)
        .description(R.string.description_ns_client),
    aapsLogger, rh, injector
) {

    private val disposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val listLog: MutableList<EventNSClientNewLog> = ArrayList()
    override var status = ""
    override var nsClientService: NSClientService? = null
    val isAllowed: Boolean
        get() = nsClientReceiverDelegate.allowed
    val blockingReason: String
        get() = nsClientReceiverDelegate.blockingReason

    override fun onStart() {
        context.bindService(Intent(context, NSClientService::class.java), mConnection, Context.BIND_AUTO_CREATE)
        super.onStart()
        nsClientReceiverDelegate.grabReceiversState()
        disposable += rxBus
            .toObservable(EventNSClientStatus::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event ->
                               status = event.getStatus(context)
                               rxBus.send(EventNSClientUpdateGUI())
                               // Pass to setup wizard
                               rxBus.send(EventSWSyncStatus(event.getStatus(context)))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNetworkChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ if (nsClientService != null) context.unbindService(mConnection) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientNewLog::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventNSClientNewLog ->
                           addToLog(event)
                           aapsLogger.debug(LTag.NSCLIENT, event.action + " " + event.logText)
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventChargingState::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ ev -> nsClientReceiverDelegate.onStatusEvent(ev) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientResend::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event -> resend(event.reason) }, fabricPrivacy::logException)
    }

    override fun onStop() {
        context.applicationContext.unbindService(mConnection)
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            preferenceFragment.findPreference<PreferenceScreen>(rh.gs(R.string.ns_sync_options))?.isVisible = false

            preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_create_announcements_from_errors))?.isVisible = false
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_create_announcements_from_carbs_req))?.isVisible = false
        }
        preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_ns_receive_tbr_eb))?.isVisible = config.isEngineeringMode()
        if (activePlugin.activeBgSource is DoingOwnUploadSource) {
            preferenceFragment.findPreference<SwitchPreference>(rh.gs(R.string.key_do_ns_upload))?.isVisible = false
        }
    }

    override val hasWritePermission: Boolean get() = nsClientService?.hasWriteAuth ?: false
    override val connected: Boolean get() = nsClientService?.isConnected ?: false

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is disconnected")
            nsClientService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            aapsLogger.debug(LTag.NSCLIENT, "Service is connected")
            val mLocalBinder = service as NSClientService.LocalBinder?
            nsClientService = mLocalBinder?.serviceInstance // is null when running in roboelectric
        }
    }

    override fun clearLog() {
        handler.post {
            synchronized(listLog) { listLog.clear() }
            rxBus.send(EventNSClientUpdateGUI())
        }
    }

    private fun addToLog(ev: EventNSClientNewLog) {
        synchronized(listLog) {
            listLog.add(ev)
            // remove the first line if log is too large
            if (listLog.size >= Constants.MAX_LOG_LINES) {
                listLog.removeAt(0)
            }
        }
        rxBus.send(EventNSClientUpdateGUI())
    }

    override fun textLog(): Spanned {
        try {
            val newTextLog = StringBuilder()
            synchronized(listLog) {
                for (log in listLog) newTextLog.append(log.toPreparedHtml())
            }
            return fromHtml(newTextLog.toString())
        } catch (e: OutOfMemoryError) {
            uiInteraction.showToastAndNotification(context, "Out of memory!\nStop using this phone !!!", R.raw.error)
        }
        return fromHtml("")
    }

    override fun resend(reason: String) {
        nsClientService?.resend(reason)
    }

    override fun pause(newState: Boolean) {
        sp.putBoolean(R.string.key_ns_client_paused, newState)
        rxBus.send(EventPreferenceChange(rh.gs(R.string.key_ns_client_paused)))
    }

    override val version: NsClient.Version
        get() = NsClient.Version.V1

    override val address: String get() = nsClientService?.nsURL ?: ""

    override fun handleClearAlarm(originalAlarm: NSAlarm, silenceTimeInMilliseconds: Long) {
        if (!isEnabled()) return
        if (!sp.getBoolean(R.string.key_ns_upload, true)) {
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

    override fun updateLatestBgReceivedIfNewer(latestReceived: Long) {
        nsClientService?.let { if (latestReceived > it.latestDateInReceivedData) it.latestDateInReceivedData = latestReceived }
    }

    override fun updateLatestTreatmentReceivedIfNewer(latestReceived: Long) {
        nsClientService?.let { if (latestReceived > it.latestDateInReceivedData) it.latestDateInReceivedData = latestReceived }
    }

    override fun resetToFullSync() {
        dataSyncSelector.resetToNextFullSync()
    }
}