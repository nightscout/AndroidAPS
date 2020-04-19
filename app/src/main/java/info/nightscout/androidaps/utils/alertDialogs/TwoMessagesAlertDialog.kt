package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.extensions.runOnUiThread

object TwoMessagesAlertDialog {

    @SuppressLint("InflateParams")
    fun showAlert(context: Context, title: String, message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)? = null, @DrawableRes icon: Int? = null) {

        val secondMessageLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_two_messages, null)
        (secondMessageLayout.findViewById<View>(R.id.password_prompt_title) as TextView).text = secondMessage

        val dialog = AlertDialogHelper.Builder(context)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title, icon
                ?: R.drawable.ic_check_while_48dp))
            .setView(secondMessageLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (ok != null) {
                    runOnUiThread(Runnable {
                        ok()
                    })
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (cancel != null) {
                    runOnUiThread(Runnable {
                        cancel()
                    })
                }
            }
            .show()
        dialog.setCanceledOnTouchOutside(false)
    }

}