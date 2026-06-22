package app.aaps.plugins.calibration

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.calibration.Calibration
import app.aaps.core.interfaces.calibration.CalibrationContext
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventCalibrationChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.plugins.calibration.compose.CalibrationComposeContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LinearCalibrationPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val dateUtil: DateUtil,
    private val persistenceLayer: PersistenceLayer,
    private val notificationManager: NotificationManager,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val rxBus: RxBus
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.CALIBRATION)
        .icon(IcCalibration)
        .pluginName(R.string.linear_calibration_name)
        .shortName(R.string.calibration_shortname)
        .description(R.string.description_linear_calibration)
        .composeContent { CalibrationComposeContent() },
    aapsLogger, rh
), Calibration {

    private var scope: CoroutineScope? = null

    override suspend fun onStart() {
        super.onStart()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // Calibration entries now live in the main DB and arrive both from local entry (master)
        // and NS sync (follower). Re-emit EventCalibrationChanged on any change so the BG graph
        // recomputes the fit — replaces the event the old repository fired on insert/invalidate.
        scope?.launch {
            persistenceLayer.observeChanges<CAL>().collect {
                rxBus.send(EventCalibrationChanged())
            }
        }
        // App-wide "Reset databases" wipes the table via Room clearAllTables, which bypasses the
        // change-tracking flow above — observe the dedicated cleared signal so the graph recomputes.
        scope?.launch {
            persistenceLayer.databaseClearedFlow.collect {
                rxBus.send(EventCalibrationChanged())
            }
        }
    }

    override suspend fun onStop() {
        scope?.cancel()
        scope = null
        super.onStop()
    }

    override suspend fun calibrate(
        data: MutableList<InMemoryGlucoseValue>,
        context: CalibrationContext
    ): MutableList<InMemoryGlucoseValue> {
        if (data.isEmpty()) return data

        val now = dateUtil.now()
        val sessionStart = context.sensorSessionStart
            ?: persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp

        if (sessionStart != null && now - sessionStart < T.hours(WARM_UP_HOURS).msecs()) {
            aapsLogger.debug(LTag.GLUCOSE) { "LinearCalibration: in warm-up window, identity" }
            return data
        }

        detectAndNotifyGap(data, sessionStart)

        // Without a recorded SENSOR_CHANGE, entries can span multiple sensors with
        // different bias — fitting across them is unsafe. Gap detection above will
        // prompt the user to log a sensor change; until then we apply identity.
        if (sessionStart == null) {
            aapsLogger.debug(LTag.GLUCOSE) { "LinearCalibration: no sensor session start, identity" }
            return data
        }

        val entries = persistenceLayer.getValidCalibrationEntriesSince(sessionStart)
        val fit = fitLinearCalibration(entries, now)
        if (fit == null) {
            aapsLogger.debug(LTag.GLUCOSE) { "LinearCalibration: ${entries.size} entries (<$MIN_ENTRIES_FOR_FIT), identity" }
            return data
        }
        if (!fit.slopeInRange) {
            aapsLogger.warn(LTag.GLUCOSE, "LinearCalibration: slope ${fit.slope} outside [$SLOPE_MIN, $SLOPE_MAX], identity")
            return data
        }
        if (!fit.correctionInRange) {
            aapsLogger.warn(
                LTag.GLUCOSE,
                "LinearCalibration: mid-range correction ${fit.correctionAtCenter} mg/dL outside [$CORRECTION_AT_CENTER_MIN, $CORRECTION_AT_CENTER_MAX], identity"
            )
            return data
        }

        for (entry in data) {
            if (entry.timestamp >= sessionStart) {
                entry.calibrated = fit.slope * entry.value + fit.offset
            }
        }
        aapsLogger.debug(LTag.GLUCOSE) {
            "LinearCalibration: slope=${fit.slope}, offset=${fit.offset}, applied to ${data.count { it.calibrated != null }}/${data.size}"
        }
        return data
    }

    override suspend fun checkPreconditions(): AddEntryResult = checkPreconditionsAt(dateUtil.now())

    private suspend fun checkPreconditionsAt(timestamp: Long): AddEntryResult {
        val sessionStart = persistenceLayer.getLastTherapyRecordUpToNow(TE.Type.SENSOR_CHANGE)?.timestamp
            ?: return AddEntryResult.Rejected.NoSession
        val warmUpEndsAt = sessionStart + T.hours(WARM_UP_HOURS).msecs()
        if (timestamp < warmUpEndsAt) return AddEntryResult.Rejected.InWarmUp(warmUpEndsAt)
        val delta = glucoseStatusProvider.glucoseStatusData?.shortAvgDelta
        if (delta != null) {
            // shortAvgDelta is computed on .recalculated (calibrated) values once an applicable
            // fit is in place, so its magnitude scales with slope. Scale the raw-units threshold
            // by the active slope so a sensor rate of e.g. 5 mg/dL/5min (the "stable enough"
            // bar) is treated identically whether or not calibration is multiplying the signal.
            val activeFit = fitLinearCalibration(persistenceLayer.getValidCalibrationEntriesSince(sessionStart), timestamp)
            val effectiveThreshold = if (activeFit != null && activeFit.isApplicable) {
                DELTA_GATE_MGDL_PER_5MIN * activeFit.slope
            } else {
                DELTA_GATE_MGDL_PER_5MIN
            }
            if (abs(delta) > effectiveThreshold) return AddEntryResult.Rejected.DeltaTooHigh(delta, effectiveThreshold)
        }
        persistenceLayer.getBgReadingsDataFromTimeToTime(
            start = timestamp - PAIR_LOOKBACK_MS,
            end = timestamp,
            ascending = false
        ).firstOrNull() ?: return AddEntryResult.Rejected.NoSensorPair
        return AddEntryResult.Accepted
    }

    override suspend fun addEntry(bgMgdl: Double, timestamp: Long): AddEntryResult {
        val pre = checkPreconditionsAt(timestamp)
        if (pre is AddEntryResult.Rejected) {
            aapsLogger.warn(LTag.GLUCOSE, "LinearCalibration.addEntry rejected: $pre")
            return pre
        }
        // checkPreconditionsAt has already verified the pair exists; re-fetch for the actual sensor value.
        val pair = persistenceLayer.getBgReadingsDataFromTimeToTime(
            start = timestamp - PAIR_LOOKBACK_MS,
            end = timestamp,
            ascending = false
        ).first()
        persistenceLayer.insertOrUpdateCalibrationEntry(CAL(timestamp = timestamp, fingerstickMgdl = bgMgdl, sensorMgdlAtPairing = pair.value))
        aapsLogger.debug(LTag.GLUCOSE) {
            "LinearCalibration.addEntry: fingerstick=$bgMgdl sensorAtPairing=${pair.value}"
        }
        return AddEntryResult.Accepted
    }

    private suspend fun detectAndNotifyGap(data: List<InMemoryGlucoseValue>, sessionStart: Long?) {
        val gapThresholdMs = T.mins(GAP_THRESHOLD_MIN).msecs()
        var gapTime: Long? = null
        for (i in 0 until data.size - 1) {
            val newer = data[i].timestamp
            val older = data[i + 1].timestamp
            if (sessionStart != null && newer <= sessionStart) break
            if (newer - older > gapThresholdMs) {
                gapTime = older + (newer - older) / 2
                break
            }
        }
        val detectedAt = gapTime ?: return

        val nearby = persistenceLayer.getTherapyEventDataFromToTime(
            from = detectedAt - SENSOR_CHANGE_PROXIMITY_MS,
            to = detectedAt + SENSOR_CHANGE_PROXIMITY_MS
        ).any { it.type == TE.Type.SENSOR_CHANGE }
        if (nearby) return

        notificationManager.post(
            id = NotificationId.SENSOR_CHANGE_DETECTED,
            text = rh.gs(R.string.sensor_change_detected_text, dateUtil.timeString(detectedAt)),
            actions = listOf(
                NotificationAction(R.string.sensor_change_detected_action) {
                    runBlocking { insertSensorChange(detectedAt) }
                }
            )
        )
    }

    private suspend fun insertSensorChange(timestamp: Long) {
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE(
                timestamp = timestamp,
                type = TE.Type.SENSOR_CHANGE,
                glucoseUnit = GlucoseUnit.MGDL
            ),
            action = Action.CAREPORTAL,
            source = Sources.SensorInsert,
            note = null,
            listValues = listOf(
                ValueWithUnit.Timestamp(timestamp),
                ValueWithUnit.TEType(TE.Type.SENSOR_CHANGE)
            )
        )
    }

    private companion object {

        const val GAP_THRESHOLD_MIN = 30L
        const val WARM_UP_HOURS = 2L

        // GlucoseStatus.shortAvgDelta is mg/dL per 5 min — match the unit here. 5 mg/dL / 5 min
        // ≈ 1 mg/dL / min, the conventional "stable enough to calibrate" threshold across CGM apps.
        const val DELTA_GATE_MGDL_PER_5MIN = 5.0
        const val SENSOR_CHANGE_PROXIMITY_MS = 60L * 60L * 1000L
        const val PAIR_LOOKBACK_MS = 10L * 60L * 1000L
    }
}
