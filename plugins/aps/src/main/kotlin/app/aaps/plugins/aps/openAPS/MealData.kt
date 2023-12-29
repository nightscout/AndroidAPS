package app.aaps.plugins.aps.openAPS

class MealData (
    var carbs: Int,
    var mealCOB: Double,
    var slopeFromMaxDeviation: Double,
    var slopeFromMinDeviation: Double,
    var lastBolusTime: Long,
    var lastCarbTime: Long
)