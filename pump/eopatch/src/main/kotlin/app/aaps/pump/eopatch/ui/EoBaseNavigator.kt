package app.aaps.pump.eopatch.ui

interface EoBaseNavigator {

    fun back()

    fun finish(finishAffinity: Boolean = false)
}