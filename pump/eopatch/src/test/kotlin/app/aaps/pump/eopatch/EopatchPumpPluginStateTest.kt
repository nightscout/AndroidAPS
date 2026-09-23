package app.aaps.pump.eopatch

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.alarm.IAlarmManager
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Covers the state predicates on [EopatchPumpPlugin].
 *
 * The command queue asks these before it sends anything, so they decide whether a command is
 * dispatched, queued, or left waiting for a connection that will never come. They are each one line,
 * which is exactly why they are worth pinning: a plausible-looking "correction" to any of them
 * changes what the queue does with every command.
 */
class EopatchPumpPluginStateTest {

    private val patchConfig: PatchConfig = mock()
    private val patchManagerExecutor: PatchManagerExecutor = mock()
    private val preferenceManager: PreferenceManager = mock()
    private val patchState: PatchState = mock()

    /** Typed explicitly: an inline lambda leaves the compiler comparing two views of the same type. */
    private val enactResultProvider: () -> PumpEnactResult = { mock() }

    private fun sut() = EopatchPumpPlugin(
        aapsLogger = mock<AAPSLogger>(),
        rh = mock<ResourceHelper>(),
        preferences = mock<Preferences>(),
        commandQueue = mock<CommandQueue>(),
        aapsSchedulers = mock<AapsSchedulers>(),
        fabricPrivacy = mock<FabricPrivacy>(),
        dateUtil = mock<DateUtil>(),
        pumpSync = mock<PumpSync>(),
        patchManager = mock<IPatchManager>(),
        patchManagerExecutor = patchManagerExecutor,
        alarmManager = mock<IAlarmManager>(),
        preferenceManager = preferenceManager,
        pumpEnactResultProvider = enactResultProvider,
        patchConfig = patchConfig,
        normalBasalManager = mock<NormalBasalManager>(),
        protectionCheck = mock<ProtectionCheck>(),
        blePreCheck = mock<BlePreCheck>(),
        bolusProgressData = mock<BolusProgressData>(),
        notificationManager = mock()
    )

    private fun patch(
        activated: Boolean = false,
        deactivated: Boolean = false,
        connection: BleConnectionState = BleConnectionState.DISCONNECTED,
        basalPaused: Boolean = false
    ) {
        whenever(patchConfig.isActivated).thenReturn(activated)
        whenever(patchConfig.isDeactivated).thenReturn(deactivated)
        whenever(patchManagerExecutor.patchConnectionState).thenReturn(connection)
        whenever(preferenceManager.patchState).thenReturn(patchState)
        whenever(patchState.isNormalBasalPaused).thenReturn(basalPaused)
    }

    // ---- configured ----

    @Test
    fun withNoPatchActivatedTheDriverIsNotConfigured() {
        patch(activated = false)

        assertThat(sut().isConfigured()).isFalse()
    }

    @Test
    fun anActivatedPatchMakesTheDriverConfigured() {
        patch(activated = true)

        assertThat(sut().isConfigured()).isTrue()
    }

    // ---- connected ----

    @Test
    fun aConnectedPatchIsReportedConnected() {
        patch(activated = true, connection = BleConnectionState.CONNECTED)

        assertThat(sut().isConnected()).isTrue()
    }

    @Test
    fun aPatchThatIsNotConnectedIsReportedDisconnected() {
        patch(activated = true, connection = BleConnectionState.DISCONNECTED)

        assertThat(sut().isConnected()).isFalse()
    }

    /**
     * Deliberate, and it reads like a mistake: a deactivated patch answers "connected" even though
     * no radio link exists. There is nothing left to talk to, so claiming connected keeps the command
     * queue from waiting on a connection that can never be made. Changing this to `false` would leave
     * the queue trying to reach a patch that is gone.
     */
    @Test
    fun aDeactivatedPatchReportsConnectedSoTheQueueDoesNotWaitForIt() {
        patch(activated = false, deactivated = true, connection = BleConnectionState.DISCONNECTED)

        assertThat(sut().isConnected()).isTrue()
    }

    @Test
    fun aConnectingPatchIsReportedAsConnecting() {
        patch(connection = BleConnectionState.CONNECTING)

        assertThat(sut().isConnecting()).isTrue()
        assertThat(sut().isConnected()).isFalse()
    }

    // ---- initialized ----

    @Test
    fun theDriverIsInitializedOnlyWhenActivatedAndConnected() {
        patch(activated = true, connection = BleConnectionState.CONNECTED)

        assertThat(sut().isInitialized()).isTrue()
    }

    @Test
    fun anActivatedButDisconnectedPatchIsNotInitialized() {
        patch(activated = true, connection = BleConnectionState.DISCONNECTED)

        assertThat(sut().isInitialized()).isFalse()
    }

    /** Deactivated counts as connected, but with nothing activated it is still not initialized. */
    @Test
    fun aDeactivatedPatchIsNotInitialized() {
        patch(activated = false, deactivated = true)

        assertThat(sut().isInitialized()).isFalse()
    }

    // ---- suspended ----

    @Test
    fun aPausedBasalMeansTheDriverReportsSuspended() {
        patch(activated = true, basalPaused = true)

        assertThat(sut().isSuspended()).isTrue()
    }

    @Test
    fun aRunningBasalMeansTheDriverIsNotSuspended() {
        patch(activated = true, basalPaused = false)

        assertThat(sut().isSuspended()).isFalse()
    }

    // ---- the constants the queue relies on ----

    /** This driver never blocks the queue on its own account. */
    @Test
    fun theDriverIsNeverBusyAndNeverHandshaking() {
        patch()

        assertThat(sut().isBusy()).isFalse()
        assertThat(sut().isHandshakeInProgress()).isFalse()
    }

    @Test
    fun thePumpIdentifiesItselfAsAnEoflowEopatch() {
        patch()
        whenever(patchConfig.patchSerialNumber).thenReturn("SN-4242")

        val plugin = sut()

        assertThat(plugin.manufacturer()).isEqualTo(ManufacturerType.Eoflow)
        assertThat(plugin.model()).isEqualTo(PumpType.EOFLOW_EOPATCH2)
        assertThat(plugin.serialNumber()).isEqualTo("SN-4242")
    }
}
