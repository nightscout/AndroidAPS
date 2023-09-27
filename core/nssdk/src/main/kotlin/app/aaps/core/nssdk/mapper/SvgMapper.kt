package app.aaps.core.nssdk.mapper

import com.google.gson.Gson
import app.aaps.core.nssdk.localmodel.entry.Direction
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.remotemodel.RemoteEntry

fun NSSgvV3.convertToRemoteAndBack(): NSSgvV3? =
    toRemoteEntry().toSgv()

fun String.toNSSgvV3(): NSSgvV3? =
    Gson().fromJson(this, RemoteEntry::class.java).toSgv()

internal fun RemoteEntry.toSgv(): NSSgvV3? {

    this.sgv ?: return null
    if (this.type != "sgv") return null

    return NSSgvV3(
        date = this.date ?: 0L,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset,
        subject = this.subject,
        direction = this.direction.toDirection(),
        sgv = this.sgv,
        isReadOnly = this.isReadOnly ?: false,
        isValid = this.isValid ?: true,
        noise = this.noise, // TODO: to Enum?
        filtered = this.filtered,
        unfiltered = this.unfiltered,
        units = NsUnits.fromString(this.units),
    )
}

private fun String?.toDirection(): Direction =
    Direction.values().firstOrNull { it.nsName == this } ?: Direction.INVALID

internal fun NSSgvV3.toRemoteEntry(): RemoteEntry =
    RemoteEntry(
        type = "sgv",
        date = this.date,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset,
        subject = this.subject,
        direction = this.direction?.nsName,
        sgv = this.sgv,
        isReadOnly = this.isReadOnly,
        isValid = this.isValid,
        noise = this.noise,
        filtered = this.filtered,
        unfiltered = this.unfiltered,
        units = this.units.value
    )

