package info.nightscout.androidaps.utils.protection

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.ToastUtils
import java.util.concurrent.Executors

object BiometricCheck {
    fun biometricPrompt(activity: FragmentActivity, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null) {
        val executor = Executors.newSingleThreadExecutor()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) cancel?.run()
                else {
                    // Called when an unrecoverable error has been encountered and the operation is complete.
                    ToastUtils.showToastInUiThread(activity.baseContext, errString.toString())
                    // call ok, because it's not possible to bypass it when biometrics fail
                    ok?.run()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                // Called when a biometric is recognized.
                ok?.run()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called when a biometric is valid but not recognized.
                fail?.run()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(MainApp.gs(R.string.biometric_title))
                .setSubtitle("Set the subtitle to display.")
                .setDescription(MainApp.gs(R.string.biometric_title))
                .setNegativeButtonText(MainApp.gs(R.string.cancel))
                .build()

        biometricPrompt.authenticate(promptInfo)
    }

}