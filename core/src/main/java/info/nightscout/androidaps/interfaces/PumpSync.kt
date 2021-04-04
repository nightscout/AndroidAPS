package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

/**
 * This interface allows pump drivers to push data changes (creation and update of treatments, temporary basals and extended boluses) back to AAPS-core.
 *
 * Intended use cases for handling bolus treatments:
 *
 *  - for pumps that have a reliable history that can be read and which therefore issue a bolus on the pump,
 *    read the history back and add new bolus entries on the pump, the method [syncBolusWithPumpId]
 *    are used to inform AAPS-core of a new bolus.
 *    [VirtualPumpPlugin](info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin) is a pump driver that
 *    takes this approach.
 *  - for pumps that don't support history or take rather long to complete a bolus, the methods
 *    [addBolusWithTempId] and [syncBolusWithTempId] provide a mechanism to notify AAPS-core of a started
 *    bolus, so AAPS-core can operate under the assumption the bolus will be delivered and effect IOB until delivery
 *    completed. Upon completion, the pump driver will call the second method to turn a temporary bolus into a finished
 *    bolus.
 */
interface PumpSync {

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
     * After reading record from history or completed bolus call syncBolusWithTempId with the same temporaryId provided
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
    fun addBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType, pumpType: PumpType, pumpSerial: String): Boolean

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
    fun syncBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType?, pumpId: Long?, pumpType: PumpType, pumpSerial: String): Boolean

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
    fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: DetailedBolusInfo.BolusType?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

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
     **/
    fun insertTherapyEventIfNewWithTimestamp(timestamp: Long, type: DetailedBolusInfo.EventType, note: String? = null, pumpId: Long? = null, pumpType: PumpType, pumpSerial: String)

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

        fun toDbType(): TemporaryBasal.Type =
            when (this) {
                NORMAL                -> TemporaryBasal.Type.NORMAL
                EMULATED_PUMP_SUSPEND -> TemporaryBasal.Type.EMULATED_PUMP_SUSPEND
                PUMP_SUSPEND          -> TemporaryBasal.Type.PUMP_SUSPEND
                SUPERBOLUS            -> TemporaryBasal.Type.SUPERBOLUS
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

    fun syncTemporaryBasalWithPumpId(timestamp: Long, rate: Double, duration: Long, isAbsolute: Boolean, type: TemporaryBasalType?, pumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean

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
     * @param timestamp     timestamp of event from pump history
     * @param endPumpId     pump id of ending event from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     * @return true if running record is found and ended by changing duration
     **/
    fun syncStopTemporaryBasalWithPumpId(timestamp: Long, endPumpId: Long, pumpType: PumpType, pumpSerial: String): Boolean
}