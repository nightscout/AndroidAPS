package app.aaps.pump.medtrum.compose

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.medtrum.MedtrumPlugin
import app.aaps.pump.medtrum.MedtrumPump
import app.aaps.pump.medtrum.R
import app.aaps.pump.medtrum.ble.MedtrumBleTransport
import app.aaps.pump.medtrum.code.ConnectionState
import app.aaps.pump.medtrum.comm.enums.MedtrumPumpState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * Unit test for [MedtrumPatchViewModel]'s synchronous pure state setters and its default state.
 *
 * The VM owns its OWN CoroutineScope (Dispatchers.Default + SupervisorJob). Its init{} launches two
 * collectors on `medtrumPump.connectionStateFlow` / `pumpStateFlow`; both must be stubbed or the
 * collector coroutine NPEs on a null flow. Those collectors are no-ops while `patchStep` is null
 * (which it is at construction and throughout these tests), so they never race the assertions below.
 * StandardTestDispatcher is installed as Main so any viewModelScope work stays deferred.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MedtrumPatchViewModelTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var insulinManager: InsulinManager
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var persistenceLayer: PersistenceLayer

    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var rxBus: RxBus

    private val medtrumPlugin: MedtrumPlugin = mock()
    private val medtrumPump: MedtrumPump = mock()
    private val bleTransport: MedtrumBleTransport = mock()
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var sut: MedtrumPatchViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(mainDispatcher)

        // init{} launches two collectors that read these non-null StateFlow getters.
        whenever(medtrumPump.connectionStateFlow).thenReturn(MutableStateFlow(ConnectionState.DISCONNECTED))
        whenever(medtrumPump.pumpStateFlow).thenReturn(MutableStateFlow(MedtrumPumpState.NONE))
        whenever(rh.gs(any<TextRef>())).thenReturn("text")

        sut = MedtrumPatchViewModel(
            aapsLogger, medtrumPlugin, commandQueue, medtrumPump, insulinManager,
            profileFunction, profileRepository, preferences, persistenceLayer, bleTransport, rh, rxBus
        )
    }

    /** The message of the one dialog the wizard sent. */
    private fun dialogMessage(): String {
        val sent = argumentCaptor<Event>()
        verify(rxBus, timeout(2000)).send(sent.capture())
        return (sent.firstValue as EventShowDialog.Ok).message
    }

    // A refused switch was only logged: the gate did nothing and did not say why.
    @Test
    fun aProfileTheGateCannotActivate_saysWhyAndStaysOnTheGate() = runBlocking<Unit> {
        val insulin = ICfg("Fiasp", peak = 55, dia = 7.0, concentration = 1.0)
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(mock<ProfileStore>()))
        whenever(insulinManager.insulins).thenReturn(arrayListOf(insulin))
        // createProfileSwitch is not stubbed, so it refuses (null).
        whenever(profileFunction.profileSwitchRefusal(any(), eq("Night"), any(), any(), any(), eq(insulin))).thenReturn("The pump cannot take it")
        sut.selectProfile("Night")

        sut.activateSelectedProfile()

        assertThat(dialogMessage()).isEqualTo("The pump cannot take it")
        assertThat(sut.patchStep.value).isNull()
    }

    // The patch is running by then, so the wizard goes on, but the user must know the old insulin is still in use.
    @Test
    fun anInsulinThatIsNotSwitched_saysSo() = runBlocking<Unit> {
        val chosen = ICfg("Lyumjev", peak = 45, dia = 7.0, concentration = 1.0)
        whenever(insulinManager.insulins).thenReturn(arrayListOf(ICfg("Fiasp", peak = 55, dia = 7.0, concentration = 1.0), chosen))
        whenever(profileFunction.insulinSwitchRefusal(chosen)).thenReturn(null)
        // A mocked suspend Boolean answers null, so the refusal is stubbed.
        whenever(profileFunction.createProfileSwitchWithNewInsulin(chosen, Sources.Medtrum)).thenReturn(false)
        sut.selectInsulin(chosen)

        sut.executeInsulinProfileSwitch()
        mainDispatcher.scheduler.advanceUntilIdle()

        assertThat(dialogMessage()).isEqualTo("text")
        verifyBlocking(profileFunction) { createProfileSwitchWithNewInsulin(chosen, Sources.Medtrum) }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun defaultState_isPrepareWizardStart() {
        assertThat(sut.patchStep.value).isNull()
        assertThat(sut.setupStep.value).isEqualTo(MedtrumPatchViewModel.SetupStep.INITIAL)
        assertThat(sut.title.value).isEqualTo(R.string.step_prepare_patch)
        assertThat(sut.wizardPage.value).isEqualTo(WizardPage.PREPARE)
        assertThat(sut.totalSteps.value).isEqualTo(5)
        assertThat(sut.currentStepIndex.value).isEqualTo(0)
        assertThat(sut.canGoBack.value).isTrue()
        assertThat(sut.selectedInsulin.value).isNull()
        assertThat(sut.selectedProfile.value).isNull()
        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.NONE)
        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.NONE)
    }

    @Test
    fun selectInsulin_updatesSelectedInsulin() {
        val iCfg: ICfg = mock()

        sut.selectInsulin(iCfg)

        assertThat(sut.selectedInsulin.value).isSameInstanceAs(iCfg)
    }

    @Test
    fun selectProfile_updatesSelectedProfile() {
        sut.selectProfile("Night")

        assertThat(sut.selectedProfile.value).isEqualTo("Night")
    }

    @Test
    fun updateSiteLocation_updatesSiteLocation() {
        sut.updateSiteLocation(TE.Location.SIDE_LEFT_UPPER_ARM)

        assertThat(sut.siteLocation.value).isEqualTo(TE.Location.SIDE_LEFT_UPPER_ARM)
    }

    @Test
    fun updateSiteArrow_updatesSiteArrow() {
        sut.updateSiteArrow(TE.Arrow.UP)

        assertThat(sut.siteArrow.value).isEqualTo(TE.Arrow.UP)
    }

    @Test
    fun updateSetupStep_updatesSetupStep() {
        sut.updateSetupStep(MedtrumPatchViewModel.SetupStep.PRIMED)

        assertThat(sut.setupStep.value).isEqualTo(MedtrumPatchViewModel.SetupStep.PRIMED)
    }
}
