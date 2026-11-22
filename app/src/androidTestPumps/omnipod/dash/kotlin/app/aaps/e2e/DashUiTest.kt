package app.aaps.e2e

import app.aaps.e2e.scenario.PumpUnderTest

/** The Omnipod Dash screens, see [AbstractOmnipodUiTest] and [OmnipodScreens]. */
class DashUiTest : AbstractOmnipodUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = DashPumpUnderTest()
}
