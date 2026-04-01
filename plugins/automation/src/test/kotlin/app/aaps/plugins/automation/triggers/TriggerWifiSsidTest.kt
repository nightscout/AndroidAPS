package app.aaps.plugins.automation.triggers

import app.aaps.core.interfaces.receivers.ReceiverStatusStore.NetworkStatus
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class TriggerWifiSsidTest : TriggerTestBase() {

    @Test fun shouldRunTest() = runTest {
        val networkFlow = MutableStateFlow<NetworkStatus?>(NetworkStatus())
        whenever(receiverStatusStore.networkStatusFlow).thenReturn(networkFlow)
        var t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_EQUAL)
        networkFlow.value = NetworkStatus(wifiConnected = false)
        assertThat(t.shouldRun()).isFalse()
        networkFlow.value = NetworkStatus(wifiConnected = true, ssid = "otherSSID")
        assertThat(t.shouldRun()).isFalse()
        networkFlow.value = NetworkStatus(wifiConnected = true, ssid = "aSSID 1")
        assertThat(t.shouldRun()).isTrue()
        t = TriggerWifiSsid(injector).setValue("aSSID 1").comparator(Comparator.Compare.IS_NOT_AVAILABLE)
        networkFlow.value = NetworkStatus(wifiConnected = false)
        assertThat(t.shouldRun()).isTrue()

        // no network data
        networkFlow.value = null
        assertThat(t.shouldRun()).isFalse()
    }

    @Test fun copyConstructorTest() = runTest {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL_OR_LESSER)
        val t1 = t.duplicate() as TriggerWifiSsid
        assertThat(t1.ssid.value).isEqualTo("aSSID")
        assertThat(t.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL_OR_LESSER)
    }

    var json = "{\"data\":{\"comparator\":\"IS_EQUAL\",\"ssid\":\"aSSID\"},\"type\":\"TriggerWifiSsid\"}"
    @Test fun toJSONTest() = runTest {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        JSONAssert.assertEquals(json, t.toJSON(), true)
    }

    @Test @Throws(JSONException::class) fun fromJSONTest() {
        val t: TriggerWifiSsid = TriggerWifiSsid(injector).setValue("aSSID").comparator(Comparator.Compare.IS_EQUAL)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerWifiSsid
        assertThat(t2.comparator.value).isEqualTo(Comparator.Compare.IS_EQUAL)
        assertThat(t2.ssid.value).isEqualTo("aSSID")
    }

    @Test fun iconTest() = runTest {
        assertThat(TriggerWifiSsid(injector).icon().get()).isEqualTo(R.drawable.ic_network_wifi)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(TriggerWifiSsid(injector).friendlyName()).isEqualTo(app.aaps.core.ui.R.string.ns_wifi_ssids)
    }

    @Test fun friendlyDescriptionTest() = runTest {
        assertThat(TriggerWifiSsid(injector).friendlyDescription()).isNull() //not mocked
    }
}
