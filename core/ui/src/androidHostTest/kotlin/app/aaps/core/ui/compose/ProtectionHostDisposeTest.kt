package app.aaps.core.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.aaps.core.interfaces.protection.AuthMethod
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.HierarchicalProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.protection.ProtectionType
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A request left open when the host goes away (the activity is destroyed) is cancelled, so its
 * callback - which holds the old screen - can never run later against a dead NavController.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProtectionHostDisposeTest {

    @get:Rule
    val compose = createComposeRule()

    private val authRequest = HierarchicalProtectionRequest(
        id = 7L,
        minimumLevel = ProtectionCheck.Protection.PREFERENCES,
        availableMethods = listOf(AuthMethod(ProtectionCheck.Protection.MASTER, ProtectionType.MASTER_PASSWORD, "hash", false)),
        hasBiometric = false,
        biometricGrantsLevel = null,
        onResult = {}
    )

    private fun protectionCheck(auth: HierarchicalProtectionRequest?, single: ProtectionRequest?): ProtectionCheck = mock<ProtectionCheck>().also {
        whenever(it.pendingAuthRequest).thenReturn(MutableStateFlow(auth))
        whenever(it.pendingRequest).thenReturn(MutableStateFlow(single))
    }

    @Test
    fun pendingAuthRequestIsCancelledWhenTheHostLeaves() {
        val check = protectionCheck(authRequest, null)
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) ProtectionHost(protectionCheck = check, preferences = mock<Preferences>(), checkPassword = { _, _ -> false })
        }
        compose.waitForIdle()
        verify(check, never()).completeAuthRequest(any(), any())

        shown = false
        compose.waitForIdle()

        verify(check).completeAuthRequest(7L, AuthorizationResult(null, ProtectionResult.CANCELLED))
    }

    @Test
    fun noPendingRequestNothingToCancel() {
        val check = protectionCheck(null, null)
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) ProtectionHost(protectionCheck = check, preferences = mock<Preferences>(), checkPassword = { _, _ -> false })
        }
        shown = false
        compose.waitForIdle()

        verify(check, never()).completeAuthRequest(any(), any())
        verify(check, never()).completeRequest(any(), any())
    }
}
