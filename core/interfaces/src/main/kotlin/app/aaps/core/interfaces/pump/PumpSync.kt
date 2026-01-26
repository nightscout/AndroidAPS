package app.aaps.core.interfaces.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * This interface allows pump drivers to push data changes (creation and update of treatments, temporary basals and extended boluses) back to AAPS-core.
 *
 * Intended use cases for handling bolus treatments:
 *
 *  - for pumps that have a reliable history that can be read and which therefore issue a bolus on the pump,
 *    read the history back and add new bolus entries on the pump, the method [syncBolusWithPumpId]
 *    are used to inform AAPS-core of a new bolus.
 *    [info.nightscout.androidaps.danars.DanaRSPlugin] is a pump driver that
 *    takes this approach.
 *  - for pumps that don't support history or take rather long to complete a bolus, the methods
 *    [addBolusWithTempId] and [syncBolusWithTempId] provide a mechanism to notify AAPS-core of a started
 *    bolus, so AAPS-core can operate under the assumption the bolus will be delivered and effect IOB until delivery
 *    completed. Upon completion, the pump driver will call the second method to turn a temporary bolus into a finished
 *    bolus.
 */
interface PumpSync {

    /**
     *  Reset stored identification of last used pump
     *
     *  Call this function when new pump is paired to accept data from new pump
     *  to prevent overlapping pump histories
     *  @param endRunning    if true end previous running TBR and EB
     */

    fun connectNewPump(endRunning: Boolean = true)

    /**
     *  Verify if current pump type and SN is properly registered
     *  @param type current PumpType
     *  @param serialNumber current serial number
     *
     *  @return true if type and SN match to last call of connectNewPump
     */
    fun verifyPumpIdentification(type: PumpType, serialNumber: String): Boolean

    /*
     *   GENERAL STATUS
     */

    /**
     *  Query expected pump state
     *
     *  Driver may query AAPS for expecting state of the pump and use it for sanity check
     *  or generation of status for NS
     *
     *  TemporaryBasal
     *  duration in milliseconds
     *  rate in U/h or % where 100% is equal to no TBR
     *
     *  ExtendedBolus
     *  duration in milliseconds
     *  amount in U
     *  rate in U/h (synthetic only)
     *
     *  @return         data from database.
     *                  temporaryBasal (and extendedBolus) is null if there is no record in progress based on data in database
     *                  bolus is null when there is no record in database
     */
    data class PumpState(val temporaryBasal: TemporaryBasal?, val extendedBolus: ExtendedBolus?, val bolus: Bolus?, val profile: PumpProfile?, val serialNumber: String) {

        data class TemporaryBasal(
            val timestamp: Long,
            val duration: Long,
            val rate: Double,
            val isAbsolute: Boolean,
            val type: TemporaryBasalType,
            val id: Long,
            val pumpId: Long?,
            // used only to cancel TBR on pump change
            val pumpType: PumpType = PumpType.USER,
            val pumpSerial: String = ""
        ) {

            val end: Long get() = timestamp + duration
            val plannedRemainingMinutes: Long get() = max(T.msecs(end - System.currentTimeMillis()).mins(), 0L)
            fun convertedToAbsolute(time: Long, profile: Profile): Double =
                if (isAbsolute) rate
                else profile.getBasal(time) * rate / 100

            fun toStringFull(dateUtil: DateUtil, rh: ResourceHelper): String {
                return when {
                    isAbsolute -> {
                        rh.gs(R.string.temp_basal_absolute_rate, rate, dateUtil.timeString(timestamp), getPassedDurationToTimeInMinutes(dateUtil.now()), durationInMinutes)
                    }

                    else       -> { // percent
                        rh.gs(R.string.temp_basal_percent_rate, rate, dateUtil.timeString(timestamp), getPassedDurationToTimeInMinutes(dateUtil.now()), durationInMinutes)
                    }
                }
            }

            val durationInMinutes: Int
                get() = T.msecs(duration).mins().toInt()

            private fun getPassedDurationToTimeInMinutes(time: Long): Int =
                ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

        }

        data class ExtendedBolus(
            val timestamp: Long,
            val duration: Long,
            val amount: Double,
            val rate: Double,
            // used only to cancel EB on pump change
            val pumpType: PumpType = PumpType.USER,
            val pumpSerial: String = ""
        ) {

            val end: Long
                get() = timestamp + duration

            val plannedRemainingMinutes: Long
                get() = max(T.msecs(end - System.currentTimeMillis()).mins(), 0L)

            private fun getPassedDurationToTimeInMinutes(time: Long): Int =
                ((min(time, end) - timestamp) / 60.0 / 1000).roundToInt()

            fun toStringFull(dateUtil: DateUtil, rh: ResourceHelper): String =
                rh.gs(R.string.temp_basal_extended_bolus, rate, dateUtil.timeString(timestamp), getPassedDurationToTimeInMinutes(dateUtil.now()), T.msecs(duration).mins())

        }

