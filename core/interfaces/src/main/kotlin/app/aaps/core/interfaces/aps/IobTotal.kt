package app.aaps.core.interfaces.aps

import kotlinx.serialization.Serializable

@Suppress("SpellCheckingInspection")
@Serializable
data class IobTotal(
    val time: Long,
    var iob: Double = 0.0,
    var activity: Double = 0.0,
    var bolussnooze: Double = 0.0,
    var basaliob: Double = 0.0,
    var netbasalinsulin: Double = 0.0,
    var hightempinsulin: Double = 0.0,

    // oref1
    var lastBolusTime: Long = 0,
    var iobWithZeroTemp: IobTotal? = null,
    var netInsulin: Double = 0.0, // for calculations from temp basals only
    var extendedBolusInsulin: Double = 0.0, // total insulin for extended bolus
) {

    companion object
}