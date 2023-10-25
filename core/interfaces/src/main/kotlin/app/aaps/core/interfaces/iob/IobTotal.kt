package app.aaps.core.interfaces.iob

@Suppress("SpellCheckingInspection")
open class IobTotal(val time: Long) {

    var iob = 0.0
    var activity = 0.0
    var bolussnooze = 0.0
    var basaliob = 0.0
    var netbasalinsulin = 0.0
    var hightempinsulin = 0.0

    // oref1
    var lastBolusTime: Long = 0
    var iobWithZeroTemp: IobTotal? = null
    var netInsulin = 0.0 // for calculations from temp basals only
    var extendedBolusInsulin = 0.0 // total insulin for extended bolus

    companion object
}
