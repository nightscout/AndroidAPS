package app.aaps.core.keys

import android.content.Context
import androidx.annotation.StringRes

sealed class ResourceOrString {

    data class AsResource(@StringRes val key: Int) : ResourceOrString()
    data class AsString(val stringKey: String) : ResourceOrString()

    fun asString(context: Context): String =
        when (this) {
            is AsResource -> context.getString(key)
            is AsString   -> stringKey
        }
}