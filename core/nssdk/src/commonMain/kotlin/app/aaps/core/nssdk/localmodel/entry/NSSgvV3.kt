package app.aaps.core.nssdk.localmodel.entry

import kotlinx.serialization.Serializable

@Serializable
data class NSSgvV3(
    var date: Long?,
    val device: String? = null, // sourceSensor
    val identifier: String?,
    val srvModified: Long? = null,
    val srvCreated: Long? = null,
    var utcOffset: Long?,
    val subject: String? = null,
    var isReadOnly: Boolean = false,
    val isValid: Boolean,
    val sgv: Double,
    val units: NsUnits,
    val direction: Direction?,
    val noise: Double?,
    val filtered: Double?, // number in doc (I found decimal values in API v1
    val unfiltered: Double? // number in doc (I found decimal values in API v1
)
