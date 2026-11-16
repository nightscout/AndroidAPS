package app.aaps.e2e

import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens

/** The Omnipod Dash screens, see [AbstractPumpUiTest] and [OmnipodScreens]. */
class DashUiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DashPumpUnderTest()
    override fun screens(): PumpScreens = OmnipodScreens
}
