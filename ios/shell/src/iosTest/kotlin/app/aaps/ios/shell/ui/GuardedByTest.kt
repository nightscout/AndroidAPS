package app.aaps.ios.shell.ui

import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.HierarchicalProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That a protected action really is protected on iOS.
 *
 * This guard was `{ _, action -> action() }` - it ignored the protection entirely and ran everything,
 * left over from when iOS had no `ProtectionCheck`. `AppNavGraph` puts bolus entry, the quick wizard
 * and the settings screens behind it, so all three were reachable with no prompt.
 *
 * The reason it survived unnoticed is the reason these tests exist: with protection set to NONE - the
 * default, and how most devices are configured - the correct code and the broken code behave
 * identically. Only a device with protection switched on can tell them apart, and by then it is a
 * medical app letting anyone bolus.
 */
class GuardedByTest {

    /** Answers with whatever it was told to, and records what it was asked. */
    private class FakeProtectionCheck(private val answer: ProtectionResult) : ProtectionCheck {

        var asked: ProtectionCheck.Protection? = null
        var timesAsked = 0

        override fun requestProtection(protection: ProtectionCheck.Protection, onResult: (ProtectionResult) -> Unit) {
            asked = protection
            timesAsked++
            onResult(answer)
        }

        override fun requestAuthorization(minimumLevel: ProtectionCheck.Protection, onResult: (AuthorizationResult) -> Unit) =
            onResult(AuthorizationResult(null, answer))

        override fun isLocked(protection: ProtectionCheck.Protection): Boolean = false
        override fun resetAuthorization() {}
        override val pendingRequest: StateFlow<ProtectionRequest?> = MutableStateFlow(null)
        override val pendingAuthRequest: StateFlow<HierarchicalProtectionRequest?> = MutableStateFlow(null)
        override fun completeRequest(requestId: Long, result: ProtectionResult) {}
        override fun completeAuthRequest(requestId: Long, result: AuthorizationResult) {}
    }

    @Test
    fun `a granted action runs`() {
        var ran = false
        val check = FakeProtectionCheck(ProtectionResult.GRANTED)

        guardedBy(check)(ProtectionCheck.Protection.BOLUS) { ran = true }

        assertTrue(ran)
    }

    /** The regression: refusing must actually stop the action, not merely log. */
    @Test
    fun `a denied action does not run`() {
        var ran = false
        val check = FakeProtectionCheck(ProtectionResult.DENIED)

        guardedBy(check)(ProtectionCheck.Protection.BOLUS) { ran = true }

        assertFalse(ran, "a denied bolus must not be delivered")
    }

    /** Backing out of the prompt is not consent. */
    @Test
    fun `a cancelled action does not run`() {
        var ran = false
        val check = FakeProtectionCheck(ProtectionResult.CANCELLED)

        guardedBy(check)(ProtectionCheck.Protection.PREFERENCES) { ran = true }

        assertFalse(ran)
    }

    /**
     * The level asked for must be the level requested. Asking for a lower one would satisfy the
     * check with a weaker credential than the action calls for.
     */
    @Test
    fun `the protection asked for is the one passed in`() {
        val check = FakeProtectionCheck(ProtectionResult.GRANTED)

        guardedBy(check)(ProtectionCheck.Protection.PREFERENCES) {}

        assertEquals(ProtectionCheck.Protection.PREFERENCES, check.asked)
    }

    /** The check is consulted every time, so a granted action cannot leave the door open. */
    @Test
    fun `every call asks again`() {
        val check = FakeProtectionCheck(ProtectionResult.GRANTED)
        val guard = guardedBy(check)

        guard(ProtectionCheck.Protection.BOLUS) {}
        guard(ProtectionCheck.Protection.BOLUS) {}

        assertEquals(2, check.timesAsked)
    }
}