        data class Bolus(val timestamp: Long, val amount: Double)
    }

    fun expectedPumpState(): PumpState

    /*
     *   BOLUSES & CARBS
     */

    /**
     * Create bolus with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist, new record is created.
     * If exists false is returned and data is ignored
     *
     * USAGE:
     * Generate unique temporaryId
     * Call before bolus when no pumpId is known (provide timestamp, amount, temporaryId, type, pumpType, pumpSerial)
     * After reading record from history or completed bolus call [syncBolusWithTempId] with the same temporaryId provided
     * If syncBolusWithTempId is not called afterwards record remains valid and is calculated towards iob
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param temporaryId   temporary id generated when pump id in not know yet
     * @param type          type of bolus (NORMAL, SMB, PRIME)
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun addBolusWithTempId(timestamp: Long, amount: PumpInsulin, temporaryId: Long, type: BS.Type, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of boluses with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist data is ignored and false returned.
     * If exists, amount and timestamp is updated, type and pumpId only if provided
     * isValid field is preserved
     *
     * USAGE:
     * After reading record from history or completed bolus call syncBolusWithTempId and
     * provide updated timestamp, amount, pumpId (if known), type (if change needed) with the same temporaryId, pumpType, pumpSerial
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param temporaryId   temporary id generated when pump id in not know yet
     * @param type          type of bolus (NORMAL, SMB, PRIME)
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if record is successfully updated
     **/
    fun syncBolusWithTempId(timestamp: Long, amount: PumpInsulin, temporaryId: Long, type: BS.Type?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of boluses
     *
     * Search for combination of pumpId, PumpType, pumpSerial
     *
     * If db record doesn't exist, new record is created.
     * If exists, amount, type (if provided) and timestamp is updated
     * isValid field is preserved
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param type          type of bolus (NORMAL, SMB, PRIME)
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun syncBolusWithPumpId(timestamp: Long, amount: PumpInsulin, type: BS.Type?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of carbs
     *
     * Assuming there will be no clash on timestamp from different pumps or UI
     * only timestamp is compared
     *
     * If db record doesn't exist, new record is created.
     * If exists, data is ignored
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of carbs
     * @param pumpId        pump id from history if coming form pump history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean

    /*
     *   THERAPY EVENTS
     */

    /**
     * Synchronization of events like CANNULA_CHANGE
     *
     * Assuming there will be no clash on timestamp from different pumps
     * only timestamp and type is compared
     *
     * If db record doesn't exist, new record is created.
     * If exists, data is ignored
     *
     * @param timestamp     timestamp of event from pump history
     * @param type          type like CANNULA_CHANGE, INSULIN_CHANGE
     * @param note          note
     * @param pumpId        pump id from history if available
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun insertTherapyEventIfNewWithTimestamp(timestamp: Long, type: TE.Type, note: String? = null, pumpId: Long? = null, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of FINGER_STICK_BG_VALUE events
     *
     * Assuming there will be no clash on timestamp from different pumps
     * only timestamp and type is compared
     *
     * If db record doesn't exist, new record is created.
     * If exists, data is ignored
     *
     * @param timestamp     timestamp of event from pump history
     * @param glucose       glucose value
     * @param glucoseUnit   glucose unit
     * @param note          note
     * @param pumpId        pump id from history if available
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/
    fun insertFingerBgIfNewWithTimestamp(timestamp: Long, glucose: Double, glucoseUnit: GlucoseUnit, note: String? = null, pumpId: Long? = null, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Create an announcement
     *
     * It's common TherapyEvent NOTE
     * Event is sent to NS as an announcement
     *
     * Common use is report failures like occlusion, empty reservoir etc
     *
     * Created with now() as a timestamp
     *
     * @param error         error message
     * @param pumpId        pump id from history if available
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     **/
    fun insertAnnouncement(error: String, pumpId: Long? = null, pumpType: PumpType, pumpSerial: String)

    /*
     *   TEMPORARY BASALS
     */

    enum class TemporaryBasalType {
        NORMAL,
        EMULATED_PUMP_SUSPEND,  // Initiated by AAPS as zero TBR
        PUMP_SUSPEND,           // Initiated on PUMP
        SUPERBOLUS;

        fun toDbType(): TB.Type =
            when (this) {
                NORMAL                -> TB.Type.NORMAL
                EMULATED_PUMP_SUSPEND -> TB.Type.EMULATED_PUMP_SUSPEND
                PUMP_SUSPEND          -> TB.Type.PUMP_SUSPEND
                SUPERBOLUS            -> TB.Type.SUPERBOLUS
            }

        companion object {

            fun fromDbType(dbType: TB.Type) = entries.firstOrNull { it.name == dbType.name }
                ?: NORMAL
        }

    }

    /**
     * Synchronization of temporary basals
     *
     * Search for combination of pumpId, PumpType, pumpSerial
     *
     * If exists, timestamp, duration, rate and type (if provided) is updated
     * If db record doesn't exist, new record is created.
     *      If overlap another running TBR, running is cut off
     * isValid field is preserved
     *
     * if driver does cut of ended TBR by itself use only [syncTemporaryBasalWithPumpId]
     * if driver send another [syncTemporaryBasalWithPumpId] to cut previous by AAPS it's necessary
     *      to send [syncTemporaryBasalWithPumpId] sorted by timestamp for proper cutting
     *
     * if driver use combination of start [syncTemporaryBasalWithPumpId] and end [syncStopTemporaryBasalWithPumpId]
     *      events AAPS does the cutting itself. Events must be sorted by timestamp
     * if db record already has endPumpId assigned by [syncStopTemporaryBasalWithPumpId] other updates
     *      are ignored
     *
     * see [app.aaps.database.impl.transactions.SyncPumpTemporaryBasalTransaction]
     *
     * @param timestamp     timestamp of event from pump history
     * @param rate          TBR rate in U/h or % (value of 100% is equal to no TBR)
     * @param duration      duration in milliseconds
     * @param isAbsolute    is TBR in U/h or % ?
     * @param type          type of TBR, from request sent to the driver
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/

    fun syncTemporaryBasalWithPumpId(timestamp: Long, rate: PumpRate, duration: Long, isAbsolute: Boolean, type: TemporaryBasalType?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of temporary basals end event
     * (for pumps having separate event for end of TBR or not having history)
     * (not useful for pump modifying duration in history log)
     *
     * Search first for a TBR with combination of endPumpId, pumpType, pumpSerial
     *      if found assume, some running TBR has been already cut off and ignore data. False is returned
     *
     * Search for running TBR with combination of pumpType, pumpSerial
     *
     * If exists,
     *      currently running record is cut off by provided timestamp (ie duration is adjusted)
     *      endPumpId is stored to running record
     * If db record doesn't exist data is ignored and false returned
     *
     * see [app.aaps.database.transactions.SyncPumpCancelTemporaryBasalIfAnyTransaction]
     *
     * @param timestamp     timestamp of event from pump history
     * @param endPumpId     pump id of ending event from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @param ignorePumpIds if true data is not checked for valid pump
     * @return true if running record is found and ended by changing duration
     **/
    fun syncStopTemporaryBasalWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String, ignorePumpIds: Boolean = false): Boolean

    /**
     * Create temporary basal with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist, new record is created.
     * If exists false is returned and data is ignored
     *
     * USAGE:
     * Generate unique temporaryId
     * Call on setting temporary basal when no pumpId is known (provide timestamp, temporaryId, type, pumpType, pumpSerial)
     * After reading record from history or completed bolus call [syncTemporaryBasalWithTempId] with the same temporaryId provided
     * If syncTemporaryBasalWithTempId is not called afterwards record remains valid and is calculated towards iob
     *
     * @param timestamp     timestamp of event from pump history
     * @param rate          TBR rate in U/h or % (value of 100% is equal to no TBR)
     * @param duration      duration in milliseconds
     * @param isAbsolute    is TBR in U/h or % ?
     * @param tempId        pump id from history
     * @param type          type of TBR, from request sent to the driver
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     *
     * see [app.aaps.database.impl.transactions.InsertTemporaryBasalWithTempIdTransaction]
     **/

    fun addTemporaryBasalWithTempId(timestamp: Long, rate: PumpRate, duration: Long, isAbsolute: Boolean, tempId: Long, type: TemporaryBasalType, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of temporary basal with temporary id
     *
     * Search for combination of  temporaryId, PumpType, pumpSerial
     *
     * If db record doesn't exist data is ignored and false returned.
     * If exists, data is updated, type and pumpId only if provided
     * isValid field is preserved
     *
     * USAGE:
     * After reading record from history or completed bolus call syncTemporaryBasalWithTempId and
     * provide updated timestamp, rate, duration, pumpId (if known), type (if change needed) with the same temporaryId, pumpType, pumpSerial
     *
     * @param timestamp     timestamp of event from pump history
     * @param rate          TBR rate in U/h or % (value of 100% is equal to no TBR)
     * @param duration      duration in milliseconds
     * @param isAbsolute    is TBR in U/h or % ?
     * @param temporaryId   temporary id generated when pump id in not know yet
     * @param type          type of TBR, from request sent to the driver
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if record is successfully updated
     **/
    fun syncTemporaryBasalWithTempId(
        timestamp: Long,
        rate: PumpRate,
        duration: Long,
        isAbsolute: Boolean,
        temporaryId: Long,
        type: TemporaryBasalType?,
        pumpId: Long?,
        pumpType: PumpType,
        pumpSerial: String
    ): Boolean

    /**
     * Invalidate of temporary basals that failed to start
     * EROS specific, replace by setting duration to zero ????
     *
     * If exists, isValid is set false
     * If db record doesn't exist data is ignored and false returned
     *
     *
     * @param id temporary basal id
     * @param sources caller
     * @param timestamp caller
     * @return true if running record is found and invalidated
     **/
    fun invalidateTemporaryBasal(id: Long, sources: Sources, timestamp: Long): Boolean

    /**
     * Invalidate of temporary basals that failed to start
     *
     * If exists, isValid is set false
     * If db record doesn't exist data is ignored and false returned
     *
     *
     * @param pumpId        pumpId of temporary basal
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if running record is found and invalidated
     **/

    fun invalidateTemporaryBasalWithPumpId(pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Invalidate of temporary basals that failed to start
     * MDT specific
     *
     * If exists, isValid is set false
     * If db record doesn't exist data is ignored and false returned
     *
     *
     * @param temporaryId    temporary id of temporary basal
     * @return true if running record is found and invalidated
     **/
    fun invalidateTemporaryBasalWithTempId(temporaryId: Long): Boolean

    /**
     * Synchronization of extended bolus
     *
     * Search for combination of pumpId, PumpType, pumpSerial
     *
     * If exists and endId is null (ie. has not been cut off), timestamp, duration, amount is updated
     * If overlap another running EB, running is cut off and new record is created
     * If db record doesn't exist, new record is created.
     * isValid field is preserved
     *
     * see [app.aaps.database.impl.transactions.SyncPumpExtendedBolusTransaction]
     *
     * @param timestamp     timestamp of event from pump history
     * @param rate        EB total amount in U
     * @param duration      duration in milliseconds
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/

    fun syncExtendedBolusWithPumpId(timestamp: Long, rate: PumpRate, duration: Long, isEmulatingTB: Boolean, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

    /**
     * Synchronization of extended bolus end event
     * (for pumps having separate event for end of EB or not having history)
     * (not useful for pump modifying duration in history log)
     *
     * Search first for a TBR with combination of endPumpId, pumpType, pumpSerial
     *      if found assume, some running EB has been already cut off and ignore data. False is returned
     *
     * Search for running EB with combination of pumpType, pumpSerial
     *
     * If exists,
     *      currently running record is cut off by provided timestamp (ie duration and amount is adjusted)
     *      endPumpId is stored to running record
     * If db record doesn't exist data is ignored and false returned
     *
     * see [app.aaps.database.impl.transactions.SyncPumpCancelExtendedBolusIfAnyTransaction]
     *
     * @param timestamp     timestamp of event from pump history
     * @param endPumpId     pump id of ending event from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if running record is found and ended by changing duration
     **/
    fun syncStopExtendedBolusWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

    /*
    *   TOTAL DAILY DOSE
    */

    /**
     * Synchronization of TDD
     *
     * Search for existing record following way
     *      1. pumpId, pumpType, pumpSerial (only if pumpId is provided)
     *      2. timestamp, pumpType, pumpSerial
     *
     * If record is found data is updated
     *      isValid field is preserved
     * If db record doesn't exist, new record is created.
     *
     * see [app.aaps.database.impl.transactions.SyncPumpTotalDailyDoseTransaction]
     *
     * @param timestamp     timestamp of event from pump history
     * @param bolusAmount   bolus part
     * @param basalAmount   basal part
     * @param totalAmount   if > 0, this value is used as total. Otherwise it's calculated as basal + bolus
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if new record is created
     **/

    fun createOrUpdateTotalDailyDose(timestamp: Long, bolusAmount: Double, basalAmount: Double, totalAmount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean


    /**
     * Check if a Profile is Running at time
     * INSIGHT Specific
     *
     * Search for a running profile to allow synchronization of extended boluses
     *
     * @param time  time of the requested verification
     * @return true if running profile is found
     **/

    fun isProfileRunning(time: Long): Boolean
}