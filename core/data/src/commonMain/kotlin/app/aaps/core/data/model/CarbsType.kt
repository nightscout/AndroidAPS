package app.aaps.core.data.model

/**
 * What kind of meal the carbs of a bolus wizard entry are. The type splits the entered grams into
 * carbs eaten now and extended carbs (eCarbs) that start later and are spread over some hours.
 *
 * Bread is the plain case: all carbs now, no eCarbs. Cake and pizza move part of the carbs to
 * eCarbs, because fat and protein slow the rise. The percentages are fixed, the same as the phone
 * wizard has always used.
 */
enum class CarbsType(
    val carbsPercent: Int,
    val eCarbsPercent: Int,
    val eCarbsDelayMinutes: Int,
    val eCarbsDurationHours: Int
) {

    BREAD(carbsPercent = 100, eCarbsPercent = 0, eCarbsDelayMinutes = 0, eCarbsDurationHours = 0),
    CAKE(carbsPercent = 90, eCarbsPercent = 20, eCarbsDelayMinutes = 60, eCarbsDurationHours = 2),
    PIZZA(carbsPercent = 80, eCarbsPercent = 35, eCarbsDelayMinutes = 60, eCarbsDurationHours = 3);

    /** The part of [totalCarbs] that is eaten now and goes into the calculation. */
    fun carbsNow(totalCarbs: Int): Int = totalCarbs * carbsPercent / 100

    /** The part of [totalCarbs] that is entered as eCarbs, 0 for bread. */
    fun eCarbs(totalCarbs: Int): Int = totalCarbs * eCarbsPercent / 100
}
