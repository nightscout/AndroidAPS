package app.aaps.cgm.dexcomg7

import android.Manifest
import android.content.Context
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.data.G7StringNonKey
import app.aaps.cgm.dexcomg7.session.G7Alerts
import app.aaps.cgm.dexcomg7.session.G7PairingService
import app.aaps.cgm.dexcomg7.session.G7Reading
import app.aaps.cgm.dexcomg7.session.G7Session
import app.aaps.cgm.dexcomg7.ui.G7ComposeContent
import app.aaps.cgm.dexcomg7.protocol.G7LifecycleState
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dexcom G7 and ONE+ read straight over Bluetooth, with no Dexcom app or xDrip on the phone.
 *
 * Pair by scanning the applicator barcode or by typing its 4-digit code. Readings, backfill after
 * time out of range, sensor status, lifecycle alerts and calibration come from the sensor itself.
 * The sensor accepts one phone app at a time, so the Dexcom app must not use the same sensor.
 *
 * Only readings the sensor marks as usable are stored: warmup, failed, and calibration echo readings
 * are dropped. The loop should get nothing rather than a number the sensor does not stand behind.
 *
 * Protocol and pairing flow ported from Trio's G7SensorKit (MIT, LoopKit Authors). Android Bluetooth
 * behaviour follows what the Watch-APS direct collector learned.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(445)
@SingleIn(AppScope::class)
@Inject
class DexcomG7DirectPlugin(
    rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    preferences: Preferences,
    notificationManager: NotificationManager,
    private val context: Context,
    private val persistenceLayer: PersistenceLayer,
    private val store: G7StateStore,
    private val session: G7Session,
    private val pairing: G7PairingService,
    private val alerts: G7Alerts,
    private val blePreCheck: BlePreCheck
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.BGSOURCE)
        .icon(IcGenericCgm)
        .pluginName(TextRef.AndroidRes(R.string.dexcom_g7_direct))
        .shortName(TextRef.AndroidRes(R.string.dexcom_g7_direct_short))
        .preferencesVisibleInSimpleMode(false)
        .description(TextRef.AndroidRes(R.string.description_dexcom_g7_direct)),
    ownPreferences = G7StringNonKey.entries,
    aapsLogger = aapsLogger,
    rh = rh,
    preferences = preferences,
    notificationManager = notificationManager
), BgSource {

    init {
        pluginDescription.composeContent { G7ComposeContent(rh.gs(R.string.dexcom_g7_direct), blePreCheck) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readingsJob: Job? = null

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "dexcom_g7_direct_settings",
        // a BG source plugin always names itself
        title = pluginDescription.pluginName!!,
        items = listOf(
            BooleanKey.BgSourceUploadToNs,
            BooleanKey.BgSourceCreateSensorChange
        ),
        icon = pluginDescription.icon
    )

    override fun requiredPermissions(): List<PermissionGroup> = listOf(
        PermissionGroup(
            permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
            rationaleTitle = TextRef.AndroidRes(R.string.dexcom_g7_permission_bluetooth_title),
            rationaleDescription = TextRef.AndroidRes(R.string.dexcom_g7_permission_bluetooth_description)
        )
    )

    override suspend fun onStart() {
        super.onStart()
        readingsJob?.cancel()
        readingsJob = scope.launch { session.readings.collect { store(it) } }
        alerts.start()
        session.start()
    }

    override suspend fun onStop() {
        pairing.cancel()
        session.stop()
        alerts.stop()
        readingsJob?.cancel()
        readingsJob = null
        super.onStop()
    }

    /** True when the sensor cannot give readings the loop may use: failed or its session is over. */
    override fun hasSensorError(): Boolean =
        store.value.lifecycleState(System.currentTimeMillis()).let { it == G7LifecycleState.FAILED || it == G7LifecycleState.EXPIRED }

    // The stored state is not exported (it holds the key), so an import would wipe it. Keep it.
    private var stateBeforeImport: String? = null

    override fun beforeImport() {
        stateBeforeImport = preferences.get(G7StringNonKey.State)
    }

    override fun afterImport() {
        stateBeforeImport?.let { preferences.put(G7StringNonKey.State, it) }
        stateBeforeImport = null
        store.reload()
    }

    internal suspend fun store(readings: List<G7Reading>) {
        val usable = readings.filter { it.isUsable }
        readings.filterNot { it.isUsable }.forEach {
            aapsLogger.debug(LTag.BGSOURCE, "Dexcom G7: not storing reading ${it.glucose} (state ${it.algorithmState}, displayOnly ${it.displayOnly})")
        }
        val activatedAt = store.value.activatedAt
        val sensorStart = if (preferences.get(BooleanKey.BgSourceCreateSensorChange) && activatedAt != null && activatedAt < System.currentTimeMillis()) activatedAt else null
        if (usable.isEmpty() && sensorStart == null) return
        val values = usable.map {
            GV(
                timestamp = it.timestamp,
                value = it.clampedGlucose!!.toDouble(),
                raw = null,
                noise = null,
                trendArrow = trendArrow(it.trend),
                sourceSensor = SourceSensor.DEXCOM_G7_NATIVE
            )
        }
        runCatching { persistenceLayer.insertCgmSourceData(Sources.Dexcom, values, emptyList(), sensorStart) }
            .onFailure { aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: storing readings failed", it) }
    }

    companion object {

        fun trendArrow(trend: G7Trend?): TrendArrow = when (trend) {
            G7Trend.DOUBLE_DOWN     -> TrendArrow.DOUBLE_DOWN
            G7Trend.SINGLE_DOWN     -> TrendArrow.SINGLE_DOWN
            G7Trend.FORTY_FIVE_DOWN -> TrendArrow.FORTY_FIVE_DOWN
            G7Trend.FLAT            -> TrendArrow.FLAT
            G7Trend.FORTY_FIVE_UP   -> TrendArrow.FORTY_FIVE_UP
            G7Trend.SINGLE_UP       -> TrendArrow.SINGLE_UP
            G7Trend.DOUBLE_UP       -> TrendArrow.DOUBLE_UP
            null                    -> TrendArrow.NONE
        }
    }
}
