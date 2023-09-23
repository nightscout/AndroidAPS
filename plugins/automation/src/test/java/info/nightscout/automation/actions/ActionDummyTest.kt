package info.nightscout.automation.actions

import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ActionDummyTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        var action: Action? = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy\"}"))
        Assertions.assertTrue(action is ActionDummy)

        action = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.automation.actions.ActionDummy\"}"))
        Assertions.assertTrue(action is ActionDummy)

        action = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"ActionDummy\"}"))
        Assertions.assertTrue(action is ActionDummy)
    }
}