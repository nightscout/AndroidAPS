package app.aaps.pump.aaruy.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.services.AaruyBLE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AaruyReplaceWizardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication() as Context
    private val dateUtil: DateUtil = mock()
    private val insulinManager: InsulinManager = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), mock<ResourceHelper>(), mock<Preferences>(), dateUtil, mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    /** As on a real pump: the rewind shows its own state, then the stopped one. */
    private val ble: AaruyBLE = mock<AaruyBLE>().also {
        whenever(it.replaceDevice()).thenAnswer {
            pump.pumpState = AaruyPumpState.PUSHBACK_RUNNING
            pump.pumpState = AaruyPumpState.STOP_RUNNING
            Unit
        }
    }

    private fun show(connected: Boolean): AaruyReplaceWizardViewModel {
        whenever(dateUtil.now()).thenAnswer { System.currentTimeMillis() }
        pump.connectionState = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
        val viewModel = AaruyReplaceWizardViewModel(
            mock(), ble, pump, mock<PumpSync>(), dateUtil, mock<ResourceHelper>(), mock<RxBus>(), mock<Preferences>(),
            mock<ProfileFunction>(), insulinManager, mock<PersistenceLayer>()
        )
        compose.setContent { MaterialTheme { AaruyReplaceWizardScreen(viewModel, "Change reservoir and battery", setToolbarConfig = {}, onFinish = {}) } }
        return viewModel
    }

    @Test
    fun theFirstStepAsksToDisconnectAndRewind() {
        show(connected = true)
        compose.onNodeWithText(context.getString(R.string.actions_reservoir_rewind_prompt)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.aaruy_rewind)).assertIsEnabled()
    }

    /** Nothing may be sent to a pump that is not connected, and the screen says why. */
    @Test
    fun withoutAConnectionNothingCanBeSent() {
        show(connected = false)
        compose.onNodeWithText(context.getString(R.string.aaruy_rewind)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.bluetooth_disconnect)).assertIsDisplayed()
    }

    @Test
    fun withTwoInsulinsTheInsulinStepOffersBoth() {
        whenever(insulinManager.insulins).thenReturn(arrayListOf(ICfg("Lyumjev", 45, 5.0, 1.0), ICfg("Fiasp", 55, 5.0, 1.0)))
        val viewModel = show(connected = true)
        // The rewind ends when the pump stops. A real pump says so in less than a second.
        viewModel.rewind()
        compose.waitUntil(10_000) { viewModel.uiState.value.step == AaruyReplaceStep.ASSEMBLE }
        // Seen on a real pump: the piston still moves back after the pump has reported the end.
        compose.onNodeWithText(context.getString(R.string.aaruy_motor_still_running)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(CoreUiR.string.next)).assertIsEnabled().performClick()
        compose.waitUntil(5_000) { viewModel.uiState.value.step == AaruyReplaceStep.SELECT_INSULIN }
        compose.onAllNodesWithText("Fiasp", substring = true).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Lyumjev", substring = true).onFirst().assertIsDisplayed()
    }
}
