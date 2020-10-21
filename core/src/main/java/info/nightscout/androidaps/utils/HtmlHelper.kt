package info.nightscout.androidaps.utils

import android.text.Html
import android.text.Spanned

object HtmlHelper {
    fun fromHtml(source: String): Spanned {
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
    }
}