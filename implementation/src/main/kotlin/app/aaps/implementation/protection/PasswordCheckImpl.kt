package app.aaps.implementation.protection

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.objects.R
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.Reusable
import javax.inject.Inject

@Reusable
class PasswordCheckImpl @Inject constructor(
    private val preferences: Preferences,
    private val cryptoUtil: CryptoUtil
) : PasswordCheck {

    // since androidx.autofill.HintConstants are not available
    @Suppress("PrivatePropertyName")
    private val AUTOFILL_HINT_NEW_PASSWORD = "newPassword"

    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore

    /**
    Asks for "managed" kind of password, checking if it is valid.
     */
    @SuppressLint("InflateParams")
    override fun queryPassword(context: Context, @StringRes labelId: Int, preference: StringPreferenceKey, ok: ((String) -> Unit)?, cancel: (() -> Unit)?, fail: (() -> Unit)?, pinInput: Boolean) {
        val password = preferences.get(preference)
        if (password == "") {
            ok?.invoke("")
            return
        }
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, app.aaps.core.ui.R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE
        if (pinInput) {
            userInput.setHint(app.aaps.core.ui.R.string.pin_hint)
            userInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${preference}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        fun validatePassword(): Boolean {
            val enteredPassword = userInput.text.toString()
            if (cryptoUtil.checkPassword(enteredPassword, password)) {
                val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                im.hideSoftInputFromWindow(userInput.windowToken, 0)
                ok?.invoke(enteredPassword)
                return true
            }
            val msg = if (pinInput) app.aaps.core.ui.R.string.wrongpin else app.aaps.core.ui.R.string.wrongpassword
            ToastUtils.errorToast(context, context.getString(msg))
            fail?.invoke()
            return false
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(app.aaps.core.ui.dialogs.AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(app.aaps.core.ui.R.string.ok)) { _, _ -> validatePassword() }
            .setNegativeButton(context.getString(app.aaps.core.ui.R.string.cancel)) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        val alert = alertDialogBuilder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            runOnUiThread { show() }
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (validatePassword())
                    alert.dismiss()
                true
            } else {
                false
            }
        }
    }

    @SuppressLint("InflateParams")
    override fun setPassword(context: Context, @StringRes labelId: Int, preference: StringPreferenceKey, ok: ((String) -> Unit)?, cancel: (() -> Unit)?, clear: (() -> Unit)?, pinInput: Boolean) {
        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, app.aaps.core.ui.R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText
        if (pinInput) {
            userInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            userInput2.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            userInput.setHint(app.aaps.core.ui.R.string.pin_hint)
            userInput2.setHint(app.aaps.core.ui.R.string.pin_hint)
        }
        userInput.setAutofillHints(AUTOFILL_HINT_NEW_PASSWORD, "aaps_${preference}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(app.aaps.core.ui.dialogs.AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(app.aaps.core.ui.R.string.ok)) { _, _ ->
                val enteredPassword = userInput.text.toString()
                val enteredPassword2 = userInput2.text.toString()
                if (enteredPassword != enteredPassword2) {
                    val msg = if (pinInput) app.aaps.core.ui.R.string.pin_dont_match else app.aaps.core.ui.R.string.passwords_dont_match
                    ToastUtils.errorToast(context, context.getString(msg))
                } else if (enteredPassword.isNotEmpty()) {
                    preferences.put(preference, cryptoUtil.hashPassword(enteredPassword))
                    exportPasswordDataStore.clearPasswordDataStore(context)
                    val msg = if (pinInput) app.aaps.core.ui.R.string.pin_set else app.aaps.core.ui.R.string.password_set
                    ToastUtils.okToast(context, context.getString(msg))
                    ok?.invoke(enteredPassword)
                } else {
                    if (preferences.getIfExists(preference) != null) {
                        preferences.remove(preference)
                        val msg = if (pinInput) app.aaps.core.ui.R.string.pin_cleared else app.aaps.core.ui.R.string.password_cleared
                        ToastUtils.graphicalToast(context, context.getString(msg), app.aaps.core.ui.R.drawable.ic_toast_delete_confirm)
                        clear?.invoke()
                    } else {
                        val msg = if (pinInput) app.aaps.core.ui.R.string.pin_not_changed else app.aaps.core.ui.R.string.password_not_changed
                        ToastUtils.warnToast(context, context.getString(msg))
                        cancel?.invoke()
                    }
                }

            }
            .setNegativeButton(
                context.getString(app.aaps.core.ui.R.string.cancel)
            ) { dialog, _ ->
                val msg = if (pinInput) app.aaps.core.ui.R.string.pin_not_changed else app.aaps.core.ui.R.string.password_not_changed
                ToastUtils.infoToast(context, msg)
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
    override fun queryAnyPassword(
        context: Context, @StringRes labelId: Int, preference: StringPreferenceKey, @StringRes passwordExplanation: Int?,
        @StringRes passwordWarning: Int?, ok: ((String) -> Unit)?, cancel: (() -> Unit)?
    ) {

        val promptsView = LayoutInflater.from(context).inflate(R.layout.passwordprompt, null)
        val alertDialogBuilder = MaterialAlertDialogBuilder(context, app.aaps.core.ui.R.style.DialogTheme)
        alertDialogBuilder.setView(promptsView)
        passwordExplanation?.let { alertDialogBuilder.setMessage(it) }

        passwordWarning?.let {
            val extraWarning: TextView = promptsView.findViewById<TextView>(R.id.password_prompt_extra_message)
            extraWarning.text = context.getString(it)
            extraWarning.visibility = View.VISIBLE
        }

        val userInput = promptsView.findViewById<View>(R.id.password_prompt_pass) as EditText
        val userInput2 = promptsView.findViewById<View>(R.id.password_prompt_pass_confirm) as EditText

        userInput2.visibility = View.GONE

        userInput.setAutofillHints(View.AUTOFILL_HINT_PASSWORD, "aaps_${preference.key}")
        userInput.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES

        fun validatePassword() {
            val enteredPassword = userInput.text.toString()
            ok?.invoke(enteredPassword)
        }

        alertDialogBuilder
            .setCancelable(false)
            .setCustomTitle(app.aaps.core.ui.dialogs.AlertDialogHelper.buildCustomTitle(context, context.getString(labelId), R.drawable.ic_header_key))
            .setPositiveButton(context.getString(app.aaps.core.ui.R.string.ok)) { _, _ -> validatePassword() }
            .setNegativeButton(
                context.getString(app.aaps.core.ui.R.string.cancel)
            ) { dialog, _ ->
                cancel?.invoke()
                dialog.cancel()
            }

        val alert = alertDialogBuilder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
        }

        userInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validatePassword()
                alert.dismiss()
                true
            } else {
                false
            }
        }
    }

}
