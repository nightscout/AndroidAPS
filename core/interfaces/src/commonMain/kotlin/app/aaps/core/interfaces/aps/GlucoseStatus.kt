package app.aaps.core.interfaces.aps

interface GlucoseStatus {

    val glucose: Double
    val noise: Double
    val delta: Double
    val shortAvgDelta: Double
    val longAvgDelta: Double
    val date: Long
}