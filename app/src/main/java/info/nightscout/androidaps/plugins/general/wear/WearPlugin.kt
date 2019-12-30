package info.nightscout.androidaps.plugins.general.wear

import android.content.Intent
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPlugin internal @Inject constructor(
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val mainApp: MainApp,
    private val loopPlugin: LoopPlugin
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(WearFragment::class.java.name)
    .pluginName(R.string.wear)
    .shortName(R.string.wear_shortname)
    .preferencesId(R.xml.pref_wear)
    .description(R.string.description_wear)
) {

    private val disposable = CompositeDisposable()
    override fun onStart() {
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(true, true, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(true, true, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventTempBasalChange? -> sendDataToWatch(true, true, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ sendDataToWatch(true, true, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventNewBasalProfile? -> sendDataToWatch(false, true, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventAutosensCalculationFinished? -> sendDataToWatch(true, true, true) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventPreferenceChange? ->
                // possibly new high or low mark
                resendDataToWatch()
                // status may be formated differently
                sendDataToWatch(true, false, false)
            }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventRefreshOverview? -> if (WatchUpdaterService.shouldReportLoopStatus(loopPlugin.isEnabled(PluginType.LOOP))) sendDataToWatch(true, false, false) }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventBolusRequested::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventBolusRequested ->
                val status = String.format(resourceHelper.gs(R.string.bolusrequested), event.amount)
                val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 0)
                intent.putExtra("progressstatus", status)
                mainApp.startService(intent)
            }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventDismissBolusProgressIfRunning ->
                if (event.result == null) return@subscribe
                val status: String = if (event.result.success) {
                    resourceHelper.gs(R.string.success)
                } else {
                    resourceHelper.gs(R.string.nosuccess)
                }
                val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 100)
                intent.putExtra("progressstatus", status)
                mainApp.startService(intent)
            }) { FabricPrivacy.logException(it) })
        disposable.add(rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ event: EventOverviewBolusProgress ->
                if (!event.isSMB() || sp.getBoolean("wear_notifySMB", true)) {
                    val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                    intent.putExtra("progresspercent", event.percent)
                    intent.putExtra("progressstatus", event.status)
                    mainApp.startService(intent)
                }
            }) { FabricPrivacy.logException(it) })
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private fun sendDataToWatch(status: Boolean, basals: Boolean, bgValue: Boolean) {
        //Log.d(TAG, "WR: WearPlugin:sendDataToWatch (status=" + status + ",basals=" + basals + ",bgValue=" + bgValue + ")");
        if (isEnabled(getType())) {
            // only start service when this plugin is enabled
            if (bgValue) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java))
            }
            if (basals) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BASALS))
            }
            if (status) {
                mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_STATUS))
            }
        }
    }

    fun resendDataToWatch() {
        //Log.d(TAG, "WR: WearPlugin:resendDataToWatch");
        mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_RESEND))
    }

    fun openSettings() {
        //Log.d(TAG, "WR: WearPlugin:openSettings");
        mainApp.startService(Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS))
    }

    fun requestNotificationCancel(actionstring: String?) { //Log.d(TAG, "WR: WearPlugin:requestNotificationCancel");
        val intent = Intent(mainApp, WatchUpdaterService::class.java)
            .setAction(WatchUpdaterService.ACTION_CANCEL_NOTIFICATION)
        intent.putExtra("actionstring", actionstring)
        mainApp.startService(intent)
    }

    fun requestActionConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        mainApp.startService(intent)
    }

    fun requestChangeConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(mainApp, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_CHANGECONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        mainApp.startService(intent)
    }
}