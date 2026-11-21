package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.comm.BasicParam
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.verify

class AaruyPumpBolusTest {

    private val bolusProgressData = mock(BolusProgressData::class.java)
    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java),
        mock(Preferences::class.java),
        mock(DateUtil::class.java),
        mock(PumpSync::class.java),
        mock(TemporaryBasalStorage::class.java),
        bolusProgressData
    )

    private fun pumpReportsBolusStep(step: Int) =
        pump.handleLoadBasicParam(BasicParam(maxBasal = 2.0, maxBolus = 10.0, bolusStep = step, lowAlert = 20.0, closeInjectSound = 0, pumpType = 0))

    /** The totals of the boluses shown as running, in order. */
    private fun shownTotals(): List<Double> = argumentCaptor<Double>().run {
        verify(bolusProgressData, atLeast(0)).startIfIdle(capture(), any())
        allValues
    }

    /**
     * Seen on a real pump (2026-11-21): after an app restart the status of a running bolus came before the pump settings,
     * and the default step showed a bolus of 1.0 U as 1.5 U. It is shown once the pump has reported its step.
     */
    @Test
    fun aFoundBolusWaitsForTheBolusStepOfThePump() {
        pump.showFoundBolus(given = 0.5, secondsLeft = 40, isSmb = false)
        assertEquals(emptyList<Double>(), shownTotals())

        pumpReportsBolusStep(1)
        pump.showFoundBolus(given = 0.5, secondsLeft = 40, isSmb = false)
        assertEquals(1, shownTotals().size)
        assertEquals(1.0, shownTotals().single(), 1e-9)
    }

    /** Seen on a real pump set to 0.05 U: 0.05 U given and 18 s left was a bolus of 0.5 U. */
    @Test
    fun theFoundBolusIsWhatWasGivenPlusTheStepsLeft() {
        pumpReportsBolusStep(2)
        pump.showFoundBolus(given = 0.05, secondsLeft = 18, isSmb = false)
        assertEquals(0.5, shownTotals().single(), 1e-9)
    }

    @Test
    fun aNewPumpMustReportItsBolusStepAgain() {
        pumpReportsBolusStep(1)
        pump.reset(newPump = true)
        pump.showFoundBolus(given = 0.5, secondsLeft = 40, isSmb = false)
        assertEquals(emptyList<Double>(), shownTotals())
    }

    @Test
    fun stoppedBolusKeepsTheAmountThePumpReported() {
        pump.handleBolusStatusUpdate(false, 0.05)

        // A stop has no amount, so the driver calls this without one.
        pump.handleBolusStatusUpdate(true)

        assertTrue(pump.bolusDone)
        assertEquals(0.05, pump.bolusDelivered, 0.0001)
        assertEquals(0.05, pump.lastBolusAmount, 0.0001)
    }

    @Test
    fun finishedBolusReportsTheAmount() {
        pump.handleBolusStatusUpdate(true, 0.5)

        assertEquals(0.5, pump.bolusDelivered, 0.0001)
        assertEquals(0.5, pump.lastBolusAmount, 0.0001)
    }
}
