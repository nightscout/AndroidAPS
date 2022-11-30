package info.nightscout.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.automation.actions.Action
import info.nightscout.automation.actions.ActionLoopEnable
import info.nightscout.automation.actions.ActionStopProcessing
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerConnectorTest
import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.interfaces.ConfigBuilder
import info.nightscout.interfaces.aps.Loop
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONObject
import org.junit.Assert
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
                it.rxBus = RxBus(aapsSchedulers, aapsLogger)
            }
        }
    }

    @Test
    fun testCloneEvent() {
        // create test object
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest.oneItem)) as TriggerConnector
        event.addAction(ActionLoopEnable(injector))

        // export to json
        val eventJsonExpected =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"actions\":[\"{\\\"type\\\":\\\"ActionLoopEnable\\\"}\"],\"enabled\":true}"
        Assert.assertEquals(eventJsonExpected, event.toJSON())

        // clone
        val clone = AutomationEventObject(injector).fromJSON(eventJsonExpected, 1)

        // check title
        Assert.assertEquals(event.title, clone.title)

        // check trigger
        Assert.assertNotNull(clone.trigger)
        Assert.assertFalse(event.trigger === clone.trigger) // not the same object reference
        Assert.assertEquals(event.trigger.javaClass, clone.trigger.javaClass)
        Assert.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON())

        // check action
        Assert.assertEquals(1, clone.actions.size)
        Assert.assertFalse(event.actions === clone.actions) // not the same object reference
        Assert.assertEquals(clone.toJSON(), clone.toJSON())
    }

    @Test
    fun hasStopProcessing() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest.oneItem)) as TriggerConnector
        Assert.assertFalse(event.hasStopProcessing())
        event.addAction(ActionStopProcessing(injector))
        Assert.assertTrue(event.hasStopProcessing())
    }
}
