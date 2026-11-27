package app.aaps.implementation.bolus

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.MasterActionExecutor
import app.aaps.core.interfaces.calibration.AddEntryResult
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.source.SensorCalibrationResult
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.extensions.friendlyDescription
import app.aaps.core.ui.extensions.toStringFull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The batch actions that change stored records without a pump command: removing records and adding a calibration.
 *
 * Only the master runs this, for its own screens and for every paired client, so the records the loop uses change
 * only on the device that doses. A client never removes anything itself: the master's change comes back to it
 * through Nightscout, like every other change the master makes.
 */
@SingleIn(AppScope::class)
@Inject
class MasterRecordWriter(
    private val persistenceLayer: PersistenceLayer,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil,
    private val profileFunction: ProfileFunction,
    private val decimalFormatter: DecimalFormatter,
    private val translator: Translator,
    private val activePlugin: ActivePlugin,
    private val uel: UserEntryLogger,
    private val xDripSource: XDripSource,
    private val xDripBroadcast: XDripBroadcast,
    private val aapsLogger: AAPSLogger
) {

    /** A record found in this database, ready to describe and to remove. */
    private sealed interface Stored {

        val timestamp: Long
        val isValid: Boolean

        data class Bolus(val record: BS) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class Carbs(val record: CA) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class BolusCalculatorResult(val record: BCR) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class TemporaryTarget(val record: TT) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class TemporaryBasal(val record: TB) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class ExtendedBolus(val record: EB) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class ProfileSwitch(val record: PS) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class RunningMode(val record: RM) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class TherapyEvent(val record: TE) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class GlucoseValue(val record: GV) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }

        data class Calibration(val record: CAL) : Stored {

            override val timestamp get() = record.timestamp
            override val isValid get() = record.isValid
        }
    }

    // ----- Removing records -----

    /**
     * What the master will remove: the confirmation [lines], how many records it [found], and how many records sent
     * by a client it does [notHave].
     */
    data class RemovalPlan(val lines: List<ConfirmationLine>, val found: Int, val notHave: Int)

    /**
     * Find [removals] here and describe them: each record, or a count when there are many.
     *
     * A record sent by a client (it has no local id) that is not here, or is already removed, is counted and named in
     * one line. It is not removed: only the master's own copies are, and a client never removes anything itself. On
     * the master's own screens a missing record was removed in the meantime, so it is left out without a word.
     */
    suspend fun planRemovals(removals: List<BatchAction.Remove>): RemovalPlan {
        val found = mutableListOf<Stored>()
        var notHave = 0
        removals.forEach { removal ->
            removal.records.forEach { ref ->
                val stored = find(removal.kind, ref)?.takeIf { it.isValid }
                when {
                    stored != null      -> found += stored
                    ref.localId == null -> notHave++
                }
            }
        }
        val lines = buildList {
            if (found.size <= MAX_LISTED) found.sortedBy { it.timestamp }.forEach { addAll(describe(it)) }
            else add(ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(CoreUiStrings.confirm_remove_multiple_items, found.size)))
            if (notHave > 0) add(ConfirmationLine(ConfirmationRole.NORMAL, notHaveText(notHave)))
        }
        return RemovalPlan(lines, found.size, notHave)
    }

    /** "The master does not have [count] of these entries. They are not removed." */
    fun notHaveText(count: Int): String = rh.gs(CoreUiStrings.remove_not_on_master, count)

    /**
     * Remove the master's own copies of [removals]. A record that is not here or already removed is skipped: the
     * wanted end state is reached either way, so it is not an error.
     */
    suspend fun remove(removals: List<BatchAction.Remove>, source: Sources) {
        removals.forEach { removal ->
            removal.records.forEach { ref -> find(removal.kind, ref)?.takeIf { it.isValid }?.let { invalidate(it, source) } }
        }
    }

    /** The confirmation line for removing every "AndroidAPS started" event. */
    fun removeStartEventsLine(): ConfirmationLine =
        ConfirmationLine(ConfirmationRole.PRIMARY, rh.gs(InterfacesStrings.confirmation_line, rh.gs(CoreUiStrings.removerecord), rh.gs(CoreUiStrings.androidaps_start)))

    /** Remove every "AndroidAPS started" event, matched by the master's own text (the events carry it in its language). */
    suspend fun removeStartEvents(source: Sources) {
        persistenceLayer.invalidateTherapyEventsWithNote(rh.gs(CoreUiStrings.androidaps_start), Action.RESTART_EVENTS_REMOVED, source)
    }

    /** Look the record up by local id when this device built the action, else by its Nightscout id. */
    private suspend fun find(kind: BatchAction.Remove.Kind, ref: BatchAction.Remove.Ref): Stored? = when (kind) {
        BatchAction.Remove.Kind.BOLUS                   -> lookup(ref, persistenceLayer::getBolusById, persistenceLayer::getBolusByNSId)?.let { Stored.Bolus(it) }
        BatchAction.Remove.Kind.CARBS                   -> lookup(ref, persistenceLayer::getCarbsById, persistenceLayer::getCarbsByNSId)?.let { Stored.Carbs(it) }
        BatchAction.Remove.Kind.BOLUS_CALCULATOR_RESULT ->
            lookup(ref, persistenceLayer::getBolusCalculatorResultById, persistenceLayer::getBolusCalculatorResultByNSId)?.let { Stored.BolusCalculatorResult(it) }

        BatchAction.Remove.Kind.TEMPORARY_TARGET        ->
            lookup(ref, persistenceLayer::getTemporaryTargetById, persistenceLayer::getTemporaryTargetByNSId)?.let { Stored.TemporaryTarget(it) }

        BatchAction.Remove.Kind.TEMPORARY_BASAL         ->
            lookup(ref, persistenceLayer::getTemporaryBasalById, persistenceLayer::getTemporaryBasalByNSId)?.let { Stored.TemporaryBasal(it) }

        BatchAction.Remove.Kind.EXTENDED_BOLUS          ->
            lookup(ref, persistenceLayer::getExtendedBolusById, persistenceLayer::getExtendedBolusByNSId)?.let { Stored.ExtendedBolus(it) }

        BatchAction.Remove.Kind.PROFILE_SWITCH          ->
            lookup(ref, persistenceLayer::getProfileSwitchById, persistenceLayer::getProfileSwitchByNSId)?.let { Stored.ProfileSwitch(it) }

        BatchAction.Remove.Kind.RUNNING_MODE            -> lookup(ref, persistenceLayer::getRunningModeById, persistenceLayer::getRunningModeByNSId)?.let { Stored.RunningMode(it) }
        BatchAction.Remove.Kind.THERAPY_EVENT           -> lookup(ref, persistenceLayer::getTherapyEventById, persistenceLayer::getTherapyEventByNSId)?.let { Stored.TherapyEvent(it) }
        BatchAction.Remove.Kind.GLUCOSE_VALUE           -> lookup(ref, persistenceLayer::getBgReadingById, persistenceLayer::getBgReadingByNSId)?.let { Stored.GlucoseValue(it) }
        BatchAction.Remove.Kind.CALIBRATION             ->
            lookup(ref, persistenceLayer::getCalibrationEntryById, persistenceLayer::getCalibrationEntryByNSId)?.let { Stored.Calibration(it) }
    }

    private suspend fun <R> lookup(ref: BatchAction.Remove.Ref, byLocalId: suspend (Long) -> R?, byNsId: suspend (String) -> R?): R? =
        ref.localId?.let { byLocalId(it) } ?: ref.nsId?.let { byNsId(it) }

    /** "What: value" and "Date: when" for one record. */
    private suspend fun describe(stored: Stored): List<ConfirmationLine> {
        val what = when (stored) {
            is Stored.Bolus                 -> line(CoreUiStrings.configbuilder_insulin, rh.gs(InterfacesStrings.format_insulin_units, stored.record.amount))
            is Stored.Carbs                 -> line(InterfacesStrings.carbs, rh.gs(InterfacesStrings.format_carbs, stored.record.amount.toInt()))
            is Stored.BolusCalculatorResult -> rh.gs(CoreUiStrings.boluswizard)
            is Stored.TemporaryTarget       -> line(CoreUiStrings.temporary_target, stored.record.friendlyDescription(profileUtil.units, rh, profileUtil))
            is Stored.TemporaryBasal        -> profileFunction.getProfile(stored.record.timestamp)?.let { profile ->
                line(CoreUiStrings.tempbasal_label, stored.record.toStringFull(profile, dateUtil, rh))
            } ?: rh.gs(CoreUiStrings.tempbasal_label)

            is Stored.ExtendedBolus         -> line(CoreUiStrings.extended_bolus, stored.record.toStringFull(dateUtil, rh))
            is Stored.ProfileSwitch         -> line(CoreUiStrings.careportal_profileswitch, stored.record.profileName)
            is Stored.RunningMode           -> line(CoreUiStrings.running_mode, translator.translate(stored.record.mode))
            is Stored.TherapyEvent          -> line(CoreUiStrings.event_type, translator.translate(stored.record.type))
            is Stored.GlucoseValue          -> line(CoreUiStrings.bg_label, profileUtil.fromMgdlToStringWithUnits(stored.record.value))
            is Stored.Calibration           -> line(CoreUiStrings.calibration, profileUtil.fromMgdlToStringWithUnits(stored.record.fingerstickMgdl))
        }
        return listOf(
            ConfirmationLine(ConfirmationRole.PRIMARY, what),
            ConfirmationLine(ConfirmationRole.NORMAL, line(CoreUiStrings.date, dateUtil.dateAndTimeString(stored.timestamp)))
        )
    }

    private fun line(label: TextRef, value: String): String =
        rh.gs(InterfacesStrings.confirmation_line, rh.gs(label), value)

    /** Remove one record, with the same user entry the list screens wrote when they removed it themselves. */
    private suspend fun invalidate(stored: Stored, source: Sources) {
        when (stored) {
            is Stored.Bolus                 -> stored.record.let {
                persistenceLayer.invalidateBolus(
                    it.id, action = Action.BOLUS_REMOVED, source = source,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Insulin(it.amount))
                )
            }

            is Stored.Carbs                 -> stored.record.let {
                persistenceLayer.invalidateCarbs(
                    it.id, action = Action.CARBS_REMOVED, source = source,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.Gram(it.amount.toInt()))
                )
            }

            is Stored.BolusCalculatorResult -> stored.record.let {
                persistenceLayer.invalidateBolusCalculatorResult(
                    it.id, action = Action.BOLUS_CALCULATOR_RESULT_REMOVED, source = source,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp))
                )
            }

            is Stored.TemporaryTarget       -> stored.record.let {
                persistenceLayer.invalidateTemporaryTarget(
                    id = it.id, action = Action.TT_REMOVED, source = source, note = null,
                    listValues = listOfNotNull(
                        ValueWithUnit.Timestamp(it.timestamp),
                        ValueWithUnit.TETTReason(it.reason),
                        ValueWithUnit.Mgdl(it.lowTarget),
                        ValueWithUnit.Mgdl(it.highTarget).takeIf { _ -> it.lowTarget != it.highTarget },
                        ValueWithUnit.Minute(it.duration.milliseconds.inWholeMinutes.toInt())
                    )
                )
            }

            is Stored.TemporaryBasal        -> stored.record.let {
                persistenceLayer.invalidateTemporaryBasal(
                    id = it.id, action = Action.TEMP_BASAL_REMOVED, source = source,
                    listValues = listOf(
                        ValueWithUnit.Timestamp(it.timestamp),
                        if (it.isAbsolute) ValueWithUnit.UnitPerHour(it.rate) else ValueWithUnit.Percent(it.rate.toInt()),
                        ValueWithUnit.Minute(T.msecs(it.duration).mins().toInt())
                    )
                )
            }

            is Stored.ExtendedBolus         -> stored.record.let {
                persistenceLayer.invalidateExtendedBolus(
                    id = it.id, action = Action.EXTENDED_BOLUS_REMOVED, source = source,
                    listValues = listOf(
                        ValueWithUnit.Timestamp(it.timestamp),
                        ValueWithUnit.Insulin(it.amount),
                        ValueWithUnit.UnitPerHour(it.rate),
                        ValueWithUnit.Minute(it.duration.milliseconds.inWholeMinutes.toInt())
                    )
                )
            }

            is Stored.ProfileSwitch         -> stored.record.let {
                persistenceLayer.invalidateProfileSwitch(
                    id = it.id, action = Action.PROFILE_SWITCH_REMOVED, source = source, note = it.profileName,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp))
                )
            }

            is Stored.RunningMode           -> stored.record.let {
                persistenceLayer.invalidateRunningMode(
                    id = it.id, action = Action.LOOP_REMOVED, source = source, note = null,
                    listValues = listOf(
                        ValueWithUnit.Timestamp(it.timestamp),
                        ValueWithUnit.RMMode(it.mode),
                        ValueWithUnit.Minute(it.duration.milliseconds.inWholeMinutes.toInt())
                    )
                )
            }

            is Stored.TherapyEvent          -> stored.record.let {
                persistenceLayer.invalidateTherapyEvent(
                    id = it.id, action = Action.CAREPORTAL_REMOVED, source = source, note = it.note,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp), ValueWithUnit.TEType(it.type))
                )
            }

            is Stored.GlucoseValue          -> stored.record.let {
                persistenceLayer.invalidateGlucoseValue(
                    id = it.id, action = Action.BG_REMOVED, source = source, note = null,
                    listValues = listOf(ValueWithUnit.Timestamp(it.timestamp))
                )
            }

            is Stored.Calibration           -> stored.record.let {
                persistenceLayer.invalidateCalibrationEntry(
                    id = it.id, action = Action.CALIBRATION_REMOVED, source = source, note = "id=${it.id}",
                    listValues = listOf(ValueWithUnit.fromGlucoseUnit(profileUtil.fromMgdlToUnits(it.fingerstickMgdl), profileUtil.units))
                )
            }
        }
        aapsLogger.debug(LTag.DATABASE, "Removed ${stored::class.simpleName} at ${stored.timestamp} (source $source)")
    }

    // ----- Calibration -----

    /**
     * Why the master cannot take a calibration now, or null when it can. Checked on the master's own data: by
     * the sensor itself when the BG source calibrates in the sensor, by the calibration plugin otherwise.
     */
    suspend fun calibrationRefusal(calibration: BatchAction.Calibration): String? {
        val bgSource = activePlugin.activeBgSource
        if (bgSource.calibratesInSensor()) return bgSource.sensorCalibrationRefusal(calibration.glucoseMgdl.roundToInt(), dateUtil.now())
        return (activePlugin.activeCalibration.checkPreconditions() as? AddEntryResult.Rejected)?.let { rejectionMessage(it) }
    }

    /**
     * The confirmation lines for a calibration. A value that goes to the sensor gets a warning: the sensor keeps
     * it for good, unlike a calibration entry stored in the app.
     */
    fun calibrationLines(calibration: BatchAction.Calibration): List<ConfirmationLine> {
        val value = ConfirmationLine(ConfirmationRole.PRIMARY, line(CoreUiStrings.calibration, profileUtil.fromMgdlToStringWithUnits(calibration.glucoseMgdl)))
        return if (activePlugin.activeBgSource.calibratesInSensor()) listOf(value, ConfirmationLine(ConfirmationRole.WARNING, rh.gs(CoreUiStrings.calibration_to_sensor_warning)))
        else listOf(value)
    }

    /**
     * Store a calibration on the master, paired with its own latest sensor reading, or hand it to the sensor when the
     * BG source calibrates in the sensor. Never both: a sensor that holds a calibration re-bases its own readings,
     * and a line fitted in the app on top would correct them twice. The conditions are checked again here (time has
     * passed since the prepare); a refusal goes to [onError] so the user is told.
     */
    suspend fun addCalibration(calibration: BatchAction.Calibration, source: Sources, onError: (MasterActionExecutor.Failure) -> Unit) {
        val value = ValueWithUnit.fromGlucoseUnit(profileUtil.fromMgdlToUnits(calibration.glucoseMgdl), profileUtil.units)
        val bgSource = activePlugin.activeBgSource
        if (bgSource.calibratesInSensor()) {
            when (val sent = bgSource.calibrateSensor(calibration.glucoseMgdl.roundToInt(), dateUtil.now())) {
                SensorCalibrationResult.Queued       -> {
                    uel.log(action = Action.CALIBRATION, source = source, note = rh.gs(CoreUiStrings.calibration_sent_to_sensor), value = value)
                    return
                }

                is SensorCalibrationResult.Refused   -> {
                    uel.log(action = Action.CALIBRATION, source = source, note = "rejected: ${sent.reason}", value = value)
                    onError(MasterActionExecutor.Failure(sent.reason))
                    return
                }
                // The source changed its mind between the two calls: store it in the app as before.
                SensorCalibrationResult.NotSupported -> Unit
            }
        }
        when (val result = activePlugin.activeCalibration.addEntry(calibration.glucoseMgdl, dateUtil.now())) {
            AddEntryResult.Accepted    -> {
                uel.log(action = Action.CALIBRATION, source = source, value = value)
                // xDrip, when it is the BG source here, calibrates its own readings too.
                if (xDripSource.isEnabled()) xDripBroadcast.sendCalibration(profileUtil.fromMgdlToUnits(calibration.glucoseMgdl))
            }

            is AddEntryResult.Rejected -> {
                val message = rejectionMessage(result)
                uel.log(action = Action.CALIBRATION, source = source, note = "rejected: $message", value = value)
                onError(MasterActionExecutor.Failure(message))
            }
        }
    }

    private fun rejectionMessage(rejected: AddEntryResult.Rejected): String = when (rejected) {
        is AddEntryResult.Rejected.DeltaTooHigh -> rh.gs(
            CoreUiStrings.cal_reject_delta_too_high,
            formatDelta(rejected.deltaMgdlPer5Min),
            formatDelta(rejected.thresholdMgdlPer5Min),
            profileUtil.unitLabel
        )

        AddEntryResult.Rejected.NoSensorPair    -> rh.gs(CoreUiStrings.cal_reject_no_pair)
        is AddEntryResult.Rejected.InWarmUp     -> rh.gs(CoreUiStrings.cal_reject_warmup)
        AddEntryResult.Rejected.NoSession       -> rh.gs(CoreUiStrings.cal_reject_no_session)
    }

    // The delta is in mg/dL per 5 min; shown in the user's unit.
    private fun formatDelta(mgdlPer5Min: Double): String {
        val delta = profileUtil.fromMgdlToUnits(mgdlPer5Min)
        return if (profileUtil.units == GlucoseUnit.MMOL) decimalFormatter.to1Decimal(delta) else decimalFormatter.to0Decimal(delta)
    }

    private companion object {

        /** Up to this many records are listed one by one in the confirmation; more are shown as a count. */
        const val MAX_LISTED = 5
    }
}
