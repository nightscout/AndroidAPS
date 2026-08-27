package app.aaps.plugins.automation.actions

import app.aaps.plugins.automation.asJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class ActionDummyTest : ActionsTestBase() {

    @Test
    fun instantiateTest() {
        var action: Action? = actionFactory.instantiate("""{"type":"info.nightscout.androidaps.plugins.general.automation.actions.ActionDummy"}""".asJsonObject())
        assertIs<ActionDummy>(action)

        action = actionFactory.instantiate("""{"type":"app.aaps.plugins.automation.actions.ActionDummy"}""".asJsonObject())
        assertIs<ActionDummy>(action)

        action = actionFactory.instantiate("""{"type":"ActionDummy"}""".asJsonObject())
        assertIs<ActionDummy>(action)
    }
}
