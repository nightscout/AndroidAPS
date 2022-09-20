package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.entry.Direction
import info.nightscout.sdk.localmodel.entry.Sgv
import info.nightscout.sdk.localmodel.entry.SgvUnits
import info.nightscout.sdk.remotemodel.RemoteEntry
import java.util.*

@JvmSynthetic
internal fun SgvUnits.toRemoteString(): String = when (this) {
    SgvUnits.MG_DL -> "mg/dl"
    SgvUnits.MMOL_L -> "mmol/l" // TODO check NS conventions
}

@JvmSynthetic
internal fun String?.toSvgUnits(): SgvUnits = when (this?.lowercase(Locale.getDefault())) {
    "mgdl", "mg/dl", "mg" -> SgvUnits.MG_DL
    "mmol", "mmol/l", "mol", "mmoll" -> SgvUnits.MMOL_L
    else -> SgvUnits.MG_DL
}

@JvmSynthetic
internal fun RemoteEntry.toSgv(): Sgv? {

    this.sgv ?: return null
    if (this.type != "sgv") return null

    return Sgv(
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
        units = this.units.toSvgUnits()
    )
}

private fun String?.toDirection(): Direction {
    // Todo: check for other writings?
    return Direction.values().firstOrNull { it.nsName == this } ?: Direction.INVALID
}
