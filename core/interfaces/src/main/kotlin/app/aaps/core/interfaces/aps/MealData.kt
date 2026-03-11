package app.aaps.core.interfaces.aps

import app.aaps.annotations.DisplayAsDate
import kotlinx.serialization.Serializable

@Serializable
data class MealData(

    var carbs: Double = 0.0,
    var mealCOB: Double = 0.0,
    var slopeFromMaxDeviation: Double = 0.0,
    var slopeFromMinDeviation: Double = 999.0,
    @DisplayAsDate
    var lastBolusTime: Long = 0,
    @DisplayAsDate
    var lastCarbTime: Long = 0L,
    var usedMinCarbsImpact: Double = 0.0
)