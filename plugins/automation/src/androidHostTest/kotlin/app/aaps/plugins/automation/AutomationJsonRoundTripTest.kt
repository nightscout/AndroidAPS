package app.aaps.plugins.automation

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.triggers.TriggerBg
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Guards the stored automation format across the org.json to kotlinx.serialization move.
 *
 * [AutomationRuntime.EMPTY_EVENT] is a real string written by the old org.json code, so it is the
 * fixture that matters: it stores whole numbers as integers (`"bg":4`, `"value":8`), which kotlinx
 * would reject with its strict accessors. Reading it has to keep working forever - it is on every
 * existing install and it arrives over sync from phones running older versions.
 */
class AutomationJsonRoundTripTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    private val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(
            aapsLogger, mock(), rh, profileFunction, mock(), preferences, mock(), mock(), mock(), mock(), mock(), dateUtil
        )
    }

    private val triggerFactory: TriggerFactory by lazy {
        TriggerFactory(triggerDeps, mock(), mock(), mock())
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

    @BeforeEach fun prepare() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test fun legacyIntegerNumbersStillRead() {
        val event = eventFactory.fromJSON(AutomationRuntime.EMPTY_EVENT)

        assertThat(event.title).isEqualTo("Low")
        assertThat(event.isEnabled).isTrue()

        // "bg":4 was written by org.json as an integer. Strict kotlinx accessors would have returned
        // the default (0.0) here, silently turning a low-glucose trigger into "bg below 0".
        val bgTrigger = event.trigger.list.filterIsInstance<TriggerBg>().single()
        assertThat(bgTrigger.bg.value).isEqualTo(4.0)
        assertThat(bgTrigger.bg.units).isEqualTo(GlucoseUnit.MMOL)

        assertThat(event.actions).hasSize(1)
    }

    @Test fun roundTripIsIdempotent() {
        // First write may differ from the stored legacy bytes (kotlinx writes 4.0 where org.json wrote
        // 4). That is accepted: it costs one re-canonicalising sync push per master. What must NOT
        // happen is that it keeps changing - that would be an endless push loop between phones.
        val first = eventFactory.fromJSON(AutomationRuntime.EMPTY_EVENT).toJSON()
        val second = eventFactory.fromJSON(first).toJSON()
        val third = eventFactory.fromJSON(second).toJSON()

        assertThat(second).isEqualTo(first)
        assertThat(third).isEqualTo(first)
    }

    @Test fun everyKeyOfTheStoredShapeSurvives() {
        val before = Json.parseToJsonElement(AutomationRuntime.EMPTY_EVENT).jsonObject
        val after = Json.parseToJsonElement(eventFactory.fromJSON(AutomationRuntime.EMPTY_EVENT).toJSON()).jsonObject

        // Nothing the old format carried may disappear. The new form adds keys the fixture omits
        // (id, systemAction, ...) because fromJSON defaults them - that direction is safe.
        assertThat(after.keys).containsAtLeastElementsIn(before.keys)

        assertThat(after.lenientString("title")).isEqualTo(before.lenientString("title"))

        // The trigger and the actions stay JSON *strings* inside the event, not nested objects.
        val trigger = Json.parseToJsonElement(after.lenientString("trigger")).jsonObject
        assertThat(trigger.lenientString("type")).isEqualTo("TriggerConnector")
        assertThat((trigger["data"] as kotlinx.serialization.json.JsonObject).lenientString("connectorType")).isEqualTo("AND")
    }

    @Test fun unparseableTriggerDegradesInsteadOfThrowing() {
        val broken = "{\"title\":\"Broken\",\"enabled\":true,\"trigger\":\"not json at all\",\"actions\":[]}"

        val event = eventFactory.fromJSON(broken)

        assertThat(event.title).isEqualTo("Broken")
        assertThat(event.trigger.list).isEmpty()
    }

    @Test fun quotedNumberStillRead() {
        // Nightscout documents in the wild contain quoted numbers. org.json coerced them on read;
        // strict kotlinx does not. The lenient readers must keep coercing.
        val quoted = "{\"bg\":\"4\",\"comparator\":\"IS_LESSER\",\"units\":\"mmol\"}"

        assertThat(Json.parseToJsonElement(quoted).jsonObject.lenientDouble("bg")).isEqualTo(4.0)
    }
}
