package info.nightscout.automation.triggers

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.jupiter.api.Test

class TriggerConnectorTest : TriggerTestBase() {

    @Test fun testTriggerList() {
        val t = TriggerConnector(injector)
        val t2 = TriggerConnector(injector)
        val t3 = TriggerConnector(injector)
        Assert.assertTrue(t.size() == 0)
        t.list.add(t2)
        Assert.assertTrue(t.size() == 1)
        Assert.assertEquals(t2, t.list[0])
        t.list.add(t3)
        Assert.assertTrue(t.size() == 2)
        Assert.assertEquals(t2, t.list[0])
        Assert.assertEquals(t3, t.list[1])
        Assert.assertTrue(t.list.remove(t2))
        Assert.assertTrue(t.size() == 1)
        Assert.assertEquals(t3, t.list[0])
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun testListTriggerOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.OR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        Assert.assertFalse(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun testListTriggerXOR() {
        val t = TriggerConnector(injector, TriggerConnector.Type.XOR)
        t.list.add(TriggerDummy(injector))
        t.list.add(TriggerDummy(injector))
        Assert.assertFalse(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assert.assertTrue(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assert.assertFalse(t.shouldRun())
    }

    @Test fun testListTriggerAND() {
        val t = TriggerConnector(injector, TriggerConnector.Type.AND)
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector, true))
        Assert.assertTrue(t.shouldRun())
        t.list.add(TriggerDummy(injector, true))
        t.list.add(TriggerDummy(injector))
        Assert.assertFalse(t.shouldRun())
    }

    @Test fun toJSONTest() {
        val t = TriggerConnector(injector)
        Assert.assertEquals(empty, t.toJSON())
        t.list.add(TriggerConnector(injector))
        Assert.assertEquals(oneItem, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t = TriggerConnector(injector)
        t.list.add(TriggerConnector(injector))
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerConnector
        Assert.assertEquals(1, t2.size().toLong())
        Assert.assertTrue(t2.list[0] is TriggerConnector)
    }

    companion object {

        const val empty = "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[]},\"type\":\"TriggerConnector\"}"
        const val oneItem =
            "{\"data\":{\"connectorType\":\"AND\",\"triggerList\":[\"{\\\"data\\\":{\\\"connectorType\\\":\\\"AND\\\",\\\"triggerList\\\":[]},\\\"type\\\":\\\"TriggerConnector\\\"}\"]},\"type\":\"TriggerConnector\"}"
    }
}