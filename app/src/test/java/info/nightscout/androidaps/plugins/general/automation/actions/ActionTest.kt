package info.nightscout.androidaps.plugins.general.automation.actions

import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest
class ActionTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        val action: Action? = ActionDummy(injector).instantiate(JSONObject("{\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy\"}"))
        Assert.assertNotEquals(null, action)
    }
}