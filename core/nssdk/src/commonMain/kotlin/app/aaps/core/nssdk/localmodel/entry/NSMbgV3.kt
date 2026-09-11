package app.aaps.core.nssdk.localmodel.entry

import kotlinx.serialization.Serializable

/**
 * Calibration pair carried over the NS `entries` collection as a marked `mbg` entry.
 * [mbg] is the fingerstick reference value; [sensorMgdlAtPairing] is the AAPS-specific field
 * a follower needs to re-fit the calibration curve. [isCalibration] is the marker that lets a
 * follower accept only AAPS calibration entries and ignore foreign manual BG readings.
 */
@Serializable
data class NSMbgV3(
    var date: Long?,
    val device: String? = null,
    val identifier: String?,
    val srvModified: Long? = null,
    val srvCreated: Long? = null,
    var utcOffset: Long?,
    val subject: String? = null,
    var isReadOnly: Boolean = false,
    val isValid: Boolean,
    val mbg: Double,
    val sensorMgdlAtPairing: Double,
    val units: NsUnits,
    val isCalibration: Boolean = true
)
