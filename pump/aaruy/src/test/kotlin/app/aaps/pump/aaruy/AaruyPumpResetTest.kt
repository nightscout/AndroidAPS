package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/** What an app start forgets, and what only a new pump does. */
class AaruyPumpResetTest {

    private val pump = AaruyPump(mock<AAPSLogger>(), mock<ResourceHelper>(), mock<Preferences>(), mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    /** The pump-unreachable alarm counts from the last contact. Set to 0 at each app start, it could ring too early. */
    @Test
    fun anAppStartKeepsTheLastContact() {
        pump.lastConnection = 1_800_000_000_000L
        pump.reset(newPump = false)
        assertEquals(1_800_000_000_000L, pump.lastConnection)
    }

    @Test
    fun aNewPumpHasNoContactYet() {
        pump.lastConnection = 1_800_000_000_000L
        pump.reset(newPump = true)
        assertEquals(0L, pump.lastConnection)
    }

    /** Not known until the first status. 0 went to Nightscout, the watch and automation rules as an empty reservoir. */
    @Test
    fun reservoirAndBatteryAreNotKnownAfterAReset() {
        pump.reservoir = 120.0
        pump.remainBattery = 80.0
        pump.reset(newPump = false)
        assertNull(pump.reservoir)
        assertNull(pump.remainBattery)
    }
}
