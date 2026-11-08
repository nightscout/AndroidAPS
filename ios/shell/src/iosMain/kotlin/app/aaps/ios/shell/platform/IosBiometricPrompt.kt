package app.aaps.ios.shell.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * What a Face ID or Touch ID request came to, reduced to the answers the protection host understands.
 *
 * The host has two callers with different needs. The app launch gate wants to know granted, denied,
 * or "ask for the master password instead". The hierarchical dialog also wants to tell a plain
 * cancel apart from a fallback, so both are kept here and each caller folds them as it needs.
 */
enum class BiometricOutcome {

    /** The face or finger was recognised. */
    GRANTED,

    /** Biometry worked but did not recognise the person, after the system's own retries. */
    DENIED,

    /** The person tapped Cancel. */
    CANCELLED,

    /**
     * Biometry could not answer: the "Enter Password" button, no Face ID on this phone, nothing
     * enrolled, locked out after too many tries, or the system took the prompt down. All of these
     * mean the same thing to the caller - ask for a password instead.
     */
    FALLBACK
}

/**
 * Maps an `LAError` code to a [BiometricOutcome].
 *
 * Separate and pure so the simulator tests can pin every branch: the prompt itself needs a person
 * and a face, which no test has. Only two codes are named. Everything else, including codes Apple
 * adds later, falls back to a password, which is the safe direction - the person can still get in,
 * and nothing is granted without a check.
 */
internal fun biometricOutcomeFor(errorCode: Long): BiometricOutcome = when (errorCode) {
    LAErrorAuthenticationFailed -> BiometricOutcome.DENIED
    LAErrorUserCancel           -> BiometricOutcome.CANCELLED
    else                        -> BiometricOutcome.FALLBACK
}

/**
 * The Face ID / Touch ID sheet, the one part of application protection iOS has to supply itself.
 *
 * The counterpart of `BiometricCheck` on Android. The shared `ProtectionHost` draws every password
 * and PIN dialog in Compose, so this is the only prompt the platform owns. Without it the app root
 * used its default, which cancelled at once: a person with biometric protection saw an empty screen
 * and no way past it.
 *
 * Biometrics only, not the device passcode. The passcode would let anyone who knows the phone's
 * code into AAPS, while the master password is a separate secret - the same choice Android makes
 * with `BIOMETRIC_STRONG` and its own password fallback.
 *
 * `NSFaceIDUsageDescription` has to be in the app's Info.plist. Without it iOS terminates the app on
 * the first Face ID request rather than refusing it.
 */
class IosBiometricPrompt {

    /**
     * Shows the system sheet with [reason] as its text and reports the outcome on the main thread.
     *
     * The reason is the title the host already localised, so no extra string is needed. The reply
     * arrives on a private queue; it is moved to the main queue because every caller changes Compose
     * state in response.
     */
    fun authenticate(reason: String, onOutcome: (BiometricOutcome) -> Unit) {
        // A new context per request: a context that has answered once will not ask again.
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, reason) { success, error ->
            val outcome = if (success) BiometricOutcome.GRANTED else biometricOutcomeFor(error?.code ?: 0L)
            dispatch_async(dispatch_get_main_queue()) { onOutcome(outcome) }
        }
    }
}
