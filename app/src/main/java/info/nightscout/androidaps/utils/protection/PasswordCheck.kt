package info.nightscout.androidaps.utils.protection

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PasswordCheck @Inject constructor(val sp: SP) {

    @SuppressLint("InflateParams")
    fun queryPassword(activity: FragmentActivity, @StringRes labelId: Int, @StringRes preference: Int, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null) {
        val password = sp.getString(preference, "")
        if (password == "") {
            ok?.run()
            return
        }
        val promptsView = LayoutInflater.from(activity).inflate(R.layout.passwordprompt, null)

        val alertDialogBuilder = AlertDialog.Builder(activity)
        alertDialogBuilder.setView(promptsView)

        val label = promptsView.findViewById<View>(R.id.passwordprompt_text) as TextView
        label.text = activity.getString(labelId)
        val userInput = promptsView.findViewById<View>(R.id.passwordprompt_pass) as EditText

        alertDialogBuilder
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                if (password == enteredPassword) ok?.run()
                else {
                    ToastUtils.showToastInUiThread(activity, activity.getString(R.string.wrongpassword))
                    fail?.run()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel)
            ) { dialog, _ ->
                cancel?.run()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }
}
