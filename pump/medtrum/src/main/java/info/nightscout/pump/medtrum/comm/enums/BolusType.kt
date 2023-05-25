package info.nightscout.pump.medtrum.comm.enums

enum class BolusType {
    NONE,
    NORMAL,
    EXTEND,
    COMBINATION;

    fun getValue(): Int {
        return ordinal
    }
}
