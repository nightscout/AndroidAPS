package app.aaps.core.data.model

import java.util.TimeZone

/**
 * Calibration pair used by the linear calibration engine: a fingerstick reference value
 * paired with the sensor value at that moment. Synced to NS as a marked `mbg` entry so a
 * follower can re-fit the same calibration curve locally.
 */
data class CAL(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    override var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var fingerstickMgdl: Double,
    var sensorMgdlAtPairing: Double
) : HasIDs, TimeStamped
