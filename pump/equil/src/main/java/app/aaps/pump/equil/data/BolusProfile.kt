package app.aaps.pump.equil.data


class BolusProfile() {
    var timestamp: Long = 0
    var injectStart: Long = 0
    var injectStop: Long = 0
    var stop: Boolean = false
    var insulin: Double = 0.0
}