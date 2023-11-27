package app.aaps.core.data.iob

data class GlucoseStatus(
    val glucose: Double,
    val noise: Double = 0.0,
    val delta: Double = 0.0,
    val shortAvgDelta: Double = 0.0,
    val longAvgDelta: Double = 0.0,
    val date: Long = 0L,
    val duraISFminutes: Double = 0.0,
    val duraISFaverage: Double = 0.0,
    val parabolaMinutes: Double = 0.0,
    val deltaPl: Double = 0.0,
    val deltaPn: Double = 0.0,
    val bgAcceleration: Double = 0.0,
    val a0: Double = 0.0,
    val a1: Double = 0.0,
    val a2: Double = 0.0,
    val corrSqu: Double = 0.0
)