package app.aaps.pump.omnipod.dash

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpProfile
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The pump-state answers the command queue and the loop rely on.
 *
 * These are small methods, but each one gates insulin: `isThisProfileSet` deciding wrongly means AAPS
 * believes the pod is running a basal profile it is not, and `baseBasalRate` is what the loop treats as
 * the rate currently being delivered.
 */
class OmnipodDashPumpPluginStateTest {

    private lateinit var podStateManager: OmnipodDashPodStateManager
    private lateinit var sut: OmnipodDashPumpPlugin

    @BeforeEach
    fun setUp() {
        podStateManager = mock()
        sut = OmnipodDashPumpPlugin(
            aapsLogger = mock<AAPSLogger>(),
            rh = mock<ResourceHelper>(),
            preferences = mock<Preferences>(),
            commandQueue = mock<CommandQueue>(),
            omnipodManager = mock<OmnipodDashManager>(),
            podStateManager = podStateManager,
            history = mock<DashHistory>(),
            pumpSync = mock<PumpSync>(),
            rxBus = mock<RxBus>(),
            aapsSchedulers = mock<AapsSchedulers>(),
            uiInteraction = mock<UiInteraction>(),
            notificationManager = mock<NotificationManager>(),
            pumpEnactResultProvider = { mock<PumpEnactResult>() },
            bolusProgressData = mock<BolusProgressData>(),
            dashHistoryDatabase = mock<DashHistoryDatabase>(),
            protectionCheck = mock<ProtectionCheck>(),
            blePreCheck = mock<BlePreCheck>()
        )
    }

    // region isThisProfileSet - the answer that gates setBasal

    /**
     * Before activation finishes there is no pod to compare against, and answering "not set" would have
     * the queue push a basal to a pod that cannot take one, so it deliberately claims the profile is set.
     */
    @Test
    fun `an unactivated pod reports the profile as already set`() {
        whenever(podStateManager.isActivationCompleted).thenReturn(false)

        assertThat(sut.isThisProfileSet(mock<PumpProfile>())).isTrue()
    }

    /** A suspended pod means a basal change stopped half way, so the profile is NOT in force. */
    @Test
    fun `a suspended pod reports the profile as not set`() {
        whenever(podStateManager.isActivationCompleted).thenReturn(true)
        whenever(podStateManager.isSuspended).thenReturn(true)

        assertThat(sut.isThisProfileSet(mock<PumpProfile>())).isFalse()
    }

    /**
     * A profile carrying no basal values is refused rather than mapped to an empty pod program. An
     * empty program would be a pod delivering no basal at all, so failing loudly is the safe answer.
     */
    @Test
    fun `a profile with no basal values is refused rather than mapped to an empty program`() {
        whenever(podStateManager.isActivationCompleted).thenReturn(true)
        whenever(podStateManager.isSuspended).thenReturn(false)
        val emptyProfile = mock<PumpProfile>()
        whenever(emptyProfile.getBasalValues()).thenReturn(emptyArray())

        assertThrows<IllegalArgumentException> { sut.isThisProfileSet(emptyProfile) }
    }

    // endregion

    // region baseBasalRate - what the loop reads as the delivered rate

    /**
     * An alarming pod is not delivering, whatever its program says. Reporting the programmed rate here
     * would have the loop account for basal insulin that is not going in.
     */
    @Test
    fun `an alarming pod reports a zero base rate even with a program loaded`() {
        val program = mock<BasalProgram>()
        whenever(program.rateAt(org.mockito.kotlin.any())).thenReturn(1.25)
        whenever(podStateManager.basalProgram).thenReturn(program)
        whenever(podStateManager.alarmType).thenReturn(mock())

        assertThat(sut.baseBasalRate.cU).isEqualTo(0.0)
    }

    @Test
    fun `a healthy pod reports the rate its program gives for now`() {
        val program = mock<BasalProgram>()
        whenever(program.rateAt(org.mockito.kotlin.any())).thenReturn(1.25)
        whenever(podStateManager.basalProgram).thenReturn(program)
        whenever(podStateManager.alarmType).thenReturn(null)

        assertThat(sut.baseBasalRate.cU).isEqualTo(1.25)
    }

    @Test
    fun `a pod with no program reports a zero base rate`() {
        whenever(podStateManager.basalProgram).thenReturn(null)
        whenever(podStateManager.alarmType).thenReturn(null)

        assertThat(sut.baseBasalRate.cU).isEqualTo(0.0)
    }

    // endregion

    // region connection and activation state

    @Test
    fun `the pump counts as initialized only while a pod is running`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        assertThat(sut.isInitialized()).isTrue()

        whenever(podStateManager.isPodRunning).thenReturn(false)
        assertThat(sut.isInitialized()).isFalse()
    }

    @Test
    fun `suspension is reported straight from the pod state`() {
        whenever(podStateManager.isSuspended).thenReturn(true)
        assertThat(sut.isSuspended()).isTrue()

        whenever(podStateManager.isSuspended).thenReturn(false)
        assertThat(sut.isSuspended()).isFalse()
    }

    /** Busy blocks the command queue, and it must do so only part way through activation. */
    @Test
    fun `the pump is busy only part way through activation`() {
        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.NOT_STARTED)
        assertThat(sut.isBusy()).isFalse()

        whenever(podStateManager.activationProgress).thenReturn(ActivationProgress.COMPLETED)
        assertThat(sut.isBusy()).isFalse()
    }

    /**
     * With no pod running the pump reports connected, so the queue does not sit waiting for a radio
     * link to something that is not there.
     */
    @Test
    fun `no pod running counts as connected`() {
        whenever(podStateManager.isPodRunning).thenReturn(false)

        assertThat(sut.isConnected()).isTrue()
    }

    @Test
    fun `a running pod counts as connected only when bluetooth is connected`() {
        whenever(podStateManager.isPodRunning).thenReturn(true)
        whenever(podStateManager.bluetoothConnectionState)
            .thenReturn(OmnipodDashPodStateManager.BluetoothConnectionState.DISCONNECTED)
        assertThat(sut.isConnected()).isFalse()

        whenever(podStateManager.bluetoothConnectionState)
            .thenReturn(OmnipodDashPodStateManager.BluetoothConnectionState.CONNECTED)
        assertThat(sut.isConnected()).isTrue()
    }

    @Test
    fun `nothing is connecting or handshaking until a connect is started`() {
        assertThat(sut.isConnecting()).isFalse()
        assertThat(sut.isHandshakeInProgress()).isFalse()
    }

    // endregion

    // region identity

    /** Without a pod the serial falls back to the fake-TBR marker rather than being blank. */
    @Test
    fun `the serial number falls back when no pod is paired`() {
        whenever(podStateManager.uniqueId).thenReturn(null)

        assertThat(sut.serialNumber()).isNotEmpty()
    }

    @Test
    fun `the serial number is the pod unique id when one is paired`() {
        whenever(podStateManager.uniqueId).thenReturn(123456789L)

        assertThat(sut.serialNumber()).isEqualTo("123456789")
    }

    @Test
    fun `the pump reports itself as an Insulet pump that cannot handle DST`() {
        assertThat(sut.manufacturer()).isEqualTo(ManufacturerType.Insulet)
        assertThat(sut.canHandleDST()).isFalse()
        assertThat(sut.isFakingTempsByExtendedBoluses).isFalse()
    }

    // endregion
}
