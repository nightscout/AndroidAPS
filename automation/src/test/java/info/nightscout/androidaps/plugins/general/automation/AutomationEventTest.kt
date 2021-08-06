package info.nightscout.androidaps.plugins.general.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.interfaces.ConfigBuilder
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.actions.ActionLoopEnable
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnectorTest
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class AutomationEventTest : TestBase() {

    @Mock lateinit var loopPlugin: Loop
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var configBuilder: ConfigBuilder

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutomationEvent) {
                it.aapsLogger = aapsLogger
            }
            if (it is ActionLoopEnable) {
                it.loopPlugin = loopPlugin
                it.resourceHelper = resourceHelper
                it.configBuilder = configBuilder
                it.rxBus = RxBusWrapper(aapsSchedulers)
            }
        }
    }

    @Test
    fun testCloneEvent() {
        // create test object
        val event = AutomationEvent(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest.oneItem))
            ?: throw Exception()
        event.addAction(ActionLoopEnable(injector))

        // export to json
        val eventJsonExpected = "{\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[\\\"{\\\\\\\"data\\\\\\\":{\\\\\\\"connectorType\\\\\\\":\\\\\\\"AND\\\\\\\",\\\\\\\"triggerList\\\\\\\":[]},\\\\\\\"type\\\\\\\":\\\\\\\"TriggerConnector\\\\\\\"}\\\"]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Test\",\"systemAction\":false,\"actions\":[\"{\\\"type\\\":\\\"info.nightscout.androidaps.plugins.general.automation.actions.ActionLoopEnable\\\"}\"],\"enabled\":true}"
        Assert.assertEquals(eventJsonExpected, event.toJSON())

        // clone
        val clone = AutomationEvent(injector).fromJSON(eventJsonExpected)

        // check title
        Assert.assertEquals(event.title, clone.title)

        // check trigger
        Assert.assertNotNull(clone.trigger)
        Assert.assertFalse(event.trigger === clone.trigger) // not the same object reference
        Assert.assertEquals(event.trigger.javaClass, clone.trigger.javaClass)
        Assert.assertEquals(event.trigger.toJSON(), clone.trigger.toJSON())

        // check action
        Assert.assertEquals(1, clone.actions.size.toLong())
        Assert.assertFalse(event.actions === clone.actions) // not the same object reference
        Assert.assertEquals(clone.toJSON(), clone.toJSON())
    }
}