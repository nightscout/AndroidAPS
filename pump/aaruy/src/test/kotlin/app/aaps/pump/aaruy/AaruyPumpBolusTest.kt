package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AaruyPumpBolusTest {

    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java),
        mock(Preferences::class.java),
        mock(DateUtil::class.java),
        mock(PumpSync::class.java),
        mock(TemporaryBasalStorage::class.java),
        mock(BolusProgressData::class.java)
    )

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
