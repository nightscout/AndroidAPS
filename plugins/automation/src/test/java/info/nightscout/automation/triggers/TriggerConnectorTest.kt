package info.nightscout.automation.triggers

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Test

class TriggerConnectorTest : TriggerTestBase() {

    private val empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"TriggerConnector\"}"
    val oneItem =
        "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\"]},\"type\":\"TriggerConnector\"}"

    @Test fun testTriggerList() {
        val t = TriggerConnector(injector)
        val t2 = TriggerConnector(injector)
        val t3 = TriggerConnector(injector)
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

    @Test fun testListTriggerOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.OR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isFalse()
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isTrue()
    }

    @Test fun testListTriggerXOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.XOR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isFalse()
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isTrue()
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun testListTriggerAND() {
        val t = TriggerConnector(injector, TriggerConnector.Type.AND)
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector, true))
        assertThat(t.shouldRun()).isTrue()
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun toJSONTest() {
        val t = TriggerConnector(injector)
        assertThat(t.toJSON()).isEqualTo(empty)
        t.list.add(TriggerConnector(injector))
        assertThat(t.toJSON()).isEqualTo(oneItem)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerConnector(injector)
        t.list.add(TriggerConnector(injector))
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerConnector
        assertThat(t2.size()).isEqualTo(1)
        assertThat(t2.list[0]).isInstanceOf(TriggerConnector::class.java)
    }

}
