package app.aaps.plugins.automation.triggers

import kotlin.test.assertIs
import org.json.JSONObject
import org.junit.jupiter.api.Test

class TriggerDummyTest : TriggerTestBase() {

    @Test
    fun instantiateTest() {
        var trigger: Trigger? = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy\"}"))
        assertIs<TriggerDummy>(trigger)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"app.aaps.plugins.automation.triggers.TriggerDummy\"}"))
        assertIs<TriggerDummy>(trigger)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"TriggerDummy\"}"))
        assertIs<TriggerDummy>(trigger)
    }

}
