package app.aaps.plugins.aps.openAPS

data class GlucoseStatus(
    var glucose: Double,
    var noise: Int,
    var delta: Double,
    var short_avgdelta: Double,
    var long_avgdelta: Double,
    var date: Long,
    var duraISFminutes: Double?,
    var duraISFaverage: Double?,
    var bgAcceleration: Double?,
    var a0: Double?,
    var a1: Double?,
    var a2: Double?,
    var corrSqu: Double?
)