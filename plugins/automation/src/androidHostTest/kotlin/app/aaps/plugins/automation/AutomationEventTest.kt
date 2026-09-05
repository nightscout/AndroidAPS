package app.aaps.plugins.automation

import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.actions.ActionSMBChange
import app.aaps.plugins.automation.actions.ActionStopProcessing
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerConnectorTest
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class AutomationEventTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var pumpEnactResultProvider: () -> PumpEnactResult

    // Real factories over mocks: fromJSON has to rebuild actions and triggers for the clone assertions.
    private val triggerDeps: app.aaps.plugins.automation.triggers.TriggerDeps by lazy {
        app.aaps.plugins.automation.triggers.TriggerDeps(
            aapsLogger, mock(), rh, profileFunction, mock(), preferences, mock(), mock(), mock(), mock(), mock(), dateUtil
        )
    }

    private val triggerFactory: app.aaps.plugins.automation.triggers.TriggerFactory by lazy {
        app.aaps.plugins.automation.triggers.TriggerFactory(triggerDeps, mock(), mock(), mock())
    }

    private val actionFactory: ActionFactory by lazy {
        ActionFactory(
            triggerDeps, aapsLogger, rh, pumpEnactResultProvider, mock(), dateUtil, mock(), mock(), mock(),
            profileFunction, mock(), mock(), mock(), mock(), mock(), preferences, mock(), mock(), mock(), mock(),
            mock(), mock()
        )
    }

    private val eventFactory: AutomationEventFactory by lazy {
        AutomationEventFactory(aapsLogger, dateUtil, actionFactory, triggerFactory, triggerDeps)
    }

    @Test fun testCloneEvent() {
        // create test object
        val event = eventFactory.newEvent()
        event.title = "Test"
        event.trigger = triggerFactory.instantiate(TriggerConnectorTest().oneItem.asJsonObject()) as TriggerConnector
        event.addAction(ActionSMBChange(aapsLogger, rh, pumpEnactResultProvider, dateUtil, preferences))

        // export to json
        val eventJson = event.toJSON()
        val parsed = eventJson.asJsonObject()
        // Verify id is present and is a valid UUID
        assertThat(parsed.containsKey("id")).isTrue()
        assertThat(parsed.lenientString("id")).isNotEmpty()
        // Verify other fields (lenient because of id)
        assertThat(parsed.lenientString("title")).isEqualTo("Test")
        assertThat(parsed.lenientBoolean("enabled", false)).isTrue()
        assertThat(parsed.lenientBoolean("userAction", false)).isFalse()
        assertThat(parsed.lenientBoolean("systemAction", false)).isFalse()

        // clone
        val clone = eventFactory.fromJSON(eventJson)

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
        val event = eventFactory.newEvent()
        event.title = "Test"
        event.trigger = triggerFactory.instantiate(TriggerConnectorTest().oneItem.asJsonObject()) as TriggerConnector
        val originalId = event.id
        val clone = eventFactory.fromJSON(event.toJSON())
        assertThat(clone.id).isEqualTo(originalId)
    }

    @Test fun idGeneratedWhenMissingInJson() {
        // Simulate legacy JSON without id field
        val legacyJson =
            "{\"userAction\":false,\"autoRemove\":false,\"readOnly\":false,\"trigger\":\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\",\"title\":\"Legacy\",\"systemAction\":false,\"actions\":[],\"enabled\":true}"
        val event = eventFactory.fromJSON(legacyJson)
        assertThat(event.id).isNotEmpty()
        assertThat(event.title).isEqualTo("Legacy")
    }

    /**
     * Asking must not answer by changing the thing asked about.
     *
     * `areActionsValid()` used to set `isEnabled = false`, and its only caller is the Compose state
     * builder - so merely drawing the automation list switched rules off. On a client that is wrong:
     * a follower edits rules but never runs them and has a smaller plugin set on purpose, so an
     * action can be invalid there and perfectly fine on the master. `AutomationRuntime` makes that
     * decision now, behind its master-only guard.
     */
    @Test fun askingWhetherActionsAreValidDoesNotDisableTheEvent() {
        val invalid = mock<Action>()
        whenever(invalid.isValid()).thenReturn(false)
        val event = eventFactory.newEvent()
        event.title = "Test"
        event.addAction(invalid)

        assertThat(event.areActionsValid()).isFalse()
        assertThat(event.isEnabled).isTrue()
    }

    @Test fun hasStopProcessing() {
        val event = eventFactory.newEvent()
        event.title = "Test"
        event.trigger = triggerFactory.instantiate(TriggerConnectorTest().oneItem.asJsonObject()) as TriggerConnector
        assertThat(event.hasStopProcessing()).isFalse()
        event.addAction(ActionStopProcessing(aapsLogger, rh, pumpEnactResultProvider))
        assertThat(event.hasStopProcessing()).isTrue()
    }
}
