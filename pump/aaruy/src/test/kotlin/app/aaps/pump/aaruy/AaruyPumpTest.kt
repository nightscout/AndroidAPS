package app.aaps.pump.aaruy

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AaruyType
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.keys.AaruyDoubleKey
import app.aaps.pump.aaruy.keys.AaruyIntKey
import app.aaps.pump.aaruy.keys.AaruyLongKey
import app.aaps.pump.aaruy.keys.AaruyStringKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.aaruy.mylibrary.open.data.BasalInfo
import com.aaruy.mylibrary.open.data.BasalItem
import com.aaruy.mylibrary.open.data.BasicParam
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for [AaruyPump] state assembly.
 *
 * The class is a plain state holder (no Android APIs on the tested paths), so tests use
 * plain JUnit 5 + Mockito + Truth without Robolectric. Every observable StateFlow is backed
 * by a simple getter/setter, so most assertions check that setters propagate to both the flow
 * and (where applicable) the injected [Preferences].
 *
 * Time-window logic (`isTempBasalInProgress` / `isExtendedInProgress`) is driven by a stubbed
 * `dateUtil.now()` so tests stay deterministic.
 */
internal class AaruyPumpTest : TestBaseWithProfile() {

    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock private lateinit var bolusProgressData: BolusProgressData

    private lateinit var aaruyPump: AaruyPump

    @BeforeEach
    fun prepare() {
        aaruyPump = AaruyPump(
            aapsLogger, rh, preferences, dateUtil,
            pumpSync, temporaryBasalStorage, bolusProgressData
        )
    }

    // ============================================================
    // 1. State setters
    // ============================================================

    @Test
    fun `pumpState setter updates flow and preferences`() {
        aaruyPump.pumpState = AaruyPumpState.BASALRATE_RUNNING

        assertThat(aaruyPump.pumpState).isEqualTo(AaruyPumpState.BASALRATE_RUNNING)
        assertThat(aaruyPump.pumpStateFlow.value).isEqualTo(AaruyPumpState.BASALRATE_RUNNING)
        verify(preferences).put(AaruyIntKey.PumpState, AaruyPumpState.BASALRATE_RUNNING.state.toInt())
    }

    @Test
    fun `pumpLastState setter updates flow and preferences`() {
        aaruyPump.pumpLastState = AaruyPumpState.STOP_RUNNING

        assertThat(aaruyPump.pumpLastState).isEqualTo(AaruyPumpState.STOP_RUNNING)
        verify(preferences).put(AaruyIntKey.PumpLastState, AaruyPumpState.STOP_RUNNING.state.toInt())
    }

    @Test
    fun `connectionState setter updates flow`() {
        aaruyPump.connectionState = ConnectionState.CONNECTED
        assertThat(aaruyPump.connectionStateFlow.value).isEqualTo(ConnectionState.CONNECTED)
    }

    @Test
    fun `reservoir setter updates flow`() {
        aaruyPump.reservoir = 120.0
        assertThat(aaruyPump.reservoir).isEqualTo(120.0)
        assertThat(aaruyPump.reservoirFlow.value).isEqualTo(120.0)
    }

    @Test
    fun `remainBattery setter updates flow`() {
        aaruyPump.remainBattery = 75
        assertThat(aaruyPump.remainBattery).isEqualTo(75)
        assertThat(aaruyPump.remainBatteryFlow.value).isEqualTo(75)
    }

    @Test
    fun `lastConnection setter updates flow and preferences`() {
        aaruyPump.lastConnection = 1_000_000L
        assertThat(aaruyPump.lastConnection).isEqualTo(1_000_000L)
        verify(preferences).put(AaruyLongKey.PumpLastConnect, 1_000_000L)
    }

    @Test
    fun `pumpSN setter updates field and preferences`() {
        aaruyPump.pumpSN = "aaruy_SN123"
        assertThat(aaruyPump.pumpSN).isEqualTo("aaruy_SN123")
        verify(preferences).put(AaruyStringKey.PumpName, "aaruy_SN123")
    }

