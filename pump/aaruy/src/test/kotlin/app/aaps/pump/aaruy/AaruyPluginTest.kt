package app.aaps.pump.aaruy

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.database.AaruyHistoryDatabase
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AaruyPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var aaruyHistoryDatabase: AaruyHistoryDatabase
    @Mock lateinit var blePreCheck: BlePreCheck

    lateinit var aaruyPump: AaruyPump
    private lateinit var aaruyPlugin: AaruyPlugin

    @BeforeEach
    fun prepareMocks() {
        // strings used by constraint messages (PumpPluginBase / AaruyPlugin)
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio))
            .thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate))
            .thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbolus))
            .thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")

        aaruyPump = mock()
        // `pumpDescription` is computed in the class initializer from pumpType(), so stub before ctor
        whenever(aaruyPump.pumpType()).thenReturn(PumpType.AARUY_BASE)
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.NO_RUNNING)
        whenever(aaruyPump.maxBasal).thenReturn(3.0)
        whenever(aaruyPump.maxBolus).thenReturn(5.0)
        whenever(aaruyPump.pumpSN).thenReturn("SN-TEST")

        aaruyPlugin = AaruyPlugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context,
            constraintChecker, aaruyPump,
            detailedBolusInfoStorage, temporaryBasalStorage,
            BolusProgressData(ch, CoroutineScope(Dispatchers.Unconfined)),
            dateUtil, aaruyHistoryDatabase,
            blePreCheck, pumpEnactResultProvider, mock()
        )
    }

    // ============================================================
    // Constraints
    // ============================================================

    @Test
    fun basalRateShouldBeLimited() {
        aaruyPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        whenever(aaruyPump.maxBasal).thenReturn(0.8)

        val constraint: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val result = aaruyPlugin.applyBasalConstraints(constraint, mock<Profile>())

        assertEquals(0.8, result.value(), 0.01)
    }

    @Test
    fun bolusAmountShouldBeLimited() {
        aaruyPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        whenever(aaruyPump.maxBolus).thenReturn(5.0)

        val constraint: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val result = aaruyPlugin.applyBolusConstraints(constraint)

        assertEquals(5.0, result.value(), 0.01)
    }

    @Test
    fun extendedBolusAmountShouldBeLimited() {
        aaruyPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        whenever(aaruyPump.maxBolus).thenReturn(5.0)

        val constraint: Constraint<Double> = ConstraintObject(Double.MAX_VALUE, aapsLogger)
        val result = aaruyPlugin.applyExtendedBolusConstraints(constraint)

        assertEquals(5.0, result.value(), 0.01)
    }

    @Test
    fun basalPercentShouldBeClampedToZeroWhenNegative() {
        aaruyPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        val constraint: Constraint<Int> = ConstraintObject(-10, aapsLogger)

        val result = aaruyPlugin.applyBasalPercentConstraints(constraint, mock<Profile>())

        assertEquals(0, result.value())
    }

    @Test
    fun basalPercentShouldBeLimitedByDescriptionMaxTempPercent() {
        aaruyPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        val constraint: Constraint<Int> = ConstraintObject(Int.MAX_VALUE, aapsLogger)

        val result = aaruyPlugin.applyBasalPercentConstraints(constraint, mock<Profile>())

        assertEquals(aaruyPlugin.pumpDescription.maxTempPercent, result.value())
    }

    // ============================================================
    // Lifecycle / identification
    // ============================================================

    @Test
    fun isInitializedShouldReturnFalseWhenPumpStateIsFirstInit() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        assertThat(aaruyPlugin.isInitialized()).isFalse()
    }

    @Test
    fun isInitializedShouldReturnTrueOtherwise() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.BASALRATE_RUNNING)
        assertThat(aaruyPlugin.isInitialized()).isTrue()
    }

    @Test
    fun isSuspendedShouldBeTrueBelowBasalRateRunning() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.NO_RUNNING)
        assertThat(aaruyPlugin.isSuspended()).isTrue()
    }

    @Test
    fun isSuspendedShouldBeFalseBetweenBasalRateAndPushForward() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.BASALRATE_RUNNING)
        assertThat(aaruyPlugin.isSuspended()).isFalse()
    }

    @Test
    fun isSuspendedShouldBeTrueAbovePushForward() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.STOP_RUNNING)
        assertThat(aaruyPlugin.isSuspended()).isTrue()
    }

    @Test
    fun manufacturerShouldBeAaruy() {
        assertThat(aaruyPlugin.manufacturer()).isEqualTo(ManufacturerType.Aaruy)
    }

    @Test
    fun modelShouldDelegateToPump() {
        whenever(aaruyPump.pumpType()).thenReturn(PumpType.AARUY_BASE)
        assertThat(aaruyPlugin.model()).isEqualTo(PumpType.AARUY_BASE)
    }

    @Test
    fun serialNumberShouldDelegateToPumpSN() {
        whenever(aaruyPump.pumpSN).thenReturn("SN-42")
        assertThat(aaruyPlugin.serialNumber()).isEqualTo("SN-42")
    }

    @Test
    fun canHandleDSTShouldBeTrue() {
        assertThat(aaruyPlugin.canHandleDST()).isTrue()
    }

    @Test
    fun isFakingTempsByExtendedBolusesShouldBeFalse() {
        assertThat(aaruyPlugin.isFakingTempsByExtendedBoluses).isFalse()
    }

    @Test
    fun isBusyShouldReturnFalse() {
        assertThat(aaruyPlugin.isBusy()).isFalse()
    }

    // ============================================================
    // baseBasalRate
    // ============================================================

    @Test
    fun baseBasalRateShouldUseLastBasalRateWhenAboveMinimum() {
        whenever(aaruyPump.lastBasalRate).thenReturn(0.5)
        assertThat(aaruyPlugin.baseBasalRate.cU).isWithin(0.001).of(0.5)
    }

    @Test
    fun baseBasalRateShouldClampTo0025WhenBelowMinimum() {
        whenever(aaruyPump.lastBasalRate).thenReturn(0.001)
        assertThat(aaruyPlugin.baseBasalRate.cU).isWithin(0.001).of(0.025)
    }

    // ============================================================
    // setNewBasalProfile
    // ============================================================

    @Test
    fun setNewBasalProfileWhenNotInitializedShouldSucceedWithComment() = runBlocking<Unit> {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        whenever(rh.gs(app.aaps.core.ui.R.string.pump_not_initialized_profile_not_set))
            .thenReturn("not initialized")

        val result = aaruyPlugin.setNewBasalProfile(mock())

        assertThat(result.success).isTrue()
        assertThat(result.comment).isEqualTo("not initialized")
    }

    // ============================================================
    // setTempBasalPercent (unsupported)
    // ============================================================

    @Test
    fun setTempBasalPercentShouldBeRejected() = runBlocking<Unit> {
        val result = aaruyPlugin.setTempBasalPercent(120, 30, enforceNew = false, tbrType = PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.comment).contains("percentage")
    }

    // ============================================================
    // setExtendedBolus (base pump rejects)
    // ============================================================

    @Test
    fun setExtendedBolusRejectedOnBasePump() = runBlocking<Unit> {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.BASALRATE_RUNNING)
        whenever(aaruyPump.pumpType()).thenReturn(PumpType.AARUY_BASE)

        val result = aaruyPlugin.setExtendedBolus(1.0, 60)

        assertThat(result.success).isFalse()
        assertThat(result.comment).contains("base")
    }

    @Test
    fun setExtendedBolusWhenNotInitializedShouldFail() = runBlocking<Unit> {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)

        val result = aaruyPlugin.setExtendedBolus(1.0, 60)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    // ============================================================
    // cancel / clear (no bound service -> all fail)
    // ============================================================

    @Test
    fun cancelTempBasalWhenNotInitializedShouldFail() = runBlocking<Unit> {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        val result = aaruyPlugin.cancelTempBasal(enforceNew = false)
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun cancelExtendedBolusWhenNotInitializedShouldFail() = runBlocking<Unit> {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        val result = aaruyPlugin.cancelExtendedBolus()
        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun clearAlarmsWhenNotInitializedShouldFail() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        val result = aaruyPlugin.clearAlarms()
        assertThat(result.success).isFalse()
    }

    // ============================================================
    // isThisProfileSet
    // ============================================================

    @Test
    fun isThisProfileSetShouldReturnTrueWhenNotInitialized() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        assertThat(aaruyPlugin.isThisProfileSet(mock())).isTrue()
    }

    // ============================================================
    // Custom actions / commands
    // ============================================================

    @Test
    fun getCustomActionsShouldReturnNull() {
        assertThat(aaruyPlugin.getCustomActions()).isNull()
    }

    @Test
    fun executeCustomCommandShouldReturnNull() {
        assertThat(aaruyPlugin.executeCustomCommand(mock())).isNull()
    }

    // ============================================================
    // clearAllTables
    // ============================================================

    @Test
    fun clearAllTablesShouldDelegateToDatabase() {
        aaruyPlugin.clearAllTables()
        verify(aaruyHistoryDatabase).clearAllTables()
    }

    // ============================================================
    // connect / disconnect (no service bound -> no-op)
    // ============================================================

    @Test
    fun connectWithoutBoundServiceShouldNotCrash() {
        whenever(preferences.get(app.aaps.pump.aaruy.keys.AaruyStringKey.MacAddress))
            .thenReturn("AA:BB:CC:DD:EE:FF")
        whenever(preferences.get(app.aaps.pump.aaruy.keys.AaruyStringKey.PumpName))
            .thenReturn("TestPump")

        aaruyPlugin.connect("test")

        assertThat(aaruyPlugin.isConnected()).isFalse()
        assertThat(aaruyPlugin.isConnecting()).isFalse()
    }

    @Test
    fun disconnectWithoutBoundServiceShouldNotCrash() {
        aaruyPlugin.disconnect("test")
        // nothing observable; just verifying it doesn't throw
    }

    @Test
    fun stopBolusDeliveringWhenNotInitializedShouldNoOp() {
        whenever(aaruyPump.pumpState).thenReturn(AaruyPumpState.FIRST_INIT)
        aaruyPlugin.stopBolusDelivering()
        verify(aaruyHistoryDatabase, never()).clearAllTables()
    }
}