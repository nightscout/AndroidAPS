package app.aaps.plugins.aps.openAPS

data class RT(
    val temp: String = "absolute",
    var bg: Double? = null,
    var tick: String? = null,
    var eventualBG: Int? = null,
    var targetBG: Double? = null,
    var snoozeBG: Int? = null, // AMA only
    var insulinReq: Double? = null,
    var carbsReq: Int? = null,
    var carbsReqWithin: Int? = null,
    var units: Double? = null, // microbolus
    var deliverAt: Long? = null, // The time at which the microbolus should be delivered
    var sensitivityRatio: Double? = null, // autosens ratio (fraction of normal basal)
    var reason: StringBuilder = StringBuilder(),
    var duration: Int? = null,
    var rate: Double? = null,
    var predBGs: Predictions?= null,
    var COB: Double? = null,
    var IOB: Double? = null,
    var variable_sens: Double? = null,

    var consoleLog: MutableList<String>? = null,
    var consoleError: MutableList<String>? = null
) {
    data class Predictions (
        var IOB: List<Int>? = null,
        var ZT: List<Int>? = null,
        var COB: List<Int>? = null,
        var aCOB: List<Int>? = null, // AMA only
        var UAM: List<Int>? = null
    )
}