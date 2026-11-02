package app.aaps.implementation.protection

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.runOnUiThread

object BiometricCheck {

    /**
     * Biometric prompt without internal master password fallback.
     * All errors and the negative button trigger [onFallback], letting the caller
     * (e.g. ProtectionHost) show the unified auth dialog instead.
     */
    fun biometricPromptSimple(activity: FragmentActivity, title: String, rxBus: RxBus, onSuccess: Runnable?, onFallback: Runnable?, onCancel: Runnable?) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    ERROR_NEGATIVE_BUTTON -> onFallback?.run() // "Use PIN/Password" button
                    ERROR_USER_CANCELED   -> onCancel?.run()

                    else                  -> {
                        rxBus.send(EventShowSnackbar(errString.toString(), EventShowSnackbar.Type.Error))
                        onFallback?.run()
                    }
                }
            }

            override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess?.run()
            }

        })

        val promptInfo = PromptInfo.Builder()
            .setTitle(title)
            .setDescription(activity.getString(R.string.biometric_title))
            .setNegativeButtonText(activity.getString(R.string.use_pin_password))
            .setConfirmationRequired(false)
            .build()

        runOnUiThread {
            // While the activity is stopped, authenticate() only writes a log line and calls nothing back.
            // The caller would wait for ever and the app would stay open without its lock.
            if (activity.supportFragmentManager.isStateSaved) onFallback?.run()
            else biometricPrompt.authenticate(promptInfo)
        }
    }

}