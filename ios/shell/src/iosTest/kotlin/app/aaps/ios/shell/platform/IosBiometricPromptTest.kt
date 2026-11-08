package app.aaps.ios.shell.platform

import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorAuthenticationFailed
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorBiometryNotAvailable
import platform.LocalAuthentication.LAErrorBiometryNotEnrolled
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How each answer from Face ID or Touch ID is read.
 *
 * These pin the one decision in the prompt: which codes mean "denied", which mean "cancelled", and
 * that everything else asks for a password. The last part matters most. An unknown code that was
 * read as cancelled would leave the person at an empty screen, and one read as granted would open
 * the app without a check.
 */
class IosBiometricPromptTest {

    @Test
    fun `a face that is not recognised is denied`() {
        assertEquals(BiometricOutcome.DENIED, biometricOutcomeFor(LAErrorAuthenticationFailed))
    }

    @Test
    fun `the cancel button is cancelled`() {
        assertEquals(BiometricOutcome.CANCELLED, biometricOutcomeFor(LAErrorUserCancel))
    }

    @Test
    fun `the enter password button falls back`() {
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorUserFallback))
    }

    @Test
    fun `a phone without usable biometry falls back`() {
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorBiometryNotAvailable))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorBiometryNotEnrolled))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorBiometryLockout))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorPasscodeNotSet))
    }

    @Test
    fun `a prompt taken down by the system falls back`() {
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorSystemCancel))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(LAErrorAppCancel))
    }

    /** A code this version of iOS does not have yet must not be read as granted or as cancelled. */
    @Test
    fun `an unknown code falls back`() {
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(-9999L))
        assertEquals(BiometricOutcome.FALLBACK, biometricOutcomeFor(0L))
    }
}
