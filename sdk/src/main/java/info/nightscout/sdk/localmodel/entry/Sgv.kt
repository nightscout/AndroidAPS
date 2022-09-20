package info.nightscout.sdk.localmodel.entry

data class Sgv(
    override val date: Long,
    override val device: String?,
    override val identifier: String,
    override val srvModified: Long,
    override val srvCreated: Long,
    override val utcOffset: Int,
    override val subject: String?,
    override var isReadOnly: Boolean,
    override val isValid: Boolean,
    val sgv: Double, // TODO: might be Double?
    val units: SgvUnits,
    val direction: Direction,
    val noise: Int?, // TODO: enum?
    val filtered: Double?, // number in doc (I found decimal values in API v1
    val unfiltered: Double?, // number in doc (I found decimal values in API v1
    // TODO: add SVG fields
) : Entry
