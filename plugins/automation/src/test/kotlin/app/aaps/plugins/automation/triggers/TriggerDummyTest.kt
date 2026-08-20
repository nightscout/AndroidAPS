package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.asJsonObject
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class TriggerDummyTest : TriggerTestBase() {

    @Test
    fun instantiateTest() {
        var trigger: Trigger? = triggerFactory.instantiate("{\"data\":{},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy\"}".asJsonObject())
        assertIs<TriggerDummy>(trigger)

        trigger = triggerFactory.instantiate("{\"data\":{},\"type\":\"app.aaps.plugins.automation.triggers.TriggerDummy\"}".asJsonObject())
        assertIs<TriggerDummy>(trigger)

        trigger = triggerFactory.instantiate("{\"data\":{},\"type\":\"TriggerDummy\"}".asJsonObject())
        assertIs<TriggerDummy>(trigger)
    }

}
