package app.aaps.implementation.pump

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.TB
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PumpSyncConcentrationTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var activePump: PumpWithConcentration
    @Mock lateinit var effectiveProfile: EffectiveProfile

    private lateinit var sut: PumpSyncImplementation

    private val iCfg200 = ICfg(insulinLabel = "Test", insulinEndTime = 18000000L, insulinPeakTime = 1800000L, concentration = 2.0)
    private val iCfg100 = ICfg(insulinLabel = "Test", insulinEndTime = 18000000L, insulinPeakTime = 1800000L, concentration = 1.0)
    private val iCfg50 = ICfg(insulinLabel = "Test", insulinEndTime = 18000000L, insulinPeakTime = 1800000L, concentration = 0.5)

    private val pumpType = PumpType.GENERIC_AAPS
    private val pumpSerial = "12345"
    private val now = 1000000000L

    // Captured values from persistence layer calls
    private var capturedBolus: BS? = null
    private var capturedTB: TB? = null
    private var capturedEB: EB? = null

    @BeforeEach
    fun setup() {
        capturedBolus = null
        capturedTB = null
        capturedEB = null

        whenever(dateUtil.now()).thenReturn(now)
        whenever(activePlugin.activePump).thenReturn(activePump)
        whenever(activePump.selectedActivePump()).thenReturn(activePump)

        // Register pump so confirmActivePump passes
        whenever(preferences.get(StringNonKey.ActivePumpType)).thenReturn(pumpType.description)
        whenever(preferences.get(StringNonKey.ActivePumpSerialNumber)).thenReturn(pumpSerial)
        whenever(preferences.get(LongNonKey.ActivePumpChangeTimestamp)).thenReturn(now - 10000)

        sut = PumpSyncImplementation(
            aapsLogger, dateUtil, preferences, notificationManager,
            profileFunction, persistenceLayer, activePlugin
        )
    }

    private suspend fun stubInsertBolusWithTempId() {
        whenever(persistenceLayer.insertBolusWithTempId(any())).thenAnswer { invocation ->
            capturedBolus = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<BS>().apply { inserted.add(capturedBolus!!) }
        }
    }

    private suspend fun stubSyncPumpBolus() {
        whenever(persistenceLayer.syncPumpBolus(any(), any())).thenAnswer { invocation ->
            capturedBolus = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<BS>().apply { inserted.add(capturedBolus!!) }
        }
    }

    private suspend fun stubSyncPumpBolusWithTempId() {
        whenever(persistenceLayer.syncPumpBolusWithTempId(any(), any())).thenAnswer { invocation ->
            capturedBolus = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<BS>().apply { updated.add(capturedBolus!!) }
        }
    }

    private suspend fun stubSyncPumpTemporaryBasal() {
        whenever(persistenceLayer.syncPumpTemporaryBasal(any(), any())).thenAnswer { invocation ->
            capturedTB = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<TB>().apply { inserted.add(capturedTB!!) }
        }
    }

    private suspend fun stubInsertTemporaryBasalWithTempId() {
        whenever(persistenceLayer.insertTemporaryBasalWithTempId(any())).thenAnswer { invocation ->
            capturedTB = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<TB>().apply { inserted.add(capturedTB!!) }
        }
    }

    private suspend fun stubSyncPumpExtendedBolus() {
        whenever(persistenceLayer.syncPumpExtendedBolus(any())).thenAnswer { invocation ->
            capturedEB = invocation.getArgument(0)
            PersistenceLayer.TransactionResult<EB>().apply { inserted.add(capturedEB!!) }
        }
    }

    // --- addBolusWithTempId concentration conversion ---

    @Test
    fun `addBolusWithTempId converts U200 pump insulin to IU`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubInsertBolusWithTempId()

            // PumpInsulin(3.0) with U200 -> 3.0 * 2.0 = 6.0 IU
            sut.addBolusWithTempId(now, PumpInsulin(3.0), 100L, BS.Type.NORMAL, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(6.0)
        }
    }

    @Test
    fun `addBolusWithTempId priming uses raw cU value`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubInsertBolusWithTempId()

            // Priming: PumpInsulin(3.0) -> uses cU directly = 3.0
            sut.addBolusWithTempId(now, PumpInsulin(3.0), 100L, BS.Type.PRIMING, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(3.0)
        }
    }

    @Test
    fun `addBolusWithTempId with U100 stores cU value unchanged`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(1.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg100)
            stubInsertBolusWithTempId()

            // U100: PumpInsulin(3.0) -> 3.0 * 1.0 = 3.0
            sut.addBolusWithTempId(now, PumpInsulin(3.0), 100L, BS.Type.NORMAL, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(3.0)
        }
    }

    // --- syncBolusWithPumpId concentration conversion ---

    @Test
    fun `syncBolusWithPumpId converts U200 pump insulin to IU`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubSyncPumpBolus()

            sut.syncBolusWithPumpId(now, PumpInsulin(3.0), BS.Type.NORMAL, 200L, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(6.0)
        }
    }

    @Test
    fun `syncBolusWithPumpId priming uses raw cU`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubSyncPumpBolus()

            sut.syncBolusWithPumpId(now, PumpInsulin(3.0), BS.Type.PRIMING, 200L, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(3.0)
        }
    }

    // --- syncBolusWithTempId concentration conversion ---

    @Test
    fun `syncBolusWithTempId converts U50 pump insulin to IU`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(0.5)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg50)
            stubSyncPumpBolusWithTempId()

            // U50: PumpInsulin(3.0) -> 3.0 * 0.5 = 1.5
            sut.syncBolusWithTempId(now, PumpInsulin(3.0), 100L, BS.Type.NORMAL, 200L, pumpType, pumpSerial)

            assertThat(capturedBolus!!.amount).isEqualTo(1.5)
        }
    }

    // --- syncTemporaryBasalWithPumpId concentration conversion ---

    @Test
    fun `syncTemporaryBasalWithPumpId converts absolute rate with U200`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubSyncPumpTemporaryBasal()

            // Absolute: PumpRate(2.0) with U200 -> 2.0 * 2.0 = 4.0 IU/h
            sut.syncTemporaryBasalWithPumpId(now, PumpRate(2.0), 1800000L, true, PumpSync.TemporaryBasalType.NORMAL, 300L, pumpType, pumpSerial)

            assertThat(capturedTB!!.rate).isEqualTo(4.0)
            assertThat(capturedTB!!.isAbsolute).isTrue()
        }
    }

    @Test
    fun `syncTemporaryBasalWithPumpId does not convert percent rate`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubSyncPumpTemporaryBasal()

            // Percent (not absolute): PumpRate(150.0) with U200 -> stays 150.0 (no conversion)
            sut.syncTemporaryBasalWithPumpId(now, PumpRate(150.0), 1800000L, false, PumpSync.TemporaryBasalType.NORMAL, 300L, pumpType, pumpSerial)

            assertThat(capturedTB!!.rate).isEqualTo(150.0)
            assertThat(capturedTB!!.isAbsolute).isFalse()
        }
    }

    // --- addTemporaryBasalWithTempId concentration conversion ---

    @Test
    fun `addTemporaryBasalWithTempId converts absolute rate with U200`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubInsertTemporaryBasalWithTempId()

            sut.addTemporaryBasalWithTempId(now, PumpRate(2.0), 1800000L, true, 400L, PumpSync.TemporaryBasalType.NORMAL, pumpType, pumpSerial)

            assertThat(capturedTB!!.rate).isEqualTo(4.0)
        }
    }

    // --- syncExtendedBolusWithPumpId concentration conversion ---

    @Test
    fun `syncExtendedBolusWithPumpId converts rate with U200`() {
        runBlocking {
            whenever(profileFunction.getProfile(now)).thenReturn(effectiveProfile)
            whenever(effectiveProfile.insulinConcentration()).thenReturn(2.0)
            whenever(effectiveProfile.iCfg).thenReturn(iCfg200)
            stubSyncPumpExtendedBolus()

            // PumpRate(2.0) with U200 -> 2.0 * 2.0 = 4.0
            sut.syncExtendedBolusWithPumpId(now, PumpRate(2.0), 3600000L, false, 500L, pumpType, pumpSerial)

            assertThat(capturedEB!!.amount).isEqualTo(4.0)
        }
    }

    // --- No profile running ---

    @Test
    fun `addBolusWithTempId returns false when no profile running`() {
        runBlocking { whenever(profileFunction.getProfile(now)).thenReturn(null) }

        val result = runBlocking { sut.addBolusWithTempId(now, PumpInsulin(3.0), 100L, BS.Type.NORMAL, pumpType, pumpSerial) }

        assertThat(result).isFalse()
    }

    @Test
    fun `syncTemporaryBasalWithPumpId returns false when no profile running`() {
        runBlocking { whenever(profileFunction.getProfile(now)).thenReturn(null) }

        val result = runBlocking { sut.syncTemporaryBasalWithPumpId(now, PumpRate(2.0), 1800000L, true, PumpSync.TemporaryBasalType.NORMAL, 300L, pumpType, pumpSerial) }

        assertThat(result).isFalse()
    }

    @Test
    fun `syncExtendedBolusWithPumpId returns false when no profile running`() {
        runBlocking { whenever(profileFunction.getProfile(now)).thenReturn(null) }

        val result = runBlocking { sut.syncExtendedBolusWithPumpId(now, PumpRate(2.0), 3600000L, false, 500L, pumpType, pumpSerial) }

        assertThat(result).isFalse()
    }

    // --- VirtualPump bypass ---

    @Test
    fun `verifyPumpIdentification returns true for VirtualPump`() {
        val virtualPump = org.mockito.kotlin.mock<app.aaps.core.interfaces.pump.Pump>(extraInterfaces = arrayOf(VirtualPump::class))
        whenever(activePump.selectedActivePump()).thenReturn(virtualPump)

        val result = sut.verifyPumpIdentification(PumpType.DANA_RS, "99999")

        assertThat(result).isTrue()
    }

    @Test
    fun `verifyPumpIdentification returns true when type and serial match`() {
        val result = sut.verifyPumpIdentification(pumpType, pumpSerial)

        assertThat(result).isTrue()
    }

    @Test
    fun `verifyPumpIdentification returns false when serial mismatch`() {
        val result = sut.verifyPumpIdentification(pumpType, "wrong_serial")

        assertThat(result).isFalse()
    }
}
