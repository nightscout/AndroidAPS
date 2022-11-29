package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.entry.Direction
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.remotemodel.RemoteEntry

@JvmSynthetic
internal fun RemoteEntry.toSgv(): NSSgvV3? {

    this.sgv ?: return null
    if (this.type != "sgv") return null

    return NSSgvV3(
        date = this.date,
        device = this.device,
        identifier = this.identifier,
        srvModified = this.srvModified,
        srvCreated = this.srvCreated,
        utcOffset = this.utcOffset ?: 0,
        subject = this.subject,
        direction = this.direction.toDirection(),
        sgv = this.sgv,
        isReadOnly = this.isReadOnly ?: false,
        isValid = this.isValid ?: true,
        noise = this.noise, // TODO: to Enum?
        filtered = this.filtered,
        unfiltered = this.unfiltered,
        units = NsUnits.fromString(this.units)
    )
}

private fun String?.toDirection(): Direction =
     Direction.values().firstOrNull { it.nsName == this } ?: Direction.INVALID
