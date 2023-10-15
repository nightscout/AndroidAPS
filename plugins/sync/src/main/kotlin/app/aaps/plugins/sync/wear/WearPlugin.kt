package app.aaps.plugins.sync.wear

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventDismissBolusProgressIfRunning
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import dagger.android.HasAndroidInjector
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
    private val dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper

) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(WearFragment::class.java.name)
        .pluginIcon(app.aaps.core.main.R.drawable.ic_watch)
        .pluginName(app.aaps.core.ui.R.string.wear)
        .shortName(R.string.wear_shortname)
        .preferencesId(R.xml.pref_wear)
        .description(R.string.description_wear),
    aapsLogger, rh, injector
) {

    private val disposable = CompositeDisposable()

    var connectedDevice = "---"
    var savedCustomWatchface: CwfData? = null

    override fun onStart() {
        super.onStart()
        dataLayerListenerServiceMobileHelper.startService(context)
        disposable += rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventDismissBolusProgressIfRunning ->
                           event.resultSuccess?.let {
                               val status =
                                   if (it) rh.gs(app.aaps.core.ui.R.string.success)
                                   else rh.gs(R.string.no_success)
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
            .subscribe({
                           dataHandlerMobile.resendData("EventPreferenceChange")
                           checkCustomWatchfacePreferences()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.resendData("EventAutosensCalculationFinished") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.resendData("EventLoopUpdateGui") }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           it.customWatchfaceData?.let { cwf ->
                               if (!it.exportFile) {
                                   savedCustomWatchface = cwf
                                   checkCustomWatchfacePreferences()
                               }
                           }
                       }, fabricPrivacy::logException)
    }

    fun checkCustomWatchfacePreferences() {
        savedCustomWatchface?.let { cwf ->
            val cwfAuthorization = sp.getBoolean(app.aaps.core.utils.R.string.key_wear_custom_watchface_autorization, false)
            val cwfName = sp.getString(app.aaps.core.utils.R.string.key_wear_cwf_watchface_name, "")
            val authorVersion = sp.getString(app.aaps.core.utils.R.string.key_wear_cwf_author_version, "")
            val fileName = sp.getString(app.aaps.core.utils.R.string.key_wear_cwf_filename, "")
            var toUpdate = false
            CwfData("", cwf.metadata, mutableMapOf()).also {
                if (cwfAuthorization != cwf.metadata[CwfMetadataKey.CWF_AUTHORIZATION]?.toBooleanStrictOrNull()) {
                    it.metadata[CwfMetadataKey.CWF_AUTHORIZATION] = cwfAuthorization.toString()
                    toUpdate = true
                }
                if (cwfName == cwf.metadata[CwfMetadataKey.CWF_NAME] && authorVersion == cwf.metadata[CwfMetadataKey.CWF_AUTHOR_VERSION] && fileName != cwf.metadata[CwfMetadataKey.CWF_FILENAME]) {
                    it.metadata[CwfMetadataKey.CWF_FILENAME] = fileName
                    toUpdate = true
                }

                if (toUpdate)
                    rxBus.send(EventMobileToWear(EventData.ActionUpdateCustomWatchface(it)))
            }
        }
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
        dataLayerListenerServiceMobileHelper.stopService(context)
    }
}