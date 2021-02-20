package info.nightscout.androidaps.utils.protection

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.*
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.extensions.runOnUiThread
import java.util.concurrent.Executors

object BiometricCheck {
    fun biometricPrompt(activity: FragmentActivity, title: Int, ok: Runnable?, cancel: Runnable? = null, fail: Runnable? = null, passwordCheck: PasswordCheck) {
        val executor = Executors.newSingleThreadExecutor()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    ERROR_UNABLE_TO_PROCESS,
                    ERROR_TIMEOUT,
                    ERROR_CANCELED,
                    ERROR_LOCKOUT,
                    ERROR_VENDOR,
                    ERROR_LOCKOUT_PERMANENT,
                    ERROR_USER_CANCELED        -> {
                        ToastUtils.showToastInUiThread(activity.baseContext, errString.toString())
                        // fallback to master password
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { ok?.run() }, { cancel?.run() }, { fail?.run() })
                        }
                    }

                    ERROR_NEGATIVE_BUTTON      ->
                        cancel?.run()

                    ERROR_NO_DEVICE_CREDENTIAL -> {
                        ToastUtils.showToastInUiThread(activity.baseContext, errString.toString())
                        // no pin set
                        // fallback to master password
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { ok?.run() }, { cancel?.run() }, { fail?.run() })
                        }
                    }

                    ERROR_NO_SPACE,
                    ERROR_HW_UNAVAILABLE,
                    ERROR_HW_NOT_PRESENT,
                    ERROR_NO_BIOMETRICS        ->
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, R.string.key_master_password, { ok?.run() }, { cancel?.run() }, { fail?.run() })
                        }
                }
            }

            override fun onAuthenticationSucceeded(result: AuthenticationResult) {
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

        val promptInfo = PromptInfo.Builder()
            .setTitle(activity.getString(title))
            .setDescription(activity.getString(R.string.biometric_title))
            .setNegativeButtonText(activity.getString(R.string.cancel)) // not possible with setDeviceCredentialAllowed
//            .setDeviceCredentialAllowed(true) // setDeviceCredentialAllowed creates new activity when PIN is requested, activity.fragmentManager crash afterwards
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

}