package app.aaps.core.interfaces.profile

interface DefaultValueHelper {

    var bgTargetLow: Double
    var bgTargetHigh: Double

    fun determineHighLine(): Double
    fun determineLowLine(): Double
}