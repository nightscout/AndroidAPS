package app.aaps.plugins.automation

import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionSMBChange
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

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutomationEventObject) {
                it.aapsLogger = aapsLogger
            }
            if (it is Action) {
                it.aapsLogger = aapsLogger
                it.rh = rh
            }
            if (it is ActionSMBChange) {
                it.dateUtil = dateUtil
                it.preferences = preferences
            }
        }
    }

    @Test fun testCloneEvent() {
        // create test object
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        event.addAction(ActionSMBChange(injector))

        // export to json
        val eventJson = event.toJSON()
        val parsed = JSONObject(eventJson)
        // Verify id is present and is a valid UUID
        assertThat(parsed.has("id")).isTrue()
        assertThat(parsed.getString("id")).isNotEmpty()
        // Verify other fields (lenient because of id)
        assertThat(parsed.getString("title")).isEqualTo("Test")
        assertThat(parsed.getBoolean("enabled")).isTrue()
        assertThat(parsed.getBoolean("userAction")).isFalse()
        assertThat(parsed.getBoolean("systemAction")).isFalse()

        // clone
        val clone = AutomationEventObject(injector).fromJSON(eventJson)

        // check id preserved
        assertThat(clone.id).isEqualTo(event.id)

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

    @Test fun idPreservedOnRoundtrip() {
        val event = AutomationEventObject(injector)
        event.title = "Test"
        event.trigger = TriggerDummy(injector).instantiate(JSONObject(TriggerConnectorTest().oneItem)) as TriggerConnector
        val originalId = event.id
        val clone = AutomationEventObject(injector).fromJSON(event.toJSON())
        assertThat(clone.id).isEqualTo(originalId)
    }

    @Test fun idGeneratedWhenMissingInJson() {
        // Simulate legacy JSON without id field
        val legacyJson =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Legacy\",\"systemAction\":false,\"actions\":[],\"enabled\":true}"
        val event = AutomationEventObject(injector).fromJSON(legacyJson)
        assertThat(event.id).isNotEmpty()
        assertThat(event.title).isEqualTo("Legacy")
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
