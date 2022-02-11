package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes

interface EoBaseNavigator {
    fun toast(message: String)

    fun toast(@StringRes message: Int)

    fun back()

    fun finish(finishAffinity: Boolean = false)

    fun startActivityForResult(action: Context.() -> Intent, requestCode: Int, vararg params: Pair<String, Any?>)

    fun checkCommunication(onSuccess: () -> Unit, onCancel: (() -> Unit)? = null, onDiscard: (() -> Unit)? = null, goHomeAfterDiscard: Boolean = true)
}