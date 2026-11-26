package app.aaps.pump.aaruy

import app.aaps.core.interfaces.utils.HardLimits
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * The hard limits for a test. The driver reads max basal, [rate] in U/h, and max bolus, [bolus] in U, see
 * [AaruyPump.maxBasalForPump] and [AaruyPump.maxBolusForPump]. 2 U/h is what the max basal setting was before the hard
 * limit took its place, 17 U is the hard limit of an adult.
 */
internal fun testHardLimits(rate: Double = 2.0, bolus: Double = 17.0): HardLimits = mock {
    on { maxBasal() } doReturn rate
    on { maxBolus() } doReturn bolus
}
