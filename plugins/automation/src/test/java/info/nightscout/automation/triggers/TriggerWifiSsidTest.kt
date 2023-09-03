package info.nightscout.automation.triggers

import com.google.common.base.Optional
import info.nightscout.automation.R
import info.nightscout.automation.elements.Comparator
import info.nightscout.rx.events.EventNetworkChange
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class TriggerWifiSsidTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        val e = EventNetworkChange()
        `when`(receiverStatusStore.lastNetworkEvent).thenReturn(e)
        var t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_EQUAL)
        e.wifiConnected = false
        Assertions.assertFalse(t.shouldRun())
        e.wifiConnected = true
        e.ssid = "otherSSID"
        Assertions.assertFalse(t.shouldRun())
        e.wifiConnected = true
        e.ssid = "aSSID 1"
        Assertions.assertTrue(t.shouldRun())
        t = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        e.wifiConnected = false
        Assertions.assertTrue(t.shouldRun())

        // no network data
        `when`(receiverStatusStore.lastNetworkEvent).thenReturn(null)
        Assertions.assertFalse(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerWifiSsid
        Assertions.assertEquals("aSSID", t1.ssid.value)
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL_OR_LESSER, t.comparator.value)
    }

    var json = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"ssid\":\"aSSID\"},\"type\":\"TriggerWifiSsid\"}"
    @Test fun toJSONTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        Assertions.assertEquals(json, t.toJSON())
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerWifiSsid
        Assertions.assertEquals(Comparator.Compare.IS_EQUAL, t2.comparator.value)
        Assertions.assertEquals("aSSID", t2.ssid.value)
    }

    @Test fun iconTest() {
        Assertions.assertEquals(Optional.of(R.drawable.ic_network_wifi), TriggerWifiSsid(injector).icon())
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.string.ns_wifi_ssids, TriggerWifiSsid(injector).friendlyName())
    }

    @Test fun friendlyDescriptionTest() {
        Assertions.assertEquals(null, TriggerWifiSsid(injector).friendlyDescription()) //not mocked
    }
}