package info.nightscout.androidaps.interfaces

interface Interval {

    fun durationInMsec(): Long
    fun start(): Long

    // planned end time at time of creation
    fun originalEnd(): Long

    // end time after cut
    fun end(): Long
    fun cutEndTo(end: Long)
    fun match(time: Long): Boolean
    fun before(time: Long): Boolean
    fun after(time: Long): Boolean
    val isInProgress: Boolean
    val isEndingEvent: Boolean
    val isValid: Boolean
}