    @Test
    fun `pumpWarning setter updates flow`() {
        aaruyPump.pumpWarning = AlarmState.PUMP_LOW_POWER
        assertThat(aaruyPump.pumpWarningFlow.value).isEqualTo(AlarmState.PUMP_LOW_POWER)
    }

    // ============================================================
    // 2. Temp basal
    // ============================================================

    @Test
    fun `isTempBasalInProgress is true within the window`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.tempBasalStart = now - 5_000
        aaruyPump.tempBasalDuration = 30_000

        assertThat(aaruyPump.isTempBasalInProgress).isTrue()
    }

    @Test
    fun `isTempBasalInProgress is false once the window elapsed`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.tempBasalStart = now - 60_000
        aaruyPump.tempBasalDuration = 30_000

        assertThat(aaruyPump.isTempBasalInProgress).isFalse()
    }

    @Test
    fun `isTempBasalInProgress is false when start is zero`() {
        aaruyPump.tempBasalStart = 0
        aaruyPump.tempBasalDuration = 30_000
        assertThat(aaruyPump.isTempBasalInProgress).isFalse()
    }

    @Test
    fun `setting isTempBasalInProgress to false clears the window`() {
        aaruyPump.tempBasalStart = 100L
        aaruyPump.tempBasalDuration = 1000L

        aaruyPump.isTempBasalInProgress = false

        assertThat(aaruyPump.tempBasalStart).isEqualTo(0L)
        assertThat(aaruyPump.tempBasalDuration).isEqualTo(0L)
    }

    @Test
    fun `fromTemporaryBasal copies timestamp duration and rate`() {
        val tbr: PumpSync.PumpState.TemporaryBasal = mock {
            on { timestamp } doReturn 500L
            on { duration } doReturn 60_000L
            on { rate } doReturn 0.5
        }

        aaruyPump.fromTemporaryBasal(tbr)

        assertThat(aaruyPump.tempBasalStart).isEqualTo(500L)
        assertThat(aaruyPump.tempBasalDuration).isEqualTo(60_000L)
        assertThat(aaruyPump.tempBasalAbsoluteRate).isEqualTo(0.5)
    }

    @Test
    fun `fromTemporaryBasal null clears all fields`() {
        aaruyPump.tempBasalStart = 100L
        aaruyPump.tempBasalDuration = 1000L
        aaruyPump.tempBasalAbsoluteRate = 1.0

        aaruyPump.fromTemporaryBasal(null)

        assertThat(aaruyPump.tempBasalStart).isEqualTo(0L)
        assertThat(aaruyPump.tempBasalDuration).isEqualTo(0L)
        assertThat(aaruyPump.tempBasalAbsoluteRate).isEqualTo(0.0)
    }

    @Test
    fun `temporaryBasalToString returns empty when not in progress`() {
        aaruyPump.tempBasalStart = 0
        assertThat(aaruyPump.temporaryBasalToString()).isEmpty()
    }

    // ============================================================
    // 3. Extended bolus
    // ============================================================

    @Test
    fun `isExtendedInProgress is true within the window`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.extendedBolusStart = now - 60_000
        aaruyPump.extendedBolusDuration = 30 * 60_000L
        aaruyPump.extendedBolusAmount = 3.0

        assertThat(aaruyPump.isExtendedInProgress).isTrue()
    }

    @Test
    fun `isExtendedInProgress is false before start`() {
        aaruyPump.extendedBolusStart = 0
        assertThat(aaruyPump.isExtendedInProgress).isFalse()
    }

    @Test
    fun `setting isExtendedInProgress to true throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            aaruyPump.isExtendedInProgress = true
        }
    }

    @Test
    fun `setting isExtendedInProgress to false clears all fields`() {
        aaruyPump.extendedBolusStart = 100L
        aaruyPump.extendedBolusDuration = 1000L
        aaruyPump.extendedBolusAmount = 1.0

        aaruyPump.isExtendedInProgress = false

        assertThat(aaruyPump.extendedBolusStart).isEqualTo(0L)
        assertThat(aaruyPump.extendedBolusDuration).isEqualTo(0L)
        assertThat(aaruyPump.extendedBolusAmount).isEqualTo(0.0)
    }

    @Test
    fun `extendedBolusAbsoluteRate derives amount over duration`() {
        aaruyPump.extendedBolusDuration = 2 * 60 * 60 * 1000L // 2h
        aaruyPump.extendedBolusAmount = 3.0

        assertThat(aaruyPump.extendedBolusAbsoluteRate).isWithin(1e-6).of(1.5)
    }

    @Test
    fun `setting extendedBolusAbsoluteRate recomputes amount`() {
        aaruyPump.extendedBolusDuration = 2 * 60 * 60 * 1000L
        aaruyPump.extendedBolusAbsoluteRate = 1.5

        assertThat(aaruyPump.extendedBolusAmount).isWithin(1e-6).of(3.0)
    }

    @Test
    fun `extendedBolusRemainingMinutes is positive inside window`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.extendedBolusStart = now - 60_000
        aaruyPump.extendedBolusDuration = 120 * 60_000L

        assertThat(aaruyPump.extendedBolusRemainingMinutes).isEqualTo(119)
    }

    @Test
    fun `extendedBolusRemainingMinutes is zero after end`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.extendedBolusStart = now - 120 * 60_000L
        aaruyPump.extendedBolusDuration = 60 * 60_000L

        assertThat(aaruyPump.extendedBolusRemainingMinutes).isEqualTo(0)
    }

    @Test
    fun `fromExtendedBolus copies timestamp duration and amount`() {
        val eb: PumpSync.PumpState.ExtendedBolus = mock {
            on { timestamp } doReturn 1_000L
            on { duration } doReturn 60_000L
            on { amount } doReturn 2.0
        }

        aaruyPump.fromExtendedBolus(eb)

        assertThat(aaruyPump.extendedBolusStart).isEqualTo(1_000L)
        assertThat(aaruyPump.extendedBolusDuration).isEqualTo(60_000L)
        assertThat(aaruyPump.extendedBolusAmount).isEqualTo(2.0)
    }

    // ============================================================
    // 4. Alarms
    // ============================================================

    @Test
    fun `addAlarm adds to activeAlarms and persists`() {
        aaruyPump.addAlarm(AlarmState.PUMP_LOW_POWER)

        assertThat(aaruyPump.activeAlarms).contains(AlarmState.PUMP_LOW_POWER)
        verify(preferences).put(any<AaruyStringKey>(), any())
    }

    @Test
    fun `removeAlarm removes from activeAlarms`() {
        aaruyPump.addAlarm(AlarmState.PUMP_LOW_POWER)
        aaruyPump.addAlarm(AlarmState.PUMP_LOW_RESERVOIR)

        aaruyPump.removeAlarm(AlarmState.PUMP_LOW_POWER)

        assertThat(aaruyPump.activeAlarms).doesNotContain(AlarmState.PUMP_LOW_POWER)
        assertThat(aaruyPump.activeAlarms).contains(AlarmState.PUMP_LOW_RESERVOIR)
    }

    @Test
    fun `clearAlarmState empties activeAlarms`() {
        aaruyPump.addAlarm(AlarmState.PUMP_LOW_POWER)
        aaruyPump.addAlarm(AlarmState.PUMP_NO_POWER)

        aaruyPump.clearAlarmState()

        assertThat(aaruyPump.activeAlarms).isEmpty()
    }

    @Test
    fun `alarmStateToString returns a non-empty label for every alarm state`() {
        whenever(rh.gs(any<Int>())).thenReturn("label")

        AlarmState.entries.forEach { state ->
            assertThat(aaruyPump.alarmStateToString(state)).isNotEmpty()
        }
    }

    // ============================================================
    // 5. pumpType mapping
    // ============================================================

    @Test
    fun `pumpType B200A maps to AARUY_FULL`() {
        assertThat(aaruyPump.pumpType(AaruyType.B200A)).isEqualTo(PumpType.AARUY_FULL)
    }

    @Test
    fun `pumpType B200C maps to AARUY_FULL`() {
        assertThat(aaruyPump.pumpType(AaruyType.B200C)).isEqualTo(PumpType.AARUY_FULL)
    }

    @Test
    fun `pumpType B200B maps to AARUY_BASE`() {
        assertThat(aaruyPump.pumpType(AaruyType.B200B)).isEqualTo(PumpType.AARUY_BASE)
    }

    @Test
    fun `pumpType B200D maps to AARUY_BASE`() {
        assertThat(aaruyPump.pumpType(AaruyType.B200D)).isEqualTo(PumpType.AARUY_BASE)
    }

    @Test
    fun `pumpType no-arg reads the persisted pumpType field (default 3 == B200A)`() {
        assertThat(aaruyPump.pumpType()).isEqualTo(PumpType.AARUY_FULL)
    }

    // ============================================================
    // 6. Status updates
    // ============================================================

    @Test
    fun `handleBolusStatusUpdate small bolus updates delivered flow and last bolus`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)
        aaruyPump.curBolusTime = 500_000L

        aaruyPump.handleBolusStatusUpdate(bolusType = 1, bolusCompleted = true, amountDelivered = 2.5)

        assertThat(aaruyPump.bolusProgressLastTimeStamp).isEqualTo(now)
        assertThat(aaruyPump.bolusDone).isTrue()
        assertThat(aaruyPump.lastBolusAmount).isEqualTo(2.5)
        assertThat(aaruyPump.lastBolusTime).isEqualTo(500_000L)
        assertThat(aaruyPump.bolusAmountDeliveredFlow.value).isEqualTo(2.5)
    }

    @Test
    fun `handleBolusStatusUpdate large bolus does not overwrite delivered flow`() {
        val now = 1_000_000L
        whenever(dateUtil.now()).thenReturn(now)

        aaruyPump.handleBolusStatusUpdate(bolusType = 1, bolusCompleted = false, amountDelivered = 30.0)

        // 30 > 25 sentinel, so _bolusAmountDelivered keeps its previous value
        assertThat(aaruyPump.bolusAmountDeliveredFlow.value).isEqualTo(0.0)
        assertThat(aaruyPump.bolusDone).isFalse()
    }

    @Test
    fun `handleBasalStatusUpdate updates lastBasalRate`() {
        aaruyPump.handleBasalStatusUpdate(0.85)
        assertThat(aaruyPump.lastBasalRate).isEqualTo(0.85)
    }

    // ============================================================
    // 7. reset
    // ============================================================

    @Test
    fun `reset clears connection and pump states`() {
        aaruyPump.lastConnection = 1_000_000L
        aaruyPump.pumpState = AaruyPumpState.BASALRATE_RUNNING
        aaruyPump.pumpLastState = AaruyPumpState.BASALRATE_RUNNING

        aaruyPump.reset()

        assertThat(aaruyPump.lastConnection).isEqualTo(0L)
        assertThat(aaruyPump.pumpState).isEqualTo(AaruyPumpState.FIRST_INIT)
        assertThat(aaruyPump.pumpLastState).isEqualTo(AaruyPumpState.FIRST_INIT)
    }

    // ============================================================
    // 8. syncPrefParam
    // ============================================================

    @Test
    fun `syncPrefParam returns true when preferences match pump state`() {
        aaruyPump.maxBolus = 20.0
        aaruyPump.maxBasal = 3.0
        aaruyPump.lowAlert = 30.0
        aaruyPump.bolusStep = 2
        aaruyPump.closeInjectSound = 0

        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBolus)).thenReturn(20.0)
        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBasal)).thenReturn(3.0)
        whenever(preferences.get(AaruyDoubleKey.PumpPrefLowAlert)).thenReturn(30.0)
        whenever(preferences.get(AaruyIntKey.PumpPrefBolusStep)).thenReturn(1) // getBolusIncFromString -> 2
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundRemind)).thenReturn(0)
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundAlarm)).thenReturn(0)

        assertThat(aaruyPump.syncPrefParam()).isTrue()
    }

    @Test
    fun `syncPrefParam clamps maxBolus to the hard upper bound`() {
        aaruyPump.maxBolus = 10.0
        aaruyPump.maxBasal = 2.0
        aaruyPump.lowAlert = 20.0
        aaruyPump.bolusStep = 2
        aaruyPump.closeInjectSound = 0

        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBolus)).thenReturn(100.0) // exceeds 25.0 cap
        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBasal)).thenReturn(2.0)
        whenever(preferences.get(AaruyDoubleKey.PumpPrefLowAlert)).thenReturn(20.0)
        whenever(preferences.get(AaruyIntKey.PumpPrefBolusStep)).thenReturn(1)
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundRemind)).thenReturn(0)
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundAlarm)).thenReturn(0)

        assertThat(aaruyPump.syncPrefParam()).isFalse()
        assertThat(aaruyPump.maxBolus).isEqualTo(25.0)
    }

    // ============================================================
    // 9. handleLoadBasicParam
    // ============================================================

    @Test
    fun `handleLoadBasicParam applies BasicParam fields and returns sync result`() {
        val basicParam: BasicParam = mock {
            on { pumpType } doReturn (0x80 or 2) // masked to 2 == B200B
            on { maxBasal } doReturn 2.5
            on { maxBolus } doReturn 15.0
            on { bolusStep } doReturn 2
            on { lowAlert } doReturn 25.0
            on { closeInjectSound } doReturn 0
        }
        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBolus)).thenReturn(15.0)
        whenever(preferences.get(AaruyDoubleKey.PumpPrefMaxBasal)).thenReturn(2.5)
        whenever(preferences.get(AaruyDoubleKey.PumpPrefLowAlert)).thenReturn(25.0)
        whenever(preferences.get(AaruyIntKey.PumpPrefBolusStep)).thenReturn(1)
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundRemind)).thenReturn(0)
        whenever(preferences.get(AaruyIntKey.PumpPrefSoundAlarm)).thenReturn(0)

        aaruyPump.handleLoadBasicParam(basicParam)

        assertThat(aaruyPump.pumpType).isEqualTo(2)
        assertThat(aaruyPump.maxBasal).isEqualTo(2.5)
        assertThat(aaruyPump.maxBolus).isEqualTo(15.0)
        assertThat(aaruyPump.lowAlert).isEqualTo(25.0)
        assertThat(aaruyPump.closeInjectSound).isEqualTo(0)
    }

    // ============================================================
    // 10. buildAaruyProfileRecord
    // ============================================================
    @Test
    fun `buildAaruyProfileRecord produces a BasalInfo`() {
        whenever(dateUtil.now()).thenReturn(0L)

        // Profile.ProfileValue: 需要 timeAsSeconds 和 value 两个字段。
        // 用 mock 避免依赖 ProfileValue 的构造签名（不同 AAPS 版本构造略有差异）。
        val profileValue: Profile.ProfileValue = mock {
            on { timeAsSeconds } doReturn 0
            on { value } doReturn 0.5
        }
        val profile: Profile = mock {
            on { getBasalValues() } doReturn arrayOf(profileValue)
        }

        val result = aaruyPump.buildAaruyProfileRecord(profile)

        assertThat(result).isInstanceOf(BasalInfo::class.java)
        assertThat(result.basalItems).isNotNull()
    }

    @Test
    fun `buildAaruyProfileArray produces non-empty JSON bytes`() {
        whenever(dateUtil.now()).thenReturn(0L)

        val profileValue: Profile.ProfileValue = mock {
            on { timeAsSeconds } doReturn 0
            on { value } doReturn 0.5
        }
        val profile: Profile = mock {
            on { getBasalValues() } doReturn arrayOf(profileValue)
        }

        val bytes = aaruyPump.buildAaruyProfileArray(profile)

        assertThat(bytes).isNotEmpty()
        // Gson 输出对象时首字符是 '{'
        assertThat(bytes[0].toInt()).isEqualTo('{'.code)
    }
}