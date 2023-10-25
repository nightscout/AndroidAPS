package app.aaps.core.interfaces.iob

class MealData {

    var carbs = 0.0
    var mealCOB = 0.0
    var slopeFromMaxDeviation = 0.0
    var slopeFromMinDeviation = 999.0
    var lastBolusTime: Long = 0
    var lastCarbTime = 0L
    var usedMinCarbsImpact = 0.0
}