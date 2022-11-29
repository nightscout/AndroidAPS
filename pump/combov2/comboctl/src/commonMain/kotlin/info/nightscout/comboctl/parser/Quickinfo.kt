package info.nightscout.comboctl.parser

/**
 * Reservoir state as shown on display.
 */
enum class ReservoirState {
    EMPTY,
    LOW,
    FULL
}

/**
 * Data class with the contents of the RT quickinfo screen.
 */
data class Quickinfo(val availableUnits: Int, val reservoirState: ReservoirState)
