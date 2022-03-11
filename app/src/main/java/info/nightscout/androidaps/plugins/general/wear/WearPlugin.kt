package info.nightscout.androidaps.plugins.general.wear

import android.content.Context
import android.content.Intent
import dagger.Lazy
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.wear.wearintegration.WatchUpdaterService
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val ctx: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val loop: Loop,
    private val rxBus: RxBus,
    private val actionStringHandler: Lazy<ActionStringHandler>

) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(WearFragment::class.java.name)
    .pluginIcon(R.drawable.ic_watch)
    .pluginName(R.string.wear)
    .shortName(R.string.wear_shortname)
    .preferencesId(R.xml.pref_wear)
    .description(R.string.description_wear),
    aapsLogger, rh, injector
) {

    private val disposable = CompositeDisposable()
    override fun onStart() {
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = false) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = false, basals = true, bgValue = false) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendDataToWatch(status = true, basals = true, bgValue = true) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                // possibly new high or low mark
                resendDataToWatch()
                // status may be formatted differently
                sendDataToWatch(status = true, basals = false, bgValue = false)
            }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                if (WatchUpdaterService.shouldReportLoopStatus((loop as PluginBase).isEnabled()))
                    sendDataToWatch(status = true, basals = false, bgValue = false)
            }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventBolusRequested::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventBolusRequested ->
                val status = rh.gs(R.string.bolusrequested, event.amount)
                val intent = Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 0)
                intent.putExtra("progressstatus", status)
                ctx.startService(intent)
            }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventDismissBolusProgressIfRunning ->
                if (event.result == null) return@subscribe
                val status: String = if (event.result!!.success) {
                    rh.gs(R.string.success)
                } else {
                    rh.gs(R.string.nosuccess)
                }
                val intent = Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                intent.putExtra("progresspercent", 100)
                intent.putExtra("progressstatus", status)
                ctx.startService(intent)
            }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventOverviewBolusProgress ->
                if (!event.isSMB() || sp.getBoolean("wear_notifySMB", true)) {
                    val intent = Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BOLUSPROGRESS)
                    intent.putExtra("progresspercent", event.percent)
                    intent.putExtra("progressstatus", event.status)
                    ctx.startService(intent)
                }
            }, fabricPrivacy::logException))
        actionStringHandler.get().setup()
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
        actionStringHandler.get().tearDown()
    }

    private fun sendDataToWatch(status: Boolean, basals: Boolean, bgValue: Boolean) {
        //Log.d(TAG, "WR: WearPlugin:sendDataToWatch (status=" + status + ",basals=" + basals + ",bgValue=" + bgValue + ")");
        if (isEnabled(getType())) {
            // only start service when this plugin is enabled
            if (bgValue) {
                ctx.startService(Intent(ctx, WatchUpdaterService::class.java))
            }
            if (basals) {
                ctx.startService(Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_BASALS))
            }
            if (status) {
                ctx.startService(Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_STATUS))
            }
        }
    }

    fun resendDataToWatch() {
        //Log.d(TAG, "WR: WearPlugin:resendDataToWatch");
        ctx.startService(Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_RESEND))
    }

    fun openSettings() {
        //Log.d(TAG, "WR: WearPlugin:openSettings");
        ctx.startService(Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_OPEN_SETTINGS))
    }

    fun requestNotificationCancel(actionString: String?) { //Log.d(TAG, "WR: WearPlugin:requestNotificationCancel");
        val intent = Intent(ctx, WatchUpdaterService::class.java)
            .setAction(WatchUpdaterService.ACTION_CANCEL_NOTIFICATION)
        intent.putExtra("actionstring", actionString)
        ctx.startService(intent)
    }

    fun requestActionConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_ACTIONCONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        ctx.startService(intent)
    }

    fun requestChangeConfirmation(title: String, message: String, actionString: String) {
        val intent = Intent(ctx, WatchUpdaterService::class.java).setAction(WatchUpdaterService.ACTION_SEND_CHANGECONFIRMATIONREQUEST)
        intent.putExtra("title", title)
        intent.putExtra("message", message)
        intent.putExtra("actionstring", actionString)
        ctx.startService(intent)
    }
}