package app.aaps.pump.aaruy

import app.aaps.core.interfaces.utils.HardLimits
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * The hard limits for a test. The driver reads only max basal, [rate] in U/h, which the pump gets as its own max basal,
 * see [AaruyPump.maxBasalForPump]. 2 U/h is what the max basal setting was before the hard limit took its place.
 */
internal fun testHardLimits(rate: Double = 2.0): HardLimits = mock { on { maxBasal() } doReturn rate }
