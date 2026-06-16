package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.entry.NSMbgV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.remotemodel.RemoteEntry
import com.google.gson.Gson

fun String.toCalibrationMbg(): NSMbgV3? =
    Gson().fromJson(this, RemoteEntry::class.java).toCalibrationMbg()

/**
 * Maps a NS `entries` document to an AAPS calibration mbg.
 * Returns null unless it is a `mbg` entry explicitly marked as an AAPS calibration
 * ([RemoteEntry.isCalibration] == true) and carrying both calibration values — so foreign
 * manual BG readings from other uploaders are never picked up as calibrations.
 */
internal fun RemoteEntry.toCalibrationMbg(): NSMbgV3? {

    if (this.type != "mbg") return null
    if (this.isCalibration != true) return null
    val date = this.date ?: return null
    val mbg = this.mbg ?: return null
    val sensorMgdlAtPairing = this.sensorMgdlAtPairing ?: return null

    return NSMbgV3(
        date = date,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset,
        subject = this.subject,
        isReadOnly = this.isReadOnly == true,
        isValid = this.isValid != false,
        mbg = mbg,
        sensorMgdlAtPairing = sensorMgdlAtPairing,
        units = NsUnits.fromString(this.units),
        isCalibration = true
    )
}

internal fun NSMbgV3.toRemoteEntry(): RemoteEntry =
    RemoteEntry(
        type = "mbg",
        date = this.date,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset,
        subject = this.subject,
        direction = null,
        sgv = null,
        isReadOnly = this.isReadOnly,
        isValid = this.isValid,
        noise = null,
        filtered = null,
        unfiltered = null,
        units = this.units.value,
        mbg = this.mbg,
        sensorMgdlAtPairing = this.sensorMgdlAtPairing,
        isCalibration = this.isCalibration
    )
