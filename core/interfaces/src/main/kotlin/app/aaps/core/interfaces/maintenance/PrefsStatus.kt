package app.aaps.core.interfaces.maintenance

import android.os.Parcelable

interface PrefsStatus : Parcelable {

    val icon: Int
    val isOk: Boolean get() = false
    val isWarning: Boolean get() = false
    val isError: Boolean get() = false
}