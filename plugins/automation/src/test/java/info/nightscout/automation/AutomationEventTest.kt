package info.nightscout.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.automation.actions.Action
import info.nightscout.automation.actions.ActionLoopEnable
import info.nightscout.automation.actions.ActionStopProcessing
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerConnectorTest
import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.aps.Loop
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

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

    @Test
    fun testCloneEvent() {
        // create test object
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        event.addAction(ActionLoopEnable(injector))

        // export to json
        val eventJsonExpected =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"actions\":[\"{\\\"type\\\":\\\"ActionLoopEnable\\\"}\"],\"enabled\":true}"
        Assertions.assertEquals(eventJsonExpected, event.toJSON())

        // clone
        val clone = AutomationEventObject(injector).fromJSON(eventJsonExpected, 1)

        // check title
        Assertions.assertEquals(event.title, clone.title)

        // check trigger
        Assertions.assertNotNull(clone.trigger)
        Assertions.assertFalse(event.trigger === clone.trigger) // not the same object reference
        Assertions.assertEquals(event.trigger.javaClass, clone.trigger.javaClass)
        Assertions.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON())

        // check action
        Assertions.assertEquals(1, clone.actions.size)
        Assertions.assertFalse(event.actions === clone.actions) // not the same object reference
        Assertions.assertEquals(clone.toJSON(), clone.toJSON())
    }

    @Test
    fun hasStopProcessing() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        Assertions.assertFalse(event.hasStopProcessing())
        event.addAction(ActionStopProcessing(injector))
        Assertions.assertTrue(event.hasStopProcessing())
    }
}
