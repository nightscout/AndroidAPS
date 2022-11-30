package info.nightscout.automation.triggers

import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.Test

class TriggerDummyTest : TriggerTestBase() {

    @Test
    fun instantiateTest() {
        var trigger: Trigger? = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy\"}"))
        Assert.assertTrue(trigger is TriggerDummy)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"info.nightscout.automation.triggers.TriggerDummy\"}"))
        Assert.assertTrue(trigger is TriggerDummy)

        trigger = TriggerDummy(injector).instantiate(JSONObject("{\"data\":{},\"type\":\"TriggerDummy\"}"))
        Assert.assertTrue(trigger is TriggerDummy)
    }

}