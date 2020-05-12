package info.nightscout.androidaps.utils

import android.os.Build
import android.text.Html
import android.text.Spanned

object HtmlHelper {
    fun fromHtml(source: String): Spanned {
        // API level 24 to replace call
        @Suppress("DEPRECATION")
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
            else -> Html.fromHtml(source)
        }
    }
}