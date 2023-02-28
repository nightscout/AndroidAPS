package info.nightscout.pump.medtrum.ui

interface BaseNavigator {
    fun back()

    fun finish(finishAffinity: Boolean = false)
}