package info.nightscout.androidaps.utils.protection

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.CryptoUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

// since androidx.autofill.HintConstants are not available
val AUTOFILL_HINT_NEW_PASSWORD = "newPassword"

@Singleton
class PasswordCheck @Inject constructor(val sp: SP) {

    @SuppressLint("InflateParams")
    fun queryPassword(activity: FragmentActivity, @StringRes labelId: Int, @StringRes preference: Int, ok: ( (String) -> Unit)?, cancel: (()->Unit)? = null, fail: (()->Unit)? = null) {
        val password = sp.getString(preference, "")
        if (password == "") {
                ok?.invoke("")
            return
        }

        val titleLayout = activity.layoutInflater.inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = activity.getString(labelId)
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_key_48dp)

        val promptsView = LayoutInflater.from(activity).inflate(R.layout.passwordprompt, null)

        val alertDialogBuilder = AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AppTheme))
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.passwordprompt_pass) as EditText
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val autoFillHintPasswordKind = activity.getString(preference)
            userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${autoFillHintPasswordKind}")
            userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(titleLayout)
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                if (CryptoUtil.checkPassword(enteredPassword, password)) ok?.invoke(enteredPassword)
                else {
                    ToastUtils.showToastInUiThread(activity, activity.getString(R.string.wrongpassword))
                    fail?.invoke()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel)
            ) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }

    @SuppressLint("InflateParams")
    fun setPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, ok: ( (String) -> Unit)? = null, cancel: (()->Unit)? = null, clear: (()->Unit)? = null) {
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)

        val titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = context.getText(labelId)
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_key_48dp)

        val alertDialogBuilder = AlertDialog.Builder(ContextThemeWrapper(context, R.style.AppTheme))
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.passwordprompt_pass) as EditText

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val autoFillHintPasswordKind = context.getString(preference)
            userInput.setAutofillHints(AUTOFILL_HINT_NEW_PASSWORD, "aaps_${autoFillHintPasswordKind}")
            userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(titleLayout)
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                if (enteredPassword.isNotEmpty()) {
                    sp.putString(preference, CryptoUtil.hashPassword(enteredPassword))
                    ToastUtils.showToastInUiThread(context, context.getString(R.string.password_set))
                    ok?.invoke(enteredPassword)
                } else {
                    if (sp.contains(preference)) {
                        sp.remove(preference)
                        ToastUtils.showToastInUiThread(context, context.getString(R.string.password_cleared))
                        clear?.invoke()
                    } else {
                        ToastUtils.showToastInUiThread(context, context.getString(R.string.password_not_changed))
                        cancel?.invoke()
                    }
                }

            }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                ToastUtils.showToastInUiThread(context, context.getString(R.string.password_not_changed))
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }
}
