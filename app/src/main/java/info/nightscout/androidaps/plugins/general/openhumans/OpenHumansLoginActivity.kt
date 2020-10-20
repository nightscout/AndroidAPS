package info.nightscout.androidaps.plugins.general.openhumans

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class OpenHumansLoginActivity : NoSplashAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_humans_login)
        val button = findViewById<Button>(R.id.button)
        val checkbox = findViewById<CheckBox>(R.id.checkbox)

        button.setOnClickListener {
            if (checkbox.isChecked) {
                CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(OpenHumansUploader.AUTH_URL))
            } else {
                Toast.makeText(this, R.string.you_need_to_accept_the_of_use_first, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val code = intent.data?.getQueryParameter("code")
        if (supportFragmentManager.fragments.size == 0 && code != null) {
            ExchangeAuthTokenDialog(code).show(supportFragmentManager, "ExchangeAuthTokenDialog")
        }
    }

    class ExchangeAuthTokenDialog : DaggerDialogFragment() {

        @Inject
        lateinit var openHumansUploader: OpenHumansUploader

        private var disposable: Disposable? = null

        init {
            isCancelable = false
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireActivity())
                .setTitle(R.string.completing_login)
                .setMessage(R.string.please_wait)
                .create()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            disposable = openHumansUploader.login(arguments?.getString("authToken")!!).subscribeOn(Schedulers.io()).subscribe({
                dismiss()
                SetupDoneDialog().show(parentFragmentManager, "SetupDoneDialog")
            }, {
                dismiss()
                ErrorDialog(it.message).show(parentFragmentManager, "ErrorDialog")
            })
        }

        override fun onDestroy() {
            disposable?.dispose()
            super.onDestroy()
        }

        companion object {

            operator fun invoke(authToken: String): ExchangeAuthTokenDialog {
                val dialog = ExchangeAuthTokenDialog()
                val args = Bundle()
                args.putString("authToken", authToken)
                dialog.arguments = args
                return dialog
            }

        }
    }

    class ErrorDialog : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val message = arguments?.getString("message")
            val shownMessage = if (message == null) getString(R.string.there_was_an_error)
            else "${getString(R.string.there_was_an_error)}\n\n$message"
            return AlertDialog.Builder(requireActivity())
                .setTitle(R.string.error)
                .setMessage(shownMessage)
                .setPositiveButton(R.string.close, null)
                .create()
        }

        companion object {

            operator fun invoke(message: String?): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString("message", message)
                dialog.arguments = args
                return dialog
            }
        }
    }

    class SetupDoneDialog : DialogFragment() {

        init {
            isCancelable = false
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(requireActivity())
                .setTitle(R.string.successfully_logged_in)
                .setMessage(R.string.setup_will_continue_in_background)
                .setCancelable(false)
                .setPositiveButton(R.string.close) { _, _ ->
                    requireActivity().run {
                        setResult(FragmentActivity.RESULT_OK)
                        requireActivity().finish()
                    }
                }
                .create()
        }
    }
}