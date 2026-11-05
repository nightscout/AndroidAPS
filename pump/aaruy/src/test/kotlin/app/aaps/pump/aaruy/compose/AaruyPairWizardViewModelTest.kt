package app.aaps.pump.aaruy.compose

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.SingleProfile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.services.AaruyBLE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AaruyPairWizardViewModelTest {

    private val ble: AaruyBLE = mock()
    private val rxBus: RxBus = mock()
    private val profileFunction: ProfileFunction = mock()
    private val profileRepository: ProfileRepository = mock()
    private val insulinManager: InsulinManager = mock()
    private val rh: ResourceHelper = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), rh, mock<Preferences>(), mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(ble.scannedDevices).thenReturn(MutableStateFlow(emptyList()))
        val profiles = listOf("home", "work").map { name -> mock<SingleProfile> { on { this.name } doReturn name } }
        whenever(profileRepository.profiles).thenReturn(MutableStateFlow(profiles))
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(mock<ProfileStore>()))
        whenever(insulinManager.insulins).thenReturn(arrayListOf(ICfg("Lyumjev", 45, 5.0, 1.0)))
        whenever(rh.gs(any<Int>())).thenReturn("text")
        runBlocking { whenever(profileFunction.getOriginalProfileName()).thenReturn("work") }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AaruyPairWizardViewModel(ble, pump, mock(), rh, rxBus, mock(), profileFunction, profileRepository, insulinManager)

    @Test
    fun withAProfileRunningThePairingStartsWithTheScan() {
        runBlocking { whenever(profileFunction.getRequestedProfile()).thenReturn(mock<PS>()) }
        val viewModel = viewModel()
        assertEquals(AaruyPairStep.BLE_SCAN, viewModel.step.value)
        assertEquals(listOf(AaruyPairStep.BLE_SCAN, AaruyPairStep.PRESS_BUTTON, AaruyPairStep.COMPLETE), viewModel.steps.value)
    }

    /** A new pump needs a basal profile, as on the other pumps. */
    @Test
    fun withNoProfileThePairingAsksForOneAndOffersTheRunningName() {
        val viewModel = viewModel()
        assertEquals(AaruyPairStep.PROFILE_GATE, viewModel.step.value)
        assertEquals(AaruyPairStep.entries, viewModel.steps.value)
        assertEquals(listOf("home", "work"), viewModel.availableProfiles.value)
        assertEquals("work", viewModel.selectedProfile.value)
    }

    @Test
    fun anActivatedProfileGoesOnToTheScan() {
        runBlocking {
            whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock<PS>())
        }
        val viewModel = viewModel()
        viewModel.selectProfile("home")
        viewModel.activateSelectedProfile()
        assertEquals(AaruyPairStep.BLE_SCAN, viewModel.step.value)
        runBlocking { verify(profileFunction).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any()) }
    }

    /** The shared helper says why. The wizard must not go on to pair a pump with no profile. */
    @Test
    fun aRefusedProfileKeepsTheStep() {
        val viewModel = viewModel()
        viewModel.activateSelectedProfile()
        assertEquals(AaruyPairStep.PROFILE_GATE, viewModel.step.value)
        runBlocking { verify(profileFunction).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any()) }
    }

    /** Leaving while the pump waits for its button: no half paired pump stays behind. */
    @Test
    fun cancelWhileWaitingForTheButtonUnpairs() {
        runBlocking { whenever(profileFunction.getRequestedProfile()).thenReturn(mock<PS>()) }
        val viewModel = viewModel()
        viewModel.selectDevice(mock())
        assertEquals(AaruyPairStep.PRESS_BUTTON, viewModel.step.value)
        viewModel.cancel()
        verify(ble).unbindDevice()
    }

    @Test
    fun cancelDuringTheScanUnpairsNothing() {
        runBlocking { whenever(profileFunction.getRequestedProfile()).thenReturn(mock<PS>()) }
        viewModel().cancel()
        verify(ble, never()).unbindDevice()
    }
}
