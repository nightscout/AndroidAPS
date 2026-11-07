package app.aaps.core.interfaces.stats

interface TIR {

    val date: Long
    val lowThreshold: Double
    val highThreshold: Double
    var below: Int
    var inRange: Int
    var above: Int
    var error: Int
    var count: Int
    fun error()
    fun below()
    fun inRange()
    fun above()
}
