package info.nightscout.androidaps.utils.protection

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.ToastUtils

object PasswordCheck {
    fun queryPassword(activity: FragmentActivity, @StringRes labelId: Int, @StringRes preference: Int, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null) {
        val password = SP.getString(preference, "")
        if (password == "") {
            ok?.run()
            return
        }
        val promptsView = LayoutInflater.from(activity).inflate(R.layout.passwordprompt, null)

        val alertDialogBuilder = AlertDialog.Builder(activity)
        alertDialogBuilder.setView(promptsView)

        val label = promptsView.findViewById<View>(R.id.passwordprompt_text) as TextView
        label.text = MainApp.gs(labelId)
        val userInput = promptsView.findViewById<View>(R.id.passwordprompt_pass) as EditText

        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(MainApp.gs(R.string.ok)) { _, _ ->
                    val enteredPassword = userInput.text.toString()
                    if (password == enteredPassword) ok?.run()
                    else {
                        ToastUtils.showToastInUiThread(activity, MainApp.gs(R.string.wrongpassword))
                        fail?.run()
                    }
                }
                .setNegativeButton(MainApp.gs(R.string.cancel)
                ) { dialog, _ ->
                    cancel?.run()
                    dialog.cancel()
                }

        alertDialogBuilder.create().show()
    }
}
