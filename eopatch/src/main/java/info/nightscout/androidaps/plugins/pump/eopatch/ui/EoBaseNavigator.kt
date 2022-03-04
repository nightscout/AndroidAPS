package info.nightscout.androidaps.plugins.pump.eopatch.ui

import androidx.annotation.StringRes

interface EoBaseNavigator {
    fun toast(message: String)

    fun toast(@StringRes message: Int)

    fun back()

    fun finish(finishAffinity: Boolean = false)
}