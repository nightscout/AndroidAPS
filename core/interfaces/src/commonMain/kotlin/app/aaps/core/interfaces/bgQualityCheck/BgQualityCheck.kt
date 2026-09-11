package app.aaps.core.interfaces.bgQualityCheck

import kotlinx.coroutines.flow.StateFlow

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
    val stateFlow: StateFlow<State>
    fun stateDescription(): String
}
