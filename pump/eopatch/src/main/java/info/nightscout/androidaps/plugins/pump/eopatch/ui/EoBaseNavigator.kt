package info.nightscout.androidaps.plugins.pump.eopatch.ui

interface EoBaseNavigator {
    fun back()

    fun finish(finishAffinity: Boolean = false)
}