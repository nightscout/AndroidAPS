package app.aaps.core.ui.toast

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.getThemeColor
import java.lang.ref.WeakReference

object ToastUtils {

    /** Reference to last displayed Toast */
    private var lastToast: WeakReference<Toast> = WeakReference(null)

    fun warnToast(ctx: Context?, string: String?) {
        graphicalToast(ctx, string, R.drawable.ic_toast_warn, true)
    }

    fun warnToast(ctx: Context?, @StringRes id: Int) {
        graphicalToast(ctx, ctx?.getString(id), R.drawable.ic_toast_warn, true)
    }

    fun infoToast(ctx: Context?, string: String?) {
        graphicalToast(ctx, string, R.drawable.ic_toast_info, true)
    }

    fun infoToast(ctx: Context?, @StringRes id: Int) {
        graphicalToast(ctx, ctx?.getString(id), R.drawable.ic_toast_info, true)
    }

    fun okToast(ctx: Context?, string: String?, isShort: Boolean = true) {
        graphicalToast(ctx, string, R.drawable.ic_toast_check, isShort)
    }

    fun errorToast(ctx: Context?, string: String?) {
        graphicalToast(ctx, string, R.drawable.ic_toast_error, true)
    }

    fun longErrorToast(ctx: Context?, string: String?) {
        graphicalToast(ctx, string, R.drawable.ic_toast_error, false)
    }

    fun errorToast(ctx: Context?, @StringRes id: Int) {
        graphicalToast(ctx, ctx?.getString(id), R.drawable.ic_toast_error, true)
    }

    fun graphicalToast(ctx: Context?, string: String?, @DrawableRes iconId: Int) {
        graphicalToast(ctx, string, iconId, true)
    }

    @SuppressLint("InflateParams")
    fun graphicalToast(ctx: Context?, string: String?, @DrawableRes iconId: Int, isShort: Boolean) {
        runOnUiThread {
            val toastRoot = LayoutInflater.from(ContextThemeWrapper(ctx, R.style.AppTheme)).inflate(R.layout.toast, null)
            val toastMessage = toastRoot.findViewById<TextView>(android.R.id.message)
            toastMessage.text = string
            val toastIcon = toastRoot.findViewById<ImageView>(android.R.id.icon)
            toastIcon.setImageResource(iconId)
            lastToast.get()?.cancel()
            val toast = Toast(ctx)
            toast.duration = if (isShort) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            @Suppress("DEPRECATION")
            toast.view = toastRoot
            toast.show()
            lastToast = WeakReference(toast)
        }
    }

    fun showToastInUiThread(ctx: Context?, string: String?) {
        runOnUiThread {
            val toast: Toast =
                Toast.makeText(
                    ctx,
                    fromHtml("<font color='" + ContextThemeWrapper(ctx, R.style.AppTheme).getThemeColor(R.attr.toastBaseTextColor) + "'>" + string + "</font>"),
                    Toast.LENGTH_SHORT
                )
            toast.show()
        }
    }

    fun playSound(ctx: Context, soundID: Int) {
        val audioAttributionContext = ctx.createAttributionContext("aapsAudio")
        val soundMP = MediaPlayer.create(audioAttributionContext, soundID)
        soundMP.start()
        soundMP.setOnCompletionListener { obj: MediaPlayer -> obj.release() }
    }

    private fun fromHtml(source: String): Spanned {
        return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY)
    }

    object Long {

        fun warnToast(ctx: Context?, string: String) {
            graphicalToast(ctx, string, R.drawable.ic_toast_warn, false)
        }

        fun infoToast(ctx: Context?, string: String) {
            graphicalToast(ctx, string, R.drawable.ic_toast_info, false)
        }

        fun okToast(ctx: Context?, string: String) {
            graphicalToast(ctx, string, R.drawable.ic_toast_check, false)
        }

        fun errorToast(ctx: Context?, string: String) {
            graphicalToast(ctx, string, R.drawable.ic_toast_error, false)
        }
    }
}