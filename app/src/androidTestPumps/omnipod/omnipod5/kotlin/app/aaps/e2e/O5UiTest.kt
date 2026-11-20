package app.aaps.e2e

import app.aaps.e2e.scenario.PumpUnderTest

/** The Omnipod 5 screens, see [AbstractOmnipodUiTest] and [OmnipodScreens]. */
class O5UiTest : AbstractOmnipodUiTest() {

    override fun pumpUnderTest(): PumpUnderTest = O5PumpUnderTest()
}
