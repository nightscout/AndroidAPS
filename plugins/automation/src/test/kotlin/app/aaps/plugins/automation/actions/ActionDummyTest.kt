package app.aaps.plugins.automation.actions

import kotlin.test.assertIs
import org.json.JSONObject
import org.junit.jupiter.api.Test

class ActionDummyTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        var action: Action? = ActionDummy(injector).instantiate(JSONObject("""{"type":"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy"}"""))
        assertIs<ActionDummy>(action)

        action = ActionDummy(injector).instantiate(JSONObject("""{"type":"app.aaps.plugins.automation.actions.ActionDummy"}"""))
        assertIs<ActionDummy>(action)

        action = ActionDummy(injector).instantiate(JSONObject("""{"type":"ActionDummy"}"""))
        assertIs<ActionDummy>(action)
    }
}
