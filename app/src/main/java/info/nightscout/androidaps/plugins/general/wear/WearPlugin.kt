package info.nightscout.androidaps.plugins.general.wear

import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventMobileToWear
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.wear.wearintegration.DataHandlerMobile
import info.nightscout.androidaps.plugins.general.wear.wearintegration.DataLayerListenerServiceMobileHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val sp: SP,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val context: Context,
    private val dataHandlerMobile: DataHandlerMobile,
    val dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper

) : PluginBase(
    PluginDescription()
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

    var connectedDevice = "---"

    override fun onStart() {
        super.onStart()
        dataLayerListenerServiceMobileHelper.startService(context)
        disposable += rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventDismissBolusProgressIfRunning ->
                           event.result?.let {
                               val status =
                                   if (it.success) rh.gs(R.string.success)
                                   else rh.gs(R.string.nosuccess)
                               if (isEnabled()) rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = 100, status = status)))
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventOverviewBolusProgress ->
                           if (!event.isSMB() || sp.getBoolean("wear_notifySMB", true)) {
                               if (isEnabled()) rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = event.percent, status = event.status)))
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.resendData("EventPreferenceChange") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.resendData("EventAutosensCalculationFinished") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.resendData("EventLoopUpdateGui") }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
        dataLayerListenerServiceMobileHelper.stopService(context)
    }
}