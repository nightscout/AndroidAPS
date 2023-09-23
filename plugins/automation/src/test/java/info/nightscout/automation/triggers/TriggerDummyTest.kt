package info.nightscout.automation.triggers

import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TriggerDummyTest : TriggerTestBase() {

    @Test
    fun instantiateTest() {
        var trigger: Trigger? = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy\"}"))
        Assertions.assertTrue(trigger is TriggerDummy)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"info.nightscout.automation.triggers.TriggerDummy\"}"))
        Assertions.assertTrue(trigger is TriggerDummy)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"TriggerDummy\"}"))
        Assertions.assertTrue(trigger is TriggerDummy)
    }

}