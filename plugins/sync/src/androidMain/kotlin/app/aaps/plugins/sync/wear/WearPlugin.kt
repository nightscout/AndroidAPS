package app.aaps.plugins.sync.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Watch
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventNsClientStatusUpdated
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.events.EventWearUpdateTiles
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.utils.DeferredForegroundStart
import app.aaps.plugins.sync.SyncStrings
import app.aaps.plugins.sync.wear.WearPlugin.Companion.RESEND_DEBOUNCE
import app.aaps.plugins.sync.wear.compose.WearComposeContent
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import app.aaps.plugins.sync.wear.wearintegration.DataHandlerMobile
import app.aaps.plugins.sync.wear.wearintegration.DataLayerListenerServiceMobileHelper
import app.aaps.shared.impl.extensions.safeQueryBroadcastReceivers
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import dev.zacsweers.metro.IntKey as MetroIntKey

@SingleIn(AppScope::class)
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(350)
@Inject
class WearPlugin(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val rxBus: RxBus,
    private val context: Context,
    private val dataHandlerMobile: DataHandlerMobile,
    private val dataLayerListenerServiceMobileHelper: DataLayerListenerServiceMobileHelper,
    private val config: Config,
    private val bolusProgressData: BolusProgressData,
    private val persistenceLayer: PersistenceLayer,
    private val scenes: SceneAutomationApi,
    notificationManager: NotificationManager,
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.SYNC)
        .icon(Icons.Default.Watch)
        .pluginName(CoreUiStrings.wear)
        .description(SyncStrings.description_wear)
        .composeContent { WearComposeContent() },
    aapsLogger = aapsLogger, rh = rh, preferences = preferences, notificationManager = notificationManager
) {

    private var scope: CoroutineScope? = null
    private val deferredStart = DeferredForegroundStart()

    private val _connectedDevice = MutableStateFlow<String?>(null)
    val connectedDevice: StateFlow<String?> = _connectedDevice.asStateFlow()

    private val _savedCustomWatchface = MutableStateFlow<CwfData?>(null)
    val savedCustomWatchface: StateFlow<CwfData?> = _savedCustomWatchface.asStateFlow()

    /**
     * What the watch last said about Watch Face Push: whether it has it, and which face it holds.
     * Null until the watch reports, and again when it disconnects - a fresh watch must speak for
     * itself, since the answer differs from one watch to the next.
     */
    private val _watchFacePushStatus = MutableStateFlow<EventData.WatchFacePushStatus?>(null)
    val watchFacePushStatus: StateFlow<EventData.WatchFacePushStatus?> = _watchFacePushStatus.asStateFlow()

    fun updateConnectedDevice(deviceName: String?) {
        _connectedDevice.value = deviceName
        if (deviceName == null) _watchFacePushStatus.value = null
    }

    fun updateSavedCustomWatchface(cwfData: CwfData?) {
        _savedCustomWatchface.value = cwfData
    }

    override suspend fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        deferredStart.start { dataLayerListenerServiceMobileHelper.startService(context) }
        // Last percent actually sent to the watch. Starts at 100 = "nothing to clear": the empty-status
        // clear frame on state-null is only needed when the watch was left mid-progress (< 100). If the
        // driver already reported 100% ("Bolus delivered successfully"), sending the clear frame would
        // overwrite that text with "100% - " for the notification's 5 s dismiss window.
        var lastSentPercent = 100
        bolusProgressData.state
            .drop(1) // Skip initial null emission on collection start
            .collectResilient(newScope, aapsLogger, LTag.WEAR) { state ->
                if (isEnabled()) {
                    if (state != null) {
                        if (!state.isSMB || preferences.get(BooleanKey.WearNotifyOnSmb)) {
                            rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = state.percent, status = rh.gs(state.wearStatus))))
                            lastSentPercent = state.percent
                        }
                    } else if (lastSentPercent < 100) {
                        // Bolus ended without a 100% frame (cancelled/failed) — send 100% to clear wear display
                        rxBus.send(EventMobileToWear(EventData.BolusProgress(percent = 100, status = "")))
                        lastSentPercent = 100
                    }
                }
            }
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
            // Which Watch Face Format face the watch installs; the watch swaps its slot on arrival
            preferences.observe(StringKey.WearPushedWatchface).drop(1).map {},
        ).collectResilient(newScope, aapsLogger, LTag.WEAR) {
            dataHandlerMobile.resendData("PreferenceChange")
            checkCustomWatchfacePreferences()
        }
        resendRequests(newScope)
            .collectResilient(newScope, aapsLogger, LTag.WEAR) { reason -> dataHandlerMobile.resendData(reason) }
        // Refresh wear scene tile whenever the scene list changes (add / update / delete). The
        // active state goes too: an edited follow-up changes which button the tile offers.
        scenes.scenesFlow
            .drop(1) // Skip initial replay on subscribe
            .collectResilient(newScope, aapsLogger, LTag.WEAR) {
                dataHandlerMobile.sendScenes()
                dataHandlerMobile.sendActiveSceneState(scenes.hasSceneToStop())
            }
        // Push active-scene flag to wear so the tile can swap between scene list and STOP button
        scenes.activeFlow
            .collectResilient(newScope, aapsLogger, LTag.WEAR) { dataHandlerMobile.sendActiveSceneState(it) }
        rxBus.toFlow(EventWearUpdateTiles::class)
            .collectResilient(newScope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) { dataHandlerMobile.sendUserActions() }
        rxBus.toFlow(EventWearUpdateGui::class)
            .collectResilient(newScope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) { event ->
                // This one observed on aapsSchedulers.main, not io: it writes the watchface StateFlow the
                // UI reads and then walks preferences. newScope is IO, so the body is put back on main
                // rather than the collector being moved - the other subscriptions here want IO.
                withContext(Dispatchers.Main) {
                    event.customWatchfaceData?.let { cwf ->
                        if (!event.exportFile) {
                            _savedCustomWatchface.value = cwf
                            checkCustomWatchfacePreferences()
                        }
                    }
                    event.watchFacePushStatus?.let { _watchFacePushStatus.value = it }
                }
            }
        rxBus.toFlow(EventMobileToWear::class)
            .collectResilient(newScope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) {
                // If there is a broadcast selected (ie.
                //  AAPSClient want pass data to AAPS
                //  AAPSClient2 want pass data to AAPS or AAPSClient 1
                // ) do it here as the data is prepared
                if (config.AAPSCLIENT && preferences.get(BooleanKey.WearBroadcastData)) broadcastData(it.payload)
            }
    }

    /**
     * The events after which the watch gets everything again, as one flow with one [RESEND_DEBOUNCE].
     *
     * Every resend builds the whole graph and all treatments again. After a BG the calculation, the loop
     * and NS each send their event within seconds, often twice, and each one used to start its own
     * resend: 9 full resends in 45 s were measured on a phone. Gives the reason of the last event of a
     * burst, for the log.
     *
     * The sources are subscribed here, undispatched, into a channel. `merge` would subscribe a moment
     * later in its own coroutines, and an event sent before that would be lost (see `collectResilient`).
     * The channel keeps the request until the debounce reads it.
     */
    @OptIn(FlowPreview::class)
    internal fun resendRequests(scope: CoroutineScope): Flow<String> {
        val requests = Channel<String>(Channel.CONFLATED)
        fun forward(source: Flow<*>, reason: String) {
            source.collectResilient(scope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) { requests.trySend(reason) }
        }
        forward(rxBus.toFlow(EventAutosensCalculationFinished::class), "EventAutosensCalculationFinished")
        forward(rxBus.toFlow(EventLoopUpdateGui::class), "EventLoopUpdateGui")
        // AAPSCLIENT: fresh predictions arrive via NS devicestatus, not a local loop run — without this the
        // watch graph trails the phone by one loop cycle (the BG-triggered autosens resend fires BEFORE the
        // master's new devicestatus lands). Event is only sent on AAPSCLIENT; processedDeviceStatusData is
        // updated synchronously before it fires, so the resend reads the new predictions.
        forward(rxBus.toFlow(EventNsClientStatusUpdated::class), "EventNsClientStatusUpdated")
        // Push status to watch quickly when a TT changes, without waiting for the loop's 10s debounce
        forward(persistenceLayer.observeChanges<TT>().drop(1), "TempTargetChange") // drop: the initial emission on collection start
        // Push status to watch quickly when the running mode changes on the phone, so the
        // running-mode complication and tile do not wait for the next loop run. A wear-side
        // change already refreshes through handleRunningModeConfirmed.
        forward(persistenceLayer.observeChanges<RM>().drop(1), "RunningModeChange")
        return requests.receiveAsFlow().debounce(RESEND_DEBOUNCE)
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

    override suspend fun onStop() {
        scope?.cancel()
        scope = null
        deferredStart.cancel()
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
        title = CoreUiStrings.wear,
        items = listOf(
            BooleanKey.WearControl,
            BooleanKey.WearBroadcastData,
            PreferenceSubScreenDef(
                key = "wear_wizard_settings",
                title = CoreUiStrings.wear_wizard_settings,
                summary = SyncStrings.wear_wizard_settings_summary,
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
                title = SyncStrings.wear_custom_watchface_settings,
                items = listOf(
                    BooleanKey.WearCustomWatchfaceAuthorization
                )
            ),
            PreferenceSubScreenDef(
                key = "wear_general_settings",
                title = SyncStrings.wear_general_settings,
                items = listOf(
                    BooleanKey.WearNotifyOnSmb
                )
            )
        ),
        icon = Icons.Default.Watch
    )

    internal companion object {

        /** The same 2 s the TT and running mode changes waited before they were merged in here. */
        val RESEND_DEBOUNCE = 2.seconds
    }
}