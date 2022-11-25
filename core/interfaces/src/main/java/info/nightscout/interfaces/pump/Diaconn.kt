package info.nightscout.interfaces.pump

interface Diaconn {

    fun loadHistory(): PumpEnactResult // for history browser
    fun setUserOptions(): PumpEnactResult // pump etc settings
}