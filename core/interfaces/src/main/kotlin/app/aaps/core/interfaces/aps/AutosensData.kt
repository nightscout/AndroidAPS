package app.aaps.core.interfaces.aps

interface AutosensData {

    data class CarbsInPast(

        var time: Long,
        var carbs: Double,
        var min5minCarbImpact: Double = 0.0,
        var remaining: Double
    ) {
        // override fun toString(): String =
        //     String.format(Locale.ENGLISH, "CarbsInPast: time: %s carbs: %.02f min5minCI: %.02f remaining: %.2f", dateUtil.dateAndTimeString(time), carbs, min5minCarbImpact, remaining)
    }

    var time: Long
    var bg: Double
    var pastSensitivity: String
    var deviation: Double
    var validDeviation: Boolean
    var activeCarbsList: MutableList<CarbsInPast>
    var absorbed: Double
    var carbsFromBolus: Double
    var cob: Double
    var bgi: Double
    var delta: Double
    var avgDelta: Double
    var slopeFromMaxDeviation: Double
    var slopeFromMinDeviation: Double
    var usedMinCarbsImpact: Double
    var failOverToMinAbsorptionRate: Boolean

    var avgDeviation: Double

    var absorbing: Boolean
    var mealCarbs: Double
    var mealStartCounter: Int
    var type: String
    var uam: Boolean
    var extraDeviation: MutableList<Double>

    var autosensResult: AutosensResult

    fun cloneCarbsList(): MutableList<CarbsInPast>
    fun deductAbsorbedCarbs()
    fun removeOldCarbs(toTime: Long, isAAPSOrWeighted: Boolean)
}