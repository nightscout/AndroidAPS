package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.rx.events.EventNetworkChange
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerWifiSsidTest : TriggerTestBase() {

    @Test fun shouldRunTest() {
        val e = EventNetworkChange()
        whenever(receiverStatusStore.lastNetworkEvent).thenReturn(e)
        var t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_EQUAL)
        e.wifiConnected = false
        assertThat(t.shouldRun()).isFalse()
        e.wifiConnected = true
        e.ssid = "otherSSID"
        assertThat(t.shouldRun()).isFalse()
        e.wifiConnected = true
        e.ssid = "aSSID 1"
        assertThat(t.shouldRun()).isTrue()
        t = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        e.wifiConnected = false
        assertThat(t.shouldRun()).isTrue()

        // no network data
        whenever(receiverStatusStore.lastNetworkEvent).thenReturn(null)
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerWifiSsid
        assertThat(t1.ssid.value).isEqualTo("aSSID")
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    var json = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"ssid\":\"aSSID\"},\"type\":\"TriggerWifiSsid\"}"
    @Test fun toJSONTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(json, t.toJSON(), true)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerWifiSsid
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.ssid.value).isEqualTo("aSSID")
    }

    @Test fun iconTest() {
        assertThat(TriggerWifiSsid(injector).icon().get()).isEqualTo(R.drawable.ic_network_wifi)
    }

    @Test fun friendlyNameTest() {
        assertThat(TriggerWifiSsid(injector).friendlyName()).isEqualTo(app.aaps.core.ui.R.string.ns_wifi_ssids)
    }

    @Test fun friendlyDescriptionTest() {
        assertThat(TriggerWifiSsid(injector).friendlyDescription()).isNull() //not mocked
    }
}
