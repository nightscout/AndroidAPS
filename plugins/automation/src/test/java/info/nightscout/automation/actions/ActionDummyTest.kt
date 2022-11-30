package info.nightscout.automation.actions

import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.Test

class ActionDummyTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        var action: Action? = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy\"}"))
        Assert.assertTrue(action is ActionDummy)

        action = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.automation.actions.ActionDummy\"}"))
        Assert.assertTrue(action is ActionDummy)

        action = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"ActionDummy\"}"))
        Assert.assertTrue(action is ActionDummy)
    }
}