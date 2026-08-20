package app.aaps.plugins.automation.triggers

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class TriggerConnectorTest : TriggerTestBase() {

    private val empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"TriggerConnector\"}"
    val oneItem =
        "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\"]},\"type\":\"TriggerConnector\"}"

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
        assertThat(t.toJSON()).isEqualTo(empty)
        t.list.add(TriggerConnector(triggerDeps))
        assertThat(t.toJSON()).isEqualTo(oneItem)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() = runTest {
        val t = TriggerConnector(triggerDeps)
        t.list.add(TriggerConnector(triggerDeps))
        val t2 = triggerFactory.instantiate(JSONObject(t.toJSON())) as TriggerConnector
        assertThat(t2.size()).isEqualTo(1)
        assertIs<TriggerConnector>(t2.list[0])
    }

}
