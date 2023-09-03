package info.nightscout.automation.triggers

import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TriggerConnectorTest : TriggerTestBase() {

    private val empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"TriggerConnector\"}"
    val oneItem =
        "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\"]},\"type\":\"TriggerConnector\"}"

    @Test fun testTriggerList() {
        val t = TriggerConnector(injector)
        val t2 = TriggerConnector(injector)
        val t3 = TriggerConnector(injector)
        Assertions.assertTrue(t.size() == 0)
        t.list.add(t2)
        Assertions.assertTrue(t.size() == 1)
        Assertions.assertEquals(t2, t.list[0])
        t.list.add(t3)
        Assertions.assertTrue(t.size() == 2)
        Assertions.assertEquals(t2, t.list[0])
        Assertions.assertEquals(t3, t.list[1])
        Assertions.assertTrue(t.list.remove(t2))
        Assertions.assertTrue(t.size() == 1)
        Assertions.assertEquals(t3, t.list[0])
        Assertions.assertTrue(t.shouldRun())
    }

    @Test fun testListTriggerOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.OR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        Assertions.assertFalse(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assertions.assertTrue(t.shouldRun())
    }

    @Test fun testListTriggerXOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.XOR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        Assertions.assertFalse(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assertions.assertTrue(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun testListTriggerAND() {
        val t = TriggerConnector(injector, TriggerConnector.Type.AND)
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector, true))
        Assertions.assertTrue(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun toJSONTest() {
        val t = TriggerConnector(injector)
        Assertions.assertEquals(empty, t.toJSON())
        t.list.add(TriggerConnector(injector))
        Assertions.assertEquals(oneItem, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerConnector(injector)
        t.list.add(TriggerConnector(injector))
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerConnector
        Assertions.assertEquals(1, t2.size().toLong())
        Assertions.assertTrue(t2.list[0] is TriggerConnector)
    }

}