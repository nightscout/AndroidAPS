package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

/**
 * This interface allows pump drivers to push data changes (creation and update of treatments) back to AAPS-core.
 *
 * Intended use cases for handling bolus treatments:
 *
 *  - for pumps that have a reliable history that can be read and which therefore issue a bolus on the pump,
 *    read the history back and add new bolus entries on the pump, the method [syncBolusWithPumpId]
 *    are used to inform AAPS-core of a new bolus.
 *  - for pumps that don't support history or take rather long to complete a bolus, the methods
 *    [addBolusWithTempId] and [syncBolusWithTempId] provide a mechanism to notify AAPS-core of a started
 *    bolus, so AAPS-core can operate under the assumption the bolus will be delivered and effect IOB until delivery
 *    completed. Upon completion, the pump driver will call the second method to turn a temporary bolus into a finished
 *    bolus.
 */
interface PumpSync {
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
    fun addBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType, pumpType: PumpType, pumpSerial: String) : Boolean

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
    fun syncBolusWithTempId(timestamp: Long, amount: Double, temporaryId: Long, type: DetailedBolusInfo.BolusType?, pumpId: Long?, pumpType: PumpType, pumpSerial: String) : Boolean

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
    fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: DetailedBolusInfo.BolusType?, pumpId: Long, pumpType: PumpType, pumpSerial: String) : Boolean

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
    fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String) : Boolean

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
}