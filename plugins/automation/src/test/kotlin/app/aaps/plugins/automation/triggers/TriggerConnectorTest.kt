package app.aaps.plugins.automation.triggers

import app.aaps.plugins.automation.asJsonObject
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.skyscreamer.jsonassert.JSONAssert
import org.json.JSONException
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class TriggerConnectorTest : TriggerTestBase() {

    // Written by the old org.json code, which emitted keys in hash order ("data" before "type").
    // Kept in that order on purpose: these are what real stored automations look like, and
    // AutomationEventTest feeds oneItem straight into the factory, so they cover reading legacy data.
    private val empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"TriggerConnector\"}"
    val oneItem =
        "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\"]},\"type\":\"TriggerConnector\"}"

    // What we write now. kotlinx keeps insertion order, so "type" comes first. Child triggers are
    // stored as JSON *strings* inside triggerList, so their own key order is part of the text and
    // cannot be compared loosely - which is exactly why this second pair has to exist.
    private val emptyCanonical = "{\"type\":\"TriggerConnector\",\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]}}"
    private val oneItemCanonical =
        "{\"type\":\"TriggerConnector\",\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"type\\\":\\\"TriggerConnector\\\",\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]}}\"]}}"

    @Test fun testTriggerList() = runTest {
        val t = TriggerConnector(triggerDeps)
        val t2 = TriggerConnector(triggerDeps)
        val t3 = TriggerConnector(triggerDeps)
        assertThat(t.size()).isEqualTo(0)
        t.list.add(t2)
        assertThat(t.size()).isEqualTo(1)
        assertThat(t.list).containsExactly(t2)
        t.list.add(t3)
        assertThat(t.size()).isEqualTo(2)
        assertThat(t.list).containsExactly(t2, t3).inOrder()
        assertThat(t.list.remove(t2)).isTrue()
        assertThat(t.size()).isEqualTo(1)
        assertThat(t.list).containsExactly(t3)
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun testListTriggerOR() = runTest {
        val t = TriggerConnector(triggerDeps, TriggerConnector.Type.OR)
        t.list.add(TriggerDummy(triggerDeps))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isFalse()
        t.list.add(TriggerDummy(triggerDeps, true))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun testListTriggerXOR() = runTest {
        val t = TriggerConnector(triggerDeps, TriggerConnector.Type.XOR)
        t.list.add(TriggerDummy(triggerDeps))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isFalse()
        t.list.add(TriggerDummy(triggerDeps, true))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isTrue()
        t.list.add(TriggerDummy(triggerDeps, true))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun testListTriggerAND() = runTest {
        val t = TriggerConnector(triggerDeps, TriggerConnector.Type.AND)
        t.list.add(TriggerDummy(triggerDeps, true))
        t.list.add(TriggerDummy(triggerDeps, true))
        assertThat(t.shouldRun()).isTrue()
        t.list.add(TriggerDummy(triggerDeps, true))
        t.list.add(TriggerDummy(triggerDeps))
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun toJSONTest() = runTest {
        val t = TriggerConnector(triggerDeps)
        JSONAssert.assertEquals(emptyCanonical, t.toJSON(), true)
        t.list.add(TriggerConnector(triggerDeps))
        JSONAssert.assertEquals(oneItemCanonical, t.toJSON(), true)
    }

    @Test fun legacyKeyOrderStillParses() = runTest {
        // The old hash-order form has to keep reading - it is on disk and it arrives over sync from
        // phones running older versions.
        val t = triggerFactory.instantiate(oneItem.asJsonObject()) as TriggerConnector
        assertThat(t.list).hasSize(1)
        assertIs<TriggerConnector>(t.list[0])
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() = runTest {
        val t = TriggerConnector(triggerDeps)
        t.list.add(TriggerConnector(triggerDeps))
        val t2 = triggerFactory.instantiate(t.toJSON().asJsonObject()) as TriggerConnector
        assertThat(t2.size()).isEqualTo(1)
        assertIs<TriggerConnector>(t2.list[0])
    }

}
