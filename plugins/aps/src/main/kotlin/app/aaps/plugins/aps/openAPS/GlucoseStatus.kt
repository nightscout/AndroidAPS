package app.aaps.plugins.aps.openAPS

data class GlucoseStatus(
    var glucose: Double,
    var noise: Int,
    var delta: Double,
    var short_avgdelta: Double,
    var long_avgdelta: Double,
    var date: Long,
    var duraISFminutes: Double? = null,
    var duraISFaverage: Double? = null,
    var bgAcceleration: Double? = null,
    var a0: Double? = null,
    var a1: Double? = null,
    var a2: Double? = null,
    var corrSqu: Double? = null
)