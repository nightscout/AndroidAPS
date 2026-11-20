package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The part of the biometric prompt that is a decision rather than a system call.
 *
 * [LocalAuthenticationEvaluator] itself cannot be tested here - a test binary gets no Face ID - so
 * the mapping lives in [biometricOutcome] and the prompt takes a [BiometricEvaluator], the same
 * split `UrlLauncher` uses.
 *
 * The rule these protect: only a plain user cancel leaves the screen as it was. Everything else
 * falls back to the password, so no error can leave the owner shut out of their own app. That was
 * the real damage in nightscout/AndroidAPS#5520.
 */
class IosBiometricPromptTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    @Test
    fun `a matching scan succeeds`() {
        assertEquals(BiometricOutcome.SUCCESS, biometricOutcome(success = true, errorCode = null))
    }

    @Test
    fun `the user dismissing the sheet is a cancel`() {
        // LAErrorUserCancel. Android's ERROR_USER_CANCELED, the one case that is not a fallback.
        assertEquals(BiometricOutcome.CANCEL, biometricOutcome(success = false, errorCode = -2L))
    }

    @Test
    fun `the fallback button asks for the password`() {
        // LAErrorUserFallback, the button named by localizedFallbackTitle - Android's negative button.
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = -3L))
    }

    @Test
    fun `a system or app cancel asks for the password`() {
        // LAErrorSystemCancel and LAErrorAppCancel. Android's ERROR_CANCELED, which falls back while
        // the screen is on; the iOS gate asks again by itself on the next return to the foreground.
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = -4L))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = -9L))
    }

    @Test
    fun `a face that does not match asks for the password`() {
        // LAErrorAuthenticationFailed, after the system stopped offering retries.
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = -1L))
    }

    @Test
    fun `a phone that cannot do biometrics asks for the password`() {
        // Not available, not enrolled, locked out, no passcode. None of these may shut the owner out.
        for (code in listOf(-5L, -6L, -7L, -8L))
            assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = code), "code $code")
    }

    @Test
    fun `an unknown error asks for the password rather than refusing`() {
        // The case that matters most: a code from some later iOS that nobody here has seen.
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = -99999L))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcome(success = false, errorCode = null))
    }

    @Test
    fun `the prompt reports the reason and the fallback title it was given`() {
        var seenReason: String? = null
        var seenFallback: String? = null
        val prompt = IosBiometricPrompt(
            { reason, fallbackTitle, reply ->
                seenReason = reason
                seenFallback = fallbackTitle
                reply(true, null)
            },
            SilentLogger
        )

        prompt.show("Authentication required", "Use PIN/Password", {}, {}, {})

        assertEquals("Authentication required", seenReason)
        assertEquals("Use PIN/Password", seenFallback)
    }
}
