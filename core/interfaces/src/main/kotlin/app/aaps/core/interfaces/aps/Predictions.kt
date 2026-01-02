package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

@Serializable
data class Predictions(
    var IOB: List<Int>? = null,
    var ZT: List<Int>? = null,
    var COB: List<Int>? = null,
    var aCOB: List<Int>? = null, // AMA only
    var UAM: List<Int>? = null
)