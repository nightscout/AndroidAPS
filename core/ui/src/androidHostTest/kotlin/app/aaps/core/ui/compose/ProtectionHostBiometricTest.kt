package app.aaps.core.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.aaps.core.interfaces.protection.AuthMethod
import app.aaps.core.interfaces.protection.HierarchicalProtectionRequest
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A biometric prompt started while another activity is on top is dropped by the system with no
 * callback. Nothing would answer the request, and the app would stay open without its lock. So the
 * host must wait until the screen is resumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProtectionHostBiometricTest {

    @get:Rule
    val compose = createComposeRule()

    private val request = HierarchicalProtectionRequest(
        id = 3L,
        minimumLevel = ProtectionCheck.Protection.APPLICATION,
        availableMethods = listOf(
            AuthMethod(ProtectionCheck.Protection.APPLICATION, ProtectionType.BIOMETRIC, "", false),
            AuthMethod(ProtectionCheck.Protection.MASTER, ProtectionType.MASTER_PASSWORD, "hash", false)
        ),
        hasBiometric = true,
        biometricGrantsLevel = ProtectionCheck.Protection.APPLICATION,
        onResult = {}
    )

    private val check: ProtectionCheck = mock<ProtectionCheck>().also {
        whenever(it.pendingAuthRequest).thenReturn(MutableStateFlow(request))
    }

    private class FakeOwner : LifecycleOwner {

        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun promptWaitsUntilTheScreenIsResumed() {
        val owner = FakeOwner().apply { registry.currentState = Lifecycle.State.CREATED }
        var shown = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ProtectionHost(
                    protectionCheck = check,
                    checkPassword = { _, _ -> false },
                    showBiometricSimple = { _, _, _, _ -> shown++ }
                )
            }
        }
        compose.waitForIdle()
        assertThat(shown).isEqualTo(0)

        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()

        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun promptStartsAtOnceWhenTheScreenIsResumed() {
        val owner = FakeOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        var shown = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ProtectionHost(
                    protectionCheck = check,
                    checkPassword = { _, _ -> false },
                    showBiometricSimple = { _, _, _, _ -> shown++ }
                )
            }
        }
        compose.waitForIdle()

        assertThat(shown).isEqualTo(1)
    }
}
