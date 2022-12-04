package info.nightscout.interfaces.bgQualityCheck

import androidx.annotation.DrawableRes

interface BgQualityCheck {
    enum class State {
        UNKNOWN,
        FIVE_MIN_DATA,
        RECALCULATED,
        DOUBLED,
        FLAT // stale data for 45 min
    }

    var state: State
    var message: String
    @DrawableRes fun icon(): Int
    fun stateDescription(): String
}