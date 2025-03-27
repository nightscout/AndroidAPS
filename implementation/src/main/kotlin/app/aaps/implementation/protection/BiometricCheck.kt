package app.aaps.implementation.protection

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.ERROR_CANCELED
import androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT
import androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS
import androidx.biometric.BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt.ERROR_NO_SPACE
import androidx.biometric.BiometricPrompt.ERROR_TIMEOUT
import androidx.biometric.BiometricPrompt.ERROR_UNABLE_TO_PROCESS
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.biometric.BiometricPrompt.ERROR_VENDOR
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
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
                        ToastUtils.errorToast(activity.baseContext, errString.toString())
                        // fallback to master password
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, StringKey.ProtectionMasterPassword, { ok?.run() }, { cancel?.run() }, { fail?.run() })
                        }
                    }

                    ERROR_NEGATIVE_BUTTON      ->
                        cancel?.run()

                    ERROR_NO_DEVICE_CREDENTIAL -> {
                        ToastUtils.errorToast(activity.baseContext, errString.toString())
                        // no pin set
                        // fallback to master password
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, StringKey.ProtectionMasterPassword, { ok?.run() }, { cancel?.run() }, { fail?.run() })
                        }
                    }

                    ERROR_NO_SPACE,
                    ERROR_HW_UNAVAILABLE,
                    ERROR_HW_NOT_PRESENT,
                    ERROR_NO_BIOMETRICS        ->
                        runOnUiThread {
                            passwordCheck.queryPassword(activity, R.string.master_password, StringKey.ProtectionMasterPassword, { ok?.run() }, { cancel?.run() }, { fail?.run() })
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
            .setConfirmationRequired(false)
//            .setDeviceCredentialAllowed(true) // setDeviceCredentialAllowed creates new activity when PIN is requested, activity.fragmentManager crash afterwards
            .build()

        runOnUiThread {
            biometricPrompt.authenticate(promptInfo)
        }
    }

}