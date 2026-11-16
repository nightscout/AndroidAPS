package app.aaps.e2e

import app.aaps.e2e.scenario.PumpUnderTest
import app.aaps.e2e.ui.AbstractPumpUiTest
import app.aaps.e2e.ui.PumpScreens

/** The Omnipod 5 screens, see [AbstractPumpUiTest] and [OmnipodScreens]. */
class O5UiTest : AbstractPumpUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = O5PumpUnderTest()
    override fun screens(): PumpScreens = OmnipodScreens
}
