package info.nightscout.core.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.nightscout.core.ui.R

object TwoMessagesAlertDialog {

    private fun runOnUiThread(theRunnable: Runnable?) = theRunnable?.let {
        Handler(Looper.getMainLooper()).post(it)
    }

    @SuppressLint("InflateParams")
    fun showAlert(context: Context, title: String, message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null) {

        val secondMessageLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_two_messages, null)
        (secondMessageLayout.findViewById<View>(R.id.password_prompt_title) as TextView).text = secondMessage

        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
            .setMessage(message)
            .setCustomTitle(
                AlertDialogHelper.buildCustomTitle(
                    context, title, icon
                        ?: R.drawable.ic_check_white_48dp
                )
            )
            .setView(secondMessageLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (ok != null) runOnUiThread { ok() }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (cancel != null) runOnUiThread { cancel() }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

}
