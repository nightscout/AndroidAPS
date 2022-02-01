package info.nightscout.androidaps.plugins.general.automation.actions

import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class ActionTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        val action: Action? = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy\"}"))
        Assert.assertNotEquals(null, action)
    }
}