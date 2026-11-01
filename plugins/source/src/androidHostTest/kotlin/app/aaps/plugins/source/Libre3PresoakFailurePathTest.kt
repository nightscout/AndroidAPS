package app.aaps.plugins.source

import android.app.Service
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ServiceCompat
import app.aaps.core.interfaces.source.PromotionRejectReason
import app.aaps.core.interfaces.source.PromotionResult
import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import androidx.test.core.app.ApplicationProvider
import app.aaps.plugins.source.activities.Libre3PresoakAction
import app.aaps.plugins.source.activities.Libre3StatusScreen
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Failure paths of the three `runCatching` sites of the pre-soak screen and service.
 *
 * `cancelStaging` when `shutdown` throws is in [Libre3PresoakPluginTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Libre3PresoakFailurePathTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun installMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        // Clear while the test dispatcher is still installed. The action object captures
        // Dispatchers.Main the first time it is touched.
        Libre3PresoakAction.clear()
        Libre3SensorStore(ApplicationProvider.getApplicationContext(), null).clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `a throw inside a pre-soak action leaves the previous message`() {
        Libre3PresoakAction.clear()
        Libre3PresoakAction.run { "kept" }
        assertThat(Libre3PresoakAction.message.value).isEqualTo("kept")

        Libre3PresoakAction.run { throw IllegalStateException("promote") }

        assertThat(Libre3PresoakAction.message.value).isEqualTo("kept")
    }

    @Test
    fun `onStartCommand stops the service when startForeground throws`() {
        Mockito.mockStatic(ServiceCompat::class.java).use { mocked ->
            mocked.`when`<Unit> {
                ServiceCompat.startForeground(any(), any(), any(), any())
            }.thenThrow(SecurityException("foreground type"))
            val service = Robolectric.buildService(Libre3SessionService::class.java).create().get()

            val result = service.onStartCommand(Intent(), 0, 1)

            assertThat(result).isEqualTo(Service.START_NOT_STICKY)
            assertThat(Shadows.shadowOf(service).isStoppedBySelf).isTrue()
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "h2000dp")
    fun `a refusal before the swap says that nothing was changed`() {
        val shown = confirmPromote { PromotionResult.Rejected(PromotionRejectReason.STAGING_NOT_SETTLED) }

        assertThat(shown).isEqualTo("The pre-soak sensor could not be promoted. Nothing was changed.")
        assertThat(shown).isNotEqualTo(BOUND_FAILED_TEXT)
        assertThat(shown).isNotEqualTo(CHECK_STATE_TEXT)
    }

    @Test
    @Config(sdk = [35], qualifiers = "h2000dp")
    fun `a failed calibration bound says the sensor is promoted and the old entries remain`() {
        val shown = confirmPromote { PromotionResult.OkBoundFailed }

        assertThat(shown).isEqualTo(BOUND_FAILED_TEXT)
        assertThat(shown).isNotEqualTo("The pre-soak sensor could not be promoted. Nothing was changed.")
        assertThat(shown).isNotEqualTo("Done. The pre-soak sensor now feeds the loop.")
    }

    @Test
    @Config(sdk = [35], qualifiers = "h2000dp")
    fun `a follow-up failure with a production identity says to check the sensor`() {
        val shown = confirmPromote { PromotionResult.OkFollowUpFailed(productionIdentityPresent = true) }

        assertThat(shown).isEqualTo(CHECK_STATE_TEXT)
        assertThat(shown).isNotEqualTo("The pre-soak sensor could not be promoted. Nothing was changed.")
        assertThat(shown).isNotEqualTo(NO_IDENTITY_TEXT)
    }

    @Test
    @Config(sdk = [35], qualifiers = "h2000dp")
    fun `a follow-up failure without a production identity does not claim the sensor is in place`() {
        val shown = confirmPromote { PromotionResult.OkFollowUpFailed(productionIdentityPresent = false) }

        assertThat(shown).isEqualTo(NO_IDENTITY_TEXT)
        assertThat(shown).isNotEqualTo(CHECK_STATE_TEXT)
        assertThat(shown).isNotEqualTo("The pre-soak sensor could not be promoted. Nothing was changed.")
    }

    @Test
    @Config(sdk = [35], qualifiers = "h2000dp")
    fun `a throw before any promotion result replaces the previous message with the store`() {
        val store = Libre3SensorStore(ApplicationProvider.getApplicationContext(), null)
        store.clear()
        assertThat(store.saveIdentityAndWait(SCREEN_SENSOR)).isTrue()
        Libre3PresoakAction.clear()
        Libre3PresoakAction.run { "kept" }
        assertThat(Libre3PresoakAction.message.value).isEqualTo("kept")

        compose.setContent {
            MaterialTheme {
                Libre3StatusScreen(
                    onBack = {},
                    onOpenLog = {},
                    onOpenStart = {},
                    onConnect = {},
                    isPluginStopped = { false },
                    stagingStateFlow = MutableStateFlow(StagingState.READY),
                    stagingEvidenceFlow = MutableStateFlow(null),
                    stagingLifecycleFlow = MutableStateFlow(null),
                    stagingCurveFlow = MutableStateFlow(emptyList()),
                    formatGlucose = { it.toString() },
                    formatTime = { it.toString() },
                    formatAge = { it.toString() },
                    onPromote = { throw IllegalStateException("screen") },
                    onCancelStaging = {},
                    onSensorForgotten = {},
                    presoakMessageFlow = Libre3PresoakAction.message,
                    runPresoakAction = { work -> Libre3PresoakAction.run(work) },
                )
            }
        }
        compose.onNodeWithText("Promote this sensor").performClick()
        compose.onNodeWithText("Promote", substring = false).performClick()
        compose.waitForIdle()

        assertThat(Libre3PresoakAction.message.value).isEqualTo(
            "The promotion failed before it returned a result. Production sensor read from the store: MH0SCREEN.",
        )
    }

    private fun confirmPromote(onPromote: suspend () -> PromotionResult): String? {
        Libre3SensorStore(ApplicationProvider.getApplicationContext(), null).clear()
        var shown: String? = null
        compose.setContent {
            MaterialTheme {
                Libre3StatusScreen(
                    onBack = {},
                    onOpenLog = {},
                    onOpenStart = {},
                    onConnect = {},
                    isPluginStopped = { false },
                    stagingStateFlow = MutableStateFlow(StagingState.READY),
                    stagingEvidenceFlow = MutableStateFlow(null),
                    stagingLifecycleFlow = MutableStateFlow(null),
                    stagingCurveFlow = MutableStateFlow(emptyList()),
                    formatGlucose = { it.toString() },
                    formatTime = { it.toString() },
                    formatAge = { it.toString() },
                    onPromote = onPromote,
                    onCancelStaging = {},
                    onSensorForgotten = {},
                    presoakMessageFlow = MutableStateFlow(null),
                    runPresoakAction = { work -> shown = runBlocking { work() } },
                )
            }
        }
        // The status list is taller than a phone window. A tall test window keeps the
        // promote button on screen, because touch injection misses a node that is off screen.
        compose.onNodeWithText("Promote this sensor").performClick()
        compose.onNodeWithText("Promote", substring = false).performClick()
        return shown
    }

    companion object {
        private const val BOUND_FAILED_TEXT =
            "The sensor is promoted, but the old calibration entries could not be ignored. " +
                "Check a fingerstick before you trust the loop, and calibrate again if those old entries still apply."
        private const val CHECK_STATE_TEXT =
            "The sensor looks promoted, but the promotion did not finish cleanly. " +
                "Check the Libre 3 status to see which sensor feeds the loop before you trust it."
        private const val NO_IDENTITY_TEXT =
            "A step after the exchange failed, and the production sensor identity is missing. " +
                "This is not a complete success. Open the Libre 3 status and check which sensor is in use."

        private val SCREEN_SENSOR = Libre3SensorIdentity(
            serialNumber = "MH0SCREEN",
            bleAddress = "AA:BB:CC:DD:EE:09",
            blePin = byteArrayOf(1, 2, 3, 4),
            receiverId = 9,
            generation = 0,
            warmupMinutes = 60,
            wearDurationMinutes = 14 * 24 * 60,
            activatedAtMs = 1_777_000_000_000L,
        )
    }
}
