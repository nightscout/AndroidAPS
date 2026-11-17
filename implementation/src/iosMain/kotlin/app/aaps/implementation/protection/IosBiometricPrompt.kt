package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * What the user did at the Face ID / Touch ID sheet, in the three answers `ProtectionHost` knows.
 *
 * The names are the host's, not Apple's: the point of this type is to say what should happen next,
 * so the mapping from a system error code is decided once, in [biometricOutcome], and tested.
 */
enum class BiometricOutcome {

    /** The scan matched. Grant the level biometrics cover. */
    SUCCESS,

    /** Ask for the password or PIN instead. Also the answer to every error, so nobody is locked out. */
    FALLBACK,

    /** The user dismissed the sheet without choosing the password. Leave the screen as it was. */
    CANCEL
}

/**
 * The LocalAuthentication error codes this maps. Written out rather than taken from the platform
 * package, so the mapping below can be read against Apple's documented values and tested without a
 * device - a test cannot build an `NSError` from LocalAuthentication either.
 */
private object LaError {

    const val AUTHENTICATION_FAILED = -1L
    const val USER_CANCEL = -2L
    const val USER_FALLBACK = -3L
    const val SYSTEM_CANCEL = -4L
    const val APP_CANCEL = -9L
}

/**
 * Turns the result of one `evaluatePolicy` call into what the host should do.
 *
 * Mirrors `BiometricCheck.biometricPromptSimple` on Android one case at a time:
 *
 * | iOS                              | Android                 |
 * |----------------------------------|-------------------------|
 * | [LaError.USER_FALLBACK]          | `ERROR_NEGATIVE_BUTTON` |
 * | [LaError.USER_CANCEL]            | `ERROR_USER_CANCELED`   |
 * | [LaError.SYSTEM_CANCEL], app cancel | `ERROR_CANCELED`     |
 * | anything else                    | the `else` branch       |
 *
 * Everything that is not a plain user cancel falls back. That is the rule that matters: a phone with
 * no enrolled face, a locked-out sensor or an error nobody anticipated must still let its owner in
 * with the master password. Returning [BiometricOutcome.CANCEL] there would leave the app sitting on
 * its lock screen with no way forward, which is the bug this whole change is about.
 *
 * Android re-arms the prompt for `ERROR_CANCELED` when the screen is off and falls back when it is
 * not. There is no re-arm here, because the iOS gate already asks again on
 * `UIApplicationWillEnterForegroundNotification`; falling back matches what Android does in the
 * case the user can actually see.
 */
fun biometricOutcome(success: Boolean, errorCode: Long?): BiometricOutcome =
    when {
        success                             -> BiometricOutcome.SUCCESS
        errorCode == LaError.USER_CANCEL    -> BiometricOutcome.CANCEL
        errorCode == LaError.SYSTEM_CANCEL  -> BiometricOutcome.FALLBACK
        errorCode == LaError.APP_CANCEL     -> BiometricOutcome.FALLBACK
        errorCode == LaError.USER_FALLBACK  -> BiometricOutcome.FALLBACK
        // A scan that did not match, after the system stopped retrying.
        errorCode == LaError.AUTHENTICATION_FAILED -> BiometricOutcome.FALLBACK
        // No biometry, none enrolled, sensor locked out, no passcode, or something new in a later iOS.
        else                                -> BiometricOutcome.FALLBACK
    }

/**
 * Shows the system biometric sheet. Split out so [IosBiometricPrompt] can be tested: `LAContext`
 * cannot be driven from a test, the same reason `UrlLauncher` exists.
 */
fun interface BiometricEvaluator {

    /**
     * Asks for a scan and reports the raw system answer.
     *
     * @param reason shown by the system in the sheet.
     * @param fallbackTitle the button that means "let me type the password" - Android's negative button.
     * @param reply success, and the `LAError` code when it failed.
     */
    fun evaluate(reason: String, fallbackTitle: String, reply: (success: Boolean, errorCode: Long?) -> Unit)
}

/**
 * The real one.
 *
 * Deliberately `DeviceOwnerAuthenticationWithBiometrics` and not `DeviceOwnerAuthentication`: the
 * second accepts the phone's own passcode, which would make the screen lock stand in for the AAPS
 * master password. Android keeps those apart - a `BiometricPrompt` with an explicit negative button,
 * never the device credential - and this is the line that keeps iOS the same.
 */
class LocalAuthenticationEvaluator : BiometricEvaluator {

    override fun evaluate(reason: String, fallbackTitle: String, reply: (Boolean, Long?) -> Unit) {
        val context = LAContext()
        // The button Apple draws when biometrics fail or are refused. Named like Android's negative
        // button, and it answers with USER_FALLBACK rather than offering the device passcode.
        context.localizedFallbackTitle = fallbackTitle
        context.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, reason) { success, error ->
            reply(success, error?.code)
        }
    }
}

/**
 * The iOS half of `ProtectionHost`'s `showBiometricSimple`, the counterpart of `BiometricCheck` on
 * Android.
 *
 * Until this existed the host used its own default, which answers "fall back" straight away, so
 * nothing on iOS ever asked for Face ID and application protection could not be satisfied
 * (nightscout/AndroidAPS#5520).
 */
class IosBiometricPrompt(
    private val evaluator: BiometricEvaluator,
    private val aapsLogger: AAPSLogger
) {

    /**
     * Asks for a scan and reports one of the three answers.
     *
     * The callbacks are handed back on the main queue. `evaluatePolicy` answers on a queue of its
     * own choosing, and all three callers end in Compose state writes and a recomposition.
     */
    fun show(
        title: String,
        fallbackTitle: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
        onCancel: () -> Unit
    ) {
        evaluator.evaluate(title, fallbackTitle) { success, errorCode ->
            val outcome = biometricOutcome(success, errorCode)
            if (!success) aapsLogger.debug(LTag.CORE, "Biometric prompt failed with $errorCode, answering $outcome")
            dispatch_async(dispatch_get_main_queue()) {
                when (outcome) {
                    BiometricOutcome.SUCCESS  -> onSuccess()
                    BiometricOutcome.FALLBACK -> onFallback()
                    BiometricOutcome.CANCEL   -> onCancel()
                }
            }
        }
    }
}
