package app.aaps.plugins.sync.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.compose.WearComposeContent
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import app.aaps.shared.impl.extensions.safeQueryBroadcastReceivers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    preferences: Preferences,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val context: Context,
    private val dataHandlerMobile: DataHandlerMobile,
    private val dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper,
    private val config: Config,
    private val dateUtil: DateUtil,
    private val versionCheckerUtils: VersionCheckerUtils,
    private val bolusProgressData: BolusProgressData,
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SYNC)
        .icon(Icons.Default.Watch)
        .pluginName(app.aaps.core.ui.R.string.wear)
        .shortName(R.string.wear_shortname)
        .description(R.string.description_wear)
        .composeContent { plugin ->
            WearComposeContent(
            )
        },
    aapsLogger = aapsLogger, rh = rh, preferences = preferences
) {

    private val disposable = CompositeDisposable()
    private var scope: CoroutineScope? = null

    private val _connectedDevice = MutableStateFlow<String?>(null)
    val connectedDevice: StateFlow<String?> = _connectedDevice.asStateFlow()

    private val _savedCustomWatchface = MutableStateFlow<CwfData?>(null)
    val savedCustomWatchface: StateFlow<CwfData?> = _savedCustomWatchface.asStateFlow()

    fun updateConnectedDevice(deviceName: String?) {
        _connectedDevice.value = deviceName
    }

    fun updateSavedCustomWatchface(cwfData: CwfData?) {
        _savedCustomWatchface.value = cwfData
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        dataLayerListenerServiceMobileHelper.startService(context)
        bolusProgressData.state
            .drop(1) // Skip initial null emission on collection start
            .onEach { state ->
                if (isEnabled()) {
                    if (state != null) {
                        if (!state.isSMB || preferences.get(BooleanKey.WearNotifyOnSmb)) {
                            rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = state.percent, status = state.status)))
                        }
                    } else {
                        // Bolus ended — send 100% to clear wear display
                        rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = 100, status = "")))
                    }
                }
            }
            .launchIn(newScope)
        merge(
            // Preferences sent to watch via resendData()
            preferences.observe(BooleanKey.WearControl).drop(1).map {},
            preferences.observe(IntKey.OverviewBolusPercentage).drop(1).map {},
            preferences.observe(IntKey.SafetyMaxCarbs).drop(1).map {},
            preferences.observe(DoubleKey.SafetyMaxBolus).drop(1).map {},
            preferences.observe(DoubleKey.OverviewInsulinButtonIncrement1).drop(1).map {},
            preferences.observe(DoubleKey.OverviewInsulinButtonIncrement2).drop(1).map {},
            preferences.observe(IntKey.OverviewCarbsButtonIncrement1).drop(1).map {},
            preferences.observe(IntKey.OverviewCarbsButtonIncrement2).drop(1).map {},
            // Custom watchface preferences
            preferences.observe(BooleanKey.WearCustomWatchfaceAuthorization).drop(1).map {},
            preferences.observe(StringNonKey.WearCwfWatchfaceName).drop(1).map {},
            preferences.observe(StringNonKey.WearCwfAuthorVersion).drop(1).map {},
            preferences.observe(StringNonKey.WearCwfFileName).drop(1).map {},
        ).onEach {
            dataHandlerMobile.resendData("PreferenceChange")
            checkCustomWatchfacePreferences()
        }.launchIn(newScope)
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
                                   _savedCustomWatchface.value = cwf
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
        _savedCustomWatchface.value?.let { cwf ->
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
        scope?.cancel()
        scope = null
        disposable.clear()
        super.onStop()
        dataLayerListenerServiceMobileHelper.stopService(context)
    }

    private fun broadcastData(payload: EventData) {
        // Identify and update source set before broadcast
        val client = if (config.AAPSCLIENT1) 1 else if (config.AAPSCLIENT2) 2 else if (config.AAPSCLIENT3) 3 else throw UnsupportedOperationException()
        val dataToSend = when (payload) {
            is EventData.SingleBg -> payload.copy().apply { dataset = client }
            is EventData.Status   -> payload.copy().apply { dataset = client }
            else                  -> payload
        }
        broadcast(
            Intent(Intents.AAPS_CLIENT_WEAR_DATA)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(Bundle().apply {
                    putInt(WearDataReceiver.CLIENT, if (config.AAPSCLIENT1) 1 else if (config.AAPSCLIENT2) 2 else if (config.AAPSCLIENT3) 3 else throw UnsupportedOperationException())
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

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "wear_settings",
        titleResId = app.aaps.core.ui.R.string.wear,
        items = listOf(
            BooleanKey.WearControl,
            BooleanKey.WearBroadcastData,
            PreferenceSubScreenDef(
                key = "wear_wizard_settings",
                titleResId = app.aaps.core.ui.R.string.wear_wizard_settings,
                summaryResId = R.string.wear_wizard_settings_summary,
                items = listOf(
                    BooleanKey.WearWizardBg,
                    BooleanKey.WearWizardTt,
                    BooleanKey.WearWizardTrend,
                    BooleanKey.WearWizardCob,
                    BooleanKey.WearWizardIob
                )
            ),
            PreferenceSubScreenDef(
                key = "wear_custom_watchface_settings",
                titleResId = R.string.wear_custom_watchface_settings,
                items = listOf(
                    BooleanKey.WearCustomWatchfaceAuthorization
                )
            ),
            PreferenceSubScreenDef(
                key = "wear_general_settings",
                titleResId = R.string.wear_general_settings,
                items = listOf(
                    BooleanKey.WearNotifyOnSmb
                )
            )
        ),
        icon = Icons.Default.Watch
    )
}