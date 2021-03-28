package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

interface PumpSync {
    fun addBolusWithTempId(timestamp: Long, amount: Double, driverId: Long, pumpType: PumpType, pumpSerial: String)
    fun syncBolusWithTempId(timestamp: Long, amount: Double, driverId: Long, pumpId: Long?, pumpType: PumpType, pumpSerial: String)

    /**
     * Synchronization of boluses
     *
     * Search for combination of pumpId, PumpType, pumpSerial
     *
     * If db record doesn't exist, new record is created.
     * If exists, data is updated
     * isValid field is preserved
     *
     * @param timestamp     timestamp of event from pump history
     * @param amount        amount of insulin
     * @param type          type of bolus (NORMAL, SMB, PRIME). Default is NORMAL
     * @param pumpId        pump id from history
     * @param pumpType      pump type like PumpType.ACCU_CHEK_COMBO
     * @param pumpSerial    pump serial number
     **/
    fun syncBolusWithPumpId(timestamp: Long, amount: Double, type: DetailedBolusInfo.BolusType = DetailedBolusInfo.BolusType.NORMAL, pumpId: Long, pumpType: PumpType, pumpSerial: String)

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
     **/
    fun syncCarbsWithTimestamp(timestamp: Long, amount: Double, pumpId: Long?, pumpType: PumpType, pumpSerial: String)

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