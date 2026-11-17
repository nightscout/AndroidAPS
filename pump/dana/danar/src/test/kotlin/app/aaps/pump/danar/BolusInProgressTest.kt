package app.aaps.pump.danar

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BolusInProgressTest : TestBase() {

    private val preferences: Preferences = mock()
    private val pumpSync: PumpSync = mock()
    private val dateUtil: DateUtil = mock()
    private var stored = ""
    private var now = START
    private lateinit var bolusInProgress: BolusInProgress

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(DanaStringNonKey.BolusInProgress)).thenAnswer { stored }
        doAnswer { stored = it.getArgument(1); Unit }.whenever(preferences).put(eq(DanaStringNonKey.BolusInProgress), any<String>())
        doAnswer { stored = ""; Unit }.whenever(preferences).remove(DanaStringNonKey.BolusInProgress)
        whenever(dateUtil.now()).thenAnswer { now }
        whenever(dateUtil.dateAndTimeAndSecondsString(any())).thenReturn("time")
        bolusInProgress = BolusInProgress(preferences, pumpSync, dateUtil, aapsLogger)
    }

    private suspend fun verifyRecorded(amount: Double, pumpType: PumpType = PumpType.DANA_R) {
        verify(pumpSync).syncBolusWithPumpId(eq(START), argThat<PumpInsulin> { cU == amount }, eq(BS.Type.NORMAL), eq(START), eq(pumpType), eq(SERIAL))
    }

    @Test
    fun withNothingSavedNothingIsRecorded() = runBlocking<Unit> {
        bolusInProgress.recordFromPump(START, 1.0, PumpType.DANA_R, SERIAL)
        bolusInProgress.recordWhole(PumpType.DANA_R_KOREAN, SERIAL)
        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
    }

    @Test
    fun aBolusThatMayStillRunCountsAllAndStaysSaved() = runBlocking<Unit> {
        bolusInProgress.save(START, 2.0, BS.Type.NORMAL)
        now = START + 30_000

        // The pump has not logged it yet: its last bolus is an older one.
        bolusInProgress.recordFromPump(START - 3_600_000, 0.5, PumpType.DANA_R, SERIAL)

        verifyRecorded(2.0)
        assertThat(stored).isNotEmpty()
    }

    @Test
    fun afterTheEndTheAmountThePumpLoggedCounts() = runBlocking<Unit> {
        bolusInProgress.save(START, 2.0, BS.Type.NORMAL)
        now = START + ENDED

        // In whole minutes, so up to a minute before the start. The user stopped it on the pump.
        bolusInProgress.recordFromPump(START - 50_000, 1.2, PumpType.DANA_R, SERIAL)

        verifyRecorded(1.2)
        assertThat(stored).isEmpty()
    }

    @Test
    fun afterTheEndANewerBolusOnThePumpLeavesTheWholeAmount() = runBlocking<Unit> {
        bolusInProgress.save(START, 2.0, BS.Type.NORMAL)
        now = START + ENDED + 600_000

        bolusInProgress.recordFromPump(START + ENDED + 300_000, 0.5, PumpType.DANA_R, SERIAL)

        verifyRecorded(2.0)
        assertThat(stored).isEmpty()
    }

    @Test
    fun afterTheEndABolusThePumpDoesNotShowLeavesTheWholeAmount() = runBlocking<Unit> {
        bolusInProgress.save(START, 2.0, BS.Type.NORMAL)
        now = START + ENDED

        bolusInProgress.recordFromPump(START - 3_600_000, 0.5, PumpType.DANA_R, SERIAL)

        verifyRecorded(2.0)
        assertThat(stored).isEmpty()
    }

    @Test
    fun theKoreanDanaRCountsAll() = runBlocking<Unit> {
        bolusInProgress.save(START, 2.0, BS.Type.NORMAL)

        bolusInProgress.recordWhole(PumpType.DANA_R_KOREAN, SERIAL)

        verifyRecorded(2.0, PumpType.DANA_R_KOREAN)
        assertThat(stored).isEmpty()
    }

    @Test
    fun aSavedBolusThatCannotBeReadIsRemoved() = runBlocking<Unit> {
        stored = "not a bolus"

        bolusInProgress.recordWhole(PumpType.DANA_R_KOREAN, SERIAL)

        verify(pumpSync, never()).syncBolusWithPumpId(any(), any(), anyOrNull(), any(), any(), any())
        assertThat(stored).isEmpty()
    }

    private companion object {

        const val START = 1_700_000_000_000L
        const val SERIAL = "AAA00000AA"

        /** 2 U at the slowest 60 s/U, and the minute the pump may take to log it. */
        const val ENDED = 180_000L
    }
}
