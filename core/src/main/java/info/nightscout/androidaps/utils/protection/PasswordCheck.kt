package info.nightscout.androidaps.utils.protection

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.CryptoUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.AlertDialogHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

// since androidx.autofill.HintConstants are not available
const val AUTOFILL_HINT_NEW_PASSWORD = "newPassword"

@RequiresApi(Build.VERSION_CODES.O)
@Singleton
class PasswordCheck @Inject constructor(
    val sp: SP,
    private val cryptoUtil: CryptoUtil
) {

    /**
    Asks for "managed" kind of password, checking if it is valid.
     */
    @SuppressLint("InflateParams")
    fun queryPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, ok: ((String) -> Unit)?, cancel: (() -> Unit)? = null, fail: (() -> Unit)? = null) {
        val password = sp.getString(preference, "")
        if (password == "") {
            ok?.invoke("")
            return
        }

        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = AlertDialogHelper.Builder(context)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE

        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                if (cryptoUtil.checkPassword(enteredPassword, password)) ok?.invoke(enteredPassword)
                else {
                    ToastUtils.errorToast(context, context.getString(R.string.wrongpassword))
                    fail?.invoke()
                }
            }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }

    @SuppressLint("InflateParams")
    fun setPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, ok: ((String) -> Unit)? = null, cancel: (() -> Unit)? = null, clear: (() -> Unit)? = null) {
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = AlertDialogHelper.Builder(context)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(AUTOFILL_HINT_NEW_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                val enteredPassword2 = userInput2.text.toString()
                if (enteredPassword != enteredPassword2) {
                    ToastUtils.errorToast(context, context.getString(R.string.passwords_dont_match))
                } else if (enteredPassword.isNotEmpty()) {
                    sp.putString(preference, cryptoUtil.hashPassword(enteredPassword))
                    ToastUtils.okToast(context, context.getString(R.string.password_set))
                    ok?.invoke(enteredPassword)
                } else {
                    if (sp.contains(preference)) {
                        sp.remove(preference)
                        ToastUtils.graphicalToast(context, context.getString(R.string.password_cleared), R.drawable.ic_toast_delete_confirm)
                        clear?.invoke()
                    } else {
                        ToastUtils.warnToast(context, context.getString(R.string.password_not_changed))
                        cancel?.invoke()
                    }
                }

            }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                ToastUtils.infoToast(context, context.getString(R.string.password_not_changed))
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }

    /**
    Prompt free-form password, with additional help and warning messages.
    Preference ID (preference) is used only to generate ID for password managers,
    since this query does NOT check validity of password.
     */
    @SuppressLint("InflateParams")
    fun queryAnyPassword(context: Context, @StringRes labelId: Int, @StringRes preference: Int, @StringRes passwordExplanation: Int?,
                         @StringRes passwordWarning: Int?, ok: ((String) -> Unit)?, cancel: (() -> Unit)? = null) {

        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = AlertDialogHelper.Builder(context)
        alertDialogBuilder.setView(promptsView)
        passwordExplanation?.let { alertDialogBuilder.setMessage(it) }

        passwordWarning?.let {
            val extraWarning: TextView = promptsView.findViewById<View>(R.id.password_prompt_extra_message) as TextView
            extraWarning.text = context.getString(it)
            extraWarning.visibility = View.VISIBLE
        }

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE

        val autoFillHintPasswordKind = context.getString(preference)
        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${autoFillHintPasswordKind}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                ok?.invoke(enteredPassword)
            }
            .setNegativeButton(context.getString(R.string.cancel)
            ) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        alertDialogBuilder.create().show()
    }
}
