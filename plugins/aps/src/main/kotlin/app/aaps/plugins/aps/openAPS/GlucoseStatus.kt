package app.aaps.plugins.aps.openAPS

data class GlucoseStatus(
    var glucose: Double,
    var noise: Int,
    var delta: Double,
    var short_avgdelta: Double,
    var long_avgdelta: Double,
    var date: Long
)