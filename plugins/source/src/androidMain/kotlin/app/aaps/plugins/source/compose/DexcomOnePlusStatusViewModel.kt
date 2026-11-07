package app.aaps.plugins.source.compose

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.dexcomoneplus.OnePlusCalibrationOutcome
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDrivers
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.source.DexcomOnePlusHost
import app.aaps.plugins.source.DexcomOnePlusPlugin
import app.aaps.plugins.source.DexcomOnePlusSensorStartCorrection
import app.aaps.plugins.source.DexcomOnePlusWatchHandover
import app.aaps.plugins.source.SourceStrings
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How often the screen goes back to the driver. */
private const val DRIVER_REFRESH_MILLIS = 1_000L

/** How often the screen goes back to the database. A reading arrives every five minutes. */
private const val GLUCOSE_REFRESH_MILLIS = 30_000L

/**
 * State of the ONE+ / G7 status screen, for the plugin tab and for the status Activity.
 *
 * It reads what the status Activity used to read itself: the driver every second, the newest
 * reading every 30 seconds, and the slot flows of the plugin as they change. It only reads,
 * except for the three actions the person starts by hand: promote, cancel the pre-soak, and
 * correct the insertion date. All three are the plugin's own functions, with their own checks.
 */
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class DexcomOnePlusStatusViewModel(
    private val plugin: DexcomOnePlusPlugin,
    internal val host: DexcomOnePlusHost,
    private val preferences: Preferences,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    internal val rh: TextResolver,
) : ViewModel() {

    /** What the driver says right now. */
    private data class DriverReading(
        val state: OnePlusWarmupState,
        val sessionUp: Boolean,
        val calibrationOutcome: OnePlusCalibrationOutcome?,
        val calibrationWaiting: Boolean,
        val sendCalibrationToSensor: Boolean,
        val correctSensorStartEnabled: Boolean,
        val nowMs: Long,
        val sensor: DexcomOnePlusSensorFacts,
    )

    init {
        // As the status Activity did when it opened: the driver in use follows the engineering
        // switch before the screen reads it.
        plugin.syncDriverFromPrefs()
    }

    private val driverTicks: Flow<DriverReading> = flow {
        while (true) {
            // Asked again every time: a promotion swaps which instance `default()` hands out, and a
            // kept reference would show the retired one for the rest of the screen's life.
            val driver = OnePlusCgmDrivers.default()
            emit(
                DriverReading(
                    state = driver.warmupState(),
                    sessionUp = driver.isSessionUp(),
                    calibrationOutcome = driver.lastCalibrationOutcome(),
                    calibrationWaiting = driver.calibrationPending(),
                    sendCalibrationToSensor = preferences.get(DexcomOnePlusBooleanKey.SendCalibrationToSensor),
                    correctSensorStartEnabled = preferences.get(DexcomOnePlusBooleanKey.CorrectSensorStart),
                    nowMs = dateUtil.now(),
                    sensor = plugin.sensorFacts(),
                ),
            )
            delay(DRIVER_REFRESH_MILLIS)
        }
    }

    private val glucoseTicks: Flow<GV?> = flow {
        while (true) {
            emit(persistenceLayer.getLastGlucoseValue())
            delay(GLUCOSE_REFRESH_MILLIS)
        }
    }

    val input: StateFlow<DexcomOnePlusStatusInput?> = combine(
        driverTicks,
        glucoseTicks,
        plugin.stagingState,
        plugin.stagingEvidence,
        plugin.lifecycle,
    ) { driver, glucose, stagingState, stagingEvidence, lifecycle ->
        DexcomOnePlusStatusInput(
            phase = driver.state.phase,
            driverMessage = driver.state.message,
            sessionUp = driver.sessionUp,
            lifecycle = lifecycle,
            newestGlucose = glucose,
            calibrationOutcome = driver.calibrationOutcome,
            calibrationWaiting = driver.calibrationWaiting,
            sendCalibrationToSensor = driver.sendCalibrationToSensor,
            correctSensorStartEnabled = driver.correctSensorStartEnabled,
            stagingState = stagingState,
            stagingEvidence = stagingEvidence,
            nowMs = driver.nowMs,
            sensor = driver.sensor,
            warmupEndsAtMs = driver.state.endsAtEpochMs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    private val _promoteResult = MutableStateFlow<TextRef?>(null)

    /** What the last promotion did, or null before one was asked for. */
    val promoteResult: StateFlow<TextRef?> = _promoteResult.asStateFlow()

    val format: DexcomOnePlusStatusFormat = DexcomOnePlusStatusFormat(
        rh = rh,
        glucose = { mgdl -> profileUtil.fromMgdlToStringWithUnits(mgdl) },
        time = { epochMs -> dateUtil.timeString(epochMs) },
        age = { millis -> formatSensorAge(millis, rh) },
        dateTime = { epochMs -> dateUtil.dateAndTimeString(epochMs) },
    )

    /**
     * Promotes the pre-soak sensor. [allowEarly] is the early way out, after its own warning.
     *
     * Refused with a message while the plugin is switched off, before anything is asked of it.
     */
    fun promote(allowEarly: Boolean) {
        if (plugin.isStopped()) {
            _promoteResult.value = SourceStrings.dexcom_oneplus_plugin_stopped
            return
        }
        viewModelScope.launch {
            _promoteResult.value = DexcomOnePlusStatusState.promotionMessage(plugin.promoteStagingToProduction(allowEarly))
        }
    }

    fun cancelStaging() {
        plugin.cancelStaging()
        _promoteResult.value = null
    }

    suspend fun correctSensorStart(startMs: Long): DexcomOnePlusSensorStartCorrection.Verdict =
        plugin.correctProductionSensorStart(startMs)

    /** Direct-to-watch for the production sensor. Its card is drawn only where [DexcomOnePlusWatchHandover.isSupported]. */
    val watchHandover: DexcomOnePlusWatchHandover get() = plugin.watchHandover

    /** The direct-to-watch switch, live. */
    val watchDirectEnabled: StateFlow<Boolean> = preferences.observe(DexcomOnePlusBooleanKey.WatchDirect)
}
