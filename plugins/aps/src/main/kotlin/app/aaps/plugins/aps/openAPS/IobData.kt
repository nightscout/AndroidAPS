package app.aaps.plugins.aps.openAPS

typealias IobData = Array<Iob>

data class Iob(
    var iob: Double,
    var basaliob: Double,
    var bolussnooze: Double,
    var activity: Double,
    var lastBolusTime: Long,
    var time: String, // as ISO
    var iobWithZeroTemp: Iob?
)
