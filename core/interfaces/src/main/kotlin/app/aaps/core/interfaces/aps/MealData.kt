package app.aaps.core.interfaces.aps

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
@OptIn(InternalSerializationApi::class)
data class MealData(

    var carbs: Double = 0.0,
    var mealCOB: Double = 0.0,
    var slopeFromMaxDeviation: Double = 0.0,
    var slopeFromMinDeviation: Double = 999.0,
    var lastBolusTime: Long = 0,
    var lastCarbTime: Long = 0L,
    var usedMinCarbsImpact: Double = 0.0
)