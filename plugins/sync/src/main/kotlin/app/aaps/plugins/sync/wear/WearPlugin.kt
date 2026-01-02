package app.aaps.plugins.sync.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.receivers.Intents
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
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import app.aaps.shared.impl.extensions.safeQueryBroadcastReceivers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val preferences: Preferences,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val context: Context,
    private val dataHandlerMobile: DataHandlerMobile,
    private val dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper,
    private val config: Config
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .fragmentClass(WearFragment::class.java.name)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_watch)
        .pluginName(app.aaps.core.ui.R.string.wear)
        .shortName(R.string.wear_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_wear),
    aapsLogger, rh
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
                           if (!BolusProgressData.isSMB || preferences.get(BooleanKey.WearNotifyOnSmb)) {
                               if (isEnabled()) rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = BolusProgressData.percent, status = BolusProgressData.status)))
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
            .toObservable(EventWearUpdateTiles::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ dataHandlerMobile.sendUserActions() }, fabricPrivacy::logException)
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
        disposable += rxBus
            .toObservable(EventMobileToWear::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                // If there is a broadcast selected (ie.
                //  AAPSClient want pass data to AAPS
                //  AAPSClient2 want pass data to AAPS or AAPSClient 1
                // ) do it here as the data is prepared
                if (config.AAPSCLIENT && preferences.get(BooleanKey.WearBroadcastData)) broadcastData(it.payload)
            }
    }

    fun checkCustomWatchfacePreferences() {
        savedCustomWatchface?.let { cwf ->
            val cwfAuthorization = preferences.get(BooleanKey.WearCustomWatchfaceAuthorization)
            val cwfName = preferences.get(StringNonKey.WearCwfWatchfaceName)
            val authorVersion = preferences.get(StringNonKey.WearCwfAuthorVersion)
            val fileName = preferences.get(StringNonKey.WearCwfFileName)
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

    private fun broadcastData(payload: EventData) {
        // Identify and update source set before broadcast
        val client = if (config.AAPSCLIENT1) 1 else if (config.AAPSCLIENT2) 2 else throw UnsupportedOperationException()
        val dataToSend = when (payload) {
            is EventData.SingleBg -> payload.copy().apply { dataset = client }
            is EventData.Status   -> payload.copy().apply { dataset = client }
            else                  -> payload
        }
        broadcast(
            Intent(Intents.AAPS_CLIENT_WEAR_DATA)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply {
                    putInt(WearDataReceiver.CLIENT, if (config.AAPSCLIENT1) 1 else if (config.AAPSCLIENT2) 2 else throw UnsupportedOperationException())
                    putString(WearDataReceiver.DATA, dataToSend.serialize())
                })
        )
    }

    private fun broadcast(intent: Intent) {
        context.packageManager.safeQueryBroadcastReceivers(intent, 0).forEach { resolveInfo ->
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent, WearDataReceiver.PERMISSION)
                aapsLogger.debug(LTag.WEAR, "Sending broadcast " + intent.action + " to: " + it)
            }
        }
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null && requiredKey != "wear_wizard_settings" && requiredKey != "wear_custom_watchface_settings" && requiredKey != "wear_general_settings") return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "wear_settings"
            title = rh.gs(R.string.wear_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearControl, summary = R.string.wearcontrol_summary, title = R.string.wearcontrol_title))
            if (config.AAPSCLIENT)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearBroadcastData, summary = R.string.wear_broadcast_data_summary, title = R.string.wear_broadcast_data))
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "wear_wizard_settings"
                title = rh.gs(app.aaps.core.ui.R.string.wear_wizard_settings)
                summary = rh.gs(R.string.wear_wizard_settings_summary)
                //dependency = rh.gs(BooleanKey.WearControl.key)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearWizardBg, title = app.aaps.core.ui.R.string.bg_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearWizardTt, title = app.aaps.core.ui.R.string.tt_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearWizardTrend, title = app.aaps.core.ui.R.string.bg_trend_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearWizardCob, title = app.aaps.core.ui.R.string.treatments_wizard_cob_label))
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearWizardIob, title = app.aaps.core.ui.R.string.iob_label))
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "wear_custom_watchface_settings"
                title = rh.gs(R.string.wear_custom_watchface_settings)
                addPreference(
                    AdaptiveSwitchPreference(
                        ctx = context,
                        booleanKey = BooleanKey.WearCustomWatchfaceAuthorization,
                        summary = R.string.wear_custom_watchface_authorization_summary,
                        title = R.string.wear_custom_watchface_authorization_title
                    )
                )
            })
            addPreference(preferenceManager.createPreferenceScreen(context).apply {
                key = "wear_general_settings"
                title = rh.gs(R.string.wear_general_settings)
                addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.WearNotifyOnSmb, summary = R.string.wear_notifysmb_summary, title = R.string.wear_notifysmb_title))
            })
        }
    }
}