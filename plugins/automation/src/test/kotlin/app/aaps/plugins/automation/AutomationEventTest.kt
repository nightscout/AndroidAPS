package app.aaps.plugins.automation

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionLoopEnable
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerConnectorTest
import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.skyscreamer.jsonassert.JSONAssert

class AutomationEventTest : TestBase() {

    @Mock lateinit var loopPlugin: Loop
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var configBuilder: ConfigBuilder

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutomationEventObject) {
                it.aapsLogger = aapsLogger
            }
            if (it is Action) {
                it.aapsLogger = aapsLogger
            }
            if (it is ActionLoopEnable) {
                it.loopPlugin = loopPlugin
                it.rh = rh
                it.configBuilder = configBuilder
                it.rxBus = rxBus
            }
        }
    }

    @Test fun testCloneEvent() {
        // create test object
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        event.addAction(ActionLoopEnable(injector))

        // export to json
        val eventJsonExpected =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"actions\":[\"{\\\"type\\\":\\\"ActionLoopEnable\\\"}\"],\"enabled\":true}"
        JSONAssert.assertEquals(eventJsonExpected, event.toJSON(), true)

        // clone
        val clone = AutomationEventObject(injector).fromJSON(eventJsonExpected, 1)

        // check title
        assertThat(clone.title).isEqualTo(event.title)

        // check trigger
        assertThat(clone.trigger).isNotNull()
        assertThat(event.trigger).isNotSameInstanceAs(clone.trigger)
        assertThat(event.trigger.javaClass).isNotInstanceOf(clone.trigger.javaClass)
        JSONAssert.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON(), true)

        // check action
        assertThat(clone.actions).hasSize(1)
        assertThat(event.actions).isNotSameInstanceAs(clone.actions)
        JSONAssert.assertEquals(clone.toJSON(), clone.toJSON(), true)
    }

    @Test fun hasStopProcessing() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        assertThat(event.hasStopProcessing()).isFalse()
        event.addAction(ActionStopProcessing(injector))
        assertThat(event.hasStopProcessing()).isTrue()
    }
}
